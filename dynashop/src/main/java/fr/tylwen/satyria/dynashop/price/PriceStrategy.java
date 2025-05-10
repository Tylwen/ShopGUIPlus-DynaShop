package fr.tylwen.satyria.dynashop.price;

import java.util.Optional;

import org.bukkit.inventory.ItemStack;

import fr.tylwen.satyria.dynashop.DynaShopPlugin;
import fr.tylwen.satyria.dynashop.config.DataConfig;
import fr.tylwen.satyria.dynashop.data.DynamicPrice;
import fr.tylwen.satyria.dynashop.data.ItemPriceData;
import fr.tylwen.satyria.dynashop.data.ShopConfigManager;
import fr.tylwen.satyria.dynashop.data.param.DynaShopType;

public class PriceStrategy {
    private final DynaShopPlugin plugin;
    private final RecipePrice priceRecipe;

    private final ShopConfigManager shopConfigManager;
    private final DataConfig dataConfig;

    public PriceStrategy(DynaShopPlugin plugin) {
        this.plugin = plugin;
        this.priceRecipe = new RecipePrice(plugin);
        this.shopConfigManager = plugin.getShopConfigManager();
        this.dataConfig = plugin.getDataConfig();
    }

    
    /**
     * Récupère le prix dynamique d'un item dans un magasin donné.
     *
     * @param shopID   L'ID du magasin.
     * @param itemID   L'ID de l'item.
     * @param itemStack L'ItemStack de l'item.
     * @return Un objet DynamicPrice contenant les informations de prix, ou null si aucune donnée n'est trouvée.
     */
    public DynamicPrice getDynamicPrice(String shopID, String itemID, ItemStack itemStack) {
        // // Vérifier d'abord dans le cache pour les prix de recette
        // String cacheKeyBuy = shopID + ":" + itemID + ":buyPrice";
        // String cacheKeySell = shopID + ":" + itemID + ":sellPrice";
        
        // Charger les prix depuis la base de données
        Optional<DynamicPrice> priceFromDatabase = plugin.getItemDataManager().getItemValues(shopID, itemID);
    
        // Charger les données supplémentaires depuis les fichiers de configuration
        ItemPriceData priceData = shopConfigManager.getItemAllValues(shopID, itemID);
        
        // Déterminer le type de l'item
        DynaShopType type = shopConfigManager.getTypeDynaShop(shopID, itemID);
        
        // Traiter les prix basés sur les recettes
        if (type == DynaShopType.RECIPE) {
            // Vérifier si les prix sont en cache
            double buyPrice = plugin.getCachedRecipePrice(shopID, itemID, "buyPrice");
            double sellPrice = plugin.getCachedRecipePrice(shopID, itemID, "sellPrice");
            
            // Si les prix ne sont pas en cache, les calculer
            if (buyPrice < 0 || sellPrice < 0) {
                // Utiliser les calculs synchrones pour l'initialisation
                // buyPrice = priceRecipe.calculatePrice(shopID, itemID, itemStack, "buyPrice", new ArrayList<>());
                // sellPrice = priceRecipe.calculatePrice(shopID, itemID, itemStack, "sellPrice", new ArrayList<>());
                buyPrice = priceRecipe.calculateBuyPrice(shopID, itemID, itemStack);
                sellPrice = priceRecipe.calculateSellPrice(shopID, itemID, itemStack);
                
                // Mettre en cache les résultats calculés
                plugin.cacheRecipePrice(shopID, itemID, "buyPrice", buyPrice);
                plugin.cacheRecipePrice(shopID, itemID, "sellPrice", sellPrice);
                
                // Planifier un calcul asynchrone pour mettre à jour le cache
                priceRecipe.calculatePriceAsync(shopID, itemID, itemStack, "buyPrice", newPrice -> {
                    plugin.cacheRecipePrice(shopID, itemID, "buyPrice", newPrice);
                });
                
                priceRecipe.calculatePriceAsync(shopID, itemID, itemStack, "sellPrice", newPrice -> {
                    plugin.cacheRecipePrice(shopID, itemID, "sellPrice", newPrice);
                });
            }
            
            // Calculer ou récupérer les valeurs min/max
            double minBuy, maxBuy, minSell, maxSell;
            
            // Vérifier si les bornes sont en cache
            double cachedMinBuy = plugin.getCachedRecipePrice(shopID, itemID, "buyDynamic.min");
            double cachedMaxBuy = plugin.getCachedRecipePrice(shopID, itemID, "buyDynamic.max");
            double cachedMinSell = plugin.getCachedRecipePrice(shopID, itemID, "sellDynamic.min");
            double cachedMaxSell = plugin.getCachedRecipePrice(shopID, itemID, "sellDynamic.max");
            
            if (cachedMinBuy >= 0) {
                minBuy = cachedMinBuy;
            } else {
                minBuy = priceData.minBuy.orElse(buyPrice * 0.5); // Valeur par défaut
                // Calculer la valeur en arrière-plan et mettre à jour le cache
                priceRecipe.calculatePriceAsync(shopID, itemID, itemStack, "buyDynamic.min", newPrice -> {
                    plugin.cacheRecipePrice(shopID, itemID, "buyDynamic.min", newPrice);
                });
            }
            
            if (cachedMaxBuy >= 0) {
                maxBuy = cachedMaxBuy;
            } else {
                maxBuy = priceData.maxBuy.orElse(buyPrice * 2.0); // Valeur par défaut
                // Calculer la valeur en arrière-plan et mettre à jour le cache
                priceRecipe.calculatePriceAsync(shopID, itemID, itemStack, "buyDynamic.max", newPrice -> {
                    plugin.cacheRecipePrice(shopID, itemID, "buyDynamic.max", newPrice);
                });
            }
            
            if (cachedMinSell >= 0) {
                minSell = cachedMinSell;
            } else {
                minSell = priceData.minSell.orElse(sellPrice * 0.5); // Valeur par défaut
                // Calculer la valeur en arrière-plan et mettre à jour le cache
                priceRecipe.calculatePriceAsync(shopID, itemID, itemStack, "sellDynamic.min", newPrice -> {
                    plugin.cacheRecipePrice(shopID, itemID, "sellDynamic.min", newPrice);
                });
            }
            
            if (cachedMaxSell >= 0) {
                maxSell = cachedMaxSell;
            } else {
                maxSell = priceData.maxSell.orElse(sellPrice * 2.0); // Valeur par défaut
                // Calculer la valeur en arrière-plan et mettre à jour le cache
                priceRecipe.calculatePriceAsync(shopID, itemID, itemStack, "sellDynamic.max", newPrice -> {
                    plugin.cacheRecipePrice(shopID, itemID, "sellDynamic.max", newPrice);
                });
            }
            
            DynamicPrice recipePrice = new DynamicPrice(buyPrice, sellPrice, minBuy, maxBuy, minSell, maxSell, 1.0, 1.0, 1.0, 1.0, 0, 0, 0, 1.0, 1.0);
            recipePrice.setFromRecipe(true); // Marquer comme provenant d'une recette
            return recipePrice;
        }
    
        
        if (type == DynaShopType.STOCK) {
            return plugin.getPriceStock().createStockPrice(shopID, itemID, itemStack);
            // price.setFromStock(true);
        }
        
        // Si aucune donnée n'est trouvée dans la base de données ou les fichiers de configuration, retourner null
        if (priceFromDatabase.isEmpty() && (priceData.buyPrice.isEmpty() || priceData.sellPrice.isEmpty())) {
            return null;
        }
        
        // Fusionner les données
        double buyPrice = priceFromDatabase.map(DynamicPrice::getBuyPrice).orElse(priceData.buyPrice.orElse(-1.0));
        double sellPrice = priceFromDatabase.map(DynamicPrice::getSellPrice).orElse(priceData.sellPrice.orElse(-1.0));
    
        double minBuy = priceData.minBuy.orElse(buyPrice);
        double maxBuy = priceData.maxBuy.orElse(buyPrice);
        double minSell = priceData.minSell.orElse(sellPrice);
        double maxSell = priceData.maxSell.orElse(sellPrice);
    
        double growthBuy = priceData.growthBuy.orElseGet(() -> {
            boolean hasBuyDynamic = shopConfigManager.hasSection(shopID, itemID, "buyDynamic");
            return hasBuyDynamic ? dataConfig.getBuyGrowthRate() : 1.0; // Valeur par défaut pour growthBuy
        });
    
        double decayBuy = priceData.decayBuy.orElseGet(() -> {
            boolean hasBuyDynamic = shopConfigManager.hasSection(shopID, itemID, "buyDynamic");
            return hasBuyDynamic ? dataConfig.getBuyDecayRate() : 1.0; // Valeur par défaut pour decayBuy
        });
    
        double growthSell = priceData.growthSell.orElseGet(() -> {
            boolean hasSellDynamic = shopConfigManager.hasSection(shopID, itemID, "sellDynamic");
            return hasSellDynamic ? dataConfig.getSellGrowthRate() : 1.0; // Valeur par défaut pour growthSell
        });
    
        double decaySell = priceData.decaySell.orElseGet(() -> {
            boolean hasSellDynamic = shopConfigManager.hasSection(shopID, itemID, "sellDynamic");
            return hasSellDynamic ? dataConfig.getSellDecayRate() : 1.0; // Valeur par défaut pour decaySell
        });
    
        int stock = priceFromDatabase.map(DynamicPrice::getStock).orElse(priceData.stock.orElse(0));

        int minStock = priceData.minStock.orElseGet(() -> {
            boolean hasStock = shopConfigManager.hasSection(shopID, itemID, "stock");
            return hasStock ? dataConfig.getStockMin() : 0; // Valeur par défaut pour minStock
        });
    
        int maxStock = priceData.maxStock.orElseGet(() -> {
            boolean hasStock = shopConfigManager.hasSection(shopID, itemID, "stock");
            return hasStock ? dataConfig.getStockMax() : Integer.MAX_VALUE; // Valeur par défaut pour maxStock
        });
    
        double stockBuyModifier = priceData.stockBuyModifier.orElseGet(() -> {
            boolean hasStock = shopConfigManager.hasSection(shopID, itemID, "stock");
            return hasStock ? dataConfig.getStockBuyModifier() : 1.0; // Valeur par défaut pour stockBuyModifier
        });
    
        double stockSellModifier = priceData.stockSellModifier.orElseGet(() -> {
            boolean hasStock = shopConfigManager.hasSection(shopID, itemID, "stock");
            return hasStock ? dataConfig.getStockSellModifier() : 1.0; // Valeur par défaut pour stockSellModifier
        });
    
        return new DynamicPrice(buyPrice, sellPrice, minBuy, maxBuy, minSell, maxSell, growthBuy, decayBuy, growthSell, decaySell, stock, minStock, maxStock, stockBuyModifier, stockSellModifier);
        // DynamicPrice price = new DynamicPrice(buyPrice, sellPrice, minBuy, maxBuy, minSell, maxSell, growthBuy, decayBuy, growthSell, decaySell, stock, minStock, maxStock, stockBuyModifier, stockSellModifier);
        
        // return price;
    }
}
