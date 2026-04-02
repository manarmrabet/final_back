package com.example.CWMS.controller;

import com.example.CWMS.service.MobileStockService; // Import correct du nouveau service
import com.example.CWMS.dto.ErpLotLineDTO;
import com.example.CWMS.dto.ErpStockDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/erp/stock")
@RequiredArgsConstructor
public class MobileStockController {

    // Utilisation exclusive du service dédié au mobile
    private final MobileStockService mobileService;

    /**
     * Consultation Article : résumé + liste des emplacements (Mobile)
     */
    @GetMapping("/article-summary/{code}")
    public ResponseEntity<?> getArticleSummary(@PathVariable String code) {
        try {
            // Appelle la méthode getArticleSummary de MobileStockService
            return ResponseEntity.ok(mobileService.getArticleSummary(code.trim()));
        } catch (Exception e) {
            return ResponseEntity.status(404).body(Map.of("message", "Article introuvable : " + code));
        }
    }

    /**
     * Consultation Lot : toutes les lignes d'un lot (Mobile)
     */
    @GetMapping("/lot-details/{lotNumber}")
    public ResponseEntity<?> getLotDetails(@PathVariable String lotNumber) {
        try {
            // Appelle la méthode getLotDetails de MobileStockService
            List<ErpLotLineDTO> lines = mobileService.getLotDetails(lotNumber.trim());
            return lines.isEmpty()
                    ? ResponseEntity.status(404).body(Map.of("message", "Aucun résultat pour le lot : " + lotNumber))
                    : ResponseEntity.ok(lines);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Liste des lots par code article (Ancienne logique Flutter)
     */
    @GetMapping("/lots/{code}")
    public ResponseEntity<List<ErpStockDTO>> getLots(@PathVariable String code) {
        // Appelle la méthode getLotsByItem de MobileStockService
        List<ErpStockDTO> results = mobileService.getLotsByItem(code.trim());
        return results.isEmpty() ? ResponseEntity.noContent().build() : ResponseEntity.ok(results);
    }
}
