package com.example.CWMS.dto;

import lombok.Data;

/**
 * DTO de transfert — une ligne d'étiquette.
 *
 * Mapping ERP → DTO  (tables autorisées uniquement)
 * ─────────────────────────────────────────────────────────────────────
 *  twhinh312310.t_rcno   → rcno
 *  twhinh312310.t_rcln   → rcln
 *  twhinh312310.t_sfbp   → sfbp
 *  'COFAT TUNIS'          → company         (valeur fixe)
 *  'A'                    → rotationClass   (valeur fixe)
 *  twhinh312310.t_item   → item
 *  ttcibd001310.t_dsca   → description      (fallback t_dscb > item)
 *  twhltc100310.t_ldat   → validityDate
 *  twhinh312310.t_qrec   → qty
 *  twhinh312310.t_clot   → labelNumber
 *  twhltc100310.t_frdt   → dateLabel
 *  twhwmd400310.t_locc   → location
 *  DATEPART(t_ldat)       → weekIncoming    (calculé)
 *  twhinh312310.t_mpnr   → mpnr
 *  twhltc100310.t_orno   → linkedOrder      ← NOUVEAU (RFr sur étiquette photo)
 *  ttcibd001120.t_cwun   → unit             ← NOUVEAU
 * ─────────────────────────────────────────────────────────────────────
 */
@Data
public class EtiquetteDTO {
    private String rcno;          // Numéro réception
    private String rcln;          // Ligne réception
    private String sfbp;          // Code fournisseur
    private String company;       // "COFAT TUNIS"
    private String rotationClass; // "A"
    private String item;          // Code article
    private String description;   // Description article
    private String validityDate;  // dd/MM/yyyy — depuis t_ldat
    private String qty;           // Quantité reçue
    private String labelNumber;   // Numéro lot (t_clot)
    private String dateLabel;     // dd/MM/yyyy — depuis t_frdt
    private String location;      // Emplacement (t_locc)
    private String weekIncoming;  // ex: "16/2026"
    private String mpnr;          // Manufacturer Part Number
    private String linkedOrder;   // Ordre lié (t_orno) — affiché comme "RFr"
    private String unit;          // Unité (t_cwun)
}