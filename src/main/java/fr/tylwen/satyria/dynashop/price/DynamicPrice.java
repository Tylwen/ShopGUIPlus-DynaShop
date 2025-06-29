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
package fr.tylwen.satyria.dynashop.price;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;

import fr.tylwen.satyria.dynashop.DynaShopPlugin;
import fr.tylwen.satyria.dynashop.data.param.DynaShopType;
import fr.tylwen.satyria.dynashop.system.InflationManager;
import net.brcdev.shopgui.ShopGuiPlusApi;
import net.brcdev.shopgui.modifier.PriceModifier;
import net.brcdev.shopgui.modifier.PriceModifierActionType;
import net.brcdev.shopgui.shop.Shop;
import net.brcdev.shopgui.shop.item.ShopItem;

public class DynamicPrice implements Cloneable {

    private double buyPrice, sellPrice;
    private double minBuy, maxBuy, minSell, maxSell;
    private double growthBuy, decayBuy, growthSell, decaySell;
    static final double MIN_MARGIN = 0.01; // Marge minimale entre buyPrice et sellPrice
    // private boolean isFromRecipe;
    
    // Variables pour le système de stock
    private int stock; // Stock actuel
    private final int minStock; // Stock minimum
    private final int maxStock; // Stock maximum
    private final double stockModifier; // Coefficient pour ajuster le prix d'achat en fonction du stock
    // private final double stockBuyModifier; // Coefficient pour ajuster le prix d'achat en fonction du stock
    // private final double stockSellModifier; // Coefficient pour ajuster le prix de vente en fonction du stock
    // private boolean isFromStock; // Indique si le prix provient du stock
    // private boolean isFromRecipeStock; // Indique si le prix provient du stock d'une recette

    private DynaShopType typeDynaShop; // Type de DynamicPrice (RECIPE, STOCK, etc.)
    private DynaShopType buyTypeDynaShop; // Type spécifique pour l'achat
    private DynaShopType sellTypeDynaShop; // Type spécifique pour la vente
    // private DynaShopType buyTypeDynaShop = DynaShopType.UNKNOWN;
    // private DynaShopType sellTypeDynaShop = DynaShopType.UNKNOWN;

    /**
     * @param buyPrice  prix initial d'achat
     * @param sellPrice prix initial de revente
     * @param minBuy    borne minimale achat
     * @param maxBuy    borne maximale achat
     * @param minSell   borne minimale revente
     * @param maxSell   borne maximale revente
     * @param growthBuy facteur de croissance à chaque achat
     * @param decayBuy  facteur de décroissance sur le prix d'achat au fil du temps
     * @param growthSell facteur de croissance du prix de vente au fil du temps
     * @param decaySell  facteur de décroissance à chaque vente
     * @param stock le stock initial
     * @param minStock le stock minimum
     * @param maxStock le stock maximum
     * @param stockBuyModifier le coefficient pour ajuster le prix d'achat en fonction du stock
     * @param stockSellModifier le coefficient pour ajuster le prix de vente en fonction du stock
     * @throws IllegalArgumentException si les bornes sont invalides ou si les facteurs de croissance/décroissance sont négatifs
     */
    public DynamicPrice(double buyPrice, double sellPrice,
        double minBuy, double maxBuy, double minSell, double maxSell,
        double growthBuy, double decayBuy,
        double growthSell, double decaySell,
        int stock, int minStock, int maxStock, double stockModifier) {
        // double stockBuyModifier, double stockSellModifier) {
        if (minBuy > 0 && maxBuy > 0 && minBuy > maxBuy) {
            throw new IllegalArgumentException("minBuy ne peut pas être supérieur à maxBuy");
        }
        if (minSell > 0 && maxSell > 0 && minSell > maxSell) {
            throw new IllegalArgumentException("minSell ne peut pas être supérieur à maxSell");
        }
        if (growthBuy <= 0 || decayBuy <= 0 || growthSell <= 0 || decaySell <= 0) {
            throw new IllegalArgumentException("Les facteurs de croissance et de décroissance doivent être positifs");
        }
        if (minStock > maxStock) {
            throw new IllegalArgumentException("minStock (" + minStock + ") ne peut pas être supérieur à maxStock (" + maxStock + ")");
        }

        // Conserver -1 comme valeur spéciale
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
        
        // Appliquer les limites seulement si le prix est positif
        if (this.buyPrice > 0) {
            this.buyPrice = Math.max(minBuy, Math.min(buyPrice, maxBuy));
        }
        if (this.sellPrice > 0) {
            this.sellPrice = Math.max(minSell, Math.min(sellPrice, maxSell));
        }
        
        // Vérifier les marges uniquement si les deux prix sont positifs
        if (this.buyPrice > 0 && this.sellPrice > 0) {
            // Vérifier que buyPrice est supérieur ou égal à sellPrice + MIN_MARGIN
            if (this.buyPrice < this.sellPrice + MIN_MARGIN) {
                this.buyPrice = this.sellPrice + MIN_MARGIN;
            }
            // Vérifier que sellPrice est inférieur ou égal à buyPrice - MIN_MARGIN
            if (this.sellPrice > this.buyPrice - MIN_MARGIN) {
                this.sellPrice = this.buyPrice - MIN_MARGIN;
            }
        }

        // Initialiser le reste comme avant
        this.minBuy = minBuy;
        this.maxBuy = maxBuy;
        this.minSell = minSell;
        this.maxSell = maxSell;
        this.growthBuy = growthBuy;
        this.decayBuy = decayBuy;
        this.growthSell = growthSell;
        this.decaySell = decaySell;

        this.stock = stock;
        this.minStock = minStock;
        this.maxStock = maxStock;
        this.stockModifier = stockModifier;
        // this.stockBuyModifier = stockBuyModifier;
        // this.stockSellModifier = stockSellModifier;

        // this.typeDynaShop = DynaShopType.NONE;
        this.typeDynaShop = DynaShopType.UNKNOWN;
        this.buyTypeDynaShop = DynaShopType.UNKNOWN;
        this.sellTypeDynaShop = DynaShopType.UNKNOWN;

        // this.isFromRecipe = false;
        // this.isFromStock = false;
    }

    public DynamicPrice(double buyPrice, double sellPrice) {
        // this(buyPrice, sellPrice, 0.0, Double.MAX_VALUE, 0.0, Double.MAX_VALUE, 1.0, 1.0, 1.0, 1.0);
        this(buyPrice, sellPrice, 0.0, Double.MAX_VALUE, 0.0, Double.MAX_VALUE, 1.0, 1.0, 1.0, 1.0,
            0, 0, Integer.MAX_VALUE, 1.0);
    }

    public DynamicPrice(double buyPrice, double sellPrice, int stock) {
        this(buyPrice, sellPrice, 0.0, Double.MAX_VALUE, 0.0, Double.MAX_VALUE, 1.0, 1.0, 1.0, 1.0,
            stock, 0, Integer.MAX_VALUE, 1.0);
    }
    
    public DynamicPrice clone() {
        try {
            return (DynamicPrice) super.clone();
        } catch (CloneNotSupportedException e) {
            // Cette exception ne devrait jamais se produire car nous implémentons Cloneable
            throw new RuntimeException("Erreur lors du clonage de DynamicPrice", e);
        }
    }

    /**
     * Calcule le prix moyen pour un achat de quantité k avec growth/decay progressif
     * @param quantity la quantité à acheter/vendre
     * @param growthFactor le facteur de croissance (growth pour achat, decay pour vente)
     * @return le prix moyen par unité
     */
    public double calculateProgressiveAveragePrice(int quantity, double growthFactor, boolean isBuy) {
        double initialPrice = isBuy ? this.buyPrice : this.sellPrice;
        
        if (initialPrice <= 0 || quantity <= 0) {
            return initialPrice;
        }
        
        if (Math.abs(growthFactor - 1.0) < 0.000001) {
            // Si g ≈ 1, pas de changement progressif
            return initialPrice;
        }
        
        // DynaShopPlugin.getInstance().getLogger().info(String.format(
        //     "Calcul prix progressif: %s, quantité=%d, facteur=%.4f, prix initial=%.2f",
        //     isBuy ? "achat" : "vente", quantity, growthFactor, initialPrice
        // ));

        // Utiliser le cache centralisé du plugin
        String cacheKey = String.format("progressive_%s_%d_%.6f_%.2f", isBuy ? "buy" : "sell", quantity, growthFactor, initialPrice);
        return DynaShopPlugin.getInstance().getCalculatedPriceCache().get(cacheKey, () -> {
            // P_avg = P_before × (g^k - 1) / (k × (g - 1))
            double gPowK = Math.pow(growthFactor, quantity);
            double averagePrice = initialPrice * (gPowK - 1) / (quantity * (growthFactor - 1));

            // DynaShopPlugin.getInstance().getLogger().info(String.format("Résultat prix progressif: %.2f", averagePrice));
            
            return averagePrice;
        });
    }

    // /**
    //  * Calcule le prix final après un achat/vente progressif
    //  * @param quantity la quantité achetée/vendue
    //  * @param growthFactor le facteur de croissance
    //  * @return le nouveau prix de base
    //  */
    // public double calculateProgressiveFinalPrice(int quantity, double growthFactor, boolean isBuy) {
    //     double initialPrice = isBuy ? this.buyPrice : this.sellPrice;
        
    //     if (initialPrice <= 0) {
    //         return initialPrice;
    //     }
        
    //     // P_after = P_before × g^k
    //     return initialPrice * Math.pow(growthFactor, quantity);
    // }
    public double calculateProgressiveFinalPrice(int quantity, double growthFactor, boolean isBuy) {
        double initialPrice = isBuy ? this.buyPrice : this.sellPrice;
        
        // DynaShopPlugin.getInstance().getLogger().info("DEBUG: calculateProgressiveFinalPrice - isBuy=" + isBuy + 
        //     ", initialPrice=" + initialPrice + ", quantity=" + quantity + ", growthFactor=" + growthFactor);
        
        if (initialPrice <= 0) {
            // DynaShopPlugin.getInstance().getLogger().info("DEBUG: Price <= 0, returning: " + initialPrice);
            return initialPrice;
        }
        
        // P_after = P_before × g^k
        double result = initialPrice * Math.pow(growthFactor, quantity);
        // DynaShopPlugin.getInstance().getLogger().info("DEBUG: Final result: " + result);
        return result;
    }

    /**
     * Applique un growth/decay progressif pendant l'achat
     */
    public void applyProgressiveGrowth(int amount) {
        if (buyPrice > 0) {
            double finalBuyPrice = calculateProgressiveFinalPrice(amount, growthBuy, true);
            this.buyPrice = Math.min(maxBuy, Math.max(finalBuyPrice, minBuy));
        }
        if (sellPrice > 0) {
            double finalSellPrice = calculateProgressiveFinalPrice(amount, growthSell, false);
            this.sellPrice = Math.max(minSell, Math.min(finalSellPrice, maxSell));
        }
        
        // Vérifier les marges
        if (buyPrice > 0 && sellPrice > 0) {
            if (buyPrice < sellPrice + MIN_MARGIN) {
                buyPrice = sellPrice + MIN_MARGIN;
            }
            if (sellPrice > buyPrice - MIN_MARGIN) {
                sellPrice = buyPrice - MIN_MARGIN;
            }
        }
    }

    /**
     * Applique un decay progressif pendant la vente
     */
    public void applyProgressiveDecay(int amount) {
        if (buyPrice > 0) {
            double finalBuyPrice = calculateProgressiveFinalPrice(amount, decayBuy, true);
            this.buyPrice = Math.max(minBuy, Math.min(finalBuyPrice, maxBuy));
        }
        if (sellPrice > 0) {
            double finalSellPrice = calculateProgressiveFinalPrice(amount, decaySell, false);
            this.sellPrice = Math.min(maxSell, Math.max(finalSellPrice, minSell));
            // this.sellPrice = Math.min(minSell, Math.max(finalSellPrice, maxSell));
        }
        
        // Vérifier les marges
        if (buyPrice > 0 && sellPrice > 0) {
            if (sellPrice > buyPrice - MIN_MARGIN) {
                sellPrice = buyPrice - MIN_MARGIN;
            }
            if (buyPrice < sellPrice + MIN_MARGIN) {
                buyPrice = sellPrice + MIN_MARGIN;
            }
        }
    }
    
    // public void applyGrowth(int amount) {
    //     // Ne pas modifier les prix désactivés (-1)
    //     if (buyPrice > 0) {
    //         buyPrice = Math.min(maxBuy, Math.max(buyPrice * Math.pow(growthBuy, amount), minBuy));
    //     }
    //     if (sellPrice > 0) {
    //         sellPrice = Math.max(minSell, Math.min(sellPrice * Math.pow(growthSell, amount), maxSell));
    //     }

    //     // Vérifier les marges uniquement si les deux prix sont positifs
    //     if (buyPrice > 0 && sellPrice > 0) {
    //         if (buyPrice < sellPrice + MIN_MARGIN) {
    //             buyPrice = sellPrice + MIN_MARGIN;
    //         }
    //         if (sellPrice > buyPrice - MIN_MARGIN) {
    //             sellPrice = buyPrice - MIN_MARGIN;
    //         }
    //     }
    // }
    
    // public void applyDecay(int amount) {
    //     // Ne pas modifier les prix désactivés (-1)
    //     if (buyPrice > 0) {
    //         buyPrice = Math.max(minBuy, Math.min(buyPrice * Math.pow(decayBuy, amount), maxBuy));
    //     }
    //     if (sellPrice > 0) {
    //         sellPrice = Math.min(maxSell, Math.max(sellPrice * Math.pow(decaySell, amount), minSell));
    //     }

    //     // Vérifier les marges uniquement si les deux prix sont positifs
    //     if (buyPrice > 0 && sellPrice > 0) {
    //         if (sellPrice > buyPrice - MIN_MARGIN) {
    //             sellPrice = buyPrice - MIN_MARGIN;
    //         }
    //         if (buyPrice < sellPrice + MIN_MARGIN) {
    //             buyPrice = sellPrice + MIN_MARGIN;
    //         }
    //     }
    // }
    
    /**
     * Applique les changements de prix d'achat seulement si le prix est positif
     */
    public void applyBuyPriceChanges() {
        if (buyPrice > 0) {
            this.buyPrice *= DynaShopPlugin.getInstance().getDataConfig().getPriceDecrease();
            this.buyPrice = Math.max(minBuy, Math.min(this.buyPrice, maxBuy));
        }
    }
    
    /**
     * Applique les changements de prix de vente seulement si le prix est positif
     */
    public void applySellPriceChanges() {
        if (sellPrice > 0) {
            this.sellPrice *= DynaShopPlugin.getInstance().getDataConfig().getPriceIncrease();
            this.sellPrice = Math.max(minSell, Math.min(this.sellPrice, maxSell));
        }
    }

    public void setBuyPrice(double buyPrice) {
        this.buyPrice = Math.max(minBuy, Math.min(buyPrice, maxBuy));
    }
    public void setSellPrice(double sellPrice) {
        this.sellPrice = Math.max(minSell, Math.min(sellPrice, maxSell));
    }
    public void setMinBuyPrice(double minBuy) {
        // if (minBuy > maxBuy) {
        //     throw new IllegalArgumentException("minBuy ne peut pas être supérieur à maxBuy");
        // }
        this.minBuy = minBuy;
        // if (buyPrice < minBuy) {
        //     buyPrice = minBuy;
        // }
    }
    public void setMaxBuyPrice(double maxBuy) {
        // if (maxBuy < minBuy) {
        //     throw new IllegalArgumentException("maxBuy ne peut pas être inférieur à minBuy");
        // }
        this.maxBuy = maxBuy;
        // if (buyPrice > maxBuy) {
        //     buyPrice = maxBuy;
        // }
    }
    public void setMinSellPrice(double minSell) {
        // if (minSell > maxSell) {
        //     throw new IllegalArgumentException("minSell ne peut pas être supérieur à maxSell");
        // }
        this.minSell = minSell;
        // if (sellPrice < minSell) {
        //     sellPrice = minSell;
        // }
    }
    public void setMaxSellPrice(double maxSell) {
        // if (maxSell < minSell) {
        //     throw new IllegalArgumentException("maxSell ne peut pas être inférieur à minSell");
        // }
        this.maxSell = maxSell;
        // if (sellPrice > maxSell) {
        //     sellPrice = maxSell;
        // }
    }

    public void setGrowthBuy(double growthBuy) {
        if (growthBuy <= 0) {
            throw new IllegalArgumentException("Le facteur de croissance d'achat doit être positif");
        }
        this.growthBuy = growthBuy;
    }

    public void setDecayBuy(double decayBuy) {
        if (decayBuy <= 0) {
            throw new IllegalArgumentException("Le facteur de décroissance d'achat doit être positif");
        }
        this.decayBuy = decayBuy;
    }

    public void setGrowthSell(double growthSell) {
        if (growthSell <= 0) {
            throw new IllegalArgumentException("Le facteur de croissance de vente doit être positif");
        }
        this.growthSell = growthSell;
    }

    public void setDecaySell(double decaySell) {
        if (decaySell <= 0) {
            throw new IllegalArgumentException("Le facteur de décroissance de vente doit être positif");
        }
        this.decaySell = decaySell;
    }

    // — Getters —
    public double getBuyPrice() {
        return buyPrice;
    }

    public double getBuyPriceForAmount(int amount) {
        return buyPrice * amount;
    }

    public double getSellPrice() {
        return sellPrice;
    }
    
    public double getSellPriceForAmount(int amount) {
        return sellPrice * amount;
    }

    public double getMinBuyPrice() {
        return minBuy;
    }

    public double getMaxBuyPrice() {
        return maxBuy;
    }

    public double getMinSellPrice() {
        return minSell;
    }

    public double getMaxSellPrice() {
        return maxSell;
    }

    public double getGrowthBuy() {
        return growthBuy;
    }

    public double getDecayBuy() {
        return decayBuy;
    }

    public double getGrowthSell() {
        return growthSell;
    }

    public double getDecaySell() {
        return decaySell;
    }

    public boolean isValid() {
        return buyPrice >= minBuy && buyPrice <= maxBuy && sellPrice >= minSell && sellPrice <= maxSell;
    }

    // // Pour le système de recette DynaShopType.RECIPE
    // public boolean isFromRecipe() {
    //     return isFromRecipe;
    // }

    // public void setFromRecipe(boolean fromRecipe) {
    //     this.isFromRecipe = fromRecipe;
    // }


    // Pour le système de stock DynaShopType.STOCK
    public int getStock() {
        return stock;
    }

    public void setStock(int stock) {
        this.stock = stock;
    }

    public void incrementStock(int amount) {
        this.stock = Math.min(this.stock + amount, this.maxStock);
    }

    public void decrementStock(int amount) {
        this.stock = Math.max(this.stock - amount, this.minStock);
    }

    public int getMinStock() {
        return minStock;
    }

    public int getMaxStock() {
        return maxStock;
    }

    public double getStockModifier() {
        return stockModifier;
    }

    // public double getStockBuyModifier() {
    //     return stockBuyModifier;
    // }

    // public double getStockSellModifier() {
    //     return stockSellModifier;
    // }

    // public boolean isFromStock() {
    //     return isFromStock;
    // }

    // public void setFromStock(boolean fromStock) {
    //     this.isFromStock = fromStock;
    // }


    // private void adjustPricesBasedOnStock() {
    //     // Calculer le ratio de stock (entre 0 et 1)
    //     double stockRatio = Math.max(0.0, Math.min(1.0, (double)(stock - minStock) / (maxStock - minStock)));
        
    //     // Ajuster seulement les prix positifs
    //     if (buyPrice > 0) {
    //         // Formule inversée : prix élevés quand stock proche de 0, prix bas quand stock proche du max
    //         buyPrice = maxBuy - (maxBuy - minBuy) * stockRatio;
    //         // buyPrice = maxBuy - (maxBuy - minBuy) * stockRatio * stockBuyModifier;
    //         // buyPrice = maxBuy - (maxBuy - minBuy) / (1.0 + Math.exp(-0.0005 * (stockRatio))) * stockBuyModifier;
    //     }
        
    //     if (sellPrice > 0) {
    //         // Formule inversée : prix élevés quand stock proche de 0, prix bas quand stock proche du max
    //         sellPrice = maxSell - (maxSell - minSell) * stockRatio;
    //         // sellPrice = maxSell - (maxSell - minSell) * stockRatio * stockSellModifier;
    //         // sellPrice = maxSell - (maxSell - minSell) / (1.0 + Math.exp(-0.0005 * (stockRatio))) * stockSellModifier;
    //     }
        
    //     // Vérifier les marges uniquement si les deux prix sont positifs
    //     if (buyPrice > 0 && sellPrice > 0) {
    //         if (buyPrice < sellPrice + MIN_MARGIN) {
    //             buyPrice = sellPrice + MIN_MARGIN;
    //         }
    //         if (sellPrice > buyPrice - MIN_MARGIN) {
    //             sellPrice = buyPrice - MIN_MARGIN;
    //         }
    //     }
    // }
    
    // public void increaseStock(int amount) {
    //     incrementStock(amount);
    //     adjustPricesBasedOnStock();
    // }
    
    // public void decreaseStock(int amount) {
    //     decrementStock(amount);
    //     adjustPricesBasedOnStock();
    // }

    /**
     * Applique les modificateurs de prix à cet objet DynamicPrice
     * @param buyModifier Modificateur pour les prix d'achat
     * @param sellModifier Modificateur pour les prix de vente
     * @return this (pour permettre le chaînage de méthodes)
     */
    public DynamicPrice applyModifiers(double buyModifier, double sellModifier) {
        // Ne pas appliquer les modificateurs si les prix sont négatifs (désactivés)
        if (this.buyPrice > 0) {
            this.buyPrice = this.buyPrice * buyModifier;
            this.minBuy = this.minBuy * buyModifier;
            this.maxBuy = this.maxBuy * buyModifier;
        }

        if (this.sellPrice > 0) {
            this.sellPrice = this.sellPrice * sellModifier;
            this.minSell = this.minSell * sellModifier;
            this.maxSell = this.maxSell * sellModifier;
        }
        
        return this;
    }
    
    /**
     * Applique les modificateurs ShopGUI+ à cet objet DynamicPrice
     * @param player Le joueur concerné (pour les modificateurs spécifiques)
     * @param shopID L'ID du shop
     * @param itemID L'ID de l'item
     * @return this (pour permettre le chaînage de méthodes)
     */
    public DynamicPrice applyShopGuiPlusModifiers(Player player, String shopID, String itemID) {
        try {
            // Obtenir le shop et l'item
            Shop shop = ShopGuiPlusApi.getShop(shopID);
            if (shop == null) return this;
            
            ShopItem shopItem = shop.getShopItem(itemID);
            if (shopItem == null) return this;
            
            // Récupérer les modificateurs
            PriceModifier buyModifier = ShopGuiPlusApi.getPriceModifier(player, shopItem, PriceModifierActionType.BUY);
            PriceModifier sellModifier = ShopGuiPlusApi.getPriceModifier(player, shopItem, PriceModifierActionType.SELL);
            
            // Si les deux modificateurs sont 1.0, ne rien faire
            if (buyModifier.getModifier() == 1.0 && sellModifier.getModifier() == 1.0) {
                return this;
            }
            
            // // Appliquer les modificateurs
            // double originalBuy = this.buyPrice;
            // double originalSell = this.sellPrice;
            
            this.applyModifiers(buyModifier.getModifier(), sellModifier.getModifier());
            
            // // Log des changements significatifs
            // if (Math.abs(originalBuy - this.buyPrice) > 0.01 || 
            //     Math.abs(originalSell - this.sellPrice) > 0.01) {
            //     DynaShopPlugin.getInstance().getLogger().info(
            //         "Prix modifiés pour " + shopID + ":" + itemID +
            //         " - Buy: " + originalBuy + " -> " + this.buyPrice +
            //         ", Sell: " + originalSell + " -> " + this.sellPrice
            //     );
            // }
            
        } catch (Exception e) {
            DynaShopPlugin.getInstance().getLogger().warning("Error applying price modifiers: " + e.getMessage());
        }

        return this;
    }

    
    // /**
    //  * Applique les modificateurs de prix ShopGUI+ à un objet DynamicPrice
    //  */
    // private void applyPriceModifiers(Player player, String shopID, String itemID, DynamicPrice price) {
    //     try {
    //         // Obtenir le shop et l'item
    //         Shop shop = ShopGuiPlusApi.getShop(shopID);
    //         if (shop == null) return;
            
    //         ShopItem shopItem = shop.getShopItem(itemID);
    //         if (shopItem == null) return;
            
    //         // Enregistrer les valeurs avant modification
    //         double originalBuyPrice = price.getBuyPrice();
    //         double originalSellPrice = price.getSellPrice();
            
    //         // Récupérer les modificateurs (sans joueur spécifique)
    //         // double buyModifier = ShopGuiPlusApi.getBuyPriceModifier(null, shop, shopItem);
    //         // double sellModifier = ShopGuiPlusApi.getSellPriceModifier(null, shop, shopItem);
    //         PriceModifier buyModifier = ShopGuiPlusApi.getPriceModifier(player, shopItem, PriceModifierActionType.BUY);
    //         PriceModifier sellModifier = ShopGuiPlusApi.getPriceModifier(player, shopItem, PriceModifierActionType.SELL);
    //         // if (buyModifier == null || sellModifier == null) {
    //         //     // Si les modificateurs ne sont pas définis, utiliser 1.0 (pas de modification)
    //         //     // buyModifier = new PriceModifier(1.0);
    //         //     // sellModifier = new PriceModifier(1.0);
    //         //     buyModifier.setModifier(1.0);
    //         //     sellModifier.setModifier(1.0);
    //         // }
    //         if (buyModifier.getModifier() == 1.0 && sellModifier.getModifier() == 1.0) {
    //             // Si les modificateurs sont 1.0, pas besoin de les appliquer
    //             return;
    //         }

    //         // Ne pas appliquer les modificateurs si les prix sont négatifs (désactivés)
    //         if (price.getBuyPrice() > 0) {
    //             price.setBuyPrice(price.getBuyPrice() * buyModifier.getModifier());
    //             price.setMinBuyPrice(price.getMinBuyPrice() * buyModifier.getModifier());
    //             price.setMaxBuyPrice(price.getMaxBuyPrice() * buyModifier.getModifier());
    //         }

    //         if (price.getSellPrice() > 0) {
    //             price.setSellPrice(price.getSellPrice() * sellModifier.getModifier());
    //             price.setMinSellPrice(price.getMinSellPrice() * sellModifier.getModifier());
    //             price.setMaxSellPrice(price.getMaxSellPrice() * sellModifier.getModifier());
    //         }

    //         // // Log des modificateurs appliqués (optionnel)
    //         // if (buyModifier.getModifier() != 1.0 || sellModifier.getModifier() != 1.0) {
    //         //     DynaShopPlugin.getInstance().getLogger().info(
    //         //         "Modificateurs de prix appliqués à " + shopID + ":" + itemID +
    //         //         " - Buy: x" + buyModifier.getModifier() + ", Sell: x" + sellModifier.getModifier()
    //         //     );
    //         // }
            
    //         // Vérifier si les modificateurs ont été appliqués
    //         if (Math.abs(originalBuyPrice - price.getBuyPrice()) > 0.01 || 
    //             Math.abs(originalSellPrice - price.getSellPrice()) > 0.01) {
    //             mainPlugin.getLogger().info("Prix modifiés pour " + shopID + ":" + itemID +
    //                 " - Buy: " + originalBuyPrice + " -> " + price.getBuyPrice() +
    //                 ", Sell: " + originalSellPrice + " -> " + price.getSellPrice());
    //         } else {
    //             mainPlugin.getLogger().info("Aucun changement dans les prix pour " + shopID + ":" + itemID);
    //         }
    //     } catch (Exception e) {
    //         DynaShopPlugin.getInstance().getLogger().warning("Erreur lors de l'application des modificateurs de prix: " + e.getMessage());
    //     }
    // }

    public DynaShopType getDynaShopType() {
        // if (this.typeDynaShop == null) {
        //     // Si le type n'est pas défini, retourner UNKNOWN
        //     return DynaShopType.UNKNOWN;
        // } else if (this.typeDynaShop == DynaShopType.UNKNOWN) {
        //     // Si le type est UNKNOWN, retourner le type par défaut
        //     return DynaShopPlugin.getInstance().getShopConfigManager().getTypeDynaShop(
        // }
        return typeDynaShop;
    }

    public void setDynaShopType(DynaShopType typeDynaShop) {
        this.typeDynaShop = typeDynaShop;
    }

    public DynaShopType getBuyTypeDynaShop() {
        if (buyTypeDynaShop == DynaShopType.UNKNOWN) {
            // Fallback sur le type général si non défini
            return typeDynaShop != null ? typeDynaShop : DynaShopType.NONE;
        }
        return buyTypeDynaShop;
    }

    public DynaShopType getSellTypeDynaShop() {
        if (sellTypeDynaShop == DynaShopType.UNKNOWN) {
            // Fallback sur le type général si non défini
            return typeDynaShop != null ? typeDynaShop : DynaShopType.NONE;
        }
        return sellTypeDynaShop;
    }

    public void setBuyTypeDynaShop(DynaShopType type) {
        this.buyTypeDynaShop = type;
    }

    public void setSellTypeDynaShop(DynaShopType type) {
        this.sellTypeDynaShop = type;
    }

    // public double getPrice(String typePrice) {
    //     switch (typePrice.toUpperCase()) {
    //         case "BUY":
    //             return buyPrice;
    //         case "SELL":
    //             return sellPrice;
    //         case "MINBUY":
    //             return minBuy;
    //         case "MAXBUY":
    //             return maxBuy;
    //         case "MINSELL":
    //             return minSell;
    //         case "MAXSELL":
    //             return maxSell;
    //         case "GROWTHBUY":
    //             return growthBuy;
    //         case "DECAYBUY":
    //             return decayBuy;
    //         case "GROWTHSELL":
    //             return growthSell;
    //         case "DECAYSELL":
    //             return decaySell;
    //         default:
    //             // Si le type de prix n'est pas reconnu, retourner -1.0
    //             return -1.0; // Indique un prix désactivé
    //     }
    // }

    /**
     * Applique le facteur d'inflation aux prix
     * @param shopID L'ID du shop
     * @param itemID L'ID de l'item
     * @return this (pour permettre le chaînage de méthodes)
     */
    public DynamicPrice applyInflation(String shopID, String itemID) {
        InflationManager inflationManager = DynaShopPlugin.getInstance().getInflationManager();
        if (inflationManager != null && inflationManager.isEnabled()) {
            // Appliquer l'inflation au prix d'achat s'il est positif
            if (this.buyPrice > 0) {
                this.buyPrice = inflationManager.applyInflationToPrice(shopID, itemID, this.buyPrice);
                this.minBuy = inflationManager.applyInflationToPrice(shopID, itemID, this.minBuy);
                this.maxBuy = inflationManager.applyInflationToPrice(shopID, itemID, this.maxBuy);
            }
            
            // Appliquer l'inflation au prix de vente s'il est positif
            if (this.sellPrice > 0) {
                this.sellPrice = inflationManager.applyInflationToPrice(shopID, itemID, this.sellPrice);
                this.minSell = inflationManager.applyInflationToPrice(shopID, itemID, this.minSell);
                this.maxSell = inflationManager.applyInflationToPrice(shopID, itemID, this.maxSell);
            }
        }
        return this;
    }
}