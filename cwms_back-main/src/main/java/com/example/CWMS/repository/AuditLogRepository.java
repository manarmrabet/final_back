package com.example.CWMS.repository;

import com.example.CWMS.model.AuditLog;
import com.example.CWMS.model.AuditLog.EventType;
import com.example.CWMS.model.AuditLog.Severity;
import com.example.CWMS.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    // ✅ findByUser utilise l'objet User directement (userId = Integer, conforme User.java)
    Page<AuditLog> findByUser(User user, Pageable pageable);

    // ✅ Accès par userId (Integer, comme UserId dans User.java)
    @Query("SELECT a FROM AuditLog a WHERE a.user.userId = :userId ORDER BY a.createdAt DESC")
    Page<AuditLog> findByUserId(@Param("userId") Integer userId, Pageable pageable);

    // ✅ Historique connexions d'un user
    @Query("""
        SELECT a FROM AuditLog a
        WHERE a.user.userId = :userId
          AND a.eventType IN ('LOGIN', 'LOGOUT', 'LOGIN_FAILED')
        ORDER BY a.createdAt DESC
        """)
    List<AuditLog> findConnectionsByUserId(@Param("userId") Integer userId);

    // ✅ Détection brute force — username snapshot (fonctionne même sans FK résolue)
    @Query("""
        SELECT COUNT(a) FROM AuditLog a
        WHERE a.username  = :username
          AND a.eventType = com.example.CWMS.model.AuditLog.EventType.LOGIN_FAILED
          AND a.createdAt >= :since
        """)
    long countRecentFailedLogins(@Param("username") String username,
                                 @Param("since") LocalDateTime since);

    // ✅ Recherche multicritères
    @Query("""
        SELECT a FROM AuditLog a
        WHERE (:eventType IS NULL OR a.eventType  = :eventType)
          AND (:severity  IS NULL OR a.severity   = :severity)
          AND (:userId    IS NULL OR a.user.userId = :userId)
          AND (:from      IS NULL OR a.createdAt  >= :from)
          AND (:to        IS NULL OR a.createdAt  <= :to)
        ORDER BY a.createdAt DESC
        """)
    Page<AuditLog> search(
            @Param("eventType") EventType     eventType,
            @Param("severity")  Severity      severity,
            @Param("userId")    Integer       userId,
            @Param("from")      LocalDateTime from,
            @Param("to")        LocalDateTime to,
            Pageable pageable
    );
}