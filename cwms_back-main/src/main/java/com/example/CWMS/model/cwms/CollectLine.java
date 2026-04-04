package com.example.CWMS.model.cwms;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "collect_lines")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CollectLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private InventorySession session;

    /**
     * Zone / emplacement scanné sur le terrain.
     * Correspond à ErpStock.location (t_loca) pour la comparaison.
     */
    @Column(name = "location_code", nullable = false)
    private String locationCode;

    @Column(name = "location_label")
    private String locationLabel;

    /**
     * Valeurs scannées en JSON dynamique.
     * Clés selon le template : {"ARTICLE":"ART001","LOT":"L123","QUANTITE":"10"}
     * ARTICLE → ErpStock.itemCode
     * LOT     → ErpStock.lotNumber
     * QUANTITE → comparé à ErpStock.getQuantityAvailable()
     */
    @Column(name = "values_json", nullable = false, length = 2000)
    private String valuesJson;

    @Column(name = "scanned_by")
    private String scannedBy;

    @Column(name = "scanned_at")
    private LocalDateTime scannedAt;

    @PrePersist
    protected void onCreate() {
        this.scannedAt = LocalDateTime.now();
    }
}