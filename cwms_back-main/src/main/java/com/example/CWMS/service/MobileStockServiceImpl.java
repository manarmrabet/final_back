package com.example.CWMS.service;

import com.example.CWMS.iservice.MobileStockService;
import com.example.CWMS.model.erp.ErpArticle;
import com.example.CWMS.model.erp.ErpStock;
import com.example.CWMS.repository.erp.ErpArticleRepository;
import com.example.CWMS.repository.erp.ErpStockRepository;
import com.example.CWMS.dto.ErpArticleSummaryDTO;
import com.example.CWMS.dto.ErpLotLineDTO;
import com.example.CWMS.dto.ErpStockDTO; // Import manquant sur tes captures
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MobileStockServiceImpl implements MobileStockService {

    private final ErpStockRepository stockRepository;
    private final ErpArticleRepository articleRepository;
//récupére les infos de l'article en plus de qté

    @Override
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


    //Permet au magasinier de scanner un lot et de voir instantanément où il est rangé
    @Override
    public List<ErpLotLineDTO> getLotDetails(String lotNumber) {
        return stockRepository.findByLotNumber(lotNumber.trim()).stream()
                .map(this::mapToLotLine)
                .collect(Collectors.toList());
    }

    //Fournit une liste de lots simplifiée pour un article
    @Override
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


    /* il force la récupération de la désignation de l'article pour chaque ligne
    afin que l'affichage mobile soit complet **/
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
