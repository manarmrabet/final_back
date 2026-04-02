package com.example.CWMS.iservice;

import com.example.CWMS.dto.ErpArticleSummaryDTO;
import com.example.CWMS.dto.ErpLotLineDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface ErpConsultationService {
    ErpArticleSummaryDTO getArticleSummary(String scanCode);
    List<ErpLotLineDTO> getLotDetails(String lotNumber);
    Page<ErpLotLineDTO> getAllStockPaginated(Pageable pageable);
}
