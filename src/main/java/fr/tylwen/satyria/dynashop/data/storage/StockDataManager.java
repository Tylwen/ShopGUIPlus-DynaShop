package fr.tylwen.satyria.dynashop.data.storage;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.google.gson.reflect.TypeToken;

public class StockDataManager {

    private final File file;
    private Map<String, Integer> stockLevels = new HashMap<>();

    public StockDataManager(File file) {
        this.file = file;
    }

    public void load() {
        try {
            Type type = new TypeToken<Map<String, Integer>>(){}.getType();
            stockLevels = JsonStorage.loadFromFile(file, type, new HashMap<>());
        } catch (Exception e) {
            stockLevels = new HashMap<>();
        }
    }

    public void save() {
        // try {
        //     JsonStorage.saveToFile(file, stockLevels);
        // } catch (Exception e) {
        //     // log
        // }
        CompletableFuture.runAsync(() -> {
            try {
                JsonStorage.saveToFile(file, stockLevels);
            } catch (IOException e) {
                // plugin.getLogger().severe("Erreur lors de la sauvegarde des niveaux de stock : " + e.getMessage());
            }
        });
    }

    public Integer getStock(String key) {
        return stockLevels.get(key);
    }

    public void setStock(String key, Integer level) {
        stockLevels.put(key, level);
    }

    public Map<String, Integer> getAll() {
        return stockLevels;
    }
}
