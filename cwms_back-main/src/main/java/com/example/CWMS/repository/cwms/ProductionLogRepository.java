package com.example.CWMS.repository.cwms;

import com.example.CWMS.model.cwms.ProductionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ══════════════════════════════════════════════════════════════════════════
 *  ProductionLogRepository — VERSION COMPLÈTE
 *
 *  Contient toutes les méthodes existantes + les deux nouvelles méthodes
 *  d'archivage (findOldLogs / deleteOldLogs), calquées exactement sur
 *  le pattern utilisé dans AuditLogRepository.
 * ══════════════════════════════════════════════════════════════════════════
 */
@Repository
public interface ProductionLogRepository extends JpaRepository<ProductionLog, Long> {

    // ── Méthodes existantes (inchangées) ─────────────────────────────────

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

    @Query(value =
            "SELECT user_id, user_name, COUNT(*) AS nb_ops, " +
                    "SUM(qty_requested) AS total_qty " +
                    "FROM dbo.ProductionLog WHERE status = 'SUCCESS' " +
                    "GROUP BY user_id, user_name ORDER BY nb_ops DESC",
            nativeQuery = true)
    List<Object[]> getOperatorStats();

    List<ProductionLog> findTop50ByCreatedAtAfterOrderByCreatedAtDesc(LocalDateTime since);

    // ── NOUVELLES MÉTHODES POUR L'ARCHIVAGE ──────────────────────────────
    //    Pattern identique à AuditLogRepository.findOldLogs / deleteOldLogs

    /**
     * Récupère tous les logs dont la date de création est ANTÉRIEURE à cutoff.
     * Utilisé par ProductionArchiveService pour sélectionner ce qui doit être
     * archivé en CSV avant suppression.
     *
     * Exemple d'usage dans AuditLogRepository :
     *   List<AuditLog> findOldLogs(LocalDateTime cutoff)
     */
    @Query("SELECT p FROM ProductionLog p WHERE p.createdAt < :cutoff ORDER BY p.createdAt ASC")
    List<ProductionLog> findOldLogs(@Param("cutoff") LocalDateTime cutoff);

    /**
     * Supprime en masse tous les logs antérieurs à cutoff.
     * Appelé APRÈS l'écriture du CSV pour ne jamais perdre de données.
     *
     * @Modifying + @Transactional est géré au niveau service (comme dans
     * AuditArchiveService qui utilise @Transactional sur processArchiving()).
     */
    @Modifying
    @Query("DELETE FROM ProductionLog p WHERE p.createdAt < :cutoff")
    void deleteOldLogs(@Param("cutoff") LocalDateTime cutoff);
}