package com.example.CWMS.repository.cwms;

import com.example.CWMS.model.cwms.AuditLog;
import com.example.CWMS.model.cwms.AuditLog.EventType;
import com.example.CWMS.model.cwms.AuditLog.Severity;
import com.example.CWMS.model.cwms.User;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    // ✅ OK : Les méthodes de nommage automatique gèrent très bien le Pageable
    Page<AuditLog> findByUser(User user, Pageable pageable);

    // ✅ CORRIGÉ : Suppression du ORDER BY (conflit avec Pageable sur SQL Server)
    @Query("SELECT a FROM AuditLog a WHERE a.user.userId = :userId")
    Page<AuditLog> findByUserId(@Param("userId") Integer userId, Pageable pageable);

    // ✅ OK : Ici on peut garder le ORDER BY car on retourne une List (pas de Pageable)
    @Query("""
        SELECT a FROM AuditLog a
        WHERE a.user.userId = :userId
          AND a.eventType IN ('LOGIN', 'LOGOUT', 'LOGIN_FAILED')
        ORDER BY a.createdAt DESC
        """)
    List<AuditLog> findConnectionsByUserId(@Param("userId") Integer userId);

    // ✅ OK : Requête de comptage simple



    ;

    // ✅ CORRIGÉ : Suppression du ORDER BY final
    @Query("""
        SELECT a FROM AuditLog a
        WHERE (:eventType IS NULL OR a.eventType  = :eventType)
          AND (:severity  IS NULL OR a.severity   = :severity)
          AND (:userId    IS NULL OR a.user.userId = :userId)
          AND (:from      IS NULL OR a.createdAt  >= :from)
          AND (:to        IS NULL OR a.createdAt  <= :to)
        """)
    Page<AuditLog> search(
            @Param("eventType") EventType     eventType,
            @Param("severity")  Severity      severity,
            @Param("userId")    Integer       userId,
            @Param("from")      LocalDateTime from,
            @Param("to")        LocalDateTime to,
            Pageable pageable
    );
    // ✅ Pour delete forcé : supprime tous les logs d'un user
    @Modifying
    @Query("DELETE FROM AuditLog a WHERE a.user.userId = :userId")
    void deleteAllByUserId(@Param("userId") Integer userId);
//archivage

    @Query("SELECT a FROM AuditLog a WHERE a.createdAt < :cutoffDate")
    List<AuditLog> findOldLogs(@Param("cutoffDate") LocalDateTime cutoffDate);

    @Modifying
    @Transactional
    @Query("DELETE FROM AuditLog a WHERE a.createdAt < :cutoffDate")
    void deleteOldLogs(@Param("cutoffDate") LocalDateTime cutoffDate);
}