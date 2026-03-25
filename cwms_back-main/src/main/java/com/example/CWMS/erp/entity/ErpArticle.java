package com.example.CWMS.erp.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Entité ERP — Table dbo_ttdipu001120
 * READ ONLY.
 *
 * CORRECTION : colonnes alignées sur le vrai schéma de la table.
 * Colonnes supprimées car absentes : t_cuni, t_csgp
 * (t_cuni est dans dbo_ttcibd001120, pas ici)
 */
@Entity
@Table(name = "dbo_ttdipu001120")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErpArticle {

    @Id
    @Column(name = "t_item")
    private String itemCode;

    /** Désignation */
    @Column(name = "t_seak", length = 100)
    private String designation;

    /** Unité d'achat */
    @Column(name = "t_cuqp")
    private String purchaseUnit;

    /** Unité de prix */
    @Column(name = "t_cupp")
    private String priceUnit;

    /** Groupe article */
    @Column(name = "t_cpgp")
    private String itemGroup;

    /** Entrepôt par défaut */
    @Column(name = "t_cwar")
    private String defaultWarehouse;

    /** Type de planification */
    @Column(name = "t_prip")
    private String planningType;

    /** Taux de TVA */
    @Column(name = "t_cvat")
    private String vatRate;

    /** Acheteur responsable */
    @Column(name = "t_buyr")
    private String buyer;

    /** Fournisseur principal */
    @Column(name = "t_ccur")
    private String mainSupplier;

    /** Statut article */
    @Column(name = "t_suti")
    private String itemStatus;

    /** Qualité requise */
    @Column(name = "t_qual")
    private String quality;

    /** Numéro douanier */
    @Column(name = "t_hstd")
    private String customsNumber;
}