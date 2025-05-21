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
import net.brcdev.shopgui.ShopGuiPlusApi;
// import fr.tylwen.satyria.dynashop.database.BatchDatabaseUpdater;
import net.brcdev.shopgui.shop.item.ShopItem;

import org.bukkit.Bukkit;

import java.util.HashMap;
import java.util.Map;
// import java.util.concurrent.CompletableFuture;

public class DynamicPricesTask implements Runnable {
    private final DynaShopPlugin plugin;
    
    public DynamicPricesTask(DynaShopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        try {
            // Vérifier que ShopGUIPlus est bien initialisé et que les shops sont chargés
            if (ShopGuiPlusApi.getPlugin() == null || 
                ShopGuiPlusApi.getPlugin().getShopManager() == null) {
                plugin.getLogger().warning("ShopGUIPlus n'est pas encore complètement initialisé. La tâche DynamicPricesTask sera exécutée la prochaine fois.");
                return;
            }
            
            try {
                // Vérifier explicitement si les shops sont chargés
                if (ShopGuiPlusApi.getPlugin().getShopManager().getShops().isEmpty()) {
                    plugin.getLogger().warning("Les shops de ShopGUIPlus ne sont pas encore chargés. La tâche DynamicPricesTask sera exécutée la prochaine fois.");
                    return;
                }
            } catch (net.brcdev.shopgui.exception.shop.ShopsNotLoadedException e) {
                plugin.getLogger().warning("Les shops de ShopGUIPlus ne sont pas encore chargés (exception). La tâche DynamicPricesTask sera exécutée la prochaine fois.");
                return;
            }

            // plugin.getLogger().warning("######### DÉBUT DE LA TÂCHE DYNAMICPRICESTASK #########");
            // plugin.getLogger().warning("Démarrage de la tâche de mise à jour des prix...");

            // Au lieu de charger tous les prix synchrones, on fait la requête dans un thread séparé
            plugin.getDataManager().executeAsync(() -> {
                // Charger les prix depuis la base de données (fait en thread asynchrone)
                Map<ShopItem, DynamicPrice> priceMap = plugin.getDataManager().loadPricesFromDatabase();
                if (priceMap == null || priceMap.isEmpty()) {
                    // plugin.getLogger().info("Aucun prix à mettre à jour (priceMap vide ou null)");
                    return null;
                }
                
                // plugin.getLogger().info("Nombre d'items chargés depuis la base de données: " + priceMap.size());
                
                // Traiter les données en thread asynchrone
                Map<String, DynamicPrice> pricesToUpdate = new HashMap<>();
                // int itemsSkippedDueToStock = 0;
                
                for (Map.Entry<ShopItem, DynamicPrice> entry : priceMap.entrySet()) {
                    ShopItem item = entry.getKey();
                    DynamicPrice price = entry.getValue();
                    
                    String shopID = item.getShop().getId();
                    String itemID = item.getId();

                    int stock = entry.getValue().getStock();
                    if (stock > 0) {
                        // itemsSkippedDueToStock++;
                        // Si le stock est supérieur à 0, on ne met pas à jour le prix
                        continue;
                    }
                    
                    boolean needsUpdate = false;
                    double oldBuyPrice = price.getBuyPrice();
                    double oldSellPrice = price.getSellPrice();
                    
                    // // Vérifier si l'item utilise le système de prix dynamique
                    // if (plugin.getShopConfigManager().hasSection(shopID, itemID, "buyDynamic") && plugin.getShopConfigManager().getTypeDynaShop(shopID, itemID) == DynaShopType.DYNAMIC) {
                    //     price.applyBuyPriceChanges();
                    //     if (Math.abs(oldBuyPrice - price.getBuyPrice()) > 0.001) {
                    //         needsUpdate = true;
                    //         plugin.getLogger().info("Prix d'achat modifié pour " + shopID + ":" + itemID + " de " + oldBuyPrice + " à " + price.getBuyPrice());
                    //     }
                    // }
                    
                    // if (plugin.getShopConfigManager().hasSection(shopID, itemID, "sellDynamic") && plugin.getShopConfigManager().getTypeDynaShop(shopID, itemID) == DynaShopType.DYNAMIC) {
                    //     price.applySellPriceChanges();
                    //     if (Math.abs(oldSellPrice - price.getSellPrice()) > 0.001) {
                    //         needsUpdate = true;
                    //         plugin.getLogger().info("Prix de vente modifié pour " + shopID + ":" + itemID + " de " + oldSellPrice + " à " + price.getSellPrice());
                    //     }
                    // }
                    
                    // Vérifier si l'item utilise le système de prix dynamique
                    if (plugin.getShopConfigManager().hasSection(shopID, itemID, "buyDynamic") && 
                        plugin.getShopConfigManager().getTypeDynaShop(shopID, itemID) == DynaShopType.DYNAMIC) {
                        
                        // Récupérer les limites min et max pour l'achat
                        double minBuy = plugin.getShopConfigManager().getItemValue(shopID, itemID, "buyDynamic.min", Double.class).orElse(0.0);
                        double maxBuy = plugin.getShopConfigManager().getItemValue(shopID, itemID, "buyDynamic.max", Double.class).orElse(Double.MAX_VALUE);
                        
                        // Appliquer les changements de prix
                        price.applyBuyPriceChanges();
                        
                        // Appliquer les limites min/max
                        double newBuyPrice = price.getBuyPrice();
                        if (newBuyPrice < minBuy) {
                            price.setBuyPrice(minBuy);
                            // plugin.getLogger().info("Prix d'achat limité au minimum pour " + shopID + ":" + itemID + " (" + newBuyPrice + " -> " + minBuy + ")");
                        } else if (newBuyPrice > maxBuy) {
                            price.setBuyPrice(maxBuy);
                            // plugin.getLogger().info("Prix d'achat limité au maximum pour " + shopID + ":" + itemID + " (" + newBuyPrice + " -> " + maxBuy + ")");
                        }
                        
                        // Vérifier si le prix a changé
                        if (Math.abs(oldBuyPrice - price.getBuyPrice()) > 0.001) {
                            needsUpdate = true;
                            // plugin.getLogger().info("Prix d'achat modifié pour " + shopID + ":" + itemID + " de " + oldBuyPrice + " à " + price.getBuyPrice());
                        }
                    }
                    
                    if (plugin.getShopConfigManager().hasSection(shopID, itemID, "sellDynamic") && 
                        plugin.getShopConfigManager().getTypeDynaShop(shopID, itemID) == DynaShopType.DYNAMIC) {
                        
                        // Récupérer les limites min et max pour la vente
                        double minSell = plugin.getShopConfigManager().getItemValue(shopID, itemID, "sellDynamic.min", Double.class).orElse(0.0);
                        double maxSell = plugin.getShopConfigManager().getItemValue(shopID, itemID, "sellDynamic.max", Double.class).orElse(Double.MAX_VALUE);
                        
                        // Appliquer les changements de prix
                        price.applySellPriceChanges();
                        
                        // Appliquer les limites min/max
                        double newSellPrice = price.getSellPrice();
                        if (newSellPrice < minSell) {
                            price.setSellPrice(minSell);
                            // plugin.getLogger().info("Prix de vente limité au minimum pour " + shopID + ":" + itemID + " (" + newSellPrice + " -> " + minSell + ")");
                        } else if (newSellPrice > maxSell) {
                            price.setSellPrice(maxSell);
                            // plugin.getLogger().info("Prix de vente limité au maximum pour " + shopID + ":" + itemID + " (" + newSellPrice + " -> " + maxSell + ")");
                        }
                        
                        // Vérifier si le prix a changé
                        if (Math.abs(oldSellPrice - price.getSellPrice()) > 0.001) {
                            needsUpdate = true;
                            // plugin.getLogger().info("Prix de vente modifié pour " + shopID + ":" + itemID + " de " + oldSellPrice + " à " + price.getSellPrice());
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
                
                // plugin.getLogger().info("Items ignorés à cause du stock: " + itemsSkippedDueToStock);
                // plugin.getLogger().info("Nombre d'items à mettre à jour: " + pricesToUpdate.size());
                
                // Envoyer toutes les mises à jour à la base de données via le BatchDatabaseUpdater
                for (Map.Entry<String, DynamicPrice> entry : pricesToUpdate.entrySet()) {
                    String[] parts = entry.getKey().split(":");
                    String shopID = parts[0];
                    String itemID = parts[1];
                    DynamicPrice price = entry.getValue();
                    
                    // plugin.getLogger().info("Envoi de la mise à jour pour " + shopID + ":" + itemID + " avec buyPrice=" + price.getBuyPrice() + ", sellPrice=" + price.getSellPrice());
                    
                    plugin.getBatchDatabaseUpdater().queueUpdate(shopID, itemID, price);
                }
                
                // if (!pricesToUpdate.isEmpty()) {
                //     // plugin.getLogger().info("Mise à jour de " + pricesToUpdate.size() + " prix planifiée");
                // }
                
                return null;
            });
            // plugin.getLogger().warning("######### FIN DE LA TÂCHE DYNAMICPRICESTASK #########");
        } catch (Exception e) {
            plugin.getLogger().severe("ERREUR CRITIQUE dans DynamicPricesTask: " + e.getMessage());
            e.printStackTrace();
        }
    }
}