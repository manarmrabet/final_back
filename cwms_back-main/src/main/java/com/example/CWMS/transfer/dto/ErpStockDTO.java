package com.example.CWMS.transfer.dto;

import com.example.CWMS.erp.entity.ErpStock;
import lombok.*;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErpStockDTO {
    private Long id;
    private String itemCode;
    private String designation; // <--- AJOUTER
    private String unit;        // <--- AJOUTER
    private String location;
    private String lotNumber;
    private String warehouseCode;
    private int quantityAvailable;
    private int quantityBlocked;
    private LocalDate entryDate;
    private LocalDate lastTransactionDate;
    private String status;
    private String lineStatus;
    private String itemLabel;


    public static ErpStockDTO from(ErpStock s) {
        return ErpStockDTO.builder()
                .id(s.getIdStockage())
                .itemCode(s.getItemCode() != null ? s.getItemCode().trim() : null)
                .location(s.getLocation() != null ? s.getLocation().trim() : "N/A")
                .lotNumber(s.getLotNumber() != null ? s.getLotNumber().trim() : "N/A")
                .warehouseCode(s.getWarehouseCode() != null ? s.getWarehouseCode().trim() : "N/A")
                .quantityAvailable(s.getAvailableQuantityAsInt())
                .quantityBlocked(s.getBlockedQuantityAsInt())
                .entryDate(s.getEntryDate())
                .lastTransactionDate(s.getLastTransactionDate())
                .status(s.getComputedStatus())
                .lineStatus(s.getLineStatus())
                // .unit("") // Vous pouvez ajouter des valeurs par défaut ici si nécessaire
                .build();
    }
}