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
package fr.tylwen.satyria.dynashop.compatibility;

import java.lang.reflect.Method;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Fournisseur par défaut qui n'utilise pas de code NMS
 */
public class ItemNameProviderFallback implements ItemNameProvider {
    
    @Override
    public String getLocalizedName(ItemStack item, Player player) {
        // 1. Vérifier si l'item a un nom personnalisé
        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasDisplayName()) {
                return ChatColor.stripColor(meta.getDisplayName());
            }
            
            // 2. Vérifier si l'item a un nom localisé (disponible après 1.14)
            try {
                Method getLocalizedNameMethod = ItemMeta.class.getMethod("getLocalizedName");
                if (getLocalizedNameMethod != null) {
                    String localizedName = (String) getLocalizedNameMethod.invoke(meta);
                    if (localizedName != null && !localizedName.isEmpty()) {
                        return localizedName;
                    }
                }
            } catch (Exception ignored) {
                // La méthode n'existe peut-être pas dans cette version
            }
        }
        
        // 3. Formater le nom du matériau
        String materialName = item.getType().name();
        return formatMaterialName(materialName);
    }
    
    @Override
    public boolean isCompatible() {
        // Toujours compatible car c'est une solution de repli
        return true;
    }
    
    private String formatMaterialName(String materialName) {
        String[] words = materialName.toLowerCase().split("_");
        StringBuilder result = new StringBuilder();
        
        for (String word : words) {
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)))
                      .append(word.substring(1))
                      .append(" ");
            }
        }
        
        return result.toString().trim();
    }
}