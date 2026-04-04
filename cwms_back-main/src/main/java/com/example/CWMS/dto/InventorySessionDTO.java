package com.example.CWMS.dto;

import com.example.CWMS.model.cwms.InventorySession.SessionStatus;
import lombok.*;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventorySessionDTO {
    private Long id;
    private String name;
    private String warehouseCode;
    private String warehouseLabel;
    private SessionStatus status;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime validatedAt;
    private int totalLines;
}