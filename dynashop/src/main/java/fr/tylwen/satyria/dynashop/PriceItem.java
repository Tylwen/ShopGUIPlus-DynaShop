package fr.tylwen.satyria.dynashop;

import org.bukkit.inventory.ItemStack;

import fr.tylwen.satyria.dynashop.data.param.DynaShopType;

public interface PriceItem {
    /**
     * Calcule le prix d'achat d'un item
     */
    double calculateBuyPrice(String shopID, String itemID, ItemStack item);
    
    /**
     * Calcule le prix de vente d'un item
     */
    double calculateSellPrice(String shopID, String itemID, ItemStack item);
    
    /**
     * Gère une transaction d'achat
     */
    void processBuyTransaction(String shopID, String itemID, int amount);
    
    /**
     * Gère une transaction de vente
     */
    void processSellTransaction(String shopID, String itemID, int amount);
    
    /**
     * Vérifie si un achat est possible
     */
    boolean canBuy(String shopID, String itemID, int amount);
    
    /**
     * Vérifie si une vente est possible
     */
    boolean canSell(String shopID, String itemID, int amount);
    
    /**
     * Retourne le type de stratégie de prix
     */
    DynaShopType getType();
}