package com.example.CWMS.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportLineDTO {
    private String locationCode;
    private String itemCode;
    private String designation;
    private String lotNumber;
    private String warehouseCode;
    private String unit;
    private double quantiteErp;
    private double quantiteCollecte;
    private double ecart;
    private String statut;
}