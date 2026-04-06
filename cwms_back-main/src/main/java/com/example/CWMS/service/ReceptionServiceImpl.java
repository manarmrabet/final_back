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
 * ═══════════════════════════════════════════════════════════════════════════════
 * ReceptionServiceImpl  —  Version finale basée sur l'analyse réelle de la BDD
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * FAITS ÉTABLIS PAR L'ANALYSE SSMS :
 * ─────────────────────────────────
 *  • t_shda dans twhinh310310 → nvarchar(9) VIDE (valeur : "")
 *    La vraie date de réception est dans t_pddt (datetime) → ex: 2026-02-02
 *
 *  • Jointure valide : pol.t_rcno = rch.t_rcno
 *    Ex: PO0000032 → t_rcno = OR0000026, rch.t_rcno = OR0000026 ✓
 *
 *  • t_orno dans ttdpur401330 = "PO0000032" (format PO + 7 chiffres)
 *    t_rcno dans twhinh310310 = "OR0000026" (format OR + 7 chiffres)
 *    Ces deux champs sont DIFFÉRENTS — t_rcno est la clé de jointure.
 *
 *  • Filtrage par date : utiliser rch.t_pddt (datetime) NOT rch.t_shda
 * ═══════════════════════════════════════════════════════════════════════════════
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReceptionServiceImpl implements ReceptionService {

    @Qualifier("erpNamedJdbc")
    private final NamedParameterJdbcTemplate jdbc;

    private static final DateTimeFormatter FMT_DISPLAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // ══════════════════════════════════════════════════════════════════════════
    // SELECT de base — utilisé par searchByOrder et getReceptionDetail
    // ══════════════════════════════════════════════════════════════════════════
    private static final String SQL_SELECT_LINES =
            "SELECT " +
                    "    pol.t_sfbp                                              AS fournisseur, " +
                    "    ISNULL(sup.t_nama, pol.t_sfbp)                          AS descFrs, " +
                    "    ISNULL(CAST(pol.t_dino AS NVARCHAR(30)), '')            AS daeFact, " +
                    "    ISNULL(CAST(pol.t_sqnb AS NVARCHAR(10)), '')            AS lg, " +
                    "    pol.t_orno                                              AS oa, " +
                    "    ISNULL(rch.t_cwar, pol.t_cwar)                          AS cwar, " +
                    "    pol.t_item                                              AS article, " +
                    "    ISNULL(itm.t_dsca, pol.t_item)                          AS description, " +
                    "    ISNULL(pol.t_qoor, 0)                                   AS qteCdee, " +
                    "    ISNULL(pol.t_qips, 0)                                   AS qteRecue, " +
                    "    ISNULL(CAST(rch.t_dino AS NVARCHAR(30)), '')            AS dino, " +
                    "    ISNULL(stk.t_loca, 'RECEIVING')                         AS emplacement, " +
                    // Date réelle = t_pddt (datetime) — t_shda est vide dans cet ERP
                    "    CONVERT(VARCHAR(10), rch.t_pddt, 103)                   AS dateReception, " +
                    "    rch.t_rcno                                              AS numeroReception, " +
                    "    ISNULL(rch.t_curr, ISNULL(pol.t_cupp, 'USD'))           AS devise, " +
                    "    ISNULL(pol.t_pric, 0)                                   AS prixUnitaire " +
                    "FROM  [ERP].[dbo].[dbo_ttdpur401330]  pol " +
                    "INNER JOIN [ERP].[dbo].[dbo_twhinh310310]  rch " +
                    "       ON  rch.t_rcno = pol.t_rcno " +
                    "       AND pol.t_rcno IS NOT NULL " +
                    "       AND pol.t_rcno <> '' " +
                    "LEFT  JOIN [ERP].[dbo].[dbo_ttccom100310]  sup " +
                    "       ON  sup.t_bpid = pol.t_sfbp " +
                    "LEFT  JOIN [ERP].[dbo].[dbo_ttcibd001310]  itm " +
                    "       ON  itm.t_item = pol.t_item " +
                    "LEFT  JOIN [ERP].[dbo].[dbo_twhinr1401200] stk " +
                    "       ON  stk.t_cwar = ISNULL(rch.t_cwar, pol.t_cwar) " +
                    "       AND stk.t_item = pol.t_item ";

    // ══════════════════════════════════════════════════════════════════════════
    // 1. Recherche par numéro de commande (t_orno de ttdpur401330)
    //    Format attendu : PO0000032  (ou le format utilisé dans votre ERP)
    // ══════════════════════════════════════════════════════════════════════════
    @Override
    public List<ReceptionLineDTO> searchByOrder(String orderNumber) {
        log.info("[Reception] searchByOrder → '{}'", orderNumber);
        String sql = SQL_SELECT_LINES +
                "WHERE pol.t_orno = :orderNumber " +
                "ORDER BY pol.t_sqnb";
        return jdbc.query(sql,
                new MapSqlParameterSource("orderNumber", orderNumber.trim()),
                this::mapLine);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 2. Recherche par plage de dates
    //    Filtrage sur rch.t_pddt (datetime) — la colonne date réelle
    //    Paramètres reçus au format DD/MM/YYYY depuis le frontend
    // ══════════════════════════════════════════════════════════════════════════
    @Override
    public List<ReceptionOrderDTO> searchByDateRange(String startDate, String endDate) {
        log.info("[Reception] searchByDateRange → '{}' à '{}'", startDate, endDate);

        // Conversion DD/MM/YYYY → java.sql.Date pour paramètre JDBC typé
        java.sql.Date sqlStart = toSqlDate(startDate, "startDate");
        java.sql.Date sqlEnd   = toSqlDate(endDate,   "endDate");

        String sql =
                "SELECT " +
                        "    pol.t_orno                                          AS orderNumber, " +
                        "    CONVERT(VARCHAR(10), rch.t_pddt, 103)               AS orderDate, " +
                        "    ISNULL(sup.t_nama, pol.t_sfbp)                      AS supplier, " +
                        "    pol.t_sfbp                                          AS supplierCode, " +
                        "    ISNULL(SUM(pol.t_qips), 0)                          AS totalQty, " +
                        "    ISNULL(rch.t_curr, ISNULL(pol.t_cupp, 'USD'))       AS devise " +
                        "FROM  [ERP].[dbo].[dbo_ttdpur401330]  pol " +
                        "INNER JOIN [ERP].[dbo].[dbo_twhinh310310]  rch " +
                        "       ON  rch.t_rcno = pol.t_rcno " +
                        "       AND pol.t_rcno IS NOT NULL " +
                        "       AND pol.t_rcno <> '' " +
                        "LEFT  JOIN [ERP].[dbo].[dbo_ttccom100310]  sup " +
                        "       ON  sup.t_bpid = pol.t_sfbp " +
                        // Filtrage sur t_pddt (datetime réel)
                        "WHERE rch.t_pddt >= :startDate " +
                        "  AND rch.t_pddt <  DATEADD(day, 1, :endDate) " +
                        "GROUP BY pol.t_orno, rch.t_pddt, sup.t_nama, pol.t_sfbp, rch.t_curr, pol.t_cupp " +
                        "ORDER BY rch.t_pddt DESC";

        var params = new MapSqlParameterSource()
                .addValue("startDate", sqlStart, java.sql.Types.DATE)
                .addValue("endDate",   sqlEnd,   java.sql.Types.DATE);

        return jdbc.query(sql, params, (rs, i) -> {
            var dto = new ReceptionOrderDTO();
            dto.setOrderNumber(rs.getString("orderNumber"));
            dto.setDate(rs.getString("orderDate"));
            dto.setSupplier(rs.getString("supplier"));
            dto.setSupplierCode(rs.getString("supplierCode"));
            dto.setTotalQty(rs.getBigDecimal("totalQty"));
            dto.setDevise(rs.getString("devise"));
            return dto;
        });
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 3. Détail d'une réception par numéro de réception (t_rcno)
    //    t_rcno = numéro twhinh, ex: OR0000026
    // ══════════════════════════════════════════════════════════════════════════
    @Override
    public List<ReceptionLineDTO> getReceptionDetail(String receptionNumber) {
        log.info("[Reception] getReceptionDetail → '{}'", receptionNumber);
        String sql = SQL_SELECT_LINES +
                "WHERE rch.t_rcno = :rcno " +
                "ORDER BY pol.t_sqnb";
        return jdbc.query(sql,
                new MapSqlParameterSource("rcno", receptionNumber.trim()),
                this::mapLine);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 4. Statistiques sur une plage de dates
    // ══════════════════════════════════════════════════════════════════════════
    @Override
    public ReceptionStatsDTO getStats(String startDate, String endDate) {
        log.info("[Reception] getStats → '{}' à '{}'", startDate, endDate);
        java.sql.Date sqlStart = toSqlDate(startDate, "startDate");
        java.sql.Date sqlEnd   = toSqlDate(endDate,   "endDate");

        String sql =
                "SELECT " +
                        "    COUNT(DISTINCT pol.t_orno)    AS totalOrders, " +
                        "    ISNULL(SUM(pol.t_qoor), 0)    AS totalOrdered, " +
                        "    ISNULL(SUM(pol.t_qips), 0)    AS totalReceived, " +
                        "    COUNT(DISTINCT pol.t_sfbp)    AS supplierCount " +
                        "FROM  [ERP].[dbo].[dbo_ttdpur401330]  pol " +
                        "INNER JOIN [ERP].[dbo].[dbo_twhinh310310]  rch " +
                        "       ON  rch.t_rcno = pol.t_rcno " +
                        "       AND pol.t_rcno IS NOT NULL AND pol.t_rcno <> '' " +
                        "WHERE rch.t_pddt >= :startDate " +
                        "  AND rch.t_pddt <  DATEADD(day, 1, :endDate)";

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

    // ══════════════════════════════════════════════════════════════════════════
    // 5–8. Exports — délèguent à PdfBuilder / ExcelBuilder
    // ══════════════════════════════════════════════════════════════════════════
    @Override
    public byte[] generatePdfByOrder(String orderNumber) {
        List<ReceptionLineDTO> lines = searchByOrder(orderNumber);
        if (lines.isEmpty())
            throw new RuntimeException("Aucune réception trouvée pour la commande : " + orderNumber);
        return PdfBuilder.build(lines, orderNumber, false);
    }

    @Override
    public byte[] generatePdfValued(String orderNumber) {
        List<ReceptionLineDTO> lines = searchByOrder(orderNumber);
        if (lines.isEmpty())
            throw new RuntimeException("Aucune réception trouvée pour la commande : " + orderNumber);
        return PdfBuilder.build(lines, orderNumber, true);
    }

    @Override
    public byte[] exportExcel(String startDate, String endDate) {
        List<ReceptionOrderDTO> orders = searchByDateRange(startDate, endDate);
        if (orders.isEmpty())
            throw new RuntimeException("Aucune réception dans la plage : " + startDate + " → " + endDate);
        List<String> nums = orders.stream().map(ReceptionOrderDTO::getOrderNumber).distinct().toList();
        return exportExcelBulk(nums);
    }

    @Override
    public byte[] exportExcelBulk(List<String> orderNumbers) {
        if (orderNumbers == null || orderNumbers.isEmpty())
            throw new IllegalArgumentException("Liste de commandes vide.");

        List<ReceptionLineDTO> all = new ArrayList<>();
        for (String on : orderNumbers) {
            all.addAll(searchByOrder(on.trim()));
        }
        if (all.isEmpty())
            throw new RuntimeException(
                    "Aucune ligne de réception trouvée pour : " + orderNumbers +
                            ". Vérifiez que le numéro est au format PO0000032 (pas OR...).");
        return ExcelBuilder.build(all);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Row mapper : ResultSet → ReceptionLineDTO
    // ══════════════════════════════════════════════════════════════════════════
    private ReceptionLineDTO mapLine(ResultSet rs, int rowNum) throws SQLException {
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

    // ══════════════════════════════════════════════════════════════════════════
    // Conversion de date DD/MM/YYYY → java.sql.Date (typé JDBC — pas de CONVERT)
    // ══════════════════════════════════════════════════════════════════════════
    private java.sql.Date toSqlDate(String ddMmYyyy, String fieldName) {
        if (ddMmYyyy == null || ddMmYyyy.isBlank())
            throw new IllegalArgumentException(fieldName + " : date vide.");
        try {
            LocalDate ld = LocalDate.parse(ddMmYyyy.trim(), FMT_DISPLAY);
            return java.sql.Date.valueOf(ld);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    fieldName + " : format invalide '" + ddMmYyyy +
                            "'. Attendu DD/MM/YYYY.", e);
        }
    }
}