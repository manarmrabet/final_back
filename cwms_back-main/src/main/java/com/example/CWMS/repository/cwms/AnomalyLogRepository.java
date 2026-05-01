package com.example.CWMS.repository.cwms;

import com.example.CWMS.model.cwms.AnomalyLog;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AnomalyLogRepository extends JpaRepository<AnomalyLog, Long> {

    // Utilisé par le dashboard Angular (7 derniers jours)
    List<AnomalyLog> findByDetectedAtAfterOrderByDetectedAtDesc(LocalDateTime since);

    // ANTI-DOUBLON : vérifier si un mouvement a déjà été loggé
    boolean existsByTransferId(Long transferId);
    // Supprime les entrées plus vieilles que `cutoff` — utilisé par le scheduler de purge
    @Modifying
    @Transactional
    @Query("DELETE FROM AnomalyLog a WHERE a.detectedAt < :cutoff")
    int deleteByDetectedAtBefore(@Param("cutoff") LocalDateTime cutoff);
}