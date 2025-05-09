package fr.tylwen.satyria.dynashop.data;

import fr.tylwen.satyria.dynashop.DynaShopPlugin;

public class DynamicPrice {
    private double buyPrice, sellPrice;
    private final double minBuy, maxBuy, minSell, maxSell;
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
    // public DynamicPrice(double buyPrice, double sellPrice,
    //                     double minBuy, double maxBuy, double minSell, double maxSell,
    //                     double growthBuy, double decayBuy,
    //                     double growthSell, double decaySell) {
    //     this.buyPrice   = buyPrice;
    //     this.sellPrice  = sellPrice;
    //     this.minBuy     = minBuy;
    //     this.maxBuy     = maxBuy;
    //     this.minSell    = minSell;
    //     this.maxSell    = maxSell;
    //     this.growthBuy  = growthBuy;
    //     this.decayBuy   = decayBuy;
    //     this.growthSell = growthSell;
    //     this.decaySell  = decaySell;
    // }
    // public DynamicPrice(double buyPrice, double sellPrice,
    //                     double minBuy, double maxBuy, double minSell, double maxSell,
    //                     double growthBuy, double decayBuy,
    //                     double growthSell, double decaySell) {
    public DynamicPrice(double buyPrice, double sellPrice,
        double minBuy, double maxBuy, double minSell, double maxSell,
        double growthBuy, double decayBuy,
        double growthSell, double decaySell,
        int stock, int minStock, int maxStock,
        double stockBuyModifier, double stockSellModifier) {
        if (minBuy > maxBuy) {
            throw new IllegalArgumentException("minBuy ne peut pas être supérieur à maxBuy");
        }
        if (minSell > maxSell) {
            throw new IllegalArgumentException("minSell ne peut pas être supérieur à maxSell");
        }
        if (growthBuy <= 0 || decayBuy <= 0 || growthSell <= 0 || decaySell <= 0) {
            throw new IllegalArgumentException("Les facteurs de croissance et de décroissance doivent être positifs");
        }
        if (minStock > maxStock) {
            throw new IllegalArgumentException("minStock ne peut pas être supérieur à maxStock");
        }

        this.buyPrice = Math.max(minBuy, Math.min(buyPrice, maxBuy));
        this.sellPrice = Math.max(minSell, Math.min(sellPrice, maxSell));
        
        // Vérifier que buyPrice est supérieur ou égal à sellPrice + MIN_MARGIN
        if (buyPrice < sellPrice + MIN_MARGIN) {
            this.buyPrice = sellPrice + MIN_MARGIN;
        }
        // Vérifier que sellPrice est inférieur ou égal à buyPrice - MIN_MARGIN
        if (sellPrice > buyPrice - MIN_MARGIN) {
            this.sellPrice = buyPrice - MIN_MARGIN;
        }

        // Initialiser les bornes
        this.minBuy = minBuy;
        this.maxBuy = maxBuy;
        this.minSell = minSell;
        this.maxSell = maxSell;
    
        // Initialiser les facteurs de croissance et de décroissance
        this.growthBuy = growthBuy;
        this.decayBuy = decayBuy;
        this.growthSell = growthSell;
        this.decaySell = decaySell;
        
        this.stock = stock;
        this.minStock = minStock;
        this.maxStock = maxStock;
        this.stockBuyModifier = stockBuyModifier;
        this.stockSellModifier = stockSellModifier;

        this.isFromRecipe = false; // Par défaut, pas d'origine de recette
        this.isFromStock = false; // Par défaut, pas d'origine de stock
        // this.isFromRecipeStock = false; // Par défaut, pas d'origine de recette de stock
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

    // public void incrementBuy() {
    //     buyPrice = Math.min(buyPrice * growthBuy, maxBuy);
    // }

    // public void decrementBuy() {
    //     buyPrice = Math.max(buyPrice * decayBuy, minBuy);
    // }

    // public void incrementSell() {
    //     sellPrice = Math.max(sellPrice * decaySell, minSell);
    // }

    // public void decrementSell() {
    //     sellPrice = Math.min(sellPrice * growthSell, maxSell);
    // }

    // /** Appliqué périodiquement pour “rafraîchir” les prix */
    // public void applyDecay() {
    //     buyPrice  = Math.max(buyPrice * decayBuy, minBuy);
    //     sellPrice = Math.min(sellPrice * growthSell, maxSell);

    //     // Vérifier que sellPrice est inférieur ou égal à buyPrice - MIN_MARGIN
    //     if (sellPrice > buyPrice - MIN_MARGIN) {
    //         sellPrice = buyPrice - MIN_MARGIN;
    //     }
    // }
    // public void applyDecay(int amount) {
    //     for (int i = 1; i < amount; i++) {
    //         applyDecay();
    //     }
    // }
    
    public void applyDecay(int amount) {
        // buyPrice  = Math.max(buyPrice * Math.pow(decayBuy, amount), minBuy);
        sellPrice = Math.min(sellPrice * Math.pow(decaySell, amount), maxSell);

        // Vérifier que sellPrice est inférieur ou égal à buyPrice - MIN_MARGIN
        if (sellPrice > buyPrice - MIN_MARGIN) {
            sellPrice = buyPrice - MIN_MARGIN;
        }
    }
    // public void applyDecay() {
    //     System.out.println("Avant décroissance : buyPrice = " + buyPrice + ", decayBuy = " + decayBuy + ", minBuy = " + minBuy);
    //     if (decayBuy < 1.0) {
    //         buyPrice = Math.max(buyPrice * decayBuy, minBuy);
    //     }
    //     if (growthSell > 1.0) {
    //         sellPrice = Math.min(sellPrice * growthSell, maxSell);
    //     }
    //     System.out.println("Après décroissance : buyPrice = " + buyPrice + ", sellPrice = " + sellPrice);
    // }

    // public void applyGrowth() {
    //     buyPrice  = Math.min(buyPrice * growthBuy, maxBuy);
    //     sellPrice = Math.max(sellPrice * decaySell, minSell);

    //     // Vérifier que buyPrice est supérieur ou égal à sellPrice + MIN_MARGIN
    //     if (buyPrice < sellPrice + MIN_MARGIN) {
    //         buyPrice = sellPrice + MIN_MARGIN;
    //     }
    // }
    // public void applyGrowth(int amount) {
    //     for (int i = 1; i < amount; i++) {
    //         applyGrowth();
    //     }
    // }
    
    public void applyGrowth(int amount) {
        buyPrice  = Math.min(buyPrice * Math.pow(growthBuy, amount), maxBuy);
        // sellPrice = Math.max(sellPrice * Math.pow(growthSell, amount), minSell);

        // Vérifier que buyPrice est supérieur ou égal à sellPrice + MIN_MARGIN
        if (buyPrice < sellPrice + MIN_MARGIN) {
            buyPrice = sellPrice + MIN_MARGIN;
        }
    }
    // public void applyGrowth() {
    //     System.out.println("Avant croissance : buyPrice = " + buyPrice + ", growthBuy = " + growthBuy + ", maxBuy = " + maxBuy);
    //     if (growthBuy > 1.0) {
    //         buyPrice = Math.min(buyPrice * growthBuy, maxBuy);
    //     }
    //     if (decaySell < 1.0) {
    //         sellPrice = Math.max(sellPrice * decaySell, minSell);
    //     }
    //     System.out.println("Après croissance : buyPrice = " + buyPrice + ", sellPrice = " + sellPrice);
    // }
    // public void applyGrowth() {
    //     System.out.println("Avant croissance : buyPrice = " + buyPrice + ", growthBuy = " + growthBuy + ", maxBuy = " + maxBuy);
    //     if (buyPrice < maxBuy) {
    //         buyPrice *= growthBuy;
    //         if (buyPrice > maxBuy) {
    //             buyPrice = maxBuy;
    //         }
    //     }
    //     System.out.println("Après croissance : buyPrice = " + buyPrice);
    // }
    
    // public void applyDecay() {
    //     System.out.println("Avant décroissance : sellPrice = " + sellPrice + ", decaySell = " + decaySell + ", minSell = " + minSell);
    //     if (sellPrice > minSell) {
    //         sellPrice *= decaySell;
    //         if (sellPrice < minSell) {
    //             sellPrice = minSell;
    //         }
    //     }
    //     System.out.println("Après décroissance : sellPrice = " + sellPrice);
    // }
    // public void applyDynamicChanges() {
    //     // this.buyPrice *= 1.0001;
    //     // this.sellPrice *= 0.9999;
    //     this.buyPrice *= 1.01;
    //     this.sellPrice *= 0.99;
    
    //     // Vérifier les bornes
    //     this.buyPrice = Math.min(this.buyPrice, this.maxBuy);
    //     this.buyPrice = Math.max(this.buyPrice, this.minBuy);
    
    //     this.sellPrice = Math.min(this.sellPrice, this.maxSell);
    //     this.sellPrice = Math.max(this.sellPrice, this.minSell);
    // }
    public void applyBuyPriceChanges() {
        this.buyPrice *= 0.99;
        // this.buyPrice = Math.min(this.buyPrice, this.maxBuy);
        // this.buyPrice = Math.max(this.buyPrice, this.minBuy);
        this.buyPrice = Math.max(minBuy, Math.min(this.buyPrice, maxBuy));
    }
    public void applySellPriceChanges() {
        this.sellPrice *= 1.01;
        // this.sellPrice = Math.min(this.sellPrice, this.maxSell);
        // this.sellPrice = Math.max(this.sellPrice, this.minSell);
        this.sellPrice = Math.max(minSell, Math.min(this.sellPrice, maxSell));
    }

    public void setBuyPrice(double buyPrice) {
        this.buyPrice = Math.max(minBuy, Math.min(buyPrice, maxBuy));
    }
    public void setSellPrice(double sellPrice) {
        this.sellPrice = Math.max(minSell, Math.min(sellPrice, maxSell));
    }

    // — Getters —
    public double getBuyPrice() {
        return buyPrice;
    }

    // public double getBuyPriceForAmount(int amount) {
    //     // double price = buyPrice;
    //     // for (int i = 0; i < amount; i++) {
    //     //     price = Math.min(price * growthBuy, maxBuy);
    //     // }
    //     // return price;
    //     // double price = buyPrice;
    //     // for (int i = 0; i < amount; i++) {
    //     //     price *= growthBuy;
    //     // }
    //     // return Math.min(price, maxBuy);
    //     double price = buyPrice;
    //     // for (int i = 1; i < amount; i++) {
    //     //     price += Math.min(price * growthBuy, maxBuy);
    //     // }

    //     price = Math.min(price * growthBuy, maxBuy) * amount;
    //     // price = Math.min(price * Math.pow(growthBuy, amount), maxBuy);
    //     return price;
    // }
    public double getBuyPriceForAmount(int amount) {
        // return buyPrice * amount * Math.pow(growthBuy, amount);
        return buyPrice * amount;
        // buyPrice = Math.min(buyPrice * growthBuy, maxBuy) * amount;
        // price = Math.min(price * Math.pow(growthBuy, amount), maxBuy);
        // return buyPrice;
    }

    public double getSellPrice() {
        return sellPrice;
    }
    // public double getSellPriceForAmount(int amount) {
    //     double price = sellPrice;
    //     // for (int i = 1; i < amount; i++) {
    //     //     price += Math.max(price * decaySell, minSell);
    //     // }
    //     // price = Math.max(price * decaySell, minSell) * amount;

    //     price = Math.max(price * growthSell, minSell) * amount;
    //     // price = Math.max(price * Math.pow(growthSell, amount), minSell);
    //     return price;
    // }
    public double getSellPriceForAmount(int amount) {
        // return sellPrice * amount * Math.pow(growthSell, amount);
        return sellPrice * amount;
        // price = Math.max(price * growthSell, minSell) * amount;
        // price = Math.max(price * Math.pow(growthSell, amount), minSell);
        // return price;
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

    public boolean isFromRecipe() {
        return isFromRecipe;
    }

    public void setFromRecipe(boolean fromRecipe) {
        this.isFromRecipe = fromRecipe;
    }

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
    
    // public void adjustPricesBasedOnStock() {
    //     // Ajuster le prix d'achat en fonction du stock
    //     double stockFactorBuy = 1.0 + ((double) (maxStock - stock) / maxStock) * stockBuyModifier;
    //     buyPrice = Math.min(buyPrice * stockFactorBuy, maxBuy);
    
    //     // Ajuster le prix de vente en fonction du stock
    //     double stockFactorSell = 1.0 - ((double) (stock - minStock) / maxStock) * stockSellModifier;
    //     sellPrice = Math.max(sellPrice * stockFactorSell, minSell);
    
    //     // Vérifier les bornes
    //     if (buyPrice < sellPrice + MIN_MARGIN) {
    //         buyPrice = sellPrice + MIN_MARGIN;
    //     }
    //     if (sellPrice > buyPrice - MIN_MARGIN) {
    //         sellPrice = buyPrice - MIN_MARGIN;
    //     }
    // }
    // public void adjustPricesBasedOnPrices() {
    //     // Variables de débogage
    //     double oldBuyPrice = this.buyPrice;
    //     double oldSellPrice = this.sellPrice;
        
    //     // Calculer le ratio de prix (entre 0 et 1)
    //     double priceBuyRatio = Math.max(0.0, Math.min(1.0, (buyPrice - minBuy) / (maxBuy - minBuy)));
    //     double priceSellRatio = Math.max(0.0, Math.min(1.0, (sellPrice - minSell) / (maxSell - minSell)));

    //     buyPrice = minBuy + (maxBuy - minBuy) * priceBuyRatio * stockBuyModifier;
    //     sellPrice = minSell + (maxSell - minSell) * priceSellRatio * stockSellModifier;

    //     // Vérifier les bornes
    //     if (buyPrice < sellPrice + MIN_MARGIN) {
    //         buyPrice = sellPrice + MIN_MARGIN;
    //     }
    //     if (sellPrice > buyPrice - MIN_MARGIN) {
    //         sellPrice = buyPrice - MIN_MARGIN;
    //     }

        
    //     // Log des changements
    //     DynaShopPlugin.getInstance().getLogger().info("Stock: " + stock + "/" + maxStock + " (ratio BUY: " + priceBuyRatio + ", SELL: " + priceSellRatio + ")");
    //     // DynaShopPlugin.getInstance().getLogger().info("Stock: " + stock + "/" + maxStock + " (ratio : " + priceRatio + ")");
    //     DynaShopPlugin.getInstance().getLogger().info("Prix d'achat: " + oldBuyPrice + " -> " + buyPrice + " (mod: " + stockBuyModifier + ")");
    //     DynaShopPlugin.getInstance().getLogger().info("Prix de vente: " + oldSellPrice + " -> " + sellPrice + " (mod: " + stockSellModifier + ")");
    // }

    public void adjustPricesBasedOnStock() {
        // Variables de débogage
        double oldBuyPrice = this.buyPrice;
        double oldSellPrice = this.sellPrice;
        
        // Calculer le ratio de stock (entre 0 et 1)
        double stockRatio = Math.max(0.0, Math.min(1.0, (double)(stock - minStock) / (maxStock - minStock)));
        
        // Formule ajustée pour le prix d'achat (diminue quand le stock augmente)
        // buyPrice = minBuy + (maxBuy - minBuy) * (1.0 - stockRatio * stockBuyModifier);
        // Formule inversée : prix élevés quand stock proche de 0, prix bas quand stock proche du max
        buyPrice = maxBuy - (maxBuy - minBuy) * stockRatio * stockBuyModifier;
        
        // Formule ajustée pour le prix de vente (diminue quand le stock augmente)
        // sellPrice = minSell + (maxSell - minSell) * (1.0 - stockRatio * stockSellModifier);
        // Formule inversée : prix élevés quand stock proche de 0, prix bas quand stock proche du max
        sellPrice = maxSell - (maxSell - minSell) * stockRatio * stockSellModifier;
        
        // Vérifier les bornes
        if (buyPrice < sellPrice + MIN_MARGIN) {
            buyPrice = sellPrice + MIN_MARGIN;
        }
        if (sellPrice > buyPrice - MIN_MARGIN) {
            sellPrice = buyPrice - MIN_MARGIN;
        }
        
        // Log des changements
        DynaShopPlugin.getInstance().getLogger().info("Stock: " + stock + "/" + maxStock + " (ratio: " + stockRatio + ")");
        DynaShopPlugin.getInstance().getLogger().info("Prix d'achat: " + oldBuyPrice + " -> " + buyPrice + " (mod: " + stockBuyModifier + ")");
        DynaShopPlugin.getInstance().getLogger().info("Prix de vente: " + oldSellPrice + " -> " + sellPrice + " (mod: " + stockSellModifier + ")");
    }
    
    public void increaseStock(int amount) {
        stock = Math.min(stock + amount, maxStock);
        // adjustPricesBasedOnStock();
        adjustPricesBasedOnStock();
    }
    
    public void decreaseStock(int amount) {
        stock = Math.max(stock - amount, minStock);
        // adjustPricesBasedOnStock();
        adjustPricesBasedOnStock();
    }
}