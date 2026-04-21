package com.example.CWMS.dto;

import lombok.*;
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
}