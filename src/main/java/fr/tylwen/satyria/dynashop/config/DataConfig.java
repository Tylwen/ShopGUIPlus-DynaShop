package fr.tylwen.satyria.dynashop.config;

import org.bukkit.configuration.file.FileConfiguration;

public class DataConfig {
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
    private final double stockBuyModifier;
    private final double stockSellModifier;

    // Variables pour les recettes
    private final double shapedValue;
    private final double shapelessValue;
    private final double furnaceValue;

    public DataConfig(FileConfiguration config) {
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
        this.priceMax = config.getDouble("default.price-max", 1000.0);
        this.priceMargin = config.getDouble("default.price-margin", 0.1);

        this.buyGrowthRate = config.getDouble("default.buy-growth-rate", 1.00005);
        this.buyDecayRate = config.getDouble("default.buy-decay-rate", 0.99998);
        this.sellGrowthRate = config.getDouble("default.sell-growth-rate", 1.00002);
        this.sellDecayRate = config.getDouble("default.sell-decay-rate", 0.99995);

        this.priceIncrease = config.getDouble("default.price-increase", 1.001);
        this.priceDecrease = config.getDouble("default.price-decrease", 0.999);

        this.stockMin = config.getInt("default.stock-min", 0);
        this.stockMax = config.getInt("default.stock-max", Integer.MAX_VALUE);
        this.stockBuyModifier = config.getDouble("default.stock-buy-modifier", 0.5);
        this.stockSellModifier = config.getDouble("default.stock-sell-modifier", 2.0);

        // Charger les valeurs pour les recipes
        this.shapedValue = config.getDouble("recipe.shaped", 1.0);
        this.shapelessValue = config.getDouble("recipe.shapeless", 1.0);
        this.furnaceValue = config.getDouble("recipe.furnace", 1.0);

        
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

    // Getters pour les valeurs de rafraîchissement de l'interface graphique
    public long getGuiRefreshDefaultItems() {
        return guiRefreshDefaultItems;
    }
    
    public long getGuiRefreshCriticalItems() {
        return guiRefreshCriticalItems;
    }
}