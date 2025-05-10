package fr.tylwen.satyria.dynashop.price;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.util.Consumer;

import fr.tylwen.satyria.dynashop.DynaShopPlugin;
import fr.tylwen.satyria.dynashop.data.CustomRecipeManager;
import fr.tylwen.satyria.dynashop.data.DynamicPrice;
import fr.tylwen.satyria.dynashop.data.ShopConfigManager;
import fr.tylwen.satyria.dynashop.data.param.DynaShopType;
import fr.tylwen.satyria.dynashop.data.param.RecipeType;
import net.brcdev.shopgui.ShopGuiPlusApi;
import net.brcdev.shopgui.shop.item.ShopItem;

public class RecipePrice implements DynaShopPrice {
    private final DynaShopPlugin plugin;
    private final ShopConfigManager shopConfigManager;
    private final CustomRecipeManager customRecipeManager;
    private final YamlConfiguration configMain;
    private final PriceStrategy priceStategy;
    
    // Ajouter ces champs à la classe PriceRecipe
    private final Map<String, List<ItemStack>> ingredientsCache = new HashMap<>();
    private final long CACHE_DURATION = 20L * 60L * 5L; // 5 minutes
    private final Map<String, Long> cacheTimestamps = new HashMap<>();

    public RecipePrice(DynaShopPlugin plugin) {
        this.plugin = plugin;
        this.shopConfigManager = plugin.getShopConfigManager();
        this.customRecipeManager = plugin.getCustomRecipeManager();
        this.configMain = plugin.getConfigMain();
        this.priceStategy = plugin.getPriceStrategy();
    }

    @Override
    public DynaShopType getType() {
        return DynaShopType.RECIPE;
    }

    @Override
    public boolean canBuy(String shopID, String itemID, int amount) {
        return true;
    }

    @Override
    public boolean canSell(String shopID, String itemID, int amount) {
        return true;
    }

    @Override
    public boolean canBuy(ShopItem item) {
        return canBuy(item.getShop().getId(), item.getId(), item.getItem().getAmount());
    }

    @Override
    public boolean canSell(ShopItem item) {
        return canSell(item.getShop().getId(), item.getId(), item.getItem().getAmount());
    }

    @Override
    public double calculateBuyPrice(ShopItem item) {
        return calculatePrice(item.getShop().getId(), item.getId(), item.getItem(), "buyPrice", new ArrayList<>());
    }

    @Override
    public double calculateSellPrice(ShopItem item) {
        return calculatePrice(item.getShop().getId(), item.getId(), item.getItem(), "sellPrice", new ArrayList<>());
    }

    @Override
    public double calculateBuyPrice(String shopID, String itemID, ItemStack item) {
        return calculatePrice(shopID, itemID, item, "buyPrice", new ArrayList<>());
    }

    @Override
    public double calculateSellPrice(String shopID, String itemID, ItemStack item) {
        return calculatePrice(shopID, itemID, item, "sellPrice", new ArrayList<>());
    }

    private double calculatePrice(String shopID, String itemID, ItemStack item, String typePrice, List<String> visitedItems) {
        List<ItemStack> ingredients = getIngredients(shopID, itemID, item);
        ingredients = consolidateIngredients(ingredients);
        double basePrice = 0.0;

        // Calculer le prix de base en fonction des ingrédients
        for (ItemStack ingredient : ingredients) {
            if (ingredient == null || ingredient.getType() == Material.AIR) {
                continue; // Ignorer les ingrédients invalides
            }
            double ingredientPrice = getIngredientPrice(ingredient, typePrice, visitedItems);
            basePrice += ingredientPrice * ingredient.getAmount(); // Multiplier par la quantité de l'ingrédient
        }

        // Appliquer le modificateur en fonction du type de recette
        double modifier = getRecipeModifier(item);
        return basePrice * modifier;
    }
    
    public void calculatePriceAsync(String shopID, String itemID, ItemStack item, String typePrice, Consumer<Double> callback) {
        // Exécuter le calcul de prix coûteux dans un thread séparé
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<String> visitedItems = new ArrayList<>();
            double price = calculatePrice(shopID, itemID, item, typePrice, visitedItems);
            
            // Cacher le prix calculé
            plugin.cacheRecipePrice(shopID, itemID, typePrice, price);
            
            // Revenir au thread principal pour exécuter le callback
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(price));
            // try {
            //     // Forcer l'obtention d'une nouvelle connexion à la base de données pour cette opération asynchrone
            //     plugin.getDataManager().reloadDatabaseConnection();
                
            //     List<String> visitedItems = new ArrayList<>();
            //     double price = calculatePrice(shopID, itemID, item, typePrice, visitedItems);
                
            //     // Cacher le prix calculé
            //     plugin.cacheRecipePrice(shopID, itemID, typePrice, price);
                
            //     // Revenir au thread principal pour exécuter le callback
            //     Bukkit.getScheduler().runTask(plugin, () -> {
            //         try {
            //             callback.accept(price);
            //         } catch (Exception e) {
            //             plugin.getLogger().severe("Erreur dans le callback: " + e.getMessage());
            //             e.printStackTrace();
            //         }
            //     });
            // } catch (Exception e) {
            //     plugin.getLogger().severe("Erreur lors du calcul asynchrone du prix: " + e.getMessage());
            //     e.printStackTrace();
                
            //     // Revenir au thread principal avec une valeur par défaut
            //     Bukkit.getScheduler().runTask(plugin, () -> {
            //         try {
            //             callback.accept(10.0); // Valeur par défaut en cas d'erreur
            //         } catch (Exception ex) {
            //             plugin.getLogger().severe("Erreur dans le callback d'erreur: " + ex.getMessage());
            //         }
            //     });
            // }
        });
    }

    @Override
    public void postSellTransaction(ShopItem item) {
        postSellTransaction(item.getShop().getId(), item.getId(), item.getItem());
    }

    @Override
    public void postBuyTransaction(ShopItem item) {
        postBuyTransaction(item.getShop().getId(), item.getId(), item.getItem());
    }

    @Override
    public void postSellTransaction(String shopID, String itemID, ItemStack item) {
        // Appliquer la croissance ou la décadence aux ingrédients
        List<String> visitedItems = new ArrayList<>();
        applyGrowthOrDecayToIngredients(shopID, itemID, item, 1, true, visitedItems, 0);
        
        // Enregistrer le prix dans la base de données
        plugin.getBatchDatabaseUpdater().queueUpdate(shopID, itemID, priceStategy.getDynamicPrice(shopID, itemID, item));
    }

    @Override
    public void postBuyTransaction(String shopID, String itemID, ItemStack item) {
        // Appliquer la croissance ou la décadence aux ingrédients
        List<String> visitedItems = new ArrayList<>();
        applyGrowthOrDecayToIngredients(shopID, itemID, item, 1, false, visitedItems, 0);
        
        // Enregistrer le prix dans la base de données
        plugin.getBatchDatabaseUpdater().queueUpdate(shopID, itemID, priceStategy.getDynamicPrice(shopID, itemID, item));
    }


    private void applyGrowthOrDecayToIngredients(String shopID, String itemID, ItemStack itemStack, int amount, boolean isGrowth, List<String> visitedItems, int depth) {
        // Vérifier la profondeur pour éviter les boucles infinies
        if (depth > 5) {
            plugin.getLogger().warning("Profondeur maximale atteinte lors de l'application de la croissance pour l'item : " + itemID);
            return;
        }
        // Vérifier si l'item a déjà été visité pour éviter les boucles infinies
        if (visitedItems.contains(itemID)) {
            return;
        }
        visitedItems.add(itemID); // Ajouter l'item à la liste des items visités

        // Récupérer la liste des ingrédients de la recette
        List<ItemStack> ingredients = getIngredients(shopID, itemID, itemStack);
        ingredients = consolidateIngredients(ingredients); // Consolider les ingrédients

        for (ItemStack ingredient : ingredients) {
            if (ingredient == null || ingredient.getType() == Material.AIR) {
                continue; // Ignorer les ingrédients invalides
            }
            
            String ingredientID = ShopGuiPlusApi.getItemStackShopItem(ingredient).getId();
            String ingredientShopID = ShopGuiPlusApi.getItemStackShopItem(ingredient).getShop().getId();
            
            // Recupérer le prix de l'ingrédient
            DynamicPrice ingredientPrice = priceStategy.getDynamicPrice(ingredientShopID, ingredientID, ingredient);

            if (ingredientPrice != null) {
                // Appliquer growth ou decay
                if (isGrowth) {
                    ingredientPrice.applyGrowth(amount * ingredient.getAmount());
                } else {
                    ingredientPrice.applyDecay(amount * ingredient.getAmount());
                }

                // Log pour vérifier les changements
                plugin.getLogger().info("Prix mis à jour pour l'ingrédient " + ingredientID + " x " + amount * ingredient.getAmount() + ": " +
                    "Buy = " + ingredientPrice.getBuyPrice() + ", Sell = " + ingredientPrice.getSellPrice());
                
                // Appel récursif pour appliquer la croissance aux ingrédients de l'ingrédient
                // if (ingredientPrice.getType() == DynaShopType.RECIPE) {
                if (!ingredientPrice.isFromRecipe()) {
                    // plugin.getItemDataManager().savePrice(shopingredientID, ingredientID, ingredientPrice.getBuyPrice(), ingredientPrice.getSellPrice());
                    plugin.getBatchDatabaseUpdater().queueUpdate(ingredientShopID, ingredientID, ingredientPrice);
                } else {
                    // Appel récursif pour appliquer la croissance aux ingrédients de l'ingrédient
                    applyGrowthOrDecayToIngredients(ingredientShopID, ingredientID, ingredient, amount, isGrowth, visitedItems, depth + 1);
                }
            }
        }
    }




    public List<ItemStack> getIngredients(String shopID, String itemID, ItemStack item) {
        List<ItemStack> ingredients = new ArrayList<>();
        RecipeType typeRecipe = shopConfigManager.getTypeRecipe(shopID, itemID);
        String cacheKey = shopID + ":" + itemID;
        
        // Vérifier si la recette est en cache et si le cache est encore valide
        if (ingredientsCache.containsKey(cacheKey) && System.currentTimeMillis() - cacheTimestamps.getOrDefault(cacheKey, 0L) < CACHE_DURATION) {
            return new ArrayList<>(ingredientsCache.get(cacheKey));
        }

        if (shopConfigManager.getItemValue(shopID, itemID, "recipe.pattern", Boolean.class).orElse(false)) {
            // Si une recette est définie dans la configuration, on l'utilise
            ConfigurationSection recipeSection = shopConfigManager.getSection(shopID, itemID, "recipe");
            Recipe recipeConfig = customRecipeManager.loadRecipeFromShopConfig(shopID, itemID, recipeSection).orElse(null);
            if (recipeConfig != null) {
                switch (typeRecipe) {
                    case SHAPED -> { // CRAFTING
                        if (recipeConfig instanceof ShapedRecipe shapedRecipe) {
                            for (ItemStack ingredient : shapedRecipe.getIngredientMap().values()) {
                                if (ingredient != null && ingredient.getType() != Material.AIR) {
                                    ItemStack fixe = new ItemStack(ingredient.getType(), 1);
                                    ingredients.add(fixe);
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
                    default -> {
                        // Gérer d'autres types de recettes si nécessaire
                    }
                }
                return ingredients; // Retourner les ingrédients trouvés dans la recette
            }
        }

        for (Recipe recipe : plugin.getServer().getRecipesFor(item)) {
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

    /**
     * Récupérer le prix d'un ingrédient en fonction de son type et de sa quantité.
     * 
     * @param ingredient L'ItemStack représentant l'ingrédient.
     * @param typePrice Le type de prix à récupérer (buyPrice ou sellPrice).
     * @return Le prix de l'ingrédient.
     */
    private double getIngredientPrice(ItemStack ingredient, String typePrice, List<String> visitedItems) {
        String shopID = ShopGuiPlusApi.getItemStackShopItem(ingredient).getShop().getId();
        String itemID = ShopGuiPlusApi.getItemStackShopItem(ingredient).getId();
        
        // Vérifier si l'item a déjà été visité
        if (visitedItems.contains(itemID)) {
            plugin.getLogger().warning("Boucle détectée pour l'item : " + itemID);
            return 0.0; // Retourner 0 pour éviter une boucle infinie
        }

        // Ajouter l'item à la liste des visités
        visitedItems.add(itemID);

        // 1. Vérifier le cache si disponible
        Double cachedPrice = checkCache(shopID, itemID, typePrice);
        if (cachedPrice != null) {
            return cachedPrice;
        }
    
        // Vérifier le type d'item
        DynaShopType itemType = shopConfigManager.getTypeDynaShop(shopID, itemID);

        // // Si l'ingrédient est un item avec stock, prendre en compte sa valeur de stock
        // if (itemType == DynaShopType.STOCK) {
        //     plugin.getLogger().info("Calcul du prix de l'ingrédient " + itemID + " avec stock.");
        //     // Récupérer la valeur de stock actuelle
        //     Optional<Integer> stockOptional = plugin.getItemDataManager().getStock(shopID, itemID);
        //     if (stockOptional.isPresent()) {
        //         plugin.getLogger().info("Stock de l'ingrédient " + itemID + ": " + stockOptional.get());
        //         int stock = stockOptional.get();
                
        //         // Récupérer les bornes de stock
        //         int minStock = plugin.getShopConfigManager()
        //             .getItemValue(shopID, itemID, "stock.min", Integer.class).orElse(0);
        //         int maxStock = plugin.getShopConfigManager()
        //             .getItemValue(shopID, itemID, "stock.max", Integer.class).orElse(1000);
                
        //         // Récupérer le prix de base
        //         Optional<Double> basePrice;
        //         if (typePrice.equals("buyPrice")) {
        //             plugin.getLogger().info("Calcul du prix d'achat de l'ingrédient " + itemID + " avec stock.");
        //             basePrice = plugin.getItemDataManager().getBuyPrice(shopID, itemID);
        //             if (basePrice.isEmpty()) {
        //                 plugin.getLogger().info("Prix d'achat non trouvé dans la base de données, vérification de la configuration.");
        //                 basePrice = plugin.getShopConfigManager()
        //                     .getItemValue(shopID, itemID, "buyPrice", Double.class);
        //             }
        //         } else { // sellPrice
        //             plugin.getLogger().info("Calcul du prix de vente de l'ingrédient " + itemID + " avec stock.");
        //             basePrice = plugin.getItemDataManager().getSellPrice(shopID, itemID);
        //             if (basePrice.isEmpty()) {
        //                 plugin.getLogger().info("Prix de vente non trouvé dans la base de données, vérification de la configuration.");
        //                 basePrice = plugin.getShopConfigManager()
        //                     .getItemValue(shopID, itemID, "sellPrice", Double.class);
        //             }
        //         }
                
        //         if (basePrice.isPresent()) {
        //             plugin.getLogger().info("Prix de base de l'ingrédient " + itemID + ": " + basePrice.get());
        //             // Calculer le ratio de stock (entre 0 et 1)
        //             double stockRatio = Math.max(0.0, Math.min(1.0, (double)(stock - minStock) / (maxStock - minStock)));
                    
        //             // Récupérer les bornes de prix
        //             double minPrice = plugin.getShopConfigManager()
        //                 .getItemValue(shopID, itemID, typePrice.replace("Price", "Dynamic.min"), Double.class)
        //                 .orElse(basePrice.get() * 0.5);
        //             double maxPrice = plugin.getShopConfigManager()
        //                 .getItemValue(shopID, itemID, typePrice.replace("Price", "Dynamic.max"), Double.class)
        //                 .orElse(basePrice.get() * 2.0);
                    
        //             // Même formule que dans adjustPricesBasedOnStock
        //             double price = maxPrice - (maxPrice - minPrice) * stockRatio;
                    
        //             plugin.getLogger().info("Prix de l'ingrédient " + itemID + " ajusté par stock: " + price + " (stock: " + stock + ")");
        //             return price;
        //         }
        //     }
        // }
        
        // Si l'ingrédient est un item avec stock, utiliser PriceStock
        if (itemType == DynaShopType.STOCK) {
            return plugin.getPriceStock().calculatePrice(shopID, itemID, typePrice);
        }
        
        // Pour les items en mode RECIPE
        if (itemType == DynaShopType.RECIPE) {
            ItemStack itemStack = ShopGuiPlusApi.getShop(shopID).getShopItem(itemID).getItem();
            if (itemStack != null) {
                // Calculer le prix via la recette de l'ingrédient
                return calculatePrice(shopID, itemID, itemStack, typePrice, visitedItems);
            }
        }

        // Pour les autres types d'items, continuer avec la logique existante
        Optional<Double> price = Optional.empty();

        // Récupérer le prix d'achat depuis la base de données
        if (!typePrice.contains(".")) {
            price = plugin.getItemDataManager().getPrice(shopID, itemID, typePrice);
        }
    
        // Si le prix n'est pas trouvé dans la base de données, chercher dans les fichiers de configuration
        if (price.isEmpty()) {
            price = plugin.getShopConfigManager().getItemValue(shopID, itemID, typePrice, Double.class);
        }
        
        // Si aucun prix n'est trouvé, vérifier si l'item a une recette
        if (price.isEmpty()) {
            ItemStack itemStack = ShopGuiPlusApi.getShop(shopID).getShopItem(itemID).getItem();
            if (itemStack != null) {
                // Calculer le prix via la recette de l'ingrédient
                return calculatePrice(shopID, itemID, itemStack, typePrice, visitedItems);
            }
        }
    
        // Retourner le prix trouvé ou une valeur par défaut
        return price.orElse(10.0); // 10.0 est une valeur par défaut si aucun prix n'est trouvé
    }

    private Double checkCache(String shopID, String itemID, String typePrice) {
        // Utiliser le système de cache global du plugin
        double cachedPrice = plugin.getCachedRecipePrice(shopID, itemID, typePrice);
        
        // Si le prix est en cache (différent de -1.0, la valeur retournée quand le prix n'est pas en cache)
        if (cachedPrice >= 0) {
            return cachedPrice;
        }
        
        return null; // Le prix n'est pas en cache
    }
    
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
        for (Recipe recipe : plugin.getServer().getRecipesFor(item)) {
            if (recipe instanceof ShapedRecipe) {
                return configMain.getDouble("actions.shaped", 1.0);
            } else if (recipe instanceof ShapelessRecipe) {
                return configMain.getDouble("actions.shapeless", 1.0);
            } else if (recipe instanceof FurnaceRecipe) {
                return configMain.getDouble("actions.furnace", 1.0);
            }
        }

        // Retourner un modificateur par défaut si aucune recette n'est trouvée
        return 1.0;
    }
    
}
