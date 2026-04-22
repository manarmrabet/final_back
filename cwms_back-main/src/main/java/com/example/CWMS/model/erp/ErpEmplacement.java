package com.example.CWMS.model.erp;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
@Entity
@Table(name = "dbo_ttccom100310")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErpEmplacement {

    @Id
    @Column(name = "t_bpid")
    private String locationCode;

    /** Nom complet de l'emplacement */
    @Column(name = "t_nama", length = 255)
    private String locationName;

    /** Titre court */
    @Column(name = "t_ctit")
    private String shortTitle;


    /** Classe / type de l'emplacement (MAGASIN, ZONE, ALLÉE...) */
    @Column(name = "t_clan")
    private String locationType;

    /** Pays */
    @Column(name = "t_ccur")
    private String country;

    /** Responsable */
    @Column(name = "t_sndr")
    private String manager;

    /** Téléphone */
    @Column(name = "t_fovn")
    private String phone;

    /** Date de début d'activité */
    @Column(name = "t_stdt")
    private LocalDate startDate;

    /** Date de fin d'activité */
    @Column(name = "t_endt")
    private LocalDate endDate;

    /** Actif ? (date fin nulle = actif) */
    @Transient
    public boolean isActive() {
        return endDate == null || endDate.isAfter(LocalDate.now());
    }
}