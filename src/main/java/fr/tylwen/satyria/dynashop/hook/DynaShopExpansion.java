package fr.tylwen.satyria.dynashop.hook;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
// import java.lang.foreign.Linker.Option;
// import java.text.DecimalFormat;
// import java.text.DecimalFormatSymbols;
// import java.util.ArrayList;
// import java.util.HashMap;
import java.util.HashSet;
// import java.util.Map;
// import java.sql.SQLException;
import java.util.Optional;
// import java.util.concurrent.CompletableFuture;

// import org.bukkit.Bukkit;
// import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
// import org.jetbrains.annotations.NotNull;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import fr.tylwen.satyria.dynashop.DynaShopPlugin;
// import fr.tylwen.satyria.dynashop.data.ItemPriceData;
import fr.tylwen.satyria.dynashop.data.ShopConfigManager;
import fr.tylwen.satyria.dynashop.data.cache.LimitCacheEntry;
import fr.tylwen.satyria.dynashop.data.storage.StorageManager;
// import fr.tylwen.satyria.dynashop.data.param.DynaShopType;
// import fr.tylwen.satyria.dynashop.database.DataManager;
// import fr.tylwen.satyria.dynashop.database.ItemDataManager;
// import fr.tylwen.satyria.dynashop.listener.DynaShopListener;
import fr.tylwen.satyria.dynashop.listener.ShopItemPlaceholderListener;
// import fr.tylwen.satyria.dynashop.price.DynamicPrice;
import fr.tylwen.satyria.dynashop.price.PriceRecipe;
import fr.tylwen.satyria.dynashop.system.TransactionLimiter.LimitPeriod;
// import fr.tylwen.satyria.dynashop.system.TransactionLimiter.TransactionLimit;
import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.brcdev.shopgui.ShopGuiPlusApi;
// import net.brcdev.shopgui.player.PlayerData;

public class DynaShopExpansion extends PlaceholderExpansion {

    private final DynaShopPlugin plugin;
    // private DataManager dataManager;
    // ItemDataManager itemDataManager = new ItemDataManager(dataManager);
    // private final ItemDataManager itemDataManager;
    private final StorageManager storageManager;
    private final ShopConfigManager shopConfigManager;
    private final PriceRecipe priceRecipe;

    public DynaShopExpansion(DynaShopPlugin plugin) {
        this.plugin = plugin;
        // this.itemDataManager = plugin.getItemDataManager();
        this.shopConfigManager = plugin.getShopConfigManager();
        this.priceRecipe = plugin.getPriceRecipe();
        this.storageManager = plugin.getStorageManager();
    }

    // public DynaShopExpansion(ItemDataManager itemDataManager, ShopConfigManager shopConfigManager, PriceRecipe priceRecipe) {
    //     // this.plugin = DynaShopPlugin.getInstance();
    //     this.itemDataManager = itemDataManager;
    //     this.shopConfigManager = shopConfigManager;
    //     this.priceRecipe = priceRecipe;
    // }
    
    public String getIdentifier() {
        return "dynashop";
    }
    
    public String getAuthor() {
        return "Tylwen";
    }
    
    public String getVersion() {
        return this.plugin.getDescription().getVersion();
    }

    public boolean persist() {
        return true;
    }

    public boolean canRegister() {
        return true;
    }

    // // public boolean register() {
    // //     return super.register();
    // // }
    // @Override
    // public boolean register() {
    //     // Enregistrer les placeholders standards
    //     boolean registered = super.register();
        
    //     // Enregistrer notre placeholder générique avec ShopGUI+
    //     if (registered) {
    //         plugin.getLogger().info("Enregistrement des placeholders génériques pour ShopGUI+...");
    //         try {
    //             // Cette ligne dépend de la façon dont ShopGUI+ permet d'enregistrer des placeholders
    //             // Si ShopGUI+ a une méthode, utilisez-la ici
    //             ShopGuiPlusApi.registerPlaceholderHook("dynashop", this);
    //         } catch (Exception e) {
    //             plugin.getLogger().warning("Impossible d'enregistrer les placeholders avec ShopGUI+: " + e.getMessage());
    //         }
    //     }
        
    //     return registered;
    // }
    @Override
    public boolean register() {
        // Enregistrer les placeholders standards
        boolean registered = super.register();
        
        // Log informationnel
        if (registered) {
            plugin.getLogger().info("Placeholders DynaShop enregistrés avec succès");
            // plugin.getLogger().info("Utilisez %dynashop_current_buyPrice% pour obtenir le prix d'achat actuel");
            // plugin.getLogger().info("Utilisez %dynashop_current_buyMinPrice% pour obtenir le prix d'achat minimum actuel");
            // plugin.getLogger().info("Utilisez %dynashop_current_buyMaxPrice% pour obtenir le prix d'achat maximum actuel");
            // plugin.getLogger().info("Utilisez %dynashop_current_sellPrice% pour obtenir le prix de vente actuel");
            // plugin.getLogger().info("Utilisez %dynashop_current_sellMinPrice% pour obtenir le prix de vente minimum actuel");
            // plugin.getLogger().info("Utilisez %dynashop_current_sellMaxPrice% pour obtenir le prix de vente maximum actuel");
            // plugin.getLogger().info("Utilisez %dynashop_buy_price_shopID:itemID% pour obtenir le prix d'achat d'un item dans un shop");
            // try {
            //     // Enregistrer notre placeholder générique avec ShopGUI+
            //     plugin.getLogger().info("Enregistrement des placeholders génériques pour ShopGUI+...");
            //     ShopGuiPlusApi.registerPlaceholderHook("dynashop", this);
            // } catch (Exception e) {
            //     plugin.getLogger().warning("Impossible d'enregistrer les placeholders avec ShopGUI+: " + e.getMessage());
            // }
        }
        
        return registered;
    }

    // /**
    //  * Obtient le prix selon le type spécifié.
    //  * @param shopID l'ID du shop
    //  * @param itemID l'ID de l'item
    //  * @param priceType le type de prix (buy, sell, buy_min, etc.)
    //  * @return le prix formaté en String
    //  */
    // private String getPriceByType(String shopID, String itemID, String priceType) {
    //     // Vérifier d'abord si "useRecipe" est activé
    //     // boolean useRecipe = shopConfigManager.getItemValue(shopID, itemID, "useRecipe", Boolean.class).orElse(false);
    //     boolean useRecipe = shopConfigManager.getTypeDynaShop(shopID, itemID).orElse(DynaShopType.NONE) == DynaShopType.RECIPE;
        
    //     switch (priceType) {
    //         case "buy":
    //             if (useRecipe) {
    //                 // Calculer le prix via la recette
    //                 ItemStack itemStack = ShopGuiPlusApi.getShop(shopID).getShopItem(itemID).getItem();
    //                 if (itemStack != null) {
    //                     double recipeBuyPrice = priceRecipe.calculateBuyPrice(shopID, itemID, itemStack, new ArrayList<>());
    //                     return String.format("%.3f", recipeBuyPrice);
    //                 }
    //                 return "N/A"; // Aucun prix disponible via recette
    //             }

    //             // Priorité à la base de données
    //             Optional<Double> buyPrice = this.itemDataManager.getBuyPrice(shopID, itemID);
    //             if (buyPrice.isPresent()) {
    //                 return String.format("%.3f", buyPrice.get());
    //             }

    //             // Fallback sur les fichiers de configuration
    //             Optional<Double> configBuyPrice = shopConfigManager.getItemValue(shopID, itemID, "buyPrice", Double.class);
    //             if (configBuyPrice.isPresent()) {
    //                 return String.format("%.3f", configBuyPrice.get());
    //             }
                
    //             return "N/A";
                
    //         case "sell":
    //             if (useRecipe) {
    //                 // Calculer le prix via la recette
    //                 ItemStack itemStack = ShopGuiPlusApi.getShop(shopID).getShopItem(itemID).getItem();
    //                 if (itemStack != null) {
    //                     double recipeSellPrice = priceRecipe.calculateSellPrice(shopID, itemID, itemStack, new ArrayList<>());
    //                     return String.format("%.3f", recipeSellPrice);
    //                 }
    //                 return "N/A"; // Aucun prix disponible via recette
    //             }

    //             // Priorité à la base de données
    //             Optional<Double> sellPrice = this.itemDataManager.getSellPrice(shopID, itemID);
    //             if (sellPrice.isPresent()) {
    //                 return String.format("%.3f", sellPrice.get());
    //             }

    //             // Fallback sur les fichiers de configuration
    //             Optional<Double> configSellPrice = shopConfigManager.getItemValue(shopID, itemID, "sellPrice", Double.class);
    //             if (configSellPrice.isPresent()) {
    //                 return String.format("%.3f", configSellPrice.get());
    //             }
                
    //             return "N/A";
                
    //         case "buy_min":
    //             Optional<Double> minBuyPrice = shopConfigManager.getItemValue(shopID, itemID, "minBuy", Double.class);
    //             if (minBuyPrice.isPresent()) {
    //                 return String.format("%.3f", minBuyPrice.get());
    //             }
    //             return "N/A";
                
    //         case "buy_max":
    //             Optional<Double> maxBuyPrice = shopConfigManager.getItemValue(shopID, itemID, "maxBuy", Double.class);
    //             if (maxBuyPrice.isPresent()) {
    //                 return String.format("%.3f", maxBuyPrice.get());
    //             }
    //             return "N/A";
                
    //         case "sell_min":
    //             Optional<Double> minSellPrice = shopConfigManager.getItemValue(shopID, itemID, "minSell", Double.class);
    //             if (minSellPrice.isPresent()) {
    //                 return String.format("%.3f", minSellPrice.get());
    //             }
    //             return "N/A";
                
    //         case "sell_max":
    //             Optional<Double> maxSellPrice = shopConfigManager.getItemValue(shopID, itemID, "maxSell", Double.class);
    //             if (maxSellPrice.isPresent()) {
    //                 return String.format("%.3f", maxSellPrice.get());
    //             }
    //             return "N/A";
                
    //         default:
    //             return "Type inconnu";
    //     }
    // }
    // /**
    //  * Obtient le prix selon le type spécifié.
    //  */
    // public String getPriceByType(String shopID, String itemID, String priceType) {
    //     // Vérifier d'abord si "useRecipe" est activé
    //     // boolean useRecipe = shopConfigManager.getItemValue(shopID, itemID, "useRecipe", Boolean.class).orElse(false);
    //     boolean useRecipe = shopConfigManager.getTypeDynaShop(shopID, itemID).orElse(DynaShopType.NONE) == DynaShopType.RECIPE;
    //     ItemStack itemStack = null;
        
    //     if (useRecipe) {
    //         itemStack = ShopGuiPlusApi.getShop(shopID).getShopItem(itemID).getItem();
    //         if (itemStack == null) {
    //             return "N/A";
    //         }
    //     }
        
    //     switch (priceType) {
    //         case "buy":
    //             if (useRecipe && itemStack != null) {
    //                 double recipeBuyPrice = priceRecipe.calculateBuyPrice(shopID, itemID, itemStack, new ArrayList<>());
    //                 // return String.format("%.3f", recipeBuyPrice);
    //                 // return String.valueOf(recipeBuyPrice);
    //                 // return String.format("%." + maximumFractionDigits + "f", recipeBuyPrice);
    //                 return formatPrice(recipeBuyPrice);
    //             }
    //             // return this.itemDataManager.getBuyPrice(shopID, itemID)
    //             //     .or(() -> shopConfigManager.getItemValue(shopID, itemID, "buyPrice", Double.class))
    //             //     .map(price -> String.format("%.3f", price))
    //             //     .orElse("N/A");

    //             // Priorité à la base de données
    //             Optional<Double> buyPrice = this.itemDataManager.getBuyPrice(shopID, itemID);
    //             if (buyPrice.isPresent()) {
    //                 // return String.format("%.3f", buyPrice.get());
    //                 return formatPrice(buyPrice.get());
    //             }

    //             // Fallback sur les fichiers de configuration
    //             Optional<Double> configBuyPrice = shopConfigManager.getItemValue(shopID, itemID, "buyPrice", Double.class);
    //             if (configBuyPrice.isPresent()) {
    //                 // return String.format("%.3f", configBuyPrice.get());
    //                 return formatPrice(configBuyPrice.get());
    //             }

    //             return "N/A";
                    
    //         case "sell":
    //             if (useRecipe && itemStack != null) {
    //                 double recipeSellPrice = priceRecipe.calculateSellPrice(shopID, itemID, itemStack, new ArrayList<>());
    //                 // return String.format("%.3f", recipeSellPrice);
    //                 return formatPrice(recipeSellPrice);
    //             }
    //             // return this.itemDataManager.getSellPrice(shopID, itemID)
    //             //     .or(() -> shopConfigManager.getItemValue(shopID, itemID, "sellPrice", Double.class))
    //             //     .map(price -> String.format("%.3f", price))
    //             //     .orElse("N/A");
                
    //             // Priorité à la base de données
    //             Optional<Double> sellPrice = this.itemDataManager.getSellPrice(shopID, itemID);
    //             if (sellPrice.isPresent()) {
    //                 // return String.format("%.3f", sellPrice.get());
    //                 return formatPrice(sellPrice.get());
    //             }

    //             // Fallback sur les fichiers de configuration
    //             Optional<Double> configSellPrice = shopConfigManager.getItemValue(shopID, itemID, "sellPrice", Double.class);
    //             if (configSellPrice.isPresent()) {
    //                 // return String.format("%.3f", configSellPrice.get());
    //                 return formatPrice(configSellPrice.get());
    //             }

    //             return "N/A";
            
    //         case "buy_min":
    //             if (useRecipe && itemStack != null) {
    //                 // Utiliser la valeur en cache si disponible
    //                 double cachedMin = plugin.getCachedRecipePrice(shopID, itemID, "buyDynamic.min");
    //                 if (cachedMin >= 0) {
    //                     // return String.format("%.3f", cachedMin);
    //                     return formatPrice(cachedMin);
    //                 }
    //             }
    //             // return shopConfigManager.getItemValue(shopID, itemID, "buyDynamic.min", Double.class)
    //             //     .map(price -> String.format("%.3f", price))
    //             //     .orElse("N/A");

    //             Optional<Double> minBuyPrice = shopConfigManager.getItemValue(shopID, itemID, "buyDynamic.min", Double.class);
    //             if (minBuyPrice.isPresent()) {
    //                 // return String.format("%.3f", minBuyPrice.get());
    //                 return formatPrice(minBuyPrice.get());
    //             }
    //             return "N/A";
                    
    //         case "buy_max":
    //             if (useRecipe && itemStack != null) {
    //                 double cachedMax = plugin.getCachedRecipePrice(shopID, itemID, "buyDynamic.max");
    //                 if (cachedMax >= 0) {
    //                     // return String.format("%.3f", cachedMax);
    //                     return formatPrice(cachedMax);
    //                 }
    //             }
    //             // return shopConfigManager.getItemValue(shopID, itemID, "buyDynamic.max", Double.class)
    //             //     .map(price -> String.format("%.3f", price))
    //             //     .orElse("N/A");
    //             Optional<Double> maxBuyPrice = shopConfigManager.getItemValue(shopID, itemID, "buyDynamic.max", Double.class);
    //             if (maxBuyPrice.isPresent()) {
    //                 // return String.format("%.3f", maxBuyPrice.get());
    //                 return formatPrice(maxBuyPrice.get());
    //             }
    //             return "N/A";
                    
    //         case "sell_min":
    //             if (useRecipe && itemStack != null) {
    //                 double cachedMin = plugin.getCachedRecipePrice(shopID, itemID, "sellDynamic.min");
    //                 if (cachedMin >= 0) {
    //                     // return String.format("%.3f", cachedMin);
    //                     return formatPrice(cachedMin);
    //                 }
    //             }
    //             // return shopConfigManager.getItemValue(shopID, itemID, "sellDynamic.min", Double.class)
    //             //     .map(price -> String.format("%.3f", price))
    //             //     .orElse("N/A");
    //             Optional<Double> minSellPrice = shopConfigManager.getItemValue(shopID, itemID, "sellDynamic.min", Double.class);
    //             if (minSellPrice.isPresent()) {
    //                 // return String.format("%.3f", minSellPrice.get());
    //                 return formatPrice(minSellPrice.get());
    //             }
    //             return "N/A";
                    
    //         case "sell_max":
    //             if (useRecipe && itemStack != null) {
    //                 double cachedMax = plugin.getCachedRecipePrice(shopID, itemID, "sellDynamic.max");
    //                 if (cachedMax >= 0) {
    //                     // return String.format("%.3f", cachedMax);
    //                     return formatPrice(cachedMax);
    //                 }
    //             }
    //             // return shopConfigManager.getItemValue(shopID, itemID, "sellDynamic.max", Double.class)
    //             //     .map(price -> String.format("%.3f", price))
    //             //     .orElse("N/A");
    //             Optional<Double> maxSellPrice = shopConfigManager.getItemValue(shopID, itemID, "sellDynamic.max", Double.class);
    //             if (maxSellPrice.isPresent()) {
    //                 // return String.format("%.3f", maxSellPrice.get());
    //                 return formatPrice(maxSellPrice.get());
    //             }
    //             return "N/A";
                
    //         default:
    //             return "Type inconnu";
    //     }
    // }

    // public String getStockByType(String shopID, String itemID, String stockType) {
    //     // // Vérifier d'abord si "useRecipe" est activé
    //     // boolean useRecipe = shopConfigManager.getTypeDynaShop(shopID, itemID).orElse(DynaShopType.NONE) == DynaShopType.RECIPE;
    //     // ItemStack itemStack = null;
        
    //     // if (useRecipe) {
    //     //     itemStack = ShopGuiPlusApi.getShop(shopID).getShopItem(itemID).getItem();
    //     //     if (itemStack == null) {
    //     //         return "N/A";
    //     //     }
    //     // }
        
    //     switch (stockType) {
    //         case "stock":
    //             // if (useRecipe && itemStack != null) {
    //             //     // Utiliser la valeur en cache si disponible
    //             //     double cachedStock = plugin.getCachedRecipePrice(shopID, itemID, "stock");
    //             //     if (cachedStock >= 0) {
    //             //         return String.valueOf((int) cachedStock);
    //             //     }
    //             // }
    //             Optional<Integer> stock = this.itemDataManager.getStock(shopID, itemID);
    //             if (stock.isPresent()) {
    //                 return String.valueOf(stock.get());
    //             }
    //             Optional<Integer> configStock = shopConfigManager.getItemValue(shopID, itemID, "stock.base", Integer.class);
    //             if (configStock.isPresent()) {
    //                 return String.valueOf(configStock.get());
    //             }
    //             return "N/A";
    //         case "stock_min":
    //             // if (useRecipe && itemStack != null) {
    //             //     double cachedMinStock = plugin.getCachedRecipePrice(shopID, itemID, "stock.min");
    //             //     if (cachedMinStock >= 0) {
    //             //         return String.valueOf((int) cachedMinStock);
    //             //     }
    //             // }
    //             Optional<Integer> minStock = shopConfigManager.getItemValue(shopID, itemID, "stock.min", Integer.class);
    //             if (minStock.isPresent()) {
    //                 return String.valueOf(minStock.get());
    //             }
    //             return "N/A";
    //         case "stock_max":
    //             // if (useRecipe && itemStack != null) {
    //             //     double cachedMaxStock = plugin.getCachedRecipePrice(shopID, itemID, "stock.max");
    //             //     if (cachedMaxStock >= 0) {
    //             //         return String.valueOf((int) cachedMaxStock);
    //             //     }
    //             // }
    //             Optional<Integer> maxStock = shopConfigManager.getItemValue(shopID, itemID, "stock.max", Integer.class);
    //             if (maxStock.isPresent()) {
    //                 return String.valueOf(maxStock.get());
    //             }
    //             return "N/A";
    //         default:
    //             return "Type inconnu";
    //     }
    // }

    // /**
    //  * Récupère tous les prix et valeurs pour un item spécifique en UNE SEULE opération de base de données.
    //  * Beaucoup plus efficace qu'appeler getPriceByType() plusieurs fois.
    //  * 
    //  * @param shopID ID du shop
    //  * @param itemID ID de l'item
    //  * @return Map contenant toutes les valeurs formatées
    //  */
    // private Map<String, String> getAllItemValues(String shopID, String itemID) {
    //     Map<String, String> values = new HashMap<>();
        
    //     // // UN SEUL accès à la base de données pour récupérer toutes les informations
    //     // Optional<DynamicPrice> priceData = this.itemDataManager.getItemValues(shopID, itemID);
        
    //     // // UN SEUL accès aux fichiers de config pour récupérer les infos manquantes
    //     // ItemPriceData configData = shopConfigManager.getItemAllValues(shopID, itemID);
        
    //     // // // Formatage numérique
    //     // // int maximumFractionDigits = 8;
    //     // // if (ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().contains("numberFormat.maximumFractionDigits")) {
    //     // //     maximumFractionDigits = ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getInt("numberFormat.maximumFractionDigits");
    //     // // }
        
    //     // // Extraire toutes les valeurs en une fois
    //     // double buyPrice = priceData.map(DynamicPrice::getBuyPrice).orElse(configData.buyPrice.orElse(0.0));
    //     // double sellPrice = priceData.map(DynamicPrice::getSellPrice).orElse(configData.sellPrice.orElse(0.0));
    //     // double minBuy = configData.minBuy.orElse(buyPrice * 0.5);
    //     // double maxBuy = configData.maxBuy.orElse(buyPrice * 2.0);
    //     // double minSell = configData.minSell.orElse(sellPrice * 0.5);
    //     // double maxSell = configData.maxSell.orElse(sellPrice * 2.0);

    //     DynamicPrice price = plugin.getDynaShopListener().getOrLoadPrice(player, shopID, itemID);

    //     // Formatter toutes les valeurs en texte
    //     values.put("buy", plugin.getPriceFormatter().formatPrice(price.getBuyPrice()));
    //     values.put("sell", plugin.getPriceFormatter().formatPrice(price.getSellPrice()));
    //     values.put("buy_min", plugin.getPriceFormatter().formatPrice(price.getMinBuy()));
    //     values.put("buy_max", plugin.getPriceFormatter().formatPrice(price.getMaxBuy()));
    //     values.put("sell_min", plugin.getPriceFormatter().formatPrice(price.getMinSell()));
    //     values.put("sell_max", plugin.getPriceFormatter().formatPrice(price.getMaxSell()));

    //     // Valeurs pour "N/A" ou zéro
    //     if (price.getBuyPrice() <= 0.001) values.put("buy", "N/A");
    //     if (price.getSellPrice() <= 0.001) values.put("sell", "N/A");
    //     if (price.getMinBuy() <= 0.001) values.put("buy_min", "N/A");
    //     if (price.getMaxBuy() <= 0.001) values.put("buy_max", "N/A");
    //     if (price.getMinSell() <= 0.001) values.put("sell_min", "N/A");
    //     if (price.getMaxSell() <= 0.001) values.put("sell_max", "N/A");

    //     return values;
    // }

    // /**
    // * Formate un prix en respectant les paramètres de configuration de ShopGUI+
    // * @param price Le prix à formater
    // * @param maxFractionDigits Nombre maximum de chiffres après la virgule
    // * @return Le prix formaté sous forme de chaîne
    // */
    // public String formatPrice(double price) {
    //     // Récupérer les séparateurs depuis la configuration
    //     int maximumFractionDigits = ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getInt("numberFormat.maximumFractionDigits", 8);
    //     String decimalSeparator = ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getString("numberFormat.decimalSeparator", ".");
    //     String groupingSeparator = ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getString("numberFormat.groupingSeparator", ",");
    //     int minimumFractionDigits = ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getInt("numberFormat.minimumFractionDigits", 0);
    //     boolean hideFraction = ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getBoolean("numberFormat.hideFraction", true);
        
    //     // Vérifier si le nombre est un entier et si hideFraction est activé
    //     boolean isInteger = (price == Math.floor(price)) && !Double.isInfinite(price);
        
    //     // Configurer les symboles de formatage
    //     DecimalFormatSymbols symbols = new DecimalFormatSymbols();
    //     symbols.setDecimalSeparator(decimalSeparator.charAt(0));
    //     symbols.setGroupingSeparator(groupingSeparator.charAt(0));
        
    //     // Créer un format adapté
    //     DecimalFormat df;
        
    //     if (hideFraction && isInteger) {
    //         // Aucune décimale pour les entiers quand hideFraction est activé
    //         df = new DecimalFormat("#,##0", symbols);
    //         df.setMinimumFractionDigits(0);
    //         df.setMaximumFractionDigits(0);
    //     } else {
    //         // Pattern normal pour les nombres avec décimales
    //         df = new DecimalFormat("#,##0.#", symbols);
    //         df.setMaximumFractionDigits(maximumFractionDigits);
    //         df.setMinimumFractionDigits(minimumFractionDigits);
    //     }
        
    //     // Activer le regroupement des chiffres
    //     df.setGroupingUsed(true);
        
    //     return df.format(price);
    // }

    // public String formatStock(int stock) {
    //     // Récupérer les séparateurs depuis la configuration
    //     String groupingSeparator = ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getString("numberFormat.groupingSeparator", ",");
        
    //     // Configurer les symboles de formatage
    //     DecimalFormatSymbols symbols = new DecimalFormatSymbols();
    //     symbols.setGroupingSeparator(groupingSeparator.charAt(0));
        
    //     // Créer un format adapté pour les entiers
    //     DecimalFormat df = new DecimalFormat("#,##0", symbols);
    //     df.setGroupingUsed(true);
        
    //     return df.format(stock);
    // }
    
    public String onRequest(OfflinePlayer player, String identifier) {
        // %dynashop_current_buy%
        if (identifier.equals("current_buy") && player instanceof Player p) {
            // Player p = (Player) player;
            ShopItemPlaceholderListener listener = plugin.getShopItemPlaceholderListener();

            String shopId = listener.getCurrentShopId(p);
            String itemId = listener.getCurrentItemId(p);

            if (shopId != null && itemId != null) {
                // Récupérer le prix actuel
                String buyPrice = plugin.getPriceFormatter().getPriceByType(shopId, itemId, "buy");
                String buyMinPrice = plugin.getPriceFormatter().getPriceByType(shopId, itemId, "buy_min");
                String buyMaxPrice = plugin.getPriceFormatter().getPriceByType(shopId, itemId, "buy_max");

                if (buyPrice != null && buyMinPrice != null && buyMaxPrice != null) {
                    return String.format("%s (%s - %s)", buyPrice, buyMinPrice, buyMaxPrice);
                } else if (buyPrice != null) {
                    return buyPrice;
                } else {
                    return "N/A"; // Aucun prix disponible
                }
            }
        }

        // %dynashop_current_buyPrice%
        if (identifier.equals("current_buyPrice") && player instanceof Player p) {
            // Player p = (Player) player;
            ShopItemPlaceholderListener listener = plugin.getShopItemPlaceholderListener();
            
            // Vérification de nullité
            if (listener == null) {
                plugin.getLogger().warning("ShopItemPlaceholderListener est null dans DynaShopExpansion.onRequest()");
                return "N/A";
            }
            
            String shopId = listener.getCurrentShopId(p);
            String itemId = listener.getCurrentItemId(p);

            // plugin.getLogger().info("Shop ID: " + shopId + ", Item ID: " + itemId);
            
            if (shopId != null && itemId != null) {
                // Récupérer le prix actuel
                String price = plugin.getPriceFormatter().getPriceByType(shopId, itemId, "buy");
                return price != null ? price : "N/A"; // Si le prix est null, retourner "N/A"
            }
            
            return "N/A"; // Pas de shop ouvert
        }

        // %dynashop_current_buyMinPrice%
        if (identifier.equals("current_buyMinPrice") && player instanceof Player p) {
            // Player p = (Player) player;
            ShopItemPlaceholderListener listener = plugin.getShopItemPlaceholderListener();
            
            // Vérification de nullité
            if (listener == null) {
                plugin.getLogger().warning("ShopItemPlaceholderListener est null dans DynaShopExpansion.onRequest()");
                return "N/A";
            }
            
            String shopId = listener.getCurrentShopId(p);
            String itemId = listener.getCurrentItemId(p);
            
            if (shopId != null && itemId != null) {
                // Récupérer le prix actuel
                String price = plugin.getPriceFormatter().getPriceByType(shopId, itemId, "buy_min");
                return price != null ? price : "N/A"; // Si le prix est null, retourner "N/A"
            }
            
            return "N/A"; // Pas de shop ouvert
        }

        // %dynashop_current_buyMaxPrice%
        if (identifier.equals("current_buyMaxPrice") && player instanceof Player p) {
            // Player p = (Player) player;
            ShopItemPlaceholderListener listener = plugin.getShopItemPlaceholderListener();
            
            // Vérification de nullité
            if (listener == null) {
                plugin.getLogger().warning("ShopItemPlaceholderListener est null dans DynaShopExpansion.onRequest()");
                return "N/A";
            }
            
            String shopId = listener.getCurrentShopId(p);
            String itemId = listener.getCurrentItemId(p);
            
            if (shopId != null && itemId != null) {
                // Récupérer le prix actuel
                String price = plugin.getPriceFormatter().getPriceByType(shopId, itemId, "buy_max");
                return price != null ? price : "N/A"; // Si le prix est null, retourner "N/A"
            }
            
            return "N/A"; // Pas de shop ouvert
        }
        
        // %dynashop_current_sell%
        if (identifier.equals("current_sell") && player instanceof Player p) {
            // Player p = (Player) player;
            ShopItemPlaceholderListener listener = plugin.getShopItemPlaceholderListener();

            String shopId = listener.getCurrentShopId(p);
            String itemId = listener.getCurrentItemId(p);

            if (shopId != null && itemId != null) {
                // Récupérer le prix actuel
                String sellPrice = plugin.getPriceFormatter().getPriceByType(shopId, itemId, "sell");
                String sellMinPrice = plugin.getPriceFormatter().getPriceByType(shopId, itemId, "sell_min");
                String sellMaxPrice = plugin.getPriceFormatter().getPriceByType(shopId, itemId, "sell_max");

                if (sellPrice != null && sellMinPrice != null && sellMaxPrice != null) {
                    return String.format("%s (%s - %s)", sellPrice, sellMinPrice, sellMaxPrice);
                } else if (sellPrice != null) {
                    return sellPrice;
                } else {
                    return "N/A"; // Aucun prix disponible
                }
            }
        }

        // %dynashop_current_sellPrice%
        if (identifier.equals("current_sellPrice") && player instanceof Player p) {
            // Player p = (Player) player;
            ShopItemPlaceholderListener listener = plugin.getShopItemPlaceholderListener();
            
            // Vérification de nullité
            if (listener == null) {
                plugin.getLogger().warning("ShopItemPlaceholderListener est null dans DynaShopExpansion.onRequest()");
                return "N/A";
            }
            
            String shopId = listener.getCurrentShopId(p);
            String itemId = listener.getCurrentItemId(p);
            
            if (shopId != null && itemId != null) {
                // Récupérer le prix actuel
                String price = plugin.getPriceFormatter().getPriceByType(shopId, itemId, "sell");
                return price != null ? price : "N/A"; // Si le prix est null, retourner "N/A"
            }

            return "N/A"; // Pas de shop ouvert
        }

        // %dynashop_current_sellMinPrice%
        if (identifier.equals("current_sellMinPrice") && player instanceof Player p) {
            // Player p = (Player) player;
            ShopItemPlaceholderListener listener = plugin.getShopItemPlaceholderListener();
            
            // Vérification de nullité
            if (listener == null) {
                plugin.getLogger().warning("ShopItemPlaceholderListener est null dans DynaShopExpansion.onRequest()");
                return "N/A";
            }
            
            String shopId = listener.getCurrentShopId(p);
            String itemId = listener.getCurrentItemId(p);
            
            if (shopId != null && itemId != null) {
                // Récupérer le prix actuel
                String price = plugin.getPriceFormatter().getPriceByType(shopId, itemId, "sell_min");
                return price != null ? price : "N/A"; // Si le prix est null, retourner "N/A"
            }
            
            return "N/A"; // Pas de shop ouvert
        }

        // %dynashop_current_sellMaxPrice%
        if (identifier.equals("current_sellMaxPrice") && player instanceof Player p) {
            // Player p = (Player) player;
            ShopItemPlaceholderListener listener = plugin.getShopItemPlaceholderListener();
            
            // Vérification de nullité
            if (listener == null) {
                plugin.getLogger().warning("ShopItemPlaceholderListener est null dans DynaShopExpansion.onRequest()");
                return "N/A";
            }
            
            String shopId = listener.getCurrentShopId(p);
            String itemId = listener.getCurrentItemId(p);
            
            if (shopId != null && itemId != null) {
                // Récupérer le prix actuel
                String price = plugin.getPriceFormatter().getPriceByType(shopId, itemId, "sell_max");
                return price != null ? price : "N/A"; // Si le prix est null, retourner "N/A"
            }
            
            return "N/A"; // Pas de shop ouvert
        }
        
        // %dynashop_buy_price_shopID:itemID%
        // // if (identifier.contains("buy_price")) {
        // if (identifier.startsWith("buy_price")) {
        //     // String itemID = identifier.substring(11, identifier.length() - 1).toUpperCase().replace("-", "_").replace(" ", "_");
        //     // String itemID = identifier.substring(10).toUpperCase().replace("-", "_").replace(" ", "_");
        //     // String[] parts = identifier.split(":");
        //     String[] parts = identifier.substring(10).split(":");
        //     if (parts.length != 2) {
        //         return null; // Invalid format
        //     }
        //     String shopID = parts[0].replace("-", "_").replace(" ", "_"); // Extract shopID from the placeholder
        //     String itemID = parts[1].replace("-", "_").replace(" ", "_"); // Extract itemID from the placeholder

        //     // try {
        //     //     return Double.toString(this.itemDataManager.getBuyPrice(shopID, itemID).orElse(0.0));
        //     // } catch (SQLException e) {
        //     //     e.printStackTrace();
        //     //     return null;
        //     // }

        //     Optional<Double> buyPrice = this.itemDataManager.getBuyPrice(shopID, itemID);
        //     if (buyPrice.isPresent()) {
        //         return Double.toString(buyPrice.get());
        //         // return String.valueOf(buyPrice.get());
        //     }
        //     // on doit sortir un double et pas un string
        //     return shopConfigManager.getItemValue(shopID, itemID, "buyPrice")
        //         .map(String::valueOf)
        //         .orElse("0.0");
        // }
        if (identifier.startsWith("buy_price")) {
            String[] parts = identifier.substring(10).split(":");
            if (parts.length != 2) {
                return null; // Format invalide
            }
            String shopID = parts[0].replace("-", "_").replace(" ", "_");
            String itemID = parts[1].replace("-", "_").replace(" ", "_");
            
            // Vérifier si "useRecipe" est activé dans la configuration
            boolean useRecipe = shopConfigManager.getItemValue(shopID, itemID, "useRecipe", Boolean.class).orElse(false);
            if (useRecipe) {
                // Calculer le prix via la recette
                ItemStack itemStack = ShopGuiPlusApi.getShop(shopID).getShopItem(itemID).getItem();
                if (itemStack != null) {
                    double recipeBuyPrice = priceRecipe.calculatePrice(shopID, itemID, itemStack, "buyPrice", new HashSet<>());
                    return String.valueOf(recipeBuyPrice);
                }
                return "N/A"; // Aucun prix disponible via recette
            }

            // Priorité à la base de données
            Optional<Double> buyPrice = this.storageManager.getBuyPrice(shopID, itemID);
            if (buyPrice.isPresent()) {
                return String.valueOf(buyPrice.get());
            }

            // Fallback sur les fichiers de configuration
            Optional<Double> configBuyPrice = shopConfigManager.getItemValue(shopID, itemID, "buyPrice", Double.class);
            if (configBuyPrice.isPresent()) {
                return String.valueOf(configBuyPrice.get());
            }

            // // Calculer le prix via la recette si activé
            // // ItemStack itemStack = shopConfigManager.getItemStack(shopID, itemID);
            // ItemStack itemStack = ShopGuiPlusApi.getShop(shopID).getShopItem(itemID).getItem();
            // if (itemStack != null) {
            //     double recipeBuyPrice = priceRecipe.calculateBuyPrice(itemStack);
            //     return String.valueOf(recipeBuyPrice);
            // }

            return "N/A"; // Aucun prix disponible
        }

        // %dynashop_sell_price_shopID:itemID%
        if (identifier.startsWith("sell_price")) {
            String[] parts = identifier.substring(11).split(":");
            if (parts.length != 2) {
                return null; // Format invalide
            }
            String shopID = parts[0].replace("-", "_").replace(" ", "_");
            String itemID = parts[1].replace("-", "_").replace(" ", "_");

            // Vérifier si "useRecipe" est activé dans la configuration
            boolean useRecipe = shopConfigManager.getItemValue(shopID, itemID, "useRecipe", Boolean.class).orElse(false);
            if (useRecipe) {
                // Calculer le prix via la recette
                ItemStack itemStack = ShopGuiPlusApi.getShop(shopID).getShopItem(itemID).getItem();
                if (itemStack != null) {
                    double recipeSellPrice = priceRecipe.calculatePrice(shopID, itemID, itemStack, "sellPrice", new HashSet<>());
                    return String.valueOf(recipeSellPrice);
                }
                return "N/A"; // Aucun prix disponible via recette
            }

            // Priorité à la base de données
            Optional<Double> sellPrice = this.storageManager.getSellPrice(shopID, itemID);
            if (sellPrice.isPresent()) {
                return String.valueOf(sellPrice.get());
            }

            // Fallback sur les fichiers de configuration
            Optional<Double> configSellPrice = shopConfigManager.getItemValue(shopID, itemID, "sellPrice", Double.class);
            if (configSellPrice.isPresent()) {
                return String.valueOf(configSellPrice.get());
            }

            return "N/A"; // Aucun prix disponible
        }

        // %dynashop_buy_min_price_shopID:itemID%
        if (identifier.startsWith("buy_min_price")) {
            String[] parts = identifier.substring(15).split(":");
            if (parts.length != 2) {
                return null; // Format invalide
            }
            String shopID = parts[0];
            String itemID = parts[1];

            // Vérifier si "useRecipe" est activé dans la configuration
            boolean useRecipe = shopConfigManager.getItemValue(shopID, itemID, "useRecipe", Boolean.class).orElse(false);
            if (useRecipe) {
                // Calculer le prix minimum via la recette
                ItemStack itemStack = ShopGuiPlusApi.getShop(shopID).getShopItem(itemID).getItem();
                if (itemStack != null) {
                    double recipeMinBuyPrice = priceRecipe.calculatePrice(shopID, itemID, itemStack, "buyDynamic.min", new HashSet<>()); // Utilisez un calcul spécifique si nécessaire
                    return String.valueOf(recipeMinBuyPrice);
                }
                return "N/A"; // Aucun prix disponible via recette
            }

            // Fallback sur les fichiers de configuration
            Optional<Double> minBuyPrice = shopConfigManager.getItemValue(shopID, itemID, "minBuy", Double.class);
            if (minBuyPrice.isPresent()) {
                return String.valueOf(minBuyPrice.get());
            }

            return "N/A"; // Aucun prix disponible
        }

        // %dynashop_buy_max_price_shopID:itemID%
        if (identifier.startsWith("buy_max_price")) {
            String[] parts = identifier.substring(15).split(":");
            if (parts.length != 2) {
                return null; // Format invalide
            }
            String shopID = parts[0];
            String itemID = parts[1];

            // Vérifier si "useRecipe" est activé dans la configuration
            boolean useRecipe = shopConfigManager.getItemValue(shopID, itemID, "useRecipe", Boolean.class).orElse(false);
            if (useRecipe) {
                // Calculer le prix maximum via la recette
                ItemStack itemStack = ShopGuiPlusApi.getShop(shopID).getShopItem(itemID).getItem();
                if (itemStack != null) {
                    double recipeMaxBuyPrice = priceRecipe.calculatePrice(shopID, itemID, itemStack, "buyDynamic.max", new HashSet<>()); // Utilisez un calcul spécifique si nécessaire
                    return String.valueOf(recipeMaxBuyPrice);
                }
                return "N/A"; // Aucun prix disponible via recette
            }

            // Fallback sur les fichiers de configuration
            Optional<Double> maxBuyPrice = shopConfigManager.getItemValue(shopID, itemID, "maxBuy", Double.class);
            if (maxBuyPrice.isPresent()) {
                return String.valueOf(maxBuyPrice.get());
            }

            return "N/A"; // Aucun prix disponible
        }

        // %dynashop_sell_min_price_shopID:itemID%
        if (identifier.startsWith("sell_min_price")) {
            String[] parts = identifier.substring(16).split(":");
            if (parts.length != 2) {
                return null; // Format invalide
            }
            String shopID = parts[0];
            String itemID = parts[1];

            // Vérifier si "useRecipe" est activé dans la configuration
            boolean useRecipe = shopConfigManager.getItemValue(shopID, itemID, "useRecipe", Boolean.class).orElse(false);
            if (useRecipe) {
                // Calculer le prix minimum via la recette
                ItemStack itemStack = ShopGuiPlusApi.getShop(shopID).getShopItem(itemID).getItem();
                if (itemStack != null) {
                    double recipeMinSellPrice = priceRecipe.calculatePrice(shopID, itemID, itemStack, "sellDynamic.min", new HashSet<>()); // Utilisez un calcul spécifique si nécessaire
                    return String.valueOf(recipeMinSellPrice);
                }
                return "N/A"; // Aucun prix disponible via recette
            }

            // Fallback sur les fichiers de configuration
            Optional<Double> minSellPrice = shopConfigManager.getItemValue(shopID, itemID, "minSell", Double.class);
            if (minSellPrice.isPresent()) {
                return String.valueOf(minSellPrice.get());
            }

            return "N/A"; // Aucun prix disponible
        }

        // %dynashop_sell_max_price_shopID:itemID%
        if (identifier.startsWith("sell_max_price")) {
            String[] parts = identifier.substring(16).split(":");
            if (parts.length != 2) {
                return null; // Format invalide
            }
            String shopID = parts[0];
            String itemID = parts[1];

            // Vérifier si "useRecipe" est activé dans la configuration
            boolean useRecipe = shopConfigManager.getItemValue(shopID, itemID, "useRecipe", Boolean.class).orElse(false);
            if (useRecipe) {
                // Calculer le prix maximum via la recette
                ItemStack itemStack = ShopGuiPlusApi.getShop(shopID).getShopItem(itemID).getItem();
                if (itemStack != null) {
                    double recipeMaxSellPrice = priceRecipe.calculatePrice(shopID, itemID, itemStack, "sellDynamic.max", new HashSet<>()); // Utilisez un calcul spécifique si nécessaire
                    return String.valueOf(recipeMaxSellPrice);
                }
                return "N/A"; // Aucun prix disponible via recette
            }

            // Fallback sur les fichiers de configuration
            Optional<Double> maxSellPrice = shopConfigManager.getItemValue(shopID, itemID, "maxSell", Double.class);
            if (maxSellPrice.isPresent()) {
                return String.valueOf(maxSellPrice.get());
            }

            return "N/A"; // Aucun prix disponible
        }

        // %dynashop_limit_buy_remaining_shopID:itemID%
        if (identifier.startsWith("limit_buy_remaining_")) {
            String[] parts = identifier.substring("limit_buy_remaining_".length()).split(":");
            if (parts.length != 2) return null;
            
            String shopID = parts[0];
            String itemID = parts[1];
            
            if (!(player instanceof Player)) return "N/A";
            
            // Utiliser directement LimitCacheEntry au lieu de getRemainingAmountSync
            LimitCacheEntry limit = plugin.getTransactionLimiter().getTransactionLimit((Player)player, shopID, itemID, true);
            if (limit != null && limit.baseLimit > 0) {
                // Accéder directement à la propriété remaining
                return String.valueOf(limit.remaining);
            } else {
                return plugin.getLangConfig().getPlaceholderNoLimit();
            }
        }

        // %dynashop_limit_sell_remaining_shopID:itemID%
        if (identifier.startsWith("limit_sell_remaining_")) {
            String[] parts = identifier.substring("limit_sell_remaining_".length()).split(":");
            if (parts.length != 2) return null;
            
            String shopID = parts[0];
            String itemID = parts[1];
            
            if (!(player instanceof Player)) return "N/A";
            
            // Utiliser directement LimitCacheEntry au lieu de getRemainingAmountSync
            LimitCacheEntry limit = plugin.getTransactionLimiter().getTransactionLimit((Player)player, shopID, itemID, false);
            if (limit != null && limit.baseLimit > 0) {
                // Accéder directement à la propriété remaining
                return String.valueOf(limit.remaining);
            } else {
                return plugin.getLangConfig().getPlaceholderNoLimit();
            }
        }

        if (player != null) {
            // Limite d'achat pour un item spécifique
            if (identifier.startsWith("buy_limit_")) {
                String itemInfo = identifier.substring("buy_limit_".length());
                String[] parts = itemInfo.split("_");
                if (parts.length == 2) {
                    String shopId = parts[0];
                    String itemId = parts[1];
                    LimitCacheEntry limit = plugin.getTransactionLimiter().getTransactionLimit((Player) player, shopId, itemId, true);
                    if (limit == null || limit.baseLimit <= 0) {
                        return plugin.getLangConfig().getPlaceholderNoLimit();
                    }
            
                    try {
                        // Accès direct à la propriété remaining
                        return String.valueOf(limit.remaining);
                    } catch (Exception e) {
                        return "N/A";
                    }
                }
            }
            
            // Limite de vente pour un item spécifique
            if (identifier.startsWith("sell_limit_")) {
                String itemInfo = identifier.substring("sell_limit_".length());
                String[] parts = itemInfo.split("_");
                if (parts.length == 2) {
                    String shopId = parts[0];
                    String itemId = parts[1];
                    LimitCacheEntry limit = plugin.getTransactionLimiter().getTransactionLimit((Player) player, shopId, itemId, false);
                    if (limit == null || limit.baseLimit <= 0) {
                        return plugin.getLangConfig().getPlaceholderNoLimit();
                    }
                    
                    try {
                        // Accès direct à la propriété remaining
                        return String.valueOf(limit.remaining);
                    } catch (Exception e) {
                        return "N/A";
                    }
                }
            }
            
            // Temps avant prochain reset pour un achat
            if (identifier.startsWith("next_buy_reset_")) {
                String itemInfo = identifier.substring("next_buy_reset_".length());
                String[] parts = itemInfo.split("_");
                if (parts.length == 2) {
                    String shopId = parts[0];
                    String itemId = parts[1];
                    LimitCacheEntry limit = plugin.getTransactionLimiter().getTransactionLimit((Player) player, shopId, itemId, true);
                    if (limit == null || limit.baseLimit <= 0) {
                        return plugin.getLangConfig().getPlaceholderNoLimit();
                    }
                    
                    try {
                        // Accès direct à la propriété nextAvailable
                        return formatTimeRemaining(limit.nextAvailable, limit);
                    } catch (Exception e) {
                        return "N/A";
                    }
                }
            }
            
            // Temps avant prochain reset pour une vente
            if (identifier.startsWith("next_sell_reset_")) {
                String itemInfo = identifier.substring("next_sell_reset_".length());
                String[] parts = itemInfo.split("_");
                if (parts.length == 2) {
                    String shopId = parts[0];
                    String itemId = parts[1];
                    LimitCacheEntry limit = plugin.getTransactionLimiter().getTransactionLimit((Player) player, shopId, itemId, false);
                    if (limit == null || limit.baseLimit <= 0) {
                        return plugin.getLangConfig().getPlaceholderNoLimit();
                    }
                    
                    try {
                        // Accès direct à la propriété nextAvailable
                        return formatTimeRemaining(limit.nextAvailable, limit);
                    } catch (Exception e) {
                        return "N/A";
                    }
                }
            }
        }
        return null; // Placeholder non reconnu

        // // %dynashop_buy_min_price_shopID:itemID%
        // if (identifier.contains("buy_min_price")) {
        //     // String itemID = identifier.substring(15).toUpperCase().replace("-", "_").replace(" ", "_");
        //     if (identifier.split(":").length != 2) {
        //         return null; // Invalid format
        //     }
        //     String shopID = identifier.substring(15, identifier.indexOf(":"));
        //     String itemID = identifier.substring(identifier.indexOf(":") + 1).replace("-", "_").replace(" ", "_");
        //     try {
        //         // la valeur est dans le fichier de config de ShopGui+
        //         return Double.toString(this.itemDataManager.getBuyMinPrice(shopID, itemID).orElse(0.0));
        //     } catch (SQLException e) {
        //         e.printStackTrace();
        //         return null;
        //     }
        // }

        // // %dynashop_buy_max_price_shopID:itemID%
        // if (identifier.contains("buy_max_price")) {
        //     String itemID = identifier.substring(15).toUpperCase().replace("-", "_").replace(" ", "_");
        //     try {
        //         return Double.toString(this.itemDataManager.getBuyMaxPrice(itemID));
        //     } catch (SQLException e) {
        //         e.printStackTrace();
        //         return null;
        //     }
        // }
        
        // // %dynashop_sell_price_shopID:itemID%
        // if (identifier.contains("sell_price")) {
        //     String itemID = identifier.substring(11).toUpperCase().replace("-", "_").replace(" ", "_");
        //     try {
        //         return Double.toString(this.itemDataManager.getSellPrice(itemID));
        //     } catch (SQLException e) {
        //         e.printStackTrace();
        //         return null;
        //     }
        // }

        // // %dynashop_sell_min_price_shopID:itemID%
        // if (identifier.contains("sell_min_price")) {
        //     String itemID = identifier.substring(16).toUpperCase().replace("-", "_").replace(" ", "_");
        //     try {
        //         return Double.toString(this.itemDataManager.getSellMinPrice(itemID));
        //     } catch (SQLException e) {
        //         e.printStackTrace();
        //         return null;
        //     }
        // }

        // // %dynashop_sell_max_price_shopID:itemID%
        // if (identifier.contains("sell_max_price")) {
        //     String itemID = identifier.substring(16).toUpperCase().replace("-", "_").replace(" ", "_");
        //     try {
        //         return Double.toString(this.itemDataManager.getSellMaxPrice(itemID));
        //     } catch (SQLException e) {
        //         e.printStackTrace();
        //         return null;
        //     }
        // }
        
        // // We return null if an invalid placeholder (f.e. %someplugin_placeholder3%)
        // // was provided
        // return "";
    }

    /**
     * Formate un temps en millisecondes en une chaîne lisible
     * @param millisRemaining Temps restant en millisecondes
     * @param limit L'entrée de cache de la limite
     * @return Chaîne formatée (ex: "04.03.2023 00:00:00" ou "01h 30m 45s")
     */
    private String formatTimeRemaining(long millisRemaining, LimitCacheEntry limit) {
        if (millisRemaining <= 0) {
            return plugin.getLangConfig().getPlaceholderNoLimit();
        }
        
        // Si c'est une période prédéfinie (DAILY, WEEKLY, etc.), on affiche la date complète
        LimitPeriod period = getPeriodForCooldown(limit.cooldown);
        if (period != LimitPeriod.NONE) {
            LocalDateTime resetTime = LocalDateTime.now().plus(millisRemaining, ChronoUnit.MILLIS);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
            return resetTime.format(formatter);
        }
        
        // Sinon c'est un cooldown numérique, on affiche juste le temps restant
        long secondsRemaining = millisRemaining / 1000;
        long hours = secondsRemaining / 3600;
        long minutes = (secondsRemaining % 3600) / 60;
        long seconds = secondsRemaining % 60;
        
        if (hours > 0) {
            return String.format("%02dh %02dm %02ds", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%02dm %02ds", minutes, seconds);
        } else {
            return String.format("%02ds", seconds);
        }
    }

    /**
     * Détermine la période équivalente pour un cooldown donné
     */
    private LimitPeriod getPeriodForCooldown(int cooldown) {
        if (cooldown >= 31536000) return LimitPeriod.FOREVER;
        if (cooldown >= 2592000) return LimitPeriod.MONTHLY;
        if (cooldown >= 604800) return LimitPeriod.WEEKLY;
        if (cooldown >= 86400) return LimitPeriod.DAILY;
        return LimitPeriod.NONE;
    }

    public String setPlaceholders(Player player, String identifier) {
        return PlaceholderAPI.setPlaceholders(player, identifier);
    }


    // /**
    //  * Cette méthode spéciale est appelée directement par ShopGUI+ via PlaceholderAPI
    //  * pour obtenir automatiquement les prix pour l'item actuellement affiché.
    //  */
    // public String onPlaceholderRequest(Player player, String identifier) {
    //     if (player == null) {
    //         return "";
    //     }
        
    //     // Format: shop_price_TYPE - où TYPE peut être: buy, sell, buy_min, buy_max, sell_min, sell_max
    //     if (identifier.startsWith("shop_price_")) {
    //         String priceType = identifier.substring(11); // Extraire le type après "shop_price_"
            
    //         // Récupérer l'item et le shop actuellement visualisés par le joueur
    //         // Utilisation de la méthode getNativeShopItemFromContext de l'API ShopGUI+
    //         try {
    //             // Object[] context = ShopGuiPlusApi.getOpenShopContext(player);
    //             Object[] context = ShopGuiPlusApi.getPlugin().getPlayerManager().getPlayerData(player).getOpenGui().toString().split(",");
    //             if (context == null || context.length < 2) {
    //                 return "N/A"; // Aucun contexte disponible
    //             }
                
    //             String shopID = (String) context[0];
    //             String itemID = (String) context[1];
                
    //             // Maintenant que nous avons le shopID et itemID, on peut obtenir le prix
    //             return getPriceByType(shopID, itemID, priceType);
    //         } catch (Exception e) {
    //             plugin.getLogger().warning("Erreur lors de la récupération du contexte ShopGUI+: " + e.getMessage());
    //             return "Erreur";
    //         }
    //     }
        
    //     return null; // Pas notre placeholder
    // }

}

// public class DynaShopExpansion extends PlaceholderExpansion {

//     private final ItemDataManager itemDataManager;
//     private final ShopConfigManager shopConfigManager;
//     private final PriceRecipe priceRecipe;

//     private final Map<String, BiFunction<String[], OfflinePlayer, String>> placeholderHandlers = new HashMap<>();

//     public DynaShopExpansion(ItemDataManager itemDataManager, ShopConfigManager shopConfigManager, PriceRecipe priceRecipe) {
//         this.itemDataManager = itemDataManager;
//         this.shopConfigManager = shopConfigManager;
//         this.priceRecipe = priceRecipe;

//         // Register placeholder handlers
//         placeholderHandlers.put("buy_price", this::handleBuyPrice);
//         placeholderHandlers.put("sell_price", this::handleSellPrice);
//         placeholderHandlers.put("buy_min_price", this::handleBuyMinPrice);
//         placeholderHandlers.put("buy_max_price", this::handleBuyMaxPrice);
//         placeholderHandlers.put("sell_min_price", this::handleSellMinPrice);
//         placeholderHandlers.put("sell_max_price", this::handleSellMaxPrice);
//     }

//     @Override
//     public String onRequest(OfflinePlayer player, String identifier) {
//         String[] parts = identifier.split("_", 2);
//         if (parts.length < 2) {
//             return null; // Invalid format
//         }

//         String placeholderType = parts[0] + "_" + parts[1];
//         String[] args = parts.length > 2 ? parts[2].split(":") : new String[0];

//         BiFunction<String[], OfflinePlayer, String> handler = placeholderHandlers.get(placeholderType);
//         if (handler != null) {
//             return handler.apply(args, player);
//         }

//         return null; // Placeholder not recognized
//     }

//     private String handleBuyPrice(String[] args, OfflinePlayer player) {
//         return handlePrice(args, "buyPrice", priceRecipe::calculateBuyPrice);
//     }

//     private String handleSellPrice(String[] args, OfflinePlayer player) {
//         return handlePrice(args, "sellPrice", priceRecipe::calculateSellPrice);
//     }

//     private String handleBuyMinPrice(String[] args, OfflinePlayer player) {
//         return handlePrice(args, "minBuy", priceRecipe::calculateBuyPrice);
//     }

//     private String handleBuyMaxPrice(String[] args, OfflinePlayer player) {
//         return handlePrice(args, "maxBuy", priceRecipe::calculateBuyPrice);
//     }

//     private String handleSellMinPrice(String[] args, OfflinePlayer player) {
//         return handlePrice(args, "minSell", priceRecipe::calculateSellPrice);
//     }

//     private String handleSellMaxPrice(String[] args, OfflinePlayer player) {
//         return handlePrice(args, "maxSell", priceRecipe::calculateSellPrice);
//     }

//     private String handlePrice(String[] args, String configKey, QuadFunction<String, String, ItemStack, List<String>, Double> recipeCalculator) {
//         if (args.length != 2) {
//             return null; // Invalid format
//         }

//         String shopID = args[0].replace("-", "_").replace(" ", "_");
//         String itemID = args[1].replace("-", "_").replace(" ", "_");

//         // Check if "useRecipe" is enabled
//         boolean useRecipe = shopConfigManager.getItemBooleanValue(shopID, itemID, "useRecipe").orElse(false);
//         if (useRecipe) {
//             ItemStack itemStack = ShopGuiPlusApi.getShop(shopID).getShopItem(itemID).getItem();
//             if (itemStack != null) {
//                 double recipePrice = recipeCalculator.apply(shopID, itemID, itemStack, new ArrayList<>());
//                 return String.valueOf(recipePrice);
//             }
//             return "N/A"; // No price available via recipe
//         }

//         // Fallback to database or configuration
//         Optional<Double> price = itemDataManager.getPrice(shopID, itemID, configKey);
//         if (price.isPresent()) {
//             return String.valueOf(price.get());
//         }

//         Optional<Double> configPrice = shopConfigManager.getItemValue(shopID, itemID, configKey);
//         return configPrice.map(String::valueOf).orElse("N/A");
//     }

//     @FunctionalInterface
//     private interface QuadFunction<T, U, V, W, R> {
//         R apply(T t, U u, V v, W w);
//     }
// }