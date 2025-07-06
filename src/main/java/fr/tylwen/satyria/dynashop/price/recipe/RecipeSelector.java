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
import fr.tylwen.satyria.dynashop.price.recipe.MinecraftRecipeValidator.RecipeVariant;

import java.util.*;

/**
 * Sélectionneur de recettes qui choisit la meilleure recette selon différents critères
 */
public class RecipeSelector {
    private final DynaShopPlugin plugin;
    private final MinecraftRecipeValidator validator;
    
    public RecipeSelector(DynaShopPlugin plugin, MinecraftRecipeValidator validator) {
        this.plugin = plugin;
        this.validator = validator;
    }
    
    /**
     * Critères de sélection de recette
     */
    public enum CostCriteria {
        MINIMUM_COST,           // Coût total minimum
        MAXIMUM_AVAILABILITY,   // Disponibilité maximum des ingrédients
        BALANCED,               // Équilibre entre coût et disponibilité
        PROFIT_MAXIMIZATION     // Maximisation du profit
    }
    
    /**
     * Critères d'optimisation
     */
    public static class OptimizationCriteria {
        private final CostCriteria primaryCriteria;
        private final boolean preferVanillaRecipes;
        private final boolean allowCustomRecipes;
        private final double minProfitMargin;
        
        public OptimizationCriteria(CostCriteria primaryCriteria, boolean preferVanillaRecipes, 
                                   boolean allowCustomRecipes, double minProfitMargin) {
            this.primaryCriteria = primaryCriteria;
            this.preferVanillaRecipes = preferVanillaRecipes;
            this.allowCustomRecipes = allowCustomRecipes;
            this.minProfitMargin = minProfitMargin;
        }
        
        public CostCriteria getPrimaryCriteria() { return primaryCriteria; }
        public boolean isPreferVanillaRecipes() { return preferVanillaRecipes; }
        public boolean isAllowCustomRecipes() { return allowCustomRecipes; }
        public double getMinProfitMargin() { return minProfitMargin; }
    }
    
    /**
     * Informations sur le coût d'une recette
     */
    public static class RecipeCostInfo {
        private final Recipe recipe;
        private final List<ItemStack> ingredients;
        private final double totalCost;
        private final double totalSellPrice;
        private final int minAvailableStock;
        private final boolean isVanillaRecipe;
        private final double profitMargin;
        
        public RecipeCostInfo(Recipe recipe, List<ItemStack> ingredients, double totalCost, 
                             double totalSellPrice, int minAvailableStock, boolean isVanillaRecipe) {
            this.recipe = recipe;
            this.ingredients = ingredients;
            this.totalCost = totalCost;
            this.totalSellPrice = totalSellPrice;
            this.minAvailableStock = minAvailableStock;
            this.isVanillaRecipe = isVanillaRecipe;
            this.profitMargin = totalSellPrice > 0 ? (totalSellPrice - totalCost) / totalSellPrice : 0.0;
        }
        
        public Recipe getRecipe() { return recipe; }
        public List<ItemStack> getIngredients() { return ingredients; }
        public double getTotalCost() { return totalCost; }
        public double getTotalSellPrice() { return totalSellPrice; }
        public int getMinAvailableStock() { return minAvailableStock; }
        public boolean isVanillaRecipe() { return isVanillaRecipe; }
        public double getProfitMargin() { return profitMargin; }
    }
    
    /**
     * Sélectionne la meilleure recette selon les critères donnés
     */
    public Recipe selectBestRecipe(List<Recipe> recipes, OptimizationCriteria criteria) {
        if (recipes.isEmpty()) {
            return null;
        }
        
        List<RecipeCostInfo> costInfos = new ArrayList<>();
        
        // Calculer les informations de coût pour chaque recette
        for (Recipe recipe : recipes) {
            List<ItemStack> ingredients = validator.getIngredientsFromRecipe(recipe);
            RecipeCostInfo costInfo = calculateRecipeCost(recipe, ingredients);
            
            // Filtrer selon les critères
            if (shouldIncludeRecipe(costInfo, criteria)) {
                costInfos.add(costInfo);
            }
        }
        
        if (costInfos.isEmpty()) {
            plugin.getLogger().warning("Aucune recette ne satisfait les critères de sélection");
            return recipes.get(0); // Retourner la première recette par défaut
        }
        
        // Trier selon les critères
        costInfos.sort(createComparator(criteria));
        
        RecipeCostInfo bestCostInfo = costInfos.get(0);
        plugin.getLogger().info("Recette sélectionnée: coût=" + bestCostInfo.getTotalCost() + 
                               ", stock=" + bestCostInfo.getMinAvailableStock() + 
                               ", vanilla=" + bestCostInfo.isVanillaRecipe());
        
        return bestCostInfo.getRecipe();
    }
    
    /**
     * Sélectionne la meilleure variante de recette
     */
    public RecipeVariant selectOptimalVariant(List<RecipeVariant> variants, OptimizationCriteria criteria) {
        if (variants.isEmpty()) {
            return null;
        }
        
        List<RecipeCostInfo> costInfos = new ArrayList<>();
        
        for (RecipeVariant variant : variants) {
            RecipeCostInfo costInfo = calculateRecipeCost(variant.getRecipe(), variant.getIngredients());
            
            if (shouldIncludeRecipe(costInfo, criteria)) {
                costInfos.add(costInfo);
            }
        }
        
        if (costInfos.isEmpty()) {
            return variants.get(0); // Retourner la première variante par défaut
        }
        
        costInfos.sort(createComparator(criteria));
        RecipeCostInfo bestCostInfo = costInfos.get(0);
        
        // Retrouver la variante correspondante
        for (RecipeVariant variant : variants) {
            if (variant.getRecipe().equals(bestCostInfo.getRecipe())) {
                return variant;
            }
        }
        
        return variants.get(0);
    }
    
    /**
     * Calcule le coût total d'une recette
     */
    private RecipeCostInfo calculateRecipeCost(Recipe recipe, List<ItemStack> ingredients) {
        double totalCost = 0.0;
        double totalSellPrice = 0.0;
        int minAvailableStock = Integer.MAX_VALUE;
        boolean isVanillaRecipe = recipe != null;
        
        for (ItemStack ingredient : ingredients) {
            if (ingredient == null || ingredient.getType() == Material.AIR) {
                continue;
            }
            
            // Trouver le prix de l'ingrédient
            DynamicPrice price = findIngredientPrice(ingredient);
            if (price != null) {
                double ingredientCost = price.getBuyPrice() * ingredient.getAmount();
                double ingredientSellPrice = price.getSellPrice() * ingredient.getAmount();
                int ingredientStock = price.getStock();
                
                if (ingredientCost > 0) {
                    totalCost += ingredientCost;
                }
                if (ingredientSellPrice > 0) {
                    totalSellPrice += ingredientSellPrice;
                }
                if (ingredientStock > 0) {
                    minAvailableStock = Math.min(minAvailableStock, ingredientStock / ingredient.getAmount());
                }
            }
        }
        
        if (minAvailableStock == Integer.MAX_VALUE) {
            minAvailableStock = 0;
        }
        
        return new RecipeCostInfo(recipe, ingredients, totalCost, totalSellPrice, minAvailableStock, isVanillaRecipe);
    }
    
    /**
     * Trouve le prix d'un ingrédient dans les shops
     */
    private DynamicPrice findIngredientPrice(ItemStack ingredient) {
        if (ingredient == null) {
            return null;
        }
        
        try {
            // Rechercher l'ingrédient dans tous les shops en utilisant la logique du système existant
            FoundItem foundItem = findItemInAllShops(ingredient);
            
            if (foundItem != null && foundItem.isFound()) {
                // Utiliser getOrLoadPrice du DynaShopListener pour récupérer le prix complet
                return plugin.getDynaShopListener().getOrLoadPrice(
                    null, // Player (peut être null pour les calculs internes)
                    foundItem.getShopID(), 
                    foundItem.getItemID(), 
                    ingredient, 
                    new HashSet<>(), // Visited items (vide pour éviter les cycles)
                    new HashMap<>()  // Last results (vide)
                );
            }
            
            plugin.getLogger().fine("Aucun prix trouvé pour l'ingrédient: " + ingredient.getType());
            return null;
            
        } catch (Exception e) {
            plugin.getLogger().warning("Erreur lors de la recherche de prix pour " + ingredient.getType() + ": " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Trouve un item dans tous les shops disponibles en utilisant la logique de PriceRecipe
     */
    private FoundItem findItemInAllShops(ItemStack targetItem) {
        try {
            // Utiliser l'API ShopGUI+ pour parcourir tous les shops
            for (net.brcdev.shopgui.shop.Shop shop : net.brcdev.shopgui.ShopGuiPlusApi.getPlugin().getShopManager().getShops()) {
                for (net.brcdev.shopgui.shop.item.ShopItem shopItem : shop.getShopItems()) {
                    ItemStack shopItemStack = shopItem.getItem();
                    
                    // Vérifier si l'item correspond (même logique que dans PriceRecipe.findItemInShops)
                    if (areItemsEquivalent(targetItem, shopItemStack)) {
                        return new FoundItem(shop.getId(), shopItem.getId(), true);
                    }
                }
            }
            
            return new FoundItem("", "", false);
            
        } catch (Exception e) {
            plugin.getLogger().warning("Erreur lors de la recherche d'item dans les shops: " + e.getMessage());
            return new FoundItem("", "", false);
        }
    }
    
    /**
     * Vérifie si deux items sont équivalents pour le crafting (même logique que PriceRecipe)
     */
    private boolean areItemsEquivalent(ItemStack item1, ItemStack item2) {
        if (item1 == null || item2 == null) {
            return false;
        }
        
        // Même type de matériau - pour les recettes, on peut être moins strict sur les métadonnées
        return item1.getType() == item2.getType();
    }
    
    /**
     * Classe pour représenter un item trouvé dans les shops (similaire à PriceRecipe.FoundItem)
     */
    private static class FoundItem {
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
     * Détermine si une recette doit être incluse selon les critères
     */
    private boolean shouldIncludeRecipe(RecipeCostInfo costInfo, OptimizationCriteria criteria) {
        // Vérifier les critères de base
        if (!criteria.isAllowCustomRecipes() && !costInfo.isVanillaRecipe()) {
            return false;
        }
        
        // Vérifier la marge de profit minimum
        if (costInfo.getProfitMargin() < criteria.getMinProfitMargin()) {
            return false;
        }
        
        // Vérifier la disponibilité minimum
        return costInfo.getMinAvailableStock() > 0;
    }
    
    /**
     * Crée un comparateur selon les critères d'optimisation
     */
    private Comparator<RecipeCostInfo> createComparator(OptimizationCriteria criteria) {
        return (info1, info2) -> {
            // Priorité aux recettes vanilla si demandé
            if (criteria.isPreferVanillaRecipes()) {
                if (info1.isVanillaRecipe() && !info2.isVanillaRecipe()) {
                    return -1;
                } else if (!info1.isVanillaRecipe() && info2.isVanillaRecipe()) {
                    return 1;
                }
            }
            
            // Appliquer le critère principal
            switch (criteria.getPrimaryCriteria()) {
                case MINIMUM_COST:
                    return Double.compare(info1.getTotalCost(), info2.getTotalCost());
                    
                case MAXIMUM_AVAILABILITY:
                    return Integer.compare(info2.getMinAvailableStock(), info1.getMinAvailableStock());
                    
                case PROFIT_MAXIMIZATION:
                    return Double.compare(info2.getProfitMargin(), info1.getProfitMargin());
                    
                case BALANCED:
                    // Score équilibré: 50% coût, 30% disponibilité, 20% profit
                    double score1 = calculateBalancedScore(info1);
                    double score2 = calculateBalancedScore(info2);
                    return Double.compare(score2, score1); // Score plus élevé = meilleur
                    
                default:
                    return 0;
            }
        };
    }
    
    /**
     * Calcule un score équilibré pour une recette
     */
    private double calculateBalancedScore(RecipeCostInfo info) {
        // Normaliser les valeurs (approximation)
        double costScore = info.getTotalCost() > 0 ? 1.0 / info.getTotalCost() : 0.0;
        double availabilityScore = Math.min(info.getMinAvailableStock() / 100.0, 1.0);
        double profitScore = Math.min(info.getProfitMargin(), 1.0);
        
        return 0.5 * costScore + 0.3 * availabilityScore + 0.2 * profitScore;
    }
    
    /**
     * Vérifie si une recette peut être craftée avec les items disponibles
     */
    public boolean canCraftWith(Recipe recipe, List<ItemStack> availableItems) {
        List<ItemStack> requiredIngredients = validator.getIngredientsFromRecipe(recipe);
        
        // Créer une carte des items disponibles
        Map<Material, Integer> availableMap = new EnumMap<>(Material.class);
        for (ItemStack item : availableItems) {
            if (item != null && item.getType() != Material.AIR) {
                availableMap.put(item.getType(), 
                    availableMap.getOrDefault(item.getType(), 0) + item.getAmount());
            }
        }
        
        // Vérifier si tous les ingrédients requis sont disponibles
        for (ItemStack required : requiredIngredients) {
            if (required == null || required.getType() == Material.AIR) {
                continue;
            }
            
            int availableAmount = availableMap.getOrDefault(required.getType(), 0);
            if (availableAmount < required.getAmount()) {
                return false;
            }
        }
        
        return true;
    }
}
