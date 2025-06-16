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
package fr.tylwen.satyria.dynashop.compatibility.versions;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import de.tr7zw.changeme.nbtapi.utils.MinecraftVersion;
import fr.tylwen.satyria.dynashop.compatibility.ItemNameProvider;

import java.lang.reflect.Method;

public class ItemNameProvider_1_21 implements ItemNameProvider {
    
    // private static final String[] POSSIBLE_VERSIONS = {"v1_20_R1", "v1_20_R2", "v1_20_R3", "v1_20_R4"};
    private String detectedVersion;
    private Class<?> craftItemStackClass;
    private Method asNMSCopyMethod;
    // private Method getNameMethod;
    private Method getItemMethod;     // Méthode pour obtenir l'Item à partir de ItemStack
    private Method getDescriptionMethod; // Méthode pour obtenir le Component à partir de Item
    private Method getStringMethod;   // Méthode pour obtenir le String à partir de Component
    
    public ItemNameProvider_1_21() {
        // Déterminer automatiquement la version exacte
        // detectedVersion = detectServerVersion();
        detectedVersion = MinecraftVersion.getVersion().getPackageName();
        
        if (detectedVersion != null) {
            try {
                // Charger les classes et méthodes via réflexion
                craftItemStackClass = Class.forName("org.bukkit.craftbukkit." + detectedVersion + ".inventory.CraftItemStack");
                
                // Étape 1: ItemStack bukkit → ItemStack NMS
                asNMSCopyMethod = craftItemStackClass.getMethod("asNMSCopy", ItemStack.class);
                
                // Étape 2: ItemStack NMS → Item NMS
                Class<?> nmsItemStackClass = asNMSCopyMethod.getReturnType();
                getItemMethod = nmsItemStackClass.getMethod("getItem");
                
                // Étape 3: Item NMS → Component
                Class<?> nmsItemClass = getItemMethod.getReturnType();
                getDescriptionMethod = nmsItemClass.getMethod("getDescription");
                
                // Étape 4: Component → String
                Class<?> componentClass = getDescriptionMethod.getReturnType();
                getStringMethod = componentClass.getMethod("getString");
                
                // Bukkit.getLogger().info("[DynaShopGUI+] Version 1.21 détectée: " + detectedVersion);
            } catch (Exception e) {
                Bukkit.getLogger().warning("[DynaShopGUI+] Échec du chargement du fournisseur 1.21: " + e.getMessage());
                // e.printStackTrace(); // Pour déboguer
            }
        }
    }
    
    
    @Override
    public String getLocalizedName(ItemStack item, Player player) {
        if (!isCompatible() || item == null) return null;
        
        try {
            // Convertir ItemStack Bukkit en ItemStack NMS
            Object nmsItemStack = asNMSCopyMethod.invoke(null, item);
            
            // Obtenir l'Item à partir de ItemStack
            Object nmsItem = getItemMethod.invoke(nmsItemStack);
            
            // Obtenir le Component à partir de Item
            Object nameComponent = getDescriptionMethod.invoke(nmsItem);
            
            // Obtenir la chaîne à partir du Component
            return (String) getStringMethod.invoke(nameComponent);
        } catch (Exception e) {
            // Bukkit.getLogger().warning("[DynaShopGUI+] Erreur lors de l'obtention du nom localisé: " + e.getMessage());
            return null;
        }
    }
    
    @Override
    public boolean isCompatible() {
        return detectedVersion != null && craftItemStackClass != null &&
               asNMSCopyMethod != null && getItemMethod != null && getDescriptionMethod != null && getStringMethod != null;
    }
}