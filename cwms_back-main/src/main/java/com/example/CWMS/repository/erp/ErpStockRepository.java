package com.example.CWMS.repository.erp;

import com.example.CWMS.model.erp.ErpStock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Repository pour dbo_twhinr1401200 (stock détaillé niveau 140).
 *
 * RÈGLE : toutes les lectures/écritures de stock passent ici.
 * La méthode moveLot() native SQL est SUPPRIMÉE — le service gère
 * les mouvements via addQuantity/subtractQuantity + save/delete
 * pour respecter la logique Infor LN.
 */
@Repository
public interface ErpStockRepository extends JpaRepository<ErpStock, Long> {

    // ══════════════════════════════════════════════════════════════════════════
    // LECTURES DE BASE
    // ══════════════════════════════════════════════════════════════════════════

    @Query("SELECT s FROM ErpStock s ORDER BY s.idStockage DESC")
    Page<ErpStock> findAll(Pageable pageable);

    @Query("SELECT s FROM ErpStock s WHERE TRIM(s.lotNumber) = TRIM(:lotNumber)")
    List<ErpStock> findByLotNumber(@Param("lotNumber") String lotNumber);

    @Query("""
        SELECT s FROM ErpStock s
        WHERE TRIM(s.itemCode) = TRIM(:itemCode)
        ORDER BY s.location
        """)
    List<ErpStock> findByItemCode(@Param("itemCode") String itemCode);

    @Query("SELECT s FROM ErpStock s WHERE TRIM(s.location) = TRIM(:location)")
    List<ErpStock> findByLocation(@Param("location") String location);

    @Query("SELECT s FROM ErpStock s WHERE TRIM(s.warehouseCode) = TRIM(:warehouseCode)")
    List<ErpStock> findByWarehouseCode(@Param("warehouseCode") String warehouseCode);

    @Query("SELECT s FROM ErpStock s WHERE TRIM(s.location) IN :locations")
    List<ErpStock> findByLocationIn(@Param("locations") List<String> locations);

    @Query("SELECT s FROM ErpStock s WHERE TRIM(s.warehouseCode) IN :warehouseCodes")
    List<ErpStock> findByWarehouseCodeIn(@Param("warehouseCodes") List<String> warehouseCodes);

    // ══════════════════════════════════════════════════════════════════════════
    // LOOKUP PRINCIPAL MOUVEMENT : item + warehouse + location + lot
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Clé naturelle complète (4 champs) — utilisée pour charger la ligne source/destination.
     * TRIM sur tous les champs car l'ERP stocke avec espaces trailing.
     */
    @Query("""
        SELECT s FROM ErpStock s
        WHERE TRIM(s.warehouseCode) = TRIM(:warehouseCode)
          AND TRIM(s.itemCode)      = TRIM(:itemCode)
          AND TRIM(s.location)      = TRIM(:location)
          AND TRIM(s.lotNumber)     = TRIM(:lotNumber)
        """)
    Optional<ErpStock> findByWarehouseCodeAndItemCodeAndLocationAndLotNumber(
            @Param("warehouseCode") String warehouseCode,
            @Param("itemCode")      String itemCode,
            @Param("location")      String location,
            @Param("lotNumber")     String lotNumber);

    /**
     * Toutes les lignes pour un article à une location (sans filtre lot).
     * Utilisé pour vérification de stock résiduel.
     */
    @Query("""
        SELECT s FROM ErpStock s
        WHERE TRIM(s.warehouseCode) = TRIM(:warehouseCode)
          AND TRIM(s.itemCode)      = TRIM(:itemCode)
          AND TRIM(s.location)      = TRIM(:location)
        """)
    List<ErpStock> findByWarehouseCodeAndItemCodeAndLocation(
            @Param("warehouseCode") String warehouseCode,
            @Param("itemCode")      String itemCode,
            @Param("location")      String location);

    // ══════════════════════════════════════════════════════════════════════════
    // COMPATIBILITÉ — anciens appels (TransferServiceImpl / ErpStockUpdater)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Utilisé par TransferServiceImpl pour valider le lot à la source.
     * item + location + lot (sans warehouseCode — comportement existant).
     */
    @Query("""
        SELECT s FROM ErpStock s
        WHERE TRIM(s.itemCode)  = TRIM(:itemCode)
          AND TRIM(s.location)  = TRIM(:location)
          AND TRIM(s.lotNumber) = TRIM(:lotNumber)
        """)
    Optional<ErpStock> findByItemCodeAndLocationAndLotNumber(
            @Param("itemCode")  String itemCode,
            @Param("location")  String location,
            @Param("lotNumber") String lotNumber);

    /**
     * item + location (sans lot ni warehouse) — utilisé par validateTransferRequest.
     */
    @Query("""
        SELECT s FROM ErpStock s
        WHERE TRIM(s.itemCode) = TRIM(:itemCode)
          AND TRIM(s.location) = TRIM(:location)
        """)
    List<ErpStock> findByItemCodeAndLocation(
            @Param("itemCode") String itemCode,
            @Param("location") String location);

    @Query("""
        SELECT s FROM ErpStock s
        WHERE TRIM(s.itemCode) = TRIM(:itemCode)
          AND TRIM(s.location) = TRIM(:location)
        ORDER BY s.inventoryDateRaw ASC
        """)
    List<ErpStock> findByItemCodeAndLocationOrderByEntryDateAsc(
            @Param("itemCode") String itemCode,
            @Param("location") String location);

    // ══════════════════════════════════════════════════════════════════════════
    // ARTICLES (batch labels)
    // ══════════════════════════════════════════════════════════════════════════

    @Query("""
        SELECT DISTINCT TRIM(s.location)
        FROM ErpStock s
        WHERE TRIM(s.warehouseCode) = TRIM(:warehouseCode)
          AND s.location IS NOT NULL
        """)
    List<String> findDistinctLocationsByWarehouse(@Param("warehouseCode") String warehouseCode);

    @Query("SELECT DISTINCT TRIM(s.warehouseCode) FROM ErpStock s WHERE s.warehouseCode IS NOT NULL")
    List<String> findDistinctWarehouses();
}