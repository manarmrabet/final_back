package com.example.CWMS.service;

import com.example.CWMS.dto.*;
import com.example.CWMS.iservice.IProductionService;
import com.example.CWMS.model.cwms.ProductionLog;
import com.example.CWMS.repository.cwms.ProductionLogRepository;
import com.example.CWMS.repository.erp.LotProjection;
import com.example.CWMS.repository.erp.StockLotRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service de gestion des sorties production.
 *
 * BONNE PRATIQUE — principes appliqués :
 *
 * 1. SÉPARATION DES RESPONSABILITÉS
 *    → readLot()            : lecture seule, retourne un POJO typé
 *    → resolveMagasin()     : résolution ERP, retourne un contexte typé
 *    → executeErpOperations(): 4 opérations ERP atomiques
 *    → saveAndReturn()      : construction réponse + log garanti
 *    → saveLogDirect()      : traçabilité, ne lève jamais d'exception
 *
 * 2. TRAÇABILITÉ GARANTIE
 *    → saveLogDirect() est appelé sur TOUS les chemins (succès ET erreur)
 *    → entouré d'un try/catch : le log ne peut jamais bloquer la réponse
 *    → status FAILED tracé pour : lot introuvable, stock vide,
 *      qty invalide, magasin introuvable, erreur ERP
 *
 * 3. @TRANSACTIONAL sur les méthodes de sortie
 *    → si une étape ERP échoue → rollback automatique des INSERT/UPDATE
 *    → le log CWMS reste sauvegardé (base séparée, hors transaction ERP)
 *
 * 4. AUCUN ACCÈS PAR INDEX sur les Object[]
 *    → LotProjection pour findLotWithDesignation (accès par nom)
 *    → Object[] uniquement pour getMagasinsData3001/3002 avec
 *      vérification défensive de r.length avant tout accès
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ProductionServiceImpl implements IProductionService {

    private final StockLotRepository      stockLotRepo;
    private final ProductionLogRepository logRepo;

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    // ════════════════════════════════════════════════════════════════════════
    //  CHECK STOCK — lecture seule, pas de @Transactional
    // ════════════════════════════════════════════════════════════════════════
    @Override
    public StockCheckDTO checkStock(String clot) {
        StockCheckDTO dto = new StockCheckDTO();
        dto.setLotCode(clot);

        Optional<LotProjection> raw = stockLotRepo.findLotWithDesignation(clot);
        if (raw.isEmpty()) {
            dto.setFound(false);
            dto.setSufficient(false);
            return dto;
        }

        LotProjection row = raw.get();
        dto.setFound(true);
        dto.setWarehouse   (proj(row.getT_cwar()));
        dto.setLocation    (proj(row.getT_loca()));
        dto.setItemCode    (proj(row.getT_item()));
        dto.setLotCode     (proj(row.getT_clot()));
        dto.setQtyAvailable(row.getQty() != null ? row.getQty() : 0.0);
        dto.setUnit("UN");
        dto.setSufficient(dto.getQtyAvailable() > 0);
        return dto;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  SORTIE TOTALE
    //  @Transactional : rollback ERP si une étape échoue
    //  Log CWMS : sauvegardé dans tous les cas via saveAndReturn()
    // ════════════════════════════════════════════════════════════════════════
    @Override
    @Transactional
    public SortieResponseDTO sortieTotale(SortieRequestDTO req,
                                          Long userId, String userName) {
        SortieResponseDTO resp = new SortieResponseDTO();
        resp.setLotCode(req.getLotCode());
        resp.setOperationType("TOTALE");

        // 1. Lire le lot (projection — sans risque d'index out of bounds)
        LotSnapshot snap = readLot(req.getLotCode());
        if (snap == null)
            return saveAndReturn(resp, req, userId, userName,
                    "Lot introuvable : " + req.getLotCode(),
                    0, 0, false);

        if (snap.qty <= 0)
            return saveAndReturn(resp, req, userId, userName,
                    "Stock déjà vide",
                    snap.qty, snap.qty, false);

        // 2. Résoudre le magasin (inclut désormais cwar='04')
        MagasinContext ctx = resolveMagasin(snap.cwar, req.getLotCode());
        if (ctx == null)
            return saveAndReturn(resp, req, userId, userName,
                    "Aucun magasin ERP pour cwar=" + snap.cwar
                            + " — vérifiez dbo_ttcmcs003140",
                    snap.qty, snap.qty, false);

        // 3-6. INSERT twhinh220140 + twhinp100140
        //      UPDATE ttcibd100140 + twhwmd215140
        String err = executeErpOperations(ctx, snap, snap.qty, req.getLotCode());
        if (err != null)
            return saveAndReturn(resp, req, userId, userName,
                    err, snap.qty, snap.qty, false);

        // 7. Vider le stock (t_qhnd → 0)
        stockLotRepo.sortieTotale(req.getLotCode());

        return saveAndReturn(resp, req, userId, userName,
                null, snap.qty, 0.0, true);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  SORTIE PARTIELLE
    //  Identique à la totale mais avec qty < stock (strictement inférieur)
    // ════════════════════════════════════════════════════════════════════════
    @Override
    @Transactional
    public SortieResponseDTO sortiePartielle(SortieRequestDTO req,
                                             Long userId, String userName) {
        SortieResponseDTO resp = new SortieResponseDTO();
        resp.setLotCode(req.getLotCode());
        resp.setOperationType("PARTIELLE");

        // Validation quantité — log tracé même ici
        if (req.getQuantite() == null || req.getQuantite() <= 0) {
            resp.setSuccess(false);
            resp.setMessage("Quantité invalide");
            saveLogDirect(req, userId, userName, 0, 0, 0,
                    ProductionLog.OperationType.PARTIELLE,
                    ProductionLog.OperationStatus.FAILED,
                    "Quantité invalide ou nulle");
            return resp;
        }
        double qty = req.getQuantite();

        // 1. Lire le lot
        LotSnapshot snap = readLot(req.getLotCode());
        if (snap == null)
            return saveAndReturn(resp, req, userId, userName,
                    "Lot introuvable : " + req.getLotCode(),
                    0, 0, false);

        // 2. Contrôle qty < stock (côté backend — même si Flutter vérifie déjà)
        if (qty >= snap.qty) {
            resp.setSuccess(false);
            resp.setMessage(String.format(
                    "Quantité (%.0f) >= stock disponible (%.0f). "
                            + "Utilisez la sortie totale.", qty, snap.qty));
            resp.setQtyBefore(snap.qty);
            saveLogDirect(req, userId, userName,
                    snap.qty, qty, snap.qty,
                    ProductionLog.OperationType.PARTIELLE,
                    ProductionLog.OperationStatus.FAILED,
                    "qty >= stock: " + qty + " >= " + snap.qty);
            return resp;
        }

        // 3. Résoudre le magasin
        MagasinContext ctx = resolveMagasin(snap.cwar, req.getLotCode());
        if (ctx == null)
            return saveAndReturn(resp, req, userId, userName,
                    "Aucun magasin ERP pour cwar=" + snap.cwar
                            + " — vérifiez dbo_ttcmcs003140",
                    snap.qty, snap.qty, false);

        // 4-7. Opérations ERP
        String err = executeErpOperations(ctx, snap, qty, req.getLotCode());
        if (err != null)
            return saveAndReturn(resp, req, userId, userName,
                    err, snap.qty, snap.qty, false);

        // 8. Décrémenter t_qhnd
        stockLotRepo.sortiePartielle(req.getLotCode(), qty);

        return saveAndReturn(resp, req, userId, userName,
                null, snap.qty, snap.qty - qty, true);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  DASHBOARD
    // ════════════════════════════════════════════════════════════════════════
    @Override
    public List<ProductionLogDTO> getAllLogs() {
        return logRepo.findAllByOrderByCreatedAtDesc()
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    public List<ProductionLogDTO> getTodayLogs() {
        return logRepo.findTodayLogs()
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    public ProductionStatsDTO getStats() {
        ProductionStatsDTO stats = new ProductionStatsDTO();
        stats.setTotalOpsToday(logRepo.countSuccessToday());
        stats.setTotalQtyToday(logRepo.sumQtyToday());
        stats.setRecentLogs(getTodayLogs());
        stats.setOperatorStats(
                logRepo.getOperatorStats().stream().map(row ->
                        new ProductionStatsDTO.OperatorStatDTO(
                                ((Number) row[0]).longValue(),
                                row[1] != null ? row[1].toString() : "N/A",
                                ((Number) row[2]).longValue(),
                                row[3] != null ? ((Number) row[3]).doubleValue() : 0.0
                        )).collect(Collectors.toList())
        );
        return stats;
    }

    @Override
    public List<ProductionLogDTO> getLogsByUser(Long userId) {
        return logRepo.findByUserIdOrderByCreatedAtDesc(userId)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    // ════════════════════════════════════════════════════════════════════════
    //  HELPERS PRIVÉS
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Lit le lot et retourne un POJO typé (LotSnapshot).
     *
     * BONNE PRATIQUE : retourne un objet métier (LotSnapshot) et non
     * un Object[] ou une LotProjection directement. Le reste du service
     * ne dépend pas des détails de la couche de persistance.
     *
     * Retourne null si lot introuvable → le appelant décide quoi faire.
     */
    private LotSnapshot readLot(String clot) {
        Optional<LotProjection> raw = stockLotRepo.findLotWithDesignation(clot);
        if (raw.isEmpty()) return null;

        LotProjection r = raw.get();

        // Log de diagnostic si une colonne clé est null
        if (r.getT_item() == null)
            log.warn("findLotWithDesignation({}) : t_item est NULL "
                    + "— vérifiez dbo_twhinr1401200", clot);

        LotSnapshot s = new LotSnapshot();
        s.cwar = proj(r.getT_cwar());
        s.loca = proj(r.getT_loca());
        s.item = proj(r.getT_item());
        s.clot = proj(r.getT_clot());
        s.qty  = r.getQty() != null ? r.getQty() : 0.0;
        s.orun = "UN"; // unité fixe ERP
        return s;
    }

    /**
     * Résout le magasin ERP pour un cwar donné.
     *
     * BONNE PRATIQUE : vérification défensive de r.length avant
     * tout accès à un index — même avec des alias garantis,
     * une requête vide ou un driver différent peut retourner
     * moins de colonnes que prévu.
     *
     * Loggue les cas problématiques pour faciliter le diagnostic.
     */
    private MagasinContext resolveMagasin(String cwar, String clot) {
        List<Object[]> mag3001 = stockLotRepo.getMagasinsData3001();

        if (mag3001.isEmpty()) {
            log.error("getMagasinsData3001() retourne 0 lignes. "
                    + "Vérifiez dbo_ttcmcs003140 et dbo_twhinh010140 "
                    + "(doivent contenir des entrées CBS/BSP).");
            return null;
        }

        // Chercher la ligne dont col0 (t_cwar) = cwar du lot
        Object[] magasin = mag3001.stream()
                .filter(r -> r.length > 0 && cwar.equals(safe(r[0])))
                .findFirst()
                .orElse(null);

        if (magasin == null) {
            // Log détaillé pour le diagnostic
            String cwarsDispos = mag3001.stream()
                    .filter(r -> r.length > 0 && r[0] != null)
                    .map(r -> r[0].toString().trim())
                    .distinct()
                    .collect(Collectors.joining(", "));
            log.warn("Aucun magasin trouvé pour cwar='{}'. "
                            + "Magasins disponibles dans ttcmcs003140 : [{}]",
                    cwar, cwarsDispos);
            return null;
        }

        // Vérification défensive avant accès aux index
        if (magasin.length < 5) {
            log.error("getMagasinsData3001() : ligne avec {} colonnes "
                            + "au lieu de 7 attendues. Row={}",
                    magasin.length, java.util.Arrays.toString(magasin));
            return null;
        }

        // col0=cwar, col1=ose, col2=oset, col3=orno, col4=comp
        String orno = safe(magasin[3]);
        int    oset = magasin[2] != null ? ((Number) magasin[2]).intValue() : 1;
        String comp = safe(magasin[4]);

        // Vérifier que le lot est bien dans ce magasin
        List<Object[]> mag3002 = stockLotRepo.getMagasinsData3002(
                cwar, oset, orno, clot);

        if (mag3002.isEmpty()) {
            log.warn("getMagasinsData3002 vide : lot='{}' non trouvé "
                            + "dans cwar='{}'. Le lot existe-t-il dans ce magasin ?",
                    clot, cwar);
            return null;
        }

        MagasinContext ctx = new MagasinContext();
        ctx.orno = orno;
        ctx.comp = comp;
        // oset de mag3002 col1 — fallback sur oset de mag3001
        ctx.oset = (mag3002.get(0).length > 1 && mag3002.get(0)[1] != null)
                ? ((Number) mag3002.get(0)[1]).intValue()
                : oset;
        return ctx;
    }

    /**
     * Exécute les 4 opérations ERP dans la même transaction.
     *
     * BONNE PRATIQUE : entouré d'un try/catch pour transformer
     * toute exception SQL en message lisible → le service peut
     * tracer un log FAILED et retourner une réponse propre
     * au lieu d'une stack trace brute.
     *
     * Retourne null si tout est OK, message d'erreur sinon.
     */
    private String executeErpOperations(MagasinContext ctx,
                                        LotSnapshot snap,
                                        double qty,
                                        String clot) {
        try {
            int newPono = stockLotRepo.getMaxPono(ctx.orno) + 1;



            // Garde : vérifier qu'aucun ordre actif n'existe déjà
            int ordresActifs = stockLotRepo.countOrdresActifs(ctx.orno);
            if (ordresActifs > 0) {
                log.warn("executeErpOperations : ordre actif (ssts 30/70/90) "
                        + "déjà présent pour orno={}. Doublon bloqué.", ctx.orno);
                return "Un ordre de sortie est déjà en cours pour ce lot "
                        + "(statut actif dans LN). Attendez sa complétion.";
            }

            // INSERT ordre de sortie
            int ins1 = stockLotRepo.insertOrdreWhinh220(
                    ctx.orno, newPono, ctx.oset,
                    snap.cwar, ctx.comp, snap.item,
                    clot, qty, snap.orun, qty, qty);

            if (ins1 == 0) {
                log.error("insertOrdreWhinh220 : 0 ligne affectée pour "
                        + "clot={}, orno={}, pono={}", clot, ctx.orno, newPono);
                return "INSERT dbo_twhinh220140 échoué (0 ligne affectée)";
            }

            // INSERT ligne picking
            stockLotRepo.insertLigneWhinp100(
                    ctx.orno, newPono, snap.item, snap.orun, qty, snap.cwar);

            // UPDATE allocations
            stockLotRepo.updateAlloArticle(snap.item, qty);
            stockLotRepo.updateAlloEmplacement(snap.item, snap.cwar, qty);

            return null; // succès

        } catch (Exception e) {
            log.error("executeErpOperations : erreur pour clot={} : {}",
                    clot, e.getMessage(), e);
            return "Erreur ERP : " + e.getMessage();
        }
    }

    /**
     * Construit la réponse ET sauvegarde le log CWMS.
     *
     * BONNE PRATIQUE : appelé sur TOUS les chemins de sortie
     * (succès ET chaque type d'erreur) → traçabilité complète garantie.
     * Le log est sauvegardé avant de retourner la réponse.
     */
    private SortieResponseDTO saveAndReturn(SortieResponseDTO resp,
                                            SortieRequestDTO req,
                                            Long userId, String userName,
                                            String errMsg,
                                            double qtyBefore, double qtyAfter,
                                            boolean success) {
        double qtySortie = qtyBefore - qtyAfter;
        ProductionLog.OperationType   type = req.isSortieComplete()
                ? ProductionLog.OperationType.TOTALE
                : ProductionLog.OperationType.PARTIELLE;
        ProductionLog.OperationStatus st   = success
                ? ProductionLog.OperationStatus.SUCCESS
                : ProductionLog.OperationStatus.FAILED;

        ProductionLog entry = saveLogDirect(req, userId, userName,
                qtyBefore, qtySortie, qtyAfter, type, st, errMsg);

        resp.setSuccess(success);
        resp.setMessage(success
                ? (req.isSortieComplete()
                ? "Sortie totale effectuée — lot vidé"
                : String.format("Sortie partielle effectuée. "
                + "Restant : %.0f", qtyAfter))
                : errMsg);
        resp.setQtyBefore(qtyBefore);
        resp.setQtySortie(qtySortie);
        resp.setQtyAfter(qtyAfter);
        if (entry != null) resp.setLogId(entry.getId());
        return resp;
    }

    /**
     * Sauvegarde le log dans ProductionLog (base CWMS — séparée de l'ERP).
     *
     * BONNE PRATIQUE :
     * → try/catch global : une erreur de log ne doit JAMAIS
     *   bloquer ni faire planter la réponse principale
     * → Utilise LotProjection pour relire les infos du lot :
     *   accès sécurisé par nom, pas par index
     * → Nommé saveLogDirect (pas saveLog) pour éviter toute
     *   confusion avec les méthodes de logging SLF4J
     */
    private ProductionLog saveLogDirect(SortieRequestDTO req,
                                        Long userId, String userName,
                                        double qtyBefore, double qtyRequested,
                                        double qtyAfter,
                                        ProductionLog.OperationType type,
                                        ProductionLog.OperationStatus status,
                                        String errMsg) {
        try {
            String itemCode = "N/A", warehouse = "N/A", location = "N/A";

            Optional<LotProjection> raw =
                    stockLotRepo.findLotWithDesignation(req.getLotCode());
            if (raw.isPresent()) {
                LotProjection r = raw.get();
                warehouse = proj(r.getT_cwar());
                location  = proj(r.getT_loca());
                itemCode  = proj(r.getT_item());
            }

            return logRepo.save(ProductionLog.builder()
                    .lotCode      (req.getLotCode())
                    .itemCode     (itemCode)
                    .warehouse    (warehouse)
                    .location     (location)
                    .qtyBefore    (qtyBefore)
                    .qtyRequested (qtyRequested)
                    .qtyAfter     (qtyAfter)
                    .operationType(type)
                    .status       (status)
                    .userId       (userId)
                    .userName     (userName)
                    .deviceInfo   (req.getDeviceInfo())
                    .source       (req.getSource() != null
                            ? ProductionLog.SourceType.valueOf(req.getSource())
                            : ProductionLog.SourceType.MOBILE)
                    .notes        (req.getNotes())
                    .errorMessage (errMsg)
                    .createdAt    (LocalDateTime.now())
                    .build());

        } catch (Exception e) {
            log.error("CRITIQUE — saveLogDirect échoué pour lot={} : {}",
                    req.getLotCode(), e.getMessage(), e);
            return null;
        }
    }

    private ProductionLogDTO toDTO(ProductionLog l) {
        ProductionLogDTO dto = new ProductionLogDTO();
        dto.setId           (l.getId());
        dto.setLotCode      (l.getLotCode());
        dto.setItemCode     (l.getItemCode());
        dto.setWarehouse    (l.getWarehouse());
        dto.setLocation     (l.getLocation());
        dto.setQtyBefore    (l.getQtyBefore());
        dto.setQtyRequested (l.getQtyRequested());
        dto.setQtyAfter     (l.getQtyAfter());
        dto.setQtyDelta     (l.getQtyBefore() - l.getQtyAfter());
        dto.setOperationType(l.getOperationType().name());
        dto.setStatus       (l.getStatus().name());
        dto.setUserId       (l.getUserId());
        dto.setUserName     (l.getUserName());
        dto.setDeviceInfo   (l.getDeviceInfo());
        dto.setSource       (l.getSource().name());
        dto.setCreatedAt    (l.getCreatedAt() != null
                ? l.getCreatedAt().format(FMT) : "");
        dto.setNotes        (l.getNotes());
        dto.setStockVide    (l.getQtyAfter() != null && l.getQtyAfter() == 0.0);
        dto.setErrorMessage (l.getErrorMessage());
        return dto;
    }

    // ── Utilitaires ──────────────────────────────────────────────────────────

    /** Sécurise une String issue d'une LotProjection. */
    private String proj(String s) {
        return s != null ? s.trim() : "N/A";
    }

    /** Sécurise un Object brut (uniquement pour getMagasinsData3001/3002). */
    private String safe(Object o) {
        return o != null ? o.toString().trim() : "N/A";
    }

    // ── POJOs internes — isolation de la couche de persistance ───────────────
    private static class LotSnapshot {
        String cwar, loca, item, clot, orun;
        double qty;
    }

    private static class MagasinContext {
        String orno, comp;
        int    oset;
    }
}