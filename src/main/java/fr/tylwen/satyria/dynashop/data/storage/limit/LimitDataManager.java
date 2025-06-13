package fr.tylwen.satyria.dynashop.data.storage.limit;

import com.google.gson.reflect.TypeToken;

import fr.tylwen.satyria.dynashop.data.cache.LimitCacheEntry;
import fr.tylwen.satyria.dynashop.data.storage.JsonStorage;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class LimitDataManager {
    private final File file;
    private Map<String, LimitCacheEntry> cache = new HashMap<>();

    public LimitDataManager(File file) {
        this.file = file;
    }

    public void load() {
        try {
            Type type = new TypeToken<Map<String, LimitCacheEntry>>(){}.getType();
            cache = JsonStorage.loadFromFile(file, type, new HashMap<>());
        } catch (Exception e) {
            cache = new HashMap<>();
        }
    }

    public void save() {
        CompletableFuture.runAsync(() -> {
            try {
                JsonStorage.saveToFile(file, cache);
            } catch (IOException e) {
                // log si besoin
            }
        });
    }

    public LimitCacheEntry get(String key) {
        return cache.get(key);
    }

    public void set(String key, LimitCacheEntry value) {
        cache.put(key, value);
    }

    public Map<String, LimitCacheEntry> getAll() {
        return cache;
    }
}