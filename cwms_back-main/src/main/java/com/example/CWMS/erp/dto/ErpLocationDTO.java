package com.example.CWMS.erp.dto;

import lombok.*;

/**
 * DTO retourné par GET /transfers/erp/location/{locationCode}
 * Permet au mobile de détecter un transfert inter-magasin.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErpLocationDTO {
    private String locationCode;
    private String warehouseCode;  // t_cwar depuis dbo_twhinr1401200
    private String locationType;   // t_clan depuis dbo_ttccom100120 (si trouvé)
}