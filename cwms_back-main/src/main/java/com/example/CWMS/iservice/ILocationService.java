package com.example.CWMS.iservice;

import com.example.CWMS.model.erp.ErpLocation;

/**
 * Service de validation des emplacements WH contre dbo_twhwmd300310.
 *
 * RÈGLE INFOR LN :
 *  - Tout emplacement source DOIT exister et être actif dans twhwmd300310.
 *  - Tout emplacement destination DOIT appartenir au même magasin.
 *  - L'emplacement WIP est traité comme cas spécial : autorisé même absent.
 */
public interface ILocationService {

    /**
     * Charge l'emplacement ou lève LocationNotFoundException.
     * Utilisé pour valider source et destination.
     */
    ErpLocation requireLocation(String warehouseCode, String locationCode);

    /**
     * Valide l'emplacement source : doit exister ET être actif.
     *
     * @throws LocationNotFoundException si introuvable
     * @throws InvalidMovementException  si inactif / fermé
     */
    void validateSourceLocation(String warehouseCode, String locationCode);

    /**
     * Valide l'emplacement destination : doit appartenir au même magasin.
     * Si locationCode est le WIP, la validation DB est ignorée.
     *
     * @return true  si l'emplacement existe en base
     *         false si WIP ou absent (mouvement autorisé avec note)
     */
    boolean validateDestinationLocation(String warehouseCode, String locationCode, boolean isWip);

    /**
     * Vérifie existence sans lever d'exception.
     */
    boolean locationExists(String warehouseCode, String locationCode);
}