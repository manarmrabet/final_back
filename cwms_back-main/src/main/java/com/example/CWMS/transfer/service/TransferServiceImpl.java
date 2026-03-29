package com.example.CWMS.transfer.service;

import com.example.CWMS.audit.Auditable;
import com.example.CWMS.erp.entity.ErpArticle;
import com.example.CWMS.erp.entity.ErpEmplacement;
import com.example.CWMS.erp.entity.ErpStock;
import com.example.CWMS.erp.repository.ErpArticleRepository;
import com.example.CWMS.erp.repository.ErpEmplacementRepository;
import com.example.CWMS.erp.repository.ErpStockRepository;
import com.example.CWMS.model.User;
import com.example.CWMS.repository.UserRepository;
import com.example.CWMS.transfer.dto.*;
import com.example.CWMS.transfer.model.StockTransfer;
import com.example.CWMS.transfer.repository.StockTransferRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransferServiceImpl implements TransferService {

    // ─── ERP (lecture) ────────────────────────────────────────────────────
    private final ErpArticleRepository      erpArticleRepo;
    private final ErpStockRepository        erpStockRepo;
    private final ErpEmplacementRepository  erpEmplacementRepo;

    // ─── CWMS (lecture/écriture) ──────────────────────────────────────────
    private final StockTransferRepository   transferRepo;
    private final UserRepository            userRepo;

    /**
     * DataSource ERP — utilisé pour appeler la stored procedure de mise à jour.
     * Pattern A : même serveur SQL, écriture via SP pour garder la logique ERP
     * isolée et auditée côté ERP.
     *
     * Injection par @Qualifier pour éviter la confusion avec la datasource CWMS.
     */
    @Qualifier("erpDataSource")
    private final DataSource erpDataSource;

    // ═════════════════════════════════════════════════════════════════════════
    // CRÉER UN TRANSFERT
    // ═════════════════════════════════════════════════════════════════════════

    @Override
    @Auditable(action = "CREATE_TRANSFER")
    public TransferResponseDTO createTransfer(TransferRequestDTO request) {
        log.info("Création transfert : {} | {} → {} | qté={}",
                request.getErpItemCode(), request.getSourceLocation(),
                request.getDestLocation(), request.getQuantity());

        // 1. Validation métier (stock ERP vérifié avant écriture)
        validateTransferRequest(request);

        // 2. Enrichissement article depuis ERP
        ErpArticle article = erpArticleRepo.findByItemCode(request.getErpItemCode())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Article introuvable dans l'ERP : " + request.getErpItemCode()));

        // 3. Opérateur connecté
        User operator = getCurrentUser();
        String sourceWarehouse = request.getSourceLocation() != null
                ? request.getSourceLocation().split("/")[0].trim()
                : request.getSourceLocation();

        String destWarehouse = request.getDestLocation() != null
                ? request.getDestLocation().split("/")[0].trim()
                : request.getDestLocation();

        // 4. Enregistrement dans CWMSDB
        StockTransfer transfer = StockTransfer.builder()
                .erpItemCode(request.getErpItemCode())
                .erpItemLabel(article.getDesignation())
                .lotNumber(request.getLotNumber())
                .sourceLocation(request.getSourceLocation())
                .destLocation(request.getDestLocation())
                .sourceWarehouse(sourceWarehouse)      // ← NOUVEAU
                .destWarehouse(destWarehouse)
                .quantity(request.getQuantity())
                .unit(article.getStockUnit())
                .status(StockTransfer.TransferStatus.DONE)
                .transferType(request.getTransferType() != null
                        ? request.getTransferType()
                        : StockTransfer.TransferType.INTERNAL_RELOCATION)
                .operator(operator)
                .notes(request.getNotes())
                .completedAt(LocalDateTime.now())
                .build();

        StockTransfer saved = transferRepo.save(transfer);
        log.info("Transfert #{} enregistré dans CWMSDB", saved.getId());

        // 5. Mise à jour ERP via Stored Procedure (Pattern A — même serveur SQL)
        //    La SP gère l'écriture dans dbo_twhinr1401200 de façon atomique.
        //    Si la SP échoue, on log le warning mais on ne fait pas échouer le transfert CWMS
        //    (le transfert est déjà enregistré — la SP peut être rejouée).
        updateErpStock(request);

        return TransferResponseDTO.from(saved);
    }

    /**
     * Appel de la Stored Procedure ERP pour mettre à jour t_ball.
     *
     * La SP doit être créée dans la base ERP (voir script SQL ci-dessous).
     * Elle soustrait la quantité de t_ball dans l'emplacement source
     * et l'ajoute dans l'emplacement destination.
     *
     * Script de création de la SP (à exécuter une seule fois sur ERP) :
     * ─────────────────────────────────────────────────────────────────────
     * CREATE OR ALTER PROCEDURE sp_cwms_transfert_stock
     *     @p_item      NVARCHAR(50),
     *     @p_source    NVARCHAR(50),
     *     @p_dest      NVARCHAR(50),
     *     @p_lot       NVARCHAR(50) = NULL,
     *     @p_qty       INT
     * AS
     * BEGIN
     *     ET NOCOUNT ON;
     *     BEGIN TRY
     *         BEGINTRANSACTION;
     *
     *         -- Soustraire du source
     *         UPDATE dbo_twhinr1401200
     *         SET t_ball = CAST(CAST(t_ball AS FLOAT) - @p_qty AS NVARCHAR(50))
     *         WHERE t_item = @p_item
     *           AND t_loca = @p_source
     *           AND (@p_lot IS NULL OR t_clot = @p_lot);
     *
     *         -- Ajouter à la destination (si ligne existe)
     *         IF EXISTS (
     *             SELECT 1 FROM dbo_twhinr1401200
     *             WHERE t_item = @p_item AND t_loca = @p_dest
     *               AND (@p_lot IS NULL OR t_clot = @p_lot)
     *         )
     *         BEGIN
     *             UPDATE dbo_twhinr1401200
     *             SET t_ball = CAST(CAST(t_ball AS FLOAT) + @p_qty AS NVARCHAR(50))
     *             WHERE t_item = @p_item AND t_loca = @p_dest
     *               AND (@p_lot IS NULL OR t_clot = @p_lot);
     *         END
     *         ELSE
     *         BEGIN
     *             -- Créer la ligne destination si elle n'existe pas
     *             INSERT INTO dbo_twhinr1401200
     *                 (t_cwar, t_loca, t_item, t_clot, t_idat, t_ball, t_bout, t_btri, t_lsid, t_crdt)
     *             SELECT t_cwar, @p_dest, t_item, t_clot,
     *                    CAST(GETDATE() AS DATE),
     *                    CAST(@p_qty AS NVARCHAR(50)), '0', '0', 'A',
     *                    CAST(GETDATE() AS DATE)
     *             FROM dbo_twhinr1401200
     *             WHERE t_item = @p_item AND t_loca = @p_source
     *               AND (@p_lot IS NULL OR t_clot = @p_lot)
     *             LIMIT 1;  -- SQL Server: utiliser TOP 1 à la place
     *         END
     *
     *         COMMIT;
     *     END TRY
     *     BEGINCATCH
     *         ROLLBACK;
     *         THROW;
     *     END CATCH
     * END
     * ─────────────────────────────────────────────────────────────────────
     */
    private void updateErpStock(TransferRequestDTO request) {
        try (Connection conn = erpDataSource.getConnection();
             CallableStatement cs = conn.prepareCall(
                     "{CALL sp_cwms_transfert_stock(?, ?, ?, ?, ?)}")) {

            cs.setString(1, request.getErpItemCode());
            cs.setString(2, request.getSourceLocation());
            cs.setString(3, request.getDestLocation());
            cs.setString(4, request.getLotNumber()); // null si pas de lot
            cs.setInt(5,    request.getQuantity());
            cs.execute();

            log.info("✅ ERP stock mis à jour via SP pour article {} | {} → {}",
                    request.getErpItemCode(), request.getSourceLocation(), request.getDestLocation());

        } catch (Exception e) {
            // On log en WARNING mais on ne fait pas échouer le transfert CWMS.
            // Le transfert est déjà sauvegardé — la SP peut être rejouée manuellement.
            log.warn("⚠️ ERP stock update via SP échoué (transfert CWMS OK) : {}", e.getMessage());
        }
    }

    @Override
    public List<TransferResponseDTO> createTransferBatch(List<TransferRequestDTO> requests) {
        log.info("Batch transfert : {} lignes", requests.size());
        List<TransferResponseDTO> results = new ArrayList<>();

        for (TransferRequestDTO request : requests) {
            try {
                results.add(createTransfer(request));
            } catch (Exception e) {
                log.error("Erreur batch article {} : {}", request.getErpItemCode(), e.getMessage());
                results.add(TransferResponseDTO.builder()
                        .erpItemCode(request.getErpItemCode())
                        .sourceLocation(request.getSourceLocation())
                        .destLocation(request.getDestLocation())
                        .quantity(request.getQuantity())
                        .status(StockTransfer.TransferStatus.ERROR)
                        .errorMessage(e.getMessage())
                        .createdAt(LocalDateTime.now())
                        .build());
            }
        }

        long success = results.stream()
                .filter(r -> StockTransfer.TransferStatus.DONE.equals(r.getStatus())).count();
        log.info("Batch terminé : {}/{} succès", success, requests.size());
        return results;
    }



    // ═════════════════════════════════════════════════════════════════════════
    // VALIDATION / ANNULATION
    // ═════════════════════════════════════════════════════════════════════════

    @Override
    @Transactional
    @Auditable(action = "VALIDATE_TRANSFER")
    public TransferResponseDTO validateTransfer(Long transferId) {
        StockTransfer t = getTransferOrThrow(transferId);
        if (!StockTransfer.TransferStatus.PENDING.equals(t.getStatus())) {
            throw new IllegalStateException("Seul un PENDING peut être validé. Statut : " + t.getStatus());
        }
        User validator = getCurrentUser();
        t.setStatus(StockTransfer.TransferStatus.DONE);
        t.setValidatedBy(validator);
        t.setValidatedAt(LocalDateTime.now());
        t.setCompletedAt(LocalDateTime.now());
        return TransferResponseDTO.from(transferRepo.save(t));
    }

    @Override
    @Transactional
    @Auditable(action = "CANCEL_TRANSFER")
    public TransferResponseDTO cancelTransfer(Long transferId, String reason) {
        StockTransfer t = getTransferOrThrow(transferId);
        if (StockTransfer.TransferStatus.DONE.equals(t.getStatus())) {
            throw new IllegalStateException("Impossible d'annuler un DONE");
        }
        t.setStatus(StockTransfer.TransferStatus.CANCELLED);
        t.setErrorMessage(reason);
        t.setValidatedBy(getCurrentUser());
        t.setValidatedAt(LocalDateTime.now());
        return TransferResponseDTO.from(transferRepo.save(t));
    }

    // ═════════════════════════════════════════════════════════════════════════
    // CONSULTATION
    // ═════════════════════════════════════════════════════════════════════════

    @Override
    public TransferResponseDTO getById(Long id) {
        return TransferResponseDTO.from(getTransferOrThrow(id));
    }

    @Override
    public Page<TransferResponseDTO> getAll(Pageable pageable) {
        return transferRepo.findAll(pageable).map(TransferResponseDTO::from);
    }

    @Override
    public Page<TransferResponseDTO> search(String status, String itemCode, String location,
                                            LocalDateTime from, LocalDateTime to, Pageable pageable) {
        String s = blank(status)   ? null : status.trim();
        String i = blank(itemCode) ? null : itemCode.trim();
        String l = blank(location) ? null : location.trim();
        return transferRepo.search(s, i, l, from, to, pageable).map(TransferResponseDTO::from);
    }

    @Override
    public List<TransferResponseDTO> getMyTransfers(Integer operatorId) {
        return transferRepo
                .findByOperator_UserIdOrderByCreatedAtDesc(operatorId, PageRequest.of(0, 50))
                .stream().map(TransferResponseDTO::from).collect(Collectors.toList());
    }
    @Override
    @Transactional(transactionManager = "erpTransactionManager", readOnly = true)
    public List<ErpStockDTO> getStockByLot(String lotNumber) {
        List<ErpStock> stocks = erpStockRepo.findByLotNumber(lotNumber);

        if (stocks.isEmpty()) {
            throw new NoSuchElementException("Lot introuvable dans l'ERP : " + lotNumber);
        }

        return stocks.stream().map(s -> {
            // Enrichir avec la désignation article depuis dbo_ttcibd001120
            String label = erpArticleRepo.findByItemCode(s.getItemCode().trim())
                    .map(ErpArticle::getDesignation)
                    .orElse(s.getItemCode().trim());

            return ErpStockDTO.builder()
                    .id(s.getIdStockage())
                    .itemCode(s.getItemCode() != null ? s.getItemCode().trim() : null)
                    .itemLabel(label)               // ← désignation pour l'affichage
                    .location(s.getLocation())
                    .lotNumber(s.getLotNumber())
                    .quantityAvailable(s.getAvailableQuantityAsInt())
                    .warehouseCode(s.getWarehouseCode())
                    .entryDate(s.getEntryDate())
                    .lastTransactionDate(s.getLastTransactionDate())
                    .lineStatus(s.getLineStatus())
                    .build();
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional(transactionManager = "erpTransactionManager", readOnly = true)
    public ErpLocationDTO getLocationInfo(String locationCode) {
        // Chercher dans dbo_twhinr1401200 pour récupérer t_cwar de cet emplacement
        List<ErpStock> stocks = erpStockRepo.findByLocation(locationCode);

        String warehouseCode = stocks.isEmpty()
                ? locationCode   // fallback : on retourne le code lui-même
                : stocks.get(0).getWarehouseCode();

        // Chercher aussi dans dbo_ttccom100120 pour le type d'emplacement
        String locationType = erpEmplacementRepo.findByLocationCode(locationCode)
                .map(ErpEmplacement::getLocationType)
                .orElse("UNKNOWN");

        return ErpLocationDTO.builder()
                .locationCode(locationCode)
                .warehouseCode(warehouseCode)
                .locationType(locationType)
                .build();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // DONNÉES ERP
    // ═════════════════════════════════════════════════════════════════════════

    @Override
    @Transactional(transactionManager = "erpTransactionManager", readOnly = true)
    public ErpArticleDTO getArticleByCode(String itemCode) {
        return erpArticleRepo.findByItemCode(itemCode)
                .map(ErpArticleDTO::from)
                .orElseThrow(() -> new NoSuchElementException("Article introuvable : " + itemCode));
    }

    @Override
    @Transactional(transactionManager = "erpTransactionManager", readOnly = true)
    public List<ErpArticleDTO> searchArticles(String query) {
        return erpArticleRepo.search(query).stream()
                .map(ErpArticleDTO::from).collect(Collectors.toList());
    }

    @Override
    @Transactional(transactionManager = "erpTransactionManager", readOnly = true)
    public List<ErpStockDTO> getStockByItem(String itemCode) {
        return erpStockRepo.findByItemCode(itemCode).stream()
                .map(ErpStockDTO::from).collect(Collectors.toList());
    }

    @Override
    @Transactional(transactionManager = "erpTransactionManager", readOnly = true)
    public List<ErpStockDTO> getStockByLocation(String location) {
        return erpStockRepo.findByLocation(location).stream()
                .map(ErpStockDTO::from).collect(Collectors.toList());
    }

    // ═════════════════════════════════════════════════════════════════════════
    // DASHBOARD ENRICHI
    // ═════════════════════════════════════════════════════════════════════════

    @Override
    public TransferDashboardDTO getDashboard() {
        // Comptage par statut
        Map<String, Long> countByStatus = new HashMap<>();
        transferRepo.countByStatusGrouped()
                .forEach(row -> countByStatus.put((String) row[0], (Long) row[1]));

        // Totaux par période
        LocalDateTime today     = LocalDateTime.now().toLocalDate().atStartOfDay();
        LocalDateTime weekAgo   = LocalDateTime.now().minusDays(7);
        LocalDateTime monthAgo  = LocalDateTime.now().minusDays(30);

        long totalToday     = transferRepo.countSince(today);
        long totalThisWeek  = transferRepo.countSince(weekAgo);
        long totalThisMonth = transferRepo.countSince(monthAgo);

        // Top 5 articles
        List<TransferDashboardDTO.TopItemDTO> topItems =
                transferRepo.findTopTransferredItems(weekAgo, PageRequest.of(0, 5))
                        .stream()
                        .map(r -> new TransferDashboardDTO.TopItemDTO(
                                (String) r[0], (String) r[1], (Long) r[2]))
                        .collect(Collectors.toList());

        // Top 3 opérateurs
        List<TransferDashboardDTO.TopOperatorDTO> topOperators =
                transferRepo.findTopOperators(weekAgo, PageRequest.of(0, 3))
                        .stream()
                        .map(r -> new TransferDashboardDTO.TopOperatorDTO(
                                (String) r[0], (Long) r[1]))
                        .collect(Collectors.toList());

        // Top 3 emplacements sources
        List<TransferDashboardDTO.TopLocationDTO> topSourceLocations =
                transferRepo.findTopSourceLocations(weekAgo, PageRequest.of(0, 3))
                        .stream()
                        .map(r -> new TransferDashboardDTO.TopLocationDTO(
                                (String) r[0], (Long) r[1]))
                        .collect(Collectors.toList());

        return TransferDashboardDTO.builder()
                .countByStatus(countByStatus)
                .totalToday(totalToday)
                .totalThisWeek(totalThisWeek)
                .totalThisMonth(totalThisMonth)
                .topItems(topItems)
                .topOperators(topOperators)
                .topSourceLocations(topSourceLocations)
                .build();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // HELPERS PRIVÉS
    // ═════════════════════════════════════════════════════════════════════════

    private void validateTransferRequest(TransferRequestDTO req) {
        if (req.getSourceLocation().equalsIgnoreCase(req.getDestLocation())) {
            throw new IllegalArgumentException("Source et destination identiques");
        }
        if (req.getQuantity() <= 0) {
            throw new IllegalArgumentException("La quantité doit être > 0");
        }

        List<ErpStock> stocks = req.getLotNumber() != null
                ? erpStockRepo.findByItemCodeAndLocationAndLotNumber(
                        req.getErpItemCode(), req.getSourceLocation(), req.getLotNumber())
                .map(List::of).orElse(List.of())
                : erpStockRepo.findByItemCodeAndLocation(
                req.getErpItemCode(), req.getSourceLocation());

        int dispo = stocks.stream().mapToInt(ErpStock::getAvailableQuantityAsInt).sum();
        if (dispo < req.getQuantity()) {
            throw new IllegalArgumentException(String.format(
                    "Stock insuffisant dans %s : disponible=%d, demandé=%d",
                    req.getSourceLocation(), dispo, req.getQuantity()));
        }
    }

    private StockTransfer getTransferOrThrow(Long id) {
        return transferRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Transfert introuvable : " + id));
    }

    private User getCurrentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String username = principal instanceof UserDetails ud ? ud.getUsername() : principal.toString();
        return userRepo.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Utilisateur introuvable : " + username));
    }

    private boolean blank(String s) { return s == null || s.isBlank(); }
}