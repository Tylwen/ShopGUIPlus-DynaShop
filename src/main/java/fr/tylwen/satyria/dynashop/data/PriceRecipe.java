package fr.tylwen.satyria.dynashop.data;

import org.bukkit.Bukkit;
import org.bukkit.Material;
// import org.bukkit.block.BlastFurnace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
// import org.bukkit.inventory.BlastingRecipe;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
// import org.bukkit.inventory.SmokingRecipe;
// import org.bukkit.inventory.StonecuttingRecipe;
// import org.bukkit.inventory.CampfireRecipe;
// import org.bukkit.inventory.BrewerInventory;
import org.bukkit.util.Consumer;

import fr.tylwen.satyria.dynashop.DynaShopPlugin;
import fr.tylwen.satyria.dynashop.data.param.DynaShopType;
import fr.tylwen.satyria.dynashop.data.param.RecipeType;
import net.brcdev.shopgui.ShopGuiPlusApi;
import net.brcdev.shopgui.shop.Shop;
import net.brcdev.shopgui.shop.item.ShopItem;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
// import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class PriceRecipe {
    private final FileConfiguration config;
    
    // Ajouter ces champs à la classe PriceRecipe
    private final Map<String, List<ItemStack>> ingredientsCache = new HashMap<>();
    private final long CACHE_DURATION = 20L * 60L * 5L; // 5 minutes
    // private final long CACHE_DURATION = 20L; // 1 seconde
    private final Map<String, Long> cacheTimestamps = new HashMap<>();

    private final ExecutorService highPriorityExecutor;
    private final Map<String, Integer> itemAccessCounter = new ConcurrentHashMap<>();
    private final List<String> popularItems = new ArrayList<>();
    private static final int POPULAR_THRESHOLD = 10;
    
    // Limiter la profondeur de récursion pour éviter les boucles infinies
    // private static final int MAX_RECURSION_DEPTH = 5;
    // Pool de threads limité pour les calculs asynchrones
    private final ExecutorService recipeExecutor;

    public PriceRecipe(FileConfiguration config) {
        this.config = config;
        
        // Créer un pool de threads dédié et limité pour les calculs de recettes
        this.recipeExecutor = Executors.newFixedThreadPool(2, r -> {
            Thread thread = new Thread(r, "Recipe-Calculator");
            thread.setDaemon(true);
            return thread;
        });
        
        // Créer un pool de threads prioritaire pour les items populaires
        this.highPriorityExecutor = Executors.newFixedThreadPool(1, r -> {
            Thread thread = new Thread(r, "High-Priority-Calculator");
            thread.setDaemon(true);
            thread.setPriority(Thread.MAX_PRIORITY);
            return thread;
        });
        
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

    // /**
    //  * Calcule toutes les valeurs importantes pour une recette en une seule passe
    //  * Cette méthode remplace les multiples appels séparés à calculatePrice, calculateStock, etc.
    //  */
    // // public RecipeCalculationResult calculateRecipeValues(String shopID, String itemID, ItemStack item) {
    // public RecipeCalculationResult calculateRecipeValues(String shopID, String itemID, ItemStack item, List<String> visitedItems) {
    //     // List<String> visitedItems = new ArrayList<>();
        
    //     // Récupérer tous les ingrédients une seule fois
    //     List<ItemStack> ingredients = getIngredients(shopID, itemID, item);
    //     ingredients = consolidateIngredients(ingredients);
        
    //     // Variables pour le calcul
    //     double basePrice = 0.0;
    //     double baseSellPrice = 0.0;
    //     double baseMinBuyPrice = 0.0;
    //     double baseMaxBuyPrice = 0.0;
    //     double baseMinSellPrice = 0.0;
    //     double baseMaxSellPrice = 0.0;
    //     int minAvailableStock = Integer.MAX_VALUE;
    //     int totalMinStock = 0;
    //     int totalMaxStock = 0;
        
    //     // Parcourir les ingrédients une seule fois
    //     for (ItemStack ingredient : ingredients) {
    //         if (ingredient == null || ingredient.getType() == Material.AIR) {
    //             continue;
    //         }
            
    //         // Récupérer toutes les données de l'ingrédient en une fois
    //         String ingredientID = ShopGuiPlusApi.getItemStackShopItem(ingredient).getId();
    //         String ingredientShopID = ShopGuiPlusApi.getItemStackShopItem(ingredient).getShop().getId();
            
    //         // Éviter les boucles infinies
    //         if (visitedItems.contains(ingredientID)) {
    //             continue;
    //         }
    //         visitedItems.add(ingredientID);
            
    //         // Obtenir le type de l'ingrédient
    //         DynaShopType ingredientType = getIngredientType(ingredient);
            
    //         // Selon le type de l'ingrédient, obtenir les valeurs
    //         double ingredientBuyPrice = 0.0;
    //         double ingredientSellPrice = 0.0;
    //         double ingredientMinBuyPrice = 0.0;
    //         double ingredientMaxBuyPrice = 0.0;
    //         double ingredientMinSellPrice = 0.0;
    //         double ingredientMaxSellPrice = 0.0;
    //         int ingredientStock = 0;
    //         int ingredientMinStock = 0;
    //         int ingredientMaxStock = 0;
            
    //         if (ingredientType == DynaShopType.STOCK) {
    //             // Pour les ingrédients en mode STOCK
    //             ingredientBuyPrice = DynaShopPlugin.getInstance().getPriceStock().calculatePrice(ingredientShopID, ingredientID, "buyPrice");
    //             ingredientSellPrice = DynaShopPlugin.getInstance().getPriceStock().calculatePrice(ingredientShopID, ingredientID, "sellPrice");
                
    //             // Récupérer les bornes depuis la configuration
    //             ingredientMinBuyPrice = DynaShopPlugin.getInstance().getShopConfigManager()
    //                 .getItemValue(ingredientShopID, ingredientID, "buyDynamic.min", Double.class)
    //                 .orElse(ingredientBuyPrice * 0.5);
                    
    //             ingredientMaxBuyPrice = DynaShopPlugin.getInstance().getShopConfigManager()
    //                 .getItemValue(ingredientShopID, ingredientID, "buyDynamic.max", Double.class)
    //                 .orElse(ingredientBuyPrice * 2.0);
                    
    //             ingredientMinSellPrice = DynaShopPlugin.getInstance().getShopConfigManager()
    //                 .getItemValue(ingredientShopID, ingredientID, "sellDynamic.min", Double.class)
    //                 .orElse(ingredientSellPrice * 0.5);
                    
    //             ingredientMaxSellPrice = DynaShopPlugin.getInstance().getShopConfigManager()
    //                 .getItemValue(ingredientShopID, ingredientID, "sellDynamic.max", Double.class)
    //                 .orElse(ingredientSellPrice * 2.0);
                
    //             // Récupérer le stock actuel et maximum
    //             ingredientStock = DynaShopPlugin.getInstance().getItemDataManager().getStock(ingredientShopID, ingredientID).orElse(0);
    //             ingredientMinStock = DynaShopPlugin.getInstance().getShopConfigManager()
    //                 .getItemValue(ingredientShopID, ingredientID, "stock.min", Integer.class).orElse(0);
    //             ingredientMaxStock = DynaShopPlugin.getInstance().getShopConfigManager()
    //                 .getItemValue(ingredientShopID, ingredientID, "stock.max", Integer.class).orElse(0);
    //         } else if (ingredientType == DynaShopType.RECIPE) {
    //             // Pour les ingrédients eux-mêmes basés sur des recettes, calculer récursivement
    //             ItemStack ingredientItemStack = ShopGuiPlusApi.getShop(ingredientShopID).getShopItem(ingredientID).getItem();
    //             if (ingredientItemStack != null) {
    //                 List<String> newVisitedItems = new ArrayList<>(visitedItems);
    //                 // RecipeCalculationResult ingredientResult = calculateRecipeValues(ingredientShopID, ingredientID, ingredientItemStack);
    //                 RecipeCalculationResult ingredientResult = calculateRecipeValues(ingredientShopID, ingredientID, ingredientItemStack, newVisitedItems);
                    
    //                 ingredientBuyPrice = ingredientResult.getBuyPrice();
    //                 ingredientSellPrice = ingredientResult.getSellPrice();
    //                 ingredientMinBuyPrice = ingredientResult.getMinBuyPrice();
    //                 ingredientMaxBuyPrice = ingredientResult.getMaxBuyPrice();
    //                 ingredientMinSellPrice = ingredientResult.getMinSellPrice();
    //                 ingredientMaxSellPrice = ingredientResult.getMaxSellPrice();
    //                 ingredientStock = ingredientResult.getStock();
    //                 ingredientMinStock = ingredientResult.getMinStock();
    //                 ingredientMaxStock = ingredientResult.getMaxStock();
    //             }
    //         } else {
    //             // Pour les autres types d'ingrédients (DYNAMIC, etc.)
    //             ingredientBuyPrice = DynaShopPlugin.getInstance().getItemDataManager()
    //                 .getBuyPrice(ingredientShopID, ingredientID)
    //                 .orElse(DynaShopPlugin.getInstance().getShopConfigManager()
    //                     .getItemValue(ingredientShopID, ingredientID, "buyPrice", Double.class)
    //                     .orElse(10.0));
                        
    //             ingredientSellPrice = DynaShopPlugin.getInstance().getItemDataManager()
    //                 .getSellPrice(ingredientShopID, ingredientID)
    //                 .orElse(DynaShopPlugin.getInstance().getShopConfigManager()
    //                     .getItemValue(ingredientShopID, ingredientID, "sellPrice", Double.class)
    //                     .orElse(8.0));
                        
    //             // Récupérer les bornes depuis la configuration
    //             ingredientMinBuyPrice = DynaShopPlugin.getInstance().getShopConfigManager()
    //                 .getItemValue(ingredientShopID, ingredientID, "buyDynamic.min", Double.class)
    //                 .orElse(ingredientBuyPrice * 0.5);
                    
    //             ingredientMaxBuyPrice = DynaShopPlugin.getInstance().getShopConfigManager()
    //                 .getItemValue(ingredientShopID, ingredientID, "buyDynamic.max", Double.class)
    //                 .orElse(ingredientBuyPrice * 2.0);
                    
    //             ingredientMinSellPrice = DynaShopPlugin.getInstance().getShopConfigManager()
    //                 .getItemValue(ingredientShopID, ingredientID, "sellDynamic.min", Double.class)
    //                 .orElse(ingredientSellPrice * 0.5);
                    
    //             ingredientMaxSellPrice = DynaShopPlugin.getInstance().getShopConfigManager()
    //                 .getItemValue(ingredientShopID, ingredientID, "sellDynamic.max", Double.class)
    //                 .orElse(ingredientSellPrice * 2.0);
    //         }
            
    //         // Calculer la contribution de cet ingrédient aux différentes valeurs
    //         int amount = ingredient.getAmount();
    //         basePrice += ingredientBuyPrice * amount;
    //         baseSellPrice += ingredientSellPrice * amount;
    //         baseMinBuyPrice += ingredientMinBuyPrice * amount;
    //         baseMaxBuyPrice += ingredientMaxBuyPrice * amount;
    //         baseMinSellPrice += ingredientMinSellPrice * amount;
    //         baseMaxSellPrice += ingredientMaxSellPrice * amount;
            
    //         // Calcul du stock disponible pour cet ingrédient
    //         int availableForCrafting = ingredientStock / amount;
    //         minAvailableStock = Math.min(minAvailableStock, availableForCrafting);

    //         // Stock minimum
    //         int minAvailableForCrafting = ingredientMinStock / amount;
    //         totalMinStock += minAvailableForCrafting;

    //         // Stock maximum
    //         int maxAvailableForCrafting = ingredientMaxStock / amount;
    //         totalMaxStock += maxAvailableForCrafting;
            
    //         // Optimisation: sortir tôt si on trouve un stock zéro
    //         if (minAvailableStock == 0) break;
    //     }
        
    //     // Appliquer le modificateur de recette
    //     double modifier = getRecipeModifier(item);
    //     double finalBuyPrice = basePrice * modifier;
    //     double finalSellPrice = baseSellPrice * modifier;
    //     double finalMinBuyPrice = baseMinBuyPrice * modifier;
    //     double finalMaxBuyPrice = baseMaxBuyPrice * modifier;
    //     double finalMinSellPrice = baseMinSellPrice * modifier;
    //     double finalMaxSellPrice = baseMaxSellPrice * modifier;
        
    //     // Vérifier que le prix de vente n'est pas supérieur au prix d'achat
    //     if (finalSellPrice > finalBuyPrice - DynamicPrice.MIN_MARGIN) {
    //         finalSellPrice = finalBuyPrice - DynamicPrice.MIN_MARGIN;
    //     }
        
    //     // Vérifier que les bornes sont respectées
    //     finalBuyPrice = Math.max(finalMinBuyPrice, Math.min(finalBuyPrice, finalMaxBuyPrice));
    //     finalSellPrice = Math.max(finalMinSellPrice, Math.min(finalSellPrice, finalMaxSellPrice));
        
    //     // Ajuster le stock maximum et actuel
    //     int finalStock = (minAvailableStock == Integer.MAX_VALUE) ? 0 : minAvailableStock;
        
    //     // Mettre en cache tous les résultats
    //     DynaShopPlugin.getInstance().cacheRecipePrice(shopID, itemID, "buyPrice", finalBuyPrice);
    //     DynaShopPlugin.getInstance().cacheRecipePrice(shopID, itemID, "sellPrice", finalSellPrice);
    //     DynaShopPlugin.getInstance().cacheRecipePrice(shopID, itemID, "buyDynamic.min", finalMinBuyPrice);
    //     DynaShopPlugin.getInstance().cacheRecipePrice(shopID, itemID, "buyDynamic.max", finalMaxBuyPrice);
    //     DynaShopPlugin.getInstance().cacheRecipePrice(shopID, itemID, "sellDynamic.min", finalMinSellPrice);
    //     DynaShopPlugin.getInstance().cacheRecipePrice(shopID, itemID, "sellDynamic.max", finalMaxSellPrice);
    //     DynaShopPlugin.getInstance().cacheRecipeStock(shopID, itemID, "stock", finalStock);
    //     DynaShopPlugin.getInstance().cacheRecipeStock(shopID, itemID, "minstock", totalMinStock);
    //     DynaShopPlugin.getInstance().cacheRecipeStock(shopID, itemID, "maxstock", totalMaxStock);
        
    //     return new RecipeCalculationResult(
    //         finalBuyPrice, finalSellPrice,
    //         finalMinBuyPrice, finalMaxBuyPrice,
    //         finalMinSellPrice, finalMaxSellPrice,
    //         finalStock, totalMinStock, totalMaxStock
    //     );
    // }
    public RecipeCalculationResult calculateRecipeValues(String shopID, String itemID, ItemStack item, List<String> visitedItems) {
        // Récupérer tous les ingrédients une seule fois
        List<ItemStack> ingredients = getIngredients(shopID, itemID, item);
        ingredients = consolidateIngredients(ingredients);
        
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
        
        // Parcourir les ingrédients une seule fois
        for (ItemStack ingredient : ingredients) {
            if (ingredient == null || ingredient.getType() == Material.AIR) {
                continue;
            }
            
            // // // Récupérer toutes les données de l'ingrédient en une fois
            // // String ingredientID = ShopGuiPlusApi.getItemStackShopItem(ingredient).getId();
            // // String ingredientShopID = ShopGuiPlusApi.getItemStackShopItem(ingredient).getShop().getId();
            // String ingredientShopID = null;
            // String ingredientID = null;

            // try {
            //     // Shop shop = ShopGuiPlusApi.getShop(shopID);
            //     // ShopItem shopItem = shop.getShopItem(itemID);
            //     // if (shopItem != null) {
            //     //     ingredientID = shopItem.getId();
            //     //     ingredientShopID = shop.getId();
            //     // }
            //     // ingredientShopID = shop.getId();
            //     // ingredientID = shop.getShopItem(itemID).getId();
                
            //     // if (ingredientID == null) {
            //     //     ingredientShopID = ShopGuiPlusApi.getItemStackShopItem(ingredient).getShop().getId();
            //     //     ingredientID = ShopGuiPlusApi.getItemStackShopItem(ingredient).getId();
            //     // }
            //     // D'abord, essayer de trouver l'ingrédient dans le shop courant
            //     Shop currentShop = ShopGuiPlusApi.getShop(shopID);
            //     for (ShopItem item2 : currentShop.getShopItems()) {
            //         if (item2.getItem().getType() == ingredient.getType()) {
            //             ingredientID = item2.getId();
            //             ingredientShopID = shopID;
            //             break;
            //         }
            //     }
                
            //     // Si non trouvé dans le shop courant, chercher dans tous les shops
            //     if (ingredientID == null) {
            //         ShopItem shopItem = ShopGuiPlusApi.getItemStackShopItem(ingredient);
            //         if (shopItem != null) {
            //             ingredientID = shopItem.getId();
            //             ingredientShopID = shopItem.getShop().getId();
            //         }
            //     }
            // } catch (Exception e) {
            //     ingredientShopID = ShopGuiPlusApi.getItemStackShopItem(ingredient).getShop().getId();
            //     ingredientID = ShopGuiPlusApi.getItemStackShopItem(ingredient).getId();
            // }

            // if (ingredientID == null || ingredientShopID == null) {
            //     DynaShopPlugin.getInstance().getLogger().warning("ID ou ShopID manquant pour l'ingrédient " + ingredient);
            //     continue; // Passer à l'ingrédient suivant si l'ID est manquant
            // // } else {
            // //     DynaShopPlugin.getInstance().getLogger().info("ID de l'ingrédient: " + ingredientID + ", ShopID: " + ingredientShopID);
            // //     // DynaShopPlugin.getInstance().getLogger().info("Ingrédient: " + ingredient);
            // //     // DynaShopPlugin.getInstance().getLogger().info("ItemStack: " + item);
            // }
            FoundItem foundItem = findItemInShops(shopID, ingredient);
            if (!foundItem.isFound()) {
                DynaShopPlugin.getInstance().getLogger().warning("ID ou ShopID manquant pour l'ingrédient " + ingredient);
                continue; // Passer à l'ingrédient suivant si l'ID est manquant
            }
            String ingredientID = foundItem.getItemID();
            String ingredientShopID = foundItem.getShopID();

            // Éviter les boucles infinies
            if (visitedItems.contains(ingredientID)) {
                continue;
            }
            visitedItems.add(ingredientID);
            
            // // Obtenir le type de l'ingrédient
            // DynaShopType ingredientType = getIngredientType(ingredient);
            
            DynamicPrice ingredientPrice = DynaShopPlugin.getInstance().getDynaShopListener()
                .getOrLoadPrice(ingredientShopID, ingredientID, ingredient);
            if (ingredientPrice == null) {
                DynaShopPlugin.getInstance().getLogger().warning("Prix non trouvé pour l'ingrédient " + ingredientID + " dans le shop " + ingredientShopID);
                continue; // Passer à l'ingrédient suivant si le prix n'est pas trouvé
            }
                
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
            basePrice += ingredientBuyPrice * amount;
            baseSellPrice += ingredientSellPrice * amount;
            baseMinBuyPrice += ingredientMinBuyPrice * amount;
            baseMaxBuyPrice += ingredientMaxBuyPrice * amount;
            baseMinSellPrice += ingredientMinSellPrice * amount;
            baseMaxSellPrice += ingredientMaxSellPrice * amount;
            
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
        double modifier = getRecipeModifier(item);
        double finalBuyPrice = basePrice * modifier;
        double finalSellPrice = baseSellPrice * modifier;
        double finalMinBuyPrice = baseMinBuyPrice * modifier;
        double finalMaxBuyPrice = baseMaxBuyPrice * modifier;
        double finalMinSellPrice = baseMinSellPrice * modifier;
        double finalMaxSellPrice = baseMaxSellPrice * modifier;
        
        // Vérifier que le prix de vente n'est pas supérieur au prix d'achat
        if (finalSellPrice > finalBuyPrice - DynamicPrice.MIN_MARGIN) {
            finalSellPrice = finalBuyPrice - DynamicPrice.MIN_MARGIN;
        }
        
        // Vérifier que les bornes sont respectées
        finalBuyPrice = Math.max(finalMinBuyPrice, Math.min(finalBuyPrice, finalMaxBuyPrice));
        finalSellPrice = Math.max(finalMinSellPrice, Math.min(finalSellPrice, finalMaxSellPrice));
        
        // Ajuster le stock maximum et actuel
        int finalStock = (minAvailableStock == Integer.MAX_VALUE) ? 0 : minAvailableStock;
        
        // Mettre en cache tous les résultats
        DynaShopPlugin.getInstance().cacheRecipePrice(shopID, itemID, "buyPrice", finalBuyPrice);
        DynaShopPlugin.getInstance().cacheRecipePrice(shopID, itemID, "sellPrice", finalSellPrice);
        DynaShopPlugin.getInstance().cacheRecipePrice(shopID, itemID, "buyDynamic.min", finalMinBuyPrice);
        DynaShopPlugin.getInstance().cacheRecipePrice(shopID, itemID, "buyDynamic.max", finalMaxBuyPrice);
        DynaShopPlugin.getInstance().cacheRecipePrice(shopID, itemID, "sellDynamic.min", finalMinSellPrice);
        DynaShopPlugin.getInstance().cacheRecipePrice(shopID, itemID, "sellDynamic.max", finalMaxSellPrice);
        DynaShopPlugin.getInstance().cacheRecipeStock(shopID, itemID, "stock", finalStock);
        DynaShopPlugin.getInstance().cacheRecipeStock(shopID, itemID, "minstock", totalMinStock);
        DynaShopPlugin.getInstance().cacheRecipeStock(shopID, itemID, "maxstock", totalMaxStock);
        
        return new RecipeCalculationResult(
            finalBuyPrice, finalSellPrice,
            finalMinBuyPrice, finalMaxBuyPrice,
            finalMinSellPrice, finalMaxSellPrice,
            finalStock, totalMinStock, totalMaxStock
        );
    }
    // // Méthode synchrone qui attend le résultat du calcul asynchrone
    // public RecipeCalculationResult calculateRecipeValues(String shopID, String itemID, ItemStack item, List<String> visitedItems) {
    //     try {
    //         return calculateRecipeValuesAsync(shopID, itemID, item, visitedItems).get();
    //     } catch (Exception e) {
    //         DynaShopPlugin.getInstance().getLogger().severe("Erreur lors du calcul des valeurs de recette: " + e.getMessage());
    //         // Retourner des valeurs par défaut en cas d'erreur
    //         return new RecipeCalculationResult(
    //             10.0, 8.0,  // prix par défaut
    //             5.0, 20.0,  // bornes d'achat par défaut
    //             4.0, 16.0,  // bornes de vente par défaut
    //             0, 0, 1000  // stock par défaut
    //         );
    //     }
    // }

    /**
     * Version asynchrone pour calculer toutes les valeurs de recette en une fois
     */
    public void calculateRecipeValuesAsync(String shopID, String itemID, ItemStack item, Consumer<RecipeCalculationResult> callback) {
        CompletableFuture.supplyAsync(() -> {
            try {
                return calculateRecipeValues(shopID, itemID, item, new ArrayList<>());
            } catch (Exception e) {
                DynaShopPlugin.getInstance().getLogger().warning("Erreur lors du calcul des valeurs pour " 
                    + shopID + ":" + itemID + ": " + e.getMessage());
                // Valeurs par défaut en cas d'erreur
                return new RecipeCalculationResult(10.0, 8.0, 5.0, 20.0, 4.0, 16.0, 0, 0, 0);
            }
        }, recipeExecutor).thenAcceptAsync(result -> {
            Bukkit.getScheduler().runTask(DynaShopPlugin.getInstance(), () -> {
                try {
                    callback.accept(result);
                } catch (Exception e) {
                    DynaShopPlugin.getInstance().getLogger().warning("Erreur dans le callback: " + e.getMessage());
                }
            });
        });
    }

    // /**
    //  * Calcule toutes les valeurs importantes pour une recette en une seule passe de manière asynchrone
    //  */
    // public CompletableFuture<RecipeCalculationResult> calculateRecipeValuesAsync(String shopID, String itemID, ItemStack item, List<String> visitedItems) {
    //     CompletableFuture<RecipeCalculationResult> future = new CompletableFuture<>();
        
    //     // Exécuter les calculs dans une tâche asynchrone
    //     Bukkit.getScheduler().runTaskAsynchronously(DynaShopPlugin.getInstance(), () -> {
    //         try {
    //             // Récupérer les ingrédients
    //             List<ItemStack> ingredients = getIngredients(shopID, itemID, item);
    //             ingredients = consolidateIngredients(ingredients);
                
    //             // Variables pour le calcul
    //             double basePrice = 0.0;
    //             double baseSellPrice = 0.0;
    //             double baseMinBuyPrice = 0.0;
    //             double baseMaxBuyPrice = 0.0;
    //             double baseMinSellPrice = 0.0;
    //             double baseMaxSellPrice = 0.0;
    //             int minAvailableStock = Integer.MAX_VALUE;
    //             int totalMinStock = 0;
    //             int totalMaxStock = 0;
                
    //             // Parcourir les ingrédients de manière synchrone dans cette tâche asynchrone
    //             for (ItemStack ingredient : ingredients) {
    //                 if (ingredient == null || ingredient.getType() == Material.AIR) {
    //                     continue;
    //                 }
                    
    //                 // Récupérer les données de l'ingrédient
    //                 String ingredientID = ShopGuiPlusApi.getItemStackShopItem(ingredient).getId();
    //                 String ingredientShopID = ShopGuiPlusApi.getItemStackShopItem(ingredient).getShop().getId();
                    
    //                 // Éviter les boucles infinies
    //                 if (visitedItems.contains(ingredientID)) {
    //                     continue;
    //                 }
    //                 visitedItems.add(ingredientID);
                    
    //                 // Obtenir le type de l'ingrédient
    //                 DynaShopType ingredientType = getIngredientType(ingredient);
                    
    //                 // Variables pour stocker les valeurs de l'ingrédient
    //                 double ingredientBuyPrice = 0.0;
    //                 double ingredientSellPrice = 0.0;
    //                 double ingredientMinBuyPrice = 0.0;
    //                 double ingredientMaxBuyPrice = 0.0;
    //                 double ingredientMinSellPrice = 0.0;
    //                 double ingredientMaxSellPrice = 0.0;
    //                 int ingredientStock = 0;
    //                 int ingredientMinStock = 0;
    //                 int ingredientMaxStock = 0;
                    
    //                 // Calculer les valeurs selon le type d'ingrédient
    //                 if (ingredientType == DynaShopType.STOCK) {
    //                     // Code pour les ingrédients en mode STOCK
    //                     ingredientBuyPrice = DynaShopPlugin.getInstance().getPriceStock().calculatePrice(ingredientShopID, ingredientID, "buyPrice");
    //                     ingredientSellPrice = DynaShopPlugin.getInstance().getPriceStock().calculatePrice(ingredientShopID, ingredientID, "sellPrice");
                        
    //                     // Récupérer les bornes
    //                     ingredientMinBuyPrice = DynaShopPlugin.getInstance().getShopConfigManager()
    //                         .getItemValue(ingredientShopID, ingredientID, "buyDynamic.min", Double.class)
    //                         .orElse(ingredientBuyPrice * 0.5);
    //                     ingredientMaxBuyPrice = DynaShopPlugin.getInstance().getShopConfigManager()
    //                         .getItemValue(ingredientShopID, ingredientID, "buyDynamic.max", Double.class)
    //                         .orElse(ingredientBuyPrice * 2.0);
    //                     ingredientMinSellPrice = DynaShopPlugin.getInstance().getShopConfigManager()
    //                         .getItemValue(ingredientShopID, ingredientID, "sellDynamic.min", Double.class)
    //                         .orElse(ingredientSellPrice * 0.5);
    //                     ingredientMaxSellPrice = DynaShopPlugin.getInstance().getShopConfigManager()
    //                         .getItemValue(ingredientShopID, ingredientID, "sellDynamic.max", Double.class)
    //                         .orElse(ingredientSellPrice * 2.0);
                            
    //                     // Récupérer le stock actuel et maximum
    //                     ingredientStock = DynaShopPlugin.getInstance().getItemDataManager().getStock(ingredientShopID, ingredientID).orElse(0);
    //                     ingredientMinStock = DynaShopPlugin.getInstance().getShopConfigManager()
    //                         .getItemValue(ingredientShopID, ingredientID, "stock.min", Integer.class).orElse(0);
    //                     ingredientMaxStock = DynaShopPlugin.getInstance().getShopConfigManager()
    //                         .getItemValue(ingredientShopID, ingredientID, "stock.max", Integer.class).orElse(0);
    //                 } else if (ingredientType == DynaShopType.RECIPE) {
    //                     // Code pour les ingrédients basés sur des recettes
    //                     ItemStack ingredientItemStack = ShopGuiPlusApi.getShop(ingredientShopID).getShopItem(ingredientID).getItem();
    //                     if (ingredientItemStack != null) {
    //                         List<String> newVisitedItems = new ArrayList<>(visitedItems);
    //                         RecipeCalculationResult ingredientResult = calculateRecipeValues(ingredientShopID, ingredientID, ingredientItemStack, newVisitedItems);
                            
    //                         ingredientBuyPrice = ingredientResult.getBuyPrice();
    //                         ingredientSellPrice = ingredientResult.getSellPrice();
    //                         ingredientMinBuyPrice = ingredientResult.getMinBuyPrice();
    //                         ingredientMaxBuyPrice = ingredientResult.getMaxBuyPrice();
    //                         ingredientMinSellPrice = ingredientResult.getMinSellPrice();
    //                         ingredientMaxSellPrice = ingredientResult.getMaxSellPrice();
    //                         ingredientStock = ingredientResult.getStock();
    //                         ingredientMinStock = ingredientResult.getMinStock();
    //                         ingredientMaxStock = ingredientResult.getMaxStock();
    //                     }
    //                 } else {
    //                     // Pour les autres types d'ingrédients (DYNAMIC, etc.)
    //                     ingredientBuyPrice = DynaShopPlugin.getInstance().getItemDataManager()
    //                         .getBuyPrice(ingredientShopID, ingredientID)
    //                         .orElse(DynaShopPlugin.getInstance().getShopConfigManager()
    //                             .getItemValue(ingredientShopID, ingredientID, "buyPrice", Double.class)
    //                             .orElse(10.0));
                                
    //                     ingredientSellPrice = DynaShopPlugin.getInstance().getItemDataManager()
    //                         .getSellPrice(ingredientShopID, ingredientID)
    //                         .orElse(DynaShopPlugin.getInstance().getShopConfigManager()
    //                             .getItemValue(ingredientShopID, ingredientID, "sellPrice", Double.class)
    //                             .orElse(8.0));
                                
    //                     // Récupérer les bornes depuis la configuration
    //                     ingredientMinBuyPrice = DynaShopPlugin.getInstance().getShopConfigManager()
    //                         .getItemValue(ingredientShopID, ingredientID, "buyDynamic.min", Double.class)
    //                         .orElse(ingredientBuyPrice * 0.5);
                            
    //                     ingredientMaxBuyPrice = DynaShopPlugin.getInstance().getShopConfigManager()
    //                         .getItemValue(ingredientShopID, ingredientID, "buyDynamic.max", Double.class)
    //                         .orElse(ingredientBuyPrice * 2.0);
                            
    //                     ingredientMinSellPrice = DynaShopPlugin.getInstance().getShopConfigManager()
    //                         .getItemValue(ingredientShopID, ingredientID, "sellDynamic.min", Double.class)
    //                         .orElse(ingredientSellPrice * 0.5);
                            
    //                     ingredientMaxSellPrice = DynaShopPlugin.getInstance().getShopConfigManager()
    //                         .getItemValue(ingredientShopID, ingredientID, "sellDynamic.max", Double.class)
    //                         .orElse(ingredientSellPrice * 2.0);
    //                 }
                    
    //                 // Ajouter la contribution de cet ingrédient aux totaux
    //                 int amount = ingredient.getAmount();
    //                 basePrice += ingredientBuyPrice * amount;
    //                 baseSellPrice += ingredientSellPrice * amount;
    //                 baseMinBuyPrice += ingredientMinBuyPrice * amount;
    //                 baseMaxBuyPrice += ingredientMaxBuyPrice * amount;
    //                 baseMinSellPrice += ingredientMinSellPrice * amount;
    //                 baseMaxSellPrice += ingredientMaxSellPrice * amount;
                    
    //                 // Calcul du stock
    //                 int availableForCrafting = ingredientStock / amount;
    //                 minAvailableStock = Math.min(minAvailableStock, availableForCrafting);

    //                 // Stock minimum
    //                 int minAvailableForCrafting = ingredientMinStock / amount;
    //                 totalMinStock += minAvailableForCrafting;

    //                 // Stock maximum
    //                 int maxAvailableForCrafting = ingredientMaxStock / amount;
    //                 totalMaxStock += maxAvailableForCrafting;
                    
    //                 // Sortir tôt si stock zéro
    //                 if (minAvailableStock == 0) break;
    //             }
                
    //             // Finaliser les calculs
    //             double modifier = getRecipeModifier(item);
    //             double finalBuyPrice = basePrice * modifier;
    //             double finalSellPrice = baseSellPrice * modifier;
    //             double finalMinBuyPrice = baseMinBuyPrice * modifier;
    //             double finalMaxBuyPrice = baseMaxBuyPrice * modifier;
    //             double finalMinSellPrice = baseMinSellPrice * modifier;
    //             double finalMaxSellPrice = baseMaxSellPrice * modifier;
        
    //             // Vérifier que le prix de vente n'est pas supérieur au prix d'achat
    //             if (finalSellPrice > finalBuyPrice - DynamicPrice.MIN_MARGIN) {
    //                 finalSellPrice = finalBuyPrice - DynamicPrice.MIN_MARGIN;
    //             }
                
    //             // Vérifier que les bornes sont respectées
    //             finalBuyPrice = Math.max(finalMinBuyPrice, Math.min(finalBuyPrice, finalMaxBuyPrice));
    //             finalSellPrice = Math.max(finalMinSellPrice, Math.min(finalSellPrice, finalMaxSellPrice));
                
    //             // Ajuster le stock maximum et actuel
    //             int finalStock = (minAvailableStock == Integer.MAX_VALUE) ? 0 : minAvailableStock;
                
    //             // Créer le résultat
    //             RecipeCalculationResult result = new RecipeCalculationResult(
    //                 finalBuyPrice, finalSellPrice,
    //                 finalMinBuyPrice, finalMaxBuyPrice,
    //                 finalMinSellPrice, finalMaxSellPrice,
    //                 finalStock, totalMinStock, totalMaxStock
    //             );
                
    //             // Mettre en cache les résultats et compléter le future
    //             cacheResults(shopID, itemID, result);
    //             future.complete(result);
                
    //         } catch (Exception e) {
    //             future.completeExceptionally(e);
    //         }
    //     });
        
    //     return future;
    // }
    
    // // Méthode utilitaire pour mettre en cache les résultats
    // private void cacheResults(String shopID, String itemID, RecipeCalculationResult result) {
    //     DynaShopPlugin.getInstance().cacheRecipePrice(shopID, itemID, "buyPrice", result.getBuyPrice());
    //     DynaShopPlugin.getInstance().cacheRecipePrice(shopID, itemID, "sellPrice", result.getSellPrice());
    //     DynaShopPlugin.getInstance().cacheRecipePrice(shopID, itemID, "buyDynamic.min", result.getMinBuyPrice());
    //     DynaShopPlugin.getInstance().cacheRecipePrice(shopID, itemID, "buyDynamic.max", result.getMaxBuyPrice());
    //     DynaShopPlugin.getInstance().cacheRecipePrice(shopID, itemID, "sellDynamic.min", result.getMinSellPrice());
    //     DynaShopPlugin.getInstance().cacheRecipePrice(shopID, itemID, "sellDynamic.max", result.getMaxSellPrice());
    //     DynaShopPlugin.getInstance().cacheRecipeStock(shopID, itemID, "stock", result.getStock());
    //     DynaShopPlugin.getInstance().cacheRecipeStock(shopID, itemID, "minstock", result.getMinStock());
    //     DynaShopPlugin.getInstance().cacheRecipeStock(shopID, itemID, "maxstock", result.getMaxStock());
    // }

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
        }
    }
    
    // public enum RecipeType {
    //     NONE,
    //     CRAFTING,
    //     SMELTING,
    //     BLAST_FURNACE,
    //     SMOKER,
    //     STONECUTTER
    // }

    public double calculateBuyPrice(String shopID, String itemID, ItemStack item, List<String> visitedItems) {
        return calculatePrice(shopID, itemID, item, "buyPrice", visitedItems);
    }

    public double calculateSellPrice(String shopID, String itemID, ItemStack item, List<String> visitedItems) {
        return calculatePrice(shopID, itemID, item, "sellPrice", visitedItems);
    }

    public double calculatePrice(String shopID, String itemID, ItemStack item, String typePrice, List<String> visitedItems) {
        // // Protection contre les récursions trop profondes
        // if (visitedItems.size() > MAX_RECURSION_DEPTH) {
        //     return 10.0; // Valeur par défaut
        // }

        List<ItemStack> ingredients = getIngredients(shopID, itemID, item);
        ingredients = consolidateIngredients(ingredients);
        double basePrice = 0.0;

        // Calculer le prix de base en fonction des ingrédients
        for (ItemStack ingredient : ingredients) {
            if (ingredient == null || ingredient.getType() == Material.AIR) {
                continue; // Ignorer les ingrédients invalides
            }
            // Copier la liste des items visités pour éviter les modifications dans la récursion
            List<String> newVisitedItems = new ArrayList<>(visitedItems);
            double ingredientPrice = getIngredientPrice(shopID, ingredient, typePrice, newVisitedItems);
            basePrice += ingredientPrice * ingredient.getAmount(); // Multiplier par la quantité de l'ingrédient
        }

        // Appliquer le modificateur en fonction du type de recette
        double modifier = getRecipeModifier(item);
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
    public int calculateStock(String shopID, String itemID, ItemStack item, List<String> visitedItems) {
        List<ItemStack> ingredients = getIngredients(shopID, itemID, item);
        ingredients = consolidateIngredients(ingredients);

        int minAvailableStock = Integer.MAX_VALUE;
        for (ItemStack ingredient : ingredients) {
            if (ingredient == null || ingredient.getType() == Material.AIR) { continue; }

            List<String> newVisitedItems = new ArrayList<>(visitedItems);
            int ingredientStock = getIngredientStock(shopID, ingredient, newVisitedItems);
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
    //     Bukkit.getScheduler().runTaskAsynchronously(DynaShopPlugin.getInstance(), () -> {
    //         List<String> visitedItems = new ArrayList<>();
    //         int stock = calculateStock(shopID, itemID, item, visitedItems);

    //         DynaShopPlugin.getInstance().cacheRecipeStock(shopID, itemID, "stock", stock);
    //         // Revenir au thread principal pour mettre à jour le stock
    //         Bukkit.getScheduler().runTask(DynaShopPlugin.getInstance(), () -> callback.accept(stock));
    //     });
    // }
    /**
     * Version entièrement asynchrone du calcul de stock
     */
    public void calculateStockAsync(String shopID, String itemID, ItemStack item, Consumer<Integer> callback) {
        CompletableFuture.supplyAsync(() -> {
            try {
                List<String> visitedItems = new ArrayList<>();
                return calculateStock(shopID, itemID, item, visitedItems);
            } catch (Exception e) {
                DynaShopPlugin.getInstance().getLogger().warning("Erreur lors du calcul du stock pour " + shopID + ":" + itemID + ": " + e.getMessage());
                return 0;
            }
        }, recipeExecutor).thenAcceptAsync(stock -> {
            Bukkit.getScheduler().runTask(DynaShopPlugin.getInstance(), () -> {
                try {
                    callback.accept(stock);
                } catch (Exception e) {
                    DynaShopPlugin.getInstance().getLogger().warning("Erreur dans le callback de stock: " + e.getMessage());
                }
            });
        });
    }

    public int calculateMaxStock(String shopID, String itemID, ItemStack item, List<String> visitedItems) {
        List<ItemStack> ingredients = getIngredients(shopID, itemID, item);
        ingredients = consolidateIngredients(ingredients);
        int maxStock = 0;
        // Calculer le stock maximum en fonction des ingrédients
        for (ItemStack ingredient : ingredients) {
            if (ingredient == null || ingredient.getType() == Material.AIR) {
                continue; // Ignorer les ingrédients invalides
            }
            int ingredientStock = getIngredientMaxStock(shopID, ingredient, visitedItems);
            // maxStock += ingredientStock * ingredient.getAmount(); // Multiplier par la quantité de l'ingrédient
            maxStock += ingredientStock / ingredient.getAmount(); // Diviser par la quantité de l'ingrédient
            // maxStock += ingredientStock; // Ajouter le stock maximum de l'ingrédient
        }
        return maxStock;
    }

    // public void calculatePriceAsync(String shopID, String itemID, ItemStack item, String typePrice, Consumer<Double> callback) {
    //     // Exécuter le calcul de prix coûteux dans un thread séparé
    //     Bukkit.getScheduler().runTaskAsynchronously(DynaShopPlugin.getInstance(), () -> {
    //         List<String> visitedItems = new ArrayList<>();
    //         double price = calculatePrice(shopID, itemID, item, typePrice, visitedItems);
            
    //         // Cacher le prix calculé
    //         DynaShopPlugin.getInstance().cacheRecipePrice(shopID, itemID, typePrice, price);
            
    //         // Revenir au thread principal pour exécuter le callback
    //         Bukkit.getScheduler().runTask(DynaShopPlugin.getInstance(), () -> callback.accept(price));
    //         // try {
    //         //     // Forcer l'obtention d'une nouvelle connexion à la base de données pour cette opération asynchrone
    //         //     DynaShopPlugin.getInstance().getDataManager().reloadDatabaseConnection();
                
    //         //     List<String> visitedItems = new ArrayList<>();
    //         //     double price = calculatePrice(shopID, itemID, item, typePrice, visitedItems);
                
    //         //     // Cacher le prix calculé
    //         //     DynaShopPlugin.getInstance().cacheRecipePrice(shopID, itemID, typePrice, price);
                
    //         //     // Revenir au thread principal pour exécuter le callback
    //         //     Bukkit.getScheduler().runTask(DynaShopPlugin.getInstance(), () -> {
    //         //         try {
    //         //             callback.accept(price);
    //         //         } catch (Exception e) {
    //         //             DynaShopPlugin.getInstance().getLogger().severe("Erreur dans le callback: " + e.getMessage());
    //         //             e.printStackTrace();
    //         //         }
    //         //     });
    //         // } catch (Exception e) {
    //         //     DynaShopPlugin.getInstance().getLogger().severe("Erreur lors du calcul asynchrone du prix: " + e.getMessage());
    //         //     e.printStackTrace();
                
    //         //     // Revenir au thread principal avec une valeur par défaut
    //         //     Bukkit.getScheduler().runTask(DynaShopPlugin.getInstance(), () -> {
    //         //         try {
    //         //             callback.accept(10.0); // Valeur par défaut en cas d'erreur
    //         //         } catch (Exception ex) {
    //         //             DynaShopPlugin.getInstance().getLogger().severe("Erreur dans le callback d'erreur: " + ex.getMessage());
    //         //         }
    //         //     });
    //         // }
    //     });
    // }
    // /**
    //  * Version entièrement asynchrone du calcul de prix
    //  */
    // public void calculatePriceAsync(String shopID, String itemID, ItemStack item, String typePrice, Consumer<Double> callback) {
    //     CompletableFuture.supplyAsync(() -> {
    //         try {
    //             List<String> visitedItems = new ArrayList<>();
    //             return calculatePrice(shopID, itemID, item, typePrice, visitedItems);
    //         } catch (Exception e) {
    //             DynaShopPlugin.getInstance().getLogger().warning("Erreur lors du calcul du prix pour " + shopID + ":" + itemID + ": " + e.getMessage());
    //             return 10.0; // Valeur par défaut en cas d'erreur
    //         }
    //     }, recipeExecutor).thenAcceptAsync(price -> {
    //         Bukkit.getScheduler().runTask(DynaShopPlugin.getInstance(), () -> {
    //             try {
    //                 callback.accept(price);
    //             } catch (Exception e) {
    //                 DynaShopPlugin.getInstance().getLogger().warning("Erreur dans le callback de prix: " + e.getMessage());
    //             }
    //         });
    //     });
    // }
    /**
     * Version entièrement asynchrone du calcul de prix avec priorisation
     */
    public void calculatePriceAsync(String shopID, String itemID, ItemStack item, String typePrice, Consumer<Double> callback) {
        // Vérifier si c'est un item populaire ou fréquemment consulté
        boolean isHighPriority = isPopularItem(shopID, itemID);
        
        // Définir le fournisseur de calcul
        Supplier<Double> priceCalculator = () -> {
            try {
                List<String> visitedItems = new ArrayList<>();
                return calculatePrice(shopID, itemID, item, typePrice, visitedItems);
            } catch (Exception e) {
                DynaShopPlugin.getInstance().getLogger().warning("Erreur lors du calcul du prix pour " + shopID + ":" + itemID + ": " + e.getMessage());
                return 10.0; // Valeur par défaut en cas d'erreur
            }
        };
        
        // Ajuster la priorité d'exécution
        ExecutorService executor = isHighPriority ? highPriorityExecutor : recipeExecutor;
        
        CompletableFuture.supplyAsync(priceCalculator, executor)
            .thenAcceptAsync(price -> {
                Bukkit.getScheduler().runTask(DynaShopPlugin.getInstance(), () -> {
                    try {
                        callback.accept(price);
                    } catch (Exception e) {
                        DynaShopPlugin.getInstance().getLogger().warning("Erreur dans le callback de prix: " + e.getMessage());
                    }
                });
            });
    }

    // public double calculateDecay(String shopID, String itemID, ItemStack item, String typePrice, List<String> visitedItems) {
    //     List<ItemStack> ingredients = getIngredients(shopID, itemID, item);
    //     ingredients = consolidateIngredients(ingredients);
    //     double basePrice = 0.0;

    //     // Calculer le prix de base en fonction des ingrédients
    //     for (ItemStack ingredient : ingredients) {
    //         if (ingredient == null || ingredient.getType() == Material.AIR) {
    //             continue; // Ignorer les ingrédients invalides
    //         }
    //         double ingredientPrice = getIngredientPrice(ingredient, typePrice, visitedItems);
    //         basePrice += ingredientPrice * ingredient.getAmount(); // Multiplier par la quantité de l'ingrédient
    //     }

    //     // Appliquer le modificateur en fonction du type de recette
    //     double modifier = getRecipeModifier(item);
    //     return basePrice * modifier;
    // }

    // public List<ItemStack> getIngredients(String shopdID, String itemID, ItemStack item) {
    //     List<ItemStack> ingredients = new ArrayList<>();
    //     String typeRecipe = DynaShopPlugin.getInstance().getShopConfigManager().getTypeRecipe(shopdID, itemID);

    //     // for (Recipe recipe : item.getItemMeta().getPersistentDataContainer().getRecipesFor(item)) {
    //     for (Recipe recipe : DynaShopPlugin.getInstance().getServer().getRecipesFor(item)) {
    //         if (recipe instanceof ShapedRecipe shapedRecipe && typeRecipe.equalsIgnoreCase("SHAPED")) {
    //             // // ingredients.addAll(shapedRecipe.getIngredientMap().values());
    //             // shapedRecipe.getIngredientMap().forEach((key, ingredient) -> {
    //             //     if (ingredient != null && ingredient.getType() != Material.AIR) {
    //             //         // ingredients.add(ingredient.clone()); // Ajouter une copie de l'ItemStack
    //             //         ingredients.add(ingredient.clone()); // Ajouter une copie de l'ItemStack
    //             //     }
    //             // });
    //             for (ItemStack ingredient : shapedRecipe.getIngredientMap().values()) {
    //                 if (ingredient != null && ingredient.getType() != Material.AIR) {
    //                     ItemStack fixed = new ItemStack(ingredient.getType(), 1);
    //                     ingredients.add(fixed); // Ajouter une copie de l'ItemStack
    //                 }
    //             }
    //         } else if (recipe instanceof ShapelessRecipe shapelessRecipe && typeRecipe.equalsIgnoreCase("SHAPELESS")) {
    //             // ingredients.addAll(shapelessRecipe.getIngredientList());
    //             for (ItemStack ingredient : shapelessRecipe.getIngredientList()) {
    //                 if (ingredient != null && ingredient.getType() != Material.AIR) {
    //                     ItemStack fixed = new ItemStack(ingredient.getType(), 1);
    //                     ingredients.add(fixed); // Ajouter une copie de l'ItemStack
    //                 }
    //             }
    //         } else if (recipe instanceof FurnaceRecipe furnaceRecipe && typeRecipe.equalsIgnoreCase("FURNACE")) {
    //             // ingredients.add(furnaceRecipe.getInput());
    //             ItemStack fixed = new ItemStack(furnaceRecipe.getInput().getType(), 1);
    //             ingredients.add(fixed); // Ajouter une copie de l'ItemStack
    //         }
    //     }

    //     return ingredients;
    // }
    
    public List<ItemStack> getIngredients(String shopID, String itemID, ItemStack item) {
        List<ItemStack> ingredients = new ArrayList<>();
        RecipeType typeRecipe = DynaShopPlugin.getInstance().getShopConfigManager().getTypeRecipe(shopID, itemID);
        String cacheKey = shopID + ":" + itemID;

        // Vérifier si la recette est en cache et si le cache est encore valide
        if (ingredientsCache.containsKey(cacheKey) && 
            System.currentTimeMillis() - cacheTimestamps.getOrDefault(cacheKey, 0L) < CACHE_DURATION) {
            return new ArrayList<>(ingredientsCache.get(cacheKey));
        }

        if (DynaShopPlugin.getInstance().getShopConfigManager().getItemValue(shopID, itemID, "recipe.pattern", Boolean.class).orElse(false)) {
            // Si une recette est définie dans la configuration, on l'utilise
            ConfigurationSection recipeSection = DynaShopPlugin.getInstance().getShopConfigManager().getSection(shopID, itemID, "recipe");
            Recipe recipeConfig = DynaShopPlugin.getInstance().getCustomRecipeManager().loadRecipeFromShopConfig(shopID, itemID, recipeSection).orElse(null);
            if (recipeConfig != null) {
                switch (typeRecipe) {
                    case SHAPED -> { // CRAFTING
                        if (recipeConfig instanceof ShapedRecipe shapedRecipe) {
                            for (ItemStack ingredient : shapedRecipe.getIngredientMap().values()) {
                                if (ingredient != null && ingredient.getType() != Material.AIR) {
                                    ItemStack fixed = new ItemStack(ingredient.getType(), 1);
                                    ingredients.add(fixed); // Ajouter une copie de l'ItemStack
                                }
                            }
                        }
                    }
                    case SHAPELESS -> { // CRAFTING
                        if (recipeConfig instanceof ShapelessRecipe shapelessRecipe) {
                            for (ItemStack ingredient : shapelessRecipe.getIngredientList()) {
                                if (ingredient != null && ingredient.getType() != Material.AIR) {
                                    ItemStack fixed = new ItemStack(ingredient.getType(), 1);
                                    ingredients.add(fixed); // Ajouter une copie de l'ItemStack
                                }
                            }
                        }
                    }
                    case FURNACE -> { // SMELTING
                        if (recipeConfig instanceof FurnaceRecipe furnaceRecipe) {
                            ItemStack fixed = new ItemStack(furnaceRecipe.getInput().getType(), 1);
                            ingredients.add(fixed); // Ajouter une copie de l'ItemStack
                        }
                    }
                    // case BLAST_FURNACE -> { // SMELTING
                    //     if (recipeConfig instanceof BlastingRecipe blastingRecipe) {
                    //         ItemStack fixed = new ItemStack(blastingRecipe.getInput().getType(), 1);
                    //         ingredients.add(fixed); // Ajouter une copie de l'ItemStack
                    //     }
                    // }
                    // case SMOKER -> { // SMELTING
                    //     if (recipeConfig instanceof SmokingRecipe smokingRecipe) {
                    //         ItemStack fixed = new ItemStack(smokingRecipe.getInput().getType(), 1);
                    //         ingredients.add(fixed); // Ajouter une copie de l'ItemStack
                    //     }
                    // }
                    // case STONECUTTER -> { // SMELTING
                    //     if (recipeConfig instanceof StonecuttingRecipe stonecuttingRecipe) {
                    //         ItemStack fixed = new ItemStack(stonecuttingRecipe.getInput().getType(), 1);
                    //         ingredients.add(fixed); // Ajouter une copie de l'ItemStack
                    //     }
                    // }
                    // case BREWING -> { // SMELTING
                    //     if (recipeConfig instanceof BrewerInventory brewingRecipe) {
                    //         ItemStack fixed = new ItemStack(brewingRecipe.getIngredient().getType(), 1);
                    //         ingredients.add(fixed); // Ajouter une copie de l'ItemStack
                    //     }
                    // }
                    default -> {
                    // Aucun traitement pour les autres types
                    }
                }
                return ingredients; // Retourner les ingrédients trouvés dans la recette
            }
        }

        for (Recipe recipe : DynaShopPlugin.getInstance().getServer().getRecipesFor(item)) {
            switch (typeRecipe) {
                case SHAPED -> { // CRAFTING
                    if (recipe instanceof ShapedRecipe shapedRecipe) {
                        for (ItemStack ingredient : shapedRecipe.getIngredientMap().values()) {
                            if (ingredient != null && ingredient.getType() != Material.AIR) {
                                ItemStack fixed = new ItemStack(ingredient.getType(), 1);
                                ingredients.add(fixed); // Ajouter une copie de l'ItemStack
                            }
                        }
                    }
                }
                case SHAPELESS -> { // CRAFTING
                    if (recipe instanceof ShapelessRecipe shapelessRecipe) {
                        for (ItemStack ingredient : shapelessRecipe.getIngredientList()) {
                            if (ingredient != null && ingredient.getType() != Material.AIR) {
                                ItemStack fixed = new ItemStack(ingredient.getType(), 1);
                                ingredients.add(fixed); // Ajouter une copie de l'ItemStack
                            }
                        }
                    }
                }
                case FURNACE -> { // SMELTING
                    if (recipe instanceof FurnaceRecipe furnaceRecipe) {
                        ItemStack fixed = new ItemStack(furnaceRecipe.getInput().getType(), 1);
                        ingredients.add(fixed); // Ajouter une copie de l'ItemStack
                    }
                }
                default -> {
                // Aucun traitement pour les autres types
                }
            }
        }

        // Mettre en cache les résultats
        ingredientsCache.put(cacheKey, new ArrayList<>(ingredients));
        cacheTimestamps.put(cacheKey, System.currentTimeMillis());

        return ingredients;
    }

    // private double getIngredientPrice(String shopID, ItemStack ingredient, String typePrice, List<String> visitedItems) {
    //     // String itemID = ShopGuiPlusApi.getItemStackShopItem(ingredient).getId();
    //     // String shopID = ShopGuiPlusApi.getItemStackShopItem(ingredient).getShop().getId();
    //     FoundItem foundItem = findItemInShops(shopID, ingredient);
    //     if (!foundItem.isFound()) {
    //         DynaShopPlugin.getInstance().getLogger().warning("Impossible de trouver l'ingrédient " + ingredient + " dans le shop " + shopID);
    //         return 0; // Retourner 0 si l'ingrédient n'est pas trouvé
    //     }

    //     String ingredientID = foundItem.getItemID();
    //     String ingredientShopID = foundItem.getShopID();

    //     // Vérifier si l'item a déjà été visité
    //     if (visitedItems.contains(ingredientID)) {
    //         // DynaShopPlugin.getInstance().getLogger().warning("Boucle détectée pour l'item : " + ingredientID);
    //         return 0.0; // Retourner 0 pour éviter une boucle infinie
    //     }

    //     // Ajouter l'item à la liste des visités
    //     visitedItems.add(ingredientID);

    //     // // 1. Vérifier le cache si disponible
    //     // Double cachedPrice = checkCache(shopID, itemID, typePrice);
    //     // if (cachedPrice != null) {
    //     //     return cachedPrice;
    //     // }
    
    //     // Vérifier le type d'item
    //     DynaShopType itemType = DynaShopPlugin.getInstance().getShopConfigManager().getTypeDynaShop(ingredientShopID, ingredientID);

    //     // // Si l'ingrédient est un item avec stock, prendre en compte sa valeur de stock
    //     // if (itemType == DynaShopType.STOCK) {
    //     //     DynaShopPlugin.getInstance().getLogger().info("Calcul du prix de l'ingrédient " + itemID + " avec stock.");
    //     //     // Récupérer la valeur de stock actuelle
    //     //     Optional<Integer> stockOptional = DynaShopPlugin.getInstance().getItemDataManager().getStock(shopID, itemID);
    //     //     if (stockOptional.isPresent()) {
    //     //         DynaShopPlugin.getInstance().getLogger().info("Stock de l'ingrédient " + itemID + ": " + stockOptional.get());
    //     //         int stock = stockOptional.get();
                
    //     //         // Récupérer les bornes de stock
    //     //         int minStock = DynaShopPlugin.getInstance().getShopConfigManager()
    //     //             .getItemValue(shopID, itemID, "stock.min", Integer.class).orElse(0);
    //     //         int maxStock = DynaShopPlugin.getInstance().getShopConfigManager()
    //     //             .getItemValue(shopID, itemID, "stock.max", Integer.class).orElse(1000);
                
    //     //         // Récupérer le prix de base
    //     //         Optional<Double> basePrice;
    //     //         if (typePrice.equals("buyPrice")) {
    //     //             DynaShopPlugin.getInstance().getLogger().info("Calcul du prix d'achat de l'ingrédient " + itemID + " avec stock.");
    //     //             basePrice = DynaShopPlugin.getInstance().getItemDataManager().getBuyPrice(shopID, itemID);
    //     //             if (basePrice.isEmpty()) {
    //     //                 DynaShopPlugin.getInstance().getLogger().info("Prix d'achat non trouvé dans la base de données, vérification de la configuration.");
    //     //                 basePrice = DynaShopPlugin.getInstance().getShopConfigManager()
    //     //                     .getItemValue(shopID, itemID, "buyPrice", Double.class);
    //     //             }
    //     //         } else { // sellPrice
    //     //             DynaShopPlugin.getInstance().getLogger().info("Calcul du prix de vente de l'ingrédient " + itemID + " avec stock.");
    //     //             basePrice = DynaShopPlugin.getInstance().getItemDataManager().getSellPrice(shopID, itemID);
    //     //             if (basePrice.isEmpty()) {
    //     //                 DynaShopPlugin.getInstance().getLogger().info("Prix de vente non trouvé dans la base de données, vérification de la configuration.");
    //     //                 basePrice = DynaShopPlugin.getInstance().getShopConfigManager()
    //     //                     .getItemValue(shopID, itemID, "sellPrice", Double.class);
    //     //             }
    //     //         }
                
    //     //         if (basePrice.isPresent()) {
    //     //             DynaShopPlugin.getInstance().getLogger().info("Prix de base de l'ingrédient " + itemID + ": " + basePrice.get());
    //     //             // Calculer le ratio de stock (entre 0 et 1)
    //     //             double stockRatio = Math.max(0.0, Math.min(1.0, (double)(stock - minStock) / (maxStock - minStock)));
                    
    //     //             // Récupérer les bornes de prix
    //     //             double minPrice = DynaShopPlugin.getInstance().getShopConfigManager()
    //     //                 .getItemValue(shopID, itemID, typePrice.replace("Price", "Dynamic.min"), Double.class)
    //     //                 .orElse(basePrice.get() * 0.5);
    //     //             double maxPrice = DynaShopPlugin.getInstance().getShopConfigManager()
    //     //                 .getItemValue(shopID, itemID, typePrice.replace("Price", "Dynamic.max"), Double.class)
    //     //                 .orElse(basePrice.get() * 2.0);
                    
    //     //             // Même formule que dans adjustPricesBasedOnStock
    //     //             double price = maxPrice - (maxPrice - minPrice) * stockRatio;
                    
    //     //             DynaShopPlugin.getInstance().getLogger().info("Prix de l'ingrédient " + itemID + " ajusté par stock: " + price + " (stock: " + stock + ")");
    //     //             return price;
    //     //         }
    //     //     }
    //     // }
        
    //     // Si l'ingrédient est un item avec stock, utiliser PriceStock
    //     if (itemType == DynaShopType.STOCK) {
    //         return DynaShopPlugin.getInstance().getPriceStock().calculatePrice(ingredientShopID, ingredientID, typePrice);
    //     }
        
    //     // Pour les items en mode RECIPE
    //     if (itemType == DynaShopType.RECIPE) {
    //         ItemStack itemStack = ShopGuiPlusApi.getShop(ingredientShopID).getShopItem(ingredientID).getItem();
    //         if (itemStack != null) {
    //             // Calculer le prix via la recette de l'ingrédient
    //             return calculatePrice(ingredientShopID, ingredientID, itemStack, typePrice, visitedItems);
    //         }
    //     }

    //     // Pour les autres types d'items, continuer avec la logique existante
    //     Optional<Double> price = Optional.empty();

    //     // Récupérer le prix d'achat depuis la base de données
    //     if (!typePrice.contains(".")) {
    //         price = DynaShopPlugin.getInstance().getItemDataManager().getPrice(ingredientShopID, ingredientID, typePrice);
    //     // } else {
    //     //     DynaShopPlugin.getInstance().getLogger().warning("Le type de prix " + typePrice + " n'est pas valide pour l'item " + itemID);
    //     }
    
    //     // Si le prix n'est pas trouvé dans la base de données, chercher dans les fichiers de configuration
    //     if (price.isEmpty()) {
    //         price = DynaShopPlugin.getInstance().getShopConfigManager().getItemValue(ingredientShopID, ingredientID, typePrice, Double.class);
    //     }

    //     // Si aucun prix n'est trouvé, vérifier si l'item a une recette
    //     if (price.isEmpty()) {
    //         ItemStack itemStack = ShopGuiPlusApi.getShop(ingredientShopID).getShopItem(ingredientID).getItem();
    //         if (itemStack != null) {
    //             // Calculer le prix via la recette de l'ingrédient
    //             return calculatePrice(ingredientShopID, ingredientID, itemStack, typePrice, visitedItems);
    //         }
    //     }
    
    //     // Retourner le prix trouvé ou une valeur par défaut
    //     return price.orElse(10.0); // 10.0 est une valeur par défaut si aucun prix n'est trouvé
    // }
    private double getIngredientPrice(String shopID, ItemStack ingredient, String typePrice, List<String> visitedItems) {
        // Trouver l'item dans les shops
        FoundItem foundItem = findItemInShops(shopID, ingredient);
        if (!foundItem.isFound()) {
            DynaShopPlugin.getInstance().getLogger().warning("Impossible de trouver l'ingrédient " + ingredient + " dans le shop " + shopID);
            return 0; // Retourner 0 si l'ingrédient n'est pas trouvé
        }
        
        String ingredientID = foundItem.getItemID();
        String ingredientShopID = foundItem.getShopID();
        
        // Vérifier si l'item a déjà été visité pour éviter les boucles infinies
        if (visitedItems.contains(ingredientID)) {
            return 0.0; // Retourner 0 pour éviter une boucle infinie
        }
        visitedItems.add(ingredientID);
        
        // Utiliser getOrLoadPrice pour obtenir toutes les informations de prix
        DynamicPrice price = DynaShopPlugin.getInstance().getDynaShopListener()
            .getOrLoadPrice(ingredientShopID, ingredientID, ingredient);
        
        if (price == null) {
            return 10.0; // Valeur par défaut en cas d'erreur
        }
        
        // Retourner le prix demandé selon le type
        if (typePrice.equals("buyPrice")) {
            return price.getBuyPrice();
        } else if (typePrice.equals("sellPrice")) {
            return price.getSellPrice();
        } else if (typePrice.equals("buyDynamic.min")) {
            return price.getMinBuyPrice();
        } else if (typePrice.equals("buyDynamic.max")) {
            return price.getMaxBuyPrice();
        } else if (typePrice.equals("sellDynamic.min")) {
            return price.getMinSellPrice();
        } else if (typePrice.equals("sellDynamic.max")) {
            return price.getMaxSellPrice();
        }
        
        return 10.0; // Valeur par défaut si le type de prix n'est pas reconnu
    }

    // // Méthodes auxiliaires extraites:
    // private Double checkCache(String shopID, String itemID, String typePrice) {
    //     // Utiliser le système de cache global du plugin
    //     double cachedPrice = DynaShopPlugin.getInstance().getCachedRecipePrice(shopID, itemID, typePrice);
        
    //     // Si le prix est en cache (différent de -1.0, la valeur retournée quand le prix n'est pas en cache)
    //     if (cachedPrice >= 0) {
    //         // Ajouter du logging pour le débogage
    //         // if (DynaShopPlugin.getInstance().isDebug()) {
    //         //     DynaShopPlugin.getInstance().getLogger().info("Cache hit pour " + shopID + ":" + itemID + ":" + typePrice + " = " + cachedPrice);
    //         // }
    //         return cachedPrice;
    //     }
        
    //     return null; // Le prix n'est pas en cache
    // }

    public List<ItemStack> consolidateIngredients(List<ItemStack> ingredients) {
        // Map<Material, Integer> ingredientCounts = new HashMap<>();
        Map<Material, Integer> ingredientCounts = new EnumMap<>(Material.class);

        for (ItemStack ingredient : ingredients) {
            Material material = ingredient.getType();
            int amount = ingredientCounts.getOrDefault(material, 0) + ingredient.getAmount();
            ingredientCounts.put(material, amount);
        }

        List<ItemStack> consolidated = new ArrayList<>();
        for (Map.Entry<Material, Integer> entry : ingredientCounts.entrySet()) {
            ItemStack itemStack = new ItemStack(entry.getKey());
            itemStack.setAmount(entry.getValue());
            consolidated.add(itemStack);
        }

        return consolidated;
    }

    private double getRecipeModifier(ItemStack item) {
        // Déterminer le type de recette et appliquer le modificateur correspondant
        // for (Recipe recipe : item.getItemMeta().getPersistentDataContainer().getRecipesFor(item)) {
        for (Recipe recipe : DynaShopPlugin.getInstance().getServer().getRecipesFor(item)) {
            if (recipe instanceof ShapedRecipe) {
                // return config.getDouble("actions.shaped", 1.0);
                return DynaShopPlugin.getInstance().getDataConfig().getShapedValue();
            } else if (recipe instanceof ShapelessRecipe) {
                // return config.getDouble("actions.shapeless", 1.0);
                return DynaShopPlugin.getInstance().getDataConfig().getShapelessValue();
            } else if (recipe instanceof FurnaceRecipe) {
                // return config.getDouble("actions.furnace", 1.0);
                return DynaShopPlugin.getInstance().getDataConfig().getFurnaceValue();
            }
        }

        // Retourner un modificateur par défaut si aucune recette n'est trouvée
        return 1.0;
    }

    public int getIngredientStock(String shopID, ItemStack ingredient, List<String> visitedItems) {
        // Récupérer l'ID de l'item dans le shop
        // String itemID = ShopGuiPlusApi.getItemStackShopItem(ingredient).getId();
        // String shopID = ShopGuiPlusApi.getItemStackShopItem(ingredient).getShop().getId();
        FoundItem foundItem = findItemInShops(shopID, ingredient);
        if (!foundItem.isFound()) {
            DynaShopPlugin.getInstance().getLogger().warning("Impossible de trouver l'ingrédient " + ingredient + " dans le shop " + shopID);
            return 0; // Retourner 0 si l'ingrédient n'est pas trouvé
        }
        String ingredientID = foundItem.getItemID();
        String ingredientShopID = foundItem.getShopID();

        // Vérifier si l'item a déjà été visité
        if (visitedItems.contains(ingredientID)) {
            // DynaShopPlugin.getInstance().getLogger().warning("Boucle détectée pour l'item : " + ingredientID);
            return 0; // Retourner 0 pour éviter une boucle infinie
        }
        // Ajouter l'item à la liste des visités
        visitedItems.add(ingredientID);

        // Récupérer le stock de l'item
        Optional<Integer> stockOptional = DynaShopPlugin.getInstance().getItemDataManager().getStock(ingredientShopID, ingredientID);
        if (stockOptional.isPresent()) {
            return stockOptional.get();
        }
        return 0; // Retourner 0 si le stock n'est pas trouvé
    }

    public int getIngredientMaxStock(String shopID, ItemStack ingredient, List<String> visitedItems) {
        // Récupérer l'ID de l'item dans le shop
        // String itemID = ShopGuiPlusApi.getItemStackShopItem(ingredient).getId();
        // String shopID = ShopGuiPlusApi.getItemStackShopItem(ingredient).getShop().getId();
        FoundItem foundItem = findItemInShops(shopID, ingredient);
        if (!foundItem.isFound()) {
            DynaShopPlugin.getInstance().getLogger().warning("Impossible de trouver l'ingrédient " + ingredient + " dans le shop " + shopID);
            return 0; // Retourner 0 si l'ingrédient n'est pas trouvé
        }
        String ingredientID = foundItem.getItemID();
        String ingredientShopID = foundItem.getShopID();

        // Vérifier si l'item a déjà été visité
        if (visitedItems.contains(ingredientID)) {
            // DynaShopPlugin.getInstance().getLogger().warning("Boucle détectée pour l'item : " + ingredientID);
            return 0; // Retourner 0 pour éviter une boucle infinie
        }
        // Ajouter l'item à la liste des visités
        visitedItems.add(ingredientID);

        // Récupérer le stock maximum de l'item
        Optional<Integer> maxStockOptional = DynaShopPlugin.getInstance().getShopConfigManager().getItemValue(ingredientShopID, ingredientID, "stock.max", Integer.class);
        if (maxStockOptional.isPresent()) {
            return maxStockOptional.get();
        }
        return 0; // Retourner 0 si le stock maximum n'est pas trouvé
    }

    //     // Récupérer l'ID de l'item dans le shop
    //     String itemID = ShopGuiPlusApi.getItemStackShopItem(ingredient).getId();
    //     String shopID = ShopGuiPlusApi.getItemStackShopItem(ingredient).getShop().getId();

    //     // Récupérer le stock maximum de l'item
    //     return DynaShopPlugin.getInstance().getShopConfigManager().getItemValue(shopID, itemID, "stock.max", Integer.class).orElse(1000);
    // }

    public DynaShopType getItemType(String shopID, String itemID) {
        // Récupérer le type d'item depuis la configuration
        return DynaShopPlugin.getInstance().getShopConfigManager().getTypeDynaShop(shopID, itemID);
    }

    public DynaShopType getIngredientType(String shopID, ItemStack ingredient) {
        // Récupérer l'ID de l'item dans le shop
        // String itemID = ShopGuiPlusApi.getItemStackShopItem(ingredient).getId();
        // String shopID = ShopGuiPlusApi.getItemStackShopItem(ingredient).getShop().getId();
        FoundItem foundItem = findItemInShops(shopID, ingredient);
        if (!foundItem.isFound()) {
            DynaShopPlugin.getInstance().getLogger().warning("Impossible de trouver l'ingrédient " + ingredient + " dans le shop " + shopID);
            return DynaShopType.UNKNOWN; // Retourner un type inconnu si l'ingrédient n'est pas trouvé
        }
        String ingredientID = foundItem.getItemID();
        String ingredientShopID = foundItem.getShopID();
        
        // Récupérer le type d'item depuis la configuration
        return DynaShopPlugin.getInstance().getShopConfigManager().getTypeDynaShop(ingredientShopID, ingredientID);
    }

    // public int calculateStock(String shopID, String itemID, ItemStack item) {
    //     // Récupérer le stock de l'item
    //     Optional<Integer> stockOptional = DynaShopPlugin.getInstance().getItemDataManager().getStock(shopID, itemID);
    //     if (stockOptional.isPresent()) {
    //         return stockOptional.get();
    //     }
    //     return 0; // Retourner 0 si le stock n'est pas trouvé
    // }

    // public int calculateMaxStock(String shopID, String itemID) {
    //     // Récupérer le stock maximum de l'item
    //     return DynaShopPlugin.getInstance().getShopConfigManager().getItemValue(shopID, itemID, "stock.max", Integer.class).orElse(1000);
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
        String itemID = null;
        String shopID = null;
        
        try {
            // D'abord, essayer de trouver l'ingrédient dans le shop préféré
            Shop currentShop = ShopGuiPlusApi.getShop(preferredShopID);
            for (ShopItem item : currentShop.getShopItems()) {
                if (item.getItem().getType() == ingredient.getType()) {
                    itemID = item.getId();
                    shopID = preferredShopID;
                    break;
                }
            }
            
            // Si non trouvé dans le shop courant, chercher dans tous les shops
            if (itemID == null) {
                ShopItem shopItem = ShopGuiPlusApi.getItemStackShopItem(ingredient);
                if (shopItem != null) {
                    itemID = shopItem.getId();
                    shopID = shopItem.getShop().getId();
                }
            }
        } catch (Exception e) {
            // En cas d'erreur, essayer la méthode directe
            try {
                ShopItem shopItem = ShopGuiPlusApi.getItemStackShopItem(ingredient);
                if (shopItem != null) {
                    itemID = shopItem.getId();
                    shopID = shopItem.getShop().getId();
                }
            } catch (Exception ex) {
                DynaShopPlugin.getInstance().getLogger().warning("Impossible de trouver l'ingrédient " + ingredient + " dans aucun shop: " + ex.getMessage());
            }
        }
        
        return new FoundItem(shopID, itemID, itemID != null && shopID != null);

        
            // // // Récupérer toutes les données de l'ingrédient en une fois
            // // String ingredientID = ShopGuiPlusApi.getItemStackShopItem(ingredient).getId();
            // // String ingredientShopID = ShopGuiPlusApi.getItemStackShopItem(ingredient).getShop().getId();
            // String ingredientShopID = null;
            // String ingredientID = null;

            // try {
            //     // Shop shop = ShopGuiPlusApi.getShop(shopID);
            //     // ShopItem shopItem = shop.getShopItem(itemID);
            //     // if (shopItem != null) {
            //     //     ingredientID = shopItem.getId();
            //     //     ingredientShopID = shop.getId();
            //     // }
            //     // ingredientShopID = shop.getId();
            //     // ingredientID = shop.getShopItem(itemID).getId();
                
            //     // if (ingredientID == null) {
            //     //     ingredientShopID = ShopGuiPlusApi.getItemStackShopItem(ingredient).getShop().getId();
            //     //     ingredientID = ShopGuiPlusApi.getItemStackShopItem(ingredient).getId();
            //     // }
            //     // D'abord, essayer de trouver l'ingrédient dans le shop courant
            //     Shop currentShop = ShopGuiPlusApi.getShop(shopID);
            //     for (ShopItem item2 : currentShop.getShopItems()) {
            //         if (item2.getItem().getType() == ingredient.getType()) {
            //             ingredientID = item2.getId();
            //             ingredientShopID = shopID;
            //             break;
            //         }
            //     }
                
            //     // Si non trouvé dans le shop courant, chercher dans tous les shops
            //     if (ingredientID == null) {
            //         ShopItem shopItem = ShopGuiPlusApi.getItemStackShopItem(ingredient);
            //         if (shopItem != null) {
            //             ingredientID = shopItem.getId();
            //             ingredientShopID = shopItem.getShop().getId();
            //         }
            //     }
            // } catch (Exception e) {
            //     ingredientShopID = ShopGuiPlusApi.getItemStackShopItem(ingredient).getShop().getId();
            //     ingredientID = ShopGuiPlusApi.getItemStackShopItem(ingredient).getId();
            // }

            // if (ingredientID == null || ingredientShopID == null) {
            //     DynaShopPlugin.getInstance().getLogger().warning("ID ou ShopID manquant pour l'ingrédient " + ingredient);
            //     continue; // Passer à l'ingrédient suivant si l'ID est manquant
            // // } else {
            // //     DynaShopPlugin.getInstance().getLogger().info("ID de l'ingrédient: " + ingredientID + ", ShopID: " + ingredientShopID);
            // //     // DynaShopPlugin.getInstance().getLogger().info("Ingrédient: " + ingredient);
            // //     // DynaShopPlugin.getInstance().getLogger().info("ItemStack: " + item);
            // }
    }
    
}