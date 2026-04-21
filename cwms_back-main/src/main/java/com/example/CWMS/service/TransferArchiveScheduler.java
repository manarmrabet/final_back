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

    private static final int BATCH = 500;

    @Value("${cwms.archive.dir}")
    private String archiveDir;

    private static final List<StockTransfer.TransferStatus> ARCHIVABLE = List.of(
            StockTransfer.TransferStatus.DONE,
            StockTransfer.TransferStatus.CANCELLED,
            StockTransfer.TransferStatus.ERROR
    );

    private final StockTransferRepository repo;
    private final TransactionTemplate     tx;

    // ── Automatic — 1st of every month at 02:00 ──────────────────────────────

    @Scheduled(cron = "${cwms.archive.cron:0 0 2 1 * *}")
    public void archiveMonthlyTransfers() {
        LocalDate first = LocalDate.now().minusMonths(1).withDayOfMonth(1);
        LocalDateTime from = first.atStartOfDay();
        LocalDateTime to   = first.plusMonths(1).atStartOfDay().minusNanos(1);
        String label = first.format(DateTimeFormatter.ofPattern("yyyy-MM"));
        log.info("[Archive] mensuel démarré — période {}", label);
        doArchive(from, to, label);
    }

    // ── Test mode — archives last 5 minutes ──────────────────────────────────
    //    Triggered via: POST /api/transfers/archives/trigger?testMode=true

    public void archiveForTesting() {
        LocalDateTime to   = LocalDateTime.now();
        LocalDateTime from = to.minusMinutes(5);
        String label = "TEST_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        log.info("[Archive] TEST — fenêtre {} → {}", from, to);
        doArchive(from, to, label);
    }

    // ── Core logic ────────────────────────────────────────────────────────────

    private void doArchive(LocalDateTime from, LocalDateTime to, String label) {
        long total = 0;
        int  batch = 0;
        String ts   = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String path = archiveDir + "transfers_" + label + "_" + ts + ".csv";

        try {
            Files.createDirectories(Paths.get(archiveDir));

            try (BufferedWriter w = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(path), StandardCharsets.UTF_8))) {

                w.write('\uFEFF'); // BOM for Excel
                w.write("ID;Date création;Code article;Nom article;Lot;" +
                        "Source;Destination;Entrepôt src;Entrepôt dst;" +
                        "Quantité;Unité;Statut;Opérateur;Notes;Date complétion");
                w.newLine();

                List<StockTransfer> page;
                do {
                    page = repo.findTransfersForArchive(from, to, ARCHIVABLE, PageRequest.of(0, BATCH));
                    if (page.isEmpty()) break;

                    for (StockTransfer t : page) {
                        w.write(String.format("%d;%s;%s;%s;%s;%s;%s;%s;%s;%d;%s;%s;%s;%s;%s",
                                t.getId(),
                                nvl(t.getCreatedAt()),
                                nvl(t.getErpItemCode()),
                                esc(t.getErpItemLabel()),
                                nvl(t.getLotNumber()),
                                nvl(t.getSourceLocation()),
                                nvl(t.getDestLocation()),
                                nvl(t.getSourceWarehouse()),
                                nvl(t.getDestWarehouse()),
                                t.getQuantity() != null ? t.getQuantity() : 0,
                                nvl(t.getUnit()),
                                t.getStatus()       != null ? t.getStatus().name()       : "",
                                t.getOperator()     != null
                                        ? esc(t.getOperator().getFirstName() + " " + t.getOperator().getLastName())
                                        : "",
                                esc(t.getNotes()),
                                nvl(t.getCompletedAt())
                        ));
                        w.newLine();
                    }

                    // Delete batch in its own transaction
                    List<Long> ids = page.stream().map(StockTransfer::getId).collect(Collectors.toList());
                    tx.execute(s -> { repo.deleteAllByIdInBatch(ids); return null; });

                    total += page.size();
                    log.info("  batch #{} — {} archivés (total={})", ++batch, page.size(), total);

                } while (page.size() == BATCH);
            }

            if (total == 0) {
                Files.deleteIfExists(Paths.get(path));
                log.info("[Archive] {} — aucun transfert éligible", label);
            } else {
                log.info("[Archive] {} — {} transferts → {}", label, total, path);
            }

        } catch (Exception e) {
            log.error("[Archive] {} ÉCHEC : {}", label, e.getMessage(), e);
            throw new RuntimeException("Archivage échoué : " + e.getMessage(), e);
        }
    }

    private String nvl(Object o)    { return o == null ? "" : o.toString(); }
    private String esc(String s)    {
        if (s == null) return "";
        return (s.contains(";") || s.contains("\"") || s.contains("\n"))
                ? "\"" + s.replace("\"","\"\"") + "\"" : s;
    }
}