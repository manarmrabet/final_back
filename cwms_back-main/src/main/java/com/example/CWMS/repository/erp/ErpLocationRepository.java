package com.example.CWMS.repository.erp;

import com.example.CWMS.model.erp.ErpLocation;
import com.example.CWMS.model.erp.ErpLocationId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository READ ONLY pour dbo_twhwmd300310 (emplacements WH Infor LN).
 *
 * RÈGLES SQL SERVER :
 *  - LTRIM(RTRIM()) uniquement sur les colonnes VARCHAR (t_cwar, t_loca, t_dsca...)
 *  - Jamais de TRIM/LTRIM/RTRIM sur tinyint/int (t_strt, t_ball, t_oclo...)
 *    → SQL Server Error 8116 : Argument data type tinyint is invalid for TRIM
 *  - Toutes les requêtes sont nativeQuery=true pour contrôle total du SQL généré
 */
@Repository
public interface ErpLocationRepository extends JpaRepository<ErpLocation, ErpLocationId> {

    // ══════════════════════════════════════════════════════════════════════════
    // LOOKUP PRINCIPAL
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Charge un emplacement par (warehouse, location).
     * LTRIM/RTRIM uniquement sur t_cwar et t_loca (VARCHAR).
     */
    @Query(value = """
        SELECT *
        FROM   dbo_twhwmd300310
        WHERE  LTRIM(RTRIM(t_cwar)) = LTRIM(RTRIM(:warehouseCode))
          AND  LTRIM(RTRIM(t_loca)) = LTRIM(RTRIM(:locationCode))
        """, nativeQuery = true)
    Optional<ErpLocation> findByWarehouseCodeAndLocationCode(
            @Param("warehouseCode") String warehouseCode,
            @Param("locationCode")  String locationCode);

    // ══════════════════════════════════════════════════════════════════════════
    // LISTES PAR MAGASIN
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Tous les emplacements d'un magasin — pour affichage / sélecteur.
     */
    @Query(value = """
        SELECT *
        FROM   dbo_twhwmd300310
        WHERE  LTRIM(RTRIM(t_cwar)) = LTRIM(RTRIM(:warehouseCode))
        ORDER  BY t_loca
        """, nativeQuery = true)
    List<ErpLocation> findAllByWarehouseCode(@Param("warehouseCode") String warehouseCode);

    /**
     * Emplacements actifs uniquement (t_strt = 1 ET t_oclo = 0).
     * Comparaison directe sur tinyint — pas de TRIM.
     */
    @Query(value = """
        SELECT *
        FROM   dbo_twhwmd300310
        WHERE  LTRIM(RTRIM(t_cwar)) = LTRIM(RTRIM(:warehouseCode))
          AND  t_strt = 1
          AND  (t_oclo IS NULL OR t_oclo = 0)
        ORDER  BY t_loca
        """, nativeQuery = true)
    List<ErpLocation> findActiveByWarehouseCode(@Param("warehouseCode") String warehouseCode);

    // ══════════════════════════════════════════════════════════════════════════
    // EXISTENCE
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Vérifie si un emplacement existe dans un magasin.
     * Retourne 1 si trouvé, 0 sinon.
     */
    @Query(value = """
        SELECT CASE WHEN COUNT(1) > 0 THEN 1 ELSE 0 END
        FROM   dbo_twhwmd300310
        WHERE  LTRIM(RTRIM(t_cwar)) = LTRIM(RTRIM(:warehouseCode))
          AND  LTRIM(RTRIM(t_loca)) = LTRIM(RTRIM(:locationCode))
        """, nativeQuery = true)
    int countByWarehouseCodeAndLocationCode(
            @Param("warehouseCode") String warehouseCode,
            @Param("locationCode")  String locationCode);

    /**
     * Wrapper booléen appelé par LocationServiceImpl.
     * Utilise countByWarehouseCodeAndLocationCode en default method.
     */
    default boolean existsByWarehouseCodeAndLocationCode(
            String warehouseCode, String locationCode) {
        return countByWarehouseCodeAndLocationCode(warehouseCode, locationCode) > 0;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // RECHERCHE PAR EMPLACEMENT SEUL (sans warehouse — pour getLocationInfo)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Cherche un emplacement par son code seul (toutes magasins).
     * Utilisé dans TransferServiceImpl.getLocationInfo() quand le warehouse
     * n'est pas encore connu.
     */
    @Query(value = """
        SELECT TOP 1 *
        FROM   dbo_twhwmd300310
        WHERE  LTRIM(RTRIM(t_loca)) = LTRIM(RTRIM(:locationCode))
        ORDER  BY t_cwar
        """, nativeQuery = true)
    Optional<ErpLocation> findFirstByLocationCode(
            @Param("locationCode") String locationCode);

    // ══════════════════════════════════════════════════════════════════════════
    // ZONES DISTINCTES — pour le sélecteur de zone dans la création de session
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Retourne les zones distinctes (t_zone) pour un magasin donné.
     * Exclut les valeurs NULL et les chaînes vides/espaces.
     * LTRIM/RTRIM sur t_zone (VARCHAR 3) et t_cwar (VARCHAR 3).
     */
    @Query(value = """
        SELECT DISTINCT LTRIM(RTRIM(t_zone))
        FROM   dbo_twhwmd300310
        WHERE  LTRIM(RTRIM(t_cwar)) = LTRIM(RTRIM(:warehouseCode))
          AND  t_zone IS NOT NULL
          AND  LTRIM(RTRIM(t_zone)) <> ''
        ORDER  BY LTRIM(RTRIM(t_zone))
        """, nativeQuery = true)
    List<String> findDistinctZonesByWarehouseCode(@Param("warehouseCode") String warehouseCode);

}