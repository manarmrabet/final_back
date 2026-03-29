package com.example.CWMS.transfer.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErpStockWithLabelDTO {

    private String lotNumber;
    private String itemCode;
    private String itemLabel;       // t_dsca depuis dbo_ttcibd001120 (join)
    private String warehouseCode;   // t_cwar depuis dbo_twhinr1401200
    private String location;        // t_loca
    private int    quantityAvailable;
    private String lineStatus;
}