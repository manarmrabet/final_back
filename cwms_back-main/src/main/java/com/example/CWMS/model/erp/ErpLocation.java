package com.example.CWMS.model.erp;

import jakarta.persistence.*;
import lombok.*;

/**
 * Entité ERP — Table dbo_twhwmd300310 (emplacements Warehouse Management).
 *
 * C'est la VRAIE table des emplacements WH Infor LN.
 * Clé naturelle : (t_cwar, t_loca).
 */
@Entity
@Table(name = "dbo_twhwmd300310")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(ErpLocationId.class)
public class ErpLocation {

    /** Code magasin (t_cwar) */
    @Id
    @Column(name = "t_cwar", length = 3, nullable = false)
    private String warehouseCode;

    /** Code emplacement (t_loca) */
    @Id
    @Column(name = "t_loca", length = 6, nullable = false)
    private String locationCode;

    /** Description de l'emplacement (t_dsca) */
    @Column(name = "t_dsca", length = 30)
    private String description;

    /** Clé de recherche (t_seak) */
    @Column(name = "t_seak", length = 30)
    private String searchKey;

    /** Statut de l'emplacement (t_strt) — 1=actif, 0=inactif */
    @Column(name = "t_strt")
    private Integer status;

    /** Colonne physique (t_coln) */
    @Column(name = "t_coln")
    private Integer column;

    /** Rack / étagère (t_rack) */
    @Column(name = "t_rack")
    private Integer rack;

    /** Zone (t_zone) */
    @Column(name = "t_zone", length = 3)
    private String zone;

    /** Type d'emplacement (t_loct) */
    @Column(name = "t_loct")
    private Integer locationType;

    /** Blocage réception (t_ball) */
    @Column(name = "t_ball")
    private Integer blockedAll;

    /** Emplacement fermé (t_oclo) */
    @Column(name = "t_oclo")
    private Integer closed;

    /** Autorise entrée (t_inca) */
    @Column(name = "t_inca")
    private Integer allowInbound;

    /** Autorise sortie (t_inlo / t_outl) */
    @Column(name = "t_outl")
    private Integer allowOutbound;

    /**
     * Retourne true si l'emplacement est actif et disponible pour un mouvement.
     * Règle : status actif (t_strt = 1) ET non fermé (t_oclo = 0).
     */
    @Transient
    public boolean isActive() {
        // Dans l'ERP : status 0 ou 1 sont OK, et t_oclo (closed) doit être 2 (Ouvert)
        boolean active = (status != null && (status == 1 || status == 0));
        boolean notClosed = (closed != null && closed == 2);
        return active && notClosed;
    }

    /**
     * Retourne true si l'emplacement autorise les entrées de stock (t_inca).
     */
    @Transient
    public boolean acceptsInbound() {
        // Doit être actif ET allowInbound doit être 2 (Autorisé)
        return isActive() && (allowInbound != null && allowInbound == 2);
    }

    /**
     * Retourne true si l'emplacement autorise les sorties de stock (t_outl).
     */
    @Transient
    public boolean acceptsOutbound() {
        // Doit être actif ET allowOutbound doit être 2 (Autorisé)
        return isActive() && (allowOutbound != null && allowOutbound == 2);
    }
}
