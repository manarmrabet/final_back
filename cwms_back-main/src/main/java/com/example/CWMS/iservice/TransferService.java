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

    /**
     * BUG 5 CORRIGÉ — ordre des paramètres aligné avec TransferServiceImpl et TransferController.
     *
     * Ancienne signature (interface) : search(status, operator, itemCode, location, from, to, pageable)
     * Impl et controller utilisaient : search(status, itemCode, location, operator, from, to, pageable)
     *
     * Un appel via proxy AOP ou mock de test aurait mélangé itemCode ↔ operator silencieusement.
     * Ordre canonique désormais : status → itemCode → location → operator → from → to → pageable
     */
    Page<TransferResponseDTO> search(
            String        status,
            String        itemCode,
            String        location,
            String        operator,
            LocalDateTime from,
            LocalDateTime to,
            Pageable      pageable
    );

    Page<TransferResponseDTO> getMyTransfers(Integer operatorId, Pageable pageable);

    // ─── ERP Data ─────────────────────────────────────────────────────────────

    List<String> getDistinctWarehouses();

    ErpArticleDTO getArticleByCode(String itemCode);

    List<ErpArticleDTO> searchArticles(String query);

    List<ErpStockDTO> getStockByItem(String itemCode);

    List<ErpStockDTO> getStockByLocation(String location);

    List<ErpStockDTO> getStockByLot(String lotNumber);

    ErpLocationDTO getLocationInfo(String locationCode);

    // ─── Dashboard ────────────────────────────────────────────────────────────

    TransferDashboardDTO getDashboard();
}