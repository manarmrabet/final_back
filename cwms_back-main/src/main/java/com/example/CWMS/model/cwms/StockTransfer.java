package com.example.CWMS.model.cwms;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

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

    @Column(name = "erp_item_code", nullable = false, length = 50)
    private String erpItemCode;

    @Column(name = "erp_item_label", length = 255)
    private String erpItemLabel;

    @Column(name = "lot_number", length = 50)
    private String lotNumber;

    @Column(name = "source_location", nullable = false, length = 50)
    private String sourceLocation;

    @Column(name = "dest_location", nullable = false, length = 50)
    private String destLocation;

    @Column(name = "source_warehouse", length = 50)
    private String sourceWarehouse;

    @Column(name = "dest_warehouse", length = 50)
    private String destWarehouse;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "unit", length = 20)
    private String unit;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    @Builder.Default
    private TransferStatus status = TransferStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "transfer_type", length = 30)
    @Builder.Default
    private TransferType transferType = TransferType.INTERNAL_RELOCATION;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operator_id")
    private User operator;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "validated_by")
    private User validatedBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "validated_at")
    private LocalDateTime validatedAt;

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

    // ✅ FIX SpotBugs — entités JPA @ManyToOne
    // Hibernate gère le cycle de vie — accesseurs explicites sans copie
    public void setOperator(User operator) {
        this.operator = operator;
    }

    public User getOperator() {
        return operator;
    }

    public void setValidatedBy(User validatedBy) {
        this.validatedBy = validatedBy;
    }

    public User getValidatedBy() {
        return validatedBy;
    }

    // ─── Enums ───────────────────────────────────────────────────────────────

    public enum TransferStatus {
        PENDING, DONE, ERROR, CANCELLED
    }

    public enum TransferType {
        PUTAWAY, INTERNAL_RELOCATION, REPLENISHMENT
    }
}