package com.example.CWMS.model.cwms;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "ProductionLog")
public class ProductionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── Identification du lot ────────────────────────────────────────────
    @Column(name = "lot_code",   nullable = false, length = 50)
    private String lotCode;

    @Column(name = "item_code",  nullable = false, length = 50)
    private String itemCode;

    @Column(name = "warehouse",  nullable = false, length = 20)
    private String warehouse;

    @Column(name = "location",   length = 50)
    private String location;

    // ── Quantités ────────────────────────────────────────────────────────
    @Column(name = "qty_before",    nullable = false)
    private Double qtyBefore;

    @Column(name = "qty_requested", nullable = false)
    private Double qtyRequested;

    @Column(name = "qty_after",     nullable = false)
    private Double qtyAfter;

    // ── Type opération ────────────────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false, length = 20)
    private OperationType operationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private OperationStatus status = OperationStatus.SUCCESS;

    // ── Traçabilité — userId vient du JWT, jamais du body ─────────────────
    @Column(name = "user_id",   nullable = false)
    private Long userId;

    @Column(name = "user_name", length = 100)
    private String userName;

    @Column(name = "device_info", length = 200)
    private String deviceInfo;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    @Builder.Default
    private SourceType source = SourceType.MOBILE;

    // ── Audit ─────────────────────────────────────────────────────────────
    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "notes", length = 500)
    private String notes;

    // ── Enums ─────────────────────────────────────────────────────────────
    public enum OperationType   { TOTALE, PARTIELLE }
    public enum OperationStatus { SUCCESS, FAILED }
    public enum SourceType      { MOBILE, WEB }
}