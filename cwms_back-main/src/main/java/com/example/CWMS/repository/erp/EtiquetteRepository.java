package com.example.CWMS.repository.erp;

import com.example.CWMS.dto.EtiquetteDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Slf4j
@Repository
@RequiredArgsConstructor
public class EtiquetteRepository {

    @Qualifier("erpJdbcTemplate")
    private final JdbcTemplate erpJdbc;

    /**
     * ═══════════════════════════════════════════════════════════════════
     *  MAPPING RÉEL des colonnes (vérifié sur vos screenshots SQL)
     * ═══════════════════════════════════════════════════════════════════
     *
     *  twhinh312310 (Image 2) — Table principale réception
     *    ✅ t_rcno   → numéro réception
     *    ✅ t_rcln   → ligne réception
     *    ✅ t_oorg   → organisation (filtre = 80)
     *    ✅ t_sfbp   → code fournisseur
     *    ✅ t_item   → code article
     *    ✅ t_clot   → numéro de lot / label number
     *    ✅ t_qrec   → quantité reçue
     *    ✅ t_serl   → numéro de série
     *    ✅ t_cmnf   → commentaire
     *    ✅ t_mpnr   → manufacturer part number
     *    ❌ t_idat   → ABSENT → remplacé par t_ldat de twhltc100310
     *    ❌ t_crdt   → ABSENT → remplacé par t_frdt de twhltc100310
     *
     *  ttcibd001120 (Image 4) — Table articles
     *    ✅ t_item   → code article (clé de jointure)
     *    ✅ t_dsca   → description principale
     *    ✅ t_dscb   → description secondaire (backup si t_dsca null)
     *    ✅ t_cwun   → unité de mesure
     *
     *  twhltc100310 (Image 3) — Table lot/étiquette
     *    ✅ t_item   → code article (clé de jointure 1)
     *    ✅ t_clot   → numéro lot   (clé de jointure 2)
     *    ✅ t_sel1   → statut impression (UPDATE cible)
     *    ✅ t_ldat   → date lot (utilisée comme ValidityDate)
     *    ✅ t_frdt   → date de fabrication (utilisée comme DateLabel)
     *    ✅ t_quam   → quantité lot
     *    ✅ t_orno   → ordre lié
     *
     *  twhwmd400310 (Image 1) — Table emplacements
     *    ✅ t_item   → code article (clé de jointure)
     *    ✅ t_locc   → code emplacement ← t_clan était absent, on prend t_locc
     *    ✅ t_seak   → stock EAK
     *    ✅ t_pkdf   → picking default
     * ═══════════════════════════════════════════════════════════════════
     */
    private static final String SQL_SELECT = """
        SELECT
            h.t_rcno,
            h.t_rcln,
            h.t_sfbp,
            'COFAT TUNIS'                                       AS company,
            'A'                                                 AS rotation_class,
            h.t_item                                            AS item,

            -- Description : t_dsca en priorité, t_dscb en fallback, code article en dernier
            ISNULL(NULLIF(LTRIM(RTRIM(i.t_dsca)),''),
                   ISNULL(NULLIF(LTRIM(RTRIM(i.t_dscb)),''), h.t_item))
                                                                AS description,

            -- ValidityDate : t_ldat de twhltc100310 (date lot) — t_idat absent de twhinh312310
            ISNULL(CONVERT(VARCHAR(10), l.t_ldat, 103), '-')   AS validity_date,

            -- Quantité reçue depuis la table principale
            ISNULL(CAST(h.t_qrec AS VARCHAR(20)), '0')         AS qty,

            -- Numéro de lot — t_clot présent dans les deux tables, on prend twhinh312310
            ISNULL(CAST(h.t_clot AS VARCHAR(50)), '')           AS label_number,

            -- DateLabel : t_frdt (date fabrication) depuis twhltc100310
            ISNULL(CONVERT(VARCHAR(10), l.t_frdt, 103), '-')   AS date_label,

            -- Emplacement : t_locc depuis twhwmd400310 (t_clan inexistant dans cette table)
            ISNULL(NULLIF(LTRIM(RTRIM(w.t_locc)),''), '-')     AS location,

            -- Semaine d'entrée calculée depuis t_ldat
            ISNULL(
                CAST(DATEPART(WEEK, l.t_ldat) AS VARCHAR(3)) + '/'
                + CAST(DATEPART(YEAR, l.t_ldat) AS VARCHAR(4)),
                '-'
            )                                                   AS week_incoming,

            -- Manufacturer Part Number (info complémentaire sur l'étiquette)
            ISNULL(h.t_mpnr, '')                                AS mpnr

        FROM dbo.twhinh312310 h

        -- Jointure articles : t_item = t_item (ttcibd001120 confirmé image 4)
        LEFT JOIN dbo.ttcibd001120 i
               ON h.t_item = i.t_item

        -- Jointure lot : double clé t_item + t_clot pour cibler le bon lot
        LEFT JOIN dbo.twhltc100310 l
               ON h.t_item = l.t_item
              AND h.t_clot  = l.t_clot

        -- Jointure emplacement : t_item comme clé (image 1 confirme t_item présent)
        LEFT JOIN dbo.twhwmd400310 w
               ON h.t_item = w.t_item

        WHERE h.t_rcno = ?
          AND h.t_oorg = 80

        ORDER BY
            TRY_CAST(h.t_rcln AS INT) ASC
        """;

    /**
     * UPDATE twhltc100310 après génération PDF (rapport §6)
     * Marque t_sel1 = '1' pour éviter double impression
     * Jointure sur t_rcno via twhinh312310 car twhltc100310 n'a pas t_rcno direct
     */
    private static final String SQL_MARK_PRINTED = """
        UPDATE dbo.twhltc100310
           SET t_sel1 = '1'
         WHERE t_item IN (
             SELECT DISTINCT t_item
               FROM dbo.twhinh312310
              WHERE t_rcno = ?
                AND t_oorg = 80
         )
           AND t_clot IN (
             SELECT DISTINCT t_clot
               FROM dbo.twhinh312310
              WHERE t_rcno = ?
                AND t_oorg = 80
         )
        """;

    public List<EtiquetteDTO> findByOrderNumber(String rcno) {
        log.info("[EtiquetteRepo] SELECT pour RCNO={}", rcno);
        return erpJdbc.query(
                SQL_SELECT,
                ps -> ps.setString(1, rcno),
                (rs, row) -> {
                    var dto = new EtiquetteDTO();
                    dto.setRcno(rs.getString("t_rcno"));
                    dto.setRcln(rs.getString("t_rcln"));
                    dto.setSfbp(rs.getString("t_sfbp"));
                    dto.setCompany(rs.getString("company"));
                    dto.setRotationClass(rs.getString("rotation_class"));
                    dto.setItem(rs.getString("item"));
                    dto.setDescription(rs.getString("description"));
                    dto.setValidityDate(rs.getString("validity_date"));
                    dto.setQty(rs.getString("qty"));
                    dto.setLabelNumber(rs.getString("label_number"));
                    dto.setDateLabel(rs.getString("date_label"));
                    dto.setLocation(rs.getString("location"));
                    dto.setWeekIncoming(rs.getString("week_incoming"));
                    dto.setMpnr(rs.getString("mpnr"));
                    return dto;
                }
        );
    }

    /**
     * markAsPrinted : passe t_sel1='1' dans twhltc100310
     * Le UPDATE utilise 2x le même RCNO (deux sous-requêtes IN)
     */
    public void markAsPrinted(String rcno) {
        int rows = erpJdbc.update(SQL_MARK_PRINTED, rcno, rcno);
        log.info("[EtiquetteRepo] markAsPrinted RCNO={} → {} lots marqués", rcno, rows);
    }
}