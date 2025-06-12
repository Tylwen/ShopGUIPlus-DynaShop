package fr.tylwen.satyria.dynashop.data.storage;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.google.gson.reflect.TypeToken;

import fr.tylwen.satyria.dynashop.price.DynamicPrice;

public class PriceDataManager {
    private final File file;
    private Map<String, DynamicPrice> prices = new HashMap<>();

    public PriceDataManager(File file) {
        this.file = file;
    }

    public void load() {
        try {
            Type type = new TypeToken<Map<String, DynamicPrice>>(){}.getType();
            prices = JsonStorage.loadFromFile(file, type, new HashMap<>());
        } catch (Exception e) {
            prices = new HashMap<>();
        }
    }

    public void save() {
        // try {
        //     JsonStorage.saveToFile(file, prices);
        // } catch (Exception e) {
        //     // log
        // }
        CompletableFuture.runAsync(() -> {
            try {
                JsonStorage.saveToFile(file, prices);
            } catch (IOException e) {
                // plugin.getLogger().severe("Erreur lors de la sauvegarde des prix : " + e.getMessage());
            }
        });
    }

    public DynamicPrice getPrice(String key) {
        return prices.get(key);
    }

    public void setPrice(String key, DynamicPrice price) {
        prices.put(key, price);
    }

    public Map<String, DynamicPrice> getAll() {
        return prices;
    }
}