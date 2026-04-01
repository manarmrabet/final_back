package com.example.CWMS.erp.service;

import com.example.CWMS.erp.dto.ErpArticleSummaryDTO;
import com.example.CWMS.erp.dto.ErpLotLineDTO;
import com.example.CWMS.erp.dto.ErpStockDTO;
import com.example.CWMS.erp.entity.*;
import com.example.CWMS.erp.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ErpConsultationService {

    private final ErpStockRepository stockRepository;
    private final ErpArticleRepository articleRepository;

    /**
     * Utilisé par le Web et le Mobile pour le résumé d'un article
     */
    @Transactional(transactionManager = "erpTransactionManager", readOnly = true)
    public ErpArticleSummaryDTO getArticleSummary(String scanCode) {
        ErpArticle art = articleRepository.findByItemCode(scanCode.trim()).orElse(null);
        List<ErpStock> stocks = stockRepository.findByItemCode(scanCode.trim());

        List<ErpLotLineDTO> lots = stocks.stream()
                .map(s -> mapToLotLineDTO(s, art))
                .collect(Collectors.toList());

        return ErpArticleSummaryDTO.builder()
                .itemCode(art != null ? art.getItemCode() : scanCode)
                .designation(art != null ? art.getDesignation() : "Inconnu")
                .unit(art != null ? art.getStockUnit() : "N/A")
                .lots(lots)
                .build();
    }

    /**
     * FIX: Méthode manquante pour les détails d'un lot (Web & Mobile)
     */
    @Transactional(transactionManager = "erpTransactionManager", readOnly = true)
    public List<ErpLotLineDTO> getLotDetails(String lotNumber) {
        List<ErpStock> stocks = stockRepository.findByLotNumber(lotNumber.trim());
        return stocks.stream().map(s -> {
            ErpArticle art = articleRepository.findByItemCode(s.getItemCode()).orElse(null);
            return mapToLotLineDTO(s, art);
        }).collect(Collectors.toList());
    }

    /**
     * Utilisé par l'ancien endpoint mobile pour lister les lots
     */
    @Transactional(transactionManager = "erpTransactionManager", readOnly = true)
    public List<ErpStockDTO> getLotsByItem(String itemCode) {
        return stockRepository.findByItemCode(itemCode.trim()).stream()
                .map(s -> {
                    ErpArticle art = articleRepository.findByItemCode(s.getItemCode()).orElse(null);
                    // builder sécurisé contre les Nulls pour Flutter
                    return ErpStockDTO.builder()
                            .id(s.getIdStockage())
                            .itemCode(s.getItemCode())
                            .designation(art != null ? art.getDesignation() : "")
                            .unit(art != null ? art.getStockUnit() : "MT")
                            .lotNumber(s.getLotNumber() != null ? s.getLotNumber() : "N/A")
                            .location(s.getLocation() != null ? s.getLocation() : "N/A")
                            .warehouseCode(s.getWarehouseCode() != null ? s.getWarehouseCode() : "N/A")
                            .quantityAvailable(s.getAvailableQuantityAsInt())
                            .entryDate(s.getEntryDate())
                            .status(s.getComputedStatus())
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Transactional(transactionManager = "erpTransactionManager", readOnly = true)
    public Page<ErpLotLineDTO> getAllStockPaginated(Pageable pageable) {
        return stockRepository.findAll(pageable).map(s -> {
            ErpArticle art = articleRepository.findByItemCode(s.getItemCode()).orElse(null);
            return mapToLotLineDTO(s, art);
        });
    }

    private ErpLotLineDTO mapToLotLineDTO(ErpStock stock, ErpArticle art) {
        return ErpLotLineDTO.builder()
                .lotNumber(stock.getLotNumber() != null ? stock.getLotNumber() : "")
                .itemCode(stock.getItemCode())
                .designation(art != null ? art.getDesignation() : "")
                .unit(art != null ? art.getStockUnit() : "")
                .location(stock.getLocation() != null ? stock.getLocation() : "")
                .warehouseCode(stock.getWarehouseCode() != null ? stock.getWarehouseCode() : "")
                .quantityAvailable(stock.getAvailableQuantityAsInt())
                .status(stock.getComputedStatus())
                .entryDate(ErpStock.formatErpDate(stock.getInventoryDateRaw()))
                .build();
    }
}