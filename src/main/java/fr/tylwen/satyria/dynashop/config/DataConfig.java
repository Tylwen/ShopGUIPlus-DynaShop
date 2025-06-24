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
package fr.tylwen.satyria.dynashop.config;

import org.bukkit.configuration.file.FileConfiguration;

public class DataConfig {
    private final FileConfiguration config;

    // Variables pour la configuration de la base de données
    private final String databaseType;
    private final String databaseHost;
    private final int databasePort;
    private final String databaseName;
    private final String databaseUsername;
    private final String databasePassword;
    private final String databaseTablePrefix;
    // private final String databaseSqliteFile;

    private final int dynamicPriceDuration;
    private final long guiRefreshDefaultItems;
    private final long guiRefreshCriticalItems;

    // Variables pour les valeurs par défaut
    private final double priceMinMultiply;
    private final double priceMaxMultiply;
    private final double priceMin;
    private final double priceMax;
    private final double priceMargin;
    private final double buyGrowthRate;
    private final double buyDecayRate;
    private final double sellGrowthRate;
    private final double sellDecayRate;
    private final double priceIncrease;
    private final double priceDecrease;
    private final int stockMin;
    private final int stockMax;
    private final double stockModifier;
    private final double stockBuyModifier;
    private final double stockSellModifier;

    // Variables pour les recettes
    private final double shapedValue;
    private final double shapelessValue;
    private final double furnaceValue;
    // private final double brewingValue; // Si vous avez besoin de gérer les recettes de brassage
    // private final double smithingValue; // Si vous avez besoin de gérer les recettes de forge
    // private final double campfireValue; // Si vous avez besoin de gérer les recettes de feu de camp
    private final double stonecutterValue; // Si vous avez besoin de gérer les recettes de tailleur de pierre
    // private final double grindstoneValue; // Si vous avez besoin de gérer les recettes de meule
    // private final double loomValue; // Si vous avez besoin de gérer les recettes de métier à tisser
    // private final double smithingTableValue; // Si vous avez besoin de gérer les recettes de table de forge
    // private final double cartographyTableValue; // Si vous avez besoin de gérer les recettes de table de cartographie
    // private final double anvilValue; // Si vous avez besoin de gérer les recettes d'enclume

    public DataConfig(FileConfiguration config) {
        this.config = config;
        // Charger la configuration de la base de données
        this.databaseType = config.getString("database.type", "flatfile").toLowerCase();
        this.databaseHost = config.getString("database.mysql.host", "localhost");
        this.databasePort = config.getInt("database.mysql.port", 3306);
        this.databaseName = config.getString("database.mysql.name", "dynashop");
        this.databaseUsername = config.getString("database.mysql.username", "root");
        this.databasePassword = config.getString("database.mysql.password", "");
        this.databaseTablePrefix = config.getString("database.table-prefix", "dynashop");
        // this.databaseSqliteFile = config.getString("database.sqlite.file", "dynashop.db");

        // Duration of dynamic pricing period (in seconds)
        this.dynamicPriceDuration = config.getInt("time-period", 5);

        // Charger les valeurs par défaut depuis le fichier de configuration
        this.priceMinMultiply = config.getDouble("default.price-min-multiply", 0.5);
        this.priceMaxMultiply = config.getDouble("default.price-max-multiply", 2.0);

        this.priceMin = config.getDouble("default.price-min", 0.01);
        this.priceMax = config.getDouble("default.price-max", Integer.MAX_VALUE);
        this.priceMargin = config.getDouble("default.price-margin", 0.1);

        this.buyGrowthRate = config.getDouble("default.buy-growth-rate", 1.00005);
        this.buyDecayRate = config.getDouble("default.buy-decay-rate", 0.99998);
        this.sellGrowthRate = config.getDouble("default.sell-growth-rate", 1.00002);
        this.sellDecayRate = config.getDouble("default.sell-decay-rate", 0.99995);

        this.priceIncrease = config.getDouble("default.price-increase", 1.001);
        this.priceDecrease = config.getDouble("default.price-decrease", 0.999);

        this.stockMin = config.getInt("default.stock-min", 0);
        this.stockMax = config.getInt("default.stock-max", Integer.MAX_VALUE);
        this.stockModifier = config.getDouble("default.stock-modifier", 1.0);
        this.stockBuyModifier = config.getDouble("default.stock-buy-modifier", 0.5);
        this.stockSellModifier = config.getDouble("default.stock-sell-modifier", 2.0);

        // Charger les valeurs pour les recipes
        this.shapedValue = config.getDouble("recipe.shaped", 1.0);
        this.shapelessValue = config.getDouble("recipe.shapeless", 1.0);
        this.furnaceValue = config.getDouble("recipe.furnace", 1.0);
        this.stonecutterValue = config.getDouble("recipe.stonecutter", 1.0);
        
        this.guiRefreshDefaultItems = config.getLong("gui.refresh.default-items", 1000); // 1 seconde en ms
        this.guiRefreshCriticalItems = config.getLong("gui.refresh.critical-items", 300); // 0.3 seconde en ms
    }

    // Getters pour accéder aux valeurs de la configuration de la base de données
    public String getDatabaseType() {
        return databaseType;
    }

    public String getDatabaseHost() {
        return databaseHost;
    }

    public int getDatabasePort() {
        return databasePort;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public String getDatabaseUsername() {
        return databaseUsername;
    }

    public String getDatabasePassword() {
        return databasePassword;
    }

    public String getDatabaseTablePrefix() {
        return databaseTablePrefix;
    }

    // public String getDatabaseSqliteFile() {
    //     return databaseSqliteFile;
    // }

    public int getDynamicPriceDuration() {
        return dynamicPriceDuration;
    }

    // Getters pour accéder aux valeurs par défaut

    public double getPriceMinMultiply() {
        return priceMinMultiply;
    }

    public double getPriceMaxMultiply() {
        return priceMaxMultiply;
    }

    public double getPriceMin() {
        return priceMin;
    }

    public double getPriceMax() {
        return priceMax;
    }

    public double getPriceMargin() {
        return priceMargin;
    }

    public double getBuyGrowthRate() {
        return buyGrowthRate;
    }

    public double getBuyDecayRate() {
        return buyDecayRate;
    }

    public double getSellGrowthRate() {
        return sellGrowthRate;
    }

    public double getSellDecayRate() {
        return sellDecayRate;
    }

    public double getPriceIncrease() {
        return priceIncrease;
    }

    public double getPriceDecrease() {
        return priceDecrease;
    }

    public int getStockMin() {
        return stockMin;
    }

    public int getStockMax() {
        return stockMax;
    }

    public double getStockModifier() {
        return stockModifier;
    }

    public double getStockBuyModifier() {
        return stockBuyModifier;
    }

    public double getStockSellModifier() {
        return stockSellModifier;
    }

    // Getters pour accéder aux valeurs des recettes
    public double getShapedValue() {
        return shapedValue;
    }

    public double getShapelessValue() {
        return shapelessValue;
    }

    public double getFurnaceValue() {
        return furnaceValue;
    }

    /**
     * Obtient le modificateur pour les recettes de tailleur de pierre
     * @return Le modificateur pour les recettes de tailleur de pierre
     */
    public double getStonecutterValue() {
        return config.getDouble("recipe.stonecutter", 1.0); // Valeur par défaut de 1.0
    }

    public double getSmithingValue() {
        return config.getDouble("recipe.smithing", 1.0); // Valeur par défaut de 1.0
    }

    // Getters pour les valeurs de rafraîchissement de l'interface graphique
    public long getGuiRefreshDefaultItems() {
        return guiRefreshDefaultItems;
    }
    
    public long getGuiRefreshCriticalItems() {
        return guiRefreshCriticalItems;
    }
}