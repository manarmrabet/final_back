package com.example.CWMS.controller;

import com.example.CWMS.dto.ReceptionLineDTO;
import com.example.CWMS.dto.ReceptionOrderDTO;
import com.example.CWMS.dto.ReceptionStatsDTO;
import com.example.CWMS.iservice.ReceptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reception")
@RequiredArgsConstructor
@Slf4j
public class ReceptionController {

    private final ReceptionService receptionService;

    /**
     * ─── 1. Search by order number ─────────────────────────────────────────
     * GET /api/reception/by-order/{orderNumber}
     */
    @GetMapping("/by-order/{orderNumber}")
    public ResponseEntity<?> searchByOrder(@PathVariable String orderNumber) {
        try {
            log.info("[Reception] searchByOrder: {}", orderNumber);
            List<ReceptionLineDTO> result = receptionService.searchByOrder(orderNumber.trim().toUpperCase());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("[Reception] searchByOrder error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * ─── 2. Search by date range ────────────────────────────────────────────
     * GET /api/reception/by-date-range?startDate=dd/MM/yyyy&endDate=dd/MM/yyyy
     */
    @GetMapping("/by-date-range")
    public ResponseEntity<?> searchByDateRange(
            @RequestParam String startDate,
            @RequestParam String endDate) {
        try {
            log.info("[Reception] searchByDateRange: {} → {}", startDate, endDate);
            List<ReceptionOrderDTO> result = receptionService.searchByDateRange(startDate, endDate);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("[Reception] searchByDateRange error | start={} end={}", startDate, endDate, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * ─── 3. Reception detail ────────────────────────────────────────────────
     * GET /api/reception/detail/{receptionNumber}
     */
    @GetMapping("/detail/{receptionNumber}")
    public ResponseEntity<?> getReceptionDetail(@PathVariable String receptionNumber) {
        try {
            return ResponseEntity.ok(receptionService.getReceptionDetail(receptionNumber));
        } catch (Exception e) {
            log.error("[Reception] getReceptionDetail error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * ─── 4. Stats ───────────────────────────────────────────────────────────
     * GET /api/reception/stats?startDate=dd/MM/yyyy&endDate=dd/MM/yyyy
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getStats(
            @RequestParam String startDate,
            @RequestParam String endDate) {
        try {
            return ResponseEntity.ok(receptionService.getStats(startDate, endDate));
        } catch (Exception e) {
            log.error("[Reception] getStats error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * ─── 5. Export PDF standard ─────────────────────────────────────────────
     * GET /api/reception/export/pdf/order/{orderNumber}
     */
    @GetMapping("/export/pdf/order/{orderNumber}")
    public ResponseEntity<?> exportPdfByOrder(@PathVariable String orderNumber) {
        try {
            byte[] pdf = receptionService.generatePdfByOrder(orderNumber.trim().toUpperCase());
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"reception_" + orderNumber + ".pdf\"")
                    .body(pdf);
        } catch (Exception e) {
            log.error("[Reception] exportPdfByOrder error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * ─── 6. Export PDF valorisé ──────────────────────────────────────────────
     * GET /api/reception/export/pdf/valued/{orderNumber}
     */
    @GetMapping("/export/pdf/valued/{orderNumber}")
    public ResponseEntity<?> exportPdfValued(@PathVariable String orderNumber) {
        try {
            byte[] pdf = receptionService.generatePdfValued(orderNumber.trim().toUpperCase());
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"reception_valued_" + orderNumber + ".pdf\"")
                    .body(pdf);
        } catch (Exception e) {
            log.error("[Reception] exportPdfValued error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * ─── 7. Export Excel par plage de dates ─────────────────────────────────
     * GET /api/reception/export/excel?startDate=dd/MM/yyyy&endDate=dd/MM/yyyy
     */
    @GetMapping("/export/excel")
    public ResponseEntity<?> exportExcel(
            @RequestParam String startDate,
            @RequestParam String endDate) {
        try {
            byte[] excel = receptionService.exportExcel(startDate, endDate);
            String filename = "reception_" + startDate.replace("/", "-")
                    + "_" + endDate.replace("/", "-") + ".xlsx";
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + filename + "\"")
                    .body(excel);
        } catch (Exception e) {
            log.error("[Reception] exportExcel error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * ─── 8. Export Excel bulk ────────────────────────────────────────────────
     * POST /api/reception/export/excel/bulk
     * Body: { "orderNumbers": ["OR0000131", "OR0000132"] }
     */
    @PostMapping("/export/excel/bulk")
    public ResponseEntity<?> exportExcelBulk(@RequestBody Map<String, List<String>> body) {
        try {
            List<String> orderNumbers = body.get("orderNumbers");
            if (orderNumbers == null || orderNumbers.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "orderNumbers list is required"));
            }
            // Normaliser les numéros (trim + uppercase)
            List<String> normalized = orderNumbers.stream()
                    .map(s -> s.trim().toUpperCase())
                    .toList();
            log.info("[Reception] exportExcelBulk: {}", normalized);
            byte[] excel = receptionService.exportExcelBulk(normalized);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"reception_bulk.xlsx\"")
                    .body(excel);
        } catch (Exception e) {
            log.error("[Reception] exportExcelBulk error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", e.getMessage()));
        }
    }
}