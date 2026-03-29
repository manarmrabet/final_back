package com.example.CWMS.transfer.dto;

import lombok.*;
import java.util.List;
import java.util.Map;

/**
 * DTO Dashboard Transferts — affiché sur la page web et le dashboard principal.
 *
 * Statistiques incluses :
 *  - countByStatus      : comptage par statut (PENDING/DONE/ERROR/CANCELLED)
 *  - totalToday         : nombre de transferts aujourd'hui
 *  - totalThisWeek      : nombre de transferts cette semaine
 *  - totalThisMonth     : nombre de transferts ce mois
 *  - topItems           : top 5 articles les plus mouvementés (7 jours)
 *  - topOperators       : top 3 opérateurs les plus actifs (7 jours)
 *  - topSourceLocations : top 3 emplacements sources les plus utilisés
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferDashboardDTO {

    /** Nombre par statut : {PENDING: 5, DONE: 42, ERROR: 1, CANCELLED: 0} */
    private Map<String, Long> countByStatus;

    /** Transferts aujourd'hui */
    private long totalToday;

    /** Transferts cette semaine (7 jours glissants) */
    private long totalThisWeek;

    /** Transferts ce mois */
    private long totalThisMonth;

    /** Top 5 articles  plus transférés */
    private List<TopItemDTO> topItems;

    /** Top 3 opérateurs les plus actifs */
    private List<TopOperatorDTO> topOperators;

    /** Top 3 emplacements sources */
    private List<TopLocationDTO> topSourceLocations;

    // ─── Inner classes ────────────────────────────────────────────────────

    @Data
    @AllArgsConstructor
    public static class TopItemDTO {
        private String itemCode;
        private String itemLabel;
        private long   count;
    }

    @Data
    @AllArgsConstructor
    public static class TopOperatorDTO {
        private String operatorName;
        private long   count;
    }

    @Data
    @AllArgsConstructor
    public static class TopLocationDTO {
        private String location;
        private long   count;
    }
}