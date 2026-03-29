package com.example.CWMS.transfer.model;

import com.example.CWMS.audit.Auditable;
import com.example.CWMS.model.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Entité CWMSDB — Table stock_transfers
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

    // Emplacements (comme avant)
    @Column(name = "source_location", nullable = false, length = 50)
    private String sourceLocation;

    @Column(name = "dest_location", nullable = false, length = 50)
    private String destLocation;

    // === NOUVELLES COLONNES ===
    @Column(name = "source_warehouse", length = 50)
    private String sourceWarehouse;

    @Column(name = "dest_warehouse", length = 50)
    private String destWarehouse;

    // Quantité
    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "unit", length = 20)
    private String unit;

    // Statut & Type
    @Column(name = "status", length = 20)
    @Builder.Default
    private String status = TransferStatus.PENDING;

    @Column(name = "transfer_type", length = 30)
    @Builder.Default
    private String transferType = TransferType.INTERNAL_RELOCATION;

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

    // Lifecycle
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) status = TransferStatus.PENDING;
        if (transferType == null) transferType = TransferType.INTERNAL_RELOCATION;
    }

    // Constantes
    public static final class TransferStatus {
        public static final String PENDING   = "PENDING";
        public static final String DONE      = "DONE";
        public static final String ERROR     = "ERROR";
        public static final String CANCELLED = "CANCELLED";
        private TransferStatus() {}
    }

    public static final class TransferType {
        public static final String PUTAWAY              = "PUTAWAY";
        public static final String INTERNAL_RELOCATION  = "INTERNAL_RELOCATION";
        public static final String REPLENISHMENT        = "REPLENISHMENT";
        private TransferType() {}
    }
}