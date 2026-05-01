package com.example.CWMS.dto;

import lombok.*;
import java.util.Collections;
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

    // ✅ FIX SpotBugs — protection de la représentation interne
    public List<ErpLotLineDTO> getLots() {
        return lots == null ? null : Collections.unmodifiableList(lots);
    }
}