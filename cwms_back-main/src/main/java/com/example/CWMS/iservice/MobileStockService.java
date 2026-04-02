package com.example.CWMS.iservice;
import com.example.CWMS.dto.ErpArticleSummaryDTO;
import com.example.CWMS.dto.ErpLotLineDTO;
import com.example.CWMS.dto.ErpStockDTO;
import java.util.List;

public interface MobileStockService {
    ErpArticleSummaryDTO getArticleSummary(String code);
    List<ErpLotLineDTO> getLotDetails(String lotNumber);
    List<ErpStockDTO> getLotsByItem(String code);
}
