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

    @Column(name = "t_kitm")
    private String itemType;

    @Column(name = "t_seak")
    private String searchName;

    @Column(name = "t_seab")
    private String searchName2;

    /** FIX: Cette méthode permet au service de récupérer le segment (SF, PF, PR) */
    public String getItemCategory() {
        return this.itemGroup;
    }

    public String getPurchaseUnit() {
        return this.stockUnit;
    }
}