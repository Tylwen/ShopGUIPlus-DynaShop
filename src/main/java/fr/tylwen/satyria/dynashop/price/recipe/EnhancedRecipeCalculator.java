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

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

import fr.tylwen.satyria.dynashop.DynaShopPlugin;
import fr.tylwen.satyria.dynashop.price.DynamicPrice;
import fr.tylwen.satyria.dynashop.price.PriceRecipe.RecipeCalculationResult;
import fr.tylwen.satyria.dynashop.price.recipe.MinecraftRecipeValidator.RecipeMatchResult;
import fr.tylwen.satyria.dynashop.price.recipe.MinecraftRecipeValidator.RecipeVariant;
import fr.tylwen.satyria.dynashop.price.recipe.RecipeSelector.OptimizationCriteria;
import fr.tylwen.satyria.dynashop.price.recipe.RecipeSelector.CostCriteria;
import net.brcdev.shopgui.ShopGuiPlusApi;
import net.brcdev.shopgui.shop.item.ShopItem;

import java.util.*;

/**
 * Calculateur de recettes amélioré avec validation Minecraft et sélection intelligente
 */
public class EnhancedRecipeCalculator {
    private final DynaShopPlugin plugin;
    private final MinecraftRecipeValidator validator;
    private final RecipeSelector selector;
    
    // Cache pour les résultats de validation
    private final Map<String, RecipeValidationResult> validationCache = new HashMap<>();
    
    public EnhancedRecipeCalculator(DynaShopPlugin plugin) {
        this.plugin = plugin;
        this.validator = new MinecraftRecipeValidator(plugin);
        this.selector = new RecipeSelector(plugin, validator);
    }
    
    /**
     * Résultat de validation d'une recette
     */
    public static class RecipeValidationResult {
        private final boolean isValid;
        private final boolean isVanillaCompliant;
        private final List<ItemStack> validatedIngredients;
        private final Recipe matchedRecipe;
        private final List<String> warnings;
        
        public RecipeValidationResult(boolean isValid, boolean isVanillaCompliant, 
                                     List<ItemStack> validatedIngredients, Recipe matchedRecipe, 
                                     List<String> warnings) {
            this.isValid = isValid;
            this.isVanillaCompliant = isVanillaCompliant;
            this.validatedIngredients = validatedIngredients;
            this.matchedRecipe = matchedRecipe;
            this.warnings = warnings;
        }
        
        public boolean isValid() { return isValid; }
        public boolean isVanillaCompliant() { return isVanillaCompliant; }
        public List<ItemStack> getValidatedIngredients() { return validatedIngredients; }
        public Recipe getMatchedRecipe() { return matchedRecipe; }
        public List<String> getWarnings() { return warnings; }
    }
    
    /**
     * Calcule le prix d'une recette avec validation Minecraft
     */
    public RecipeCalculationResult calculateWithMinecraftValidation(String shopID, String itemID) {
        try {
            // Récupérer l'item du shop
            ShopItem shopItem = ShopGuiPlusApi.getShop(shopID).getShopItem(itemID);
            if (shopItem == null) {
                plugin.getLogger().warning("Item non trouvé: " + shopID + ":" + itemID);
                return createErrorResult();
            }
            
            ItemStack resultItem = shopItem.getItem();
            if (resultItem == null) {
                plugin.getLogger().warning("ItemStack null pour: " + shopID + ":" + itemID);
                return createErrorResult();
            }
            
            // Validation avec les recettes Minecraft
            RecipeValidationResult validationResult = validateRecipeConfiguration(shopID, itemID, resultItem.getType());
            
            if (!validationResult.isValid()) {
                plugin.getLogger().warning("Recette invalide pour " + shopID + ":" + itemID);
                for (String warning : validationResult.getWarnings()) {
                    plugin.getLogger().warning("  - " + warning);
                }
                // Fallback vers le calcul traditionnel
                return plugin.getPriceRecipe().calculateRecipeValues(shopID, itemID, new HashSet<>(), new HashMap<>());
            }
            
            // Utiliser les ingrédients validés
            List<ItemStack> validatedIngredients = validationResult.getValidatedIngredients();
            
            // Calculer le prix avec les ingrédients validés
            return calculatePriceWithValidatedIngredients(shopID, itemID, validatedIngredients, validationResult.isVanillaCompliant());
            
        } catch (Exception e) {
            plugin.getLogger().severe("Erreur lors du calcul de recette pour " + shopID + ":" + itemID + ": " + e.getMessage());
            return createErrorResult();
        }
    }
    
    /**
     * Obtient toutes les variantes possibles d'une recette
     */
    public List<RecipeVariant> getAllPossibleVariants(String shopID, String itemID) {
        try {
            ShopItem shopItem = ShopGuiPlusApi.getShop(shopID).getShopItem(itemID);
            if (shopItem == null) {
                return new ArrayList<>();
            }
            
            ItemStack resultItem = shopItem.getItem();
            if (resultItem == null) {
                return new ArrayList<>();
            }
            
            // Créer les variantes avec estimation des coûts
            return validator.getAllRecipeVariants(resultItem.getType());
            
        } catch (Exception e) {
            plugin.getLogger().warning("Erreur lors de la récupération des variantes pour " + shopID + ":" + itemID + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    /**
     * Sélectionne la variante optimale selon les critères
     */
    public RecipeVariant selectOptimalVariant(List<RecipeVariant> variants, OptimizationCriteria criteria) {
        if (variants.isEmpty()) {
            return null;
        }
        
        return selector.selectOptimalVariant(variants, criteria);
    }
    
    /**
     * Valide la configuration d'une recette
     */
    private RecipeValidationResult validateRecipeConfiguration(String shopID, String itemID, Material resultMaterial) {
        String cacheKey = shopID + ":" + itemID;
        
        if (validationCache.containsKey(cacheKey)) {
            return validationCache.get(cacheKey);
        }
        
        // Récupérer les ingrédients configurés
        List<ItemStack> configuredIngredients = plugin.getPriceRecipe().getIngredients(shopID, itemID);
        
        // Valider contre les recettes Minecraft
        RecipeMatchResult matchResult = validator.validateRecipe(shopID, itemID, configuredIngredients, resultMaterial);
        
        List<String> warnings = new ArrayList<>(matchResult.getIssues());
        
        // Déterminer si la recette est valide
        boolean isValid = true;
        boolean isVanillaCompliant = matchResult.isValid();
        
        List<ItemStack> validatedIngredients = configuredIngredients;
        Recipe matchedRecipe = matchResult.getMatchedRecipe();
        
        // Si la recette n'est pas conforme à Minecraft, utiliser la recette configurée mais avec un avertissement
        if (!isVanillaCompliant) {
            warnings.add("La recette configurée ne correspond pas exactement aux recettes Minecraft vanilla");
            
            // Vérifier si on doit utiliser la recette Minecraft à la place
            if (plugin.getConfig().getBoolean("recipe.enforce-minecraft-compliance", false)) {
                if (matchedRecipe != null) {
                    validatedIngredients = validator.getIngredientsFromRecipe(matchedRecipe);
                    warnings.add("Utilisation de la recette Minecraft à la place de la configuration");
                } else {
                    warnings.add("Aucune recette Minecraft trouvée - recette personnalisée autorisée");
                }
            }
        }
        
        RecipeValidationResult result = new RecipeValidationResult(isValid, isVanillaCompliant, validatedIngredients, matchedRecipe, warnings);
        validationCache.put(cacheKey, result);
        
        return result;
    }
    
    /**
     * Calcule le prix avec les ingrédients validés
     */
    private RecipeCalculationResult calculatePriceWithValidatedIngredients(String shopID, String itemID, 
                                                                          List<ItemStack> validatedIngredients, 
                                                                          boolean isVanillaCompliant) {
        // Variables pour le calcul
        double totalBuyPrice = 0.0;
        double totalSellPrice = 0.0;
        double minBuyPrice = 0.0;
        double maxBuyPrice = 0.0;
        double minSellPrice = 0.0;
        double maxSellPrice = 0.0;
        int minAvailableStock = Integer.MAX_VALUE;
        int totalMinStock = 0;
        int totalMaxStock = 0;
        
        boolean allBuyPricesValid = true;
        boolean allSellPricesValid = true;
        
        // Calculer les prix des ingrédients
        for (ItemStack ingredient : validatedIngredients) {
            if (ingredient == null || ingredient.getType() == Material.AIR) {
                continue;
            }
            
            // Trouver l'ingrédient dans les shops
            DynamicPrice ingredientPrice = findIngredientPrice(shopID, ingredient);
            
            if (ingredientPrice != null) {
                // Calculer les prix
                double buyPrice = ingredientPrice.getBuyPrice() * ingredient.getAmount();
                double sellPrice = ingredientPrice.getSellPrice() * ingredient.getAmount();
                
                if (buyPrice > 0) {
                    totalBuyPrice += buyPrice;
                    minBuyPrice += ingredientPrice.getMinBuyPrice() * ingredient.getAmount();
                    maxBuyPrice += ingredientPrice.getMaxBuyPrice() * ingredient.getAmount();
                } else {
                    allBuyPricesValid = false;
                }
                
                if (sellPrice > 0) {
                    totalSellPrice += sellPrice;
                    minSellPrice += ingredientPrice.getMinSellPrice() * ingredient.getAmount();
                    maxSellPrice += ingredientPrice.getMaxSellPrice() * ingredient.getAmount();
                } else {
                    allSellPricesValid = false;
                }
                
                // Calculer le stock
                int ingredientStock = ingredientPrice.getStock();
                if (ingredientStock > 0) {
                    int availableForRecipe = ingredientStock / ingredient.getAmount();
                    minAvailableStock = Math.min(minAvailableStock, availableForRecipe);
                    totalMinStock += ingredientPrice.getMinStock() / ingredient.getAmount();
                    totalMaxStock += ingredientPrice.getMaxStock() / ingredient.getAmount();
                }
            } else {
                plugin.getLogger().warning("Prix non trouvé pour l'ingrédient: " + ingredient.getType());
                allBuyPricesValid = false;
                allSellPricesValid = false;
            }
        }
        
        // Ajuster les prix si certains ingrédients n'ont pas de prix
        if (!allBuyPricesValid) {
            totalBuyPrice = -1.0;
            minBuyPrice = -1.0;
            maxBuyPrice = -1.0;
        }
        
        if (!allSellPricesValid) {
            totalSellPrice = -1.0;
            minSellPrice = -1.0;
            maxSellPrice = -1.0;
        }
        
        if (minAvailableStock == Integer.MAX_VALUE) {
            minAvailableStock = 0;
        }
        
        // Appliquer les modificateurs selon le type de recette
        if (isVanillaCompliant) {
            // Bonus pour les recettes vanilla
            double vanillaBonus = plugin.getConfig().getDouble("recipe.vanilla-bonus", 1.0);
            if (totalBuyPrice > 0) totalBuyPrice *= vanillaBonus;
            if (totalSellPrice > 0) totalSellPrice *= vanillaBonus;
        }
        
        plugin.getLogger().info("Calcul de recette " + (isVanillaCompliant ? "vanilla" : "custom") + 
                               " pour " + shopID + ":" + itemID + 
                               " - Achat: " + totalBuyPrice + ", Vente: " + totalSellPrice + 
                               ", Stock: " + minAvailableStock);
        
        return plugin.getPriceRecipe().new RecipeCalculationResult(
            totalBuyPrice, totalSellPrice,
            minBuyPrice, maxBuyPrice,
            minSellPrice, maxSellPrice,
            minAvailableStock, totalMinStock, totalMaxStock
        );
    }
    
    /**
     * Trouve le prix d'un ingrédient dans les shops
     */
    private DynamicPrice findIngredientPrice(String shopID, ItemStack ingredient) {
        // Utiliser la méthode existante du système DynaShop
        try {
            return plugin.getDynaShopListener().getOrLoadPriceInternal(
                null, shopID, ingredient.getType().name(), ingredient, 
                new HashSet<>(), new HashMap<>(), true
            );
        } catch (Exception e) {
            plugin.getLogger().warning("Erreur lors de la recherche du prix pour " + ingredient.getType() + ": " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Crée un résultat d'erreur par défaut
     */
    private RecipeCalculationResult createErrorResult() {
        return plugin.getPriceRecipe().new RecipeCalculationResult(
            -1.0, -1.0,   // Prix d'achat et de vente
            -1.0, -1.0,   // Min/Max prix d'achat
            -1.0, -1.0,   // Min/Max prix de vente
            0, 0, 0       // Stock actuel, min, max
        );
    }
    
    /**
     * Crée des critères d'optimisation par défaut
     */
    public OptimizationCriteria createDefaultCriteria() {
        boolean preferVanilla = plugin.getConfig().getBoolean("recipe.prefer-vanilla", true);
        boolean allowCustom = plugin.getConfig().getBoolean("recipe.allow-custom", true);
        double minProfit = plugin.getConfig().getDouble("recipe.min-profit-margin", 0.1);
        
        return new OptimizationCriteria(CostCriteria.BALANCED, preferVanilla, allowCustom, minProfit);
    }
    
    /**
     * Nettoie les caches
     */
    public void clearCaches() {
        validationCache.clear();
        validator.clearCaches();
    }
}
