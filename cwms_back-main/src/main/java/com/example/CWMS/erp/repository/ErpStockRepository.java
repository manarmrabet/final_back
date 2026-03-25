// ─────────────────────────────────────────────────────────────────────────────
// FILE 1 : ErpStockRepository.java
// ─────────────────────────────────────────────────────────────────────────────
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
 * Utilisé par TransferService pour valider le stock avant transfert.
 */
@Repository
public interface ErpStockRepository extends JpaRepository<ErpStock, Long> {

    /**
     * Stock d'un article dans un emplacement donné.
     * Utilisé pour valider qu'il y a assez de stock avant transfert.
     */
    List<ErpStock> findByItemCodeAndLocation(String itemCode, String location);

    /**
     * Tout le stock d'un article (tous emplacements).
     * Utilisé par le web pour afficher la carte de stock.
     */
    List<ErpStock> findByItemCode(String itemCode);

    /**
     * Tout le stock dans un emplacement (pour vue par zone).
     */
    List<ErpStock> findByLocation(String location);

    /**
     * Stock d'un article par lot dans un emplacement.
     * Pour les transferts avec gestion de lot.
     */
    Optional<ErpStock> findByItemCodeAndLocationAndLotNumber(
            String itemCode, String location, String lotNumber);

    /**
     * Tous les lots d'un article dans un emplacement.
     */
    List<ErpStock> findByItemCodeAndLocationOrderByEntryDateAsc(
            String itemCode, String location);

    /**
     * Articles présents dans un emplacement (pour affichage inventaire zone).
     */
    @Query("SELECT DISTINCT s.itemCode FROM ErpStock s WHERE s.location = :location")
    List<String> findDistinctItemCodesByLocation(@Param("location") String location);

    /**
     * Résumé stock par emplacement pour un article — somme des quantités.
     * Retourne : [itemCode, location, sumQty]
     */
    @Query("""
        SELECT s.itemCode, s.location, SUM(CAST(COALESCE(NULLIF(s.quantityAvailable,''),0) AS double))
        FROM ErpStock s
        WHERE s.itemCode = :itemCode
        GROUP BY s.itemCode, s.location
        """)
    List<Object[]> findStockSummaryByItem(@Param("itemCode") String itemCode);
}