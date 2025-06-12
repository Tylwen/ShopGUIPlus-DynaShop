package fr.tylwen.satyria.dynashop.data.storage;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.google.gson.reflect.TypeToken;

public class LimitNextAvailableTimeCacheDataManager {

    private final File file;
    private Map<String, Long> limitNextAvailableTime = new HashMap<>();

    public LimitNextAvailableTimeCacheDataManager(File file) {
        this.file = file;
    }

    public void load() {
        try {
            Type type = new TypeToken<Map<String, Long>>(){}.getType();
            limitNextAvailableTime = JsonStorage.loadFromFile(file, type, new HashMap<>());
        } catch (Exception e) {
            limitNextAvailableTime = new HashMap<>();
        }
    }

    public void save() {
        // try {
        //     JsonStorage.saveToFile(file, limitNextAvailableTime);
        // } catch (Exception e) {
        //     // log
        // }
        CompletableFuture.runAsync(() -> {
            try {
                JsonStorage.saveToFile(file, limitNextAvailableTime);
            } catch (IOException e) {
                // plugin.getLogger().severe("Erreur lors de la sauvegarde des niveaux de stock : " + e.getMessage());
            }
        });
    }

    public Long getNextAvailable(String key) {
        return limitNextAvailableTime.get(key);
    }

    public void setNextAvailable(String key, Long level) {
        limitNextAvailableTime.put(key, level);
    }

    public Map<String, Long> getAll() {
        return limitNextAvailableTime;
    }
}
