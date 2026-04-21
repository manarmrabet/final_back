package com.example.CWMS.iservice;

import com.example.CWMS.dto.*;
import java.util.List;

public interface IProductionService {
    StockCheckDTO      checkStock(String clot);
    SortieResponseDTO  sortieTotale(SortieRequestDTO req, Long userId, String userName);
    SortieResponseDTO  sortiePartielle(SortieRequestDTO req, Long userId, String userName);
    List<ProductionLogDTO> getAllLogs();
    List<ProductionLogDTO> getTodayLogs();
    ProductionStatsDTO     getStats();
    List<ProductionLogDTO> getLogsByUser(Long userId);
}