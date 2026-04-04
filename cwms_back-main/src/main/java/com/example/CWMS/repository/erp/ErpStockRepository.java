package com.example.CWMS.repository.erp;

import com.example.CWMS.model.erp.ErpStock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ErpStockRepository extends JpaRepository<ErpStock, Long> {

    // ─── EXISTANT (ne pas toucher) ────────────────────────────────────────────

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
        ORDER BY s.entryDate ASC
        """)
    List<ErpStock> findByItemCodeAndLocationOrderByEntryDateAsc(
            @Param("itemCode") String itemCode,
            @Param("location") String location);

    @Modifying
    @Query(value = """
        UPDATE dbo_twhinr1401200
        SET    t_loca = :destLocation,
               t_cwar = :destWarehouse,
               t_trdt = GETDATE()
        WHERE  LTRIM(RTRIM(t_item)) = LTRIM(RTRIM(:itemCode))
          AND  LTRIM(RTRIM(t_clot)) = LTRIM(RTRIM(:lotNumber))
          AND  LTRIM(RTRIM(t_loca)) = LTRIM(RTRIM(:sourceLocation))
        """, nativeQuery = true)
    int moveLot(
            @Param("itemCode")       String itemCode,
            @Param("lotNumber")      String lotNumber,
            @Param("sourceLocation") String sourceLocation,
            @Param("destLocation")   String destLocation,
            @Param("destWarehouse")  String destWarehouse
    );

    // ─── AJOUT MODULE INVENTAIRE ──────────────────────────────────────────────

    /**
     * Récupère tout le stock d'un magasin (t_cwar).
     * Utilisé par le moteur de comparaison inventaire.
     */
    @Query("SELECT s FROM ErpStock s WHERE TRIM(s.warehouseCode) = TRIM(:warehouseCode)")
    List<ErpStock> findByWarehouseCode(@Param("warehouseCode") String warehouseCode);

    /**
     * Récupère le stock ERP pour une liste de locations (emplacements).
     * La "zone" en collecte correspond au préfixe ou code de l'emplacement.
     * Utilisé pour comparer ERP vs collecté sur une zone scannée.
     */
    @Query("SELECT s FROM ErpStock s WHERE TRIM(s.location) IN :locations")
    List<ErpStock> findByLocationIn(@Param("locations") List<String> locations);

    /**
     * Récupère le stock ERP pour une liste de magasins.
     * Utilisé quand on scanne plusieurs zones d'un même magasin.
     */
    @Query("SELECT s FROM ErpStock s WHERE TRIM(s.warehouseCode) IN :warehouseCodes")
    List<ErpStock> findByWarehouseCodeIn(@Param("warehouseCodes") List<String> warehouseCodes);

    /**
     * Récupère les locations distinctes d'un magasin.
     * Utilisé pour afficher les zones disponibles dans le formulaire web.
     */
    @Query("SELECT DISTINCT TRIM(s.location) FROM ErpStock s WHERE TRIM(s.warehouseCode) = TRIM(:warehouseCode) AND s.location IS NOT NULL")
    List<String> findDistinctLocationsByWarehouse(@Param("warehouseCode") String warehouseCode);

    /**
     * Récupère les magasins distincts disponibles dans le stock ERP.
     * Utilisé pour alimenter le sélecteur magasin dans le web.
     */
    @Query("SELECT DISTINCT TRIM(s.warehouseCode) FROM ErpStock s WHERE s.warehouseCode IS NOT NULL")
    List<String> findDistinctWarehouses();
}