// ─────────────────────────────────────────────────────────────────────────────
// FILE 5 : TransferDashboardDTO.java  (Stats pour Angular)
// ─────────────────────────────────────────────────────────────────────────────
package com.example.CWMS.transfer.dto;

import lombok.*;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferDashboardDTO {

    /** Nombre par statut : {PENDING: 5, DONE: 42, ERROR: 1} */
    private Map<String, Long> countByStatus;

    /** Total transferts aujourd'hui */
    private long totalToday;

    /** Total transferts cette semaine */
    private long totalThisWeek;

    /** Top 5 articles les plus transférés */
    private List<TopItemDTO> topItems;

    @Data
    @AllArgsConstructor
    public static class TopItemDTO {
        private String itemCode;
        private String itemLabel;
        private long   count;
    }
}