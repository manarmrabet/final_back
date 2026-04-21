package com.example.CWMS.iservice;

import com.example.CWMS.dto.AddCollectLineRequest;
import com.example.CWMS.dto.CollectLineDTO;
import com.example.CWMS.dto.CreateSessionRequest;
import com.example.CWMS.dto.InventoryReportDTO;
import com.example.CWMS.dto.InventorySessionDTO;
import org.springframework.http.ResponseEntity;
import java.util.List;

public interface InventoryService {

    // Sessions
    List<InventorySessionDTO> getAllSessions();
    InventorySessionDTO getSessionById(Long id);
    InventorySessionDTO createSession(CreateSessionRequest request, String username);
    InventorySessionDTO validateSession(Long sessionId, String username);

    // Lignes
    CollectLineDTO addLine(AddCollectLineRequest request, String username);
    List<CollectLineDTO> getLinesBySession(Long sessionId);
    void deleteLine(Long lineId);

    // Données ERP
    List<String> getErpWarehouses();
    List<String> getErpLocationsByWarehouse(String warehouseCode);

    /**
     * Zones distinctes d'un magasin ERP (t_zone depuis dbo_twhwmd300310).
     * Filtre les valeurs nulles/vides et trie alphabétiquement.
     */
    List<String> getErpZonesByWarehouse(String warehouseCode);

    // Rapport
    InventoryReportDTO generateReport(Long sessionId, String username);
    InventoryReportDTO getReport(Long sessionId);

    // Export
    ResponseEntity<byte[]> exportCollectExcel(Long sessionId);
    ResponseEntity<byte[]> exportReportExcel(Long sessionId);
}