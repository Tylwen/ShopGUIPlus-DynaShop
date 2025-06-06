package fr.tylwen.satyria.dynashop.price;

import fr.tylwen.satyria.dynashop.DynaShopPlugin;
import fr.tylwen.satyria.dynashop.config.DataConfig;
// import org.bukkit.inventory.ItemStack;
import fr.tylwen.satyria.dynashop.data.param.DynaShopType;
import net.brcdev.shopgui.ShopGuiPlusApi;

import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class PriceStock {
    private final DynaShopPlugin plugin;
    private final DataConfig dataConfig;
    private final Map<String, DynamicPrice> priceCache = new HashMap<>();
    
    // Durée de validité du cache en millisecondes (5 minutes)
    private static final long CACHE_DURATION = 5 * 60 * 1000;
    private final Map<String, Long> cacheTimestamps = new HashMap<>();

    public PriceStock(DynaShopPlugin plugin) {
        this.plugin = plugin;
        this.dataConfig = plugin.getDataConfig();
    }

    /**
     * Calcule le prix en fonction du stock actuel.
     */
    public double calculatePrice(String shopID, String itemID, String typePrice) {
        // Vérifier le cache d'abord
        String cacheKey = shopID + ":" + itemID + ":" + typePrice;
        if (isCacheValid(cacheKey)) {
            return getCachedPrice(cacheKey, typePrice);
        }
        
        // Récupérer stock actuel
        Optional<Integer> stockOptional = plugin.getItemDataManager().getStock(shopID, itemID);
        int stock = stockOptional.orElse(0);
        
        // Récupérer les configurations de stock
        int minStock = plugin.getShopConfigManager()
            .getItemValue(shopID, itemID, "stock.min", Integer.class)
            .orElse(dataConfig.getStockMin());
        
        int maxStock = plugin.getShopConfigManager()
            .getItemValue(shopID, itemID, "stock.max", Integer.class)
            .orElse(dataConfig.getStockMax());
        
        // Récupérer le prix de base
        Optional<Double> basePrice = getBasePrice(shopID, itemID, typePrice);
        if (basePrice.isEmpty()) {
            return 0.0;
        }
        
        // Récupérer les bornes de prix
        double minPrice = plugin.getShopConfigManager()
            .getItemValue(shopID, itemID, typePrice.replace("Price", "Dynamic.min"), Double.class)
            .orElse(basePrice.get() * 0.5);
        
        double maxPrice = plugin.getShopConfigManager()
            .getItemValue(shopID, itemID, typePrice.replace("Price", "Dynamic.max"), Double.class)
            .orElse(basePrice.get() * 2.0);
        
        // Récupérer le modificateur
        double modifier = plugin.getShopConfigManager()
            .getItemValue(shopID, itemID, "stock." + (typePrice.equals("buyPrice") ? "buyModifier" : "sellModifier"), Double.class)
            .orElse(typePrice.equals("buyPrice") ? dataConfig.getStockBuyModifier() : dataConfig.getStockSellModifier());
        
        // Calculer le ratio de stock (entre 0 et 1)
        double stockRatio = Math.max(0.0, Math.min(1.0, (double)(stock - minStock) / (maxStock - minStock)));
        
        // Formule : prix élevés quand stock proche de 0, prix bas quand stock proche du max
        double price = maxPrice - (maxPrice - minPrice) * stockRatio * modifier;
        
        // Mettre en cache
        cachePrice(cacheKey, price, typePrice);
        
        // // Appliquer le multiplicateur d'enchantement si activé pour cet item
        // boolean enchantmentEnabled = plugin.getShopConfigManager()
        //     .getItemValue(shopID, itemID, "dynaShop.enchantment", Boolean.class)
        //     .orElse(false);
        // ItemStack item = ShopGuiPlusApi.getShop(shopID).getShopItem(itemID).getItem();
        // if (enchantmentEnabled && item != null) {
        //     price *= plugin.getPriceRecipe().getEnchantMultiplier(item);
        // }
        
        return price;
    }
    
    /**
     * Récupère le prix de base depuis la BD ou la configuration.
     */
    private Optional<Double> getBasePrice(String shopID, String itemID, String typePrice) {
        if (typePrice.equals("buyPrice")) {
            Optional<Double> price = plugin.getItemDataManager().getBuyPrice(shopID, itemID);
            if (price.isPresent()) {
                return price;
            }
            return plugin.getShopConfigManager().getItemValue(shopID, itemID, "buyPrice", Double.class);
        } else {
            Optional<Double> price = plugin.getItemDataManager().getSellPrice(shopID, itemID);
            if (price.isPresent()) {
                return price;
            }
            return plugin.getShopConfigManager().getItemValue(shopID, itemID, "sellPrice", Double.class);
        }
    }
    
    /**
     * Gère une transaction d'achat.
     */
    public void processBuyTransaction(String shopID, String itemID, int amount) {
        // Diminuer le stock
        decreaseStock(shopID, itemID, amount);
        
        // Invalider le cache
        invalidateCache(shopID, itemID);
    }
    
    /**
     * Gère une transaction de vente.
     */
    public void processSellTransaction(String shopID, String itemID, int amount) {
        // Augmenter le stock
        increaseStock(shopID, itemID, amount);
        
        // Invalider le cache
        invalidateCache(shopID, itemID);
    }
    
    /**
     * Diminue le stock d'un item.
     */
    // public void decreaseStock(String shopID, String itemID, int amount) {
    //     Optional<Integer> currentStock = plugin.getItemDataManager().getStock(shopID, itemID);
    //     int minStock = plugin.getShopConfigManager()
    //         .getItemValue(shopID, itemID, "stock.min", Integer.class)
    //         .orElse(dataConfig.getStockMin());
        
    //     int newStock = Math.max(currentStock.orElse(0) - amount, minStock);
    //     plugin.getDataManager().insertStock(shopID, itemID, newStock);
        
    //     // Mettre à jour les prix dans la BD
    //     updatePricesInDatabase(shopID, itemID);
    // }
    public void decreaseStock(String shopID, String itemID, int amount) {
        // Vérifier si l'item est en mode STOCK ou STATIC_STOCK
        // DynaShopType type = plugin.getShopConfigManager().getTypeDynaShop(shopID, itemID);
        DynaShopType type = plugin.getShopConfigManager().getTypeDynaShop(shopID, itemID, "buy");
        if (type != DynaShopType.STOCK && type != DynaShopType.STATIC_STOCK) {
            // Ne rien faire si l'item n'est pas en mode stock
            return;
        }
        
        Optional<Integer> currentStock = plugin.getItemDataManager().getStock(shopID, itemID);
        int minStock = plugin.getShopConfigManager()
            .getItemValue(shopID, itemID, "stock.min", Integer.class)
            .orElse(dataConfig.getStockMin());
        
        int newStock = Math.max(currentStock.orElse(0) - amount, minStock);
        plugin.getDataManager().insertStock(shopID, itemID, newStock);
        
        // Mettre à jour les prix dans la BD
        updatePricesInDatabase(shopID, itemID);
    }
    
    /**
     * Augmente le stock d'un item.
     */
    // public void increaseStock(String shopID, String itemID, int amount) {
    //     Optional<Integer> currentStock = plugin.getItemDataManager().getStock(shopID, itemID);
    //     int maxStock = plugin.getShopConfigManager()
    //         .getItemValue(shopID, itemID, "stock.max", Integer.class)
    //         .orElse(dataConfig.getStockMax());
        
    //     int newStock = Math.min(currentStock.orElse(0) + amount, maxStock);
    //     plugin.getDataManager().insertStock(shopID, itemID, newStock);
        
    //     // Mettre à jour les prix dans la BD
    //     updatePricesInDatabase(shopID, itemID);
    // }
    public void increaseStock(String shopID, String itemID, int amount) {
        // Vérifier si l'item est en mode STOCK ou STATIC_STOCK
        // DynaShopType type = plugin.getShopConfigManager().getTypeDynaShop(shopID, itemID);
        DynaShopType type = plugin.getShopConfigManager().getTypeDynaShop(shopID, itemID, "sell");
        if (type != DynaShopType.STOCK && type != DynaShopType.STATIC_STOCK) {
            // Ne rien faire si l'item n'est pas en mode stock
            return;
        }
        
        Optional<Integer> currentStock = plugin.getItemDataManager().getStock(shopID, itemID);
        int maxStock = plugin.getShopConfigManager()
            .getItemValue(shopID, itemID, "stock.max", Integer.class)
            .orElse(dataConfig.getStockMax());
        
        int newStock = Math.min(currentStock.orElse(0) + amount, maxStock);
        plugin.getDataManager().insertStock(shopID, itemID, newStock);
        
        // Mettre à jour les prix dans la BD
        updatePricesInDatabase(shopID, itemID);
    }
    
    /**
     * Met à jour les prix dans la base de données.
     */
    private void updatePricesInDatabase(String shopID, String itemID) {
        double buyPrice = calculatePrice(shopID, itemID, "buyPrice");
        double sellPrice = calculatePrice(shopID, itemID, "sellPrice");
        
        // Vérifier les marges minimales
        if (buyPrice < sellPrice + DynamicPrice.MIN_MARGIN) {
            buyPrice = sellPrice + DynamicPrice.MIN_MARGIN;
        }
        
        plugin.getBatchDatabaseUpdater().queuePriceUpdate(shopID, itemID, buyPrice, sellPrice);
    }
    
    /**
     * Vérifie si un achat est possible (stock suffisant).
     */
    // public boolean canBuy(String shopID, String itemID, int amount) {
    //     Optional<Integer> stockOptional = plugin.getItemDataManager().getStock(shopID, itemID);
    //     return stockOptional.map(stock -> stock >= amount).orElse(true);
    // }
    public boolean canBuy(String shopID, String itemID, int amount) {
        DynaShopType type = plugin.getShopConfigManager().getTypeDynaShop(shopID, itemID, "buy");
        if (type != DynaShopType.STOCK && type != DynaShopType.STATIC_STOCK) {
            // Si l'item n'est pas en mode stock, l'achat est toujours possible
            return true;
        }
        
        Optional<Integer> stockOptional = plugin.getItemDataManager().getStock(shopID, itemID);
        return stockOptional.map(stock -> stock >= amount).orElse(true);
    }
    
    /**
     * Vérifie si une vente est possible (stock pas plein).
     */
    // public boolean canSell(String shopID, String itemID, int amount) {
    //     Optional<Integer> stockOptional = plugin.getItemDataManager().getStock(shopID, itemID);
    //     int maxStock = plugin.getShopConfigManager()
    //         .getItemValue(shopID, itemID, "stock.max", Integer.class)
    //         .orElse(dataConfig.getStockMax());
        
    //     return stockOptional.map(stock -> stock + amount <= maxStock).orElse(true);
    // }
    public boolean canSell(String shopID, String itemID, int amount) {
        DynaShopType type = plugin.getShopConfigManager().getTypeDynaShop(shopID, itemID, "sell");
        if (type != DynaShopType.STOCK && type != DynaShopType.STATIC_STOCK) {
            // Si l'item n'est pas en mode stock, la vente est toujours possible
            return true;
        }
        
        Optional<Integer> stockOptional = plugin.getItemDataManager().getStock(shopID, itemID);
        int maxStock = plugin.getShopConfigManager()
            .getItemValue(shopID, itemID, "stock.max", Integer.class)
            .orElse(dataConfig.getStockMax());
        
        return stockOptional.map(stock -> stock + amount <= maxStock).orElse(true);
    }
    
    /**
     * Crée un objet DynamicPrice basé sur le stock.
     */
    public DynamicPrice createStockPrice(String shopID, String itemID) {
        double buyPrice = calculatePrice(shopID, itemID, "buyPrice");
        double sellPrice = calculatePrice(shopID, itemID, "sellPrice");
        
        Optional<Integer> stockOptional = plugin.getItemDataManager().getStock(shopID, itemID);
        int stock = stockOptional.orElse(0);
        
        int minStock = plugin.getShopConfigManager()
            .getItemValue(shopID, itemID, "stock.min", Integer.class)
            .orElse(dataConfig.getStockMin());
        
        int maxStock = plugin.getShopConfigManager()
            .getItemValue(shopID, itemID, "stock.max", Integer.class)
            .orElse(dataConfig.getStockMax());
        
        double minBuy = plugin.getShopConfigManager()
            .getItemValue(shopID, itemID, "buyDynamic.min", Double.class)
            .orElse(buyPrice * 0.5);
        
        double maxBuy = plugin.getShopConfigManager()
            .getItemValue(shopID, itemID, "buyDynamic.max", Double.class)
            .orElse(buyPrice * 2.0);
        
        double minSell = plugin.getShopConfigManager()
            .getItemValue(shopID, itemID, "sellDynamic.min", Double.class)
            .orElse(sellPrice * 0.5);
        
        double maxSell = plugin.getShopConfigManager()
            .getItemValue(shopID, itemID, "sellDynamic.max", Double.class)
            .orElse(sellPrice * 2.0);
        
        double stockBuyModifier = plugin.getShopConfigManager()
            .getItemValue(shopID, itemID, "stock.buyModifier", Double.class)
            .orElse(dataConfig.getStockBuyModifier());
        
        double stockSellModifier = plugin.getShopConfigManager()
            .getItemValue(shopID, itemID, "stock.sellModifier", Double.class)
            .orElse(dataConfig.getStockSellModifier());
        
        DynamicPrice price = new DynamicPrice(
            buyPrice, sellPrice, 
            minBuy, maxBuy, minSell, maxSell,
            1.0, 1.0, 1.0, 1.0,  // Growth/decay factors sont 1.0 pour STOCK
            stock, minStock, maxStock, 
            stockBuyModifier, stockSellModifier
        );
        
        // price.setDynaShopType(DynaShopType.STOCK);
        price.setFromStock(true);
        return price;
    }

    public DynamicPrice createStaticStockPrice(String shopID, String itemID) {
        // double buyPrice = calculatePrice(shopID, itemID, "buyPrice");
        // double sellPrice = calculatePrice(shopID, itemID, "sellPrice");
        double buyPrice = plugin.getShopConfigManager().getItemValue(shopID, itemID, "buyPrice", Double.class)
            .orElse(-1.0);
        double sellPrice = plugin.getShopConfigManager().getItemValue(shopID, itemID, "sellPrice", Double.class)
            .orElse(-1.0);
        
        Optional<Integer> stockOptional = plugin.getItemDataManager().getStock(shopID, itemID);
        int stock = stockOptional.orElse(0);
        
        int minStock = plugin.getShopConfigManager()
            .getItemValue(shopID, itemID, "stock.min", Integer.class)
            .orElse(dataConfig.getStockMin());
        
        int maxStock = plugin.getShopConfigManager()
            .getItemValue(shopID, itemID, "stock.max", Integer.class)
            .orElse(dataConfig.getStockMax());
        
        DynamicPrice price = new DynamicPrice(
            buyPrice, sellPrice,
            buyPrice, buyPrice,
            sellPrice, sellPrice,
            1.0, 1.0, 1.0, 1.0,
            stock, minStock, maxStock,
            dataConfig.getStockBuyModifier(), dataConfig.getStockSellModifier()
        );
        
        // price.setDynaShopType(DynaShopType.STATIC_STOCK);
        price.setFromStock(true);
        return price;
    }
    
    // Méthodes pour la gestion du cache
    private boolean isCacheValid(String key) {
        return cacheTimestamps.containsKey(key) && 
               System.currentTimeMillis() - cacheTimestamps.get(key) < CACHE_DURATION;
    }
    
    private double getCachedPrice(String key, String typePrice) {
        DynamicPrice cachedPrice = priceCache.get(key);
        if (cachedPrice == null) return 0.0;
        
        return typePrice.equals("buyPrice") ? cachedPrice.getBuyPrice() : cachedPrice.getSellPrice();
    }
    
    private void cachePrice(String key, double price, String typePrice) {
        DynamicPrice dynamicPrice = priceCache.computeIfAbsent(key, k -> new DynamicPrice(0, 0));
        
        if (typePrice.equals("buyPrice")) {
            dynamicPrice.setBuyPrice(price);
        } else {
            dynamicPrice.setSellPrice(price);
        }
        
        cacheTimestamps.put(key, System.currentTimeMillis());
    }
    
    private void invalidateCache(String shopID, String itemID) {
        String buyKey = shopID + ":" + itemID + ":buyPrice";
        String sellKey = shopID + ":" + itemID + ":sellPrice";
        
        priceCache.remove(buyKey);
        priceCache.remove(sellKey);
        cacheTimestamps.remove(buyKey);
        cacheTimestamps.remove(sellKey);
    }
}