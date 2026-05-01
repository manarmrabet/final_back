package com.example.CWMS.dto;

import lombok.*;
import java.util.Collections;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddCollectLineRequest {
    private Long sessionId;
    private String locationCode;
    private String locationLabel;
    private Map<String, String> values;

    // ✅ FIX SpotBugs — protection de la représentation interne
    public Map<String, String> getValues() {
        return values == null ? null : Collections.unmodifiableMap(values);
    }
}