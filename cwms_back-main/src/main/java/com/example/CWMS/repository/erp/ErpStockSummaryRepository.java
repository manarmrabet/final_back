package com.example.CWMS.repository.erp;

import com.example.CWMS.model.erp.ErpStockSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface ErpStockSummaryRepository extends JpaRepository<ErpStockSummary, Long> {

    Optional<ErpStockSummary> findByWarehouseCodeAndItemCodeAndLotNumber(
            String warehouseCode, String itemCode, String lotNumber);
}