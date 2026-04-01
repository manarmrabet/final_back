package com.example.CWMS.erp.dto;

import lombok.*;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErpArticleSummaryDTO {
    private String itemCode;
    private String designation;
    private String searchName;
    private String mainLot;
    private String mainWarehouse;
    private double totalQtyOnHand;
    private String unit;
    private String lastDate;
    private String itemCategory;

    // FIX: Changer ErpStockDTO par ErpLotLineDTO
    private List<ErpLotLineDTO> lots;
}