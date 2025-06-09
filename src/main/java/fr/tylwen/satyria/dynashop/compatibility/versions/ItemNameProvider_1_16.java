package fr.tylwen.satyria.dynashop.compatibility.versions;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import fr.tylwen.satyria.dynashop.compatibility.ItemNameProvider;

import java.lang.reflect.Method;

public class ItemNameProvider_1_16 implements ItemNameProvider {
    
    private static final String[] POSSIBLE_VERSIONS = {"v1_16_R1", "v1_16_R2", "v1_16_R3"};
    private String detectedVersion;
    private Class<?> craftItemStackClass;
    private Class<?> nmsItemStackClass;
    private Method asNMSCopyMethod;
    private Method getNameMethod;
    private Method getStringMethod;
    
    public ItemNameProvider_1_16() {
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
                
                Bukkit.getLogger().info("[DynaShopGUI+] Version 1.16 détectée: " + detectedVersion);
            } catch (Exception e) {
                Bukkit.getLogger().warning("[DynaShopGUI+] Échec du chargement du fournisseur 1.16: " + e.getMessage());
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