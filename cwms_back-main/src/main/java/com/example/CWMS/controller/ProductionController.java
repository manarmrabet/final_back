package com.example.CWMS.controller;

import com.example.CWMS.dto.*;
import com.example.CWMS.iservice.IProductionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/production")
@RequiredArgsConstructor
// PAS de @CrossOrigin ici — centralisé dans CorsConfig
public class ProductionController {

    private final IProductionService productionService;

    // ── Vérification stock (GET — tous les rôles connectés) ─────────────────
    @GetMapping("/check/{clot}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<StockCheckDTO> checkStock(@PathVariable String clot) {
        return ResponseEntity.ok(productionService.checkStock(clot));
    }

    // ── Sortie totale ────────────────────────────────────────────────────────
    @PostMapping("/sortie/totale")
    @PreAuthorize("hasAnyRole('OPERATEUR','ADMIN','MANAGER','MAGASINIER','RESPONSABLE_MAGASINIER')")
    public ResponseEntity<SortieResponseDTO> sortieTotale(
            @Valid @RequestBody SortieRequestDTO req,
            @AuthenticationPrincipal UserDetails user) {
        // userId et userName extraits du JWT — jamais du body
        Long   userId   = extractUserId(user);
        String userName = user.getUsername();
        req.setSortieComplete(true);
        return ResponseEntity.ok(productionService.sortieTotale(req, userId, userName));
    }

    // ── Sortie partielle ─────────────────────────────────────────────────────
    @PostMapping("/sortie/partielle")
    @PreAuthorize("hasAnyRole('OPERATEUR','ADMIN','MANAGER','MAGASINIER','RESPONSABLE_MAGASINIER')")
    public ResponseEntity<SortieResponseDTO> sortiePartielle(
            @Valid @RequestBody SortieRequestDTO req,
            @AuthenticationPrincipal UserDetails user) {
        req.setSortieComplete(false);
        Long   userId   = extractUserId(user);
        String userName = user.getUsername();
        return ResponseEntity.ok(productionService.sortiePartielle(req, userId, userName));
    }

    // ── Dashboard — tous les logs ────────────────────────────────────────────
    @GetMapping("/logs")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<List<ProductionLogDTO>> getAllLogs() {
        return ResponseEntity.ok(productionService.getAllLogs());
    }

    // ── Dashboard — logs du jour ─────────────────────────────────────────────
    @GetMapping("/logs/today")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','OPERATEUR')")
    public ResponseEntity<List<ProductionLogDTO>> getTodayLogs() {
        return ResponseEntity.ok(productionService.getTodayLogs());
    }

    // ── Stats dashboard ──────────────────────────────────────────────────────
    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ProductionStatsDTO> getStats() {
        return ResponseEntity.ok(productionService.getStats());
    }

    // ── Mes logs (opérateur) ─────────────────────────────────────────────────
    @GetMapping("/logs/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ProductionLogDTO>> getMyLogs(
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(productionService.getLogsByUser(extractUserId(user)));
    }

    // ── Helper : extraire userId depuis le UserDetails de votre Security ──────
    // Adaptez selon votre implémentation de UserDetails (champ id)
    private Long extractUserId(UserDetails user) {
        // Si votre UserDetails implémente une interface avec getId() :
        // if (user instanceof com.example.CWMS.Security.UserDetailsImpl u) return u.getId();
        // Fallback : hashCode comme id temporaire (remplacez par votre impl)
        return (long) user.getUsername().hashCode();
    }
}