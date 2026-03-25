// ─────────────────────────────────────────────────────────────────────────────
// FILE 1 : ErpArticleRepository.java
// ─────────────────────────────────────────────────────────────────────────────
package com.example.CWMS.erp.repository;

import com.example.CWMS.erp.entity.ErpArticle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ErpArticleRepository extends JpaRepository<ErpArticle, String> {

    /** Recherche par code exact (scan QR/barcode) */
    Optional<ErpArticle> findByItemCode(String itemCode);

    /** Recherche par désignation (recherche texte web) */
    List<ErpArticle> findByDesignationContainingIgnoreCase(String keyword);

    /** Articles d'un groupe */
    List<ErpArticle> findByItemGroup(String group);

    /** Recherche combinée code ou désignation */
    @Query("""
        SELECT a FROM ErpArticle a
        WHERE UPPER(a.itemCode) LIKE UPPER(CONCAT('%', :q, '%'))
           OR UPPER(a.designation) LIKE UPPER(CONCAT('%', :q, '%'))
        """)
    List<ErpArticle> search(@Param("q") String query);
}