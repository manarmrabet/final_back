package com.example.CWMS.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO retourné par ProductionArchiveController.listArchives()
 * Même structure que ArchiveFileDTO (utilisé par l'audit),
 * adapté aux fichiers production_backup_*.csv
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProductionArchiveFileDTO {

    /** Nom du fichier  ex: production_backup_20260502_020001.csv */
    private String        fileName;

    /** Taille en octets */
    private long          sizeBytes;

    /** Date extraite du nom du fichier */
    private LocalDateTime archiveDate;

    /** Nombre de lignes de données (hors en-tête) */
    private int           lineCount;
}