package fr.tylwen.satyria.dynashop.system;

import fr.tylwen.satyria.dynashop.DynaShopPlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.scheduler.BukkitTask;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;
// import java.util.UUID;
import java.util.logging.Level;

public class InflationManager {

    private final DynaShopPlugin plugin;
    private boolean enabled;
    private double baseRate; // Taux d'inflation de base par jour
    private double transactionMultiplier; // Multiplicateur basé sur le nombre de transactions
    private double moneyInflationThreshold; // Seuil à partir duquel l'inflation par masse monétaire s'active
    private double moneyInflationRate; // Taux d'inflation basé sur la masse monétaire
    private long lastInflationUpdate; // Timestamp du dernier ajustement
    
    private Map<String, Double> categoryInflationRates; // Taux d'inflation par catégorie
    private Map<String, Double> itemInflationRates; // Taux d'inflation par item spécifique
    private double inflationFactor = 1.0; // Facteur d'inflation actuel (1.0 = pas d'inflation)
    private double deflationRate; // Taux de déflation automatique
    
    private BukkitTask inflationTask;
    private final DecimalFormat df = new DecimalFormat("#,##0.00");
    
    public InflationManager(DynaShopPlugin plugin) {
        this.plugin = plugin;
        this.categoryInflationRates = new HashMap<>();
        this.itemInflationRates = new HashMap<>();
        loadConfig();
    }
    
    public void loadConfig() {
        ConfigurationSection inflationConfig = plugin.getConfig().getConfigurationSection("inflation");
        
        if (inflationConfig == null) {
            plugin.getLogger().info("No inflation configuration found. Creating default config.");
            plugin.getConfig().set("inflation.enabled", false);
            plugin.getConfig().set("inflation.base-rate", 0.5); // 0.5% par jour
            plugin.getConfig().set("inflation.transaction-multiplier", 0.001); // 0.001% par transaction
            plugin.getConfig().set("inflation.money-threshold", 1000000); // 1 million
            plugin.getConfig().set("inflation.money-rate", 0.1); // 0.1% par million au-delà du seuil
            plugin.getConfig().set("inflation.deflation-rate", 0.05); // 0.05% par jour de déflation automatique
            plugin.getConfig().set("inflation.update-interval", 24); // En heures
            plugin.getConfig().set("inflation.max-factor", 5.0); // Facteur d'inflation maximum
            plugin.saveConfig();
            
            enabled = false;
            baseRate = 0.5;
            transactionMultiplier = 0.001;
            moneyInflationThreshold = 1000000;
            moneyInflationRate = 0.1;
            deflationRate = 0.05;
        } else {
            enabled = inflationConfig.getBoolean("enabled", false);
            baseRate = inflationConfig.getDouble("base-rate", 0.5);
            transactionMultiplier = inflationConfig.getDouble("transaction-multiplier", 0.001);
            moneyInflationThreshold = inflationConfig.getDouble("money-threshold", 1000000);
            moneyInflationRate = inflationConfig.getDouble("money-rate", 0.1);
            deflationRate = inflationConfig.getDouble("deflation-rate", 0.05);
            
            // Charger les taux d'inflation par catégorie
            if (inflationConfig.isConfigurationSection("categories")) {
                ConfigurationSection categoriesSection = inflationConfig.getConfigurationSection("categories");
                for (String category : categoriesSection.getKeys(false)) {
                    double rate = categoriesSection.getDouble(category, baseRate);
                    categoryInflationRates.put(category, rate);
                }
            }
            
            // Charger les taux d'inflation par item
            if (inflationConfig.isConfigurationSection("items")) {
                ConfigurationSection itemsSection = inflationConfig.getConfigurationSection("items");
                for (String item : itemsSection.getKeys(false)) {
                    double rate = itemsSection.getDouble(item, baseRate);
                    itemInflationRates.put(item, rate);
                }
            }
            
            // Charger le facteur d'inflation actuel s'il existe
            inflationFactor = plugin.getStorageManager().getInflationFactor();
            if (inflationFactor <= 0) {
                inflationFactor = 1.0; // Valeur par défaut si non définie
            }
            
            lastInflationUpdate = plugin.getStorageManager().getLastInflationUpdate();
            if (lastInflationUpdate <= 0) {
                lastInflationUpdate = System.currentTimeMillis(); // Initialiser à maintenant
                plugin.getStorageManager().saveInflationData(inflationFactor, lastInflationUpdate);
            }
        }
        
        if (enabled) {
            startInflationTask();
        } else {
            stopInflationTask();
        }
    }
    
    public void startInflationTask() {
        if (inflationTask != null) {
            stopInflationTask();
        }
        
        int updateIntervalHours = plugin.getConfig().getInt("inflation.update-interval", 24);
        long ticks = updateIntervalHours * 60 * 60 * 20L; // Convertir des heures en ticks
        
        inflationTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin,
            this::updateInflation, ticks, ticks);
        
        plugin.getLogger().info("Inflation system started with update interval of " + updateIntervalHours + " hours");
    }
    
    public void stopInflationTask() {
        if (inflationTask != null) {
            inflationTask.cancel();
            inflationTask = null;
        }
    }
    
    public void updateInflation() {
        if (!enabled) return;
        
        long now = System.currentTimeMillis();
        long elapsedTime = now - lastInflationUpdate;
        double elapsedDays = elapsedTime / (1000.0 * 60 * 60 * 24); // Convertir en jours
        
        // Calculer l'inflation basée sur le temps
        double timeInflation = 1.0 + (baseRate / 100.0 * elapsedDays);
        
        // Calculer la déflation automatique
        double deflation = 1.0 - (deflationRate / 100.0 * elapsedDays);
        
        // Calculer l'inflation basée sur les transactions
        // int transactionCount = plugin.getTransactionLimiter().getMetrics().getOrDefault("total_transactions", 0);
        // Calculer l'inflation basée sur les transactions
        // Obtenir les statistiques via CompletableFuture
        int transactionCount = 0;
        try {
            Map<String, Object> stats = plugin.getTransactionLimiter().getStatistics().get(); // Attendre le résultat
            // Extraire et convertir la valeur, en gérant le fait que c'est un Object
            if (stats.containsKey("total_records")) {
                Object value = stats.get("total_records");
                if (value instanceof Integer intValue) {
                    transactionCount = intValue;
                } else if (value instanceof Number numberValue) {
                    transactionCount = numberValue.intValue();
                } else if (value != null) {
                    transactionCount = Integer.parseInt(value.toString());
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Erreur lors de la récupération des statistiques de transactions", e);
        }

        double transactionInflation = 1.0 + (transactionCount * transactionMultiplier / 100.0);
        
        // Calculer l'inflation basée sur la masse monétaire
        double totalMoney = getTotalMoneyInEconomy();
        double moneyInflation = 1.0;
        if (totalMoney > moneyInflationThreshold) {
            double excessMoney = totalMoney - moneyInflationThreshold;
            double excessFactor = excessMoney / moneyInflationThreshold;
            moneyInflation = 1.0 + (excessFactor * moneyInflationRate / 100.0);
        }
        
        // Combiner les facteurs d'inflation
        double newInflationFactor = this.inflationFactor * timeInflation * transactionInflation * moneyInflation * deflation;
        
        // Limiter le facteur d'inflation maximum
        double maxFactor = plugin.getConfig().getDouble("inflation.max-factor", 5.0);
        newInflationFactor = Math.min(newInflationFactor, maxFactor);
        newInflationFactor = Math.max(newInflationFactor, 0.5); // Minimum de 0.5 (déflation limitée)
        
        // Mettre à jour le facteur d'inflation
        this.inflationFactor = newInflationFactor;
        this.lastInflationUpdate = now;
        
        // Sauvegarder les données d'inflation
        plugin.getStorageManager().saveInflationData(inflationFactor, lastInflationUpdate);
        
        plugin.getLogger().info("Inflation updated: Factor=" + df.format(inflationFactor) + 
                               " (Time=" + df.format(timeInflation) + 
                               ", Transactions=" + df.format(transactionInflation) + 
                               ", Money=" + df.format(moneyInflation) + 
                               ", Deflation=" + df.format(deflation) + ")");
    }
    
    private double getTotalMoneyInEconomy() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            return 0.0;
        }
        
        try {
            Economy economy = plugin.getTaxService().getEconomy();
            double total = 0.0;
            
            // Obtenir tous les joueurs qui ont déjà joué
            for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
                if (player != null && player.hasPlayedBefore()) {
                    total += economy.getBalance(player);
                }
            }
            return total;
        } catch (Exception e) {
            plugin.getLogger().warning("Erreur lors du calcul de la masse monétaire: " + e.getMessage());
            return 0.0;
        }
    }
    
    public double applyInflationToPrice(String shopId, String itemId, double basePrice) {
        if (!enabled || inflationFactor <= 0) {
            return basePrice;
        }
        
        // Appliquer d'abord le taux spécifique à l'item s'il existe
        String itemKey = shopId + ":" + itemId;
        if (itemInflationRates.containsKey(itemKey)) {
            double itemRate = itemInflationRates.get(itemKey);
            return basePrice * (inflationFactor * itemRate / baseRate);
        }
        
        // Sinon, appliquer le taux de la catégorie si défini
        String category = plugin.getShopConfigManager().getItemCategory(shopId, itemId);
        if (category != null && categoryInflationRates.containsKey(category)) {
            double categoryRate = categoryInflationRates.get(category);
            return basePrice * (inflationFactor * categoryRate / baseRate);
        }
        
        // Sinon, appliquer le facteur d'inflation standard
        return basePrice * inflationFactor;
    }
    
    public double getInflationFactor() {
        return inflationFactor;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (enabled && inflationTask == null) {
            startInflationTask();
        } else if (!enabled && inflationTask != null) {
            stopInflationTask();
        }
        plugin.getConfig().set("inflation.enabled", enabled);
        plugin.saveConfig();
    }
    
    public void resetInflation() {
        inflationFactor = 1.0;
        lastInflationUpdate = System.currentTimeMillis();
        plugin.getStorageManager().saveInflationData(inflationFactor, lastInflationUpdate);
    }
}