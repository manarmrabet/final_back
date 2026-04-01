package com.example.CWMS.transfer.service;

import com.example.CWMS.transfer.model.StockTransfer;
import com.example.CWMS.transfer.model.StockTransferArchive;
import com.example.CWMS.transfer.repository.StockTransferArchiveRepository;
import com.example.CWMS.transfer.repository.StockTransferRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransferArchiveScheduler {

    private static final int RETENTION_DAYS = 0;   // 0 = tout archiver (idéal pour test)
    private static final int BATCH_SIZE = 500;

    private static final List<String> ARCHIVABLE_STATUSES = List.of("DONE", "CANCELLED", "ERROR");

    private final StockTransferRepository transferRepo;
    private final StockTransferArchiveRepository archiveRepo;
    private final TransactionTemplate txTemplate;

    /**
     * Exécute l'archivage AUTOMATIQUEMENT dès le démarrage de l'application
     */
    @EventListener(ApplicationReadyEvent.class)
    public void runArchiveOnStartup() {
        log.info("=== ARCHIVAGE FORCÉ AU DÉMARRAGE ===");
        log.info("Rétention configurée : {} jours (0 = tout archiver)", RETENTION_DAYS);
        archiveOldTransfers();
    }

    /**
     * Scheduler normal (la nuit)
     */
    @Scheduled(cron = "${cwms.archive.cron:0 0 2 * * *}")
    public void archiveOldTransfers() {

        LocalDateTime cutoff = LocalDateTime.now().minusDays(RETENTION_DAYS);
        long totalArchived = 0;
        int batchNumber = 0;

        log.info("▶ Archivage démarré — cutoff={} (rétention {} jours)", cutoff, RETENTION_DAYS);

        List<StockTransfer> batch;

        do {
            batch = transferRepo.findOldTransfersToArchive(
                    cutoff, ARCHIVABLE_STATUSES, PageRequest.of(0, BATCH_SIZE));

            if (batch.isEmpty()) {
                log.info("Aucun transfert éligible trouvé.");
                break;
            }

            final List<StockTransfer> currentBatch = batch;
            final List<Long> ids = currentBatch.stream()
                    .map(StockTransfer::getId)
                    .collect(Collectors.toList());

            txTemplate.execute(status -> {
                List<StockTransferArchive> archives = currentBatch.stream()
                        .map(this::toArchive)
                        .collect(Collectors.toList());

                archiveRepo.saveAll(archives);
                transferRepo.deleteAllByIdInBatch(ids);
                return null;
            });

            batchNumber++;
            totalArchived += currentBatch.size();

            log.info("  Lot #{} : {} transferts archivés (total={})",
                    batchNumber, currentBatch.size(), totalArchived);

        } while (batch.size() == BATCH_SIZE);

        if (totalArchived == 0) {
            log.info("▶ Archivage terminé — aucun transfert éligible");
        } else {
            log.info("▶ Archivage terminé — {} transferts déplacés en {} lot(s)",
                    totalArchived, batchNumber);
        }
    }

    private StockTransferArchive toArchive(StockTransfer t) {
        return StockTransferArchive.builder()
                .id(t.getId())
                .erpItemCode(t.getErpItemCode())
                .erpItemLabel(t.getErpItemLabel())
                .lotNumber(t.getLotNumber())
                .sourceLocation(t.getSourceLocation())
                .destLocation(t.getDestLocation())
                .sourceWarehouse(t.getSourceWarehouse())
                .destWarehouse(t.getDestWarehouse())
                .quantity(t.getQuantity())
                .unit(t.getUnit())
                .status(t.getStatus())
                .transferType(t.getTransferType())
                .notes(t.getNotes())
                .errorMessage(t.getErrorMessage())
                .operator(t.getOperator())
                .validatedBy(t.getValidatedBy())
                .createdAt(t.getCreatedAt())
                .completedAt(t.getCompletedAt())
                .validatedAt(t.getValidatedAt())
                .archivedAt(LocalDateTime.now())
                .build();
    }
}