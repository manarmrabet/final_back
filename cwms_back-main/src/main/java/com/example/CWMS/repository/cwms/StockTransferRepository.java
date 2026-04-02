package com.example.CWMS.repository.cwms;

import com.example.CWMS.model.cwms.StockTransfer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface StockTransferRepository extends JpaRepository<StockTransfer, Long> {

    // ─── Par statut ───────────────────────────────────────────────────────
    Page<StockTransfer> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);
    List<StockTransfer> findByStatus(String status);
    long countByStatus(String status);

    // ─── Par opérateur ────────────────────────────────────────────────────
    Page<StockTransfer> findByOperator_UserIdOrderByCreatedAtDesc(Integer operatorId, Pageable pageable);
    List<StockTransfer> findByOperator_UserIdAndStatus(Integer operatorId, String status);

    // ─── Par article ──────────────────────────────────────────────────────
    List<StockTransfer> findByErpItemCodeOrderByCreatedAtDesc(String itemCode);
    Page<StockTransfer> findByErpItemCode(String itemCode, Pageable pageable);

    // ─── Par emplacement ──────────────────────────────────────────────────
    List<StockTransfer> findBySourceLocationOrDestLocation(String source, String dest);

    // ─── Dashboard — comptage par statut ─────────────────────────────────
    @Query("""
        SELECT t.status, COUNT(t)
        FROM StockTransfer t
        GROUP BY t.status
        """)
    List<Object[]> countByStatusGrouped();

    // ─── Dashboard — transferts récents ──────────────────────────────────
    @Query("""
        SELECT t FROM StockTransfer t
        WHERE t.createdAt >= :since
        ORDER BY t.createdAt DESC
        """)
    List<StockTransfer> findRecentTransfers(@Param("since") LocalDateTime since);

    // ─── Dashboard — comptage depuis une date ────────────────────────────
    @Query("""
        SELECT COUNT(t) FROM StockTransfer t
        WHERE t.createdAt >= :since
        """)
    long countSince(@Param("since") LocalDateTime since);

    // ─── Dashboard — top articles (7 jours) ──────────────────────────────
    @Query("""
        SELECT t.erpItemCode, t.erpItemLabel, COUNT(t) as nb
        FROM StockTransfer t
        WHERE t.createdAt >= :since
        GROUP BY t.erpItemCode, t.erpItemLabel
        ORDER BY nb DESC
        """)
    List<Object[]> findTopTransferredItems(@Param("since") LocalDateTime since, Pageable pageable);

    // ─── Dashboard — top opérateurs (7 jours) ────────────────────────────
    @Query("""
        SELECT CONCAT(t.operator.firstName, ' ', t.operator.lastName), COUNT(t) as nb
        FROM StockTransfer t
        WHERE t.createdAt >= :since
          AND t.operator IS NOT NULL
        GROUP BY t.operator.firstName, t.operator.lastName
        ORDER BY nb DESC
        """)
    List<Object[]> findTopOperators(@Param("since") LocalDateTime since, Pageable pageable);

    // ─── Dashboard — top emplacements sources (7 jours) ──────────────────
    @Query("""
        SELECT t.sourceLocation, COUNT(t) as nb
        FROM StockTransfer t
        WHERE t.createdAt >= :since
        GROUP BY t.sourceLocation
        ORDER BY nb DESC
        """)
    List<Object[]> findTopSourceLocations(@Param("since") LocalDateTime since, Pageable pageable);

    // ─── En attente par opérateur (mobile) ───────────────────────────────
    @Query("""
        SELECT t FROM StockTransfer t
        WHERE t.operator.userId = :operatorId
          AND t.status = 'PENDING'
        ORDER BY t.createdAt ASC
        """)
    List<StockTransfer> findPendingByOperator(@Param("operatorId") Integer operatorId);

    // ─── Recherche avancée ────────────────────────────────────────────────
    @Query("""
        SELECT t FROM StockTransfer t
        WHERE (COALESCE(:status,   t.status)      = t.status)
          AND (COALESCE(:itemCode, t.erpItemCode) = t.erpItemCode)
          AND (:location IS NULL
               OR t.sourceLocation = :location
               OR t.destLocation   = :location)
          AND (:from IS NULL OR t.createdAt >= :from)
          AND (:to   IS NULL OR t.createdAt <= :to)
        """)
    Page<StockTransfer> search(
            @Param("status")   String        status,
            @Param("itemCode") String        itemCode,
            @Param("location") String        location,
            @Param("from")     LocalDateTime from,
            @Param("to")       LocalDateTime to,
            Pageable           pageable
    );

    @Query("""
        SELECT t FROM StockTransfer t
        WHERE (:sourceWarehouse IS NULL OR t.sourceWarehouse = :sourceWarehouse)
          AND (:destWarehouse IS NULL OR t.destWarehouse = :destWarehouse)
        """)
    Page<StockTransfer> findByWarehouses(
            @Param("sourceWarehouse") String sourceWarehouse,
            @Param("destWarehouse")   String destWarehouse,
            Pageable pageable);

    /**
     * Récupère les transferts terminés sur une période donnée pour l'archivage mensuel.
     * Utilisé par TransferArchiveScheduler.
     */
    @Query("""
        SELECT t FROM StockTransfer t
        WHERE t.createdAt >= :from
          AND t.createdAt <= :to
          AND t.status IN :statuses
        ORDER BY t.createdAt ASC
        """)
    List<StockTransfer> findTransfersForArchive(
            @Param("from")     LocalDateTime from,
            @Param("to")       LocalDateTime to,
            @Param("statuses") List<String>  statuses,
            Pageable           pageable);
}