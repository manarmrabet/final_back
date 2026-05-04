package com.example.CWMS.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Nettoyage automatique des fichiers CSV d'archives expirés.
 *
 * Règle métier : conservation 3 mois (configurable), puis suppression.
 * Exécution   : tous les jours à 3h00 (après l'archivage à 2h00 le 1er).
 *
 * application.properties :
 *   cwms.archive.dir=C:/cwms/archives/
 *   cwms.archive.retention-months=3     # optionnel, défaut 3
 *   cwms.cleanup.cron=0 0 3 * * *       # optionnel
 */
@Service
@Slf4j
public class TransferCleanupScheduler {

    @Value("${cwms.archive.dir}")
    private String archiveDir;

    @Value("${cwms.archive.retention-months:3}")
    private int retentionMonths;

    @Scheduled(cron = "${cwms.cleanup.cron:0 0 3 * * *}")
    public void cleanOldArchives() {
        Path dir = Paths.get(archiveDir);

        if (!Files.exists(dir)) {
            log.debug("[Cleanup] Répertoire absent — rien à nettoyer");
            return;
        }

        Instant cutoff = LocalDateTime.now()
                .minusMonths(retentionMonths)
                .atZone(ZoneId.systemDefault())
                .toInstant();

        log.info("[Cleanup] Suppression des archives avant le {} (rétention {} mois)",
                LocalDateTime.ofInstant(cutoff, ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                retentionMonths);

        List<String> deleted = new ArrayList<>();
        List<String> errors  = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.csv")) {
            for (Path file : stream) {
                try {
                    BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
                    if (attrs.lastModifiedTime().toInstant().isBefore(cutoff)) {
                        Files.delete(file);
                        deleted.add(file.getFileName().toString());
                    }
                } catch (IOException e) {
                    errors.add(file.getFileName().toString());
                    log.warn("[Cleanup] Impossible de supprimer {} : {}", file.getFileName(), e.getMessage());
                }
            }
        } catch (IOException e) {
            log.error("[Cleanup] Erreur parcours {} : {}", archiveDir, e.getMessage(), e);
            return;
        }

        if (deleted.isEmpty() && errors.isEmpty()) {
            log.info("[Cleanup] Aucun fichier à supprimer");
        } else {
            if (!deleted.isEmpty()) log.info("[Cleanup] Supprimés ({}) : {}", deleted.size(), deleted);
            if (!errors.isEmpty())  log.warn("[Cleanup] Erreurs   ({}) : {}", errors.size(),  errors);
        }
    }
}