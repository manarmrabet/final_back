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

    @Query("SELECT a FROM ErpArticle a WHERE TRIM(a.itemCode) = TRIM(:itemCode)")
    Optional<ErpArticle> findByItemCode(@Param("itemCode") String itemCode);

    List<ErpArticle> findByDesignationContainingIgnoreCase(String keyword);
    List<ErpArticle> findByItemGroup(String group);

    /**
     * Recherche multicritère :
     * Code Article OR Désignation OR Nom abrégé (seak) OR Nom abrégé 2 (seab)
     */
    @Query("""
        SELECT a FROM ErpArticle a
        WHERE UPPER(TRIM(a.itemCode)) LIKE UPPER(CONCAT('%', :q, '%'))
           OR UPPER(a.designation) LIKE UPPER(CONCAT('%', :q, '%'))
           OR UPPER(a.searchName) LIKE UPPER(CONCAT('%', :q, '%'))
           OR UPPER(a.searchName2) LIKE UPPER(CONCAT('%', :q, '%'))
        """)
    List<ErpArticle> search(@Param("q") String query);


}