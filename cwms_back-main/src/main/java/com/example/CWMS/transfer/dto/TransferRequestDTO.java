// ─────────────────────────────────────────────────────────────────────────────
// FILE 1 : TransferRequestDTO.java  (Mobile → Backend)
// ─────────────────────────────────────────────────────────────────────────────
package com.example.CWMS.transfer.dto;

import jakarta.validation.constraints.*;
import lombok.*;

/**
 * DTO reçu depuis Flutter lors d'un transfert interne.
 * Validé avec Bean Validation avant traitement.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferRequestDTO {

    @NotBlank(message = "Le code article est obligatoire")
    private String erpItemCode;

    @NotBlank(message = "L'emplacement source est obligatoire")
    private String sourceLocation;

    @NotBlank(message = "L'emplacement destination est obligatoire")
    private String destLocation;

    @NotNull(message = "La quantité est obligatoire")
    @Min(value = 1, message = "La quantité doit être supérieure à 0")
    private Integer quantity;

    /** Optionnel — si gestion par lot */
    private String lotNumber;

    /**
     * PUTAWAY | INTERNAL_RELOCATION | REPLENISHMENT
     * Défaut : INTERNAL_RELOCATION
     */
    private String transferType;

    /** Note libre de l'opérateur */
    private String notes;
}