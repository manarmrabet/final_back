// ─────────────────────────────────────────────────────────────────────────────
// FILE 4 : ErpStockDTO.java  (ERP Stock → Web/Mobile)
// ─────────────────────────────────────────────────────────────────────────────
package com.example.CWMS.transfer.dto;

import com.example.CWMS.erp.entity.ErpStock;
import lombok.*;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErpStockDTO {

    private Long   id;
    private String itemCode;
    private String location;
    private String lotNumber;
    private int    quantityAvailable;
    private String warehouseCode;
    private LocalDate entryDate;
    private LocalDate lastTransactionDate;
    private String lineStatus;
    private String itemLabel;   // désignation article depuis dbo_ttcibd001120

    public static ErpStockDTO from(ErpStock s) {
        return ErpStockDTO.builder()
                .id(s.getIdStockage())
                .itemCode(s.getItemCode())
                .location(s.getLocation())
                .lotNumber(s.getLotNumber())
                .quantityAvailable(s.getAvailableQuantityAsInt())
                .warehouseCode(s.getWarehouseCode())
                .entryDate(s.getEntryDate())
                .lastTransactionDate(s.getLastTransactionDate())
                .lineStatus(s.getLineStatus())
                .build();
    }
}