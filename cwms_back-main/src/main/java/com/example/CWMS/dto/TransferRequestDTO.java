package com.example.CWMS.dto;

import jakarta.validation.constraints.*;
import lombok.*;

/**
 * DTO reçu depuis Flutter lors d'un transfert interne.
 *
 * Flutter envoie :
 *   - sourceLocation  = t_loca source  (ex: "RECEPTION")
 *   - destLocation    = emplacement destination scanné (ex: "ALLEE-A1")
 *   - sourceWarehouse = t_cwar source  (lu depuis le scan du lot ERP)
 *   - destWarehouse   = t_cwar dest    (lu depuis getLocationInfo côté Flutter)
 *   - quantity        = t_qhnd (quantité du lot entier)
 *   - lotNumber       = t_clot
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

    /**
     * Magasin source — envoyé par Flutter depuis warehouseSource du carton.
     * Correspond à t_cwar de la ligne ERP source.
     * Optionnel : si absent, le backend le lit depuis l'ERP.
     */
    private String sourceWarehouse;

    /**
     * Magasin destination — envoyé par Flutter depuis getLocationInfo().
     * Correspond au t_cwar à écrire sur la ligne ERP déplacée.
     * Optionnel : si absent, le backend le lit depuis l'ERP.
     */
    private String destWarehouse;

    @NotNull(message = "La quantité est obligatoire")
    @Min(value = 1, message = "La quantité doit être supérieure à 0")
    private Integer quantity;

    private String lotNumber;

    /**
     * PUTAWAY | INTERNAL_RELOCATION | REPLENISHMENT
     * Défaut : INTERNAL_RELOCATION
     */
    private String transferType;

    private String notes;
}