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

    private static final String SQL_SELECT = """
        SELECT
            h.t_rcno,
            h.t_rcln,
            h.t_sfbp,
            'COFAT TUNIS' AS company,
            'A' AS rotation_class,
            LTRIM(RTRIM(h.t_item)) AS item,
            
            ISNULL(NULLIF(LTRIM(RTRIM(h.t_cmnf)), ''), LTRIM(RTRIM(h.t_item))) AS description,
            
            ISNULL(CONVERT(VARCHAR(10), l.t_ldat, 103), '-') AS validity_date,
            ISNULL(CAST(h.t_qrec AS VARCHAR(20)), '0') AS qty,
            ISNULL(LTRIM(RTRIM(CAST(h.t_clot AS VARCHAR(50)))), '') AS label_number,
            ISNULL(CONVERT(VARCHAR(10), l.t_frdt, 103), '-') AS date_label,
            ISNULL(CAST(w.t_locc AS VARCHAR(20)), '-') AS location,
            
            ISNULL(
                CAST(DATEPART(WEEK, COALESCE(l.t_ldat, GETDATE())) AS VARCHAR(3)) + '/' +
                CAST(DATEPART(YEAR, COALESCE(l.t_ldat, GETDATE())) AS VARCHAR(4)),
                '00/0000'
            ) AS week_incoming,
            
            ISNULL(LTRIM(RTRIM(h.t_mpnr)), '') AS mpnr,
            ISNULL(LTRIM(RTRIM(CAST(l.t_orno AS VARCHAR(50)))), '') AS linked_order,
            ISNULL(NULLIF(LTRIM(RTRIM(h.t_rcun)), ''), 'PCE') AS unit

        FROM dbo_twhinh312310 h
        LEFT JOIN dbo_twhltc100310 l 
               ON LTRIM(RTRIM(h.t_item)) = LTRIM(RTRIM(l.t_item)) 
              AND ISNULL(h.t_clot, '') = ISNULL(l.t_clot, '')   -- Gestion des NULL
        LEFT JOIN dbo_twhwmd400310 w 
               ON LTRIM(RTRIM(h.t_item)) = LTRIM(RTRIM(w.t_item))

        WHERE h.t_rcno = ?
          AND h.t_oorg = 80

        ORDER BY TRY_CAST(h.t_rcln AS INT) ASC
        """;

    private static final String SQL_MARK_PRINTED = """
        UPDATE dbo_twhltc100310
           SET t_sel1 = '1'
         WHERE t_item IN (SELECT DISTINCT t_item FROM dbo_twhinh312310 WHERE t_rcno = ? AND t_oorg = 80)
           AND t_clot IN (SELECT DISTINCT ISNULL(t_clot, '') FROM dbo_twhinh312310 WHERE t_rcno = ? AND t_oorg = 80)
        """;

    public List<EtiquetteDTO> findByOrderNumber(String rcno) {
        log.info("[EtiquetteRepo] Exécution SELECT pour RCNO = '{}'", rcno);

        List<EtiquetteDTO> result = erpJdbc.query(SQL_SELECT,
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
                    dto.setLinkedOrder(rs.getString("linked_order"));
                    dto.setUnit(rs.getString("unit"));
                    return dto;
                });

        log.info("[EtiquetteRepo] RCNO={} → {} ligne(s) trouvée(s)", rcno, result.size());
        return result;
    }

    public void markAsPrinted(String rcno) {
        int rows = erpJdbc.update(SQL_MARK_PRINTED, rcno, rcno);
        log.info("[EtiquetteRepo] markAsPrinted RCNO={} → {} lots marqués", rcno, rows);
    }
}