package com.example.CWMS.service;

import com.example.CWMS.model.cwms.StockTransfer;
import com.example.CWMS.repository.cwms.StockTransferRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.FileWriter;
import java.io.PrintWriter;
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

    private static final int    BATCH_SIZE   = 500;
    private static final String ARCHIVE_DIR  = "archives/transfers/";

    private static final List<String> ARCHIVABLE_STATUSES =
            List.of("DONE", "CANCELLED", "ERROR");

    private final StockTransferRepository transferRepo;
    private final TransactionTemplate     txTemplate;

    /**
     * Archivage automatique : 1er jour de chaque mois à 02h00.
     * - Exporte TOUS les transferts terminés du mois précédent dans un CSV.
     * - Supprime ces lignes de la table stock_transfers.
     */
    @Scheduled(cron = "${cwms.archive.cron:0 0 2 1 * *}")
    public void archiveMonthlyTransfers() {

        // Période : le mois précédent complet
        LocalDate firstDayOfLastMonth = LocalDate.now().minusMonths(1).withDayOfMonth(1);
        LocalDateTime from = firstDayOfLastMonth.atStartOfDay();
        LocalDateTime to   = firstDayOfLastMonth.plusMonths(1).atStartOfDay().minusNanos(1);

        String monthLabel = firstDayOfLastMonth.format(DateTimeFormatter.ofPattern("yyyy-MM"));
        log.info("▶ Archivage mensuel démarré — période : {}", monthLabel);

        long totalArchived = 0;
        int  batchNumber   = 0;

        // Nom du fichier CSV
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String fileName  = ARCHIVE_DIR + "transfers_" + monthLabel + "_" + timestamp + ".csv";

        try {
            Files.createDirectories(Paths.get(ARCHIVE_DIR));

            try (PrintWriter writer = new PrintWriter(new FileWriter(fileName, java.nio.charset.StandardCharsets.UTF_8))) {

                // BOM UTF-8 pour Excel
                writer.print('\uFEFF');

                // En-têtes
                writer.println(
                        "ID;Date création;Code article;Nom article;Lot;" +
                                "Source;Destination;Entrepôt source;Entrepôt dest;" +
                                "Quantité;Unité;Statut;Type;Opérateur;Notes;Date complétion"
                );

                List<StockTransfer> batch;

                do {
                    final LocalDateTime fFrom = from;
                    final LocalDateTime fTo   = to;

                    batch = transferRepo.findTransfersForArchive(
                            fFrom, fTo, ARCHIVABLE_STATUSES, PageRequest.of(0, BATCH_SIZE));

                    final List<StockTransfer> monthBatch = batch;

                    if (monthBatch.isEmpty()) break;

                    // Écriture CSV
                    for (StockTransfer t : monthBatch) {
                        writer.printf("%d;%s;%s;%s;%s;%s;%s;%s;%s;%d;%s;%s;%s;%s;%s;%s%n",
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
                                nvl(t.getStatus()),
                                nvl(t.getTransferType()),
                                t.getOperator() != null
                                        ? csvEscape(t.getOperator().getFirstName() + " " + t.getOperator().getLastName())
                                        : "",
                                csvEscape(t.getNotes()),
                                nvl(t.getCompletedAt())
                        );
                    }

                    // Suppression en base dans une transaction
                    final List<Long> ids = monthBatch.stream()
                            .map(StockTransfer::getId)
                            .collect(Collectors.toList());

                    txTemplate.execute(status -> {
                        transferRepo.deleteAllByIdInBatch(ids);
                        return null;
                    });

                    batchNumber++;
                    totalArchived += monthBatch.size();

                    log.info("  Lot #{} : {} transferts archivés (total={})",
                            batchNumber, monthBatch.size(), totalArchived);

                } while (batch.size() == BATCH_SIZE);
            }

            if (totalArchived == 0) {
                // Supprimer le fichier vide
                Files.deleteIfExists(Paths.get(fileName));
                log.info("▶ Archivage terminé — aucun transfert éligible pour {}", monthLabel);
            } else {
                log.info("▶ Archivage terminé — {} transferts exportés dans : {}", totalArchived, fileName);
            }

        } catch (Exception e) {
            log.error("❌ Échec de l'archivage mensuel : {}", e.getMessage(), e);
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