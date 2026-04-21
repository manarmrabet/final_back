package com.example.CWMS.repository.erp;

import com.example.CWMS.model.erp.ErpStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockLotRepository extends JpaRepository<ErpStock, Long> {

    // ════════════════════════════════════════════════════════════════════════
    // LECTURE
    // ════════════════════════════════════════════════════════════════════════

    // ── 1. Snapshot lot — PROJECTION ─────────────────────────────────────
    @Query(value = """
        SELECT TOP 1
            t_cwar                AS t_cwar,
            t_loca                AS t_loca,
            t_item                AS t_item,
            t_clot                AS t_clot,
            CAST(t_qhnd AS FLOAT) AS qty
        FROM dbo_twhinr1401200
        WHERE LTRIM(RTRIM(t_clot)) = LTRIM(RTRIM(:clot))
        """, nativeQuery = true)
    Optional<LotProjection> findLotWithDesignation(@Param("clot") String clot);

    // ── 2. Quantité seule ─────────────────────────────────────────────────
    @Query(value = """
        SELECT TOP 1 CAST(t_qhnd AS FLOAT)
        FROM dbo_twhinr1401200
        WHERE LTRIM(RTRIM(t_clot)) = LTRIM(RTRIM(:clot))
        """, nativeQuery = true)
    Optional<Double> getQtyByLot(@Param("clot") String clot);

    // ── 3. Résolution magasins (GetMagasinsData3001) ──────────────────────
    // ✅ Pas de filtre hardcodé sur t_cwar → tous les magasins inclus
    // ✅ ROW_NUMBER() dynamique → compatible cwar='04','01','10'...
    @Query(value = """
        SELECT
            w.t_cwar                                        AS col0,
            ROW_NUMBER() OVER (
                PARTITION BY o.t_otyp
                ORDER BY w.t_cwar DESC
            )                                               AS col1,
            ROW_NUMBER() OVER (
                ORDER BY o.t_otyp DESC, w.t_cwar DESC
            )                                               AS col2,
            CONVERT(VARCHAR(8), GETDATE(), 112)             AS col3,
            w.t_comp                                        AS col4,
            w.t_cadr                                        AS col5,
            o.t_otyp                                        AS col6
        FROM dbo_ttcmcs003140 w
        CROSS JOIN dbo_twhinh010140 o
        /* WHERE o.t_otyp IN ('CBS', 'BSP')*/
        ORDER BY o.t_otyp DESC, w.t_cwar DESC
        """, nativeQuery = true)
    List<Object[]> getMagasinsData3001();

    // ── 4. Vérification lot dans magasin (GetMagasinsData3002) ───────────
    // ✅ Filtre AND :oset IN (1,2) supprimé → ne bloque plus cwar='04'
    @Query(value = """
        SELECT TOP 1
            :tcwar  AS col0,
            :oset   AS col1,
            :orno   AS col2,
            t_item  AS col3,
            t_clot  AS col4
        FROM dbo_twhinr1401200
        WHERE LTRIM(RTRIM(t_cwar)) = LTRIM(RTRIM(:tcwar))
          AND LTRIM(RTRIM(t_clot)) = LTRIM(RTRIM(:clot))
        """, nativeQuery = true)
    List<Object[]> getMagasinsData3002(
            @Param("tcwar") String tcwar,
            @Param("oset")  int    oset,
            @Param("orno")  String orno,
            @Param("clot")  String clot);

    // ── 5. Max pono ───────────────────────────────────────────────────────
    @Query(value = """
        SELECT COALESCE(MAX(t_pono), 0)
        FROM dbo_twhinh220140
        WHERE LTRIM(RTRIM(t_orno)) = LTRIM(RTRIM(:orno))
          AND t_oorg = 51
        """, nativeQuery = true)
    int getMaxPono(@Param("orno") String orno);




    /**
     * Vérifie si un ordre actif (ssts >= 30) existe déjà pour ce lot.
     * Utilisé avant de créer un nouvel ordre pour éviter les doublons.
     * Retourne 1 si un ordre bloquant existe, 0 sinon.
     */
    @Query(value = """
    SELECT COUNT(1)
    FROM dbo_twhinh220140
    WHERE LTRIM(RTRIM(t_orno)) = LTRIM(RTRIM(:orno))
      AND t_oorg = 51
      AND t_ssts IN (30, 70, 90)
    """, nativeQuery = true)
    int countOrdresActifs(@Param("orno") String orno);

    // ════════════════════════════════════════════════════════════════════════
    // ÉCRITURE — @Modifying + @Transactional + clearAutomatically = true
    //
    // POURQUOI ces trois annotations ensemble ?
    //
    // @Modifying          : indique à JPA que c'est un INSERT/UPDATE/DELETE
    //                       (pas un SELECT) → sans ça = exception immédiate
    //
    // clearAutomatically  : vide le cache de premier niveau (EntityManager)
    //                       après l'exécution → les lectures suivantes
    //                       voient les données fraîches de la base
    //                       → sans ça = lectures obsolètes après UPDATE
    //
    // @Transactional      : certains contextes d'appel (tests, appels directs)
    //                       n'ont pas de transaction active → cette annotation
    //                       en crée une si nécessaire
    //                       → sans ça = "Executing an update/delete query"
    // ════════════════════════════════════════════════════════════════════════

    // ── 6. INSERT ordre de sortie → dbo_twhinh220140 ─────────────────────
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query(value = """
        INSERT INTO dbo_twhinh220140 (
            t_oorg, t_orno, t_pono, t_seqn, t_oset, t_cwar, t_comp,
            t_acvt, t_item, t_serl, t_ssts, t_lsel, t_clot, t_effn,
            t_revi, t_prio, t_qoro, t_orun, t_ubin, t_hstq, t_qord,
            t_qoor, t_pddt, t_prdt, t_addt, t_inup, t_Refcntd, t_Refcntu
        ) VALUES (
            51, :orno, :pono, 1, :oset, :cwar, :comp,
            1,  :item, ' ',  20, 3,    :clot, 0,
            ' ', 2147483647, :qoro, :orun, 2, 3, :qord,
            :qoor, GETDATE(), GETDATE(), '1970-01-01', 1, 0, 0
        )
        """, nativeQuery = true)
    int insertOrdreWhinh220(
            @Param("orno")  String orno,
            @Param("pono")  int    pono,
            @Param("oset")  int    oset,
            @Param("cwar")  String cwar,
            @Param("comp")  String comp,
            @Param("item")  String item,
            @Param("clot")  String clot,
            @Param("qoro")  double qoro,
            @Param("orun")  String orun,
            @Param("qord")  double qord,
            @Param("qoor")  double qoor);

    // ── 7. INSERT ligne picking → dbo_twhinp100140 ───────────────────────
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query(value = """
        INSERT INTO dbo_twhinp100140 (
            t_koor, t_orno, t_kotr, t_pono, t_ponb, t_boml, t_effn,
            t_item, t_cuni, t_plpl, t_pric, t_ccur, t_chan,
            t_qana, t_qorg, t_qinp, t_expl, t_date, t_ordt,
            t_typw, t_cwar, t_prio, t_blck, t_scon, t_oprs,
            t_owns, t_cacc, t_subc, t_topl, t_Refcntd, t_Refcntu
        ) VALUES (
            32,    :orno, 2, :pono, 1, 0, 0,
            :item, :orun, 2, 0, '  ', '  ',
            :qoro, :qoro, 0, 0, GETDATE(), GETDATE(),
            1,    :cwar, 2147483647, 2, 1, 4,
            10, 2, 2, 32, 0, 0
        )
        """, nativeQuery = true)
    int insertLigneWhinp100(
            @Param("orno")  String orno,
            @Param("pono")  int    pono,
            @Param("item")  String item,
            @Param("orun")  String orun,
            @Param("qoro")  double qoro,
            @Param("cwar")  String cwar);

    // ── 8. UPDATE allocation article → dbo_ttcibd100140 ──────────────────
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query(value = """
        UPDATE dbo_ttcibd100140
        SET t_allo = t_allo + :qty
        WHERE LTRIM(RTRIM(t_item)) = LTRIM(RTRIM(:item))
        """, nativeQuery = true)
    int updateAlloArticle(
            @Param("item") String item,
            @Param("qty")  double qty);

    // ── 9. UPDATE allocation emplacement → dbo_twhwmd215140 ──────────────
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query(value = """
        UPDATE dbo_twhwmd215140
        SET t_qall = t_qall + :qty
        WHERE LTRIM(RTRIM(t_item)) = LTRIM(RTRIM(:item))
          AND LTRIM(RTRIM(t_cwar)) = LTRIM(RTRIM(:cwar))
        """, nativeQuery = true)
    int updateAlloEmplacement(
            @Param("item") String item,
            @Param("cwar") String cwar,
            @Param("qty")  double qty);

    // ── 10. Sortie totale : t_qhnd → 0 ───────────────────────────────────
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query(value = """
    UPDATE dbo_twhinr1401200
    SET t_qhnd = '0',
        t_trdt = GETDATE()
    WHERE LTRIM(RTRIM(t_clot)) = LTRIM(RTRIM(:clot))
      -- TRY_CAST renvoie NULL si erreur, donc la condition sera fausse sans planter
      AND TRY_CAST(LTRIM(RTRIM(t_qhnd)) AS FLOAT) > 0
    """, nativeQuery = true)
    int sortieTotale(@Param("clot") String clot);

    // ── 11. Sortie partielle : t_qhnd -= qty ─────────────────────────────
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query(value = """
    UPDATE dbo_twhinr1401200
    SET t_qhnd = CAST(TRY_CAST(LTRIM(RTRIM(t_qhnd)) AS FLOAT) - :qty AS VARCHAR(20)),
        t_trdt = GETDATE()
    WHERE LTRIM(RTRIM(t_clot)) = LTRIM(RTRIM(:clot))
      AND TRY_CAST(LTRIM(RTRIM(t_qhnd)) AS FLOAT) >= :qty
    """, nativeQuery = true)
    int sortiePartielle(@Param("clot") String clot, @Param("qty") double qty);


// ════════════════════════════════════════════════════════════════════════
    // SECTION 3 — CONFIRMATION ERP (NOUVEAU — étape manquante)
    // ════════════════════════════════════════════════════════════════════════

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query(value = """
        UPDATE dbo_twhinh220140
        SET    t_ssts = 70,
               t_inup = 1
        WHERE  LTRIM(RTRIM(t_orno)) = LTRIM(RTRIM(:orno))
          AND  t_pono  = :pono
          AND  t_oorg  = 51
          AND  t_ssts  = 20
        """, nativeQuery = true)
    int confirmerOrdreWhinh220(
            @Param("orno") String orno,
            @Param("pono") int    pono);



}