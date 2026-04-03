package com.example.CWMS.service;

import com.example.CWMS.model.cwms.StockTransfer;
import com.example.CWMS.repository.cwms.StockTransferRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransferArchiveScheduler {

    private static final int BATCH_SIZE = 500;

    // ✅ CORRECTION : chemin lu depuis application.properties
    // Même propriété que TransferArchiveController → cohérence garantie
    @Value("${cwms.archive.dir}")
    private String archiveDir;

    // ✅ CORRECTION : liste d'enums typés au lieu de List<String>
    private static final List<StockTransfer.TransferStatus> ARCHIVABLE_STATUSES = List.of(
            StockTransfer.TransferStatus.DONE,
            StockTransfer.TransferStatus.CANCELLED,
            StockTransfer.TransferStatus.ERROR
    );

    private final StockTransferRepository transferRepo;
    private final TransactionTemplate     txTemplate;

    /**
     * Archivage automatique : 1er jour de chaque mois à 02h00.
     * - Exporte tous les transferts terminés du mois précédent dans un CSV.
     * - Supprime ces lignes de la table stock_transfers.
     */
    @Scheduled(cron = "${cwms.archive.cron:0 0 2 1 * *}")
    public void archiveMonthlyTransfers() {

        LocalDate firstDayOfLastMonth = LocalDate.now().minusMonths(1).withDayOfMonth(1);
        LocalDateTime from = firstDayOfLastMonth.atStartOfDay();
        LocalDateTime to   = firstDayOfLastMonth.plusMonths(1).atStartOfDay().minusNanos(1);

        String monthLabel = firstDayOfLastMonth.format(DateTimeFormatter.ofPattern("yyyy-MM"));
        log.info("Archivage mensuel démarré — période : {}", monthLabel);

        long totalArchived = 0;
        int  batchNumber   = 0;

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String fileName  = archiveDir + "transfers_" + monthLabel + "_" + timestamp + ".csv";

        try {
            Files.createDirectories(Paths.get(archiveDir));

            // ✅ CORRECTION : BufferedWriter au lieu de PrintWriter direct
            // Réduit les appels système (flush par buffer de 8Ko au lieu de ligne par ligne)
            try (BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(
                            new FileOutputStream(fileName), StandardCharsets.UTF_8))) {

                // BOM UTF-8 pour Excel
                writer.write('\uFEFF');

                writer.write("ID;Date création;Code article;Nom article;Lot;" +
                        "Source;Destination;Entrepôt source;Entrepôt dest;" +
                        "Quantité;Unité;Statut;Type;Opérateur;Notes;Date complétion");
                writer.newLine();

                List<StockTransfer> batch;

                do {
                    batch = transferRepo.findTransfersForArchive(
                            from, to, ARCHIVABLE_STATUSES, PageRequest.of(0, BATCH_SIZE));

                    if (batch.isEmpty()) break;

                    for (StockTransfer t : batch) {
                        writer.write(String.format("%d;%s;%s;%s;%s;%s;%s;%s;%s;%d;%s;%s;%s;%s;%s;%s",
                                t.getId(),
                                nvl(t.getCreatedAt()),
                                nvl(t.getErpItemCode()),
                                csvEscape(t.getErpItemLabel()),
                                nvl(t.getLotNumber()),
                                nvl(t.getSourceLocation()),
                                nvl(t.getDestLocation()),
                                nvl(t.getSourceWarehouse()),
                                nvl(t.getDestWarehouse()),
                                t.getQuantity() != null ? t.getQuantity() : 0,
                                nvl(t.getUnit()),
                                // ✅ .name() pour convertir l'enum en String
                                t.getStatus()       != null ? t.getStatus().name()       : "",
                                t.getTransferType() != null ? t.getTransferType().name() : "",
                                t.getOperator() != null
                                        ? csvEscape(t.getOperator().getFirstName() + " " + t.getOperator().getLastName())
                                        : "",
                                csvEscape(t.getNotes()),
                                nvl(t.getCompletedAt())
                        ));
                        writer.newLine();
                    }

                    final List<Long> ids = batch.stream()
                            .map(StockTransfer::getId)
                            .collect(Collectors.toList());

                    txTemplate.execute(status -> {
                        transferRepo.deleteAllByIdInBatch(ids);
                        return null;
                    });

                    batchNumber++;
                    totalArchived += batch.size();
                    log.info("  Lot #{} : {} transferts archivés (total={})",
                            batchNumber, batch.size(), totalArchived);

                } while (batch.size() == BATCH_SIZE);
            }

            if (totalArchived == 0) {
                Files.deleteIfExists(Paths.get(fileName));
                log.info("Archivage terminé — aucun transfert éligible pour {}", monthLabel);
            } else {
                log.info("Archivage terminé — {} transferts exportés dans : {}", totalArchived, fileName);
            }

        } catch (Exception e) {
            log.error("Échec de l'archivage mensuel : {}", e.getMessage(), e);
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private String nvl(Object o) {
        return o == null ? "" : o.toString();
    }

    private String csvEscape(String s) {
        if (s == null) return "";
        if (s.contains(";") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}