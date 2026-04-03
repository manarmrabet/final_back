package com.example.CWMS.service;

import com.example.CWMS.audit.Auditable;
import com.example.CWMS.dto.TransferDashboardDTO;
import com.example.CWMS.dto.TransferRequestDTO;
import com.example.CWMS.dto.TransferResponseDTO;
import com.example.CWMS.dto.ErpArticleDTO;
import com.example.CWMS.dto.ErpLocationDTO;
import com.example.CWMS.dto.ErpStockDTO;
import com.example.CWMS.model.erp.ErpArticle;
import com.example.CWMS.model.erp.ErpEmplacement;
import com.example.CWMS.model.erp.ErpStock;
import com.example.CWMS.repository.erp.ErpArticleRepository;
import com.example.CWMS.repository.erp.ErpEmplacementRepository;
import com.example.CWMS.repository.erp.ErpStockRepository;
import com.example.CWMS.iservice.TransferService;
import com.example.CWMS.model.cwms.User;
import com.example.CWMS.repository.cwms.UserRepository;
import com.example.CWMS.model.cwms.StockTransfer;
import com.example.CWMS.repository.cwms.StockTransferRepository;
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

    // ─── ERP (lecture) ────────────────────────────────────────────────────
    private final ErpArticleRepository     erpArticleRepo;
    private final ErpStockRepository       erpStockRepo;
    private final ErpEmplacementRepository erpEmplacementRepo;

    // ─── CWMS (lecture/écriture) ──────────────────────────────────────────
    private final StockTransferRepository  transferRepo;
    private final UserRepository           userRepo;

    // ─── ERP (écriture) ───────────────────────────────────────────────────
    private final ErpStockUpdater          erpStockUpdater;

    // ═════════════════════════════════════════════════════════════════════════
    // CRÉER UN TRANSFERT
    // ═════════════════════════════════════════════════════════════════════════

    @Override
    @Auditable(action = "CREATE_TRANSFER")
    @Transactional
    public TransferResponseDTO createTransfer(TransferRequestDTO request) {
        log.info("Création transfert : {} | lot={} | {} → {}",
                request.getErpItemCode(), request.getLotNumber(),
                request.getSourceLocation(), request.getDestLocation());

        // ✅ CORRECTION 1 : article chargé UNE SEULE FOIS ici
        // puis passé en paramètre à validateTransferRequest()
        // → supprime la 2ème requête ERP qui était dans validateTransferRequest()
        ErpArticle article = erpArticleRepo.findByItemCode(request.getErpItemCode())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Article introuvable dans l'ERP : " + request.getErpItemCode()));

        validateTransferRequest(request, article);

        // ✅ CORRECTION 2 : getCurrentUser() appelé UNE SEULE FOIS
        // et stocké dans une variable locale — pas de nouvelle requête SQL
        User operator = getCurrentUser();

        String sourceWarehouse = extractWarehouse(request.getSourceLocation());
        String destWarehouse   = extractWarehouse(request.getDestLocation());

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
                // ✅ CORRECTION 3 : enum typé au lieu de String
                .status(StockTransfer.TransferStatus.DONE)
                .transferType(request.getTransferType() != null
                        ? StockTransfer.TransferType.valueOf(request.getTransferType())
                        : StockTransfer.TransferType.INTERNAL_RELOCATION)
                .operator(operator)
                .notes(request.getNotes())
                .completedAt(LocalDateTime.now())
                .build();

        StockTransfer saved = transferRepo.save(transfer);
        log.info("Transfert #{} enregistré dans CWMSDB", saved.getId());

        // ── Mise à jour ERP (non bloquante) ──────────────────────────────
        try {
            boolean erpOk = erpStockUpdater.moveLot(request);
            if (!erpOk) {
                log.warn("Transfert #{} : lot non trouvé dans l'ERP au moment du déplacement",
                        saved.getId());
            }
        } catch (Exception e) {
            log.error("Transfert #{} : erreur ERP non bloquante → {}",
                    saved.getId(), e.getMessage(), e);
        }

        return TransferResponseDTO.from(saved);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // BATCH
    // ═════════════════════════════════════════════════════════════════════════

    @Override
    public List<TransferResponseDTO> createTransferBatch(List<TransferRequestDTO> requests) {
        log.info("Batch transfert : {} lignes", requests.size());

        // ✅ CORRECTION 2 (batch) : getCurrentUser() appelé UNE SEULE FOIS
        // pour tout le batch — évite N requêtes SQL inutiles
        User operator = getCurrentUser();

        List<TransferResponseDTO> results = new ArrayList<>();

        for (TransferRequestDTO request : requests) {
            try {
                results.add(createTransferWithUser(request, operator));
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

    /**
     * Version interne de createTransfer qui accepte un User déjà chargé.
     * Utilisée par le batch pour éviter une requête SQL par ligne.
     */
    @Transactional
    private TransferResponseDTO createTransferWithUser(TransferRequestDTO request, User operator) {
        ErpArticle article = erpArticleRepo.findByItemCode(request.getErpItemCode())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Article introuvable dans l'ERP : " + request.getErpItemCode()));

        validateTransferRequest(request, article);

        StockTransfer transfer = StockTransfer.builder()
                .erpItemCode(request.getErpItemCode())
                .erpItemLabel(article.getDesignation())
                .lotNumber(request.getLotNumber())
                .sourceLocation(request.getSourceLocation())
                .destLocation(request.getDestLocation())
                .sourceWarehouse(extractWarehouse(request.getSourceLocation()))
                .destWarehouse(extractWarehouse(request.getDestLocation()))
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

        try {
            erpStockUpdater.moveLot(request);
        } catch (Exception e) {
            log.error("Batch transfert #{} : erreur ERP → {}", saved.getId(), e.getMessage());
        }

        return TransferResponseDTO.from(saved);
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
            throw new IllegalStateException(
                    "Seul un PENDING peut être validé. Statut : " + t.getStatus());
        }
        // ✅ getCurrentUser() appelé une fois, résultat réutilisé
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
        User validator = getCurrentUser();
        t.setStatus(StockTransfer.TransferStatus.CANCELLED);
        t.setErrorMessage(reason);
        t.setValidatedBy(validator);
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
    @Transactional(readOnly = true)
    public Page<TransferResponseDTO> getAll(Pageable pageable) {
        return transferRepo.findAll(pageable).map(TransferResponseDTO::from);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TransferResponseDTO> search(String status, String itemCode, String location,
                                            LocalDateTime from, LocalDateTime to,
                                            Pageable pageable) {
        String s = blank(status)   ? null : status.trim();
        String i = blank(itemCode) ? null : itemCode.trim();
        String l = blank(location) ? null : location.trim();

        log.debug("search() → status={} itemCode={} location={} from={} to={} page={}",
                s, i, l, from, to, pageable.getPageNumber());

        return transferRepo.search(s, i, l, from, to, pageable)
                .map(TransferResponseDTO::from);
    }

    /**
     * ✅ CORRECTION : vraie pagination — le controller contrôle page et size.
     * Plus de PageRequest(0, 50) caché dans le service.
     */
    @Override
    @Transactional(readOnly = true)
    public Page<TransferResponseDTO> getMyTransfers(Integer operatorId, Pageable pageable) {
        return transferRepo
                .findByOperator_UserIdOrderByCreatedAtDesc(operatorId, pageable)
                .map(TransferResponseDTO::from);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // ERP — STOCK PAR LOT
    // ═════════════════════════════════════════════════════════════════════════

    @Override
    @Transactional(transactionManager = "erpTransactionManager", readOnly = true)
    public List<ErpStockDTO> getStockByLot(String lotNumber) {
        List<ErpStock> stocks = erpStockRepo.findByLotNumber(lotNumber);
        if (stocks.isEmpty()) {
            throw new NoSuchElementException("Lot introuvable dans l'ERP : " + lotNumber);
        }

        // ✅ CORRECTION : 1 seule requête SQL pour tous les labels
        // Avant : 1 requête findByItemCode() par ligne de stock (N requêtes)
        // Après : 1 requête findAllByItemCodeIn() pour tous les codes
        Set<String> codes = stocks.stream()
                .map(s -> s.getItemCode().trim())
                .collect(Collectors.toSet());

        Map<String, String> labelsByCode = erpArticleRepo.findAllByItemCodeIn(codes)
                .stream()
                .collect(Collectors.toMap(
                        a -> a.getItemCode().trim(),
                        ErpArticle::getDesignation
                ));

        return stocks.stream().map(s -> {
            String code  = s.getItemCode() != null ? s.getItemCode().trim() : null;
            String label = code != null
                    ? labelsByCode.getOrDefault(code, code)
                    : null;
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
                    .lineStatus(s.getLineStatus())
                    .build();
        }).collect(Collectors.toList());
    }

    // ═════════════════════════════════════════════════════════════════════════
    // ERP — EMPLACEMENT
    // ═════════════════════════════════════════════════════════════════════════

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
    @Transactional(readOnly = true)
    public TransferDashboardDTO getDashboard() {
        Map<String, Long> countByStatus = new HashMap<>();
        transferRepo.countByStatusGrouped()
                .forEach(row -> countByStatus.put(row[0].toString(), (Long) row[1]));

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

    /**
     * ✅ CORRECTION : article déjà chargé passé en paramètre
     * → supprime la requête ERP qui était dans cette méthode
     */
    private void validateTransferRequest(TransferRequestDTO req, ErpArticle article) {
        if (req.getSourceLocation().equalsIgnoreCase(req.getDestLocation())) {
            throw new IllegalArgumentException("Source et destination identiques");
        }
        if (req.getQuantity() <= 0) {
            throw new IllegalArgumentException("La quantité doit être > 0");
        }

        // Vérifier que le lot existe bien à la source dans l'ERP
        List<ErpStock> stocks = req.getLotNumber() != null
                ? erpStockRepo.findByItemCodeAndLocationAndLotNumber(
                        req.getErpItemCode(),
                        req.getSourceLocation(),
                        req.getLotNumber())
                .map(List::of).orElse(List.of())
                : erpStockRepo.findByItemCodeAndLocation(
                req.getErpItemCode(),
                req.getSourceLocation());

        if (stocks.isEmpty()) {
            throw new IllegalArgumentException(String.format(
                    "Lot introuvable dans l'ERP : article=%s lot=%s source=%s",
                    req.getErpItemCode(), req.getLotNumber(), req.getSourceLocation()));
        }
    }

    /**
     * Extrait le code magasin depuis un emplacement.
     * "MAG01/ALL-A1" → "MAG01"
     * "MAG01"        → "MAG01"
     */
    private String extractWarehouse(String location) {
        if (location == null) return null;
        return location.contains("/")
                ? location.split("/")[0].trim()
                : location.trim();
    }

    private StockTransfer getTransferOrThrow(Long id) {
        return transferRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Transfert introuvable : " + id));
    }

    private User getCurrentUser() {
        Object principal = SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        String username = principal instanceof UserDetails ud
                ? ud.getUsername()
                : principal.toString();
        return userRepo.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException(
                        "Utilisateur introuvable : " + username));
    }

    private boolean blank(String s) {
        return s == null || s.isBlank();
    }
}