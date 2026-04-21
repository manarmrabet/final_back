package com.example.CWMS.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
public class ProductionStatsDTO {
    private Long   totalOpsToday;
    private Double totalQtyToday;
    private Long   failedToday;
    private List<ProductionLogDTO> recentLogs;
    private List<OperatorStatDTO>  operatorStats;

    @Data @AllArgsConstructor @NoArgsConstructor
    public static class OperatorStatDTO {
        private Long   userId;
        private String userName;
        private Long   nbOps;
        private Double totalQty;
    }
}