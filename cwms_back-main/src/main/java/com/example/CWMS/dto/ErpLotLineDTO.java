package com.example.CWMS.dto;

import lombok.*;

/**
 * DTO représentant une ligne d'un lot.
 * Un lot peut contenir plusieurs articles → une instance par article.
 *
 * Retourné par GET /api/erp/stock/lot-details/{lotNumber}
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErpLotLineDTO {

    private Long   id;
    private String lotNumber;           // Numéro du lot scanné
    private String itemCode;            // Code article de cette ligne
    private String designation;// Libellé article (enrichi depuis ErpArticle)
    private String searchName;
    private String unit;                // Unité de mesure
    private String category;            // Catégorie article (t_citg)
    private String location;            // Emplacement physique (t_loca)
    private String warehouseCode;       // Entrepôt (t_cwar)
    private int    quantityAvailable;   // Quantité disponible (t_qhnd)
    private int    quantityBlocked;     // Quantité bloquée (t_qblk)
    private String entryDate;           // Date inventaire formatée
    private String lastTransactionDate; // Date dernière transaction
    private String status;              // AVAILABLE / BLOCKED / PARTIAL_BLOCK / EMPTY
}