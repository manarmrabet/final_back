package com.example.CWMS.repository.erp;

import com.example.CWMS.model.erp.StockLot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface StockLotRepository extends JpaRepository<StockLot, Long> {

    // Lire le stock d'un lot (avec désignation via jointure)
    @Query(value =
            "SELECT TOP 1 s.t_cwar, s.t_loca, s.t_item, s.t_clot, s.t_qhnd, " +
                    "ISNULL(i.t_dsca,'') AS t_dsca " +
                    "FROM dbo.dbo_twhinr1401200 s " +
                    "LEFT JOIN dbo.dbo_ttcibd001120 i ON s.t_item = i.t_item " +
                    "WHERE s.t_clot = :clot",
            nativeQuery = true)
    Optional<Object[]> findLotWithDesignation(@Param("clot") String clot);

    // Lire uniquement la quantité (pour double-vérif avant UPDATE)
    @Query(value =
            "SELECT t_qhnd FROM dbo.dbo_twhinr1401200 WHERE t_clot = :clot",
            nativeQuery = true)
    Optional<Double> getQtyByLot(@Param("clot") String clot);

    // SORTIE TOTALE : t_qhnd → 0
    // La condition AND t_qhnd > 0 empêche un double-vidage concurrent
    @Modifying
    @Transactional
    @Query(value =
            "UPDATE dbo.dbo_twhinr1401200 " +
                    "SET t_qhnd = 0, t_trdt = GETDATE() " +
                    "WHERE t_clot = :clot AND t_qhnd > 0",
            nativeQuery = true)
    int sortieTotale(@Param("clot") String clot);

    // SORTIE PARTIELLE : décrémente t_qhnd
    // La condition AND t_qhnd >= :qty protège contre un stock insuffisant concurrent
    @Modifying
    @Transactional
    @Query(value =
            "UPDATE dbo.dbo_twhinr1401200 " +
                    "SET t_qhnd = t_qhnd - :qty, t_trdt = GETDATE() " +
                    "WHERE t_clot = :clot AND t_qhnd >= :qty",
            nativeQuery = true)
    int sortiePartielle(@Param("clot") String clot, @Param("qty") double qty);
}