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

    /** * Utilisation de TRIM pour ignorer les espaces vides stockés dans l'ERP
     * Cela permet de trouver '10729202' même si la DB contient '10729202   '
     */
    @Query("SELECT a FROM ErpArticle a WHERE TRIM(a.itemCode) = TRIM(:itemCode)")
    Optional<ErpArticle> findByItemCode(@Param("itemCode") String itemCode);

    List<ErpArticle> findByDesignationContainingIgnoreCase(String keyword);
    List<ErpArticle> findByItemGroup(String group);

    @Query("""
        SELECT a FROM ErpArticle a
        WHERE UPPER(a.itemCode) LIKE UPPER(CONCAT('%', :q, '%'))
           OR UPPER(a.designation) LIKE UPPER(CONCAT('%', :q, '%'))
        """)
    List<ErpArticle> search(@Param("q") String query);
}