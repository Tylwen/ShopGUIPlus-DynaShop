package fr.tylwen.satyria.dynashop.data;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
// import org.bukkit.plugin.Plugin;
// import org.bukkit.inventory.ItemStack;

import fr.tylwen.satyria.dynashop.DynaShopPlugin;
import fr.tylwen.satyria.dynashop.data.param.DynaShopType;
import fr.tylwen.satyria.dynashop.data.param.RecipeType;
import net.brcdev.shopgui.ShopGuiPlusApi;
// import net.brcdev.shopgui.exception.shop.ShopsNotLoadedException;
import net.brcdev.shopgui.shop.Shop;
import net.brcdev.shopgui.shop.item.ShopItem;

import java.io.File;
// import java.io.ObjectInputFilter.Config;
import java.util.HashMap;
import java.util.Map;
// import java.util.HashMap;
// import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class ShopConfigManager {
    private final File shopConfigFolder;
    private final Map<ShopItem, DynamicPrice> priceMap = new HashMap<>();
    private final Map<String, RecipeType> recipeTypeCache = new ConcurrentHashMap<>();

    // Cache pour les configurations YAML
    private final Map<String, YamlConfiguration> shopConfigCache = new ConcurrentHashMap<>();
    private final Map<File, Long> fileLastModifiedCache = new ConcurrentHashMap<>();
    
    // Cache pour les sections de configuration
    private final Map<String, ConfigurationSection> sectionCache = new ConcurrentHashMap<>();

    /**
     * Constructeur qui initialise le répertoire de configuration des shops.
     *
     * @param shopConfigFolder Le répertoire contenant les fichiers de configuration des shops.
     */
    public ShopConfigManager(File shopConfigFolder) {
        this.shopConfigFolder = shopConfigFolder;
    }

    /**
     * Récupère la configuration YAML pour un shop spécifique, avec mise en cache.
     *
     * @param shopID L'ID du shop.
     * @return La configuration YAML du shop.
     */
    public YamlConfiguration getShopConfig(String shopID) {
        return shopConfigCache.computeIfAbsent(shopID, id -> {
            File shopFile = ShopFile.getFileByShopID(id);
            if (shopFile == null || !shopFile.exists()) {
                return new YamlConfiguration();
            }
            YamlConfiguration config = YamlConfiguration.loadConfiguration(shopFile);
            fileLastModifiedCache.put(shopFile, shopFile.lastModified());
            return config;
        });
    }
    
    /**
     * Vérifie si la configuration en cache est à jour et la recharge si nécessaire.
     *
     * @param shopID L'ID du shop.
     * @return La configuration YAML à jour.
     */
    public YamlConfiguration getOrUpdateShopConfig(String shopID) {
        File shopFile = ShopFile.getFileByShopID(shopID);
        if (shopFile == null || !shopFile.exists()) {
            return new YamlConfiguration();
        }
        
        // Vérifier si le fichier a été modifié depuis la dernière fois
        Long lastCachedModified = fileLastModifiedCache.get(shopFile);
        long currentModified = shopFile.lastModified();
        
        if (lastCachedModified == null || currentModified > lastCachedModified) {
            // Le fichier a été modifié, recharger la configuration
            YamlConfiguration config = YamlConfiguration.loadConfiguration(shopFile);
            shopConfigCache.put(shopID, config);
            fileLastModifiedCache.put(shopFile, currentModified);
            
            // Vider le cache des sections pour ce shop
            clearSectionCacheForShop(shopID);
            
            return config;
        }
        
        return shopConfigCache.getOrDefault(shopID, YamlConfiguration.loadConfiguration(shopFile));
    }
    
    /**
     * Nettoie le cache des sections pour un shop spécifique.
     *
     * @param shopID L'ID du shop.
     */
    private void clearSectionCacheForShop(String shopID) {
        sectionCache.keySet().removeIf(key -> key.startsWith(shopID + ":"));
    }
    
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
        return sectionCache.computeIfAbsent(cacheKey, key -> {
            YamlConfiguration config = getOrUpdateShopConfig(shopID);
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
        fileLastModifiedCache.clear();
        sectionCache.clear();
        
        // Recharger les fichiers de shop
        File shopDir = getShopDirectory();
        if (shopDir != null && shopDir.exists()) {
            ShopFile.loadShopFiles(shopDir);
            
            // Précharger les configurations des shops fréquemment utilisés
            for (String shopID : ShopFile.getAllShopIDs()) {
                getOrUpdateShopConfig(shopID);
            }
        }
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
    
        DynaShopPlugin.getInstance().getDataManager().loadPricesFromDatabase();
    
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
        if (itemSec == null) {
            return;
        }
    
        ShopItem item = shop.getShopItems().stream()
            .filter(i -> i.getId().equals(key))
            .findFirst()
            .orElse(null);
        // if (item == null || priceMap.containsKey(item)) {
        if (item == null) {
            return;
        }

        if (!itemSec.isConfigurationSection("buyDynamic") && !itemSec.isConfigurationSection("sellDynamic") && getTypeDynaShop(shop.getId(), item.getId()) != DynaShopType.STATIC_STOCK) {
            if (DynaShopPlugin.getInstance().getItemDataManager().itemExists(shop.getId(), item.getId())) {
                DynaShopPlugin.getInstance().getItemDataManager().deleteItem(shop.getId(), item.getId());
            }
            priceMap.remove(item);
            return;
        }
    
        DynamicPrice price = createDynamicPrice(itemSec);
        priceMap.put(item, price);

        // // Charger les recettes
        // DynaShopPlugin.getInstance().getCustomRecipeManager().loadRecipeFromShopConfig(shop.getId(), key, itemSec).ifPresent(recipe -> {
        //     Bukkit.addRecipe(recipe);
        //     DynaShopPlugin.getInstance().getLogger().info("Recette ajoutée pour l'item " + key + " dans le shop " + shop.getId());
        // });
    
        if (!DynaShopPlugin.getInstance().getItemDataManager().itemHasPrice(shop.getId(), item.getId())) {
            DynaShopPlugin.getInstance().getItemDataManager().savePrice(shop.getId(), item.getId(), price.getBuyPrice(), price.getSellPrice());
        }
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
    
        // return new DynamicPrice(baseBuy, baseSell, minBuy, maxBuy, minSell, maxSell, growthBuy, decayBuy, growthSell, decaySell);
        return new DynamicPrice(baseBuy, baseSell, minBuy, maxBuy, minSell, maxSell, growthBuy, decayBuy, growthSell, decaySell, stock, minStock, maxStock, buyModifier, sellModifier);
    }

    // /**
    //  * Vérifie si une section dynamique existe pour un item dans un shop.
    //  *
    //  * @param shopID  L'ID du shop.
    //  * @param itemID  L'ID de l'item.
    //  * @param section Le nom de la section à vérifier (ex. "buyDynamic", "sellDynamic").
    //  * @return true si la section dynamique existe, false sinon.
    //  */
    // public boolean hasSection(String shopID, String itemID, String section) {
    //     File shopFile = ShopFile.getFileByShopID(shopID);
    //     if (shopFile == null || !shopFile.exists()) {
    //         return false;
    //     }
    
    //     YamlConfiguration config = YamlConfiguration.loadConfiguration(shopFile);
    //     ConfigurationSection shopSection = config.getConfigurationSection(shopID);
    //     if (shopSection == null) {
    //         return false;
    //     }

    //     ConfigurationSection itemsSection = shopSection.getConfigurationSection("items");
    //     if (itemsSection == null) {
    //         return false;
    //     }
    
    //     ConfigurationSection itemSection = itemsSection.getConfigurationSection(itemID);
    //     if (itemSection == null) {
    //         return false;
    //     }
    
    //     // return itemSection.isConfigurationSection(section);

    //     // Pour les sections imbriquées comme "recipe.type"
    //     String[] sectionParts = section.split("\\.");
    //     ConfigurationSection currentSection = itemSection;
        
    //     for (int i = 0; i < sectionParts.length - 1; i++) {
    //         String sectionPart = sectionParts[i];
    //         currentSection = findSectionIgnoreCase(currentSection, sectionPart);
    //         if (currentSection == null) {
    //             return false;
    //         }
    //     }
        
    //     String finalSection = sectionParts[sectionParts.length - 1];
    //     return findKeyIgnoreCase(currentSection, finalSection) != null || 
    //         findSectionIgnoreCase(currentSection, finalSection) != null;
    // }

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

    public boolean hasRecipePattern(String shopID, String itemID) {
        return hasSection(shopID, itemID, "recipe.pattern");
    }
    public boolean hasRecipeIngredients(String shopID, String itemID) {
        return hasSection(shopID, itemID, "recipe.ingredients");
    }

    public boolean hasDynaShopSection(String shopID, String itemID) {
        return hasSection(shopID, itemID, "typeDynaShop");
    }
    // public boolean hasDynaShopSection(String shopID, String itemID, String section) {
    //     return hasSection(shopID, itemID, "dynashop." + section);
    // }

    /**
     * Récupère le type de DynaShop d'un item dans un shop.
     *
     * @param shopID L'ID du shop.
     * @param itemID L'ID de l'item.
     * @return Le type de DynaShop (ex. "buy", "sell", "none").
     */
    public DynaShopType getTypeDynaShop(String shopID, String itemID) {
        String type = getItemValue(shopID, itemID, "typeDynaShop", String.class).orElse("NONE");
        try {
            return DynaShopType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            return DynaShopType.NONE; // Retourner NONE si le type est invalide
        }
    }
    // public String getTypeDynaShop(String shopID, String itemID) {
    //     return getItemValue(shopID, itemID, "typeDynaShop", String.class).orElse("none");
    // }
    // public String getTypeDynaShop(String shopID, String itemID) {
    //     File shopFile = ShopFile.getFileByShopID(shopID);
    //     if (shopFile == null || !shopFile.exists()) {
    //         return null;
    //     }
    
    //     YamlConfiguration config = YamlConfiguration.loadConfiguration(shopFile);
    //     ConfigurationSection shopSection = config.getConfigurationSection(shopID);
    //     ConfigurationSection itemsSection = shopSection.getConfigurationSection("items");
    //     if (itemsSection == null) {
    //         return null;
    //     }
    
    //     ConfigurationSection itemSection = itemsSection.getConfigurationSection(itemID);
    //     if (itemSection == null) {
    //         return null;
    //     }
    
    //     return itemSection.getString("typeDynashop", "none");
    // }

    /**
     * Récupère le type de recette d'un item dans un shop.
     *
     * @param shopID L'ID du shop.
     * @param itemID L'ID de l'item.
     * @return Le type de recette (ex. "craft", "smelt", "none").
     */
    public RecipeType getTypeRecipe(String shopID, String itemID) {
        String key = shopID + ":" + itemID;

        return recipeTypeCache.computeIfAbsent(key, k -> {
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
    // public String getTypeRecipe(String shopID, String itemID) {
    //     return getItemValue(shopID, itemID, "recipe.type", String.class).orElse("none");
    // }
    // public String getTypeRecipe(String shopID, String itemID) {
    //     File shopFile = ShopFile.getFileByShopID(shopID);
    //     if (shopFile == null || !shopFile.exists()) {
    //         return null;
    //     }
    
    //     YamlConfiguration config = YamlConfiguration.loadConfiguration(shopFile);
    //     ConfigurationSection shopSection = config.getConfigurationSection(shopID);
    //     ConfigurationSection itemsSection = shopSection.getConfigurationSection("items");
    //     if (itemsSection == null) {
    //         return null;
    //     }
    
    //     ConfigurationSection itemSection = itemsSection.getConfigurationSection(itemID);
    //     if (itemSection == null) {
    //         return null;
    //     }
    
    //     return itemSection.getString("recipe.type", "none");
    // }


    // /**
    //  * Récupère une valeur spécifique depuis le fichier de configuration d'un shop.
    //  *
    //  * @param shopID L'ID du shop.
    //  * @param itemID L'ID de l'item.
    //  * @param key    La clé à récupérer (ex. "buyPrice", "sellPrice").
    //  * @return La valeur sous forme de double (Optional.empty() si non trouvée).
    //  */
    // public Optional<Double> getItemValue(String shopID, String itemID, String key) {
    //     // Récupérer le fichier correspondant au shopID via ShopFile
    //     File shopFile = ShopFile.getFileByShopID(shopID);
    //     // DynaShopPlugin.getInstance().getLogger().info("ShopFile: " + shopFile + " | ShopID: " + shopID);
    //     if (shopFile == null || !shopFile.exists()) {
    //         return Optional.empty();
    //     }
    
    //     // Charger la configuration YAML du fichier
    //     YamlConfiguration config = YamlConfiguration.loadConfiguration(shopFile);

    //     ConfigurationSection shopSection = config.getConfigurationSection(shopID);
    
    //     // Accéder à la section "items" du shop
    //     ConfigurationSection itemsSection = shopSection.getConfigurationSection("items");
    //     if (itemsSection == null) {
    //         return Optional.empty();
    //     }
    
    //     // Accéder à la section spécifique de l'item
    //     ConfigurationSection itemSection = itemsSection.getConfigurationSection(itemID);
    //     if (itemSection == null) {
    //         return Optional.empty();
    //     }
    
    //     // DynaShopPlugin.getInstance().getLogger().info("ItemSection: " + itemSection + " | ItemID: " + itemID + " | Key: " + key);
    //     // Récupérer la valeur associée à la clé
    //     return Optional.ofNullable(itemSection.getDouble(key, -1.0))
    //                    .filter(value -> value >= 0); // Filtrer les valeurs invalides (par exemple, -1.0)
    // }

    // public Optional<Integer> getItemIntegerValue(String shopID, String itemID, String key) {
    //     // Récupérer le fichier correspondant au shopID via ShopFile
    //     File shopFile = ShopFile.getFileByShopID(shopID);
    //     if (shopFile == null || !shopFile.exists()) {
    //         return Optional.empty();
    //     }
    
    //     // Charger la configuration YAML du fichier
    //     YamlConfiguration config = YamlConfiguration.loadConfiguration(shopFile);

    //     ConfigurationSection shopSection = config.getConfigurationSection(shopID);
    
    //     // Accéder à la section "items" du shop
    //     ConfigurationSection itemsSection = shopSection.getConfigurationSection("items");
    //     if (itemsSection == null) {
    //         return Optional.empty();
    //     }
    
    //     // Accéder à la section spécifique de l'item
    //     ConfigurationSection itemSection = itemsSection.getConfigurationSection(itemID);
    //     if (itemSection == null) {
    //         return Optional.empty();
    //     }
    
    //     // Récupérer la valeur associée à la clé
    //     return Optional.of(itemSection.getInt(key, -1)); // Filtrer les valeurs invalides (par exemple, -1.0)
    // }

    
    // /**
    //  * Récupère une valeur booléenne spécifique depuis le fichier de configuration d'un shop.
    //  *
    //  * @param shopID L'ID du shop.
    //  * @param itemID L'ID de l'item.
    //  * @param key    La clé à récupérer (ex. "buyPrice", "sellPrice").
    //  * @return La valeur sous forme de boolean (Optional.empty() si non trouvée).
    //  */
    // public Optional<Boolean> getItemBooleanValue(String shopID, String itemID, String key) {
    //     // Récupérer le fichier correspondant au shopID via ShopFile
    //     File shopFile = ShopFile.getFileByShopID(shopID);
    //     if (shopFile == null || !shopFile.exists()) {
    //         return Optional.empty();
    //     }
    
    //     // Charger la configuration YAML du fichier
    //     YamlConfiguration config = YamlConfiguration.loadConfiguration(shopFile);

    //     ConfigurationSection shopSection = config.getConfigurationSection(shopID);
    
    //     // Accéder à la section "items" du shop
    //     ConfigurationSection itemsSection = shopSection.getConfigurationSection("items");
    //     if (itemsSection == null) {
    //         return Optional.empty();
    //     }
    
    //     // Accéder à la section spécifique de l'item
    //     ConfigurationSection itemSection = itemsSection.getConfigurationSection(itemID);
    //     if (itemSection == null) {
    //         return Optional.empty();
    //     }
    
    //     // Récupérer la valeur associée à la clé
    //     return Optional.of(itemSection.getBoolean(key, false)); // Filtrer les valeurs invalides (par exemple, -1.0)
    // }

    // private <T> Optional<T> getItemConfigValue(String shopID, String itemID, String key, Class<T> type) {
    public <T> Optional<T> getItemValue(String shopID, String itemID, String key, Class<T> type) {
        // // Récupérer le fichier correspondant au shopID via ShopFile
        // File shopFile = ShopFile.getFileByShopID(shopID);
        // if (shopFile == null || !shopFile.exists()) {
        //     return Optional.empty();
        // }
    
        // Charger la configuration YAML du fichier
        // YamlConfiguration config = YamlConfiguration.loadConfiguration(shopFile);
        YamlConfiguration config = getOrUpdateShopConfig(shopID);
    
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

    // /**
    //  * Récupère une valeur spécifique depuis le fichier de configuration d'un shop.
    //  *
    //  * @param shopID L'ID du shop.
    //  * @param itemID L'ID de l'item.
    //  * @return La valeur sous forme de double (Optional.empty() si non trouvée).
    //  */
    // public Optional<DynamicPrice> getItemValues(String shopID, String itemID) {
    //     // Optional<Double> buyPrice = getItemValue(shopID, itemID, "buyPrice");
    //     // Optional<Double> sellPrice = getItemValue(shopID, itemID, "sellPrice");
    //     Optional<Double> buyPrice = getItemValue(shopID, itemID, "buyPrice", Double.class);
    //     Optional<Double> sellPrice = getItemValue(shopID, itemID, "sellPrice", Double.class);
    //     if (buyPrice.isPresent() && sellPrice.isPresent()) {
    //         return Optional.of(new DynamicPrice(buyPrice.get(), sellPrice.get()));
    //     }
    //     return Optional.empty();
    // }

    // public Optional<DynamicPrice> getItemAllValues(String shopID, String itemID) {
    //     Optional<Double> buyPrice = getItemValue(shopID, itemID, "buyPrice");
    //     Optional<Double> sellPrice = getItemValue(shopID, itemID, "sellPrice");
    //     Optional<Double> minBuy = getItemValue(shopID, itemID, "buyDynamic.min");
    //     Optional<Double> maxBuy = getItemValue(shopID, itemID, "buyDynamic.max");
    //     Optional<Double> minSell = getItemValue(shopID, itemID, "sellDynamic.min");
    //     Optional<Double> maxSell = getItemValue(shopID, itemID, "sellDynamic.max");
    //     Optional<Double> growthBuy = getItemValue(shopID, itemID, "buyDynamic.growth");
    //     Optional<Double> decayBuy = getItemValue(shopID, itemID, "buyDynamic.decay");
    //     Optional<Double> growthSell = getItemValue(shopID, itemID, "sellDynamic.growth");
    //     Optional<Double> decaySell = getItemValue(shopID, itemID, "sellDynamic.decay");
    //     if (buyPrice.isPresent() && sellPrice.isPresent()) {
    //         return Optional.of(new DynamicPrice(
    //             buyPrice.get(), sellPrice.get(),
    //             minBuy.orElse(buyPrice.get()), maxBuy.orElse(buyPrice.get()),
    //             minSell.orElse(sellPrice.get()), maxSell.orElse(sellPrice.get()),
    //             growthBuy.orElse(1.0), decayBuy.orElse(1.0),
    //             growthSell.orElse(1.0), decaySell.orElse(1.0)
    //         ));
    //     }
    //     return Optional.empty();
    // }
    public ItemPriceData getItemAllValues(String shopID, String itemID) {
        ItemPriceData itemPriceData = new ItemPriceData();

        itemPriceData.buyPrice = getItemValue(shopID, itemID, "buyPrice", Double.class);
        itemPriceData.sellPrice = getItemValue(shopID, itemID, "sellPrice", Double.class);

        itemPriceData.minBuy = getItemValue(shopID, itemID, "buyDynamic.min", Double.class);
        itemPriceData.maxBuy = getItemValue(shopID, itemID, "buyDynamic.max", Double.class);
        itemPriceData.minSell = getItemValue(shopID, itemID, "sellDynamic.min", Double.class);
        itemPriceData.maxSell = getItemValue(shopID, itemID, "sellDynamic.max", Double.class);

        itemPriceData.growthBuy = getItemValue(shopID, itemID, "buyDynamic.growth", Double.class);
        itemPriceData.decayBuy = getItemValue(shopID, itemID, "buyDynamic.decay", Double.class);
        itemPriceData.growthSell = getItemValue(shopID, itemID, "sellDynamic.growth", Double.class);
        itemPriceData.decaySell = getItemValue(shopID, itemID, "sellDynamic.decay", Double.class);

        itemPriceData.stock = getItemValue(shopID, itemID, "stock.base", Integer.class);
        itemPriceData.minStock = getItemValue(shopID, itemID, "stock.min", Integer.class);
        itemPriceData.maxStock = getItemValue(shopID, itemID, "stock.max", Integer.class);
        itemPriceData.stockBuyModifier = getItemValue(shopID, itemID, "stock.buyModifier", Double.class);
        itemPriceData.stockSellModifier = getItemValue(shopID, itemID, "stock.sellModifier", Double.class);

        return itemPriceData;
    }

    // public ItemStack getItemStack(String shopID, String itemID) {
    //     Optional<ItemStack> itemStack = DynaShopPlugin.getInstance().getItemDataManager().getItemStack(shopID, itemID);
    //     if (itemStack.isPresent()) {
    //         return itemStack.get();
    //     } else {
    //         return null;
    //     }
    // }

    public ConfigurationSection getSection(String shopID, String itemID, String section) {
        // File shopFile = ShopFile.getFileByShopID(shopID);
        // if (shopFile == null || !shopFile.exists()) {
        //     return null;
        // }
    
        YamlConfiguration config = getOrUpdateShopConfig(shopID);
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
}