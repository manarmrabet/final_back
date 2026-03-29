package com.example.CWMS.erp.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "dbo_ttcibd001120")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErpArticle {

    @Id
    @Column(name = "t_item")
    private String itemCode;

    @Column(name = "t_dsca")
    private String designation;

    @Column(name = "t_citg")
    private String itemGroup;

    @Column(name = "t_cuni")
    private String stockUnit;

    /** Ajout du champ manquant pour corriger l'erreur de compilation du DTO */
    @Column(name = "t_kitm")
    private String itemType;

    /** Alias pour la compatibilité avec TransferServiceImpl */
    public String getPurchaseUnit() {
        return this.stockUnit;
    }
}