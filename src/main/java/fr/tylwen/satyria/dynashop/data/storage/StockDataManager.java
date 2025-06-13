package fr.tylwen.satyria.dynashop.data.storage;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.google.gson.reflect.TypeToken;

public class StockDataManager {

    private final File file;
    private Map<String, Integer> stock = new HashMap<>();
    private final Object lock = new Object();

    public StockDataManager(File file) {
        this.file = file;
    }

    // public void load() {
    //     try {
    //         Type type = new TypeToken<Map<String, Integer>>(){}.getType();
    //         stock = JsonStorage.loadFromFile(file, type, new HashMap<>());
    //     } catch (Exception e) {
    //         stock = new HashMap<>();
    //     }
    // }
    public void load() {
        synchronized(lock) {
            try {
                if (file.exists()) {
                    Map<String, Integer> loadedData = JsonStorage.loadFromFile(file, 
                        new TypeToken<Map<String, Integer>>(){}.getType(), 
                        new HashMap<>());
                    
                    // Protéger contre les valeurs nulles
                    if (loadedData != null) {
                        this.stock = Collections.synchronizedMap(new HashMap<>(loadedData));
                    } else {
                        this.stock = Collections.synchronizedMap(new HashMap<>());
                    }
                } else {
                    this.stock = Collections.synchronizedMap(new HashMap<>());
                }
            } catch (Exception e) {
                this.stock = Collections.synchronizedMap(new HashMap<>());
            }
        }
    }

    // public void save() {
    //     // try {
    //     //     JsonStorage.saveToFile(file, stock);
    //     // } catch (Exception e) {
    //     //     // log
    //     // }
    //     CompletableFuture.runAsync(() -> {
    //         try {
    //             JsonStorage.saveToFile(file, stock);
    //         } catch (IOException e) {
    //             // plugin.getLogger().severe("Erreur lors de la sauvegarde des niveaux de stock : " + e.getMessage());
    //         }
    //     });
    // }
    public void save() {
        CompletableFuture.runAsync(() -> {
            synchronized(lock) {
                try {
                    // Utiliser une copie pour éviter les modifications pendant la sauvegarde
                    Map<String, Integer> stockCopy = new HashMap<>(stock);
                    JsonStorage.saveToFile(file, stockCopy);
                } catch (IOException e) {
                    // Gérer l'erreur
                }
            }
        });
    }

    // public Integer get(String key) {
    //     return stock.get(key);
    // }
    public Integer get(String key) {
        synchronized(lock) {
            return stock.get(key);
        }
    }

    // public void set(String key, Integer level) {
    //     stock.put(key, level);
    // }
    public void set(String key, Integer value) {
        synchronized(lock) {
            stock.put(key, value);
        }
    }

    // public Map<String, Integer> getAll() {
    //     return stock;
    // }
    public Map<String, Integer> getAll() {
        synchronized(lock) {
            // Retourner une copie défensive
            return new HashMap<>(stock);
        }
    }

    // public void remove(String key) {
    //     stock.remove(key);
    //     save(); // Sauvegarde après suppression
    // }
    public void remove(String key) {
        synchronized(lock) {
            stock.remove(key);
        }
    }
}
