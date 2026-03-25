// ─────────────────────────────────────────────────────────────────────────────
// FILE 2 : ErpEmplacementRepository.java
// ─────────────────────────────────────────────────────────────────────────────
package com.example.CWMS.erp.repository;

import com.example.CWMS.erp.entity.ErpEmplacement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ErpEmplacementRepository extends JpaRepository<ErpEmplacement, String> {

    Optional<ErpEmplacement> findByLocationCode(String code);

    List<ErpEmplacement> findByLocationType(String type);

    /** Emplacements actifs seulement (date fin nulle ou future) */
    @Query("""
        SELECT e FROM ErpEmplacement e
        WHERE e.endDate IS NULL OR e.endDate > CURRENT_DATE
        ORDER BY e.locationCode
        """)
    List<ErpEmplacement> findAllActive();

    /** Recherche par code ou nom */
    @Query("""
        SELECT e FROM ErpEmplacement e
        WHERE UPPER(e.locationCode) LIKE UPPER(CONCAT('%', :q, '%'))
           OR UPPER(e.locationName) LIKE UPPER(CONCAT('%', :q, '%'))
        """)
    List<ErpEmplacement> search(@Param("q") String query);
}