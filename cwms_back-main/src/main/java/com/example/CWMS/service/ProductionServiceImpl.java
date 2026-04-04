package com.example.CWMS.service;

import com.example.CWMS.dto.*;
import com.example.CWMS.iservice.IProductionService;
import com.example.CWMS.model.cwms.ProductionLog;
import com.example.CWMS.repository.cwms.ProductionLogRepository;
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

@Service
@Slf4j
@RequiredArgsConstructor
public class ProductionServiceImpl implements IProductionService {

    private final StockLotRepository    stockLotRepo;
    private final ProductionLogRepository logRepo;

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    // ── Vérification stock avant sortie ─────────────────────────────────────
    @Override
    public StockCheckDTO checkStock(String clot) {
        StockCheckDTO dto = new StockCheckDTO();
        dto.setLotCode(clot);

        Optional<Object[]> raw = stockLotRepo.findLotWithDesignation(clot);
        if (raw.isEmpty()) {
            dto.setFound(false);
            dto.setSufficient(false);
            return dto;
        }

        Object[] row = raw.get();
        dto.setFound(true);
        dto.setWarehouse (row[0] != null ? row[0].toString().trim() : "N/A");
        dto.setLocation  (row[1] != null ? row[1].toString().trim() : "N/A");
        dto.setItemCode  (row[2] != null ? row[2].toString().trim() : "N/A");
        dto.setLotCode   (row[3] != null ? row[3].toString().trim() : clot);
        dto.setQtyAvailable(row[4] != null ? ((Number) row[4]).doubleValue() : 0.0);
        dto.setUnit      ("UN"); // <-- Fixé à "UN" car t_cuni est supprimé
        dto.setDesignation(row[5] != null ? row[5].toString().trim() : ""); // <-- Index 5 maintenant
        dto.setSufficient(dto.getQtyAvailable() != null && dto.getQtyAvailable() > 0);
        return dto;
    }

    // ── Sortie Totale ────────────────────────────────────────────────────────
    @Override
    @Transactional
    public SortieResponseDTO sortieTotale(SortieRequestDTO req,
                                          Long userId, String userName) {
        SortieResponseDTO resp = new SortieResponseDTO();
        resp.setLotCode(req.getLotCode());
        resp.setOperationType("TOTALE");

        // 1. Lire stock actuel (snapshot avant modification)
        Optional<Double> currentQty = stockLotRepo.getQtyByLot(req.getLotCode());
        if (currentQty.isEmpty() || currentQty.get() <= 0) {
            return saveAndReturn(resp, req, userId, userName,
                    "Stock vide ou lot introuvable", 0.0, 0.0, false);
        }

        double qtyBefore = currentQty.get();

        // 2. UPDATE ERP atomique (condition AND t_qhnd > 0 protège la concurrence)
        int updated = stockLotRepo.sortieTotale(req.getLotCode());
        if (updated == 0) {
            return saveAndReturn(resp, req, userId, userName,
                    "Mise à jour ERP échouée (stock déjà modifié)", qtyBefore, qtyBefore, false);
        }

        // 3. Log CWMSDB
        return saveAndReturn(resp, req, userId, userName, null, qtyBefore, 0.0, true);
    }

    // ── Sortie Partielle ─────────────────────────────────────────────────────
    @Override
    @Transactional
    public SortieResponseDTO sortiePartielle(SortieRequestDTO req,
                                             Long userId, String userName) {
        SortieResponseDTO resp = new SortieResponseDTO();
        resp.setLotCode(req.getLotCode());
        resp.setOperationType("PARTIELLE");

        if (req.getQuantite() == null || req.getQuantite() <= 0) {
            resp.setSuccess(false);
            resp.setMessage("Quantité invalide");
            return resp;
        }

        double qty = req.getQuantite();

        // 1. Lire stock actuel
        Optional<Double> currentQty = stockLotRepo.getQtyByLot(req.getLotCode());
        if (currentQty.isEmpty()) {
            return saveAndReturn(resp, req, userId, userName,
                    "Lot introuvable : " + req.getLotCode(), 0.0, 0.0, false);
        }

        double qtyBefore = currentQty.get();

        // 2. Contrôle stock suffisant
        if (qty > qtyBefore) {
            resp.setSuccess(false);
            resp.setMessage(String.format(
                    "Stock insuffisant. Disponible : %.0f | Demandé : %.0f",
                    qtyBefore, qty));
            resp.setQtyBefore(qtyBefore);
            // Log de l'échec (audit complet)
            saveLog(req, userId, userName, qtyBefore, qty, qtyBefore,
                    ProductionLog.OperationType.PARTIELLE,
                    ProductionLog.OperationStatus.FAILED,
                    "Stock insuffisant: " + qtyBefore + " < " + qty);
            return resp;
        }

        // 3. UPDATE ERP atomique
        int updated = stockLotRepo.sortiePartielle(req.getLotCode(), qty);
        if (updated == 0) {
            return saveAndReturn(resp, req, userId, userName,
                    "Mise à jour ERP échouée (concurrence)", qtyBefore, qtyBefore, false);
        }

        double qtyAfter = qtyBefore - qty;
        return saveAndReturn(resp, req, userId, userName, null, qtyBefore, qtyAfter, true);
    }

    // ── Dashboard ────────────────────────────────────────────────────────────
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

    // ── Helpers privés ───────────────────────────────────────────────────────
    private SortieResponseDTO saveAndReturn(SortieResponseDTO resp,
                                            SortieRequestDTO req, Long userId, String userName,
                                            String errMsg, double qtyBefore, double qtyAfter, boolean success) {

        double qtySortie = qtyBefore - qtyAfter;
        ProductionLog.OperationType type = req.isSortieComplete()
                ? ProductionLog.OperationType.TOTALE
                : ProductionLog.OperationType.PARTIELLE;
        ProductionLog.OperationStatus st = success
                ? ProductionLog.OperationStatus.SUCCESS
                : ProductionLog.OperationStatus.FAILED;

        ProductionLog log = saveLog(req, userId, userName,
                qtyBefore, qtySortie, qtyAfter, type, st, errMsg);

        resp.setSuccess(success);
        resp.setMessage(success
                ? (req.isSortieComplete()
                ? "Sortie totale effectuée — lot vidé"
                : String.format("Sortie partielle effectuée. Restant : %.0f", qtyAfter))
                : errMsg);
        resp.setQtyBefore(qtyBefore);
        resp.setQtySortie(qtySortie);
        resp.setQtyAfter(qtyAfter);
        if (log != null) resp.setLogId(log.getId());
        return resp;
    }

    private ProductionLog saveLog(SortieRequestDTO req, Long userId,
                                  String userName, double qtyBefore, double qtyRequested,
                                  double qtyAfter, ProductionLog.OperationType type,
                                  ProductionLog.OperationStatus status, String errMsg) {
        try {
            String itemCode = "N/A", warehouse = "N/A", location = "N/A";

            // 1. Appel à la base ERP
            Optional<Object[]> raw = stockLotRepo.findLotWithDesignation(req.getLotCode());

            if (raw.isPresent()) {
                Object[] r = raw.get();
                // SECURITE : On vérifie la taille du tableau avant d'accéder aux index
                if (r.length >= 3) {
                    warehouse = r[0] != null ? r[0].toString().trim() : "N/A";
                    location  = r[1] != null ? r[1].toString().trim() : "N/A";
                    itemCode  = r[2] != null ? r[2].toString().trim() : "N/A";
                }
            }

            // 2. Construction du log (reste inchangé)
            ProductionLog log = ProductionLog.builder()
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
                    .build();

            // 3. Sauvegarde CRITIQUE
            return logRepo.save(log);

        } catch (Exception e) {
            // Affiche l'erreur réelle dans les logs pour débugger si ça échoue encore
            log.error("ERREUR CRITIQUE SAUVEGARDE LOG: ", e);
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
}