package fr.tylwen.satyria.dynashop.data.storage;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.google.gson.reflect.TypeToken;

public class LimitRemainingAmountCacheDataManager {

    private final File file;
    private Map<String, Integer> limitRemainingAmount = new HashMap<>();

    public LimitRemainingAmountCacheDataManager(File file) {
        this.file = file;
    }

    public void load() {
        try {
            Type type = new TypeToken<Map<String, Integer>>(){}.getType();
            limitRemainingAmount = JsonStorage.loadFromFile(file, type, new HashMap<>());
        } catch (Exception e) {
            limitRemainingAmount = new HashMap<>();
        }
    }

    public void save() {
        // try {
        //     JsonStorage.saveToFile(file, limitRemainingAmount);
        // } catch (Exception e) {
        //     // log
        // }
        CompletableFuture.runAsync(() -> {
            try {
                JsonStorage.saveToFile(file, limitRemainingAmount);
            } catch (IOException e) {
                // plugin.getLogger().severe("Erreur lors de la sauvegarde des niveaux de stock : " + e.getMessage());
            }
        });
    }

    public Integer getRemaining(String key) {
        return limitRemainingAmount.get(key);
    }

    public void setRemaining(String key, Integer level) {
        limitRemainingAmount.put(key, level);
    }

    public Map<String, Integer> getAll() {
        return limitRemainingAmount;
    }
}
