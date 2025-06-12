package fr.tylwen.satyria.dynashop.data.storage;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.google.gson.reflect.TypeToken;

import fr.tylwen.satyria.dynashop.system.TransactionLimiter.TransactionLimit;

public class LimitDataManager {
    private final File file;
    private Map<String, TransactionLimit> limits = new HashMap<>();

    public LimitDataManager(File file) {
        this.file = file;
    }

    public void load() {
        try {
            Type type = new TypeToken<Map<String, TransactionLimit>>(){}.getType();
            limits = JsonStorage.loadFromFile(file, type, new HashMap<>());
        } catch (Exception e) {
            limits = new HashMap<>();
        }
    }

    public void save() {
        // try {
        //     JsonStorage.saveToFile(file, limits);
        // } catch (Exception e) {
        //     // log
        // }
        CompletableFuture.runAsync(() -> {
            try {
                JsonStorage.saveToFile(file, limits);
            } catch (IOException e) {
                // plugin.getLogger().severe("Erreur lors de la sauvegarde des prix : " + e.getMessage());
            }
        });
    }

    public TransactionLimit getLimit(String key) {
        return limits.get(key);
    }

    public void setLimit(String key, TransactionLimit limit) {
        limits.put(key, limit);
    }

    public Map<String, TransactionLimit> getAll() {
        return limits;
    }
}
