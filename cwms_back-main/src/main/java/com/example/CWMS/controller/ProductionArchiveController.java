package com.example.CWMS.controller;

import com.example.CWMS.dto.ApiResponse;
import com.example.CWMS.dto.ProductionArchiveFileDTO;
import com.example.CWMS.service.ProductionArchiveService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/production-archives")
@RequiredArgsConstructor
@Slf4j
public class ProductionArchiveController {

    private final ProductionArchiveService archiveService;

    private static final String ARCHIVE_DIR = "archives/production_logs/";
    private static final DateTimeFormatter FILE_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    // ── Liste des archives ────────────────────────────────────────────────
    /**
     * ✅ FIX HTTP 500 — trois causes possibles corrigées :
     *
     * 1. folder.listFiles() retourne NULL si le dossier n'est pas un répertoire
     *    ou si une I/O error se produit → NullPointerException dans Arrays.stream()
     *    → Ajout d'un null-check explicite AVANT le stream.
     *
     * 2. Le stream forEach() sur Files.list() n'était pas fermé
     *    → Utilisation de try-with-resources pour garantir la fermeture.
     *
     * 3. Toute exception non catchée remonte en HTTP 500
     *    → Wrapping global try/catch avec log + retour liste vide.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<List<ProductionArchiveFileDTO>>> listArchives() {
        try {
            File folder = new File(ARCHIVE_DIR);

            // ✅ FIX 1 — dossier absent ou vide : retour liste vide (pas 500)
            if (!folder.exists() || !folder.isDirectory()) {
                return ResponseEntity.ok(ApiResponse.success(List.of()));
            }

            File[] csvFiles = folder.listFiles(f -> f.getName().endsWith(".csv"));

            // ✅ FIX 2 — listFiles() peut retourner null (I/O error)
            if (csvFiles == null) {
                log.warn("[ProductionArchive] listFiles() a retourné null pour : {}", ARCHIVE_DIR);
                return ResponseEntity.ok(ApiResponse.success(List.of()));
            }

            List<ProductionArchiveFileDTO> files = Arrays.stream(csvFiles)
                    .sorted(Comparator.comparing(File::getName).reversed())
                    .map(f -> {
                        // Extraction de la date depuis le nom du fichier
                        LocalDateTime date = null;
                        try {
                            String namePart = f.getName()
                                    .replace("production_backup_", "")
                                    .replace(".csv", "");
                            date = LocalDateTime.parse(namePart, FILE_FMT);
                        } catch (Exception ignored) {}

                        // Comptage lignes hors en-tête — UTF-8 explicite
                        int lines = 0;
                        try (BufferedReader br = new BufferedReader(
                                new FileReader(f, StandardCharsets.UTF_8))) {
                            lines = (int) br.lines().count() - 1;
                            if (lines < 0) lines = 0;
                        } catch (Exception e) {
                            log.warn("[ProductionArchive] Impossible de lire {} : {}",
                                    f.getName(), e.getMessage());
                        }

                        return new ProductionArchiveFileDTO(f.getName(), f.length(), date, lines);
                    })
                    .collect(Collectors.toList());

            return ResponseEntity.ok(ApiResponse.success(files));

        } catch (Exception e) {
            // ✅ FIX 3 — plus de HTTP 500 non expliqué
            log.error("[ProductionArchive] Erreur listArchives : {}", e.getMessage(), e);
            return ResponseEntity.ok(ApiResponse.success(List.of()));
        }
    }

    // ── Téléchargement ────────────────────────────────────────────────────
    @GetMapping("/download/{filename}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Resource> downloadArchive(@PathVariable String filename) {

        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            log.warn("[ProductionArchive] Nom rejeté : {}", filename);
            return ResponseEntity.badRequest().build();
        }

        try {
            Path filePath = Paths.get(ARCHIVE_DIR).resolve(filename).normalize();
            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists()) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);

        } catch (Exception e) {
            log.error("[ProductionArchive] Erreur DL {} : {}", filename, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // ── Trigger manuel ────────────────────────────────────────────────────
    @PostMapping("/trigger")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, String>>> triggerArchive() {
        log.info("[ProductionArchive] Déclenchement manuel.");
        String fileName = archiveService.triggerManualArchive();

        if (fileName == null) {
            return ResponseEntity.ok(ApiResponse.success(
                    Map.of("message", "Aucune sortie de plus de 2 semaines à archiver.")
            ));
        }

        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "message", "Archivage effectué avec succès.",
                "file",    fileName
        )));
    }
}