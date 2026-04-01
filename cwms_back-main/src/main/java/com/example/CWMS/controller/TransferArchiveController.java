package com.example.CWMS.controller;

import com.example.CWMS.dto.TransferResponseDTO;
import com.example.CWMS.service.ArchiveQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Endpoints dédiés aux archives de transferts.
 */
@RestController
@RequestMapping("/api/transfers/archives")
@RequiredArgsConstructor
@Slf4j
public class TransferArchiveController {

    private final ArchiveQueryService archiveQueryService;

    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchArchive(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String itemCode,
            @RequestParam(required = false) String location,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        int safeSize = Math.min(size, 100);

        PageRequest pageable = PageRequest.of(
                page, safeSize,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<TransferResponseDTO> result = archiveQueryService
                .searchArchive(status, itemCode, location, from, to, pageable);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Archives récupérées",
                "data", Map.of(
                        "content", result.getContent(),
                        "page", result.getNumber(),
                        "size", result.getSize(),
                        "totalElements", result.getTotalElements(),
                        "totalPages", result.getTotalPages(),
                        "first", result.isFirst(),
                        "last", result.isLast()
                )
        ));
    }

    @GetMapping("/export/csv")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String itemCode,
            @RequestParam(required = false) String location,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {

        log.info("Export CSV demandé - status={}, itemCode={}, location={}, from={}, to={}",
                status, itemCode, location, from, to);

        PageRequest pageable = PageRequest.of(0, 50_000,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<TransferResponseDTO> result = archiveQueryService
                .searchArchive(status, itemCode, location, from, to, pageable);

        log.info("Export CSV - {} archives trouvées (totalElements = {})",
                result.getContent().size(), result.getTotalElements());

        byte[] csv = buildCsv(result);

        String filename = "transferts_archives_" + java.time.LocalDate.now() + ".csv";

        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .header("Content-Type", "text/csv; charset=UTF-8")
                .body(csv);
    }

    private byte[] buildCsv(Page<TransferResponseDTO> page) {
        StringBuilder sb = new StringBuilder();

        // BOM UTF-8 pour Excel
        sb.append('\uFEFF');

        // En-têtes
        sb.append("ID;Date;Article (Code);Article (Nom);Lot;")
                .append("Source;Destination;Entrepôt source;Entrepôt dest;")
                .append("Quantité;Unité;Statut;Type;Opérateur;Notes\n");

        log.info("buildCsv() - Nombre de lignes à écrire : {}", page.getContent().size());

        for (TransferResponseDTO t : page.getContent()) {
            sb.append(nvl(t.getId())).append(';')
                    .append(nvl(t.getCreatedAt())).append(';')
                    .append(nvl(t.getErpItemCode())).append(';')
                    .append(csvEscape(t.getErpItemLabel())).append(';')
                    .append(nvl(t.getLotNumber())).append(';')
                    .append(nvl(t.getSourceLocation())).append(';')
                    .append(nvl(t.getDestLocation())).append(';')
                    .append(nvl(t.getSourceWarehouse())).append(';')
                    .append(nvl(t.getDestWarehouse())).append(';')
                    .append(nvl(t.getQuantity())).append(';')
                    .append(nvl(t.getUnit())).append(';')
                    .append(nvl(t.getStatus())).append(';')
                    .append(nvl(t.getTransferType())).append(';')
                    .append(csvEscape(t.getOperatorName())).append(';')
                    .append(csvEscape(t.getNotes())).append('\n');
        }

        log.info("CSV généré - Taille finale : {} octets", sb.length());

        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private String nvl(Object o) {
        return o == null ? "" : o.toString();
    }

    private String csvEscape(String s) {
        if (s == null) return "";
        if (s.contains(";") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}