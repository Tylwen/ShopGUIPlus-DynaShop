package fr.tylwen.satyria.dynashop.data.storage;

import fr.tylwen.satyria.dynashop.price.DynamicPrice;
// import fr.tylwen.satyria.dynashop.data.cache.LimitCacheEntry;
import fr.tylwen.satyria.dynashop.system.chart.PriceHistory;
import fr.tylwen.satyria.dynashop.system.chart.PriceHistory.PriceDataPoint;
import fr.tylwen.satyria.dynashop.system.TransactionLimiter.TransactionRecord;
import net.brcdev.shopgui.shop.item.ShopItem;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Interface principale pour la gestion du stockage des données
 * Implémentée par des classes spécifiques pour chaque type de stockage (MySQL, FLATFILE)
 */
public interface StorageManager {
    
    /**
     * Initialise le système de stockage
     */
    void initialize();
    
    /**
     * Ferme proprement le système de stockage
     */
    void shutdown();

    // ============ MÉTHODES DE GESTION DES PRIX ============
    
    /**
     * Récupère un prix dynamique pour un item spécifique
     */
    Optional<DynamicPrice> getPrices(String shopId, String itemId);
    
    /**
     * Récupère le prix d'achat d'un item
     */
    Optional<Double> getBuyPrice(String shopId, String itemId);
    
    /**
     * Récupère le prix de vente d'un item
     */
    Optional<Double> getSellPrice(String shopId, String itemId);
    
    /**
     * Récupère le stock d'un item
     */
    Optional<Integer> getStock(String shopId, String itemId);
    
    /**
     * Enregistre un prix dynamique pour un item
     */
    void savePrice(String shopId, String itemId, double buyPrice, double sellPrice, int stock);
    
    /**
     * Enregistre uniquement le prix d'achat d'un item
     */
    void saveBuyPrice(String shopId, String itemId, double buyPrice);
    
    /**
     * Enregistre uniquement le prix de vente d'un item
     */
    void saveSellPrice(String shopId, String itemId, double sellPrice);
    
    /**
     * Enregistre uniquement le stock d'un item
     */
    void saveStock(String shopId, String itemId, int stock);

    /**
     * Supprime uniquement le stock d'un item
     */
    void deleteStock(String shopId, String itemId);

    /**
     * Nettoie la table de stock en supprimant les entrées pour les items qui ne sont pas en mode STOCK ou STATIC_STOCK
     */
    void cleanupStockTable();

    /**
     * Supprime un item de la base de données
     */
    void deleteItem(String shopId, String itemId);
    
    /**
     * Vérifie si un item existe dans la base de données
     */
    boolean itemExists(String shopId, String itemId);
    
    /**
     * Charge tous les prix depuis la base de données
     */
    Map<ShopItem, DynamicPrice> loadAllPrices();

    // ============ MÉTHODES DE GESTION DES TRANSACTIONS ============
    
    /**
     * Enregistre un lot de transactions
     */
    void saveTransactionsBatch(List<TransactionRecord> transactions);
    
    /**
     * Obtient la quantité utilisée pour une limite de transaction
     */
    int getUsedAmount(UUID playerUuid, String shopId, String itemId, boolean isBuy, LocalDateTime since);
    
    /**
     * Obtient le timestamp de la dernière transaction
     */
    Optional<LocalDateTime> getLastTransactionTime(UUID playerUuid, String shopId, String itemId, boolean isBuy);
    
    /**
     * Réinitialise les limites pour un item spécifique
     */
    boolean resetLimits(UUID playerUuid, String shopId, String itemId);
    
    /**
     * Réinitialise toutes les limites pour un joueur
     */
    boolean resetAllLimits(UUID playerUuid);
    
    /**
     * Réinitialise toutes les limites pour tous les joueurs
     */
    boolean resetAllLimits();
    
    /**
     * Nettoie les transactions expirées
     */
    void cleanupExpiredTransactions();

    // ============ MÉTHODES DE GESTION DE L'HISTORIQUE DES PRIX ============
    
    /**
     * Récupère l'historique des prix d'un item
     */
    PriceHistory getPriceHistory(String shopId, String itemId);

    /**
     * Récupère l'historique des prix d'un item avec un intervalle spécifique
     */
    public List<PriceDataPoint> getAggregatedPriceHistory(String shopId, String itemId, int interval, LocalDateTime startTime, int maxPoints);

    /**
     * Sauvegarde un point de données historique
     */
    void savePriceDataPoint(String shopId, String itemId, PriceHistory.PriceDataPoint point, int intervalMinutes);
    
    /**
     * Purge l'ancien historique des prix
     */
    void purgeOldPriceHistory(int daysToKeep);
    
    // ============ MÉTHODES DE GESTION DE L'INFLATION ============
    
    /**
     * Récupère le facteur d'inflation actuel
     */
    double getInflationFactor();
    
    /**
     * Récupère le timestamp de la dernière mise à jour de l'inflation
     */
    long getLastInflationUpdate();
    
    /**
     * Enregistre les données d'inflation
     */
    void saveInflationData(double factor, long timestamp);
    
    // ============ MÉTHODES UTILITAIRES ============
    
    /**
     * Exécute une opération de base de données de manière asynchrone
     */
    <T> CompletableFuture<T> executeAsync(DatabaseOperation<T> operation);
    
    /**
     * Obtient des statistiques sur l'utilisation
     */
    Map<String, Object> getStatistics();
    
    /**
     * Interface fonctionnelle pour les opérations de base de données
     */
    @FunctionalInterface
    interface DatabaseOperation<T> {
        T execute() throws Exception;
    }
}