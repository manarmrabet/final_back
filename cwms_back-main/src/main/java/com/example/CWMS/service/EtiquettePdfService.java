package com.example.CWMS.service;

import com.example.CWMS.dto.EtiquetteDTO;
import com.example.CWMS.repository.erp.EtiquetteRepository;
import com.itextpdf.barcodes.Barcode128;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.properties.TextAlignment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EtiquettePdfService {

    private final EtiquetteRepository repo;

    private static final float W = 340f;
    private static final float H = 210f;

    private static final DeviceRgb BLUE_DARK = new DeviceRgb(0, 51, 102);
    private static final DeviceRgb GREEN     = new DeviceRgb(0, 153, 51);
    private static final DeviceRgb YELLOW    = new DeviceRgb(255, 204, 0);
    private static final DeviceRgb WHITE     = new DeviceRgb(255, 255, 255);
    private static final DeviceRgb BLACK     = new DeviceRgb(0, 0, 0);
    private static final DeviceRgb GRAY      = new DeviceRgb(80, 80, 80);

    public byte[] generate(String orderEtiquette, int start, int end, String username) {
        List<EtiquetteDTO> all = repo.findByOrderNumber(orderEtiquette);

        if (all.isEmpty()) {
            throw new RuntimeException("Aucune étiquette trouvée pour RCNO : " + orderEtiquette);
        }

        List<EtiquetteDTO> subset = (start == 0 && end == 0)
                ? all
                : all.subList(Math.max(0, start - 1), Math.min(end, all.size()));

        byte[] pdf = buildPdf(subset);
        repo.markAsPrinted(orderEtiquette);

        log.info("[Etiquette] {} étiquette(s) générée(s) | RCNO={}", subset.size(), orderEtiquette);
        return pdf;
    }

    private byte[] buildPdf(List<EtiquetteDTO> list) {
        var bos = new ByteArrayOutputStream();

        try (var pdfDoc = new PdfDocument(new PdfWriter(bos))) {
            for (int i = 0; i < list.size(); i++) {
                pdfDoc.addNewPage(new PageSize(W, H));
            }

            var doc = new Document(pdfDoc);
            doc.setMargins(0, 0, 0, 0);

            for (int i = 0; i < list.size(); i++) {
                drawLabel(pdfDoc, doc, list.get(i), i + 1);
            }
        } catch (Exception e) {
            log.error("[Etiquette] Erreur PDF", e);
            throw new RuntimeException("Erreur génération PDF : " + e.getMessage(), e);
        }
        return bos.toByteArray();
    }

    private void drawLabel(PdfDocument pdfDoc, Document doc, EtiquetteDTO e, int pageNumber) {
        var cv = new PdfCanvas(pdfDoc.getPage(pageNumber));

        // ── Fond blanc total ──────────────────────────────────────────────
        cv.setFillColor(WHITE).rectangle(0, 0, W, H).fill();

        // ── 1. BANDEAU SUPÉRIEUR ─────────────────────────────────────────
        float headerH      = 28f;
        float headerBottom = H - headerH;  // y = 182

        // Carré VERT semaine (coin haut-droit)
        float weekW = 45f; // Ajusté pour correspondre au ratio de l'image 2
        cv.setFillColor(GREEN).rectangle(W - weekW, headerBottom, weekW, headerH).fill();

        // Texte "COFAT TUNIS"
        txt(doc, pageNumber, s(e.getCompany()), 11f, 6, headerBottom + 10, 160, true, BLACK, TextAlignment.LEFT);

        // Texte "Class Rotation : X"
        txt(doc, pageNumber, "Class Rotation : " + s(e.getRotationClass()), 7.5f, 140, headerBottom + 12, 105, false, BLACK, TextAlignment.CENTER);

        // Texte semaine (ex: 8/26) dans le carré vert
        txt(doc, pageNumber, s(e.getWeekIncoming()), 10f, W - weekW, headerBottom + 9, weekW, true, BLACK, TextAlignment.CENTER);

        // ── 2. CODE ARTICLE (grand texte) ────────────────────────────────
        float itemTextBottom = headerBottom - 25f;
        txt(doc, pageNumber, s(e.getItem()), 22f, 6, itemTextBottom, 200, true, BLACK, TextAlignment.LEFT);

        // ── 3. ZONE JAUNE (Positionnée selon l'image 2) ──────────────────
        float yellowX      = 200f;  // Commence plus à gauche pour être plus large
        float yellowBottom = 95f;   // S'arrête plus haut pour laisser place au 2ème barcode
        float yellowTop    = headerBottom;
        cv.setFillColor(YELLOW).rectangle(yellowX, yellowBottom, W - yellowX - 2, yellowTop - yellowBottom).fill();

        // ── 4. BARCODE ARTICLE (côté gauche) ─────────────────────────────
        float bcArticleTop = itemTextBottom - 5f;
        float bcArticleH   = 35f;
        float bcArticleW   = yellowX - 15f; // S'arrête juste avant la zone jaune

        addBarcode(pdfDoc, cv, doc, pageNumber, s(e.getItem()), 6, bcArticleTop, bcArticleW, bcArticleH);

        // ── 5. ZONE TEXTE INFOS (Alignée à gauche) ───────────────────────
        float infoY1 = 76f;
        float infoY2 = 62f;
        float infoY3 = 48f;

        String desc = s(e.getDescription());
        if (desc.length() > 28) desc = desc.substring(0, 26) + "…";

        txt(doc, pageNumber, "Desc: " + desc,              7.5f, 6,   infoY1, 180, false, BLACK, TextAlignment.LEFT);
        txt(doc, pageNumber, "QTE : " + s(e.getQty()),     8f,   6,   infoY2, 100, true,  BLACK, TextAlignment.LEFT);
        txt(doc, pageNumber, "RFr:" + s(e.getLinkedOrder()), 7.5f, 120, infoY2, 80, false, BLACK, TextAlignment.LEFT);

        txt(doc, pageNumber, "NC: " + s(e.getLocation()),  7.5f, 6,   infoY3, 90, false, BLACK, TextAlignment.LEFT);
        txt(doc, pageNumber, s(e.getMpnr()),               7.5f, 110, infoY3, 90, false, BLACK, TextAlignment.LEFT);
        txt(doc, pageNumber, "DATE: " + s(e.getDateLabel()), 7.5f, 6, 34f, 130, false, BLACK, TextAlignment.LEFT);

        // ── 6. GRAND BARCODE (Bas-Droit, aligné sous le jaune) ───────────
        if (!s(e.getLabelNumber()).isBlank()) {
            float bigBcX   = 200f; // Aligné sur le début de la zone jaune
            float bigBcTop = 55f;
            float bigBcW   = W - bigBcX - 10f;
            float bigBcH   = 32f;
            addBarcode(pdfDoc, cv, doc, pageNumber, s(e.getLabelNumber()), bigBcX, bigBcTop, bigBcW, bigBcH);
        }
    }
    private void addBarcode(PdfDocument pdfDoc, PdfCanvas cv, Document doc, int pageNumber,
                            String code, float x, float yTop, float maxW, float barH) {
        if (code == null || code.isBlank()) return;

        String clean = code.trim().replaceAll("[^A-Z0-9a-z \\-./]+", "").toUpperCase();
        if (clean.isBlank()) return;

        try {
            var bc = new Barcode128(pdfDoc);
            bc.setCode(clean);
            bc.setFont(null);
            bc.setBarHeight(Math.max(barH - 4, 18f));
            bc.setStartStopText(false);

            var xo = bc.createFormXObject(BLACK, BLACK, pdfDoc);
            float yBottom = yTop - Math.max(barH, 18f);
            if (yBottom < 8) yBottom = 8;

            cv.addXObjectFittedIntoRectangle(xo, new Rectangle(x, yBottom, maxW, Math.max(barH, 18f)));

            // ← Texte sous barcode PLUS GRAND : 10f bold pour meilleure lisibilité
            txt(doc, pageNumber, clean, 10f, x, yBottom - 13, maxW, true, BLACK, TextAlignment.CENTER);
        } catch (Exception ex) {
            log.warn("[Etiquette] Erreur barcode '{}'", code);
        }
    }

    private void txt(Document doc, int pageNumber, String text, float size, float x, float y, float w,
                     boolean bold, DeviceRgb color, TextAlignment align) {
        if (text == null || text.isBlank()) return;
        var p = new Paragraph(text)
                .setFontSize(size)
                .setTextAlignment(align)
                .setMultipliedLeading(1.0f);
        if (bold) p.setBold();
        if (color != null) p.setFontColor(color);
        doc.add(p.setFixedPosition(pageNumber, x, y, w));
    }

    private String s(String v) {
        return v != null ? v.trim() : "";
    }
}