package com.example.CWMS.dto;

import lombok.*;

/**
 * DTO exposant les informations d'un emplacement WH au client (Flutter / Angular).
 *
 * Source : dbo_twhwmd300310 via ErpLocationRepository.
 *
 * Champs retournés :
 *  - locationCode  (t_loca)
 *  - warehouseCode (t_cwar)
 *  - description   (t_dsca)
 *  - zone          (t_zone)
 *  - locationType  (t_loct)
 *  - active        (calculé depuis t_strt + t_oclo)
 *  - exists        true si trouvé dans twhwmd300310
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErpLocationDTO {

    private String  locationCode;
    private String  warehouseCode;
    private String  description;
    private String  zone;
    private Integer locationType;

    /** true si l'emplacement est actif et non fermé */
    private boolean active;

    /** true si l'emplacement existe dans dbo_twhwmd300310 */
    private boolean exists;
}