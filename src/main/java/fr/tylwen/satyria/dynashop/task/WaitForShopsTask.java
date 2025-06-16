/*
 * ShopGUI+ DynaShop - Dynamic Economy Addon for Minecraft
 * Copyright (C) 2025 Tylwen
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package fr.tylwen.satyria.dynashop.task;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
// import java.util.Map;

import org.bukkit.scheduler.BukkitTask;

import fr.tylwen.satyria.dynashop.DynaShopPlugin;
import fr.tylwen.satyria.dynashop.config.DataConfig;
import fr.tylwen.satyria.dynashop.price.DynamicPrice;
import fr.tylwen.satyria.dynashop.system.chart.PriceHistory;
import fr.tylwen.satyria.dynashop.system.chart.PriceHistory.PriceDataPoint;
import net.brcdev.shopgui.ShopGuiPlusApi;
import net.brcdev.shopgui.exception.shop.ShopsNotLoadedException;
import net.brcdev.shopgui.shop.Shop;
import net.brcdev.shopgui.shop.ShopManager;
import net.brcdev.shopgui.shop.item.ShopItem;

public class WaitForShopsTask implements Runnable {
    private final DynaShopPlugin plugin;
    private boolean isTasksInitialized = false;
    private final DataConfig dataConfig;
    // private int taskId = -1; // Stocker l'ID ici
    private BukkitTask selfTask; // Référence directe à la tâche

    public WaitForShopsTask(DynaShopPlugin plugin) {
        this.plugin = plugin;
        this.dataConfig = plugin.getDataConfig();
    }
    
    // Méthode pour définir la référence à la tâche
    public void setSelfTask(BukkitTask task) {
        this.selfTask = task;
    }

    @Override
    public void run() {
        try {
            // // Si c'est la première exécution, essayer de récupérer notre propre ID
            // if (taskId == -1) {
            //     for (BukkitTask task : plugin.getServer().getScheduler().getPendingTasks()) {
            //         if (task.getOwner() == plugin && task.getTaskId() == task.getTaskId()) {
            //             taskId = task.getTaskId();
            //             break;
            //         }
            //     }
            // }
            // Vérifier d'abord si ShopGUIPlus est correctement initialisé
            if (ShopGuiPlusApi.getPlugin() == null) {
                plugin.getLogger().severe("ShopGUIPlus is not initialized. Try again in 5 seconds...");
                return; // Arrêter l'exécution si ShopGUIPlus n'est pas prêt
            }
            ShopManager manager = ShopGuiPlusApi.getPlugin().getShopManager();
            if (manager.getShops().isEmpty()) {
                plugin.getLogger().info("Shops are not loaded yet. Try again in 5 seconds...");
                return; // Les shops ne sont pas encore prêts
            }
            
            // Si les shops sont chargés mais que nous n'avons pas encore initialisé les tâches
            if (!isTasksInitialized) {
                // Initialiser les prix à partir des configurations des shops
                plugin.getShopConfigManager().initPricesFromShopConfigs();
                plugin.getStorageManager().cleanupStockTable();
                
                // Démarrer les tâches qui dépendent des shops
                startDependentTasks();
                
                // Marquer les tâches comme initialisées pour ne pas les redémarrer
                isTasksInitialized = true;
                
                plugin.getLogger().info("Shops loaded successfully! All dependent tasks have been started.");
                // // Annuler cette tâche une fois que tout est initialisé
                // if (taskId != -1) {
                //     plugin.getServer().getScheduler().cancelTask(taskId);
                //     // plugin.getLogger().info("WaitForShopsTask cancelled successfully (ID: " + taskId + ")");
                // }
                
                // Annuler la tâche en utilisant la référence directe
                if (selfTask != null) {
                    selfTask.cancel();
                    // plugin.getLogger().info("WaitForShopsTask cancelled successfully");
                } else {
                    plugin.getLogger().warning("Could not cancel WaitForShopsTask: reference is null");
                }
            }
            
            // // Ne pas annuler cette tâche si elle est encore nécessaire pour d'autres vérifications
            // plugin.getServer().getScheduler().cancelTask(plugin.getWaitForShopsTaskId());
        } catch (ShopsNotLoadedException e) {
            plugin.getLogger().info("Shops are not loaded yet. Try again in 5 seconds...");
        } catch (Exception e) {
            plugin.getLogger().severe("Error checking shop status: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Démarre toutes les tâches qui dépendent des shops.
     */
    private void startDependentTasks() {
        final int INTERVAL_MINUTES = plugin.getConfigMain().getInt("history.save-interval", 15);
        
        // Démarrer DynamicPricesTask
        plugin.getLogger().info("Starting DynamicPricesTask...");
        plugin.setDynamicPricesTaskId(plugin.getServer().getScheduler().runTaskTimerAsynchronously(
            plugin, 
            new DynamicPricesTask(plugin), 
            20L * 10L, // Délai initial de 10 secondes
            20L * 60L * dataConfig.getDynamicPriceDuration() // Durée de X minutes
        ).getTaskId());
        
        // plugin.getLogger().info("DynamicPricesTask registered with ID: " + plugin.getDynamicPricesTaskId());
        
        // // Préchargement des recettes populaires
        // plugin.getLogger().info("Preloading popular items...");
        // plugin.getServer().getScheduler().runTaskAsynchronously(plugin, plugin::preloadPopularItems);
        
        // Autres tâches dépendantes...
        plugin.getServer().getScheduler().runTask(plugin, plugin::setupMetrics);

        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            try {
                for (Shop shop : ShopGuiPlusApi.getPlugin().getShopManager().getShops()) {
                    for (ShopItem item : shop.getShopItems()) {
                        try {
                            DynamicPrice price = plugin.getDynaShopListener().getOrLoadPrice(null, shop.getId(), item.getId(), item.getItem(), new HashSet<>(), new HashMap<>());
                            if (price != null) {
                                // Récupérer l'historique existant
                                PriceHistory history = plugin.getStorageManager().getPriceHistory(shop.getId(), item.getId());
                                
                                // Ajouter un nouveau point toutes les heures
                                LocalDateTime now = LocalDateTime.now();
                                if (history.getDataPoints().isEmpty() || history.getDataPoints().get(history.getDataPoints().size() - 1).getTimestamp().plusMinutes(INTERVAL_MINUTES).isBefore(now)) {

                                    PriceDataPoint dataPoint = new PriceDataPoint(
                                        now, 
                                        price.getBuyPrice(), price.getBuyPrice(), price.getBuyPrice(), price.getBuyPrice(),
                                        price.getSellPrice(), price.getSellPrice(), price.getSellPrice(), price.getSellPrice(),
                                        0
                                    );

                                    history.addDataPoint(dataPoint);
                                    plugin.getStorageManager().savePriceDataPoint(shop.getId(), item.getId(), dataPoint, INTERVAL_MINUTES);

                                    // plugin.getLogger().info("Point d'historique ajouté pour " + shop.getId() + ":" + item.getId());
                                }
                            }
                        } catch (Exception e) {
                            plugin.getLogger().warning("Erreur lors de l'enregistrement de l'historique pour " + shop.getId() + ":" + item.getId() + ": " + e.getMessage());
                        }
                    }
                }
            } catch (ShopsNotLoadedException e) {
                e.printStackTrace();
            }
        }, 20 * 60 * 5, 20 * 60 * 15); // Démarrer après 5 minutes, puis toutes les 15 minutes
    }
}