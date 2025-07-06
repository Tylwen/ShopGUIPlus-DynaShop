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
package fr.tylwen.satyria.dynashop.price.recipe;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.*;
import fr.tylwen.satyria.dynashop.DynaShopPlugin;
import fr.tylwen.satyria.dynashop.data.param.RecipeType;

import java.util.*;

/**
 * Validateur de recettes qui vérifie la conformité avec les recettes Minecraft vanilla
 */
public class MinecraftRecipeValidator {
    private final DynaShopPlugin plugin;
    private final Map<Material, List<Recipe>> recipeCache = new EnumMap<>(Material.class);
    private final Map<String, RecipeMatchResult> validationCache = new HashMap<>();
    
    public MinecraftRecipeValidator(DynaShopPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Résultat de la validation d'une recette
     */
    public static class RecipeMatchResult {
        private final boolean valid;
        private final Recipe matchedRecipe;
        private final List<String> issues;
        private final double similarity;
        
        public RecipeMatchResult(boolean valid, Recipe matchedRecipe, List<String> issues, double similarity) {
            this.valid = valid;
            this.matchedRecipe = matchedRecipe;
            this.issues = issues;
            this.similarity = similarity;
        }
        
        public boolean isValid() { return valid; }
        public Recipe getMatchedRecipe() { return matchedRecipe; }
        public List<String> getIssues() { return issues; }
        public double getSimilarity() { return similarity; }
    }
    
    /**
     * Variante de recette avec critères de sélection
     */
    public static class RecipeVariant {
        private final Recipe recipe;
        private final List<ItemStack> ingredients;
        private final double estimatedCost;
        private final int availability;
        private final RecipeType recipeType;
        
        public RecipeVariant(Recipe recipe, List<ItemStack> ingredients, double estimatedCost, int availability, RecipeType recipeType) {
            this.recipe = recipe;
            this.ingredients = ingredients;
            this.estimatedCost = estimatedCost;
            this.availability = availability;
            this.recipeType = recipeType;
        }
        
        public Recipe getRecipe() { return recipe; }
        public List<ItemStack> getIngredients() { return ingredients; }
        public double getEstimatedCost() { return estimatedCost; }
        public int getAvailability() { return availability; }
        public RecipeType getRecipeType() { return recipeType; }
    }
    
    /**
     * Valide une recette configurée contre les recettes Minecraft
     */
    public RecipeMatchResult validateRecipe(String shopID, String itemID, List<ItemStack> configuredIngredients, Material resultMaterial) {
        String cacheKey = shopID + ":" + itemID + ":" + configuredIngredients.hashCode();
        
        if (validationCache.containsKey(cacheKey)) {
            return validationCache.get(cacheKey);
        }
        
        List<Recipe> minecraftRecipes = getMinecraftRecipes(resultMaterial);
        if (minecraftRecipes.isEmpty()) {
            // Aucune recette Minecraft trouvée - autoriser la recette custom
            plugin.getLogger().info("Aucune recette Minecraft trouvée pour " + resultMaterial + " - autorisation de la recette custom");
            RecipeMatchResult result = new RecipeMatchResult(true, null, Arrays.asList("Recette custom autorisée"), 1.0);
            validationCache.put(cacheKey, result);
            return result;
        }
        
        RecipeMatchResult bestMatch = findBestMatchingRecipe(configuredIngredients, minecraftRecipes);
        validationCache.put(cacheKey, bestMatch);
        
        return bestMatch;
    }
    
    /**
     * Récupère toutes les recettes Minecraft pour un matériau donné
     */
    public List<Recipe> getMinecraftRecipes(Material material) {
        if (recipeCache.containsKey(material)) {
            return recipeCache.get(material);
        }
        
        List<Recipe> recipes = new ArrayList<>();
        ItemStack targetItem = new ItemStack(material);
        
        try {
            // Utiliser l'API Bukkit pour récupérer les recettes
            List<Recipe> allRecipes = Bukkit.getRecipesFor(targetItem);
            
            for (Recipe recipe : allRecipes) {
                if (recipe.getResult().getType() == material) {
                    recipes.add(recipe);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Erreur lors de la récupération des recettes pour " + material + ": " + e.getMessage());
        }
        
        recipeCache.put(material, recipes);
        return recipes;
    }
    
    /**
     * Trouve la recette Minecraft qui correspond le mieux aux ingrédients configurés
     */
    public RecipeMatchResult findBestMatchingRecipe(List<ItemStack> configuredIngredients, List<Recipe> minecraftRecipes) {
        Recipe bestRecipe = null;
        double bestSimilarity = 0.0;
        List<String> bestIssues = new ArrayList<>();
        
        for (Recipe recipe : minecraftRecipes) {
            List<ItemStack> recipeIngredients = getIngredientsFromRecipe(recipe);
            double similarity = calculateSimilarity(configuredIngredients, recipeIngredients);
            
            if (similarity > bestSimilarity) {
                bestSimilarity = similarity;
                bestRecipe = recipe;
                bestIssues = findIssues(configuredIngredients, recipeIngredients);
            }
        }
        
        boolean isValid = bestSimilarity >= 0.8; // Seuil de similarité de 80%
        
        if (!isValid && bestRecipe != null) {
            plugin.getLogger().warning("Recette configurée ne correspond pas aux recettes Minecraft (similarité: " + 
                                      String.format("%.2f", bestSimilarity * 100) + "%)");
            for (String issue : bestIssues) {
                plugin.getLogger().warning("  - " + issue);
            }
        }
        
        return new RecipeMatchResult(isValid, bestRecipe, bestIssues, bestSimilarity);
    }
    
    /**
     * Extrait les ingrédients d'une recette Minecraft
     */
    public List<ItemStack> getIngredientsFromRecipe(Recipe recipe) {
        List<ItemStack> ingredients = new ArrayList<>();
        
        if (recipe instanceof ShapedRecipe shapedRecipe) {
            ingredients.addAll(extractShapedIngredients(shapedRecipe));
        } else if (recipe instanceof ShapelessRecipe shapelessRecipe) {
            ingredients.addAll(extractShapelessIngredients(shapelessRecipe));
        } else if (recipe instanceof FurnaceRecipe furnaceRecipe) {
            ingredients.addAll(extractSingleIngredient(furnaceRecipe.getInput()));
        } else if (recipe instanceof BlastingRecipe blastingRecipe) {
            ingredients.addAll(extractSingleIngredient(blastingRecipe.getInput()));
        } else if (recipe instanceof SmokingRecipe smokingRecipe) {
            ingredients.addAll(extractSingleIngredient(smokingRecipe.getInput()));
        } else if (recipe instanceof CampfireRecipe campfireRecipe) {
            ingredients.addAll(extractSingleIngredient(campfireRecipe.getInput()));
        } else if (recipe instanceof StonecuttingRecipe stonecuttingRecipe) {
            ingredients.addAll(extractSingleIngredient(stonecuttingRecipe.getInput()));
        } else if (recipe instanceof SmithingRecipe smithingRecipe) {
            ingredients.addAll(extractSmithingIngredients(smithingRecipe));
        }
        
        return ingredients;
    }
    
    /**
     * Extrait les ingrédients d'une recette shaped
     */
    private List<ItemStack> extractShapedIngredients(ShapedRecipe shapedRecipe) {
        List<ItemStack> ingredients = new ArrayList<>();
        Map<Character, ItemStack> ingredientMap = shapedRecipe.getIngredientMap();
        String[] shape = shapedRecipe.getShape();
        
        Map<Material, Integer> materialCounts = new EnumMap<>(Material.class);
        
        for (String row : shape) {
            for (char c : row.toCharArray()) {
                if (c != ' ' && ingredientMap.containsKey(c)) {
                    ItemStack ingredient = ingredientMap.get(c);
                    if (ingredient != null && ingredient.getType() != Material.AIR) {
                        materialCounts.put(ingredient.getType(), 
                            materialCounts.getOrDefault(ingredient.getType(), 0) + 1);
                    }
                }
            }
        }
        
        for (Map.Entry<Material, Integer> entry : materialCounts.entrySet()) {
            ingredients.add(new ItemStack(entry.getKey(), entry.getValue()));
        }
        
        return ingredients;
    }
    
    /**
     * Extrait les ingrédients d'une recette shapeless
     */
    private List<ItemStack> extractShapelessIngredients(ShapelessRecipe shapelessRecipe) {
        List<ItemStack> ingredients = new ArrayList<>();
        List<ItemStack> recipeIngredients = shapelessRecipe.getIngredientList();
        
        Map<Material, Integer> materialCounts = new EnumMap<>(Material.class);
        
        for (ItemStack ingredient : recipeIngredients) {
            if (ingredient != null && ingredient.getType() != Material.AIR) {
                materialCounts.put(ingredient.getType(), 
                    materialCounts.getOrDefault(ingredient.getType(), 0) + ingredient.getAmount());
            }
        }
        
        for (Map.Entry<Material, Integer> entry : materialCounts.entrySet()) {
            ingredients.add(new ItemStack(entry.getKey(), entry.getValue()));
        }
        
        return ingredients;
    }
    
    /**
     * Extrait un ingrédient unique (pour les recettes de type four, etc.)
     */
    private List<ItemStack> extractSingleIngredient(ItemStack input) {
        List<ItemStack> ingredients = new ArrayList<>();
        if (input != null && input.getType() != Material.AIR) {
            ingredients.add(input.clone());
        }
        return ingredients;
    }
    
    /**
     * Extrait les ingrédients d'une recette smithing
     */
    private List<ItemStack> extractSmithingIngredients(SmithingRecipe smithingRecipe) {
        List<ItemStack> ingredients = new ArrayList<>();
        
        try {
            // Utiliser la réflexion pour accéder aux ingrédients si nécessaire
            java.lang.reflect.Method getBaseMethod = smithingRecipe.getClass().getMethod("getBase");
            java.lang.reflect.Method getAdditionMethod = smithingRecipe.getClass().getMethod("getAddition");
            
            ItemStack base = (ItemStack) getBaseMethod.invoke(smithingRecipe);
            ItemStack addition = (ItemStack) getAdditionMethod.invoke(smithingRecipe);
            
            if (base != null && base.getType() != Material.AIR) {
                ingredients.add(base.clone());
            }
            if (addition != null && addition.getType() != Material.AIR) {
                ingredients.add(addition.clone());
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Erreur lors de l'extraction des ingrédients de SmithingRecipe: " + e.getMessage());
        }
        
        return ingredients;
    }
    
    /**
     * Calcule la similarité entre deux listes d'ingrédients
     */
    private double calculateSimilarity(List<ItemStack> configured, List<ItemStack> minecraft) {
        if (configured.isEmpty() && minecraft.isEmpty()) return 1.0;
        if (configured.isEmpty() || minecraft.isEmpty()) return 0.0;
        
        Map<Material, Integer> configuredMap = consolidateIngredients(configured);
        Map<Material, Integer> minecraftMap = consolidateIngredients(minecraft);
        
        Set<Material> allMaterials = new HashSet<>();
        allMaterials.addAll(configuredMap.keySet());
        allMaterials.addAll(minecraftMap.keySet());
        
        int matches = 0;
        int total = 0;
        
        for (Material material : allMaterials) {
            int configuredAmount = configuredMap.getOrDefault(material, 0);
            int minecraftAmount = minecraftMap.getOrDefault(material, 0);
            
            if (configuredAmount == minecraftAmount) {
                matches += Math.max(configuredAmount, minecraftAmount);
            }
            total += Math.max(configuredAmount, minecraftAmount);
        }
        
        return total == 0 ? 0.0 : (double) matches / total;
    }
    
    /**
     * Consolide les ingrédients en comptant les quantités par matériau
     */
    private Map<Material, Integer> consolidateIngredients(List<ItemStack> ingredients) {
        Map<Material, Integer> consolidated = new EnumMap<>(Material.class);
        
        for (ItemStack ingredient : ingredients) {
            if (ingredient != null && ingredient.getType() != Material.AIR) {
                consolidated.put(ingredient.getType(), 
                    consolidated.getOrDefault(ingredient.getType(), 0) + ingredient.getAmount());
            }
        }
        
        return consolidated;
    }
    
    /**
     * Trouve les différences entre la configuration et la recette Minecraft
     */
    private List<String> findIssues(List<ItemStack> configured, List<ItemStack> minecraft) {
        List<String> issues = new ArrayList<>();
        
        Map<Material, Integer> configuredMap = consolidateIngredients(configured);
        Map<Material, Integer> minecraftMap = consolidateIngredients(minecraft);
        
        // Vérifier les ingrédients manquants
        for (Map.Entry<Material, Integer> entry : minecraftMap.entrySet()) {
            Material material = entry.getKey();
            int expectedAmount = entry.getValue();
            int actualAmount = configuredMap.getOrDefault(material, 0);
            
            if (actualAmount == 0) {
                issues.add("Ingrédient manquant: " + material.name());
            } else if (actualAmount != expectedAmount) {
                issues.add("Quantité incorrecte pour " + material.name() + 
                          " (attendu: " + expectedAmount + ", configuré: " + actualAmount + ")");
            }
        }
        
        // Vérifier les ingrédients en trop
        for (Map.Entry<Material, Integer> entry : configuredMap.entrySet()) {
            Material material = entry.getKey();
            if (!minecraftMap.containsKey(material)) {
                issues.add("Ingrédient en trop: " + material.name());
            }
        }
        
        return issues;
    }
    
    /**
     * Obtient toutes les variantes possibles d'une recette
     */
    public List<RecipeVariant> getAllRecipeVariants(Material material) {
        List<RecipeVariant> variants = new ArrayList<>();
        List<Recipe> recipes = getMinecraftRecipes(material);
        
        for (Recipe recipe : recipes) {
            List<ItemStack> ingredients = getIngredientsFromRecipe(recipe);
            RecipeType recipeType = determineRecipeType(recipe);
            
            // Estimation du coût (sera calculé plus précisément ailleurs)
            double estimatedCost = 0.0;
            int availability = 100; // Valeur par défaut
            
            variants.add(new RecipeVariant(recipe, ingredients, estimatedCost, availability, recipeType));
        }
        
        return variants;
    }
    
    /**
     * Détermine le type de recette DynaShop à partir d'une recette Minecraft
     */
    private RecipeType determineRecipeType(Recipe recipe) {
        if (recipe instanceof ShapedRecipe) {
            return RecipeType.SHAPED;
        } else if (recipe instanceof ShapelessRecipe) {
            return RecipeType.SHAPELESS;
        } else if (recipe instanceof FurnaceRecipe || recipe instanceof BlastingRecipe || recipe instanceof SmokingRecipe || recipe instanceof CampfireRecipe) {
            return RecipeType.FURNACE;
        } else if (recipe instanceof StonecuttingRecipe) {
            return RecipeType.STONECUTTER;
        } else if (recipe instanceof SmithingRecipe) {
            return RecipeType.SMITHING;
        } else {
            return RecipeType.NONE;
        }
    }
    
    /**
     * Nettoie les caches (à appeler lors du rechargement)
     */
    public void clearCaches() {
        recipeCache.clear();
        validationCache.clear();
    }
}
