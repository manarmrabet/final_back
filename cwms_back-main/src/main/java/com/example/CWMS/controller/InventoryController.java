package com.example.CWMS.controller;

import com.example.CWMS.dto.*;
import com.example.CWMS.iservice.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class InventoryController {

    private final InventoryService inventoryService;

    // ── Sessions ──────────────────────────────────────────────────

    @GetMapping("/sessions")
    public ResponseEntity<List<InventorySessionDTO>> getAllSessions() {
        return ResponseEntity.ok(inventoryService.getAllSessions());
    }

    @GetMapping("/sessions/{id}")
    public ResponseEntity<InventorySessionDTO> getSession(@PathVariable Long id) {
        return ResponseEntity.ok(inventoryService.getSessionById(id));
    }

    @PostMapping("/sessions")
    public ResponseEntity<InventorySessionDTO> createSession(
            @RequestBody CreateSessionRequest request, Authentication auth) {
        return ResponseEntity.ok(inventoryService.createSession(request, auth.getName()));
    }

    @PutMapping("/sessions/{id}/validate")
    public ResponseEntity<InventorySessionDTO> validateSession(
            @PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(inventoryService.validateSession(id, auth.getName()));
    }

    // ── Lignes ────────────────────────────────────────────────────

    @PostMapping("/lines")
    public ResponseEntity<CollectLineDTO> addLine(
            @RequestBody AddCollectLineRequest request, Authentication auth) {
        return ResponseEntity.ok(inventoryService.addLine(request, auth.getName()));
    }

    @GetMapping("/sessions/{sessionId}/lines")
    public ResponseEntity<List<CollectLineDTO>> getLines(@PathVariable Long sessionId) {
        return ResponseEntity.ok(inventoryService.getLinesBySession(sessionId));
    }

    @DeleteMapping("/lines/{lineId}")
    public ResponseEntity<Void> deleteLine(@PathVariable Long lineId) {
        inventoryService.deleteLine(lineId);
        return ResponseEntity.noContent().build();
    }

    // ── Templates ─────────────────────────────────────────────────

    @GetMapping("/templates")
    public ResponseEntity<List<CollectTemplateDTO>> getTemplates() {
        return ResponseEntity.ok(inventoryService.getActiveTemplates());
    }

    @PostMapping("/templates")
    public ResponseEntity<CollectTemplateDTO> createTemplate(@RequestBody CollectTemplateDTO dto) {
        return ResponseEntity.ok(inventoryService.createTemplate(dto));
    }

    // ── Données ERP (pour alimenter les dropdowns web/mobile) ─────

    @GetMapping("/erp/warehouses")
    public ResponseEntity<List<String>> getWarehouses() {
        return ResponseEntity.ok(inventoryService.getErpWarehouses());
    }

    @GetMapping("/erp/locations")
    public ResponseEntity<List<String>> getLocations(@RequestParam String warehouseCode) {
        return ResponseEntity.ok(inventoryService.getErpLocationsByWarehouse(warehouseCode));
    }

    // ── Rapport & Export ──────────────────────────────────────────

    @PostMapping("/sessions/{sessionId}/report")
    public ResponseEntity<InventoryReportDTO> generateReport(
            @PathVariable Long sessionId, Authentication auth) {
        return ResponseEntity.ok(inventoryService.generateReport(sessionId, auth.getName()));
    }

    @GetMapping("/sessions/{sessionId}/report")
    public ResponseEntity<InventoryReportDTO> getReport(@PathVariable Long sessionId) {
        return ResponseEntity.ok(inventoryService.getReport(sessionId));
    }

    @GetMapping("/sessions/{sessionId}/export/collect")
    public ResponseEntity<byte[]> exportCollect(@PathVariable Long sessionId) {
        return inventoryService.exportCollectExcel(sessionId);
    }

    @GetMapping("/sessions/{sessionId}/export/report")
    public ResponseEntity<byte[]> exportReport(@PathVariable Long sessionId) {
        return inventoryService.exportReportExcel(sessionId);
    }
}