package fr.tylwen.satyria.dynashop.data.price.type;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.util.Consumer;

import fr.tylwen.satyria.dynashop.DynaShopPlugin;
import fr.tylwen.satyria.dynashop.PriceItem;
import fr.tylwen.satyria.dynashop.data.param.DynaShopType;
import fr.tylwen.satyria.dynashop.data.param.RecipeType;
import fr.tylwen.satyria.dynashop.data.price.DynamicPrice;
import net.brcdev.shopgui.ShopGuiPlusApi;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
// import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class PriceRecipe implements PriceItem {
    private final FileConfiguration config;
    private final DynaShopPlugin mainPlugin;
    private final PriceRecipe priceRecipe;
    
    // Ajouter ces champs à la classe PriceRecipe
    private final Map<String, List<ItemStack>> ingredientsCache = new HashMap<>();
    private final long CACHE_DURATION = 20L * 60L * 5L; // 5 minutes
    private final Map<String, Long> cacheTimestamps = new HashMap<>();

    // public PriceRecipe(FileConfiguration config) {
    //     this.config = config;
    // }
    public PriceRecipe(DynaShopPlugin mainPlugin) {
        this.mainPlugin = mainPlugin;
        this.config = mainPlugin.getConfig();
        this.priceRecipe = mainPlugin.getPriceRecipe();
    }
    
    // public enum RecipeType {
    //     NONE,
    //     CRAFTING,
    //     SMELTING,
    //     BLAST_FURNACE,
    //     SMOKER,
    //     STONECUTTER
    // }

    // public double calculateBuyPrice(String shopID, String itemID, ItemStack item, List<String> visitedItems) {
    //     List<ItemStack> ingredients = getIngredients(shopID, itemID, item);
    //     ingredients = consolidateIngredients(ingredients);
    //     double basePrice = 0.0;

    //     // Calculer le prix de base en fonction des ingrédients
    //     for (ItemStack ingredient : ingredients) {
    //         if (ingredient == null || ingredient.getType() == Material.AIR) {
    //             continue; // Ignorer les ingrédients invalides
    //         }
    //         double ingredientPrice = getIngredientPrice(ingredient, "buyPrice", visitedItems);
    //         basePrice += ingredientPrice * ingredient.getAmount(); // Multiplier par la quantité de l'ingrédient
    //     }

    //     // Appliquer le modificateur en fonction du type de recette
    //     double modifier = getRecipeModifier(item);
    //     return basePrice * modifier;
    // }
    @Override
    public double calculateBuyPrice(String shopID, String itemID, ItemStack item) {
        return calculatePrice(shopID, itemID, item, "buyPrice", new ArrayList<>());
    }

    // public double calculateSellPrice(String shopID, String itemID, ItemStack item, List<String> visitedItems) {
    //     List<ItemStack> ingredients = getIngredients(shopID, itemID, item);
    //     ingredients = consolidateIngredients(ingredients);
    //     double basePrice = 0.0;

    //     // Calculer le prix de base en fonction des ingrédients
    //     for (ItemStack ingredient : ingredients) {
    //         if (ingredient == null || ingredient.getType() == Material.AIR) {
    //             continue; // Ignorer les ingrédients invalides
    //         }
    //         double ingredientPrice = getIngredientPrice(ingredient, "sellPrice", visitedItems);
    //         basePrice += ingredientPrice * ingredient.getAmount(); // Multiplier par la quantité de l'ingrédient
    //     }

    //     // Appliquer le modificateur en fonction du type de recette
    //     double modifier = getRecipeModifier(item);
    //     return basePrice * modifier;
    // }
    @Override
    public double calculateSellPrice(String shopID, String itemID, ItemStack item) {
        return calculatePrice(shopID, itemID, item, "sellPrice", new ArrayList<>());
    }

    public double calculatePrice(String shopID, String itemID, ItemStack item, String typePrice, List<String> visitedItems) {
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
        Bukkit.getScheduler().runTaskAsynchronously(DynaShopPlugin.getInstance(), () -> {
            List<String> visitedItems = new ArrayList<>();
            double price = calculatePrice(shopID, itemID, item, typePrice, visitedItems);
            
            // Cacher le prix calculé
            DynaShopPlugin.getInstance().cacheRecipePrice(shopID, itemID, typePrice, price);
            
            // Revenir au thread principal pour exécuter le callback
            Bukkit.getScheduler().runTask(DynaShopPlugin.getInstance(), () -> callback.accept(price));
            // try {
            //     // Forcer l'obtention d'une nouvelle connexion à la base de données pour cette opération asynchrone
            //     DynaShopPlugin.getInstance().getDataManager().reloadDatabaseConnection();
                
            //     List<String> visitedItems = new ArrayList<>();
            //     double price = calculatePrice(shopID, itemID, item, typePrice, visitedItems);
                
            //     // Cacher le prix calculé
            //     DynaShopPlugin.getInstance().cacheRecipePrice(shopID, itemID, typePrice, price);
                
            //     // Revenir au thread principal pour exécuter le callback
            //     Bukkit.getScheduler().runTask(DynaShopPlugin.getInstance(), () -> {
            //         try {
            //             callback.accept(price);
            //         } catch (Exception e) {
            //             DynaShopPlugin.getInstance().getLogger().severe("Erreur dans le callback: " + e.getMessage());
            //             e.printStackTrace();
            //         }
            //     });
            // } catch (Exception e) {
            //     DynaShopPlugin.getInstance().getLogger().severe("Erreur lors du calcul asynchrone du prix: " + e.getMessage());
            //     e.printStackTrace();
                
            //     // Revenir au thread principal avec une valeur par défaut
            //     Bukkit.getScheduler().runTask(DynaShopPlugin.getInstance(), () -> {
            //         try {
            //             callback.accept(10.0); // Valeur par défaut en cas d'erreur
            //         } catch (Exception ex) {
            //             DynaShopPlugin.getInstance().getLogger().severe("Erreur dans le callback d'erreur: " + ex.getMessage());
            //         }
            //     });
            // }
        });
    }

    @Override
    public void processBuyTransaction(String shopID, String itemID, int amount) {
        ItemStack itemStack = ShopGuiPlusApi.getShop(shopID).getShopItem(itemID).getItem();
        // ItemStack itemStack = DynaShopPlugin.getInstance().getShopConfigManager().getItemStack(shopID, itemID);
        if (itemStack != null) {
            applyGrowthOrDecayToIngredients(shopID, itemID, itemStack, amount, true, new ArrayList<>(), 0);
        }
    }

    @Override
    public void processSellTransaction(String shopID, String itemID, int amount) {
        ItemStack itemStack = ShopGuiPlusApi.getShop(shopID).getShopItem(itemID).getItem();
        // ItemStack itemStack = DynaShopPlugin.getInstance().getShopConfigManager().getItemStack(shopID, itemID);
        if (itemStack != null) {
            applyGrowthOrDecayToIngredients(shopID, itemID, itemStack, amount, false, new ArrayList<>(), 0);
        }
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

    private double getIngredientPrice(ItemStack ingredient, String typePrice, List<String> visitedItems) {
        String itemID = ShopGuiPlusApi.getItemStackShopItem(ingredient).getId(); // Utiliser l'ID de l'item dans le shop
        String shopID = ShopGuiPlusApi.getItemStackShopItem(ingredient).getShop().getId(); // Utiliser l'ID du shop de l'item
        
        // Vérifier si l'item a déjà été visité
        if (visitedItems.contains(itemID)) {
            DynaShopPlugin.getInstance().getLogger().warning("Boucle détectée pour l'item : " + itemID);
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
        DynaShopType itemType = DynaShopPlugin.getInstance().getShopConfigManager().getTypeDynaShop(shopID, itemID);
        
        // // Si l'ingrédient est un item avec stock, prendre en compte sa valeur de stock
        // if (itemType == DynaShopType.STOCK) {
        //     DynaShopPlugin.getInstance().getLogger().info("Calcul du prix de l'ingrédient " + itemID + " avec stock.");
        //     // Récupérer la valeur de stock actuelle
        //     Optional<Integer> stockOptional = DynaShopPlugin.getInstance().getItemDataManager().getStock(shopID, itemID);
        //     if (stockOptional.isPresent()) {
        //         DynaShopPlugin.getInstance().getLogger().info("Stock de l'ingrédient " + itemID + ": " + stockOptional.get());
        //         int stock = stockOptional.get();
                
        //         // Récupérer les bornes de stock
        //         int minStock = DynaShopPlugin.getInstance().getShopConfigManager()
        //             .getItemValue(shopID, itemID, "stock.min", Integer.class).orElse(0);
        //         int maxStock = DynaShopPlugin.getInstance().getShopConfigManager()
        //             .getItemValue(shopID, itemID, "stock.max", Integer.class).orElse(1000);
                
        //         // Récupérer le prix de base
        //         Optional<Double> basePrice;
        //         if (typePrice.equals("buyPrice")) {
        //             DynaShopPlugin.getInstance().getLogger().info("Calcul du prix d'achat de l'ingrédient " + itemID + " avec stock.");
        //             basePrice = DynaShopPlugin.getInstance().getItemDataManager().getBuyPrice(shopID, itemID);
        //             if (basePrice.isEmpty()) {
        //                 DynaShopPlugin.getInstance().getLogger().info("Prix d'achat non trouvé dans la base de données, vérification de la configuration.");
        //                 basePrice = DynaShopPlugin.getInstance().getShopConfigManager()
        //                     .getItemValue(shopID, itemID, "buyPrice", Double.class);
        //             }
        //         } else { // sellPrice
        //             DynaShopPlugin.getInstance().getLogger().info("Calcul du prix de vente de l'ingrédient " + itemID + " avec stock.");
        //             basePrice = DynaShopPlugin.getInstance().getItemDataManager().getSellPrice(shopID, itemID);
        //             if (basePrice.isEmpty()) {
        //                 DynaShopPlugin.getInstance().getLogger().info("Prix de vente non trouvé dans la base de données, vérification de la configuration.");
        //                 basePrice = DynaShopPlugin.getInstance().getShopConfigManager()
        //                     .getItemValue(shopID, itemID, "sellPrice", Double.class);
        //             }
        //         }
                
        //         if (basePrice.isPresent()) {
        //             DynaShopPlugin.getInstance().getLogger().info("Prix de base de l'ingrédient " + itemID + ": " + basePrice.get());
        //             // Calculer le ratio de stock (entre 0 et 1)
        //             double stockRatio = Math.max(0.0, Math.min(1.0, (double)(stock - minStock) / (maxStock - minStock)));
                    
        //             // Récupérer les bornes de prix
        //             double minPrice = DynaShopPlugin.getInstance().getShopConfigManager()
        //                 .getItemValue(shopID, itemID, typePrice.replace("Price", "Dynamic.min"), Double.class)
        //                 .orElse(basePrice.get() * 0.5);
        //             double maxPrice = DynaShopPlugin.getInstance().getShopConfigManager()
        //                 .getItemValue(shopID, itemID, typePrice.replace("Price", "Dynamic.max"), Double.class)
        //                 .orElse(basePrice.get() * 2.0);
                    
        //             // Même formule que dans adjustPricesBasedOnStock
        //             double price = maxPrice - (maxPrice - minPrice) * stockRatio;
                    
        //             DynaShopPlugin.getInstance().getLogger().info("Prix de l'ingrédient " + itemID + " ajusté par stock: " + price + " (stock: " + stock + ")");
        //             return price;
        //         }
        //     }
        // }
        
        // Si l'ingrédient est un item avec stock, utiliser PriceStock
        if (itemType == DynaShopType.STOCK) {
            return DynaShopPlugin.getInstance().getPriceStock().calculatePrice(shopID, itemID, typePrice);
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
            price = DynaShopPlugin.getInstance().getItemDataManager().getPrice(shopID, itemID, typePrice);
        }
    
        // Si le prix n'est pas trouvé dans la base de données, chercher dans les fichiers de configuration
        if (price.isEmpty()) {
            price = DynaShopPlugin.getInstance().getShopConfigManager().getItemValue(shopID, itemID, typePrice, Double.class);
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

    // Méthodes auxiliaires extraites:
    private Double checkCache(String shopID, String itemID, String typePrice) {
        // Utiliser le système de cache global du plugin
        double cachedPrice = DynaShopPlugin.getInstance().getCachedRecipePrice(shopID, itemID, typePrice);
        
        // Si le prix est en cache (différent de -1.0, la valeur retournée quand le prix n'est pas en cache)
        if (cachedPrice >= 0) {
            // Ajouter du logging pour le débogage
            // if (DynaShopPlugin.getInstance().isDebug()) {
            //     DynaShopPlugin.getInstance().getLogger().info("Cache hit pour " + shopID + ":" + itemID + ":" + typePrice + " = " + cachedPrice);
            // }
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
        for (Recipe recipe : DynaShopPlugin.getInstance().getServer().getRecipesFor(item)) {
            if (recipe instanceof ShapedRecipe) {
                return config.getDouble("actions.shaped", 1.0);
            } else if (recipe instanceof ShapelessRecipe) {
                return config.getDouble("actions.shapeless", 1.0);
            } else if (recipe instanceof FurnaceRecipe) {
                return config.getDouble("actions.furnace", 1.0);
            }
        }

        // Retourner un modificateur par défaut si aucune recette n'est trouvée
        return 1.0;
    }
    
    /**
     * Applique la croissance ou la décroissance aux ingrédients d'une recette.
     * Cette méthode est appelée de manière récursive pour chaque ingrédient trouvé dans la recette.
     * @param shopID
     * @param itemID
     * @param itemStack
     * @param amount
     * @param isGrowth
     * @param visitedItems
     */
    public void applyGrowthOrDecayToIngredients(String shopID, String itemID, ItemStack itemStack, int amount, boolean isGrowth, List<String> visitedItems, int depth) {

        // Limiter la profondeur de récursion
        if (depth > 5) {
            DynaShopPlugin.getInstance().getLogger().warning("Profondeur de récursion maximale atteinte pour " + itemID);
            return;
        }


        // Vérifier si l'item a déjà été visité pour éviter les boucles infinies
        if (visitedItems.contains(itemID)) {
            return;
        }
        visitedItems.add(itemID); // Ajouter l'item à la liste des items visités

        // Récupérer la liste des ingrédients de la recette
        List<ItemStack> ingredients = priceRecipe.getIngredients(shopID, itemID, itemStack);
        ingredients = priceRecipe.consolidateIngredients(ingredients); // Consolider les ingrédients

        for (ItemStack ingredient : ingredients) {
            if (ingredient == null || ingredient.getType() == Material.AIR) {
                continue; // Ignorer les ingrédients invalides
            }

            String ingredientID = ShopGuiPlusApi.getItemStackShopItem(ingredient).getId(); // Utiliser l'ID de l'item dans le shop
            String shopIngredientID = ShopGuiPlusApi.getItemStackShopItem(ingredient).getShop().getId(); // Utiliser l'ID du shop de l'item

            // Récupérer le prix dynamique de l'ingrédient
            // Optional<DynamicPrice> ingredientPriceOpt = DynaShopPlugin.getInstance().getItemDataManager().getPrice(shopID, ingredientID);
            DynamicPrice ingredientPrice = DynaShopPlugin.getInstance().getDynaShopListener().getOrLoadPrice(shopIngredientID, ingredientID, itemStack);

            // if (ingredientPriceOpt.isPresent()) {
            if (ingredientPrice != null) {
                // DynamicPrice ingredientPrice = ingredientPriceOpt.get();

                // Appliquer growth ou decay
                if (isGrowth) {
                    ingredientPrice.applyGrowth(amount * ingredient.getAmount());
                } else {
                    ingredientPrice.applyDecay(amount * ingredient.getAmount());
                }

                // Log pour vérifier les changements
                mainPlugin.getLogger().info("Prix mis à jour pour l'ingrédient " + ingredientID + " x " + amount * ingredient.getAmount() + ": " +
                    "Buy = " + ingredientPrice.getBuyPrice() + ", Sell = " + ingredientPrice.getSellPrice());

                // Sauvegarder les nouveaux prix dans la base de données
                // Si l'ingrédient est lui-même basé sur une recette, appliquer récursivement
                if (!ingredientPrice.isFromRecipe()) {
                    // DynaShopPlugin.getInstance().getItemDataManager().savePrice(shopingredientID, ingredientID, ingredientPrice.getBuyPrice(), ingredientPrice.getSellPrice());
                    DynaShopPlugin.getInstance().getBatchDatabaseUpdater().queueUpdate(shopIngredientID, ingredientID, ingredientPrice);
                } else {
                    // Appliquer la croissance ou la décroissance aux ingrédients de la recette de l'ingrédient
                    applyGrowthOrDecayToIngredients(shopIngredientID, ingredientID, ingredient, ingredient.getAmount(), isGrowth, visitedItems, depth + 1);
                }
            } else {
                mainPlugin.getLogger().warning("Prix dynamique introuvable pour l'ingrédient " + ingredientID + " dans le shop " + shopIngredientID);
            }
        }
    }

    @Override
    public boolean canBuy(String shopID, String itemID, int amount) {
        // Pour les recettes, on peut toujours acheter
        return true;
    }

    @Override
    public boolean canSell(String shopID, String itemID, int amount) {
        // Pour les recettes, on peut toujours vendre
        return true;
    }

    @Override
    public DynaShopType getType() {
        return DynaShopType.RECIPE;
    }
}