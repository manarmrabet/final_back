package com.example.CWMS.dto;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class ArchiveFileDTO {
    private String filename;
    private long sizeBytes;
    private LocalDateTime archiveDate;   // extraite du nom du fichier
    private int recordCount;// nombre de lignes dans le CSV (hors header)


}