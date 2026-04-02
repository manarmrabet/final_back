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
//Récupère tous les logs pour un objet User spécifique avec pagination.
    //   Les méthodes de nommage automatique gèrent très bien le Pageable
    Page<AuditLog> findByUser(User user, Pageable pageable);



    // Filtre spécifiquement les événements de type LOGIN, LOGOUT ou LOGIN_FAILED
    @Query("""
        SELECT a FROM AuditLog a
        WHERE a.user.userId = :userId
          AND a.eventType IN ('LOGIN', 'LOGOUT', 'LOGIN_FAILED')
        ORDER BY a.createdAt DESC
        """)
    List<AuditLog> findConnectionsByUserId(@Param("userId") Integer userId);

    //  OK : Requête de comptage simple



    ;

    //  gère des filtres optionnels
    //  (si un paramètre est NULL, il est ignoré)
    //  sur le type, la sévérité, l'utilisateur et les dates.
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
    // Supprime physiquement tous les logs d'un utilisateur (utilisé pour les suppressions forcées)
    @Modifying
    @Query("DELETE FROM AuditLog a WHERE a.user.userId = :userId")
    void deleteAllByUserId(@Param("userId") Integer userId);
//archivage
    //Sélectionne les logs créés avant une date donnée pour préparation à l'archivage

    @Query("SELECT a FROM AuditLog a WHERE a.createdAt < :cutoffDate")
    List<AuditLog> findOldLogs(@Param("cutoffDate") LocalDateTime cutoffDate);
//Supprime les logs plus vieux qu'une date de coupure (après archivage).
    @Modifying
    @Transactional
    @Query("DELETE FROM AuditLog a WHERE a.createdAt < :cutoffDate")
    void deleteOldLogs(@Param("cutoffDate") LocalDateTime cutoffDate);
}