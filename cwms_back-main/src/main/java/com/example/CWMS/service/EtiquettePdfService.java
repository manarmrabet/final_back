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

    // ─────────────────────────────────────────────────────────────────────────
    //  Dimensions : 120 mm × 74 mm → points PDF  (1 mm = 2.8346 pt)
    //  Rapport §4.1 : "340pt × 285pt" — on garde 340 × 210 (74mm réel)
    // ─────────────────────────────────────────────────────────────────────────
    private static final float W = 340f;
    private static final float H = 210f;

    // Couleurs COFAT GROUP (identiques à PdfBuilder existant)
    private static final DeviceRgb BLUE  = new DeviceRgb(26,  58, 107);
    private static final DeviceRgb LGRAY = new DeviceRgb(240, 240, 240);
    private static final DeviceRgb WHITE = new DeviceRgb(255, 255, 255);
    private static final DeviceRgb DKGRAY= new DeviceRgb(100, 100, 100);

    // ─────────────────────────────────────────────────────────────────────────
    //  Point d'entrée — appelé par EtiquetteController
    // ─────────────────────────────────────────────────────────────────────────
    public byte[] generate(String orderEtiquette, int start, int end, String username) {

        List<EtiquetteDTO> all = repo.findByOrderNumber(orderEtiquette);

        if (all.isEmpty())
            throw new RuntimeException("Aucune étiquette trouvée pour RCNO : " + orderEtiquette);

        // Rapport §2.3 : start==0 && end==0 → toutes les étiquettes
        List<EtiquetteDTO> subset = (start == 0 && end == 0)
                ? all
                : all.subList(Math.max(0, start - 1), Math.min(end, all.size()));

        byte[] pdf = buildPdf(subset);

        // Rapport §6 : UPDATE t_sel1='1' après génération pour éviter double impression
        repo.markAsPrinted(orderEtiquette);

        log.info("[Etiquette] {} étiquettes générées | user={} | RCNO={}",
                subset.size(), username, orderEtiquette);
        return pdf;





    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Génération PDF — une page par étiquette
    // ─────────────────────────────────────────────────────────────────────────
    private byte[] buildPdf(List<EtiquetteDTO> list) {
        var bos = new ByteArrayOutputStream();
        var labelSize = new PageSize(W, H);

        try (var pdfDoc = new PdfDocument(new PdfWriter(bos))) {
            var doc = new Document(pdfDoc, labelSize);
            doc.setMargins(0, 0, 0, 0); // marges gérées manuellement dans drawLabel

            for (int i = 0; i < list.size(); i++) {
                if (i > 0) pdfDoc.addNewPage(labelSize);
                drawLabel(pdfDoc, doc, list.get(i), i + 1);
            }
        } catch (Exception e) {
            log.error("[Etiquette] Erreur génération PDF pour RCNO={} | liste taille={}",
                    list.isEmpty() ? "UNKNOWN" : list.get(0).getRcno(),
                    list.size(), e);   // ← ajoute e pour stack trace complète
            throw new RuntimeException("Erreur PDF : " + e.getMessage(), e);
        }
        return bos.toByteArray();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Maquette étiquette — layout selon rapport §4
    //
    //  Coordonnées iText : origine (0,0) = bas-gauche de la page
    //  H = 210pt (hauteur totale)
    //
    //  Zone Y (de haut en bas) :
    //  ┌──────────────────────────────────────────────┐ H=210
    //  │  BANDEAU BLEU : Company | RCNO               │ 210→188
    //  ├──────────────────────────────────────────────┤ 188
    //  │  BANDEAU GRIS : Item | Description           │ 188→168
    //  ├──────────────────────────────────────────────┤ 168
    //  │  Ligne 1 : Supplier | Qty | Validity         │ ~158
    //  │  Ligne 2 : Week     | Class | Date           │ ~146
    //  │  Ligne 3 : MPNR     | Lot                   │ ~134
    //  ├──────────────────────────────────────────────┤
    //  │  BARCODE Article     |  BARCODE LabelNumber  │ 120→85
    //  ├──────────────────────────────────────────────┤ 72
    //  │  Séparateur + Emplacement                    │ 72→55
    //  └──────────────────────────────────────────────┘ 0
    // ─────────────────────────────────────────────────────────────────────────
    private void drawLabel(PdfDocument pdf, Document doc,
                           EtiquetteDTO e, int page) {

        var cv = new PdfCanvas(pdf.getPage(page));

        // ── Bordure extérieure ──────────────────────────────────────────
        cv.setStrokeColor(BLUE).setLineWidth(0.8f)
                .rectangle(2, 2, W - 4, H - 4).stroke();

        // ── 1. Bandeau supérieur BLEU : Company + RCNO ─────────────────
        //    Y = H-22 (bas du bandeau), hauteur = 20
        cv.setFillColor(BLUE)
                .rectangle(2, H - 22, W - 4, 20).fill();

        txt(doc, page, s(e.getCompany()),
                9, 6, H - 19, 160, true, WHITE, TextAlignment.LEFT);
        txt(doc, page, "RCNO: " + s(e.getRcno()),
                8, 170, H - 19, 163, false, WHITE, TextAlignment.RIGHT);

        // ── 2. Bandeau gris clair : Article + Description ───────────────
        //    Y = H-42 (bas), hauteur = 18
        cv.setFillColor(LGRAY)
                .rectangle(2, H - 42, W - 4, 18).fill();

        txt(doc, page, s(e.getItem()),
                8, 6, H - 40, 110, true, BLUE, TextAlignment.LEFT);

        // Description tronquée à 35 chars pour tenir sur la ligne
        String desc = s(e.getDescription());
        if (desc.length() > 35) desc = desc.substring(0, 33) + "…";
        txt(doc, page, desc,
                7, 120, H - 40, 215, false, null, TextAlignment.LEFT);

        // ── 3. Ligne info 1 : Fournisseur | Qty | Validity ─────────────
        txt(doc, page, "Supplier: " + s(e.getSfbp()),
                7, 6, H - 56, 110, false, null, TextAlignment.LEFT);
        txt(doc, page, "Qty: " + s(e.getQty()),
                8, 120, H - 56, 90, true, null, TextAlignment.LEFT);
        txt(doc, page, "Validity: " + s(e.getValidityDate()),
                7, 215, H - 56, 118, false, null, TextAlignment.LEFT);

        // ── 4. Ligne info 2 : Week | Rotation Class | Date Label ───────
        txt(doc, page, "Week In: " + s(e.getWeekIncoming()),
                7, 6, H - 68, 110, false, null, TextAlignment.LEFT);
        txt(doc, page, "Class: " + s(e.getRotationClass()),
                7, 120, H - 68, 90, false, null, TextAlignment.LEFT);
        txt(doc, page, "Date: " + s(e.getDateLabel()),
                7, 215, H - 68, 118, false, null, TextAlignment.LEFT);

        // ── 5. Ligne info 3 : MPNR (Manufacturer Part Number) ──────────
        if (s(e.getMpnr()).length() > 0) {
            txt(doc, page, "MPN: " + s(e.getMpnr()),
                    7, 6, H - 80, 327, false, DKGRAY, TextAlignment.LEFT);
        }

        // ── 6. Séparateur horizontal ────────────────────────────────────
        cv.setStrokeColor(BLUE).setLineWidth(0.4f)
                .moveTo(6, H - 84).lineTo(W - 6, H - 84).stroke();

        // ── 7. Barcodes Code 128 (rapport §4.1) ────────────────────────
        //    Article → gauche  |  Label Number → droite
        //    Zone : y = H-118 à H-88 (hauteur ≈ 28pt)
        addBarcode(pdf, cv, doc, page,
                s(e.getItem()),
                6, H - 88, 155, 28);                 // x, yTop, w, h

        if (!s(e.getLabelNumber()).isBlank()) {
            addBarcode(pdf, cv, doc, page,
                    s(e.getLabelNumber()),
                    172, H - 88, 161, 28);
        }

        // ── 8. Séparateur bas ───────────────────────────────────────────
        cv.setStrokeColor(BLUE).setLineWidth(0.4f)
                .moveTo(6, H - 124).lineTo(W - 6, H - 124).stroke();

        // ── 9. Emplacement (pied d'étiquette) ───────────────────────────
        cv.setFillColor(LGRAY)
                .rectangle(2, 3, W - 4, 14).fill();
        txt(doc, page, "Location: " + s(e.getLocation()),
                7, 6, 5, 327, false, BLUE, TextAlignment.LEFT);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Barcode128 helper
    //  x, yTop = coin haut-gauche du barcode (on convertit en coords iText)
    // ─────────────────────────────────────────────────────────────────────────
    private void addBarcode(PdfDocument pdf, PdfCanvas cv, Document doc, int page,
                            String code, float x, float yTop, float maxW, float barH) {
        if (code == null || code.isBlank() || code.trim().length() < 1) {
            log.warn("[Etiquette] Barcode ignoré (vide ou blanc) pour page {}", page);
            return;
        }

        String cleanCode = code.trim();

        // Nettoyage agressif : on garde uniquement les caractères sûrs pour Code 128
        cleanCode = cleanCode.replaceAll("[^A-Z0-9 \\-./]+", "");  // A-Z, 0-9, espace, -, ., /

        if (cleanCode.isBlank()) {
            log.warn("[Etiquette] Barcode ignoré après nettoyage (caractères invalides) pour code original='{}' page {}",
                    code, page);
            return;
        }

        try {
            var bc = new Barcode128(pdf);
            bc.setCode(cleanCode);
            bc.setFont(null);           // pas de texte intégré
            bc.setBarHeight(barH);
            bc.setStartStopText(false);
            bc.setExtended(false);      // important pour éviter certains bugs

            var xo = bc.createFormXObject(BLUE, BLUE, pdf);

            float yBottom = yTop - barH;
            if (yBottom < 0) yBottom = 5; // protection contre position hors page

            cv.addXObjectFittedIntoRectangle(xo, new Rectangle(x, yBottom, maxW, barH));

            // Texte lisible en dessous (on utilise le code nettoyé)
            txt(doc, page, cleanCode,
                    5.5f, x, yBottom - 9, maxW, false, DKGRAY, TextAlignment.CENTER);

            log.debug("[Etiquette] Barcode généré avec succès : {}", cleanCode);

        } catch (Exception ex) {
            log.error("[Etiquette] Erreur Barcode pour code original='{}' (nettoyé='{}') page {}",
                    code, cleanCode, page, ex);
            // On ne bloque plus la génération du PDF pour un seul barcode
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helper texte avec positionnement absolu
    // ─────────────────────────────────────────────────────────────────────────
    private void txt(Document doc, int page, String text,
                     float size, float x, float y, float w,
                     boolean bold, DeviceRgb color,
                     TextAlignment align) {
        var p = new Paragraph(text != null ? text : "")
                .setFontSize(size)
                .setTextAlignment(align)
                .setMultipliedLeading(1.0f);  // évite chevauchement inter-lignes
        if (bold)         p.setBold();
        if (color != null) p.setFontColor(color);
        doc.add(p.setFixedPosition(page, x, y, w));
    }

    private String s(String v) { return v != null ? v.trim() : ""; }
}