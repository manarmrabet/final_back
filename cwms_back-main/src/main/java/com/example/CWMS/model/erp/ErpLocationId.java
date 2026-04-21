package com.example.CWMS.model.erp;

import java.io.Serializable;
import lombok.*;

/**
 * Clé composite pour ErpLocation (dbo_twhwmd300310).
 * Clé naturelle Infor LN : (t_cwar, t_loca).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ErpLocationId implements Serializable {
    private String warehouseCode;
    private String locationCode;
}
