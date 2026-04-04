package com.example.CWMS.service;

import com.example.CWMS.dto.*;
import com.example.CWMS.iservice.InventoryService;
import com.example.CWMS.model.cwms.*;
import com.example.CWMS.model.cwms.InventorySession.SessionStatus;
import com.example.CWMS.model.erp.ErpArticle;
import com.example.CWMS.model.erp.ErpStock;
import com.example.CWMS.repository.cwms.*;
import com.example.CWMS.repository.erp.ErpArticleRepository;
import com.example.CWMS.repository.erp.ErpStockRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final InventorySessionRepository sessionRepo;
    private final CollectLineRepository       lineRepo;
    private final CollectTemplateRepository   templateRepo;
    private final InventoryReportRepository   reportRepo;
    private final ErpStockRepository          erpStockRepo;
    private final ErpArticleRepository        erpArticleRepo;
    private final ObjectMapper                objectMapper;

    // ════════════════════════════════════════════════════════════════
    // SESSIONS
    // ════════════════════════════════════════════════════════════════

    @Override
    public List<InventorySessionDTO> getAllSessions() {
        return sessionRepo.findAllByOrderByCreatedAtDesc()
                .stream().map(this::toSessionDTO).collect(Collectors.toList());
    }

    @Override
    public InventorySessionDTO getSessionById(Long id) {
        return toSessionDTO(getSessionOrThrow(id));
    }

    @Override
    public InventorySessionDTO createSession(CreateSessionRequest req, String username) {
        InventorySession session = InventorySession.builder()
                .name(req.getName().trim())
                .warehouseCode(req.getWarehouseCode().trim().toUpperCase())
                .warehouseLabel(req.getWarehouseLabel())
                .status(SessionStatus.EN_COURS)
                .createdBy(username)
                .build();
        return toSessionDTO(sessionRepo.save(session));
    }

    @Override
    @Transactional
    public InventorySessionDTO validateSession(Long sessionId, String username) {
        InventorySession session = getSessionOrThrow(sessionId);
        if (session.getStatus() != SessionStatus.EN_COURS)
            throw new RuntimeException("Session déjà validée ou clôturée");
        session.setStatus(SessionStatus.VALIDEE);
        session.setValidatedBy(username);
        session.setValidatedAt(LocalDateTime.now());
        return toSessionDTO(sessionRepo.save(session));
    }

    // ════════════════════════════════════════════════════════════════
    // LIGNES DE COLLECTE
    // ════════════════════════════════════════════════════════════════

    @Override
    public CollectLineDTO addLine(AddCollectLineRequest req, String username) {
        InventorySession session = getSessionOrThrow(req.getSessionId());
        if (session.getStatus() != SessionStatus.EN_COURS)
            throw new RuntimeException("Session non active");
        CollectLine line = CollectLine.builder()
                .session(session)
                .locationCode(req.getLocationCode().trim().toUpperCase())
                .locationLabel(req.getLocationLabel())
                .valuesJson(toJson(req.getValues()))
                .scannedBy(username)
                .build();
        return toLineDTO(lineRepo.save(line));
    }

    @Override
    public List<CollectLineDTO> getLinesBySession(Long sessionId) {
        return lineRepo.findBySessionId(sessionId)
                .stream().map(this::toLineDTO).collect(Collectors.toList());
    }

    @Override
    public void deleteLine(Long lineId) {
        lineRepo.deleteById(lineId);
    }

    // ════════════════════════════════════════════════════════════════
    // TEMPLATES — FIX : éviter les doublons de nom
    // ════════════════════════════════════════════════════════════════

    @Override
    public List<CollectTemplateDTO> getActiveTemplates() {
        return templateRepo.findByActiveTrue()
                .stream().map(this::toTemplateDTO).collect(Collectors.toList());
    }

    @Override
    public CollectTemplateDTO createTemplate(CollectTemplateDTO dto) {
        // FIX : vérifier si un template avec ce nom existe déjà
        Optional<CollectTemplate> existing = templateRepo.findByName(dto.getName().trim());
        if (existing.isPresent()) {
            // Mettre à jour au lieu de créer un doublon
            CollectTemplate t = existing.get();
            t.setFieldsJson(toJson(dto.getFields()));
            t.setActive(true);
            return toTemplateDTO(templateRepo.save(t));
        }
        CollectTemplate t = CollectTemplate.builder()
                .name(dto.getName().trim())
                .fieldsJson(toJson(dto.getFields()))
                .active(true)
                .build();
        return toTemplateDTO(templateRepo.save(t));
    }

    // ════════════════════════════════════════════════════════════════
    // DONNÉES ERP
    // ════════════════════════════════════════════════════════════════

    @Override
    public List<String> getErpWarehouses() {
        return erpStockRepo.findDistinctWarehouses();
    }

    @Override
    public List<String> getErpLocationsByWarehouse(String warehouseCode) {
        return erpStockRepo.findDistinctLocationsByWarehouse(warehouseCode);
    }

    // ════════════════════════════════════════════════════════════════
    // MOTEUR DE COMPARAISON — FIX duplicate key constraint
    // ════════════════════════════════════════════════════════════════

    @Override
    @Transactional
    public InventoryReportDTO generateReport(Long sessionId, String username) {
        InventorySession session = getSessionOrThrow(sessionId);
        List<CollectLine> collectLines = lineRepo.findBySessionId(sessionId);

        if (collectLines.isEmpty())
            throw new RuntimeException("Aucune ligne collectée pour cette session");

        // 1. Emplacements scannés
        List<String> scannedLocations = collectLines.stream()
                .map(CollectLine::getLocationCode).map(String::trim)
                .distinct().collect(Collectors.toList());

        // 2. Stock ERP
        List<ErpStock> erpStocks = erpStockRepo.findByLocationIn(scannedLocations);
        log.info("Session {} — {} lignes collectées, {} lignes ERP", sessionId, collectLines.size(), erpStocks.size());

        // 3. Désignations articles en batch
        Set<String> allItemCodes = new HashSet<>();
        erpStocks.forEach(s -> { if (s.getItemCode() != null) allItemCodes.add(trim(s.getItemCode())); });
        collectLines.forEach(cl -> {
            Map<String, String> vals = fromJson(cl.getValuesJson());
            String art = vals.getOrDefault("ARTICLE", vals.getOrDefault("article", ""));
            if (!art.isEmpty()) allItemCodes.add(trim(art));
        });

        Map<String, ErpArticle> articleMap = new HashMap<>();
        if (!allItemCodes.isEmpty()) {
            erpArticleRepo.findAllByItemCodeIn(allItemCodes)
                    .forEach(a -> articleMap.put(trim(a.getItemCode()), a));
        }

        // 4. Map ERP
        Map<String, Double> erpMap = new HashMap<>();
        Map<String, ErpStock> erpDetailMap = new HashMap<>();
        for (ErpStock s : erpStocks) {
            String key = buildKey(s.getItemCode(), s.getLocation(), s.getLotNumber());
            erpMap.merge(key, s.getQuantityAvailable(), Double::sum);
            erpDetailMap.put(key, s);
        }

        // 5. Map Collecte
        Map<String, Double> collectMap = new HashMap<>();
        for (CollectLine cl : collectLines) {
            Map<String, String> vals = fromJson(cl.getValuesJson());
            String itemCode  = trim(vals.getOrDefault("ARTICLE", vals.getOrDefault("article", "")));
            String lotNumber = trim(vals.getOrDefault("LOT",     vals.getOrDefault("lot", "")));
            String location  = trim(cl.getLocationCode());
            double qty       = parseQty(vals.getOrDefault("QUANTITE", vals.getOrDefault("quantite", "0")));
            if (itemCode.isEmpty()) continue;
            collectMap.merge(buildKey(itemCode, location, lotNumber), qty, Double::sum);
        }

        // 6. Analyse écarts
        List<ReportLineDTO> reportLines = new ArrayList<>();
        Set<String> allKeys = new HashSet<>();
        allKeys.addAll(erpMap.keySet());
        allKeys.addAll(collectMap.keySet());

        int conforme = 0, ecart = 0, manquant = 0, surplus = 0;
        for (String key : allKeys) {
            String[] parts   = key.split("\\|", -1);
            String itemCode  = parts.length > 0 ? parts[0] : "";
            String location  = parts.length > 1 ? parts[1] : "";
            String lotNumber = parts.length > 2 ? parts[2] : "";

            Double qErp      = erpMap.get(key);
            Double qCollecte = collectMap.get(key);
            ErpStock  detail = erpDetailMap.get(key);
            ErpArticle art   = articleMap.get(itemCode);

            String statut;
            if (qErp != null && qCollecte != null) {
                statut = (Math.abs(qErp - qCollecte) < 0.001) ? "CONFORME" : "ECART";
                if ("CONFORME".equals(statut)) conforme++; else ecart++;
            } else if (qErp != null) {
                statut = "MANQUANT"; qCollecte = 0.0; manquant++;
            } else {
                statut = "SURPLUS"; qErp = 0.0; surplus++;
            }

            reportLines.add(ReportLineDTO.builder()
                    .locationCode(location)
                    .itemCode(itemCode)
                    .designation(art != null ? art.getDesignation() : "")
                    .lotNumber(lotNumber)
                    .warehouseCode(detail != null ? trim(detail.getWarehouseCode()) : session.getWarehouseCode())
                    .unit(art != null ? art.getStockUnit() : "")
                    .quantiteErp(qErp)
                    .quantiteCollecte(qCollecte)
                    .ecart(qCollecte - qErp)
                    .statut(statut)
                    .build());
        }

        reportLines.sort(Comparator.comparingInt(l -> switch (l.getStatut()) {
            case "MANQUANT" -> 0; case "ECART" -> 1; case "SURPLUS" -> 2; default -> 3;
        }));

        // 7. FIX DUPLICATE KEY : update si rapport existe, sinon créer
        InventoryReport report = reportRepo.findBySessionId(sessionId).orElse(null);
        if (report == null) {
            report = InventoryReport.builder().session(session).build();
        }
        report.setTotalErp(erpMap.size());
        report.setTotalCollecte(collectMap.size());
        report.setTotalConforme(conforme);
        report.setTotalEcart(ecart);
        report.setTotalManquant(manquant);
        report.setTotalSurplus(surplus);
        report.setReportJson(toJson(reportLines));
        report.setGeneratedBy(username);
        report.setGeneratedAt(LocalDateTime.now());
        reportRepo.save(report);

        log.info("Rapport session {} : {} conformes, {} écarts, {} manquants, {} surplus",
                sessionId, conforme, ecart, manquant, surplus);
        return buildReportDTO(report, session, reportLines);
    }

    @Override
    public InventoryReportDTO getReport(Long sessionId) {
        InventoryReport report = reportRepo.findBySessionId(sessionId)
                .orElseThrow(() -> new RuntimeException("Aucun rapport pour la session: " + sessionId));
        return buildReportDTO(report, report.getSession(), fromJsonList(report.getReportJson()));
    }

    // ════════════════════════════════════════════════════════════════
    // EXPORT EXCEL — FIX : vérifier que le rapport existe avant export
    // ════════════════════════════════════════════════════════════════

    @Override
    public ResponseEntity<byte[]> exportCollectExcel(Long sessionId) {
        InventorySession session = getSessionOrThrow(sessionId);
        List<CollectLine> lines = lineRepo.findBySessionId(sessionId);
        if (lines.isEmpty())
            throw new RuntimeException("Aucune ligne à exporter");

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Collecte");
            CellStyle hStyle = headerStyle(wb);

            List<String> headers = new ArrayList<>(List.of("Emplacement", "Libellé"));
            if (!lines.isEmpty()) {
                Map<String, String> first = fromJson(lines.get(0).getValuesJson());
                headers.addAll(new ArrayList<>(first.keySet()));
            }
            headers.addAll(List.of("Saisi par", "Date/Heure"));

            Row hRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                Cell c = hRow.createCell(i); c.setCellValue(headers.get(i)); c.setCellStyle(hStyle);
            }
            int rowNum = 1;
            for (CollectLine cl : lines) {
                Row row = sheet.createRow(rowNum++);
                Map<String, String> vals = fromJson(cl.getValuesJson());
                row.createCell(0).setCellValue(cl.getLocationCode());
                row.createCell(1).setCellValue(cl.getLocationLabel() != null ? cl.getLocationLabel() : "");
                int col = 2;
                for (int i = 2; i < headers.size() - 2; i++)
                    row.createCell(col++).setCellValue(vals.getOrDefault(headers.get(i), ""));
                row.createCell(col++).setCellValue(cl.getScannedBy() != null ? cl.getScannedBy() : "");
                row.createCell(col).setCellValue(cl.getScannedAt() != null ? cl.getScannedAt().toString() : "");
            }
            for (int i = 0; i < headers.size(); i++) sheet.autoSizeColumn(i);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return excelResponse(out, "collecte_" + session.getWarehouseCode() + ".xlsx");
        } catch (Exception e) {
            throw new RuntimeException("Erreur export collecte: " + e.getMessage(), e);
        }
    }

    @Override
    public ResponseEntity<byte[]> exportReportExcel(Long sessionId) {
        // FIX : récupérer le rapport existant, pas le régénérer
        InventoryReport savedReport = reportRepo.findBySessionId(sessionId)
                .orElseThrow(() -> new RuntimeException("Générez d'abord le rapport avant d'exporter"));
        InventoryReportDTO report = buildReportDTO(savedReport, savedReport.getSession(), fromJsonList(savedReport.getReportJson()));

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            CellStyle hStyle = headerStyle(wb);

            // Synthèse
            Sheet syn = wb.createSheet("Synthèse");
            String[][] synthData = {
                    {"Magasin", report.getWarehouseCode()},
                    {"Session", report.getSessionName()},
                    {"Date", report.getGeneratedAt() != null ? report.getGeneratedAt().toString() : ""},
                    {"", ""},
                    {"Total ERP",        String.valueOf(report.getTotalErp())},
                    {"Total Collecté",   String.valueOf(report.getTotalCollecte())},
                    {"✅ Conformes",     String.valueOf(report.getTotalConforme())},
                    {"⚠️ Écarts",        String.valueOf(report.getTotalEcart())},
                    {"🔴 Manquants",     String.valueOf(report.getTotalManquant())},
                    {"🟡 Surplus",       String.valueOf(report.getTotalSurplus())},
            };
            for (int i = 0; i < synthData.length; i++) {
                Row row = syn.createRow(i);
                row.createCell(0).setCellValue(synthData[i][0]);
                row.createCell(1).setCellValue(synthData[i][1]);
            }
            syn.autoSizeColumn(0); syn.autoSizeColumn(1);

            // Onglets par statut
            String[] statuts = {"CONFORME", "ECART", "MANQUANT", "SURPLUS"};
            String[] onglets = {"Conformes", "Ecarts", "Manquants", "Surplus"};
            String[] cols    = {"Emplacement","Article","Désignation","Lot","Unité","Qté ERP","Qté Collectée","Écart","Statut"};

            for (int s = 0; s < statuts.length; s++) {
                final String statut = statuts[s];
                Sheet sheet = wb.createSheet(onglets[s]);
                Row hRow = sheet.createRow(0);
                for (int i = 0; i < cols.length; i++) {
                    Cell c = hRow.createCell(i); c.setCellValue(cols[i]); c.setCellStyle(hStyle);
                }
                int rowNum = 1;
                for (ReportLineDTO line : report.getLines()) {
                    if (!statut.equals(line.getStatut())) continue;
                    Row row = sheet.createRow(rowNum++);
                    row.createCell(0).setCellValue(line.getLocationCode());
                    row.createCell(1).setCellValue(line.getItemCode());
                    row.createCell(2).setCellValue(line.getDesignation() != null ? line.getDesignation() : "");
                    row.createCell(3).setCellValue(line.getLotNumber() != null ? line.getLotNumber() : "");
                    row.createCell(4).setCellValue(line.getUnit() != null ? line.getUnit() : "");
                    row.createCell(5).setCellValue(line.getQuantiteErp());
                    row.createCell(6).setCellValue(line.getQuantiteCollecte());
                    row.createCell(7).setCellValue(line.getEcart());
                    row.createCell(8).setCellValue(line.getStatut());
                }
                for (int i = 0; i < cols.length; i++) sheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return excelResponse(out, "rapport_inventaire_" + sessionId + ".xlsx");
        } catch (Exception e) {
            throw new RuntimeException("Erreur export rapport: " + e.getMessage(), e);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // HELPERS
    // ════════════════════════════════════════════════════════════════

    private InventorySession getSessionOrThrow(Long id) {
        return sessionRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Session introuvable: " + id));
    }

    private String buildKey(String item, String location, String lot) {
        return String.join("|", trim(item), trim(location), trim(lot));
    }

    private String trim(String s) {
        return s == null ? "" : s.trim().toUpperCase();
    }

    private double parseQty(String s) {
        if (s == null || s.trim().isEmpty()) return 0.0;
        try { return Double.parseDouble(s.trim().replace(",", ".")); }
        catch (Exception e) { return 0.0; }
    }

    private String toJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); }
        catch (Exception e) { return "{}"; }
    }

    private Map<String, String> fromJson(String json) {
        try { return objectMapper.readValue(json, new TypeReference<>() {}); }
        catch (Exception e) { return new HashMap<>(); }
    }

    private List<ReportLineDTO> fromJsonList(String json) {
        try { return objectMapper.readValue(json,
                objectMapper.getTypeFactory().constructCollectionType(List.class, ReportLineDTO.class)); }
        catch (Exception e) { return new ArrayList<>(); }
    }

    private CellStyle headerStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont(); f.setBold(true); f.setColor(IndexedColors.WHITE.getIndex());
        s.setFont(f);
        s.setFillForegroundColor(IndexedColors.ROYAL_BLUE.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setBorderBottom(BorderStyle.THIN);
        return s;
    }

    private ResponseEntity<byte[]> excelResponse(ByteArrayOutputStream out, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(out.toByteArray());
    }

    // ── Mappers ────────────────────────────────────────────────────

    private InventorySessionDTO toSessionDTO(InventorySession s) {
        return InventorySessionDTO.builder()
                .id(s.getId()).name(s.getName())
                .warehouseCode(s.getWarehouseCode()).warehouseLabel(s.getWarehouseLabel())
                .status(s.getStatus()).createdBy(s.getCreatedBy())
                .createdAt(s.getCreatedAt()).validatedAt(s.getValidatedAt())
                .totalLines(lineRepo.countBySessionId(s.getId()))
                .build();
    }

    private CollectLineDTO toLineDTO(CollectLine cl) {
        return CollectLineDTO.builder()
                .id(cl.getId()).sessionId(cl.getSession().getId())
                .locationCode(cl.getLocationCode()).locationLabel(cl.getLocationLabel())
                .values(fromJson(cl.getValuesJson()))
                .scannedBy(cl.getScannedBy()).scannedAt(cl.getScannedAt())
                .build();
    }

    private CollectTemplateDTO toTemplateDTO(CollectTemplate t) {
        List<String> fields;
        try { fields = objectMapper.readValue(t.getFieldsJson(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)); }
        catch (Exception e) { fields = new ArrayList<>(); }
        return CollectTemplateDTO.builder()
                .id(t.getId()).name(t.getName()).fields(fields).active(t.isActive()).build();
    }

    private InventoryReportDTO buildReportDTO(InventoryReport r, InventorySession s, List<ReportLineDTO> lines) {
        return InventoryReportDTO.builder()
                .id(r.getId()).sessionId(s.getId()).sessionName(s.getName())
                .warehouseCode(s.getWarehouseCode())
                .totalErp(r.getTotalErp()).totalCollecte(r.getTotalCollecte())
                .totalConforme(r.getTotalConforme()).totalEcart(r.getTotalEcart())
                .totalManquant(r.getTotalManquant()).totalSurplus(r.getTotalSurplus())
                .generatedAt(r.getGeneratedAt()).lines(lines)
                .build();
    }
}