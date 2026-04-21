package com.example.CWMS.model.cwms;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "collect_templates")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CollectTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    /**
     * Champs configurables stockés en JSON.
     * Exemple : ["ARTICLE","LOT","EMPLACEMENT","QUANTITE"]
     * Les champs ARTICLE et LOT doivent correspondre exactement
     * aux valeurs scannées comparées avec ErpStock.itemCode et ErpStock.lotNumber.
     */
    @Column(name = "fields_json", nullable = false, length = 1000)
    private String fieldsJson;

    @Column(nullable = false)
    private boolean active = true;
}