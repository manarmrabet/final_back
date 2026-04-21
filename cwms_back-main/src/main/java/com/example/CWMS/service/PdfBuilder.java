package com.example.CWMS.service;

import com.example.CWMS.dto.ReceptionLineDTO;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.properties.VerticalAlignment;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Génère les PDF de bons de réception (standard ou valorisé).
 * Classe utilitaire — méthodes statiques uniquement.
 */
@Slf4j
public final class PdfBuilder {

    // Couleurs COFAT GROUP
    private static final DeviceRgb COLOR_HEADER  = new DeviceRgb(26,  58,  107); // bleu foncé
    private static final DeviceRgb COLOR_SUBTOT  = new DeviceRgb(210, 225, 245); // bleu clair
    private static final DeviceRgb COLOR_WHITE   = new DeviceRgb(255, 255, 255);

    private static final DateTimeFormatter FMT_DISPLAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private PdfBuilder() { /* classe utilitaire */ }

    /**
     * @param lines    lignes de réception à inclure
     * @param orderRef numéro de commande ou référence
     * @param valued   true = inclure prix unitaires et valorisation
     * @return bytes du PDF généré
     */
    public static byte[] build(List<ReceptionLineDTO> lines, String orderRef, boolean valued) {
        var bos = new ByteArrayOutputStream();
        try {
            var pdf = new PdfDocument(new PdfWriter(bos));
            var doc = new Document(pdf, PageSize.A4.rotate());
            doc.setMargins(20, 20, 20, 20);

            // ── Titre ─────────────────────────────────────────────────────────
            doc.add(new Paragraph("BONS DE RÉCEPTION" + (valued ? "  —  VALORISÉ" : ""))
                    .setFontSize(14).setBold()
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontColor(COLOR_HEADER)
                    .setMarginBottom(4));

            // ── Méta ──────────────────────────────────────────────────────────
            if (!lines.isEmpty()) {
                ReceptionLineDTO first = lines.get(0);
                doc.add(new Paragraph(
                        "Commande : " + orderRef +
                                "   |   Fournisseur : " + first.getFournisseur() + " – " + first.getDescFrs() +
                                "   |   Devise : " + first.getDevise() +
                                "   |   Export : " + LocalDate.now().format(FMT_DISPLAY))
                        .setFontSize(8)
                        .setFontColor(ColorConstants.GRAY)
                        .setMarginBottom(8));
            }

            // ── Regroupement par numéro de réception ──────────────────────────
            Map<String, List<ReceptionLineDTO>> grouped = lines.stream()
                    .collect(Collectors.groupingBy(
                            l -> Optional.ofNullable(l.getNumeroReception()).orElse("—"),
                            LinkedHashMap::new,
                            Collectors.toList()));

            BigDecimal grandQteCd  = BigDecimal.ZERO;
            BigDecimal grandQteRec = BigDecimal.ZERO;
            BigDecimal grandValeur = BigDecimal.ZERO;

            for (var entry : grouped.entrySet()) {
                String rcno        = entry.getKey();
                List<ReceptionLineDTO> grp = entry.getValue();

                // Label ORNO
                doc.add(new Paragraph("ORNO: " + rcno)
                        .setFontSize(9).setBold().setFontColor(COLOR_HEADER).setMarginTop(6));

                // Colonnes
                float[] widths = valued
                        ? new float[]{60, 70, 45, 45, 85, 120, 50, 50, 65, 35, 55, 65}
                        : new float[]{60, 70, 45, 45, 85, 120, 50, 50, 65, 35};

                String[] headers = valued
                        ? new String[]{"Date", "N° Réc.", "Fact.", "Ship.", "Article", "Description",
                        "Qté Cd.", "Qté Réc.", "Empl.", "Dev.", "P.U.", "Valeur"}
                        : new String[]{"Date", "N° Réc.", "Fact.", "Ship.", "Article", "Description",
                        "Qté Cd.", "Qté Réc.", "Empl.", "Dev."};

                var table = new Table(UnitValue.createPointArray(widths)).useAllAvailableWidth();

                for (String h : headers)
                    table.addHeaderCell(headerCell(h));

                BigDecimal subQteCd  = BigDecimal.ZERO;
                BigDecimal subQteRec = BigDecimal.ZERO;
                BigDecimal subValeur = BigDecimal.ZERO;

                for (var l : grp) {
                    table.addCell(dataCell(l.getDateReception()));
                    table.addCell(dataCell(l.getNumeroReception()));
                    table.addCell(dataCell(l.getDaeFact()));
                    table.addCell(dataCell(l.getDino()));
                    table.addCell(dataCell(l.getArticle()));
                    table.addCell(dataCell(l.getDescription()));
                    table.addCell(numCell(l.getQteCdee()));
                    table.addCell(numCell(l.getQteRecue()));
                    table.addCell(dataCell(l.getEmplacement()));
                    table.addCell(dataCell(l.getDevise()));
                    if (valued) {
                        table.addCell(numCell(l.getPrixUnitaire()));
                        BigDecimal val = l.getValeurTotale() != null
                                ? l.getValeurTotale() : BigDecimal.ZERO;
                        table.addCell(numCell(val));
                        subValeur = subValeur.add(val);
                    }
                    subQteCd  = subQteCd.add(nvl(l.getQteCdee()));
                    subQteRec = subQteRec.add(nvl(l.getQteRecue()));
                }

                // Ligne sous-total
                int emptyCols = headers.length - (valued ? 4 : 2);
                for (int i = 0; i < emptyCols; i++)
                    table.addCell(subtotalCell(""));
                table.addCell(subtotalCell("Sous-total " + rcno));
                table.addCell(subtotalCell(fmt(subQteCd)));
                table.addCell(subtotalCell(fmt(subQteRec)));
                if (valued)
                    table.addCell(subtotalCell(fmt(subValeur)));

                doc.add(table);

                grandQteCd  = grandQteCd.add(subQteCd);
                grandQteRec = grandQteRec.add(subQteRec);
                grandValeur = grandValeur.add(subValeur);
            }

            // ── Total général ─────────────────────────────────────────────────
            String totalLine = "TOTAL GÉNÉRAL  —  Qté Commandée : " + fmt(grandQteCd) +
                    "   |   Qté Reçue : " + fmt(grandQteRec);
            if (valued) totalLine += "   |   Valeur : " + fmt(grandValeur);
            doc.add(new Paragraph(totalLine)
                    .setFontSize(10).setBold().setFontColor(COLOR_HEADER).setMarginTop(12));

            // ── Zone signatures ───────────────────────────────────────────────
            doc.add(new Paragraph("\n"));
            var sigTable = new Table(3).useAllAvailableWidth().setMarginTop(20);
            for (String label : new String[]{"REQUISITEUR", "TECH RECEIVING", "Receiving Manager"}) {
                sigTable.addCell(new Cell()
                        .add(new Paragraph(label).setFontSize(9).setBold()
                                .setTextAlignment(TextAlignment.CENTER))
                        .setHeight(55)
                        .setVerticalAlignment(VerticalAlignment.TOP));
            }
            doc.add(sigTable);

            doc.close();
            log.info("[PdfBuilder] PDF généré — {} lignes, {} réceptions, valorisé={}",
                    lines.size(), grouped.size(), valued);

        } catch (Exception e) {
            log.error("[PdfBuilder] Erreur génération PDF", e);
            throw new RuntimeException("Erreur lors de la génération du PDF : " + e.getMessage(), e);
        }
        return bos.toByteArray();
    }

    // ── Cellules ──────────────────────────────────────────────────────────────
    private static Cell headerCell(String text) {
        return new Cell()
                .add(new Paragraph(text).setFontSize(8).setBold()
                        .setFontColor(COLOR_WHITE))
                .setBackgroundColor(COLOR_HEADER)
                .setPadding(3);
    }

    private static Cell dataCell(String text) {
        return new Cell()
                .add(new Paragraph(text != null ? text : "").setFontSize(8))
                .setPadding(2);
    }

    private static Cell numCell(BigDecimal value) {
        return new Cell()
                .add(new Paragraph(fmt(value)).setFontSize(8)
                        .setTextAlignment(TextAlignment.RIGHT))
                .setPadding(2);
    }

    private static Cell subtotalCell(String text) {
        return new Cell()
                .add(new Paragraph(text).setFontSize(8).setBold()
                        .setTextAlignment(TextAlignment.RIGHT))
                .setBackgroundColor(COLOR_SUBTOT)
                .setPadding(2);
    }

    private static String fmt(BigDecimal v) {
        return v == null ? "0.00" : String.format("%,.2f", v);
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}