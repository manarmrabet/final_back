package com.example.CWMS.dto;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.*;
import java.util.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder


public class TransferDashboardDTO {

    private Map<String, Long> countByStatus;
    private long totalToday;
    private long totalThisWeek;
    private long totalThisMonth;
    private List<TopItemDTO> topItems;
    private List<TopOperatorDTO> topOperators;
    private List<TopLocationDTO> topSourceLocations;

    // ✅ FIX SpotBugs — setters avec copie défensive
    public void setCountByStatus(Map<String, Long> countByStatus) {
        this.countByStatus = countByStatus == null ? null : new HashMap<>(countByStatus);
    }

    public void setTopItems(List<TopItemDTO> topItems) {
        this.topItems = topItems == null ? null : new ArrayList<>(topItems);
    }

    public void setTopOperators(List<TopOperatorDTO> topOperators) {
        this.topOperators = topOperators == null ? null : new ArrayList<>(topOperators);
    }

    public void setTopSourceLocations(List<TopLocationDTO> topSourceLocations) {
        this.topSourceLocations = topSourceLocations == null ? null : new ArrayList<>(topSourceLocations);
    }

    // ✅ FIX SpotBugs — getters protégés
    public Map<String, Long> getCountByStatus() {
        return countByStatus == null ? null : Collections.unmodifiableMap(countByStatus);
    }

    public List<TopItemDTO> getTopItems() {
        return topItems == null ? null : Collections.unmodifiableList(topItems);
    }

    public List<TopOperatorDTO> getTopOperators() {
        return topOperators == null ? null : Collections.unmodifiableList(topOperators);
    }

    public List<TopLocationDTO> getTopSourceLocations() {
        return topSourceLocations == null ? null : Collections.unmodifiableList(topSourceLocations);
    }

    @Data @AllArgsConstructor
    public static class TopItemDTO {
        private String itemCode;
        private String itemLabel;
        private long count;
    }

    @Data @AllArgsConstructor
    public static class TopOperatorDTO {
        private String operatorName;
        private long count;
    }

    @Data @AllArgsConstructor
    public static class TopLocationDTO {
        private String location;
        private long count;
    }
}