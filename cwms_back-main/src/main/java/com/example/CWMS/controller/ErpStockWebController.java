package com.example.CWMS.controller;

import com.example.CWMS.service.StockDashboardService;
import com.example.CWMS.service.ErpConsultationService;
import com.example.CWMS.dto.ErpArticleSummaryDTO;
import com.example.CWMS.dto.ErpLotLineDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/web/erp/stock")
@RequiredArgsConstructor
public class ErpStockWebController {

    private final StockDashboardService dashboardService;
    private final ErpConsultationService consultationService;

    @GetMapping("/all")
    public ResponseEntity<Page<ErpLotLineDTO>> getAllStock(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(consultationService.getAllStockPaginated(PageRequest.of(page, size)));
    }

    @GetMapping("/lot-details/{lotNumber}")
    public ResponseEntity<List<ErpLotLineDTO>> getLotDetails(@PathVariable String lotNumber) {
        return ResponseEntity.ok(consultationService.getLotDetails(lotNumber));
    }

    @GetMapping("/article-summary/{code}")
    public ResponseEntity<ErpArticleSummaryDTO> getArticleSummary(@PathVariable String code) {
        return ResponseEntity.ok(consultationService.getArticleSummary(code));
    }

    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboard() {
        return ResponseEntity.ok(dashboardService.getDashboardData());
    }
}