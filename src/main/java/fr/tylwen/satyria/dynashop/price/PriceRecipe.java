package fr.tylwen.satyria.dynashop.price;

import org.bukkit.Bukkit;
import org.bukkit.Material;
// import org.bukkit.block.BlastFurnace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
// import org.bukkit.inventory.BlastingRecipe;
// import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
// import org.bukkit.inventory.Recipe;
// import org.bukkit.inventory.ShapedRecipe;
// import org.bukkit.inventory.ShapelessRecipe;
// import org.bukkit.inventory.meta.ItemMeta;
// import org.bukkit.inventory.SmokingRecipe;
// import org.bukkit.inventory.StonecuttingRecipe;
// import org.bukkit.inventory.CampfireRecipe;
// import org.bukkit.inventory.BrewerInventory;
import org.bukkit.util.Consumer;

import fr.tylwen.satyria.dynashop.DynaShopPlugin;
import fr.tylwen.satyria.dynashop.data.cache.CacheManager;
import fr.tylwen.satyria.dynashop.data.param.DynaShopType;
import fr.tylwen.satyria.dynashop.data.param.RecipeType;
import fr.tylwen.satyria.dynashop.utils.PrioritizedRunnable;
import net.brcdev.shopgui.ShopGuiPlusApi;
import net.brcdev.shopgui.shop.Shop;
import net.brcdev.shopgui.shop.item.ShopItem;

import java.util.ArrayList;
// import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
// import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
// import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.Comparator;

public class PriceRecipe {
    private final DynaShopPlugin plugin;
    private final FileConfiguration config;
    
    // // Ajouter ces champs à la classe PriceRecipe
    // private final Map<String, List<ItemStack>> ingredientsCache = new HashMap<>();
    // private final long CACHE_DURATION = 20L * 60L * 5L; // 5 minutes
    // // private final long CACHE_DURATION = 20L; // 1 seconde
    // private final Map<String, Long> cacheTimestamps = new HashMap<>();

    private final ExecutorService highPriorityExecutor;
    private final Map<String, Integer> itemAccessCounter = new ConcurrentHashMap<>();
    private final List<String> popularItems = new ArrayList<>();
    private static final int POPULAR_THRESHOLD = 10;
    
    // Limiter la profondeur de récursion pour éviter les boucles infinies
    // private static final int MAX_RECURSION_DEPTH = 5;
    // Pool de threads limité pour les calculs asynchrones
    private final ExecutorService recipeExecutor;
    
    private final CacheManager<String, List<ItemStack>> recipeCache;
    
    private final Map<String, Map<Integer, Double>> enchantMultipliers = new HashMap<>();

    // public PriceRecipe(FileConfiguration config) {
    //     this.config = config;
        
    //     // Créer un pool de threads dédié et limité pour les calculs de recettes
    //     this.recipeExecutor = Executors.newFixedThreadPool(2, r -> {
    //         Thread thread = new Thread(r, "Recipe-Calculator");
    //         thread.setDaemon(true);
    //         return thread;
    //     });
        
    //     // Créer un pool de threads prioritaire pour les items populaires
    //     this.highPriorityExecutor = Executors.newFixedThreadPool(1, r -> {
    //         Thread thread = new Thread(r, "High-Priority-Calculator");
    //         thread.setDaemon(true);
    //         thread.setPriority(Thread.MAX_PRIORITY);
    //         return thread;
    //     });
        
    //     // Charger les items populaires depuis la configuration
    //     this.loadPopularItems();
    // }
    // public PriceRecipe(FileConfiguration config) {
    //     this.config = config;
    public PriceRecipe(DynaShopPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
        
        // Obtenir la référence au cache centralisé
        this.recipeCache = plugin.getRecipeCache();
        
        // ThreadFactory optimisé pour les recettes
        ThreadFactory recipeThreadFactory = r -> {
            Thread thread = new Thread(r, "Recipe-Calculator");
            thread.setDaemon(true);
            return thread;
        };
        
        // Utiliser un ThreadPoolExecutor configurable au lieu de newFixedThreadPool
        this.recipeExecutor = new ThreadPoolExecutor(
            2, // corePoolSize - threads toujours actifs
            4, // maximumPoolSize - taille maximale lors des pics
            60L, TimeUnit.SECONDS, // temps avant de libérer un thread au-delà du corePoolSize
            new LinkedBlockingQueue<>(), // file d'attente standard
            recipeThreadFactory,
            new ThreadPoolExecutor.CallerRunsPolicy() // stratégie pour gérer la surcharge
        );
        
        // ThreadFactory optimisé pour les tâches prioritaires
        ThreadFactory priorityThreadFactory = r -> {
            Thread thread = new Thread(r, "High-Priority-Calculator");
            thread.setDaemon(true);
            thread.setPriority(Thread.MAX_PRIORITY);
            return thread;
        };
        
        // Pool de haute priorité avec file d'attente limitée
        this.highPriorityExecutor = new ThreadPoolExecutor(
            1, 2, 
            60L, TimeUnit.SECONDS,
            new PriorityBlockingQueue<>(10, 
                Comparator.comparing(r -> ((PrioritizedRunnable)r).getPriority())
            ),
            priorityThreadFactory
        );
        
        this.loadEnchantMultipliers();
        
        // Charger les items populaires depuis la configuration
        this.loadPopularItems();
    }

    /**
     * Classe représentant le résultat complet d'un calcul de recette
     */
    public class RecipeCalculationResult {
        private double buyPrice;
        private double sellPrice;
        private double minBuyPrice;
        private double maxBuyPrice;
        private double minSellPrice;
        private double maxSellPrice;
        private int stock;
        private int minStock;
        private int maxStock;
        
        // Constructeur, getters et setters...
        
        public RecipeCalculationResult(double buyPrice, double sellPrice, 
                                    double minBuyPrice, double maxBuyPrice,
                                    double minSellPrice, double maxSellPrice,
                                    int stock, int minStock, int maxStock) {
            this.buyPrice = buyPrice;
            this.sellPrice = sellPrice;
            this.minBuyPrice = minBuyPrice;
            this.maxBuyPrice = maxBuyPrice;
            this.minSellPrice = minSellPrice;
            this.maxSellPrice = maxSellPrice;
            this.stock = stock;
            this.minStock = minStock;
            this.maxStock = maxStock;
        }
        
        // Getters...
        public double getBuyPrice() { return buyPrice; }
        public double getSellPrice() { return sellPrice; }
        public double getMinBuyPrice() { return minBuyPrice; }
        public double getMaxBuyPrice() { return maxBuyPrice; }
        public double getMinSellPrice() { return minSellPrice; }
        public double getMaxSellPrice() { return maxSellPrice; }
        public int getStock() { return stock; }
        public int getMinStock() { return minStock; }
        public int getMaxStock() { return maxStock; }
    }

    // public RecipeCalculationResult calculateRecipeValues(String shopID, String itemID, Set<String> visitedItems) {
    public RecipeCalculationResult calculateRecipeValues(String shopID, String itemID, Set<String> visitedItems, Map<String, DynamicPrice> lastResults) {
        // Récupérer tous les ingrédients une seule fois
        List<ItemStack> ingredients = getIngredients(shopID, itemID);
        ingredients = consolidateIngredients(ingredients);
        // for (ItemStack ingredient : ingredients) {
        //     plugin.getLogger().info("Ingrédient: " + ingredient);
        // }
        
        // Variables pour le calcul
        double basePrice = 0.0;
        double baseSellPrice = 0.0;
        double baseMinBuyPrice = 0.0;
        double baseMaxBuyPrice = 0.0;
        double baseMinSellPrice = 0.0;
        double baseMaxSellPrice = 0.0;
        int minAvailableStock = Integer.MAX_VALUE;
        int totalMinStock = 0;
        int totalMaxStock = 0;
        
        // Flags pour savoir si tous les ingrédients ont un prix à -1
        boolean allBuyPricesNegative = true;
        boolean allSellPricesNegative = true;
        
        // Parcourir les ingrédients une seule fois
        for (ItemStack ingredient : ingredients) {
            if (ingredient == null || ingredient.getType() == Material.AIR) {
                continue;
            }
            
            FoundItem foundItem = findItemInShops(shopID, ingredient);
            if (!foundItem.isFound()) {
                plugin.getLogger().warning("Missing ID or ShopID for ingredient " + ingredient);
                continue; // Passer à l'ingrédient suivant si l'ID est manquant
            }
            String ingredientID = foundItem.getItemID();
            String ingredientShopID = foundItem.getShopID();
            
            DynamicPrice ingredientPrice = null;

            // String cycleKey = ingredientShopID + ":" + ingredientID;
            // boolean isCycle = false;
            // // Éviter les boucles infinies
            // if (visitedItems.contains(cycleKey)) {
            //     DynamicPrice last = lastResults.get(cycleKey);
            //     // if (last != null) continue; // Si on a déjà un résultat, on continue
            //     if (last != null) {
            //         ingredientPrice = last;
            //         // continue; // Si on a déjà un résultat, on continue
            //         isCycle = true;
            //     } else {
            //         plugin.getLogger().warning("Cycle détecté pour " + cycleKey + " mais aucun lastResult n'est disponible !");
            //         continue; // On ne peut rien faire, on saute cet ingrédient
            //     }
            // }
            // visitedItems.add(cycleKey);
            
            // // Obtenir le type de l'ingrédient
            // DynaShopType ingredientType = getIngredientType(ingredient);
            // DynamicPrice ingredientPrice = plugin.getDynaShopListener().getOrLoadPrice(ingredientShopID, ingredientID, ingredient);
            // DynamicPrice ingredientPrice = plugin.getDynaShopListener().getOrLoadPrice(null, ingredientShopID, ingredientID, ingredient, visitedItems, new HashMap<>());
            // DynamicPrice ingredientPrice = plugin.getDynaShopListener().getOrLoadPrice(null, ingredientShopID, ingredientID, ingredient, new HashSet<>(visitedItems), new HashMap<>(lastResults));
            // if (!isCycle) {
            //     visitedItems.add(cycleKey);
                // try {
                    // plugin.getLogger().info("Ingredient " + ingredientID + " (" + ingredientShopID + ") (lastResults: " + lastResults + ")" + " visitedItems: " + visitedItems);
                    // ingredientPrice = plugin.getDynaShopListener().getOrLoadPrice(null, ingredientShopID, ingredientID, ingredient, visitedItems, lastResults);
                    ingredientPrice = plugin.getDynaShopListener().getOrLoadPriceInternal(null, ingredientShopID, ingredientID, ingredient, visitedItems, lastResults, false);
                    if (ingredientPrice == null) {

                        // plugin.getLogger().warning("Price not found for ingredient " + ingredientID + " in shop " + ingredientShopID);
                        continue; // Passer à l'ingrédient suivant si le prix n'est pas trouvé
                    // } else {
                    //     // Mettre à jour les résultats précédents avec le prix récupéré
                    //     lastResults.put(cycleKey, ingredientPrice);
                    }
                    // plugin.getLogger().info("Ingredient " + ingredientID + " (" + ingredientShopID + ") price: " + ingredientPrice.getBuyPrice() + " (lastResults: " + lastResults + ")");
                // } catch (Exception e) {
                //     plugin.getLogger().warning("Error retrieving price for ingredient " + ingredientID + " in shop " + ingredientShopID + ": " + e.getMessage());
                //     // En cas d'erreur, continuer avec les valeurs par défaut
                //     ingredientPrice = new DynamicPrice(-1.0, -1.0, -1.0, -1.0, -1.0, -1.0, 1.0, 1.0, 1.0, 1.0, -1, -1, -1, 1.0, 1.0);
                // } finally {
                //     visitedItems.remove(cycleKey);
                // }
            // }
            // // Vérifier si l'ingrédient a un prix valide
            // if (ingredientPrice == null || ingredientPrice.getBuyPrice() < 0 || ingredientPrice.getSellPrice() < 0) {
            //     plugin.getLogger().warning("Invalid price for ingredient " + ingredientID + " in shop " + ingredientShopID);
            //     continue; // Passer à l'ingrédient suivant si le prix est invalide
            // }

            // Utiliser les valeurs récupérées
            double ingredientBuyPrice = ingredientPrice.getBuyPrice();
            double ingredientSellPrice = ingredientPrice.getSellPrice();
            double ingredientMinBuyPrice = ingredientPrice.getMinBuyPrice();
            double ingredientMaxBuyPrice = ingredientPrice.getMaxBuyPrice();
            double ingredientMinSellPrice = ingredientPrice.getMinSellPrice();
            double ingredientMaxSellPrice = ingredientPrice.getMaxSellPrice();
            int ingredientStock = ingredientPrice.getStock();
            int ingredientMinStock = ingredientPrice.getMinStock();
            int ingredientMaxStock = ingredientPrice.getMaxStock();
            
            // Calculer la contribution de cet ingrédient aux différentes valeurs
            int amount = ingredient.getAmount();
            
            // Ne tenir compte du prix d'achat que s'il est positif
            if (ingredientBuyPrice >= 0) {
                basePrice += ingredientBuyPrice * amount;
                baseMinBuyPrice += ingredientMinBuyPrice * amount;
                baseMaxBuyPrice += ingredientMaxBuyPrice * amount;
                allBuyPricesNegative = false;
            }
            
            // Ne tenir compte du prix de vente que s'il est positif
            if (ingredientSellPrice >= 0) {
                baseSellPrice += ingredientSellPrice * amount;
                baseMinSellPrice += ingredientMinSellPrice * amount;
                baseMaxSellPrice += ingredientMaxSellPrice * amount;
                allSellPricesNegative = false;
            }
            
            // Calcul du stock disponible pour cet ingrédient
            int availableForCrafting = ingredientStock / amount;
            minAvailableStock = Math.min(minAvailableStock, availableForCrafting);

            // Stock minimum
            int minAvailableForCrafting = ingredientMinStock / amount;
            totalMinStock += minAvailableForCrafting;

            // Stock maximum
            int maxAvailableForCrafting = ingredientMaxStock / amount;
            totalMaxStock += maxAvailableForCrafting;
            
            // Optimisation: sortir tôt si on trouve un stock zéro
            if (minAvailableStock == 0) break;
        }
        
        // Appliquer le modificateur de recette
        // double modifier = getRecipeModifier(item);
        double modifier = getRecipeModifier(shopID, itemID);

        double finalBuyPrice = allBuyPricesNegative ? -1.0 : basePrice * modifier;
        double finalSellPrice = allSellPricesNegative ? -1.0 : baseSellPrice * modifier;
        double finalMinBuyPrice = allBuyPricesNegative ? -1.0 : baseMinBuyPrice * modifier;
        double finalMaxBuyPrice = allBuyPricesNegative ? -1.0 : baseMaxBuyPrice * modifier;
        double finalMinSellPrice = allSellPricesNegative ? -1.0 : baseMinSellPrice * modifier;
        double finalMaxSellPrice = allSellPricesNegative ? -1.0 : baseMaxSellPrice * modifier;
        
        // Vérifier que le prix de vente n'est pas supérieur au prix d'achat
        if (finalBuyPrice >= 0 && finalSellPrice >= 0 && finalSellPrice > finalBuyPrice - DynamicPrice.MIN_MARGIN) {
            finalSellPrice = finalBuyPrice - DynamicPrice.MIN_MARGIN;
        }
        
        // Vérifier que les bornes sont respectées
        if (finalBuyPrice >= 0) {
            finalBuyPrice = Math.max(finalMinBuyPrice, Math.min(finalBuyPrice, finalMaxBuyPrice));
        }
        if (finalSellPrice >= 0) {
            finalSellPrice = Math.max(finalMinSellPrice, Math.min(finalSellPrice, finalMaxSellPrice));
        }
        
        // Ajuster le stock maximum et actuel
        int finalStock = (minAvailableStock == Integer.MAX_VALUE) ? 0 : minAvailableStock;
        
        // // Mettre en cache tous les résultats
        // plugin.cacheRecipePrice(shopID, itemID, "buyPrice", finalBuyPrice);
        // plugin.cacheRecipePrice(shopID, itemID, "sellPrice", finalSellPrice);
        // plugin.cacheRecipePrice(shopID, itemID, "buyDynamic.min", finalMinBuyPrice);
        // plugin.cacheRecipePrice(shopID, itemID, "buyDynamic.max", finalMaxBuyPrice);
        // plugin.cacheRecipePrice(shopID, itemID, "sellDynamic.min", finalMinSellPrice);
        // plugin.cacheRecipePrice(shopID, itemID, "sellDynamic.max", finalMaxSellPrice);
        // plugin.cacheRecipeStock(shopID, itemID, "stock", finalStock);
        // plugin.cacheRecipeStock(shopID, itemID, "minstock", totalMinStock);
        // plugin.cacheRecipeStock(shopID, itemID, "maxstock", totalMaxStock);
        
        // String buyPriceKey = shopID + ":" + itemID + ":buyPrice";
        // String sellPriceKey = shopID + ":" + itemID + ":sellPrice";
        // String minBuyPriceKey = shopID + ":" + itemID + ":buyDynamic.min";
        // String maxBuyPriceKey = shopID + ":" + itemID + ":buyDynamic.max";
        // String minSellPriceKey = shopID + ":" + itemID + ":sellDynamic.min";
        // String maxSellPriceKey = shopID + ":" + itemID + ":sellDynamic.max";
        // String stockKey = shopID + ":" + itemID + ":stock";
        // String minStockKey = shopID + ":" + itemID + ":minstock";
        // String maxStockKey = shopID + ":" + itemID + ":maxstock";
        // // Mettre en cache les résultats dans le cache centralisé
        // plugin.getCalculatedPriceCache().put(buyPriceKey, finalBuyPrice);
        // plugin.getCalculatedPriceCache().put(sellPriceKey, finalSellPrice);
        // plugin.getCalculatedPriceCache().put(minBuyPriceKey, finalMinBuyPrice);
        // plugin.getCalculatedPriceCache().put(maxBuyPriceKey, finalMaxBuyPrice);
        // plugin.getCalculatedPriceCache().put(minSellPriceKey, finalMinSellPrice);
        // plugin.getCalculatedPriceCache().put(maxSellPriceKey, finalMaxSellPrice);
        // plugin.getCalculatedPriceCache().put(stockKey, finalStock);
        // plugin.getCalculatedPriceCache().put(minStockKey, totalMinStock);
        // plugin.getCalculatedPriceCache().put(maxStockKey, totalMaxStock);
        CacheManager<String, Double> priceCache = plugin.getCalculatedPriceCache();
        CacheManager<String, Integer> stockCache = plugin.getStockCache();

        // Prix
        priceCache.put(shopID + ":" + itemID + ":buyPrice", finalBuyPrice);
        priceCache.put(shopID + ":" + itemID + ":sellPrice", finalSellPrice);
        priceCache.put(shopID + ":" + itemID + ":buyDynamic.min", finalMinBuyPrice);
        priceCache.put(shopID + ":" + itemID + ":buyDynamic.max", finalMaxBuyPrice);
        priceCache.put(shopID + ":" + itemID + ":sellDynamic.min", finalMinSellPrice);
        priceCache.put(shopID + ":" + itemID + ":sellDynamic.max", finalMaxSellPrice);

        // Stock
        stockCache.put(shopID + ":" + itemID + ":stock", finalStock);
        stockCache.put(shopID + ":" + itemID + ":minstock", totalMinStock);
        stockCache.put(shopID + ":" + itemID + ":maxstock", totalMaxStock);

        
        return new RecipeCalculationResult(
            finalBuyPrice, finalSellPrice,
            finalMinBuyPrice, finalMaxBuyPrice,
            finalMinSellPrice, finalMaxSellPrice,
            finalStock, totalMinStock, totalMaxStock
        );
    }
    // /**
    //  * Version asynchrone pour calculer toutes les valeurs de recette en une fois
    //  */
    // public void calculateRecipeValuesAsync(String shopID, String itemID, ItemStack item, Consumer<RecipeCalculationResult> callback) {
    //     CompletableFuture.supplyAsync(() -> {
    //         try {
    //             return calculateRecipeValues(shopID, itemID, item, new ArrayList<>());
    //         } catch (Exception e) {
    //             plugin.getLogger().warning("Error calculating values for " + shopID + ":" + itemID + ": " + e.getMessage());
    //             // Valeurs par défaut en cas d'erreur
    //             return new RecipeCalculationResult(10.0, 8.0, 5.0, 20.0, 4.0, 16.0, 0, 0, 0);
    //         }
    //     }, recipeExecutor).thenAcceptAsync(result -> {
    //         Bukkit.getScheduler().runTask(plugin, () -> {
    //             try {
    //                 callback.accept(result);
    //             } catch (Exception e) {
    //                 plugin.getLogger().warning("Error in callback: " + e.getMessage());
    //             }
    //         });
    //     });
    // }

    // public DynamicPrice createRecipePrice(String shopID, String itemID, Map<String, DynamicPrice> lastResults) {
    public DynamicPrice createRecipePrice(String shopID, String itemID, Set<String> visitedItems, Map<String, DynamicPrice> lastResults) {
        // // Ajoute la clé de cycle
        // String key = shopID + ":" + itemID;
        // if (visited.contains(key)) {
        //     plugin.getLogger().warning("Cycle détecté dans la recette pour " + key + " !");
        //     // Retourne un prix par défaut ou null
        //     return null;
        // }
        // visited.add(key);
        // // Créer un prix dynamique pour la recette
        // DynamicPrice recipePrice = new DynamicPrice(0.0, 0.0);
        // recipePrice.setDynaShopType(DynaShopType.RECIPE);
        // return recipePrice;

        // RecipeCalculationResult result = calculateRecipeValues(shopID, itemID, new ArrayList<>());
        // RecipeCalculationResult result = calculateRecipeValues(shopID, itemID, new HashSet<>());
        // RecipeCalculationResult result = calculateRecipeValues(shopID, itemID, new HashSet<>(), new HashMap<>());
        // RecipeCalculationResult result = calculateRecipeValues(shopID, itemID, new HashSet<>(), lastResults);
        RecipeCalculationResult result = calculateRecipeValues(shopID, itemID, visitedItems, lastResults);

        // Créer l'objet DynamicPrice avec les valeurs calculées
        DynamicPrice recipePrice = new DynamicPrice(
            result.getBuyPrice(), result.getSellPrice(),
            result.getMinBuyPrice(), result.getMaxBuyPrice(), 
            result.getMinSellPrice(), result.getMaxSellPrice(),
            1.0, 1.0, 1.0, 1.0,
            result.getStock(), result.getMinStock(), result.getMaxStock(),
            1.0, 1.0
        );
        
        recipePrice.setDynaShopType(DynaShopType.RECIPE);
        // recipePrice.setFromRecipe(true);
        return recipePrice;
    }

    /**
     * Version asynchrone optimisée pour calculer toutes les valeurs de recette en une fois
     */
    public void calculateRecipeValuesAsync(String shopID, String itemID, ItemStack item, Consumer<RecipeCalculationResult> callback) {
        // Obtenir un indicateur de priorité basé sur la popularité
        int priority = isPopularItem(shopID, itemID) ? 10 : 1;
        
        // Sélectionner le pool en fonction de la priorité
        ExecutorService executor = priority > 5 ? highPriorityExecutor : recipeExecutor;
        
        // Tâche de calcul avec mécanisme de retry
        Runnable calculationTask = () -> {
            try {
                RecipeCalculationResult result = calculateRecipeValues(shopID, itemID, new HashSet<>(), new HashMap<>());
                // RecipeCalculationResult result = calculateRecipeValues(shopID, itemID, new HashSet<>());
                
                // Exécuter le callback sur le thread principal de Bukkit
                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        callback.accept(result);
                    } catch (Exception e) {
                        plugin.getLogger().warning("Error in calculation callback: " + e.getMessage());
                    }
                });
            } catch (Exception e) {
                // Log de l'erreur
                plugin.getLogger().warning("Error calculating values for " + shopID + ":" + itemID + ": " + e.getMessage());
                
                // Réessayer après un délai si c'est une erreur temporaire
                if (e instanceof IllegalStateException || e.getCause() instanceof IllegalStateException) {
                    Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, 
                        // this::retryCalculation, 10L); // 0.5 seconde plus tard
                        () -> retryCalculation(shopID, itemID, item, callback), 10L); // 0.5 seconde plus tard
                } else {
                    // Erreur non récupérable, utiliser des valeurs par défaut
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        callback.accept(new RecipeCalculationResult(-1.0, -1.0, -1.0, -1.0, -1.0, -1.0, -1, -1, -1));
                    });
                }
            }
        };
        
        // Soumettre la tâche avec sa priorité
        if (executor == highPriorityExecutor) {
            executor.submit(new PrioritizedRunnable(calculationTask, priority));
        } else {
            executor.submit(calculationTask);
        }
    }

    /**
     * Calcule les prix de plusieurs items en parallèle de manière optimisée
     */
    public void batchCalculateRecipes(List<RecipeRequest> requests, Consumer<Map<String, RecipeCalculationResult>> callback) {
        Map<String, RecipeCalculationResult> results = new ConcurrentHashMap<>();
        AtomicInteger counter = new AtomicInteger(requests.size());
        
        // Traiter chaque demande en parallèle
        for (RecipeRequest request : requests) {
            calculateRecipeValuesAsync(
                request.getShopID(), 
                request.getItemID(), 
                request.getItem(),
                result -> {
                    // Stocker le résultat
                    results.put(request.getShopID() + ":" + request.getItemID(), result);
                    
                    // Si tous les calculs sont terminés, appeler le callback
                    if (counter.decrementAndGet() == 0) {
                        callback.accept(results);
                    }
                }
            );
        }
    }

    /**
     * Classe pour représenter une demande de calcul de recette
     */
    public static class RecipeRequest {
        private final String shopID;
        private final String itemID;
        private final ItemStack item;
        
        public RecipeRequest(String shopID, String itemID, ItemStack item) {
            this.shopID = shopID;
            this.itemID = itemID;
            this.item = item;
        }
        
        // Getters
        public String getShopID() { return shopID; }
        public String getItemID() { return itemID; }
        public ItemStack getItem() { return item; }
    }
        
    /**
     * Méthode de retry pour les calculs qui ont échoué
     */
    private void retryCalculation(String shopID, String itemID, ItemStack item, Consumer<RecipeCalculationResult> callback) {
        try {
            RecipeCalculationResult result = calculateRecipeValues(shopID, itemID, new HashSet<>(), new HashMap<>());
            // RecipeCalculationResult result = calculateRecipeValues(shopID, itemID, new HashSet<>());
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(result));
        } catch (Exception e) {
            // Après l'échec du retry, utiliser les valeurs par défaut
            plugin.getLogger().severe("Retry failed for " + shopID + ":" + itemID + ": " + e.getMessage());
            Bukkit.getScheduler().runTask(plugin, () -> {
                callback.accept(new RecipeCalculationResult(-1.0, -1.0, -1.0, -1.0, -1.0, -1.0, -1, -1, -1));
            });
        }
    }

    /**
     * Nettoie les ressources lors de la fermeture du plugin
     */
    public void shutdown() {
        recipeExecutor.shutdown();
        highPriorityExecutor.shutdown();
        try {
            if (!recipeExecutor.awaitTermination(500, TimeUnit.MILLISECONDS)) {
                recipeExecutor.shutdownNow();
            }
            if (!highPriorityExecutor.awaitTermination(500, TimeUnit.MILLISECONDS)) {
                highPriorityExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            recipeExecutor.shutdownNow();
            highPriorityExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // public double calculateBuyPrice(String shopID, String itemID, ItemStack item, Set<String> visitedItems) {
    //     return calculatePrice(shopID, itemID, item, "buyPrice", visitedItems);
    // }

    // public double calculateSellPrice(String shopID, String itemID, ItemStack item, Set<String> visitedItems) {
    //     return calculatePrice(shopID, itemID, item, "sellPrice", visitedItems);
    // }

    public double calculatePrice(String shopID, String itemID, ItemStack item, String typePrice, Set<String> visitedItems) {
        List<ItemStack> ingredients = getIngredients(shopID, itemID);
        ingredients = consolidateIngredients(ingredients);
        double basePrice = 0.0;
        Map<String, DynamicPrice> lastResults = new HashMap<>();

        // Calculer le prix de base en fonction des ingrédients
        for (ItemStack ingredient : ingredients) {
            if (ingredient == null || ingredient.getType() == Material.AIR) {
                continue; // Ignorer les ingrédients invalides
            }
            // Copier la liste des items visités pour éviter les modifications dans la récursion
            // Set<String> newVisitedItems = new HashSet<>(visitedItems);
            // DynamicPrice ingredientPrice = getIngredientPrice(shopID, ingredient, newVisitedItems, lastResults);
            DynamicPrice ingredientPrice = getIngredientPrice(shopID, ingredient, visitedItems, lastResults);
            if (ingredientPrice == null) continue;
            
            double value;
            switch (typePrice) {
                case "buyPrice": value = ingredientPrice.getBuyPrice(); break;
                case "sellPrice": value = ingredientPrice.getSellPrice(); break;
                case "buyDynamic.min": value = ingredientPrice.getMinBuyPrice(); break;
                case "buyDynamic.max": value = ingredientPrice.getMaxBuyPrice(); break;
                case "sellDynamic.min": value = ingredientPrice.getMinSellPrice(); break;
                case "sellDynamic.max": value = ingredientPrice.getMaxSellPrice(); break;
                default: value = -1.0;
            }

            basePrice += value * ingredient.getAmount(); // Multiplier par la quantité de l'ingrédient
        }

        // Appliquer le modificateur en fonction du type de recette
        // double modifier = getRecipeModifier(item);
        double modifier = getRecipeModifier(shopID, itemID);
        // double price = basePrice * modifier;    
        
        // // Appliquer le multiplicateur d'enchantement si activé pour cet item
        // if (isEnchantmentModifierEnabled(shopID, itemID)) {
        //     price *= getEnchantMultiplier(item);
        // }
        // return price;

        return basePrice * modifier;
    }

    // public int calculateStock(String shopID, String itemID, ItemStack item, List<String> visitedItems) {
    //     List<ItemStack> ingredients = getIngredients(shopID, itemID, item);
    //     ingredients = consolidateIngredients(ingredients);
    //     int totalStock = 0;
    //     // Calculer le stock total en fonction des ingrédients
    //     for (ItemStack ingredient : ingredients) {
    //         if (ingredient == null || ingredient.getType() == Material.AIR) {
    //             continue; // Ignorer les ingrédients invalides
    //         }
    //         List<String> newVisitedItems = new ArrayList<>(visitedItems);
    //         int ingredientStock = getIngredientStock(ingredient, newVisitedItems);
    //         // totalStock += ingredientStock * ingredient.getAmount(); // Multiplier par la quantité de l'ingrédient
    //         totalStock += ingredientStock / ingredient.getAmount(); // Diviser par la quantité de l'ingrédient
    //         // totalStock += ingredientStock; // Ajouter le stock de l'ingrédient
    //     }
    //     return totalStock;
    // }
    public int calculateStock(String shopID, String itemID, List<String> visitedItems) {
        List<ItemStack> ingredients = getIngredients(shopID, itemID);
        ingredients = consolidateIngredients(ingredients);

        int minAvailableStock = Integer.MAX_VALUE;
        for (ItemStack ingredient : ingredients) {
            if (ingredient == null || ingredient.getType() == Material.AIR) { continue; }

            List<String> newVisitedItems = new ArrayList<>(visitedItems);
            
            // Vérifier si l'ingrédient est en mode STOCK ou STATIC_STOCK
            FoundItem foundItem = findItemInShops(shopID, ingredient);
            if (!foundItem.isFound()) {
                continue;
            }
            
            String ingredientID = foundItem.getItemID();
            String ingredientShopID = foundItem.getShopID();
            
            // Vérifier le type de l'ingrédient
            DynaShopType ingredientType = plugin.getShopConfigManager().getTypeDynaShop(ingredientShopID, ingredientID);
            
            // Ne calculer le stock que pour les items en mode STOCK ou STATIC_STOCK
            if (ingredientType != DynaShopType.STOCK && ingredientType != DynaShopType.STATIC_STOCK) {
                continue; // Ignorer cet ingrédient pour le calcul du stock
            }

            int ingredientStock = getIngredientStock(shopID, ingredient, newVisitedItems);
            if (ingredientStock < 0) {
                continue; // Ignorer les stocks négatifs (désactivés)
            }

            int availableForCrafting = ingredientStock / ingredient.getAmount();
            minAvailableStock = Math.min(minAvailableStock, availableForCrafting);

            if (minAvailableStock == 0) {
                break; // Pas besoin de continuer si le stock minimum est atteint
            }
        }

        return (minAvailableStock == Integer.MAX_VALUE) ? 0 : minAvailableStock;
    }

    // public void calculateStockAsync(String shopID, String itemID, ItemStack item, Consumer<Integer> callback) {
    //     // Exécuter le calcul de stock coûteux dans un thread séparé
    //     Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
    //         List<String> visitedItems = new ArrayList<>();
    //         int stock = calculateStock(shopID, itemID, item, visitedItems);

    //         plugin.cacheRecipeStock(shopID, itemID, "stock", stock);
    //         // Revenir au thread principal pour mettre à jour le stock
    //         Bukkit.getScheduler().runTask(plugin, () -> callback.accept(stock));
    //     });
    // }
    /**
     * Version entièrement asynchrone du calcul de stock
     */
    public void calculateStockAsync(String shopID, String itemID, Consumer<Integer> callback) {
        CompletableFuture.supplyAsync(() -> {
            try {
                List<String> visitedItems = new ArrayList<>();
                return calculateStock(shopID, itemID, visitedItems);
            } catch (Exception e) {
                plugin.getLogger().warning("Error calculating stock for " + shopID + ":" + itemID + ": " + e.getMessage());
                return 0;
            }
        }, recipeExecutor).thenAcceptAsync(stock -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    callback.accept(stock);
                } catch (Exception e) {
                    plugin.getLogger().warning("Error in stock callback: " + e.getMessage());
                }
            });
        });
    }

    public int calculateMaxStock(String shopID, String itemID, List<String> visitedItems) {
        List<ItemStack> ingredients = getIngredients(shopID, itemID);
        ingredients = consolidateIngredients(ingredients);
        int maxStock = 0;
        
        // Calculer le stock maximum en fonction des ingrédients
        for (ItemStack ingredient : ingredients) {
            if (ingredient == null || ingredient.getType() == Material.AIR) {
                continue; // Ignorer les ingrédients invalides
            }
            
            // Vérifier si l'ingrédient est en mode STOCK ou STATIC_STOCK
            FoundItem foundItem = findItemInShops(shopID, ingredient);
            if (!foundItem.isFound()) {
                continue;
            }
            
            String ingredientID = foundItem.getItemID();
            String ingredientShopID = foundItem.getShopID();
            
            // Vérifier le type de l'ingrédient
            DynaShopType ingredientType = plugin.getShopConfigManager().getTypeDynaShop(ingredientShopID, ingredientID);
            
            // Ne calculer le stock max que pour les items en mode STOCK ou STATIC_STOCK
            if (ingredientType != DynaShopType.STOCK && ingredientType != DynaShopType.STATIC_STOCK) {
                continue; // Ignorer cet ingrédient pour le calcul du stock max
            }
            
            int ingredientMaxStock = getIngredientMaxStock(shopID, ingredient, visitedItems);
            if (ingredientMaxStock < 0) {
                continue; // Ignorer les stocks max négatifs (désactivés)
            }
            
            int availableMaxStock = ingredientMaxStock / ingredient.getAmount();
            maxStock += availableMaxStock;
        }
        
        return maxStock;
    }

    /**
     * Version entièrement asynchrone du calcul de prix avec priorisation
     */
    public void calculatePriceAsync(String shopID, String itemID, ItemStack item, String typePrice, Consumer<Double> callback) {
        // Vérifier si c'est un item populaire ou fréquemment consulté
        boolean isHighPriority = isPopularItem(shopID, itemID);
        
        // Définir le fournisseur de calcul
        Supplier<Double> priceCalculator = () -> {
            try {
                Set<String> visitedItems = new HashSet<>();
                return calculatePrice(shopID, itemID, item, typePrice, visitedItems);
            } catch (Exception e) {
                plugin.getLogger().warning("Error calculating price for " + shopID + ":" + itemID + ": " + e.getMessage());
                return -1.0; // Valeur par défaut en cas d'erreur
            }
        };
        
        // Ajuster la priorité d'exécution
        ExecutorService executor = isHighPriority ? highPriorityExecutor : recipeExecutor;
        
        CompletableFuture.supplyAsync(priceCalculator, executor)
            .thenAcceptAsync(price -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        callback.accept(price);
                    } catch (Exception e) {
                        plugin.getLogger().warning("Error in price callback: " + e.getMessage());
                    }
                });
            });
    }
    
    // public List<ItemStack> getIngredients(String shopID, String itemID, ItemStack item) {
    //     List<ItemStack> ingredients = new ArrayList<>();
    //     // RecipeType typeRecipe = plugin.getShopConfigManager().getTypeRecipe(shopID, itemID);
    //     String cacheKey = shopID + ":" + itemID;

    //     // Vérifier si la recette est en cache et si le cache est encore valide
    //     if (ingredientsCache.containsKey(cacheKey) && 
    //         System.currentTimeMillis() - cacheTimestamps.getOrDefault(cacheKey, 0L) < CACHE_DURATION) {
    //         return new ArrayList<>(ingredientsCache.get(cacheKey));
    //     }

    //     if (plugin.getShopConfigManager().hasRecipePattern(shopID, itemID)) {
    //         // Récupérer directement les ingrédients stockés
    //         ingredients = plugin.getCustomIngredientsManager().getRecipeIngredients(shopID, itemID);
            
    //         if (ingredients.isEmpty()) {
    //             // Utiliser uniquement la recette définie manuellement dans la configuration
    //             // if (plugin.getShopConfigManager().getItemValue(shopID, itemID, "recipe.pattern", Boolean.class).orElse(false)) {
    //             if (plugin.getShopConfigManager().hasRecipePattern(shopID, itemID)) {
    //                 ConfigurationSection recipeSection = plugin.getShopConfigManager().getSection(shopID, itemID, "recipe");
    //                 Recipe recipeConfig = plugin.getCustomRecipeManager().loadRecipeFromShopConfig(shopID, itemID, recipeSection).orElse(null);
                    
    //                 if (recipeConfig != null) {
    //                     RecipeType typeRecipe = plugin.getShopConfigManager().getTypeRecipe(shopID, itemID);
                        
    //                     switch (typeRecipe) {
    //                         case SHAPED -> {
    //                             if (recipeConfig instanceof ShapedRecipe shapedRecipe) {
    //                                 for (ItemStack ingredient : shapedRecipe.getIngredientMap().values()) {
    //                                     if (ingredient != null && ingredient.getType() != Material.AIR) {
    //                                         ItemStack fixed = ingredient.clone();
    //                                         fixed.setAmount(1);
    //                                         ingredients.add(fixed);
    //                                         plugin.getLogger().info("Ingrédient ajouté: " + fixed);
    //                                     }
    //                                 }
    //                             }
    //                         }
    //                         case SHAPELESS -> {
    //                             if (recipeConfig instanceof ShapelessRecipe shapelessRecipe) {
    //                                 for (ItemStack ingredient : shapelessRecipe.getIngredientList()) {
    //                                     if (ingredient != null && ingredient.getType() != Material.AIR) {
    //                                         ItemStack fixed = ingredient.clone();
    //                                         fixed.setAmount(1);
    //                                         ingredients.add(fixed);
    //                                     }
    //                                 }
    //                             }
    //                         }
    //                         case FURNACE -> {
    //                             if (recipeConfig instanceof FurnaceRecipe furnaceRecipe) {
    //                                 ItemStack fixed = furnaceRecipe.getInput().clone();
    //                                 fixed.setAmount(1);
    //                                 ingredients.add(fixed);
    //                             }
    //                         }
    //                         default -> {
    //                             // Aucun traitement pour les autres types
    //                         }
    //                     }
    //                 }
    //             } else {
    //                 plugin.getLogger().warning("Aucune recette trouvée pour " + shopID + ":" + itemID + " bien que recipe.pattern soit défini");
    //             }
    //         } else {
    //             plugin.getLogger().info("Recette trouvée dans le cache pour " + shopID + ":" + itemID);
    //         }
    //     } else {
    //         plugin.getLogger().warning("Aucune recette définie dans la configuration pour " + shopID + ":" + itemID);
    //     }

    //     // Mettre en cache les résultats
    //     ingredientsCache.put(cacheKey, new ArrayList<>(ingredients));
    //     cacheTimestamps.put(cacheKey, System.currentTimeMillis());

    //     return ingredients;
    // }

    // public List<ItemStack> getIngredients(String shopID, String itemID, ItemStack item) {
    //     List<ItemStack> ingredients = new ArrayList<>();
    //     String cacheKey = shopID + ":" + itemID;

    //     // Vérifier le cache
    //     if (ingredientsCache.containsKey(cacheKey) && 
    //         System.currentTimeMillis() - cacheTimestamps.getOrDefault(cacheKey, 0L) < CACHE_DURATION) {
    //         return new ArrayList<>(ingredientsCache.get(cacheKey));
    //     }

    //     // Vérifier si la recette est définie
    //     if (plugin.getShopConfigManager().hasRecipePattern(shopID, itemID)) {
    //         plugin.getLogger().info("Recette trouvée pour " + shopID + ":" + itemID);
            
    //         // Lire directement les ingrédients depuis la configuration
    //         ConfigurationSection recipeSection = plugin.getShopConfigManager().getSection(shopID, itemID, "recipe");
    //         if (recipeSection != null) {
    //             String type = recipeSection.getString("type", "NONE").toUpperCase();
    //             plugin.getLogger().info("Type de recette: " + type);
                
    //             if (type.equals("SHAPED") || type.equals("SHAPELESS")) {
    //                 ConfigurationSection ingredientsSection = recipeSection.getConfigurationSection("ingredients");
    //                 if (ingredientsSection != null) {
    //                     for (String key : ingredientsSection.getKeys(false)) {
    //                         try {
    //                             ConfigurationSection ingredientSection = ingredientsSection.getConfigurationSection(key);
    //                             if (ingredientSection != null) {
    //                                 String itemRef = ingredientSection.getString("item");
    //                                 if (itemRef != null && itemRef.contains(":")) {
    //                                     String[] parts = itemRef.split(":");
    //                                     if (parts.length == 2) {
    //                                         String ingredientShopID = parts[0];
    //                                         String ingredientItemID = parts[1];
                                            
    //                                         // Obtenir l'item via l'API ShopGUI+
    //                                         try {
    //                                             // Shop shop = ShopGuiPlusApi.getShop(ingredientShopID);
    //                                             // ItemStack ingredientItem = ShopGuiPlusApi.getItemStackInShop(ingredientShopID, ingredientItemID);
    //                                             ItemStack ingredientItem = ShopGuiPlusApi.getShop(ingredientShopID).getShopItem(ingredientItemID).getItem();
    //                                             if (ingredientItem != null) {
    //                                                 plugin.getLogger().info("Ingrédient trouvé: " + ingredientItem);
    //                                                 ingredientItem.setAmount(ingredientSection.getInt("quantity", 1));
    //                                                 ingredients.add(ingredientItem);
    //                                             }
    //                                         } catch (Exception e) {
    //                                             plugin.getLogger().warning("Erreur lors de la récupération de l'ingrédient " + itemRef + ": " + e.getMessage());
    //                                         }
    //                                     }
    //                                 } else {
    //                                     // Fallback sur le type simple
    //                                     Material material = Material.matchMaterial(ingredientSection.getString("material", "AIR"));
    //                                     if (material != null && material != Material.AIR) {
    //                                         ItemStack ingredientItem = new ItemStack(material, ingredientSection.getInt("quantity", 1));
    //                                         plugin.getLogger().info("Ingrédient matériau trouvé: " + ingredientItem);
    //                                         ingredients.add(ingredientItem);
    //                                     }
    //                                 }
    //                             }
    //                         } catch (Exception e) {
    //                             plugin.getLogger().warning("Erreur lors du chargement de l'ingrédient " + key + ": " + e.getMessage());
    //                         }
    //                     }
    //                 }
    //             } else if (type.equals("FURNACE")) {
    //                 ConfigurationSection inputSection = recipeSection.getConfigurationSection("input");
    //                 if (inputSection != null) {
    //                     String itemRef = inputSection.getString("item");
    //                     if (itemRef != null && itemRef.contains(":")) {
    //                         String[] parts = itemRef.split(":");
    //                         if (parts.length == 2) {
    //                             String inputShopID = parts[0];
    //                             String inputItemID = parts[1];
                                
    //                             try {
    //                                 // ItemStack inputItem = ShopGuiPlusApi.getItemStackInShop(inputShopID, inputItemID);
    //                                 ItemStack inputItem = ShopGuiPlusApi.getShop(inputShopID).getShopItem(inputItemID).getItem();
    //                                 if (inputItem != null) {
    //                                     plugin.getLogger().info("Ingrédient four trouvé: " + inputItem);
    //                                     inputItem.setAmount(inputSection.getInt("quantity", 1));
    //                                     ingredients.add(inputItem);
    //                                 }
    //                             } catch (Exception e) {
    //                                 plugin.getLogger().warning("Erreur lors de la récupération de l'ingrédient " + itemRef + ": " + e.getMessage());
    //                             }
    //                         }
    //                     } else {
    //                         Material material = Material.matchMaterial(inputSection.getString("material", "AIR"));
    //                         if (material != null && material != Material.AIR) {
    //                             ItemStack inputItem = new ItemStack(material, inputSection.getInt("quantity", 1));
    //                             plugin.getLogger().info("Ingrédient four matériau trouvé: " + inputItem);
    //                             ingredients.add(inputItem);
    //                         }
    //                     }
    //                 }
    //             }
    //         }
    //     } else {
    //         plugin.getLogger().warning("Aucune recette définie dans la configuration pour " + shopID + ":" + itemID);
    //     }

    //     // Mettre en cache les résultats
    //     ingredientsCache.put(cacheKey, new ArrayList<>(ingredients));
    //     cacheTimestamps.put(cacheKey, System.currentTimeMillis());

    //     return ingredients;
    // }

    // public List<ItemStack> getIngredients(String shopID, String itemID, ItemStack item) {
    //     List<ItemStack> ingredients = new ArrayList<>();
    //     String cacheKey = shopID + ":" + itemID;

    //     // Vérifier le cache
    //     if (ingredientsCache.containsKey(cacheKey) && 
    //         System.currentTimeMillis() - cacheTimestamps.getOrDefault(cacheKey, 0L) < CACHE_DURATION) {
    //         return new ArrayList<>(ingredientsCache.get(cacheKey));
    //     }

    //     // Vérifier si la recette est définie
    //     if (plugin.getShopConfigManager().hasRecipePattern(shopID, itemID)) {
    //         plugin.getLogger().info("Recette trouvée pour " + shopID + ":" + itemID);
            
    //         // Lire directement les ingrédients depuis la configuration
    //         ConfigurationSection recipeSection = plugin.getShopConfigManager().getSection(shopID, itemID, "recipe");
    //         if (recipeSection != null) {
    //             // String type = recipeSection.getString("type", "NONE").toUpperCase();
    //             RecipeType typeRecipe = RecipeType.fromString(recipeSection.getString("type", "NONE").toUpperCase());
    //             plugin.getLogger().info("Type de recette: " + typeRecipe);

    //             if (typeRecipe == RecipeType.SHAPED) {
    //                 // Charger le pattern et compter les occurrences de chaque symbole
    //                 List<String> pattern = recipeSection.getStringList("pattern");
    //                 Map<Character, Integer> symbolCounts = new HashMap<>();
                    
    //                 // Compter les occurrences de chaque symbole dans le pattern
    //                 for (String row : pattern) {
    //                     for (char c : row.toCharArray()) {
    //                         if (c != ' ') {
    //                             symbolCounts.put(c, symbolCounts.getOrDefault(c, 0) + 1);
    //                         }
    //                     }
    //                 }
                    
    //                 plugin.getLogger().info("Occurrences de symboles: " + symbolCounts);
                    
    //                 // Charger les ingrédients avec leurs quantités ajustées
    //                 ConfigurationSection ingredientsSection = recipeSection.getConfigurationSection("ingredients");
    //                 if (ingredientsSection != null) {
    //                     for (String key : ingredientsSection.getKeys(false)) {
    //                         try {
    //                             if (key.length() != 1) {
    //                                 plugin.getLogger().warning("Clé d'ingrédient invalide: " + key);
    //                                 continue;
    //                             }
                                
    //                             char symbol = key.charAt(0);
    //                             int occurrences = symbolCounts.getOrDefault(symbol, 0);
                                
    //                             if (occurrences == 0) {
    //                                 plugin.getLogger().warning("Symbole " + symbol + " non utilisé dans le pattern");
    //                                 continue;
    //                             }
                                
    //                             ConfigurationSection ingredientSection = ingredientsSection.getConfigurationSection(key);
    //                             if (ingredientSection != null) {
    //                                 String itemRef = ingredientSection.getString("item");
    //                                 if (itemRef != null && itemRef.contains(":")) {
    //                                     String[] parts = itemRef.split(":");
    //                                     if (parts.length == 2) {
    //                                         String ingredientShopID = parts[0];
    //                                         String ingredientItemID = parts[1];
                                            
    //                                         // Obtenir l'item via l'API ShopGUI+
    //                                         try {
    //                                             ItemStack ingredientItem = ShopGuiPlusApi.getShop(ingredientShopID).getShopItem(ingredientItemID).getItem();
    //                                             if (ingredientItem != null) {
    //                                                 // Multiplier la quantité de base par le nombre d'occurrences
    //                                                 int baseQuantity = ingredientSection.getInt("quantity", 1);
    //                                                 int totalQuantity = baseQuantity * occurrences;
                                                    
    //                                                 ingredientItem.setAmount(totalQuantity);
    //                                                 plugin.getLogger().info("Ingrédient " + symbol + " trouvé: " + ingredientItem + 
    //                                                                                             " (x" + occurrences + " = " + totalQuantity + ")");
    //                                                 ingredients.add(ingredientItem);
    //                                             }
    //                                         } catch (Exception e) {
    //                                             plugin.getLogger().warning("Erreur lors de la récupération de l'ingrédient " + itemRef + ": " + e.getMessage());
    //                                         }
    //                                     }
    //                                 } else {
    //                                     // Fallback sur le type simple
    //                                     Material material = Material.matchMaterial(ingredientSection.getString("material", "AIR"));
    //                                     if (material != null && material != Material.AIR) {
    //                                         int baseQuantity = ingredientSection.getInt("quantity", 1);
    //                                         int totalQuantity = baseQuantity * occurrences;
                                            
    //                                         ItemStack ingredientItem = new ItemStack(material, totalQuantity);
    //                                         plugin.getLogger().info("Ingrédient matériau " + symbol + " trouvé: " + ingredientItem + 
    //                                                                                     " (x" + occurrences + " = " + totalQuantity + ")");
    //                                         ingredients.add(ingredientItem);
    //                                     }
    //                                 }
    //                             }
    //                         } catch (Exception e) {
    //                             plugin.getLogger().warning("Erreur lors du chargement de l'ingrédient " + key + ": " + e.getMessage());
    //                         }
    //                     }
    //                 }
    //             } else if (typeRecipe == RecipeType.SHAPELESS) {
    //                 // Pour les recettes shapeless, on ne change pas la logique actuelle
    //                 ConfigurationSection ingredientsSection = recipeSection.getConfigurationSection("ingredients");
    //                 if (ingredientsSection != null) {
    //                     for (String key : ingredientsSection.getKeys(false)) {
    //                         try {
    //                             ConfigurationSection ingredientSection = ingredientsSection.getConfigurationSection(key);
    //                             if (ingredientSection != null) {
    //                                 String itemRef = ingredientSection.getString("item");
    //                                 if (itemRef != null && itemRef.contains(":")) {
    //                                     String[] parts = itemRef.split(":");
    //                                     if (parts.length == 2) {
    //                                         String ingredientShopID = parts[0];
    //                                         String ingredientItemID = parts[1];
                                            
    //                                         // Obtenir l'item via l'API ShopGUI+
    //                                         try {
    //                                             ItemStack ingredientItem = ShopGuiPlusApi.getShop(ingredientShopID).getShopItem(ingredientItemID).getItem();
    //                                             if (ingredientItem != null) {
    //                                                 ingredientItem.setAmount(ingredientSection.getInt("quantity", 1));
    //                                                 plugin.getLogger().info("Ingrédient trouvé: " + ingredientItem);
    //                                                 ingredients.add(ingredientItem);
    //                                             }
    //                                         } catch (Exception e) {
    //                                             plugin.getLogger().warning("Erreur lors de la récupération de l'ingrédient " + itemRef + ": " + e.getMessage());
    //                                         }
    //                                     }
    //                                 } else {
    //                                     // Fallback sur le type simple
    //                                     Material material = Material.matchMaterial(ingredientSection.getString("material", "AIR"));
    //                                     if (material != null && material != Material.AIR) {
    //                                         ItemStack ingredientItem = new ItemStack(material, ingredientSection.getInt("quantity", 1));
    //                                         plugin.getLogger().info("Ingrédient matériau trouvé: " + ingredientItem);
    //                                         ingredients.add(ingredientItem);
    //                                     }
    //                                 }
    //                             }
    //                         } catch (Exception e) {
    //                             plugin.getLogger().warning("Erreur lors du chargement de l'ingrédient " + key + ": " + e.getMessage());
    //                         }
    //                     }
    //                 }
    //             } else if (typeRecipe == RecipeType.FURNACE) {
    //                 ConfigurationSection inputSection = recipeSection.getConfigurationSection("input");
    //                 if (inputSection != null) {
    //                     String itemRef = inputSection.getString("item");
    //                     if (itemRef != null && itemRef.contains(":")) {
    //                         String[] parts = itemRef.split(":");
    //                         if (parts.length == 2) {
    //                             String inputShopID = parts[0];
    //                             String inputItemID = parts[1];
                                
    //                             try {
    //                                 // ItemStack inputItem = ShopGuiPlusApi.getItemStackInShop(inputShopID, inputItemID);
    //                                 ItemStack inputItem = ShopGuiPlusApi.getShop(inputShopID).getShopItem(inputItemID).getItem();
    //                                 if (inputItem != null) {
    //                                     plugin.getLogger().info("Ingrédient four trouvé: " + inputItem);
    //                                     inputItem.setAmount(inputSection.getInt("quantity", 1));
    //                                     ingredients.add(inputItem);
    //                                 }
    //                             } catch (Exception e) {
    //                                 plugin.getLogger().warning("Erreur lors de la récupération de l'ingrédient " + itemRef + ": " + e.getMessage());
    //                             }
    //                         }
    //                     } else {
    //                         Material material = Material.matchMaterial(inputSection.getString("material", "AIR"));
    //                         if (material != null && material != Material.AIR) {
    //                             ItemStack inputItem = new ItemStack(material, inputSection.getInt("quantity", 1));
    //                             plugin.getLogger().info("Ingrédient four matériau trouvé: " + inputItem);
    //                             ingredients.add(inputItem);
    //                         }
    //                     }
    //                 }
    //             }
    //         }
    //     } else {
    //         plugin.getLogger().warning("Aucune recette définie dans la configuration pour " + shopID + ":" + itemID);
    //     }

    //     // Mettre en cache les résultats
    //     ingredientsCache.put(cacheKey, new ArrayList<>(ingredients));
    //     cacheTimestamps.put(cacheKey, System.currentTimeMillis());

    //     return ingredients;
    // }

    /**
     * Récupère les ingrédients d'une recette à partir de la configuration
     */
    public List<ItemStack> getIngredients(String shopID, String itemID) {
    // public List<ItemStack> getIngredients(String shopID, String itemID, ItemStack item) {
        String cacheKey = shopID + ":" + itemID;

        // // Vérifier le cache
        // if (isInCache(cacheKey)) {
        //     return new ArrayList<>(ingredientsCache.get(cacheKey));
        // }

        // Utiliser le CacheManager pour récupérer/calculer les ingrédients
        return recipeCache.get(cacheKey, () -> {
            List<ItemStack> ingredients = new ArrayList<>();

            // // Vérifier si la recette est définie
            // if (!plugin.getShopConfigManager().hasRecipePattern(shopID, itemID)) {
            //     plugin.getLogger().warning("No recipe defined in configuration for " + shopID + ":" + itemID);
            //     return ingredients;
            // }

            // Récupérer la configuration de la recette
            ConfigurationSection recipeSection = plugin.getShopConfigManager().getSection(shopID, itemID, "recipe");
            if (recipeSection == null) {
                return ingredients;
            }

            // Charger les ingrédients selon le type de recette
            RecipeType typeRecipe = RecipeType.fromString(recipeSection.getString("type", "NONE").toUpperCase());
            // ingredients = loadIngredientsByType(shopID, itemID, recipeSection, typeRecipe);
            return loadIngredientsByType(recipeSection, typeRecipe);
        });
        
        // // Mettre en cache
        // updateCache(cacheKey, ingredients);
        
        // return ingredients;
    }

    // /**
    //  * Vérifie si les ingrédients sont en cache et si le cache est valide
    //  */
    // private boolean isInCache(String cacheKey) {
    //     // return ingredientsCache.containsKey(cacheKey) && 
    //     //     System.currentTimeMillis() - cacheTimestamps.getOrDefault(cacheKey, 0L) < CACHE_DURATION;
    //     return recipeCache.get(cacheKey, () -> null) != null;
    // }

    // /**
    //  * Met à jour le cache des ingrédients
    //  */
    // private void updateCache(String cacheKey, List<ItemStack> ingredients) {
    //     // ingredientsCache.put(cacheKey, new ArrayList<>(ingredients));
    //     // cacheTimestamps.put(cacheKey, System.currentTimeMillis());
    //     recipeCache.put(cacheKey, new ArrayList<>(ingredients));
    // }

    /**
     * Charge les ingrédients selon le type de recette
     */
    private List<ItemStack> loadIngredientsByType(ConfigurationSection recipeSection, RecipeType typeRecipe) {
        switch (typeRecipe) {
            case SHAPED:
                return loadShapedIngredients(recipeSection);
            case SHAPELESS:
                return loadShapelessIngredients(recipeSection);
            case FURNACE:
                return loadFurnaceIngredients(recipeSection);
            default:
                plugin.getLogger().warning("Unsupported recipe type: " + typeRecipe);
                return new ArrayList<>();
        }
    }

    /**
     * Charge les ingrédients d'une recette SHAPED
     */
    private List<ItemStack> loadShapedIngredients(ConfigurationSection recipeSection) {
        List<ItemStack> ingredients = new ArrayList<>();
        
        // Compter les occurrences de chaque symbole dans le pattern
        Map<Character, Integer> symbolCounts = countSymbolsInPattern(recipeSection.getStringList("pattern"));
        
        // Charger les ingrédients
        ConfigurationSection ingredientsSection = recipeSection.getConfigurationSection("ingredients");
        if (ingredientsSection == null) {
            return ingredients;
        }
        
        for (String key : ingredientsSection.getKeys(false)) {
            if (key.length() != 1) {
                plugin.getLogger().warning("Invalid ingredient key: " + key);
                continue;
            }
            
            char symbol = key.charAt(0);
            int occurrences = symbolCounts.getOrDefault(symbol, 0);
            
            if (occurrences == 0) {
                plugin.getLogger().warning("Symbol " + symbol + " not used in pattern");
                continue;
            }
            
            ItemStack ingredient = loadIngredientFromSection(ingredientsSection, key);
            if (ingredient != null) {
                // Multiplier la quantité par le nombre d'occurrences
                ingredient.setAmount(ingredient.getAmount() * occurrences);
                ingredients.add(ingredient);
            }
        }
        
        return ingredients;
    }

    /**
     * Compte les occurrences de chaque symbole dans le pattern
     */
    private Map<Character, Integer> countSymbolsInPattern(List<String> pattern) {
        Map<Character, Integer> symbolCounts = new HashMap<>();
        
        for (String row : pattern) {
            for (char c : row.toCharArray()) {
                if (c != ' ') {
                    symbolCounts.put(c, symbolCounts.getOrDefault(c, 0) + 1);
                }
            }
        }
        
        return symbolCounts;
    }

    /**
     * Charge les ingrédients d'une recette SHAPELESS
     */
    private List<ItemStack> loadShapelessIngredients(ConfigurationSection recipeSection) {
        List<ItemStack> ingredients = new ArrayList<>();
        
        ConfigurationSection ingredientsSection = recipeSection.getConfigurationSection("ingredients");
        if (ingredientsSection == null) {
            return ingredients;
        }
        
        for (String key : ingredientsSection.getKeys(false)) {
            ItemStack ingredient = loadIngredientFromSection(ingredientsSection, key);
            if (ingredient != null) {
                ingredients.add(ingredient);
            }
        }
        
        return ingredients;
    }

    /**
     * Charge les ingrédients d'une recette FURNACE
     */
    private List<ItemStack> loadFurnaceIngredients(ConfigurationSection recipeSection) {
        List<ItemStack> ingredients = new ArrayList<>();
        
        // Vérifier si l'entrée est spécifiée comme une chaîne directe
        if (recipeSection.isString("input")) {
            String itemRef = recipeSection.getString("input");
            if (itemRef != null && itemRef.contains(":")) {
                ItemStack ingredient = loadShopItem(itemRef);
                if (ingredient != null) {
                    ingredients.add(ingredient);
                }
            }
            return ingredients;
        }
        
        // Sinon, traiter comme une section de configuration
        ConfigurationSection inputSection = recipeSection.getConfigurationSection("input");
        if (inputSection == null) {
            return ingredients;
        }
        
        // Utiliser l'ancienne méthode pour la compatibilité
        ItemStack ingredient = loadIngredientFromSection(inputSection);
        if (ingredient != null) {
            ingredients.add(ingredient);
        }
        
        return ingredients;
    }
    // private List<ItemStack> loadFurnaceIngredients(ConfigurationSection recipeSection) {
    //     List<ItemStack> ingredients = new ArrayList<>();
        
    //     ConfigurationSection inputSection = recipeSection.getConfigurationSection("input");
    //     if (inputSection == null) {
    //         return ingredients;
    //     }
        
    //     ItemStack ingredient = loadIngredientFromSection(inputSection);
    //     if (ingredient != null) {
    //         ingredients.add(ingredient);
    //     }
        
    //     return ingredients;
    // }

    /**
     * Charge un ingrédient à partir d'une section de configuration ou d'une valeur directe
     */
    private ItemStack loadIngredientFromSection(ConfigurationSection ingredientsSection, String key) {
        if (ingredientsSection == null) {
            return null;
        }
        
        try {
            // Vérifier si c'est une valeur directe (nouvelle syntaxe X: minerais:RUBY)
            if (ingredientsSection.isString(key)) {
                String itemRef = ingredientsSection.getString(key);
                if (itemRef != null && itemRef.contains(":")) {
                    return loadShopItem(itemRef);
                }
            }
            
            // Sinon, c'est une section de configuration (ancienne syntaxe X: {item: minerais:RUBY})
            ConfigurationSection section = ingredientsSection.getConfigurationSection(key);
            if (section != null) {
                String itemRef = section.getString("item");
                if (itemRef != null && itemRef.contains(":")) {
                    return loadShopItem(itemRef);
                } else {
                    return loadMaterialItem(section);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error loading ingredient " + key + ": " + e.getMessage());
        }
        
        return null;
    }
    /**
     * Version de compatibilité pour l'ancien format
     */
    private ItemStack loadIngredientFromSection(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        
        try {
            String itemRef = section.getString("item");
            if (itemRef != null && itemRef.contains(":")) {
                return loadShopItem(itemRef);
            } else {
                return loadMaterialItem(section);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error loading ingredient: " + e.getMessage());
            return null;
        }
    }

    /**
     * Charge un item depuis un shop (format "shopID:itemID")
     */
    // private ItemStack loadShopItem(String itemRef, int quantity) {
    private ItemStack loadShopItem(String itemRef) {
        String[] parts = itemRef.split(":");
        if (parts.length != 2) {
            return null;
        }
        
        String shopID = parts[0];
        String itemID = parts[1];
        
        try {
            ItemStack item = ShopGuiPlusApi.getShop(shopID).getShopItem(itemID).getItem();
            if (item != null) {
                // item.setAmount(quantity);
                item.setAmount(1); // Pour éviter les problèmes de quantité, on fixe à 1
                // plugin.getLogger().info("Ingrédient trouvé: " + item);
                return item;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error loading ingredient " + itemRef + ": " + e.getMessage());
        }
        
        return null;
    }

    /**
     * Charge un item depuis un matériau standard
     */
    private ItemStack loadMaterialItem(ConfigurationSection section) {
        Material material = Material.matchMaterial(section.getString("material", "AIR"));
        if (material != null && material != Material.AIR) {
            // ItemStack item = new ItemStack(material, section.getInt("quantity", 1));
            ItemStack item = new ItemStack(material, 1);
            plugin.getLogger().info("Ingredient material found: " + item);
            return item;
        }
        return null;
    }

    // private double getIngredientPrice(String shopID, ItemStack ingredient, String typePrice, Set<String> visitedItems, Map<String, DynamicPrice> lastResults) {
    //     // Trouver l'item dans les shops
    //     FoundItem foundItem = findItemInShops(shopID, ingredient);
    //     if (!foundItem.isFound()) {
    //         plugin.getLogger().warning("Unable to find ingredient " + ingredient + " in shop " + shopID);
    //         return -1.0; // Retourner -1.0 pour indiquer une erreur
    //     }
        
    //     String ingredientID = foundItem.getItemID();
    //     String ingredientShopID = foundItem.getShopID();
        
    //     // Vérifier si l'item a déjà été visité pour éviter les boucles infinies
    //     String cycleKey = ingredientShopID + ":" + ingredientID;
    //     if (visitedItems.contains(cycleKey)) {
    //         // Double last = lastResults.get(cycleKey + ":" + typePrice);
    //         // if (last != null) return last;
    //         DynamicPrice last = lastResults.get(cycleKey + ":" + typePrice);
    //         if (last != null) return last;
    //         return -1.0; // Retourner -1.0 pour indiquer une erreur de cycle
    //     }
    //     visitedItems.add(cycleKey);

    //     // Utiliser getOrLoadPrice pour obtenir toutes les informations de prix
    //     // DynamicPrice price = plugin.getDynaShopListener().getOrLoadPrice(ingredientShopID, ingredientID, ingredient);
    //     DynamicPrice price = plugin.getDynaShopListener().getOrLoadPrice(null, ingredientShopID, ingredientID, ingredient, visitedItems, lastResults);

    //     if (price == null) {
    //         return -1.0; // Retourner -1.0 pour indiquer une erreur
    //     }
        
    //     // // Retourner le prix demandé selon le type
    //     // if (typePrice.equals("buyPrice")) {
    //     //     return price.getBuyPrice();
    //     // } else if (typePrice.equals("sellPrice")) {
    //     //     return price.getSellPrice();
    //     // } else if (typePrice.equals("buyDynamic.min")) {
    //     //     return price.getMinBuyPrice();
    //     // } else if (typePrice.equals("buyDynamic.max")) {
    //     //     return price.getMaxBuyPrice();
    //     // } else if (typePrice.equals("sellDynamic.min")) {
    //     //     return price.getMinSellPrice();
    //     // } else if (typePrice.equals("sellDynamic.max")) {
    //     //     return price.getMaxSellPrice();
    //     // }
        
    //     // return -1.0; // Retourner -1.0 pour indiquer une erreur si le type de prix n'est pas reconnu
    //     double value;
    //     if (typePrice.equals("buyPrice")) {
    //         value = price.getBuyPrice();
    //     } else if (typePrice.equals("sellPrice")) {
    //         value = price.getSellPrice();
    //     } else if (typePrice.equals("buyDynamic.min")) {
    //         value = price.getMinBuyPrice();
    //     } else if (typePrice.equals("buyDynamic.max")) {
    //         value = price.getMaxBuyPrice();
    //     } else if (typePrice.equals("sellDynamic.min")) {
    //         value = price.getMinSellPrice();
    //     } else if (typePrice.equals("sellDynamic.max")) {
    //         value = price.getMaxSellPrice();
    //     } else {
    //         value = -1.0;
    //     }
    //     lastResults.put(cycleKey + ":" + typePrice, value);
    //     return value;
    // }
    private DynamicPrice getIngredientPrice(String shopID, ItemStack ingredient, Set<String> visitedItems, Map<String, DynamicPrice> lastResults) {
        FoundItem foundItem = findItemInShops(shopID, ingredient);
        if (!foundItem.isFound()) {
            plugin.getLogger().warning("Unable to find ingredient " + ingredient + " in shop " + shopID);
            return null;
            // return new DynamicPrice(-1, -1, -1, -1, -1, -1, 1, 1, 1, 1, 0, 0, 0, 1, 1);
        }

        String ingredientID = foundItem.getItemID();
        String ingredientShopID = foundItem.getShopID();
        String cycleKey = ingredientShopID + ":" + ingredientID;

        if (visitedItems.contains(cycleKey)) {
            DynamicPrice last = lastResults.get(cycleKey);
            if (last != null) return last;
            // plugin.getLogger().warning("Cycle détecté pour " + cycleKey + " mais aucun lastResult n'est disponible !");
            return null;
        }
        visitedItems.add(cycleKey);

        try {
            DynamicPrice price = plugin.getDynaShopListener().getOrLoadPrice(null, ingredientShopID, ingredientID, ingredient, visitedItems, lastResults);

            if (price != null) {
                lastResults.put(cycleKey, price);
            // } else {
                // plugin.getLogger().warning("Price not found for " + cycleKey);
            }
            return price;
        } finally {
            visitedItems.remove(cycleKey);
        }
    }

    // // Méthodes auxiliaires extraites:
    // private Double checkCache(String shopID, String itemID, String typePrice) {
    //     // Utiliser le système de cache global du plugin
    //     double cachedPrice = plugin.getCachedRecipePrice(shopID, itemID, typePrice);
        
    //     // Si le prix est en cache (différent de -1.0, la valeur retournée quand le prix n'est pas en cache)
    //     if (cachedPrice >= 0) {
    //         // Ajouter du logging pour le débogage
    //         // if (plugin.isDebug()) {
    //         //     plugin.getLogger().info("Cache hit pour " + shopID + ":" + itemID + ":" + typePrice + " = " + cachedPrice);
    //         // }
    //         return cachedPrice;
    //     }
        
    //     return null; // Le prix n'est pas en cache
    // }

    // public List<ItemStack> consolidateIngredients(List<ItemStack> ingredients) {
    //     // Map<Material, Integer> ingredientCounts = new HashMap<>();
    //     Map<Material, Integer> ingredientCounts = new EnumMap<>(Material.class);

    //     for (ItemStack ingredient : ingredients) {
    //         Material material = ingredient.getType();
    //         int amount = ingredientCounts.getOrDefault(material, 0) + ingredient.getAmount();
    //         ingredientCounts.put(material, amount);
    //     }

    //     List<ItemStack> consolidated = new ArrayList<>();
    //     for (Map.Entry<Material, Integer> entry : ingredientCounts.entrySet()) {
    //         ItemStack itemStack = new ItemStack(entry.getKey());
    //         itemStack.setAmount(entry.getValue());
    //         consolidated.add(itemStack);
    //     }

    //     return consolidated;
    // }
    // public List<ItemStack> consolidateIngredients(List<ItemStack> ingredients) {
    //     // Map<Material, Integer> ingredientCounts = new HashMap<>();
    //     Map<ItemStack, Integer> ingredientCounts = new HashMap<>();
    //     for (ItemStack ingredient : ingredients) {
    //         if (ingredient == null || ingredient.getType() == Material.AIR) {
    //             continue; // Ignorer les ingrédients invalides
    //         }
    //         // // Utiliser un ItemStack comme clé pour conserver le nom et les enchantements
    //         // ingredientCounts.merge(ingredient, ingredient.getAmount(), Integer::sum);
    //         int amount = ingredientCounts.getOrDefault(ingredient, 0) + ingredient.getAmount();
    //         ingredientCounts.put(ingredient, amount);
    //         // plugin.getLogger().info("Consolidating ingredient: " + ingredient + " with amount: " + amount);
    //     }

    //     List<ItemStack> consolidated = new ArrayList<>();
    //     for (Map.Entry<ItemStack, Integer> entry : ingredientCounts.entrySet()) {
    //         ItemStack itemStack = entry.getKey().clone();
    //         itemStack.setAmount(entry.getValue());
    //         consolidated.add(itemStack);
    //     }

    //     return consolidated;
    // }
    public List<ItemStack> consolidateIngredients(List<ItemStack> ingredients) {
        Map<String, ItemStack> uniqueItems = new HashMap<>();
        Map<String, Integer> amounts = new HashMap<>();
        
        for (ItemStack ingredient : ingredients) {
            if (ingredient == null || ingredient.getType() == Material.AIR) {
                continue;
            }
            
            // Créer une clé unique basée sur le type de matériau et les métadonnées
            String key = createItemKey(ingredient);
            
            // Ajouter ou incrémenter la quantité
            amounts.put(key, amounts.getOrDefault(key, 0) + ingredient.getAmount());
            // Stocker l'item s'il n'existe pas déjà
            uniqueItems.computeIfAbsent(key, k -> ingredient.clone());
        }

        // Créer la liste consolidée
        List<ItemStack> consolidated = new ArrayList<>();
        for (Map.Entry<String, ItemStack> entry : uniqueItems.entrySet()) {
            ItemStack item = entry.getValue();
            item.setAmount(amounts.get(entry.getKey()));
            consolidated.add(item);
        }
        
        return consolidated;
    }
    
    private String createItemKey(ItemStack item) {
        StringBuilder key = new StringBuilder(item.getType().toString());
        
        if (item.hasItemMeta()) {
            if (item.getItemMeta().hasDisplayName()) {
                key.append(":").append(item.getItemMeta().getDisplayName());
            }
            if (item.getItemMeta().hasCustomModelData()) {
                key.append(":").append(item.getItemMeta().getCustomModelData());
            }
        }
        
        return key.toString();
    }

    // private double getRecipeModifier(ItemStack item) {
    //     // Déterminer le type de recette et appliquer le modificateur correspondant
    //     // for (Recipe recipe : item.getItemMeta().getPersistentDataContainer().getRecipesFor(item)) {
    //     for (Recipe recipe : plugin.getServer().getRecipesFor(item)) {
    //         if (recipe instanceof ShapedRecipe) {
    //             // return config.getDouble("actions.shaped", 1.0);
    //             return plugin.getDataConfig().getShapedValue();
    //         } else if (recipe instanceof ShapelessRecipe) {
    //             // return config.getDouble("actions.shapeless", 1.0);
    //             return plugin.getDataConfig().getShapelessValue();
    //         } else if (recipe instanceof FurnaceRecipe) {
    //             // return config.getDouble("actions.furnace", 1.0);
    //             return plugin.getDataConfig().getFurnaceValue();
    //         }
    //     }

    //     // Retourner un modificateur par défaut si aucune recette n'est trouvée
    //     return 1.0;
    // }
    private double getRecipeModifier(String shopID, String itemID) {
        // Déterminer le type de recette et appliquer le modificateur correspondant
        // for (Recipe recipe : item.getItemMeta().getPersistentDataContainer().getRecipesFor(item)) {
        // for (Recipe recipe : plugin.getServer().getRecipesFor(item)) {
        //     if (recipe instanceof ShapedRecipe) {
        //         // return config.getDouble("actions.shaped", 1.0);
        //         return plugin.getDataConfig().getShapedValue();
        //     } else if (recipe instanceof ShapelessRecipe) {
        //         // return config.getDouble("actions.shapeless", 1.0);
        //         return plugin.getDataConfig().getShapelessValue();
        //     } else if (recipe instanceof FurnaceRecipe) {
        //         // return config.getDouble("actions.furnace", 1.0);
        //         return plugin.getDataConfig().getFurnaceValue();
        //     }
        // }
        RecipeType recipeType = plugin.getShopConfigManager().getTypeRecipe(shopID, itemID);
        if (recipeType == RecipeType.SHAPED) {
            return plugin.getDataConfig().getShapedValue();
        } else if (recipeType == RecipeType.SHAPELESS) {
            return plugin.getDataConfig().getShapelessValue();
        } else if (recipeType == RecipeType.FURNACE) {
            return plugin.getDataConfig().getFurnaceValue();
        }

        // Retourner un modificateur par défaut si aucune recette n'est trouvée
        return 1.0;
    }

    public int getIngredientStock(String shopID, ItemStack ingredient, List<String> visitedItems) {
        // Récupérer l'ID de l'item dans le shop
        FoundItem foundItem = findItemInShops(shopID, ingredient);
        if (!foundItem.isFound()) {
            plugin.getLogger().warning("Unable to find ingredient " + ingredient + " in shop " + shopID);
            return -1; // Retourner -1 si l'ingrédient n'est pas trouvé
        }
        
        String ingredientID = foundItem.getItemID();
        String ingredientShopID = foundItem.getShopID();

        // Vérifier le type de l'ingrédient
        DynaShopType ingredientType = plugin.getShopConfigManager().getTypeDynaShop(ingredientShopID, ingredientID);
        
        // Ne récupérer le stock que si l'item est en mode STOCK ou STATIC_STOCK
        if (ingredientType != DynaShopType.STOCK && ingredientType != DynaShopType.STATIC_STOCK) {
            return -1; // Retourner -1 pour indiquer que l'item n'est pas en mode stock
        }

        // Vérifier si l'item a déjà été visité
        if (visitedItems.contains(ingredientID)) {
            return 0; // Retourner 0 pour éviter une boucle infinie
        }
        // Ajouter l'item à la liste des visités
        visitedItems.add(ingredientID);

        // Récupérer le stock de l'item
        Optional<Integer> stockOptional = plugin.getStorageManager().getStock(ingredientShopID, ingredientID);

        if (stockOptional.isPresent()) {
            return stockOptional.get();
        }
        
        return 0; // Retourner 0 si le stock n'est pas trouvé
    }

    public int getIngredientMaxStock(String shopID, ItemStack ingredient, List<String> visitedItems) {
        // Récupérer l'ID de l'item dans le shop
        FoundItem foundItem = findItemInShops(shopID, ingredient);
        if (!foundItem.isFound()) {
            plugin.getLogger().warning("Unable to find ingredient " + ingredient + " in shop " + shopID);
            return -1; // Retourner -1 si l'ingrédient n'est pas trouvé
        }
        
        String ingredientID = foundItem.getItemID();
        String ingredientShopID = foundItem.getShopID();

        // Vérifier le type de l'ingrédient
        DynaShopType ingredientType = plugin.getShopConfigManager().getTypeDynaShop(ingredientShopID, ingredientID);
        
        // Ne récupérer le stock max que si l'item est en mode STOCK ou STATIC_STOCK
        if (ingredientType != DynaShopType.STOCK && ingredientType != DynaShopType.STATIC_STOCK) {
            return -1; // Retourner -1 pour indiquer que l'item n'est pas en mode stock
        }

        // Vérifier si l'item a déjà été visité
        if (visitedItems.contains(ingredientID)) {
            return 0; // Retourner 0 pour éviter une boucle infinie
        }
        // Ajouter l'item à la liste des visités
        visitedItems.add(ingredientID);

        // Récupérer le stock maximum de l'item
        Optional<Integer> maxStockOptional = plugin.getShopConfigManager()
            .getItemValue(ingredientShopID, ingredientID, "stock.max", Integer.class);
        
        if (maxStockOptional.isPresent()) {
            return maxStockOptional.get();
        }
        
        return 0; // Retourner 0 si le stock maximum n'est pas trouvé
    }

    //     // Récupérer l'ID de l'item dans le shop
    //     String itemID = ShopGuiPlusApi.getItemStackShopItem(ingredient).getId();
    //     String shopID = ShopGuiPlusApi.getItemStackShopItem(ingredient).getShop().getId();

    //     // Récupérer le stock maximum de l'item
    //     return plugin.getShopConfigManager().getItemValue(shopID, itemID, "stock.max", Integer.class).orElse(1000);
    // }

    public DynaShopType getItemType(String shopID, String itemID) {
        // Récupérer le type d'item depuis la configuration
        return plugin.getShopConfigManager().getTypeDynaShop(shopID, itemID);
    }

    public DynaShopType getIngredientType(String shopID, ItemStack ingredient) {
        // Récupérer l'ID de l'item dans le shop
        // String itemID = ShopGuiPlusApi.getItemStackShopItem(ingredient).getId();
        // String shopID = ShopGuiPlusApi.getItemStackShopItem(ingredient).getShop().getId();
        FoundItem foundItem = findItemInShops(shopID, ingredient);
        if (!foundItem.isFound()) {
            plugin.getLogger().warning("Unable to find ingredient " + ingredient + " in shop " + shopID);
            return DynaShopType.UNKNOWN; // Retourner un type inconnu si l'ingrédient n'est pas trouvé
        }
        String ingredientID = foundItem.getItemID();
        String ingredientShopID = foundItem.getShopID();
        
        // Récupérer le type d'item depuis la configuration
        return plugin.getShopConfigManager().getTypeDynaShop(ingredientShopID, ingredientID);
    }

    // public int calculateStock(String shopID, String itemID, ItemStack item) {
    //     // Récupérer le stock de l'item
    //     Optional<Integer> stockOptional = plugin.getItemDataManager().getStock(shopID, itemID);
    //     if (stockOptional.isPresent()) {
    //         return stockOptional.get();
    //     }
    //     return 0; // Retourner 0 si le stock n'est pas trouvé
    // }

    // public int calculateMaxStock(String shopID, String itemID) {
    //     // Récupérer le stock maximum de l'item
    //     return plugin.getShopConfigManager().getItemValue(shopID, itemID, "stock.max", Integer.class).orElse(1000);
    // }

    /**
     * Détermine si un item est considéré comme populaire ou fréquemment consulté
     * @param shopID L'ID du shop
     * @param itemID L'ID de l'item
     * @return true si l'item est populaire, false sinon
     */
    public boolean isPopularItem(String shopID, String itemID) {
        String key = shopID + ":" + itemID;
        
        // Vérifier d'abord si l'item est dans la liste prédéfinie des items populaires
        if (popularItems.contains(key)) {
            return true;
        }
        
        // Vérifier ensuite si l'item a été fréquemment consulté
        int accessCount = itemAccessCounter.getOrDefault(key, 0);
        
        // Incrémenter le compteur d'accès
        itemAccessCounter.put(key, accessCount + 1);
        
        // Si l'item devient populaire, l'ajouter à la liste
        if (accessCount + 1 >= POPULAR_THRESHOLD && !popularItems.contains(key)) {
            popularItems.add(key);
            return true;
        }
        
        return accessCount >= POPULAR_THRESHOLD;
    }

    /**
     * Charge la liste des items populaires depuis la configuration
     */
    private void loadPopularItems() {
        List<String> configPopularItems = config.getStringList("popular-items");
        if (configPopularItems != null && !configPopularItems.isEmpty()) {
            popularItems.addAll(configPopularItems);
        }
    }

    /**
     * Classe utilitaire pour stocker les informations d'un item trouvé
     */
    public class FoundItem {
        private final String shopID;
        private final String itemID;
        private final boolean found;
        
        public FoundItem(String shopID, String itemID, boolean found) {
            this.shopID = shopID;
            this.itemID = itemID;
            this.found = found;
        }
        
        public String getShopID() { return shopID; }
        public String getItemID() { return itemID; }
        public boolean isFound() { return found; }
    }

    /**
     * Trouve un item dans les shops, en cherchant d'abord dans le shop spécifié
     * puis dans tous les autres shops si nécessaire
     * 
     * @param preferredShopID Le shop où chercher d'abord
     * @param ingredient L'ingrédient à chercher
     * @return Un objet FoundItem contenant les informations de l'item trouvé
     */
    public FoundItem findItemInShops(String preferredShopID, ItemStack ingredient) {
        String shopID = null;
        String itemID = null;
        
        try {
            // D'abord, chercher dans le shop préféré
            Shop shop = ShopGuiPlusApi.getShop(preferredShopID);
            if (shop != null) {
                for (ShopItem shopItem : shop.getShopItems()) {
                    if (customCompare(ingredient, shopItem.getItem())) {
                        itemID = shopItem.getId();
                        shopID = preferredShopID;
                        break;
                    }
                }
            }
            
            // Si pas trouvé, chercher dans tous les shops
            if (itemID == null) {
                for (Shop otherShop : ShopGuiPlusApi.getPlugin().getShopManager().getShops()) {
                    if (!otherShop.getId().equals(preferredShopID)) {
                        for (ShopItem item : otherShop.getShopItems()) {
                            if (customCompare(ingredient, item.getItem())) {
                                itemID = item.getId();
                                shopID = otherShop.getId();
                                break;
                            }
                        }
                        if (itemID != null) break;
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error searching for ingredient " + ingredient + ": " + e.getMessage());
        }
        
        return new FoundItem(shopID, itemID, itemID != null && shopID != null);
    }
    
    /**
     * Compare deux ItemStacks en tenant compte des métadonnées importantes
     */
    public boolean customCompare(ItemStack item1, ItemStack item2) {
        // Comparer les types d'items
        if (item1.getType() != item2.getType()) {
            return false; // Types différents, pas égaux
        }
        
        // Comparer les noms des items
        String name1 = item1.hasItemMeta() && item1.getItemMeta().hasDisplayName() ? item1.getItemMeta().getDisplayName() : "";
        String name2 = item2.hasItemMeta() && item2.getItemMeta().hasDisplayName() ? item2.getItemMeta().getDisplayName() : "";
        
        if (!name1.equals(name2)) {
            return false; // Noms différents, pas égaux
        }
        
        // Comparer les enchantements
        if (!item1.getEnchantments().equals(item2.getEnchantments())) {
            return false; // Enchantements différents, pas égaux
        }
        
        // Comparer les CustomModelData
        boolean hasCustomModelData1 = item1.hasItemMeta() && item1.getItemMeta().hasCustomModelData();
        boolean hasCustomModelData2 = item2.hasItemMeta() && item2.getItemMeta().hasCustomModelData();
        
        if (hasCustomModelData1 != hasCustomModelData2) {
            return false; // Un seul a CustomModelData, pas égaux
        }
        
        if (hasCustomModelData1 && hasCustomModelData2 && 
            item1.getItemMeta().getCustomModelData() != item2.getItemMeta().getCustomModelData()) {
            return false; // CustomModelData différents, pas égaux
        }
        
        return true; // Tous les tests ont réussi, items considérés comme égaux
    }

    // Charger les multiplicateurs depuis la config globale
    private void loadEnchantMultipliers() {
        if (!config.isConfigurationSection("enchant_multipliers")) return;
        ConfigurationSection section = config.getConfigurationSection("enchant_multipliers");
        for (String enchantKey : section.getKeys(false)) {
            ConfigurationSection levels = section.getConfigurationSection(enchantKey);
            Map<Integer, Double> levelMap = new HashMap<>();
            for (String levelStr : levels.getKeys(false)) {
                double mult = levels.getDouble(levelStr, 1.0);
                levelMap.put(Integer.parseInt(levelStr), mult);
            }
            enchantMultipliers.put(enchantKey, levelMap);
        }
    }

    // // Utilitaire pour savoir si l'option est activée pour l'item
    // private boolean isEnchantmentModifierEnabled(String shopID, String itemID) {
    //     return plugin.getShopConfigManager()
    //         .getItemValue(shopID, itemID, "dynaShop.enchantment", Boolean.class)
    //         .orElse(false);
    // }

    // Calcule le multiplicateur d'enchantement pour un item
    public double getEnchantMultiplier(ItemStack item) {
        if (!item.hasItemMeta() || !item.getItemMeta().hasEnchants()) return 1.0;
        double multiplier = 1.0;
        for (Map.Entry<org.bukkit.enchantments.Enchantment, Integer> entry : item.getEnchantments().entrySet()) {
            String enchKey = entry.getKey().getKey().getKey().toUpperCase();
            int level = entry.getValue();
            Map<Integer, Double> levelMap = enchantMultipliers.get(enchKey);
            if (levelMap != null) {
                Double mult = levelMap.get(level);
                if (mult != null) multiplier *= mult;
            }
        }
        return multiplier;
    }
    
}