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
package fr.tylwen.satyria.dynashop.utils;

import fr.tylwen.satyria.dynashop.DynaShopPlugin;
import fr.tylwen.satyria.dynashop.data.param.DynaShopType;

import net.brcdev.shopgui.ShopGuiPlusApi;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
// import java.util.ArrayList;
import java.util.HashSet;
import java.util.Optional;

import org.bukkit.inventory.ItemStack;

public class PriceFormatter {
    
    private final DynaShopPlugin plugin;
    
    public PriceFormatter(DynaShopPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Formate un prix en respectant les paramètres de configuration
     */
    public String formatPrice(double price) {
        try {
            // Récupérer les séparateurs depuis la configuration
            int maximumFractionDigits = ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getInt("numberFormat.maximumFractionDigits", 8);
            String decimalSeparator = ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getString("numberFormat.decimalSeparator", ".");
            String groupingSeparator = ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getString("numberFormat.groupingSeparator", ",");
            int minimumFractionDigits = ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getInt("numberFormat.minimumFractionDigits", 0);
            boolean hideFraction = ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getBoolean("numberFormat.hideFraction", true);
            
            // Vérifier si le nombre est un entier et si hideFraction est activé
            boolean isInteger = (price == Math.floor(price)) && !Double.isInfinite(price);
            
            // Configurer les symboles de formatage
            DecimalFormatSymbols symbols = new DecimalFormatSymbols();
            symbols.setDecimalSeparator(decimalSeparator.charAt(0));
            symbols.setGroupingSeparator(groupingSeparator.charAt(0));
            
            // Créer un format adapté
            DecimalFormat df;
            
            if (hideFraction && isInteger) {
                // Aucune décimale pour les entiers quand hideFraction est activé
                df = new DecimalFormat("#,##0", symbols);
                df.setMinimumFractionDigits(0);
                df.setMaximumFractionDigits(0);
            } else {
                // Pattern normal pour les nombres avec décimales
                df = new DecimalFormat("#,##0.#", symbols);
                df.setMaximumFractionDigits(maximumFractionDigits);
                df.setMinimumFractionDigits(minimumFractionDigits);
            }
            
            // Activer le regroupement des chiffres
            df.setGroupingUsed(true);
            
            return df.format(price);
        } catch (Exception e) {
            // En cas d'erreur, revenir à un format simple
            return String.format("%.2f", price);
        }
    }
    
    /**
     * Formate un nombre d'items en stock
     */
    public String formatStock(int stock) {
        try {
            // Récupérer les séparateurs depuis la configuration
            String groupingSeparator = ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getString("numberFormat.groupingSeparator", ",");
            
            // Configurer les symboles de formatage
            DecimalFormatSymbols symbols = new DecimalFormatSymbols();
            symbols.setGroupingSeparator(groupingSeparator.charAt(0));
            
            // Créer un format adapté pour les entiers
            DecimalFormat df = new DecimalFormat("#,##0", symbols);
            df.setGroupingUsed(true);
            
            return df.format(stock);
        } catch (Exception e) {
            // En cas d'erreur, revenir à un format simple
            return String.valueOf(stock);
        }
    }
    
    /**
     * Obtient le prix selon le type spécifié.
     */
    public String getPriceByType(String shopID, String itemID, String priceType) {
        boolean useRecipe = plugin.getShopConfigManager().getTypeDynaShop(shopID, itemID).orElse(DynaShopType.NONE) == DynaShopType.RECIPE;
        // boolean useRecipe = plugin.getShopConfigManager().resolveTypeDynaShop(shopID, itemID, priceType.equals("buy") || priceType.equals("buy_min") || priceType.equals("buy_max")) == DynaShopType.RECIPE;
        ItemStack itemStack = null;
        
        if (useRecipe) {
            itemStack = ShopGuiPlusApi.getShop(shopID).getShopItem(itemID).getItem();
            if (itemStack == null) {
                return "N/A";
            }
        }

        switch (priceType) {
            case "buy":
                if (useRecipe) {
                    // Si c'est un DynaShop de type RECIPE, on utilise le prix de la recette
                    // double recipePrice = plugin.getPriceRecipe().calculatePrice(shopID, itemID, itemStack, "buyPrice", new ArrayList<>());
                    
                    // Utiliser le cache pour récupérer le prix d'achat d'une recette
                    final ItemStack finalItem = itemStack;
                    double recipePrice = plugin.getCalculatedPriceCache().get(shopID + ":" + itemID + ":buyPrice", 
                        () -> plugin.getPriceRecipe().calculatePrice(shopID, itemID, finalItem, "buyPrice", new HashSet<>()));

                    if (recipePrice >= 0) {
                        return formatPrice(recipePrice);
                    }
                }

                // Priorité à la base de données
                Optional<Double> buyPrice = plugin.getStorageManager().getBuyPrice(shopID, itemID);
                if (buyPrice.isPresent()) {
                    return formatPrice(buyPrice.get());
                }
                
                // Fallback sur les fichiers de configuration
                Optional<Double> configBuyPrice = plugin.getShopConfigManager().getItemValue(shopID, itemID, "buyPrice", Double.class);
                if (configBuyPrice.isPresent()) {
                    return formatPrice(configBuyPrice.get());
                }
                
                return "N/A";
                
            case "sell":
                if (useRecipe) {
                    // Si c'est un DynaShop de type RECIPE, on utilise le prix de la recette
                    // double recipePrice = plugin.getPriceRecipe().calculatePrice(shopID, itemID, itemStack, "sellPrice", new ArrayList<>());

                    final ItemStack finalItem = itemStack;
                    double recipePrice = plugin.getCalculatedPriceCache().get(shopID + ":" + itemID + ":sellPrice", 
                        () -> plugin.getPriceRecipe().calculatePrice(shopID, itemID, finalItem, "sellPrice", new HashSet<>()));
                    if (recipePrice >= 0) {
                        return formatPrice(recipePrice);
                    }
                }

                // Priorité à la base de données
                Optional<Double> sellPrice = plugin.getStorageManager().getSellPrice(shopID, itemID);
                if (sellPrice.isPresent()) {
                    return formatPrice(sellPrice.get());
                }
                
                // Fallback sur les fichiers de configuration
                Optional<Double> configSellPrice = plugin.getShopConfigManager().getItemValue(shopID, itemID, "sellPrice", Double.class);
                if (configSellPrice.isPresent()) {
                    return formatPrice(configSellPrice.get());
                }
                
                return "N/A";
                
            case "buy_min":
                if (useRecipe) {
                    // Si c'est un DynaShop de type RECIPE, on utilise le prix de la recette
                    // double recipePrice = plugin.getPriceRecipe().calculatePrice(shopID, itemID, itemStack, "buyDynamic.min", new ArrayList<>());
                    // double recipePrice = plugin.getCachedRecipePrice(shopID, itemID, "buyDynamic.min");
                    final ItemStack finalItem = itemStack;
                    double recipePrice = plugin.getCalculatedPriceCache().get(shopID + ":" + itemID + ":buyDynamic.min", 
                        () -> plugin.getPriceRecipe().calculatePrice(shopID, itemID, finalItem, "buyDynamic.min", new HashSet<>()));
                    if (recipePrice >= 0) {
                        return formatPrice(recipePrice);
                    }
                }
                
                // Fallback sur les fichiers de configuration
                Optional<Double> configBuyMinPrice = plugin.getShopConfigManager().getItemValue(shopID, itemID, "buyMinPrice", Double.class);
                if (configBuyMinPrice.isPresent()) {
                    return formatPrice(configBuyMinPrice.get());
                }
                
                return "N/A";

            case "sell_min":
                if (useRecipe) {
                    // Si c'est un DynaShop de type RECIPE, on utilise le prix de la recette
                    // double recipePrice = plugin.getPriceRecipe().calculatePrice(shopID, itemID, itemStack, "sellDynamic.min", new ArrayList<>());
                    // double recipePrice = plugin.getCachedRecipePrice(shopID, itemID, "sellDynamic.min");
                    final ItemStack finalItem = itemStack;
                    double recipePrice = plugin.getCalculatedPriceCache().get(shopID + ":" + itemID + ":sellDynamic.min", 
                        () -> plugin.getPriceRecipe().calculatePrice(shopID, itemID, finalItem, "sellDynamic.min", new HashSet<>()));
                    if (recipePrice >= 0) {
                        return formatPrice(recipePrice);
                    }
                }
                
                // Fallback sur les fichiers de configuration
                Optional<Double> configSellMinPrice = plugin.getShopConfigManager().getItemValue(shopID, itemID, "sellMinPrice", Double.class);
                if (configSellMinPrice.isPresent()) {
                    return formatPrice(configSellMinPrice.get());
                }
                
                return "N/A";

            case "buy_max":
                if (useRecipe) {
                    // Si c'est un DynaShop de type RECIPE, on utilise le prix de la recette
                    // double recipePrice = plugin.getPriceRecipe().calculatePrice(shopID, itemID, itemStack, "buyDynamic.max", new ArrayList<>());
                    // double recipePrice = plugin.getCachedRecipePrice(shopID, itemID, "buyDynamic.max");
                    final ItemStack finalItem = itemStack;
                    double recipePrice = plugin.getCalculatedPriceCache().get(shopID + ":" + itemID + ":buyDynamic.max", 
                        () -> plugin.getPriceRecipe().calculatePrice(shopID, itemID, finalItem, "buyDynamic.max", new HashSet<>()));
                    if (recipePrice >= 0) {
                        return formatPrice(recipePrice);
                    }
                }
                
                // Fallback sur les fichiers de configuration
                Optional<Double> configBuyMaxPrice = plugin.getShopConfigManager().getItemValue(shopID, itemID, "buyMaxPrice", Double.class);
                if (configBuyMaxPrice.isPresent()) {
                    return formatPrice(configBuyMaxPrice.get());
                }
                
                return "N/A";

            case "sell_max":
                if (useRecipe) {
                    // Si c'est un DynaShop de type RECIPE, on utilise le prix de la recette
                    // double recipePrice = plugin.getPriceRecipe().calculatePrice(shopID, itemID, itemStack, "sellDynamic.max", new ArrayList<>());
                    // double recipePrice = plugin.getCachedRecipePrice(shopID, itemID, "sellDynamic.max");
                    final ItemStack finalItem = itemStack;
                    double recipePrice = plugin.getCalculatedPriceCache().get(shopID + ":" + itemID + ":sellDynamic.max", 
                        () -> plugin.getPriceRecipe().calculatePrice(shopID, itemID, finalItem, "sellDynamic.max", new HashSet<>()));
                    if (recipePrice >= 0) {
                        return formatPrice(recipePrice);
                    }
                }
                
                // Fallback sur les fichiers de configuration
                Optional<Double> configSellMaxPrice = plugin.getShopConfigManager().getItemValue(shopID, itemID, "sellMaxPrice", Double.class);
                if (configSellMaxPrice.isPresent()) {
                    return formatPrice(configSellMaxPrice.get());
                }
                
                return "N/A";
            
            default:
                return "N/A";
        }
    }
    
    /**
     * Obtient le stock selon le type spécifié.
     */
    public String getStockByType(String shopID, String itemID, String stockType) {
        switch (stockType) {
            case "stock":
                Optional<Integer> stockOptional = plugin.getStorageManager().getStock(shopID, itemID);
                if (stockOptional.isPresent()) {
                    return formatStock(stockOptional.get());
                }

                // Fallback sur les fichiers de configuration
                stockOptional = plugin.getShopConfigManager().getItemValue(shopID, itemID, "stock.base", Integer.class);
                if (stockOptional.isPresent()) {
                    return formatStock(stockOptional.get());
                }

                return "N/A";

            case "stock_min":
                // int minStock = plugin.getShopConfigManager().getItemValue(shopID, itemID, "stock.min", Integer.class)
                //     .orElse(plugin.getDataConfig().getStockMin());
                Optional<Integer> minStock = plugin.getShopConfigManager().getItemValue(shopID, itemID, "stock.min", Integer.class);

                if (minStock.isPresent()) {
                    if (minStock.get() < 0) {
                        return "N/A"; // Si le stock minimum est négatif, on retourne "N/A"
                    }
                } else {
                    minStock = Optional.of(plugin.getDataConfig().getStockMin());
                }

                return String.valueOf(minStock.get());
                
            case "stock_max":
                Optional<Integer> maxStock = plugin.getShopConfigManager().getItemValue(shopID, itemID, "stock.max", Integer.class);
                if (maxStock.isPresent()) {
                    if (maxStock.get() < 0) {
                        return "N/A";
                    }
                } else {
                    maxStock = Optional.of(plugin.getDataConfig().getStockMax());
                }

                return String.valueOf(maxStock.get());

            default:
                return "N/A";
        }
    }
}