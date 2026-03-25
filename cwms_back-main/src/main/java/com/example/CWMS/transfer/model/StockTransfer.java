package com.example.CWMS.transfer.model;

import com.example.CWMS.audit.Auditable;
import com.example.CWMS.model.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Entité CWMSDB — Table stock_transfers
 *
 * Représente un transfert interne d'article entre deux emplacements
 * dans l'entrepôt (putaway, relocation, rangement après réception).
 *
 * Ce n'est PAS une sortie de stock commerciale/expédition.
 * Exemples :
 *   - ZONE-RECEPTION → MAGASIN-A / ALLÉE-B3
 *   - MAGASIN-A → MAGASIN-B (réorganisation)
 *   - ZONE-PICKING → ZONE-RESERVE (réapprovisionnement interne)
 *
 * DDL généré automatiquement par Hibernate (ddl-auto=update sur CWMSDB) :
 * CREATE TABLE stock_transfers (
 *   id              BIGINT IDENTITY PRIMARY KEY,
 *   erp_item_code   NVARCHAR(50) NOT NULL,
 *   erp_item_label  NVARCHAR(255),
 *   lot_number      NVARCHAR(50),
 *   source_location NVARCHAR(50) NOT NULL,
 *   dest_location   NVARCHAR(50) NOT NULL,
 *   quantity        INT NOT NULL,
 *   unit            NVARCHAR(20),
 *   status          NVARCHAR(20) DEFAULT 'PENDING',
 *   transfer_type   NVARCHAR(30) DEFAULT 'INTERNAL_RELOCATION',
 *   operator_id     INT,
 *   notes           NVARCHAR(500),
 *   created_at      DATETIME2,
 *   completed_at    DATETIME2,
 *   validated_by    INT,
 *   validated_at    DATETIME2,
 *   error_message   NVARCHAR(500)
 * )
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

    // ─── Données article (dénormalisées depuis ERP au moment du transfert) ───
    /** Code article ERP (t_item) */
    @Column(name = "erp_item_code", nullable = false, length = 50)
    private String erpItemCode;

    /** Désignation article ERP (t_seak) — snapshot au moment du transfert */
    @Column(name = "erp_item_label", length = 255)
    private String erpItemLabel;

    /** Numéro de lot (t_clot) — peut être null si pas de gestion par lot */
    @Column(name = "lot_number", length = 50)
    private String lotNumber;

    // ─── Emplacements ────────────────────────────────────────────────────────
    /** Emplacement source (où l'article se trouve avant) */
    @Column(name = "source_location", nullable = false, length = 50)
    private String sourceLocation;

    /** Emplacement destination (où l'article va après) */
    @Column(name = "dest_location", nullable = false, length = 50)
    private String destLocation;

    // ─── Quantité ────────────────────────────────────────────────────────────
    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    /** Unité de mesure (t_cuni depuis ERP) */
    @Column(name = "unit", length = 20)
    private String unit;

    // ─── Statut ──────────────────────────────────────────────────────────────
    /**
     * PENDING   = en attente (créé par opérateur mobile, pas encore validé)
     * DONE      = effectué et validé
     * ERROR     = erreur lors du traitement
     * CANCELLED = annulé par superviseur
     */
    @Column(name = "status", length = 20)
    @Builder.Default
    private String status = TransferStatus.PENDING;

    /**
     * Type de transfert interne :
     * PUTAWAY            = réception → stockage (mise en place)
     * INTERNAL_RELOCATION = déplacement entre deux zones du magasin
     * REPLENISHMENT      = réapprovisionnement zone picking depuis réserve
     */
    @Column(name = "transfer_type", length = 30)
    @Builder.Default
    private String transferType = TransferType.INTERNAL_RELOCATION;

    // ─── Acteurs ─────────────────────────────────────────────────────────────
    /** Opérateur qui a effectué le transfert (mobile) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operator_id")
    private User operator;

    /** Superviseur qui a validé (web) — peut être null si validation auto */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "validated_by")
    private User validatedBy;

    // ─── Dates ───────────────────────────────────────────────────────────────
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "validated_at")
    private LocalDateTime validatedAt;

    // ─── Métadonnées ─────────────────────────────────────────────────────────
    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    // ─── Lifecycle ───────────────────────────────────────────────────────────
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) status = TransferStatus.PENDING;
        if (transferType == null) transferType = TransferType.INTERNAL_RELOCATION;
    }

    // ─── Constantes statut ───────────────────────────────────────────────────
    public static final class TransferStatus {
        public static final String PENDING   = "PENDING";
        public static final String DONE      = "DONE";
        public static final String ERROR     = "ERROR";
        public static final String CANCELLED = "CANCELLED";
        private TransferStatus() {}
    }

    // ─── Constantes type ─────────────────────────────────────────────────────
    public static final class TransferType {
        public static final String PUTAWAY              = "PUTAWAY";
        public static final String INTERNAL_RELOCATION  = "INTERNAL_RELOCATION";
        public static final String REPLENISHMENT        = "REPLENISHMENT";
        private TransferType() {}
    }
}