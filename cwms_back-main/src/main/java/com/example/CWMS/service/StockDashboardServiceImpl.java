package com.example.CWMS.service;

import com.example.CWMS.iservice.StockDashboardService;
import com.example.CWMS.model.erp.ErpArticle;
import com.example.CWMS.model.erp.ErpStock;
import com.example.CWMS.repository.erp.ErpArticleRepository;
import com.example.CWMS.repository.erp.ErpStockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockDashboardServiceImpl implements StockDashboardService {

    private final ErpStockRepository stockRepository;
    private final ErpArticleRepository articleRepository;
    private final ErpConsultationServiceImpl consultationService;


  //calcule les statistiques par entrepôt, par catégorie et le Top 10 des articles/emplacements
    @Override
    @Transactional(transactionManager = "erpTransactionManager", readOnly = true)
    public Map<String, Object> getDashboardData() {
        try {
            // 1. Récupération de tout le stock
            List<ErpStock> allStock = stockRepository.findAll();
            if (allStock == null || allStock.isEmpty()) {
                return createEmptyResponse();
            }

            // 2. Récupération des articles par "Batches" pour éviter la limite de 2100 paramètres
            Set<String> itemCodes = allStock.stream()
                    .map(ErpStock::getItemCode)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            Map<String, ErpArticle> articleCache = new HashMap<>();
            List<String> codesList = new ArrayList<>(itemCodes);

            // On découpe la liste par paquets de 1000 pour SQL Server
            for (int i = 0; i < codesList.size(); i += 1000) {
                int end = Math.min(i + 1000, codesList.size());
                List<String> batch = codesList.subList(i, end);
                articleRepository.findAllById(batch).forEach(art ->
                        articleCache.put(art.getItemCode(), art)
                );
            }

            // --- CALCULS DES STATISTIQUES ---

            // A. Par Entrepôt
            List<Map<String, Object>> byWarehouse = allStock.stream()
                    .filter(s -> s.getWarehouseCode() != null)
                    .collect(Collectors.groupingBy(ErpStock::getWarehouseCode, Collectors.summingDouble(ErpStock::getQuantityAvailable)))
                    .entrySet().stream()
                    .map(e -> {
                        Map<String, Object> m = new HashMap<>();
                        m.put("warehouse", e.getKey());
                        m.put("totalQty", e.getValue());
                        return m;
                    }).collect(Collectors.toList());

            // B. Top 10 Emplacements
            List<Map<String, Object>> byLocation = allStock.stream()
                    .filter(s -> s.getLocation() != null && !s.getLocation().isEmpty())
                    .collect(Collectors.groupingBy(ErpStock::getLocation, Collectors.summingDouble(ErpStock::getQuantityAvailable)))
                    .entrySet().stream()
                    .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                    .limit(10)
                    .map(e -> {
                        Map<String, Object> m = new HashMap<>();
                        m.put("location", e.getKey());
                        m.put("totalQty", e.getValue());
                        return m;
                    }).collect(Collectors.toList());

            // C. Par Catégorie (Utilise le cache batché)
            List<Map<String, Object>> byCategory = allStock.stream()
                    .collect(Collectors.groupingBy(s -> {
                        ErpArticle art = articleCache.get(s.getItemCode());
                        return (art != null && art.getItemCategory() != null) ? art.getItemCategory() : "NON CLASSÉ";
                    }, Collectors.counting()))
                    .entrySet().stream()
                    .map(e -> {
                        Map<String, Object> m = new HashMap<>();
                        m.put("category", e.getKey());
                        m.put("itemCount", e.getValue());
                        return m;
                    }).collect(Collectors.toList());

            // D. Top 10 Articles
            List<Map<String, Object>> topItems = allStock.stream()
                    .collect(Collectors.groupingBy(ErpStock::getItemCode, Collectors.summingDouble(ErpStock::getQuantityAvailable)))
                    .entrySet().stream()
                    .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                    .limit(10)
                    .map(e -> {
                        ErpArticle art = articleCache.get(e.getKey());
                        Map<String, Object> m = new HashMap<>();
                        m.put("itemCode", e.getKey());
                        m.put("designation", art != null ? art.getDesignation() : "N/A");
                        m.put("totalQty", e.getValue());
                        return m;
                    }).collect(Collectors.toList());

            // Réponse finale
            Map<String, Object> response = new HashMap<>();
            response.put("byWarehouse", byWarehouse);
            response.put("byLocation", byLocation);
            response.put("byCategory", byCategory);
            response.put("topItems", topItems);
            response.put("totalQty", allStock.stream().mapToDouble(ErpStock::getQuantityAvailable).sum());
            response.put("lastUpdate", new Date());

            return response;

        } catch (Exception e) {
            log.error("Erreur critique Dashboard COFAT: ", e);
            return createEmptyResponse();
        }
    }


    //Elle évite que  l'application plante si la base de données ERP est vide
    private Map<String, Object> createEmptyResponse() {
        Map<String, Object> empty = new HashMap<>();
        empty.put("byWarehouse", new ArrayList<>());
        empty.put("byLocation", new ArrayList<>());
        empty.put("byCategory", new ArrayList<>());
        empty.put("topItems", new ArrayList<>());
        empty.put("totalQty", 0.0);
        return empty;
    }


}
