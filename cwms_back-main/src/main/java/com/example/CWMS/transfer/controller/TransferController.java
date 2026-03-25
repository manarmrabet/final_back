package com.example.CWMS.transfer.controller;

import com.example.CWMS.dto.ApiResponse;
import com.example.CWMS.transfer.dto.*;
import com.example.CWMS.transfer.service.TransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Controller REST — Transferts internes de stock.
 *
 * FIX : Page<T> → PagedResponse<T> pour éviter le warning de sérialisation JSON
 * et le blocage de l'affichage côté Angular.
 */
@RestController
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;

    // ═════════════════════════════════════════════════════════════════════════
    // TRANSFERTS
    // ═════════════════════════════════════════════════════════════════════════

    @PostMapping("/api/transfers")
    public ResponseEntity<ApiResponse<TransferResponseDTO>> createTransfer(
            @Valid @RequestBody TransferRequestDTO request) {
        return ResponseEntity.ok(
                ApiResponse.success("Transfert enregistré avec succès",
                        transferService.createTransfer(request)));
    }

    @PostMapping("/api/transfers/batch")
    public ResponseEntity<ApiResponse<List<TransferResponseDTO>>> createTransferBatch(
            @Valid @RequestBody List<TransferRequestDTO> requests) {
        List<TransferResponseDTO> results = transferService.createTransferBatch(requests);
        long errors = results.stream().filter(r -> "ERROR".equals(r.getStatus())).count();
        String msg = errors == 0
                ? results.size() + " transfert(s) enregistrés avec succès"
                : (results.size() - errors) + "/" + results.size() + " succès, " + errors + " erreur(s)";
        return ResponseEntity.ok(ApiResponse.success(msg, results));
    }

    /**
     * Liste paginée — FIX : retourne PagedResponse<T> au lieu de Page<T>
     * Page<T> Spring Data ne se sérialise pas correctement en JSON → Angular bloque sur "Chargement..."
     */
    @GetMapping("/api/transfers")
    public ResponseEntity<ApiResponse<PagedResponse<TransferResponseDTO>>> getAll(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                PagedResponse.of(transferService.getAll(
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))))));
    }

    /** Recherche avancée avec filtres */
    @GetMapping("/api/transfers/search")
    public ResponseEntity<ApiResponse<PagedResponse<TransferResponseDTO>>> search(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String itemCode,
            @RequestParam(required = false) String location,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                PagedResponse.of(transferService.search(
                        status, itemCode, location, from, to,
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))))));
    }

    @GetMapping("/api/transfers/{id}")
    public ResponseEntity<ApiResponse<TransferResponseDTO>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(transferService.getById(id)));
    }

    @GetMapping("/api/transfers/my/{operatorId}")
    public ResponseEntity<ApiResponse<List<TransferResponseDTO>>> getMyTransfers(
            @PathVariable Integer operatorId) {
        return ResponseEntity.ok(ApiResponse.success(transferService.getMyTransfers(operatorId)));
    }

    @PutMapping("/api/transfers/{id}/validate")
    public ResponseEntity<ApiResponse<TransferResponseDTO>> validateTransfer(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Transfert validé",
                transferService.validateTransfer(id)));
    }

    @PutMapping("/api/transfers/{id}/cancel")
    public ResponseEntity<ApiResponse<TransferResponseDTO>> cancelTransfer(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "") String reason) {
        return ResponseEntity.ok(ApiResponse.success("Transfert annulé",
                transferService.cancelTransfer(id, reason)));
    }

    @GetMapping("/api/transfers/dashboard")
    public ResponseEntity<ApiResponse<TransferDashboardDTO>> getDashboard() {
        return ResponseEntity.ok(ApiResponse.success(transferService.getDashboard()));
    }

    // ═════════════════════════════════════════════════════════════════════════
    // ERP DATA
    // ═════════════════════════════════════════════════════════════════════════

    @GetMapping("/api/erp/articles/{code}")
    public ResponseEntity<ApiResponse<ErpArticleDTO>> getArticleByCode(@PathVariable String code) {
        return ResponseEntity.ok(ApiResponse.success(transferService.getArticleByCode(code)));
    }

    @GetMapping("/api/erp/articles/search")
    public ResponseEntity<ApiResponse<List<ErpArticleDTO>>> searchArticles(@RequestParam String q) {
        return ResponseEntity.ok(ApiResponse.success(transferService.searchArticles(q)));
    }

    @GetMapping("/api/erp/stock/item/{itemCode}")
    public ResponseEntity<ApiResponse<List<ErpStockDTO>>> getStockByItem(
            @PathVariable String itemCode) {
        return ResponseEntity.ok(ApiResponse.success(transferService.getStockByItem(itemCode)));
    }

    @GetMapping("/api/erp/stock/location/{location}")
    public ResponseEntity<ApiResponse<List<ErpStockDTO>>> getStockByLocation(
            @PathVariable String location) {
        return ResponseEntity.ok(ApiResponse.success(transferService.getStockByLocation(location)));
    }
}