package com.example.CWMS.model.cwms;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * AnomalyLog — VERSION FINALE
 *
 * CORRECTION DOUBLONS :
 *   Le scheduler s'exécute toutes les heures + déclenchement manuel.
 *   Les mêmes mouvements (ex: #10017, #10018) étaient analysés
 *   et sauvegardés à chaque exécution → N lignes identiques en base.
 *
 *   Solution : contrainte UNIQUE sur transfer_id.
 *   Si un mouvement a déjà été loggé, la sauvegarde est ignorée
 *   silencieusement grâce au saveIfNotExists() dans AnomalyAlertListener.
 */
@Entity
@Table(
        name = "cwms_anomaly_logs",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_anomaly_transfer_id", columnNames = "transfer_id")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnomalyLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transfer_id", nullable = false, unique = true)
    private Long transferId;

    @Column(name = "operateur", length = 100)
    private String operateur;

    @Column(name = "article", length = 100)
    private String article;

    @Column(name = "magasin")
    private Double magasin;

    @Column(name = "emplacement", length = 100)
    private String emplacement;

    @Column(name = "qte_avant")
    private Double qteAvant;

    @Column(name = "qte_sortie")
    private Double qteSortie;

    @Column(name = "qte_apres")
    private Double qteApres;

    @Column(name = "source", length = 50)
    private String source;

    @Column(name = "appareil", length = 100)
    private String appareil;

    @Column(name = "anomaly_score")
    private Double anomalyScore;

    @Column(name = "iso_flag")
    private Integer isoFlag;

    @Column(name = "lof_flag")
    private Integer lofFlag;

    @Column(name = "mouvement_date")
    private LocalDateTime mouvementDate;

    @Column(name = "detected_at")
    private LocalDateTime detectedAt;

    @Column(name = "email_sent")
    private Boolean emailSent;

    @Column(name = "recipient_email", length = 200)
    private String recipientEmail;
}