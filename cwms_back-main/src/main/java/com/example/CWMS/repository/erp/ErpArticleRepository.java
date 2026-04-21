package com.example.CWMS.repository.erp;

import com.example.CWMS.model.erp.ErpArticle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface ErpArticleRepository extends JpaRepository<ErpArticle, String> {

    @Query("SELECT a FROM ErpArticle a WHERE TRIM(a.itemCode) = TRIM(:itemCode)")
    Optional<ErpArticle> findByItemCode(@Param("itemCode") String itemCode);


    /**
     * Recherche multicritère : code, désignation, noms abrégés.
     */
    @Query("""
        SELECT a FROM ErpArticle a
        WHERE UPPER(TRIM(a.itemCode))   LIKE UPPER(CONCAT('%', :q, '%'))
           OR UPPER(a.designation)      LIKE UPPER(CONCAT('%', :q, '%'))
           OR UPPER(a.searchName)       LIKE UPPER(CONCAT('%', :q, '%'))
           OR UPPER(a.searchName2)      LIKE UPPER(CONCAT('%', :q, '%'))
        """)
    List<ErpArticle> search(@Param("q") String query);

    /**
     * ✅ AJOUT — Charge plusieurs articles en une seule requête SQL (IN).
     * Utilisé par getStockByLot() pour éviter N requêtes dans une boucle.
     *
     * Avant : 1 requête findByItemCode par ligne de stock.
     * Après : 1 seule requête pour tous les codes du lot.
     */
    @Query("SELECT a FROM ErpArticle a WHERE TRIM(a.itemCode) IN :codes")
    List<ErpArticle> findAllByItemCodeIn(@Param("codes") Set<String> codes);
}