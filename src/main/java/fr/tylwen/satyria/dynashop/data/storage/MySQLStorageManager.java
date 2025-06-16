package fr.tylwen.satyria.dynashop.data.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import fr.tylwen.satyria.dynashop.DynaShopPlugin;
import fr.tylwen.satyria.dynashop.config.DataConfig;
// import fr.tylwen.satyria.dynashop.data.cache.LimitCacheEntry;
import fr.tylwen.satyria.dynashop.data.param.DynaShopType;
import fr.tylwen.satyria.dynashop.price.DynamicPrice;
import fr.tylwen.satyria.dynashop.data.model.TransactionRecord;
import fr.tylwen.satyria.dynashop.system.chart.PriceHistory;
import fr.tylwen.satyria.dynashop.system.chart.PriceHistory.PriceDataPoint;
import net.brcdev.shopgui.ShopGuiPlusApi;
import net.brcdev.shopgui.shop.Shop;
import net.brcdev.shopgui.shop.item.ShopItem;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

// import org.bukkit.Bukkit;

/**
 * Implémentation MySQL/MariaDB du gestionnaire de stockage
 */
public class MySQLStorageManager implements StorageManager {
    private final DynaShopPlugin plugin;
    private final DataConfig dataConfig;
    private HikariDataSource dataSource;
    private boolean isInitialized = false;
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();
    private final String timeReference;
    
    private static final int RETRY_LIMIT = 3;
    private static final int RETRY_DELAY_MS = 1000;
    
    public MySQLStorageManager(DynaShopPlugin plugin) {
        this.plugin = plugin;
        this.dataConfig = plugin.getDataConfig();
        this.timeReference = plugin.getConfig().getString("limit.time-reference", "first");
    }

    @Override
    public void initialize() {
        if (isInitialized && dataSource != null && !dataSource.isClosed()) {
            return;
        }
        
        closeDataSource();
        
        try {
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
            isInitialized = true;
            
            createTables();
            migrateFromOldSchema();
            
            plugin.getLogger().info("MySQL connection established successfully");
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to initialize MySQL database: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    @Override
    public void shutdown() {
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
        
        plugin.getLogger().info("MySQL connection closed");
    }
    
    // ============ MÉTHODES PRIVÉES D'INITIALISATION ============
    
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
    
    private void createTables() {
        String tablePrefix = dataConfig.getDatabaseTablePrefix();
        
        // Table pour les prix d'achat
        String createBuyPriceTableSQL = "CREATE TABLE IF NOT EXISTS " + tablePrefix + "_buy_prices (" +
                "shopID VARCHAR(255) NOT NULL, " +
                "itemID VARCHAR(255) NOT NULL, " +
                "price DOUBLE NOT NULL, " +
                "PRIMARY KEY (shopID, itemID)" +
                ")";
        
        // Table pour les prix de vente
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
        
        // Tables pour les transactions (limites d'achat/vente)
        String[] transactionTables = {
            tablePrefix + "_transaction_limits",
            tablePrefix + "_tx_daily",
            tablePrefix + "_tx_weekly",
            tablePrefix + "_tx_monthly",
            tablePrefix + "_tx_yearly",
            tablePrefix + "_tx_forever"
        };
        
        String transactionTableStructure = 
            "player_uuid VARCHAR(36) NOT NULL, " +
            "shop_id VARCHAR(100) NOT NULL, " +
            "item_id VARCHAR(100) NOT NULL, " +
            "transaction_type VARCHAR(10) NOT NULL, " + 
            "amount INT NOT NULL, " +
            "transaction_time TIMESTAMP NOT NULL, " +
            "PRIMARY KEY (player_uuid, shop_id, item_id, transaction_type, transaction_time)";
        
        // Table pour les métadonnées
        String createMetadataTableSQL = "CREATE TABLE IF NOT EXISTS " + tablePrefix + "_metadata (" +
                "meta_key VARCHAR(50) PRIMARY KEY, " +
                "value TEXT NOT NULL" +
                ")";
        
        // Table pour l'historique des prix
        String createPriceHistoryTableSQL = "CREATE TABLE IF NOT EXISTS " + tablePrefix + "_price_history (" +
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
                "INDEX (shop_id, item_id)" +
                ")";
        
        executeUpdate(createBuyPriceTableSQL);
        executeUpdate(createSellPriceTableSQL);
        executeUpdate(createStockTableSQL);
        executeUpdate(createMetadataTableSQL);
        executeUpdate(createPriceHistoryTableSQL);
        
        // Créer les tables de transactions
        for (String tableName : transactionTables) {
            String createTransactionTableSQL = "CREATE TABLE IF NOT EXISTS " + tableName + " (" + transactionTableStructure + ")";
            executeUpdate(createTransactionTableSQL);
        }
        
        // Créer une vue pour les transactions
        try {
            StringBuilder viewSQLBuilder = new StringBuilder("CREATE OR REPLACE VIEW " + tablePrefix + "_transactions_view AS ");
            for (int i = 0; i < transactionTables.length; i++) {
                if (i > 0) viewSQLBuilder.append(" UNION ALL ");
                viewSQLBuilder.append("SELECT * FROM ").append(transactionTables[i]);
            }
            String viewSQL = viewSQLBuilder.toString();
            executeUpdate(viewSQL);
        } catch (Exception e) {
            plugin.getLogger().warning("Note: Unable to create transactions view: " + e.getMessage());
        }
        
        // Créer une vue pour les prix
        createPricesView(tablePrefix);
        
        // Créer des index pour améliorer les performances
        executeUpdate("CREATE INDEX IF NOT EXISTS idx_price_history_shop_item ON " + tablePrefix + "_price_history (shop_id, item_id)");
        
        for (String tableName : transactionTables) {
            executeUpdate("CREATE INDEX IF NOT EXISTS " + tableName + "_time_idx ON " + tableName + " (transaction_time)");
            executeUpdate("CREATE INDEX IF NOT EXISTS " + tableName + "_player_idx ON " + tableName + " (player_uuid)");
            executeUpdate("CREATE INDEX IF NOT EXISTS " + tableName + "_lookup_idx ON " + tableName + " (player_uuid, shop_id, item_id, transaction_type)");
        }
    }
    
    private void createPricesView(String tablePrefix) {
        try {
            String viewSQL = "CREATE OR REPLACE VIEW " + tablePrefix + "_items AS " +
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
                    
            executeUpdate(viewSQL);
        } catch (Exception e) {
            plugin.getLogger().warning("Note: Unable to create prices view: " + e.getMessage());
        }
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
            
            plugin.getLogger().info("Migration des données de prix vers le nouveau schéma réussie!");
        }
    }
    
    // ============ MÉTHODES UTILITAIRES DE BASE DE DONNÉES ============
    
    private Connection getConnection() throws SQLException {
        if (!isInitialized || dataSource == null || dataSource.isClosed()) {
            initialize();
        }
        
        return dataSource.getConnection();
    }
    
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
    
    private int executeUpdate(String sql, Object... params) {
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
    
    private void handleSQLException(SQLException e, int attempt) {
        if (attempt < RETRY_LIMIT - 1) {
            plugin.getLogger().warning("SQL error (attempt " + (attempt + 1) + "/" + RETRY_LIMIT + "): " + e.getMessage());
        } else {
            plugin.getLogger().severe("Critical SQL error: " + e.getMessage());
            e.printStackTrace();
        }
        
        if (isConnectionError(e)) {
            plugin.getLogger().info("Attempting to reconnect to the database...");
            closeDataSource();
            initialize();
        }
    }
    
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
    
    @Override
    public <T> CompletableFuture<T> executeAsync(DatabaseOperation<T> operation) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return operation.execute();
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Error during asynchronous execution", e);
                throw new RuntimeException(e);
            }
        }, databaseExecutor);
    }
    
    // ============ IMPLÉMENTATION DES MÉTHODES DE L'INTERFACE ============
    
    @Override
    public Optional<DynamicPrice> getPrices(String shopId, String itemId) {
        String tablePrefix = dataConfig.getDatabaseTablePrefix();
        String sql = "SELECT buyPrice, sellPrice, stock FROM " + tablePrefix + "_items WHERE shopID = ? AND itemID = ?";
        
        return executeQuery(sql, rs -> {
            if (rs.next()) {
                double buyPrice = rs.getDouble("buyPrice");
                double sellPrice = rs.getDouble("sellPrice");
                int stock = rs.getInt("stock");
                
                return new DynamicPrice(
                    buyPrice >= 0 ? buyPrice : -1.0,
                    sellPrice >= 0 ? sellPrice : -1.0,
                    stock
                );
            }
            return null;
        }, shopId, itemId);
    }
    
    @Override
    public Optional<Double> getBuyPrice(String shopId, String itemId) {
        String tablePrefix = dataConfig.getDatabaseTablePrefix();
        String sql = "SELECT price FROM " + tablePrefix + "_buy_prices WHERE shopID = ? AND itemID = ?";
        
        return executeQuery(sql, rs -> {
            if (rs.next()) {
                return rs.getDouble("price");
            }
            return -1.0; // Retourne -1.0 si le prix n'existe pas
        }, shopId, itemId).filter(price -> price >= 0);
    }
    
    @Override
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
    
    @Override
    public Optional<Integer> getStock(String shopId, String itemId) {
        String tablePrefix = dataConfig.getDatabaseTablePrefix();
        String sql = "SELECT stock FROM " + tablePrefix + "_stock WHERE shopID = ? AND itemID = ?";
        
        return executeQuery(sql, rs -> {
            if (rs.next()) {
                return rs.getInt("stock") >= 0 ? rs.getInt("stock") : -1;
            }
            return -1; // Retourne -1 si le stock n'existe pas
        }, shopId, itemId);
    }
    
    @Override
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
    
    @Override
    public void saveBuyPrice(String shopId, String itemId, double buyPrice) {
        String tablePrefix = dataConfig.getDatabaseTablePrefix();
        
        if (buyPrice >= 0) {
            String buySQL = "REPLACE INTO " + tablePrefix + "_buy_prices (shopID, itemID, price) VALUES (?, ?, ?)";
            executeUpdate(buySQL, shopId, itemId, buyPrice);
        } else {
            String deleteBuySQL = "DELETE FROM " + tablePrefix + "_buy_prices WHERE shopID = ? AND itemID = ?";
            executeUpdate(deleteBuySQL, shopId, itemId);
        }
    }
    
    @Override
    public void saveSellPrice(String shopId, String itemId, double sellPrice) {
        String tablePrefix = dataConfig.getDatabaseTablePrefix();
        
        if (sellPrice >= 0) {
            String sellSQL = "REPLACE INTO " + tablePrefix + "_sell_prices (shopID, itemID, price) VALUES (?, ?, ?)";
            executeUpdate(sellSQL, shopId, itemId, sellPrice);
        } else {
            String deleteSellSQL = "DELETE FROM " + tablePrefix + "_sell_prices WHERE shopID = ? AND itemID = ?";
            executeUpdate(deleteSellSQL, shopId, itemId);
        }
    }
    
    @Override
    public void saveStock(String shopId, String itemId, int stock) {
        String tablePrefix = dataConfig.getDatabaseTablePrefix();
        // String sql = "REPLACE INTO " + tablePrefix + "_stock (shopID, itemID, stock) VALUES (?, ?, ?)";
        // executeUpdate(sql, shopId, itemId, stock);

        if (stock >= 0) {
            String sql = "REPLACE INTO " + tablePrefix + "_stock (shopID, itemID, stock) VALUES (?, ?, ?)";
            executeUpdate(sql, shopId, itemId, stock);
        } else {
            String deleteSQL = "DELETE FROM " + tablePrefix + "_stock WHERE shopID = ? AND itemID = ?";
            executeUpdate(deleteSQL, shopId, itemId);
        }
    }

    @Override
    public void deleteStock(String shopId, String itemId) {
        String tablePrefix = dataConfig.getDatabaseTablePrefix();
        String sql = "DELETE FROM " + tablePrefix + "_stock WHERE shopID = ? AND itemID = ?";
        executeUpdate(sql, shopId, itemId);
    }

    @Override
    public void cleanupStockTable() {
        String tablePrefix = dataConfig.getDatabaseTablePrefix();
        String sql = "SELECT shopID, itemID FROM " + tablePrefix + "_stock";

        executeQuery(sql, rs -> {
            while (rs.next()) {
                String shopId = rs.getString("shopID");
                String itemId = rs.getString("itemID");
                
                DynaShopType typeDynaShop = plugin.getShopConfigManager().getTypeDynaShop(shopId, itemId);
                if (typeDynaShop != DynaShopType.STOCK && typeDynaShop != DynaShopType.STATIC_STOCK) {
                    deleteStock(shopId, itemId);
                }
            }
            return null;
        });
    }
    
    @Override
    public void deleteItem(String shopId, String itemId) {
        String tablePrefix = dataConfig.getDatabaseTablePrefix();
        
        String deleteBuySQL = "DELETE FROM " + tablePrefix + "_buy_prices WHERE shopID = ? AND itemID = ?";
        executeUpdate(deleteBuySQL, shopId, itemId);
        
        String deleteSellSQL = "DELETE FROM " + tablePrefix + "_sell_prices WHERE shopID = ? AND itemID = ?";
        executeUpdate(deleteSellSQL, shopId, itemId);
        
        String deleteStockSQL = "DELETE FROM " + tablePrefix + "_stock WHERE shopID = ? AND itemID = ?";
        executeUpdate(deleteStockSQL, shopId, itemId);
    }
    
    @Override
    public boolean itemExists(String shopId, String itemId) {
        String tablePrefix = dataConfig.getDatabaseTablePrefix();
        String sql = "SELECT COUNT(*) FROM " + tablePrefix + "_items WHERE shopID = ? AND itemID = ?";
        
        return executeQuery(sql, rs -> {
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
            return false;
        }, shopId, itemId).orElse(false);
    }
    
    @Override
    public Map<ShopItem, DynamicPrice> loadAllPrices() {
        Map<ShopItem, DynamicPrice> priceMap = new HashMap<>();
        String tablePrefix = dataConfig.getDatabaseTablePrefix();
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
    
    @Override
    public void saveTransactionsBatch(List<TransactionRecord> transactions) {
        if (transactions.isEmpty()) return;
        
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            
            try {
                for (TransactionRecord record : transactions) {
                    String tableName = getAppropriateTransactionTable(record);
                    String transactionType = record.isBuy() ? "BUY" : "SELL";
                    
                    String sql = "INSERT INTO " + tableName + 
                            " (player_uuid, shop_id, item_id, transaction_type, amount, transaction_time) " +
                            "VALUES (?, ?, ?, ?, ?, ?) " +
                            "ON DUPLICATE KEY UPDATE amount = amount + ?, transaction_time = VALUES(transaction_time)";
                    
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setString(1, record.getPlayerUuid().toString());
                        stmt.setString(2, record.getShopId());
                        stmt.setString(3, record.getItemId());
                        stmt.setString(4, transactionType);
                        stmt.setInt(5, record.getQuantity());
                        stmt.setTimestamp(6, Timestamp.valueOf(record.getTimestamp()));
                        stmt.setInt(7, record.getQuantity());

                        stmt.executeUpdate();
                    }
                }
                
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error saving transactions: " + e.getMessage());
        }
    }
    
    private String getAppropriateTransactionTable(TransactionRecord record) {
        String tablePrefix = dataConfig.getDatabaseTablePrefix();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime timestamp = record.getTimestamp();

        if (timestamp.isAfter(now.minusDays(1))) {
            return tablePrefix + "_tx_daily";
        } else if (timestamp.isAfter(now.minusWeeks(1))) {
            return tablePrefix + "_tx_weekly";
        } else if (timestamp.isAfter(now.minusMonths(1))) {
            return tablePrefix + "_tx_monthly";
        } else if (timestamp.isAfter(now.minusYears(1))) {
            return tablePrefix + "_tx_yearly";
        } else {
            return tablePrefix + "_tx_forever";
        }
    }
    
    @Override
    public int getUsedAmount(UUID playerUuid, String shopId, String itemId, boolean isBuy, LocalDateTime since) {
        String tablePrefix = dataConfig.getDatabaseTablePrefix();
        String transactionType = isBuy ? "BUY" : "SELL";
        String sql = "SELECT COALESCE(SUM(amount), 0) AS total FROM " + tablePrefix + "_transactions_view " +
                "WHERE player_uuid = ? AND shop_id = ? AND item_id = ? AND transaction_type = ? AND transaction_time >= ?";
        
        return executeQuery(sql, rs -> {
            if (rs.next()) {
                return rs.getInt("total");
            }
            return 0;
        }, playerUuid.toString(), shopId, itemId, transactionType, Timestamp.valueOf(since)).orElse(0);
    }
    
    // @Override
    // public Optional<LocalDateTime> getLastTransactionTime(UUID playerUuid, String shopId, String itemId, boolean isBuy) {
    //     String tablePrefix = dataConfig.getDatabaseTablePrefix();
    //     String transactionType = isBuy ? "BUY" : "SELL";
    //     String sql = "SELECT MAX(transaction_time) AS latest FROM " + tablePrefix + "_transactions_view " +
    //             "WHERE player_uuid = ? AND shop_id = ? AND item_id = ? AND transaction_type = ?";
        
    //     return executeQuery(sql, rs -> {
    //         if (rs.next() && rs.getTimestamp("latest") != null) {
    //             return rs.getTimestamp("latest").toLocalDateTime();
    //         }
    //         return null;
    //     }, playerUuid.toString(), shopId, itemId, transactionType);
    // }
    @Override
    public Optional<LocalDateTime> getLastTransactionTime(UUID playerUuid, String shopId, String itemId, boolean isBuy) {
        String tablePrefix = dataConfig.getDatabaseTablePrefix();
        String transactionType = isBuy ? "BUY" : "SELL";
        if (timeReference.equalsIgnoreCase("last")) {
            // Si le temps de référence est "last", on utilise la dernière transaction
            String sql = "SELECT MAX(transaction_time) AS latest FROM " + tablePrefix + "_transactions_view " +
                    "WHERE player_uuid = ? AND shop_id = ? AND item_id = ? AND transaction_type = ?";
            
            return executeQuery(sql, rs -> {
                if (rs.next() && rs.getTimestamp("latest") != null) {
                    return rs.getTimestamp("latest").toLocalDateTime();
                }
                return null;
            }, playerUuid.toString(), shopId, itemId, transactionType);
        } else {
            String sql = "SELECT MIN(transaction_time) AS earliest FROM " + tablePrefix + "_transactions_view " +
                    "WHERE player_uuid = ? AND shop_id = ? AND item_id = ? AND transaction_type = ?";
            
            return executeQuery(sql, rs -> {
                if (rs.next() && rs.getTimestamp("earliest") != null) {
                    return rs.getTimestamp("earliest").toLocalDateTime();
                }
                return null;
            }, playerUuid.toString(), shopId, itemId, transactionType);
        }
    }
    
    @Override
    public boolean resetLimits(UUID playerUuid, String shopId, String itemId) {
        // String tablePrefix = dataConfig.getDatabaseTablePrefix();
        String[] transactionTables = getTransactionTables();
        boolean success = true;
        
        try (Connection conn = getConnection()) {
            for (String table : transactionTables) {
                String sql = "DELETE FROM " + table + " WHERE player_uuid = ? AND shop_id = ? AND item_id = ?";
                
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, playerUuid.toString());
                    stmt.setString(2, shopId);
                    stmt.setString(3, itemId);
                    stmt.executeUpdate();
                } catch (SQLException e) {
                    success = false;
                    plugin.getLogger().warning("Error resetting limits: " + e.getMessage());
                }
            }
        } catch (SQLException e) {
            success = false;
            plugin.getLogger().warning("Connection error while resetting limits: " + e.getMessage());
        }
        
        return success;
    }
    
    @Override
    public boolean resetAllLimits(UUID playerUuid) {
        // String tablePrefix = dataConfig.getDatabaseTablePrefix();
        String[] transactionTables = getTransactionTables();
        boolean success = true;
        
        try (Connection conn = getConnection()) {
            for (String table : transactionTables) {
                String sql = "DELETE FROM " + table + " WHERE player_uuid = ?";
                
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, playerUuid.toString());
                    stmt.executeUpdate();
                } catch (SQLException e) {
                    success = false;
                    plugin.getLogger().warning("Error resetting all limits: " + e.getMessage());
                }
            }
        } catch (SQLException e) {
            success = false;
            plugin.getLogger().warning("Connection error while resetting all limits: " + e.getMessage());
        }
        
        return success;
    }
    
    @Override
    public boolean resetAllLimits() {
        // String tablePrefix = dataConfig.getDatabaseTablePrefix();
        String[] transactionTables = getTransactionTables();
        boolean success = true;
        
        try (Connection conn = getConnection()) {
            for (String table : transactionTables) {
                String sql = "TRUNCATE TABLE " + table;
                
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.executeUpdate();
                } catch (SQLException e) {
                    success = false;
                    plugin.getLogger().warning("Error resetting all limits for all players: " + e.getMessage());
                }
            }
        } catch (SQLException e) {
            success = false;
            plugin.getLogger().warning("Connection error while resetting all limits for all players: " + e.getMessage());
        }
        
        return success;
    }
    
    @Override
    public void cleanupExpiredTransactions() {
        LocalDateTime now = LocalDateTime.now();
        
        cleanupTable(dataConfig.getDatabaseTablePrefix() + "_tx_daily", now.truncatedTo(ChronoUnit.DAYS));
        cleanupTable(dataConfig.getDatabaseTablePrefix() + "_tx_weekly", now.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)).truncatedTo(ChronoUnit.DAYS));
        cleanupTable(dataConfig.getDatabaseTablePrefix() + "_tx_monthly", now.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS));
        cleanupTable(dataConfig.getDatabaseTablePrefix() + "_tx_yearly", now.withDayOfYear(1).truncatedTo(ChronoUnit.DAYS));
    }

    private void cleanupTable(String tableName, LocalDateTime cutoffDate) {
        String sql = "DELETE FROM " + tableName + " WHERE transaction_time < ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setTimestamp(1, Timestamp.valueOf(cutoffDate));
            int deleted = stmt.executeUpdate();
            
            if (deleted > 0) {
                plugin.getLogger().info("Cleanup of " + tableName + ": " + deleted + " entries deleted");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Error cleaning up " + tableName + ": " + e.getMessage());
        }
    }
    
    private String[] getTransactionTables() {
        String tablePrefix = dataConfig.getDatabaseTablePrefix();
        return new String[] {
            tablePrefix + "_transaction_limits",
            tablePrefix + "_tx_daily",
            tablePrefix + "_tx_weekly",
            tablePrefix + "_tx_monthly",
            tablePrefix + "_tx_yearly",
            tablePrefix + "_tx_forever"
        };
    }
    
    @Override
    public PriceHistory getPriceHistory(String shopId, String itemId) {
        PriceHistory history = new PriceHistory(shopId, itemId);
        String tablePrefix = dataConfig.getDatabaseTablePrefix();
        
        String sql = "SELECT timestamp, " +
                "open_buy_price, close_buy_price, high_buy_price, low_buy_price, " +
                "open_sell_price, close_sell_price, high_sell_price, low_sell_price, " +
                "volume " +
                "FROM " + tablePrefix + "_price_history " +
                "WHERE shop_id = ? AND item_id = ? " +
                // "ORDER BY timestamp ASC"
                "ORDER BY timestamp DESC " +
                "LIMIT " + new PriceHistory(shopId, itemId).getMaxDataPoints(); // Limite le nombre de points récupérés
        
        executeQuery(sql, rs -> {
            while (rs.next()) {
                LocalDateTime timestamp = rs.getTimestamp("timestamp").toLocalDateTime();
                double openBuyPrice = rs.getDouble("open_buy_price");
                double closeBuyPrice = rs.getDouble("close_buy_price");
                double highBuyPrice = rs.getDouble("high_buy_price");
                double lowBuyPrice = rs.getDouble("low_buy_price");
                double openSellPrice = rs.getDouble("open_sell_price");
                double closeSellPrice = rs.getDouble("close_sell_price");
                double highSellPrice = rs.getDouble("high_sell_price");
                double lowSellPrice = rs.getDouble("low_sell_price");
                double volume = rs.getDouble("volume");
                
                PriceHistory.PriceDataPoint point = new PriceHistory.PriceDataPoint(
                    timestamp,
                    openBuyPrice, closeBuyPrice, highBuyPrice, lowBuyPrice,
                    openSellPrice, closeSellPrice, highSellPrice, lowSellPrice,
                    volume
                );
                
                history.addDataPoint(point);
            }
            return null;
        }, shopId, itemId);
        
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
    @Override
    public List<PriceDataPoint> getAggregatedPriceHistory(String shopId, String itemId, int interval, LocalDateTime startTime, int maxPoints) {
        List<PriceDataPoint> aggregatedPoints = new ArrayList<>();
        String tablePrefix = dataConfig.getDatabaseTablePrefix();
         
        try (Connection conn = getConnection()) {
            int paramIndex = 1;

            String sql =
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
    
    // @Override
    // public void savePriceDataPoint(String shopId, String itemId, PriceHistory.PriceDataPoint point) {
    //     String tablePrefix = dataConfig.getDatabaseTablePrefix();
        
    //     String sql = "INSERT INTO " + tablePrefix + "_price_history " +
    //             "(shop_id, item_id, timestamp, " +
    //             "open_buy_price, close_buy_price, high_buy_price, low_buy_price, " +
    //             "open_sell_price, close_sell_price, high_sell_price, low_sell_price, " +
    //             "volume) " +
    //             "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
    //     try (Connection conn = getConnection();
    //          PreparedStatement stmt = conn.prepareStatement(sql)) {
            
    //         stmt.setString(1, shopId);
    //         stmt.setString(2, itemId);
    //         stmt.setTimestamp(3, Timestamp.valueOf(point.getTimestamp()));
            
    //         // Prix d'achat
    //         stmt.setDouble(4, point.getOpenBuyPrice());
    //         stmt.setDouble(5, point.getCloseBuyPrice());
    //         stmt.setDouble(6, point.getHighBuyPrice());
    //         stmt.setDouble(7, point.getLowBuyPrice());
            
    //         // Prix de vente
    //         stmt.setDouble(8, point.getOpenSellPrice());
    //         stmt.setDouble(9, point.getCloseSellPrice());
    //         stmt.setDouble(10, point.getHighSellPrice());
    //         stmt.setDouble(11, point.getLowSellPrice());
            
    //         // Volume
    //         stmt.setDouble(12, point.getVolume());
            
    //         stmt.executeUpdate();
            
    //     } catch (SQLException e) {
    //         plugin.getLogger().severe("Error saving price history point: " + e.getMessage());
    //     }
    // }
    
    @Override
    public void savePriceDataPoint(String shopId, String itemId, PriceHistory.PriceDataPoint point, int intervalMinutes) {
        String tablePrefix = dataConfig.getDatabaseTablePrefix();
        
        // Calculer le timestamp tronqué pour l'intervalle
        LocalDateTime truncatedTimestamp = point.getTimestamp()
            .withSecond(0)
            .withNano(0)
            .minusMinutes(point.getTimestamp().getMinute() % intervalMinutes);
        
        String sql = "INSERT INTO " + tablePrefix + "_price_history " +
                "(shop_id, item_id, timestamp, " +
                "open_buy_price, close_buy_price, high_buy_price, low_buy_price, " +
                "open_sell_price, close_sell_price, high_sell_price, low_sell_price, " +
                "volume) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, shopId);
            stmt.setString(2, itemId);
            stmt.setTimestamp(3, Timestamp.valueOf(truncatedTimestamp));

            // Prix d'achat
            stmt.setDouble(4, point.getOpenBuyPrice());
            stmt.setDouble(5, point.getCloseBuyPrice());
            stmt.setDouble(6, point.getHighBuyPrice());
            stmt.setDouble(7, point.getLowBuyPrice());

            // Prix de vente
            stmt.setDouble(8, point.getOpenSellPrice());
            stmt.setDouble(9, point.getCloseSellPrice());
            stmt.setDouble(10, point.getHighSellPrice());
            stmt.setDouble(11, point.getLowSellPrice());

            // Volume
            stmt.setDouble(12, point.getVolume());

            stmt.executeUpdate();

        } catch (SQLException e) {
            plugin.getLogger().severe("Error saving price history point: " + e.getMessage());
        }
    }

    @Override
    public void purgeOldPriceHistory(int daysToKeep) {
        String tablePrefix = dataConfig.getDatabaseTablePrefix();
        LocalDateTime cutoff = LocalDateTime.now().minusDays(daysToKeep);
        
        String sql = "DELETE FROM " + tablePrefix + "_price_history WHERE timestamp < ?";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setTimestamp(1, Timestamp.valueOf(cutoff));
            int rowsDeleted = stmt.executeUpdate();
            
            plugin.getLogger().info("Price history purge: " + rowsDeleted + " entries deleted");
        } catch (SQLException e) {
            plugin.getLogger().severe("Error purging price history: " + e.getMessage());
        }
    }
    
    @Override
    public double getInflationFactor() {
        String tablePrefix = dataConfig.getDatabaseTablePrefix();
        String sql = "SELECT value FROM " + tablePrefix + "_metadata WHERE meta_key = 'inflation_factor'";
        
        return executeQuery(sql, rs -> {
            if (rs.next()) {
                try {
                    return Double.parseDouble(rs.getString("value"));
                } catch (NumberFormatException e) {
                    return 1.0; // Valeur par défaut en cas d'erreur
                }
            }
            return 1.0; // Valeur par défaut si non trouvé
        }).orElse(1.0);
    }
    
    @Override
    public long getLastInflationUpdate() {
        String tablePrefix = dataConfig.getDatabaseTablePrefix();
        String sql = "SELECT value FROM " + tablePrefix + "_metadata WHERE meta_key = 'last_inflation_update'";
        
        return executeQuery(sql, rs -> {
            if (rs.next()) {
                try {
                    return Long.parseLong(rs.getString("value"));
                } catch (NumberFormatException e) {
                    return System.currentTimeMillis(); // Valeur par défaut en cas d'erreur
                }
            }
            return System.currentTimeMillis(); // Valeur par défaut si non trouvé
        }).orElse(System.currentTimeMillis());
    }
    
    @Override
    public void saveInflationData(double factor, long timestamp) {
        String tablePrefix = dataConfig.getDatabaseTablePrefix();
        
        try (Connection conn = getConnection()) {
            // S'assurer que la table metadata existe
            PreparedStatement createStmt = conn.prepareStatement(
                "CREATE TABLE IF NOT EXISTS " + tablePrefix + "_metadata (" +
                "meta_key VARCHAR(50) PRIMARY KEY, " +
                "value TEXT NOT NULL)"
            );
            createStmt.executeUpdate();
            
            // Sauvegarder le facteur d'inflation
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
            
        } catch (SQLException e) {
            plugin.getLogger().severe("Error saving inflation data: " + e.getMessage());
        }
    }
    
    @Override
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        String tablePrefix = dataConfig.getDatabaseTablePrefix();
        
        try (Connection conn = getConnection()) {
            // Total des enregistrements de prix
            String pricesSql = "SELECT COUNT(*) AS total FROM " + tablePrefix + "_items";
            try (PreparedStatement stmt = conn.prepareStatement(pricesSql);
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    stats.put("total_prices", rs.getInt("total"));
                }
            }
            
            // Total des enregistrements de transactions
            String txSql = "SELECT COUNT(*) AS total FROM " + tablePrefix + "_transactions_view";
            try (PreparedStatement stmt = conn.prepareStatement(txSql);
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    stats.put("total_records", rs.getInt("total"));
                }
            }
            
            // Transactions par type
            String typeSql = "SELECT transaction_type, COUNT(*) AS count FROM " + tablePrefix + "_transactions_view GROUP BY transaction_type";
            try (PreparedStatement stmt = conn.prepareStatement(typeSql);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    stats.put("count_" + rs.getString("transaction_type").toLowerCase(), rs.getInt("count"));
                }
            }
            
            // Plus ancien enregistrement
            String oldestSql = "SELECT MIN(transaction_time) AS oldest FROM " + tablePrefix + "_transactions_view";
            try (PreparedStatement stmt = conn.prepareStatement(oldestSql);
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next() && rs.getTimestamp("oldest") != null) {
                    stats.put("oldest_record", rs.getTimestamp("oldest").toString());
                }
            }
            
            // Utilisation de l'espace
            String sizeSql = "SELECT 'stock' AS table_name, COUNT(*) AS row_count FROM " + tablePrefix + "_stock " +
                    "UNION ALL SELECT 'buy_prices', COUNT(*) FROM " + tablePrefix + "_buy_prices " +
                    "UNION ALL SELECT 'sell_prices', COUNT(*) FROM " + tablePrefix + "_sell_prices " +
                    "UNION ALL SELECT 'price_history', COUNT(*) FROM " + tablePrefix + "_price_history";
            try (PreparedStatement stmt = conn.prepareStatement(sizeSql);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    stats.put("table_" + rs.getString("table_name") + "_count", rs.getInt("row_count"));
                }
            }
            
        } catch (SQLException e) {
            plugin.getLogger().severe("Error retrieving statistics: " + e.getMessage());
        }
        
        return stats;
    }
    
    @FunctionalInterface
    private interface ResultSetProcessor<T> {
        T process(ResultSet rs) throws SQLException;
    }
}