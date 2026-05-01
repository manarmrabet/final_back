package com.example.CWMS.model.erp;

import jakarta.persistence.*;
import lombok.Data;
import java.util.Date;

@Entity
@Data
@Table(name = "dbo_twhinr1401200")
public class StockLot {

    @Id
    @Column(name = "id_stockage")
    private Long id;

    @Column(name = "t_cwar") private String warehouse;
    @Column(name = "t_loca") private String location;
    @Column(name = "t_item") private String itemCode;
    @Column(name = "t_clot") private String lotCode;
    @Column(name = "t_qhnd") private Double qtyOnHand;
    @Column(name = "t_qblk") private Double qtyBlocked;
    @Column(name = "t_cuni") private String unit;
    @Column(name = "t_trdt") private Date lastMovement;

    // ✅ FIX SpotBugs — copie défensive Date
    public void setLastMovement(Date lastMovement) {
        this.lastMovement = lastMovement == null ? null : new Date(lastMovement.getTime());
    }

    public Date getLastMovement() {
        return lastMovement == null ? null : new Date(lastMovement.getTime());
    }
}