package fr.tylwen.satyria.dynashop.data.price.type;

import java.util.Optional;

import org.bukkit.inventory.ItemStack;

import fr.tylwen.satyria.dynashop.DynaShopPlugin;
import fr.tylwen.satyria.dynashop.PriceItem;
import fr.tylwen.satyria.dynashop.config.ShopConfigManager;
import fr.tylwen.satyria.dynashop.data.param.DynaShopType;
import fr.tylwen.satyria.dynashop.data.price.DynamicPrice;
import fr.tylwen.satyria.dynashop.database.ItemDataManager;

public class PriceDynamic implements PriceItem {
    private final DynaShopPlugin plugin;
    private final ItemDataManager itemDataManager;
    private final ShopConfigManager shopConfigManager;

    public PriceDynamic(DynaShopPlugin plugin) {
        this .plugin = plugin;
        this.itemDataManager = plugin.getItemDataManager();
        this.shopConfigManager = plugin.getShopConfigManager();
    }

    @Override
    public double calculateBuyPrice(String shopID, String itemID, ItemStack item) {
        Optional<DynamicPrice> priceOpt = itemDataManager.getPrices(shopID, itemID);
        
        // Si un prix existe en base de données, l'utiliser
        if (priceOpt.isPresent()) {
            return priceOpt.get().getBuyPrice();
        }
        
        // Sinon, utiliser la configuration
        return shopConfigManager.getItemValue(shopID, itemID, "buyPrice", Double.class)
                .orElse(0.0);
    }

    @Override
    public double calculateSellPrice(String shopID, String itemID, ItemStack item) {
        Optional<DynamicPrice> priceOpt = itemDataManager.getPrices(shopID, itemID);
        
        // Si un prix existe en base de données, l'utiliser
        if (priceOpt.isPresent()) {
            return priceOpt.get().getSellPrice();
        }
        
        // Sinon, utiliser la configuration
        return shopConfigManager.getItemValue(shopID, itemID, "sellPrice", Double.class)
                .orElse(0.0);
    }

    @Override
    public void processBuyTransaction(String shopID, String itemID, int amount) {
        Optional<DynamicPrice> priceOpt = itemDataManager.getPrices(shopID, itemID);
        if (priceOpt.isPresent()) {
            DynamicPrice price = priceOpt.get();
            
            for (int i = 0; i < amount; i++) {
                price.setBuyPrice(price.getBuyPrice() * plugin.getConfig().getDouble("default.buy-growth-rate"));
                // price.incrementBuy();
                // price.decrementSell();
                // price.setStock(price.getStock() - 1);
            }
            price.setStock(price.getStock() - amount);
            // itemDataManager.saveDynamicPrice(shopID, itemID, price);
            itemDataManager.savePrice(shopID, itemID, price);
        }
    }

    @Override
    public void processSellTransaction(String shopID, String itemID, int amount) {
        Optional<DynamicPrice> priceOpt = itemDataManager.getPrices(shopID, itemID);
        
        if (priceOpt.isPresent()) {
            DynamicPrice price = priceOpt.get();
            
            // Modifier les prix selon les facteurs de décroissance
            for (int i = 0; i < amount; i++) {
                price.decrementBuy();
                price.incrementSell();
            }
            
            // Mettre à jour le stock
            price.setStock(price.getStock() + amount);
            
            // Enregistrer les modifications
            itemDataManager.saveDynamicPrice(shopID, itemID, price);
    }

    @Override
    public boolean canBuy(String shopID, String itemID, int amount) {
        Optional<DynamicPrice> priceOpt = itemDataManager.getPrices(shopID, itemID);
        
        // On peut toujours acheter sauf si le stock est géré et insuffisant
        return priceOpt.map(price -> price.getStock() >= amount).orElse(true);
        // // Logique de vérification de l'achat
        // // ...
        // return true;
    }

    @Override
    public boolean canSell(String shopID, String itemID, int amount) {
        Optional<DynamicPrice> priceOpt = itemDataManager.getPrices(shopID, itemID);
        
        // On peut toujours vendre sauf si le stock est géré et atteint son maximum
        return priceOpt.map(price -> price.getStock() + amount <= price.getMaxStock()).orElse(true);
        // // Logique de vérification de la vente
        // // ...
        // return true;
    }

    @Override
    public DynaShopType getType() {
        return DynaShopType.DYNAMIC;
    }

    // Autres méthodes spécifiques à la stratégie dynamique
    // incrementBuy, decrementSell, etc.
    public void incrementBuy(String shopID, String itemID) {
        Optional<DynamicPrice> priceOpt = itemDataManager.getPrices(shopID, itemID);
        if (priceOpt.isPresent()) {
            DynamicPrice price = priceOpt.get();
            price.incrementBuy();
            itemDataManager.saveDynamicPrice(shopID, itemID, price);
        }
    }
    
}
