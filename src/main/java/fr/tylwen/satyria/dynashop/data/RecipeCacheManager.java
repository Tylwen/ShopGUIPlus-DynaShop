package fr.tylwen.satyria.dynashop.data;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RecipeCacheManager {
    private final Map<String, Double> recipePriceCache = new ConcurrentHashMap<>();
    private final Map<String, Integer> priceStockCache = new ConcurrentHashMap<>();
    private final Map<String, Long> recipeCacheTimestamps = new ConcurrentHashMap<>();
    private final long cacheDurationMillis;

    public RecipeCacheManager(long cacheDurationMillis) {
        this.cacheDurationMillis = cacheDurationMillis;
    }

    public double getCachedRecipePrice(String shopID, String itemID, String priceType) {
        String cacheKey = shopID + ":" + itemID + ":" + priceType;
        if (recipePriceCache.containsKey(cacheKey) &&
            System.currentTimeMillis() - recipeCacheTimestamps.getOrDefault(cacheKey, 0L) < cacheDurationMillis) {
            return recipePriceCache.get(cacheKey);
        }
        return -1.0;
    }

    public int getCachedRecipeStock(String shopID, String itemID, String priceType) {
        String cacheKey = shopID + ":" + itemID + ":" + priceType;
        if (priceStockCache.containsKey(cacheKey) &&
            System.currentTimeMillis() - recipeCacheTimestamps.getOrDefault(cacheKey, 0L) < cacheDurationMillis) {
            return priceStockCache.get(cacheKey);
        }
        return -1;
    }

    public void cacheRecipePrice(String shopID, String itemID, String priceType, double price) {
        String cacheKey = shopID + ":" + itemID + ":" + priceType;
        recipePriceCache.put(cacheKey, price);
        recipeCacheTimestamps.put(cacheKey, System.currentTimeMillis());
    }

    public void cacheRecipeStock(String shopID, String itemID, String priceType, double stock) {
        String cacheKey = shopID + ":" + itemID + ":" + priceType;
        priceStockCache.put(cacheKey, (int) stock);
        recipeCacheTimestamps.put(cacheKey, System.currentTimeMillis());
    }

    public void cleanup() {
        long currentTime = System.currentTimeMillis();
        recipePriceCache.entrySet().removeIf(entry ->
            currentTime - recipeCacheTimestamps.getOrDefault(entry.getKey(), 0L) > cacheDurationMillis);
        priceStockCache.entrySet().removeIf(entry ->
            currentTime - recipeCacheTimestamps.getOrDefault(entry.getKey(), 0L) > cacheDurationMillis);
        // Optionnel : nettoyer aussi les timestamps orphelins
    }
}
