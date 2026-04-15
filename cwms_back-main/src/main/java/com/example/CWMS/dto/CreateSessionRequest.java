package com.example.CWMS.dto;

import lombok.*;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateSessionRequest {
    private String name;
    private String warehouseCode;
    private String warehouseLabel;

    /** Zone ERP optionnelle (t_zone de dbo_twhwmd300310) */
    private String warehouseZone;

    /**
     * Champs que l'opérateur veut collecter.
     * Sous-ensemble de : ARTICLE, LOT, EMPLACEMENT, QUANTITE
     * Sélectionnés par cases à cocher à la création de session.
     * Remplace le système de templates séparés.
     */
    private List<String> collectFields;
}