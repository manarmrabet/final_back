package com.example.CWMS.service;

import com.example.CWMS.dto.TransferRequestDTO;
import com.example.CWMS.exception.InsufficientStockException;
import com.example.CWMS.exception.InvalidMovementException;
import com.example.CWMS.exception.LocationNotFoundException;
import com.example.CWMS.model.erp.ErpStock;
import com.example.CWMS.repository.erp.ErpStockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Service dédié à la mise à jour du stock ERP (dbo_twhinr1401200)
 * lors d'un transfert CWMS validé (flux TransferServiceImpl).
 *
 * ══════════════════════════════════════════════════════════════════
 * DIFFÉRENCE AVEC StockMovementServiceImpl :
 *
 * ErpStockUpdater → appelé par TransferServiceImpl (flux transfert
 *   classique CWMS : scan carton → lot entier déplacé d'un coup).
 *   Reçoit un TransferRequestDTO, opère sur lot entier.
 *
 * StockMovementServiceImpl → appelé par StockMovementController
 *   (flux WIP / entre emplacements) avec BigDecimal granulaire.
 *
 * RÈGLE INFOR LN OBLIGATOIRE (les 2 services la respectent) :
 *   - Utiliser uniquement t_qhnd (jamais t_qblk)
 *   - Source : soustraire → si = 0, DELETE ligne
 *   - Destination : UPDATE si existe, INSERT sinon
 *   - Ne jamais modifier dbo_twhinr150310
 *   - Transfert intra-magasin uniquement
 * ══════════════════════════════════════════════════════════════════
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ErpStockUpdater {

    private final ErpStockRepository erpStockRepo;

    // ══════════════════════════════════════════════════════════════════════════
    // POINT D'ENTRÉE PRINCIPAL
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Déplace le stock ERP de sourceLocation vers destLocation pour un lot donné.
     *
     * Retourne true  si le mouvement a réussi.
     * Retourne false si le lot source est introuvable (log warning, non bloquant).
     *
     * @throws InvalidMovementException si transfert inter-magasin détecté
     * @throws InsufficientStockException si t_qhnd < quantité demandée
     */
    @Transactional(transactionManager = "erpTransactionManager", rollbackFor = Exception.class)
    public boolean moveLot(TransferRequestDTO request) {

        final String itemCode        = trim(request.getErpItemCode());
        final String lotNumber       = trim(request.getLotNumber());
        final String sourceLocation  = trim(request.getSourceLocation());
        final String destLocation    = trim(request.getDestLocation());
        final String sourceWarehouse = trim(request.getSourceWarehouse());
        final BigDecimal quantity    = resolveQuantity(request);

        log.info("ErpStockUpdater.moveLot | item={} lot={} {}({}) → {} qty={}",
                itemCode, lotNumber, sourceLocation, sourceWarehouse, destLocation, quantity);

        // ── 1. Résoudre le magasin destination ────────────────────────────────
        final String destWarehouse = resolveDestWarehouse(
                request.getDestWarehouse(), destLocation, sourceWarehouse, sourceLocation);

        // ── 2. Vérifier intra-magasin (règle métier absolue) ──────────────────
        if (hasValue(sourceWarehouse) && hasValue(destWarehouse)
                && !sourceWarehouse.equalsIgnoreCase(destWarehouse)) {
            log.error("Transfert inter-magasin rejeté | src_wh={} dest_wh={} item={} lot={}",
                    sourceWarehouse, destWarehouse, itemCode, lotNumber);
            throw new InvalidMovementException(String.format(
                    "Transfert inter-magasin interdit : magasin source '%s' ≠ destination '%s'. " +
                            "Article=%s, lot=%s, source=%s, destination=%s",
                    sourceWarehouse, destWarehouse, itemCode, lotNumber,
                    sourceLocation, destLocation));
        }

        // ── 3. Charger la ligne source ─────────────────────────────────────────
        ErpStock source = erpStockRepo
                .findByItemCodeAndLocationAndLotNumber(itemCode, sourceLocation, lotNumber)
                .orElse(null);

        if (source == null) {
            log.warn("Ligne source introuvable | item={} lot={} loc={} " +
                            "(lot déjà déplacé ou données ERP désynchronisées)",
                    itemCode, lotNumber, sourceLocation);
            return false;
        }

        // ── 4. Vérifier la quantité disponible (t_qhnd uniquement) ────────────
        BigDecimal available = source.getQuantityOnHand();
        if (available.compareTo(quantity) < 0) {
            throw new InsufficientStockException(
                    itemCode, sourceLocation, lotNumber, available, quantity);
        }

        // ── 5. Déduire de la source ────────────────────────────────────────────
        source.subtractQuantity(quantity);
        if (source.isEmpty()) {
            log.info("Stock source épuisé — DELETE | item={} lot={} loc={}", itemCode, lotNumber, sourceLocation);
            erpStockRepo.delete(source);
        } else {
            erpStockRepo.save(source);
        }

        // ── 6. Ajouter à la destination ────────────────────────────────────────
        String finalDestWarehouse = hasValue(destWarehouse) ? destWarehouse : sourceWarehouse;
        ErpStock destination = erpStockRepo
                .findByItemCodeAndLocationAndLotNumber(itemCode, destLocation, lotNumber)
                .orElseGet(() -> {
                    log.info("Ligne destination absente — INSERT | item={} lot={} loc={}",
                            itemCode, lotNumber, destLocation);
                    return ErpStock.builder()
                            .warehouseCode(finalDestWarehouse)
                            .itemCode(itemCode)
                            .location(destLocation)
                            .lotNumber(lotNumber)
                            .quantityAvailableRaw("0.00000")
                            .lineStatus(source.getLineStatus())
                            .build();
                });

        destination.addQuantity(quantity);
        erpStockRepo.save(destination);

        log.info("ErpStockUpdater.moveLot OK | item={} lot={} {} → {} qty={}",
                itemCode, lotNumber, sourceLocation, destLocation, quantity);
        return true;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // RÉSOLUTION DU MAGASIN DESTINATION
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Ordre de priorité pour résoudre destWarehouse :
     * 1. DTO Flutter (valide si ≠ code emplacement)
     * 2. Stock ERP existant à la destination
     * 3. Magasin source (intra-magasin = même magasin)
     */
    private String resolveDestWarehouse(String dtoDestWarehouse, String destLocation,
                                        String sourceWarehouse, String sourceLocation) {
        // 1. DTO Flutter
        if (hasValue(dtoDestWarehouse) && !dtoDestWarehouse.equalsIgnoreCase(destLocation)) {
            log.debug("destWarehouse depuis DTO : {}", dtoDestWarehouse);
            return dtoDestWarehouse;
        }

        // 2. Stock ERP existant à la destination
        List<ErpStock> destStocks = erpStockRepo.findByLocation(destLocation);
        if (!destStocks.isEmpty()) {
            String fromStock = trim(destStocks.get(0).getWarehouseCode());
            if (hasValue(fromStock)) {
                log.debug("destWarehouse depuis stock ERP existant ({}) : {}", destLocation, fromStock);
                return fromStock;
            }
        }

        // 3. Fallback : magasin source
        String fallback = hasValue(sourceWarehouse) ? sourceWarehouse : sourceLocation;
        log.debug("destWarehouse — fallback magasin source : {}", fallback);
        return fallback;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Résout la quantité depuis le DTO.
     * TransferRequestDTO.quantity est Integer → converti en BigDecimal.
     */
    private BigDecimal resolveQuantity(TransferRequestDTO request) {
        if (request.getQuantity() == null || request.getQuantity() <= 0) {
            throw new InvalidMovementException("La quantité du transfert doit être > 0");
        }
        return BigDecimal.valueOf(request.getQuantity());
    }

    private String trim(String s) {
        return s != null ? s.trim() : null;
    }

    private boolean hasValue(String s) {
        return s != null && !s.isBlank();
    }
}