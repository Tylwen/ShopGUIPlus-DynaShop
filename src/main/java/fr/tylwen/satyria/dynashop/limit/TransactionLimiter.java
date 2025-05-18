package fr.tylwen.satyria.dynashop.limit;

import fr.tylwen.satyria.dynashop.DynaShopPlugin;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

public class TransactionLimiter {
    private final DynaShopPlugin plugin;
    
    private final Map<String, TransactionLimit> limitCache = new ConcurrentHashMap<>();
    private final long CACHE_DURATION = 60000; // 1 minute en millisecondes
    private final Map<String, Long> limitCacheTimestamps = new ConcurrentHashMap<>();
    
    private final Map<String, Integer> transactionCounters = new ConcurrentHashMap<>();
    private long lastMetricsReset = System.currentTimeMillis();
    
    public enum LimitPeriod {
        DAILY, WEEKLY, MONTHLY, YEARLY, FOREVER
    }
    
    public TransactionLimiter(DynaShopPlugin plugin) {
        this.plugin = plugin;
        initDatabase();
        // Appeler l'optimisation de la base de données au démarrage
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::optimizeDatabase);
        
        // Planifier une optimisation périodique (une fois par jour)
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, 
            this::optimizeDatabase,
            20L * 60L * 60L * 12L, // 12 heures après le démarrage
            20L * 60L * 60L * 24L  // Répéter toutes les 24 heures
        );
    }
    
    public Map<String, Integer> getMetrics() {
        return new HashMap<>(transactionCounters);
    }

    public void incrementCounter(String metricName) {
        transactionCounters.merge(metricName, 1, Integer::sum);
    }
    
    private void initDatabase() {
        String tablePrefix = plugin.getDataConfig().getDatabaseTablePrefix();
        String query = "CREATE TABLE IF NOT EXISTS " + tablePrefix + "_transaction_limits ("
                + "player_uuid VARCHAR(36) NOT NULL, "
                + "shop_id VARCHAR(100) NOT NULL, "
                + "item_id VARCHAR(100) NOT NULL, "
                + "transaction_type VARCHAR(10) NOT NULL, " // 'BUY' ou 'SELL'
                + "amount INT NOT NULL, "
                + "transaction_time TIMESTAMP NOT NULL, "
                + "PRIMARY KEY (player_uuid, shop_id, item_id, transaction_type, transaction_time)"
                + ")";
        
        plugin.getDataManager().executeUpdate(query);

        // Optionnel : ajouter un index pour améliorer les performances des requêtes
        
        // Ajout d'index pour accélérer les requêtes fréquentes
        String[] indexes = {
            // Index sur transaction_time pour accélérer les nettoyages
            "CREATE INDEX IF NOT EXISTS " + tablePrefix + "_tx_time_idx ON " 
                + tablePrefix + "_transaction_limits (transaction_time)",
            
            // Index sur player_uuid pour accélérer les requêtes par joueur
            "CREATE INDEX IF NOT EXISTS " + tablePrefix + "_player_idx ON " 
                + tablePrefix + "_transaction_limits (player_uuid)",
            
            // Index composite pour les requêtes de récapitulation
            "CREATE INDEX IF NOT EXISTS " + tablePrefix + "_lookup_idx ON " 
                + tablePrefix + "_transaction_limits (player_uuid, shop_id, item_id, transaction_type)"
        };
        
        for (String indexQuery : indexes) {
            try {
                plugin.getDataManager().executeUpdate(indexQuery);
            } catch (Exception e) {
                plugin.getLogger().warning("Erreur lors de la création d'index: " + e.getMessage());
            }
        }
    }
    
    public void recordTransaction(Player player, String shopId, String itemId, boolean isBuy, int amount) {
        // String tablePrefix = plugin.getDataConfig().getDatabaseTablePrefix();
        // String query = "INSERT INTO " + tablePrefix + "_transaction_limits "
        //         + "(player_uuid, shop_id, item_id, transaction_type, amount, transaction_time) "
        //         + "VALUES (?, ?, ?, ?, ?, NOW())";
            // Déterminer quelle table utiliser en fonction de la période
        TransactionLimit limit = getTransactionLimit(shopId, itemId, isBuy);
        
        String tablePrefix = plugin.getDataConfig().getDatabaseTablePrefix();
        String tableName = tablePrefix + "_transaction_limits"; // Table par défaut
        
        if (limit != null) {
            // Sélectionner la table partitionnée appropriée
            switch (limit.getPeriod()) {
                case DAILY:
                    tableName = tablePrefix + "_tx_daily";
                    break;
                case WEEKLY:
                    tableName = tablePrefix + "_tx_weekly";
                    break;
                case MONTHLY:
                    tableName = tablePrefix + "_tx_monthly";
                    break;
                case YEARLY:
                    tableName = tablePrefix + "_tx_yearly";
                    break;
                case FOREVER:
                    tableName = tablePrefix + "_tx_forever";
                    break;
            }
        }

        // Créer une copie finale de tableName pour utilisation dans la lambda
        final String finalTableName = tableName;
        
        // Reste de votre code existant en utilisant tableName au lieu de tablePrefix + "_transaction_limits"
        // Utiliser INSERT ... ON DUPLICATE KEY UPDATE pour éviter les doublons
        String query = "INSERT INTO " + finalTableName + " "
                + "(player_uuid, shop_id, item_id, transaction_type, amount, transaction_time) "
                + "VALUES (?, ?, ?, ?, ?, NOW()) "
                + "ON DUPLICATE KEY UPDATE amount = amount + ?, transaction_time = NOW()";

        // Incrémenter métriques
        incrementCounter("total_transactions");
        incrementCounter(isBuy ? "buy_transactions" : "sell_transactions");
        // incrementCounter("shop_" + shopId + "_transactions");
        // incrementCounter("item_" + itemId + "_transactions");
        // incrementCounter("player_" + player.getUniqueId() + "_transactions");
        
        // Réinitialiser les métriques quotidiennes si nécessaire
        long now = System.currentTimeMillis();
        if (now - lastMetricsReset > 24 * 60 * 60 * 1000) { // 24 heures
            transactionCounters.clear();
            lastMetricsReset = now;
        }
        
        plugin.getDataManager().executeAsync(() -> {
            try (Connection connection = plugin.getDataManager().getConnection();
                 PreparedStatement stmt = connection.prepareStatement(query)) {
                stmt.setString(1, player.getUniqueId().toString());
                stmt.setString(2, shopId);
                stmt.setString(3, itemId);
                stmt.setString(4, isBuy ? "BUY" : "SELL");
                stmt.setInt(5, amount);
                stmt.setInt(6, amount);
            
                // Exécuter la requête et retourner le nombre de lignes affectées
                int result = stmt.executeUpdate();
                
                // Si transaction réussie, vérifier si un nettoyage est nécessaire
                if (result > 0 && Math.random() < 0.01) { // 1% de chance de déclencher un nettoyage
                    cleanupExpiredTransactions();
                }
                
                return result;
            } catch (SQLException e) {
                // Si l'erreur est due à une syntaxe ON DUPLICATE KEY non supportée (SQLite)
                if (e.getMessage().contains("syntax error") && e.getMessage().contains("ON DUPLICATE KEY")) {
                    // Essayer l'approche alternative pour SQLite
                    return handleSQLiteTransaction(player, shopId, itemId, isBuy, amount, finalTableName);
                }
                plugin.getLogger().severe("Erreur lors de l'enregistrement d'une transaction: " + e.getMessage());
                return 0;
            }
        });
    }

    // Méthode auxiliaire pour gérer les transactions dans SQLite (qui ne supporte pas ON DUPLICATE KEY)
    private Integer handleSQLiteTransaction(Player player, String shopId, String itemId, boolean isBuy, int amount, String tableName) throws InterruptedException, ExecutionException {
        String transactionType = isBuy ? "BUY" : "SELL";
        
        return plugin.getDataManager().executeAsync(() -> {
            try (Connection connection = plugin.getDataManager().getConnection()) {
                // 1. Vérifier si une entrée existe pour aujourd'hui
                String checkQuery = "SELECT amount FROM " + tableName + " " +
                                "WHERE player_uuid = ? AND shop_id = ? AND item_id = ? AND transaction_type = ? " +
                                "AND date(transaction_time) = date('now')";
                                
                int existingAmount = 0;
                boolean hasExisting = false;
                
                try (PreparedStatement checkStmt = connection.prepareStatement(checkQuery)) {
                    checkStmt.setString(1, player.getUniqueId().toString());
                    checkStmt.setString(2, shopId);
                    checkStmt.setString(3, itemId);
                    checkStmt.setString(4, transactionType);
                    
                    ResultSet rs = checkStmt.executeQuery();
                    if (rs.next()) {
                        existingAmount = rs.getInt("amount");
                        hasExisting = true;
                    }
                }
                
                // 2. Mettre à jour ou insérer
                if (hasExisting) {
                    String updateQuery = "UPDATE " + tableName + " " +
                                    "SET amount = ?, transaction_time = datetime('now') " +
                                    "WHERE player_uuid = ? AND shop_id = ? AND item_id = ? AND transaction_type = ? " +
                                    "AND date(transaction_time) = date('now')";
                                    
                    try (PreparedStatement updateStmt = connection.prepareStatement(updateQuery)) {
                        updateStmt.setInt(1, existingAmount + amount);
                        updateStmt.setString(2, player.getUniqueId().toString());
                        updateStmt.setString(3, shopId);
                        updateStmt.setString(4, itemId);
                        updateStmt.setString(5, transactionType);
                        return updateStmt.executeUpdate();
                    }
                } else {
                    String insertQuery = "INSERT INTO " + tableName + " " +
                                    "(player_uuid, shop_id, item_id, transaction_type, amount, transaction_time) " +
                                    "VALUES (?, ?, ?, ?, ?, datetime('now'))";
                                    
                    try (PreparedStatement insertStmt = connection.prepareStatement(insertQuery)) {
                        insertStmt.setString(1, player.getUniqueId().toString());
                        insertStmt.setString(2, shopId);
                        insertStmt.setString(3, itemId);
                        insertStmt.setString(4, transactionType);
                        insertStmt.setInt(5, amount);
                        return insertStmt.executeUpdate();
                    }
                }
            }
        }).get(); // Attention, .get() est bloquant, mais nous sommes déjà dans un contexte async
    }
    
    public CompletableFuture<Boolean> canPerformTransaction(Player player, String shopId, String itemId, boolean isBuy, int amount) {
        TransactionLimit limit = getTransactionLimit(shopId, itemId, isBuy);
        if (limit == null) {
            return CompletableFuture.completedFuture(true);
        }
        
        String tablePrefix = plugin.getDataConfig().getDatabaseTablePrefix();
        String transactionType = isBuy ? "BUY" : "SELL";
        
        LocalDateTime startDate = getStartDateForPeriod(limit.getPeriod());
        // String query = "SELECT SUM(amount) as total FROM " + tablePrefix + "_transaction_limits "
        //         + "WHERE player_uuid = ? AND shop_id = ? AND item_id = ? AND transaction_type = ? "
        //         + "AND transaction_time >= ?";
        
        // Utiliser la vue pour interroger toutes les tables
        String query = "SELECT SUM(amount) as total FROM " + tablePrefix + "_transactions_view " +
                    "WHERE player_uuid = ? AND shop_id = ? AND item_id = ? AND transaction_type = ? " +
                    "AND transaction_time >= ?";
        
        return plugin.getDataManager().executeAsync(() -> {
            try (Connection connection = plugin.getDataManager().getConnection();
                 PreparedStatement stmt = connection.prepareStatement(query)) {
                stmt.setString(1, player.getUniqueId().toString());
                stmt.setString(2, shopId);
                stmt.setString(3, itemId);
                stmt.setString(4, transactionType);
                stmt.setTimestamp(5, java.sql.Timestamp.valueOf(startDate));
                
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    int currentTotal = rs.getInt("total");
                    return currentTotal + amount <= limit.getAmount();
                }
                return true;
            }
        });
    }
    
    private LocalDateTime getStartDateForPeriod(LimitPeriod period) {
        LocalDateTime now = LocalDateTime.now();
        
        switch (period) {
            case DAILY:
                return now.truncatedTo(ChronoUnit.DAYS);
            case WEEKLY:
                return now.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)).truncatedTo(ChronoUnit.DAYS);
            case MONTHLY:
                return now.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS);
            case YEARLY:
                return now.withDayOfYear(1).truncatedTo(ChronoUnit.DAYS);
            case FOREVER:
            default:
                return LocalDateTime.ofEpochSecond(0, 0, ZoneId.systemDefault().getRules().getOffset(now));
        }
    }
    
    public CompletableFuture<Integer> getRemainingAmount(Player player, String shopId, String itemId, boolean isBuy) {
        TransactionLimit limit = getTransactionLimit(shopId, itemId, isBuy);
        if (limit == null) {
            return CompletableFuture.completedFuture(Integer.MAX_VALUE);
        }
        
        String tablePrefix = plugin.getDataConfig().getDatabaseTablePrefix();
        String transactionType = isBuy ? "BUY" : "SELL";
        
        LocalDateTime startDate = getStartDateForPeriod(limit.getPeriod());
        // String query = "SELECT SUM(amount) as total FROM " + tablePrefix + "_transaction_limits "
        //         + "WHERE player_uuid = ? AND shop_id = ? AND item_id = ? AND transaction_type = ? "
        //         + "AND transaction_time >= ?";

        // Utiliser la vue pour interroger toutes les tables
        String query = "SELECT SUM(amount) as total FROM " + tablePrefix + "_transactions_view " +
                    "WHERE player_uuid = ? AND shop_id = ? AND item_id = ? AND transaction_type = ? " +
                    "AND transaction_time >= ?";
        
        return plugin.getDataManager().executeAsync(() -> {
            try (Connection connection = plugin.getDataManager().getConnection();
                 PreparedStatement stmt = connection.prepareStatement(query)) {
                stmt.setString(1, player.getUniqueId().toString());
                stmt.setString(2, shopId);
                stmt.setString(3, itemId);
                stmt.setString(4, transactionType);
                stmt.setTimestamp(5, java.sql.Timestamp.valueOf(startDate));
                
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    int currentTotal = rs.getInt("total");
                    return Math.max(0, limit.getAmount() - currentTotal);
                }
                return limit.getAmount();
            }
        });
    }
    
    private TransactionLimit getTransactionLimit(String shopId, String itemId, boolean isBuy) {
        String cacheKey = shopId + ":" + itemId + ":" + (isBuy ? "buy" : "sell");
        
        // Vérifier le cache
        if (limitCache.containsKey(cacheKey)) {
            Long timestamp = limitCacheTimestamps.get(cacheKey);
            if (timestamp != null && System.currentTimeMillis() - timestamp < CACHE_DURATION) {
                return limitCache.get(cacheKey);
            } else {
                // Cache expiré, nettoyer
                limitCache.remove(cacheKey);
                limitCacheTimestamps.remove(cacheKey);
            }
        }

        try {
            String limitPath = isBuy ? "limit.buy" : "limit.sell";
            int amount = plugin.getShopConfigManager().getItemValue(shopId, itemId, limitPath, Integer.class).orElse(0);
            
            if (amount <= 0) {
                return null;
            }
            
            String periodStr = plugin.getShopConfigManager().getItemValue(shopId, itemId, "limit.period", String.class).orElse("FOREVER");
            LimitPeriod period;
            try {
                period = LimitPeriod.valueOf(periodStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                period = LimitPeriod.FOREVER;
            }
            
            int cooldown = plugin.getShopConfigManager().getItemValue(shopId, itemId, "limit.cooldown", Integer.class).orElse(0);
            
            return new TransactionLimit(amount, period, cooldown);
        } catch (Exception e) {
            plugin.getLogger().severe("Erreur lors de la récupération des limites pour " + shopId + ":" + itemId + ": " + e.getMessage());
            return null;
        }
    }
    
    public CompletableFuture<Long> getNextAvailableTime(Player player, String shopId, String itemId, boolean isBuy) {
        TransactionLimit limit = getTransactionLimit(shopId, itemId, isBuy);
        if (limit == null || limit.getCooldown() <= 0) {
            return CompletableFuture.completedFuture(0L);
        }
        
        String tablePrefix = plugin.getDataConfig().getDatabaseTablePrefix();
        String transactionType = isBuy ? "BUY" : "SELL";
        
        // String query = "SELECT MAX(transaction_time) as latest FROM " + tablePrefix + "_transaction_limits "
        //         + "WHERE player_uuid = ? AND shop_id = ? AND item_id = ? AND transaction_type = ?";

        // Utiliser la vue pour interroger toutes les tables
        String query = "SELECT MAX(transaction_time) as latest FROM " + tablePrefix + "_transactions_view " +
                    "WHERE player_uuid = ? AND shop_id = ? AND item_id = ? AND transaction_type = ?";
        
        return plugin.getDataManager().executeAsync(() -> {
            try (Connection connection = plugin.getDataManager().getConnection();
                 PreparedStatement stmt = connection.prepareStatement(query)) {
                stmt.setString(1, player.getUniqueId().toString());
                stmt.setString(2, shopId);
                stmt.setString(3, itemId);
                stmt.setString(4, transactionType);
                
                ResultSet rs = stmt.executeQuery();
                if (rs.next() && rs.getTimestamp("latest") != null) {
                    java.sql.Timestamp latestTime = rs.getTimestamp("latest");
                    LocalDateTime nextAvailable = latestTime.toLocalDateTime().plusSeconds(limit.getCooldown());
                    return nextAvailable.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                }
                return 0L;
            }
        });
    }

    /**
     * Nettoie les données de transactions périmées de la base de données.
     * Cette méthode supprime toutes les transactions dont la période est expirée.
     */
    public void cleanupExpiredTransactions() {
        String tablePrefix = plugin.getDataConfig().getDatabaseTablePrefix();
        
        // Nettoyer chaque table partitionnée selon sa période spécifique
        LocalDateTime dailyCutoff = getStartDateForPeriod(LimitPeriod.DAILY);
        plugin.getDataManager().executeAsync(() -> {
            try (Connection connection = plugin.getDataManager().getConnection();
                PreparedStatement stmt = connection.prepareStatement(
                    "DELETE FROM " + tablePrefix + "_tx_daily WHERE transaction_time < ?")) {
                stmt.setTimestamp(1, java.sql.Timestamp.valueOf(dailyCutoff));
                int rowsDeleted = stmt.executeUpdate();
                if (rowsDeleted > 0) {
                    plugin.getLogger().fine("Nettoyage des limites: " + rowsDeleted + " transactions quotidiennes supprimées");
                }
                return rowsDeleted;
            } catch (SQLException e) {
                plugin.getLogger().severe("Erreur lors du nettoyage des transactions quotidiennes: " + e.getMessage());
                return 0;
            }
        });
        
        LocalDateTime weeklyCutoff = getStartDateForPeriod(LimitPeriod.WEEKLY);
        plugin.getDataManager().executeAsync(() -> {
            try (Connection connection = plugin.getDataManager().getConnection();
                PreparedStatement stmt = connection.prepareStatement(
                    "DELETE FROM " + tablePrefix + "_tx_weekly WHERE transaction_time < ?")) {
                stmt.setTimestamp(1, java.sql.Timestamp.valueOf(weeklyCutoff));
                return stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Erreur lors du nettoyage des transactions hebdomadaires: " + e.getMessage());
                return 0;
            }
        });
        
        LocalDateTime monthlyCutoff = getStartDateForPeriod(LimitPeriod.MONTHLY);
        plugin.getDataManager().executeAsync(() -> {
            try (Connection connection = plugin.getDataManager().getConnection();
                PreparedStatement stmt = connection.prepareStatement(
                    "DELETE FROM " + tablePrefix + "_tx_monthly WHERE transaction_time < ?")) {
                stmt.setTimestamp(1, java.sql.Timestamp.valueOf(monthlyCutoff));
                return stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Erreur lors du nettoyage des transactions mensuelles: " + e.getMessage());
                return 0;
            }
        });
        
        LocalDateTime yearlyCutoff = getStartDateForPeriod(LimitPeriod.YEARLY);
        plugin.getDataManager().executeAsync(() -> {
            try (Connection connection = plugin.getDataManager().getConnection();
                PreparedStatement stmt = connection.prepareStatement(
                    "DELETE FROM " + tablePrefix + "_tx_yearly WHERE transaction_time < ?")) {
                stmt.setTimestamp(1, java.sql.Timestamp.valueOf(yearlyCutoff));
                return stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Erreur lors du nettoyage des transactions annuelles: " + e.getMessage());
                return 0;
            }
        });
        
        // Nettoyer aussi la table principale pour compatibilité
        plugin.getDataManager().executeAsync(() -> {
            try (Connection connection = plugin.getDataManager().getConnection();
                PreparedStatement stmt = connection.prepareStatement(
                    "DELETE FROM " + tablePrefix + "_transaction_limits WHERE transaction_time < ?")) {
                stmt.setTimestamp(1, java.sql.Timestamp.valueOf(dailyCutoff));
                return stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Erreur lors du nettoyage de la table principale: " + e.getMessage());
                return 0;
            }
        });
    }
    // public void cleanupExpiredTransactions() {
    //     String tablePrefix = plugin.getDataConfig().getDatabaseTablePrefix();
        
    //     // Construire une requête pour chaque type de période
    //     for (LimitPeriod period : LimitPeriod.values()) {
    //         if (period == LimitPeriod.FOREVER) {
    //             continue; // Ne pas nettoyer les limites permanentes
    //         }
            
    //         LocalDateTime cutoffDate = getStartDateForPeriod(period);
    //         String query = "DELETE FROM " + tablePrefix + "_transaction_limits " + "WHERE transaction_time < ?";
            
    //         plugin.getDataManager().executeAsync(() -> {
    //             try (Connection connection = plugin.getDataManager().getConnection();
    //                 PreparedStatement stmt = connection.prepareStatement(query)) {
    //                 stmt.setTimestamp(1, java.sql.Timestamp.valueOf(cutoffDate));
    //                 // int rowsDeleted = stmt.executeUpdate();
    //                 return stmt.executeUpdate();
                    
    //                 // if (rowsDeleted > 0) {
    //                 //     plugin.getLogger().info("Nettoyage des limites: " + rowsDeleted + 
    //                 //                         " transactions " + period.name() + " périmées supprimées");
    //                 // }
                    
    //                 // return rowsDeleted;
    //             } catch (SQLException e) {
    //                 plugin.getLogger().severe("Erreur lors du nettoyage des transactions: " + e.getMessage());
    //                 return 0;
    //             }
    //         });
    //     }
    // }

    /**
     * Nettoie les transactions expirées liées au cooldown.
     */
    public void cleanupCooldownTransactions() {
        String tablePrefix = plugin.getDataConfig().getDatabaseTablePrefix();
        
        // La requête supprime les transactions dont le cooldown est expiré
        String query = "DELETE t1 FROM " + tablePrefix + "_transaction_limits t1 " +
                    "INNER JOIN " + tablePrefix + "_transaction_limits t2 ON " +
                    "t1.player_uuid = t2.player_uuid AND t1.shop_id = t2.shop_id AND " +
                    "t1.item_id = t2.item_id AND t1.transaction_type = t2.transaction_type " +
                    "WHERE t1.transaction_time < t2.transaction_time";
        
        // Version alternative pour SQLite qui ne supporte pas les JOINs dans DELETE
        String sqliteQuery = "DELETE FROM " + tablePrefix + "_transaction_limits " +
                            "WHERE transaction_time NOT IN (SELECT MAX(transaction_time) FROM " + 
                            tablePrefix + "_transaction_limits GROUP BY player_uuid, shop_id, item_id, transaction_type)";
        
        plugin.getDataManager().executeAsync(() -> {
            try (Connection connection = plugin.getDataManager().getConnection();
                PreparedStatement stmt = connection.prepareStatement(
                    plugin.getDataConfig().getDatabaseType().equals("mysql") ? query : sqliteQuery)) {
                int rowsDeleted = stmt.executeUpdate();
                
                // if (rowsDeleted > 0) {
                //     plugin.getLogger().info("Nettoyage des cooldowns: " + rowsDeleted + " transactions supprimées");
                // }
                
                return rowsDeleted;
            } catch (SQLException e) {
                plugin.getLogger().severe("Erreur lors du nettoyage des cooldowns: " + e.getMessage());
                return 0;
            }
        });
    }
    
    private static class TransactionLimit {
        private final int amount;
        private final LimitPeriod period;
        private final int cooldown;
        
        public TransactionLimit(int amount, LimitPeriod period, int cooldown) {
            this.amount = amount;
            this.period = period;
            this.cooldown = cooldown;
        }
        
        public int getAmount() {
            return amount;
        }
        
        public LimitPeriod getPeriod() {
            return period;
        }
        
        public int getCooldown() {
            return cooldown;
        }
    }
    
    // public CompletableFuture<Boolean> resetLimits(Player player, String shopId, String itemId) {
    //     String tablePrefix = plugin.getDataConfig().getDatabaseTablePrefix();
    //     String query = "DELETE FROM " + tablePrefix + "_transaction_limits "
    //             + "WHERE player_uuid = ? AND shop_id = ? AND item_id = ?";
        
    //     return plugin.getDataManager().executeAsync(() -> {
    //         try (Connection connection = plugin.getDataManager().getConnection();
    //              PreparedStatement stmt = connection.prepareStatement(query)) {
    //             stmt.setString(1, player.getUniqueId().toString());
    //             stmt.setString(2, shopId);
    //             stmt.setString(3, itemId);
                
    //             return stmt.executeUpdate() >= 0;
    //         }
    //     });
    // }
    public CompletableFuture<Boolean> resetLimits(Player player, String shopId, String itemId) {
        String tablePrefix = plugin.getDataConfig().getDatabaseTablePrefix();
        
        // Utiliser une CompletableFuture composite pour attendre tous les résultats
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        
        // Pour chaque table partitionnée
        String[] tables = {
            tablePrefix + "_tx_daily",
            tablePrefix + "_tx_weekly",
            tablePrefix + "_tx_monthly",
            tablePrefix + "_tx_yearly",
            tablePrefix + "_tx_forever",
            tablePrefix + "_transaction_limits" // Table principale aussi
        };
        
        for (String table : tables) {
            String query = "DELETE FROM " + table + " WHERE player_uuid = ? AND shop_id = ? AND item_id = ?";
            
            CompletableFuture<Boolean> future = plugin.getDataManager().executeAsync(() -> {
                try (Connection connection = plugin.getDataManager().getConnection();
                    PreparedStatement stmt = connection.prepareStatement(query)) {
                    stmt.setString(1, player.getUniqueId().toString());
                    stmt.setString(2, shopId);
                    stmt.setString(3, itemId);
                    
                    return stmt.executeUpdate() >= 0;
                }
            });
            
            futures.add(future);
        }
        
        // Combiner tous les résultats
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream().allMatch(CompletableFuture::join));
    }

    // public void recordBulkTransactions(List<TransactionRecord> records) {
    //     if (records.isEmpty()) {
    //         return;
    //     }
        
    //     String tablePrefix = plugin.getDataConfig().getDatabaseTablePrefix();
    //     String query = "INSERT INTO " + tablePrefix + "_transaction_limits "
    //             + "(player_uuid, shop_id, item_id, transaction_type, amount, transaction_time) "
    //             + "VALUES (?, ?, ?, ?, ?, NOW())";
        
    //     plugin.getDataManager().executeAsync(() -> {
    //         try (Connection connection = plugin.getDataManager().getConnection();
    //             PreparedStatement stmt = connection.prepareStatement(query)) {
                
    //             connection.setAutoCommit(false);
                
    //             for (TransactionRecord record : records) {
    //                 stmt.setString(1, record.playerUuid.toString());
    //                 stmt.setString(2, record.shopId);
    //                 stmt.setString(3, record.itemId);
    //                 stmt.setString(4, record.isBuy ? "BUY" : "SELL");
    //                 stmt.setInt(5, record.amount);
    //                 stmt.addBatch();
    //             }
                
    //             int[] results = stmt.executeBatch();
    //             connection.commit();
    //             connection.setAutoCommit(true);
                
    //             return results.length;
    //         }
    //     });
    // }
    public void recordBulkTransactions(List<TransactionRecord> records) {
        if (records.isEmpty()) {
            return;
        }
        
        // Regrouper les transactions par période (basée sur le type de limite)
        Map<String, List<TransactionRecord>> recordsByTable = new HashMap<>();
        
        for (TransactionRecord record : records) {
            TransactionLimit limit = getTransactionLimit(record.shopId, record.itemId, record.isBuy);
            String tablePrefix = plugin.getDataConfig().getDatabaseTablePrefix();
            String tableName = tablePrefix + "_transaction_limits"; // Table par défaut
            
            if (limit != null) {
                // Sélectionner la table partitionnée appropriée
                switch (limit.getPeriod()) {
                    case DAILY:
                        tableName = tablePrefix + "_tx_daily";
                        break;
                    case WEEKLY:
                        tableName = tablePrefix + "_tx_weekly";
                        break;
                    case MONTHLY:
                        tableName = tablePrefix + "_tx_monthly";
                        break;
                    case YEARLY:
                        tableName = tablePrefix + "_tx_yearly";
                        break;
                    case FOREVER:
                        tableName = tablePrefix + "_tx_forever";
                        break;
                }
            }
            
            recordsByTable.computeIfAbsent(tableName, k -> new ArrayList<>()).add(record);
        }
        
        // Traiter chaque groupe de transactions pour sa table spécifique
        for (Map.Entry<String, List<TransactionRecord>> entry : recordsByTable.entrySet()) {
            String tableName = entry.getKey();
            List<TransactionRecord> tableRecords = entry.getValue();
            
            String query = "INSERT INTO " + tableName + " "
                    + "(player_uuid, shop_id, item_id, transaction_type, amount, transaction_time) "
                    + "VALUES (?, ?, ?, ?, ?, NOW())";
            
            plugin.getDataManager().executeAsync(() -> {
                try (Connection connection = plugin.getDataManager().getConnection();
                    PreparedStatement stmt = connection.prepareStatement(query)) {
                    
                    connection.setAutoCommit(false);
                    
                    for (TransactionRecord record : tableRecords) {
                        stmt.setString(1, record.playerUuid.toString());
                        stmt.setString(2, record.shopId);
                        stmt.setString(3, record.itemId);
                        stmt.setString(4, record.isBuy ? "BUY" : "SELL");
                        stmt.setInt(5, record.amount);
                        stmt.addBatch();
                    }
                    
                    int[] results = stmt.executeBatch();
                    connection.commit();
                    connection.setAutoCommit(true);
                    
                    return results.length;
                }
            });
        }
    }

    // Classe interne pour les enregistrements de transaction en lot
    public static class TransactionRecord {
        public final UUID playerUuid;
        public final String shopId;
        public final String itemId;
        public final boolean isBuy;
        public final int amount;
        
        public TransactionRecord(UUID playerUuid, String shopId, String itemId, boolean isBuy, int amount) {
            this.playerUuid = playerUuid;
            this.shopId = shopId;
            this.itemId = itemId;
            this.isBuy = isBuy;
            this.amount = amount;
        }
    }

    public void optimizeDatabase() {
        // plugin.getLogger().info("Optimisation de la base de données des limites...");
        
        String tablePrefix = plugin.getDataConfig().getDatabaseTablePrefix();
        
        // Créer des tables partitionnées par période - Syntaxe corrigée pour MySQL/MariaDB
        String[] periodTables = {
            "CREATE TABLE IF NOT EXISTS " + tablePrefix + "_tx_daily LIKE " + tablePrefix + "_transaction_limits",
                
            "CREATE TABLE IF NOT EXISTS " + tablePrefix + "_tx_weekly LIKE " + tablePrefix + "_transaction_limits",
                
            "CREATE TABLE IF NOT EXISTS " + tablePrefix + "_tx_monthly LIKE " + tablePrefix + "_transaction_limits",
                
            "CREATE TABLE IF NOT EXISTS " + tablePrefix + "_tx_yearly LIKE " + tablePrefix + "_transaction_limits",
                
            "CREATE TABLE IF NOT EXISTS " + tablePrefix + "_tx_forever LIKE " + tablePrefix + "_transaction_limits"
        };
        
        for (String query : periodTables) {
            try {
                plugin.getDataManager().executeUpdate(query);
            } catch (Exception e) {
                plugin.getLogger().warning("Erreur lors de la création de table: " + e.getMessage());
            }
        }
        
        // Créer une vue pour simplifier les requêtes
        String viewQuery = "CREATE OR REPLACE VIEW " + tablePrefix + "_transactions_view AS "
            + "SELECT * FROM " + tablePrefix + "_tx_daily UNION ALL "
            + "SELECT * FROM " + tablePrefix + "_tx_weekly UNION ALL "
            + "SELECT * FROM " + tablePrefix + "_tx_monthly UNION ALL "
            + "SELECT * FROM " + tablePrefix + "_tx_yearly UNION ALL "
            + "SELECT * FROM " + tablePrefix + "_tx_forever UNION ALL "
            + "SELECT * FROM " + tablePrefix + "_transaction_limits";
            
        try {
            plugin.getDataManager().executeUpdate(viewQuery);
        } catch (Exception e) {
            // Si la vue ne peut pas être créée (par exemple avec SQLite), on continue sans erreur critique
            plugin.getLogger().warning("Note: Impossible de créer la vue de transactions (normal pour SQLite): " + e.getMessage());
        }
    }

    // Méthode pour obtenir des statistiques sur l'utilisation
    public CompletableFuture<Map<String, Object>> getStatistics() {
        String tablePrefix = plugin.getDataConfig().getDatabaseTablePrefix();
        
        return plugin.getDataManager().executeAsync(() -> {
            Map<String, Object> stats = new HashMap<>();
            
            try (Connection connection = plugin.getDataManager().getConnection()) {
                // Total de transactions
                try (PreparedStatement stmt = connection.prepareStatement(
                        "SELECT COUNT(*) AS total FROM " + tablePrefix + "_transaction_limits")) {
                    ResultSet rs = stmt.executeQuery();
                    if (rs.next()) {
                        stats.put("total_records", rs.getInt("total"));
                    }
                }
                
                // Transactions par type
                try (PreparedStatement stmt = connection.prepareStatement(
                        "SELECT transaction_type, COUNT(*) AS count FROM " + tablePrefix + "_transaction_limits " +
                        "GROUP BY transaction_type")) {
                    ResultSet rs = stmt.executeQuery();
                    while (rs.next()) {
                        stats.put("count_" + rs.getString("transaction_type").toLowerCase(), rs.getInt("count"));
                    }
                }
                
                // Plus ancien enregistrement
                try (PreparedStatement stmt = connection.prepareStatement(
                        "SELECT MIN(transaction_time) AS oldest FROM " + tablePrefix + "_transaction_limits")) {
                    ResultSet rs = stmt.executeQuery();
                    if (rs.next() && rs.getTimestamp("oldest") != null) {
                        stats.put("oldest_record", rs.getTimestamp("oldest").toString());
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Erreur lors de la récupération des statistiques: " + e.getMessage());
            }
            
            return stats;
        });
    }
}