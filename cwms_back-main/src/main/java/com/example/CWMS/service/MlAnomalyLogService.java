package com.example.CWMS.service;

import com.example.CWMS.dto.AnomalyAlertDTO;
import com.example.CWMS.model.cwms.AnomalyLog;
import com.example.CWMS.repository.cwms.AnomalyLogRepository;
import com.example.CWMS.scheduler.AnomalyDetectionScheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * MlAnomalyLogService
 * ────────────────────
 * Sauvegarde les alertes d'anomalie en base et les expose
 * au MlController pour le dashboard Angular.
 *
 * Flux :
 * AnomalyDetectionScheduler → saveAlert() → table cwms_anomaly_logs
 *                                         ↓
 * MlController ← getRecentAnomalies() ← Angular
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MlAnomalyLogService {

    private final AnomalyLogRepository  anomalyLogRepository;
    private final AnomalyDetectionScheduler scheduler;

    /** Sauvegarde une alerte (appelée par AnomalyAlertListener) */
    public void saveAlert(AnomalyAlertDTO dto) {
        try {
            AnomalyLog log = AnomalyLog.builder()
                    .transferId(dto.getTransferId())
                    .operateur(dto.getOperateur())
                    .article(dto.getArticle())
                    .magasin(dto.getMagasin())
                    .emplacement(dto.getEmplacement())
                    .qteAvant(dto.getQteAvant())
                    .qteSortie(dto.getQteSortie())
                    .qteApres(dto.getQteApres())
                    .source(dto.getSource())
                    .appareil(dto.getAppareil())
                    .anomalyScore(dto.getAnomalyScore())
                    .isoFlag(dto.getIsoFlag())
                    .lofFlag(dto.getLofFlag())
                    .mouvementDate(dto.getMouvementDate())
                    .detectedAt(LocalDateTime.now())
                    .emailSent(true)
                    .recipientEmail(dto.getRecipientEmail())
                    .build();
            anomalyLogRepository.save(log);
        } catch (Exception e) {
            log.error("Erreur sauvegarde AnomalyLog : {}", e.getMessage());
        }
    }

    /** Retourne les anomalies des 7 derniers jours */
    public List<AnomalyAlertDTO> getRecentAnomalies() {
        LocalDateTime since = LocalDateTime.now().minusDays(7);
        return anomalyLogRepository
                .findByDetectedAtAfterOrderByDetectedAtDesc(since)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /** Déclenche manuellement le scheduler (pour demo PFE) */
    public void triggerManually() {
        log.info("[ML] Déclenchement manuel du scheduler d'anomalie");
        scheduler.detectAndAlert();
    }

    private AnomalyAlertDTO toDTO(AnomalyLog l) {
        return AnomalyAlertDTO.builder()
                .transferId(l.getTransferId())
                .operateur(l.getOperateur())
                .article(l.getArticle())
                .magasin(l.getMagasin())
                .emplacement(l.getEmplacement())
                .qteAvant(l.getQteAvant())
                .qteSortie(l.getQteSortie())
                .qteApres(l.getQteApres())
                .source(l.getSource())
                .appareil(l.getAppareil())
                .anomalyScore(l.getAnomalyScore())
                .isoFlag(l.getIsoFlag())
                .lofFlag(l.getLofFlag())
                .mouvementDate(l.getMouvementDate())
                .build();
    }
}