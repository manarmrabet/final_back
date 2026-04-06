// ════════════════════════════════════════════════════════════════════════════
// IReceptionService.java  (iservice package)
// ════════════════════════════════════════════════════════════════════════════
package com.example.CWMS.iservice;

import com.example.CWMS.dto.ReceptionLineDTO;
import com.example.CWMS.dto.ReceptionOrderDTO;
import com.example.CWMS.dto.ReceptionStatsDTO;

import java.util.List;

public interface ReceptionService {

    // 1. Search by order number
    List<ReceptionLineDTO> searchByOrder(String orderNumber);

    // 2. Search by date range → grouped by order
    List<ReceptionOrderDTO> searchByDateRange(String startDate, String endDate);

    // 3. Get all lines for a reception number
    List<ReceptionLineDTO> getReceptionDetail(String receptionNumber);

    // 4. Stats for a date range
    ReceptionStatsDTO getStats(String startDate, String endDate);

    // 5. Generate PDF standard
    byte[] generatePdfByOrder(String orderNumber);

    // 6. Generate PDF with valorization
    byte[] generatePdfValued(String orderNumber);

    // 7. Export Excel by date range
    byte[] exportExcel(String startDate, String endDate);

    // 8. Export Excel for multiple orders (bulk)
    byte[] exportExcelBulk(List<String> orderNumbers);
}


