package fr.tylwen.satyria.dynashop.system;

import fr.tylwen.satyria.dynashop.DynaShopPlugin;
// import fr.tylwen.satyria.dynashop.system.TransactionLimiter.LimitPeriod;
// import fr.tylwen.satyria.dynashop.system.TransactionLimiter.TransactionLimit;

// import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
// import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
// import java.util.Calendar;
// import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
// import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

public class TransactionLimiter {
    private final DynaShopPlugin plugin;
    
    private final Map<String, TransactionRecord> pendingTransactions = new ConcurrentHashMap<>();
    private final Thread transactionFlushThread;
    private final AtomicBoolean running = new AtomicBoolean(true);
    
    private final Map<String, TransactionLimit> limitCache = new ConcurrentHashMap<>();
    private final long CACHE_DURATION = 60000; // 1 minute en millisecondes
    private final Map<String, Long> limitCacheTimestamps = new ConcurrentHashMap<>();
    
    private final Map<String, Integer> transactionCounters = new ConcurrentHashMap<>();
    private long lastMetricsReset = System.currentTimeMillis();
    
    // public enum LimitPeriod {
    //     DAILY, WEEKLY, MONTHLY, YEARLY, FOREVER, NONE
    // }
    public enum LimitPeriod {
        DAILY(86400),    // 24 heures
        WEEKLY(604800),  // 7 jours
        MONTHLY(2592000),  // 30 jours
        YEARLY(31536000),  // 365 jours
        FOREVER(Integer.MAX_VALUE),
        NONE(0);
        
        private final int seconds;
        
        LimitPeriod(int seconds) {
            this.seconds = seconds;
        }
        
        public int getSeconds() {
            return seconds;
        }
        
        public static LimitPeriod fromString(String value) {
            try {
                return valueOf(value.toUpperCase());
            } catch (IllegalArgumentException e) {
                // Si c'est un nombre, considérer comme des secondes
                try {
                    int seconds = Integer.parseInt(value);
                    for (LimitPeriod period : values()) {
                        if (period.getSeconds() == seconds) {
                            return period;
                        }
                    }
                    return NONE; // Valeur personnalisée
                } catch (NumberFormatException ex) {
                    return NONE; // Valeur non reconnue
                }
            }
        }
    }
    
    public TransactionLimiter(DynaShopPlugin plugin) {
        this.plugin = plugin;
        initDatabase();
        // Appeler l'optimisation de la base de données au démarrage
        // plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::optimizeDatabase);
        
        // Planifier une optimisation périodique (une fois par jour)
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, 
            // this::optimizeDatabase,
            this::cleanupExpiredTransactions,  // Au lieu de optimizeDatabase
            20L * 60L * 60L * 12L, // 12 heures après le démarrage
            20L * 60L * 60L * 24L  // Répéter toutes les 24 heures
        );
        
        // Thread de flush périodique
        transactionFlushThread = Thread.ofVirtual()
            .name("DynaShop-TxFlusher")
            .start(() -> {
                while (running.get()) {
                    try {
                        flushPendingTransactions();
                        Thread.sleep(1000); // flush toutes les secondes
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                flushPendingTransactions();
            });
    }

    // Pour sauvegarder les transactions en attente
    public void queueTransaction(Player player, String shopId, String itemId, boolean isBuy, int amount) {
        String key = player.getUniqueId() + ":" + shopId + ":" + itemId + ":" + (isBuy ? "BUY" : "SELL");
        pendingTransactions.merge(key, new TransactionRecord(player.getUniqueId(), shopId, itemId, isBuy, amount),
            (oldRec, newRec) -> new TransactionRecord(
                oldRec.playerUuid, oldRec.shopId, oldRec.itemId, oldRec.isBuy, oldRec.amount + newRec.amount
            )
        );
    }
    private void flushPendingTransactions() {
        if (pendingTransactions.isEmpty()) return;
        Map<String, TransactionRecord> toFlush = new HashMap<>(pendingTransactions);
        pendingTransactions.clear();

        for (TransactionRecord record : toFlush.values()) {
            // Utilise ta logique existante pour insérer/mise à jour dans la bonne table
            recordTransaction(
                // Tu dois pouvoir retrouver le Player à partir de l'UUID si besoin
                plugin.getServer().getPlayer(record.playerUuid),
                record.shopId, record.itemId, record.isBuy, record.amount
            );
        }
    }
    public void shutdown() {
        running.set(false);
        transactionFlushThread.interrupt();
        try {
            // Attendre la fin du thread avec un timeout raisonnable
            transactionFlushThread.join(5000);
        } catch (InterruptedException e) {
            plugin.getLogger().warning("Interrupted while waiting for database updater to finish");
            // Restaurer l'état d'interruption
            Thread.currentThread().interrupt();
        }
        
        // Si le thread ne s'est pas terminé proprement, forcer une dernière mise à jour
        if (transactionFlushThread.isAlive()) {
            plugin.getLogger().warning("Database updater thread did not terminate gracefully");
            flushPendingTransactions();
        }
    }

    public Map<String, Integer> getMetrics() {
        return new HashMap<>(transactionCounters);
    }

    public void incrementCounter(String metricName) {
        transactionCounters.merge(metricName, 1, Integer::sum);
    }
    
    private void initDatabase() {
        // String tablePrefix = plugin.getDataConfig().getDatabaseTablePrefix();
        // String query = "CREATE TABLE IF NOT EXISTS " + tablePrefix + "_transaction_limits ("
        //         + "player_uuid VARCHAR(36) NOT NULL, "
        //         + "shop_id VARCHAR(100) NOT NULL, "
        //         + "item_id VARCHAR(100) NOT NULL, "
        //         + "transaction_type VARCHAR(10) NOT NULL, " // 'BUY' ou 'SELL'
        //         + "amount INT NOT NULL, "
        //         + "transaction_time TIMESTAMP NOT NULL, "
        //         + "PRIMARY KEY (player_uuid, shop_id, item_id, transaction_type, transaction_time)"
        //         + ")";
        
        // plugin.getDataManager().executeUpdate(query);

        // // Optionnel : ajouter un index pour améliorer les performances des requêtes
        
        // // Ajout d'index pour accélérer les requêtes fréquentes
        // String[] indexes = {
        //     // Index sur transaction_time pour accélérer les nettoyages
        //     "CREATE INDEX IF NOT EXISTS " + tablePrefix + "_tx_time_idx ON " 
        //         + tablePrefix + "_transaction_limits (transaction_time)",
            
        //     // Index sur player_uuid pour accélérer les requêtes par joueur
        //     "CREATE INDEX IF NOT EXISTS " + tablePrefix + "_player_idx ON " 
        //         + tablePrefix + "_transaction_limits (player_uuid)",
            
        //     // Index composite pour les requêtes de récapitulation
        //     "CREATE INDEX IF NOT EXISTS " + tablePrefix + "_lookup_idx ON " 
        //         + tablePrefix + "_transaction_limits (player_uuid, shop_id, item_id, transaction_type)"
        // };
        
        // for (String indexQuery : indexes) {
        //     try {
        //         plugin.getDataManager().executeUpdate(indexQuery);
        //     } catch (Exception e) {
        //         plugin.getLogger().warning("Erreur lors de la création d'index: " + e.getMessage());
        //     }
        // }
        
        // Créer toutes les tables nécessaires
        createTransactionTables();
    }

    /**
     * Crée toutes les tables nécessaires pour le système de limites de transactions.
     * Compatible avec MySQL et SQLite.
     */
    private void createTransactionTables() {
        String tablePrefix = plugin.getDataConfig().getDatabaseTablePrefix();
        boolean isMySQL = plugin.getDataConfig().getDatabaseType().equalsIgnoreCase("mysql");
        boolean isSQLite = plugin.getDataConfig().getDatabaseType().equalsIgnoreCase("sqlite");
        
        // Structure de base d'une table de transactions
        String baseTableStructure =
                "player_uuid VARCHAR(36) NOT NULL, " +
                "shop_id VARCHAR(100) NOT NULL, " +
                "item_id VARCHAR(100) NOT NULL, " +
                // "transaction_type ENUM('BUY', 'SELL') NOT NULL, " + 
                "transaction_type VARCHAR(10) NOT NULL, " + 
                "amount INT NOT NULL, " +
                "transaction_time TIMESTAMP NOT NULL, " +
                // "transaction_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                "PRIMARY KEY (player_uuid, shop_id, item_id, transaction_type, transaction_time)";
        
        // Tableau des tables à créer
        String[] tablesToCreate = {
            tablePrefix + "_transaction_limits",  // Table principale
            tablePrefix + "_tx_daily",            // Limites quotidiennes
            tablePrefix + "_tx_weekly",           // Limites hebdomadaires
            tablePrefix + "_tx_monthly",          // Limites mensuelles
            tablePrefix + "_tx_yearly",           // Limites annuelles
            tablePrefix + "_tx_forever"           // Limites permanentes
        };
        
        for (String tableName : tablesToCreate) {
            if (isMySQL && !tableName.equals(tablePrefix + "_transaction_limits")) {
                // Avec MySQL, on peut utiliser LIKE pour les tables secondaires

                String query = "CREATE TABLE IF NOT EXISTS " + tableName + " LIKE " + tablePrefix + "_transaction_limits";
                plugin.getDataManager().executeUpdate(query);
            } else {
                // Création complète de la structure pour SQLite ou la table principale MySQL
                String query = "CREATE TABLE IF NOT EXISTS " + tableName + " (" + baseTableStructure + ")";
                plugin.getDataManager().executeUpdate(query);
            }
        }
        
        // Création des index pour chaque table
        for (String tableName : tablesToCreate) {
            createIndexesForTable(tableName);
        }
        
        // Création de la vue (pour MySQL uniquement)
        if (isMySQL) {
            createTransactionsView(tablePrefix, tablesToCreate);
        // } else {
        //     // Pour SQLite, on n'utilise pas de vue, mais on peut créer une table de transactions globale
        //     String query = "CREATE TABLE IF NOT EXISTS " + tablePrefix + "_transactions AS SELECT * FROM " + tablePrefix + "_transaction_limits WHERE 1=0";
        //     plugin.getDataManager().executeUpdate(query);
        }
    }

    /**
     * Crée les index nécessaires pour une table de transactions
     */
    private void createIndexesForTable(String tableName) {
        boolean isMysql = plugin.getDataConfig().getDatabaseType().equals("mysql");
        
        if (isMysql) {
            // Syntaxe MySQL
            String[] indexes = {
                "CREATE INDEX IF NOT EXISTS " + tableName + "_time_idx ON " + tableName + " (transaction_time)",
                "CREATE INDEX IF NOT EXISTS " + tableName + "_player_idx ON " + tableName + " (player_uuid)",
                "CREATE INDEX IF NOT EXISTS " + tableName + "_lookup_idx ON " + tableName + " (player_uuid, shop_id, item_id, transaction_type)"
            };
            
            for (String indexQuery : indexes) {
                try {
                    plugin.getDataManager().executeUpdate(indexQuery);
                } catch (Exception e) {
                    plugin.getLogger().warning("Erreur lors de la création d'index pour " + tableName + ": " + e.getMessage());
                }
            }
        } else {
            // Syntaxe SQLite - créer les index un par un avec des noms plus simples
            try {
                plugin.getDataManager().executeUpdate("CREATE INDEX IF NOT EXISTS " + tableName + "_time_idx ON " + tableName + " (transaction_time)");

                plugin.getDataManager().executeUpdate("CREATE INDEX IF NOT EXISTS " + tableName + "_player_idx ON " + tableName + " (player_uuid)");

                plugin.getDataManager().executeUpdate("CREATE INDEX IF NOT EXISTS " + tableName + "_lookup_idx ON " + tableName + " (player_uuid, shop_id, item_id, transaction_type)");
            } catch (Exception e) {
                plugin.getLogger().warning("Erreur lors de la création d'index SQLite pour " + tableName + ": " + e.getMessage());
            }
        }
    }

    // /**
    //  * Crée une vue qui combine toutes les tables de transactions (MySQL uniquement)
    //  */
    // private void createTransactionsView(String tablePrefix, String[] tables) {
    //     try {
    //         StringBuilder viewQuery = new StringBuilder();
    //         viewQuery.append("CREATE OR REPLACE VIEW ").append(tablePrefix).append("_transactions_view AS ");
            
    //         for (int i = 0; i < tables.length; i++) {
    //             if (i > 0) {
    //                 viewQuery.append(" UNION ALL ");
    //             }
    //             viewQuery.append("SELECT * FROM ").append(tables[i]);
    //         }
            
    //         plugin.getDataManager().executeUpdate(viewQuery.toString());
    //     } catch (Exception e) {
    //         // Si la vue ne peut pas être créée, on continue sans erreur critique
    //         plugin.getLogger().warning("Note: Impossible de créer la vue de transactions: " + e.getMessage());
    //     }
    // }
    /**
     * Crée une vue qui combine toutes les tables de transactions (compatible MySQL et SQLite)
     */
    private void createTransactionsView(String tablePrefix, String[] tables) {
        try {
            // Vérifier si nous utilisons MySQL ou SQLite
            boolean isMysql = plugin.getDataConfig().getDatabaseType().equals("mysql");
            
            // Supprimer la vue existante si elle existe
            plugin.getDataManager().executeUpdate("DROP VIEW IF EXISTS " + tablePrefix + "_transactions_view");
            
            // Construction de la requête SQL pour créer la vue
            StringBuilder viewSQL = new StringBuilder();
            viewSQL.append("CREATE ");
            if (!isMysql) {
                // SQLite n'a pas de "OR REPLACE"
                viewSQL.append("VIEW ");
            } else {
                // MySQL permet "OR REPLACE"
                viewSQL.append("OR REPLACE VIEW ");
            }
            viewSQL.append(tablePrefix).append("_transactions_view AS ");
            
            for (int i = 0; i < tables.length; i++) {
                if (i > 0) viewSQL.append(" UNION ALL ");
                viewSQL.append("SELECT * FROM ").append(tables[i]);
            }
            
            // Exécuter la requête SQL
            plugin.getDataManager().executeUpdate(viewSQL.toString());
            
        } catch (Exception e) {
            // Message d'erreur plus informatif
            plugin.getLogger().warning("Note: Impossible de créer la vue des transactions (" + 
                                    (plugin.getDataConfig().getDatabaseType().equals("mysql") ? "MySQL" : "SQLite") + 
                                    "): " + e.getMessage());
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
            // Déterminer la table à utiliser en fonction du cooldown
            LimitPeriod equivalentPeriod = limit.getPeriodEquivalent();
            // Sélectionner la table partitionnée appropriée
            switch (equivalentPeriod) {
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
                case NONE:
                default:
                    tableName = tablePrefix + "_transaction_limits"; // Table principale
                    break;
            }
        }

        // Créer une copie finale de tableName pour utilisation dans la lambda
        final String finalTableName = tableName;
        boolean isMysql = plugin.getDataConfig().getDatabaseType().equals("mysql");
        String transactionType = isBuy ? "BUY" : "SELL";

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
            try (Connection connection = plugin.getDataManager().getConnection()) {
                // plugin.getLogger().info("[DEBUG] recordTransaction : isMysql=" + isMysql + ", table=" + finalTableName);
                if (isMysql) {
                    // Reste de votre code existant en utilisant tableName au lieu de tablePrefix + "_transaction_limits"
                    // Utiliser INSERT ... ON DUPLICATE KEY UPDATE pour éviter les doublons
                    String query = "INSERT INTO " + finalTableName + " "
                            + "(player_uuid, shop_id, item_id, transaction_type, amount, transaction_time) "
                            + "VALUES (?, ?, ?, ?, ?, NOW()) "
                            + "ON DUPLICATE KEY UPDATE amount = amount + ?, transaction_time = NOW()";

                    try (PreparedStatement stmt = connection.prepareStatement(query)) {
                        stmt.setString(1, player.getUniqueId().toString());
                        stmt.setString(2, shopId);
                        stmt.setString(3, itemId);
                        stmt.setString(4, transactionType);
                        stmt.setInt(5, amount);
                        stmt.setInt(6, amount);
                    
                        // Exécuter la requête et retourner le nombre de lignes affectées
                        int result = stmt.executeUpdate();
                        
                        // Si transaction réussie, vérifier si un nettoyage est nécessaire
                        if (result > 0 && Math.random() < 0.01) { // 1% de chance de déclencher un nettoyage
                        // if (result > 0) {
                            cleanupExpiredTransactions();
                        }
                        
                        return result;
                    }
                } else {
                    return handleSQLiteTransaction(player, shopId, itemId, isBuy, amount, finalTableName);
                    // return null;
                }
            } catch (SQLException e) {
                // Si l'erreur est due à une syntaxe ON DUPLICATE KEY non supportée (SQLite)
                // if (e.getMessage().contains("syntax error") && e.getMessage().contains("ON DUPLICATE KEY")) {
                    // Essayer l'approche alternative pour SQLite
                // }
                plugin.getLogger().severe("Erreur lors de l'enregistrement d'une transaction: " + e.getMessage());
                return 0;
            }
        });
    }

    // // Méthode auxiliaire pour gérer les transactions dans SQLite (qui ne supporte pas ON DUPLICATE KEY)
    // private Integer handleSQLiteTransaction(Player player, String shopId, String itemId, boolean isBuy, int amount, String tableName) {
    //     String transactionType = isBuy ? "BUY" : "SELL";
    //     String today = java.time.LocalDate.now().toString();
        
    //     // return plugin.getDataManager().executeAsync(() -> {
    //         try (Connection connection = plugin.getDataManager().getConnection()) {
    //             // 1. Vérifier si une entrée existe pour aujourd'hui
    //             String checkQuery = "SELECT amount, transaction_time FROM " + tableName + " " +
    //                             "WHERE player_uuid = ? AND shop_id = ? AND item_id = ? AND transaction_type = ? " +
    //                             // "AND date(transaction_time) = date('now')";
    //                             "AND strftime('%Y-%m-%d', transaction_time) = ?";
    //                             // "AND strftime('%Y-%m-%d', transaction_time) = strftime('%Y-%m-%d', 'now')";
    //                             // "ORDER BY transaction_time DESC LIMIT 1";
                
    //             int existingAmount = 0;
    //             java.sql.Timestamp existingTime = null;
    //             boolean hasExisting = false;

    //             try (PreparedStatement checkStmt = connection.prepareStatement(checkQuery)) {
    //                 checkStmt.setString(1, player.getUniqueId().toString());
    //                 checkStmt.setString(2, shopId);
    //                 checkStmt.setString(3, itemId);
    //                 checkStmt.setString(4, transactionType);
    //                 checkStmt.setString(5, today);

    //                 ResultSet rs = checkStmt.executeQuery();
    //                 if (rs.next()) {
    //                     existingAmount = rs.getInt("amount");
    //                     existingTime = rs.getTimestamp("transaction_time");
    //                     hasExisting = true;
    //                 }
    //             }
    //             plugin.info("Transaction pour " + player.getName() + ": " + 
    //                     (hasExisting ? "Mise à jour de " + existingAmount + " à " + (existingAmount + amount) : 
    //                     "Insertion de " + amount) + 
    //                     (existingTime != null ? " (Dernière: " + existingTime + ")" : ""));
                
    //             // 2. Mettre à jour ou insérer
    //             if (hasExisting) {
    //                 String updateQuery = "UPDATE " + tableName + " " +
    //                                 "SET amount = ?, transaction_time = datetime('now') " +
    //                                 "WHERE player_uuid = ? AND shop_id = ? AND item_id = ? AND transaction_type = ? " +
    //                                 "AND strftime('%Y-%m-%d', transaction_time) = ?";
                                    
    //                 try (PreparedStatement updateStmt = connection.prepareStatement(updateQuery)) {
    //                     updateStmt.setInt(1, existingAmount + amount);
    //                     updateStmt.setString(2, player.getUniqueId().toString());
    //                     updateStmt.setString(3, shopId);
    //                     updateStmt.setString(4, itemId);
    //                     updateStmt.setString(5, transactionType);
    //                     updateStmt.setString(6, today);
    //                     return updateStmt.executeUpdate();
    //                 }
    //             } else {
    //                 plugin.info("Aucune transaction existante pour " + player.getName() + " sur " + shopId + ":" + itemId + ". Insertion de la nouvelle transaction.");
    //                 String insertQuery = "INSERT INTO " + tableName + " " +
    //                                 "(player_uuid, shop_id, item_id, transaction_type, amount, transaction_time) " +
    //                                 // "VALUES (?, ?, ?, ?, ?, datetime('now'))";
    //                                 "VALUES (?, ?, ?, ?, ?, datetime('now', 'localtime'))";
                                    
    //                 try (PreparedStatement insertStmt = connection.prepareStatement(insertQuery)) {
    //                     insertStmt.setString(1, player.getUniqueId().toString());
    //                     insertStmt.setString(2, shopId);
    //                     insertStmt.setString(3, itemId);
    //                     insertStmt.setString(4, transactionType);
    //                     insertStmt.setInt(5, amount);
    //                     return insertStmt.executeUpdate();
    //                 } catch (SQLException e) {
    //                     plugin.getLogger().severe("Erreur lors de l'insertion d'une transaction SQLite: " + e.getMessage());
    //                     return 0;
    //                 }
    //             }
    //     } catch (SQLException e) {
    //         plugin.getLogger().severe("Erreur SQLite dans handleSQLiteTransaction: " + e.getMessage());
    //         return 0;
    //     }
    //     // }).get(); // Attention, .get() est bloquant, mais nous sommes déjà dans un contexte async
    // }

    // private Integer handleSQLiteTransaction(Player player, String shopId, String itemId, boolean isBuy, int amount, String tableName) {
    //     plugin.getLogger().info("[DEBUG] handleSQLiteTransaction appelée pour " + tableName + " / " + player.getName());
    //     String transactionType = isBuy ? "BUY" : "SELL";
    //     String today = java.time.LocalDate.now().toString();

    //     try (Connection connection = plugin.getDataManager().getConnection()) {
    //         // DEBUG : Affiche les valeurs présentes
    //         try (PreparedStatement debugStmt = connection.prepareStatement(
    //                 "SELECT amount, transaction_time FROM " + tableName + " WHERE player_uuid = ? AND shop_id = ? AND item_id = ? AND transaction_type = ?")) {
    //             debugStmt.setString(1, player.getUniqueId().toString());
    //             debugStmt.setString(2, shopId);
    //             debugStmt.setString(3, itemId);
    //             debugStmt.setString(4, transactionType);
    //             ResultSet debugRs = debugStmt.executeQuery();
    //             while (debugRs.next()) {
    //                 plugin.getLogger().info("[DEBUG] " + tableName + " : " + debugRs.getString("transaction_time"));
    //             }
    //         } catch (Exception e) {
    //             plugin.getLogger().warning("[DEBUG] Impossible de lire les transactions existantes : " + e.getMessage());
    //         }

    //         // 1. Vérifier si une entrée existe pour aujourd'hui
    //         String checkQuery = "SELECT amount, transaction_time FROM " + tableName + " " +
    //             "WHERE player_uuid = ? AND shop_id = ? AND item_id = ? AND transaction_type = ? " +
    //             "AND strftime('%Y-%m-%d', transaction_time) = ?";
    //         int existingAmount = 0;
    //         boolean hasExisting = false;
    //         java.sql.Timestamp existingTime = null;

    //         try (PreparedStatement checkStmt = connection.prepareStatement(checkQuery)) {
    //             checkStmt.setString(1, player.getUniqueId().toString());
    //             checkStmt.setString(2, shopId);
    //             checkStmt.setString(3, itemId);
    //             checkStmt.setString(4, transactionType);
    //             checkStmt.setString(5, today);

    //             ResultSet rs = checkStmt.executeQuery();
    //             if (rs.next()) {
    //                 existingAmount = rs.getInt("amount");
    //                 existingTime = rs.getTimestamp("transaction_time");
    //                 hasExisting = true;
    //             }
    //         }

    //         plugin.info("Transaction pour " + player.getName() + ": " +
    //             (hasExisting ? "Mise à jour de " + existingAmount + " à " + (existingAmount + amount) :
    //             "Insertion de " + amount) +
    //             (existingTime != null ? " (Dernière: " + existingTime + ")" : ""));

    //         // 2. Mettre à jour ou insérer
    //         if (hasExisting) {
    //             String updateQuery = "UPDATE " + tableName + " " +
    //                 "SET amount = ?, transaction_time = datetime('now', 'localtime') " +
    //                 "WHERE player_uuid = ? AND shop_id = ? AND item_id = ? AND transaction_type = ? " +
    //                 "AND strftime('%Y-%m-%d', transaction_time) = ?";
    //             try (PreparedStatement updateStmt = connection.prepareStatement(updateQuery)) {
    //                 updateStmt.setInt(1, existingAmount + amount);
    //                 updateStmt.setString(2, player.getUniqueId().toString());
    //                 updateStmt.setString(3, shopId);
    //                 updateStmt.setString(4, itemId);
    //                 updateStmt.setString(5, transactionType);
    //                 updateStmt.setString(6, today);
    //                 return updateStmt.executeUpdate();
    //             }
    //         } else {
    //             String insertQuery = "INSERT INTO " + tableName + " " +
    //                 "(player_uuid, shop_id, item_id, transaction_type, amount, transaction_time) " +
    //                 "VALUES (?, ?, ?, ?, ?, datetime('now', 'localtime'))";
    //             try (PreparedStatement insertStmt = connection.prepareStatement(insertQuery)) {
    //                 insertStmt.setString(1, player.getUniqueId().toString());
    //                 insertStmt.setString(2, shopId);
    //                 insertStmt.setString(3, itemId);
    //                 insertStmt.setString(4, transactionType);
    //                 insertStmt.setInt(5, amount);
    //                 return insertStmt.executeUpdate();
    //             }
    //         }
    //     } catch (SQLException e) {
    //         plugin.getLogger().severe("Erreur SQLite dans handleSQLiteTransaction: " + e.getMessage());
    //         return 0;
    //     }
    // }
    // private Integer handleSQLiteTransaction(Player player, String shopId, String itemId, boolean isBuy, int amount, String tableName) {
    //     String transactionType = isBuy ? "BUY" : "SELL";
    //     String today = java.time.LocalDate.now().toString();
    //     int updatedRows = 0;

    //     try (Connection connection = plugin.getDataManager().getConnection()) {
    //         // 1. Vérifier si une transaction existe déjà pour aujourd'hui
    //         String selectSql = "SELECT amount FROM " + tableName +
    //                 " WHERE player_uuid = ? AND shop_id = ? AND item_id = ? AND transaction_type = ? " +
    //                 "AND strftime('%Y-%m-%d', transaction_time) = ?";
    //         try (PreparedStatement selectStmt = connection.prepareStatement(selectSql)) {
    //             selectStmt.setString(1, player.getUniqueId().toString());
    //             selectStmt.setString(2, shopId);
    //             selectStmt.setString(3, itemId);
    //             selectStmt.setString(4, transactionType);
    //             selectStmt.setString(5, today);

    //             ResultSet rs = selectStmt.executeQuery();
    //             if (rs.next()) {
    //                 // 2. Si existe, update
    //                 int currentAmount = rs.getInt("amount");
    //                 String updateSql = "UPDATE " + tableName +
    //                         " SET amount = ?, transaction_time = datetime('now', 'localtime')" +
    //                         " WHERE player_uuid = ? AND shop_id = ? AND item_id = ? AND transaction_type = ? " +
    //                         "AND strftime('%Y-%m-%d', transaction_time) = ?";
    //                 try (PreparedStatement updateStmt = connection.prepareStatement(updateSql)) {
    //                     updateStmt.setInt(1, currentAmount + amount);
    //                     updateStmt.setString(2, player.getUniqueId().toString());
    //                     updateStmt.setString(3, shopId);
    //                     updateStmt.setString(4, itemId);
    //                     updateStmt.setString(5, transactionType);
    //                     updateStmt.setString(6, today);
    //                     updatedRows = updateStmt.executeUpdate();
    //                     plugin.info("Transaction pour " + player.getName() + ": Mise à jour de " + currentAmount + " à " + (currentAmount + amount));
    //                 }
    //             } else {
    //                 // 3. Sinon, insert
    //                 String insertSql = "INSERT INTO " + tableName +
    //                         " (player_uuid, shop_id, item_id, transaction_type, amount, transaction_time) " +
    //                         "VALUES (?, ?, ?, ?, ?, datetime('now', 'localtime'))";
    //                 try (PreparedStatement insertStmt = connection.prepareStatement(insertSql)) {
    //                     insertStmt.setString(1, player.getUniqueId().toString());
    //                     insertStmt.setString(2, shopId);
    //                     insertStmt.setString(3, itemId);
    //                     insertStmt.setString(4, transactionType);
    //                     insertStmt.setInt(5, amount);
    //                     updatedRows = insertStmt.executeUpdate();
    //                     plugin.info("Transaction pour " + player.getName() + ": Insertion de " + amount);
    //                 }
    //             }
    //         }
    //     } catch (SQLException e) {
    //         plugin.getLogger().severe("Erreur SQLite dans handleSQLiteTransaction: " + e.getMessage());
    //         return 0;
    //     }
    //     return updatedRows;
    // }
    private Integer handleSQLiteTransaction(Player player, String shopId, String itemId, boolean isBuy, int amount, String tableName) {
        String transactionType = isBuy ? "BUY" : "SELL";
        try (Connection connection = plugin.getDataManager().getConnection()) {
            String insertSql = "INSERT INTO " + tableName +
                    " (player_uuid, shop_id, item_id, transaction_type, amount, transaction_time) " +
                    "VALUES (?, ?, ?, ?, ?, datetime('now', 'localtime'))";
            try (PreparedStatement insertStmt = connection.prepareStatement(insertSql)) {
                insertStmt.setString(1, player.getUniqueId().toString());
                insertStmt.setString(2, shopId);
                insertStmt.setString(3, itemId);
                insertStmt.setString(4, transactionType);
                insertStmt.setInt(5, amount);
                int rows = insertStmt.executeUpdate();
                // plugin.info("Transaction pour " + player.getName() + ": Insertion de " + amount);
                return rows;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur SQLite dans handleSQLiteTransaction: " + e.getMessage());
            return 0;
        }
    }
    
    // public CompletableFuture<Boolean> canPerformTransaction(Player player, String shopId, String itemId, boolean isBuy, int amount) {
    //     TransactionLimit limit = getTransactionLimit(shopId, itemId, isBuy);
    //     if (limit == null) {
    //         return CompletableFuture.completedFuture(true);
    //     }
        
    //     String tablePrefix = plugin.getDataConfig().getDatabaseTablePrefix();
    //     String transactionType = isBuy ? "BUY" : "SELL";
        
    //     // Forcer la correction des horodatages futurs AVANT la vérification
    //     cleanFutureTimestamps(player.getUniqueId(), shopId, itemId, transactionType);
    //     clearExpiredTimestamps(player.getUniqueId(), shopId, itemId, transactionType);

    //     // Déterminer la date de début en fonction de la période ou du cooldown
    //     LocalDateTime startDate;
    //     if (limit.getPeriodEquivalent() != LimitPeriod.NONE) {
    //         // Pour les périodes prédéfinies, utiliser la date de début de la période
    //         startDate = getStartDateForPeriod(limit.getPeriodEquivalent());
    //     } else {
    //         // Pour les cooldowns en secondes, calculer la date à partir du moment actuel
    //         startDate = LocalDateTime.now().minusSeconds(limit.getCooldown());
    //     }
    //     // String query = "SELECT SUM(amount) as total FROM " + tablePrefix + "_transaction_limits "
    //     //         + "WHERE player_uuid = ? AND shop_id = ? AND item_id = ? AND transaction_type = ? "
    //     //         + "AND transaction_time >= ?";
        
    //     // Utiliser la vue pour interroger toutes les tables
    //     String query = "SELECT SUM(amount) as total FROM " + tablePrefix + "_transactions_view " +
    //                 "WHERE player_uuid = ? AND shop_id = ? AND item_id = ? AND transaction_type = ? " +
    //                 "AND transaction_time >= ? AND transaction_time <= NOW()";
    //                 // "AND transaction_time >= ?";
        
    //     return plugin.getDataManager().executeAsync(() -> {
    //         try (Connection connection = plugin.getDataManager().getConnection();
    //              PreparedStatement stmt = connection.prepareStatement(query)) {
    //             stmt.setString(1, player.getUniqueId().toString());
    //             stmt.setString(2, shopId);
    //             stmt.setString(3, itemId);
    //             stmt.setString(4, transactionType);
    //             stmt.setTimestamp(5, java.sql.Timestamp.valueOf(startDate));
                
    //             // ResultSet rs = stmt.executeQuery();
    //             // if (rs.next()) {
    //             //     int currentTotal = rs.getInt("total");
    //             //     return currentTotal + amount <= limit.getAmount();
    //             // }
    //             // return true;
    //             ResultSet rs = stmt.executeQuery();
    //             if (rs.next()) {
    //                 int currentTotal = rs.getInt("total");
    //                 if (rs.wasNull()) {
    //                     currentTotal = 0; // Corriger le cas où le résultat est NULL
    //                 }
                    
    //                 // Vérifier si la limite est atteinte
    //                 boolean canPerform = currentTotal + amount <= limit.getAmount();
                    
    //                 // // Log pour le debug
    //                 // plugin.getLogger().info("Limite pour " + player.getName() + " sur " + shopId + ":" + itemId + 
    //                 //                     " - Total actuel: " + currentTotal + "/" + limit.getAmount() + 
    //                 //                     " (Depuis " + startDate + ")" +
    //                 //                     " - Peut effectuer: " + canPerform);
                    
    //                 // if (!canPerform) {
    //                 //     // Vérifier aussi le cooldown
    //                 //     getNextAvailableTime(player, shopId, itemId, isBuy).thenAccept(cooldownTime -> {
    //                 //         if (cooldownTime <= 0) {
    //                 //             // Le cooldown est écoulé, donc on peut réinitialiser
    //                 //             plugin.getLogger().info("Réinitialisation des limites pour " + player.getName() + 
    //                 //                                 " sur " + shopId + ":" + itemId + " (Cooldown écoulé)");
    //                 //             resetLimits(player, shopId, itemId);
    //                 //         }
    //                 //     });
    //                 // }
                    
    //                 return canPerform;
    //             }
    //             return true;
    //         }
    //     });
    // }

    /**
     * Version synchrone de canPerformTransaction qui retourne immédiatement une réponse.
     * Utilise le cache et des valeurs approximatives pour éviter de bloquer le thread principal.
     */
    public boolean canPerformTransactionSync(Player player, String shopId, String itemId, boolean isBuy, int amount) {
        TransactionLimit limit = getTransactionLimit(shopId, itemId, isBuy);
        if (limit == null) {
            return true; // Pas de limite définie
        }
        
        String tablePrefix = plugin.getDataConfig().getDatabaseTablePrefix();
        String transactionType = isBuy ? "BUY" : "SELL";
        
        // Forcer la correction des horodatages futurs AVANT la vérification
        cleanFutureTimestamps(player.getUniqueId(), shopId, itemId, transactionType);
        clearExpiredTimestamps(player.getUniqueId(), shopId, itemId, transactionType);

        // Déterminer la date de début en fonction de la période ou du cooldown
        LocalDateTime startDate;
        if (limit.getPeriodEquivalent() != LimitPeriod.NONE) {
            // Pour les périodes prédéfinies, utiliser la date de début de la période
            startDate = getStartDateForPeriod(limit.getPeriodEquivalent());
        } else {
            // Pour les cooldowns en secondes, calculer la date à partir du moment actuel
            startDate = LocalDateTime.now().minusSeconds(limit.getCooldown());
        }
        // String query = "SELECT SUM(amount) as total FROM " + tablePrefix + "_transaction_limits "
        //         + "WHERE player_uuid = ? AND shop_id = ? AND item_id = ? AND transaction_type = ? "
        //         + "AND transaction_time >= ?";
        
        // // Utiliser la vue pour interroger toutes les tables
        // String query = "SELECT SUM(amount) as total FROM " + tablePrefix + "_transactions_view " +
        //             "WHERE player_uuid = ? AND shop_id = ? AND item_id = ? AND transaction_type = ? " +
        //             "AND transaction_time >= ?";
        //             // "AND transaction_time >= ? AND transaction_time <= NOW()";
        
        // Vérifier le type de base de données et adapter la requête
        boolean isMysql = plugin.getDataConfig().getDatabaseType().equals("mysql");
        String query;
        if (isMysql) {
            // Utiliser la vue pour MySQL
            query = "SELECT SUM(amount) as total FROM " + tablePrefix + "_transactions_view " +
                    "WHERE player_uuid = ? AND shop_id = ? AND item_id = ? AND transaction_type = ? " +
                    "AND transaction_time >= ? AND transaction_time <= NOW()";
        } else {
            // Pour SQLite, interroger chaque table individuellement et combiner les résultats
            query = "SELECT SUM(amount) as total FROM (" +
                    "SELECT amount FROM " + tablePrefix + "_tx_daily " +
                    "WHERE player_uuid = ? AND shop_id = ? AND item_id = ? AND transaction_type = ? AND transaction_time >= ? " +
                    "UNION ALL SELECT amount FROM " + tablePrefix + "_tx_weekly " +
                    "WHERE player_uuid = ? AND shop_id = ? AND item_id = ? AND transaction_type = ? AND transaction_time >= ? " +
                    "UNION ALL SELECT amount FROM " + tablePrefix + "_tx_monthly " +
                    "WHERE player_uuid = ? AND shop_id = ? AND item_id = ? AND transaction_type = ? AND transaction_time >= ? " +
                    "UNION ALL SELECT amount FROM " + tablePrefix + "_tx_yearly " +
                    "WHERE player_uuid = ? AND shop_id = ? AND item_id = ? AND transaction_type = ? AND transaction_time >= ? " +
                    "UNION ALL SELECT amount FROM " + tablePrefix + "_tx_forever " +
                    "WHERE player_uuid = ? AND shop_id = ? AND item_id = ? AND transaction_type = ? AND transaction_time >= ? " +
                    "UNION ALL SELECT amount FROM " + tablePrefix + "_transaction_limits " +
                    "WHERE player_uuid = ? AND shop_id = ? AND item_id = ? AND transaction_type = ? AND transaction_time >= ? " +
                    ")";
        }
                 
        try (Connection connection = plugin.getDataManager().getConnection();
                PreparedStatement stmt = connection.prepareStatement(query)) {
                stmt.setString(1, player.getUniqueId().toString());
                stmt.setString(2, shopId);
                stmt.setString(3, itemId);
                stmt.setString(4, transactionType);
                stmt.setTimestamp(5, java.sql.Timestamp.valueOf(startDate));

                // Pour SQLite, nous devons répéter les paramètres pour chaque sous-requête
                if (!isMysql) {
                    for (int i = 1; i < 6; i++) {
                        int offset = i * 5;
                        stmt.setString(offset + 1, player.getUniqueId().toString());
                        stmt.setString(offset + 2, shopId);
                        stmt.setString(offset + 3, itemId);
                        stmt.setString(offset + 4, transactionType);
                        stmt.setTimestamp(offset + 5, java.sql.Timestamp.valueOf(startDate));
                    }
                }
                
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    int currentTotal = rs.getInt("total");
                    if (rs.wasNull()) {
                        currentTotal = 0; // Corriger le cas où le résultat est NULL
                    }
                    
                    // Vérifier si la limite est atteinte
                    return currentTotal + amount <= limit.getAmount();
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Erreur lors de la vérification de la limite: " + e.getMessage());
            }
        return true; // En cas d'erreur, autoriser la transaction par défaut
    }

    // private LocalDateTime getStartDateForPeriod(LimitPeriod period) {
    //     LocalDateTime now = LocalDateTime.now();
        
    //     switch (period) {
    //         case DAILY:
    //             return now.truncatedTo(ChronoUnit.DAYS);
    //         case WEEKLY:
    //             return now.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)).truncatedTo(ChronoUnit.DAYS);
    //         case MONTHLY:
    //             return now.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS);
    //         case YEARLY:
    //             return now.withDayOfYear(1).truncatedTo(ChronoUnit.DAYS);
    //         case FOREVER:
    //             return LocalDateTime.ofEpochSecond(0, 0, ZoneId.systemDefault().getRules().getOffset(now));
    //         case NONE:
    //         default:
    //             return now; // Pas de période, donc on utilise maintenant
    //     }
    // }
    private LocalDateTime getStartDateForPeriod(LimitPeriod period) {
        // CORRECTION: Utiliser la date actuelle correcte et non une date future
        LocalDateTime now = LocalDateTime.now();
        
        switch (period) {
            case DAILY:
                // Début de la journée actuelle
                return now.withHour(0).withMinute(0).withSecond(0).withNano(0);
            case WEEKLY:
                // Début de la semaine actuelle (lundi)
                return now.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                    .withHour(0).withMinute(0).withSecond(0).withNano(0);
            case MONTHLY:
                // Début du mois actuel
                return now.withDayOfMonth(1)
                    .withHour(0).withMinute(0).withSecond(0).withNano(0);
            case YEARLY:
                // Début de l'année actuelle
                return now.withDayOfYear(1)
                    .withHour(0).withMinute(0).withSecond(0).withNano(0);
            case FOREVER:
                // Une date très ancienne
                return LocalDateTime.of(2000, 1, 1, 0, 0);
            case NONE:
            default:
                // Par défaut, retourner l'instant actuel
                return now;
        }
    }
    
    public CompletableFuture<Integer> getRemainingAmount(Player player, String shopId, String itemId, boolean isBuy) {
        TransactionLimit limit = getTransactionLimit(shopId, itemId, isBuy);
        if (limit == null) {
            return CompletableFuture.completedFuture(Integer.MAX_VALUE);
        }
        
        String tablePrefix = plugin.getDataConfig().getDatabaseTablePrefix();
        String transactionType = isBuy ? "BUY" : "SELL";
        
        // Déterminer la date de début en fonction de la période ou du cooldown
        LocalDateTime startDate;
        if (limit.getPeriodEquivalent() != LimitPeriod.NONE) {
            // Pour les périodes prédéfinies, utiliser la date de début de la période
            startDate = getStartDateForPeriod(limit.getPeriodEquivalent());
        } else {
            // Pour les cooldowns en secondes, calculer la date à partir du moment actuel
            startDate = LocalDateTime.now().minusSeconds(limit.getCooldown());
        }
        // String query = "SELECT SUM(amount) as total FROM " + tablePrefix + "_transaction_limits "
        //         + "WHERE player_uuid = ? AND shop_id = ? AND item_id = ? AND transaction_type = ? "
        //         + "AND transaction_time >= ?";

        // // Utiliser la vue pour interroger toutes les tables
        // String query = "SELECT SUM(amount) as total FROM " + tablePrefix + "_transactions_view " +
        //             "WHERE player_uuid = ? AND shop_id = ? AND item_id = ? AND transaction_type = ? " +
        //             "AND transaction_time >= ?";
        
        boolean isMysql = plugin.getDataConfig().getDatabaseType().equals("mysql");
        String query;
        if (isMysql) {
            // Utiliser la vue pour MySQL
            query = "SELECT SUM(amount) as total FROM " + tablePrefix + "_transactions_view " +
                   "WHERE player_uuid = ? AND shop_id = ? AND item_id = ? AND transaction_type = ? " +
                   "AND transaction_time >= ?";
        } else {
            // Requête directe pour SQLite
            // query = "SELECT SUM(amount) as total FROM " + tablePrefix + "_transactions " +
            // query = "SELECT SUM(amount) as total FROM " + tablePrefix + "_transaction_limits " +
            //        "WHERE player_uuid = ? AND shop_id = ? AND item_id = ? AND transaction_type = ? " +
            //        "AND transaction_time >= ?";
            query = "SELECT SUM(amount) as total FROM (" +
                    "SELECT amount FROM " + tablePrefix + "_tx_daily " +
                    "WHERE player_uuid = ? AND shop_id = ? AND item_id = ? AND transaction_type = ? AND transaction_time >= ? " +
                    "UNION ALL SELECT amount FROM " + tablePrefix + "_tx_weekly " +
                    "WHERE player_uuid = ? AND shop_id = ? AND item_id = ? AND transaction_type = ? AND transaction_time >= ? " +
                    "UNION ALL SELECT amount FROM " + tablePrefix + "_tx_monthly " +
                    "WHERE player_uuid = ? AND shop_id = ? AND item_id = ? AND transaction_type = ? AND transaction_time >= ? " +
                    "UNION ALL SELECT amount FROM " + tablePrefix + "_tx_yearly " +
                    "WHERE player_uuid = ? AND shop_id = ? AND item_id = ? AND transaction_type = ? AND transaction_time >= ? " +
                    "UNION ALL SELECT amount FROM " + tablePrefix + "_tx_forever " +
                    "WHERE player_uuid = ? AND shop_id = ? AND item_id = ? AND transaction_type = ? AND transaction_time >= ? " +
                    "UNION ALL SELECT amount FROM " + tablePrefix + "_transaction_limits " +
                    "WHERE player_uuid = ? AND shop_id = ? AND item_id = ? AND transaction_type = ? AND transaction_time >= ? " +
                    ")";
        }
        
        return plugin.getDataManager().executeAsync(() -> {
            try (Connection connection = plugin.getDataManager().getConnection();
                 PreparedStatement stmt = connection.prepareStatement(query)) {
                stmt.setString(1, player.getUniqueId().toString());
                stmt.setString(2, shopId);
                stmt.setString(3, itemId);
                stmt.setString(4, transactionType);
                stmt.setTimestamp(5, java.sql.Timestamp.valueOf(startDate));
                if (!isMysql) {
                    for (int i = 1; i < 6; i++) {
                        int offset = i * 5;
                        stmt.setString(offset + 1, player.getUniqueId().toString());
                        stmt.setString(offset + 2, shopId);
                        stmt.setString(offset + 3, itemId);
                        stmt.setString(offset + 4, transactionType);
                        stmt.setTimestamp(offset + 5, java.sql.Timestamp.valueOf(startDate));
                    }
                }
                
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    int currentTotal = rs.getInt("total");
                    // plugin.getLogger().info("Total actuel pour " + player.getName() + " sur " + shopId + ":" + itemId + 
                    //                     " - Total actuel: " + currentTotal + "/" + limit.getAmount() + 
                    //                     " (Depuis " + startDate + ")");
                    return Math.max(0, limit.getAmount() - currentTotal);
                }
                return limit.getAmount();
            }
        });
    }

    // /**
    //  * Version synchrone de getRemainingAmount, utilise le cache pour de meilleures performances
    //  */
    // public int getRemainingAmountSync(Player player, String shopId, String itemId, boolean isBuy) {
    //     // Clé de cache standardisée
    //     final String cacheKey = shopId + ":" + itemId + ":" + (isBuy ? "buy" : "sell") + ":remaining:" + player.getUniqueId();
        
    //     return plugin.getLimitRemainingAmountCache().get(cacheKey, () -> {
    //         TransactionLimit limit = getTransactionLimit(shopId, itemId, isBuy);
    //         if (limit == null || limit.getAmount() <= 0) {
    //             return Integer.MAX_VALUE; // Pas de limite
    //         }
            
    //         // Logique de vérification synchrone directe
    //         String tablePrefix = plugin.getDataConfig().getDatabaseTablePrefix();
    //         String transactionType = isBuy ? "BUY" : "SELL";
            
    //         // Déterminer la date de début en fonction de la période ou du cooldown
    //         // LocalDateTime startDate = getStartDateForPeriod(limit.getPeriodEquivalent());
    //         LocalDateTime startDate;
    //         if (limit.getPeriodEquivalent() != LimitPeriod.NONE) {
    //             // Pour les périodes prédéfinies, utiliser la date de début de la période
    //             startDate = getStartDateForPeriod(limit.getPeriodEquivalent());
    //         } else {
    //             // Pour les cooldowns en secondes, calculer la date à partir du moment actuel
    //             startDate = LocalDateTime.now().minusSeconds(limit.getCooldown());
    //         }
            
    //         try (Connection connection = plugin.getDataManager().getConnection();
    //             PreparedStatement stmt = connection.prepareStatement(
    //                 "SELECT SUM(amount) as total FROM " + tablePrefix + "_transactions_view " +
    //                 "WHERE player_uuid = ? AND shop_id = ? AND item_id = ? AND transaction_type = ? " +
    //                 "AND transaction_time >= ?")) {
                
    //             stmt.setString(1, player.getUniqueId().toString());
    //             stmt.setString(2, shopId);
    //             stmt.setString(3, itemId);
    //             stmt.setString(4, transactionType);
    //             stmt.setTimestamp(5, java.sql.Timestamp.valueOf(startDate));
                
    //             ResultSet rs = stmt.executeQuery();
    //             if (rs.next()) {
    //                 int currentTotal = rs.getInt("total");
    //                 if (rs.wasNull()) {
    //                     currentTotal = 0;
    //                 }
                    
    //                 int remaining = Math.max(0, limit.getAmount() - currentTotal);
    //                 // Mettre en cache
    //                 plugin.getLimitRemainingAmountCache().put(cacheKey, remaining);
    //                 return remaining;
    //             }
    //         } catch (SQLException e) {
    //             plugin.getLogger().warning("Erreur SQL dans getRemainingAmountSync: " + e.getMessage());
    //         }
            
    //         return limit.getAmount(); // Valeur par défaut
    //     });
    // }

    /**
     * Version synchrone de getRemainingAmount, compatible avec MySQL et SQLite
     */
    public int getRemainingAmountSync(Player player, String shopId, String itemId, boolean isBuy) {
        // Clé de cache standardisée
        final String cacheKey = shopId + ":" + itemId + ":" + (isBuy ? "buy" : "sell") + ":remaining:" + player.getUniqueId();
        
        return plugin.getLimitRemainingAmountCache().get(cacheKey, () -> {
            TransactionLimit limit = getTransactionLimit(shopId, itemId, isBuy);
            if (limit == null || limit.getAmount() <= 0) {
                return Integer.MAX_VALUE; // Pas de limite
            }
            
            // Logique de vérification synchrone directe
            String tablePrefix = plugin.getDataConfig().getDatabaseTablePrefix();
            String transactionType = isBuy ? "BUY" : "SELL";
            
            // Déterminer la date de début en fonction de la période ou du cooldown
            LocalDateTime startDate;
            if (limit.getPeriodEquivalent() != LimitPeriod.NONE) {
                // Pour les périodes prédéfinies, utiliser la date de début de la période
                startDate = getStartDateForPeriod(limit.getPeriodEquivalent());
            } else {
                // Pour les cooldowns en secondes, calculer la date à partir du moment actuel
                startDate = LocalDateTime.now().minusSeconds(limit.getCooldown());
            }
            
            // Vérifier le type de base de données et adapter la requête
            boolean isMysql = plugin.getDataConfig().getDatabaseType().equals("mysql");
            String query;
            
            if (isMysql) {
                // Utiliser la vue pour MySQL
                query = "SELECT SUM(amount) as total FROM " + tablePrefix + "_transactions_view " +
                    "WHERE player_uuid = ? AND shop_id = ? AND item_id = ? AND transaction_type = ? " +
                    "AND transaction_time >= ?";
            } else {
                // Pour SQLite, interroger chaque table individuellement et combiner les résultats
                query = "SELECT SUM(amount) as total FROM (" +
                    "SELECT amount FROM " + tablePrefix + "_tx_daily " +
                    "WHERE player_uuid = ? AND shop_id = ? AND item_id = ? AND transaction_type = ? AND transaction_time >= ? " +
                    "UNION ALL SELECT amount FROM " + tablePrefix + "_tx_weekly " +
                    "WHERE player_uuid = ? AND shop_id = ? AND item_id = ? AND transaction_type = ? AND transaction_time >= ? " +
                    "UNION ALL SELECT amount FROM " + tablePrefix + "_tx_monthly " +
                    "WHERE player_uuid = ? AND shop_id = ? AND item_id = ? AND transaction_type = ? AND transaction_time >= ? " +
                    "UNION ALL SELECT amount FROM " + tablePrefix + "_tx_yearly " +
                    "WHERE player_uuid = ? AND shop_id = ? AND item_id = ? AND transaction_type = ? AND transaction_time >= ? " +
                    "UNION ALL SELECT amount FROM " + tablePrefix + "_tx_forever " +
                    "WHERE player_uuid = ? AND shop_id = ? AND item_id = ? AND transaction_type = ? AND transaction_time >= ? " +
                    "UNION ALL SELECT amount FROM " + tablePrefix + "_transaction_limits " +
                    "WHERE player_uuid = ? AND shop_id = ? AND item_id = ? AND transaction_type = ? AND transaction_time >= ? " +
                    ")";
            }
            
            try (Connection connection = plugin.getDataManager().getConnection();
                PreparedStatement stmt = connection.prepareStatement(query)) {
                
                stmt.setString(1, player.getUniqueId().toString());
                stmt.setString(2, shopId);
                stmt.setString(3, itemId);
                stmt.setString(4, transactionType);
                stmt.setTimestamp(5, java.sql.Timestamp.valueOf(startDate));
                
                // Pour SQLite, nous devons répéter les paramètres pour chaque sous-requête
                if (!isMysql) {
                    for (int i = 1; i < 6; i++) {
                        int offset = i * 5;
                        stmt.setString(offset + 1, player.getUniqueId().toString());
                        stmt.setString(offset + 2, shopId);
                        stmt.setString(offset + 3, itemId);
                        stmt.setString(offset + 4, transactionType);
                        stmt.setTimestamp(offset + 5, java.sql.Timestamp.valueOf(startDate));
                    }
                }
                
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    int currentTotal = rs.getInt("total");
                    if (rs.wasNull()) {
                        currentTotal = 0;
                    }
                    
                    int remaining = Math.max(0, limit.getAmount() - currentTotal);
                    // Mettre en cache
                    plugin.getLimitRemainingAmountCache().put(cacheKey, remaining);
                    return remaining;
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Erreur SQL dans getRemainingAmountSync: " + e.getMessage());
                // Continuer en cas d'erreur pour utiliser la valeur par défaut
            }
            
            return limit.getAmount(); // Valeur par défaut
        });
    }

    // /**
    //  * Version synchrone de getNextAvailableTime, plus rapide
    //  */
    // public long getNextAvailableTimeSync(Player player, String shopId, String itemId, boolean isBuy) {
    //     // Clé de cache
    //     final String cacheKey = shopId + ":" + itemId + ":" + (isBuy ? "buy" : "sell") + ":nexttime:" + player.getUniqueId();

    //     return plugin.getLimitNextAvailableTimeCache().get(cacheKey, () -> {
    //         TransactionLimit limit = getTransactionLimit(shopId, itemId, isBuy);
    //         if (limit == null) {
    //             return 0L; // Pas de limite
    //         }
            
    //         // Logique synchrone
    //         String tablePrefix = plugin.getDataConfig().getDatabaseTablePrefix();
    //         String transactionType = isBuy ? "BUY" : "SELL";
            
    //         try (Connection connection = plugin.getDataManager().getConnection();
    //              PreparedStatement stmt = connection.prepareStatement(
    //                  "SELECT MAX(transaction_time) as latest FROM " + tablePrefix + "_transactions_view " +
    //                  "WHERE player_uuid = ? AND shop_id = ? AND item_id = ? AND transaction_type = ?")) {
                
    //             stmt.setString(1, player.getUniqueId().toString());
    //             stmt.setString(2, shopId);
    //             stmt.setString(3, itemId);
    //             stmt.setString(4, transactionType);
                
    //             ResultSet rs = stmt.executeQuery();
    //             if (rs.next() && rs.getTimestamp("latest") != null) {
    //                 java.sql.Timestamp latestTime = rs.getTimestamp("latest");
                    
    //                 long now = System.currentTimeMillis();
    //                 LocalDateTime nextAvailable = latestTime.toLocalDateTime().plusSeconds(limit.getCooldown());
    //                 long nextTimeMillis = nextAvailable.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                    
    //                 long result = Math.max(0L, nextTimeMillis - now);
    //                 // Mettre en cache pour 5 secondes
    //                 plugin.getLimitNextAvailableTimeCache().put(cacheKey, result);
    //                 return result;
    //             }
    //         } catch (SQLException e) {
    //             plugin.getLogger().warning("Erreur SQL dans getNextAvailableTimeSync: " + e.getMessage());
    //         }
            
    //         return 0L; // Pas de transactions précédentes
    //     });


    //     // // Vérifier si on a une valeur en cache (expire après 5 secondes)
    //     // Object cachedValue = plugin.getLimitCache().get(cacheKey);
    //     // if (cachedValue != null) {
    //     //     return (long) cachedValue;
    //     // }
        
    //     // TransactionLimit limit = getTransactionLimit(shopId, itemId, isBuy);
    //     // if (limit == null) {
    //     //     return 0L;
    //     // }
        
    //     // // Logique synchrone
    //     // String tablePrefix = plugin.getDataConfig().getDatabaseTablePrefix();
    //     // String transactionType = isBuy ? "BUY" : "SELL";
        
    //     // try (Connection connection = plugin.getDataManager().getConnection();
    //     //     PreparedStatement stmt = connection.prepareStatement(
    //     //         "SELECT MAX(transaction_time) as latest FROM " + tablePrefix + "_transactions_view " +
    //     //         "WHERE player_uuid = ? AND shop_id = ? AND item_id = ? AND transaction_type = ?")) {
            
    //     //     stmt.setString(1, player.getUniqueId().toString());
    //     //     stmt.setString(2, shopId);
    //     //     stmt.setString(3, itemId);
    //     //     stmt.setString(4, transactionType);
            
    //     //     ResultSet rs = stmt.executeQuery();
    //     //     if (rs.next() && rs.getTimestamp("latest") != null) {
    //     //         java.sql.Timestamp latestTime = rs.getTimestamp("latest");
                
    //     //         long now = System.currentTimeMillis();
    //     //         LocalDateTime nextAvailable = latestTime.toLocalDateTime().plusSeconds(limit.getCooldown());
    //     //         long nextTimeMillis = nextAvailable.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                
    //     //         long result = Math.max(0L, nextTimeMillis - now);
    //     //         // Mettre en cache pour 5 secondes
    //     //         plugin.getCache().put(cacheKey, result, 5);
    //     //         return result;
    //     //     }
    //     // } catch (SQLException e) {
    //     //     plugin.getLogger().warning("Erreur SQL dans getNextAvailableTimeSync: " + e.getMessage());
    //     // }
        
    //     // return 0L;
    // }
    
    // /**
    //  * Version synchrone de getNextAvailableTime, plus rapide
    //  */
    // public long getNextAvailableTimeSync(Player player, String shopId, String itemId, boolean isBuy) {
    //     // Clé de cache
    //     final String cacheKey = shopId + ":" + itemId + ":" + (isBuy ? "buy" : "sell") + ":nexttime:" + player.getUniqueId();

    //     return plugin.getLimitNextAvailableTimeCache().get(cacheKey, () -> {
    //         TransactionLimit limit = getTransactionLimit(shopId, itemId, isBuy);
    //         if (limit == null) {
    //             return 0L; // Pas de limite
    //         }
            
    //         LimitPeriod period = limit.getPeriodEquivalent();
    //         // Pour les périodes prédéfinies (DAILY, WEEKLY, etc.), calculer la fin de la période actuelle
    //         // if (period != LimitPeriod.NONE && period != LimitPeriod.FOREVER) {
    //         if (period != LimitPeriod.NONE) {
    //             LocalDateTime now = LocalDateTime.now();
    //             LocalDateTime nextReset;
                
    //             switch (period) {
    //                 case DAILY:
    //                     // Prochain reset à minuit
    //                     nextReset = now.withHour(0).withMinute(0).withSecond(0).withNano(0).plusDays(1);
    //                     break;
    //                 case WEEKLY:
    //                     // Prochain reset lundi prochain à minuit
    //                     nextReset = now.with(TemporalAdjusters.next(java.time.DayOfWeek.MONDAY))
    //                         .withHour(0).withMinute(0).withSecond(0).withNano(0);
    //                     if (now.getDayOfWeek() == java.time.DayOfWeek.MONDAY && now.getHour() == 0 && now.getMinute() == 0) {
    //                         // Si c'est déjà lundi à minuit, on est déjà réinitialisé
    //                         return 0L;
    //                     }
    //                     break;
    //                 case MONTHLY:
    //                     // Prochain reset au 1er du mois prochain
    //                     nextReset = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0).plusMonths(1);
    //                     break;
    //                 case YEARLY:
    //                     // Prochain reset au 1er janvier prochain
    //                     nextReset = now.withDayOfYear(1).withHour(0).withMinute(0).withSecond(0).withNano(0).plusYears(1);
    //                     break;
    //                 case FOREVER:
    //                     // Pas de reset, donc on retourne -1
    //                     return -1L;
    //                 // case NONE:
    //                 default:
    //                     nextReset = now; // Ne devrait jamais arriver
    //                     break;
    //             }
                
    //             long nextResetMillis = nextReset.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    //             long nowMillis = System.currentTimeMillis();
                
    //             // Calculer le temps restant jusqu'au prochain reset
    //             return Math.max(0, nextResetMillis - nowMillis);
    //         }
            
    //         // Logique synchrone
    //         String tablePrefix = plugin.getDataConfig().getDatabaseTablePrefix();
    //         String transactionType = isBuy ? "BUY" : "SELL";
            
    //         try (Connection connection = plugin.getDataManager().getConnection();
    //              PreparedStatement stmt = connection.prepareStatement(
    //                 //  "SELECT MAX(transaction_time) as latest FROM " + tablePrefix + "_transactions_view " +
    //                 //  "WHERE player_uuid = ? AND shop_id = ? AND item_id = ? AND transaction_type = ?")) {
    //                  "SELECT MIN(transaction_time) as earliest FROM " + tablePrefix + "_transactions_view " +
    //                  "WHERE player_uuid = ? AND shop_id = ? AND item_id = ? AND transaction_type = ?")) {
                
    //             stmt.setString(1, player.getUniqueId().toString());
    //             stmt.setString(2, shopId);
    //             stmt.setString(3, itemId);
    //             stmt.setString(4, transactionType);
                
    //             ResultSet rs = stmt.executeQuery();
    //             // if (rs.next() && rs.getTimestamp("latest") != null) {
    //             //     java.sql.Timestamp latestTime = rs.getTimestamp("latest");
    //             if (rs.next() && rs.getTimestamp("earliest") != null) {
    //                 java.sql.Timestamp earliestTime = rs.getTimestamp("earliest");
                    
    //                 long now = System.currentTimeMillis();
    //                 // LocalDateTime nextAvailable = latestTime.toLocalDateTime().plusSeconds(limit.getCooldown());
    //                 LocalDateTime nextAvailable = earliestTime.toLocalDateTime().plusSeconds(limit.getCooldown());
    //                 long nextTimeMillis = nextAvailable.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                    
    //                 long result = Math.max(0L, nextTimeMillis - now);
    //                 // Mettre en cache
    //                 plugin.getLimitNextAvailableTimeCache().put(cacheKey, result);
    //                 return result;
    //             }
    //         } catch (SQLException e) {
    //             plugin.getLogger().warning("Erreur SQL dans getNextAvailableTimeSync: " + e.getMessage());
    //         }
            
    //         return 0L; // Pas de transactions précédentes
    //     });
    // }

    /**
     * Version synchrone de getNextAvailableTime, compatible avec MySQL et SQLite
     */
    public long getNextAvailableTimeSync(Player player, String shopId, String itemId, boolean isBuy) {
        // Clé de cache
        final String cacheKey = shopId + ":" + itemId + ":" + (isBuy ? "buy" : "sell") + ":nexttime:" + player.getUniqueId();

        return plugin.getLimitNextAvailableTimeCache().get(cacheKey, () -> {
            TransactionLimit limit = getTransactionLimit(shopId, itemId, isBuy);
            if (limit == null) {
                return 0L; // Pas de limite
            }
            
            LimitPeriod period = limit.getPeriodEquivalent();
            // Pour les périodes prédéfinies, calculer la fin de la période actuelle
            if (period != LimitPeriod.NONE) {
                LocalDateTime now = LocalDateTime.now();
                LocalDateTime nextReset;
                
                switch (period) {
                    case DAILY:
                        // Prochain reset à minuit
                        nextReset = now.withHour(0).withMinute(0).withSecond(0).withNano(0).plusDays(1);
                        break;
                    case WEEKLY:
                        // Prochain reset lundi prochain à minuit
                        nextReset = now.with(TemporalAdjusters.next(java.time.DayOfWeek.MONDAY))
                            .withHour(0).withMinute(0).withSecond(0).withNano(0);
                        if (now.getDayOfWeek() == java.time.DayOfWeek.MONDAY && now.getHour() == 0 && now.getMinute() == 0) {
                            // Si c'est déjà lundi à minuit, on est déjà réinitialisé
                            return 0L;
                        }
                        break;
                    case MONTHLY:
                        // Prochain reset au 1er du mois prochain
                        nextReset = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0).plusMonths(1);
                        break;
                    case YEARLY:
                        // Prochain reset au 1er janvier prochain
                        nextReset = now.withDayOfYear(1).withHour(0).withMinute(0).withSecond(0).withNano(0).plusYears(1);
                        break;
                    case FOREVER:
                        // Pas de reset, donc on retourne -1
                        return -1L;
                    default:
                        nextReset = now; // Ne devrait jamais arriver
                        break;
                }
                
                long nextResetMillis = nextReset.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                long nowMillis = System.currentTimeMillis();
                
                // Calculer le temps restant jusqu'au prochain reset
                return Math.max(0, nextResetMillis - nowMillis);
            }
            
            // Vérifier le type de base de données et adapter la requête
            String tablePrefix = plugin.getDataConfig().getDatabaseTablePrefix();
            String transactionType = isBuy ? "BUY" : "SELL";
            boolean isMysql = plugin.getDataConfig().getDatabaseType().equals("mysql");
            String query;
            
            if (isMysql) {
                // Utiliser la vue pour MySQL
                query = "SELECT MIN(transaction_time) as earliest FROM " + tablePrefix + "_transactions_view " +
                    "WHERE player_uuid = ? AND shop_id = ? AND item_id = ? AND transaction_type = ?";
            } else {
                // Pour SQLite, utiliser les tables directement
                query = "SELECT MIN(transaction_time) as earliest FROM (" +
                    "SELECT transaction_time FROM " + tablePrefix + "_tx_daily " +
                    "WHERE player_uuid = ? AND shop_id = ? AND item_id = ? AND transaction_type = ? " +
                    "UNION ALL SELECT transaction_time FROM " + tablePrefix + "_tx_weekly " +
                    "WHERE player_uuid = ? AND shop_id = ? AND item_id = ? AND transaction_type = ? " +
                    "UNION ALL SELECT transaction_time FROM " + tablePrefix + "_tx_monthly " +
                    "WHERE player_uuid = ? AND shop_id = ? AND item_id = ? AND transaction_type = ? " +
                    "UNION ALL SELECT transaction_time FROM " + tablePrefix + "_tx_yearly " +
                    "WHERE player_uuid = ? AND shop_id = ? AND item_id = ? AND transaction_type = ? " +
                    "UNION ALL SELECT transaction_time FROM " + tablePrefix + "_tx_forever " +
                    "WHERE player_uuid = ? AND shop_id = ? AND item_id = ? AND transaction_type = ? " +
                    "UNION ALL SELECT transaction_time FROM " + tablePrefix + "_transaction_limits " +
                    "WHERE player_uuid = ? AND shop_id = ? AND item_id = ? AND transaction_type = ? " +
                    ")";
            }
            
            try (Connection connection = plugin.getDataManager().getConnection();
                PreparedStatement stmt = connection.prepareStatement(query)) {
                
                stmt.setString(1, player.getUniqueId().toString());
                stmt.setString(2, shopId);
                stmt.setString(3, itemId);
                stmt.setString(4, transactionType);
                
                // Pour SQLite, nous devons répéter les paramètres pour chaque sous-requête
                if (!isMysql) {
                    for (int i = 1; i < 6; i++) {
                        int offset = i * 4;
                        stmt.setString(offset + 1, player.getUniqueId().toString());
                        stmt.setString(offset + 2, shopId);
                        stmt.setString(offset + 3, itemId);
                        stmt.setString(offset + 4, transactionType);
                    }
                }
                
                ResultSet rs = stmt.executeQuery();
                if (rs.next() && rs.getTimestamp("earliest") != null) {
                    java.sql.Timestamp earliestTime = rs.getTimestamp("earliest");
                    
                    long now = System.currentTimeMillis();
                    LocalDateTime nextAvailable = earliestTime.toLocalDateTime().plusSeconds(limit.getCooldown());
                    long nextTimeMillis = nextAvailable.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                    
                    long result = Math.max(0L, nextTimeMillis - now);
                    // Mettre en cache
                    plugin.getLimitNextAvailableTimeCache().put(cacheKey, result);
                    return result;
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Erreur SQL dans getNextAvailableTimeSync: " + e.getMessage());
            }
            
            return 0L; // Pas de transactions précédentes
        });
    }

    // limit:
    //     sell: 100  # Nombre maximum d'items vendables
    //     buy: 100   # Nombre maximum d'items achetables
    //     cooldown: 3600  # Soit en secondes (exemple: 3600 pour 1 heure)
    //     # OU
    //     # cooldown: DAILY  # Période prédéfinie (DAILY, WEEKLY, MONTHLY, YEARLY, FOREVER)
    
    /**
     * Obtient les limites de transaction pour un item spécifique
     * Optimisé pour utiliser le cache interne du plugin
     */
    public TransactionLimit getTransactionLimit(String shopId, String itemId, boolean isBuy) {
        // Désactiver les limites si SQLite
        if (plugin.getDataConfig().getDatabaseType().equalsIgnoreCase("sqlite")) {
            return null;
        }
        // Clé de cache standardisée
        final String cacheKey = shopId + ":" + itemId + ":" + (isBuy ? "buy" : "sell") + ":limit";
        
        // Utiliser le cache intégré du plugin
        return plugin.getLimitCache().get(cacheKey, () -> {
            try {
                // Si l'item n'est pas dans le cache, charger depuis la config
                String limitPath = isBuy ? "limit.buy" : "limit.sell";
                int amount = plugin.getShopConfigManager().getItemValue(shopId, itemId, limitPath, Integer.class).orElse(0);
                
                if (amount <= 0) {
                    // Pas de limite - retourner null mais garder en cache pour éviter des recherches répétées
                    // plugin.getLogger().info("Aucune limite définie pour " + shopId + ":" + itemId + " (" + limitPath + ")");
                    return null;
                }
                // plugin.getLogger().info("Limite trouvée pour " + shopId + ":" + itemId + " (" + limitPath + "): " + amount);
                
                // Traiter le cooldown (peut être un nombre ou une période)
                int cooldownSeconds = 0;
                
                // Essayer d'abord comme un entier
                Optional<Integer> cooldownInt = plugin.getShopConfigManager().getItemValue(shopId, itemId, "limit.cooldown", Integer.class);
                if (cooldownInt.isPresent()) {
                    cooldownSeconds = cooldownInt.get();
                    // plugin.getLogger().info("Cooldown trouvé pour " + shopId + ":" + itemId + " (" + limitPath + "): " + cooldownSeconds);
                } else {
                    // Essayer comme une chaîne (période ou nombre en texte)
                    Optional<String> cooldownStr = plugin.getShopConfigManager().getItemValue(shopId, itemId, "limit.cooldown", String.class);
                    if (cooldownStr.isPresent()) {
                        String periodStr = cooldownStr.get().toUpperCase();
                        
                        try {
                            // Essayer comme période prédéfinie (DAILY, WEEKLY, etc.)
                            LimitPeriod period = LimitPeriod.valueOf(periodStr);
                            cooldownSeconds = period.getSeconds();
                        } catch (IllegalArgumentException e) {
                            // Essayer comme nombre en texte
                            try {
                                cooldownSeconds = Integer.parseInt(periodStr);
                            } catch (NumberFormatException ex) {
                                cooldownSeconds = 0;
                            }
                        }
                    }
                }
                
                // Créer et retourner la limite (sera mise en cache automatiquement)
                return new TransactionLimit(amount, cooldownSeconds);
                
            } catch (Exception e) {
                plugin.getLogger().severe("Erreur lors de la récupération des limites pour " + shopId + ":" + itemId + ": " + e.getMessage());
                return null;
            }
        });
    }

    // public TransactionLimit getTransactionLimit(String shopId, String itemId, boolean isBuy) {
    //     String cacheKey = shopId + ":" + itemId + ":" + (isBuy ? "buy" : "sell");
        
    //     // Vérifier le cache
    //     if (limitCache.containsKey(cacheKey)) {
    //         Long timestamp = limitCacheTimestamps.get(cacheKey);
    //         if (timestamp != null && System.currentTimeMillis() - timestamp < CACHE_DURATION) {
    //             return limitCache.get(cacheKey);
    //         } else {
    //             // Cache expiré, nettoyer
    //             limitCache.remove(cacheKey);
    //             limitCacheTimestamps.remove(cacheKey);
    //         }
    //     }

    //     try {
    //         String limitPath = isBuy ? "limit.buy" : "limit.sell";
    //         int amount = plugin.getShopConfigManager().getItemValue(shopId, itemId, limitPath, Integer.class).orElse(0);
    //         if (amount <= 0) {
    //             // Pas de limite
    //             return null;
    //         }
            
    //         // CHANGEMENT PRINCIPAL: Récupérer le paramètre cooldown qui peut être soit un nombre soit une période
    //         // Object cooldownConfig = plugin.getShopConfigManager().getItemValue(shopId, itemId, "limit.cooldown", Object.class).orElse(0);
    //         // plugin.getLogger().info("Cooldown config: " + cooldownConfig);
    //         int cooldownSeconds = 0;
    //         Optional<Integer> cooldownInt = plugin.getShopConfigManager().getItemValue(shopId, itemId, "limit.cooldown", Integer.class);
    //         if (cooldownInt.isPresent()) {
    //             // Si c'est un nombre, utiliser directement
    //             cooldownSeconds = cooldownInt.get();
    //             // plugin.getLogger().info("Cooldown trouvé (nombre): " + cooldownSeconds);
    //         } else {
    //             // Si ce n'est pas un nombre, essayer comme une chaîne
    //             Optional<String> cooldownStr = plugin.getShopConfigManager().getItemValue(shopId, itemId, "limit.cooldown", String.class);
    //             if (cooldownStr.isPresent()) {
    //                 String periodStr = cooldownStr.get().toUpperCase();
    //                 // plugin.getLogger().info("Cooldown trouvé (texte): " + periodStr);
                    
    //                 try {
    //                     // Essayer de l'interpréter comme une période prédéfinie
    //                     LimitPeriod period = LimitPeriod.valueOf(periodStr);
    //                     cooldownSeconds = period.getSeconds();
    //                 } catch (IllegalArgumentException e) {
    //                     // Essayer de l'interpréter comme un nombre en texte
    //                     try {
    //                         cooldownSeconds = Integer.parseInt(periodStr);
    //                     } catch (NumberFormatException ex) {
    //                         // Ce n'est ni une période ni un nombre, utiliser 0
    //                         cooldownSeconds = 0;
    //                     }
    //                 }
    //             }
    //         }

    //         // // LimitPeriod period = LimitPeriod.NONE;
            
    //         // // if (cooldownConfig instanceof Number) {
    //         // //     // C'est un cooldown en secondes
    //         // //     cooldownSeconds = ((Number) cooldownConfig).intValue();
    //         // //     period = LimitPeriod.NONE; // Pas de période spécifique
    //         // // } else if (cooldownConfig instanceof String) {
    //         // //     // C'est une période prédéfinie
    //         // //     String periodStr = ((String) cooldownConfig).toUpperCase();
    //         // //     try {
    //         // //         period = LimitPeriod.valueOf(periodStr);
                    
    //         // //         // Calculer le cooldown en secondes en fonction de la période
    //         // //         switch (period) {
    //         // //             case DAILY:
    //         // //                 cooldownSeconds = 86400; // 24 heures
    //         // //                 break;
    //         // //             case WEEKLY:
    //         // //                 cooldownSeconds = 604800; // 7 jours
    //         // //                 break;
    //         // //             case MONTHLY:
    //         // //                 cooldownSeconds = 2592000; // 30 jours
    //         // //                 break;
    //         // //             case YEARLY:
    //         // //                 cooldownSeconds = 31536000; // 365 jours
    //         // //                 break;
    //         // //             case FOREVER:
    //         // //                 cooldownSeconds = Integer.MAX_VALUE;
    //         // //                 break;
    //         // //             default:
    //         // //                 cooldownSeconds = 0;
    //         // //                 break;
    //         // //         }
    //         // //     } catch (IllegalArgumentException e) {
    //         // //         // Période non reconnue, utiliser NONE
    //         // //         period = LimitPeriod.NONE;
    //         // //     }
    //         // // }
    //         // if (cooldownConfig instanceof Number) {
    //         //     // C'est directement un nombre de secondes
    //         //     cooldownSeconds = ((Number) cooldownConfig).intValue();
    //         // } else if (cooldownConfig instanceof String) {
    //         //     // C'est soit une période prédéfinie, soit un nombre sous forme de chaîne
    //         //     String cooldownStr = ((String) cooldownConfig).toUpperCase();
    //         //     try {
    //         //         // Essayer de l'interpréter comme une période prédéfinie
    //         //         LimitPeriod period = LimitPeriod.valueOf(cooldownStr);
    //         //         cooldownSeconds = period.getSeconds();
    //         //     } catch (IllegalArgumentException e) {
    //         //         // Essayer de l'interpréter comme un nombre
    //         //         try {
    //         //             cooldownSeconds = Integer.parseInt(cooldownStr);
    //         //         } catch (NumberFormatException ex) {
    //         //             // Ce n'est ni une période ni un nombre, utiliser 0
    //         //             cooldownSeconds = 0;
    //         //         }
    //         //     }
    //         // }
            
    //         // Créer et mettre en cache le résultat
    //         TransactionLimit limit = new TransactionLimit(amount, cooldownSeconds);
    //         limitCache.put(cacheKey, limit);
    //         limitCacheTimestamps.put(cacheKey, System.currentTimeMillis());
            
    //         return limit;
    //     } catch (Exception e) {
    //         plugin.getLogger().severe("Erreur lors de la récupération des limites pour " + shopId + ":" + itemId + ": " + e.getMessage());
    //         return null;
    //     }
    // }
    
    // public CompletableFuture<Long> getNextAvailableTime(Player player, String shopId, String itemId, boolean isBuy) {
    //     TransactionLimit limit = getTransactionLimit(shopId, itemId, isBuy);
    //     if (limit == null || limit.getCooldown() <= 0) {
    //         return CompletableFuture.completedFuture(0L);
    //     }
        
    //     String tablePrefix = plugin.getDataConfig().getDatabaseTablePrefix();
    //     String transactionType = isBuy ? "BUY" : "SELL";
        
    //     // cleanFutureTimestamps(player.getUniqueId(), shopId, itemId, transactionType);
    //     // clearExpiredTimestamps(player.getUniqueId(), shopId, itemId, transactionType);
        
    //     // String query = "SELECT MAX(transaction_time) as latest FROM " + tablePrefix + "_transaction_limits "
    //     //         + "WHERE player_uuid = ? AND shop_id = ? AND item_id = ? AND transaction_type = ?";

    //     // Utiliser la vue pour interroger toutes les tables
    //     String query = "SELECT MAX(transaction_time) as latest FROM " + tablePrefix + "_transactions_view " +
    //                 "WHERE player_uuid = ? AND shop_id = ? AND item_id = ? AND transaction_type = ?";
        
    //     return plugin.getDataManager().executeAsync(() -> {
    //         try (Connection connection = plugin.getDataManager().getConnection();
    //              PreparedStatement stmt = connection.prepareStatement(query)) {
    //             stmt.setString(1, player.getUniqueId().toString());
    //             stmt.setString(2, shopId);
    //             stmt.setString(3, itemId);
    //             stmt.setString(4, transactionType);
                
    //             ResultSet rs = stmt.executeQuery();
    //             if (rs.next() && rs.getTimestamp("latest") != null) {
    //                 java.sql.Timestamp latestTime = rs.getTimestamp("latest");
    //                 // LocalDateTime nextAvailable = latestTime.toLocalDateTime().plusSeconds(limit.getCooldown());
    //                 // return nextAvailable.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                    
    //                 // Vérifier si l'horodatage est dans le futur
    //                 long now = System.currentTimeMillis();
    //                 // long latestTimeMillis = latestTime.getTime();
                    
    //                 // if (latestTimeMillis > now) {
    //                 //     // plugin.getLogger().warning("Détection d'un horodatage futur pour " + player.getName() + 
    //                 //     //                     " sur " + shopId + ":" + itemId + " - Réinitialisation forcée");
                        
    //                 //     // Corriger en supprimant les entrées avec horodatages futurs
    //                 //     resetFutureTimestamps(player.getUniqueId(), shopId, itemId, transactionType);
                        
    //                 //     // Disponible immédiatement
    //                 //     return 0L;
    //                 // }
                    
    //                 // Calculer le temps jusqu'à la prochaine disponibilité
    //                 LocalDateTime nextAvailable = latestTime.toLocalDateTime().plusSeconds(limit.getCooldown());
    //                 long nextTimeMillis = nextAvailable.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                    
    //                 // Si le temps est déjà passé, retourner 0 (disponible immédiatement)
    //                 return Math.max(0L, nextTimeMillis - now);
    //             }
    //             return 0L;
    //         }
    //     });
    // }
    // Il faut récupérer le premier horodatage dans la base de données
    // je veux que getNextAvailableTime prenne également en compte les périodes DAILY, WEEKLY où çà ne doit pas être depuis le début, mais dans le jour même ou la semaine même.
    // En gros, à minuit, çà refonctionne ou Lundi pour le WEEKL
    public CompletableFuture<Long> getNextAvailableTime(Player player, String shopId, String itemId, boolean isBuy) {
        TransactionLimit limit = getTransactionLimit(shopId, itemId, isBuy);
        if (limit == null || limit.getCooldown() <= 0) {
            return CompletableFuture.completedFuture(0L);
        }
        
        LimitPeriod period = limit.getPeriodEquivalent();
        
        // Pour les périodes prédéfinies (DAILY, WEEKLY, etc.), calculer la fin de la période actuelle
        // if (period != LimitPeriod.NONE && period != LimitPeriod.FOREVER) {
        if (period != LimitPeriod.NONE) {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime nextReset;
            
            switch (period) {
                case DAILY:
                    // Prochain reset à minuit
                    nextReset = now.withHour(0).withMinute(0).withSecond(0).withNano(0).plusDays(1);
                    break;
                case WEEKLY:
                    // Prochain reset lundi prochain à minuit
                    nextReset = now.with(TemporalAdjusters.next(java.time.DayOfWeek.MONDAY))
                        .withHour(0).withMinute(0).withSecond(0).withNano(0);
                    if (now.getDayOfWeek() == java.time.DayOfWeek.MONDAY && now.getHour() == 0 && now.getMinute() == 0) {
                        // Si c'est déjà lundi à minuit, on est déjà réinitialisé
                        return CompletableFuture.completedFuture(0L);
                    }
                    break;
                case MONTHLY:
                    // Prochain reset au 1er du mois prochain
                    nextReset = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0).plusMonths(1);
                    break;
                case YEARLY:
                    // Prochain reset au 1er janvier prochain
                    nextReset = now.withDayOfYear(1).withHour(0).withMinute(0).withSecond(0).withNano(0).plusYears(1);
                    break;
                case FOREVER:
                    // Pas de reset, donc on retourne -1
                    return CompletableFuture.completedFuture(-1L);
                // case NONE:
                default:
                    nextReset = now; // Ne devrait jamais arriver
                    break;
            }
            
            long nextResetMillis = nextReset.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            long nowMillis = System.currentTimeMillis();
            
            // Calculer le temps restant jusqu'au prochain reset
            return CompletableFuture.completedFuture(Math.max(0, nextResetMillis - nowMillis));
        }
        
        String tablePrefix = plugin.getDataConfig().getDatabaseTablePrefix();
        String transactionType = isBuy ? "BUY" : "SELL";
        
        // // long now = System.currentTimeMillis();
        // LocalDateTime now = LocalDateTime.now();
        // LocalDateTime startDate;
        // if (limit.getPeriodEquivalent() != LimitPeriod.NONE) {
        //     // Pour les périodes prédéfinies, utiliser la date de début de la période
        //     startDate = getStartDateForPeriod(limit.getPeriodEquivalent());
        //     if (startDate.getDayOfWeek() == DayOfWeek.MONDAY && now.getHour() == 0 && now.getMinute() == 0) {
        //         return CompletableFuture.completedFuture(0L);
        //     }
        // } else {
        //     // Pour les cooldowns en secondes, calculer la date à partir du moment actuel
        //     startDate = LocalDateTime.now().minusSeconds(limit.getCooldown());
        // }

        // long nowMillis = System.currentTimeMillis();
        // long startTimeMillis = startDate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        // if (startTimeMillis > nowMillis) {
        //     // Si la date de début est dans le futur, retourner 0 (disponible immédiatement)
        //     return CompletableFuture.completedFuture(0L);
        // }


        boolean isMysql = plugin.getDataConfig().getDatabaseType().equals("mysql");
        String query;
        if (isMysql) {
            query = "SELECT MIN(transaction_time) as earliest FROM " + tablePrefix + "_transactions_view "
                    + "WHERE player_uuid = ? AND shop_id = ? AND item_id = ? AND transaction_type = ?";
        } else {
            query = "SELECT MIN(transaction_time) as earliest FROM ("
                    + "SELECT transaction_time FROM " + tablePrefix + "_tx_daily "
                    + "WHERE player_uuid = ? AND shop_id = ? AND item_id = ? AND transaction_type = ? "
                    + "UNION ALL SELECT transaction_time FROM " + tablePrefix + "_tx_weekly "
                    + "WHERE player_uuid = ? AND shop_id = ? AND item_id = ? AND transaction_type = ? "
                    + "UNION ALL SELECT transaction_time FROM " + tablePrefix + "_tx_monthly "
                    + "WHERE player_uuid = ? AND shop_id = ? AND item_id = ? AND transaction_type = ? "
                    + "UNION ALL SELECT transaction_time FROM " + tablePrefix + "_tx_yearly "
                    + "WHERE player_uuid = ? AND shop_id = ? AND item_id = ? AND transaction_type = ? "
                    + "UNION ALL SELECT transaction_time FROM " + tablePrefix + "_tx_forever "
                    + "WHERE player_uuid = ? AND shop_id = ? AND item_id = ? AND transaction_type = ? "
                    + "UNION ALL SELECT transaction_time FROM " + tablePrefix + "_transaction_limits "
                    + "WHERE player_uuid = ? AND shop_id = ? AND item_id = ? AND transaction_type = ?) AS combined";
        }

        return plugin.getDataManager().executeAsync(() -> {
            try (Connection connection = plugin.getDataManager().getConnection();
                 PreparedStatement stmt = connection.prepareStatement(query)) {
                stmt.setString(1, player.getUniqueId().toString());
                stmt.setString(2, shopId);
                stmt.setString(3, itemId);
                stmt.setString(4, transactionType);
                
                ResultSet rs = stmt.executeQuery();
                if (rs.next() && rs.getTimestamp("earliest") != null) {
                    java.sql.Timestamp earliestTime = rs.getTimestamp("earliest");
                    
                    // Vérifier si l'horodatage est dans le futur
                    long now = System.currentTimeMillis();
                    // long earliestTimeMillis = earliestTime.getTime();
                    
                    // if (earliestTimeMillis > now) {
                    //     // plugin.getLogger().warning("Détection d'un horodatage futur pour " + player.getName() + 
                    //     //                     " sur " + shopId + ":" + itemId + " - Réinitialisation forcée");
                        
                    //     // Corriger en supprimant les entrées avec horodatages futurs
                    //     resetFutureTimestamps(player.getUniqueId(), shopId, itemId, transactionType);
                        
                    //     // Disponible immédiatement
                    //     return 0L;
                    // }
                    
                    // Calculer le temps jusqu'à la prochaine disponibilité
                    LocalDateTime nextAvailable = earliestTime.toLocalDateTime().plusSeconds(limit.getCooldown());
                    long nextTimeMillis = nextAvailable.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                    
                    // Si le temps est déjà passé, retourner 0 (disponible immédiatement)
                    return Math.max(0L, nextTimeMillis - now);
                }
                return 0L;
            }
        });
    }


    private void clearExpiredTimestamps(UUID playerUuid, String shopId, String itemId, String transactionType) {
        // plugin.getLogger().info("Nettoyage des horodatages périmés pour " + playerUuid + " sur " + shopId + ":" + itemId);
        TransactionLimit limit = getTransactionLimit(shopId, itemId, transactionType.equals("BUY"));
        if (limit == null) {
            // plugin.getLogger().info("Aucune limite trouvée pour " + playerUuid + " sur " + shopId + ":" + itemId);
            return;
        }
        String tablePrefix = plugin.getDataConfig().getDatabaseTablePrefix();
        String[] tables = {
            tablePrefix + "_tx_daily",
            tablePrefix + "_tx_weekly",
            tablePrefix + "_tx_monthly",
            tablePrefix + "_tx_yearly",
            tablePrefix + "_tx_forever",
            tablePrefix + "_transaction_limits"
        };
        
        // Déterminer la date de début en fonction de la période ou du cooldown
        LocalDateTime startDate;
        if (limit.getPeriodEquivalent() != LimitPeriod.NONE) {
            // Pour les périodes prédéfinies, utiliser la date de début de la période
            startDate = getStartDateForPeriod(limit.getPeriodEquivalent());
        } else {
            // Pour les cooldowns en secondes, calculer la date à partir du moment actuel
            startDate = LocalDateTime.now().minusSeconds(limit.getCooldown());
        }

        // plugin.getLogger().info("Date de début pour le nettoyage: " + startDate);
        for (String table : tables) {
            String query = "DELETE FROM " + table + 
                        " WHERE player_uuid = ? AND shop_id = ? AND item_id = ? AND transaction_type = ? " +
                        " AND transaction_time < ?";

            // plugin.getLogger().info("Suppression des entrées périmées pour la table " + table + 
            //                     " pour le joueur " + playerUuid + 
            //                     " sur le shop " + shopId + 
            //                     " et l'item " + itemId);
            
            plugin.getDataManager().executeAsync(() -> {
                try (Connection connection = plugin.getDataManager().getConnection();
                    PreparedStatement stmt = connection.prepareStatement(query)) {
                    stmt.setString(1, playerUuid.toString());
                    stmt.setString(2, shopId);
                    stmt.setString(3, itemId);
                    stmt.setString(4, transactionType);
                    stmt.setTimestamp(5, java.sql.Timestamp.valueOf(startDate));
                    
                    return stmt.executeUpdate();
                } catch (SQLException e) {
                    plugin.getLogger().severe("Erreur lors de la suppression d'entrées périmées: " + e.getMessage());
                    return 0;
                }
            });
        }
    }
    
    // Nouvelle méthode pour nettoyer les horodatages futurs
    private void cleanFutureTimestamps(UUID playerUuid, String shopId, String itemId, String transactionType) {
        String tablePrefix = plugin.getDataConfig().getDatabaseTablePrefix();
        String[] tables = {
            tablePrefix + "_tx_daily",
            tablePrefix + "_tx_weekly",
            tablePrefix + "_tx_monthly",
            tablePrefix + "_tx_yearly",
            tablePrefix + "_tx_forever",
            tablePrefix + "_transaction_limits"
        };
        
        // Obtenir la date/heure actuelle
        long currentTimeMillis = System.currentTimeMillis();
        java.sql.Timestamp currentTimestamp = new java.sql.Timestamp(currentTimeMillis);
        
        for (String table : tables) {
            String query = "DELETE FROM " + table + 
                        " WHERE player_uuid = ? AND shop_id = ? AND item_id = ? AND transaction_type = ? " +
                        " AND transaction_time > ?";
                        // " AND transaction_time > NOW()";
            
            plugin.getDataManager().executeAsync(() -> {
                try (Connection connection = plugin.getDataManager().getConnection();
                    PreparedStatement stmt = connection.prepareStatement(query)) {
                    stmt.setString(1, playerUuid.toString());
                    stmt.setString(2, shopId);
                    stmt.setString(3, itemId);
                    stmt.setString(4, transactionType);
                    stmt.setTimestamp(5, currentTimestamp);
                    
                    return stmt.executeUpdate();
                } catch (SQLException e) {
                    plugin.getLogger().severe("Erreur lors de la suppression d'entrées futures: " + e.getMessage());
                    return 0;
                }
            });
        }
    }

    /**
     * Nettoie les données de transactions périmées de la base de données.
     * Cette méthode supprime toutes les transactions dont la période est expirée.
     */
    public void cleanupExpiredTransactions() {
        String tablePrefix = plugin.getDataConfig().getDatabaseTablePrefix();
        
        // Nettoyer chaque table partitionnée
        for (LimitPeriod period : LimitPeriod.values()) {
            if (period == LimitPeriod.NONE || period == LimitPeriod.FOREVER) {
                continue; // Pas de nettoyage pour ces périodes
            }
            
            LocalDateTime cutoffDate;
            String tableName;
            
            // Déterminer la date limite et la table à nettoyer
            switch (period) {
                case DAILY:
                    cutoffDate = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS);
                    tableName = tablePrefix + "_tx_daily";
                    break;
                case WEEKLY:
                    cutoffDate = LocalDateTime.now().with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)).truncatedTo(ChronoUnit.DAYS);
                    tableName = tablePrefix + "_tx_weekly";
                    break;
                case MONTHLY:
                    cutoffDate = LocalDateTime.now().withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS);
                    tableName = tablePrefix + "_tx_monthly";
                    break;
                case YEARLY:
                    cutoffDate = LocalDateTime.now().withDayOfYear(1).truncatedTo(ChronoUnit.DAYS);
                    tableName = tablePrefix + "_tx_yearly";
                    break;
                default:
                    continue; // Ignorer les autres périodes
            }
            
            // Effectuer le nettoyage
            final LocalDateTime finalCutoffDate = cutoffDate;
            final String finalTableName = tableName;
            
            plugin.getDataManager().executeAsync(() -> {
                try (Connection connection = plugin.getDataManager().getConnection();
                    PreparedStatement stmt = connection.prepareStatement(
                        "DELETE FROM " + finalTableName + " WHERE transaction_time < ?")) {
                    stmt.setTimestamp(1, java.sql.Timestamp.valueOf(finalCutoffDate));
                    return stmt.executeUpdate();
                } catch (SQLException e) {
                    plugin.getLogger().severe("Erreur lors du nettoyage des transactions " + period.name() + ": " + e.getMessage());
                    return 0;
                }
            });
        }
        
        // Nettoyer aussi la table principale pour compatibilité
        LocalDateTime defaultCutoff = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.DAYS);
        plugin.getDataManager().executeAsync(() -> {
            try (Connection connection = plugin.getDataManager().getConnection();
                PreparedStatement stmt = connection.prepareStatement(
                    "DELETE FROM " + tablePrefix + "_transaction_limits WHERE transaction_time < ?")) {
                stmt.setTimestamp(1, java.sql.Timestamp.valueOf(defaultCutoff));
                return stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Erreur lors du nettoyage de la table principale: " + e.getMessage());
                return 0;
            }
        });
    }
    // public void cleanupExpiredTransactions() {
    //     String tablePrefix = plugin.getDataConfig().getDatabaseTablePrefix();
        
    //     // Nettoyer chaque table partitionnée selon sa période spécifique
    //     LocalDateTime dailyCutoff = getStartDateForPeriod(LimitPeriod.DAILY);
    //     plugin.getDataManager().executeAsync(() -> {
    //         try (Connection connection = plugin.getDataManager().getConnection();
    //             PreparedStatement stmt = connection.prepareStatement(
    //                 "DELETE FROM " + tablePrefix + "_tx_daily WHERE transaction_time < ?")) {
    //             stmt.setTimestamp(1, java.sql.Timestamp.valueOf(dailyCutoff));
    //             return stmt.executeUpdate();
    //         } catch (SQLException e) {
    //             plugin.getLogger().severe("Erreur lors du nettoyage des transactions quotidiennes: " + e.getMessage());
    //             return 0;
    //         }
    //     });
        
    //     LocalDateTime weeklyCutoff = getStartDateForPeriod(LimitPeriod.WEEKLY);
    //     plugin.getDataManager().executeAsync(() -> {
    //         try (Connection connection = plugin.getDataManager().getConnection();
    //             PreparedStatement stmt = connection.prepareStatement(
    //                 "DELETE FROM " + tablePrefix + "_tx_weekly WHERE transaction_time < ?")) {
    //             stmt.setTimestamp(1, java.sql.Timestamp.valueOf(weeklyCutoff));
    //             return stmt.executeUpdate();
    //         } catch (SQLException e) {
    //             plugin.getLogger().severe("Erreur lors du nettoyage des transactions hebdomadaires: " + e.getMessage());
    //             return 0;
    //         }
    //     });
        
    //     LocalDateTime monthlyCutoff = getStartDateForPeriod(LimitPeriod.MONTHLY);
    //     plugin.getDataManager().executeAsync(() -> {
    //         try (Connection connection = plugin.getDataManager().getConnection();
    //             PreparedStatement stmt = connection.prepareStatement(
    //                 "DELETE FROM " + tablePrefix + "_tx_monthly WHERE transaction_time < ?")) {
    //             stmt.setTimestamp(1, java.sql.Timestamp.valueOf(monthlyCutoff));
    //             return stmt.executeUpdate();
    //         } catch (SQLException e) {
    //             plugin.getLogger().severe("Erreur lors du nettoyage des transactions mensuelles: " + e.getMessage());
    //             return 0;
    //         }
    //     });
        
    //     LocalDateTime yearlyCutoff = getStartDateForPeriod(LimitPeriod.YEARLY);
    //     plugin.getDataManager().executeAsync(() -> {
    //         try (Connection connection = plugin.getDataManager().getConnection();
    //             PreparedStatement stmt = connection.prepareStatement(
    //                 "DELETE FROM " + tablePrefix + "_tx_yearly WHERE transaction_time < ?")) {
    //             stmt.setTimestamp(1, java.sql.Timestamp.valueOf(yearlyCutoff));
    //             return stmt.executeUpdate();
    //         } catch (SQLException e) {
    //             plugin.getLogger().severe("Erreur lors du nettoyage des transactions annuelles: " + e.getMessage());
    //             return 0;
    //         }
    //     });
        
    //     // Nettoyer aussi la table principale pour compatibilité
    //     plugin.getDataManager().executeAsync(() -> {
    //         try (Connection connection = plugin.getDataManager().getConnection();
    //             PreparedStatement stmt = connection.prepareStatement(
    //                 "DELETE FROM " + tablePrefix + "_transaction_limits WHERE transaction_time < ?")) {
    //             stmt.setTimestamp(1, java.sql.Timestamp.valueOf(dailyCutoff));
    //             return stmt.executeUpdate();
    //         } catch (SQLException e) {
    //             plugin.getLogger().severe("Erreur lors du nettoyage de la table principale: " + e.getMessage());
    //             return 0;
    //         }
    //     });
    // }
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

    // /**
    //  * Nettoie les transactions expirées liées au cooldown.
    //  */
    // public void cleanupCooldownTransactions() {
    //     String tablePrefix = plugin.getDataConfig().getDatabaseTablePrefix();
        
    //     // La requête supprime les transactions dont le cooldown est expiré
    //     String query = "DELETE t1 FROM " + tablePrefix + "_transaction_limits t1 " +
    //                 "INNER JOIN " + tablePrefix + "_transaction_limits t2 ON " +
    //                 "t1.player_uuid = t2.player_uuid AND t1.shop_id = t2.shop_id AND " +
    //                 "t1.item_id = t2.item_id AND t1.transaction_type = t2.transaction_type " +
    //                 "WHERE t1.transaction_time < t2.transaction_time";
        
    //     // Version alternative pour SQLite qui ne supporte pas les JOINs dans DELETE
    //     String sqliteQuery = "DELETE FROM " + tablePrefix + "_transaction_limits " +
    //                         "WHERE transaction_time NOT IN (SELECT MAX(transaction_time) FROM " + 
    //                         tablePrefix + "_transaction_limits GROUP BY player_uuid, shop_id, item_id, transaction_type)";
        
    //     plugin.getDataManager().executeAsync(() -> {
    //         try (Connection connection = plugin.getDataManager().getConnection();
    //             PreparedStatement stmt = connection.prepareStatement(plugin.getDataConfig().getDatabaseType().equals("mysql") ? query : sqliteQuery)) {
    //                 return stmt.executeUpdate();
    //         } catch (SQLException e) {
    //             plugin.getLogger().severe("Erreur lors du nettoyage des cooldowns: " + e.getMessage());
    //             return 0;
    //         }
    //     });
    // }
    
    // private static class TransactionLimit {
    //     private final int amount;
    //     private final LimitPeriod period;
    //     private final int cooldown;
        
    //     public TransactionLimit(int amount, LimitPeriod period, int cooldown) {
    //         this.amount = amount;
    //         this.period = period;
    //         this.cooldown = cooldown;
    //     }
        
    //     public int getAmount() {
    //         return amount;
    //     }
        
    //     public LimitPeriod getPeriod() {
    //         return period;
    //     }
        
    //     public int getCooldown() {
    //         return cooldown;
    //     }
    // }
    public static class TransactionLimit {
        private final int amount;
        private final int cooldownSeconds;
        
        public TransactionLimit(int amount, int cooldownSeconds) {
            this.amount = amount;
            this.cooldownSeconds = cooldownSeconds;
        }
        
        public int getAmount() {
            return amount;
        }
        
        public int getCooldown() {
            return cooldownSeconds;
        }
        
        // Détermine la période équivalente pour l'organisation des tables
        public LimitPeriod getPeriodEquivalent() {
            if (cooldownSeconds >= 31536000) return LimitPeriod.FOREVER;
            if (cooldownSeconds >= 2592000) return LimitPeriod.MONTHLY;
            if (cooldownSeconds >= 604800) return LimitPeriod.WEEKLY;
            if (cooldownSeconds >= 86400) return LimitPeriod.DAILY;
            return LimitPeriod.NONE;
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

    /**
     * Réinitialise toutes les limites pour un joueur
     * @param playerUuid UUID du joueur
     * @return Un CompletableFuture contenant le nombre d'entrées supprimées
     */
    public CompletableFuture<Integer> resetAllLimits(UUID playerUuid) {
        String tablePrefix = plugin.getDataConfig().getDatabaseTablePrefix();
        
        // Liste des tables à nettoyer
        String[] tables = {
            tablePrefix + "_tx_daily",
            tablePrefix + "_tx_weekly",
            tablePrefix + "_tx_monthly",
            tablePrefix + "_tx_yearly",
            tablePrefix + "_tx_forever",
            tablePrefix + "_transaction_limits" // Table principale aussi
        };
        
        // CompletableFuture pour attendre toutes les opérations
        CompletableFuture<Integer> totalDeleted = CompletableFuture.completedFuture(0);
        
        for (String table : tables) {
            String query = "DELETE FROM " + table + " WHERE player_uuid = ?";
            
            CompletableFuture<Integer> tableFuture = plugin.getDataManager().executeAsync(() -> {
                try (Connection connection = plugin.getDataManager().getConnection();
                    PreparedStatement stmt = connection.prepareStatement(query)) {
                    stmt.setString(1, playerUuid.toString());
                    return stmt.executeUpdate();
                }
            });
            
            // Additionner les résultats
            totalDeleted = totalDeleted.thenCombine(tableFuture, Integer::sum);
        }
        
        return totalDeleted;
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
    // public void recordBulkTransactions(List<TransactionRecord> records) {
    //     if (records.isEmpty()) {
    //         return;
    //     }
        
    //     // Regrouper les transactions par période (basée sur le type de limite)
    //     Map<String, List<TransactionRecord>> recordsByTable = new HashMap<>();
        
    //     for (TransactionRecord record : records) {
    //         TransactionLimit limit = getTransactionLimit(record.shopId, record.itemId, record.isBuy);
    //         String tablePrefix = plugin.getDataConfig().getDatabaseTablePrefix();
    //         String tableName = tablePrefix + "_transaction_limits"; // Table par défaut
            
    //         if (limit != null) {
    //             // Sélectionner la table partitionnée appropriée
    //             switch (limit.getPeriodEquivalent()) {
    //                 case DAILY:
    //                     tableName = tablePrefix + "_tx_daily";
    //                     break;
    //                 case WEEKLY:
    //                     tableName = tablePrefix + "_tx_weekly";
    //                     break;
    //                 case MONTHLY:
    //                     tableName = tablePrefix + "_tx_monthly";
    //                     break;
    //                 case YEARLY:
    //                     tableName = tablePrefix + "_tx_yearly";
    //                     break;
    //                 case FOREVER:
    //                     tableName = tablePrefix + "_tx_forever";
    //                     break;
    //                 case NONE:
    //                     tableName = tablePrefix + "_transaction_limits"; // Table principale
    //                     break;
    //             }
    //         }
            
    //         recordsByTable.computeIfAbsent(tableName, k -> new ArrayList<>()).add(record);
    //     }
        
    //     // Traiter chaque groupe de transactions pour sa table spécifique
    //     for (Map.Entry<String, List<TransactionRecord>> entry : recordsByTable.entrySet()) {
    //         String tableName = entry.getKey();
    //         List<TransactionRecord> tableRecords = entry.getValue();
            
    //         String query = "INSERT INTO " + tableName + " "
    //                 + "(player_uuid, shop_id, item_id, transaction_type, amount, transaction_time) "
    //                 + "VALUES (?, ?, ?, ?, ?, NOW())";
            
    //         plugin.getDataManager().executeAsync(() -> {
    //             try (Connection connection = plugin.getDataManager().getConnection();
    //                 PreparedStatement stmt = connection.prepareStatement(query)) {
                    
    //                 connection.setAutoCommit(false);
                    
    //                 for (TransactionRecord record : tableRecords) {
    //                     stmt.setString(1, record.playerUuid.toString());
    //                     stmt.setString(2, record.shopId);
    //                     stmt.setString(3, record.itemId);
    //                     stmt.setString(4, record.isBuy ? "BUY" : "SELL");
    //                     stmt.setInt(5, record.amount);
    //                     stmt.addBatch();
    //                 }
                    
    //                 int[] results = stmt.executeBatch();
    //                 connection.commit();
    //                 connection.setAutoCommit(true);
                    
    //                 return results.length;
    //             }
    //         });
    //     }
    // }

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

    // public void optimizeDatabase() {
    //     // plugin.getLogger().info("Optimisation de la base de données des limites...");
        
    //     String tablePrefix = plugin.getDataConfig().getDatabaseTablePrefix();
        
    //     // Créer des tables partitionnées par période - Syntaxe corrigée pour MySQL/MariaDB
    //     String[] periodTables = {
    //         "CREATE TABLE IF NOT EXISTS " + tablePrefix + "_tx_daily LIKE " + tablePrefix + "_transaction_limits",
                
    //         "CREATE TABLE IF NOT EXISTS " + tablePrefix + "_tx_weekly LIKE " + tablePrefix + "_transaction_limits",
                
    //         "CREATE TABLE IF NOT EXISTS " + tablePrefix + "_tx_monthly LIKE " + tablePrefix + "_transaction_limits",
                
    //         "CREATE TABLE IF NOT EXISTS " + tablePrefix + "_tx_yearly LIKE " + tablePrefix + "_transaction_limits",
                
    //         "CREATE TABLE IF NOT EXISTS " + tablePrefix + "_tx_forever LIKE " + tablePrefix + "_transaction_limits"
    //     };
        
    //     for (String query : periodTables) {
    //         try {
    //             plugin.getDataManager().executeUpdate(query);
    //         } catch (Exception e) {
    //             plugin.getLogger().warning("Erreur lors de la création de table: " + e.getMessage());
    //         }
    //     }
        
    //     // Créer une vue pour simplifier les requêtes
    //     String viewQuery = "CREATE OR REPLACE VIEW " + tablePrefix + "_transactions_view AS "
    //         + "SELECT * FROM " + tablePrefix + "_tx_daily UNION ALL "
    //         + "SELECT * FROM " + tablePrefix + "_tx_weekly UNION ALL "
    //         + "SELECT * FROM " + tablePrefix + "_tx_monthly UNION ALL "
    //         + "SELECT * FROM " + tablePrefix + "_tx_yearly UNION ALL "
    //         + "SELECT * FROM " + tablePrefix + "_tx_forever UNION ALL "
    //         + "SELECT * FROM " + tablePrefix + "_transaction_limits";
            
    //     try {
    //         plugin.getDataManager().executeUpdate(viewQuery);
    //     } catch (Exception e) {
    //         // Si la vue ne peut pas être créée (par exemple avec SQLite), on continue sans erreur critique
    //         if (plugin.getDataConfig().getDatabaseType().equals("sqlite")) {
    //             String viewQueryLite = "CREATE VIEW IF NOT EXISTS " + tablePrefix + "_transactions_view AS "
    //                 + "SELECT * FROM " + tablePrefix + "_transaction_limits";
    //             plugin.getDataManager().executeUpdate(viewQueryLite);
    //             plugin.getLogger().info("Note: Impossible de créer la vue de transactions (normal pour SQLite): " + e.getMessage());
    //         } else {
    //             plugin.getLogger().warning("Erreur lors de la création de la vue de transactions: " + e.getMessage());
    //         }
    //     }
    // }

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