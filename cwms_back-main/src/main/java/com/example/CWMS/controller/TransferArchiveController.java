package com.example.CWMS.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Endpoints pour la gestion des archives CSV de transferts.
 * GET  /api/transfers/archives/files        → liste des fichiers CSV
 * GET  /api/transfers/archives/files/{name} → téléchargement d'un fichier
 */
@RestController
@RequestMapping("/api/transfers/archives")
@Slf4j
public class TransferArchiveController {

    private static final String ARCHIVE_DIR = "archives/transfers/";

    /**
     * Retourne la liste des fichiers CSV disponibles,
     * triée par date de création décroissante.
     */
    @GetMapping("/files")
    public ResponseEntity<Map<String, Object>> listArchiveFiles() {
        try {
            Path dir = Paths.get(ARCHIVE_DIR);

            if (!Files.exists(dir)) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "Aucune archive disponible",
                        "data",    List.of()
                ));
            }

            List<Map<String, Object>> files = Files.list(dir)
                    .filter(p -> p.getFileName().toString().endsWith(".csv"))
                    .sorted(Comparator.comparingLong(p -> {
                        try { return -Files.getLastModifiedTime(p).toMillis(); }
                        catch (IOException e) { return 0L; }
                    }))
                    .map(p -> {
                        try {
                            BasicFileAttributes attrs = Files.readAttributes(p, BasicFileAttributes.class);
                            long sizeKb = attrs.size() / 1024;

                            // Extraire le mois depuis le nom du fichier (transfers_2025-03_...)
                            String fileName = p.getFileName().toString();
                            String period   = extractPeriod(fileName);

                            String createdAt = attrs.creationTime()
                                    .toInstant()
                                    .atZone(ZoneId.systemDefault())
                                    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));

                            return Map.<String, Object>of(
                                    "fileName",  fileName,
                                    "period",    period,
                                    "createdAt", createdAt,
                                    "sizeKb",    sizeKb
                            );
                        } catch (IOException e) {
                            log.warn("Impossible de lire les métadonnées de {}", p, e);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", files.size() + " archive(s) disponible(s)",
                    "data",    files
            ));

        } catch (IOException e) {
            log.error("Erreur lors de la liste des archives", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "Erreur serveur : " + e.getMessage()
            ));
        }
    }

    /**
     * Télécharge un fichier CSV par son nom.
     */
    @GetMapping("/files/{fileName}")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable String fileName) {

        // Sécurité : interdire la traversée de répertoire
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            return ResponseEntity.badRequest().build();
        }
        if (!fileName.endsWith(".csv")) {
            return ResponseEntity.badRequest().build();
        }

        try {
            Path filePath = Paths.get(ARCHIVE_DIR).resolve(fileName).normalize();
            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                log.warn("Fichier introuvable ou illisible : {}", fileName);
                return ResponseEntity.notFound().build();
            }

            log.info("Téléchargement de l'archive : {}", fileName);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + fileName + "\"")
                    .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                    .body(resource);

        } catch (Exception e) {
            log.error("Erreur lors du téléchargement de {}", fileName, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Extrait la période lisible depuis le nom du fichier.
     * Ex: "transfers_2025-03_20250401_020000.csv" → "Mars 2025"
     */
    private String extractPeriod(String fileName) {
        try {
            // Format attendu : transfers_YYYY-MM_...
            String[] parts = fileName.split("_");
            if (parts.length >= 2) {
                String[] yearMonth = parts[1].split("-");
                if (yearMonth.length == 2) {
                    int year  = Integer.parseInt(yearMonth[0]);
                    int month = Integer.parseInt(yearMonth[1]);
                    String[] months = {
                            "Janvier","Février","Mars","Avril","Mai","Juin",
                            "Juillet","Août","Septembre","Octobre","Novembre","Décembre"
                    };
                    return months[month - 1] + " " + year;
                }
            }
        } catch (Exception ignored) {}
        return fileName;
    }
}