package com.example.CWMS.service;

import com.example.CWMS.dto.*;
import com.example.CWMS.iservice.ReceptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * ReceptionServiceImpl  —  Version définitive
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * FAITS ÉTABLIS PAR SSMS :
 *  • t_shda (nvarchar) = VIDE  →  la vraie date est t_pddt (datetime)
 *  • t_orno dans ttdpur401330  = "PO0000050"  (format PO...)
 *  • t_rcno (clé jointure)     = "OR0000026"  (format OR...)
 *  • Jointure correcte         : pol.t_rcno = rch.t_rcno  ✓
 *
 * CORRECTION DATE :
 *  On passe java.sql.Date directement en paramètre JDBC — SQL Server compare
 *  nativement un java.sql.Date avec une colonne datetime sans CONVERT.
 * ═══════════════════════════════════════════════════════════════════════════
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReceptionServiceImpl implements ReceptionService {

    @Qualifier("erpNamedJdbc")
    private final NamedParameterJdbcTemplate jdbc;

    private static final DateTimeFormatter FMT_DISPLAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // ── SQL base (lignes de réception complètes) ──────────────────────────
    private static final String SQL_LINES =
            "SELECT " +
                    "    pol.t_sfbp                                         AS fournisseur, " +
                    "    ISNULL(sup.t_nama, pol.t_sfbp)                     AS descFrs, " +
                    "    ISNULL(CAST(pol.t_dino AS NVARCHAR(30)), '')       AS daeFact, " +
                    "    ISNULL(CAST(pol.t_sqnb AS NVARCHAR(10)), '')       AS lg, " +
                    "    pol.t_orno                                         AS oa, " +
                    "    ISNULL(rch.t_cwar, pol.t_cwar)                     AS cwar, " +
                    "    pol.t_item                                         AS article, " +
                    "    ISNULL(itm.t_dsca, pol.t_item)                     AS description, " +
                    "    ISNULL(pol.t_qoor, 0)                              AS qteCdee, " +
                    "    ISNULL(pol.t_qips, 0)                              AS qteRecue, " +
                    "    ISNULL(CAST(rch.t_dino AS NVARCHAR(30)), '')       AS dino, " +
                    "    ISNULL(stk.t_loca, 'RECEIVING')                    AS emplacement, " +
                    "    CONVERT(VARCHAR(10), rch.t_pddt, 103)              AS dateReception, " +
                    "    rch.t_rcno                                         AS numeroReception, " +
                    "    ISNULL(rch.t_curr, ISNULL(pol.t_cupp, 'USD'))      AS devise, " +
                    "    ISNULL(pol.t_pric, 0)                              AS prixUnitaire " +
                    "FROM  [ERP].[dbo].[dbo_ttdpur401330]  pol " +
                    "INNER JOIN [ERP].[dbo].[dbo_twhinh310310]  rch " +
                    "       ON  rch.t_rcno = pol.t_rcno " +
                    "       AND pol.t_rcno IS NOT NULL AND pol.t_rcno <> '' " +
                    "LEFT  JOIN [ERP].[dbo].[dbo_ttccom100310]  sup " +
                    "       ON  sup.t_bpid = pol.t_sfbp " +
                    "LEFT  JOIN [ERP].[dbo].[dbo_ttcibd001120]  itm " +
                    "       ON  itm.t_item = pol.t_item " +
                    "LEFT  JOIN [ERP].[dbo].[dbo_twhinr1401200] stk " +
                    "       ON  stk.t_cwar = ISNULL(rch.t_cwar, pol.t_cwar) " +
                    "       AND stk.t_item = pol.t_item ";

    // ══════════════════════════════════════════════════════════════════════
    // 1. Recherche par numéro de commande (t_orno = "PO0000050")
    // ══════════════════════════════════════════════════════════════════════
    @Override
    public List<ReceptionLineDTO> searchByOrder(String orderNumber) {
        log.info("[Reception] searchByOrder → '{}'", orderNumber);
        return jdbc.query(
                SQL_LINES + "WHERE pol.t_orno = :orno ORDER BY pol.t_sqnb",
                new MapSqlParameterSource("orno", orderNumber.trim()),
                this::mapLine
        );
    }

    // ══════════════════════════════════════════════════════════════════════
    // 2. Recherche par plage de dates
    //    Paramètres reçus : DD/MM/YYYY  →  convertis en java.sql.Date
    //    Filtre sur rch.t_pddt (colonne datetime réelle)
    // ══════════════════════════════════════════════════════════════════════
    @Override
    public List<ReceptionOrderDTO> searchByDateRange(String startDate, String endDate) {
        log.info("[Reception] searchByDateRange → '{}' → '{}'", startDate, endDate);

        java.sql.Date sqlStart = parseSqlDate(startDate, "startDate");
        java.sql.Date sqlEnd   = parseSqlDate(endDate,   "endDate");

        String sql =
                "SELECT " +
                        "    pol.t_orno                                         AS orderNumber, " +
                        "    CONVERT(VARCHAR(10), rch.t_pddt, 103)              AS orderDate, " +
                        "    ISNULL(sup.t_nama, pol.t_sfbp)                     AS supplier, " +
                        "    pol.t_sfbp                                         AS supplierCode, " +
                        "    ISNULL(SUM(pol.t_qips), 0)                         AS totalQty, " +
                        "    ISNULL(rch.t_curr, ISNULL(pol.t_cupp, 'USD'))      AS devise " +
                        "FROM  [ERP].[dbo].[dbo_ttdpur401330]  pol " +
                        "INNER JOIN [ERP].[dbo].[dbo_twhinh310310]  rch " +
                        "       ON  rch.t_rcno = pol.t_rcno " +
                        "       AND pol.t_rcno IS NOT NULL AND pol.t_rcno <> '' " +
                        "LEFT  JOIN [ERP].[dbo].[dbo_ttccom100310]  sup " +
                        "       ON  sup.t_bpid = pol.t_sfbp " +
                        // Comparaison native datetime >= date (pas de CONVERT côté SQL)
                        "WHERE  CAST(rch.t_pddt AS DATE) >= :startDate " +
                        "  AND  CAST(rch.t_pddt AS DATE) <= :endDate " +
                        "GROUP  BY pol.t_orno, rch.t_pddt, sup.t_nama, pol.t_sfbp, rch.t_curr, pol.t_cupp " +
                        "ORDER  BY rch.t_pddt DESC";

        var params = new MapSqlParameterSource()
                .addValue("startDate", sqlStart, java.sql.Types.DATE)
                .addValue("endDate",   sqlEnd,   java.sql.Types.DATE);

        return jdbc.query(sql, params, (rs, i) -> {
            var d = new ReceptionOrderDTO();
            d.setOrderNumber(rs.getString("orderNumber"));
            d.setDate(rs.getString("orderDate"));
            d.setSupplier(rs.getString("supplier"));
            d.setSupplierCode(rs.getString("supplierCode"));
            d.setTotalQty(rs.getBigDecimal("totalQty"));
            d.setDevise(rs.getString("devise"));
            return d;
        });
    }

    // ══════════════════════════════════════════════════════════════════════
    // 3. Détail par numéro de réception (t_rcno = "OR0000026")
    // ══════════════════════════════════════════════════════════════════════
    @Override
    public List<ReceptionLineDTO> getReceptionDetail(String receptionNumber) {
        log.info("[Reception] getReceptionDetail → '{}'", receptionNumber);
        return jdbc.query(
                SQL_LINES + "WHERE rch.t_rcno = :rcno ORDER BY pol.t_sqnb",
                new MapSqlParameterSource("rcno", receptionNumber.trim()),
                this::mapLine
        );
    }

    // ══════════════════════════════════════════════════════════════════════
    // 4. Statistiques
    // ══════════════════════════════════════════════════════════════════════
    @Override
    public ReceptionStatsDTO getStats(String startDate, String endDate) {
        java.sql.Date sqlStart = parseSqlDate(startDate, "startDate");
        java.sql.Date sqlEnd   = parseSqlDate(endDate,   "endDate");

        String sql =
                "SELECT " +
                        "    COUNT(DISTINCT pol.t_orno)  AS totalOrders, " +
                        "    ISNULL(SUM(pol.t_qoor), 0)  AS totalOrdered, " +
                        "    ISNULL(SUM(pol.t_qips), 0)  AS totalReceived, " +
                        "    COUNT(DISTINCT pol.t_sfbp)  AS supplierCount " +
                        "FROM  [ERP].[dbo].[dbo_ttdpur401330]  pol " +
                        "INNER JOIN [ERP].[dbo].[dbo_twhinh310310]  rch " +
                        "       ON  rch.t_rcno = pol.t_rcno " +
                        "       AND pol.t_rcno IS NOT NULL AND pol.t_rcno <> '' " +
                        "WHERE  CAST(rch.t_pddt AS DATE) >= :startDate " +
                        "  AND  CAST(rch.t_pddt AS DATE) <= :endDate";

        var params = new MapSqlParameterSource()
                .addValue("startDate", sqlStart, java.sql.Types.DATE)
                .addValue("endDate",   sqlEnd,   java.sql.Types.DATE);

        try {
            return jdbc.queryForObject(sql, params, (rs, i) -> {
                var s = new ReceptionStatsDTO();
                s.setTotalOrders(rs.getLong("totalOrders"));
                s.setTotalQuantityOrdered(rs.getBigDecimal("totalOrdered"));
                s.setTotalQuantityReceived(rs.getBigDecimal("totalReceived"));
                s.setSupplierCount(rs.getLong("supplierCount"));
                BigDecimal ord = s.getTotalQuantityOrdered();
                BigDecimal rec = s.getTotalQuantityReceived();
                if (ord != null && ord.compareTo(BigDecimal.ZERO) > 0)
                    s.setReceiptRate(rec.doubleValue() / ord.doubleValue() * 100.0);
                return s;
            });
        } catch (EmptyResultDataAccessException e) {
            return new ReceptionStatsDTO();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 5–8. Exports
    // ══════════════════════════════════════════════════════════════════════
    @Override
    public byte[] generatePdfByOrder(String orderNumber) {
        List<ReceptionLineDTO> lines = searchByOrder(orderNumber);
        if (lines.isEmpty())
            throw new RuntimeException("Aucune réception pour la commande : " + orderNumber);
        return PdfBuilder.build(lines, orderNumber, false);
    }

    @Override
    public byte[] generatePdfValued(String orderNumber) {
        List<ReceptionLineDTO> lines = searchByOrder(orderNumber);
        if (lines.isEmpty())
            throw new RuntimeException("Aucune réception pour la commande : " + orderNumber);
        return PdfBuilder.build(lines, orderNumber, true);
    }

    @Override
    public byte[] exportExcel(String startDate, String endDate) {
        List<ReceptionOrderDTO> orders = searchByDateRange(startDate, endDate);
        if (orders.isEmpty())
            throw new RuntimeException("Aucune réception dans : " + startDate + " → " + endDate);
        return exportExcelBulk(orders.stream().map(ReceptionOrderDTO::getOrderNumber).distinct().toList());
    }

    @Override
    public byte[] exportExcelBulk(List<String> orderNumbers) {
        if (orderNumbers == null || orderNumbers.isEmpty())
            throw new IllegalArgumentException("Liste de commandes vide.");
        List<ReceptionLineDTO> all = new ArrayList<>();
        for (String on : orderNumbers) all.addAll(searchByOrder(on.trim()));
        if (all.isEmpty())
            throw new RuntimeException(
                    "Aucune ligne trouvée pour : " + orderNumbers +
                            " — vérifiez que le format est PO0000050 (pas OR...)");
        return ExcelBuilder.build(all);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Row mapper
    // ══════════════════════════════════════════════════════════════════════
    private ReceptionLineDTO mapLine(ResultSet rs, int i) throws SQLException {
        var dto = new ReceptionLineDTO();
        dto.setFournisseur(rs.getString("fournisseur"));
        dto.setDescFrs(rs.getString("descFrs"));
        dto.setDaeFact(rs.getString("daeFact"));
        dto.setLg(rs.getString("lg"));
        dto.setOa(rs.getString("oa"));
        dto.setCwar(rs.getString("cwar"));
        dto.setArticle(rs.getString("article"));
        dto.setDescription(rs.getString("description"));
        dto.setQteCdee(rs.getBigDecimal("qteCdee"));
        dto.setQteRecue(rs.getBigDecimal("qteRecue"));
        dto.setDino(rs.getString("dino"));
        dto.setEmplacement(rs.getString("emplacement"));
        dto.setDateReception(rs.getString("dateReception"));
        dto.setNumeroReception(rs.getString("numeroReception"));
        dto.setDevise(rs.getString("devise"));
        BigDecimal prix = rs.getBigDecimal("prixUnitaire");
        dto.setPrixUnitaire(prix);
        if (prix != null && dto.getQteRecue() != null && prix.compareTo(BigDecimal.ZERO) > 0)
            dto.setValeurTotale(prix.multiply(dto.getQteRecue()));
        return dto;
    }

    // ══════════════════════════════════════════════════════════════════════
    // Utilitaire : DD/MM/YYYY → java.sql.Date
    // ══════════════════════════════════════════════════════════════════════
    private java.sql.Date parseSqlDate(String ddMmYyyy, String fieldName) {
        if (ddMmYyyy == null || ddMmYyyy.isBlank())
            throw new IllegalArgumentException(fieldName + " : date vide.");
        try {
            return java.sql.Date.valueOf(
                    LocalDate.parse(ddMmYyyy.trim(), FMT_DISPLAY)
            );
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    fieldName + " : format invalide '" + ddMmYyyy + "'. Attendu DD/MM/YYYY.", e);
        }
    }
}