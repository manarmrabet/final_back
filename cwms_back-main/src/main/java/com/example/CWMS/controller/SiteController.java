package com.example.CWMS.controller;

import com.example.CWMS.dto.ApiResponse; // Vérifiez le package de votre ApiResponse
import com.example.CWMS.model.cwms.Site;
import com.example.CWMS.iservice.SiteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/sites")
@RequiredArgsConstructor // Utilise Lombok pour l'injection propre du service
public class SiteController {

    private final SiteService siteService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Site>>> getAllSites() {
        List<Site> sites = siteService.getAllSites();
        return ResponseEntity.ok(ApiResponse.success(sites));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Site>> getSiteById(@PathVariable Integer id) {
        Site site = siteService.getSiteById(id);
        return ResponseEntity.ok(ApiResponse.success(site));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Site>> createSite(@RequestBody Site site) {
        Site createdSite = siteService.createSite(site);
        return ResponseEntity.ok(ApiResponse.success("Site créé avec succès", createdSite));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Site>> updateSite(@PathVariable Integer id, @RequestBody Site siteDetails) {
        Site updatedSite = siteService.updateSite(id, siteDetails);
        return ResponseEntity.ok(ApiResponse.success("Site mis à jour avec succès", updatedSite));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteSite(@PathVariable Integer id) {
        siteService.deleteSite(id);
        return ResponseEntity.ok(ApiResponse.success("Site supprimé avec succès", null));
    }
}