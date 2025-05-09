package fr.tylwen.satyria.dynashop.task;

import fr.tylwen.satyria.dynashop.DynaShopPlugin;
import fr.tylwen.satyria.dynashop.data.price.DynamicPrice;
import net.brcdev.shopgui.shop.item.ShopItem;

import java.util.Map;

public class SavePricesTask implements Runnable {
    private final DynaShopPlugin plugin;

    public SavePricesTask(DynaShopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        Map<ShopItem, DynamicPrice> priceMap = plugin.getDataManager().loadPricesFromDatabase();
        if (priceMap == null) {
            return;
        }

        for (Map.Entry<ShopItem, DynamicPrice> entry : priceMap.entrySet()) {
            ShopItem item = entry.getKey();
            DynamicPrice price = entry.getValue();

            String shopID = item.getShop().getId();
            String itemID = item.getId();

            if (plugin.getShopConfigManager().hasSection(shopID, itemID, "buyDynamic")) {
                price.applyBuyPriceChanges();
                item.setBuyPrice(price.getBuyPrice());
            }
            if (plugin.getShopConfigManager().hasSection(shopID, itemID, "sellDynamic")) {
                price.applySellPriceChanges();
                item.setSellPrice(price.getSellPrice());
            }
        }

        plugin.getDataManager().savePricesToDatabase(priceMap);
    }
}