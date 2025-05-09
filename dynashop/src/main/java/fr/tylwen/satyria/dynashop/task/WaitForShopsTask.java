package fr.tylwen.satyria.dynashop.task;

import fr.tylwen.satyria.dynashop.DynaShopPlugin;

import net.brcdev.shopgui.ShopGuiPlusApi;
import net.brcdev.shopgui.exception.shop.ShopsNotLoadedException;
import net.brcdev.shopgui.shop.ShopManager;

public class WaitForShopsTask implements Runnable {
    private final DynaShopPlugin plugin;

    public WaitForShopsTask(DynaShopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        ShopManager manager = ShopGuiPlusApi.getPlugin().getShopManager();
        try {
            if (manager.getShops().isEmpty()) {
                plugin.getLogger().info("Les shops ne sont pas encore chargés. Nouvelle tentative dans 5 secondes...");
                return; // Les shops ne sont pas encore prêts
            }
        } catch (ShopsNotLoadedException e) {
            e.printStackTrace();
            plugin.getLogger().info("Les shops ne sont pas encore chargés. Nouvelle tentative dans 5 secondes...");
            return; // Les shops ne sont pas encore prêts
        }

        plugin.getShopConfigManager().initPricesFromShopConfigs();
        plugin.getLogger().info("Les shops ont été chargés avec succès !");
        plugin.getServer().getScheduler().cancelTasks(plugin);
    }
}