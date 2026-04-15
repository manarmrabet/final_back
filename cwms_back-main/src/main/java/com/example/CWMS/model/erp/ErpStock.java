package com.example.CWMS.model.erp;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.Date;

/**
 * Entité ERP — Table dbo_twhinr1401200 (stock détaillé niveau 140).
 *
 * RÈGLES INFOR LN :
 *  - t_qhnd  = quantité disponible (seul champ à modifier lors d'un mouvement)
 *  - t_qblk  = quantité bloquée    (jamais modifiée par CWMS)
 *  - t_ball / t_bout               (jamais modifiés par CWMS)
 *
 * Toutes les quantités sont exposées en BigDecimal (précision 5 décimales).
 * Le stockage reste String car c'est ce que l'ERP écrit nativement dans la colonne.
 */
@Entity
@Table(name = "dbo_twhinr1401200")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErpStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_stockage")
    private Long idStockage;

    @Column(name = "t_item")
    private String itemCode;

    @Column(name = "t_cwar")
    private String warehouseCode;

    @Column(name = "t_loca")
    private String location;

    @Column(name = "t_clot")
    private String lotNumber;

    /** Date d'entrée en stock (t_idat) */
    @Column(name = "t_idat", insertable = true, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date inventoryDateRaw;

    /** Quantité disponible stockée en String par l'ERP (t_qhnd) */
    @Column(name = "t_qhnd")
    private String quantityAvailableRaw;

    /** Quantité bloquée stockée en String par l'ERP (t_qblk) — READ ONLY */
    @Column(name = "t_qblk")
    private String quantityBlockedRaw;

    /** Blocage total (t_ball) — READ ONLY, jamais modifié par CWMS */
    @Column(name = "t_ball")
    private Integer blockedAll;

    /** Blocage sortie (t_bout) — READ ONLY, jamais modifié par CWMS */
    @Column(name = "t_bout")
    private Integer blockedOut;

    /** Statut ligne (t_lsid) */
    @Column(name = "t_lsid")
    private String lineStatus;

    /** Date dernière transaction (t_trdt) */
    @Column(name = "t_trdt")
    @Temporal(TemporalType.TIMESTAMP)
    private Date lastTransactionDateRaw;

    // ══════════════════════════════════════════════════════════════════════════
    // ACCESSEURS QUANTITÉ — BigDecimal (précision 5 décimales)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Retourne t_qhnd sous forme BigDecimal.
     * Parsing robuste : gère null, vide, virgule ou point décimal.
     */
    public BigDecimal getQuantityOnHand() {
        return parseBigDecimal(quantityAvailableRaw);
    }

    /**
     * Retourne t_qblk sous forme BigDecimal — READ ONLY.
     */
    public BigDecimal getQuantityBlocked() {
        return parseBigDecimal(quantityBlockedRaw);
    }
    public double getQuantityAvailable() {
        if (quantityAvailableRaw == null || quantityAvailableRaw.trim().isEmpty()) return 0.0;
        try { return Double.parseDouble(quantityAvailableRaw.trim().replace(",", ".")); }
        catch (Exception e) { return 0.0; }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // HELPERS MOUVEMENT (utilisés par StockMovementServiceImpl)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Soustrait qty de t_qhnd.
     * Appelé atomiquement dans la transaction de mouvement.
     *
     * @throws IllegalArgumentException si qty <= 0
     */
    public void subtractQuantity(BigDecimal qty) {
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("La quantité à soustraire doit être > 0");
        }
        BigDecimal result = getQuantityOnHand().subtract(qty).setScale(5, RoundingMode.HALF_UP);
        this.quantityAvailableRaw = result.toPlainString();
    }

    /**
     * Ajoute qty à t_qhnd.
     * Appelé atomiquement dans la transaction de mouvement.
     *
     * @throws IllegalArgumentException si qty <= 0
     */
    public void addQuantity(BigDecimal qty) {
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("La quantité à ajouter doit être > 0");
        }
        BigDecimal result = getQuantityOnHand().add(qty).setScale(5, RoundingMode.HALF_UP);
        this.quantityAvailableRaw = result.toPlainString();
    }

    /**
     * Retourne true si t_qhnd <= 0 (ligne à supprimer selon convention Infor LN).
     */
    public boolean isEmpty() {
        return getQuantityOnHand().compareTo(BigDecimal.ZERO) <= 0;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ACCESSEURS DATES
    // ══════════════════════════════════════════════════════════════════════════

    public LocalDate getEntryDate() {
        return inventoryDateRaw != null
                ? new java.sql.Date(inventoryDateRaw.getTime()).toLocalDate()
                : null;
    }

    public LocalDate getLastTransactionDate() {
        return lastTransactionDateRaw != null
                ? new java.sql.Date(lastTransactionDateRaw.getTime()).toLocalDate()
                : null;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ACCESSEURS COMPATIBILITÉ (anciens appels)
    // ══════════════════════════════════════════════════════════════════════════

    public int getAvailableQuantityAsInt() {
        return getQuantityOnHand().intValue();
    }

    public int getBlockedQuantityAsInt() {
        return getQuantityBlocked().intValue();
    }

    public String getLineStatusSafe() {
        return lineStatus != null ? lineStatus.trim() : "N/A";
    }

    @Transient
    public String getComputedStatus() {
        if (getQuantityOnHand().compareTo(BigDecimal.ZERO) <= 0) return "EMPTY";
        if ((blockedAll != null && blockedAll > 0) || (blockedOut != null && blockedOut > 0)) return "BLOCKED";
        if (getQuantityBlocked().compareTo(BigDecimal.ZERO) > 0) return "PARTIAL_BLOCK";
        return "AVAILABLE";
    }

    public static String formatErpDate(Date date) {
        if (date == null) return "N/A";
        return new SimpleDateFormat("dd/MM/yyyy").format(date);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // HELPER PRIVÉ
    // ══════════════════════════════════════════════════════════════════════════

    private static BigDecimal parseBigDecimal(String raw) {
        if (raw == null || raw.isBlank()) return BigDecimal.ZERO.setScale(5, RoundingMode.HALF_UP);
        try {
            return new BigDecimal(raw.trim().replace(",", ".")).setScale(5, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO.setScale(5, RoundingMode.HALF_UP);
        }
    }
}