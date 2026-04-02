package com.example.CWMS.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErpStockMobileDTO {
    private String itemCode;
    private String designation;
    private String lotNumber;
    private String location;
    private String warehouseCode;
    private double quantity;
    private String unit;
    private String date;
    private String status;
}