package com.example.CWMS.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CollectLineDTO {
    private Long id;
    private Long sessionId;
    private String locationCode;
    private String locationLabel;
    private Map<String, String> values;
    private String scannedBy;
    private LocalDateTime scannedAt;
}