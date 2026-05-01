package com.example.CWMS.service;

import com.example.CWMS.model.cwms.AuditLog;
import com.example.CWMS.repository.cwms.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditArchiveService {

    private final AuditLogRepository auditLogRepository;

    // Chemin relatif à la racine du projet
    private static final String ARCHIVE_DIR = "archives/audit_logs/";

    /**
     * S'exécute tous les jours à 01h00 du matin.
     * Rétention en base : 2 semaines.
     */
    @Scheduled(cron = "0 0 1 * * ?")
    @Transactional
    public void processArchiving() {
        LocalDateTime cutoffDate = LocalDateTime.now().minusWeeks(2);
        log.info("🚀 Début de l'archivage des logs antérieurs au : {}", cutoffDate);

        List<AuditLog> logsToArchive = auditLogRepository.findOldLogs(cutoffDate);

        if (logsToArchive.isEmpty()) {
            log.info("✅ Aucun log de plus de 2 semaines à traiter.");
            return;
        }

        try {
            // 1. Créer le dossier s'il n'existe pas
            Files.createDirectories(Paths.get(ARCHIVE_DIR));

            // 2. Préparer le nom du fichier (ex: archive_2024-05-20.csv)
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String fileName = ARCHIVE_DIR + "audit_backup_" + timestamp + ".csv";

            // 3. Écriture du CSV
            // ✅ APRÈS — encodage UTF-8 explicite
            try (PrintWriter writer = new PrintWriter(
                    new OutputStreamWriter(
                            new FileOutputStream(fileName),
                            StandardCharsets.UTF_8))) {
                // Header CSV
                writer.println("ID;CreatedAt;User;Action;Type;Severity;Endpoint;Status;OldValue;NewValue");

                for (AuditLog logEntry : logsToArchive) {
                    writer.printf("%d;%s;%s;%s;%s;%s;%s;%d;\"%s\";\"%s\"%n",
                            logEntry.getId(),
                            logEntry.getCreatedAt(),
                            logEntry.getUsername(),
                            clean(logEntry.getAction()),
                            logEntry.getEventType(),
                            logEntry.getSeverity(),
                            logEntry.getEndpoint(),
                            logEntry.getStatusCode(),
                            escapeJson(logEntry.getOldValue()),
                            escapeJson(logEntry.getNewValue())
                    );
                }
            }

            log.info("💾 Archive générée avec succès : {}", fileName);

            // 4. Suppression de la base de données
            auditLogRepository.deleteOldLogs(cutoffDate);
            log.info("🗑️ {} logs supprimés de la table audit_logs.", logsToArchive.size());

        } catch (Exception e) {
            log.error("❌ Échec critique de l'archivage : {}", e.getMessage(), e);
        }
    }

    private String clean(String s) {
        return s != null ? s.replace(";", ",") : "";
    }

    private String escapeJson(String json) {
        if (json == null) return "";
        return json.replace("\"", "'").replace("\n", " ").replace(";", ",");
    }
}