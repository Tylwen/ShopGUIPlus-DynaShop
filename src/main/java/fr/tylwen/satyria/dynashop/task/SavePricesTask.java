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
import fr.tylwen.satyria.dynashop.data.param.DynaShopType;
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
        plugin.getLogger().info("Démarrage de la tâche de mise à jour des prix...");

        // Au lieu de charger tous les prix synchrones, on fait la requête dans un thread séparé
        plugin.getDataManager().executeAsync(() -> {
            // Charger les prix depuis la base de données (fait en thread asynchrone)
            Map<ShopItem, DynamicPrice> priceMap = plugin.getDataManager().loadPricesFromDatabase();
            if (priceMap == null || priceMap.isEmpty()) {
                plugin.getLogger().info("Aucun prix à mettre à jour (priceMap vide ou null)");
                return null;
            }
            
            plugin.getLogger().info("Nombre d'items chargés depuis la base de données: " + priceMap.size());
            
            // Traiter les données en thread asynchrone
            Map<String, DynamicPrice> pricesToUpdate = new HashMap<>();
            int itemsSkippedDueToStock = 0;
            
            for (Map.Entry<ShopItem, DynamicPrice> entry : priceMap.entrySet()) {
                ShopItem item = entry.getKey();
                DynamicPrice price = entry.getValue();
                
                String shopID = item.getShop().getId();
                String itemID = item.getId();

                int stock = entry.getValue().getStock();
                if (stock > 0) {
                    itemsSkippedDueToStock++;
                    // Si le stock est supérieur à 0, on ne met pas à jour le prix
                    continue;
                }
                
                boolean needsUpdate = false;
                double oldBuyPrice = price.getBuyPrice();
                double oldSellPrice = price.getSellPrice();
                
                // Vérifier si l'item utilise le système de prix dynamique
                if (plugin.getShopConfigManager().hasSection(shopID, itemID, "buyDynamic") && plugin.getShopConfigManager().getTypeDynaShop(shopID, itemID) == DynaShopType.DYNAMIC) {
                    price.applyBuyPriceChanges();
                    if (Math.abs(oldBuyPrice - price.getBuyPrice()) > 0.001) {
                        needsUpdate = true;
                        plugin.getLogger().info("Prix d'achat modifié pour " + shopID + ":" + itemID + " de " + oldBuyPrice + " à " + price.getBuyPrice());
                    }
                }
                
                if (plugin.getShopConfigManager().hasSection(shopID, itemID, "sellDynamic") && plugin.getShopConfigManager().getTypeDynaShop(shopID, itemID) == DynaShopType.DYNAMIC) {
                    price.applySellPriceChanges();
                    if (Math.abs(oldSellPrice - price.getSellPrice()) > 0.001) {
                        needsUpdate = true;
                        plugin.getLogger().info("Prix de vente modifié pour " + shopID + ":" + itemID + " de " + oldSellPrice + " à " + price.getSellPrice());
                    }
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
            
            plugin.getLogger().info("Items ignorés à cause du stock: " + itemsSkippedDueToStock);
            plugin.getLogger().info("Nombre d'items à mettre à jour: " + pricesToUpdate.size());
            
            // Envoyer toutes les mises à jour à la base de données via le BatchDatabaseUpdater
            for (Map.Entry<String, DynamicPrice> entry : pricesToUpdate.entrySet()) {
                String[] parts = entry.getKey().split(":");
                String shopID = parts[0];
                String itemID = parts[1];
                DynamicPrice price = entry.getValue();
                
                plugin.getLogger().info("Envoi de la mise à jour pour " + shopID + ":" + itemID + 
                                     " avec buyPrice=" + price.getBuyPrice() + 
                                     ", sellPrice=" + price.getSellPrice());
                
                plugin.getBatchDatabaseUpdater().queueUpdate(shopID, itemID, price);
            }
            
            // if (!pricesToUpdate.isEmpty()) {
            //     // plugin.getLogger().info("Mise à jour de " + pricesToUpdate.size() + " prix planifiée");
            // }
            
            return null;
        });
    }
}