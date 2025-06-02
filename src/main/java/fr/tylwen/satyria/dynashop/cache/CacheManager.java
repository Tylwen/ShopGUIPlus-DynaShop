package fr.tylwen.satyria.dynashop.cache;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.concurrent.TimeUnit;
import org.bukkit.Bukkit;
import fr.tylwen.satyria.dynashop.DynaShopPlugin;

/**
 * Gestionnaire de cache centralisé pour DynaShopGUI+
 * @param <K> Type de la clé
 * @param <V> Type de la valeur
 */
public class CacheManager<K, V> {
    private final Map<K, CacheEntry<V>> cache = new ConcurrentHashMap<>();
    private final Map<K, CacheEntry<V>> hotCache = new ConcurrentHashMap<>();
    private final Map<K, Integer> accessCount = new ConcurrentHashMap<>();
    
    private final long cacheDuration;
    private final TimeUnit timeUnit;
    private final String cacheType;
    private final int promotionThreshold;
    
    private final DynaShopPlugin plugin;
    
    /**
     * Crée un nouveau gestionnaire de cache
     * @param plugin Instance du plugin
     * @param cacheType Nom du type de cache (pour les logs)
     * @param duration Durée de validité du cache
     * @param timeUnit Unité de temps pour la durée
     * @param promotionThreshold Nombre d'accès avant promotion dans le hot cache
     */
    public CacheManager(DynaShopPlugin plugin, String cacheType, long duration, TimeUnit timeUnit, int promotionThreshold) {
        this.plugin = plugin;
        this.cacheType = cacheType;
        this.cacheDuration = duration;
        this.timeUnit = timeUnit;
        this.promotionThreshold = promotionThreshold;
        
        // Planifier le nettoyage périodique
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::cleanup, 20*60, 20*60); // Toutes les minutes
    }
    
    /**
     * Récupère une valeur du cache ou la charge si elle n'existe pas ou est expirée
     * @param key Clé de l'entrée
     * @param loader Fournisseur pour charger la valeur si nécessaire
     * @return La valeur (du cache ou nouvellement chargée)
     */
    public V get(K key, Supplier<V> loader) {
        // Vérifier d'abord le hot cache
        CacheEntry<V> hotEntry = hotCache.get(key);
        if (hotEntry != null && !hotEntry.isExpired()) {
            accessCount.merge(key, 1, Integer::sum);
            return hotEntry.getValue();
        }
        
        // Puis vérifier le cache régulier
        CacheEntry<V> entry = cache.get(key);
        if (entry != null && !entry.isExpired()) {
            // Incrémenter le compteur d'accès
            int count = accessCount.merge(key, 1, Integer::sum);
            
            // Promouvoir dans le hot cache si le seuil est atteint
            if (count >= promotionThreshold) {
                hotCache.put(key, entry);
                cache.remove(key);
                
                // // Log pour déboguer (à retirer en production)
                // plugin.getLogger().fine(cacheType + " - Promotion de la clé " + key + " dans le hot cache");
            }
            
            return entry.getValue();
        }
        
        // Charger la valeur si elle n'est pas en cache ou est expirée
        V value = loader.get();
        CacheEntry<V> newEntry = new CacheEntry<>(value, cacheDuration, timeUnit);
        
        // Déterminer où mettre la nouvelle entrée
        if (accessCount.getOrDefault(key, 0) >= promotionThreshold) {
            hotCache.put(key, newEntry);
        } else {
            cache.put(key, newEntry);
        }
        
        return value;
    }
    
    /**
     * Force la mise en cache d'une valeur
     */
    public void put(K key, V value) {
        CacheEntry<V> entry = new CacheEntry<>(value, cacheDuration, timeUnit);
        
        if (accessCount.getOrDefault(key, 0) >= promotionThreshold) {
            hotCache.put(key, entry);
        } else {
            cache.put(key, entry);
        }
    }
    
    /**
     * Invalide une entrée du cache
     */
    public void invalidate(K key) {
        cache.remove(key);
        hotCache.remove(key);
    }
    
    /**
     * Invalide toutes les entrées qui correspondent à un préfixe
     */
    public void invalidateWithPrefix(String prefix) {
        cache.keySet().removeIf(k -> k.toString().startsWith(prefix));
        hotCache.keySet().removeIf(k -> k.toString().startsWith(prefix));
    }
    
    /**
     * Nettoie les entrées expirées
     */
    public void cleanup() {
        long now = System.currentTimeMillis();
        
        // Nettoyer le cache régulier
        cache.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
        
        // Nettoyer le hot cache
        hotCache.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
        
        // Nettoyer les compteurs d'accès pour les entrées qui n'existent plus
        accessCount.entrySet().removeIf(entry -> 
            !cache.containsKey(entry.getKey()) && !hotCache.containsKey(entry.getKey()));
    }
    
    /**
     * Vide complètement le cache
     */
    public void clear() {
        cache.clear();
        hotCache.clear();
        accessCount.clear();
    }
    
    /**
     * Classe interne représentant une entrée de cache
     */
    private static class CacheEntry<V> {
        private final V value;
        private final long expirationTime;
        
        CacheEntry(V value, long duration, TimeUnit timeUnit) {
            this.value = value;
            this.expirationTime = System.currentTimeMillis() + timeUnit.toMillis(duration);
        }
        
        V getValue() {
            return value;
        }
        
        boolean isExpired() {
            return isExpired(System.currentTimeMillis());
        }
        
        boolean isExpired(long now) {
            return now > expirationTime;
        }
    }
}