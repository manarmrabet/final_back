package com.example.CWMS.service;

import com.example.CWMS.exception.InvalidMovementException;
import com.example.CWMS.exception.LocationNotFoundException;
import com.example.CWMS.iservice.ILocationService;
import com.example.CWMS.model.erp.ErpLocation;
import com.example.CWMS.repository.erp.ErpLocationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
@Slf4j
public class LocationServiceImpl implements ILocationService {

    private final ErpLocationRepository locationRepository;

    @Value("${cwms.wip.location-code:WIP}")
    private String wipLocationCode;

    // ══════════════════════════════════════════════════════════════════════════
    // IMPLÉMENTATION
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    @Transactional(transactionManager = "erpTransactionManager", readOnly = true)
    public ErpLocation requireLocation(String warehouseCode, String locationCode) {
        return locationRepository
                .findByWarehouseCodeAndLocationCode(warehouseCode, locationCode)
                .orElseThrow(() -> new LocationNotFoundException(warehouseCode, locationCode));
    }

    @Override
    @Transactional(transactionManager = "erpTransactionManager", readOnly = true)
    public void validateSourceLocation(String warehouseCode, String locationCode) {
        ErpLocation location = requireLocation(warehouseCode, locationCode);

        if (!location.isActive()) {
            log.warn("Emplacement source inactif : warehouse={} location={} status={} closed={}",
                    warehouseCode, locationCode, location.getStatus(), location.getClosed());
            throw new InvalidMovementException(String.format(
                    "L'emplacement source '%s' (magasin '%s') est inactif ou fermé",
                    locationCode, warehouseCode));
        }

        if (!location.acceptsOutbound()) {
            log.warn("Emplacement source bloque les sorties : warehouse={} location={}",
                    warehouseCode, locationCode);
            throw new InvalidMovementException(String.format(
                    "L'emplacement source '%s' n'autorise pas les sorties de stock",
                    locationCode));
        }

        log.debug("Emplacement source validé : warehouse={} location={}", warehouseCode, locationCode);
    }

    @Override
    @Transactional(transactionManager = "erpTransactionManager", readOnly = true)
    public boolean validateDestinationLocation(String warehouseCode, String locationCode, boolean isWip) {
        // WIP traité comme emplacement spécial — peut ne pas exister dans twhwmd300
        if (isWip) {
            log.debug("Destination WIP '{}' — validation DB ignorée (règle Infor LN)", locationCode);
            return false;
        }

        return locationRepository
                .findByWarehouseCodeAndLocationCode(warehouseCode, locationCode)
                .map(loc -> {
                    if (!loc.isActive()) {
                        throw new InvalidMovementException(String.format(
                                "L'emplacement destination '%s' (magasin '%s') est inactif ou fermé",
                                locationCode, warehouseCode));
                    }
                    if (!loc.acceptsInbound()) {
                        throw new InvalidMovementException(String.format(
                                "L'emplacement destination '%s' n'autorise pas les entrées de stock",
                                locationCode));
                    }
                    log.debug("Emplacement destination validé : warehouse={} location={}",
                            warehouseCode, locationCode);
                    return true;
                })
                .orElseGet(() -> {
                    log.warn("Emplacement destination '{}' absent de twhwmd300 (magasin={}) — " +
                            "mouvement autorisé, note ajoutée", locationCode, warehouseCode);
                    return false;
                });
    }

    @Override
    @Transactional(transactionManager = "erpTransactionManager", readOnly = true)
    public boolean locationExists(String warehouseCode, String locationCode) {
        return locationRepository.existsByWarehouseCodeAndLocationCode(warehouseCode, locationCode);
    }
}