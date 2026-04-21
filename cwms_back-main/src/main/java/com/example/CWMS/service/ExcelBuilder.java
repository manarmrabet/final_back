package com.example.CWMS.service;

import com.example.CWMS.dto.ReceptionLineDTO;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Génère les fichiers Excel de bons de réception.
 *
 * CORRECTIONS APPORTÉES :
 *  • XSSFFont.setColor() ne peut PAS recevoir un short standard pour les couleurs custom.
 *    Pour les couleurs hex, utiliser setColor(XSSFColor) via cast vers XSSFFont.
 *  • Suppression de l'usage de IndexedColors.WHITE.getIndex() sur XSSFFont custom :
 *    remplacé par ((XSSFFont) font).setColor(new XSSFColor(...)).
 *  • setColor(IndexedColors.X.getIndex()) garde pour les couleurs standard POI.
 */
@Slf4j
public final class ExcelBuilder {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // Couleurs ERP COFAT (octets signés)
    private static final byte[] C_HEADER = {(byte) 26,  (byte) 58,  (byte) 107}; // bleu foncé
    private static final byte[] C_SUBTOT = {(byte) 210, (byte) 225, (byte) 245}; // bleu clair
    private static final byte[] C_ODD    = {(byte) 245, (byte) 248, (byte) 255}; // alternance
    private static final byte[] C_ORNO   = {(byte) 232, (byte) 240, (byte) 255}; // label ORNO

    private ExcelBuilder() {}

    public static byte[] build(List<ReceptionLineDTO> lines) {
        var bos = new ByteArrayOutputStream();
        try (var wb = new XSSFWorkbook()) {

            var sheet = wb.createSheet("Bons de Réception");

            // ── Largeurs colonnes ────────────────────────────────────────────
            int[] widths = {14,14,6,14,14,20,35,10,10,14,8,12,14};
            for (int i = 0; i < widths.length; i++)
                sheet.setColumnWidth(i, widths[i] * 256);

            // ── Styles ───────────────────────────────────────────────────────
            var titleStyle  = buildTitleStyle(wb);
            var metaKeyStyle = buildMetaKeyStyle(wb);
            var metaValStyle = buildMetaValStyle(wb);
            var headerStyle = buildHeaderStyle(wb);
            var ornoStyle   = buildOrnoStyle(wb);
            var dataStyle   = buildDataStyle(wb, false);
            var dataOdd     = buildDataStyle(wb, true);
            var numStyle    = buildNumStyle(wb, false);
            var numOdd      = buildNumStyle(wb, true);
            var subStyle    = buildSubtotalStyle(wb);
            var totalStyle  = buildTotalStyle(wb);

            // ── Ligne 1 : Titre ──────────────────────────────────────────────
            var rowTitle = sheet.createRow(0);
            rowTitle.setHeightInPoints(22);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 12));
            setStr(rowTitle, 0, "BONS DE RÉCEPTION", titleStyle);

            // ── Méta (lignes 2-4) ────────────────────────────────────────────
            String devise  = lines.isEmpty() ? "USD" : lines.get(0).getDevise();
            String frsCode = lines.isEmpty() ? "" : lines.get(0).getFournisseur();
            String frsNom  = lines.isEmpty() ? "" : lines.get(0).getDescFrs();
            long   nbRcno  = lines.stream()
                    .map(ReceptionLineDTO::getNumeroReception)
                    .filter(Objects::nonNull).distinct().count();

            var r2 = sheet.createRow(2);
            setStr(r2, 0, "Date export :", metaKeyStyle);
            setStr(r2, 1, LocalDate.now().format(FMT), metaValStyle);
            setStr(r2, 3, "Devise :",  metaKeyStyle);
            setStr(r2, 4, devise, metaValStyle);

            var r3 = sheet.createRow(3);
            setStr(r3, 0, "Fournisseur :", metaKeyStyle);
            sheet.addMergedRegion(new CellRangeAddress(3, 3, 1, 6));
            setStr(r3, 1,
                    frsCode + (frsNom.isBlank() ? "" : " – " + frsNom) +
                            "   Réceptions : " + nbRcno, metaValStyle);

            // ── Ligne 6 : En-têtes ───────────────────────────────────────────
            String[] cols = {
                    "Date Réc.", "N° Réc.", "LG", "N° Fact.", "N° Ship.",
                    "Article", "Description", "Qté Cdée", "Qté Reçue",
                    "Emplacement", "Devise", "P.U.", "Valeur"
            };
            var hRow = sheet.createRow(5);
            hRow.setHeightInPoints(16);
            for (int i = 0; i < cols.length; i++) setStr(hRow, i, cols[i], headerStyle);

            // ── Données groupées par numéro de réception ─────────────────────
            Map<String, List<ReceptionLineDTO>> grouped = lines.stream()
                    .collect(Collectors.groupingBy(
                            l -> Optional.ofNullable(l.getNumeroReception()).orElse("—"),
                            LinkedHashMap::new,
                            Collectors.toList()));

            int    rowIdx    = 6;
            BigDecimal gCd   = BigDecimal.ZERO;
            BigDecimal gRec  = BigDecimal.ZERO;
            BigDecimal gVal  = BigDecimal.ZERO;

            for (var entry : grouped.entrySet()) {
                String rcno = entry.getKey();
                var    grp  = entry.getValue();

                // Label ORNO
                var ornoRow = sheet.createRow(rowIdx);
                sheet.addMergedRegion(new CellRangeAddress(rowIdx, rowIdx, 0, 12));
                setStr(ornoRow, 0, "ORNO: " + rcno, ornoStyle);
                rowIdx++;

                BigDecimal subCd  = BigDecimal.ZERO;
                BigDecimal subRec = BigDecimal.ZERO;
                BigDecimal subVal = BigDecimal.ZERO;
                boolean    odd    = false;

                for (var l : grp) {
                    var row = sheet.createRow(rowIdx++);
                    var ds  = odd ? dataOdd : dataStyle;
                    var ns  = odd ? numOdd  : numStyle;
                    odd = !odd;

                    setStr(row, 0, l.getDateReception(), ds);
                    setStr(row, 1, l.getNumeroReception(), ds);
                    setStr(row, 2, l.getLg(), ds);
                    setStr(row, 3, l.getDaeFact(), ds);
                    setStr(row, 4, l.getDino(), ds);
                    setStr(row, 5, l.getArticle(), ds);
                    setStr(row, 6, l.getDescription(), ds);
                    setNum(row, 7, l.getQteCdee(), ns);
                    setNum(row, 8, l.getQteRecue(), ns);
                    setStr(row, 9, l.getEmplacement(), ds);
                    setStr(row, 10, l.getDevise(), ds);
                    setNum(row, 11, l.getPrixUnitaire(), ns);
                    BigDecimal val = l.getValeurTotale() != null ? l.getValeurTotale() : BigDecimal.ZERO;
                    setNum(row, 12, val, ns);

                    subCd  = subCd.add(nvl(l.getQteCdee()));
                    subRec = subRec.add(nvl(l.getQteRecue()));
                    subVal = subVal.add(val);
                }

                // Sous-total
                var subRow = sheet.createRow(rowIdx++);
                sheet.addMergedRegion(new CellRangeAddress(rowIdx - 1, rowIdx - 1, 0, 6));
                setStr(subRow, 0, "Sous-total " + rcno, subStyle);
                setNum(subRow, 7, subCd,  subStyle);
                setNum(subRow, 8, subRec, subStyle);
                setStr(subRow, 9,  "", subStyle);
                setStr(subRow, 10, "", subStyle);
                setStr(subRow, 11, "", subStyle);
                setNum(subRow, 12, subVal, subStyle);

                gCd  = gCd.add(subCd);
                gRec = gRec.add(subRec);
                gVal = gVal.add(subVal);
                rowIdx++; // ligne vide
            }

            // ── Total général ────────────────────────────────────────────────
            var gt = sheet.createRow(rowIdx);
            gt.setHeightInPoints(16);
            sheet.addMergedRegion(new CellRangeAddress(rowIdx, rowIdx, 0, 6));
            setStr(gt, 0, "TOTAL GÉNÉRAL", totalStyle);
            setNum(gt, 7, gCd,  totalStyle);
            setNum(gt, 8, gRec, totalStyle);
            setStr(gt, 9,  "", totalStyle);
            setStr(gt, 10, "", totalStyle);
            setStr(gt, 11, "", totalStyle);
            setNum(gt, 12, gVal, totalStyle);

            wb.write(bos);
            log.info("[ExcelBuilder] OK — {} lignes, {} réceptions", lines.size(), grouped.size());

        } catch (Exception e) {
            log.error("[ExcelBuilder] Erreur", e);
            throw new RuntimeException("Erreur génération Excel : " + e.getMessage(), e);
        }
        return bos.toByteArray();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Helpers cellules
    // ══════════════════════════════════════════════════════════════════════════

    private static void setStr(Row row, int col, String val, CellStyle style) {
        var c = row.createCell(col);
        c.setCellValue(val != null ? val : "");
        if (style != null) c.setCellStyle(style);
    }

    private static void setNum(Row row, int col, BigDecimal val, CellStyle style) {
        var c = row.createCell(col, CellType.NUMERIC);
        c.setCellValue(val != null ? val.doubleValue() : 0.0);
        if (style != null) c.setCellStyle(style);
    }

    private static BigDecimal nvl(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }

    // ══════════════════════════════════════════════════════════════════════════
    // Builders de styles — RÈGLE CRITIQUE POI :
    //   Pour les couleurs personnalisées (hex), utiliser XSSFFont et XSSFColor.
    //   Ne JAMAIS appeler font.setColor(short) avec une couleur IndexedColors
    //   et ensuite essayer de setter une XSSFColor — incompatible.
    //   Pattern correct : créer XSSFFont via (XSSFFont) wb.createFont()
    //   puis appeler xfont.setColor(new XSSFColor(bytes, null)).
    // ══════════════════════════════════════════════════════════════════════════

    private static CellStyle buildTitleStyle(XSSFWorkbook wb) {
        var xfont = (XSSFFont) wb.createFont();
        xfont.setBold(true);
        xfont.setFontHeightInPoints((short) 14);
        xfont.setColor(new XSSFColor(new byte[]{(byte)255,(byte)255,(byte)255}, null));

        var s = wb.createCellStyle();
        s.setFont(xfont);
        s.setFillForegroundColor(new XSSFColor(C_HEADER, null));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        return s;
    }

    private static CellStyle buildMetaKeyStyle(XSSFWorkbook wb) {
        var f = wb.createFont();
        f.setBold(true);
        f.setFontHeightInPoints((short) 9);
        var s = wb.createCellStyle();
        s.setFont(f);
        return s;
    }

    private static CellStyle buildMetaValStyle(XSSFWorkbook wb) {
        var f = wb.createFont();
        f.setFontHeightInPoints((short) 9);
        var s = wb.createCellStyle();
        s.setFont(f);
        return s;
    }

    private static CellStyle buildHeaderStyle(XSSFWorkbook wb) {
        var xfont = (XSSFFont) wb.createFont();
        xfont.setBold(true);
        xfont.setFontHeightInPoints((short) 9);
        xfont.setColor(new XSSFColor(new byte[]{(byte)255,(byte)255,(byte)255}, null));

        var s = wb.createCellStyle();
        s.setFont(xfont);
        s.setFillForegroundColor(new XSSFColor(C_HEADER, null));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
        return s;
    }

    private static CellStyle buildOrnoStyle(XSSFWorkbook wb) {
        var xfont = (XSSFFont) wb.createFont();
        xfont.setBold(true);
        xfont.setFontHeightInPoints((short) 9);
        xfont.setColor(new XSSFColor(C_HEADER, null));

        var s = wb.createCellStyle();
        s.setFont(xfont);
        s.setFillForegroundColor(new XSSFColor(C_ORNO, null));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return s;
    }

    private static CellStyle buildDataStyle(XSSFWorkbook wb, boolean odd) {
        var f = wb.createFont();
        f.setFontHeightInPoints((short) 9);
        var s = wb.createCellStyle();
        s.setFont(f);
        if (odd) {
            s.setFillForegroundColor(new XSSFColor(C_ODD, null));
            s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        s.setBorderBottom(BorderStyle.HAIR);
        s.setBorderTop(BorderStyle.HAIR);
        s.setBorderLeft(BorderStyle.HAIR);
        s.setBorderRight(BorderStyle.HAIR);
        return s;
    }

    private static CellStyle buildNumStyle(XSSFWorkbook wb, boolean odd) {
        var s = (XSSFCellStyle) buildDataStyle(wb, odd);
        s.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));
        s.setAlignment(HorizontalAlignment.RIGHT);
        return s;
    }

    private static CellStyle buildSubtotalStyle(XSSFWorkbook wb) {
        var xfont = (XSSFFont) wb.createFont();
        xfont.setBold(true);
        xfont.setFontHeightInPoints((short) 9);
        xfont.setColor(new XSSFColor(C_HEADER, null));

        var s = wb.createCellStyle();
        s.setFont(xfont);
        s.setFillForegroundColor(new XSSFColor(C_SUBTOT, null));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));
        s.setAlignment(HorizontalAlignment.RIGHT);
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderBottom(BorderStyle.THIN);
        return s;
    }

    private static CellStyle buildTotalStyle(XSSFWorkbook wb) {
        var xfont = (XSSFFont) wb.createFont();
        xfont.setBold(true);
        xfont.setFontHeightInPoints((short) 11);
        xfont.setColor(new XSSFColor(new byte[]{(byte)255,(byte)255,(byte)255}, null));

        var s = wb.createCellStyle();
        s.setFont(xfont);
        s.setFillForegroundColor(new XSSFColor(C_HEADER, null));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));
        s.setAlignment(HorizontalAlignment.RIGHT);
        s.setBorderTop(BorderStyle.MEDIUM);
        return s;
    }
}