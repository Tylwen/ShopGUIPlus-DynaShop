/*
 * ShopGUI+ DynaShop - Dynamic Economy Addon for Minecraft
 * Copyright (C) 2025 Tylwen
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package fr.tylwen.satyria.dynashop.price;

import fr.tylwen.satyria.dynashop.DynaShopPlugin;
import fr.tylwen.satyria.dynashop.config.DataConfig;
import fr.tylwen.satyria.dynashop.data.cache.CacheManager;
// import org.bukkit.inventory.ItemStack;
import fr.tylwen.satyria.dynashop.data.param.DynaShopType;
// import net.brcdev.shopgui.ShopGuiPlusApi;

// import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class PriceStock {
    private final DynaShopPlugin plugin;
    private final DataConfig dataConfig;
    private final CacheManager<String, DynamicPrice> priceCache;
    
    // Durée de validité du cache en millisecondes (5 minutes)
    private static final long CACHE_DURATION = 5 * 60 * 1000;
    private final Map<String, Long> cacheTimestamps = new HashMap<>();

    public PriceStock(DynaShopPlugin plugin) {
        this.plugin = plugin;
        this.dataConfig = plugin.getDataConfig();
        this.priceCache = plugin.getPriceCache();
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
        Optional<Integer> stockOptional = plugin.getStorageManager().getStock(shopID, itemID);
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
        
        // // Récupérer les bornes de prix
        // double minPrice = plugin.getShopConfigManager()
        //     .getItemValue(shopID, itemID, typePrice.replace("Price", "Dynamic.min"), Double.class)
        //     .orElse(basePrice.get() * 0.5);
        
        // double maxPrice = plugin.getShopConfigManager()
        //     .getItemValue(shopID, itemID, typePrice.replace("Price", "Dynamic.max"), Double.class)
        //     .orElse(basePrice.get() * 2.0);
        
        // // Récupérer le modificateur
        // double modifier = plugin.getShopConfigManager()
        //     .getItemValue(shopID, itemID, "stock." + (typePrice.equals("buyPrice") ? "buyModifier" : "sellModifier"), Double.class)
        //     .orElse(typePrice.equals("buyPrice") ? dataConfig.getStockBuyModifier() : dataConfig.getStockSellModifier());
        
        // // Calculer le ratio de stock (entre 0 et 1)
        // double stockRatio = Math.max(0.0, Math.min(1.0, (double)(stock - minStock) / (maxStock - minStock)));
        
        // // Formule : prix élevés quand stock proche de 0, prix bas quand stock proche du max
        // double price = maxPrice - (maxPrice - minPrice) * stockRatio * modifier;
        
        // Récupérer les bornes de prix ou les calculer avec les modificateurs
        double minPrice, maxPrice;
        String minKey = typePrice.replace("Price", "Dynamic.min");
        String maxKey = typePrice.replace("Price", "Dynamic.max");
        
        Optional<Double> minPriceConfig = plugin.getShopConfigManager()
            .getItemValue(shopID, itemID, minKey, Double.class);
        Optional<Double> maxPriceConfig = plugin.getShopConfigManager()
            .getItemValue(shopID, itemID, maxKey, Double.class);
        
        if (minPriceConfig.isPresent() && maxPriceConfig.isPresent()) {
            // Utiliser les bornes définies explicitement
            minPrice = minPriceConfig.get();
            maxPrice = maxPriceConfig.get();
        } else {
            // // Utiliser les modificateurs pour calculer les bornes
            // boolean isBuy = typePrice.equals("buyPrice");
            // double modifier = plugin.getShopConfigManager()
            //     .getItemValue(shopID, itemID, "stock." + (isBuy ? "buyModifier" : "sellModifier"), Double.class)
            //     .orElse(isBuy ? dataConfig.getStockBuyModifier() : dataConfig.getStockSellModifier());

            double trueBasePrice = getTrueBasePrice(shopID, itemID, typePrice)
                // .orElse(basePrice.get());
                .orElse(-1.0);

            // Utiliser UN SEUL modificateur pour calculer les bornes
            double modifier = plugin.getShopConfigManager()
                .getItemValue(shopID, itemID, "stock.modifier", Double.class)
                .orElse(dataConfig.getStockModifier()); // Nouvelle méthode unifiée

            minPrice = trueBasePrice * (1.0 - modifier);
            maxPrice = trueBasePrice * (1.0 + modifier);
        }
        
        // Calculer le ratio de stock (entre 0 et 1)
        double stockRatio = Math.max(0.0, Math.min(1.0, (double)(stock - minStock) / (maxStock - minStock)));
        
        // Formule : prix élevés quand stock proche de 0, prix bas quand stock proche du max
        double price = maxPrice - (maxPrice - minPrice) * stockRatio;
        
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
            Optional<Double> price = plugin.getStorageManager().getBuyPrice(shopID, itemID);
            if (price.isPresent()) {
                return price;
            }
            return plugin.getShopConfigManager().getItemValue(shopID, itemID, "buyPrice", Double.class);
        } else {
            Optional<Double> price = plugin.getStorageManager().getSellPrice(shopID, itemID);
            if (price.isPresent()) {
                return price;
            }
            return plugin.getShopConfigManager().getItemValue(shopID, itemID, "sellPrice", Double.class);
        }
    }

    private Optional<Double> getTrueBasePrice(String shopID, String itemID, String typePrice) {
        if (typePrice.equals("buyPrice")) {
            return plugin.getShopConfigManager().getItemValue(shopID, itemID, "buyPrice", Double.class);
        } else {
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
        // invalidateCache(shopID, itemID);
        DynaShopPlugin.getInstance().invalidatePriceCache(shopID, itemID, null);
    }
    
    /**
     * Gère une transaction de vente.
     */
    public void processSellTransaction(String shopID, String itemID, int amount) {
        // Augmenter le stock
        increaseStock(shopID, itemID, amount);
        
        // Invalider le cache
        // invalidateCache(shopID, itemID);
        DynaShopPlugin.getInstance().invalidatePriceCache(shopID, itemID, null);
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
        // // Vérifier si l'item est en mode STOCK ou STATIC_STOCK
        // // DynaShopType type = plugin.getShopConfigManager().getTypeDynaShop(shopID, itemID);
        // DynaShopType type = plugin.getShopConfigManager().getTypeDynaShop(shopID, itemID, "buy");
        // if (type != DynaShopType.STOCK && type != DynaShopType.STATIC_STOCK) {
        //     // Ne rien faire si l'item n'est pas en mode stock
        //     return;
        // }
        DynaShopType type = plugin.getShopConfigManager().getTypeDynaShop(shopID, itemID);
        
        // Si c'est un LINK, propager au target
        if (type == DynaShopType.LINK) {
            String linkedItemRef = plugin.getShopConfigManager().getItemValue(shopID, itemID, "link", String.class).orElse(null);
            if (linkedItemRef != null && linkedItemRef.contains(":")) {
                String[] parts = linkedItemRef.split(":");
                if (parts.length == 2) {
                    decreaseStock(parts[0], parts[1], amount);
                    return;
                }
            }
        }
        
        // Vérifier si le type réel est STOCK ou STATIC_STOCK
        DynaShopType realType = plugin.getShopConfigManager().getRealTypeDynaShop(shopID, itemID, "buy");
        if (realType != DynaShopType.STOCK && realType != DynaShopType.STATIC_STOCK) {
            return;
        }
        
        Optional<Integer> currentStock = plugin.getStorageManager().getStock(shopID, itemID);
        int minStock = plugin.getShopConfigManager()
            .getItemValue(shopID, itemID, "stock.min", Integer.class)
            .orElse(dataConfig.getStockMin());
        
        int newStock = Math.max(currentStock.orElse(0) - amount, minStock);
        plugin.getStorageManager().saveStock(shopID, itemID, newStock);

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
        // // Vérifier si l'item est en mode STOCK ou STATIC_STOCK
        // // DynaShopType type = plugin.getShopConfigManager().getTypeDynaShop(shopID, itemID);
        // DynaShopType type = plugin.getShopConfigManager().getTypeDynaShop(shopID, itemID, "sell");
        // if (type != DynaShopType.STOCK && type != DynaShopType.STATIC_STOCK) {
        //     // Ne rien faire si l'item n'est pas en mode stock
        //     return;
        // }
        DynaShopType type = plugin.getShopConfigManager().getTypeDynaShop(shopID, itemID);
        // Si c'est un LINK, propager au target
        if (type == DynaShopType.LINK) {
            String linkedItemRef = plugin.getShopConfigManager().getItemValue(shopID, itemID, "link", String.class).orElse(null);
            if (linkedItemRef != null && linkedItemRef.contains(":")) {
                String[] parts = linkedItemRef.split(":");
                if (parts.length == 2) {
                    increaseStock(parts[0], parts[1], amount);
                    return;
                }
            }
        }
        // Vérifier si le type réel est STOCK ou STATIC_STOCK
        DynaShopType realType = plugin.getShopConfigManager().getRealTypeDynaShop(shopID, itemID, "sell");
        if (realType != DynaShopType.STOCK && realType != DynaShopType.STATIC_STOCK) {
            return;
        }
        
        Optional<Integer> currentStock = plugin.getStorageManager().getStock(shopID, itemID);
        int maxStock = plugin.getShopConfigManager()
            .getItemValue(shopID, itemID, "stock.max", Integer.class)
            .orElse(dataConfig.getStockMax());
        
        int newStock = Math.min(currentStock.orElse(0) + amount, maxStock);
        plugin.getStorageManager().saveStock(shopID, itemID, newStock);
        
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
    // // public boolean canBuy(String shopID, String itemID, int amount) {
    // //     Optional<Integer> stockOptional = plugin.getItemDataManager().getStock(shopID, itemID);
    // //     return stockOptional.map(stock -> stock >= amount).orElse(true);
    // // }
    // public boolean canBuy(String shopID, String itemID, int amount) {
    //     DynaShopType type = plugin.getShopConfigManager().getTypeDynaShop(shopID, itemID, "buy");
    //     if (type != DynaShopType.STOCK && type != DynaShopType.STATIC_STOCK) {
    //         // Si l'item n'est pas en mode stock, l'achat est toujours possible
    //         return true;
    //     }
        
    //     Optional<Integer> stockOptional = plugin.getStorageManager().getStock(shopID, itemID);
    //     return stockOptional.map(stock -> stock >= amount).orElse(true);
    // }
    public boolean canBuy(String shopID, String itemID, int amount) {
        DynaShopType realType = plugin.getShopConfigManager().getRealTypeDynaShop(shopID, itemID, "buy");
        if (realType != DynaShopType.STOCK && realType != DynaShopType.STATIC_STOCK) {
            // Si l'item n'est pas en mode stock, l'achat est toujours possible
            return true;
        }

        // Pour les items LINK, vérifier le stock de l'item cible
        if (plugin.getShopConfigManager().getTypeDynaShop(shopID, itemID) == DynaShopType.LINK) {
            String linkedItemRef = plugin.getShopConfigManager().getItemValue(shopID, itemID, "link", String.class).orElse(null);
            if (linkedItemRef != null && linkedItemRef.contains(":")) {
                String[] parts = linkedItemRef.split(":");
                if (parts.length == 2) {
                    // return checkStockForBuy(parts[0], parts[1], amount);
                    Optional<Integer> stockOptional = plugin.getStorageManager().getStock(parts[0], parts[1]);
                    return stockOptional.map(stock -> stock >= amount).orElse(true);
                }
            }
        }

        // Pour les autres types, vérifier le stock de l'item actuel
        Optional<Integer> stockOptional = plugin.getStorageManager().getStock(shopID, itemID);
        return stockOptional.map(stock -> stock >= amount).orElse(true);
    }
    
    /**
     * Vérifie si une vente est possible (stock pas plein).
     */
    // // public boolean canSell(String shopID, String itemID, int amount) {
    // //     Optional<Integer> stockOptional = plugin.getItemDataManager().getStock(shopID, itemID);
    // //     int maxStock = plugin.getShopConfigManager()
    // //         .getItemValue(shopID, itemID, "stock.max", Integer.class)
    // //         .orElse(dataConfig.getStockMax());
        
    // //     return stockOptional.map(stock -> stock + amount <= maxStock).orElse(true);
    // // }
    // public boolean canSell(String shopID, String itemID, int amount) {
    //     DynaShopType type = plugin.getShopConfigManager().getTypeDynaShop(shopID, itemID, "sell");
    //     if (type != DynaShopType.STOCK && type != DynaShopType.STATIC_STOCK) {
    //         // Si l'item n'est pas en mode stock, la vente est toujours possible
    //         return true;
    //     }
        
    //     Optional<Integer> stockOptional = plugin.getStorageManager().getStock(shopID, itemID);
    //     int maxStock = plugin.getShopConfigManager()
    //         .getItemValue(shopID, itemID, "stock.max", Integer.class)
    //         .orElse(dataConfig.getStockMax());
        
    //     return stockOptional.map(stock -> stock + amount <= maxStock).orElse(true);
    // }
    public boolean canSell(String shopID, String itemID, int amount) {
        // Utiliser getRealTypeDynaShop qui suit les références pour les items LINK
        DynaShopType realType = plugin.getShopConfigManager().getRealTypeDynaShop(shopID, itemID, "sell");
        
        if (realType != DynaShopType.STOCK && realType != DynaShopType.STATIC_STOCK) {
            return true;
        }

        // Pour les items LINK, vérifier le stock de l'item cible
        if (plugin.getShopConfigManager().getTypeDynaShop(shopID, itemID) == DynaShopType.LINK) {
            String linkedItemRef = plugin.getShopConfigManager().getItemValue(shopID, itemID, "link", String.class).orElse(null);
            if (linkedItemRef != null && linkedItemRef.contains(":")) {
                String[] parts = linkedItemRef.split(":");
                if (parts.length == 2) {
                    // return checkStockForSell(parts[0], parts[1], amount);
                    Optional<Integer> stockOptional = plugin.getStorageManager().getStock(parts[0], parts[1]);
                    return stockOptional.map(stock -> stock + amount <= plugin.getShopConfigManager()
                        .getItemValue(parts[0], parts[1], "stock.max", Integer.class)
                        .orElse(dataConfig.getStockMax())).orElse(true);
                }
            }
        }

        // Pour les autres types, vérifier le stock de l'item actuel
        Optional<Integer> stockOptional = plugin.getStorageManager().getStock(shopID, itemID);
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

        double trueBaseBuyPrice = getTrueBasePrice(shopID, itemID, "buyPrice")
            .orElse(-1.0);
        double trueBaseSellPrice = getTrueBasePrice(shopID, itemID, "sellPrice")
            .orElse(-1.0);
        
        Optional<Integer> stockOptional = plugin.getStorageManager().getStock(shopID, itemID);
        int stock = stockOptional.orElse(0);
        
        int minStock = plugin.getShopConfigManager()
            .getItemValue(shopID, itemID, "stock.min", Integer.class)
            .orElse(dataConfig.getStockMin());
        
        int maxStock = plugin.getShopConfigManager()
            .getItemValue(shopID, itemID, "stock.max", Integer.class)
            .orElse(dataConfig.getStockMax());
            
        double stockModifier = plugin.getShopConfigManager()
            .getItemValue(shopID, itemID, "stock.modifier", Double.class)
            .orElse(dataConfig.getStockModifier());
        
        double minBuy = plugin.getShopConfigManager()
            .getItemValue(shopID, itemID, "buyDynamic.min", Double.class)
            .orElse(trueBaseBuyPrice * (1.0 - stockModifier));
        
        double maxBuy = plugin.getShopConfigManager()
            .getItemValue(shopID, itemID, "buyDynamic.max", Double.class)
            .orElse(trueBaseBuyPrice * (1.0 + stockModifier));
        
        double minSell = plugin.getShopConfigManager()
            .getItemValue(shopID, itemID, "sellDynamic.min", Double.class)
            .orElse(trueBaseSellPrice * (1.0 - stockModifier));

        double maxSell = plugin.getShopConfigManager()
            .getItemValue(shopID, itemID, "sellDynamic.max", Double.class)
            .orElse(trueBaseSellPrice * (1.0 + stockModifier));

        // double stockBuyModifier = plugin.getShopConfigManager()
        //     .getItemValue(shopID, itemID, "stock.buyModifier", Double.class)
        //     .orElse(dataConfig.getStockBuyModifier());
        
        // double stockSellModifier = plugin.getShopConfigManager()
        //     .getItemValue(shopID, itemID, "stock.sellModifier", Double.class)
        //     .orElse(dataConfig.getStockSellModifier());
        
        DynamicPrice price = new DynamicPrice(
            buyPrice, sellPrice, 
            minBuy, maxBuy, minSell, maxSell,
            1.0, 1.0, 1.0, 1.0,  // Growth/decay factors sont 1.0 pour STOCK
            stock, minStock, maxStock, 
            stockModifier
        );
        
        price.setDynaShopType(DynaShopType.STOCK);
        // price.setFromStock(true);
        return price;
    }

    public DynamicPrice createStaticStockPrice(String shopID, String itemID) {
        // double buyPrice = calculatePrice(shopID, itemID, "buyPrice");
        // double sellPrice = calculatePrice(shopID, itemID, "sellPrice");
        double buyPrice = plugin.getShopConfigManager().getItemValue(shopID, itemID, "buyPrice", Double.class)
            .orElse(-1.0);
        double sellPrice = plugin.getShopConfigManager().getItemValue(shopID, itemID, "sellPrice", Double.class)
            .orElse(-1.0);

        Optional<Integer> stockOptional = plugin.getStorageManager().getStock(shopID, itemID);
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
            dataConfig.getStockModifier()
        );
        
        price.setDynaShopType(DynaShopType.STATIC_STOCK);
        // price.setFromStock(true);
        return price;
    }
    
    // Méthodes pour la gestion du cache
    private boolean isCacheValid(String key) {
        return cacheTimestamps.containsKey(key) && 
               System.currentTimeMillis() - cacheTimestamps.get(key) < CACHE_DURATION;
    }
    
    // private double getCachedPrice(String key, String typePrice) {
    //     DynamicPrice cachedPrice = priceCache.get(key);
    //     if (cachedPrice == null) return 0.0;
        
    //     return typePrice.equals("buyPrice") ? cachedPrice.getBuyPrice() : cachedPrice.getSellPrice();
    // }
    private double getCachedPrice(String key, String typePrice) {
        DynamicPrice cachedPrice = priceCache.get(key, () -> null);
        if (cachedPrice == null) return 0.0;
        return typePrice.equals("buyPrice") ? cachedPrice.getBuyPrice() : cachedPrice.getSellPrice();
    }
    
    // private void cachePrice(String key, double price, String typePrice) {
    //     DynamicPrice dynamicPrice = priceCache.computeIfAbsent(key, k -> new DynamicPrice(0, 0));
        
    //     if (typePrice.equals("buyPrice")) {
    //         dynamicPrice.setBuyPrice(price);
    //     } else {
    //         dynamicPrice.setSellPrice(price);
    //     }
        
    //     cacheTimestamps.put(key, System.currentTimeMillis());
    // }
    private void cachePrice(String key, double price, String typePrice) {
        priceCache.get(key, () -> new DynamicPrice(0, 0)); // S'assure que l'entrée existe
        DynamicPrice dynamicPrice = priceCache.get(key, () -> new DynamicPrice(0, 0));
        if (typePrice.equals("buyPrice")) {
            dynamicPrice.setBuyPrice(price);
        } else {
            dynamicPrice.setSellPrice(price);
        }
        priceCache.put(key, dynamicPrice); // Met à jour l'entrée dans le cache global
    }
    
    // private void invalidateCache(String shopID, String itemID) {
    //     String buyKey = shopID + ":" + itemID + ":buyPrice";
    //     String sellKey = shopID + ":" + itemID + ":sellPrice";
        
    //     priceCache.remove(buyKey);
    //     priceCache.remove(sellKey);
    //     cacheTimestamps.remove(buyKey);
    //     cacheTimestamps.remove(sellKey);
    // }
    // private void invalidateCache(String shopID, String itemID) {
    //     String buyKey = shopID + ":" + itemID + ":buyPrice";
    //     String sellKey = shopID + ":" + itemID + ":sellPrice";
    //     priceCache.invalidate(buyKey);
    //     priceCache.invalidate(sellKey);
    // }
}