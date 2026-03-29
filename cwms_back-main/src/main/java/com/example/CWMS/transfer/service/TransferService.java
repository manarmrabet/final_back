package com.example.CWMS.transfer.service;

import com.example.CWMS.transfer.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Interface du service de transfert interne de stock.
 *
 * Principe métier :
 *  1. Valider l'article via ERP (existe, actif)
 *  2. Valider le stock ERP (quantité suffisante dans l'emplacement source)
 *  3. Valider les emplacements (source ≠ destination, tous les deux existent dans ERP)
 *  4. Enregistrer le transfert dans CWMSDB (stock_transfers)
 *  5. L'audit est automatique via @Auditable
 */
public interface TransferService {

    // ─── Opérations de transfert ──────────────────────────────────────────────

    /**
     * Créer un transfert interne (appelé par mobile ou web).
     * Lance une validation métier complète avant persistance.
     */
    TransferResponseDTO createTransfer(TransferRequestDTO request);

    /**
     * Créer plusieurs transferts en batch (mode multi-lignes Flutter).
     * Chaque ligne est traitée indépendamment — une erreur n'arrête pas les autres.
     */
    List<TransferResponseDTO> createTransferBatch(List<TransferRequestDTO> requests);

    /**
     * Valider un transfert PENDING (superviseur web).
     * Passe le statut de PENDING → DONE.
     */
    TransferResponseDTO validateTransfer(Long transferId);

    /**
     * Annuler un transfert (superviseur web).
     * Passe le statut de PENDING → CANCELLED.
     */
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

    List<TransferResponseDTO> getMyTransfers(Integer operatorId);

    // ─── ERP Data (exposés via ce service pour isoler la datasource ERP) ─────

    ErpArticleDTO getArticleByCode(String itemCode);

    List<ErpArticleDTO> searchArticles(String query);

    List<ErpStockDTO> getStockByItem(String itemCode);

    List<ErpStockDTO> getStockByLocation(String location);

    // ─── Dashboard ────────────────────────────────────────────────────────────

    TransferDashboardDTO getDashboard();


    List<ErpStockDTO> getStockByLot(String lotNumber);
    ErpLocationDTO    getLocationInfo(String locationCode);
}