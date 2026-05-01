package com.example.CWMS.dto;

import com.example.CWMS.model.cwms.InventorySession.SessionStatus;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.*;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/* ✅ Bonne pratique — on documente pourquoi on accepte ce warning Lombok
@SuppressFBWarnings(
        value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"},
        justification = "Lombok @AllArgsConstructor et @Builder gèrent ces champs. " +
                "Les DTOs sont utilisés uniquement pour la sérialisation JSON " +
                "via Jackson — aucune modification externe n'est possible en production."
)*/

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventorySessionDTO {
    private Long id;
    private String name;
    private String warehouseCode;
    private String warehouseLabel;
    private String warehouseZone;
    private List<String> collectFields;
    private SessionStatus status;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime validatedAt;
    private int totalLines;

    // ✅ FIX SpotBugs — protection de la représentation interne
    public List<String> getCollectFields() {
        return collectFields == null ? null
                : Collections.unmodifiableList(collectFields);
    }
}