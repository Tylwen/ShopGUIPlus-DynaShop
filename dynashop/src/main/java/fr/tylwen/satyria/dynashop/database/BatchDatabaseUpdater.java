package fr.tylwen.satyria.dynashop.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

// import org.bukkit.Bukkit;

import fr.tylwen.satyria.dynashop.DynaShopPlugin;
import fr.tylwen.satyria.dynashop.data.price.DynamicPrice;

// public class BatchDatabaseUpdater {
//     private final Map<String, DynamicPrice> pendingUpdates = new HashMap<>();
//     private DynaShopPlugin plugin;
    
//     public BatchDatabaseUpdater(DynaShopPlugin plugin) {
//         this.plugin = plugin;
//         // Planifier des mises à jour régulières
//         Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::flushUpdates, 100L, 100L); // Toutes les 5 secondes
//     }
    
//     public void queueUpdate(String shopID, String itemID, DynamicPrice price) {
//         String key = shopID + ":" + itemID;
//         pendingUpdates.put(key, price);
//     }
    
//     private void flushUpdates() {
//         if (pendingUpdates.isEmpty()) return;
        
//         Map<String, DynamicPrice> updates = new HashMap<>(pendingUpdates);
//         pendingUpdates.clear();
        
//         for (Map.Entry<String, DynamicPrice> entry : updates.entrySet()) {
//             String[] parts = entry.getKey().split(":");
//             plugin.getItemDataManager().savePrice(parts[0], parts[1], entry.getValue().getBuyPrice(), entry.getValue().getSellPrice());
//         }
//     }
// }

public class BatchDatabaseUpdater {
    private final Map<String, DynamicPrice> pendingUpdates = new ConcurrentHashMap<>();
    private final DynaShopPlugin plugin;
    private final Thread backgroundThread;
    private final AtomicBoolean running = new AtomicBoolean(true);
    
    public BatchDatabaseUpdater(DynaShopPlugin plugin) {
        this.plugin = plugin;
        
        // Créer un thread dédié qui vérifie régulièrement les mises à jour en attente
        backgroundThread = new Thread(() -> {
            while (running.get()) {
                try {
                    flushUpdates();
                    Thread.sleep(1000); // Vérifier chaque seconde
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    plugin.getLogger().severe("Erreur lors de la mise à jour des prix : " + e.getMessage());
                }
            }
        });
        backgroundThread.setDaemon(true);
        backgroundThread.setName("DynaShop-DBUpdater");
        backgroundThread.start();
    }
    
    public void queueUpdate(String shopID, String itemID, DynamicPrice price) {
        String key = shopID + ":" + itemID;
        pendingUpdates.put(key, price);
    }

    /**
     * Ajoute une mise à jour de prix à la file d'attente sans créer un nouvel objet DynamicPrice
     * (utilisé principalement par PriceStock).
     *
     * @param shopID    L'ID du shop.
     * @param itemID    L'ID de l'item.
     * @param buyPrice  Le nouveau prix d'achat.
     * @param sellPrice Le nouveau prix de vente.
     */
    public void queuePriceUpdate(String shopID, String itemID, double buyPrice, double sellPrice) {
        // Récupérer l'objet price existant ou en créer un nouveau
        String key = shopID + ":" + itemID;
        
        DynamicPrice price = pendingUpdates.computeIfAbsent(key, k -> {
            // Si on n'a pas d'objet price en attente, récupérer les valeurs de stock actuelles
            Optional<Integer> stockOptional = plugin.getItemDataManager().getStock(shopID, itemID);
            int stock = stockOptional.orElse(0);
            
            return new DynamicPrice(buyPrice, sellPrice, stock);
        });
        
        // Mettre à jour les prix
        price.setBuyPrice(buyPrice);
        price.setSellPrice(sellPrice);
        
        // S'assurer que l'objet est dans la map des mises à jour en attente
        pendingUpdates.put(key, price);
        
        plugin.getLogger().info("Prix mis en file d'attente pour mise à jour: " + shopID + ":" + itemID + 
                            " Buy: " + buyPrice + ", Sell: " + sellPrice);
    }
    
    private void flushUpdates() {
        if (pendingUpdates.isEmpty()) return;
        
        Map<String, DynamicPrice> updates = new HashMap<>(pendingUpdates);
        pendingUpdates.clear();
        
        try (Connection connection = plugin.getDataManager().getDatabaseConnection();
             PreparedStatement stmt = connection.prepareStatement(
                 "REPLACE INTO " + plugin.getDataConfig().getDatabaseTablePrefix() + 
                 "_prices (shopID, itemID, buyPrice, sellPrice, stock) VALUES (?, ?, ?, ?, ?)"
             )) {
            
            for (Map.Entry<String, DynamicPrice> entry : updates.entrySet()) {
                String[] parts = entry.getKey().split(":");
                DynamicPrice price = entry.getValue();
                
                stmt.setString(1, parts[0]); // shopID
                stmt.setString(2, parts[1]); // itemID
                stmt.setDouble(3, price.getBuyPrice());
                stmt.setDouble(4, price.getSellPrice());
                stmt.setInt(5, price.getStock());
                stmt.addBatch();
            }
            
            stmt.executeBatch();
            plugin.getLogger().info("Mise à jour de " + updates.size() + " prix en base de données");
        } catch (Exception e) {
            plugin.getLogger().severe("Erreur lors de la mise à jour des prix : " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public void shutdown() {
        running.set(false);
        backgroundThread.interrupt();
        flushUpdates(); // Traiter les dernières mises à jour en attente
    }
}