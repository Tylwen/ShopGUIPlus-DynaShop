// package fr.tylwen.satyria.dynashop.task;

// import fr.tylwen.satyria.dynashop.DynaShopPlugin;

// import net.brcdev.shopgui.ShopGuiPlusApi;
// import net.brcdev.shopgui.exception.shop.ShopsNotLoadedException;
// import net.brcdev.shopgui.shop.ShopManager;

// public class WaitForShopsTask implements Runnable {
//     private final DynaShopPlugin plugin;

//     public WaitForShopsTask(DynaShopPlugin plugin) {
//         this.plugin = plugin;
//     }

//     @Override
//     public void run() {
//         ShopManager manager = ShopGuiPlusApi.getPlugin().getShopManager();
//         try {
//             if (manager.getShops().isEmpty()) {
//                 plugin.getLogger().info("Shops are not loaded yet. Try again in 5 seconds...");
//                 return; // Les shops ne sont pas encore prêts
//             }
//         } catch (ShopsNotLoadedException e) {
//             e.printStackTrace();
//             plugin.getLogger().info("Shops are not loaded yet. Try again in 5 seconds...");
//             return; // Les shops ne sont pas encore prêts
//         }

//         plugin.getShopConfigManager().initPricesFromShopConfigs();
//         plugin.getLogger().info("Shops loaded successfully!");
//         plugin.getServer().getScheduler().cancelTasks(plugin);
//     }
// }
package fr.tylwen.satyria.dynashop.task;

import fr.tylwen.satyria.dynashop.DynaShopPlugin;
import fr.tylwen.satyria.dynashop.config.DataConfig;
import net.brcdev.shopgui.ShopGuiPlusApi;
import net.brcdev.shopgui.exception.shop.ShopsNotLoadedException;
import net.brcdev.shopgui.shop.ShopManager;

public class WaitForShopsTask implements Runnable {
    private final DynaShopPlugin plugin;
    private boolean isTasksInitialized = false;
    private final DataConfig dataConfig;

    public WaitForShopsTask(DynaShopPlugin plugin) {
        this.plugin = plugin;
        this.dataConfig = plugin.getDataConfig();
    }

    @Override
    public void run() {
        try {
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
                
                // Démarrer les tâches qui dépendent des shops
                startDependentTasks();
                
                // Marquer les tâches comme initialisées pour ne pas les redémarrer
                isTasksInitialized = true;
                
                plugin.getLogger().info("Shops loaded successfully! All dependent tasks have been started.");
            }
            
            // Ne pas annuler cette tâche si elle est encore nécessaire pour d'autres vérifications
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
        // Démarrer DynamicPricesTask
        plugin.getLogger().info("Starting DynamicPricesTask...");
        plugin.setDynamicPricesTaskId(plugin.getServer().getScheduler().runTaskTimerAsynchronously(
            plugin, 
            new DynamicPricesTask(plugin), 
            20L * 10L, // Délai initial de 10 secondes
            20L * 60L * dataConfig.getDynamicPriceDuration() // Durée de X minutes
        ).getTaskId());
        
        // plugin.getLogger().info("DynamicPricesTask registered with ID: " + plugin.getDynamicPricesTaskId());
        
        // Préchargement des recettes populaires
        plugin.getLogger().info("Preloading popular items...");
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, plugin::preloadPopularItems);
        
        // Autres tâches dépendantes...
    }
}