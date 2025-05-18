package fr.tylwen.satyria.dynashop.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import fr.tylwen.satyria.dynashop.DynaShopPlugin;
import fr.tylwen.satyria.dynashop.config.DataConfig;
import fr.tylwen.satyria.dynashop.data.DynamicPrice;
import net.brcdev.shopgui.ShopGuiPlusApi;
import net.brcdev.shopgui.shop.Shop;
import net.brcdev.shopgui.shop.item.ShopItem;
import org.bukkit.Bukkit;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Level;

public class DataManager {
    private final DynaShopPlugin plugin;
    private final DataConfig dataConfig;
    private HikariDataSource dataSource;
    private boolean isInitialized = false;

    private static final int RETRY_LIMIT = 3;
    private static final int RETRY_DELAY_MS = 1000;

    public DataManager(DynaShopPlugin plugin) {
        this.plugin = plugin;
        this.dataConfig = new DataConfig(plugin.getConfigMain());
    }

    /**
     * Initialise la connexion à la base de données avec un pool de connexions.
     */
    public synchronized void initDatabase() {
        if (isInitialized && dataSource != null && !dataSource.isClosed()) {
            return;
        }

        closeDataSource();

        try {
            HikariConfig config = new HikariConfig();
            String type = dataConfig.getDatabaseType();
            
            if (type.equals("mysql")) {
                setupMySQLConnection(config);
            } else {
                setupSQLiteConnection(config);
            }
            
            // Configuration commune
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(3);
            config.setConnectionTimeout(30000);
            config.setIdleTimeout(600000);
            config.setMaxLifetime(1800000);
            config.setConnectionTestQuery("SELECT 1");
            config.setPoolName("DynaShopHikariPool");
            
            dataSource = new HikariDataSource(config);
            isInitialized = true;
            
            createTables();
            plugin.getLogger().info("Database connection established successfully (type: " + type + ")");
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to initialize database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void setupMySQLConnection(HikariConfig config) {
        String host = dataConfig.getDatabaseHost();
        int port = dataConfig.getDatabasePort();
        String name = dataConfig.getDatabaseName();
        String username = dataConfig.getDatabaseUsername();
        String password = dataConfig.getDatabasePassword();

        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + name + "?useSSL=false&autoReconnect=true&useUnicode=true&characterEncoding=UTF-8");
        config.setDriverClassName("com.mysql.jdbc.Driver");
        config.setUsername(username);
        config.setPassword(password);
        
        // Optimisations spécifiques à MySQL
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("useLocalSessionState", "true");
        config.addDataSourceProperty("rewriteBatchedStatements", "true");
        config.addDataSourceProperty("cacheResultSetMetadata", "true");
        config.addDataSourceProperty("cacheServerConfiguration", "true");
        config.addDataSourceProperty("elideSetAutoCommits", "true");
        config.addDataSourceProperty("maintainTimeStats", "false");
    }

    private void setupSQLiteConnection(HikariConfig config) {
        File databaseFile = new File(plugin.getDataFolder(), dataConfig.getDatabaseSqliteFile());
        
        if (!databaseFile.exists()) {
            try {
                if (!plugin.getDataFolder().exists()) {
                    plugin.getDataFolder().mkdirs();
                }
                databaseFile.createNewFile();
                // plugin.getLogger().info("Fichier de base de données SQLite créé: " + databaseFile.getAbsolutePath());
            } catch (IOException e) {
                // plugin.getLogger().severe("Impossible de créer le fichier SQLite: " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        config.setJdbcUrl("jdbc:sqlite:" + databaseFile.getAbsolutePath());
        config.setDriverClassName("org.sqlite.JDBC");
        
        // La connexion SQLite ne supporte pas la concurrence, il faut limiter
        config.setMaximumPoolSize(1);
        config.setConnectionTimeout(30000);
    }

    /**
     * Crée les tables nécessaires si elles n'existent pas.
     */
    private void createTables() {
        String tablePrefix = dataConfig.getDatabaseTablePrefix();
        String createTableSQL = "CREATE TABLE IF NOT EXISTS " + tablePrefix + "_prices (" +
                                "shopID VARCHAR(255) NOT NULL, " +
                                "itemID VARCHAR(255) NOT NULL, " +
                                "buyPrice DOUBLE NOT NULL, " +
                                "sellPrice DOUBLE NOT NULL, " +
                                "stock INT DEFAULT 0, " +
                                "PRIMARY KEY (shopID, itemID)" +
                                ")";
                                
        executeUpdate(createTableSQL);
    }

    /**
     * Obtient une connexion à partir du pool de connexions.
     */
    public Connection getConnection() throws SQLException {
        if (!isInitialized || dataSource == null || dataSource.isClosed()) {
            initDatabase();
        }
        return dataSource.getConnection();
    }

    /**
     * Rafraîchit le pool de connexions.
     */
    public synchronized void reloadDatabaseConnection() {
        closeDataSource();
        initDatabase();
        plugin.getLogger().info("Database connection successfully reloaded.");
    }

    /**
     * Ferme le pool de connexions.
     */
    private void closeDataSource() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
        isInitialized = false;
    }

    /**
     * Exécute une requête SELECT avec gestion des erreurs et reconnexion automatique.
     */
    private <T> Optional<T> executeQuery(String sql, ResultSetProcessor<T> processor, Object... params) {
        for (int attempt = 0; attempt < RETRY_LIMIT; attempt++) {
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                for (int i = 0; i < params.length; i++) {
                    stmt.setObject(i + 1, params[i]);
                }
                
                try (ResultSet rs = stmt.executeQuery()) {
                    return Optional.ofNullable(processor.process(rs));
                }
            } catch (SQLException e) {
                handleSQLException(e, attempt);
                
                if (attempt == RETRY_LIMIT - 1) {
                    plugin.getLogger().severe("Failed to execute query after " + RETRY_LIMIT + " attempts: " + sql);
                    return Optional.empty();
                }
                
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Exécute une requête UPDATE/INSERT/DELETE avec gestion des erreurs et reconnexion automatique.
     */
    public int executeUpdate(String sql, Object... params) {
        for (int attempt = 0; attempt < RETRY_LIMIT; attempt++) {
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                for (int i = 0; i < params.length; i++) {
                    stmt.setObject(i + 1, params[i]);
                }
                
                return stmt.executeUpdate();
            } catch (SQLException e) {
                handleSQLException(e, attempt);
                
                if (attempt == RETRY_LIMIT - 1) {
                    plugin.getLogger().severe("Failed to execute update after " + RETRY_LIMIT + " attempts: " + sql);
                    return 0;
                }
                
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        return 0;
    }

    /**
     * Gère les exceptions SQL et tente de reconnecter si nécessaire.
     */
    private void handleSQLException(SQLException e, int attempt) {
        if (attempt < RETRY_LIMIT - 1) {
            plugin.getLogger().warning("SQL error (attempt " + (attempt + 1) + "/" + RETRY_LIMIT + "): " + e.getMessage());
        } else {
            plugin.getLogger().severe("Critical SQL error: " + e.getMessage());
            e.printStackTrace();
        }
        
        // Si connexion perdue, tenter de la rétablir
        if (isConnectionError(e)) {
            plugin.getLogger().info("Attempting to reconnect to the database...");
            reloadDatabaseConnection();
        }
    }

    /**
     * Vérifie si l'erreur est liée à une perte de connexion.
     */
    private boolean isConnectionError(SQLException e) {
        String message = e.getMessage().toLowerCase();
        return message.contains("closed") || 
               message.contains("terminated") || 
               message.contains("gone away") || 
               message.contains("timeout") || 
               message.contains("refused") ||
               message.contains("communication") ||
               message.contains("link failure");
    }

    /**
     * Sauvegarde le prix d'un item dans la base de données.
     */
    public void savePrice(String shopId, String itemId, double buyPrice, double sellPrice) {
        String sql = "REPLACE INTO " + dataConfig.getDatabaseTablePrefix() + 
                     "_prices (shopID, itemID, buyPrice, sellPrice, stock) " +
                     "VALUES (?, ?, ?, ?, (SELECT COALESCE(stock, 0) FROM " + 
                     dataConfig.getDatabaseTablePrefix() + "_prices WHERE shopID = ? AND itemID = ?))";
        
        executeUpdate(sql, shopId, itemId, buyPrice, sellPrice, shopId, itemId);
    }

    /**
     * Sauvegarde le prix et le stock d'un item dans la base de données.
     */
    public void savePrice(String shopId, String itemId, double buyPrice, double sellPrice, int stock) {
        String sql = "REPLACE INTO " + dataConfig.getDatabaseTablePrefix() + 
                     "_prices (shopID, itemID, buyPrice, sellPrice, stock) VALUES (?, ?, ?, ?, ?)";
        
        executeUpdate(sql, shopId, itemId, buyPrice, sellPrice, stock);
    }

    /**
     * Met à jour le stock d'un item.
     */
    public void setStock(String shopId, String itemId, int stock) {
        String sql = "UPDATE " + dataConfig.getDatabaseTablePrefix() + "_prices SET stock = ? WHERE shopID = ? AND itemID = ?";
        
        int rowsAffected = executeUpdate(sql, stock, shopId, itemId);
        
        // Si aucune ligne n'a été affectée, l'item n'existe pas encore
        if (rowsAffected == 0) {
            sql = "INSERT INTO " + dataConfig.getDatabaseTablePrefix() + "_prices (shopID, itemID, buyPrice, sellPrice, stock) VALUES (?, ?, 0, 0, ?)";
            executeUpdate(sql, shopId, itemId, stock);
        }
    }

    /**
     * Récupère le prix d'un item.
     */
    public Optional<Double> getPrice(String shopId, String itemId, String priceType) {
        String column = priceType.equals("buyPrice") ? "buyPrice" : "sellPrice";
        String sql = "SELECT " + column + " FROM " + dataConfig.getDatabaseTablePrefix() + "_prices WHERE shopID = ? AND itemID = ?";
        
        return executeQuery(sql, rs -> {
            if (rs.next()) {
                return rs.getDouble(column);
            }
            return null;
        }, shopId, itemId);
    }

    /**
     * Récupère le prix d'achat d'un item.
     */
    public Optional<Double> getBuyPrice(String shopId, String itemId) {
        return getPrice(shopId, itemId, "buyPrice");
    }

    /**
     * Récupère le prix de vente d'un item.
     */
    public Optional<Double> getSellPrice(String shopId, String itemId) {
        return getPrice(shopId, itemId, "sellPrice");
    }

    /**
     * Récupère le stock d'un item.
     */
    public Optional<Integer> getStock(String shopId, String itemId) {
        String sql = "SELECT stock FROM " + dataConfig.getDatabaseTablePrefix() + "_prices WHERE shopID = ? AND itemID = ?";

        return executeQuery(sql, rs -> {
            if (rs.next()) {
                return rs.getInt("stock");
            }
            return null;
        }, shopId, itemId);
    }

    /**
     * Récupère les prix d'un item.
     */
    public Optional<DynamicPrice> getPrices(String shopId, String itemId) {
        String sql = "SELECT buyPrice, sellPrice, stock FROM " + dataConfig.getDatabaseTablePrefix() + "_prices WHERE shopID = ? AND itemID = ?";
        
        return executeQuery(sql, rs -> {
            if (rs.next()) {
                double buyPrice = rs.getDouble("buyPrice");
                double sellPrice = rs.getDouble("sellPrice");
                int stock = rs.getInt("stock");
                return new DynamicPrice(buyPrice, sellPrice, stock);
            }
            return null;
        }, shopId, itemId);
    }

    // /**
    //  * Charge tous les prix depuis la base de données.
    //  */
    // public Map<ShopItem, DynamicPrice> loadPricesFromDatabase() {
    //     Map<ShopItem, DynamicPrice> priceMap = new HashMap<>();
    //     String sql = "SELECT shopID, itemID, buyPrice, sellPrice, stock FROM " + dataConfig.getDatabaseTablePrefix() + "_prices";
        
    //     executeQuery(sql, rs -> {
    //         while (rs.next()) {
    //             String shopId = rs.getString("shopID");
    //             String itemId = rs.getString("itemID");
    //             double buyPrice = rs.getDouble("buyPrice");
    //             double sellPrice = rs.getDouble("sellPrice");
    //             int stock = rs.getInt("stock");
                
    //             Shop shop = ShopGuiPlusApi.getPlugin().getShopManager().getShopById(shopId);
    //             if (shop != null) {
    //                 ShopItem item = shop.getShopItems().stream()
    //                     .filter(i -> i.getId().equals(itemId))
    //                     .findFirst()
    //                     .orElse(null);
                    
    //                 if (item != null) {
    //                     DynamicPrice price = new DynamicPrice(buyPrice, sellPrice, stock);
    //                     priceMap.put(item, price);
    //                 }
    //             }
    //         }
    //         return null;
    //     });
        
    //     return priceMap;
    // }
    /**
     * Charge tous les prix depuis la base de données.
     */
    public Map<ShopItem, DynamicPrice> loadPricesFromDatabase() {
        Map<ShopItem, DynamicPrice> priceMap = new HashMap<>();
        String sql = "SELECT shopID, itemID, buyPrice, sellPrice, stock FROM " + dataConfig.getDatabaseTablePrefix() + "_prices";
        
        // Log début de l'opération
        // plugin.getLogger().warning("Chargement des prix depuis la base de données...");
        
        try {
            int[] rowCount = {0}; // Utiliser un tableau pour pouvoir modifier la valeur dans le lambda
            
            executeQuery(sql, rs -> {
                while (rs.next()) {
                    rowCount[0]++;
                    String shopId = rs.getString("shopID");
                    String itemId = rs.getString("itemID");
                    double buyPrice = rs.getDouble("buyPrice");
                    double sellPrice = rs.getDouble("sellPrice");
                    int stock = rs.getInt("stock");
                    
                    // Log pour vérifier si nous récupérons bien des données
                    if (rowCount[0] <= 5 || rowCount[0] % 100 == 0) {
                        // plugin.getLogger().info("Trouvé en base: " + shopId + ":" + itemId + " - Buy: " + buyPrice + ", Sell: " + sellPrice + ", Stock: " + stock);
                    }
                    
                    try {
                        // Vérifier si ShopGuiPlusApi est initialisé
                        if (ShopGuiPlusApi.getPlugin() == null) {
                            // plugin.getLogger().severe("ShopGuiPlusApi.getPlugin() est null! L'API n'est pas initialisée.");
                            continue;
                        }
                        
                        Shop shop = ShopGuiPlusApi.getPlugin().getShopManager().getShopById(shopId);
                        if (shop == null) {
                            // plugin.getLogger().warning("Shop introuvable: " + shopId);
                            continue;
                        }
                        
                        ShopItem item = shop.getShopItems().stream()
                            .filter(i -> i.getId().equals(itemId))
                            .findFirst()
                            .orElse(null);
                        
                        if (item == null) {
                            // plugin.getLogger().warning("Item introuvable: " + itemId + " dans shop: " + shopId);
                            continue;
                        }
                        
                        DynamicPrice price = new DynamicPrice(buyPrice, sellPrice, stock);
                        priceMap.put(item, price);
                    } catch (Exception e) {
                        // plugin.getLogger().severe("Erreur lors du traitement de " + shopId + ":" + itemId + ": " + e.getMessage());
                    }
                }
                
                // plugin.getLogger().warning("Lecture terminée. Trouvé " + rowCount[0] + " enregistrements en base, " + priceMap.size() + " prix chargés avec succès.");
                return null;
            });
        } catch (Exception e) {
            // plugin.getLogger().severe("Erreur critique lors du chargement des prix: " + e.getMessage());
            e.printStackTrace();
        }
        
        // Logs finaux
        // plugin.getLogger().warning("Nombre final d'items chargés: " + priceMap.size());
        return priceMap;
    }

    /**
     * Exécute une opération de base de données de manière asynchrone.
     */
    public <T> CompletableFuture<T> executeAsync(DatabaseOperation<T> operation) {
        CompletableFuture<T> future = new CompletableFuture<>();
        
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                T result = operation.execute();
                future.complete(result);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Error during asynchronous execution", e);
                future.completeExceptionally(e);
            }
        });
        
        return future;
    }

    /**
     * Ferme toutes les connexions.
     */
    public void closeDatabase() {
        closeDataSource();
        plugin.getLogger().info("Database connection closed.");
    }

    /**
     * Interface fonctionnelle pour traiter un ResultSet.
     */
    @FunctionalInterface
    private interface ResultSetProcessor<T> {
        T process(ResultSet rs) throws SQLException;
    }

    /**
     * Interface fonctionnelle pour les opérations de base de données.
     */
    @FunctionalInterface
    public interface DatabaseOperation<T> {
        T execute() throws Exception;
    }
}