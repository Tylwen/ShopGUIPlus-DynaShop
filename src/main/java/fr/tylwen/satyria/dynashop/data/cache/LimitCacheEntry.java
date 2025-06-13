package fr.tylwen.satyria.dynashop.data.cache;

import java.util.UUID;

import fr.tylwen.satyria.dynashop.DynaShopPlugin;
import fr.tylwen.satyria.dynashop.system.TransactionLimiter.LimitPeriod;

public class LimitCacheEntry {
    final DynaShopPlugin plugin = DynaShopPlugin.getInstance();

    public UUID playerUuid;
    public String shopId;
    public String itemId;
    public boolean isBuy;
    public int baseLimit;
    public int cooldown; // en secondes
    public int remaining; // ce qui reste à acheter/vendre
    public long nextAvailable; // timestamp millis

    public LimitCacheEntry(UUID playerUuid, String shopId, String itemId, boolean isBuy, int baseLimit, int cooldown, int remaining, long nextAvailable) {
        this.playerUuid = playerUuid;
        this.shopId = shopId;
        this.itemId = itemId;
        this.isBuy = isBuy;
        this.baseLimit = baseLimit;
        this.cooldown = cooldown;
        this.remaining = remaining;
        this.nextAvailable = nextAvailable;
    }

    // Constructeur vide pour Gson
    public LimitCacheEntry() {}
    
    public int getLimit() {
        return baseLimit;
    }
    
    public int getCooldown() {
        return cooldown;
    }

    /**
     * Détermine la période équivalente pour un cooldown donné
     */
    public LimitPeriod getPeriodEquivalent() {
        if (cooldown >= 31536000) return LimitPeriod.FOREVER;
        if (cooldown >= 2592000) return LimitPeriod.MONTHLY;
        if (cooldown >= 604800) return LimitPeriod.WEEKLY;
        if (cooldown >= 86400) return LimitPeriod.DAILY;
        return LimitPeriod.NONE;
    }
}