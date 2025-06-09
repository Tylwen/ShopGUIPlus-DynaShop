package fr.tylwen.satyria.dynashop;

import org.bstats.bukkit.Metrics;
import org.bstats.charts.AdvancedPie;
// import org.bstats.charts.DrilldownPie;

// import net.brcdev.shopgui.ShopGuiPlusApi;
// import net.brcdev.shopgui.config.Lang;
// import net.brcdev.shopgui.event.ShopPreTransactionEvent;
// import net.brcdev.shopgui.api.events.ShopTransactionEvent;
// import net.brcdev.shopgui.shop.Shop;
// import net.brcdev.shopgui.shop.ShopManager.ShopAction;
// import net.brcdev.shopgui.gui.gui.OpenGui;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
// import org.bukkit.event.HandlerList;
// import org.bukkit.configuration.ConfigurationSection;
// import org.bukkit.configuration.file.YamlConfiguration;
// import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
// import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import fr.tylwen.satyria.dynashop.cache.CacheManager;
import fr.tylwen.satyria.dynashop.command.DynaShopCommand;
import fr.tylwen.satyria.dynashop.command.LimitResetCommand;
import fr.tylwen.satyria.dynashop.command.ReloadCommand;
import fr.tylwen.satyria.dynashop.config.DataConfig;
import fr.tylwen.satyria.dynashop.config.LangConfig;
import fr.tylwen.satyria.dynashop.price.DynamicPrice;
// import fr.tylwen.satyria.dynashop.data.RecipeCacheManager;
// import fr.tylwen.satyria.dynashop.config.Config;
// import fr.tylwen.satyria.dynashop.config.Lang;
// import fr.tylwen.satyria.dynashop.config.Settings;
import fr.tylwen.satyria.dynashop.data.ShopConfigManager;
import fr.tylwen.satyria.dynashop.data.param.DynaShopType;
import fr.tylwen.satyria.dynashop.database.BatchDatabaseUpdater;
import fr.tylwen.satyria.dynashop.database.DataManager;
import fr.tylwen.satyria.dynashop.database.ItemDataManager;
// import fr.tylwen.satyria.dynashop.gui.ShopRefreshManager;
import fr.tylwen.satyria.dynashop.hook.DynaShopExpansion;
// import fr.tylwen.satyria.dynashop.hook.DynaShopItemProvider;
import fr.tylwen.satyria.dynashop.hook.ShopGUIPlusHook;
import fr.tylwen.satyria.dynashop.limit.TransactionLimiter;
// import fr.tylwen.satyria.dynashop.hook.ShopItemProcessor;
import fr.tylwen.satyria.dynashop.listener.DynaShopListener;
import fr.tylwen.satyria.dynashop.listener.ShopItemPlaceholderListener;
import fr.tylwen.satyria.dynashop.price.PriceRecipe;
import fr.tylwen.satyria.dynashop.price.PriceStock;
// import fr.tylwen.satyria.dynashop.packet.ItemPacketInterceptor;
// import fr.tylwen.satyria.dynashop.utils.CommentedConfiguration;
// import fr.tylwen.satyria.dynashop.task.ReloadDatabaseTask;
// import fr.tylwen.satyria.dynashop.task.DynamicPricesTask;
import fr.tylwen.satyria.dynashop.task.WaitForShopsTask;
import fr.tylwen.satyria.dynashop.utils.PriceFormatter;
// import fr.tylwen.satyria.dynashop.data.ShopFile;
// import net.brcdev.shopgui.ShopGuiPlugin;
// import net.brcdev.shopgui.ShopGuiPlugin;
import net.brcdev.shopgui.ShopGuiPlusApi;
import net.brcdev.shopgui.shop.Shop;
// import net.brcdev.shopgui.shop.item.ShopItem;
// import net.brcdev.shopgui.provider.item.ItemProvider;
import net.brcdev.shopgui.shop.item.ShopItem;

import java.io.File;
// import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
// import java.util.Map;
// import java.util.concurrent.ConcurrentHashMap;
// import java.util.concurrent.ExecutorService;
// import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
// import java.util.stream.Collectors;


// import javax.xml.crypto.Data;

public class DynaShopPlugin extends JavaPlugin implements Listener {

    private static DynaShopPlugin instance;
    private ShopGUIPlusHook shopGUIPlusHook;
    private YamlConfiguration configMain;
    private YamlConfiguration configLang;
    // private final Map<ShopItem, DynamicPrice> priceMap = new HashMap<>();
    private DataManager dataManager;
    private BatchDatabaseUpdater batchDatabaseUpdater;
    private ShopConfigManager shopConfigManager;
    private ItemDataManager itemDataManager;
    private PriceRecipe priceRecipe;
    private PriceStock priceStock;
    private DataConfig dataConfig;
    private LangConfig langConfig;
    // private CustomRecipeManager customRecipeManager;
    private Logger logger;
    // private CommentedConfiguration config;
    private DynaShopListener dynaShopListener;
    private ShopItemPlaceholderListener shopItemPlaceholderListener;
    private DynaShopExpansion placeholderExpansion;
    private TransactionLimiter transactionLimiter;
    // private ShopRefreshManager shopRefreshManager;
    // private ItemPacketInterceptor packetInterceptor;
    private PriceFormatter priceFormatter;
    // private CustomIngredientsManager customIngredientsManager;

    private int dynamicPricesTaskId;
    private int waitForShopsTaskId;

    // private RecipeCacheManager recipeCacheManager;
    
    private String cacheMode;
    private CacheManager<String, DynamicPrice> priceCache;
    private CacheManager<String, List<ItemStack>> recipeCache;
    private CacheManager<String, Double> calculatedPriceCache;
    private CacheManager<String, Integer> stockCache;
    private CacheManager<String, Map<String, String>> displayPriceCache;

    // public DynaShopPlugin() {
    //     this.config = new CommentedConfiguration();
    //     // this.configMain = new Config(this, "config.yml", "config.yml");
    //     // this.configLang = new Config(this, "lang.yml", "lang.yml");
    // }

    public static DynaShopPlugin getInstance() {
        return instance;
    }

    public DynaShopListener getDynaShopListener() {
        return this.dynaShopListener;
    }

    public ShopItemPlaceholderListener getShopItemPlaceholderListener() {
        return this.shopItemPlaceholderListener;
    }

    public YamlConfiguration getConfigMain() {
        return this.configMain;
    }
    public YamlConfiguration getConfigLang() {
        return this.configLang;
    }
    
    public PriceFormatter getPriceFormatter() {
        return priceFormatter;
    }

    public DataManager getDataManager() {
        return this.dataManager;
    }
    
    public ItemDataManager getItemDataManager() {
        return this.itemDataManager;
    }

    public BatchDatabaseUpdater getBatchDatabaseUpdater() {
        return this.batchDatabaseUpdater;
    }

    public ShopConfigManager getShopConfigManager() {
        return this.shopConfigManager;
    }
    // public Map<ShopItem, DynamicPrice> getPriceMap() {
    //     return priceMap;
    // }
    // public CommentedConfiguration getConfig() {
    //     return this.config;
    // }
    public PriceRecipe getPriceRecipe() {
        return this.priceRecipe;
    }

    public DataConfig getDataConfig() {
        return this.dataConfig;
    }

    public LangConfig getLangConfig() {
        return this.langConfig;
    }

    // public CustomRecipeManager getCustomRecipeManager() {
    //     return this.customRecipeManager;
    // }

    public PriceStock getPriceStock() {
        return this.priceStock;
    }

    public void setDynamicPricesTaskId(int id) {
        this.dynamicPricesTaskId = id;
    }

    public int getDynamicPricesTaskId() {
        return this.dynamicPricesTaskId;
    }

    public int getWaitForShopsTaskId() {
        return this.waitForShopsTaskId;
    }
    
    // Méthode setter à ajouter
    public void setWaitForShopsTaskId(int taskId) {
        this.waitForShopsTaskId = taskId;
    }
    
    // Getter pour l'expansion PlaceholderAPI
    public DynaShopExpansion getPlaceholderExpansion() {
        return this.placeholderExpansion;
    }

    public TransactionLimiter getTransactionLimiter() {
        return this.transactionLimiter;
    }

    // public CustomIngredientsManager getCustomIngredientsManager() {
    //     return this.customIngredientsManager;
    // }

    // public ShopRefreshManager getShopRefreshManager() {
    //     return shopRefreshManager;
    // }

    // public ItemPacketInterceptor getPacketInterceptor() {
    //     return packetInterceptor;
    // }

    public void reloadConfigAndCacheMode() {
        reloadConfig();
        cacheMode = getConfig().getString("cache.mode", "full");
    }

    public boolean isRealTimeMode() {
        return "realtime".equalsIgnoreCase(cacheMode);
    }


    @Override
    public void onEnable() {
        // // Vérifier si ShopGUIPlus est installé
        // Plugin shopGuiPlus = getServer().getPluginManager().getPlugin("ShopGUIPlus");
        // if (shopGuiPlus == null) {
        //     getLogger().severe("ShopGUIPlus n'est pas installé ou activé. Le plugin DynaShop ne peut pas fonctionner.");
        //     getServer().getPluginManager().disablePlugin(this);
        //     return;
        // }
        // if (instance != null) {
        //     throw new IllegalStateException("DynaShopPlugin instance already initialized!");
        // }
        instance = this;
        this.logger = getLogger();
        init();
        initCache();
        // load();
        hookIntoShopGUIPlus();

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            // new DynaShopExpansion(this.itemDataManager, this.shopConfigManager, this.priceRecipe).register();
            // new DynaShopExpansion(this).register();
            this.placeholderExpansion = new DynaShopExpansion(this);
            this.placeholderExpansion.register();
            getLogger().info("Placeholders enregistrés avec PlaceholderAPI !");
        }
        // if (getServer().getPluginManager().getPlugin("ProtocolLib") != null) {
        //     this.packetInterceptor = new ItemPacketInterceptor(this);
        //     getLogger().info("ProtocolLib détecté, intercepteur de paquets activé!");
        // } else {
        //     getLogger().warning("ProtocolLib n'est pas installé, l'intercepteur de paquets est désactivé.");
        //     getLogger().warning("Installez ProtocolLib pour une expérience optimale avec les placeholders.");
        // }

        // getServer().getPluginManager().registerEvents(new DynaShopListener(this), this);
        getServer().getPluginManager().registerEvents(this.dynaShopListener, this);
        // Initialiser le listener avant de l'utiliser ailleurs
        this.shopItemPlaceholderListener = new ShopItemPlaceholderListener(this);
        getServer().getPluginManager().registerEvents(this.shopItemPlaceholderListener, this);

        getCommand("dynashop").setExecutor(new DynaShopCommand(this));
        getCommand("dynashop").setExecutor(new LimitResetCommand(this));
        getCommand("dynashop").setExecutor(new ReloadCommand(this));
        // getCommand("dynashop").setTabCompleter(new ReloadCommand(this));

        this.dataManager.initDatabase();

        // Utiliser un executor service commun avec un nombre limité de threads
        // ExecutorService sharedExecutor = Executors.newFixedThreadPool(3);
        // Planification des tâches
        // getServer().getScheduler().runTaskTimerAsynchronously(this, new ReloadDatabaseTask(this), 0L, 20L * 60L * 10L); // Toutes les 10 minutes
        // this.waitForShopsTaskId = getServer().getScheduler().runTaskTimer(this, new WaitForShopsTask(this), 0L, 20L * 5L).getTaskId(); // Toutes les 5 secondes
        WaitForShopsTask waitTask = new WaitForShopsTask(this);
        BukkitTask task = getServer().getScheduler().runTaskTimer(this, waitTask, 0L, 20L * 5L);
        waitTask.setSelfTask(task);
        // getServer().getScheduler().runTaskTimerAsynchronously(this, new SavePricesTask(this), 20L * 60L * 5L, 20L * 60L * 5L); // Toutes les 5 minutes
        // Modifier cette ligne
        // getServer().getScheduler().runTaskTimerAsynchronously(this, new DynamicPricesTask(this), 0L, 20L * 60L * 1L);

        // // À ajouter à la fin de onEnable() dans DynaShopPlugin.java
        // getServer().getScheduler().runTaskAsynchronously(this, () -> {
        //     getLogger().warning("Test de loadPricesFromDatabase...");
        //     Map<ShopItem, DynamicPrice> priceMap = getDataManager().loadPricesFromDatabase();
        //     getLogger().warning("Résultat: " + (priceMap == null ? "null" : priceMap.size() + " éléments"));
        // });

        // // Nouvelle implémentation avec variable pour suivre la tâche
        // this.dynamicPricesTaskId = getServer().getScheduler().runTaskTimerAsynchronously(
        //     this, 
        //     new DynamicPricesTask(this), 
        //     20L * 10L, // Délai initial de 10 secondes pour s'assurer que tout est chargé
        //     20L * 60L  // Une minute entre chaque exécution
        // ).getTaskId();

        // getLogger().info("Tâche DynamicPricesTask enregistrée avec l'ID: " + dynamicPricesTaskId);

        // Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
        //     // Précharger les recettes des items populaires
        //     for (String popularItemKey : configMain.getStringList("popular-items")) {
        //         String[] parts = popularItemKey.split(":");
        //         if (parts.length == 2) {
        //             String shopID = parts[0];
        //             String itemID = parts[1];
        //             ItemStack itemStack = ShopGuiPlusApi.getShop(shopID).getShopItem(itemID).getItem();
        //             if (itemStack != null) {
        //                 priceRecipe.getIngredients(shopID, itemID, itemStack);
        //             }
        //         }
        //     }
        // }, 20L * 60, 20L * 60 * 15); // Toutes les 15 minutes
        // // Planifier la précharge périodique des items populaires
        // Bukkit.getScheduler().runTaskTimerAsynchronously(this, 
        //     this::preloadPopularItems, 
        //     20L * 60 * 2,         // Démarrer après 2 minutes
        //     20L * 60 * 30);   // Répéter toutes les 30 minutes

        // // Ajouter une tâche de nettoyage du cache
        // Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
        //     recipeCacheManager.cleanup();
        // }, 20L * 60L * 15L, 20L * 60L * 15L); // Nettoyage toutes les 15 minutes

        // // Planifier le nettoyage des transactions périmées (toutes les heures)
        // getServer().getScheduler().runTaskTimerAsynchronously(this, 
        //     () -> transactionLimiter.cleanupExpiredTransactions(), 
        //     20L * 60L * 5L, // Délai initial: 5 minutes après le démarrage 
        //     20L * 60L * 60L // Intervalle: toutes les heures
        // );

        // // Planifier le nettoyage des cooldowns (toutes les 15 minutes)
        // getServer().getScheduler().runTaskTimerAsynchronously(this, 
        //     () -> transactionLimiter.cleanupCooldownTransactions(), 
        //     20L * 60L * 10L, // Délai initial: 10 minutes après le démarrage
        //     20L * 60L * 15L // Intervalle: toutes les 15 minutes
        // );
        // setupMetrics();

        getLogger().info("DynaShop activé avec succès !");
    }

    private void init() {
        generateFiles();
        
        // Initialiser les caches AVANT les classes qui en dépendent
        this.priceCache = new CacheManager<>(this, "priceCache", 5, TimeUnit.MINUTES, 10);
        this.recipeCache = new CacheManager<>(this, "recipeCache", 10, TimeUnit.MINUTES, 5);
        this.calculatedPriceCache = new CacheManager<>(this, "calculatedPriceCache", 5, TimeUnit.MINUTES, 10);
        this.stockCache = new CacheManager<>(this, "stockCache", 5, TimeUnit.MINUTES, 5);
        this.displayPriceCache = new CacheManager<>(this, "displayPriceCache", 2, TimeUnit.MINUTES, 20);

        // this.shopConfigManager = new ShopConfigManager(new File(Bukkit.getPluginManager().getPlugin("ShopGUIPlus").getDataFolder(), "shops/"));
        // this.shopConfigManager = new ShopConfigManager(new File(ShopGuiPlusApi.getPlugin().getConfigShops().getConfig().getCurrentPath(), "shops/"));
        this.shopConfigManager = new ShopConfigManager(this);
        this.dataConfig = new DataConfig(this.configMain);
        this.langConfig = new LangConfig(this.configLang);

        // this.customRecipeManager = new CustomRecipeManager(this);
        this.dynaShopListener = new DynaShopListener(this);
        this.priceRecipe = new PriceRecipe(this);
        this.priceStock = new PriceStock(this);
        this.dataManager = new DataManager(this);
        this.itemDataManager = new ItemDataManager(this.dataManager);
        this.batchDatabaseUpdater = new BatchDatabaseUpdater(this);
        this.transactionLimiter = new TransactionLimiter(this);
        // this.recipeCacheManager = new RecipeCacheManager(15 * 60 * 1000L); // 15 minutes en ms
        this.priceFormatter = new PriceFormatter(this);
        // this.customIngredientsManager = new CustomIngredientsManager();
        // this.shopRefreshManager = new ShopRefreshManager(this);
        // preloadPopularItems();
    }

    private void initCache() {
        // Vérifier si le mode de cache est défini dans la configuration
        cacheMode = getConfig().getString("cache.mode", "full");

        // Lire les durées depuis la configuration
        int priceDuration = configMain.getInt("cache.durations.price", 30);
        int displayDuration = configMain.getInt("cache.durations.display", 10);
        int recipeDuration = configMain.getInt("cache.durations.recipe", 300);
        int stockDuration = configMain.getInt("cache.durations.stock", 20);
        int calculatedDuration = configMain.getInt("cache.durations.calculated", 60);
        
        // Initialiser les caches avec ces durées
        if (cacheMode.equalsIgnoreCase("realtime")) {
            // En mode "realtime", on utilise des durées plus courtes
            priceDuration = 5;
            displayDuration = 1;
            // recipeDuration = 10;
            stockDuration = 5;
            calculatedDuration = 10;
        }
        priceCache = new CacheManager<>(this, "PriceCache", priceDuration, TimeUnit.SECONDS, 10);
        recipeCache = new CacheManager<>(this, "RecipeCache", recipeDuration, TimeUnit.SECONDS, 5);
        calculatedPriceCache = new CacheManager<>(this, "CalculatedPriceCache", calculatedDuration, TimeUnit.SECONDS, 5);
        stockCache = new CacheManager<>(this, "StockCache", stockDuration, TimeUnit.SECONDS, 10);
        displayPriceCache = new CacheManager<>(this, "DisplayPriceCache", displayDuration, TimeUnit.SECONDS, 15);

        // // Initialisation des caches avec des durées adaptées
        // priceCache = new CacheManager<>(this, "PriceCache", 30, TimeUnit.SECONDS, 10);
        // recipeCache = new CacheManager<>(this, "RecipeCache", 5, TimeUnit.MINUTES, 5);
        // calculatedPriceCache = new CacheManager<>(this, "CalculatedPriceCache", 1, TimeUnit.MINUTES, 5);
        // stockCache = new CacheManager<>(this, "StockCache", 20, TimeUnit.SECONDS, 10);
        // displayPriceCache = new CacheManager<>(this, "DisplayPriceCache", 10, TimeUnit.SECONDS, 15);
    }
    
    // Getters pour les caches
    public CacheManager<String, DynamicPrice> getPriceCache() {
        return priceCache;
    }
    
    public CacheManager<String, List<ItemStack>> getRecipeCache() {
        return recipeCache;
    }
    
    public CacheManager<String, Double> getCalculatedPriceCache() {
        return calculatedPriceCache;
    }
    
    public CacheManager<String, Integer> getStockCache() {
        return stockCache;
    }
    
    public CacheManager<String, Map<String, String>> getDisplayPriceCache() {
        return displayPriceCache;
    }

    public void invalidatePriceCache(String shopId, String itemId, Player player) {
        String baseKey = shopId + ":" + itemId;
        
        // Invalider les caches spécifiques au joueur si nécessaire
        if (player != null) {
            String playerKey = baseKey + ":" + player.getUniqueId().toString();
            priceCache.invalidate(playerKey);
            displayPriceCache.invalidate(playerKey);
        }
        
        // Invalider également les caches généraux (sans joueur spécifique)
        // priceCache.invalidate(baseKey);
        priceCache.invalidateWithPrefix(baseKey);
        calculatedPriceCache.invalidateWithPrefix(baseKey);
        displayPriceCache.invalidateWithPrefix(baseKey);
        
        // Si c'est une recette, invalider le cache des ingrédients aussi
        if (getShopConfigManager().getTypeDynaShop(shopId, itemId) == DynaShopType.RECIPE) {
            recipeCache.invalidate(baseKey);
        }
        
        getLogger().fine("Cache invalidé pour " + baseKey);
    }

    public void load() {
        generateFiles();
        // this.priceRecipe = new PriceRecipe(this.configMain);
        this.dataConfig = new DataConfig(this.configMain);
        this.langConfig = new LangConfig(this.configLang);
        reloadConfigAndCacheMode();
        // Lang.setConfig(this.configLang);
        // Settings.setConfig(this.configMain);
        // Settings.load();
    }

    private void generateFiles() {
        saveDefaultConfig();
        // Configuration de la langue
        File configLangFile = new File(getDataFolder(), "lang.yml");
        if (!configLangFile.exists()) {
            saveResource("lang.yml", false);
        }
        this.configLang = YamlConfiguration.loadConfiguration(configLangFile);

        // Configuration des paramètres
        File configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            saveResource("config.yml", false);
        }
        this.configMain = YamlConfiguration.loadConfiguration(configFile);
        // this.configMain = new Config(this, "config.yml");
        // this.configLang = new Config(this, "lang.yml");
    }

    public void setupMetrics() {
        // Initialiser bStats pour la collecte de statistiques
        Metrics metrics = new Metrics(this, 25992); // 25992 est l'ID de DynaShop dans bStats

        // Map en cache pour stocker les résultats
        final Map<String, Integer> cachedStatsMap = new HashMap<>();
        // Timestamp de la dernière mise à jour
        final long[] lastUpdate = {0L};
        // Période de mise à jour en millisecondes (15 minutes)
        final long updatePeriod = 15 * 60 * 1000;

        metrics.addCustomChart(new AdvancedPie("type_dynashop_used", () -> {
            // Vérifier si une mise à jour du cache est nécessaire
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastUpdate[0] > updatePeriod || cachedStatsMap.isEmpty()) {
                // Mettre à jour le cache de manière asynchrone
                getServer().getScheduler().runTaskAsynchronously(this, () -> {
                    Map<String, Integer> newMap = new HashMap<>();
                    try {
                        for (Shop shop : ShopGuiPlusApi.getPlugin().getShopManager().getShops()) {
                            for (ShopItem item : shop.getShopItems()) {
                                DynaShopType typeDynaShop = shopConfigManager.getTypeDynaShop(shop.getId(), item.getId());
                                String typeName = typeDynaShop.name();
                                newMap.put(typeName, newMap.getOrDefault(typeName, 0) + 1);
                            }
                        }
                        
                        // Mettre à jour le cache de manière thread-safe
                        synchronized (cachedStatsMap) {
                            cachedStatsMap.clear();
                            cachedStatsMap.putAll(newMap);
                            lastUpdate[0] = currentTime;
                        }
                    } catch (Exception e) {
                        // getLogger().warning("Erreur lors de la collecte des statistiques : " + e.getMessage());
                    }
                });
            }
        
            // Retourner une copie du cache actuel
            synchronized (cachedStatsMap) {
                return new HashMap<>(cachedStatsMap);
                // return cachedStatsMap;
            }
        }));

        // metrics.addCustomChart(new AdvancedPie("type_dynashop_used", () -> {
        //     Map<String, Integer> map = new HashMap<>();
        //     // Compter chaque type d'item
        //     try {
        //         for (Shop shop : ShopGuiPlusApi.getPlugin().getShopManager().getShops()) {
        //             for (ShopItem item : shop.getShopItems()) {
        //                 DynaShopType type = shopConfigManager.getTypeDynaShop(shop.getId(), item.getId());
        //                 String typeName = type.name();
        //                 map.put(typeName, map.getOrDefault(typeName, 0) + 1);
        //             }
        //         }
        //     } catch (Exception e) {
        //         getLogger().warning("Erreur lors de la collecte des statistiques : " + e.getMessage());
        //     }
        //     return map;
        // }));

    //     // Ajouter un graphique DrilldownPie pour les types d'items
    //     metrics.addCustomChart(new DrilldownPie("type_dynashop_used", () -> {
    //         Map<String, Map<String, Integer>> map = new HashMap<>();
    //         Map<String, Integer> entry = new HashMap<>();
            
    //         for (Shop shop : ShopGuiPlusApi.getPlugin().getShopManager().getShops()) {
    //             for (ShopItem item : shop.getShopItems()) {
    //                 DynaShopType type = shopConfigManager.getTypeDynaShop(shop.getId(), item.getId());
    //                 String typeName = type.name();
                    
    //                 // Incrémenter le compteur pour ce type
    //                 entry.put(typeName, entry.getOrDefault(typeName, 0) + 1);
    //                 // Ajouter l'entrée au map
    //                 switch (type) {
    //                   case DynaShopType.STOCK -> {
    //                       map.putIfAbsent("Stock items", new HashMap<>());
    //                       map.get("Stock items").put(typeName, entry.get(typeName));
    //                   }
    //                   case DynaShopType.STATIC_STOCK -> {
    //                       map.putIfAbsent("Static stock items", new HashMap<>());
    //                       map.get("Static stock items").put(typeName, entry.get(typeName));
    //                   }
    //                   case DynaShopType.RECIPE -> {
    //                       map.putIfAbsent("Recipe items", new HashMap<>());
    //                       map.get("Recipe items").put(typeName, entry.get(typeName));
    //                   }
    //                   case DynaShopType.DYNAMIC -> {
    //                       map.putIfAbsent("Dynamic items", new HashMap<>());
    //                       map.get("Dynamic items").put(typeName, entry.get(typeName));
    //                   }
    //                   default -> {
    //                       map.putIfAbsent("Other items", new HashMap<>());
    //                       map.get("Other items").put(typeName, entry.get(typeName));
    //                   }
    //                 }
    //             }
    //         }
    //         // map.put("Item Types", entry);
    //         return map;
    //     }));
    }

    @Override
    public void onDisable() {
        // Annuler explicitement les tâches
        if (dynamicPricesTaskId != 0) {
            getServer().getScheduler().cancelTask(dynamicPricesTaskId);
            getLogger().info("Tâche DynamicPricesTask annulée (ID: " + dynamicPricesTaskId + ")");
        }

        // Il faut tout désactiver du plugin
        if (waitForShopsTaskId != 0) {
            getServer().getScheduler().cancelTask(waitForShopsTaskId);
            getLogger().info("Tâche WaitForShopsTask annulée (ID: " + waitForShopsTaskId + ")");
        }


        if (shopItemPlaceholderListener != null) {
            shopItemPlaceholderListener.shutdown();
        }
        
        // if (shopRefreshManager != null) {
        //     shopRefreshManager.shutdown();
        // }
        
        // Arrêter le gestionnaire de mises à jour en batch
        if (batchDatabaseUpdater != null) {
            batchDatabaseUpdater.shutdown();
        }

        if (dynaShopListener != null) {
            HandlerList.unregisterAll(this.dynaShopListener);
        }
        // this.dynaShopListener = null;

        // if (packetInterceptor != null) {
        //     // packetInterceptor.clearCache();
        //     packetInterceptor.shutdown();
        // }
        
        // dataManager.savePricesToDatabase(priceMap);
        dataManager.closeDatabase();
        
        // Nettoyer les caches
        priceCache.clear();
        recipeCache.clear();
        calculatedPriceCache.clear();
        stockCache.clear();
        displayPriceCache.clear();

        // HandlerList.unregisterAll(this);

        // getServer().getPluginManager().disablePlugin(this);
    }

    private void hookIntoShopGUIPlus() {
        if (Bukkit.getPluginManager().getPlugin("ShopGUIPlus") != null) {
            this.shopGUIPlusHook = new ShopGUIPlusHook(this);
            Bukkit.getPluginManager().registerEvents(shopGUIPlusHook, this);

            this.getLogger().info("ShopGUI+ detected.");
        } else {
            this.getLogger().warning("ShopGUI+ not found.");
        }

        // // Enregistrer notre processeur d'items
        // ShopGuiPlugin shopGuiPlugin = (ShopGuiPlugin) getServer().getPluginManager().getPlugin("ShopGUIPlus");
        // if (shopGuiPlugin != null) {
        //     ShopItemProcessor itemProcessor = new ShopItemProcessor(this);
        //     shopGuiPlugin.getItemManager().setProvider(itemProcessor);
        //     getLogger().info("DynaShop ItemProcessor enregistré avec ShopGUIPlus !");
        // }
    }

    public void severe(String string) {
        this.logger.severe(string);
    }
      
    public void warning(String string) {
        this.logger.warning(string);
    }
    
    public void info(String string) {
        this.logger.info(string);
    }

    // public double getCachedRecipePrice(String shopID, String itemID, String priceType) {
    //     return recipeCacheManager.getCachedRecipePrice(shopID, itemID, priceType);
    // }
    // // public double getCachedRecipePrice(String shopID, String itemID, String priceType) {
    // //     String cacheKey = shopID + ":" + itemID + ":" + priceType;
    // //     return recipePriceCache.getOrDefault(cacheKey, -1.0); // Retourne -1.0 si le prix n'est pas en cache
    // // }
    // public int getCachedRecipeStock(String shopID, String itemID, String priceType) {
    //     return recipeCacheManager.getCachedRecipeStock(shopID, itemID, priceType);
    // }
    // // public int getCachedRecipeStock(String shopID, String itemID, String priceType) {
    // //     String cacheKey = shopID + ":" + itemID + ":" + priceType;
    // //     return priceStockCache.getOrDefault(cacheKey, -1); // Retourne -1 si le stock n'est pas en cache
    // // }
    
    // public void cacheRecipePrice(String shopID, String itemID, String priceType, double price) {
    //     recipeCacheManager.cacheRecipePrice(shopID, itemID, priceType, price);
    // }
    // public void cacheRecipeStock(String shopID, String itemID, String type, int stock) {
    //     recipeCacheManager.cacheRecipeStock(shopID, itemID, type, stock);
    // }

    // public void preloadPopularItems() {
    //     // Créer un pool de threads limité pour les précalculs
    //     ExecutorService precalcExecutor = Executors.newFixedThreadPool(3);
        
    //     for (String popularItemKey : configMain.getStringList("popular-items")) {
    //         String[] parts = popularItemKey.split(":");
    //         if (parts.length == 2) {
    //             final String shopID = parts[0];
    //             final String itemID = parts[1];
                
    //             // Soumettre la tâche au pool de threads
    //             precalcExecutor.submit(() -> {
    //                 try {
    //                     // Récupérer l'item de manière synchrone
    //                     ItemStack itemStack = Bukkit.getScheduler().callSyncMethod(this, () -> 
    //                         ShopGuiPlusApi.getShop(shopID).getShopItem(itemID).getItem()
    //                     ).get();
                        
    //                     if (itemStack != null) {
    //                         // Précalculer les prix et les mettre en cache
    //                         double buyPrice = priceRecipe.calculatePrice(shopID, itemID, itemStack, "buyPrice", new ArrayList<>());
    //                         double sellPrice = priceRecipe.calculatePrice(shopID, itemID, itemStack, "sellPrice", new ArrayList<>());
                            
    //                         cacheRecipePrice(shopID, itemID, "buyPrice", buyPrice);
    //                         cacheRecipePrice(shopID, itemID, "sellPrice", sellPrice);
                            
    //                         // logger.info("Precalculated prices for " + shopID + ":" + itemID + " - Buy: " + buyPrice + ", Sell: " + sellPrice);
    //                     }
    //                 } catch (Exception e) {
    //                     logger.warning("Erreur lors du précalcul des prix pour " + shopID + ":" + itemID + ": " + e.getMessage());
    //                 }
    //             });
    //         }
    //     }
        
    //     // Arrêter proprement le pool après les précalculs
    //     precalcExecutor.shutdown();
    // }
}