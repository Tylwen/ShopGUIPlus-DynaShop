package fr.tylwen.satyria.dynashop.strategy;

import fr.tylwen.satyria.dynashop.DynaShopPlugin;
import fr.tylwen.satyria.dynashop.data.param.DynaShopType;
import fr.tylwen.satyria.dynashop.data.price.type.PriceDynamic;
import fr.tylwen.satyria.dynashop.data.price.type.PriceRecipe;
import fr.tylwen.satyria.dynashop.data.price.type.PriceStock;
import fr.tylwen.satyria.dynashop.PriceItem;

public class PriceStrategyFactory {
    
    private final PriceDynamic dynamicStrategy;
    private final PriceStock stockStrategy;
    private final PriceRecipe recipeStrategy;
    
    public PriceStrategyFactory(DynaShopPlugin plugin) {
        this.dynamicStrategy = new PriceDynamic(plugin);
        this.stockStrategy = new PriceStock(plugin);
        this.recipeStrategy = new PriceRecipe(plugin);
    }
    
    public PriceItem getStrategy(String shopID, String itemID) {
        DynaShopType type = DynaShopPlugin.getInstance()
                                       .getShopConfigManager()
                                       .getTypeDynaShop(shopID, itemID);
        
        return getStrategy(type);
    }
    
    public PriceItem getStrategy(DynaShopType type) {
        switch (type) {
            case DYNAMIC:
                return dynamicStrategy;
            case STOCK:
                return stockStrategy;
            case RECIPE:
                return recipeStrategy;
            default:
                // Par défaut, utiliser la stratégie dynamique
                return dynamicStrategy;
        }
    }
}