// ─── ReceptionLineDTO.java ────────────────────────────────────────────────────
package com.example.CWMS.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class ReceptionLineDTO {
    private String fournisseur;
    private String descFrs;
    private String daeFact;
    private String lg;
    private String oa;
    private String cwar;
    private String article;
    private String description;
    private BigDecimal qteCdee;
    private BigDecimal qteRecue;
    private String dino;
    private String emplacement;
    private String dateReception;
    private String numeroReception;
    private String devise;
    private BigDecimal prixUnitaire;   // for valued PDF/Excel
    private BigDecimal valeurTotale;   // qteRecue * prixUnitaire
}

