package com.example.CWMS.model.erp;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.Date;

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

    @Column(name = "t_idat", insertable = true, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date inventoryDateRaw;

    @Column(name = "t_qhnd")
    private String quantityAvailableRaw;

    @Column(name = "t_qblk")
    private String quantityBlockedRaw;

    @Column(name = "t_ball")
    private Integer blockedAll;

    @Column(name = "t_bout")
    private Integer blockedOut;

    @Column(name = "t_lsid")
    private String lineStatus;

    @Column(name = "t_trdt")
    @Temporal(TemporalType.TIMESTAMP)
    private Date lastTransactionDateRaw;

    // ✅ FIX SpotBugs — copie défensive pour Date (objet mutable)
    public void setInventoryDateRaw(Date inventoryDateRaw) {
        this.inventoryDateRaw = inventoryDateRaw == null ? null
                : new Date(inventoryDateRaw.getTime());
    }

    public void setLastTransactionDateRaw(Date lastTransactionDateRaw) {
        this.lastTransactionDateRaw = lastTransactionDateRaw == null ? null
                : new Date(lastTransactionDateRaw.getTime());
    }

    public Date getInventoryDateRaw() {
        return inventoryDateRaw == null ? null : new Date(inventoryDateRaw.getTime());
    }

    public Date getLastTransactionDateRaw() {
        return lastTransactionDateRaw == null ? null
                : new Date(lastTransactionDateRaw.getTime());
    }

    // ══════ ACCESSEURS QUANTITÉ — inchangés ══════

    public BigDecimal getQuantityOnHand() {
        return parseBigDecimal(quantityAvailableRaw);
    }

    public BigDecimal getQuantityBlocked() {
        return parseBigDecimal(quantityBlockedRaw);
    }

    public double getQuantityAvailable() {
        if (quantityAvailableRaw == null || quantityAvailableRaw.trim().isEmpty()) return 0.0;
        try { return Double.parseDouble(quantityAvailableRaw.trim().replace(",", ".")); }
        catch (Exception e) { return 0.0; }
    }

    public void subtractQuantity(BigDecimal qty) {
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("La quantité à soustraire doit être > 0");
        BigDecimal result = getQuantityOnHand().subtract(qty).setScale(5, RoundingMode.HALF_UP);
        this.quantityAvailableRaw = result.toPlainString();
    }

    public void addQuantity(BigDecimal qty) {
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("La quantité à ajouter doit être > 0");
        BigDecimal result = getQuantityOnHand().add(qty).setScale(5, RoundingMode.HALF_UP);
        this.quantityAvailableRaw = result.toPlainString();
    }

    public boolean isEmpty() {
        return getQuantityOnHand().compareTo(BigDecimal.ZERO) <= 0;
    }

    public LocalDate getEntryDate() {
        return inventoryDateRaw != null
                ? new java.sql.Date(inventoryDateRaw.getTime()).toLocalDate() : null;
    }

    public LocalDate getLastTransactionDate() {
        return lastTransactionDateRaw != null
                ? new java.sql.Date(lastTransactionDateRaw.getTime()).toLocalDate() : null;
    }

    public int getAvailableQuantityAsInt() { return getQuantityOnHand().intValue(); }
    public int getBlockedQuantityAsInt()   { return getQuantityBlocked().intValue(); }

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

    private static BigDecimal parseBigDecimal(String raw) {
        if (raw == null || raw.isBlank()) return BigDecimal.ZERO.setScale(5, RoundingMode.HALF_UP);
        try {
            return new BigDecimal(raw.trim().replace(",", ".")).setScale(5, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO.setScale(5, RoundingMode.HALF_UP);
        }
    }
}