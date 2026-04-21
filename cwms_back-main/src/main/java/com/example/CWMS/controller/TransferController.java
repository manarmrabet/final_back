// ════════════════════════════════════════════════════════════════════
// FILE 1: TransferController.java
// ════════════════════════════════════════════════════════════════════
package com.example.CWMS.controller;

import com.example.CWMS.dto.*;
import com.example.CWMS.iservice.TransferService;
import com.example.CWMS.service.TransferArchiveScheduler;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/transfers")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService          transferService;
    private final TransferArchiveScheduler archiveScheduler;

    // ── CRUD ─────────────────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<ApiResponse<TransferResponseDTO>> createTransfer(
            @Valid @RequestBody TransferRequestDTO req) {
        return ResponseEntity.ok(ApiResponse.success("Transfert enregistré",
                transferService.createTransfer(req)));
    }

    @PostMapping("/batch")
    public ResponseEntity<ApiResponse<List<TransferResponseDTO>>> createBatch(
            @Valid @RequestBody List<TransferRequestDTO> reqs) {
        List<TransferResponseDTO> res = transferService.createTransferBatch(reqs);
        long err = res.stream().filter(r -> "ERROR".equals(r.getStatus())).count();
        String msg = err == 0
                ? res.size() + " transfert(s) enregistrés"
                : (res.size()-err) + "/" + res.size() + " succès, " + err + " erreur(s)";
        return ResponseEntity.ok(ApiResponse.success(msg, res));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<TransferResponseDTO>>> getAll(
            @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size) {
        return ResponseEntity.ok(ApiResponse.success(PagedResponse.of(
                transferService.getAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC,"createdAt"))))));
    }

    /**
     * Recherche avancée — 6 filtres combinables :
     *   status, itemCode, location, operator, from, to
     *
     * GET /api/transfers/search?itemCode=ABC&operator=Ahmed&from=2025-01-01T00:00:00
     */
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<PagedResponse<TransferResponseDTO>>> search(
            @RequestParam(required=false) String status,
            @RequestParam(required=false) String itemCode,
            @RequestParam(required=false) String location,
            @RequestParam(required=false) String operator,   // ✅ NEW — recherche sur prénom+nom
            @RequestParam(required=false)
            @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required=false)
            @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue="0")  int page,
            @RequestParam(defaultValue="20") int size) {
        return ResponseEntity.ok(ApiResponse.success(PagedResponse.of(
                transferService.search(status, itemCode, location, operator, from, to,
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC,"createdAt"))))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TransferResponseDTO>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(transferService.getById(id)));
    }

    @GetMapping("/my/{operatorId}")
    public ResponseEntity<ApiResponse<PagedResponse<TransferResponseDTO>>> myTransfers(
            @PathVariable Integer operatorId,
            @RequestParam(defaultValue="0")  int page,
            @RequestParam(defaultValue="20") int size) {
        return ResponseEntity.ok(ApiResponse.success(PagedResponse.of(
                transferService.getMyTransfers(operatorId,
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC,"createdAt"))))));
    }

    @PutMapping("/{id}/validate")
    public ResponseEntity<ApiResponse<TransferResponseDTO>> validate(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Validé", transferService.validateTransfer(id)));
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<TransferResponseDTO>> cancel(
            @PathVariable Long id,
            @RequestParam(required=false, defaultValue="") String reason) {
        return ResponseEntity.ok(ApiResponse.success("Annulé", transferService.cancelTransfer(id, reason)));
    }

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<TransferDashboardDTO>> dashboard() {
        return ResponseEntity.ok(ApiResponse.success(transferService.getDashboard()));
    }

    // ── ARCHIVE MANUEL — test immédiat ────────────────────────────────────────

    /**
     * Déclenche l'archivage manuellement.
     *
     * POST /api/transfers/archives/trigger              → archive le mois précédent
     * POST /api/transfers/archives/trigger?testMode=true → archive les 5 dernières minutes
     *
     * Ajouter @PreAuthorize("hasRole('ADMIN')") en production.
     */
    @PostMapping("/archives/trigger")
    public ResponseEntity<Map<String,Object>> triggerArchive(
            @RequestParam(defaultValue="false") boolean testMode) {
        try {
            if (testMode) archiveScheduler.archiveForTesting();
            else          archiveScheduler.archiveMonthlyTransfers();
            return ResponseEntity.ok(Map.of("success", true,
                    "message", testMode ? "Archive TEST (5 min) exécutée" : "Archive mensuelle exécutée"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "message", "Erreur : " + e.getMessage()));
        }
    }

    // ── ERP Data ─────────────────────────────────────────────────────────────

    @GetMapping("/erp/warehouses")
    public ResponseEntity<ApiResponse<List<String>>> warehouses() {
        return ResponseEntity.ok(ApiResponse.success(transferService.getDistinctWarehouses()));
    }

    @GetMapping("/erp/articles/{code}")
    public ResponseEntity<ApiResponse<ErpArticleDTO>> article(@PathVariable String code) {
        return ResponseEntity.ok(ApiResponse.success(transferService.getArticleByCode(code)));
    }

    @GetMapping("/erp/articles/search")
    public ResponseEntity<ApiResponse<List<ErpArticleDTO>>> searchArticles(@RequestParam String q) {
        return ResponseEntity.ok(ApiResponse.success(transferService.searchArticles(q)));
    }

    @GetMapping("/erp/stock/item/{itemCode}")
    public ResponseEntity<ApiResponse<List<ErpStockDTO>>> stockByItem(@PathVariable String itemCode) {
        return ResponseEntity.ok(ApiResponse.success(transferService.getStockByItem(itemCode)));
    }

    @GetMapping("/erp/stock/location/{location}")
    public ResponseEntity<ApiResponse<List<ErpStockDTO>>> stockByLocation(@PathVariable String location) {
        return ResponseEntity.ok(ApiResponse.success(transferService.getStockByLocation(location)));
    }

    @GetMapping("/erp/stock/lot/{lotNumber}")
    public ResponseEntity<ApiResponse<List<ErpStockDTO>>> stockByLot(@PathVariable String lotNumber) {
        return ResponseEntity.ok(ApiResponse.success(transferService.getStockByLot(lotNumber)));
    }

    @GetMapping("/erp/location/{locationCode}")
    public ResponseEntity<ApiResponse<ErpLocationDTO>> location(@PathVariable String locationCode) {
        return ResponseEntity.ok(ApiResponse.success(transferService.getLocationInfo(locationCode)));
    }
}
 
 