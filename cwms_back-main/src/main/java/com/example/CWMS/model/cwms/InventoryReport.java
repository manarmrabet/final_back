package com.example.CWMS.model.cwms;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_reports")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private InventorySession session;

    @Column(name = "total_erp")
    private int totalErp;

    @Column(name = "total_collecte")
    private int totalCollecte;

    @Column(name = "total_conforme")
    private int totalConforme;

    @Column(name = "total_ecart")
    private int totalEcart;

    @Column(name = "total_manquant")
    private int totalManquant;

    @Column(name = "total_surplus")
    private int totalSurplus;

    @Column(name = "report_json", columnDefinition = "NVARCHAR(MAX)")
    private String reportJson;

    @Column(name = "generated_at")
    private LocalDateTime generatedAt;

    @Column(name = "generated_by")
    private String generatedBy;

    // FIX : pas de @PrePersist — on set generatedAt manuellement dans le service
    // pour permettre la mise à jour lors de la régénération
    @PrePersist
    protected void onCreate() {
        if (this.generatedAt == null) this.generatedAt = LocalDateTime.now();
    }
}