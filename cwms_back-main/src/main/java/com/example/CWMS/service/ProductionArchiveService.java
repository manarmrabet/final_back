package com.example.CWMS.service;

import com.example.CWMS.model.cwms.ProductionLog;
import com.example.CWMS.repository.cwms.ProductionLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * ══════════════════════════════════════════════════════════════════════════
 *  ProductionArchiveService
 *
 *  Calqué exactement sur AuditArchiveService pour garder la cohérence.
 *
 *  Tâches planifiées :
 *  ① 02h00 chaque nuit  → archive les ProductionLog > 2 semaines en CSV
 *                          puis les supprime de la table SQL
 *  ② 03h00 chaque nuit  → supprime les CSV de plus d'un mois du disque
 *
 *  Méthode publique triggerManualArchive() :
 *      Appelée par ProductionArchiveController pour forcer l'archivage
 *      immédiatement (dev/test) sans attendre 02h00.
 * ══════════════════════════════════════════════════════════════════════════
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductionArchiveService {

    private final ProductionLogRepository productionLogRepository;

    /** Dossier de stockage — cohérent avec archives/audit_logs/ */
    private static final String ARCHIVE_DIR = "archives/production_logs/";

    private static final DateTimeFormatter FILE_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    // ════════════════════════════════════════════════════════════════════════
    //  ① Archivage automatique quotidien à 02h00
    //     Même pattern que AuditArchiveService.processArchiving()
    // ════════════════════════════════════════════════════════════════════════
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void processArchiving() {
        doArchive(LocalDateTime.now().minusWeeks(2));
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Déclenchement MANUEL — appelé par ProductionArchiveController
    //  Retourne le nom du fichier généré, ou null si rien à archiver.
    // ════════════════════════════════════════════════════════════════════════
    @Transactional
    public String triggerManualArchive() {
        return doArchive(LocalDateTime.now().minusWeeks(2));
    }

    // ════════════════════════════════════════════════════════════════════════
    //  ② Nettoyage des CSV de plus d'un mois à 03h00
    // ════════════════════════════════════════════════════════════════════════
    @Scheduled(cron = "0 0 3 * * ?")
    public void deleteOldCsvFiles() {
        log.info("🧹 [Production] Nettoyage des CSV > 1 mois dans {}", ARCHIVE_DIR);

        var dir = Paths.get(ARCHIVE_DIR);
        if (!Files.exists(dir)) return;

        try {
            long oneMonthAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000);
            long[] stats = {0, 0}; // [supprimés, erreurs]

            Files.list(dir)
                    .filter(p -> p.toString().endsWith(".csv"))
                    .forEach(p -> {
                        try {
                            if (Files.getLastModifiedTime(p).toMillis() < oneMonthAgo) {
                                Files.delete(p);
                                log.info("🗑️ [Production] CSV supprimé : {}", p.getFileName());
                                stats[0]++;
                            }
                        } catch (Exception e) {
                            log.warn("⚠️ [Production] Impossible de supprimer {} : {}",
                                    p.getFileName(), e.getMessage());
                            stats[1]++;
                        }
                    });

            log.info("✅ [Production] Nettoyage terminé : {} supprimé(s), {} erreur(s).",
                    stats[0], stats[1]);

        } catch (Exception e) {
            log.error("❌ [Production] Erreur nettoyage CSV : {}", e.getMessage(), e);
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Logique d'archivage partagée (scheduler + trigger manuel)
    //  Retourne le chemin du fichier créé, ou null si rien archivé.
    // ════════════════════════════════════════════════════════════════════════
    private String doArchive(LocalDateTime cutoff) {
        log.info("🚀 [Production] Archivage des logs antérieurs au : {}", cutoff);

        List<ProductionLog> toArchive = productionLogRepository.findOldLogs(cutoff);

        if (toArchive.isEmpty()) {
            log.info("✅ [Production] Aucun log de plus de 2 semaines à archiver.");
            return null;
        }

        try {
            // 1. Créer le dossier si absent — même logique qu'AuditArchiveService
            Files.createDirectories(Paths.get(ARCHIVE_DIR));

            // 2. Nom horodaté : production_backup_YYYYMMDD_HHmmss.csv
            String timestamp = LocalDateTime.now().format(FILE_FMT);
            String fileName  = ARCHIVE_DIR + "production_backup_" + timestamp + ".csv";

            // 3. Écriture CSV — UTF-8 explicite (même approche qu'AuditArchiveService)
            try (PrintWriter writer = new PrintWriter(
                    new OutputStreamWriter(
                            new FileOutputStream(fileName),
                            StandardCharsets.UTF_8))) {

                // En-tête CSV
                writer.println(
                        "ID;CreatedAt;LotCode;ItemCode;Warehouse;Location;" +
                                "QtyBefore;QtyRequested;QtyAfter;" +
                                "OperationType;Status;UserId;UserName;" +
                                "DeviceInfo;Source;Notes;ErrorMessage"
                );

                // Lignes de données
                for (ProductionLog e : toArchive) {
                    writer.printf(
                            "%d;%s;%s;%s;%s;%s;%.2f;%.2f;%.2f;%s;%s;%d;%s;%s;%s;\"%s\";\"%s\"%n",
                            e.getId(),
                            e.getCreatedAt(),
                            safe(e.getLotCode()),
                            safe(e.getItemCode()),
                            safe(e.getWarehouse()),
                            safe(e.getLocation()),
                            nvl(e.getQtyBefore()),
                            nvl(e.getQtyRequested()),
                            nvl(e.getQtyAfter()),
                            e.getOperationType() != null ? e.getOperationType().name() : "",
                            e.getStatus()        != null ? e.getStatus().name()        : "",
                            e.getUserId()        != null ? e.getUserId()               : 0L,
                            safe(e.getUserName()),
                            safe(e.getDeviceInfo()),
                            e.getSource()        != null ? e.getSource().name()        : "",
                            escape(e.getNotes()),
                            escape(e.getErrorMessage())
                    );
                }
            }

            log.info("💾 [Production] Archive créée : {} ({} lignes)",
                    fileName, toArchive.size());

            // 4. Suppression BDD — APRÈS écriture réussie (jamais de perte de données)
            productionLogRepository.deleteOldLogs(cutoff);
            log.info("🗑️ [Production] {} lignes supprimées de ProductionLog.",
                    toArchive.size());

            return fileName;

        } catch (Exception e) {
            log.error("❌ [Production] Échec archivage : {}", e.getMessage(), e);
            return null;
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Helpers — identiques au style AuditArchiveService
    // ════════════════════════════════════════════════════════════════════════

    private String safe(String s) {
        return s != null ? s.replace(";", ",").trim() : "";
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\"", "'").replace("\n", " ").replace(";", ",");
    }

    private double nvl(Double d) {
        return d != null ? d : 0.0;
    }
}