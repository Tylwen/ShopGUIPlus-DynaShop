package fr.tylwen.satyria.dynashop.price;

import org.bukkit.inventory.ItemStack;

import fr.tylwen.satyria.dynashop.data.param.DynaShopType;
import net.brcdev.shopgui.shop.item.ShopItem;

public interface DynaShopPrice {
    
    DynaShopType getType();

    boolean canBuy(ShopItem item);
    boolean canSell(ShopItem item);
    boolean canBuy(String shopID, String itemID, int amount);
    boolean canSell(String shopID, String itemID, int amount);

    double calculateBuyPrice(ShopItem item);
    double calculateSellPrice(ShopItem item);
    double calculateBuyPrice(String shopID, String itemID, ItemStack item);
    double calculateSellPrice(String shopID, String itemID, ItemStack item);

    void postSellTransaction(ShopItem item);
    void postBuyTransaction(ShopItem item);
    void postSellTransaction(String shopID, String itemID, ItemStack item);
    void postBuyTransaction(String shopID, String itemID, ItemStack item);
    
}
