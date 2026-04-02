package com.example.CWMS.model.erp;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.Date;
import java.text.SimpleDateFormat;

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
    @Column(name = "t_idat")
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

    /** Date d'entrée en stock */
    @Column(name = "t_idat", insertable = false, updatable = false)
    private LocalDate entryDate;

    @Column(name = "t_trdt")
    @Temporal(TemporalType.TIMESTAMP)
    private Date lastTransactionDateRaw;

    public double getQuantityAvailable() {
        if (quantityAvailableRaw == null || quantityAvailableRaw.trim().isEmpty()) return 0.0;
        try { return Double.parseDouble(quantityAvailableRaw.trim().replace(",", ".")); }
        catch (Exception e) { return 0.0; }
    }

    public double getQuantityBlocked() {
        if (quantityBlockedRaw == null || quantityBlockedRaw.trim().isEmpty()) return 0.0;
        try { return Double.parseDouble(quantityBlockedRaw.trim().replace(",", ".")); }
        catch (Exception e) { return 0.0; }
    }

    public int getAvailableQuantityAsInt() { return (int) getQuantityAvailable(); }
    public int getBlockedQuantityAsInt() { return (int) getQuantityBlocked(); }

    public static String formatErpDate(Date date) {
        if (date == null) return "N/A";
        return new SimpleDateFormat("dd/MM/yyyy").format(date);
    }

    @Transient
    public String getComputedStatus() {
        if (getAvailableQuantityAsInt() <= 0) return "EMPTY";
        if ((blockedAll != null && blockedAll > 0) || (blockedOut != null && blockedOut > 0)) return "BLOCKED";
        if (getBlockedQuantityAsInt() > 0) return "PARTIAL_BLOCK";
        return "AVAILABLE";
    }
    public String getLineStatus() {
        return lineStatus != null ? lineStatus.trim() : "N/A";
    }
    public java.time.LocalDate getEntryDate() {
        return inventoryDateRaw != null ?
                new java.sql.Date(inventoryDateRaw.getTime()).toLocalDate() : null;
    }

    public java.time.LocalDate getLastTransactionDate() {
        return lastTransactionDateRaw != null ?
                new java.sql.Date(lastTransactionDateRaw.getTime()).toLocalDate() : null;
    }


}