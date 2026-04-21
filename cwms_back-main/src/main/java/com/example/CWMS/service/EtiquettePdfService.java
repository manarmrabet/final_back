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
        float headerH      = 30f;
        float headerBottom = H - headerH;   // y = 178

        // ✅ Carré VERT — coin haut-droit uniquement
        float weekW = 65f;
        cv.setFillColor(GREEN).rectangle(W - weekW, headerBottom, weekW, headerH).fill();

        // ✅ "COFAT TUNIS" — noir, gras, haut-gauche
        txt(doc, pageNumber, s(e.getCompany()), 12f, 6, headerBottom + 12, 165, true, BLACK, TextAlignment.LEFT);

        // ✅ "Class Rotation : X" — centré, petite taille
        txt(doc, pageNumber, "Class Rotation : " + s(e.getRotationClass()), 7.5f, 155, headerBottom + 14, 110, false, BLACK, TextAlignment.CENTER);

        // ✅ Semaine dans le vert — NOIR gras
        txt(doc, pageNumber, s(e.getWeekIncoming()), 13f, W - weekW + 2, headerBottom + 10, weekW - 4, true, BLACK, TextAlignment.CENTER);

        // ── Ligne séparatrice sous header ────────────────────────────────
        cv.setStrokeColor(GRAY).setLineWidth(0.4f)
                .moveTo(0, headerBottom).lineTo(W, headerBottom).stroke();

        // ── 2. CODE ARTICLE (grand texte) ────────────────────────────────
        float itemTextBottom = headerBottom - 24f;   // y ≈ 152
        txt(doc, pageNumber, s(e.getItem()), 20f, 6, itemTextBottom, 200, true, BLACK, TextAlignment.LEFT);

        // ── 3. ZONE JAUNE — PLUS PETITE, espace blanc visible à gauche ───
        float yellowX      = 260f;   // démarre après le barcode + gap blanc
        float yellowBottom = 100f;
        float yellowTop    = headerBottom;
        float yellowW      = 500f;    // largeur
        cv.setFillColor(YELLOW).rectangle(yellowX, yellowBottom, yellowW, yellowTop - yellowBottom).fill();

        // ── 4. BARCODE ARTICLE — PLUS PETIT, à gauche avec gap ───────────
        float bcArticleTop = itemTextBottom - 4f;
        float bcArticleH   = 26f;                    // ✅ réduit
        float bcArticleW   = 200f;          // ✅ espace blanc entre barcode et jaune

        addBarcode(pdfDoc, cv, doc, pageNumber, s(e.getItem()), 6, bcArticleTop, bcArticleW, bcArticleH);

        // ── 5. ZONE TEXTE INFOS ───────────────────────────────────────────
        float infoY1 = 70f;
        float infoY2 = 56f;
        float infoY3 = 42f;

        String desc = s(e.getDescription());
        if (desc.length() > 28) desc = desc.substring(0, 26) + "…";

        txt(doc, pageNumber, "Desc: " + desc,                7.5f, 6,   infoY1, 180, false, BLACK, TextAlignment.LEFT);
        txt(doc, pageNumber, "QTE : " + s(e.getQty()),       8f,   6,   infoY2, 100, true,  BLACK, TextAlignment.LEFT);
        txt(doc, pageNumber, "RFr:" + s(e.getLinkedOrder()), 7.5f, 120, infoY2, 90,  false, BLACK, TextAlignment.LEFT);
        txt(doc, pageNumber, "NC: " + s(e.getLocation()),    7.5f, 6,   infoY3, 90,  false, BLACK, TextAlignment.LEFT);
        txt(doc, pageNumber, s(e.getMpnr()),                 7.5f, 110, infoY3, 90,  false, BLACK, TextAlignment.LEFT);
        txt(doc, pageNumber, "DATE: " + s(e.getDateLabel()), 7.5f, 6,   32f,   130,  false, BLACK, TextAlignment.LEFT);

        // ── 6. GRAND BARCODE LOT — bas droite ────────────────────────────
        if (!s(e.getLabelNumber()).isBlank()) {
            float bigBcX   =yellowX;
            float bigBcTop = 80f;
            float bigBcW   = W -  yellowX - 6f;
            float bigBcH   = 28f;
            addBarcode(pdfDoc, cv, doc, pageNumber, s(e.getLabelNumber()), bigBcX, bigBcTop, bigBcW, bigBcH);
        }

        // ── 7. BARRE NOIRE EN BAS ────────────────────────────────────────
        cv.setFillColor(BLACK).rectangle(0, 0, W, 12f).fill();
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
            bc.setBarHeight(Math.max(barH - 4, 14f));
            bc.setStartStopText(false);

            var xo = bc.createFormXObject(BLACK, BLACK, pdfDoc);
            float yBottom = yTop - Math.max(barH, 14f);
            if (yBottom < 12) yBottom = 12;

            cv.addXObjectFittedIntoRectangle(xo, new Rectangle(x, yBottom, maxW, Math.max(barH, 14f)));

            // ✅ Texte sous barcode — grand, gras, bien lisible
            txt(doc, pageNumber, clean, 10f, x, yBottom - 14, maxW, true, BLACK, TextAlignment.CENTER);
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