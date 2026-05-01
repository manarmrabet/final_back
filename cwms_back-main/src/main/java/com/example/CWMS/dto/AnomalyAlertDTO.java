package com.example.CWMS.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO publié dans la queue stock.alerts quand un mouvement dépasse le seuil.
 * Sérialisé en JSON par RabbitMQ, désérialisé par AnomalyAlertListener.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AnomalyAlertDTO {

    private Long   transferId;
    private String operateur;
    private String article;
    private Double magasin;
    private String emplacement;
    private Double qteAvant;
    private Double qteSortie;
    private Double qteApres;
    private String source;
    private String appareil;

    /** Score consensus calculé par FastAPI (0.0 – 1.0) */
    private Double anomalyScore;

    /** iso_flag retourné par FastAPI */
    private Integer isoFlag;

    /** lof_flag retourné par FastAPI */
    private Integer lofFlag;

    /** Timestamp du mouvement */
    private LocalDateTime mouvementDate;

    /** Email du responsable à notifier (lu depuis config ou base) */
    private String recipientEmail;
}