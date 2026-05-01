package com.example.CWMS.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// ══════════════════════════════════════════════════════════════════════════════
//  Réponse reçue de POST /detect/anomaly
// ══════════════════════════════════════════════════════════════════════════════
class MlAnomalyResponse {

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data public static class Root {
        @JsonProperty("count_total")     private int countTotal;
        @JsonProperty("count_anomalies") private int countAnomalies;
        @JsonProperty("count_alerts")    private int countAlerts;
        @JsonProperty("threshold")       private double threshold;
        @JsonProperty("results")         private List<Result> results;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data public static class Result {
        @JsonProperty("index")                    private int index;
        @JsonProperty("iso_flag")                 private int isoFlag;
        @JsonProperty("lof_flag")                 private int lofFlag;
        @JsonProperty("anomaly_consensus")        private int anomalyConsensus;
        @JsonProperty("anomaly_score_consensus")  private double anomalyScoreConsensus;
        @JsonProperty("is_alert")                 private boolean isAlert;
    }
}