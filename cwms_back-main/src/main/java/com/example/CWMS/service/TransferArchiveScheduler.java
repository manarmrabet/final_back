package com.example.CWMS.service;

import com.example.CWMS.model.cwms.StockTransfer;
import com.example.CWMS.repository.cwms.StockTransferRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationContext;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Archivage mensuel des transferts DONE / CANCELLED / ERROR.
 *
 * ┌─────────────────────────────────────────────────────────────────┐
 * │ ROOT CAUSE FINALE — pourquoi les CSV étaient vides             │
 * ├─────────────────────────────────────────────────────────────────┤
 * │ 1. nativeQuery + LocalDateTime → SQL Server JDBC (Hibernate 6) │
 * │    envoie le paramètre en VARCHAR au lieu de datetime2.        │
 * │    SQL Server accepte la requête mais retourne 0 ligne.        │
 * │    Aucune exception levée → CSV vide, rien supprimé.           │
 * │                                                                 │
 * │ 2. FileOutputStream crée le fichier AVANT l'écriture →        │
 * │    Files.exists() = true même si rien n'a été écrit →         │
 * │    la vérification fileSize == 0 ne déclenchait jamais         │
 * │    le throw car le fichier avait le BOM (3 octets).            │
 * │                                                                 │
 * │ SOLUTION                                                        │
 * │   • Tout en JPQL pur : Hibernate mappe LocalDateTime →        │
 * │     datetime2 via son type system interne, fiable.             │
 * │   • Pageable SANS Sort (PageRequest.of(p, size)) :             │
 * │     le ORDER BY est dans la JPQL → pas de doublon              │
 * │     "created_at.createdAt" généré par Hibernate.               │
 * │   • Files.newBufferedWriter + flush explicite.                  │
 * │   • Vérification CSV > 100 octets (header seul = 180 octets). │
 * └─────────────────────────────────────────────────────────────────┘
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransferArchiveScheduler {

    private static final int    BATCH_LOAD   = 500;
    private static final int    BATCH_DELETE = 500;
    private static final String S_DONE       = "DONE";
    private static final String S_CANCELLED  = "CANCELLED";
    private static final String S_ERROR      = "ERROR";
    private final ApplicationContext applicationContext;
    private static final DateTimeFormatter FILE_TS  = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final DateTimeFormatter FILE_LBL = DateTimeFormatter.ofPattern("yyyy-MM");

    @Value("${cwms.archive.dir}")
    private String archiveDir;

    private final StockTransferRepository repo;

    // ─── Planification ────────────────────────────────────────────────────────

    /** Archivage automatique : 1er de chaque mois à 2h00. */
    @Scheduled(cron = "${cwms.archive.cron:0 0 2 1 * *}")
    public void archiveMonthlyTransfers() {
        LocalDateTime from = LocalDateTime.now()
                .minusMonths(1).withDayOfMonth(1).toLocalDate().atStartOfDay();
        LocalDateTime to   = from.plusMonths(1).minusNanos(1);
        doArchive(from, to, from.format(FILE_LBL));
    }

    /** Mode test : POST /api/transfers/archives/trigger?testMode=true */
    public void archiveForTesting() {
        LocalDateTime to   = LocalDateTime.now();
        LocalDateTime from = to.minusYears(10);
        doArchive(from, to, "TEST_" + LocalDateTime.now().format(FILE_TS));
    }

    // ─── Logique principale ───────────────────────────────────────────────────

    private void doArchive(LocalDateTime from, LocalDateTime to, String label) {
        String fileName = "transfers_" + label + "_" + LocalDateTime.now().format(FILE_TS) + ".csv";
        Path   csvPath  = Paths.get(archiveDir).resolve(fileName);

        log.info("[Archive] {} — démarré : {} → {}", label, from, to);

        try {
            Files.createDirectories(Paths.get(archiveDir));

            // ── 1. COUNT JPQL (fiable LocalDateTime sur SQL Server) ──────────
            long total = repo.countForArchiveJpql(from, to, S_DONE, S_CANCELLED, S_ERROR);
            log.info("[Archive] {} — {} transferts éligibles", label, total);

            if (total == 0) {
                log.info("[Archive] {} — rien à archiver pour cette période", label);
                return;
            }

            // ── 2. Chargement paginé JPQL ───────────────────────────────────
            List<StockTransfer> all = loadAllEligible(from, to, total, label);
            if (all.isEmpty()) {
                log.warn("[Archive] {} — list vide malgré count={} → vérifier la DB", label, total);
                return;
            }

            // ── 3. Écriture CSV ──────────────────────────────────────────────
            writeCsv(csvPath, all);

            // ── 4. Vérification stricte ──────────────────────────────────────
            // Header seul = ~180 octets, donc si < 100 → écriture a échoué
            long size = Files.size(csvPath);
            if (size < 100) {
                Files.deleteIfExists(csvPath);
                throw new RuntimeException("CSV invalide (" + size + " octets) — suppression annulée");
            }

            // ── 5. Suppression par batches ───────────────────────────────────
            List<Long> ids = all.stream()
                    .map(StockTransfer::getId)
                    .collect(Collectors.toList());
            applicationContext.getBean(TransferArchiveScheduler.class).deleteInBatches(ids, label);

            log.info("[Archive] {} — SUCCÈS : {} transferts → {} ({} Ko)",
                    label, all.size(), csvPath, size / 1024);

        } catch (Exception e) {
            log.error("[Archive] {} ÉCHEC : {}", label, e.getMessage(), e);
            throw new RuntimeException("Archivage échoué : " + e.getMessage(), e);
        }
    }

    // ─── Chargement paginé JPQL ───────────────────────────────────────────────

    private List<StockTransfer> loadAllEligible(
            LocalDateTime from, LocalDateTime to, long expected, String label) {

        List<StockTransfer> result = new ArrayList<>((int) Math.min(expected, 50_000));
        int pageNum = 0;
        List<StockTransfer> page;

        do {
            // PageRequest.of(page, size) — SANS Sort → Hibernate ne génère
            // pas de ORDER BY supplémentaire qui casserait la requête
            PageRequest pr = PageRequest.of(pageNum, BATCH_LOAD);
            page = repo.findForArchiveJpql(from, to, S_DONE, S_CANCELLED, S_ERROR, pr);
            result.addAll(page);
            log.debug("[Archive] {} — page={} +{} lignes (total={}/{})",
                    label, pageNum, page.size(), result.size(), expected);
            pageNum++;
        } while (page.size() == BATCH_LOAD);

        log.info("[Archive] {} — chargement terminé : {}/{}", label, result.size(), expected);
        return result;
    }

    // ─── Suppression transactionnelle ─────────────────────────────────────────

    @Transactional
    public void deleteInBatches(List<Long> ids, String label) {
        int deleted = 0;
        for (int i = 0; i < ids.size(); i += BATCH_DELETE) {
            List<Long> batch = ids.subList(i, Math.min(i + BATCH_DELETE, ids.size()));
            repo.deleteByIdIn(batch);
            deleted += batch.size();
            log.debug("[Archive] {} — supprimé {}/{}", label, deleted, ids.size());
        }
        log.info("[Archive] {} — {} lignes supprimées de la base", label, deleted);
    }

    // ─── Écriture CSV ─────────────────────────────────────────────────────────

    private void writeCsv(Path path, List<StockTransfer> transfers) throws IOException {
        // Files.newBufferedWriter crée + écrit atomiquement, flush garanti à la fermeture
        try (BufferedWriter w = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {

            w.write('\uFEFF'); // BOM pour Excel
            w.write("ID;Date création;Code article;Nom article;Lot;" +
                    "Source;Destination;Entrepôt src;Entrepôt dst;" +
                    "Quantité;Unité;Statut;Opérateur;Notes;Date complétion");
            w.newLine();

            for (StockTransfer t : transfers) {
                w.write(
                        t.getId() + ";" +
                                nvl(t.getCreatedAt()) + ";" +
                                nvl(t.getErpItemCode()) + ";" +
                                esc(t.getErpItemLabel()) + ";" +
                                nvl(t.getLotNumber()) + ";" +
                                nvl(t.getSourceLocation()) + ";" +
                                nvl(t.getDestLocation()) + ";" +
                                nvl(t.getSourceWarehouse()) + ";" +
                                nvl(t.getDestWarehouse()) + ";" +
                                (t.getQuantity() != null ? t.getQuantity() : 0) + ";" +
                                nvl(t.getUnit()) + ";" +
                                (t.getStatus() != null ? t.getStatus().name() : "") + ";" +
                                (t.getOperator() != null
                                        ? esc(t.getOperator().getFirstName() + " " + t.getOperator().getLastName())
                                        : "") + ";" +
                                esc(t.getNotes()) + ";" +
                                nvl(t.getCompletedAt())
                );
                w.newLine();
            }
            w.flush(); // flush explicite avant fermeture du try
        }
    }

    private String nvl(Object o) { return o == null ? "" : o.toString(); }

    private String esc(String s) {
        if (s == null || s.isBlank()) return "";
        return (s.contains(";") || s.contains("\"") || s.contains("\n"))
                ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }
}