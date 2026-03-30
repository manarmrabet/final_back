package com.example.CWMS.erp.repository;

import com.example.CWMS.erp.entity.ErpStock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository ERP Stock — Lecture seule.
 * Fusionné pour supporter le TransferService (Mobile) et la Gestion Stock (Web).
 */
@Repository
public interface ErpStockRepository extends JpaRepository<ErpStock, Long> {

    // ─── PAGINATION (Utile pour l'affichage Web) ──────────────────────────────

    @Query("SELECT s FROM ErpStock s ORDER BY s.idStockage DESC")
    Page<ErpStock> findAll(Pageable pageable);

    // ─── RECHERCHES PAR LOT ───────────────────────────────────────────────────

    @Query("SELECT s FROM ErpStock s WHERE TRIM(s.lotNumber) = TRIM(:lotNumber)")
    List<ErpStock> findByLotNumber(@Param("lotNumber") String lotNumber);

    // ─── RECHERCHES PAR ARTICLE ───────────────────────────────────────────────

    /**
     * Tout le stock d'un article (tous emplacements).
     */
    @Query("""
        SELECT s FROM ErpStock s
        WHERE TRIM(s.itemCode) = TRIM(:itemCode)
        ORDER BY s.location
        """)
    List<ErpStock> findByItemCode(@Param("itemCode") String itemCode);

    // ─── RECHERCHES PAR EMPLACEMENT ───────────────────────────────────────────

    @Query("SELECT s FROM ErpStock s WHERE TRIM(s.location) = TRIM(:location)")
    List<ErpStock> findByLocation(@Param("location") String location);

    @Query("SELECT DISTINCT TRIM(s.itemCode) FROM ErpStock s WHERE TRIM(s.location) = TRIM(:location)")
    List<String> findDistinctItemCodesByLocation(@Param("location") String location);

    // ─── RECHERCHES COMBINÉES (INDISPENSABLE POUR TRANSFERTS) ─────────────────

    /**
     * Recherche combinée Article + Emplacement + Lot.
     * Utilisé par TransferService pour identifier un carton précis.
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
     * Stock d'un article dans un emplacement (tous lots confondus).
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
     * Liste des lots d'un article triés par date d'entrée (Stratégie FIFO).
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

    // ─── STATISTIQUES ET RÉSUMÉS ──────────────────────────────────────────────

    /**
     * Résumé stock par emplacement pour un article spécifique.
     * Retourne : [itemCode, location, sumQty]
     */
    @Query("SELECT TRIM(s.itemCode), TRIM(s.location), " +
            "SUM(CAST(COALESCE(NULLIF(TRIM(s.quantityAvailableRaw),''), '0') AS double)) " +
            "FROM ErpStock s " +
            "WHERE TRIM(s.itemCode) = TRIM(:itemCode) " +
            "GROUP BY s.itemCode, s.location")
    List<Object[]> findStockSummaryByItem(@Param("itemCode") String itemCode);
}