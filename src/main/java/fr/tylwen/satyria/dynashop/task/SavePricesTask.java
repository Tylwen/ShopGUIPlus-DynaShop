// package fr.tylwen.satyria.dynashop.task;

// import fr.tylwen.satyria.dynashop.DynaShopPlugin;
// import fr.tylwen.satyria.dynashop.data.DynamicPrice;
// import fr.tylwen.satyria.dynashop.database.BatchDatabaseUpdater;
// import net.brcdev.shopgui.shop.item.ShopItem;

// import java.util.Map;

// public class SavePricesTask implements Runnable {
//     private final DynaShopPlugin plugin;
//     // private final BatchDatabaseUpdater batchDatabaseUpdater;

//     public SavePricesTask(DynaShopPlugin plugin) {
//         this.plugin = plugin;
//         // this.batchDatabaseUpdater = new BatchDatabaseUpdater(plugin);
//     }

//     @Override
//     public void run() {
//         Map<ShopItem, DynamicPrice> priceMap = plugin.getDataManager().loadPricesFromDatabase();
//         if (priceMap == null) {
//             return;
//         }

//         for (Map.Entry<ShopItem, DynamicPrice> entry : priceMap.entrySet()) {
//             ShopItem item = entry.getKey();
//             DynamicPrice price = entry.getValue();

//             String shopID = item.getShop().getId();
//             String itemID = item.getId();

//             if (plugin.getShopConfigManager().hasSection(shopID, itemID, "buyDynamic")) {
//                 price.applyBuyPriceChanges();
//                 item.setBuyPrice(price.getBuyPrice());
//             }
//             if (plugin.getShopConfigManager().hasSection(shopID, itemID, "sellDynamic")) {
//                 price.applySellPriceChanges();
//                 item.setSellPrice(price.getSellPrice());
//             }
//             // batchDatabaseUpdater.queueUpdate(shopID, itemID, price);
//         }

//         // plugin.getDataManager().savePricesToDatabase(priceMap);
//         plugin.getDataManager().savePrice(
//     }
// }

package fr.tylwen.satyria.dynashop.task;

import fr.tylwen.satyria.dynashop.DynaShopPlugin;
import fr.tylwen.satyria.dynashop.data.DynamicPrice;
// import fr.tylwen.satyria.dynashop.database.BatchDatabaseUpdater;
import net.brcdev.shopgui.shop.item.ShopItem;

import org.bukkit.Bukkit;

import java.util.HashMap;
import java.util.Map;
// import java.util.concurrent.CompletableFuture;

public class SavePricesTask implements Runnable {
    private final DynaShopPlugin plugin;
    
    public SavePricesTask(DynaShopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        // Au lieu de charger tous les prix synchrones, on fait la requête dans un thread séparé
        plugin.getDataManager().executeAsync(() -> {
            // Charger les prix depuis la base de données (fait en thread asynchrone)
            Map<ShopItem, DynamicPrice> priceMap = plugin.getDataManager().loadPricesFromDatabase();
            if (priceMap == null || priceMap.isEmpty()) {
                return null;
            }
            
            // Traiter les données en thread asynchrone
            Map<String, DynamicPrice> pricesToUpdate = new HashMap<>();
            
            for (Map.Entry<ShopItem, DynamicPrice> entry : priceMap.entrySet()) {
                ShopItem item = entry.getKey();
                DynamicPrice price = entry.getValue();
                
                String shopID = item.getShop().getId();
                String itemID = item.getId();

                int stock = entry.getValue().getStock();
                if (stock > 0) {
                    // Si le stock est supérieur à 0, on ne met pas à jour le prix
                    continue;
                }
                
                boolean needsUpdate = false;
                
                // Vérifier si l'item utilise le système de prix dynamique
                if (plugin.getShopConfigManager().hasSection(shopID, itemID, "buyDynamic")) {
                    price.applyBuyPriceChanges();
                    needsUpdate = true;
                }
                
                if (plugin.getShopConfigManager().hasSection(shopID, itemID, "sellDynamic")) {
                    price.applySellPriceChanges();
                    needsUpdate = true;
                }
                
                if (needsUpdate) {
                    // Ajouter à la liste des prix à mettre à jour
                    pricesToUpdate.put(shopID + ":" + itemID, price);
                    
                    // Mettre à jour le prix dans l'interface de shop
                    // Cette opération doit être faite dans le thread principal
                    final double buyPrice = price.getBuyPrice();
                    final double sellPrice = price.getSellPrice();
                    
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        item.setBuyPrice(buyPrice);
                        item.setSellPrice(sellPrice);
                    });
                }
            }
            
            // Envoyer toutes les mises à jour à la base de données via le BatchDatabaseUpdater
            for (Map.Entry<String, DynamicPrice> entry : pricesToUpdate.entrySet()) {
                String[] parts = entry.getKey().split(":");
                String shopID = parts[0];
                String itemID = parts[1];
                DynamicPrice price = entry.getValue();
                
                plugin.getBatchDatabaseUpdater().queueUpdate(shopID, itemID, price);
            }
            
            if (!pricesToUpdate.isEmpty()) {
                // plugin.getLogger().info("Mise à jour de " + pricesToUpdate.size() + " prix planifiée");
            }
            
            return null;
        });
    }
}