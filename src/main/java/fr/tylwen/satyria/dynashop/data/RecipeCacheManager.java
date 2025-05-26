package fr.tylwen.satyria.dynashop.data;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RecipeCacheManager {
    private final Map<String, Double> recipePriceCache = new ConcurrentHashMap<>();
    private final Map<String, Integer> priceStockCache = new ConcurrentHashMap<>();
    private final Map<String, Long> recipeCacheTimestamps = new ConcurrentHashMap<>();
    private final long cacheDurationMillis;
    
    // Utiliser un cache à deux niveaux: hot cache pour les items fréquemment utilisés
    private final Map<String, CacheEntry> hotCache = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry> regularCache = new ConcurrentHashMap<>();
    
    // Ajouter des statistiques d'utilisation
    private final Map<String, Integer> accessCount = new ConcurrentHashMap<>();

    public RecipeCacheManager(long cacheDurationMillis) {
        this.cacheDurationMillis = cacheDurationMillis;
    }

    // public double getCachedRecipePrice(String shopID, String itemID, String priceType) {
    //     String cacheKey = shopID + ":" + itemID + ":" + priceType;
    //     if (recipePriceCache.containsKey(cacheKey) &&
    //         System.currentTimeMillis() - recipeCacheTimestamps.getOrDefault(cacheKey, 0L) < cacheDurationMillis) {
    //         return recipePriceCache.get(cacheKey);
    //     }
    //     return -1.0;
    // }
    public double getCachedRecipePrice(String shopID, String itemID, String priceType) {
        String cacheKey = shopID + ":" + itemID + ":" + priceType;
        
        // Vérifier d'abord dans le hot cache
        CacheEntry hotEntry = hotCache.get(cacheKey);
        if (hotEntry != null && !hotEntry.isExpired(cacheDurationMillis) && "price".equals(hotEntry.getType())) {
            accessCount.merge(cacheKey, 1, Integer::sum);
            return (Double) hotEntry.getValue();
        }
        
        // Puis dans le cache régulier
        CacheEntry regularEntry = regularCache.get(cacheKey);
        if (regularEntry != null && !regularEntry.isExpired(cacheDurationMillis) && "price".equals(regularEntry.getType())) {
            accessCount.merge(cacheKey, 1, Integer::sum);
            
            // Si l'item est fréquemment accédé, le promouvoir au hot cache
            if (accessCount.getOrDefault(cacheKey, 0) > 10) {
                hotCache.put(cacheKey, regularEntry);
                regularCache.remove(cacheKey);
            }
            
            return (Double) regularEntry.getValue();
        }
        
        return -1.0;
    }

    // public int getCachedRecipeStock(String shopID, String itemID, String priceType) {
    //     String cacheKey = shopID + ":" + itemID + ":" + priceType;
    //     if (priceStockCache.containsKey(cacheKey) &&
    //         System.currentTimeMillis() - recipeCacheTimestamps.getOrDefault(cacheKey, 0L) < cacheDurationMillis) {
    //         return priceStockCache.get(cacheKey);
    //     }
    //     return -1;
    // }
    public int getCachedRecipeStock(String shopID, String itemID, String priceType) {
        String cacheKey = shopID + ":" + itemID + ":" + priceType;
        
        // Vérifier d'abord dans le hot cache
        CacheEntry hotEntry = hotCache.get(cacheKey);
        if (hotEntry != null && !hotEntry.isExpired(cacheDurationMillis) && "stock".equals(hotEntry.getType())) {
            accessCount.merge(cacheKey, 1, Integer::sum);
            return (Integer) hotEntry.getValue();
        }
        
        // Puis dans le cache régulier
        CacheEntry regularEntry = regularCache.get(cacheKey);
        if (regularEntry != null && !regularEntry.isExpired(cacheDurationMillis) && "stock".equals(regularEntry.getType())) {
            accessCount.merge(cacheKey, 1, Integer::sum);
            
            // Si l'item est fréquemment accédé, le promouvoir au hot cache
            if (accessCount.getOrDefault(cacheKey, 0) > 10) {
                hotCache.put(cacheKey, regularEntry);
                regularCache.remove(cacheKey);
            }
            
            return (Integer) regularEntry.getValue();
        }
        
        return -1;
    }

    // public void cacheRecipePrice(String shopID, String itemID, String priceType, double price) {
    //     String cacheKey = shopID + ":" + itemID + ":" + priceType;
    //     recipePriceCache.put(cacheKey, price);
    //     recipeCacheTimestamps.put(cacheKey, System.currentTimeMillis());
    // }
    public void cacheRecipePrice(String shopID, String itemID, String priceType, double price) {
        String cacheKey = shopID + ":" + itemID + ":" + priceType;
        CacheEntry entry = new CacheEntry(price, "price");
        regularCache.put(cacheKey, entry);
        recipeCacheTimestamps.put(cacheKey, System.currentTimeMillis());
        
        // Ajouter au hot cache si c'est un item fréquemment utilisé
        accessCount.merge(cacheKey, 1, Integer::sum);
        if (accessCount.get(cacheKey) > 10) {
            hotCache.put(cacheKey, entry);
            regularCache.remove(cacheKey);
        }
    }

    // public void cacheRecipeStock(String shopID, String itemID, String priceType, double stock) {
    //     String cacheKey = shopID + ":" + itemID + ":" + priceType;
    //     priceStockCache.put(cacheKey, (int) stock);
    //     recipeCacheTimestamps.put(cacheKey, System.currentTimeMillis());
    // }
    public void cacheRecipeStock(String shopID, String itemID, String priceType, int stock) {
        String cacheKey = shopID + ":" + itemID + ":" + priceType;
        CacheEntry entry = new CacheEntry(stock, "stock");
        regularCache.put(cacheKey, entry);
        recipeCacheTimestamps.put(cacheKey, System.currentTimeMillis());
        
        // Ajouter au hot cache si c'est un item fréquemment utilisé
        accessCount.merge(cacheKey, 1, Integer::sum);
        if (accessCount.get(cacheKey) > 10) {
            hotCache.put(cacheKey, entry);
            regularCache.remove(cacheKey);
        }
    }

    public void cleanup() {
        long currentTime = System.currentTimeMillis();
        recipePriceCache.entrySet().removeIf(entry ->
            currentTime - recipeCacheTimestamps.getOrDefault(entry.getKey(), 0L) > cacheDurationMillis);
        priceStockCache.entrySet().removeIf(entry ->
            currentTime - recipeCacheTimestamps.getOrDefault(entry.getKey(), 0L) > cacheDurationMillis);
        // Optionnel : nettoyer aussi les timestamps orphelins
    }
    
    // Méthode pour promouvoir des entrées fréquemment utilisées vers le hot cache
    public void promoteFrequentItems() {
        hotCache.putAll(regularCache);
        regularCache.clear();
    }

    public class CacheEntry {
        private final Object value;        // La valeur en cache (prix ou stock)
        private final long timestamp;      // Quand l'entrée a été mise en cache
        private int accessCount;           // Nombre d'accès à cette entrée
        private final String type;         // "price" ou "stock"

        public CacheEntry(Object value, String type) {
            this.value = value;
            this.timestamp = System.currentTimeMillis();
            this.accessCount = 1;
            this.type = type;
        }

        public Object getValue() {
            this.accessCount++;
            return value;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public int getAccessCount() {
            return accessCount;
        }

        public String getType() {
            return type;
        }

        public boolean isExpired(long cacheDurationMillis) {
            return System.currentTimeMillis() - timestamp > cacheDurationMillis;
        }
    }
}
