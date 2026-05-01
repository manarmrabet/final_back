package com.example.CWMS.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import java.util.List;

/**
 * MlAnomalyRequest — DTO vers FastAPI
 * Clés JSON 100% ASCII snake_case — correspond au mouvement.py FastAPI.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MlAnomalyRequest {

    @JsonProperty("mouvements")
    private List<MouvementML> mouvements;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MouvementML {

        @JsonProperty("operateur")            private String  operateur;
        @JsonProperty("lot")                  private String  lot;
        @JsonProperty("article")              private String  article;
        @JsonProperty("magasin")              private Double  magasin;
        @JsonProperty("emplacement")          private String  emplacement;
        @JsonProperty("source")               private String  source;
        @JsonProperty("appareil")             private String  appareil;
        @JsonProperty("type_mouvement")       private String  type;

        @JsonProperty("qte_avant")            private Double  qteAvant;
        @JsonProperty("qte_sortie")           private Double  qteSortie;

        @JsonProperty("heure")                private Integer heure;
        @JsonProperty("jour_semaine")         private Integer jourSemaine;
        @JsonProperty("mois")                 private Integer mois;
        @JsonProperty("weekend")              private Integer weekend;
        @JsonProperty("has_lot")              private Integer hasLot;
        @JsonProperty("magasin_manquant")     private Integer magasinManquant;
        @JsonProperty("emplacement_manquant") private Integer emplacementManquant;
        @JsonProperty("article_manquant")     private Integer articleManquant;
        @JsonProperty("operateur_manquant")   private Integer operateurManquant;
        @JsonProperty("zero_stock_before")    private Integer zeroStockBefore;
        @JsonProperty("night_operation")      private Integer nightOperation;
    }
}