package com.example.CWMS.model.cwms;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "inventory_sessions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventorySession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    // Code magasin ERP (t_cwar dans ErpStock.warehouseCode)
    @Column(name = "warehouse_code", nullable = false)
    private String warehouseCode;

    @Column(name = "warehouse_label")
    private String warehouseLabel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionStatus status;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "validated_at")
    private LocalDateTime validatedAt;

    @Column(name = "validated_by")
    private String validatedBy;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<CollectLine> lines;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) this.status = SessionStatus.EN_COURS;
    }

    public enum SessionStatus {
        EN_COURS, VALIDEE, CLOTUREE
    }
}