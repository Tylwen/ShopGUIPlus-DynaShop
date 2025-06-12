package fr.tylwen.satyria.dynashop.cache;

import java.util.Map;
import java.util.Set;
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

    public V getIfPresent(K key) {
        CacheEntry<V> entry = hotCache.get(key);
        if (entry != null && !entry.isExpired()) {
            return entry.getValue();
        }
        entry = cache.get(key);
        if (entry != null && !entry.isExpired()) {
            return entry.getValue();
        }
        return null;
    }

    public Set<K> keySet() {
        return cache.keySet();
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


// Collecte des informations sur l’espace de travailVoici à quoi servent les différents caches dans `DynaShopPlugin` :

// ---

// ### 1. `priceCache`  
// **Type** : `CacheManager<String, DynamicPrice>`  
// **But** :  
// - Stocke les objets `DynamicPrice` pour chaque item/shop.
// - Permet de ne pas recalculer le prix dynamique complet à chaque accès (lecture rapide du cache).
// - Utilisé pour toutes les opérations qui ont besoin du prix d’un item (achat, vente, affichage…).

// ---

// ### 2. `recipeCache`  
// **Type** : `CacheManager<String, List<ItemStack>>`  
// **But** :  
// - Stocke la liste des ingrédients d’une recette pour chaque item.
// - Évite de relire et parser la config à chaque fois qu’on veut connaître la recette d’un item.
// - Utilisé lors du calcul du prix par recette ou pour afficher les ingrédients.

// ---

// ### 3. `calculatedPriceCache`  
// **Type** : `CacheManager<String, Double>`  
// **But** :  
// - Stocke le résultat du calcul final d’un prix (double) pour un item donné, souvent pour un type précis (buy/sell).
// - Sert à éviter de recalculer le prix (parfois complexe) à chaque demande, surtout pour les items dont le prix dépend de plusieurs facteurs.

// ---

// ### 4. `stockCache`  
// **Type** : `CacheManager<String, Integer>`  
// **But** :  
// - Stocke le stock courant d’un item (quantité disponible).
// - Permet de ne pas interroger la base de données ou la config à chaque accès au stock.
// - Important pour les shops en mode stock ou static_stock.

// ---

// ### 5. `displayPriceCache`  
// **Type** : `CacheManager<String, Map<String, String>>`  
// **But** :  
// - Stocke les valeurs déjà formatées pour les placeholders d’affichage (prix, stock, etc.) pour chaque item.
// - Permet d’accélérer l’affichage des lores dans les menus, car tout est déjà prêt à être injecté dans le texte.
// - Utilisé dans `ShopItemPlaceholderListener` pour éviter de recalculer/formatter à chaque tick ou refresh d’inventaire.

// ---

// **Résumé** :  
// - **priceCache** : prix dynamiques complets (objets).
// - **recipeCache** : ingrédients des recettes.
// - **calculatedPriceCache** : prix finaux (double).
// - **stockCache** : quantités en stock.
// - **displayPriceCache** : valeurs prêtes pour l’affichage (placeholders).

// Chaque cache vise à éviter des recalculs coûteux ou des accès fréquents à la base/config, et à améliorer la performance globale du plugin, surtout en mode "full cache".