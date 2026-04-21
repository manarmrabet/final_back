package com.example.CWMS.service;

import com.example.CWMS.dto.*;
import com.example.CWMS.model.cwms.StockTransfer;
import com.example.CWMS.model.erp.ErpArticle;
import com.example.CWMS.model.erp.ErpStock;
import com.example.CWMS.model.cwms.User;
import com.example.CWMS.repository.cwms.StockTransferRepository;
import com.example.CWMS.repository.erp.ErpArticleRepository;
import com.example.CWMS.repository.erp.ErpLocationRepository;
import com.example.CWMS.repository.erp.ErpStockRepository;
import com.example.CWMS.repository.cwms.UserRepository;
import com.example.CWMS.iservice.TransferService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransferServiceImpl implements TransferService {

    private final StockTransferRepository  transferRepo;
    private final ErpStockRepository       erpStockRepo;
    private final ErpArticleRepository     erpArticleRepo;
    private final ErpLocationRepository    erpLocationRepo;
    private final UserRepository           userRepo;
    private final ErpStockUpdater          erpStockUpdater;

    // ══════════════════════════════════════════════════════════════════════════
    // ERP — MAGASINS ✅ AJOUTÉ
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Retourne la liste distincte des codes magasin (t_cwar) depuis dbo_twhinr1401200.
     * Endpoint : GET /api/transfers/erp/warehouses
     */
    @Override
    @Transactional(transactionManager = "erpTransactionManager", readOnly = true)
    public List<String> getDistinctWarehouses() {
        return erpStockRepo.findDistinctWarehouses();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // CRÉATION DE TRANSFERT
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    @Transactional
    public TransferResponseDTO createTransfer(TransferRequestDTO request) {
        ErpArticle article = erpArticleRepo.findByItemCode(request.getErpItemCode())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Article introuvable dans l'ERP : " + request.getErpItemCode()));
        validateTransferRequest(request, article);
        User operator = getCurrentUser();
        return createTransferWithUser(request, operator, article);
    }

    @Override
    public List<TransferResponseDTO> createTransferBatch(List<TransferRequestDTO> requests) {
        log.info("Batch transfert : {} lignes", requests.size());
        User operator = getCurrentUser();
        List<TransferResponseDTO> results = new ArrayList<>();

        for (TransferRequestDTO request : requests) {
            try {
                ErpArticle article = erpArticleRepo.findByItemCode(request.getErpItemCode())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Article introuvable : " + request.getErpItemCode()));
                validateTransferRequest(request, article);
                results.add(createTransferWithUser(request, operator, article));
            } catch (Exception e) {
                log.error("Erreur batch article={} lot={} : {}",
                        request.getErpItemCode(), request.getLotNumber(), e.getMessage());
                results.add(TransferResponseDTO.builder()
                        .erpItemCode(request.getErpItemCode())
                        .lotNumber(request.getLotNumber())
                        .sourceLocation(request.getSourceLocation())
                        .destLocation(request.getDestLocation())
                        .quantity(request.getQuantity())
                        .status(StockTransfer.TransferStatus.ERROR.name())
                        .errorMessage(e.getMessage())
                        .createdAt(LocalDateTime.now())
                        .build());
            }
        }

        long success = results.stream()
                .filter(r -> StockTransfer.TransferStatus.DONE.name().equals(r.getStatus()))
                .count();
        log.info("Batch terminé : {}/{} succès", success, requests.size());
        return results;
    }

    @Transactional
    protected TransferResponseDTO createTransferWithUser(TransferRequestDTO request,
                                                         User operator,
                                                         ErpArticle article) {
        String sourceWarehouse = resolveSourceWarehouse(request);
        String destWarehouse   = resolveDestWarehouse(request, sourceWarehouse);

        StockTransfer transfer = StockTransfer.builder()
                .erpItemCode(request.getErpItemCode())
                .erpItemLabel(article.getDesignation())
                .lotNumber(request.getLotNumber())
                .sourceLocation(request.getSourceLocation())
                .destLocation(request.getDestLocation())
                .sourceWarehouse(sourceWarehouse)
                .destWarehouse(destWarehouse)
                .quantity(request.getQuantity())
                .unit(article.getStockUnit())
                .status(StockTransfer.TransferStatus.DONE)
                .transferType(request.getTransferType() != null
                        ? StockTransfer.TransferType.valueOf(request.getTransferType())
                        : StockTransfer.TransferType.INTERNAL_RELOCATION)
                .operator(operator)
                .notes(request.getNotes())
                .completedAt(LocalDateTime.now())
                .build();

        StockTransfer saved = transferRepo.save(transfer);
        log.info("Transfert #{} enregistré CWMSDB", saved.getId());

        try {
            boolean erpOk = erpStockUpdater.moveLot(request);
            if (!erpOk) {
                log.warn("Transfert #{} : lot non trouvé ERP", saved.getId());
            }
        } catch (Exception e) {
            log.error("Transfert #{} : erreur ERP non bloquante → {}", saved.getId(), e.getMessage(), e);
        }

        return TransferResponseDTO.from(saved);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // VALIDATION / ANNULATION
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    @Transactional
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
    public TransferResponseDTO cancelTransfer(Long transferId, String reason) {
        StockTransfer t = getTransferOrThrow(transferId);
        if (StockTransfer.TransferStatus.DONE.equals(t.getStatus())) {
            throw new IllegalStateException("Impossible d'annuler un transfert DONE");
        }
        User validator = getCurrentUser();
        t.setStatus(StockTransfer.TransferStatus.CANCELLED);
        t.setErrorMessage(reason);
        t.setValidatedBy(validator);
        t.setValidatedAt(LocalDateTime.now());
        return TransferResponseDTO.from(transferRepo.save(t));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // CONSULTATION
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    public TransferResponseDTO getById(Long id) {
        return TransferResponseDTO.from(getTransferOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TransferResponseDTO> getAll(Pageable pageable) {
        return transferRepo.findAll(pageable).map(TransferResponseDTO::from);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TransferResponseDTO> search(String status, String itemCode, String location,String operator,
                                            LocalDateTime from, LocalDateTime to, Pageable pageable) {
        String s = blank(status)   ? null : status.trim();
        String i = blank(itemCode) ? null : itemCode.trim();
        String l = blank(location) ? null : location.trim();
        String op = blank(operator) ? null : operator.trim();
        return transferRepo.search(s, i, l,op, from, to, pageable).map(TransferResponseDTO::from);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TransferResponseDTO> getMyTransfers(Integer operatorId, Pageable pageable) {
        return transferRepo.findByOperator_UserIdOrderByCreatedAtDesc(operatorId, pageable)
                .map(TransferResponseDTO::from);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ERP — EMPLACEMENT ✅ CORRIGÉ
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Récupère les informations d'un emplacement depuis dbo_twhwmd300310.
     *
     * CORRECTION : utilise findFirstByLocationCode() au lieu de
     * findAllByWarehouseCode() qui cherchait par warehouse (pas par location).
     */
    @Override
    @Transactional(transactionManager = "erpTransactionManager", readOnly = true)
    public ErpLocationDTO getLocationInfo(String locationCode) {
        return erpLocationRepo.findFirstByLocationCode(locationCode.trim())
                .map(loc -> ErpLocationDTO.builder()
                        .locationCode(loc.getLocationCode())
                        .warehouseCode(loc.getWarehouseCode())
                        .description(loc.getDescription())
                        .zone(loc.getZone())
                        .locationType(loc.getLocationType())
                        .active(loc.isActive())
                        .exists(true)
                        .build())
                .orElseGet(() -> {
                    // Emplacement absent de twhwmd300310 — fallback via stock ERP
                    List<ErpStock> stocks = erpStockRepo.findByLocation(locationCode.trim());
                    String warehouseCode = stocks.isEmpty()
                            ? null
                            : stocks.get(0).getWarehouseCode();
                    log.debug("Location '{}' absente de twhwmd300 — fallback stock ERP : wh={}",
                            locationCode, warehouseCode);
                    return ErpLocationDTO.builder()
                            .locationCode(locationCode)
                            .warehouseCode(warehouseCode)
                            .active(false)
                            .exists(false)
                            .build();
                });
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ERP — ARTICLES & STOCK
    // ══════════════════════════════════════════════════════════════════════════

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
                .map(ErpArticleDTO::from)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(transactionManager = "erpTransactionManager", readOnly = true)
    public List<ErpStockDTO> getStockByItem(String itemCode) {
        return erpStockRepo.findByItemCode(itemCode).stream()
                .map(ErpStockDTO::from)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(transactionManager = "erpTransactionManager", readOnly = true)
    public List<ErpStockDTO> getStockByLocation(String location) {
        return erpStockRepo.findByLocation(location).stream()
                .map(ErpStockDTO::from)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(transactionManager = "erpTransactionManager", readOnly = true)
    public List<ErpStockDTO> getStockByLot(String lotNumber) {
        List<ErpStock> stocks = erpStockRepo.findByLotNumber(lotNumber);
        if (stocks.isEmpty()) {
            throw new NoSuchElementException("Lot introuvable dans l'ERP : " + lotNumber);
        }

        Set<String> codes = stocks.stream()
                .map(s -> s.getItemCode().trim())
                .collect(Collectors.toSet());

        Map<String, String> labelsByCode = erpArticleRepo.findAllByItemCodeIn(codes)
                .stream()
                .collect(Collectors.toMap(
                        a -> a.getItemCode().trim(),
                        ErpArticle::getDesignation,
                        (a, b) -> a));

        return stocks.stream().map(s -> {
            String code  = s.getItemCode() != null ? s.getItemCode().trim() : null;
            String label = code != null ? labelsByCode.getOrDefault(code, code) : null;
            return ErpStockDTO.builder()
                    .id(s.getIdStockage())
                    .itemCode(code)
                    .itemLabel(label)
                    .location(s.getLocation())
                    .lotNumber(s.getLotNumber())
                    .quantityAvailable(s.getAvailableQuantityAsInt())
                    .warehouseCode(s.getWarehouseCode())
                    .entryDate(s.getEntryDate())
                    .lastTransactionDate(s.getLastTransactionDate())
                    .lineStatus(s.getLineStatusSafe())
                    .build();
        }).collect(Collectors.toList());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DASHBOARD
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public TransferDashboardDTO getDashboard() {
        Map<String, Long> countByStatus = new HashMap<>();
        transferRepo.countByStatusGrouped()
                .forEach(row -> countByStatus.put(row[0].toString(), (Long) row[1]));

        LocalDateTime today    = LocalDateTime.now().toLocalDate().atStartOfDay();
        LocalDateTime weekAgo  = LocalDateTime.now().minusDays(7);
        LocalDateTime monthAgo = LocalDateTime.now().minusDays(30);

        List<TransferDashboardDTO.TopItemDTO> topItems =
                transferRepo.findTopTransferredItems(weekAgo, PageRequest.of(0, 5))
                        .stream()
                        .map(r -> new TransferDashboardDTO.TopItemDTO(
                                (String) r[0], (String) r[1], (Long) r[2]))
                        .collect(Collectors.toList());

        List<TransferDashboardDTO.TopOperatorDTO> topOperators =
                transferRepo.findTopOperators(weekAgo, PageRequest.of(0, 3))
                        .stream()
                        .map(r -> new TransferDashboardDTO.TopOperatorDTO(
                                (String) r[0], (Long) r[1]))
                        .collect(Collectors.toList());

        List<TransferDashboardDTO.TopLocationDTO> topSourceLocations =
                transferRepo.findTopSourceLocations(weekAgo, PageRequest.of(0, 3))
                        .stream()
                        .map(r -> new TransferDashboardDTO.TopLocationDTO(
                                (String) r[0], (Long) r[1]))
                        .collect(Collectors.toList());

        return TransferDashboardDTO.builder()
                .countByStatus(countByStatus)
                .totalToday(transferRepo.countSince(today))
                .totalThisWeek(transferRepo.countSince(weekAgo))
                .totalThisMonth(transferRepo.countSince(monthAgo))
                .topItems(topItems)
                .topOperators(topOperators)
                .topSourceLocations(topSourceLocations)
                .build();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // HELPERS PRIVÉS
    // ══════════════════════════════════════════════════════════════════════════

    private void validateTransferRequest(TransferRequestDTO req, ErpArticle article) {
        if (req.getSourceLocation().equalsIgnoreCase(req.getDestLocation())) {
            throw new IllegalArgumentException("Source et destination identiques");
        }
        if (req.getQuantity() == null || req.getQuantity() <= 0) {
            throw new IllegalArgumentException("La quantité doit être > 0");
        }
        List<ErpStock> stocks = req.getLotNumber() != null
                ? erpStockRepo.findByItemCodeAndLocationAndLotNumber(
                        req.getErpItemCode(), req.getSourceLocation(), req.getLotNumber())
                .map(List::of).orElse(List.of())
                : erpStockRepo.findByItemCodeAndLocation(
                req.getErpItemCode(), req.getSourceLocation());

        if (stocks.isEmpty()) {
            throw new IllegalArgumentException(String.format(
                    "Lot introuvable dans l'ERP : article=%s lot=%s source=%s",
                    req.getErpItemCode(), req.getLotNumber(), req.getSourceLocation()));
        }
    }

    private String resolveSourceWarehouse(TransferRequestDTO request) {
        if (request.getSourceWarehouse() != null && !request.getSourceWarehouse().isBlank()) {
            return request.getSourceWarehouse().trim();
        }
        return erpStockRepo.findByItemCodeAndLocation(
                        request.getErpItemCode(), request.getSourceLocation())
                .stream().findFirst()
                .map(ErpStock::getWarehouseCode)
                .orElse(null);
    }

    private String resolveDestWarehouse(TransferRequestDTO request, String sourceWarehouse) {
        if (request.getDestWarehouse() != null && !request.getDestWarehouse().isBlank()
                && !request.getDestWarehouse().equalsIgnoreCase(request.getDestLocation())) {
            return request.getDestWarehouse().trim();
        }
        return sourceWarehouse;
    }

    private StockTransfer getTransferOrThrow(Long id) {
        return transferRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Transfert introuvable : " + id));
    }

    private User getCurrentUser() {
        Object principal = SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        String username = principal instanceof UserDetails ud
                ? ud.getUsername() : principal.toString();
        return userRepo.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Utilisateur introuvable : " + username));
    }

    private boolean blank(String s) {
        return s == null || s.isBlank();
    }
}