package com.example.CWMS.iservice;

import com.example.CWMS.dto.AddCollectLineRequest;
import com.example.CWMS.dto.CollectLineDTO;
import com.example.CWMS.dto.CollectTemplateDTO;
import com.example.CWMS.dto.CreateSessionRequest;
import com.example.CWMS.dto.InventoryReportDTO;
import com.example.CWMS.dto.InventorySessionDTO;
import org.springframework.http.ResponseEntity;
import java.util.List;

public interface InventoryService {

    List<InventorySessionDTO> getAllSessions();
    InventorySessionDTO getSessionById(Long id);
    InventorySessionDTO createSession(CreateSessionRequest request, String username);
    InventorySessionDTO validateSession(Long sessionId, String username);

    CollectLineDTO addLine(AddCollectLineRequest request, String username);
    List<CollectLineDTO> getLinesBySession(Long sessionId);
    void deleteLine(Long lineId);

    List<CollectTemplateDTO> getActiveTemplates();
    CollectTemplateDTO createTemplate(CollectTemplateDTO dto);

    List<String> getErpWarehouses();
    List<String> getErpLocationsByWarehouse(String warehouseCode);

    InventoryReportDTO generateReport(Long sessionId, String username);
    InventoryReportDTO getReport(Long sessionId);
    ResponseEntity<byte[]> exportCollectExcel(Long sessionId);
    ResponseEntity<byte[]> exportReportExcel(Long sessionId);
}