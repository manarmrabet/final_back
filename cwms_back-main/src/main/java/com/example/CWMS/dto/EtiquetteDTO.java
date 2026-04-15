package com.example.CWMS.dto;

import lombok.Data;

/**
 * DTO de transfert pour une ligne d'étiquette.
 * Tous les champs sont des String pour simplifier le rendu PDF.
 *
 * Mapping colonnes ERP → champs DTO :
 *
 *  twhinh312310.t_rcno   → rcno
 *  twhinh312310.t_rcln   → rcln
 *  twhinh312310.t_sfbp   → sfbp
 *  fixe 'COFAT TUNIS'    → company
 *  fixe 'A'              → rotationClass  (tqmptc020120 absent)
 *  twhinh312310.t_item   → item
 *  ttcibd001120.t_dsca   → description
 *  twhltc100310.t_ldat   → validityDate   (t_idat absent de twhinh312310)
 *  twhinh312310.t_qrec   → qty
 *  twhinh312310.t_clot   → labelNumber
 *  twhltc100310.t_frdt   → dateLabel      (t_crdt absent de twhinh312310)
 *  twhwmd400310.t_locc   → location       (t_clan absent de twhwmd400310)
 *  calculé DATEPART      → weekIncoming
 *  twhinh312310.t_mpnr   → mpnr           (Manufacturer Part Number)
 */
@Data
public class EtiquetteDTO {
    private String rcno;           // Numéro réception
    private String rcln;           // Ligne réception
    private String sfbp;           // Code fournisseur
    private String company;        // "COFAT TUNIS" (valeur fixe)
    private String rotationClass;  // "A" (valeur fixe, tqmptc020120 absent)
    private String item;           // Code article
    private String description;    // Description article (t_dsca > t_dscb > item)
    private String validityDate;   // Date lot formatée dd/MM/yyyy
    private String qty;            // Quantité reçue
    private String labelNumber;    // Numéro de lot / étiquette (t_clot)
    private String dateLabel;      // Date fabrication formatée dd/MM/yyyy
    private String location;       // Emplacement (t_locc)
    private String weekIncoming;   // Semaine calculée ex: "16/2026"
    private String mpnr;           // Manufacturer Part Number (t_mpnr)
}