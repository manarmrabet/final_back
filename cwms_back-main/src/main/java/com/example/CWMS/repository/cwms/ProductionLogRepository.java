package com.example.CWMS.repository.cwms;

import com.example.CWMS.model.cwms.ProductionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ProductionLogRepository extends JpaRepository<ProductionLog, Long> {

    List<ProductionLog> findAllByOrderByCreatedAtDesc();

    List<ProductionLog> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<ProductionLog> findByLotCodeOrderByCreatedAtDesc(String lotCode);

    @Query(value =
            "SELECT * FROM dbo.ProductionLog " +
                    "WHERE CAST(created_at AS DATE) = CAST(GETDATE() AS DATE) " +
                    "ORDER BY created_at DESC",
            nativeQuery = true)
    List<ProductionLog> findTodayLogs();

    @Query(value =
            "SELECT COUNT(*) FROM dbo.ProductionLog " +
                    "WHERE status = 'SUCCESS' " +
                    "AND CAST(created_at AS DATE) = CAST(GETDATE() AS DATE)",
            nativeQuery = true)
    Long countSuccessToday();

    @Query(value =
            "SELECT ISNULL(SUM(qty_requested),0) FROM dbo.ProductionLog " +
                    "WHERE status = 'SUCCESS' " +
                    "AND CAST(created_at AS DATE) = CAST(GETDATE() AS DATE)",
            nativeQuery = true)
    Double sumQtyToday();

    // Stats par opérateur pour le dashboard
    @Query(value =
            "SELECT user_id, user_name, COUNT(*) AS nb_ops, " +
                    "SUM(qty_requested) AS total_qty " +
                    "FROM dbo.ProductionLog WHERE status = 'SUCCESS' " +
                    "GROUP BY user_id, user_name ORDER BY nb_ops DESC",
            nativeQuery = true)
    List<Object[]> getOperatorStats();



    // ══ AJOUTS POUR LE MODULE ML ════════════════════════════════════════════


    List<ProductionLog> findTop50ByCreatedAtAfterOrderByCreatedAtDesc(LocalDateTime since);
}
