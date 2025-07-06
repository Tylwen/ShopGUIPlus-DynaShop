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
// import org.bukkit.event.HandlerList;
// import org.bukkit.event.HandlerList;
// import org.bukkit.configuration.ConfigurationSection;
// import org.bukkit.configuration.file.YamlConfiguration;
// import org.bukkit.event.EventHandler;
// import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
// import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import de.tr7zw.changeme.nbtapi.NBT;
import fr.tylwen.satyria.dynashop.command.DynaShopCommand;
// import fr.tylwen.satyria.dynashop.command.WebChartSubCommand;
// import fr.tylwen.satyria.dynashop.command.LimitResetCommand;
// import fr.tylwen.satyria.dynashop.command.ReloadSubCommand;
import fr.tylwen.satyria.dynashop.config.DataConfig;
import fr.tylwen.satyria.dynashop.config.LangConfig;
import fr.tylwen.satyria.dynashop.price.DynamicPrice;
// import fr.tylwen.satyria.dynashop.data.RecipeCacheManager;
// import fr.tylwen.satyria.dynashop.config.Config;
// import fr.tylwen.satyria.dynashop.config.Lang;
// import fr.tylwen.satyria.dynashop.config.Settings;
import fr.tylwen.satyria.dynashop.data.ShopConfigManager;
import fr.tylwen.satyria.dynashop.data.cache.CacheManager;
import fr.tylwen.satyria.dynashop.data.cache.LimitCacheEntry;
import fr.tylwen.satyria.dynashop.data.param.DynaShopType;
// import fr.tylwen.satyria.dynashop.data.storage.FlatFileDataManager;
import fr.tylwen.satyria.dynashop.data.storage.FlatFileStorageManager;
import fr.tylwen.satyria.dynashop.data.storage.MySQLStorageManager;
// import fr.tylwen.satyria.dynashop.data.storage.LimitNextAvailableTimeCacheDataManager;
// import fr.tylwen.satyria.dynashop.data.storage.LimitRemainingAmountCacheDataManager;
// import fr.tylwen.satyria.dynashop.data.storage.PriceDataManager;
// import fr.tylwen.satyria.dynashop.data.storage.StockDataManager;
import fr.tylwen.satyria.dynashop.data.storage.StorageManager;
import fr.tylwen.satyria.dynashop.data.storage.database.BatchDatabaseUpdater;
// import fr.tylwen.satyria.dynashop.discord.DiscordManager;
import fr.tylwen.satyria.dynashop.discord.DiscordSRVManager;
// import fr.tylwen.satyria.dynashop.database.DataManager;
// import fr.tylwen.satyria.dynashop.database.ItemDataManager;
// import fr.tylwen.satyria.dynashop.gui.ShopRefreshManager;
import fr.tylwen.satyria.dynashop.hook.DynaShopExpansion;
// import fr.tylwen.satyria.dynashop.hook.DynaShopItemProvider;
import fr.tylwen.satyria.dynashop.hook.ShopGUIPlusHook;
// import fr.tylwen.satyria.dynashop.hook.ShopItemProcessor;
import fr.tylwen.satyria.dynashop.listener.DynaShopListener;
// import fr.tylwen.satyria.dynashop.listener.MarketChartZoomListener;
import fr.tylwen.satyria.dynashop.listener.ShopItemPlaceholderListener;
import fr.tylwen.satyria.dynashop.price.PriceRecipe;
import fr.tylwen.satyria.dynashop.price.PriceStock;
import fr.tylwen.satyria.dynashop.system.InflationManager;
import fr.tylwen.satyria.dynashop.system.TaxService;
import fr.tylwen.satyria.dynashop.system.TransactionLimiter;
import fr.tylwen.satyria.dynashop.task.WaitForShopsTask;
import fr.tylwen.satyria.dynashop.utils.PriceFormatter;
import fr.tylwen.satyria.dynashop.web.MarketWebServer;
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
import java.util.UUID;
// import java.util.concurrent.CompletableFuture;
// import java.util.concurrent.ExecutionException;
// import java.util.Map;
// import java.util.concurrent.ConcurrentHashMap;
// import java.util.concurrent.ExecutorService;
// import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
// import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;
// import java.util.stream.Collectors;


// import javax.xml.crypto.Data;

public class DynaShopPlugin extends JavaPlugin {

    // private static DynaShopPlugin instance;
    // private ShopGUIPlusHook shopGUIPlusHook;
    // private YamlConfiguration configMain;
    // private YamlConfiguration configLang;
    // // private final Map<ShopItem, DynamicPrice> priceMap = new HashMap<>();

    // private DataManager dataManager;
    // private FlatFileDataManager flatFileManager;

    // private BatchDatabaseUpdater batchDatabaseUpdater;
    // private ShopConfigManager shopConfigManager;
    // private ItemDataManager itemDataManager;
    // private PriceRecipe priceRecipe;
    // private PriceStock priceStock;
    // private DataConfig dataConfig;
    // private LangConfig langConfig;
    // // private CustomRecipeManager customRecipeManager;
    // private Logger logger;
    // // private CommentedConfiguration config;
    // private DynaShopListener dynaShopListener;
    // private ShopItemPlaceholderListener shopItemPlaceholderListener;
    // private DynaShopExpansion placeholderExpansion;
    // private TransactionLimiter transactionLimiter;
    // private TaxService taxService;
    // private InflationManager inflationManager;
    // // private ShopRefreshManager shopRefreshManager;
    // // private ItemPacketInterceptor packetInterceptor;
    // private PriceFormatter priceFormatter;
    // // private CustomIngredientsManager customIngredientsManager;

    // private int dynamicPricesTaskId;
    // private int waitForShopsTaskId;
    
    // private MarketWebServer webServer;
    // private int webServerPort = 7070; // Port par défaut

    // // private RecipeCacheManager recipeCacheManager;
    
    // private String cacheMode;
    // private CacheManager<String, DynamicPrice> priceCache;
    // private CacheManager<String, List<ItemStack>> recipeCache;
    // private CacheManager<String, Double> calculatedPriceCache;
    // private CacheManager<String, Integer> stockCache;
    // private CacheManager<String, Map<String, String>> displayPriceCache;
    // private CacheManager<String, LimitCacheEntry> limitCache;
    // // private CacheManager<String, TransactionLimit> limitCache;
    // // private CacheManager<String, Integer> limitRemainingAmountCache;
    // // private CacheManager<String, Long> limitNextAvailableTimeCache;

    // // FLATFILES
    // private PriceDataManager priceDataManager;
    // private LimitDataManager limitDataManager;
    // private StockDataManager stockDataManager;
    // // private LimitRemainingAmountCacheDataManager limitRemainingAmountCacheDataManager;
    // // private LimitNextAvailableTimeCacheDataManager limitNextAvailableTimeCacheDataManager;

    // // public DynaShopPlugin() {
    // //     this.config = new CommentedConfiguration();
    // //     // this.configMain = new Config(this, "config.yml", "config.yml");
    // //     // this.configLang = new Config(this, "lang.yml", "lang.yml");
    // // }
    
    private static DynaShopPlugin instance;
    private ShopGUIPlusHook shopGUIPlusHook;
    private YamlConfiguration configMain;
    private YamlConfiguration configLang;

    // Gestionnaires de stockage unifiés
    private StorageManager storageManager;
    
    private BatchDatabaseUpdater batchDatabaseUpdater;
    private ShopConfigManager shopConfigManager;
    // private ItemDataManager itemDataManager;
    private PriceRecipe priceRecipe;
    private PriceStock priceStock;
    private DataConfig dataConfig;
    private LangConfig langConfig;
    private Logger logger;
    private DynaShopListener dynaShopListener;
    private ShopItemPlaceholderListener shopItemPlaceholderListener;
    private DynaShopExpansion placeholderExpansion;
    private TransactionLimiter transactionLimiter;
    private TaxService taxService;
    private InflationManager inflationManager;
    private PriceFormatter priceFormatter;

    private int dynamicPricesTaskId;
    private int waitForShopsTaskId;
    
    private MarketWebServer webServer;
    private int webServerPort = 7070; // Port par défaut
    
    // private DiscordManager discordManager;
    private DiscordSRVManager discordManager;
    
    private String cacheMode;
    private CacheManager<String, DynamicPrice> priceCache;
    private CacheManager<String, List<ItemStack>> recipeCache;
    private CacheManager<String, Double> calculatedPriceCache;
    private CacheManager<String, Integer> stockCache;
    private CacheManager<String, Map<String, String>> displayPriceCache;
    private CacheManager<String, LimitCacheEntry> limitCache;
    
    // // Pour la compatibilité avec l'ancien système
    // private DataManager dataManager;
    // private FlatFileStorageManager flatFileManager;

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

    // public DataManager getDataManager() {
    //     return this.dataManager;
    // }
    
    // public ItemDataManager getItemDataManager() {
    //     return this.itemDataManager;
    // }

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

    public TaxService getTaxService() {
        return this.taxService;
    }

    public InflationManager getInflationManager() {
        return this.inflationManager;
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
    
    public MarketWebServer getWebServer() {
        return webServer;
    }
    
    // public DiscordManager getDiscordManager() {
    //     return discordManager;
    // }
    public DiscordSRVManager getDiscordManager() {
        return discordManager;
    }

    // FLATFILES
    // public FlatFileDataManager getFlatFileManager() {
    //     return this.flatFileManager;
    // }

    // public PriceDataManager getPriceDataManager() {
    //     return this.priceDataManager;
    // }

    // public LimitDataManager getLimitDataManager() {
    //     return this.limitDataManager;
    // }

    // public StockDataManager getStockDataManager() {
    //     return this.stockDataManager;
    // }

    public StorageManager getStorageManager() {
        return this.storageManager;
    }

    // Cache getters

    public static String getLimitCacheKey(UUID playerUuid, String shopId, String itemId, boolean isBuy) {
        return playerUuid + ":" + shopId + ":" + itemId + ":" + (isBuy ? "buy" : "sell");
    }


    @Override
    public void onEnable() {
        instance = this;
        this.logger = getLogger();
        init();
        initCache();
        // load();
        initTranslation();
        hookIntoShopGUIPlus();

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            this.placeholderExpansion = new DynaShopExpansion(this);
            this.placeholderExpansion.register();
            getLogger().info("Placeholders enregistrés avec PlaceholderAPI !");
        }
        // if (!NBT.preloadApi()) {
        //     getLogger().warning("NBT-API wasn't initialized properly, disabling the plugin");
        //     getPluginLoader().disablePlugin(this);
        //     return;
        // }
        if (getServer().getPluginManager().getPlugin("NBTAPI") == null) {
            getLogger().warning("NBT-API n'est pas installé ou n'est pas chargé. Le plugin DynaShop ne peut pas fonctionner sans NBT-API.");
            getPluginLoader().disablePlugin(this);
            return;
        }
        
        // // Initialiser le stockage
        storageManager.initialize();
        
        // // Attendre un peu que l'initialisation soit terminée avant d'utiliser les données
        // getServer().getScheduler().runTaskLater(this, () -> {
        //     // Initialiser les autres composants qui dépendent des données
        //     this.batchDatabaseUpdater = new BatchDatabaseUpdater(this);
        //     this.transactionLimiter = new TransactionLimiter(this);
            
        //     // Maintenant que les données sont chargées, démarrer le serveur web
        //     if (getConfig().getBoolean("web-dashboard.enabled", false)) {
        //         webServerPort = getConfig().getInt("web-dashboard.port", webServerPort);
        //         webServer = new MarketWebServer(this, webServerPort);
        //         webServer.start();
        //         getLogger().info("Dashboard web démarré sur le port " + webServerPort);
        //     }
            
        //     // Enregistrer les écouteurs d'événements
        //     getServer().getPluginManager().registerEvents(this.dynaShopListener, this);
        //     getServer().getPluginManager().registerEvents(this.shopItemPlaceholderListener, this);
        // }, 40); // Attendre 2 secondes (40 ticks)
        
        // // // Initialiser le serveur web si activé
        // // if (getConfig().getBoolean("web-dashboard.enabled", false)) {
        // //     webServerPort = getConfig().getInt("web-dashboard.port", 7070);
        // //     webServer = new MarketWebServer(this, webServerPort);
        // //     webServer.start();
            
        // //     getLogger().info("Dashboard web démarré sur le port " + webServerPort);
        // // }
        
        // Le reste du code d'initialisation (après le chargement complet du stockage)
        this.batchDatabaseUpdater = new BatchDatabaseUpdater(this);
        this.transactionLimiter = new TransactionLimiter(this);

        // getServer().getPluginManager().registerEvents(new DynaShopListener(this), this);
        getServer().getPluginManager().registerEvents(this.dynaShopListener, this);
        getServer().getPluginManager().registerEvents(this.shopItemPlaceholderListener, this);
        
        // Maintenant que les données sont chargées, démarrer le serveur web
        boolean webServerEnabled = getConfig().getBoolean("web-dashboard.enabled", false);
        if (webServerEnabled) {
            webServerPort = getConfig().getInt("web-dashboard.port", 7070);
            webServer = new MarketWebServer(this, webServerPort);
            webServer.start();
            info("Dashboard web démarré sur le port " + webServerPort);
        }
        
        // // Initialiser Discord (après tout le reste)
        // if (getConfigMain().getBoolean("discord.enabled", false)) {
        //     this.discordManager = new DiscordManager(this);
        //     this.discordManager.initialize();
        // }
        // Initialiser DiscordSRV (après tout le reste)
        if (getConfigMain().getBoolean("discord.enabled", false)) {
            this.discordManager = new DiscordSRVManager(this);
            this.discordManager.initialize();
        }

        getCommand("dynashop").setExecutor(new DynaShopCommand(this));

        // Utiliser un executor service commun avec un nombre limité de threads
        // ExecutorService sharedExecutor = Executors.newFixedThreadPool(3);
        // Planification des tâches
        // getServer().getScheduler().runTaskTimerAsynchronously(this, new ReloadDatabaseTask(this), 0L, 20L * 60L * 10L); // Toutes les 10 minutes
        // this.waitForShopsTaskId = getServer().getScheduler().runTaskTimer(this, new WaitForShopsTask(this), 0L, 20L * 5L).getTaskId(); // Toutes les 5 secondes
        WaitForShopsTask waitTask = new WaitForShopsTask(this);
        BukkitTask task = getServer().getScheduler().runTaskTimer(this, waitTask, 0L, 20L * 5L);
        waitTask.setSelfTask(task);
        // setupMetrics();

        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
                // Purger l'historique plus vieux que 30 jours (configurable)
                int daysToKeep = getConfig().getInt("history.retention-days", 30);
                storageManager.purgeOldPriceHistory(daysToKeep);
            },
            20L * 60L * 60L * 3L,  // Délai initial: 3 heures après le démarrage
            20L * 60L * 60L * 24L  // Exécution toutes les 24 heures
        );

        Bukkit.getScheduler().runTaskTimerAsynchronously(this, this::flushAllCachesToStorage, 6000L, 6000L); // Toutes les 5 minutes (6000 ticks)

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
        this.limitCache = new CacheManager<>(this, "limitCache", 5, TimeUnit.MINUTES, 10);
        // this.limitRemainingAmountCache = new CacheManager<>(this, "limitRemainingAmountCache", 5, TimeUnit.MINUTES, 10);
        // this.limitNextAvailableTimeCache = new CacheManager<>(this, "limitNextAvailableTimeCache", 5, TimeUnit.MINUTES, 10);

        // this.shopConfigManager = new ShopConfigManager(new File(Bukkit.getPluginManager().getPlugin("ShopGUIPlus").getDataFolder(), "shops/"));
        // this.shopConfigManager = new ShopConfigManager(new File(ShopGuiPlusApi.getPlugin().getConfigShops().getConfig().getCurrentPath(), "shops/"));
        this.shopConfigManager = new ShopConfigManager(this);
        this.dataConfig = new DataConfig(this.configMain);
        this.langConfig = new LangConfig(this.configLang);

        // this.customRecipeManager = new CustomRecipeManager(this);
        this.dynaShopListener = new DynaShopListener(this);
        this.shopItemPlaceholderListener = new ShopItemPlaceholderListener(this);
        this.priceRecipe = new PriceRecipe(this);
        this.priceStock = new PriceStock(this);

        // Initialiser le gestionnaire de stockage approprié
        if (dataConfig.getDatabaseType().equalsIgnoreCase("flatfile")) {
            // Initialiser le gestionnaire de fichiers plats
            // this.flatFileManager = new FlatFileStorageManager(this);
            this.storageManager = new FlatFileStorageManager(this);
            
            // // Pour la compatibilité avec l'ancien code
            // this.dataManager = new DataManager(this);
        } else {
            // Initialiser le gestionnaire MySQL
            this.storageManager = new MySQLStorageManager(this);
            
            // // Pour la compatibilité avec l'ancien code
            // this.dataManager = new DataManager(this);
            // this.flatFileManager = null;
        }

        // this.itemDataManager = new ItemDataManager(this.dataManager);
        // this.batchDatabaseUpdater = new BatchDatabaseUpdater(this);
        // this.transactionLimiter = new TransactionLimiter(this);
        this.taxService = new TaxService(this);
        this.inflationManager = new InflationManager(this);
        // this.recipeCacheManager = new RecipeCacheManager(15 * 60 * 1000L); // 15 minutes en ms
        this.priceFormatter = new PriceFormatter(this);
        // this.customIngredientsManager = new CustomIngredientsManager();
        // this.shopRefreshManager = new ShopRefreshManager(this);
        // preloadPopularItems();

        // // Initialiser les FLATFILES pour les données
        // File dataDir = new File(getDataFolder(), "data");
        // if (!dataDir.exists()) {
        //     dataDir.mkdirs();
        // }
        // this.priceDataManager = new PriceDataManager(new File(dataDir, "prices.json"));
        // this.limitDataManager = new LimitDataManager(new File(dataDir, "limits.json"));
        // this.stockDataManager = new StockDataManager(new File(dataDir, "stocks.json"));
        // // this.limitRemainingAmountCacheDataManager = new LimitRemainingAmountCacheDataManager(new File(getDataFolder(), "limit_remaining_amount.json"));
        // // this.limitNextAvailableTimeCacheDataManager = new LimitNextAvailableTimeCacheDataManager(new File(getDataFolder(), "limit_next_available_time.json"));
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
        int limitDuration = configMain.getInt("cache.durations.limit", 30); // Durée pour le cache des limites
        // int limitRemainingAmountDuration = configMain.getInt("cache.durations.limit", 30); // Durée pour le cache des limites
        // int limitNextAvailableTimeDuration = configMain.getInt("cache.durations.limit", 30); // Durée pour le cache des temps d'attente
        
        // Initialiser les caches avec ces durées
        if (cacheMode.equalsIgnoreCase("realtime")) {
            // En mode "realtime", on utilise des durées plus courtes
            priceDuration = 5;
            displayDuration = 1;
            // recipeDuration = 10;
            stockDuration = 5;
            calculatedDuration = 10;
            limitDuration = 5; // Durée pour le cache des limites
            // limitRemainingAmountDuration = 5; // Durée pour le cache des limites
            // limitNextAvailableTimeDuration = 5; // Durée pour le cache des temps d'attente
        }
        priceCache = new CacheManager<>(this, "PriceCache", priceDuration, TimeUnit.SECONDS, 10);
        recipeCache = new CacheManager<>(this, "RecipeCache", recipeDuration, TimeUnit.SECONDS, 5);
        calculatedPriceCache = new CacheManager<>(this, "CalculatedPriceCache", calculatedDuration, TimeUnit.SECONDS, 5);
        stockCache = new CacheManager<>(this, "StockCache", stockDuration, TimeUnit.SECONDS, 10);
        displayPriceCache = new CacheManager<>(this, "DisplayPriceCache", displayDuration, TimeUnit.SECONDS, 15);
        limitCache = new CacheManager<>(this, "LimitCache", limitDuration, TimeUnit.SECONDS, 10);
        // limitRemainingAmountCache = new CacheManager<>(this, "LimitRemainingAmountCache", limitRemainingAmountDuration, TimeUnit.SECONDS, 10);
        // limitNextAvailableTimeCache = new CacheManager<>(this, "LimitNextAvailableTimeCache", limitNextAvailableTimeDuration, TimeUnit.SECONDS, 10);

        // initTranslation();
        // MarketChartRenderer.clearMapCache();

        // // Init des caches FLATFILES
        // this.priceDataManager.load();
        // for (Map.Entry<String, DynamicPrice> entry : priceDataManager.getAll().entrySet()) {
        //     priceCache.put(entry.getKey(), entry.getValue());
        // }
        // this.limitDataManager.load();
        // for (Map.Entry<String, LimitCacheEntry> entry : limitDataManager.getAll().entrySet()) {
        //     limitCache.put(entry.getKey(), entry.getValue());
        // }
        // // for (Map.Entry<String, TransactionLimit> entry : limitDataManager.getAll().entrySet()) {
        // //     limitCache.put(entry.getKey(), entry.getValue());
        // // }
        // this.stockDataManager.load();
        // for (Map.Entry<String, Integer> entry : stockDataManager.getAll().entrySet()) {
        //     stockCache.put(entry.getKey(), entry.getValue());
        // }
        // this.limitRemainingAmountCacheDataManager.load();
        // for (Map.Entry<String, Integer> entry : limitRemainingAmountCacheDataManager.getAll().entrySet()) {
        //     limitRemainingAmountCache.put(entry.getKey(), entry.getValue());
        // }
        // this.limitNextAvailableTimeCacheDataManager.load();
        // for (Map.Entry<String, Long> entry : limitNextAvailableTimeCacheDataManager.getAll().entrySet()) {
        //     limitNextAvailableTimeCache.put(entry.getKey(), entry.getValue());
        // }
        // Charger les données de stockage dans le cache
        // loadDataFromStorageToCache();
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
    
    public CacheManager<String, LimitCacheEntry> getLimitCache() {
        return limitCache;
    }

    // public CacheManager<String, TransactionLimit> getLimitCache() {
    //     return limitCache;
    // }

    // public CacheManager<String, Integer> getLimitRemainingAmountCache() {
    //     return limitRemainingAmountCache;
    // }

    // public CacheManager<String, Long> getLimitNextAvailableTimeCache() {
    //     return limitNextAvailableTimeCache;
    // }

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
        limitCache.invalidateWithPrefix(baseKey);
        stockCache.invalidateWithPrefix(baseKey);
        
        calculatedPriceCache.invalidateWithPrefix("progressive_");
        
        // Si c'est une recette, invalider le cache des ingrédients aussi
        if (getShopConfigManager().getTypeDynaShop(shopId, itemId) == DynaShopType.RECIPE) {
            recipeCache.invalidate(baseKey);
        }

        debug("Cache invalidé pour " + baseKey);
    }

    public void load() {
        generateFiles();
        // this.priceRecipe = new PriceRecipe(this.configMain);
        this.dataConfig = new DataConfig(this.configMain);
        this.langConfig = new LangConfig(this.configLang);
        reloadConfigAndCacheMode();
        if (this.taxService != null) {
            this.taxService.loadConfig();
        }
        // initTranslation();
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

    public void initTranslation() {
        File translationsDir = new File(getDataFolder(), "translations");
        if (!translationsDir.exists()) {
            translationsDir.mkdirs();
            // Optionnel : copier un exemple de base
            saveResource("translations/translations_en.yml", false);
            saveResource("translations/translations_fr.yml", false);
        }

        File[] files = translationsDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                String fileName = file.getName(); // ex: translations_fr.yml
                // Extraire la locale (ex: "fr" dans "translations_fr.yml")
                String locale = "default";
                if (fileName.startsWith("translations_") && fileName.endsWith(".yml")) {
                    locale = fileName.substring("translations_".length(), fileName.length() - 4);
                } else {
                    // fallback: nom sans extension
                    locale = fileName.replace(".yml", "");
                }
                info("fileName" + fileName + " locale: " + locale);
                getShopConfigManager().loadTranslationFile(locale, "translations/" + fileName);
            }
        }
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

    public void reloadData() {
        // IMPORTANT: Sauvegarder toutes les données en cache avant de fermer le stockage
        if (storageManager != null) {
            flushAllCachesToStorage();
        }

        // Arrêter les threads liés à la base
        if (batchDatabaseUpdater != null) batchDatabaseUpdater.shutdown();
        if (storageManager != null) storageManager.shutdown();

        // Réinitialiser les objets liés à la base
        if (dataConfig.getDatabaseType().equalsIgnoreCase("flatfile")) {
            this.storageManager = new FlatFileStorageManager(this);
        } else {
            this.storageManager = new MySQLStorageManager(this);
        }

        // this.itemDataManager = new ItemDataManager(storageManager);
        this.batchDatabaseUpdater = new BatchDatabaseUpdater(this);
        this.transactionLimiter = new TransactionLimiter(this);

        // Réinitialiser les caches
        initCache();

        // Initialiser le stockage
        storageManager.initialize();

        getShopConfigManager().initPricesFromShopConfigs();
        getStorageManager().cleanupStockTable();


        getLogger().info("DynaShop database reloaded successfully.");
    }

    /**
     * Synchronise les données du cache avec le stockage persistant
     */
    public void flushAllCachesToStorage() {
        getLogger().fine("Sauvegarde périodique du cache vers le stockage...");
        
        // Synchroniser les prix
        for (String key : priceCache.keySet()) {
            DynamicPrice price = priceCache.getIfPresent(key);
            if (price != null) {
                String[] parts = key.split(":");
                if (parts.length == 2) {
                    String shopId = parts[0];
                    String itemId = parts[1];
                    
                    // Sauvegarder le prix
                    storageManager.savePrice(shopId, itemId, 
                                           price.getBuyPrice(), 
                                           price.getSellPrice(), 
                                           price.getStock());
                }
            }
        }
        
        // Les stocks sont inclus dans les prix, pas besoin de les sauvegarder séparément
        
        // Les limites sont gérées automatiquement par le TransactionLimiter
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
        
        // Arrêter le gestionnaire de mises à jour en batch
        if (batchDatabaseUpdater != null) {
            batchDatabaseUpdater.shutdown();
        }
        
        // Arrêter le TransactionLimiter (flush + arrêt thread)
        if (transactionLimiter != null) {
            transactionLimiter.shutdown();
        }
        
        if (shopItemPlaceholderListener != null) {
            shopItemPlaceholderListener.shutdown();
        }
        
        // Arrêter le serveur web si actif
        if (webServer != null) {
            webServer.stop();
        }
        
        // Arrêter Discord
        if (discordManager != null) {
            discordManager.shutdown();
        }
        
        // Fermer proprement le système de stockage
        if (storageManager != null) {
            // Synchroniser les caches avant de fermer
            flushAllCachesToStorage();
            
            // Fermer le système de stockage
            storageManager.shutdown();
        }
        
        // Nettoyer les caches
        priceCache.clear();
        recipeCache.clear();
        calculatedPriceCache.clear();
        stockCache.clear();
        displayPriceCache.clear();
        limitCache.clear();
    }

    private void hookIntoShopGUIPlus() {
        if (Bukkit.getPluginManager().getPlugin("ShopGUIPlus") != null) {
            this.shopGUIPlusHook = new ShopGUIPlusHook(this);
            Bukkit.getPluginManager().registerEvents(shopGUIPlusHook, this);

            this.getLogger().info("ShopGUI+ detected.");
        } else {
            this.getLogger().warning("ShopGUI+ not found.");
        }
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

    public void debug(String string) {
        if (getConfigMain().getBoolean("debug", false)) {
            this.logger.fine(string);
        }
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