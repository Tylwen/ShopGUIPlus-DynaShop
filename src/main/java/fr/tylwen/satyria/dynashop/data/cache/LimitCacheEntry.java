/*
 * ShopGUI+ DynaShop - Dynamic Economy Addon for Minecraft
 * Copyright (C) 2025 Tylwen
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
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