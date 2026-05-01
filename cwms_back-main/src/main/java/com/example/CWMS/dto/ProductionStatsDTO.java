package com.example.CWMS.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Data
public class ProductionStatsDTO {
    private Long   totalOpsToday;
    private Double totalQtyToday;
    private Long   failedToday;
    private List<ProductionLogDTO> recentLogs;
    private List<OperatorStatDTO>  operatorStats;

    public void setRecentLogs(List<ProductionLogDTO> recentLogs) {
        this.recentLogs = recentLogs == null ? null : new ArrayList<>(recentLogs);
    }

    public void setOperatorStats(List<OperatorStatDTO> operatorStats) {
        this.operatorStats = operatorStats == null ? null : new ArrayList<>(operatorStats);
    }

    public List<ProductionLogDTO> getRecentLogs() {
        return recentLogs == null ? null : Collections.unmodifiableList(recentLogs);
    }

    public List<OperatorStatDTO> getOperatorStats() {
        return operatorStats == null ? null : Collections.unmodifiableList(operatorStats);
    }

    @Data @AllArgsConstructor @NoArgsConstructor
    public static class OperatorStatDTO {
        private Long   userId;
        private String userName;
        private Long   nbOps;
        private Double totalQty;
    }
}