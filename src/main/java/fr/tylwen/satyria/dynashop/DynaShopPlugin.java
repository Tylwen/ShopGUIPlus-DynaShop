package fr.tylwen.satyria.dynashop;

// import net.brcdev.shopgui.ShopGuiPlusApi;
// import net.brcdev.shopgui.config.Lang;
// import net.brcdev.shopgui.event.ShopPreTransactionEvent;
// import net.brcdev.shopgui.api.events.ShopTransactionEvent;
// import net.brcdev.shopgui.shop.Shop;
// import net.brcdev.shopgui.shop.ShopManager.ShopAction;
// import net.brcdev.shopgui.gui.gui.OpenGui;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
// import org.bukkit.configuration.ConfigurationSection;
// import org.bukkit.configuration.file.YamlConfiguration;
// import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
// import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import fr.tylwen.satyria.dynashop.command.DynaShopCommand;
import fr.tylwen.satyria.dynashop.command.ReloadCommand;
import fr.tylwen.satyria.dynashop.config.DataConfig;
import fr.tylwen.satyria.dynashop.config.LangConfig;
import fr.tylwen.satyria.dynashop.data.CustomRecipeManager;
import fr.tylwen.satyria.dynashop.data.DynamicPrice;
import fr.tylwen.satyria.dynashop.data.PriceRecipe;
import fr.tylwen.satyria.dynashop.data.PriceStock;
// import fr.tylwen.satyria.dynashop.config.Config;
// import fr.tylwen.satyria.dynashop.config.Lang;
// import fr.tylwen.satyria.dynashop.config.Settings;
import fr.tylwen.satyria.dynashop.data.ShopConfigManager;
import fr.tylwen.satyria.dynashop.database.BatchDatabaseUpdater;
import fr.tylwen.satyria.dynashop.database.DataManager;
import fr.tylwen.satyria.dynashop.database.ItemDataManager;
import fr.tylwen.satyria.dynashop.hook.DynaShopExpansion;
import fr.tylwen.satyria.dynashop.hook.ShopGUIPlusHook;
// import fr.tylwen.satyria.dynashop.hook.ShopItemProcessor;
import fr.tylwen.satyria.dynashop.listener.DynaShopListener;
import fr.tylwen.satyria.dynashop.listener.ShopItemPlaceholderListener;
// import fr.tylwen.satyria.dynashop.utils.CommentedConfiguration;
import fr.tylwen.satyria.dynashop.task.ReloadDatabaseTask;
import fr.tylwen.satyria.dynashop.task.DynamicPricesTask;
import fr.tylwen.satyria.dynashop.task.WaitForShopsTask;
import net.brcdev.shopgui.ShopGuiPlugin;
import net.brcdev.shopgui.ShopGuiPlusApi;
import net.brcdev.shopgui.shop.item.ShopItem;

import java.io.File;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;


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
    private CustomRecipeManager customRecipeManager;
    private Logger logger;
    // private CommentedConfiguration config;
    private DynaShopListener dynaShopListener;
    private ShopItemPlaceholderListener shopItemPlaceholderListener;
    private DynaShopExpansion placeholderExpansion;

    private int dynamicPricesTaskId;
    private int waitForShopsTaskId;

    private final Map<String, Double> recipePriceCache = new ConcurrentHashMap<>();
    // private final long RECIPE_CACHE_DURATION = 600000; // 10 minutes
    private final long RECIPE_CACHE_DURATION = 20L * 60L * 15L; // 10 minutes in ticks
    private final Map<String, Long> recipeCacheTimestamps = new ConcurrentHashMap<>();

    // public DynaShopPlugin() {
    //     this.config = new CommentedConfiguration();
    //     // this.configMain = new Config(this, "config.yml", "config.yml");
    //     // this.configLang = new Config(this, "lang.yml", "lang.yml");
    // }

    public static DynaShopPlugin getInstance() {
        return instance;
    }

    public DynaShopListener getDynaShopListener() {
        return dynaShopListener;
    }

    public ShopItemPlaceholderListener getShopItemPlaceholderListener() {
        return shopItemPlaceholderListener;
    }

    public YamlConfiguration getConfigMain() {
        return this.configMain;
    }
    public YamlConfiguration getConfigLang() {
        return this.configLang;
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

    public CustomRecipeManager getCustomRecipeManager() {
        return this.customRecipeManager;
    }

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
    
    // Getter pour l'expansion PlaceholderAPI
    public DynaShopExpansion getPlaceholderExpansion() {
        return placeholderExpansion;
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
        // load();
        hookIntoShopGUIPlus();
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            // new DynaShopExpansion(this.itemDataManager, this.shopConfigManager, this.priceRecipe).register();
            // new DynaShopExpansion(this).register();
            this.placeholderExpansion = new DynaShopExpansion(this);
            this.placeholderExpansion.register();
            getLogger().info("Placeholders enregistrés avec PlaceholderAPI !");
        }

        getServer().getPluginManager().registerEvents(new DynaShopListener(this), this);
        // Initialiser le listener avant de l'utiliser ailleurs
        this.shopItemPlaceholderListener = new ShopItemPlaceholderListener(this);
        getServer().getPluginManager().registerEvents(this.shopItemPlaceholderListener, this);

        getCommand("dynashop").setExecutor(new DynaShopCommand(this));
        getCommand("dynashop").setExecutor(new ReloadCommand(this));
        // getCommand("dynashop").setTabCompleter(new ReloadCommand(this));

        this.dataManager.initDatabase();

        // Planification des tâches
        getServer().getScheduler().runTaskTimerAsynchronously(this, new ReloadDatabaseTask(this), 0L, 20L * 60L * 10L); // Toutes les 10 minutes
        getServer().getScheduler().runTaskTimer(this, new WaitForShopsTask(this), 0L, 20L * 5L); // Toutes les 5 secondes
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
        // Planifier la précharge périodique des items populaires
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, 
            this::preloadPopularItems, 
            20L * 60,         // Démarrer après 1 minute
            20L * 60 * 15);   // Répéter toutes les 15 minutes

        // Ajouter une tâche de nettoyage du cache
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            long currentTime = System.currentTimeMillis();
            recipePriceCache.entrySet().removeIf(entry -> 
                currentTime - recipeCacheTimestamps.getOrDefault(entry.getKey(), 0L) > RECIPE_CACHE_DURATION);
        }, 20L * 60L * 5L, 20L * 60L * 5L); // Nettoyage toutes les 5 minutes

        getLogger().info("DynaShop activé avec succès !");
    }

    private void init() {
        generateFiles();
        this.shopConfigManager = new ShopConfigManager(new File(Bukkit.getPluginManager().getPlugin("ShopGUIPlus").getDataFolder(), "shops/"));
        this.priceRecipe = new PriceRecipe(this.configMain);
        this.dataConfig = new DataConfig(this.configMain);
        this.priceStock = new PriceStock(this);
        this.dataManager = new DataManager(this);
        this.itemDataManager = new ItemDataManager(this.dataManager);
        this.batchDatabaseUpdater = new BatchDatabaseUpdater(this);
        // preloadPopularItems();
    }

    // private void load() {
    //     generateFiles();
    //     this.priceRecipe = new PriceRecipe(this.configMain);
    //     this.dataConfig = new DataConfig(this.configMain);
    //     // Lang.setConfig(this.configLang);
    //     // Settings.setConfig(this.configMain);
    //     // Settings.load();
    // }

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

    @Override
    public void onDisable() {
        // Annuler explicitement les tâches
        if (dynamicPricesTaskId != 0) {
            getServer().getScheduler().cancelTask(dynamicPricesTaskId);
            getLogger().info("Tâche DynamicPricesTask annulée (ID: " + dynamicPricesTaskId + ")");
        }
        
        // Arrêter le gestionnaire de mises à jour en batch
        if (batchDatabaseUpdater != null) {
            batchDatabaseUpdater.shutdown();
        }
        
        // dataManager.savePricesToDatabase(priceMap);
        dataManager.closeDatabase();
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

    public double getCachedRecipePrice(String shopID, String itemID, String priceType) {
        String cacheKey = shopID + ":" + itemID + ":" + priceType;
        
        if (recipePriceCache.containsKey(cacheKey) && 
            System.currentTimeMillis() - recipeCacheTimestamps.getOrDefault(cacheKey, 0L) < RECIPE_CACHE_DURATION) {
            return recipePriceCache.get(cacheKey);
        }
        
        return -1.0; // Indique que le prix n'est pas en cache
    }
    
    public void cacheRecipePrice(String shopID, String itemID, String priceType, double price) {
        String cacheKey = shopID + ":" + itemID + ":" + priceType;
        recipePriceCache.put(cacheKey, price);
        recipeCacheTimestamps.put(cacheKey, System.currentTimeMillis());
    }

    public void preloadPopularItems() {
        // Créer un pool de threads limité pour les précalculs
        ExecutorService precalcExecutor = Executors.newFixedThreadPool(3);
        
        for (String popularItemKey : configMain.getStringList("popular-items")) {
            String[] parts = popularItemKey.split(":");
            if (parts.length == 2) {
                final String shopID = parts[0];
                final String itemID = parts[1];
                
                // Soumettre la tâche au pool de threads
                precalcExecutor.submit(() -> {
                    try {
                        // Récupérer l'item de manière synchrone
                        ItemStack itemStack = Bukkit.getScheduler().callSyncMethod(this, () -> 
                            ShopGuiPlusApi.getShop(shopID).getShopItem(itemID).getItem()
                        ).get();
                        
                        if (itemStack != null) {
                            // Précalculer les prix et les mettre en cache
                            double buyPrice = priceRecipe.calculatePrice(shopID, itemID, itemStack, "buyPrice", new ArrayList<>());
                            double sellPrice = priceRecipe.calculatePrice(shopID, itemID, itemStack, "sellPrice", new ArrayList<>());
                            
                            cacheRecipePrice(shopID, itemID, "buyPrice", buyPrice);
                            cacheRecipePrice(shopID, itemID, "sellPrice", sellPrice);
                            
                            logger.info("Precalculated prices for " + shopID + ":" + itemID + " - Buy: " + buyPrice + ", Sell: " + sellPrice);
                        }
                    } catch (Exception e) {
                        logger.warning("Erreur lors du précalcul des prix pour " + shopID + ":" + itemID + ": " + e.getMessage());
                    }
                });
            }
        }
        
        // Arrêter proprement le pool après les précalculs
        precalcExecutor.shutdown();
    }
} 