package com.example.CWMS.service;

import com.example.CWMS.model.erp.ErpStock;
import com.example.CWMS.repository.erp.ErpStockRepository;
import com.example.CWMS.dto.TransferRequestDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service dédié à la mise à jour du stock ERP après un transfert CWMS.
 *
 * Résolution du magasin destination (priorité décroissante) :
 *   1. destWarehouse envoyé par Flutter dans le DTO  (source : getLocationInfo)
 *   2. Stock existant à l'emplacement destination    (t_cwar depuis ErpStock)
 *   3. Magasin source                                (transfert intra-magasin)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ErpStockUpdater {

    private final ErpStockRepository erpStockRepo;

    @Transactional(transactionManager = "erpTransactionManager")
    public boolean moveLot(TransferRequestDTO request) {

        String itemCode       = trim(request.getErpItemCode());
        String lotNumber      = trim(request.getLotNumber());
        String sourceLocation = trim(request.getSourceLocation());
        String destLocation   = trim(request.getDestLocation());

        String destWarehouse = resolveDestWarehouse(
                request.getDestWarehouse(),
                destLocation,
                request.getSourceWarehouse(),
                sourceLocation
        );

        log.info("ERP moveLot → article={} lot={} | {} ({}) → {} ({})",
                itemCode, lotNumber,
                sourceLocation, request.getSourceWarehouse(),
                destLocation, destWarehouse);

        int updated = erpStockRepo.moveLot(
                itemCode, lotNumber, sourceLocation, destLocation, destWarehouse);

        if (updated == 0) {
            log.warn("⚠️  ERP moveLot : aucune ligne trouvée " +
                            "→ article={} lot={} source={} " +
                            "(lot déjà déplacé ou données ERP désynchronisées)",
                    itemCode, lotNumber, sourceLocation);
            return false;
        }

        if (updated > 1) {
            log.warn("⚠️  ERP moveLot : {} lignes mises à jour pour lot={} " +
                    "(doublon ERP détecté — à vérifier)", updated, lotNumber);
        } else {
            log.info("✅ ERP moveLot : lot={} déplacé → {} (magasin={})",
                    lotNumber, destLocation, destWarehouse);
        }

        return true;
    }

    /**
     * Résout le magasin destination.
     *
     * ErpEmplacement (dbo_ttccom100120) ne contient pas t_cwar —
     * c'est une table de partenaires/adresses, pas d'emplacements de stock.
     * La seule source fiable pour t_cwar est dbo_twhinr1401200 (ErpStock).
     *
     * Ordre de priorité :
     *  1. Flutter DTO — fiable si getLocationInfo a retourné une vraie valeur
     *     (invalide si Flutter a renvoyé le code emplacement comme fallback)
     *  2. Stock existant à la destination — lit t_cwar depuis ErpStock
     *  3. Magasin source — transfert intra-magasin
     */
    private String resolveDestWarehouse(
            String dtoDestWarehouse,
            String destLocation,
            String sourceWarehouse,
            String sourceLocation) {

        // 1. DTO Flutter — valide uniquement si différent de l'emplacement lui-même
        //    (quand getLocationInfo échoue, Flutter renvoie le code emplacement
        //     comme warehouseCode, ce qui est incorrecte)
        if (hasValue(dtoDestWarehouse)
                && !dtoDestWarehouse.equalsIgnoreCase(destLocation)) {
            log.debug("destWarehouse résolu depuis DTO Flutter : {}", dtoDestWarehouse);
            return dtoDestWarehouse;
        }

        // 2. Stock ERP existant à l'emplacement destination
        //    Si des articles sont déjà là, leur t_cwar est le bon magasin
        List<ErpStock> destStocks = erpStockRepo.findByLocation(destLocation);
        if (!destStocks.isEmpty()) {
            String fromStock = trim(destStocks.get(0).getWarehouseCode());
            if (hasValue(fromStock)) {
                log.debug("destWarehouse résolu depuis stock ERP existant à {} : {}",
                        destLocation, fromStock);
                return fromStock;
            }
        }

        // 3. Fallback : même magasin que la source
        String fallback = hasValue(sourceWarehouse) ? sourceWarehouse : sourceLocation;
        log.warn("destWarehouse introuvable pour emplacement={} → fallback magasin source : {}",
                destLocation, fallback);
        return fallback;
    }

    private String trim(String s) {
        return s != null ? s.trim() : null;
    }

    private boolean hasValue(String s) {
        return s != null && !s.isBlank();
    }
}