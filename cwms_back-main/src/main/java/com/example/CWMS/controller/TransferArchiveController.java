package com.example.CWMS.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
 *
 * GET  /api/transfers/archives/files        → liste des fichiers CSV
 * GET  /api/transfers/archives/files/{name} → téléchargement d'un fichier
 *
 * BUG 3 CORRIGÉ — ARCHIVE_DIR lu depuis application.properties (cwms.archive.dir).
 * BUG 4 CORRIGÉ — extractPeriod() gère les noms TEST_* sans lever d'exception.
 */
@RestController
@RequestMapping("/api/transfers/archives")
@Slf4j
public class TransferArchiveController {

    /**
     * Chemin absolu injecté depuis application.properties.
     *
     * Exemple application.properties :
     *   cwms.archive.dir=/var/cwms/archives/         (Linux serveur)
     *   cwms.archive.dir=C:/cwms/archives/           (Windows dev)
     *
     * Même propriété que TransferArchiveScheduler — un seul endroit à configurer.
     */
    @Value("${cwms.archive.dir}")
    private String archiveDir;

    @GetMapping("/files")
    public ResponseEntity<Map<String, Object>> listArchiveFiles() {
        try {
            Path dir = Paths.get(archiveDir);

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
                            long sizeKb    = attrs.size() / 1024;
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

    @GetMapping("/files/{fileName}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String fileName) {

        // Sécurité : bloquer les tentatives de path traversal
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            return ResponseEntity.badRequest().build();
        }
        if (!fileName.endsWith(".csv")) {
            return ResponseEntity.badRequest().build();
        }

        try {
            Path filePath = Paths.get(archiveDir).resolve(fileName).normalize();
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
     * BUG 4 CORRIGÉ — extractPeriod() ne plante plus sur les noms TEST_*.
     *
     * Ancienne version : supposait TOUJOURS le format transfers_YYYY-MM_timestamp.csv.
     * Un fichier TEST_20250503_143000.csv levait une NumberFormatException avalée
     * silencieusement, retournant le nom brut sans lisibilité dans l'UI.
     *
     * Nouvelle version :
     *   - Préfixe TEST_ → libellé "Test — horodatage" lisible
     *   - Format standard → "Mois Année" (ex: "Mai 2025")
     *   - Fallback → nom brut (sans exception)
     */
    private String extractPeriod(String fileName) {
        try {
            // Cas fichiers de test : TEST_yyyyMMdd_HHmmss.csv
            if (fileName.startsWith("TEST_")) {
                String ts = fileName.replace("TEST_", "").replace(".csv", "");
                // ts = "yyyyMMdd_HHmmss" → affichage lisible
                if (ts.length() >= 8) {
                    String date = ts.substring(0, 4) + "-" + ts.substring(4, 6) + "-" + ts.substring(6, 8);
                    return "Test — " + date;
                }
                return "Archive de test";
            }

            // Cas standard : transfers_YYYY-MM_yyyyMMdd_HHmmss.csv
            // Supprimer le préfixe "transfers_" et l'extension ".csv"
            String withoutExt    = fileName.endsWith(".csv") ? fileName.substring(0, fileName.length() - 4) : fileName;
            String withoutPrefix = withoutExt.startsWith("transfers_") ? withoutExt.substring("transfers_".length()) : withoutExt;

            // Le premier segment avant "_" doit être YYYY-MM
            String[] segments  = withoutPrefix.split("_");
            String   yearMonth = segments[0]; // ex: "2025-05"
            String[] parts     = yearMonth.split("-");

            if (parts.length == 2) {
                int year  = Integer.parseInt(parts[0]);
                int month = Integer.parseInt(parts[1]);
                if (month >= 1 && month <= 12) {
                    String[] months = {
                            "Janvier","Février","Mars","Avril","Mai","Juin",
                            "Juillet","Août","Septembre","Octobre","Novembre","Décembre"
                    };
                    return months[month - 1] + " " + year;
                }
            }
        } catch (Exception e) {
            log.debug("extractPeriod — impossible de parser '{}' : {}", fileName, e.getMessage());
        }
        // Fallback : nom brut sans extension
        return fileName.endsWith(".csv") ? fileName.substring(0, fileName.length() - 4) : fileName;
    }
}