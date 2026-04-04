package com.example.CWMS.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryReportDTO {
    private Long id;
    private Long sessionId;
    private String sessionName;
    private String warehouseCode;
    private int totalErp;
    private int totalCollecte;
    private int totalConforme;
    private int totalEcart;
    private int totalManquant;
    private int totalSurplus;
    private LocalDateTime generatedAt;
    private List<ReportLineDTO> lines;
}