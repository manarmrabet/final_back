package com.example.CWMS.repository.cwms;

import com.example.CWMS.model.cwms.InventoryReport;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface InventoryReportRepository extends JpaRepository<InventoryReport, Long> {
    Optional<InventoryReport> findBySessionId(Long sessionId);
    boolean existsBySessionId(Long sessionId);
}