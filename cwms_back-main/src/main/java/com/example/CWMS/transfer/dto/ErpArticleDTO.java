// ─────────────────────────────────────────────────────────────────────────────
// FILE 3 : ErpArticleDTO.java  (ERP → Web/Mobile)
// ─────────────────────────────────────────────────────────────────────────────
package com.example.CWMS.transfer.dto;

import com.example.CWMS.erp.entity.ErpArticle;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErpArticleDTO {

    private String itemCode;
    private String designation;
    private String unit;          // t_cuqp (unité d'achat)
    private String itemGroup;
    private String defaultWarehouse;
    private String itemStatus;

    public static ErpArticleDTO from(ErpArticle a) {
        return ErpArticleDTO.builder()
                .itemCode(a.getItemCode())
                .designation(a.getDesignation())
                .unit(a.getPurchaseUnit())
                .itemGroup(a.getItemGroup())
                .defaultWarehouse(a.getDefaultWarehouse())
                .itemStatus(a.getItemStatus())
                .build();
    }
}