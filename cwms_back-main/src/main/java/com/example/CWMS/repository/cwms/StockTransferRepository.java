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

    Page<StockTransfer> findByStatusOrderByCreatedAtDesc(StockTransfer.TransferStatus status, Pageable pageable);
    List<StockTransfer> findByStatus(StockTransfer.TransferStatus status);
    long countByStatus(StockTransfer.TransferStatus status);

    Page<StockTransfer> findByOperator_UserIdOrderByCreatedAtDesc(Integer operatorId, Pageable pageable);
    List<StockTransfer> findByOperator_UserIdAndStatus(Integer operatorId, StockTransfer.TransferStatus status);

    List<StockTransfer> findByErpItemCodeOrderByCreatedAtDesc(String itemCode);
    Page<StockTransfer> findByErpItemCode(String itemCode, Pageable pageable);
    List<StockTransfer> findBySourceLocationOrDestLocation(String source, String dest);

    @Query("SELECT t.status, COUNT(t) FROM StockTransfer t GROUP BY t.status")
    List<Object[]> countByStatusGrouped();

    @Query("SELECT t FROM StockTransfer t WHERE t.createdAt >= :since ORDER BY t.createdAt DESC")
    List<StockTransfer> findRecentTransfers(@Param("since") LocalDateTime since);

    @Query("SELECT COUNT(t) FROM StockTransfer t WHERE t.createdAt >= :since")
    long countSince(@Param("since") LocalDateTime since);

    @Query("""
        SELECT t.erpItemCode, t.erpItemLabel, COUNT(t) as nb
        FROM StockTransfer t WHERE t.createdAt >= :since
        GROUP BY t.erpItemCode, t.erpItemLabel ORDER BY nb DESC
        """)
    List<Object[]> findTopTransferredItems(@Param("since") LocalDateTime since, Pageable pageable);

    @Query("""
        SELECT CONCAT(t.operator.firstName,' ',t.operator.lastName), COUNT(t) as nb
        FROM StockTransfer t
        WHERE t.createdAt >= :since AND t.operator IS NOT NULL
        GROUP BY t.operator.firstName, t.operator.lastName ORDER BY nb DESC
        """)
    List<Object[]> findTopOperators(@Param("since") LocalDateTime since, Pageable pageable);

    @Query("""
        SELECT t.sourceLocation, COUNT(t) as nb
        FROM StockTransfer t WHERE t.createdAt >= :since
        GROUP BY t.sourceLocation ORDER BY nb DESC
        """)
    List<Object[]> findTopSourceLocations(@Param("since") LocalDateTime since, Pageable pageable);

    @Query("""
        SELECT t FROM StockTransfer t WHERE t.operator.userId = :operatorId
          AND t.status = 'PENDING' ORDER BY t.createdAt ASC
        """)
    List<StockTransfer> findPendingByOperator(@Param("operatorId") Integer operatorId);

    /**
     * Recherche avancée avec filtre opérateur (LIKE insensible à la casse sur prénom+nom).
     *
     * Règles :
     *   - Chaque param null  → filtre ignoré (pas de restriction)
     *   - location           → cherche dans sourceLocation OU destLocation
     *   - operator           → LIKE %:operator% sur CONCAT(firstName,' ',lastName)
     *   - from/to            → borne createdAt inclusive
     *
     * ⚠️  CAST(t.status AS string) est nécessaire pour comparer un enum JPA
     *     à un String passé en paramètre sous Hibernate 6+.
     */
    @Query("""
        SELECT t FROM StockTransfer t
        LEFT JOIN t.operator op
        WHERE (:status   IS NULL OR CAST(t.status AS string) = :status)
          AND (:itemCode IS NULL OR t.erpItemCode = :itemCode)
          AND (:location IS NULL
               OR t.sourceLocation = :location
               OR t.destLocation   = :location)
          AND (:operator IS NULL
               OR LOWER(CONCAT(COALESCE(op.firstName,''),' ',COALESCE(op.lastName,'')))
                  LIKE LOWER(CONCAT('%',:operator,'%')))
          AND (:from IS NULL OR t.createdAt >= :from)
          AND (:to   IS NULL OR t.createdAt <= :to)
        """)
    Page<StockTransfer> search(
            @Param("status")   String        status,
            @Param("itemCode") String        itemCode,
            @Param("location") String        location,
            @Param("operator") String        operator,
            @Param("from")     LocalDateTime from,
            @Param("to")       LocalDateTime to,
            Pageable           pageable
    );

    @Query("""
        SELECT t FROM StockTransfer t
        WHERE t.createdAt >= :from AND t.createdAt <= :to
          AND t.status IN :statuses
        ORDER BY t.createdAt ASC
        """)
    List<StockTransfer> findTransfersForArchive(
            @Param("from")     LocalDateTime from,
            @Param("to")       LocalDateTime to,
            @Param("statuses") List<StockTransfer.TransferStatus> statuses,
            Pageable           pageable
    );
}