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

    @Qualifier("erpDataSource")
    private final DataSource erpDataSource;

    // ═════════════════════════════════════════════════════════════════════════
    // CRÉER UN TRANSFERT
    // ═════════════════════════════════════════════════════════════════════════

    @Override
    @Auditable(action = "CREATE_TRANSFER")
    @Transactional   // ← FIX : transaction CWMS explicite
    public TransferResponseDTO createTransfer(TransferRequestDTO request) {
        log.info("Création transfert : {} | {} → {} | qté={}",
                request.getErpItemCode(), request.getSourceLocation(),
                request.getDestLocation(), request.getQuantity());

        validateTransferRequest(request);

        ErpArticle article = erpArticleRepo.findByItemCode(request.getErpItemCode())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Article introuvable dans l'ERP : " + request.getErpItemCode()));

        User operator = getCurrentUser();

        String sourceWarehouse = request.getSourceLocation() != null
                ? request.getSourceLocation().split("/")[0].trim()
                : request.getSourceLocation();

        String destWarehouse = request.getDestLocation() != null
                ? request.getDestLocation().split("/")[0].trim()
                : request.getDestLocation();

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
                        ? request.getTransferType()
                        : StockTransfer.TransferType.INTERNAL_RELOCATION)
                .operator(operator)
                .notes(request.getNotes())
                .completedAt(LocalDateTime.now())
                .build();

        StockTransfer saved = transferRepo.save(transfer);
        log.info("Transfert #{} enregistré dans CWMSDB", saved.getId());

        updateErpStock(request);

        return TransferResponseDTO.from(saved);
    }

    private void updateErpStock(TransferRequestDTO request) {
        try (Connection conn = erpDataSource.getConnection();
             CallableStatement cs = conn.prepareCall(
                     "{CALL sp_cwms_transfert_stock(?, ?, ?, ?, ?)}")) {

            cs.setString(1, request.getErpItemCode());
            cs.setString(2, request.getSourceLocation());
            cs.setString(3, request.getDestLocation());
            cs.setString(4, request.getLotNumber());
            cs.setInt(5,    request.getQuantity());
            cs.execute();

            log.info("✅ ERP stock mis à jour via SP pour article {} | {} → {}",
                    request.getErpItemCode(), request.getSourceLocation(), request.getDestLocation());

        } catch (Exception e) {
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
    // CONSULTATION — FIX : @Transactional ajouté sur getAll() et search()
    // ═════════════════════════════════════════════════════════════════════════

    @Override
    public TransferResponseDTO getById(Long id) {
        return TransferResponseDTO.from(getTransferOrThrow(id));
    }

    /**
     * FIX : @Transactional(readOnly=true) ajouté.
     * Sans ça, Hibernate peut ouvrir une session sans contexte de transaction
     * quand le TransactionManager CWMS n'est pas le primary → NullPointerException
     * silencieux qui bloque le chargement côté Angular.
     */
    @Override
    @Transactional(readOnly = true)
    public Page<TransferResponseDTO> getAll(Pageable pageable) {
        return transferRepo.findAll(pageable).map(TransferResponseDTO::from);
    }

    /**
     * FIX idem : @Transactional(readOnly=true) + logs pour diagnostiquer.
     */
    @Override
    @Transactional(readOnly = true)
    public Page<TransferResponseDTO> search(String status, String itemCode, String location,
                                            LocalDateTime from, LocalDateTime to, Pageable pageable) {
        String s = blank(status)   ? null : status.trim();
        String i = blank(itemCode) ? null : itemCode.trim();
        String l = blank(location) ? null : location.trim();

        log.debug("search() → status={} itemCode={} location={} from={} to={} page={}",
                s, i, l, from, to, pageable.getPageNumber());

        Page<TransferResponseDTO> result = transferRepo.search(s, i, l, from, to, pageable)
                .map(TransferResponseDTO::from);

        log.debug("search() → {} résultats (total={})",
                result.getNumberOfElements(), result.getTotalElements());

        return result;
    }

    @Override
    public List<TransferResponseDTO> getMyTransfers(Integer operatorId) {
        return transferRepo
                .findByOperator_UserIdOrderByCreatedAtDesc(operatorId, PageRequest.of(0, 50))
                .stream().map(TransferResponseDTO::from).collect(Collectors.toList());
    }

    // ═════════════════════════════════════════════════════════════════════════
    // ERP — STOCK PAR LOT + EMPLACEMENT
    // ═════════════════════════════════════════════════════════════════════════

    @Override
    @Transactional(transactionManager = "erpTransactionManager", readOnly = true)
    public List<ErpStockDTO> getStockByLot(String lotNumber) {
        List<ErpStock> stocks = erpStockRepo.findByLotNumber(lotNumber);

        if (stocks.isEmpty()) {
            throw new NoSuchElementException("Lot introuvable dans l'ERP : " + lotNumber);
        }

        return stocks.stream().map(s -> {
            String label = erpArticleRepo.findByItemCode(s.getItemCode().trim())
                    .map(ErpArticle::getDesignation)
                    .orElse(s.getItemCode().trim());

            return ErpStockDTO.builder()
                    .id(s.getIdStockage())
                    .itemCode(s.getItemCode() != null ? s.getItemCode().trim() : null)
                    .itemLabel(label)
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
        List<ErpStock> stocks = erpStockRepo.findByLocation(locationCode);

        String warehouseCode = stocks.isEmpty()
                ? locationCode
                : stocks.get(0).getWarehouseCode();

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
    // ERP — ARTICLES & STOCK
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
    // DASHBOARD
    // ═════════════════════════════════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)   // ← FIX : ajouté
    public TransferDashboardDTO getDashboard() {
        Map<String, Long> countByStatus = new HashMap<>();
        transferRepo.countByStatusGrouped()
                .forEach(row -> countByStatus.put((String) row[0], (Long) row[1]));

        LocalDateTime today    = LocalDateTime.now().toLocalDate().atStartOfDay();
        LocalDateTime weekAgo  = LocalDateTime.now().minusDays(7);
        LocalDateTime monthAgo = LocalDateTime.now().minusDays(30);

        long totalToday     = transferRepo.countSince(today);
        long totalThisWeek  = transferRepo.countSince(weekAgo);
        long totalThisMonth = transferRepo.countSince(monthAgo);

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
        String username = principal instanceof UserDetails ud
                ? ud.getUsername()
                : principal.toString();
        return userRepo.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Utilisateur introuvable : " + username));
    }

    private boolean blank(String s) { return s == null || s.isBlank(); }
}