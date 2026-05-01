package com.example.CWMS.scheduler;

import com.example.CWMS.dto.AnomalyAlertDTO;
import com.example.CWMS.dto.MlAnomalyRequest;
import com.example.CWMS.dto.MlAnomalyRequest.MouvementML;
import com.example.CWMS.model.cwms.ProductionLog;
import com.example.CWMS.repository.cwms.ProductionLogRepository;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
public class AnomalyDetectionScheduler {

    private final ProductionLogRepository productionLogRepository;
    private final RabbitTemplate          rabbitTemplate;
    private final RestTemplate            mlRestTemplate;
    private final ObjectMapper            objectMapper = new ObjectMapper();

    @Value("${cwms.rabbit.exchange:cwms.topic}")
    private String exchange;

    @Value("${cwms.rabbit.routing.alert:alert.anomaly}")
    private String alertRoutingKey;

    @Value("${cwms.ml.anomaly-threshold:0.70}")
    private double threshold;

    @Value("${cwms.alert.recipient-email:manar.mrabet.46@gmail.com}")
    private String recipientEmail;

    @Value("${cwms.ml.test-mode:false}")
    private boolean testMode;

    @Value("${cwms.ml.window-hours:${cwms.ml.test-window-hours:24}}")
    private int windowHours;

    public AnomalyDetectionScheduler(
            ProductionLogRepository productionLogRepository,
            RabbitTemplate rabbitTemplate,
            @Qualifier("mlRestTemplate") RestTemplate mlRestTemplate) {
        this.productionLogRepository = productionLogRepository;
        this.rabbitTemplate          = rabbitTemplate;
        this.mlRestTemplate          = mlRestTemplate;
    }

    @Scheduled(fixedRateString = "${cwms.ml.anomaly-check-interval:3600000}")
    public void detectAndAlert() {
        if (testMode) runTestMode();
        else          runNormalMode();
    }

    // ════════════════════════════════════════════════════════════════════════
    // MODE NORMAL
    // ════════════════════════════════════════════════════════════════════════
    private void runNormalMode() {
        log.info("[ML-Scheduler] Démarrage détection (fenêtre {}h)...", windowHours);
        LocalDateTime since = LocalDateTime.now().minusHours(windowHours);
        log.info("[ML-Scheduler] Fenêtre : depuis {} jusqu'à maintenant", since);

        // CORRECTION PRINCIPALE :
        // findTop50ByCreatedAtAfterOrderByCreatedAtDesc utilise ProductionLog.createdAt
        // qui est mappé sur la colonne "created_at" — confirmé dans la DB.
        List<ProductionLog> recent =
                productionLogRepository.findTop50ByCreatedAtAfterOrderByCreatedAtDesc(since);

        log.info("[ML-Scheduler] {} mouvements récupérés", recent.size());

        if (recent.size() < 2) {
            log.info("[ML-Scheduler] {} mouvement(s) — min 2 requis pour LOF, skip.", recent.size());
            return;
        }

        MlAnomalyRequest request = MlAnomalyRequest.builder()
                .mouvements(recent.stream().map(this::toMouvementML).collect(Collectors.toList()))
                .build();

        logJsonDebug(request);

        AnomalyResponse response = callFastApi(request);
        if (response == null || response.getResults() == null) return;

        log.info("[ML-Scheduler] ✅ {} analysés — {} anomalies — {} alertes",
                response.getCountTotal(), response.getCountAnomalies(), response.getCountAlerts());

        for (AnomalyResult result : response.getResults()) {
            if (result.isAlert()) {
                ProductionLog entry = recent.get(result.getIndex());
                publishAlert(buildAlert(entry, result), entry.getId());
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // MODE TEST
    // ════════════════════════════════════════════════════════════════════════
    private void runTestMode() {
        log.info("[ML-Scheduler] ⚠️  MODE TEST ACTIF — emails forcés sans FastAPI");
        LocalDateTime since = LocalDateTime.now().minusHours(windowHours);
        List<ProductionLog> recent =
                productionLogRepository.findTop50ByCreatedAtAfterOrderByCreatedAtDesc(since);
        if (recent.isEmpty()) {
            log.warn("[ML-Scheduler][TEST] Aucun mouvement dans les {}h", windowHours);
            return;
        }
        log.info("[ML-Scheduler][TEST] {} mouvement(s) — alertes forcées", recent.size());
        recent.forEach(p -> publishAlert(buildTestAlert(p), p.getId()));
    }

    // ════════════════════════════════════════════════════════════════════════
    // MAPPING ProductionLog → MouvementML
    // Utilise UNIQUEMENT les getters de ProductionLog — aucune modification
    // de l'entité n'est nécessaire.
    // ════════════════════════════════════════════════════════════════════════
    private MouvementML toMouvementML(ProductionLog p) {
        // createdAt : confirmé dans ProductionLog.java et dans la DB
        LocalDateTime dt  = p.getCreatedAt() != null ? p.getCreatedAt() : LocalDateTime.now();
        boolean isNight   = dt.getHour() >= 22 || dt.getHour() <= 5;
        boolean isWeekend = dt.getDayOfWeek().getValue() >= 6;

        // qtyBefore, qtyRequested, qtyAfter : confirmés dans ProductionLog.java
        double qteAvant  = p.getQtyBefore()    != null ? p.getQtyBefore()    : 0.0;
        double qteSortie = p.getQtyRequested() != null ? p.getQtyRequested() : 0.0;

        // userName : confirmé (colonne user_name)
        // itemCode : confirmé (colonne item_code)
        // location : confirmé (colonne location)
        // deviceInfo : confirmé (colonne device_info)
        // source : enum SourceType (MOBILE/WEB), confirmé
        // operationType : enum OperationType (TOTALE/PARTIELLE), confirmé
        // lotCode : confirmé (colonne lot_code)
        // warehouse : confirmé (colonne warehouse) — utilisé comme magasin

        return MouvementML.builder()
                .operateur(p.getUserName())
                .lot(p.getLotCode())
                .article(p.getItemCode())
                // warehouse existe dans ProductionLog — on l'utilise comme magasin
                // Le convertir en Double si possible, sinon null
                .magasin(parseWarehouse(p.getWarehouse()))
                .emplacement(p.getLocation())
                .source(p.getSource()        != null ? p.getSource().name()        : null)
                .appareil(p.getDeviceInfo())
                .type(p.getOperationType() != null ? p.getOperationType().name() : null)
                .qteAvant(qteAvant)
                .qteSortie(qteSortie)
                .heure(dt.getHour())
                .jourSemaine(dt.getDayOfWeek().getValue() - 1)
                .mois(dt.getMonthValue())
                .weekend(isWeekend ? 1 : 0)
                .hasLot(p.getLotCode() != null && !p.getLotCode().isBlank() ? 1 : 0)
                // warehouse est présent dans la DB (colonne "09", "10"...) donc magasin NON manquant
                .magasinManquant(p.getWarehouse() == null ? 1 : 0)
                .emplacementManquant(p.getLocation()      == null ? 1 : 0)
                .articleManquant(p.getItemCode()       == null ? 1 : 0)
                .operateurManquant(p.getUserName()       == null ? 1 : 0)
                .zeroStockBefore(qteAvant == 0 ? 1 : 0)
                .nightOperation(isNight ? 1 : 0)
                .build();
    }

    // Convertit le warehouse ("09", "10") en Double pour le champ magasin
    private Double parseWarehouse(String warehouse) {
        if (warehouse == null) return null;
        try { return Double.parseDouble(warehouse); }
        catch (NumberFormatException e) { return null; }
    }

    // ════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ════════════════════════════════════════════════════════════════════════

    private AnomalyResponse callFastApi(MlAnomalyRequest request) {
        try {
            return mlRestTemplate.postForObject("/detect/anomaly", request, AnomalyResponse.class);
        } catch (RestClientException e) {
            log.warn("[ML-Scheduler] FastAPI indisponible : {}", e.getMessage());
            return null;
        }
    }

    private void publishAlert(AnomalyAlertDTO alert, Long id) {
        try {
            rabbitTemplate.convertAndSend(exchange, alertRoutingKey, alert);
            log.info("[ML-Scheduler] ✅ Alerte publiée id={}, score={}", id, alert.getAnomalyScore());
        } catch (AmqpException e) {
            log.warn("[ML-Scheduler] RabbitMQ indisponible id={} : {}", id, e.getMessage());
        }
    }

    private void logJsonDebug(MlAnomalyRequest request) {
        try {
            String json = objectMapper.writeValueAsString(request);
            log.debug("[ML-Scheduler] JSON → FastAPI ({} bytes) : {}",
                    json.length(), json.substring(0, Math.min(400, json.length())));
        } catch (JsonProcessingException ignored) {}
    }

    private AnomalyAlertDTO buildAlert(ProductionLog p, AnomalyResult r) {
        return AnomalyAlertDTO.builder()
                .transferId(p.getId())
                .operateur(p.getUserName())
                .article(p.getItemCode())
                .emplacement(p.getLocation())
                .qteAvant(p.getQtyBefore())
                .qteSortie(p.getQtyRequested())
                .qteApres(p.getQtyAfter())
                .source(p.getSource() != null ? p.getSource().name() : null)
                .appareil(p.getDeviceInfo())
                .anomalyScore(r.getAnomalyScoreConsensus())
                .isoFlag(r.getIsoFlag())
                .lofFlag(r.getLofFlag())
                .mouvementDate(p.getCreatedAt())
                .recipientEmail(recipientEmail)
                .build();
    }

    private AnomalyAlertDTO buildTestAlert(ProductionLog p) {
        return AnomalyAlertDTO.builder()
                .transferId(p.getId())
                .operateur(p.getUserName())
                .article(p.getItemCode())
                .emplacement(p.getLocation())
                .qteAvant(p.getQtyBefore())
                .qteSortie(p.getQtyRequested())
                .qteApres(p.getQtyAfter())
                .source(p.getSource() != null ? p.getSource().name() : "MOBILE")
                .appareil(p.getDeviceInfo())
                .anomalyScore(0.99)
                .isoFlag(1)
                .lofFlag(1)
                .mouvementDate(p.getCreatedAt())
                .recipientEmail(recipientEmail)
                .build();
    }

    // ════════════════════════════════════════════════════════════════════════
    // DTOs INTERNES (réponse FastAPI)
    // ════════════════════════════════════════════════════════════════════════

    @Data @JsonIgnoreProperties(ignoreUnknown = true)
    static class AnomalyResponse {
        @JsonProperty("count_total")     private int           countTotal;
        @JsonProperty("count_anomalies") private int           countAnomalies;
        @JsonProperty("count_alerts")    private int           countAlerts;
        @JsonProperty("threshold")       private double        threshold;
        @JsonProperty("results")         private List<AnomalyResult> results;
    }

    @Data @JsonIgnoreProperties(ignoreUnknown = true)
    static class AnomalyResult {
        @JsonProperty("index")                   private int     index;
        @JsonProperty("iso_flag")                private int     isoFlag;
        @JsonProperty("lof_flag")                private int     lofFlag;
        @JsonProperty("anomaly_consensus")       private int     anomalyConsensus;
        @JsonProperty("anomaly_score_consensus") private double  anomalyScoreConsensus;
        @JsonProperty("is_alert")                private boolean isAlert;
    }
}