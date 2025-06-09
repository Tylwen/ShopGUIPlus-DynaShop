package fr.tylwen.satyria.dynashop.compatibility.versions;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import fr.tylwen.satyria.dynashop.compatibility.ItemNameProvider;

import java.lang.reflect.Method;

public class ItemNameProvider_1_21 implements ItemNameProvider {
    
    private static final String[] POSSIBLE_VERSIONS = {"v1_21_R1", "v1_21_R2", "v1_21_R3", "v1_21_R4"};
    private String detectedVersion;
    private Class<?> craftItemStackClass;
    private Method asNMSCopyMethod;
    private Method getNameMethod;
    private Method getStringMethod;

    public ItemNameProvider_1_21() {
        // Déterminer automatiquement la version exacte
        detectedVersion = detectServerVersion();
        
        if (detectedVersion != null) {
            try {
                // Correction - Utiliser le chemin correct sans duplication
                craftItemStackClass = Class.forName("org.bukkit.craftbukkit." + detectedVersion + ".inventory.CraftItemStack");
                
                // Dans 1.21, les packages NMS ont changé de structure
                asNMSCopyMethod = craftItemStackClass.getMethod("asNMSCopy", ItemStack.class);
                
                // Reste du code inchangé
                Class<?> nmsItemClass = asNMSCopyMethod.getReturnType();
                getNameMethod = nmsItemClass.getMethod("getName");
                Class<?> componentClass = getNameMethod.getReturnType();
                getStringMethod = componentClass.getMethod("getString");
                
                Bukkit.getLogger().info("[DynaShopGUI+] Version 1.21 détectée: " + detectedVersion);
            } catch (Exception e) {
                Bukkit.getLogger().warning("[DynaShopGUI+] Échec du chargement du fournisseur 1.21: " + e.getMessage());
                e.printStackTrace(); // Pour déboguer
            }
        }
    }
    
    /**
     * Détecte la version exacte du serveur
     */
    private String detectServerVersion() {
        try {
            // Obtenir la version à partir du package de CraftServer
            String packageName = Bukkit.getServer().getClass().getPackage().getName();
            // Retourne seulement la partie version (ex: v1_20_R1)
            return packageName.substring(packageName.lastIndexOf('.') + 1);
        } catch (Exception e) {
            // Méthode alternative: essayer explicitement chaque version
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
        if (!isCompatible() || item == null) return null;
        
        try {
            Object nmsItem = asNMSCopyMethod.invoke(null, item);
            Object nameComponent = getNameMethod.invoke(nmsItem);
            return (String) getStringMethod.invoke(nameComponent);
        } catch (Exception e) {
            Bukkit.getLogger().warning("[DynaShopGUI+] Erreur lors de l'obtention du nom localisé: " + e.getMessage());
            return null;
        }
    }
    
    @Override
    public boolean isCompatible() {
        return detectedVersion != null && craftItemStackClass != null && 
               asNMSCopyMethod != null && getNameMethod != null && getStringMethod != null;
    }
}