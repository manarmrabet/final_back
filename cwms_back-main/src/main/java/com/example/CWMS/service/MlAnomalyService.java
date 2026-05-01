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
 * Chemin : service/MlAnomalyService.java
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MlAnomalyService {

    private final AnomalyLogRepository      anomalyLogRepository;
    private final AnomalyDetectionScheduler anomalyDetectionScheduler;

    public List<AnomalyAlertDTO> getRecentAnomalies() {
        return anomalyLogRepository
                .findByDetectedAtAfterOrderByDetectedAtDesc(
                        LocalDateTime.now().minusDays(7))
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    public void triggerManually() {
        log.info("[ML] Déclenchement manuel du scheduler d'anomalie");
        anomalyDetectionScheduler.detectAndAlert();
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
