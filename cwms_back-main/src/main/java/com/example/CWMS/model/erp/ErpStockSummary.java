package com.example.CWMS.model.erp;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

/**
 * Maps dbo_twhinr1501200 — totaux de stock au niveau magasin (150).
 * Doit rester synchronisé avec ErpStock : tout mouvement doit
 * se refléter ici dans la même transaction.
 */
@Entity
@Table(name = "dbo_twhinr150310")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErpStockSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_summary")
    private Long idSummary;

    @Column(name = "t_item")
    private String itemCode;

    @Column(name = "t_cwar")
    private String warehouseCode;

    @Column(name = "t_clot")
    private String lotNumber;

    /** Quantité totale en main au niveau magasin */
    @Column(name = "t_qhnd", precision = 19, scale = 5)
    private BigDecimal quantityOnHand;

    public void addQuantity(BigDecimal qty) {
        this.quantityOnHand = this.quantityOnHand.add(qty);
    }

    public void subtractQuantity(BigDecimal qty) {
        this.quantityOnHand = this.quantityOnHand.subtract(qty);
    }
}
