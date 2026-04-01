package com.example.CWMS.erp.dto;

import com.example.CWMS.erp.entity.ErpArticle;
import lombok.*;

/**
 * DTO article ERP — mappé sur dbo_ttcibd001120.
 *
 * Champ "unit" = t_cuni (unité de stock).
 * Le HTML Angular doit utiliser {{ art.unit }} et non art.storageUnit.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErpArticleDTO {

    private String itemCode;
    private String designation;
    /** Unité de stock (t_cuni) */
    private String unit;
    private String itemGroup;
    private String itemType;

    public static ErpArticleDTO from(ErpArticle a) {
        if (a == null) return null;
        return ErpArticleDTO.builder()
                .itemCode(a.getItemCode()    != null ? a.getItemCode().trim()    : null)
                .designation(a.getDesignation() != null ? a.getDesignation().trim() : "")
                .unit(a.getStockUnit())
                .itemGroup(a.getItemGroup())
                .itemType(a.getItemType())
                .build();
    }
}