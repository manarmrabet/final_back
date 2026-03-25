package com.example.CWMS.transfer.repository;

import com.example.CWMS.transfer.model.StockTransfer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository CWMSDB — stock_transfers
 * Lié à la datasource primaire via CwmsDataSourceConfig.
 */
@Repository
public interface StockTransferRepository extends JpaRepository<StockTransfer, Long> {

    // ─── Requêtes par statut ─────────────────────────────────────────────────

    Page<StockTransfer> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    List<StockTransfer> findByStatus(String status);

    long countByStatus(String status);

    // ─── Requêtes par opérateur ──────────────────────────────────────────────

    Page<StockTransfer> findByOperator_UserIdOrderByCreatedAtDesc(Integer operatorId, Pageable pageable);

    List<StockTransfer> findByOperator_UserIdAndStatus(Integer operatorId, String status);

    // ─── Requêtes par article ────────────────────────────────────────────────

    List<StockTransfer> findByErpItemCodeOrderByCreatedAtDesc(String itemCode);

    Page<StockTransfer> findByErpItemCode(String itemCode, Pageable pageable);

    // ─── Requêtes par emplacement ────────────────────────────────────────────

    List<StockTransfer> findBySourceLocationOrDestLocation(String source, String dest);

    // ─── Dashboard / Statistiques ────────────────────────────────────────────

    /** Nombre de transferts par statut (pour le dashboard web) */
    @Query("""
        SELECT t.status, COUNT(t)
        FROM StockTransfer t
        GROUP BY t.status
        """)
    List<Object[]> countByStatusGrouped();

    /** Transferts des dernières N heures */
    @Query("""
        SELECT t FROM StockTransfer t
        WHERE t.createdAt >= :since
        ORDER BY t.createdAt DESC
        """)
    List<StockTransfer> findRecentTransfers(@Param("since") LocalDateTime since);

    /** Top articles les plus transférés */
    @Query("""
        SELECT t.erpItemCode, t.erpItemLabel, COUNT(t) as nb
        FROM StockTransfer t
        WHERE t.createdAt >= :since
        GROUP BY t.erpItemCode, t.erpItemLabel
        ORDER BY nb DESC
        """)
    List<Object[]> findTopTransferredItems(@Param("since") LocalDateTime since, Pageable pageable);

    /** Transferts en attente pour un opérateur (mobile) */
    @Query("""
        SELECT t FROM StockTransfer t
        WHERE t.operator.userId = :operatorId
          AND t.status = 'PENDING'
        ORDER BY t.createdAt ASC
        """)
    List<StockTransfer> findPendingByOperator(@Param("operatorId") Integer operatorId);

    /** Recherche globale (web) */
    @Query("""
        SELECT t FROM StockTransfer t
        WHERE (:status IS NULL OR t.status = :status)
          AND (:itemCode IS NULL OR t.erpItemCode = :itemCode)
          AND (:location IS NULL OR t.sourceLocation = :location OR t.destLocation = :location)
          AND (:from IS NULL OR t.createdAt >= :from)
          AND (:to IS NULL OR t.createdAt <= :to)
        ORDER BY t.createdAt DESC
        """)
    Page<StockTransfer> search(
            @Param("status")   String status,
            @Param("itemCode") String itemCode,
            @Param("location") String location,
            @Param("from")     LocalDateTime from,
            @Param("to")       LocalDateTime to,
            Pageable pageable
    );
}