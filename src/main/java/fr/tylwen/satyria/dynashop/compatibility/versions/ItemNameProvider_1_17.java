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

import fr.tylwen.satyria.dynashop.compatibility.ItemNameProvider;

import java.lang.reflect.Method;

public class ItemNameProvider_1_17 implements ItemNameProvider {
    
    private static final String[] POSSIBLE_VERSIONS = {"v1_17_R1"};
    private String detectedVersion;
    private Class<?> craftItemStackClass;
    private Class<?> nmsItemStackClass;
    private Method asNMSCopyMethod;
    private Method getNameMethod;
    private Method getStringMethod;
    
    public ItemNameProvider_1_17() {
        // Déterminer automatiquement la version exacte
        detectedVersion = detectServerVersion();
        
        if (detectedVersion != null) {
            try {
                // Charger les classes et méthodes via réflexion
                craftItemStackClass = Class.forName("org.bukkit.craftbukkit." + detectedVersion + ".inventory.CraftItemStack");
                nmsItemStackClass = Class.forName("net.minecraft.server." + detectedVersion + ".ItemStack");
                
                asNMSCopyMethod = craftItemStackClass.getMethod("asNMSCopy", ItemStack.class);
                getNameMethod = nmsItemStackClass.getMethod("getName");
                
                // Pour 1.16, getName() retourne un IChatBaseComponent qui a une méthode getString()
                Class<?> iChatBaseComponentClass = Class.forName("net.minecraft.server." + detectedVersion + ".IChatBaseComponent");
                getStringMethod = iChatBaseComponentClass.getMethod("getString");
                
                Bukkit.getLogger().info("[DynaShopGUI+] Version 1.17 détectée: " + detectedVersion);
            } catch (Exception e) {
                Bukkit.getLogger().warning("[DynaShopGUI+] Échec du chargement du fournisseur 1.17: " + e.getMessage());
            }
        }
    }
    
    /**
     * Détecte la version exacte du serveur
     */
    private String detectServerVersion() {
        // Méthode 1: Vérifier le package de CraftServer
        try {
            Class<?> serverClass = Bukkit.getServer().getClass();
            String packageName = serverClass.getPackage().getName();
            return packageName.substring(packageName.lastIndexOf('.') + 1);
        } catch (Exception e) {
            // Méthode 2: Essayer chaque version possible
            for (String version : POSSIBLE_VERSIONS) {
                try {
                    Class.forName("org.bukkit.craftbukkit." + version + ".CraftServer");
                    return version;
                } catch (ClassNotFoundException ignored) {
                    // Continuer avec la prochaine version
                }
            }
        }
        return null;
    }
    
    @Override
    public String getLocalizedName(ItemStack item, Player player) {
        try {
            Object nmsItem = asNMSCopyMethod.invoke(null, item);
            Object nameComponent = getNameMethod.invoke(nmsItem);
            return (String) getStringMethod.invoke(nameComponent);
        } catch (Exception e) {
            return null;
        }
    }
    
    @Override
    public boolean isCompatible() {
        return detectedVersion != null && craftItemStackClass != null && nmsItemStackClass != null;
    }
}