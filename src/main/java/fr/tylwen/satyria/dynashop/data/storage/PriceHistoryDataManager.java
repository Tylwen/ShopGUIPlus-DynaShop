/*
 * ShopGUI+ DynaShop - Dynamic Economy Addon for Minecraft
 * Copyright (C) 2025 Tylwen
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package fr.tylwen.satyria.dynashop.data.storage;

// import fr.tylwen.satyria.dynashop.database.PriceHistory;
// import fr.tylwen.satyria.dynashop.database.PriceHistory.PriceDataPoint;
import fr.tylwen.satyria.dynashop.system.chart.PriceHistory;
import fr.tylwen.satyria.dynashop.system.chart.PriceHistory.PriceDataPoint;

import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Gestionnaire pour l'historique des prix
 */
public class PriceHistoryDataManager {
    private final File baseFolder;
    private final Map<String, PriceHistory> historyCache = new ConcurrentHashMap<>();
    
    public PriceHistoryDataManager(File baseFolder) {
        this.baseFolder = baseFolder;
        if (!baseFolder.exists()) {
            baseFolder.mkdirs();
        }
    }
    
    public void load() {
        File[] shopFolders = baseFolder.listFiles(File::isDirectory);
        if (shopFolders == null) return;
        
        for (File shopFolder : shopFolders) {
            String shopId = shopFolder.getName();
            File[] itemFiles = shopFolder.listFiles(f -> f.isFile() && f.getName().endsWith(".json"));
            
            if (itemFiles == null) continue;
            
            for (File itemFile : itemFiles) {
                String itemId = itemFile.getName().replace(".json", "");
                try {
                    Type type = new TypeToken<List<PriceDataPoint>>(){}.getType();
                    List<PriceDataPoint> dataPoints = JsonStorage.loadFromFile(itemFile, type, new ArrayList<>());
                    
                    PriceHistory history = new PriceHistory(shopId, itemId);
                    history.setDataPoints(dataPoints);
                    
                    String key = getHistoryKey(shopId, itemId);
                    historyCache.put(key, history);
                } catch (Exception e) {
                    // Fichier n'existe pas encore ou erreur de lecture
                }
            }
        }
    }
    
    public void save() {
        CompletableFuture.runAsync(() -> {
            for (Map.Entry<String, PriceHistory> entry : historyCache.entrySet()) {
                String[] parts = entry.getKey().split(":");
                if (parts.length != 2) continue;
                
                String shopId = parts[0];
                String itemId = parts[1];
                
                File shopFolder = new File(baseFolder, shopId);
                if (!shopFolder.exists()) {
                    shopFolder.mkdirs();
                }
                
                File itemFile = new File(shopFolder, itemId + ".json");
                
                try {
                    JsonStorage.saveToFile(itemFile, entry.getValue().getDataPoints());
                } catch (IOException e) {
                    // Log error if needed
                }
            }
        });
    }
    
    /**
     * Récupère l'historique des prix pour un item
     */
    public PriceHistory getPriceHistory(String shopId, String itemId) {
        String key = getHistoryKey(shopId, itemId);
        
        PriceHistory history = historyCache.get(key);
        if (history == null) {
            history = new PriceHistory(shopId, itemId);
            historyCache.put(key, history);
        }
        
        return history;
    }

    public void getAll(String shopId, String itemId, Consumer<PriceHistory> callback) {
        CompletableFuture.runAsync(() -> {
            PriceHistory history = getPriceHistory(shopId, itemId);
            callback.accept(history);
        });
    }

    // public void getAll(Consumer<Map<String, PriceHistory>> callback) {
    //     CompletableFuture.runAsync(() -> {
    //         Map<String, PriceHistory> allHistories = new HashMap<>(historyCache);
    //         callback.accept(allHistories);
    //     });
    // }

    // public void getAll(Consumer<Map<String, PriceHistory>> callback, String shopId) {
    //     CompletableFuture.runAsync(() -> {
    //         Map<String, PriceHistory> filteredHistories = new HashMap<>();
    //         for (Map.Entry<String, PriceHistory> entry : historyCache.entrySet()) {
    //             if (entry.getKey().startsWith(shopId + ":")) {
    //                 filteredHistories.put(entry.getKey(), entry.getValue());
    //             }
    //         }
    //         callback.accept(filteredHistories);
    //     });
    // }

    public Map<String, PriceHistory> getAll() {
        return new HashMap<>(historyCache);
    }
    
    /**
     * Ajoute un point de données à l'historique
     */
    public void addDataPoint(String shopId, String itemId, PriceDataPoint dataPoint) {
        PriceHistory history = getPriceHistory(shopId, itemId);
        history.getDataPoints().add(dataPoint);
    }
    
    /**
     * Purge l'historique ancien
     */
    public void purgeOldHistory(int daysToKeep) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(daysToKeep);
        purgeOldData(cutoff);
    }

    public void purgeOldData(LocalDateTime cutoff) {
        for (PriceHistory history : historyCache.values()) {
            List<PriceDataPoint> dataPoints = history.getDataPoints();
            
            List<PriceDataPoint> filtered = new ArrayList<>();
            for (PriceDataPoint point : dataPoints) {
                if (point.getTimestamp().isAfter(cutoff)) {
                    filtered.add(point);
                }
            }
            
            if (filtered.size() < dataPoints.size()) {
                history.setDataPoints(filtered);
            }
        }
    }
    
    private String getHistoryKey(String shopId, String itemId) {
        return shopId + ":" + itemId;
    }

    public void savePriceHistory(PriceHistory history) {
        String key = getHistoryKey(history.getShopId(), history.getItemId());
        historyCache.put(key, history);
        
        // Sauvegarder immédiatement
        CompletableFuture.runAsync(() -> {
            File shopFolder = new File(baseFolder, history.getShopId());
            if (!shopFolder.exists()) {
                shopFolder.mkdirs();
            }
            
            File itemFile = new File(shopFolder, history.getItemId() + ".json");
            try {
                JsonStorage.saveToFile(itemFile, history.getDataPoints());
            } catch (IOException e) {
                // Log error if needed
            }
        });
    }
}