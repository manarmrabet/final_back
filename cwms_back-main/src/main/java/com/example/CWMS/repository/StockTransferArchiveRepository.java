package com.example.CWMS.repository;

import com.example.CWMS.model.StockTransferArchive;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface StockTransferArchiveRepository
        extends JpaRepository<StockTransferArchive, Long> {

    /**
     * Recherche paginée dans les archives.
     * Tous les paramètres sont optionnels (null = pas de filtre).
     */
    @Query("""
        SELECT a FROM StockTransferArchive a
        WHERE (:status   IS NULL OR a.status       = :status)
          AND (:itemCode IS NULL OR a.erpItemCode  = :itemCode)
          AND (:location IS NULL
               OR a.sourceLocation = :location
               OR a.destLocation   = :location)
          AND (:from     IS NULL OR a.createdAt   >= :from)
          AND (:to       IS NULL OR a.createdAt   <= :to)
        """)
    Page<StockTransferArchive> searchArchive(
            @Param("status")   String        status,
            @Param("itemCode") String        itemCode,
            @Param("location") String        location,
            @Param("from")     LocalDateTime from,
            @Param("to")       LocalDateTime to,
            Pageable pageable);

    /** Statistique : combien de lignes archivées sur une période donnée */
    long countByArchivedAtBetween(LocalDateTime start, LocalDateTime end);
}