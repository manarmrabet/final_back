package com.example.CWMS.erp.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * Entité ERP — Table dbo_twhinr1401200
 * Représente les mouvements/lignes de stock dans l'ERP.
 * READ ONLY — aucune méthode d'écriture ne doit utiliser cette entité.
 *
 * Colonnes clés pour le transfert interne :
 *   t_item  = code article
 *   t_loca  = emplacement actuel
 *   t_clot  = numéro de lot
 *   t_ball  = quantité disponible
 *   t_bout  = quantité sortie
 */
@Entity
@Table(name = "dbo_twhinr1401200")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErpStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_stockage")
    private Long idStockage;

    /** Code article — clé de jointure avec ErpArticle */
    @Column(name = "t_item")
    private String itemCode;

    /** Emplacement physique (ex: ZONE-B1, MAG-A, RECEPTION) */
    @Column(name = "t_loca")
    private String location;

    /** Numéro de lot */
    @Column(name = "t_clot")
    private String lotNumber;

    /** Date d'entrée en stock */
    @Column(name = "t_idat")
    private LocalDate entryDate;

    /** Quantité disponible en stock */
    @Column(name = "t_ball")
    private String quantityAvailable;

    /** Quantité sortie */
    @Column(name = "t_bout")
    private String quantityOut;

    /** Quantité bloquée/réservée */
    @Column(name = "t_btri")
    private String quantityBlocked;

    /** Entrepôt / warehouse code */
    @Column(name = "t_cwar")
    private String warehouseCode;

    /** Référence commande */
    @Column(name = "t_qord")
    private String orderRef;

    /** Cycle de comptage */
    @Column(name = "t_bcyc")
    private String countCycle;

    /** Date de création enregistrement */
    @Column(name = "t_crdt")
    private LocalDate createdDate;

    /** Date dernière transaction */
    @Column(name = "t_trdt")
    private LocalDate lastTransactionDate;

    /** Statut ligne stock */
    @Column(name = "t_lsid")
    private String lineStatus;

    // Getters pratiques pour le service
    public int getAvailableQuantityAsInt() {
        try {
            if (quantityAvailable == null || quantityAvailable.isBlank()) return 0;
            return (int) Double.parseDouble(quantityAvailable.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}