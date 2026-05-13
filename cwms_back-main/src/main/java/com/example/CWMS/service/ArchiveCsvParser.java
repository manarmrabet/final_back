package com.example.CWMS.service;

import com.example.CWMS.dto.ArchiveFilterDTO;
import com.example.CWMS.dto.ArchiveLogDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class ArchiveCsvParser {

    private static final String ARCHIVE_DIR = "archives/audit_logs/";
    // Correspond exactement au format écrit dans AuditArchiveService
    private static final DateTimeFormatter DT_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSS");

    /**
     * Lit tous les fichiers CSV du dossier (ou un seul si filename != null)
     * et retourne les lignes qui correspondent aux filtres.
     */
    public List<ArchiveLogDTO> search(String filename, ArchiveFilterDTO filter) {

        File folder = new File(ARCHIVE_DIR);
        if (!folder.exists()) return List.of();

        File[] files;
        if (filename != null && !filename.isBlank()) {
            File single = new File(ARCHIVE_DIR + filename);
            files = single.exists() ? new File[]{single} : new File[0];
        } else {
            files = folder.listFiles(f -> f.getName().endsWith(".csv"));
        }

        List<ArchiveLogDTO> results = new ArrayList<>();
        if (files == null) return results;

        for (File f : files) {
            results.addAll(parseAndFilter(f, filter));
        }
        return results;
    }

    // ------------------------------------------------------------------ //
    //  Parsing interne                                                     //
    // ------------------------------------------------------------------ //

    private List<ArchiveLogDTO> parseAndFilter(File file, ArchiveFilterDTO filter) {
        List<ArchiveLogDTO> rows = new ArrayList<>();

        try (BufferedReader br =
                     new BufferedReader(new FileReader(file, StandardCharsets.UTF_8))) {

            String header = br.readLine();   // on consomme l'en-tête
            if (header == null) return rows;

            String line;
            while ((line = br.readLine()) != null) {
                ArchiveLogDTO dto = parseLine(line);
                if (dto != null && matches(dto, filter)) {
                    rows.add(dto);
                }
            }

        } catch (Exception e) {
            log.warn("Impossible de lire l'archive {} : {}", file.getName(), e.getMessage());
        }
        return rows;
    }

    /**
     * Format CSV :
     * ID;CreatedAt;User;Action;Type;Severity;Endpoint;Status;"OldValue";"NewValue"
     */
    private ArchiveLogDTO parseLine(String line) {
        try {
            // Découpe basique sur ";" — attention aux champs entre guillemets
            String[] p = line.split(";", -1);
            if (p.length < 10) return null;

            ArchiveLogDTO dto = new ArchiveLogDTO();
            dto.setId(parseLong(p[0]));
            dto.setCreatedAt(parseDateTime(p[1]));
            dto.setUsername(clean(p[2]));
            dto.setAction(clean(p[3]));
            dto.setEventType(clean(p[4]));
            dto.setSeverity(clean(p[5]));
            dto.setEndpoint(clean(p[6]));
            dto.setStatusCode(parseInteger(p[7]));
            dto.setOldValue(clean(p[8]));
            dto.setNewValue(clean(p[9]));
            return dto;

        } catch (Exception e) {
            log.trace("Ligne ignorée (format invalide) : {}", line);
            return null;
        }
    }

    // ------------------------------------------------------------------ //
    //  Filtrage                                                            //
    // ------------------------------------------------------------------ //

    private boolean matches(ArchiveLogDTO dto, ArchiveFilterDTO f) {
        if (f == null) return true;

        if (notBlank(f.getUsername()) &&
                !containsIgnoreCase(dto.getUsername(), f.getUsername())) return false;

        if (notBlank(f.getAction()) &&
                !containsIgnoreCase(dto.getAction(), f.getAction())) return false;

        if (notBlank(f.getEventType()) &&
                !dto.getEventType().equalsIgnoreCase(f.getEventType())) return false;

        if (notBlank(f.getSeverity()) &&
                !dto.getSeverity().equalsIgnoreCase(f.getSeverity())) return false;

        if (f.getFrom() != null && dto.getCreatedAt() != null &&
                dto.getCreatedAt().isBefore(f.getFrom())) return false;

        if (f.getTo() != null && dto.getCreatedAt() != null &&
                dto.getCreatedAt().isAfter(f.getTo())) return false;

        return true;
    }

    // ------------------------------------------------------------------ //
    //  Helpers                                                             //
    // ------------------------------------------------------------------ //

    private String clean(String s) {
        if (s == null) return "";
        return s.trim().replace("\"", "");
    }

    private boolean notBlank(String s) { return s != null && !s.isBlank(); }

    private boolean containsIgnoreCase(String src, String keyword) {
        if (src == null) return false;
        return src.toLowerCase().contains(keyword.toLowerCase());
    }

    private Long parseLong(String s) {
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return null; }
    }

    private Integer parseInteger(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return null; }
    }

    private LocalDateTime parseDateTime(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDateTime.parse(s.trim(), DT_FMT);
        } catch (Exception e) {
            // Fallback : format ISO standard
            try { return LocalDateTime.parse(s.trim()); } catch (Exception ex) { return null; }
        }
    }
}