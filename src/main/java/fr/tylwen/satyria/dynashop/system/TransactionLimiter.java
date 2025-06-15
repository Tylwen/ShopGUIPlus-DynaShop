package fr.tylwen.satyria.dynashop.system;

import fr.tylwen.satyria.dynashop.DynaShopPlugin;
import fr.tylwen.satyria.dynashop.data.cache.LimitCacheEntry;
import fr.tylwen.satyria.dynashop.data.model.TransactionRecord;
import fr.tylwen.satyria.dynashop.data.storage.StorageManager;

import org.bukkit.entity.Player;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Gestionnaire des limites de transactions pour les achats et ventes
 * Prend en charge MySQL/MariaDB et FLATFILE en utilisant le StorageManager central
 */
public class TransactionLimiter {
    private final DynaShopPlugin plugin;
    private final StorageManager storageManager;
    private final ConcurrentLinkedQueue<TransactionRecord> transactionQueue;
    private final Map<String, Integer> metricsCounter;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean running = new AtomicBoolean(true);
    
    /**
     * Périodes de limitation prédéfinies
     */
    public enum LimitPeriod {
        // Code inchangé pour LimitPeriod
        DAILY(86400),      // 24 heures
        WEEKLY(604800),    // 7 jours
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

        public LocalDateTime getStartDate() {
            return LocalDateTime.now().with(TemporalAdjusters.firstDayOfMonth()).withHour(0).withMinute(0).withSecond(0);
        }

        public LocalDateTime getNextReset() {
            switch (this) {
                case DAILY:
                    return LocalDateTime.now().plusDays(1).withHour(0).withMinute(0).withSecond(0);
                case WEEKLY:
                    return LocalDateTime.now().plusWeeks(1).with(TemporalAdjusters.next(java.time.DayOfWeek.MONDAY)).withHour(0).withMinute(0).withSecond(0);
                case MONTHLY:
                    return LocalDateTime.now().plusMonths(1).with(TemporalAdjusters.firstDayOfMonth()).withHour(0).withMinute(0).withSecond(0);
                case YEARLY:
                    return LocalDateTime.now().plusYears(1).withMonth(1).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
                default:
                    return LocalDateTime.MAX; // Pas de réinitialisation
            }
        }
    }

    /**
     * Constructeur
     */
    public TransactionLimiter(DynaShopPlugin plugin) {
        this.plugin = plugin;
        this.transactionQueue = new ConcurrentLinkedQueue<>();
        this.metricsCounter = new ConcurrentHashMap<>();
        
        // // Utiliser le gestionnaire de stockage central
        // if (plugin.getDataConfig().getDatabaseType().equals("flatfile")) {
        //     this.storageManager = plugin.getFlatFileManager();
        // } else {
        //     this.storageManager = plugin.getDataManager();
        // }
        this.storageManager = plugin.getStorageManager();
        
        // Initialiser le système de traitement en arrière-plan
        this.scheduler = Executors.newScheduledThreadPool(1);
        
        // Planifier les tâches périodiques
        scheduler.scheduleWithFixedDelay(this::processQueue, 100, 100, TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(this::cleanupOldData, 12, 24, TimeUnit.HOURS);
    }

    /**
     * Traite la file d'attente des transactions
     */
    private void processQueue() {
        try {
            TransactionRecord record;
            int batchSize = 0;
            List<TransactionRecord> batch = new ArrayList<>();
            
            // Traiter la file par lots pour de meilleures performances
            while ((record = transactionQueue.poll()) != null && batchSize < 50) {
                batch.add(record);
                batchSize++;
            }
            
            if (!batch.isEmpty()) {
                storageManager.saveTransactionsBatch(batch);
                incrementMetric("processed_transactions", batch.size());
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Erreur lors du traitement des transactions: " + e.getMessage());
        }
    }

    /**
     * Nettoie les anciennes données
     */
    private void cleanupOldData() {
        try {
            storageManager.cleanupExpiredTransactions();
        } catch (Exception e) {
            plugin.getLogger().warning("Erreur lors du nettoyage des données: " + e.getMessage());
        }
    }

    /**
     * Ferme proprement le gestionnaire
     */
    public void shutdown() {
        running.set(false);
        
        // Arrêter le planificateur
        try {
            scheduler.shutdown();
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        // Traiter les transactions restantes
        processQueue();
    }

    /**
     * Ajoute une transaction à la file d'attente
     */
    public void queueTransaction(Player player, String shopId, String itemId, boolean isBuy, int amount) {
        if (amount <= 0 || !running.get()) return;
        
        TransactionRecord record = new TransactionRecord(
            player.getUniqueId(), shopId, itemId, isBuy, amount, LocalDateTime.now());
        
        transactionQueue.add(record);
        
        // Invalider le cache
        String cacheKey = DynaShopPlugin.getLimitCacheKey(player.getUniqueId(), shopId, itemId, isBuy);
        plugin.getLimitCache().invalidate(cacheKey);
    }

    /**
     * Vérifie si une transaction peut être effectuée
     */
    public boolean canPerformTransactionSync(Player player, String shopId, String itemId, boolean isBuy, int amount) {
        LimitCacheEntry entry = getTransactionLimit(player, shopId, itemId, isBuy);
        if (entry == null || entry.baseLimit <= 0) {
            return true; // Pas de limite
        }
        
        return entry.remaining >= amount;
    }

    /**
     * Obtient les limites de transaction pour un item spécifique
     */
    public LimitCacheEntry getTransactionLimit(Player player, String shopId, String itemId, boolean isBuy) {
        String cacheKey = DynaShopPlugin.getLimitCacheKey(player.getUniqueId(), shopId, itemId, isBuy);
        
        return plugin.getLimitCache().get(cacheKey, () -> {
            try {
                // Charger la configuration des limites
                String limitPath = isBuy ? "limit.buy" : "limit.sell";
                int limitAmount = plugin.getShopConfigManager()
                        .getItemValue(shopId, itemId, limitPath, Integer.class)
                        .orElse(0);
                
                if (limitAmount <= 0) {
                    return null; // Pas de limite
                }
                
                // Déterminer le cooldown ou la période
                int cooldownSeconds = 0;
                LimitPeriod period = LimitPeriod.NONE;
                
                Optional<Integer> cooldownInt = plugin.getShopConfigManager()
                        .getItemValue(shopId, itemId, "limit.cooldown", Integer.class);
                
                if (cooldownInt.isPresent()) {
                    cooldownSeconds = cooldownInt.get();
                } else {
                    Optional<String> cooldownStr = plugin.getShopConfigManager()
                            .getItemValue(shopId, itemId, "limit.cooldown", String.class);
                    
                    if (cooldownStr.isPresent()) {
                        String periodStr = cooldownStr.get().toUpperCase();
                        try {
                            period = LimitPeriod.valueOf(periodStr);
                            cooldownSeconds = period.getSeconds();
                        } catch (IllegalArgumentException e) {
                            try {
                                cooldownSeconds = Integer.parseInt(periodStr);
                            } catch (NumberFormatException ex) {
                                cooldownSeconds = 0;
                            }
                        }
                    }
                }
                
                if (cooldownSeconds <= 0) {
                    return null; // Pas de limite temporelle
                }
                
                // Déterminer la période pour le cooldown
                if (period == LimitPeriod.NONE) {
                    period = getPeriodForCooldown(cooldownSeconds);
                }
                
                // Calculer les valeurs remaining et nextAvailable
                int remaining = calculateRemainingAmount(player.getUniqueId(), shopId, itemId, isBuy, 
                                                        limitAmount, cooldownSeconds, period);
                
                long nextAvailable = calculateNextAvailableTime(player.getUniqueId(), shopId, itemId, isBuy, 
                                                             cooldownSeconds, period);
                
                // Créer l'entrée de cache
                return new LimitCacheEntry(
                    player.getUniqueId(), shopId, itemId, isBuy, 
                    limitAmount, cooldownSeconds, remaining, nextAvailable);
                
            } catch (Exception e) {
                plugin.getLogger().severe("Erreur lors du calcul des limites: " + e.getMessage());
                return null;
            }
        });
    }

    /**
     * Calcule la quantité restante pour une limite
     */
    private int calculateRemainingAmount(UUID playerUuid, String shopId, String itemId, boolean isBuy, int limitAmount, int cooldownSeconds, LimitPeriod period) {
        LocalDateTime startDate = period != LimitPeriod.NONE 
            ? period.getStartDate() 
            : LocalDateTime.now().minusSeconds(cooldownSeconds);
        
        int used = storageManager.getUsedAmount(playerUuid, shopId, itemId, isBuy, startDate);
        return Math.max(0, limitAmount - used);
    }
    
    public int getRemainingAmountSync(Player player, String shopId, String itemId, boolean isBuy) {
        // Clé de cache standardisée
        final String cacheKey = DynaShopPlugin.getLimitCacheKey(player.getUniqueId(), shopId, itemId, isBuy);
        
        // Vérifier si l'entrée est dans le cache
        LimitCacheEntry entry = plugin.getLimitCache().getIfPresent(cacheKey);
        if (entry != null) {
            return entry.remaining;
        }
        
        // Sinon, charger les limites complètes (ce qui va aussi calculer remaining)
        entry = getTransactionLimit(player, shopId, itemId, isBuy);
        if (entry == null) {
            return Integer.MAX_VALUE; // Pas de limite
        }
        
        return entry.remaining;
    }

    /**
     * Calcule le temps avant la prochaine disponibilité
     */
    private long calculateNextAvailableTime(UUID playerUuid, String shopId, String itemId, boolean isBuy, int cooldownSeconds, LimitPeriod period) {
        if (period != LimitPeriod.NONE && period != LimitPeriod.FOREVER) {
            // Pour les périodes prédéfinies, utiliser la prochaine réinitialisation de période
            LocalDateTime nextReset = period.getNextReset();
            return ChronoUnit.MILLIS.between(LocalDateTime.now(), nextReset);
        }
        
        // Pour les cooldowns personnalisés, vérifier la dernière transaction
        Optional<LocalDateTime> lastTransaction = storageManager.getLastTransactionTime(playerUuid, shopId, itemId, isBuy);
        
        if (lastTransaction.isPresent()) {
            LocalDateTime nextAvailable = lastTransaction.get().plusSeconds(cooldownSeconds);
            long millisUntilAvailable = ChronoUnit.MILLIS.between(LocalDateTime.now(), nextAvailable);
            return Math.max(0, millisUntilAvailable);
        }
        
        return 0; // Disponible immédiatement
    }
    
    public long getNextAvailableTimeSync(Player player, String shopId, String itemId, boolean isBuy) {
        // Clé de cache standardisée
        final String cacheKey = DynaShopPlugin.getLimitCacheKey(player.getUniqueId(), shopId, itemId, isBuy);
        
        // Vérifier si l'entrée est dans le cache
        LimitCacheEntry entry = plugin.getLimitCache().getIfPresent(cacheKey);
        if (entry != null) {
            return entry.nextAvailable;
        }
        
        // Sinon, charger les limites complètes (ce qui va aussi calculer nextAvailable)
        entry = getTransactionLimit(player, shopId, itemId, isBuy);
        if (entry == null) {
            return 0L; // Pas de limite
        }
        
        return entry.nextAvailable;
    }

    /**
     * Déterminer la période équivalente pour un cooldown donné
     */
    private LimitPeriod getPeriodForCooldown(int cooldown) {
        if (cooldown >= 31536000) return LimitPeriod.FOREVER;
        if (cooldown >= 2592000) return LimitPeriod.MONTHLY;
        if (cooldown >= 604800) return LimitPeriod.WEEKLY;
        if (cooldown >= 86400) return LimitPeriod.DAILY;
        return LimitPeriod.NONE;
    }

    /**
     * Réinitialise les limites pour un item spécifique
     */
    public CompletableFuture<Boolean> resetLimits(Player player, String shopId, String itemId) {
        UUID playerUuid = player.getUniqueId();
        
        return CompletableFuture.supplyAsync(() -> {
            boolean success = storageManager.resetLimits(playerUuid, shopId, itemId);
            
            // Invalider le cache pour cet item
            String buyCacheKey = DynaShopPlugin.getLimitCacheKey(playerUuid, shopId, itemId, true);
            String sellCacheKey = DynaShopPlugin.getLimitCacheKey(playerUuid, shopId, itemId, false);
            plugin.getLimitCache().invalidate(buyCacheKey);
            plugin.getLimitCache().invalidate(sellCacheKey);
            
            return success;
        });
    }

    /**
     * Réinitialise toutes les limites pour un joueur
     */
    public CompletableFuture<Boolean> resetAllLimits(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> storageManager.resetAllLimits(playerUuid));
    }

    /**
     * Réinitialise toutes les limites pour tous les joueurs
     */
    public CompletableFuture<Boolean> resetAllLimits() {
        return CompletableFuture.supplyAsync(() -> storageManager.resetAllLimits());
    }

    /**
     * Obtient des statistiques sur l'utilisation
     */
    public CompletableFuture<Map<String, Object>> getStatistics() {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Object> stats = new HashMap<>();
            
            // Ajouter les métriques locales
            stats.putAll(metricsCounter);
            
            // Ajouter les statistiques du stockage
            stats.putAll(storageManager.getStatistics());
            
            return stats;
        });
    }

    /**
     * Incrémente un compteur de métrique
     */
    private void incrementMetric(String metricName, int value) {
        metricsCounter.merge(metricName, value, Integer::sum);
    }
}