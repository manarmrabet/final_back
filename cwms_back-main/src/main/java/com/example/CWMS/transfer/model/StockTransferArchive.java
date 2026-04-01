package com.example.CWMS.transfer.model;

import com.example.CWMS.model.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "stock_transfers_archive",
        indexes = {
                @Index(name = "idx_arch_created",   columnList = "createdAt"),
                @Index(name = "idx_arch_item",      columnList = "erpItemCode"),
                @Index(name = "idx_arch_status",    columnList = "status"),
                @Index(name = "idx_arch_operator",  columnList = "operator_id"),
                @Index(name = "idx_arch_warehouse", columnList = "sourceWarehouse,destWarehouse")
        }
)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class StockTransferArchive {

    /** Même ID que dans stock_transfers — pas d'auto-génération */
    @Id
    private Long id;

    @Column(length = 50)
    private String erpItemCode;

    @Column(length = 200)
    private String erpItemLabel;

    @Column(length = 50)
    private String lotNumber;

    @Column(length = 100)
    private String sourceLocation;

    @Column(length = 100)
    private String destLocation;

    @Column(length = 50)
    private String sourceWarehouse;

    @Column(length = 50)
    private String destWarehouse;

    private Integer quantity;

    @Column(length = 20)
    private String unit;

    /** Stocké en String pour éviter une dépendance à l'enum métier */
    @Column(length = 20)
    private String status;

    @Column(length = 30)
    private String transferType;

    @Column(length = 500)
    private String notes;

    @Column(length = 500)
    private String errorMessage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operator_id")
    private User operator;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "validated_by_id")
    private User validatedBy;

    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private LocalDateTime validatedAt;

    /** Date à laquelle le scheduler a archivé cette ligne */
    private LocalDateTime archivedAt;
}