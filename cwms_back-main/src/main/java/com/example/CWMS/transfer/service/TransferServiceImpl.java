package com.example.CWMS.transfer.service;

import com.example.CWMS.audit.Auditable;
import com.example.CWMS.erp.entity.ErpArticle;
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

/**
 * Implémentation du service de transfert interne.
 *
 * ⚠️ Architecture dual-datasource :
 *   - Méthodes lisant ERP     → @Transactional("erpTransactionManager")
 *   - Méthodes écrivant CWMS  → @Transactional("cwmsTransactionManager") [défaut = @Primary]
 *   - Méthodes mixtes         → PAS de @Transactional global (géré localement)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransferServiceImpl implements TransferService {

    // ─── Repositories ERP (lecture seule) ────────────────────────────────────
    private final ErpArticleRepository      erpArticleRepo;
    private final ErpStockRepository        erpStockRepo;
    private final ErpEmplacementRepository  erpEmplacementRepo;

    // ─── Repositories CWMS (lecture/écriture) ────────────────────────────────
    private final StockTransferRepository   transferRepo;
    private final UserRepository            userRepo;

    // ═════════════════════════════════════════════════════════════════════════
    // CRÉER UN TRANSFERT
    // ═════════════════════════════════════════════════════════════════════════

    @Override
    @Auditable(action = "CREATE_TRANSFER")
    public TransferResponseDTO createTransfer(TransferRequestDTO request) {
        log.info("Création transfert : {} | {} → {} | qté={}",
                request.getErpItemCode(), request.getSourceLocation(),
                request.getDestLocation(), request.getQuantity());

        // 1. Validation métier
        validateTransferRequest(request);

        // 2. Enrichissement depuis ERP (lecture)
        ErpArticle article = erpArticleRepo.findByItemCode(request.getErpItemCode())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Article introuvable dans l'ERP : " + request.getErpItemCode()));

        // 3. Opérateur connecté
        User operator = getCurrentUser();

        // 4. Construction et persistance dans CWMSDB
        StockTransfer transfer = StockTransfer.builder()
                .erpItemCode(request.getErpItemCode())
                .erpItemLabel(article.getDesignation())
                .lotNumber(request.getLotNumber())
                .sourceLocation(request.getSourceLocation())
                .destLocation(request.getDestLocation())
                .quantity(request.getQuantity())
                .unit(article.getPurchaseUnit())
                .status(StockTransfer.TransferStatus.DONE) // Validation auto pour opérateur mobile
                .transferType(request.getTransferType() != null
                        ? request.getTransferType()
                        : StockTransfer.TransferType.INTERNAL_RELOCATION)
                .operator(operator)
                .notes(request.getNotes())
                .completedAt(LocalDateTime.now())
                .build();

        StockTransfer saved = transferRepo.save(transfer);
        log.info("Transfert #{} enregistré avec succès", saved.getId());

        return TransferResponseDTO.from(saved);
    }

    @Override
    public List<TransferResponseDTO> createTransferBatch(List<TransferRequestDTO> requests) {
        log.info("Batch transfert : {} lignes à traiter", requests.size());

        List<TransferResponseDTO> results = new ArrayList<>();

        for (TransferRequestDTO request : requests) {
            try {
                TransferResponseDTO dto = createTransfer(request);
                results.add(dto);
            } catch (Exception e) {
                log.error("Erreur transfert batch pour article {} : {}",
                        request.getErpItemCode(), e.getMessage());

                // On construit une réponse d'erreur sans arrêter le batch
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
    // VALIDATION / ANNULATION (Superviseur Web)
    // ═════════════════════════════════════════════════════════════════════════

    @Override
    @Transactional
    @Auditable(action = "VALIDATE_TRANSFER")
    public TransferResponseDTO validateTransfer(Long transferId) {
        StockTransfer transfer = getTransferOrThrow(transferId);

        if (!StockTransfer.TransferStatus.PENDING.equals(transfer.getStatus())) {
            throw new IllegalStateException(
                    "Seul un transfert PENDING peut être validé. Statut actuel : " + transfer.getStatus());
        }

        User validator = getCurrentUser();
        transfer.setStatus(StockTransfer.TransferStatus.DONE);
        transfer.setValidatedBy(validator);
        transfer.setValidatedAt(LocalDateTime.now());
        transfer.setCompletedAt(LocalDateTime.now());

        return TransferResponseDTO.from(transferRepo.save(transfer));
    }

    @Override
    @Transactional
    @Auditable(action = "CANCEL_TRANSFER")
    public TransferResponseDTO cancelTransfer(Long transferId, String reason) {
        StockTransfer transfer = getTransferOrThrow(transferId);

        if (StockTransfer.TransferStatus.DONE.equals(transfer.getStatus())) {
            throw new IllegalStateException("Impossible d'annuler un transfert déjà DONE");
        }

        transfer.setStatus(StockTransfer.TransferStatus.CANCELLED);
        transfer.setErrorMessage(reason);
        transfer.setValidatedBy(getCurrentUser());
        transfer.setValidatedAt(LocalDateTime.now());

        return TransferResponseDTO.from(transferRepo.save(transfer));
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
        return transferRepo.search(status, itemCode, location, from, to, pageable)
                .map(TransferResponseDTO::from);
    }

    @Override
    public List<TransferResponseDTO> getMyTransfers(Integer operatorId) {
        return transferRepo.findByOperator_UserIdOrderByCreatedAtDesc(operatorId, PageRequest.of(0, 50))
                .stream().map(TransferResponseDTO::from).collect(Collectors.toList());
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

    // ═════════════════════════════════════════════════════════════════════════
    // DASHBOARD
    // ═════════════════════════════════════════════════════════════════════════

    @Override
    public TransferDashboardDTO getDashboard() {
        // Comptage par statut
        Map<String, Long> countByStatus = new HashMap<>();
        transferRepo.countByStatusGrouped().forEach(row ->
                countByStatus.put((String) row[0], (Long) row[1]));

        // Totaux par période
        LocalDateTime today    = LocalDateTime.now().toLocalDate().atStartOfDay();
        LocalDateTime weekAgo  = LocalDateTime.now().minusDays(7);
        long totalToday    = transferRepo.findRecentTransfers(today).size();
        long totalThisWeek = transferRepo.findRecentTransfers(weekAgo).size();

        // Top articles (7 derniers jours)
        List<TransferDashboardDTO.TopItemDTO> topItems =
                transferRepo.findTopTransferredItems(weekAgo, PageRequest.of(0, 5))
                        .stream()
                        .map(row -> new TransferDashboardDTO.TopItemDTO(
                                (String) row[0], (String) row[1], (Long) row[2]))
                        .collect(Collectors.toList());

        return TransferDashboardDTO.builder()
                .countByStatus(countByStatus)
                .totalToday(totalToday)
                .totalThisWeek(totalThisWeek)
                .topItems(topItems)
                .build();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // HELPERS PRIVÉS
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Validation métier complète avant tout transfert.
     * Lit l'ERP pour vérifier stock et emplacements.
     */
    private void validateTransferRequest(TransferRequestDTO req) {
        // Emplacement source ≠ destination
        if (req.getSourceLocation().equalsIgnoreCase(req.getDestLocation())) {
            throw new IllegalArgumentException(
                    "L'emplacement source et destination ne peuvent pas être identiques");
        }

        // Quantité positive
        if (req.getQuantity() <= 0) {
            throw new IllegalArgumentException("La quantité doit être supérieure à 0");
        }

        // Vérifier stock disponible dans l'ERP
        List<ErpStock> stocks = req.getLotNumber() != null
                ? erpStockRepo.findByItemCodeAndLocationAndLotNumber(
                        req.getErpItemCode(), req.getSourceLocation(), req.getLotNumber())
                .map(List::of).orElse(List.of())
                : erpStockRepo.findByItemCodeAndLocation(
                req.getErpItemCode(), req.getSourceLocation());

        int totalAvailable = stocks.stream()
                .mapToInt(ErpStock::getAvailableQuantityAsInt)
                .sum();

        if (totalAvailable < req.getQuantity()) {
            throw new IllegalArgumentException(String.format(
                    "Stock insuffisant. Disponible dans %s : %d | Demandé : %d",
                    req.getSourceLocation(), totalAvailable, req.getQuantity()));
        }
    }

    private StockTransfer getTransferOrThrow(Long id) {
        return transferRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Transfert introuvable : " + id));
    }

    /**
     * Récupère l'utilisateur connecté depuis le SecurityContext.
     * Utilisé pour enregistrer l'opérateur du transfert.
     */
    private User getCurrentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String username = (principal instanceof UserDetails ud)
                ? ud.getUsername()
                : principal.toString();

        return userRepo.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Utilisateur connecté introuvable : " + username));
    }
}