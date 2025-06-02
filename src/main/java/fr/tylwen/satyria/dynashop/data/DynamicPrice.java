package fr.tylwen.satyria.dynashop.data;

import org.bukkit.entity.Player;

import fr.tylwen.satyria.dynashop.DynaShopPlugin;
import net.brcdev.shopgui.ShopGuiPlusApi;
import net.brcdev.shopgui.modifier.PriceModifier;
import net.brcdev.shopgui.modifier.PriceModifierActionType;
import net.brcdev.shopgui.shop.Shop;
import net.brcdev.shopgui.shop.item.ShopItem;

public class DynamicPrice {

    private double buyPrice, sellPrice;
    private double minBuy, maxBuy, minSell, maxSell;
    private final double growthBuy, decayBuy, growthSell, decaySell;
    static final double MIN_MARGIN = 0.01; // Marge minimale entre buyPrice et sellPrice
    private boolean isFromRecipe;
    
    // Variables pour le système de stock
    private int stock; // Stock actuel
    private final int minStock; // Stock minimum
    private final int maxStock; // Stock maximum
    private final double stockBuyModifier; // Coefficient pour ajuster le prix d'achat en fonction du stock
    private final double stockSellModifier; // Coefficient pour ajuster le prix de vente en fonction du stock
    private boolean isFromStock; // Indique si le prix provient du stock
    // private boolean isFromRecipeStock; // Indique si le prix provient du stock d'une recette

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
        int stock, int minStock, int maxStock,
        double stockBuyModifier, double stockSellModifier) {
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
            throw new IllegalArgumentException("minStock ne peut pas être supérieur à maxStock");
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
        this.stockBuyModifier = stockBuyModifier;
        this.stockSellModifier = stockSellModifier;
        this.isFromRecipe = false;
        this.isFromStock = false;
    }

    public DynamicPrice(double buyPrice, double sellPrice) {
        // this(buyPrice, sellPrice, 0.0, Double.MAX_VALUE, 0.0, Double.MAX_VALUE, 1.0, 1.0, 1.0, 1.0);
        this(buyPrice, sellPrice, 0.0, Double.MAX_VALUE, 0.0, Double.MAX_VALUE, 1.0, 1.0, 1.0, 1.0,
            0, 0, Integer.MAX_VALUE, 1.0, 1.0);
    }

    public DynamicPrice(double buyPrice, double sellPrice, int stock) {
        this(buyPrice, sellPrice, 0.0, Double.MAX_VALUE, 0.0, Double.MAX_VALUE, 1.0, 1.0, 1.0, 1.0,
            stock, 0, Integer.MAX_VALUE, 1.0, 1.0);
    }
    
    public void applyDecay(int amount) {
        // Ne pas modifier les prix désactivés (-1)
        if (buyPrice > 0) {
            buyPrice = Math.max(minBuy, Math.min(buyPrice * Math.pow(decayBuy, amount), maxBuy));
        }
        if (sellPrice > 0) {
            sellPrice = Math.min(maxSell, Math.max(sellPrice * Math.pow(decaySell, amount), minSell));
        }

        // Vérifier les marges uniquement si les deux prix sont positifs
        if (buyPrice > 0 && sellPrice > 0) {
            if (sellPrice > buyPrice - MIN_MARGIN) {
                sellPrice = buyPrice - MIN_MARGIN;
            }
            if (buyPrice < sellPrice + MIN_MARGIN) {
                buyPrice = sellPrice + MIN_MARGIN;
            }
        }
    }
    
    public void applyGrowth(int amount) {
        // Ne pas modifier les prix désactivés (-1)
        if (buyPrice > 0) {
            buyPrice = Math.min(maxBuy, Math.max(buyPrice * Math.pow(growthBuy, amount), minBuy));
        }
        if (sellPrice > 0) {
            sellPrice = Math.max(minSell, Math.min(sellPrice * Math.pow(growthSell, amount), maxSell));
        }

        // Vérifier les marges uniquement si les deux prix sont positifs
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

    // Pour le système de recette DynaShopType.RECIPE
    public boolean isFromRecipe() {
        return isFromRecipe;
    }

    public void setFromRecipe(boolean fromRecipe) {
        this.isFromRecipe = fromRecipe;
    }


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

    public double getStockBuyModifier() {
        return stockBuyModifier;
    }

    public double getStockSellModifier() {
        return stockSellModifier;
    }

    public boolean isFromStock() {
        return isFromStock;
    }

    public void setFromStock(boolean fromStock) {
        this.isFromStock = fromStock;
    }


    public void adjustPricesBasedOnStock() {
        // Calculer le ratio de stock (entre 0 et 1)
        double stockRatio = Math.max(0.0, Math.min(1.0, (double)(stock - minStock) / (maxStock - minStock)));
        
        // Ajuster seulement les prix positifs
        if (buyPrice > 0) {
            // Formule inversée : prix élevés quand stock proche de 0, prix bas quand stock proche du max
            buyPrice = maxBuy - (maxBuy - minBuy) * stockRatio * stockBuyModifier;
        }
        
        if (sellPrice > 0) {
            // Formule inversée : prix élevés quand stock proche de 0, prix bas quand stock proche du max
            sellPrice = maxSell - (maxSell - minSell) * stockRatio * stockSellModifier;
        }
        
        // Vérifier les marges uniquement si les deux prix sont positifs
        if (buyPrice > 0 && sellPrice > 0) {
            if (buyPrice < sellPrice + MIN_MARGIN) {
                buyPrice = sellPrice + MIN_MARGIN;
            }
            if (sellPrice > buyPrice - MIN_MARGIN) {
                sellPrice = buyPrice - MIN_MARGIN;
            }
        }
    }
    
    public void increaseStock(int amount) {
        incrementStock(amount);
        adjustPricesBasedOnStock();
    }
    
    public void decreaseStock(int amount) {
        decrementStock(amount);
        adjustPricesBasedOnStock();
    }

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
}