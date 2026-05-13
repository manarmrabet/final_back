package com.example.CWMS.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ArchiveLogDTO {
    private Long id;
    private LocalDateTime createdAt;
    private String username;
    private String action;
    private String eventType;
    private String severity;
    private String endpoint;
    private Integer statusCode;
    private String oldValue;
    private String newValue;
}