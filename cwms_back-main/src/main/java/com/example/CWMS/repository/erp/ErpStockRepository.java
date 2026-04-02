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

    // ─── PAGINATION ───────────────────────────────────────────────────────────
//Récupère tout le stock avec pagination (trié par ID décroissant)
    @Query("SELECT s FROM ErpStock s ORDER BY s.idStockage DESC")
    Page<ErpStock> findAll(Pageable pageable);
//Cherche toutes les lignes d'un lot spécifique (en ignorant les espaces avec TRIM)
    // ─── PAR LOT ─────────────────────────────────────────────────────────────

    @Query("SELECT s FROM ErpStock s WHERE TRIM(s.lotNumber) = TRIM(:lotNumber)")
    List<ErpStock> findByLotNumber(@Param("lotNumber") String lotNumber);

    // ─── PAR ARTICLE ──────────────────────────────────────────────────────────
//Liste tout le stock d'un article, trié par emplacement
    @Query("""
        SELECT s FROM ErpStock s
        WHERE TRIM(s.itemCode) = TRIM(:itemCode)
        ORDER BY s.location
        """)
    List<ErpStock> findByItemCode(@Param("itemCode") String itemCode);

    // ─── PAR EMPLACEMENT ──────────────────────────────────────────────────────

    @Query("SELECT s FROM ErpStock s WHERE TRIM(s.location) = TRIM(:location)")
    List<ErpStock> findByLocation(@Param("location") String location);


    // ─── COMBINÉES ────────────────────────────────────────────────────────────

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



    // ═════════════════════════════════════════════════════════════════════════
    // TRANSFERT ERP — utilisé par ErpStockUpdater
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Déplace le lot : met à jour t_loca ET t_cwar sur la ligne ERP existante.
     *
     * La ligne est identifiée par t_item + t_clot + t_loca source.
     * t_cwar est mis à jour car le lot peut changer de magasin (inter-magasin).
     * t_trdt est mis à jour à la date du jour (traçabilité ERP).
     *
     * Retourne le nombre de lignes mises à jour :
     *   0 = lot introuvable à la source (déjà déplacé ou mauvaise donnée)
     *   1 = succès normal
     *  >1 = plusieurs lignes trouvées (anomalie données ERP, à logger)
     */
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
}