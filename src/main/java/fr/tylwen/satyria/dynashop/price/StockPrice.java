package fr.tylwen.satyria.dynashop.price;

import java.util.Optional;

import org.bukkit.inventory.ItemStack;

import fr.tylwen.satyria.dynashop.DynaShopPlugin;
import fr.tylwen.satyria.dynashop.config.DataConfig;
import fr.tylwen.satyria.dynashop.data.param.DynaShopType;
import net.brcdev.shopgui.shop.item.ShopItem;

public class StockPrice implements DynaShopPrice {

    private final DynaShopPlugin plugin;
    private final DataConfig dataConfig;

    public StockPrice(DynaShopPlugin plugin) {
        this.plugin = plugin;
        this.dataConfig = plugin.getDataConfig();
    }

    @Override
    public DynaShopType getType() {
        return DynaShopType.STOCK;
    }

    @Override
    public boolean canBuy(String shopID, String itemID, int amount) {
        Optional<Integer> stockOptional = plugin.getItemDataManager().getStock(shopID, itemID);
        return stockOptional.map(stock -> stock >= amount).orElse(true);
    }

    @Override
    public boolean canSell(String shopID, String itemID, int amount) {
        Optional<Integer> stockOptional = plugin.getItemDataManager().getStock(shopID, itemID);
        int maxStock = plugin.getShopConfigManager()
            .getItemValue(shopID, itemID, "stock.max", Integer.class)
            .orElse(dataConfig.getStockMax());
        
        return stockOptional.map(stock -> stock + amount <= maxStock).orElse(true);
    }

    @Override
    public boolean canBuy(ShopItem item) {
        Optional<Integer> stockOptional = plugin.getItemDataManager().getStock(item);
        return stockOptional.map(stock -> stock >= item.getItem().getAmount()).orElse(true);
    }

    @Override
    public boolean canSell(ShopItem item) {
        Optional<Integer> stockOptional = plugin.getItemDataManager().getStock(item);
        int maxStock = plugin.getShopConfigManager()
            .getItemValue(item.getShop().getId(), item.getId(), "stock.max", Integer.class)
            .orElse(dataConfig.getStockMax());
        
        return stockOptional.map(stock -> stock + item.getItem().getAmount() <= maxStock).orElse(true);
    }

    @Override
    public double calculateBuyPrice(ShopItem item) {
        return 0;
    }

    @Override
    public double calculateSellPrice(ShopItem item) {
        return 0;
    }

    @Override
    public double calculateBuyPrice(String shopID, String itemID, ItemStack item) {
        return 0;
    }

    @Override
    public double calculateSellPrice(String shopID, String itemID, ItemStack item) {
        return 0;
    }

    @Override
    public void postSellTransaction(ShopItem item) {
        
    }

    @Override
    public void postBuyTransaction(ShopItem item) {
        
    }

    @Override
    public void postSellTransaction(String shopID, String itemID, ItemStack item) {
        
    }

    @Override
    public void postBuyTransaction(String shopID, String itemID, ItemStack item) {
        
    }
    
}
