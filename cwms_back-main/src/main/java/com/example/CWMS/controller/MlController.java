package com.example.CWMS.controller;

import com.example.CWMS.dto.AnomalyAlertDTO;
import com.example.CWMS.service.MlAnomalyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;


/**
 * MlController — VERSION CORRIGÉE
 * ─────────────────────────────────
 * Ajout de /api/ml/health qui proxie vers FastAPI.
 * Angular appelle ce endpoint (pas FastAPI directement) → pas de CORS.
 *
 * Chemin : controller/MlController.java
 */
@RestController
@RequestMapping("/api/ml")
@RequiredArgsConstructor
@Slf4j
public class MlController {

    private final MlAnomalyService mlAnomalyService;

    @Qualifier("mlRestTemplate")
    private final RestTemplate mlRestTemplate;

    @Value("${cwms.ml.base-url:http://localhost:8000}")
    private String mlBaseUrl;

    /**
     * Proxie le /health de FastAPI.
     * Angular appelle /api/ml/health → Spring Boot appelle http://localhost:8000/health.
     * Pas de CORS côté navigateur.
     */
    @GetMapping("/health")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getMlHealth() {
        try {
            Map<?, ?> health = mlRestTemplate.getForObject("/health", Map.class);
            return ResponseEntity.ok(health);
        } catch (RestClientException e) {
            log.warn("[ML] FastAPI health check failed : {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "status", "unavailable",
                    "models_loaded", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Retourne les anomalies des 7 derniers jours stockées en base.
     */
    @GetMapping("/anomalies")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<AnomalyAlertDTO>> getAnomalies() {
        return ResponseEntity.ok(mlAnomalyService.getRecentAnomalies());
    }

    /**
     * Déclenche manuellement le batch de détection (demo PFE).
     */
    @PostMapping("/anomalies/trigger")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> trigger() {
        mlAnomalyService.triggerManually();
        return ResponseEntity.ok("Détection lancée — vérifiez les logs et l'email.");
    }
}