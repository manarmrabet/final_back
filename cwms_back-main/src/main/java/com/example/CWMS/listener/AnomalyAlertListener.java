package com.example.CWMS.listener;

import com.example.CWMS.dto.AnomalyAlertDTO;
import com.example.CWMS.model.cwms.AnomalyLog;
import com.example.CWMS.repository.cwms.AnomalyLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import jakarta.mail.internet.MimeMessage;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * AnomalyAlertListener — EMAIL PERSONNALISÉ SELON LE VRAI MOUVEMENT
 *
 * Les "raisons de l'alerte" sont maintenant calculées dynamiquement
 * à partir des données réelles du mouvement (qteAvant, qteSortie,
 * source, heure, operateur, emplacement) — pas des valeurs hardcodées.
 *
 * 8 raisons détectées automatiquement :
 *  1. Stock vide avant sortie
 *  2. Sortie totale (qty_sortie >= qty_avant)
 *  3. Grande quantité (> 500 unités)
 *  4. Taux de sortie élevé (> 90% du stock)
 *  5. Opération nocturne (22h–6h)
 *  6. Opérateur non identifié
 *  7. Emplacement manquant
 *  8. Source automatique (API/ERP)
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AnomalyAlertListener {

    private final AnomalyLogRepository anomalyLogRepository;
    private final JavaMailSender        mailSender;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    @Value("${cwms.alert.recipient-email:manar.mrabet.46@gmail.com}")
    private String recipientEmail;

    @RabbitListener(queues = "${cwms.rabbit.queue.alerts:stock.alerts}")
    public void onAlert(AnomalyAlertDTO alert) {
        log.info("[ALERT-LISTENER] Alerte reçue id={}, score={}", alert.getTransferId(), alert.getAnomalyScore());

        if (anomalyLogRepository.existsByTransferId(alert.getTransferId())) {
            log.debug("[ALERT-LISTENER] id={} déjà loggé — skip (doublon ignoré)", alert.getTransferId());
            return;
        }

        AnomalyLog anomalyLog = AnomalyLog.builder()
                .transferId(alert.getTransferId())
                .operateur(alert.getOperateur())
                .article(alert.getArticle())
                .emplacement(alert.getEmplacement())
                .qteAvant(alert.getQteAvant())
                .qteSortie(alert.getQteSortie())
                .qteApres(alert.getQteApres())
                .source(alert.getSource())
                .appareil(alert.getAppareil())
                .anomalyScore(alert.getAnomalyScore())
                .isoFlag(alert.getIsoFlag())
                .lofFlag(alert.getLofFlag())
                .mouvementDate(alert.getMouvementDate())
                .detectedAt(LocalDateTime.now())
                .emailSent(false)
                .recipientEmail(recipientEmail)
                .build();

        try {
            anomalyLogRepository.save(anomalyLog);
        } catch (DataIntegrityViolationException e) {
            log.debug("[ALERT-LISTENER] id={} doublon contrainte DB — skip", alert.getTransferId());
            return;
        }

        try {
            sendAlertEmail(alert);
            anomalyLog.setEmailSent(true);
            anomalyLogRepository.save(anomalyLog);
            log.info("[ALERT-LISTENER] ✅ Email envoyé à {}", recipientEmail);
        } catch (Exception e) {
            log.error("[ALERT-LISTENER] ❌ Échec email id={} : {}", alert.getTransferId(), e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // DÉTECTION DES RAISONS — LOGIQUE MÉTIER
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Calcule dynamiquement les raisons de l'alerte depuis les données réelles.
     * Retourne une liste de raisons avec leur niveau de criticité.
     */
    private List<AlertReason> detectReasons(AnomalyAlertDTO alert) {
        List<AlertReason> reasons = new ArrayList<>();

        double qteAvant  = alert.getQteAvant()  != null ? alert.getQteAvant()  : 0.0;
        double qteSortie = alert.getQteSortie() != null ? alert.getQteSortie() : 0.0;

        // 1. Stock vide avant la sortie
        if (qteAvant == 0) {
            reasons.add(new AlertReason(
                    "CRITIQUE",
                    "Stock vide",
                    "Sortie effectuée alors que le stock était à 0 unité avant l'opération.",
                    "#A32D2D"
            ));
        }

        // 2. Sortie totale ou supérieure au stock
        if (qteAvant > 0 && qteSortie >= qteAvant) {
            reasons.add(new AlertReason(
                    "CRITIQUE",
                    "Sortie totale du stock",
                    String.format("%.0f unités sorties sur %.0f disponibles — stock épuisé après opération.", qteSortie, qteAvant),
                    "#A32D2D"
            ));
        }

        // 3. Grande quantité absolue (> 500)
        if (qteSortie > 500) {
            reasons.add(new AlertReason(
                    "ALERTE",
                    "Quantité sortie inhabituelle",
                    String.format("%.0f unités sorties en une seule opération (seuil habituel : < 500).", qteSortie),
                    "#854F0B"
            ));
        }

        // 4. Taux de sortie élevé (> 80% du stock)
        if (qteAvant > 0 && (qteSortie / qteAvant) > 0.80 && qteSortie < qteAvant) {
            int taux = (int) Math.round(qteSortie / qteAvant * 100);
            reasons.add(new AlertReason(
                    "ALERTE",
                    "Taux de sortie élevé",
                    String.format("%d%% du stock sorti en une opération (%.0f / %.0f unités).", taux, qteSortie, qteAvant),
                    "#854F0B"
            ));
        }

        // 5. Opération nocturne (entre 22h et 6h)
        if (alert.getMouvementDate() != null) {
            int heure = alert.getMouvementDate().getHour();
            if (heure >= 22 || heure <= 5) {
                reasons.add(new AlertReason(
                        "INFO",
                        "Opération hors horaires",
                        String.format("Opération effectuée à %02dh%02d — en dehors des horaires habituels (6h–22h).",
                                heure, alert.getMouvementDate().getMinute()),
                        "#185FA5"
                ));
            }
        }

        // 6. Opérateur non identifié
        if (alert.getOperateur() == null || alert.getOperateur().isBlank()) {
            reasons.add(new AlertReason(
                    "ALERTE",
                    "Opérateur inconnu",
                    "Aucun opérateur identifié pour ce mouvement — traçabilité insuffisante.",
                    "#854F0B"
            ));
        }

        // 7. Emplacement manquant ou générique ("0000")
        if (alert.getEmplacement() == null || alert.getEmplacement().isBlank()
                || "0000".equals(alert.getEmplacement())) {
            reasons.add(new AlertReason(
                    "INFO",
                    "Emplacement non précisé",
                    String.format("Emplacement '%s' — localisation physique non identifiée dans le magasin.",
                            alert.getEmplacement() != null ? alert.getEmplacement() : "vide"),
                    "#185FA5"
            ));
        }

        // 8. Source automatique non-mobile
        if ("API".equals(alert.getSource()) || "ERP".equals(alert.getSource())) {
            reasons.add(new AlertReason(
                    "INFO",
                    "Mouvement automatique",
                    String.format("Opération générée par %s (non initiée par un opérateur humain).", alert.getSource()),
                    "#185FA5"
            ));
        }

        // Aucune raison détectée = signal ML pur
        if (reasons.isEmpty()) {
            reasons.add(new AlertReason(
                    "ML",
                    "Signal statistique ML",
                    "Aucune règle métier explicite déclenchée. Le modèle ML (IsolationForest + LOF) "
                            + "a détecté un profil statistiquement inhabituel par rapport à l'historique des mouvements.",
                    "#534AB7"
            ));
        }

        return reasons;
    }

    // ════════════════════════════════════════════════════════════════════════
    // EMAIL HTML
    // ════════════════════════════════════════════════════════════════════════

    private void sendAlertEmail(AnomalyAlertDTO alert) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setTo(recipientEmail);

        int scorePercent = (int) Math.round(alert.getAnomalyScore() * 100);
        List<AlertReason> reasons = detectReasons(alert);

        // Sujet personnalisé selon la raison principale
        String sujetRaison = reasons.isEmpty() ? "Signal ML"
                : reasons.get(0).titre;
        helper.setSubject(String.format(
                "[CWMS] %s — Mouvement anormal (score %d%%) — ID #%d",
                sujetRaison, scorePercent, alert.getTransferId()
        ));
        helper.setText(buildBody(alert, reasons), true);
        mailSender.send(message);
    }

    private String buildBody(AnomalyAlertDTO alert, List<AlertReason> reasons) {
        int scorePercent = (int) Math.round(alert.getAnomalyScore() * 100);
        String scoreColor = scorePercent >= 90 ? "#A32D2D"
                : scorePercent >= 70 ? "#854F0B" : "#185FA5";

        StringBuilder sb = new StringBuilder();
        sb.append("<html><body style='font-family:Arial,sans-serif;color:#222;max-width:680px;margin:auto'>");

        // Header
        sb.append("<div style='background:#1a237e;color:white;padding:18px 24px;border-radius:8px 8px 0 0'>");
        sb.append("<p style='margin:0;font-size:13px;opacity:0.7'>Alerte Anomalie — CWMS</p>");
        sb.append("<h2 style='margin:4px 0 0 0;font-size:18px'>Système ML — IsolationForest + LOF</h2>");
        sb.append("</div>");

        // Score
        sb.append("<div style='background:#f8f8f8;padding:14px;text-align:center;border:1px solid #e0e0e0;border-top:none'>");
        sb.append(String.format("<span style='font-size:44px;font-weight:bold;color:%s'>%d%%</span><br>", scoreColor, scorePercent));
        sb.append("<span style='color:#666;font-size:13px'>Score d'anomalie consensus</span>");
        sb.append("</div>");

        // Section 1 : Détails
        sb.append(sectionHeader("1. Détails du mouvement"));
        sb.append("<table style='width:100%;border-collapse:collapse;border:1px solid #e0e0e0;border-top:none'>");
        row(sb, "ID mouvement", "#" + alert.getTransferId());
        row(sb, "Date", alert.getMouvementDate() != null ? alert.getMouvementDate().format(FMT) : "N/A");
        row(sb, "Opérateur", alert.getOperateur() != null ? alert.getOperateur() : "Non identifié");
        row(sb, "Article", alert.getArticle());
        row(sb, "Emplacement", alert.getEmplacement() != null ? alert.getEmplacement() : "Non renseigné");
        row(sb, "Quantité avant", fmt(alert.getQteAvant()) + " unités");
        row(sb, "Quantité sortie", fmt(alert.getQteSortie()) + " unités");
        row(sb, "Quantité après", fmt(alert.getQteApres()) + " unités");
        row(sb, "Source", alert.getSource() != null ? alert.getSource() : "N/A");
        sb.append("</table>");

        // Section 2 : Raisons PERSONNALISÉES
        sb.append(sectionHeader("2. Raisons de l'alerte"));
        sb.append("<div style='border:1px solid #e0e0e0;border-top:none;padding:14px'>");

        if (reasons.isEmpty()) {
            sb.append("<p style='color:#666;margin:0'>Aucune raison spécifique détectée.</p>");
        } else {
            for (AlertReason r : reasons) {
                sb.append(String.format(
                        "<div style='border-left:4px solid %s;padding:10px 14px;margin-bottom:10px;background:#fafafa;border-radius:0 4px 4px 0'>" +
                                "<span style='font-size:11px;font-weight:bold;color:%s;text-transform:uppercase;letter-spacing:1px'>%s</span>" +
                                "<p style='margin:4px 0 2px 0;font-weight:bold;color:#222;font-size:14px'>%s</p>" +
                                "<p style='margin:0;color:#555;font-size:13px;line-height:1.5'>%s</p>" +
                                "</div>",
                        r.couleur, r.couleur, r.niveau, r.titre, r.description
                ));
            }
        }
        sb.append("</div>");

        // Section 3 : Résultats ML
        sb.append(sectionHeader("3. Résultats ML"));
        sb.append("<table style='width:100%;border-collapse:collapse;border:1px solid #e0e0e0;border-top:none'>");
        row(sb, "Score consensus", scorePercent + "%");
        row(sb, "IsolationForest",
                alert.getIsoFlag() != null && alert.getIsoFlag() == 1
                        ? "<span style='color:#A32D2D'>Anormal</span>"
                        : "<span style='color:#3B6D11'>Normal</span>");
        row(sb, "LOF",
                alert.getLofFlag() != null && alert.getLofFlag() == 1
                        ? "<span style='color:#A32D2D'>Anormal</span>"
                        : "<span style='color:#3B6D11'>Normal</span>");
        sb.append("</table>");

        // Tableau interprétation
        sb.append("<table style='width:100%;border-collapse:collapse;margin-top:8px;font-size:12px'>");
        sb.append("<tr style='background:#1a237e;color:white'><th style='padding:6px 10px'>Score</th><th style='padding:6px 10px'>Interprétation</th></tr>");
        interpRow(sb, "0–50%", "Normal", "#3B6D11");
        interpRow(sb, "50–70%", "Surveillé", "#854F0B");
        interpRow(sb, "70–90%", "Suspect", "#854F0B");
        interpRow(sb, "90–100%", "Critique", "#A32D2D");
        sb.append("</table>");

        // Section 4 : Actions personnalisées selon les raisons
        sb.append(sectionHeader("4. Actions recommandées"));
        sb.append("<div style='border:1px solid #e0e0e0;border-top:none;padding:14px'>");
        sb.append("<ol style='margin:0;padding-left:20px;line-height:1.9;color:#333;font-size:14px'>");
        sb.append("<li>Vérifier le mouvement dans le <a href='http://localhost:4200/app/anomaly-dashboard' style='color:#1a237e'>dashboard CWMS</a></li>");

        // Actions spécifiques selon les raisons détectées
        for (AlertReason r : reasons) {
            switch (r.niveau) {
                case "CRITIQUE":
                    sb.append("<li><strong>URGENT</strong> — " + r.titre + " : vérifier immédiatement le stock physique de l'article <strong>" + alert.getArticle() + "</strong></li>");
                    break;
                case "ALERTE":
                    if (r.titre.contains("Opérateur")) {
                        sb.append("<li>Identifier l'opérateur responsable de cette opération et vérifier les droits d'accès</li>");
                    } else if (r.titre.contains("Quantité") || r.titre.contains("Taux")) {
                        sb.append("<li>Confirmer la demande de sortie auprès du responsable de production avant validation</li>");
                    }
                    break;
                case "INFO":
                    if (r.titre.contains("hors horaires")) {
                        sb.append("<li>Vérifier si une autorisation d'opération nocturne a été accordée pour cet opérateur</li>");
                    }
                    break;
            }
        }
        sb.append("<li>Contacter l'opérateur <strong>" + (alert.getOperateur() != null ? alert.getOperateur() : "inconnu") + "</strong> pour recueil de contexte</li>");
        sb.append("<li>Valider ou annuler le mouvement dans le <a href='http://localhost:4200/app/production-log' style='color:#1a237e'>journal de production</a></li>");
        sb.append("</ol></div>");

        // Footer
        sb.append("<div style='background:#f0f0f0;padding:12px 24px;border-radius:0 0 8px 8px;font-size:11px;color:#888;border:1px solid #e0e0e0;border-top:none'>");
        sb.append("Email généré automatiquement par CWMS ML — " + LocalDateTime.now().format(FMT));
        sb.append(" | <a href='http://localhost:4200/app/anomaly-dashboard' style='color:#1a237e'>Dashboard Anomalies</a>");
        sb.append("</div>");

        sb.append("</body></html>");
        return sb.toString();
    }

    // ════════════════════════════════════════════════════════════════════════
    // HELPERS HTML
    // ════════════════════════════════════════════════════════════════════════

    private String sectionHeader(String title) {
        return "<div style='background:#e8eaf6;padding:10px 14px;font-weight:bold;font-size:14px;"
                + "color:#1a237e;border:1px solid #c5cae9;border-top:none;margin-top:0'>"
                + title + "</div>";
    }

    private void row(StringBuilder sb, String label, String value) {
        sb.append("<tr>")
                .append("<td style='padding:7px 12px;border-bottom:1px solid #f0f0f0;background:#fafafa;font-weight:bold;width:35%;font-size:13px'>")
                .append(label).append("</td>")
                .append("<td style='padding:7px 12px;border-bottom:1px solid #f0f0f0;font-size:13px'>")
                .append(value != null ? value : "N/A").append("</td>")
                .append("</tr>");
    }

    private void interpRow(StringBuilder sb, String range, String label, String color) {
        sb.append("<tr>")
                .append("<td style='padding:5px 10px;border:1px solid #e0e0e0;text-align:center'>").append(range).append("</td>")
                .append("<td style='padding:5px 10px;border:1px solid #e0e0e0;color:").append(color).append("'>").append(label).append("</td>")
                .append("</tr>");
    }

    private String fmt(Double v) {
        if (v == null) return "N/A";
        return v == v.intValue() ? String.valueOf(v.intValue()) : String.valueOf(v);
    }

    // ════════════════════════════════════════════════════════════════════════
    // DTO INTERNE
    // ════════════════════════════════════════════════════════════════════════

    private static class AlertReason {
        final String niveau;
        final String titre;
        final String description;
        final String couleur;

        AlertReason(String niveau, String titre, String description, String couleur) {
            this.niveau      = niveau;
            this.titre       = titre;
            this.description = description;
            this.couleur     = couleur;
        }
    }
}