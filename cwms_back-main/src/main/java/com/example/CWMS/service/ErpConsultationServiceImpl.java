package com.example.CWMS.service;

import com.example.CWMS.dto.ErpArticleSummaryDTO;
import com.example.CWMS.dto.ErpLotLineDTO;
import com.example.CWMS.iservice.ErpConsultationService;
import com.example.CWMS.model.erp.ErpArticle;
import com.example.CWMS.model.erp.ErpStock;
import com.example.CWMS.repository.erp.ErpArticleRepository;
import com.example.CWMS.repository.erp.ErpStockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ErpConsultationServiceImpl implements ErpConsultationService {

    private final ErpStockRepository stockRepository;
    private final ErpArticleRepository articleRepository;
//Récupère les informations générales d'un article (via son code)
// et liste toutes les lignes de stock (lots) correspondantes
    /**
     * Utilisé par le Web et le Mobile pour le résumé d'un article
     */

    @Override
    @Transactional(transactionManager = "erpTransactionManager", readOnly = true)
    public ErpArticleSummaryDTO getArticleSummary(String scanCode) {
        ErpArticle art = articleRepository.findByItemCode(scanCode.trim()).orElse(null);
        List<ErpStock> stocks = stockRepository.findByItemCode(scanCode.trim());

        // 1. CALCULER LA QUANTITÉ TOTALE (C'est ce qui manque !)
        double totalQty = stocks.stream()
                .mapToDouble(ErpStock::getQuantityAvailable)
                .sum();

        List<ErpLotLineDTO> lots = stocks.stream()
                .map(s -> mapToLotLineDTO(s, art))
                .collect(Collectors.toList());

        return ErpArticleSummaryDTO.builder()
                .itemCode(art != null ? art.getItemCode() : scanCode)
                .designation(art != null ? art.getDesignation() : "Inconnu")
                .unit(art != null ? art.getStockUnit() : "N/A")
                .totalQtyOnHand(totalQty) // <--- AJOUTER CETTE LIGNE
                .lots(lots)
                // Optionnel : tu peux aussi ajouter ces champs pour enrichir l'interface Web
                .mainWarehouse(lots.isEmpty() ? "N/A" : lots.get(0).getWarehouseCode())
                .mainLot(lots.isEmpty() ? "N/A" : lots.get(0).getLotNumber())
                .build();
    }


    //Cherche toutes les occurrences d'un numéro de lot spécifique dans l'entrepôt
    // pour voir sa répartition (emplacements, quantités)


    @Override
    @Transactional(transactionManager = "erpTransactionManager", readOnly = true)
    public List<ErpLotLineDTO> getLotDetails(String lotNumber) {
        List<ErpStock> stocks = stockRepository.findByLotNumber(lotNumber.trim());
        return stocks.stream().map(s -> {
            ErpArticle art = articleRepository.findByItemCode(s.getItemCode()).orElse(null);
            return mapToLotLineDTO(s, art);
        }).collect(Collectors.toList());
    }



    //Récupère l'intégralité du stock ERP de manière paginée
    @Override
    @Transactional(transactionManager = "erpTransactionManager", readOnly = true)
    public Page<ErpLotLineDTO> getAllStockPaginated(Pageable pageable) {
        return stockRepository.findAll(pageable).map(s -> {
            ErpArticle art = articleRepository.findByItemCode(s.getItemCode()).orElse(null);
            return mapToLotLineDTO(s, art);
        });
    }


  //Fonction utilitaire privée qui transforme l'objet base de données (ErpStock)
  // en objet lisible par le Frontend (DTO)


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