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
import fr.tylwen.satyria.dynashop.data.DynamicPrice;

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
                    plugin.getLogger().severe("Error updating prices: " + e.getMessage());
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
        
        // plugin.getLogger().info("Prix mis en file d'attente pour mise à jour: " + shopID + ":" + itemID + " Buy: " + buyPrice + ", Sell: " + sellPrice);
    }
    
    // private void flushUpdates() {
    //     if (pendingUpdates.isEmpty()) return;
        
    //     Map<String, DynamicPrice> updates = new HashMap<>(pendingUpdates);
    //     pendingUpdates.clear();
        
    //     try (Connection connection = plugin.getDataManager().getConnection();
    //          PreparedStatement stmt = connection.prepareStatement(
    //              "REPLACE INTO " + plugin.getDataConfig().getDatabaseTablePrefix() + "_prices (shopID, itemID, buyPrice, sellPrice, stock) VALUES (?, ?, ?, ?, ?)"
    //          )) {
            
    //         for (Map.Entry<String, DynamicPrice> entry : updates.entrySet()) {
    //             String[] parts = entry.getKey().split(":");
    //             DynamicPrice price = entry.getValue();
                
    //             stmt.setString(1, parts[0]); // shopID
    //             stmt.setString(2, parts[1]); // itemID
    //             stmt.setDouble(3, price.getBuyPrice());
    //             stmt.setDouble(4, price.getSellPrice());
    //             stmt.setInt(5, price.getStock());
    //             stmt.addBatch();
    //         }
            
    //         stmt.executeBatch();
    //         // plugin.getLogger().info("Mise à jour de " + updates.size() + " prix en base de données");
    //     } catch (Exception e) {
    //         plugin.getLogger().severe("Error updating prices: " + e.getMessage());
    //         e.printStackTrace();
    //     }
    // }
    private void flushUpdates() {
        if (pendingUpdates.isEmpty()) return;
        
        Map<String, DynamicPrice> updates = new HashMap<>(pendingUpdates);
        pendingUpdates.clear();
        
        String tablePrefix = plugin.getDataConfig().getDatabaseTablePrefix();
        
        try (Connection connection = plugin.getDataManager().getConnection()) {
            // Préparer les requêtes pour chaque table
            String buyPriceSQL = "REPLACE INTO " + tablePrefix + "_buy_prices (shopID, itemID, price) VALUES (?, ?, ?)";
            String deleteBuySQL = "DELETE FROM " + tablePrefix + "_buy_prices WHERE shopID = ? AND itemID = ?";
            
            String sellPriceSQL = "REPLACE INTO " + tablePrefix + "_sell_prices (shopID, itemID, price) VALUES (?, ?, ?)";
            String deleteSellSQL = "DELETE FROM " + tablePrefix + "_sell_prices WHERE shopID = ? AND itemID = ?";
            
            String stockSQL = "REPLACE INTO " + tablePrefix + "_stock (shopID, itemID, stock) VALUES (?, ?, ?)";
            
            // Préparer les statements
            try (
                PreparedStatement buyStmt = connection.prepareStatement(buyPriceSQL);
                PreparedStatement deleteBuyStmt = connection.prepareStatement(deleteBuySQL);
                PreparedStatement sellStmt = connection.prepareStatement(sellPriceSQL);
                PreparedStatement deleteSellStmt = connection.prepareStatement(deleteSellSQL);
                PreparedStatement stockStmt = connection.prepareStatement(stockSQL)
            ) {
                for (Map.Entry<String, DynamicPrice> entry : updates.entrySet()) {
                    String[] parts = entry.getKey().split(":");
                    String shopID = parts[0];
                    String itemID = parts[1];
                    DynamicPrice price = entry.getValue();
                    
                    // Gérer le prix d'achat (buyPrice)
                    if (price.getBuyPrice() >= 0) {
                        buyStmt.setString(1, shopID);
                        buyStmt.setString(2, itemID);
                        buyStmt.setDouble(3, price.getBuyPrice());
                        buyStmt.addBatch();
                    } else {
                        // Supprimer l'entrée si le prix est négatif
                        deleteBuyStmt.setString(1, shopID);
                        deleteBuyStmt.setString(2, itemID);
                        deleteBuyStmt.addBatch();
                    }
                    
                    // Gérer le prix de vente (sellPrice)
                    if (price.getSellPrice() >= 0) {
                        sellStmt.setString(1, shopID);
                        sellStmt.setString(2, itemID);
                        sellStmt.setDouble(3, price.getSellPrice());
                        sellStmt.addBatch();
                    } else {
                        // Supprimer l'entrée si le prix est négatif
                        deleteSellStmt.setString(1, shopID);
                        deleteSellStmt.setString(2, itemID);
                        deleteSellStmt.addBatch();
                    }
                    
                    // Toujours mettre à jour le stock
                    stockStmt.setString(1, shopID);
                    stockStmt.setString(2, itemID);
                    stockStmt.setInt(3, price.getStock());
                    stockStmt.addBatch();
                }
                
                // Exécuter les lots
                buyStmt.executeBatch();
                deleteBuyStmt.executeBatch();
                sellStmt.executeBatch();
                deleteSellStmt.executeBatch();
                stockStmt.executeBatch();
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Error updating prices: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public void shutdown() {
        running.set(false);
        backgroundThread.interrupt();
        flushUpdates(); // Traiter les dernières mises à jour en attente
    }
}