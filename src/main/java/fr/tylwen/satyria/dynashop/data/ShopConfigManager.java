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
package fr.tylwen.satyria.dynashop.data;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
// import org.bukkit.inventory.ItemStack;
// import org.bukkit.inventory.meta.PotionMeta;

import de.tr7zw.changeme.nbtapi.NBT;
// import de.tr7zw.changeme.nbtapi.NBTCompound;
// import de.tr7zw.changeme.nbtapi.NBTItem;
import fr.tylwen.satyria.dynashop.DynaShopPlugin;
import fr.tylwen.satyria.dynashop.compatibility.ItemNameManager;
import fr.tylwen.satyria.dynashop.data.cache.CacheManager;
import fr.tylwen.satyria.dynashop.data.param.DynaShopType;
import fr.tylwen.satyria.dynashop.data.param.RecipeType;
// import fr.tylwen.satyria.dynashop.hook.ShopGUIPlusHook;
import fr.tylwen.satyria.dynashop.price.DynamicPrice;
// import net.brcdev.shopgui.ShopGuiPlugin;
import net.brcdev.shopgui.ShopGuiPlusApi;
// import net.brcdev.shopgui.exception.shop.ShopsNotLoadedException;
import net.brcdev.shopgui.shop.Shop;
import net.brcdev.shopgui.shop.item.ShopItem;

import java.io.File;
import java.util.Map;
// import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
// import java.util.stream.Collectors;

public class ShopConfigManager {

    private final DynaShopPlugin plugin;
    private final File shopConfigFolder;
    private final Map<ShopItem, DynamicPrice> priceMap = new HashMap<>();
    
    private CacheManager<String, YamlConfiguration> shopConfigCache;
    private CacheManager<String, ConfigurationSection> sectionCache;
    private CacheManager<String, RecipeType> recipeTypeCache;

    private CacheManager<String, YamlConfiguration> translationsCache;

    /**
     * Constructeur qui initialise le répertoire de configuration des shops.
     *
     * @param shopConfigFolder Le répertoire contenant les fichiers de configuration des shops.
     */
    // public ShopConfigManager(File shopConfigFolder) {
    public ShopConfigManager(DynaShopPlugin plugin) {
        this.plugin = plugin;
        // this.shopConfigFolder = shopConfigFolder;
        this.shopConfigFolder = new File(Bukkit.getPluginManager().getPlugin("ShopGUIPlus").getDataFolder(), "shops/");
        
        this.shopConfigCache = new CacheManager<>(plugin, "ShopConfigCache", 1, TimeUnit.DAYS, 5);
        this.sectionCache = new CacheManager<>(plugin, "SectionCache", 1, TimeUnit.DAYS, 10);
        this.recipeTypeCache = new CacheManager<>(plugin, "RecipeTypeCache", 1, TimeUnit.DAYS, 5);

        this.translationsCache = new CacheManager<>(plugin, "TranslationsCache", 1, TimeUnit.DAYS, 5);
    }

    /**
     * Récupère la configuration YAML pour un shop spécifique, avec mise en cache.
     *
     * @param shopID L'ID du shop.
     * @return La configuration YAML du shop.
     */
    public YamlConfiguration getShopConfig(String shopID) {
        return shopConfigCache.get(shopID, () -> {
            File shopFile = ShopFile.getFileByShopID(shopID);
            if (shopFile == null || !shopFile.exists()) {
                return new YamlConfiguration();
            }
            return YamlConfiguration.loadConfiguration(shopFile);
        });
    }

    /**
     * Récupère la liste de tous les identifiants de shops disponibles.
     * 
     * @return Une liste contenant les IDs de tous les shops.
     */
    public Set<String> getShops() {
        Set<String> shopIds = new HashSet<>();

        try {
            // Récupérer tous les shops via l'API ShopGUIPlus
            Set<Shop> shops = ShopGuiPlusApi.getPlugin().getShopManager().getShops();
            
            // Extraire les IDs de chaque shop
            for (Shop shop : shops) {
                shopIds.add(shop.getId());
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Erreur lors de la récupération des shops: " + e.getMessage());
            e.printStackTrace();
        }
        
        return shopIds;
    }

    /**
     * Récupère la liste des identifiants d'items pour un shop spécifique.
     * 
     * @param shopId L'ID du shop dont on veut récupérer les items
     * @return Une liste contenant les IDs des items du shop
     */
    public Set<String> getShopItems(String shopId) {
        Set<String> itemIds = new HashSet<>();

        try {
            // Récupérer le shop spécifique
            Shop shop = ShopGuiPlusApi.getPlugin().getShopManager().getShopById(shopId);
            
            if (shop != null) {
                // Récupérer tous les items du shop
                List<ShopItem> shopItems = shop.getShopItems();
                
                // Extraire les IDs de chaque item
                for (ShopItem item : shopItems) {
                    itemIds.add(item.getId());
                }
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Erreur lors de la récupération des items du shop " + shopId + ": " + e.getMessage());
            e.printStackTrace();
        }
        
        return itemIds;
    }
    
    // /**
    //  * Vérifie si la configuration en cache est à jour et la recharge si nécessaire.
    //  *
    //  * @param shopID L'ID du shop.
    //  * @return La configuration YAML à jour.
    //  */
    // public YamlConfiguration getOrUpdateShopConfig(String shopID) {
    //     File shopFile = ShopFile.getFileByShopID(shopID);
    //     if (shopFile == null || !shopFile.exists()) {
    //         return new YamlConfiguration();
    //     }
        
    //     // Vérifier si le fichier a été modifié depuis la dernière fois
    //     Long lastCachedModified = fileLastModifiedCache.get(shopFile);
    //     long currentModified = shopFile.lastModified();
        
    //     if (lastCachedModified == null || currentModified > lastCachedModified) {
    //         // Le fichier a été modifié, recharger la configuration
    //         YamlConfiguration config = YamlConfiguration.loadConfiguration(shopFile);
    //         shopConfigCache.put(shopID, config);
    //         fileLastModifiedCache.put(shopFile, currentModified);
            
    //         // Vider le cache des sections pour ce shop
    //         clearSectionCacheForShop(shopID);
            
    //         return config;
    //     }
        
    //     return shopConfigCache.getOrDefault(shopID, YamlConfiguration.loadConfiguration(shopFile));
    // }
    
    // /**
    //  * Nettoie le cache des sections pour un shop spécifique.
    //  *
    //  * @param shopID L'ID du shop.
    //  */
    // private void clearSectionCacheForShop(String shopID) {
    //     sectionCache.keySet().removeIf(key -> key.startsWith(shopID + ":"));
    // }
    
    /**
     * Récupère une section de configuration avec mise en cache.
     *
     * @param shopID L'ID du shop.
     * @param itemID L'ID de l'item.
     * @param section Le nom de la section.
     * @return La section de configuration.
     */
    public ConfigurationSection getCachedSection(String shopID, String itemID, String section) {
        String cacheKey = shopID + ":" + itemID + ":" + section;
        return sectionCache.get(cacheKey, () -> {
            YamlConfiguration config = getShopConfig(shopID);
            ConfigurationSection shopSection = config.getConfigurationSection(shopID);
            if (shopSection == null) return null;
            
            ConfigurationSection itemsSection = shopSection.getConfigurationSection("items");
            if (itemsSection == null) return null;
            
            ConfigurationSection itemSection = itemsSection.getConfigurationSection(itemID);
            if (itemSection == null) return null;
            
            // Pour les sections imbriquées comme "recipe.type"
            String[] sectionParts = section.split("\\.");
            ConfigurationSection currentSection = itemSection;
            
            for (int i = 0; i < sectionParts.length; i++) {
                String sectionPart = sectionParts[i];
                currentSection = findSectionIgnoreCase(currentSection, sectionPart);
                if (currentSection == null) return null;
                if (i == sectionParts.length - 1) return currentSection;
            }
            
            return null;
        });
    }
    
    /**
     * Vérifie si une section existe, en utilisant le cache.
     */
    public boolean hasSection(String shopID, String itemID, String section) {
        return getCachedSection(shopID, itemID, section) != null;
    }
    
    /**
     * Force le rechargement du cache pour tous les shops.
     */
    public void reloadCache() {
        shopConfigCache.clear();
        sectionCache.clear();
        recipeTypeCache.clear();
        
        // Recharger les fichiers de shop
        File shopDir = getShopDirectory();
        if (shopDir != null && shopDir.exists()) {
            ShopFile.loadShopFiles(shopDir);
            
            // Précharger les configurations des shops fréquemment utilisés
            for (String shopID : ShopFile.getAllShopIDs()) {
                getShopConfig(shopID);
            }
        }
        
        // Vider le cache des traductions
        translationsCache.clear();
        plugin.initTranslation();
    }

    /**
     * Charge, pour chaque shop de ShopGUI+, les définitions buyPrice / sellPrice
     * et remplit priceMap avec l'objet DynamicPrice correspondant.
     */
    public void initPricesFromShopConfigs() {
        File shopDir = getShopDirectory();
        if (shopDir == null || !shopDir.exists()) {
            return;
        }

        plugin.getStorageManager().loadAllPrices();

        // ShopFile shopFile = new ShopFile(shopDir);
        // shopFile.loadShopFiles();
        ShopFile.loadShopFiles(shopDir);

        // // Parcourir tous les fichiers de shop dans le répertoire
        // for (File file : shopFile.getShopFiles()) {
        // // for (File file : shopDir.listFiles((dir, name) -> name.endsWith(".yml"))) {
        //     processShopFile(file);
        // }

        // ShopFile shopFile = new ShopFile(shopDir);
        // shopFile.loadShopFiles();
        // for (File file : shopFile.getShopFiles()) {
        for (File file : ShopFile.getShopFiles()) {
            processShopFile(file);
        }
    }

    /**
     * Récupère le répertoire contenant les fichiers de configuration des shops.
     *
     * @return Le répertoire des shops, ou null si le plugin ShopGUI+ n'est pas trouvé.
     */
    public File getShopDirectory() {
        // Plugin shopGui = Bukkit.getServer().getPluginManager().getPlugin("ShopGUIPlus");
        // if (shopGui == null) {
        //     return null;
        // }
        // return new File(shopGui.getDataFolder(), "shops/");
        return this.shopConfigFolder;
    }

    /**
     * Traite chaque fichier de shop et en extrait les informations de prix dynamiques.
     *
     * @param file Le fichier de configuration du shop.
     */
    private void processShopFile(File file) {
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
    
        for (String shopKey : cfg.getKeys(false)) {
            ConfigurationSection shopSec = cfg.getConfigurationSection(shopKey);
            if (shopSec == null) {
                continue;
            }
            processShopSection(shopKey, shopSec);
        }
    }

    /**
     * Traite la section d'un shop spécifique.
     *
     * @param shopKey La clé du shop dans la configuration.
     * @param shopSec La section de configuration du shop.
     */
    private void processShopSection(String shopKey, ConfigurationSection shopSec) {
        ConfigurationSection itemsSec = shopSec.getConfigurationSection("items");
        if (itemsSec == null) {
            return;
        }
    
        Shop shop = ShopGuiPlusApi.getPlugin().getShopManager().getShopById(shopKey);
        if (shop == null) {
            return;
        }
    
        for (String key : itemsSec.getKeys(false)) {
            processItem(shop, key, itemsSec.getConfigurationSection(key));
        }
    }

    /**
     * Traite un item spécifique dans un shop.
     *
     * @param shop    Le shop contenant l'item.
     * @param key     La clé de l'item dans la configuration.
     * @param itemSec La section de configuration de l'item.
     */
    private void processItem(Shop shop, String key, ConfigurationSection itemSec) {
        if (itemSec == null) return;

        ShopItem item = shop.getShopItems().stream()
            .filter(i -> i.getId().equals(key))
            .findFirst()
            .orElse(null);
        if (item == null) return;
        
        DynaShopType typeGeneral = getTypeDynaShop(shop.getId(), item.getId());

        // if ((!itemSec.isConfigurationSection("buyDynamic") || !itemSec.isConfigurationSection("sellDynamic")) && getTypeDynaShop(shop.getId(), item.getId()) != DynaShopType.STATIC_STOCK) {
        if ((!itemSec.isConfigurationSection("buyDynamic") && !itemSec.isConfigurationSection("sellDynamic")) && typeGeneral != DynaShopType.STATIC_STOCK) {
            if (plugin.getStorageManager().itemExists(shop.getId(), item.getId())) {
                plugin.getStorageManager().deleteItem(shop.getId(), item.getId());
            }
            priceMap.remove(item);
            return;
        }

        DynamicPrice price = createDynamicPrice(itemSec);
        
        DynaShopType typeBuy = getTypeDynaShop(shop.getId(), item.getId(), "buy");
        DynaShopType typeSell = getTypeDynaShop(shop.getId(), item.getId(), "sell");
        price.setDynaShopType(typeGeneral);
        price.setBuyTypeDynaShop(typeBuy);
        price.setSellTypeDynaShop(typeSell);


        priceMap.put(item, price);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            if (!plugin.getStorageManager().itemExists(shop.getId(), item.getId())) {
                // plugin.getItemDataManager().createItem(shop.getId(), item.getId());
                // plugin.getLogger().info("Adding new item price for " + item.getId() + " in shop " + shop.getId());
                // plugin.getItemDataManager().savePrice(shop.getId(), item.getId(), price.getBuyPrice(), price.getSellPrice());
                if (price.getBuyPrice() > 0) {
                    plugin.getStorageManager().saveBuyPrice(shop.getId(), item.getId(), price.getBuyPrice());
                }
                if (price.getSellPrice() > 0) {
                    plugin.getStorageManager().saveSellPrice(shop.getId(), item.getId(), price.getSellPrice());
                }
                // if ((getTypeDynaShop(shop.getId(), item.getId()) == DynaShopType.STATIC_STOCK || getTypeDynaShop(shop.getId(), item.getId()) == DynaShopType.STOCK) && price.getStock() > 0) {
                if ((typeGeneral == DynaShopType.STATIC_STOCK || typeGeneral == DynaShopType.STOCK) && price.getStock() > 0) {
                    plugin.getStorageManager().saveStock(shop.getId(), item.getId(), price.getStock());
                }
            }
        });
    }

    /**
     * Crée un objet DynamicPrice à partir de la section de configuration d'un item.
     *
     * @param itemSec La section de configuration de l'item.
     * @return Un objet DynamicPrice contenant les prix dynamiques.
     */
    private DynamicPrice createDynamicPrice(ConfigurationSection itemSec) {
        double baseBuy = itemSec.getDouble("buyPrice", -1.0);
        double minBuy = baseBuy, maxBuy = baseBuy, growthBuy = 1.0, decayBuy = 1.0;
    
        if (itemSec.isConfigurationSection("buyDynamic")) {
            ConfigurationSection bp = itemSec.getConfigurationSection("buyDynamic");
            minBuy = bp.getDouble("min", 0.0);
            maxBuy = bp.getDouble("max", Double.MAX_VALUE);
            growthBuy = bp.getDouble("growth", 1.05);
            decayBuy = bp.getDouble("decay", 0.98);
        }
    
        double baseSell = itemSec.getDouble("sellPrice", -1.0);
        double minSell = baseSell, maxSell = baseSell, growthSell = 1.0, decaySell = 1.0;
    
        if (itemSec.isConfigurationSection("sellDynamic")) {
            ConfigurationSection sp = itemSec.getConfigurationSection("sellDynamic");
            minSell = sp.getDouble("min", 0.0);
            maxSell = sp.getDouble("max", Double.MAX_VALUE);
            growthSell = sp.getDouble("growth", 1.02);
            decaySell = sp.getDouble("decay", 0.95);
        }

        int stock = 0, minStock = 0, maxStock = Integer.MAX_VALUE;
        double buyModifier = 1.0, sellModifier = 1.0;

        if (itemSec.isConfigurationSection("stock")) {
            ConfigurationSection stockSec = itemSec.getConfigurationSection("stock");
            stock = stockSec.getInt("base", 0);
            minStock = stockSec.getInt("min", 0);
            maxStock = stockSec.getInt("max", Integer.MAX_VALUE);
            buyModifier = stockSec.getDouble("buyModifier", 0.5);
            sellModifier = stockSec.getDouble("sellModifier", 2.0);
        }

        // plugin.getLogger().info("Creating DynamicPrice for item: " + itemSec.getCurrentPath() +
        //     " | Buy: " + baseBuy + " (min: " + minBuy + ", max: " + maxBuy + ", growth: " + growthBuy + ", decay: " + decayBuy +
        //     ") | Sell: " + baseSell + " (min: " + minSell + ", max: " + maxSell + ", growth: " + growthSell + ", decay: " + decaySell +
        //     ") | Stock: " + stock + " (min: " + minStock + ", max: " + maxStock +
        //     ") | BuyModifier: " + buyModifier + ", SellModifier: " + sellModifier);
    
        // return new DynamicPrice(baseBuy, baseSell, minBuy, maxBuy, minSell, maxSell, growthBuy, decayBuy, growthSell, decaySell);
        return new DynamicPrice(baseBuy, baseSell, minBuy, maxBuy, minSell, maxSell, growthBuy, decayBuy, growthSell, decaySell, stock, minStock, maxStock, buyModifier, sellModifier);
    }

    /**
     * Vérifie si un item a une section dynamique dans un shop.
     *
     * @param shopID L'ID du shop.
     * @param itemID L'ID de l'item.
     * @return true si l'item a une section dynamique, false sinon.
     */
    public boolean hasDynamicSection(String shopID, String itemID) {
        // return hasSection(shopID, itemID, "buyDynamic") || hasSection(shopID, itemID, "sellDynamic");
        return hasBuyDynamicSection(shopID, itemID) || hasSellDynamicSection(shopID, itemID);
    }
    public boolean hasBuyDynamicSection(String shopID, String itemID) {
        return hasSection(shopID, itemID, "buyDynamic");
    }
    public boolean hasSellDynamicSection(String shopID, String itemID) {
        return hasSection(shopID, itemID, "sellDynamic");
    }

    /**
     * Vérifie si un item a une section de stock dans un shop.
     *
     * @param shopID L'ID du shop.
     * @param itemID L'ID de l'item.
     * @return true si l'item a une section de stock, false sinon.
     */
    public boolean hasStockSection(String shopID, String itemID) {
        return hasSection(shopID, itemID, "stock");
    }

    /**
     * Vérifie si un item a une section de recette dans un shop.
     *
     * @param shopID L'ID du shop.
     * @param itemID L'ID de l'item.
     * @return true si l'item a une section de recette, false sinon.
     */
    public boolean hasRecipeSection(String shopID, String itemID) {
        return hasSection(shopID, itemID, "recipe");
    }

    /**
     * Vérifie si un item a un type de recette dans un shop.
     *
     * @param shopID L'ID du shop.
     * @param itemID L'ID de l'item.
     * @return true si l'item a un type de recette, false sinon.
     */
    public boolean hasRecipeType(String shopID, String itemID) {
        return hasSection(shopID, itemID, "recipe.type");
    }

    // public boolean hasRecipePattern(String shopID, String itemID) {
    //     return hasSection(shopID, itemID, "recipe.pattern");
    // }
    public boolean hasRecipePattern(String shopID, String itemID) {
        YamlConfiguration config = getShopConfig(shopID);
        if (config == null) return false;
        
        // Chemin complet vers la clé pattern
        String path = shopID + ".items." + itemID + ".recipe.pattern";
        
        // Vérifier si la clé existe et contient une liste
        return config.isList(path) && !config.getStringList(path).isEmpty();
    }
    public boolean hasRecipeIngredients(String shopID, String itemID) {
        return hasSection(shopID, itemID, "recipe.ingredients");
    }

    public boolean hasDynaShopSection(String shopID, String itemID) {
        return hasSection(shopID, itemID, "typeDynaShop");
    }

    public DynaShopType resolveTypeDynaShop(String shopID, String itemID, boolean isBuy) {
        // Si tu veux garder la compatibilité, tu peux fallback sur le type général
        return getTypeDynaShop(shopID, itemID, isBuy ? "buy" : "sell");
    }

    public DynaShopType getTypeDynaShop(String shopID, String itemID, String priceType) {
        // priceType: "buy" ou "sell"
        String key = priceType.equalsIgnoreCase("buy") ? "dynaShop.buyType" : "dynaShop.sellType";
        Optional<String> typeOpt = getItemValue(shopID, itemID, key, String.class);
        if (typeOpt.isPresent()) {
            try {
                return DynaShopType.valueOf(typeOpt.get().toUpperCase());
            } catch (IllegalArgumentException e) {
                return DynaShopType.NONE;
            }
        }
        // fallback sur typeDynaShop général
        return getTypeDynaShop(shopID, itemID);
    }

    /**
     * Récupère le type de DynaShop d'un item dans un shop.
     *
     * @param shopID L'ID du shop.
     * @param itemID L'ID de l'item.
     * @return Le type de DynaShop (ex. "buy", "sell", "none").
     */
    public DynaShopType getTypeDynaShop(String shopID, String itemID) {
        String type = getItemValue(shopID, itemID, "typeDynaShop", String.class).orElse("NONE");
        
        // // Si c'est un LINK, récupérer le type de l'item lié
        // if (type.equalsIgnoreCase("LINK")) {
        //     String linkedItemRef = getItemValue(shopID, itemID, "link", String.class).orElse(null);
            
        //     if (linkedItemRef != null && linkedItemRef.contains(":")) {
        //         String[] parts = linkedItemRef.split(":");
        //         if (parts.length == 2) {
        //             String linkedShopID = parts[0];
        //             String linkedItemID = parts[1];
                    
        //             // Éviter les boucles infinies
        //             if (!linkedShopID.equals(shopID) || !linkedItemID.equals(itemID)) {
        //                 // Récupérer le type de l'item lié (récursif)
        //                 return getTypeDynaShop(linkedShopID, linkedItemID);
        //             }
        //         }
        //     }
        // }

        try {
            return DynaShopType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            return DynaShopType.NONE; // Retourner NONE si le type est invalide
        }
    }

    /**
     * Récupère le type réel de DynaShop d'un item en résolvant les références pour les items LINK.
     * Cette méthode suit les références en cascade jusqu'à trouver un item qui n'est pas de type LINK.
     *
     * @param shopId L'ID du shop.
     * @param itemId L'ID de l'item.
     * @param priceType Le type de prix ("buy" ou "sell").
     * @return Le nom du type réel de DynaShop.
     */
    public DynaShopType getRealTypeDynaShop(String shopId, String itemId, String priceType) {
        // Ensemble pour détecter les cycles
        Set<String> visited = new HashSet<>();
        String key = shopId + ":" + itemId;
        visited.add(key);
        
        // Type initial
        DynaShopType type = getTypeDynaShop(shopId, itemId, priceType);
        
        // Suivre les références tant que c'est un type LINK
        while (type == DynaShopType.LINK) {
            // Récupérer la référence de l'item lié
            String linkedItemRef = getItemValue(shopId, itemId, "link", String.class).orElse(null);
            
            if (linkedItemRef == null || !linkedItemRef.contains(":")) {
                // Lien invalide, retourner le type actuel
                return type;
            }
            
            // Décomposer la référence
            String[] parts = linkedItemRef.split(":");
            if (parts.length != 2) {
                return type;
            }

            String linkedShopId = parts[0];
            String linkedItemId = parts[1];
            
            // Vérifier les cycles
            String linkedKey = linkedShopId + ":" + linkedItemId;
            if (visited.contains(linkedKey)) {
                // Cycle détecté, retourner le type actuel
                return type;
            }
            
            // Ajouter à l'ensemble des items visités
            visited.add(linkedKey);
            
            // Mettre à jour shopId, itemId et type pour la prochaine itération
            shopId = linkedShopId;
            itemId = linkedItemId;
            type = getTypeDynaShop(shopId, itemId, priceType);
        }
        
        // Retourner le type réel trouvé
        return type;
    }

    public Map<String, DynaShopType> getAllTypeDynaShop(String shopID, String itemID) {
        Map<String, DynaShopType> types = new HashMap<>();
        types.put("buy", getTypeDynaShop(shopID, itemID, "buy"));
        types.put("sell", getTypeDynaShop(shopID, itemID, "sell"));
        return types;
    }

    /**
     * Récupère le type de recette d'un item dans un shop.
     *
     * @param shopID L'ID du shop.
     * @param itemID L'ID de l'item.
     * @return Le type de recette (ex. "craft", "smelt", "none").
     */
    public RecipeType getTypeRecipe(String shopID, String itemID) {
        String key = shopID + ":" + itemID;

        return recipeTypeCache.get(key, () -> {
            String type = getItemValue(shopID, itemID, "recipe.type", String.class).orElse("NONE");
            try {
                return RecipeType.valueOf(type.toUpperCase());
            } catch (IllegalArgumentException e) {
                return RecipeType.NONE; // Retourner NONE si le type est invalide
            }
        });

        // String type = getItemValue(shopID, itemID, "recipe.type", String.class).orElse("NONE");
        // try {
        //     return RecipeType.valueOf(type.toUpperCase());
        // } catch (IllegalArgumentException e) {
        //     return RecipeType.NONE; // Retourner NONE si le type est invalide
        // }
    }

    // private <T> Optional<T> getItemConfigValue(String shopID, String itemID, String key, Class<T> type) {
    public <T> Optional<T> getItemValue(String shopID, String itemID, String key, Class<T> type) {
        // // Récupérer le fichier correspondant au shopID via ShopFile
        // File shopFile = ShopFile.getFileByShopID(shopID);
        // if (shopFile == null || !shopFile.exists()) {
        //     return Optional.empty();
        // }
    
        // Charger la configuration YAML du fichier
        // YamlConfiguration config = YamlConfiguration.loadConfiguration(shopFile);
        YamlConfiguration config = getShopConfig(shopID);
    
        ConfigurationSection shopSection = config.getConfigurationSection(shopID);
        if (shopSection == null) {
            // Essayer de trouver la section de shop de manière insensible à la casse
            shopSection = findSectionIgnoreCase(config, shopID);
            if (shopSection == null) {
                return Optional.empty();
            }
        }
    
        // Accéder à la section "items" du shop
        ConfigurationSection itemsSection = shopSection.getConfigurationSection("items");
        if (itemsSection == null) {
            // Essayer de trouver la section items de manière insensible à la casse
            itemsSection = findSectionIgnoreCase(shopSection, "items");
            if (itemsSection == null) {
                return Optional.empty();
            }
        }
    
        // Accéder à la section spécifique de l'item
        ConfigurationSection itemSection = itemsSection.getConfigurationSection(itemID);
        if (itemSection == null) {
            // Essayer de trouver la section d'item de manière insensible à la casse
            itemSection = findSectionIgnoreCase(itemsSection, itemID);
            if (itemSection == null) {
                return Optional.empty();
            }
        }
        
        // Pour les clés complexes comme "recipe.type", diviser la clé et naviguer dans les sections
        String[] keyParts = key.split("\\.");
        ConfigurationSection currentSection = itemSection;
        
        // Naviguer à travers les sections pour les clés imbriquées
        for (int i = 0; i < keyParts.length - 1; i++) {
            String keyPart = keyParts[i];
            currentSection = currentSection.getConfigurationSection(keyPart);
            
            if (currentSection == null) {
                // Essayer de trouver la section de manière insensible à la casse
                currentSection = findSectionIgnoreCase(currentSection, keyPart);
                if (currentSection == null) {
                    return Optional.empty();
                }
            }
        }
        
        // Obtenir la dernière partie de la clé
        String finalKey = keyParts[keyParts.length - 1];
        String actualKey = findKeyIgnoreCase(currentSection, finalKey);
        
        if (actualKey == null) {
            return Optional.empty();
        }
    
        // Récupérer la valeur associée à la clé en fonction du type
        if (type == Double.class) {
            double value = currentSection.getDouble(actualKey, -1.0);
            return value >= 0 ? Optional.of(type.cast(value)) : Optional.empty();
        } else if (type == Integer.class) {
            int value = currentSection.getInt(actualKey, -1);
            return value >= 0 ? Optional.of(type.cast(value)) : Optional.empty();
        } else if (type == Boolean.class) {
            boolean value = currentSection.getBoolean(actualKey, false);
            return Optional.of(type.cast(value));
        } else if (type == String.class) {
            String value = currentSection.getString(actualKey, null);
            return value != null ? Optional.of(type.cast(value)) : Optional.empty();
        }
    
        return Optional.empty();
    }

    /**
     * Récupère la catégorie d'un item dans un shop.
     * Cette information est utilisée pour appliquer des taux d'inflation spécifiques.
     *
     * @param shopId L'ID du shop
     * @param itemId L'ID de l'item
     * @return La catégorie de l'item, ou null si non définie
     */
    public String getItemCategory(String shopId, String itemId) {
        // D'abord, essayer de récupérer la catégorie depuis la configuration de l'item
        Optional<String> categoryFromItem = getItemValue(shopId, itemId, "category", String.class);
        if (categoryFromItem.isPresent()) {
            return categoryFromItem.get();
        }
        
        // Ensuite, essayer de récupérer la catégorie depuis la configuration du shop
        YamlConfiguration config = getShopConfig(shopId);
        ConfigurationSection shopSection = config.getConfigurationSection(shopId);
        if (shopSection != null) {
            String shopCategory = shopSection.getString("category");
            if (shopCategory != null && !shopCategory.isEmpty()) {
                return shopCategory;
            }
        }
        
        // Si aucune catégorie n'est définie, utiliser l'ID du shop comme catégorie par défaut
        return shopId;
    }

    /**
     * Trouve une section dans une ConfigurationSection en ignorant la casse
     */
    private ConfigurationSection findSectionIgnoreCase(ConfigurationSection parent, String sectionName) {
        if (parent == null) return null;
        
        for (String key : parent.getKeys(false)) {
            if (key.equalsIgnoreCase(sectionName)) {
                return parent.getConfigurationSection(key);
            }
        }
        return null;
    }

    /**
     * Trouve une clé dans une ConfigurationSection en ignorant la casse
     */
    private String findKeyIgnoreCase(ConfigurationSection section, String keyName) {
        if (section == null) return null;
        
        for (String key : section.getKeys(false)) {
            if (key.equalsIgnoreCase(keyName)) {
                return key;
            }
        }
        return null;
    }

    /**
     * Récupère toutes les valeurs d'un item dans un shop, y compris les sections dynamiques.
     *
     * @param shopID L'ID du shop.
     * @param itemID L'ID de l'item.
     * @return Un objet ItemPriceData contenant toutes les valeurs de l'item.
     */
    public ItemPriceData getItemAllValues(String shopID, String itemID) {
        ItemPriceData itemPriceData = new ItemPriceData();
        
        // Récupérer la section de l'item directement (une seule navigation)
        String cacheKey = shopID + ":" + itemID;
        ConfigurationSection itemSection = sectionCache.get(cacheKey, () -> {
            YamlConfiguration config = getShopConfig(shopID);
            
            // Naviguer jusqu'à la section shop
            ConfigurationSection shopSection = config.getConfigurationSection(shopID);
            if (shopSection == null) {
                shopSection = findSectionIgnoreCase(config, shopID);
                if (shopSection == null) return null;
            }
            
            // Naviguer jusqu'à la section items
            ConfigurationSection itemsSection = shopSection.getConfigurationSection("items");
            if (itemsSection == null) {
                itemsSection = findSectionIgnoreCase(shopSection, "items");
                if (itemsSection == null) return null;
            }
            
            // Naviguer jusqu'à l'item spécifique
            ConfigurationSection section = itemsSection.getConfigurationSection(itemID);
            if (section == null) {
                section = findSectionIgnoreCase(itemsSection, itemID);
            }
            
            return section;
        });
        
        if (itemSection == null) {
            return itemPriceData; // Retourner des données vides
        }
        
        // Extraire les valeurs directes
        itemPriceData.buyPrice = getOptionalDouble(itemSection, "buyPrice");
        itemPriceData.sellPrice = getOptionalDouble(itemSection, "sellPrice");
        
        // Section buyDynamic
        ConfigurationSection buyDynamic = findSectionIgnoreCase(itemSection, "buyDynamic");
        if (buyDynamic != null) {
            itemPriceData.defaultBuy = getOptionalBoolean(buyDynamic, "default");
            itemPriceData.minBuy = getOptionalDouble(buyDynamic, "min");
            itemPriceData.maxBuy = getOptionalDouble(buyDynamic, "max");
            itemPriceData.growthBuy = getOptionalDouble(buyDynamic, "growth");
            itemPriceData.decayBuy = getOptionalDouble(buyDynamic, "decay");
            itemPriceData.minBuyLink = getOptionalString(buyDynamic, "minLink");
            itemPriceData.maxBuyLink = getOptionalString(buyDynamic, "maxLink");
        }
        
        // Section sellDynamic
        ConfigurationSection sellDynamic = findSectionIgnoreCase(itemSection, "sellDynamic");
        if (sellDynamic != null) {
            itemPriceData.defaultSell = getOptionalBoolean(sellDynamic, "default");
            itemPriceData.minSell = getOptionalDouble(sellDynamic, "min");
            itemPriceData.maxSell = getOptionalDouble(sellDynamic, "max");
            itemPriceData.growthSell = getOptionalDouble(sellDynamic, "growth");
            itemPriceData.decaySell = getOptionalDouble(sellDynamic, "decay");
            itemPriceData.minSellLink = getOptionalString(sellDynamic, "minLink");
            itemPriceData.maxSellLink = getOptionalString(sellDynamic, "maxLink");
        }
        
        // Section stock
        ConfigurationSection stock = findSectionIgnoreCase(itemSection, "stock");
        if (stock != null) {
            itemPriceData.stock = getOptionalInt(stock, "base");
            itemPriceData.minStock = getOptionalInt(stock, "min");
            itemPriceData.maxStock = getOptionalInt(stock, "max");
            itemPriceData.stockBuyModifier = getOptionalDouble(stock, "buyModifier");
            itemPriceData.stockSellModifier = getOptionalDouble(stock, "sellModifier");
        }

        // plugin.info("getItemAllValues: " + shopID + ":" + itemID + " - buyPrice: " + itemPriceData.buyPrice +
        //     ", sellPrice: " + itemPriceData.sellPrice +
        //     ", minBuy: " + itemPriceData.minBuy +
        //     ", maxBuy: " + itemPriceData.maxBuy +
        //     ", growthBuy: " + itemPriceData.growthBuy +
        //     ", decayBuy: " + itemPriceData.decayBuy +
        //     ", minSell: " + itemPriceData.minSell +
        //     ", maxSell: " + itemPriceData.maxSell +
        //     ", growthSell: " + itemPriceData.growthSell +
        //     ", decaySell: " + itemPriceData.decaySell +
        //     ", stock: " + itemPriceData.stock +
        //     ", minStock: " + itemPriceData.minStock +
        //     ", maxStock: " + itemPriceData.maxStock +
        //     ", stockBuyModifier: " + itemPriceData.stockBuyModifier +
        //     ", stockSellModifier: " + itemPriceData.stockSellModifier);
        
        return itemPriceData;
    }

    // Méthodes auxiliaires pour l'extraction des valeurs
    private Optional<Double> getOptionalDouble(ConfigurationSection section, String key) {
        if (section == null) return Optional.empty();
        
        String actualKey = findKeyIgnoreCase(section, key);
        if (actualKey == null) return Optional.empty();
        
        // Vérifier si la clé existe réellement dans la section
        if (!section.contains(actualKey)) {
            return Optional.empty();
        }
        
        double value = section.getDouble(actualKey);
        return Optional.of(value);
    }

    private Optional<Integer> getOptionalInt(ConfigurationSection section, String key) {
        if (section == null) return Optional.empty();
        
        String actualKey = findKeyIgnoreCase(section, key);
        if (actualKey == null) return Optional.empty();

        // Vérifier si la clé existe réellement dans la section
        if (!section.contains(actualKey)) {
            return Optional.empty();
        }

        int value = section.getInt(actualKey);
        return Optional.of(value);
    }

    private Optional<String> getOptionalString(ConfigurationSection section, String key) {
        if (section == null) return Optional.empty();

        String actualKey = findKeyIgnoreCase(section, key);
        if (actualKey == null) return Optional.empty();

        if (!section.contains(actualKey)) return Optional.empty();
        
        String value = section.getString(actualKey);
        return value != null && !value.isEmpty() ? Optional.of(value) : Optional.empty();
    }

    private Optional<Boolean> getOptionalBoolean(ConfigurationSection section, String key) {
        if (section == null) return Optional.empty();
        
        String actualKey = findKeyIgnoreCase(section, key);
        if (actualKey == null) return Optional.empty();
        
        // Vérifier si la clé existe réellement dans la section
        if (!section.contains(actualKey)) {
            return Optional.empty();
        }
        
        boolean value = section.getBoolean(actualKey);
        return Optional.of(value);
    }

    // public ItemStack getItemStack(String shopID, String itemID) {
    //     Optional<ItemStack> itemStack = plugin.getItemDataManager().getItemStack(shopID, itemID);
    //     if (itemStack.isPresent()) {
    //         return itemStack.get();
    //     } else {
    //         return null;
    //     }
    // }

    /**
     * Récupère une section de configuration spécifique pour un item dans un shop.
     *
     * @param shopID L'ID du shop.
     * @param itemID L'ID de l'item.
     * @param section Le nom de la section à récupérer.
     * @return La section de configuration, ou null si elle n'existe pas.
     */
    public ConfigurationSection getSection(String shopID, String itemID, String section) {
        YamlConfiguration config = getShopConfig(shopID);
        ConfigurationSection shopSection = config.getConfigurationSection(shopID);
        ConfigurationSection itemsSection = shopSection.getConfigurationSection("items");
        if (itemsSection == null) {
            return null;
        }
    
        ConfigurationSection itemSection = itemsSection.getConfigurationSection(itemID);
        if (itemSection == null) {
            return null;
        }
    
        return itemSection.getConfigurationSection(section);
    }

    // public String getItemName(String shopID, String itemID) {
    //     YamlConfiguration config = getOrUpdateShopConfig(shopID);
    //     ConfigurationSection shopSection = config.getConfigurationSection(shopID);
    //     if (shopSection == null) return null;
        
    //     ConfigurationSection itemsSection = shopSection.getConfigurationSection("items");
    //     if (itemsSection == null) return null;
        
    //     ConfigurationSection itemSection = itemsSection.getConfigurationSection(itemID);
    //     if (itemSection == null) return null;
        
    //     return itemSection.getString("name", itemID); // Retourne l'ID si le nom n'est pas défini
    // }

    /**
     * Vérifie si un item existe dans un shop donné.
     * 
     * @param shopId L'ID du shop
     * @param itemId L'ID de l'item
     * @return true si l'item existe dans le shop, false sinon
     */
    public boolean shopItemExists(String shopId, String itemId) {
        // Obtenir le shop de ShopGUIPlus
        Shop shop = ShopGuiPlusApi.getPlugin().getShopManager().getShopById(shopId);
        if (shop == null) {
            return false;
        }
        
        // Vérifier si l'item existe dans le shop
        ShopItem shopItem = shop.getShopItem(itemId);
        return shopItem != null;
    }

    /**
     * Charge un fichier de traduction dans le cache.
     * @param locale Le code de langue (ex: "fr", "en", "de")
     * @param fileName Le nom du fichier (ex: "translations_fr.yml")
     */
    public void loadTranslationFile(String locale, String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists()) {
            DynaShopPlugin.getInstance().info("Translation file for locale '" + locale + "' not found, creating default.");
            plugin.saveResource(fileName, false); // Copie depuis le jar si dispo
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        translationsCache.put(locale, config);
        DynaShopPlugin.getInstance().info("Translation file for locale '" + locale + "' loaded successfully.");
    }

    /**
     * Récupère le nom traduit d'un matériau selon la locale du joueur.
     * @param materialName Le nom du matériau (ex: "GOLD_BLOCK")
     * @param locale La locale (ex: "fr", "en")
     * @return Le nom traduit ou null si non trouvé
     */
    public String getTranslatedMaterialName(String materialName, String locale) {
        YamlConfiguration translations = translationsCache.get(locale, () -> null);
        if (translations == null) {
            DynaShopPlugin.getInstance().info("Translation file for locale '" + locale + "' not loaded.");
            return null;
        }
        return translations.getString(materialName, null);
    }

    /**
     * Récupère le nom d'affichage d'un item.
     * Utilisé pour les cartes de marché.
     * 
     * @param shopId L'ID du shop
     * @param itemId L'ID de l'item
     * @return Le nom de l'item, ou l'itemId si non disponible
     */
    public String getItemName(Player player, String shopId, String itemId) {
        Shop shop = ShopGuiPlusApi.getPlugin().getShopManager().getShopById(shopId);
        if (shop == null) {
            return itemId;
        }
        
        ShopItem shopItem = shop.getShopItem(itemId);
        if (shopItem == null) {
            return itemId;
        }
        
        // 1. Essayer d'obtenir le nom personnalisé
        if (shopItem.getItem().hasItemMeta() && shopItem.getItem().getItemMeta() != null) {
            String displayName = shopItem.getItem().getItemMeta().getDisplayName();
            if (displayName != null && !displayName.isEmpty()) {
                return ChatColor.stripColor(displayName);
            }
        }

        // 2. Utiliser NBTAPI pour accéder aux NBT data
        try {
            String nbtName = NBT.getComponents(shopItem.getItem(), nbt -> {
                // Vérifier si le nom est déjà formaté
                if (nbt.hasTag("minecraft:item_name")) {
                    String jsonName = nbt.getString("minecraft:item_name");
                    // plugin.getLogger().info("Nom JSON de l'item: " + jsonName);
                    // Le nom est stocké au format JSON: {"color":"white","italic":false,"text":"Bloc de topaze"}
                    if (jsonName != null && !jsonName.isEmpty()) {
                        try {
                            // Extraire la partie "text" du JSON
                            if (jsonName.contains("\"text\":")) {
                                return jsonName.replaceAll(".*\"text\":\"([^\"]+)\".*", "$1");
                            }
                        } catch (Exception e) {
                            plugin.getLogger().warning("Erreur lors du parsing du nom JSON: " + e.getMessage());
                        }
                    }
                }
                return null; // Retourne null si aucun nom trouvé
            });
            if (nbtName != null && !nbtName.isEmpty()) {
                return nbtName; // Retourne le nom extrait des NBT data
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Erreur lors de l'accès aux NBT data avec NBTAPI: " + e.getMessage());
        }
        
        // 2. Traduction via fichier si locale connue
        // DynaShopPlugin.getInstance().info(player.getLocale());
        if (player != null && player.getLocale() != null) {
            String locale = player.getLocale().split("_")[0]; // "fr", "en", etc.
            String translated = getTranslatedMaterialName(shopItem.getItem().getType().name(), locale);
            // DynaShopPlugin.getInstance().info(shopItem.getItem().getType().name() + " " + translated);
            if (translated != null && !translated.isEmpty()) {
                // if (shopItem.getItem().getItemMeta() != null && shopItem.getItem().getItemMeta().hasEnchants()) {
                //     // Si l'item a des enchantements, on ajoute le nom de l'enchantement
                //     StringBuilder nameWithEnchantments = new StringBuilder(translated);
                //     shopItem.getItem().getItemMeta().getEnchants().forEach((enchantment, level) -> {
                //         nameWithEnchantments.append(" ").append(enchantment.getKey().getKey()).append(" ").append(level);
                //     });
                //     translated = nameWithEnchantments.toString();
                // }
                // if (shopItem.getItem().getType() == Material.POTION || shopItem.getItem().getType() == Material.SPLASH_POTION || shopItem.getItem().getType() == Material.LINGERING_POTION) {
                //     // Pour les potions, on ajoute le type de potion
                //     PotionMeta potionMeta = (PotionMeta) shopItem.getItem().getItemMeta();
                //     if (potionMeta != null && potionMeta.hasCustomEffects()) {
                //         StringBuilder effects = new StringBuilder();
                //         potionMeta.getCustomEffects().forEach(effect -> {
                //             effects.append(effect.getType().getName()).append(" ");
                //         });
                //         translated += " (" + effects.toString().trim() + ")";
                //     }
                // }
                return translated;
            }
        }
        
        // 3. Système multi-version (anglais vanilla)
        if (player != null) {
            String localizedName = ItemNameManager.getLocalizedName(shopItem.getItem(), player);
            if (localizedName != null && !localizedName.isEmpty()) {
                return localizedName;
            }
        }

        // 4. Fallback: formatage du nom de matériau
        return formatMaterialName(shopItem.getItem().getType().name());
    }

    /**
     * Récupère le nom d'affichage d'un item avec une locale spécifique.
     * Version web-friendly pour l'interface web.
     * 
     * @param shopId L'ID du shop
     * @param itemId L'ID de l'item
     * @param locale Le code de langue (ex: "fr", "en")
     * @return Le nom de l'item, ou l'itemId si non disponible
     */
    public String getItemNameWithLocale(String shopId, String itemId, String locale) {
        Shop shop = ShopGuiPlusApi.getPlugin().getShopManager().getShopById(shopId);
        if (shop == null) {
            return itemId;
        }
        
        ShopItem shopItem = shop.getShopItem(itemId);
        if (shopItem == null || shopItem.getItem() == null) {
            return itemId;
        }
        
        // 1. Essayer d'obtenir le nom personnalisé
        if (shopItem.getItem().hasItemMeta() && shopItem.getItem().getItemMeta() != null) {
            String displayName = shopItem.getItem().getItemMeta().getDisplayName();
            if (displayName != null && !displayName.isEmpty()) {
                return ChatColor.stripColor(displayName);
            }
        }

        // 2. Utiliser NBTAPI pour accéder aux NBT data
        try {
            String nbtName = NBT.getComponents(shopItem.getItem(), nbt -> {
                // Vérifier si le nom est déjà formaté
                if (nbt.hasTag("minecraft:item_name")) {
                    String jsonName = nbt.getString("minecraft:item_name");
                    // plugin.getLogger().info("Nom JSON de l'item: " + jsonName);
                    // Le nom est stocké au format JSON: {"color":"white","italic":false,"text":"Bloc de topaze"}
                    if (jsonName != null && !jsonName.isEmpty()) {
                        try {
                            // Extraire la partie "text" du JSON
                            if (jsonName.contains("\"text\":")) {
                                return jsonName.replaceAll(".*\"text\":\"([^\"]+)\".*", "$1");
                            }
                        } catch (Exception e) {
                            plugin.getLogger().warning("Erreur lors du parsing du nom JSON: " + e.getMessage());
                        }
                    }
                }
                return null; // Retourne null si aucun nom trouvé
            });
            if (nbtName != null && !nbtName.isEmpty()) {
                if (shopItem.getItem().getItemMeta() != null && shopItem.getItem().getItemMeta().hasEnchants()) {
                    // Si l'item a des enchantements, on ajoute le nom de l'enchantement
                    StringBuilder nameWithEnchantments = new StringBuilder(nbtName);
                    shopItem.getItem().getItemMeta().getEnchants().forEach((enchantment, level) -> {
                        nameWithEnchantments.append(" ").append(enchantment.getKey().getKey()).append(" ").append(level);
                    });
                    nbtName = nameWithEnchantments.toString();
                }
                if (shopItem.getItem().getType() == Material.POTION || shopItem.getItem().getType() == Material.SPLASH_POTION || shopItem.getItem().getType() == Material.LINGERING_POTION) {
                    // // Pour les potions, on ajoute le type de potion
                    // PotionMeta potionMeta = (PotionMeta) shopItem.getItem().getItemMeta();
                    // if (potionMeta != null && potionMeta.hasCustomEffects()) {
                    //     StringBuilder effects = new StringBuilder();
                    //     potionMeta.getCustomEffects().forEach(effect -> {
                    //         effects.append(effect.getType().getName()).append(" ");
                    //     });
                    //     nbtName += " (" + effects.toString().trim() + ")";
                    // }
                }
                return nbtName; // Retourne le nom extrait des NBT data
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Erreur lors de l'accès aux NBT data avec NBTAPI: " + e.getMessage());
        }
        
        // 2. Traduction via fichier si locale connue
        String translated = getTranslatedMaterialName(shopItem.getItem().getType().name(), locale);
        // DynaShopPlugin.getInstance().info(shopItem.getItem().getType().name() + " " + translated);
        if (translated != null && !translated.isEmpty()) {
            if (shopItem.getItem().getItemMeta() != null && shopItem.getItem().getItemMeta().hasEnchants()) {
                // Si l'item a des enchantements, on ajoute le nom de l'enchantement
                StringBuilder nameWithEnchantments = new StringBuilder(translated);
                shopItem.getItem().getItemMeta().getEnchants().forEach((enchantment, level) -> {
                    nameWithEnchantments.append(" ").append(enchantment.getKey().getKey()).append(" ").append(level);
                });
                translated = nameWithEnchantments.toString();
            }
            if (shopItem.getItem().getType() == Material.POTION || shopItem.getItem().getType() == Material.SPLASH_POTION || shopItem.getItem().getType() == Material.LINGERING_POTION) {
                // // Pour les potions, on ajoute le type de potion
                // PotionMeta potionMeta = (PotionMeta) shopItem.getItem().getItemMeta();
                // if (potionMeta != null && potionMeta.hasCustomEffects()) {
                //     StringBuilder effects = new StringBuilder();
                //     potionMeta.getCustomEffects().forEach(effect -> {
                //         effects.append(effect.getType().getName()).append(" ");
                //     });
                //     translated += " (" + effects.toString().trim() + ")";
                // }
                if (hasSection(shopId, itemId, "item.potion")) {
                    // Si une section spécifique pour les potions existe, on l'utilise
                    ConfigurationSection potionSection = getSection(shopId, itemId, "item.potion");
                    if (potionSection != null) {
                        String potionType = potionSection.getString("type", "");
                        int level = potionSection.getInt("level", 1);
                        boolean extended = potionSection.getBoolean("extended", false);
                        
                        // Construire un nom descriptif
                        StringBuilder nameBuilder = new StringBuilder();
                        nameBuilder.append("Potion de ");
                        nameBuilder.append(" ").append(translatePotionType(potionType, locale));
                        if (level > 1) {
                            nameBuilder.append(" ").append(toRomanNumeral(level));
                        }
                        if (shopItem.getItem().getType() == Material.SPLASH_POTION) {
                            nameBuilder.append(" ").append("jetable");
                        } else if (shopItem.getItem().getType() == Material.LINGERING_POTION) {
                            nameBuilder.append(" ").append("persistante");
                        }
                        if (extended) {
                            // nameBuilder.append(" (étendue)");
                            nameBuilder.append(" ").append("(Durée prolongée)");
                        }
                        translated = nameBuilder.toString();
                    }
                }
            }
            return translated;
        }
        
        // // 3. Système multi-version (anglais vanilla)
        // if (player != null) {
        //     String localizedName = ItemNameManager.getLocalizedName(shopItem.getItem(), player);
        //     if (localizedName != null && !localizedName.isEmpty()) {
        //         return localizedName;
        //     }
        // }

        // 4. Fallback: formatage du nom de matériau
        return formatMaterialName(shopItem.getItem().getType().name());
    }

    /**
     * Traduit le type de potion selon la locale
     */
    private String translatePotionType(String type, String locale) {
        // Récupérer la traduction depuis le fichier de traduction
        String key = "POTION_TYPE_" + type;
        String translated = getTranslatedMaterialName(key, locale);
        
        if (translated != null && !translated.isEmpty()) {
            return translated;
        }
        
        // Fallback: formatter le nom du type
        return formatPotionType(type);
    }

    /**
     * Formate le nom du type de potion
     */
    private String formatPotionType(String type) {
        // Convertir NIGHT_VISION en "Vision nocturne", etc.
        String[] words = type.toLowerCase().split("_");
        StringBuilder result = new StringBuilder();
        
        for (String word : words) {
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1))
                    .append(" ");
            }
        }
        
        return result.toString().trim();
    }

    /**
     * Convertit un nombre en chiffres romains
     */
    private String toRomanNumeral(int number) {
        switch (number) {
            case 1: return "I";
            case 2: return "II";
            case 3: return "III";
            case 4: return "IV";
            case 5: return "V";
            default: return String.valueOf(number);
        }
    }


    // public String getItemName(Player player, String shopId, String itemId) {
    //     Shop shop = ShopGuiPlusApi.getPlugin().getShopManager().getShopById(shopId);
    //     if (shop == null) {
    //         return itemId;
    //     }
        
    //     ShopItem shopItem = shop.getShopItem(itemId);
    //     if (shopItem == null) {
    //         return itemId;
    //     }
        
    //     // 1. Essayer d'obtenir le nom personnalisé (déjà potentiellement traduit)
    //     if (shopItem.getItem().hasItemMeta() && shopItem.getItem().getItemMeta() != null) {
    //         String displayName = shopItem.getItem().getItemMeta().getDisplayName();
    //         if (displayName != null && !displayName.isEmpty()) {
    //             return ChatColor.stripColor(displayName);
    //         }
    //     }
        
    //     // 2. Utiliser l'API de localisation de Minecraft pour obtenir le nom en français
    //     try {
    //         // // Récupérer le joueur pour accéder à ses paramètres de langue
    //         // // Note: Utilise un joueur en ligne qui a le jeu en français
    //         // Player frenchPlayer = null;
    //         // for (Player player : Bukkit.getOnlinePlayers()) {
    //         //     // On prend le premier joueur, en supposant qu'il a le jeu en français
    //         //     frenchPlayer = player;
    //         //     break;
    //         // }
            
    //         // if (frenchPlayer != null) {
    //         if (player != null) {
    //             // Créer un ItemStack temporaire pour la traduction
    //             ItemStack itemStack = shopItem.getItem().clone();
                
    //             // Utiliser la méthode de localisation de Bukkit/Spigot
    //             ItemStack nmsItem = org.bukkit.craftbukkit.v1_16_R3.inventory.CraftItemStack.asNMSCopy(itemStack);
    //             String localizedName = nmsItem.getName().getString();
                
    //             if (localizedName != null && !localizedName.isEmpty()) {
    //                 return localizedName;
    //             }
    //         }
    //     } catch (Exception e) {
    //         plugin.getLogger().warning("Erreur lors de la récupération du nom localisé: " + e.getMessage());
    //     }
        
    //     // 3. Solution alternative: fichier de traduction personnalisé
    //     String materialName = shopItem.getItem().getType().name();
    //     // String translatedName = getTranslationFromFile(materialName);
    //     // if (translatedName != null) {
    //     //     return translatedName;
    //     // }
        
    //     // 4. Fallback: formatage du nom de matériau
    //     return formatMaterialName(materialName);
    // }

    // /**
    //  * Récupère la traduction d'un matériau depuis un fichier de configuration
    //  */
    // private String getTranslationFromFile(String materialName) {
    //     // Créer le fichier s'il n'existe pas
    //     File translationFile = new File(plugin.getDataFolder(), "translations.yml");
    //     if (!translationFile.exists()) {
    //         try {
    //             translationFile.createNewFile();
    //             YamlConfiguration defaultTranslations = new YamlConfiguration();
    //             // Ajouter quelques traductions par défaut
    //             defaultTranslations.set("DIAMOND_SWORD", "Épée en diamant");
    //             defaultTranslations.set("IRON_PICKAXE", "Pioche en fer");
    //             // Sauvegarder le fichier
    //             defaultTranslations.save(translationFile);
    //         } catch (IOException e) {
    //             plugin.getLogger().warning("Impossible de créer le fichier de traductions: " + e.getMessage());
    //             return null;
    //         }
    //     }
        
    //     // Charger les traductions
    //     YamlConfiguration translations = YamlConfiguration.loadConfiguration(translationFile);
    //     return translations.getString(materialName, null);
    // }


    // public String getItemName(String shopId, String itemId) {
    //     Shop shop = ShopGuiPlusApi.getPlugin().getShopManager().getShopById(shopId);
    //     if (shop == null) {
    //         return itemId;
    //     }
        
    //     ShopItem shopItem = shop.getShopItem(itemId);
    //     if (shopItem == null) {
    //         return itemId;
    //     }
        
    //     // Essayer d'obtenir le nom de l'item
    //     if (shopItem.getItem().hasItemMeta() && shopItem.getItem().getItemMeta() != null) {
    //         // Vérifier si l'item a un nom personnalisé
    //         String displayName = shopItem.getItem().getItemMeta().getDisplayName();
    //         if (displayName != null && !displayName.isEmpty()) {
    //             // Enlever les codes couleur pour un affichage plus propre sur la carte
    //             return ChatColor.stripColor(displayName);
    //         }
    //     }
    //     // String displayName = shopItem.getItem().getItemMeta().getDisplayName();
    //     // if (displayName != null && !displayName.isEmpty()) {
    //     //     // Enlever les codes couleur pour un affichage plus propre sur la carte
    //     //     return ChatColor.stripColor(displayName);
    //     // }
        
    //     // Si pas de nom personnalisé, utiliser le nom du matériau
    //     if (shopItem.getItem() != null && shopItem.getItem().getType() != null) {
    //         String materialName = shopItem.getItem().getType().name();
    //         return formatMaterialName(materialName);
    //     }
        
    //     return itemId;
    // }

    /**
     * Formate le nom d'un matériau pour un affichage plus lisible
     */
    private String formatMaterialName(String materialName) {
        // Remplacer les underscores par des espaces et mettre en majuscule la première lettre de chaque mot
        String[] words = materialName.toLowerCase().split("_");
        StringBuilder result = new StringBuilder();
        
        for (String word : words) {
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1))
                    .append(" ");
            }
        }
        
        return result.toString().trim();
    }

}