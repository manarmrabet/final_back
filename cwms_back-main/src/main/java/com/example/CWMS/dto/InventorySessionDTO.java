package com.example.CWMS.dto;

import com.example.CWMS.model.cwms.InventorySession.SessionStatus;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventorySessionDTO {
    private Long id;
    private String name;
    private String warehouseCode;
    private String warehouseLabel;

    /** Zone ERP (t_zone) — peut être null si non renseignée */
    private String warehouseZone;

    /**
     * Champs de collecte choisis à la création de la session.
     * Exemple : ["ARTICLE", "LOT", "QUANTITE"]
     */
    private List<String> collectFields;

    private SessionStatus status;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime validatedAt;
    private int totalLines;
}