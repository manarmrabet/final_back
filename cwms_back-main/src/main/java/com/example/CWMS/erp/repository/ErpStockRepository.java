package com.example.CWMS.erp.repository;

import com.example.CWMS.erp.entity.ErpStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository ERP Stock — Lecture seule.
 *
 * IMPORTANT : Les colonnes t_item et t_loca dans l'ERP contiennent
 * souvent des espaces de remplissage (ex: 'FAI-001   ').
 * Toutes les requêtes utilisent TRIM() pour éviter les 0 résultats.
 * C'est la cause de l'erreur 403 : stock=0 → "Stock insuffisant".
 */
@Repository
public interface ErpStockRepository extends JpaRepository<ErpStock, Long> {

    /**
     * Stock d'un article dans un emplacement donné.
     * FIX : TRIM sur t_item ET t_loca — l'ERP stocke des valeurs avec espaces.
     */
    @Query("""
        SELECT s FROM ErpStock s
        WHERE TRIM(s.itemCode) = TRIM(:itemCode)
          AND TRIM(s.location) = TRIM(:location)
        """)
    List<ErpStock> findByItemCodeAndLocation(
            @Param("itemCode") String itemCode,
            @Param("location") String location);

    /**
     * Tout le stock d'un article (tous emplacements).
     * FIX : TRIM sur t_item.
     */
    @Query("""
        SELECT s FROM ErpStock s
        WHERE TRIM(s.itemCode) = TRIM(:itemCode)
        ORDER BY s.location
        """)
    List<ErpStock> findByItemCode(@Param("itemCode") String itemCode);

    /**
     * Tout le stock dans un emplacement.
     */
    @Query("""
        SELECT s FROM ErpStock s
        WHERE TRIM(s.location) = TRIM(:location)
        """)
    List<ErpStock> findByLocation(@Param("location") String location);

    /**
     * Stock d'un article par lot dans un emplacement.
     * FIX : TRIM sur les trois colonnes.
     */
    @Query("""
        SELECT s FROM ErpStock s
        WHERE TRIM(s.itemCode) = TRIM(:itemCode)
          AND TRIM(s.location) = TRIM(:location)
          AND TRIM(s.lotNumber) = TRIM(:lotNumber)
        """)
    Optional<ErpStock> findByItemCodeAndLocationAndLotNumber(
            @Param("itemCode")  String itemCode,
            @Param("location")  String location,
            @Param("lotNumber") String lotNumber);

    /**
     * Tous les lots d'un article dans un emplacement.
     */
    @Query("""
        SELECT s FROM ErpStock s
        WHERE TRIM(s.itemCode) = TRIM(:itemCode)
          AND TRIM(s.location) = TRIM(:location)
        ORDER BY s.entryDate ASC
        """)
    List<ErpStock> findByItemCodeAndLocationOrderByEntryDateAsc(
            @Param("itemCode") String itemCode,
            @Param("location") String location);

    /**
     * Articles présents dans un emplacement.
     */
    @Query("SELECT DISTINCT TRIM(s.itemCode) FROM ErpStock s WHERE TRIM(s.location) = TRIM(:location)")
    List<String> findDistinctItemCodesByLocation(@Param("location") String location);

    /**
     * Résumé stock par emplacement pour un article.
     * Retourne : [itemCode, location, sumQty]
     */
    @Query("""
        SELECT TRIM(s.itemCode), TRIM(s.location),
               SUM(CAST(COALESCE(NULLIF(TRIM(s.quantityAvailable),''), '0') AS double))
        FROM ErpStock s
        WHERE TRIM(s.itemCode) = TRIM(:itemCode)
        GROUP BY s.itemCode, s.location
        """)
    List<Object[]> findStockSummaryByItem(@Param("itemCode") String itemCode);
    @Query("""
        SELECT s FROM ErpStock s
        WHERE TRIM(s.lotNumber) = TRIM(:lotNumber)
        """)
    List<ErpStock> findByLotNumber(@Param("lotNumber") String lotNumber);

}