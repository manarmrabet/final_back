package com.example.CWMS.controller;

import com.example.CWMS.dto.*;
import com.example.CWMS.dto.ErpArticleDTO;
import com.example.CWMS.dto.ErpLocationDTO;
import com.example.CWMS.dto.ErpStockDTO;
import com.example.CWMS.iservice.TransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/transfers")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;

    // ══════════════════════════════════════════════════════════════════════
    // TRANSFERTS
    // ══════════════════════════════════════════════════════════════════════

    @PostMapping
    public ResponseEntity<ApiResponse<TransferResponseDTO>> createTransfer(
            @Valid @RequestBody TransferRequestDTO request) {
        return ResponseEntity.ok(ApiResponse.success("Transfert enregistré",
                transferService.createTransfer(request)));
    }

    @PostMapping("/batch")
    public ResponseEntity<ApiResponse<List<TransferResponseDTO>>> createTransferBatch(
            @Valid @RequestBody List<TransferRequestDTO> requests) {
        List<TransferResponseDTO> results = transferService.createTransferBatch(requests);
        long errors = results.stream().filter(r -> "ERROR".equals(r.getStatus())).count();
        String msg = errors == 0
                ? results.size() + " transfert(s) enregistrés"
                : (results.size() - errors) + "/" + results.size() + " succès, " + errors + " erreur(s)";
        return ResponseEntity.ok(ApiResponse.success(msg, results));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<TransferResponseDTO>>> getAll(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                PagedResponse.of(transferService.getAll(
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))))));
    }

    @GetMapping("/search")
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

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TransferResponseDTO>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(transferService.getById(id)));
    }

    @GetMapping("/my/{operatorId}")
    public ResponseEntity<ApiResponse<List<TransferResponseDTO>>> getMyTransfers(
            @PathVariable Integer operatorId) {
        return ResponseEntity.ok(ApiResponse.success(transferService.getMyTransfers(operatorId)));
    }

    @PutMapping("/{id}/validate")
    public ResponseEntity<ApiResponse<TransferResponseDTO>> validateTransfer(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Validé", transferService.validateTransfer(id)));
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<TransferResponseDTO>> cancelTransfer(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "") String reason) {
        return ResponseEntity.ok(ApiResponse.success("Annulé", transferService.cancelTransfer(id, reason)));
    }

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<TransferDashboardDTO>> getDashboard() {
        return ResponseEntity.ok(ApiResponse.success(transferService.getDashboard()));
    }

    // ══════════════════════════════════════════════════════════════════════
    // ERP DATA — EXISTANTS
    // ══════════════════════════════════════════════════════════════════════

    @GetMapping("/erp/articles/{code}")
    public ResponseEntity<ApiResponse<ErpArticleDTO>> getArticleByCode(@PathVariable String code) {
        return ResponseEntity.ok(ApiResponse.success(transferService.getArticleByCode(code)));
    }

    @GetMapping("/erp/articles/search")
    public ResponseEntity<ApiResponse<List<ErpArticleDTO>>> searchArticles(@RequestParam String q) {
        return ResponseEntity.ok(ApiResponse.success(transferService.searchArticles(q)));
    }

    @GetMapping("/erp/stock/item/{itemCode}")
    public ResponseEntity<ApiResponse<List<ErpStockDTO>>> getStockByItem(
            @PathVariable String itemCode) {
        return ResponseEntity.ok(ApiResponse.success(transferService.getStockByItem(itemCode)));
    }

    @GetMapping("/erp/stock/location/{location}")
    public ResponseEntity<ApiResponse<List<ErpStockDTO>>> getStockByLocation(
            @PathVariable String location) {
        return ResponseEntity.ok(ApiResponse.success(transferService.getStockByLocation(location)));
    }

    // ══════════════════════════════════════════════════════════════════════
    // ERP DATA — NOUVEAUX (workflow scan carton)
    // ══════════════════════════════════════════════════════════════════════

    /**
     * GET /api/transfers/erp/stock/lot/{lotNumber}
     *
     * Récupère le stock ERP par numéro de lot (t_clot).
     * Utilisé par Flutter au scan du 1er carton :
     *   scan 202619261819 → article + magasin source + emplacement source
     *
     * FIX 403 : cet endpoint manquait → Spring retournait 403 par défaut.
     */
    @GetMapping("/erp/stock/lot/{lotNumber}")
    public ResponseEntity<ApiResponse<List<ErpStockDTO>>> getStockByLot(
            @PathVariable String lotNumber) {
        return ResponseEntity.ok(ApiResponse.success(
                transferService.getStockByLot(lotNumber)));
    }

    /**
     * GET /api/transfers/erp/location/{locationCode}
     *
     * Vérifie si l'emplacement destination existe et retourne son magasin (t_cwar).
     * Utilisé pour détecter les transferts inter-magasin.
     */
    @GetMapping("/erp/location/{locationCode}")
    public ResponseEntity<ApiResponse<ErpLocationDTO>> getLocationInfo(
            @PathVariable String locationCode) {
        return ResponseEntity.ok(ApiResponse.success(
                transferService.getLocationInfo(locationCode)));
    }
}