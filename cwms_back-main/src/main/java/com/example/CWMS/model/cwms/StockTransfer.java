package com.example.CWMS.model.cwms;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Entité CWMSDB — Table stock_transfers
 *
 * CORRECTION : status et transferType sont maintenant des enums Java (@Enumerated).
 * JPA stocke le nom en String ("DONE", "PENDING"...) mais le code est typé —
 * impossible de mettre une valeur invalide en base par erreur.
 */
@Entity
@Table(name = "stock_transfers")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Article
    @Column(name = "erp_item_code", nullable = false, length = 50)
    private String erpItemCode;

    @Column(name = "erp_item_label", length = 255)
    private String erpItemLabel;

    @Column(name = "lot_number", length = 50)
    private String lotNumber;

    // Emplacements
    @Column(name = "source_location", nullable = false, length = 50)
    private String sourceLocation;

    @Column(name = "dest_location", nullable = false, length = 50)
    private String destLocation;

    @Column(name = "source_warehouse", length = 50)
    private String sourceWarehouse;

    @Column(name = "dest_warehouse", length = 50)
    private String destWarehouse;

    // Quantité
    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "unit", length = 20)
    private String unit;

    // ✅ CORRECTION : enums typés au lieu de String libres
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    @Builder.Default
    private TransferStatus status = TransferStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "transfer_type", length = 30)
    @Builder.Default
    private TransferType transferType = TransferType.INTERNAL_RELOCATION;

    // Acteurs
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operator_id")
    private User operator;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "validated_by")
    private User validatedBy;

    // Dates
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "validated_at")
    private LocalDateTime validatedAt;

    // Métadonnées
    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null)       status       = TransferStatus.PENDING;
        if (transferType == null) transferType = TransferType.INTERNAL_RELOCATION;
    }

    // ─── Enums ────────────────────────────────────────────────────────────────

    public enum TransferStatus {
        PENDING, DONE, ERROR, CANCELLED
    }

    public enum TransferType {
        PUTAWAY, INTERNAL_RELOCATION, REPLENISHMENT
    }
}