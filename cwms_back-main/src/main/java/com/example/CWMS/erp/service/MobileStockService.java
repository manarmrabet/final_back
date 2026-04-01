package com.example.CWMS.erp.service;

import com.example.CWMS.erp.entity.ErpArticle;
import com.example.CWMS.erp.entity.ErpStock;
import com.example.CWMS.erp.repository.ErpArticleRepository;
import com.example.CWMS.erp.repository.ErpStockRepository;
import com.example.CWMS.erp.dto.ErpArticleSummaryDTO;
import com.example.CWMS.erp.dto.ErpLotLineDTO;
import com.example.CWMS.erp.dto.ErpStockDTO; // Import manquant sur tes captures
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MobileStockService {

    private final ErpStockRepository stockRepository;
    private final ErpArticleRepository articleRepository;

    public ErpArticleSummaryDTO getArticleSummary(String code) {
        ErpArticle article = articleRepository.findByItemCode(code.trim())
                .orElseThrow(() -> new RuntimeException("Article non trouvé"));

        List<ErpStock> stocks = stockRepository.findByItemCode(code.trim());
        double totalQty = stocks.stream().mapToDouble(ErpStock::getQuantityAvailable).sum();

        return ErpArticleSummaryDTO.builder()
                .itemCode(article.getItemCode())
                .designation(article.getDesignation() != null ? article.getDesignation() : "N/A")
                .unit(article.getStockUnit() != null ? article.getStockUnit() : "MT")
                .totalQtyOnHand(totalQty)
                .lots(stocks.stream().map(this::mapToLotLine).collect(Collectors.toList()))
                .mainWarehouse(stocks.isEmpty() ? "N/A" : stocks.get(0).getWarehouseCode())
                .mainLot(stocks.isEmpty() ? "N/A" : stocks.get(0).getLotNumber())
                .lastDate(stocks.isEmpty() ? "N/A" : ErpStock.formatErpDate(stocks.get(0).getInventoryDateRaw()))
                .build();
    }

    // --- NOUVELLE MÉTHODE : getLotDetails (Attendue par le contrôleur ligne 42) ---
    public List<ErpLotLineDTO> getLotDetails(String lotNumber) {
        return stockRepository.findByLotNumber(lotNumber.trim()).stream()
                .map(this::mapToLotLine)
                .collect(Collectors.toList());
    }

    // --- NOUVELLE MÉTHODE : getLotsByItem (Attendue par le contrôleur ligne 57) ---
    public List<ErpStockDTO> getLotsByItem(String code) {
        return stockRepository.findByItemCode(code.trim()).stream()
                .map(s -> ErpStockDTO.builder()
                        .itemCode(s.getItemCode())
                        .lotNumber(s.getLotNumber() != null ? s.getLotNumber() : "N/A")
                        .location(s.getLocation() != null ? s.getLocation() : "N/A")
                        .quantityAvailable(s.getAvailableQuantityAsInt())
                        .build())
                .collect(Collectors.toList());
    }

    private ErpLotLineDTO mapToLotLine(ErpStock s) {
        // On récupère la désignation réelle de l'article pour que l'écran violet soit complet
        String designation = articleRepository.findByItemCode(s.getItemCode())
                .map(ErpArticle::getDesignation)
                .orElse("Désignation non trouvée");

        return ErpLotLineDTO.builder()
                .itemCode(s.getItemCode())
                .lotNumber(s.getLotNumber() != null ? s.getLotNumber().trim() : "N/A")
                .location(s.getLocation() != null ? s.getLocation().trim() : "N/A")
                .warehouseCode(s.getWarehouseCode() != null ? s.getWarehouseCode().trim() : "N/A")
                .quantityAvailable(s.getAvailableQuantityAsInt())
                .status(s.getComputedStatus())
                .designation(designation) // <--- TRÈS IMPORTANT pour Flutter
                .unit("MT")
                .entryDate(ErpStock.formatErpDate(s.getInventoryDateRaw()))
                .build();
    }
}
