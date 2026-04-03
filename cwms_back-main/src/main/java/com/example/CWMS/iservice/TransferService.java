package com.example.CWMS.iservice;

import com.example.CWMS.dto.TransferDashboardDTO;
import com.example.CWMS.dto.TransferRequestDTO;
import com.example.CWMS.dto.TransferResponseDTO;
import com.example.CWMS.dto.ErpArticleDTO;
import com.example.CWMS.dto.ErpLocationDTO;
import com.example.CWMS.dto.ErpStockDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Interface du service de transfert interne de stock.
 *
 * Principe métier :
 *  1. Valider l'article via ERP (existe, actif)
 *  2. Valider le stock ERP (lot présent à la source)
 *  3. Valider les emplacements (source ≠ destination)
 *  4. Enregistrer le transfert dans CWMSDB
 *  5. Mettre à jour l'ERP (non bloquant)
 *  6. L'audit est automatique via @Auditable
 */
public interface TransferService {

    // ─── Opérations de transfert ──────────────────────────────────────────────

    TransferResponseDTO createTransfer(TransferRequestDTO request);

    List<TransferResponseDTO> createTransferBatch(List<TransferRequestDTO> requests);

    TransferResponseDTO validateTransfer(Long transferId);

    TransferResponseDTO cancelTransfer(Long transferId, String reason);

    // ─── Consultation ─────────────────────────────────────────────────────────

    TransferResponseDTO getById(Long id);

    Page<TransferResponseDTO> getAll(Pageable pageable);

    Page<TransferResponseDTO> search(
            String status,
            String itemCode,
            String location,
            LocalDateTime from,
            LocalDateTime to,
            Pageable pageable
    );

    /**
     * ✅ CORRECTION : vraie pagination au lieu d'une liste limitée à 50 en dur.
     * Le controller passe le Pageable (page, size, sort).
     * Exemple : GET /api/transfers/my/5?page=0&size=20
     */
    Page<TransferResponseDTO> getMyTransfers(Integer operatorId, Pageable pageable);

    // ─── ERP Data ─────────────────────────────────────────────────────────────

    ErpArticleDTO getArticleByCode(String itemCode);

    List<ErpArticleDTO> searchArticles(String query);

    List<ErpStockDTO> getStockByItem(String itemCode);

    List<ErpStockDTO> getStockByLocation(String location);

    List<ErpStockDTO> getStockByLot(String lotNumber);

    ErpLocationDTO getLocationInfo(String locationCode);

    // ─── Dashboard ────────────────────────────────────────────────────────────

    TransferDashboardDTO getDashboard();
}