package fr.tylwen.satyria.dynashop.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import fr.tylwen.satyria.dynashop.DynaShopPlugin;
import fr.tylwen.satyria.dynashop.config.DataConfig;
import fr.tylwen.satyria.dynashop.data.param.DynaShopType;
import fr.tylwen.satyria.dynashop.price.DynamicPrice;
import fr.tylwen.satyria.dynashop.system.chart.PriceHistory;
import fr.tylwen.satyria.dynashop.system.chart.PriceHistory.PriceDataPoint;
import net.brcdev.shopgui.ShopGuiPlusApi;
import net.brcdev.shopgui.shop.Shop;
import net.brcdev.shopgui.shop.item.ShopItem;
// import org.bukkit.Bukkit;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

public class DataManager {
    private final DynaShopPlugin plugin;
    private final DataConfig dataConfig;
    private HikariDataSource dataSource;
    private Connection sqliteConnection;
    private boolean isInitialized = false;
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();

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
            String type = dataConfig.getDatabaseType();
            
            if (type.equals("mysql")) {
                HikariConfig config = new HikariConfig();
                setupMySQLConnection(config);
                // Configuration commune
                config.setMaximumPoolSize(10);
                config.setMinimumIdle(5);
                config.setConnectionTimeout(30000);
                config.setIdleTimeout(60000);
                config.setMaxLifetime(1800000);
                config.setConnectionTestQuery("SELECT 1");
                config.setPoolName("DynaShopHikariPool");
                dataSource = new HikariDataSource(config);
            } else {
                setupSQLiteConnection();
                dataSource = null; // Important : pas de pool pour SQLite
            }

            isInitialized = true;
            
            createTables();
            if (type.equals("mysql")) migrateFromOldSchema(); // Migration des anciennes données si nécessaire
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

    private void setupSQLiteConnection() {
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
        
        // config.setJdbcUrl("jdbc:sqlite:" + databaseFile.getAbsolutePath());
        // config.setDriverClassName("org.sqlite.JDBC");
        
        // // La connexion SQLite ne supporte pas la concurrence, il faut limiter
        // config.setMaximumPoolSize(1);
        // config.setConnectionTimeout(30000);
        try {
            Class.forName("org.sqlite.JDBC");
            String url = "jdbc:sqlite:" + databaseFile.getAbsolutePath();
            sqliteConnection = DriverManager.getConnection(url);
            try (Statement stmt = sqliteConnection.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = ON");
            }
        } catch (SQLException | ClassNotFoundException e) {
            plugin.getLogger().severe("Erreur SQLite: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Crée les tables nécessaires si elles n'existent pas.
     */
    private void createTables() {
        String tablePrefix = dataConfig.getDatabaseTablePrefix();
        // String createTableSQL = "CREATE TABLE IF NOT EXISTS " + tablePrefix + "_prices (" +
        //                         "shopID VARCHAR(255) NOT NULL, " +
        //                         "itemID VARCHAR(255) NOT NULL, " +
        //                         "buyPrice DOUBLE NOT NULL, " +
        //                         "sellPrice DOUBLE NOT NULL, " +
        //                         "stock INT DEFAULT 0, " +
        //                         "PRIMARY KEY (shopID, itemID)" +
        //                         ")";
                                
        // Table pour les prix d'achat (uniquement quand buyPrice > 0)
        String createBuyPriceTableSQL = "CREATE TABLE IF NOT EXISTS " + tablePrefix + "_buy_prices (" +
                                "shopID VARCHAR(255) NOT NULL, " +
                                "itemID VARCHAR(255) NOT NULL, " +
                                "price DOUBLE NOT NULL, " +
                                "PRIMARY KEY (shopID, itemID)" +
                                ")";
        
        // Table pour les prix de vente (uniquement quand sellPrice > 0)
        String createSellPriceTableSQL = "CREATE TABLE IF NOT EXISTS " + tablePrefix + "_sell_prices (" +
                                "shopID VARCHAR(255) NOT NULL, " +
                                "itemID VARCHAR(255) NOT NULL, " +
                                "price DOUBLE NOT NULL, " +
                                "PRIMARY KEY (shopID, itemID)" +
                                ")";
        
        // Table pour le stock
        String createStockTableSQL = "CREATE TABLE IF NOT EXISTS " + tablePrefix + "_stock (" +
                                "shopID VARCHAR(255) NOT NULL, " +
                                "itemID VARCHAR(255) NOT NULL, " +
                                "stock INT DEFAULT -1, " +
                                "PRIMARY KEY (shopID, itemID)" +
                                ")";
                                
        // // Table pour les transactions (limites d'achat/vente)
        // String createTransactionsTableSQL = "CREATE TABLE IF NOT EXISTS " + tablePrefix + "_transactions (" +
        //                         "id " + (dataConfig.getDatabaseType().equals("mysql") ? "INT AUTO_INCREMENT" : "INTEGER") + " PRIMARY KEY, " +
        //                         "player_uuid VARCHAR(36) NOT NULL, " +
        //                         "shop_id VARCHAR(255) NOT NULL, " +
        //                         "item_id VARCHAR(255) NOT NULL, " +
        //                         "type VARCHAR(10) NOT NULL, " + // 'BUY' or 'SELL'
        //                         "amount INT NOT NULL, " +
        //                         "timestamp TIMESTAMP NOT NULL, " +
        //                         "cooldown_seconds INT NOT NULL" +
        //                         ")";
                                
        // executeUpdate(createTableSQL);
        executeUpdate(createBuyPriceTableSQL);
        executeUpdate(createSellPriceTableSQL);
        executeUpdate(createStockTableSQL);
        // executeUpdate(createTransactionsTableSQL);

        // Créer une vue pour simplifier les requêtes
        createPricesView(tablePrefix);
        
        // // Créer index pour les recherches fréquentes
        // if (dataConfig.getDatabaseType().equals("mysql")) {
        //     executeUpdate("CREATE INDEX IF NOT EXISTS idx_trans_player ON " + tablePrefix + "_transactions (player_uuid)");
        //     executeUpdate("CREATE INDEX IF NOT EXISTS idx_trans_item ON " + tablePrefix + "_transactions (shop_id, item_id)");
        // } else {
        //     try {
        //         executeUpdate("CREATE INDEX IF NOT EXISTS idx_trans_player ON " + tablePrefix + "_transactions (player_uuid)");
        //         executeUpdate("CREATE INDEX IF NOT EXISTS idx_trans_item ON " + tablePrefix + "_transactions (shop_id, item_id)");
        //     } catch (Exception e) {
        //         plugin.getLogger().warning("Note lors de la création des index de transactions: " + e.getMessage());
        //     }
        // }
        
        // // Puis créer la vue
        // createTransactionsView(tablePrefix);
    }

    // private void createTransactionsView(String tablePrefix) {
    //     try {
    //         String viewSQL;
            
    //         if (dataConfig.getDatabaseType().equals("mysql")) {
    //             executeUpdate("DROP VIEW IF EXISTS " + tablePrefix + "_transactions_view");
    //             viewSQL = "CREATE VIEW " + tablePrefix + "_transactions_view AS " +
    //                     "SELECT * FROM " + tablePrefix + "_transactions";
    //         } else {
    //             executeUpdate("DROP VIEW IF EXISTS " + tablePrefix + "_transactions_view");
    //             viewSQL = "CREATE VIEW " + tablePrefix + "_transactions_view AS " +
    //                     "SELECT * FROM " + tablePrefix + "_transactions";
    //         }
            
    //         executeUpdate(viewSQL);
    //     } catch (Exception e) {
    //         plugin.getLogger().warning("Impossible de créer la vue des transactions: " + e.getMessage());
    //     }
    // }

    private void createPricesView(String tablePrefix) {
        try {
            // // String viewSQL = "CREATE OR REPLACE VIEW " + tablePrefix + "_items AS " +
            // //                 "SELECT s.shopID, s.itemID, " +
            // //                 "COALESCE(b.price, -1) AS buyPrice, " +
            // //                 "COALESCE(sell.price, -1) AS sellPrice, " +
            // //                 "s.stock " +
            // //                 "FROM " + tablePrefix + "_stock s " +
            // //                 "LEFT JOIN " + tablePrefix + "_buy_prices b ON s.shopID = b.shopID AND s.itemID = b.itemID " +
            // //                 "LEFT JOIN " + tablePrefix + "_sell_prices sell ON s.shopID = sell.shopID AND s.itemID = sell.itemID";
            // String viewSQL = "CREATE OR REPLACE VIEW " + tablePrefix + "_items AS " +
            //                 "SELECT all_items.shopID, all_items.itemID, " +
            //                 "COALESCE(b.price, -1) AS buyPrice, " +
            //                 "COALESCE(sell.price, -1) AS sellPrice, " +
            //                 "COALESCE(s.stock, -1) AS stock " +
            //                 "FROM (" +
            //                 "   SELECT DISTINCT shopID, itemID FROM " + tablePrefix + "_buy_prices " +
            //                 "   UNION " +
            //                 "   SELECT DISTINCT shopID, itemID FROM " + tablePrefix + "_sell_prices " +
            //                 "   UNION " +
            //                 "   SELECT DISTINCT shopID, itemID FROM " + tablePrefix + "_stock" +
            //                 ") as all_items " +
            //                 "LEFT JOIN " + tablePrefix + "_stock s ON all_items.shopID = s.shopID AND all_items.itemID = s.itemID " +
            //                 "LEFT JOIN " + tablePrefix + "_buy_prices b ON all_items.shopID = b.shopID AND all_items.itemID = b.itemID " +
            //                 "LEFT JOIN " + tablePrefix + "_sell_prices sell ON all_items.shopID = sell.shopID AND all_items.itemID = sell.itemID";
            String viewSQL;
            
            // Adapter la syntaxe selon le type de base de données
            if (dataConfig.getDatabaseType().equals("mysql")) {
                // Syntaxe MySQL
                viewSQL = "CREATE OR REPLACE VIEW " + tablePrefix + "_items AS " +
                        "SELECT all_items.shopID, all_items.itemID, " +
                        "COALESCE(b.price, -1) AS buyPrice, " +
                        "COALESCE(sell.price, -1) AS sellPrice, " +
                        "COALESCE(s.stock, -1) AS stock " +
                        "FROM (" +
                        "   SELECT DISTINCT shopID, itemID FROM " + tablePrefix + "_buy_prices " +
                        "   UNION " +
                        "   SELECT DISTINCT shopID, itemID FROM " + tablePrefix + "_sell_prices " +
                        "   UNION " +
                        "   SELECT DISTINCT shopID, itemID FROM " + tablePrefix + "_stock" +
                        ") as all_items " +
                        "LEFT JOIN " + tablePrefix + "_stock s ON all_items.shopID = s.shopID AND all_items.itemID = s.itemID " +
                        "LEFT JOIN " + tablePrefix + "_buy_prices b ON all_items.shopID = b.shopID AND all_items.itemID = b.itemID " +
                        "LEFT JOIN " + tablePrefix + "_sell_prices sell ON all_items.shopID = sell.shopID AND all_items.itemID = sell.itemID";
            } else {
                // Syntaxe SQLite
                executeUpdate("DROP VIEW IF EXISTS " + tablePrefix + "_items");
                viewSQL = "CREATE VIEW " + tablePrefix + "_items AS " +
                        "SELECT all_items.shopID, all_items.itemID, " +
                        "COALESCE(b.price, -1) AS buyPrice, " +
                        "COALESCE(sell.price, -1) AS sellPrice, " +
                        "COALESCE(s.stock, -1) AS stock " +
                        "FROM (" +
                        "   SELECT DISTINCT shopID, itemID FROM " + tablePrefix + "_buy_prices " +
                        "   UNION " +
                        "   SELECT DISTINCT shopID, itemID FROM " + tablePrefix + "_sell_prices " +
                        "   UNION " +
                        "   SELECT DISTINCT shopID, itemID FROM " + tablePrefix + "_stock" +
                        ") as all_items " +
                        "LEFT JOIN " + tablePrefix + "_stock s ON all_items.shopID = s.shopID AND all_items.itemID = s.itemID " +
                        "LEFT JOIN " + tablePrefix + "_buy_prices b ON all_items.shopID = b.shopID AND all_items.itemID = b.itemID " +
                        "LEFT JOIN " + tablePrefix + "_sell_prices sell ON all_items.shopID = sell.shopID AND all_items.itemID = sell.itemID";
            }
            
            executeUpdate(viewSQL);
        } catch (Exception e) {
            // La vue peut échouer avec SQLite, ce n'est pas critique
            plugin.getLogger().warning("Note: Impossible de créer la vue des prix : " + e.getMessage());
        }
    }

    /**
     * Obtient une connexion à partir du pool de connexions.
     */
    public synchronized Connection getConnection() throws SQLException {
        if (dataConfig.getDatabaseType().equals("sqlite")) {
            File databaseFile = new File(plugin.getDataFolder(), dataConfig.getDatabaseSqliteFile());
            String url = "jdbc:sqlite:" + databaseFile.getAbsolutePath();
            return DriverManager.getConnection(url); // Nouvelle connexion à chaque fois
        } else {
            if (!isInitialized || dataSource == null || dataSource.isClosed()) {
                initDatabase();
            }
            return dataSource.getConnection();
        }
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
        try {
            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
                dataSource = null;
            }
            isInitialized = false;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to close database connection: " + e.getMessage());
            e.printStackTrace();
        }
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

    // public void createItem(String shopID, String itemID) {
    //     String sql = "INSERT INTO " + dataConfig.getDatabaseTablePrefix() + "_prices (shopID, itemID, buyPrice, sellPrice, stock) " +
    //                  "VALUES (?, ?, 0, 0, 0) ON DUPLICATE KEY UPDATE stock = stock";
        
    //     executeUpdate(sql, shopID, itemID);
    // }
    // public void createItem(String shopID, String itemID) {
    //     String tablePrefix = dataConfig.getDatabaseTablePrefix();

    //     String sql = "INSERT INTO " + tablePrefix + "_stock (shopID, itemID, stock) " +
    //                  "VALUES (?, ?, 0) ON DUPLICATE KEY UPDATE stock = stock";
    //     executeUpdate(sql, shopID, itemID);

    //     sql = "INSERT INTO " + tablePrefix + "_buy_prices (shopID, itemID, price) " +
    //           "VALUES (?, ?, 0) ON DUPLICATE KEY UPDATE price = price";
    //     executeUpdate(sql, shopID, itemID);

    //     sql = "INSERT INTO " + tablePrefix + "_sell_prices (shopID, itemID, price) " +
    //           "VALUES (?, ?, 0) ON DUPLICATE KEY UPDATE price = price";
    //     executeUpdate(sql, shopID, itemID);

    //     // plugin.getLogger().info("Item created: " + shopID + ":" + itemID);
    // }

    /**
     * Sauvegarde le prix d'un item dans la base de données.
     */
    // public void savePrice(String shopId, String itemId, double buyPrice, double sellPrice) {
    //     String sql = "REPLACE INTO " + dataConfig.getDatabaseTablePrefix() + 
    //                  "_prices (shopID, itemID, buyPrice, sellPrice, stock) " +
    //                  "VALUES (?, ?, ?, ?, (SELECT COALESCE(stock, 0) FROM " + 
    //                  dataConfig.getDatabaseTablePrefix() + "_prices WHERE shopID = ? AND itemID = ?))";
        
    //     executeUpdate(sql, shopId, itemId, buyPrice, sellPrice, shopId, itemId);
    // }
    // public void savePrice(String shopId, String itemId, double buyPrice, double sellPrice) {
    //     String sql = "INSERT INTO " + dataConfig.getDatabaseTablePrefix() + 
    //                 "_prices (shopID, itemID, buyPrice, sellPrice, stock) " +
    //                 "VALUES (?, ?, ?, ?, 0) " +
    //                 "ON DUPLICATE KEY UPDATE buyPrice = VALUES(buyPrice), sellPrice = VALUES(sellPrice)";
        
    //     executeUpdate(sql, shopId, itemId, buyPrice, sellPrice);
    // }
    // public void savePrice(String shopId, String itemId, double buyPrice, double sellPrice) {
    //     // D'abord récupérer la valeur actuelle du stock
    //     Optional<Integer> stockOpt = getStock(shopId, itemId);
    //     int stock = stockOpt.orElse(0);
        
    //     // Ensuite utiliser une simple requête REPLACE INTO
    //     String sql = "REPLACE INTO " + dataConfig.getDatabaseTablePrefix() + 
    //                 "_prices (shopID, itemID, buyPrice, sellPrice, stock) " +
    //                 "VALUES (?, ?, ?, ?, ?)";
        
    //     executeUpdate(sql, shopId, itemId, buyPrice, sellPrice, stock);
    // }

    /**
     * Sauvegarde le prix et le stock d'un item dans la base de données.
     */
    // public void savePrice(String shopId, String itemId, double buyPrice, double sellPrice, int stock) {
    //     String sql = "REPLACE INTO " + dataConfig.getDatabaseTablePrefix() + 
    //                  "_prices (shopID, itemID, buyPrice, sellPrice, stock) VALUES (?, ?, ?, ?, ?)";
        
    //     executeUpdate(sql, shopId, itemId, buyPrice, sellPrice, stock);
    // }
    public void savePrice(String shopId, String itemId, double buyPrice, double sellPrice, int stock) {
        String tablePrefix = dataConfig.getDatabaseTablePrefix();
        
        // Toujours sauvegarder le stock
        String stockSQL = "REPLACE INTO " + tablePrefix + "_stock (shopID, itemID, stock) VALUES (?, ?, ?)";
        executeUpdate(stockSQL, shopId, itemId, stock);
        
        // Sauvegarder buyPrice uniquement s'il est positif
        if (buyPrice >= 0) {
            String buySQL = "REPLACE INTO " + tablePrefix + "_buy_prices (shopID, itemID, price) VALUES (?, ?, ?)";
            executeUpdate(buySQL, shopId, itemId, buyPrice);
        } else {
            // Supprimer l'entrée si elle existe
            String deleteBuySQL = "DELETE FROM " + tablePrefix + "_buy_prices WHERE shopID = ? AND itemID = ?";
            executeUpdate(deleteBuySQL, shopId, itemId);
        }
        
        // Sauvegarder sellPrice uniquement s'il est positif
        if (sellPrice >= 0) {
            String sellSQL = "REPLACE INTO " + tablePrefix + "_sell_prices (shopID, itemID, price) VALUES (?, ?, ?)";
            executeUpdate(sellSQL, shopId, itemId, sellPrice);
        } else {
            // Supprimer l'entrée si elle existe
            String deleteSellSQL = "DELETE FROM " + tablePrefix + "_sell_prices WHERE shopID = ? AND itemID = ?";
            executeUpdate(deleteSellSQL, shopId, itemId);
        }
    }

    /**
     * Met à jour le stock d'un item.
     */
    // public void setStock(String shopId, String itemId, int stock) {
    //     String sql = "UPDATE " + dataConfig.getDatabaseTablePrefix() + "_prices SET stock = ? WHERE shopID = ? AND itemID = ?";
        
    //     int rowsAffected = executeUpdate(sql, stock, shopId, itemId);
        
    //     // Si aucune ligne n'a été affectée, l'item n'existe pas encore
    //     if (rowsAffected == 0) {
    //         sql = "INSERT INTO " + dataConfig.getDatabaseTablePrefix() + "_prices (shopID, itemID, buyPrice, sellPrice, stock) VALUES (?, ?, 0, 0, ?)";
    //         executeUpdate(sql, shopId, itemId, stock);
    //     }
    // }
    public void insertStock(String shopId, String itemId, int stock) {
        String tablePrefix = dataConfig.getDatabaseTablePrefix();
        
        // Mettre à jour le stock
        String sql = "REPLACE INTO " + tablePrefix + "_stock (shopID, itemID, stock) VALUES (?, ?, ?)";
        executeUpdate(sql, shopId, itemId, stock);
    }

    public void insertBuyPrice(String shopId, String itemId, double buyPrice) {
        String tablePrefix = dataConfig.getDatabaseTablePrefix();
        
        // Mettre à jour le prix d'achat
        String sql = "REPLACE INTO " + tablePrefix + "_buy_prices (shopID, itemID, price) VALUES (?, ?, ?)";
        executeUpdate(sql, shopId, itemId, buyPrice);
    }

    public void insertSellPrice(String shopId, String itemId, double sellPrice) {
        String tablePrefix = dataConfig.getDatabaseTablePrefix();
        
        // Mettre à jour le prix de vente
        String sql = "REPLACE INTO " + tablePrefix + "_sell_prices (shopID, itemID, price) VALUES (?, ?, ?)";
        executeUpdate(sql, shopId, itemId, sellPrice);
    }

    // /**
    //  * Récupère le prix d'un item.
    //  */
    // public Optional<Double> getPrice(String shopId, String itemId, String priceType) {
    //     String column = priceType.equals("buyPrice") ? "buyPrice" : "sellPrice";
    //     String sql = "SELECT " + column + " FROM " + dataConfig.getDatabaseTablePrefix() + "_prices WHERE shopID = ? AND itemID = ?";
        
    //     return executeQuery(sql, rs -> {
    //         if (rs.next()) {
    //             return rs.getDouble(column);
    //         }
    //         return null;
    //     }, shopId, itemId);
    // }
    public Optional<Double> getPrice(String shopId, String itemId, String priceType) {
        String tablePrefix = dataConfig.getDatabaseTablePrefix();
        // String column = priceType.equals("buyPrice") ? "price" : "price";
        String column = "price";
        String tableName = priceType.equals("buyPrice") ? tablePrefix + "_buy_prices" : tablePrefix + "_sell_prices";
        
        String sql = "SELECT " + column + " FROM " + tableName + " WHERE shopID = ? AND itemID = ?";
        
        return executeQuery(sql, rs -> {
            if (rs.next()) {
                return rs.getDouble(column);
            }
            return -1.0; // Valeur par défaut si non trouvé
        }, shopId, itemId).filter(price -> price >= 0);
    }

    // /**
    //  * Récupère le prix d'achat d'un item.
    //  */
    // public Optional<Double> getBuyPrice(String shopId, String itemId) {
    //     return getPrice(shopId, itemId, "buyPrice");
    // }

    // /**
    //  * Récupère le prix de vente d'un item.
    //  */
    // public Optional<Double> getSellPrice(String shopId, String itemId) {
    //     return getPrice(shopId, itemId, "sellPrice");
    // }

    // /**
    //  * Récupère le stock d'un item.
    //  */
    // public Optional<Integer> getStock(String shopId, String itemId) {
    //     String sql = "SELECT stock FROM " + dataConfig.getDatabaseTablePrefix() + "_prices WHERE shopID = ? AND itemID = ?";

    //     return executeQuery(sql, rs -> {
    //         if (rs.next()) {
    //             return rs.getInt("stock");
    //         }
    //         return null;
    //     }, shopId, itemId);
    // }

    public Optional<Double> getBuyPrice(String shopId, String itemId) {
        String tablePrefix = dataConfig.getDatabaseTablePrefix();
        String sql = "SELECT price FROM " + tablePrefix + "_buy_prices WHERE shopID = ? AND itemID = ?";
        
        return executeQuery(sql, rs -> {
            if (rs.next()) {
                return rs.getDouble("price");
            }
            return -1.0; // Valeur par défaut si non trouvé
        }, shopId, itemId).filter(price -> price >= 0);
    }

    public Optional<Double> getSellPrice(String shopId, String itemId) {
        String tablePrefix = dataConfig.getDatabaseTablePrefix();
        String sql = "SELECT price FROM " + tablePrefix + "_sell_prices WHERE shopID = ? AND itemID = ?";
        
        return executeQuery(sql, rs -> {
            if (rs.next()) {
                return rs.getDouble("price");
            }
            return -1.0; // Valeur par défaut si non trouvé
        }, shopId, itemId).filter(price -> price >= 0);
    }

    // public Optional<Integer> getStock(String shopId, String itemId) {
    //     String tablePrefix = dataConfig.getDatabaseTablePrefix();
    //     String sql = "SELECT stock FROM " + tablePrefix + "_stock WHERE shopID = ? AND itemID = ?";
        
    //     return executeQuery(sql, rs -> {
    //         if (rs.next()) {
    //             return rs.getInt("stock");
    //         }
    //         return 0; // Valeur par défaut si non trouvé
    //     }, shopId, itemId);
    // }
    public Optional<Integer> getStock(String shopId, String itemId) {
        String tablePrefix = dataConfig.getDatabaseTablePrefix();
        String sql = "SELECT stock FROM " + tablePrefix + "_stock WHERE shopID = ? AND itemID = ?";
        
        return executeQuery(sql, rs -> {
            if (rs.next()) {
                int stock = rs.getInt("stock");
                // Si le stock est -1, c'est que la fonctionnalité est désactivée
                // On retourne un Optional vide pour indiquer que l'item n'a pas de stock configuré
                return stock >= 0 ? stock : null;
            }
            return null; // Retourner null pour créer un Optional vide
        }, shopId, itemId);
    }

    /**
     * Récupère les prix d'un item.
     */
    // public Optional<DynamicPrice> getPrices(String shopId, String itemId) {
    //     String sql = "SELECT buyPrice, sellPrice, stock FROM " + dataConfig.getDatabaseTablePrefix() + "_prices WHERE shopID = ? AND itemID = ?";
        
    //     return executeQuery(sql, rs -> {
    //         if (rs.next()) {
    //             double buyPrice = rs.getDouble("buyPrice");
    //             double sellPrice = rs.getDouble("sellPrice");
    //             int stock = rs.getInt("stock");
    //             return new DynamicPrice(buyPrice, sellPrice, stock);
    //         }
    //         return null;
    //     }, shopId, itemId);
    // }
    // public Optional<DynamicPrice> getPrices(String shopId, String itemId) {
    //     String tablePrefix = dataConfig.getDatabaseTablePrefix();
        
    //     // Lecture des prix depuis les différentes tables
    //     Optional<Double> buyPrice = executeQuery(
    //         "SELECT price FROM " + tablePrefix + "_buy_prices WHERE shopID = ? AND itemID = ?", 
    //         rs -> rs.next() ? rs.getDouble("price") : null, 
    //         shopId, itemId
    //     );
        
    //     Optional<Double> sellPrice = executeQuery(
    //         "SELECT price FROM " + tablePrefix + "_sell_prices WHERE shopID = ? AND itemID = ?", 
    //         rs -> rs.next() ? rs.getDouble("price") : null, 
    //         shopId, itemId
    //     );
        
    //     Optional<Integer> stock = executeQuery(
    //         "SELECT stock FROM " + tablePrefix + "_stock WHERE shopID = ? AND itemID = ?", 
    //         rs -> rs.next() ? rs.getInt("stock") : null, 
    //         shopId, itemId
    //     );
        
    //     // Si aucune des tables n'a d'entrée pour cet item, retourner empty
    //     if (buyPrice.isEmpty() && sellPrice.isEmpty() && stock.isEmpty()) {
    //         return Optional.empty();
    //     }
        
    //     // Créer l'objet DynamicPrice avec les valeurs par défaut (-1) pour les prix non trouvés
    //     return Optional.of(new DynamicPrice(
    //         buyPrice.orElse(-1.0), 
    //         sellPrice.orElse(-1.0), 
    //         stock.orElse(0)
    //     ));
    // }

    public Optional<DynamicPrice> getPrices(String shopId, String itemId) {
        String tablePrefix = dataConfig.getDatabaseTablePrefix();
        
        // Essayer d'utiliser la vue d'abord
        try {
            String viewQuery = "SELECT buyPrice, sellPrice, stock FROM " + tablePrefix + "_items WHERE shopID = ? AND itemID = ?";
            
            return executeQuery(viewQuery, rs -> {
                if (rs.next()) {
                    double buyPrice = rs.getDouble("buyPrice");
                    double sellPrice = rs.getDouble("sellPrice");
                    int stock = rs.getInt("stock");
                    
                    // Si aucun prix n'est défini, ne pas créer d'objet
                    // if (buyPrice < 0 && sellPrice < 0 && stock == 0) {
                    if (buyPrice <= 0 && sellPrice <= 0 && stock < 0) {
                        return null;
                    }
                    
                    return new DynamicPrice(buyPrice, sellPrice, stock);
                }
                return null;
            }, shopId, itemId);
        } catch (Exception e) {
            // // Fallback en cas d'échec de la vue
            // Optional<Double> buyPrice = getBuyPrice(shopId, itemId);
            // Optional<Double> sellPrice = getSellPrice(shopId, itemId);
            // Optional<Integer> stock = getStock(shopId, itemId);

            // if (!buyPrice.isPresent() && !sellPrice.isPresent() && !stock.isPresent()) {
            //     return Optional.empty();
            // }
            
            // return Optional.of(new DynamicPrice(
            //     buyPrice.orElse(-1.0),
            //     sellPrice.orElse(-1.0),
            //     stock.orElse(-1)
            // ));
            // Fallback en cas d'échec de la vue - requête directe sur les tables
            String fallbackQuery = "SELECT " +
                "(SELECT price FROM " + tablePrefix + "_buy_prices WHERE shopID = ? AND itemID = ? LIMIT 1) AS buyPrice, " +
                "(SELECT price FROM " + tablePrefix + "_sell_prices WHERE shopID = ? AND itemID = ? LIMIT 1) AS sellPrice, " +
                "(SELECT stock FROM " + tablePrefix + "_stock WHERE shopID = ? AND itemID = ? LIMIT 1) AS stock";
            
            return executeQuery(fallbackQuery, rs -> {
                if (rs.next()) {
                    double buyPrice = rs.getDouble("buyPrice");
                    if (rs.wasNull()) buyPrice = -1.0;
                    
                    double sellPrice = rs.getDouble("sellPrice");
                    if (rs.wasNull()) sellPrice = -1.0;
                    
                    int stock = rs.getInt("stock");
                    if (rs.wasNull()) stock = -1;
                    
                    // Si aucun prix n'est défini, ne pas créer d'objet
                    if (buyPrice <= 0 && sellPrice <= 0 && stock < 0) {
                        return null;
                    }
                    
                    return new DynamicPrice(buyPrice, sellPrice, stock);
                }
                return null;
            }, shopId, itemId, shopId, itemId, shopId, itemId);
        }
    }

    public void deleteStock(String shopId, String itemId) {
        String tablePrefix = dataConfig.getDatabaseTablePrefix();
        
        // Supprimer le stock
        String sql = "DELETE FROM " + tablePrefix + "_stock WHERE shopID = ? AND itemID = ?";
        executeUpdate(sql, shopId, itemId);
    }

    public void deleteBuyPrice(String shopId, String itemId) {
        String tablePrefix = dataConfig.getDatabaseTablePrefix();
        
        // Supprimer le prix d'achat
        String sql = "DELETE FROM " + tablePrefix + "_buy_prices WHERE shopID = ? AND itemID = ?";
        executeUpdate(sql, shopId, itemId);
    }

    public void deleteSellPrice(String shopId, String itemId) {
        String tablePrefix = dataConfig.getDatabaseTablePrefix();
        
        // Supprimer le prix de vente
        String sql = "DELETE FROM " + tablePrefix + "_sell_prices WHERE shopID = ? AND itemID = ?";
        executeUpdate(sql, shopId, itemId);
    }

    public void deleteItem(String shopId, String itemId) {
        // String tablePrefix = dataConfig.getDatabaseTablePrefix();
        
        // Supprimer le stock
        deleteStock(shopId, itemId);
        
        // Supprimer le prix d'achat
        deleteBuyPrice(shopId, itemId);
        
        // Supprimer le prix de vente
        deleteSellPrice(shopId, itemId);
    }

    /**
     * Nettoie la table de stock en supprimant les entrées pour les items qui ne sont pas en mode STOCK ou STATIC_STOCK
     */
    public void cleanupStockTable() {
        plugin.getLogger().info("Nettoyage de la table de stock...");
        
        try {
            // Récupérer tous les items dans la table de stock
            String tablePrefix = dataConfig.getDatabaseTablePrefix();
            String sql = "SELECT shopID, itemID FROM " + tablePrefix + "_stock";
            
            executeQuery(sql, rs -> {
                // int itemsRemoved = 0;
                
                while (rs.next()) {
                    String shopId = rs.getString("shopID");
                    String itemId = rs.getString("itemID");
                    
                    // Vérifier le type de l'item
                    DynaShopType typeDynaShop = plugin.getShopConfigManager().getTypeDynaShop(shopId, itemId);
                    
                    // Si l'item n'est pas en mode STOCK ou STATIC_STOCK, supprimer son entrée
                    if (typeDynaShop != DynaShopType.STOCK && typeDynaShop != DynaShopType.STATIC_STOCK) {
                        deleteStock(shopId, itemId);
                        // itemsRemoved++;
                    }
                }
                
                // plugin.getLogger().info("Nettoyage terminé: " + itemsRemoved + " entrées supprimées.");
                return null;
            });
        } catch (Exception e) {
            plugin.getLogger().severe("Erreur lors du nettoyage de la table de stock: " + e.getMessage());
        }
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
    // public Map<ShopItem, DynamicPrice> loadPricesFromDatabase() {
    //     Map<ShopItem, DynamicPrice> priceMap = new HashMap<>();
    //     String sql = "SELECT shopID, itemID, buyPrice, sellPrice, stock FROM " + dataConfig.getDatabaseTablePrefix() + "_prices";
        
    //     // Log début de l'opération
    //     // plugin.getLogger().warning("Chargement des prix depuis la base de données...");
        
    //     try {
    //         int[] rowCount = {0}; // Utiliser un tableau pour pouvoir modifier la valeur dans le lambda
            
    //         executeQuery(sql, rs -> {
    //             while (rs.next()) {
    //                 rowCount[0]++;
    //                 String shopId = rs.getString("shopID");
    //                 String itemId = rs.getString("itemID");
    //                 double buyPrice = rs.getDouble("buyPrice");
    //                 double sellPrice = rs.getDouble("sellPrice");
    //                 int stock = rs.getInt("stock");
                    
    //                 // // Log pour vérifier si nous récupérons bien des données
    //                 // if (rowCount[0] <= 5 || rowCount[0] % 100 == 0) {
    //                 //     // plugin.getLogger().info("Trouvé en base: " + shopId + ":" + itemId + " - Buy: " + buyPrice + ", Sell: " + sellPrice + ", Stock: " + stock);
    //                 // }
                    
    //                 try {
    //                     // Vérifier si ShopGuiPlusApi est initialisé
    //                     if (ShopGuiPlusApi.getPlugin() == null) {
    //                         // plugin.getLogger().severe("ShopGuiPlusApi.getPlugin() est null! L'API n'est pas initialisée.");
    //                         continue;
    //                     }
                        
    //                     Shop shop = ShopGuiPlusApi.getPlugin().getShopManager().getShopById(shopId);
    //                     if (shop == null) {
    //                         // plugin.getLogger().warning("Shop introuvable: " + shopId);
    //                         continue;
    //                     }
                        
    //                     ShopItem item = shop.getShopItems().stream()
    //                         .filter(i -> i.getId().equals(itemId))
    //                         .findFirst()
    //                         .orElse(null);
                        
    //                     if (item == null) {
    //                         // plugin.getLogger().warning("Item introuvable: " + itemId + " dans shop: " + shopId);
    //                         continue;
    //                     }
                        
    //                     DynamicPrice price = new DynamicPrice(buyPrice, sellPrice, stock);
    //                     priceMap.put(item, price);
    //                 } catch (Exception e) {
    //                     // plugin.getLogger().severe("Erreur lors du traitement de " + shopId + ":" + itemId + ": " + e.getMessage());
    //                 }
    //             }
                
    //             // plugin.getLogger().warning("Lecture terminée. Trouvé " + rowCount[0] + " enregistrements en base, " + priceMap.size() + " prix chargés avec succès.");
    //             return null;
    //         });
    //     } catch (Exception e) {
    //         // plugin.getLogger().severe("Erreur critique lors du chargement des prix: " + e.getMessage());
    //         e.printStackTrace();
    //     }
        
    //     // Logs finaux
    //     // plugin.getLogger().warning("Nombre final d'items chargés: " + priceMap.size());
    //     return priceMap;
    // }
    public Map<ShopItem, DynamicPrice> loadPricesFromDatabase() {
        Map<ShopItem, DynamicPrice> priceMap = new HashMap<>();
        String tablePrefix = dataConfig.getDatabaseTablePrefix();
        // String sql = "SELECT st.shopID, st.itemID, " +
        //             "COALESCE(b.price, -1) AS buyPrice, " +
        //             "COALESCE(sell.price, -1) AS sellPrice, " +
        //             "COALESCE(st.stock, 0) AS stock " +
        //             "FROM " + tablePrefix + "_stock st " +
        //             "LEFT JOIN " + tablePrefix + "_buy_prices b ON st.shopID = b.shopID AND st.itemID = b.itemID " +
        //             "LEFT JOIN " + tablePrefix + "_sell_prices sell ON st.shopID = sell.shopID AND st.itemID = sell.itemID";
        String sql = "SELECT shopID, itemID, buyPrice, sellPrice, stock FROM " + tablePrefix + "_items";

        executeQuery(sql, rs -> {
            while (rs.next()) {
                String shopId = rs.getString("shopID");
                String itemId = rs.getString("itemID");
                double buyPrice = rs.getDouble("buyPrice");
                double sellPrice = rs.getDouble("sellPrice");
                int stock = rs.getInt("stock");

                Shop shop = ShopGuiPlusApi.getPlugin().getShopManager().getShopById(shopId);
                if (shop != null) {
                    ShopItem item = shop.getShopItems().stream()
                        .filter(i -> i.getId().equals(itemId))
                        .findFirst()
                        .orElse(null);

                    if (item != null) {
                        DynamicPrice price = new DynamicPrice(buyPrice, sellPrice, stock);
                        priceMap.put(item, price);
                    }
                }
            }
            return null;
        });

        return priceMap;
    }

    // /**
    //  * Exécute une opération de base de données de manière asynchrone.
    //  */
    // public <T> CompletableFuture<T> executeAsync(DatabaseOperation<T> operation) {
    //     CompletableFuture<T> future = new CompletableFuture<>();
        
    //     Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
    //         try {
    //             T result = operation.execute();
    //             future.complete(result);
    //         } catch (Exception e) {
    //             plugin.getLogger().log(Level.SEVERE, "Error during asynchronous execution", e);
    //             future.completeExceptionally(e);
    //         }
    //     });
        
    //     return future;
    // }
    public <T> CompletableFuture<T> executeAsync(DatabaseOperation<T> operation) {
        // Si c'est SQLite, utiliser un seul thread pour éviter les verrous
        if (dataConfig.getDatabaseType().equals("sqlite")) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return operation.execute();
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "Error executing database operation", e);
                    throw new RuntimeException(e);
                }
            }, databaseExecutor);
        } else {
            // Pour MySQL, on peut continuer à utiliser le thread pool par défaut
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return operation.execute();
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "Error executing database operation", e);
                    throw new RuntimeException(e);
                }
            });
        }
    }

    /**
     * Ferme toutes les connexions.
     */
    public void closeDatabase() {
        closeDataSource();
        
        if (databaseExecutor != null && !databaseExecutor.isShutdown()) {
            databaseExecutor.shutdown();
            try {
                if (!databaseExecutor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    databaseExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                databaseExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
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

    private void migrateFromOldSchema() {
        String tablePrefix = dataConfig.getDatabaseTablePrefix();
        
        // Vérifier si l'ancienne table existe
        boolean oldTableExists = executeQuery(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = ?",
            rs -> rs.next() && rs.getInt(1) > 0,
            tablePrefix + "_prices"
        ).orElse(false);
        
        if (oldTableExists) {
            // Migrer les prix d'achat positifs
            executeUpdate(
                "INSERT INTO " + tablePrefix + "_buy_prices (shopID, itemID, price) " +
                "SELECT shopID, itemID, buyPrice FROM " + tablePrefix + "_prices " +
                "WHERE buyPrice >= 0"
            );
            
            // Migrer les prix de vente positifs
            executeUpdate(
                "INSERT INTO " + tablePrefix + "_sell_prices (shopID, itemID, price) " +
                "SELECT shopID, itemID, sellPrice FROM " + tablePrefix + "_prices " +
                "WHERE sellPrice >= 0"
            );
            
            // Migrer les stocks
            executeUpdate(
                "INSERT INTO " + tablePrefix + "_stock (shopID, itemID, stock) " +
                "SELECT shopID, itemID, stock FROM " + tablePrefix + "_prices"
            );
            
            // Renommer l'ancienne table pour la conserver en backup
            executeUpdate("RENAME TABLE " + tablePrefix + "_prices TO " + tablePrefix + "_prices_old");

            // On supprime l'ancienne table de prix
            executeUpdate("DROP TABLE " + tablePrefix + "_prices_old");
            // Log de succès
            plugin.getLogger().info("Migration des données de prix vers le nouveau schéma réussie!");
            
            // plugin.getLogger().info("Migration des données de prix vers le nouveau schéma réussie!");
        }
    }

    public double getInflationFactor() {
        try (Connection conn = getConnection();
            PreparedStatement stmt = conn.prepareStatement("SELECT value FROM " + dataConfig.getDatabaseTablePrefix() + "_metadata WHERE meta_key = 'inflation_factor'")) {

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getDouble("value");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error loading inflation factor: " + e.getMessage());
        }
        return 1.0; // Valeur par défaut
    }

    public long getLastInflationUpdate() {
        try (Connection conn = getConnection();
            PreparedStatement stmt = conn.prepareStatement("SELECT value FROM " + dataConfig.getDatabaseTablePrefix() + "_metadata WHERE meta_key = 'last_inflation_update'")) {
            
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return Long.parseLong(rs.getString("value"));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error loading last inflation update: " + e.getMessage());
        }
        return 0L; // Valeur par défaut
    }

    public void saveInflationData(double factor, long timestamp) {
        try (Connection conn = getConnection()) {
            // S'assurer que la table metadata existe
            PreparedStatement createStmt = conn.prepareStatement(
                "CREATE TABLE IF NOT EXISTS " + dataConfig.getDatabaseTablePrefix() + "_metadata (" +
                "meta_key VARCHAR(50) PRIMARY KEY, " +
                "value TEXT NOT NULL)"
            );
            createStmt.executeUpdate();
            
            boolean isMysql = dataConfig.getDatabaseType().equals("mysql");
            String tablePrefix = dataConfig.getDatabaseTablePrefix();
            
            // Sauvegarder le facteur d'inflation avec syntaxe adaptée au type de base de données
            if (isMysql) {
                // Syntaxe MySQL
                PreparedStatement factorStmt = conn.prepareStatement(
                    "INSERT INTO " + tablePrefix + "_metadata (meta_key, value) VALUES ('inflation_factor', ?) " +
                    "ON DUPLICATE KEY UPDATE value = ?"
                );
                factorStmt.setString(1, String.valueOf(factor));
                factorStmt.setString(2, String.valueOf(factor));
                factorStmt.executeUpdate();
                
                // Sauvegarder le timestamp du dernier update
                PreparedStatement timeStmt = conn.prepareStatement(
                    "INSERT INTO " + tablePrefix + "_metadata (meta_key, value) VALUES ('last_inflation_update', ?) " +
                    "ON DUPLICATE KEY UPDATE value = ?"
                );
                timeStmt.setString(1, String.valueOf(timestamp));
                timeStmt.setString(2, String.valueOf(timestamp));
                timeStmt.executeUpdate();
            } else {
                // Syntaxe SQLite - utiliser INSERT OR REPLACE
                PreparedStatement factorStmt = conn.prepareStatement(
                    "INSERT OR REPLACE INTO " + tablePrefix + "_metadata (meta_key, value) VALUES ('inflation_factor', ?)"
                );
                factorStmt.setString(1, String.valueOf(factor));
                factorStmt.executeUpdate();
                
                // Sauvegarder le timestamp du dernier update
                PreparedStatement timeStmt = conn.prepareStatement(
                    "INSERT OR REPLACE INTO " + tablePrefix + "_metadata (meta_key, value) VALUES ('last_inflation_update', ?)"
                );
                timeStmt.setString(1, String.valueOf(timestamp));
                timeStmt.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error saving inflation data: " + e.getMessage());
        }
    }

    
    // /**
    //  * Sauvegarde l'historique des prix d'un item
    //  */
    // public void savePriceHistory(PriceHistory history) {
    //     try (Connection conn = getConnection()) {
    //         // S'assurer que la table existe
    //         PreparedStatement createTable = conn.prepareStatement(
    //             "CREATE TABLE IF NOT EXISTS " + dataConfig.getDatabaseTablePrefix() + "_price_history (" +
    //             "id INT AUTO_INCREMENT PRIMARY KEY, " +
    //             "shop_id VARCHAR(100) NOT NULL, " +
    //             "item_id VARCHAR(100) NOT NULL, " +
    //             "timestamp TIMESTAMP NOT NULL, " +
    //             "open_price DOUBLE NOT NULL, " +
    //             "close_price DOUBLE NOT NULL, " +
    //             "high_price DOUBLE NOT NULL, " +
    //             "low_price DOUBLE NOT NULL, " +
    //             "INDEX (shop_id, item_id))"
    //         );
    //         createTable.executeUpdate();
            
    //         // Insertion des points de données
    //         PreparedStatement insertStmt = conn.prepareStatement(
    //             "INSERT INTO " + dataConfig.getDatabaseTablePrefix() + "_price_history " +
    //             "(shop_id, item_id, timestamp, open_price, close_price, high_price, low_price) " +
    //             "VALUES (?, ?, ?, ?, ?, ?, ?)"
    //         );
            
    //         for (PriceDataPoint point : history.getDataPoints()) {
    //             insertStmt.setString(1, history.getShopId());
    //             insertStmt.setString(2, history.getItemId());
    //             insertStmt.setTimestamp(3, Timestamp.valueOf(point.getTimestamp()));
    //             insertStmt.setDouble(4, point.getOpenPrice());
    //             insertStmt.setDouble(5, point.getClosePrice());
    //             insertStmt.setDouble(6, point.getHighPrice());
    //             insertStmt.setDouble(7, point.getLowPrice());
    //             insertStmt.addBatch();
    //         }
            
    //         insertStmt.executeBatch();
            
    //     } catch (SQLException e) {
    //         plugin.getLogger().severe("Erreur lors de la sauvegarde de l'historique des prix: " + e.getMessage());
    //     }
    // }

    // /**
    //  * Récupère l'historique des prix d'un item
    //  */
    // public PriceHistory getPriceHistory(String shopId, String itemId) {
    //     PriceHistory history = new PriceHistory(shopId, itemId);
        
    //     try (Connection conn = getConnection()) {
    //         // // S'assurer que la table existe AVANT la lecture
    //         // PreparedStatement createTable = conn.prepareStatement(
    //         //     "CREATE TABLE IF NOT EXISTS " + dataConfig.getDatabaseTablePrefix() + "_price_history (" +
    //         //     "id INT AUTO_INCREMENT PRIMARY KEY, " +
    //         //     "shop_id VARCHAR(100) NOT NULL, " +
    //         //     "item_id VARCHAR(100) NOT NULL, " +
    //         //     "timestamp TIMESTAMP NOT NULL, " +
    //         //     "open_price DOUBLE NOT NULL, " +
    //         //     "close_price DOUBLE NOT NULL, " +
    //         //     "high_price DOUBLE NOT NULL, " +
    //         //     "low_price DOUBLE NOT NULL, " +
    //         //     "INDEX (shop_id, item_id))"
    //         // );
    //         // createTable.executeUpdate();

    //         PreparedStatement stmt = conn.prepareStatement(
    //             "SELECT timestamp, open_price, close_price, high_price, low_price " +
    //             "FROM " + dataConfig.getDatabaseTablePrefix() + "_price_history " +
    //             "WHERE shop_id = ? AND item_id = ? " +
    //             "ORDER BY timestamp ASC"
    //         );
            
    //         stmt.setString(1, shopId);
    //         stmt.setString(2, itemId);
            
    //         ResultSet rs = stmt.executeQuery();
    //         while (rs.next()) {
    //             LocalDateTime timestamp = rs.getTimestamp("timestamp").toLocalDateTime();
    //             double openPrice = rs.getDouble("open_price");
    //             double closePrice = rs.getDouble("close_price");
    //             double highPrice = rs.getDouble("high_price");
    //             double lowPrice = rs.getDouble("low_price");
                
    //             PriceDataPoint point = new PriceDataPoint(timestamp, openPrice, closePrice, highPrice, lowPrice);
    //             history.getDataPoints().add(point);
    //         }
            
    //     } catch (SQLException e) {
    //         plugin.getLogger().severe("Erreur lors de la récupération de l'historique des prix: " + e.getMessage());
    //     }
        
    //     return history;
    // }

    /**
     * Récupère l'historique des prix d'un item
     */
    public PriceHistory getPriceHistory(String shopId, String itemId) {
        PriceHistory history = new PriceHistory(shopId, itemId);
        String createTableSQL;
        
        if (dataConfig.getDatabaseType().equals("mysql")) {
            createTableSQL = 
                "CREATE TABLE IF NOT EXISTS " + dataConfig.getDatabaseTablePrefix() + "_price_history (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "shop_id VARCHAR(100) NOT NULL, " +
                "item_id VARCHAR(100) NOT NULL, " +
                "timestamp TIMESTAMP NOT NULL, " +
                "open_buy_price DOUBLE NOT NULL DEFAULT 0, " +
                "close_buy_price DOUBLE NOT NULL DEFAULT 0, " +
                "high_buy_price DOUBLE NOT NULL DEFAULT 0, " +
                "low_buy_price DOUBLE NOT NULL DEFAULT 0, " +
                "open_sell_price DOUBLE NOT NULL DEFAULT 0, " +
                "close_sell_price DOUBLE NOT NULL DEFAULT 0, " +
                "high_sell_price DOUBLE NOT NULL DEFAULT 0, " +
                "low_sell_price DOUBLE NOT NULL DEFAULT 0, " +
                "volume DOUBLE NOT NULL DEFAULT 0, " +
                "INDEX (shop_id, item_id))";
        } else {
            // Syntaxe SQLite (pas de INDEX inline)
            createTableSQL = 
                "CREATE TABLE IF NOT EXISTS " + dataConfig.getDatabaseTablePrefix() + "_price_history (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "shop_id VARCHAR(100) NOT NULL, " +
                "item_id VARCHAR(100) NOT NULL, " +
                "timestamp TIMESTAMP NOT NULL, " +
                "open_buy_price DOUBLE NOT NULL DEFAULT 0, " +
                "close_buy_price DOUBLE NOT NULL DEFAULT 0, " +
                "high_buy_price DOUBLE NOT NULL DEFAULT 0, " +
                "low_buy_price DOUBLE NOT NULL DEFAULT 0, " +
                "open_sell_price DOUBLE NOT NULL DEFAULT 0, " +
                "close_sell_price DOUBLE NOT NULL DEFAULT 0, " +
                "high_sell_price DOUBLE NOT NULL DEFAULT 0, " +
                "low_sell_price DOUBLE NOT NULL DEFAULT 0, " +
                "volume DOUBLE NOT NULL DEFAULT 0)";
        }
        
        try (Connection conn = getConnection()) {
            PreparedStatement createTable = conn.prepareStatement(createTableSQL);
            createTable.executeUpdate();
            
            // Pour SQLite, créer l'index séparément
            if (!dataConfig.getDatabaseType().equals("mysql")) {
                try {
                    PreparedStatement createIndex = conn.prepareStatement(
                        "CREATE INDEX IF NOT EXISTS idx_price_history_shop_item ON " + 
                        dataConfig.getDatabaseTablePrefix() + "_price_history (shop_id, item_id)"
                    );
                    createIndex.executeUpdate();
                } catch (SQLException e) {
                    // Index peut déjà exister, pas critique
                    plugin.getLogger().info("Note: " + e.getMessage());
                }
            }

            // Récupérer les données avec la nouvelle structure
            PreparedStatement stmt = conn.prepareStatement(
                "SELECT timestamp, " +
                "open_buy_price, close_buy_price, high_buy_price, low_buy_price, " +
                "open_sell_price, close_sell_price, high_sell_price, low_sell_price, " +
                "volume " +
                "FROM " + dataConfig.getDatabaseTablePrefix() + "_price_history " +
                "WHERE shop_id = ? AND item_id = ? " +
                // "ORDER BY timestamp ASC"
                "ORDER BY timestamp DESC " +
                "LIMIT " + new PriceHistory(shopId, itemId).getMaxDataPoints() // Limite le nombre de points récupérés
            );
            
            stmt.setString(1, shopId);
            stmt.setString(2, itemId);
            
            // plugin.getLogger().info("Récupération de l'historique des prix pour " + shopId + ":" + itemId);
            
            ResultSet rs = stmt.executeQuery();
            // int pointCount = 0;
            while (rs.next()) {
                // pointCount++;
                LocalDateTime timestamp = rs.getTimestamp("timestamp").toLocalDateTime();
                
                // Prix d'achat
                double openBuyPrice = rs.getDouble("open_buy_price");
                double closeBuyPrice = rs.getDouble("close_buy_price");
                double highBuyPrice = rs.getDouble("high_buy_price");
                double lowBuyPrice = rs.getDouble("low_buy_price");
                
                // Prix de vente
                double openSellPrice = rs.getDouble("open_sell_price");
                double closeSellPrice = rs.getDouble("close_sell_price");
                double highSellPrice = rs.getDouble("high_sell_price");
                double lowSellPrice = rs.getDouble("low_sell_price");

                // Volume
                double volume = rs.getDouble("volume");
                
                PriceDataPoint point = new PriceDataPoint(
                    timestamp, 
                    openBuyPrice, closeBuyPrice, highBuyPrice, lowBuyPrice,
                    openSellPrice, closeSellPrice, highSellPrice, lowSellPrice,
                    volume
                );
                // history.getDataPoints().add(point);
                history.addDataPointFromDB(point);
            }
            
            // plugin.getLogger().info("Historique récupéré: " + pointCount + " points trouvés");
            
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lors de la récupération de l'historique des prix: " + e.getMessage());
        }
        
        return history;
    }

    /**
     * Récupère les données de prix agrégées par intervalle de temps
     * @param shopId ID du shop
     * @param itemId ID de l'item
     * @param interval Intervalle en minutes (5, 15, 30, 60, etc.)
     * @param startTime Date de début (optionnelle)
     * @param maxPoints Nombre maximum de points à retourner
     * @return Liste de points de données agrégés
     */
    public List<PriceDataPoint> getAggregatedPriceHistory(String shopId, String itemId, int interval, 
                                                        LocalDateTime startTime, int maxPoints) {
        List<PriceDataPoint> aggregatedPoints = new ArrayList<>();
        String tablePrefix = dataConfig.getDatabaseTablePrefix();
        
        try (Connection conn = getConnection()) {
            // // Construction de la requête en fonction du type de base de données
            // String timeFloorFunction;
            // String dateAddFunction;
            
            // if (dataConfig.getDatabaseType().equals("mysql")) {
            //     // MySQL utilise une syntaxe différente pour tronquer les dates
            //     timeFloorFunction = "DATE_FORMAT(timestamp, '%Y-%m-%d %H:%i:00')";
            //     dateAddFunction = "DATE_SUB(NOW(), INTERVAL ? DAY)";
            // } else {
            //     // SQLite (plus basique, nécessite des ajustements)
            //     timeFloorFunction = "strftime('%Y-%m-%d %H:%M:00', timestamp)";
            //     dateAddFunction = "datetime('now', '-' || ? || ' days')";
            // }
            
            // String sql = 
            //     "WITH intervals AS (" +
            //     "  SELECT " +
            //     "    " + timeFloorFunction + " AS interval_start, " +
            //     "    MIN(open_buy_price) AS first_open_buy, " +
            //     "    MAX(high_buy_price) AS max_high_buy, " +
            //     "    MIN(low_buy_price) AS min_low_buy, " +
            //     "    MAX(close_buy_price) AS last_close_buy, " +
            //     "    MIN(open_sell_price) AS first_open_sell, " +
            //     "    MAX(high_sell_price) AS max_high_sell, " +
            //     "    MIN(low_sell_price) AS min_low_sell, " +
            //     "    MAX(close_sell_price) AS last_close_sell, " +
            //     "    SUM(volume) AS total_volume " +
            //     "  FROM " + tablePrefix + "_price_history " +
            //     "  WHERE shop_id = ? AND item_id = ? " +
            //     (startTime != null ? " AND timestamp > ? " : "") +
            //     "  GROUP BY interval_start " +
            //     "  ORDER BY interval_start DESC " +
            //     "  LIMIT ?" +
            //     ") " +
            //     "SELECT * FROM intervals ORDER BY interval_start ASC";
            
            // PreparedStatement stmt = conn.prepareStatement(sql);
            // int paramIndex = 1;
            
            // stmt.setString(paramIndex++, shopId);
            // stmt.setString(paramIndex++, itemId);
            
            // if (startTime != null) {
            //     stmt.setTimestamp(paramIndex++, Timestamp.valueOf(startTime));
            // }
            
            // stmt.setInt(paramIndex, maxPoints);
            String sql;
            int paramIndex = 1;

            if (dataConfig.getDatabaseType().equals("mysql")) {
                // MySQL : on tronque à l'intervalle en minutes
                sql =
                    "WITH intervals AS (" +
                    "  SELECT " +
                    "    DATE_FORMAT(DATE_ADD('1970-01-01 00:00:00', INTERVAL FLOOR(UNIX_TIMESTAMP(timestamp) / (? * 60)) * (? * 60) SECOND), '%Y-%m-%d %H:%i:00') AS interval_start, " +
                    "    MIN(open_buy_price) AS first_open_buy, " +
                    "    MAX(high_buy_price) AS max_high_buy, " +
                    "    MIN(low_buy_price) AS min_low_buy, " +
                    "    MAX(close_buy_price) AS last_close_buy, " +
                    "    MIN(open_sell_price) AS first_open_sell, " +
                    "    MAX(high_sell_price) AS max_high_sell, " +
                    "    MIN(low_sell_price) AS min_low_sell, " +
                    "    MAX(close_sell_price) AS last_close_sell, " +
                    "    SUM(volume) AS total_volume " +
                    "  FROM " + tablePrefix + "_price_history " +
                    "  WHERE shop_id = ? AND item_id = ? " +
                    (startTime != null ? " AND timestamp > ? " : "") +
                    "  GROUP BY interval_start " +
                    "  ORDER BY interval_start DESC " +
                    "  LIMIT ?" +
                    ") " +
                    "SELECT * FROM intervals ORDER BY interval_start ASC";
            } else {
                // SQLite : on tronque à l'intervalle en minutes avec strftime et division d'epoch
                sql =
                    "WITH intervals AS (" +
                    "  SELECT " +
                    "    strftime('%Y-%m-%d %H:%M:00', datetime((strftime('%s', timestamp) / (? * 60)) * (? * 60), 'unixepoch')) AS interval_start, " +
                    "    MIN(open_buy_price) AS first_open_buy, " +
                    "    MAX(high_buy_price) AS max_high_buy, " +
                    "    MIN(low_buy_price) AS min_low_buy, " +
                    "    MAX(close_buy_price) AS last_close_buy, " +
                    "    MIN(open_sell_price) AS first_open_sell, " +
                    "    MAX(high_sell_price) AS max_high_sell, " +
                    "    MIN(low_sell_price) AS min_low_sell, " +
                    "    MAX(close_sell_price) AS last_close_sell, " +
                    "    SUM(volume) AS total_volume " +
                    "  FROM " + tablePrefix + "_price_history " +
                    "  WHERE shop_id = ? AND item_id = ? " +
                    (startTime != null ? " AND timestamp > ? " : "") +
                    "  GROUP BY interval_start " +
                    "  ORDER BY interval_start DESC " +
                    "  LIMIT ?" +
                    ") " +
                    "SELECT * FROM intervals ORDER BY interval_start ASC";
            }

            PreparedStatement stmt = conn.prepareStatement(sql);

            // Paramètres communs (ordre important)
            stmt.setInt(paramIndex++, interval); // interval pour le tronquage
            stmt.setInt(paramIndex++, interval); // interval pour le tronquage
            stmt.setString(paramIndex++, shopId);
            stmt.setString(paramIndex++, itemId);
            if (startTime != null) {
                stmt.setTimestamp(paramIndex++, Timestamp.valueOf(startTime));
            }
            stmt.setInt(paramIndex, maxPoints);
            
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                LocalDateTime timestamp = rs.getTimestamp("interval_start").toLocalDateTime();
                
                double openBuy = rs.getDouble("first_open_buy");
                double highBuy = rs.getDouble("max_high_buy");
                double lowBuy = rs.getDouble("min_low_buy");
                double closeBuy = rs.getDouble("last_close_buy");
                
                double openSell = rs.getDouble("first_open_sell");
                double highSell = rs.getDouble("max_high_sell");
                double lowSell = rs.getDouble("min_low_sell");
                double closeSell = rs.getDouble("last_close_sell");
                
                double volume = rs.getDouble("total_volume");
                
                PriceDataPoint point = new PriceDataPoint(
                    timestamp, 
                    openBuy, closeBuy, highBuy, lowBuy,
                    openSell, closeSell, highSell, lowSell,
                    volume
                );
                
                aggregatedPoints.add(point);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lors de la récupération de l'historique agrégé: " + e.getMessage());
        }
        
        return aggregatedPoints;
    }

    // /**
    //  * Sauvegarde l'historique des prix d'un item
    //  */
    // public void savePriceHistory(PriceHistory history) {
    //     try (Connection conn = getConnection()) {
    //         // S'assurer que la table existe
    //         PreparedStatement createTable = conn.prepareStatement(
    //             "CREATE TABLE IF NOT EXISTS " + dataConfig.getDatabaseTablePrefix() + "_price_history (" +
    //             "id INT AUTO_INCREMENT PRIMARY KEY, " +
    //             "shop_id VARCHAR(100) NOT NULL, " +
    //             "item_id VARCHAR(100) NOT NULL, " +
    //             "timestamp TIMESTAMP NOT NULL, " +
    //             // Colonnes pour les prix d'achat
    //             "open_buy_price DOUBLE NOT NULL DEFAULT 0, " +
    //             "close_buy_price DOUBLE NOT NULL DEFAULT 0, " +
    //             "high_buy_price DOUBLE NOT NULL DEFAULT 0, " +
    //             "low_buy_price DOUBLE NOT NULL DEFAULT 0, " +
    //             // Colonnes pour les prix de vente
    //             "open_sell_price DOUBLE NOT NULL DEFAULT 0, " +
    //             "close_sell_price DOUBLE NOT NULL DEFAULT 0, " +
    //             "high_sell_price DOUBLE NOT NULL DEFAULT 0, " +
    //             "low_sell_price DOUBLE NOT NULL DEFAULT 0, " +
    //             // Ajout du volume
    //             "volume DOUBLE NOT NULL DEFAULT 0, " +
    //             "INDEX (shop_id, item_id))"
    //         );
    //         createTable.executeUpdate();
            
    //         // Insertion des points de données
    //         PreparedStatement insertStmt = conn.prepareStatement(
    //             "INSERT INTO " + dataConfig.getDatabaseTablePrefix() + "_price_history " +
    //             "(shop_id, item_id, timestamp, " +
    //             "open_buy_price, close_buy_price, high_buy_price, low_buy_price, " +
    //             "open_sell_price, close_sell_price, high_sell_price, low_sell_price, " +
    //             "volume) " +
    //             "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
    //         );
            
    //         for (PriceDataPoint point : history.getDataPoints()) {
    //             insertStmt.setString(1, history.getShopId());
    //             insertStmt.setString(2, history.getItemId());
    //             insertStmt.setTimestamp(3, Timestamp.valueOf(point.getTimestamp()));
                
    //             // Prix d'achat
    //             insertStmt.setDouble(4, point.getOpenBuyPrice());
    //             insertStmt.setDouble(5, point.getCloseBuyPrice());
    //             insertStmt.setDouble(6, point.getHighBuyPrice());
    //             insertStmt.setDouble(7, point.getLowBuyPrice());
                
    //             // Prix de vente
    //             insertStmt.setDouble(8, point.getOpenSellPrice());
    //             insertStmt.setDouble(9, point.getCloseSellPrice());
    //             insertStmt.setDouble(10, point.getHighSellPrice());
    //             insertStmt.setDouble(11, point.getLowSellPrice());
                
    //             // Volume
    //             insertStmt.setDouble(12, point.getVolume());
                
    //             insertStmt.addBatch();
    //         }
            
    //         insertStmt.executeBatch();
            
    //     } catch (SQLException e) {
    //         plugin.getLogger().severe("Erreur lors de la sauvegarde de l'historique des prix: " + e.getMessage());
    //     }
    // }

    /**
     * Sauvegarde un seul point de données historique
     */
    public void saveSinglePriceDataPoint(String shopId, String itemId, PriceHistory.PriceDataPoint point) {
        try (Connection conn = getConnection()) {
            // Déterminer la syntaxe SQL selon le type de base de données
            String createTableSQL;
            
            if (dataConfig.getDatabaseType().equals("mysql")) {
                // Syntaxe MySQL avec l'index intégré
                createTableSQL = 
                    "CREATE TABLE IF NOT EXISTS " + dataConfig.getDatabaseTablePrefix() + "_price_history (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "shop_id VARCHAR(100) NOT NULL, " +
                    "item_id VARCHAR(100) NOT NULL, " +
                    "timestamp TIMESTAMP NOT NULL, " +
                    "open_buy_price DOUBLE NOT NULL DEFAULT 0, " +
                    "close_buy_price DOUBLE NOT NULL DEFAULT 0, " +
                    "high_buy_price DOUBLE NOT NULL DEFAULT 0, " +
                    "low_buy_price DOUBLE NOT NULL DEFAULT 0, " +
                    "open_sell_price DOUBLE NOT NULL DEFAULT 0, " +
                    "close_sell_price DOUBLE NOT NULL DEFAULT 0, " +
                    "high_sell_price DOUBLE NOT NULL DEFAULT 0, " +
                    "low_sell_price DOUBLE NOT NULL DEFAULT 0, " +
                    "volume DOUBLE NOT NULL DEFAULT 0, " +
                    "INDEX (shop_id, item_id))";
            } else {
                // Syntaxe SQLite sans l'index intégré
                createTableSQL = 
                    "CREATE TABLE IF NOT EXISTS " + dataConfig.getDatabaseTablePrefix() + "_price_history (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "shop_id VARCHAR(100) NOT NULL, " +
                    "item_id VARCHAR(100) NOT NULL, " +
                    "timestamp TIMESTAMP NOT NULL, " +
                    "open_buy_price DOUBLE NOT NULL DEFAULT 0, " +
                    "close_buy_price DOUBLE NOT NULL DEFAULT 0, " +
                    "high_buy_price DOUBLE NOT NULL DEFAULT 0, " +
                    "low_buy_price DOUBLE NOT NULL DEFAULT 0, " +
                    "open_sell_price DOUBLE NOT NULL DEFAULT 0, " +
                    "close_sell_price DOUBLE NOT NULL DEFAULT 0, " +
                    "high_sell_price DOUBLE NOT NULL DEFAULT 0, " +
                    "low_sell_price DOUBLE NOT NULL DEFAULT 0, " +
                    "volume DOUBLE NOT NULL DEFAULT 0)";
            }
            PreparedStatement createTable = conn.prepareStatement(createTableSQL);
            createTable.executeUpdate();
            
            // Insertion d'un seul point de données
            PreparedStatement insertStmt = conn.prepareStatement(
                "INSERT INTO " + dataConfig.getDatabaseTablePrefix() + "_price_history " +
                "(shop_id, item_id, timestamp, " +
                "open_buy_price, close_buy_price, high_buy_price, low_buy_price, " +
                "open_sell_price, close_sell_price, high_sell_price, low_sell_price, " +
                "volume) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
            );
            
            insertStmt.setString(1, shopId);
            insertStmt.setString(2, itemId);
            insertStmt.setTimestamp(3, Timestamp.valueOf(point.getTimestamp()));
            
            // Prix d'achat
            insertStmt.setDouble(4, point.getOpenBuyPrice());
            insertStmt.setDouble(5, point.getCloseBuyPrice());
            insertStmt.setDouble(6, point.getHighBuyPrice());
            insertStmt.setDouble(7, point.getLowBuyPrice());
            
            // Prix de vente
            insertStmt.setDouble(8, point.getOpenSellPrice());
            insertStmt.setDouble(9, point.getCloseSellPrice());
            insertStmt.setDouble(10, point.getHighSellPrice());
            insertStmt.setDouble(11, point.getLowSellPrice());
            
            // Volume
            insertStmt.setDouble(12, point.getVolume());
            
            insertStmt.executeUpdate();
            
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lors de la sauvegarde d'un point d'historique: " + e.getMessage());
        }
    }

    public void purgeOldPriceHistory(int daysToKeep) {
        String tablePrefix = dataConfig.getDatabaseTablePrefix();
        LocalDateTime cutoff = LocalDateTime.now().minusDays(daysToKeep);
        
        String sql = "DELETE FROM " + tablePrefix + "_price_history " +
                    "WHERE timestamp < ?";
        
        try (Connection conn = getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setTimestamp(1, Timestamp.valueOf(cutoff));
            int rowsDeleted = stmt.executeUpdate();
            plugin.getLogger().info("Purge de l'historique des prix : " + rowsDeleted + " entrées supprimées");
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lors de la purge de l'historique : " + e.getMessage());
        }
    }
}