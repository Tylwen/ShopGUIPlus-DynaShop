package fr.tylwen.satyria.dynashop.compatibility.versions;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import fr.tylwen.satyria.dynashop.compatibility.ItemNameProvider;

// import org.bukkit.craftbukkit.v1_20_R4.inventory.CraftItemStack; // Import spécifique pour la version 1.20

import java.lang.reflect.Method;

public class ItemNameProvider_1_20 implements ItemNameProvider {
    
    private static final String[] POSSIBLE_VERSIONS = {"v1_20_R1", "v1_20_R2", "v1_20_R3", "v1_20_R4"};
    private String detectedVersion;
    private Class<?> craftItemStackClass;
    private Method asNMSCopyMethod;
    // private Method getNameMethod;
    private Method getItemMethod;     // Méthode pour obtenir l'Item à partir de ItemStack
    private Method getDescriptionMethod; // Méthode pour obtenir le Component à partir de Item
    private Method getStringMethod;   // Méthode pour obtenir le String à partir de Component
    
    public ItemNameProvider_1_20() {
        // Déterminer automatiquement la version exacte
        detectedVersion = detectServerVersion();
        
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
                
                Bukkit.getLogger().info("[DynaShopGUI+] Version 1.20 détectée: " + detectedVersion);
            } catch (Exception e) {
                Bukkit.getLogger().warning("[DynaShopGUI+] Échec du chargement du fournisseur 1.20: " + e.getMessage());
                e.printStackTrace(); // Pour déboguer
            }
        }
    }
    
    /**
     * Détecte la version exacte du serveur
     */
    private String detectServerVersion() {
        try {
            // // Obtenir la version à partir du package de CraftServer
            String packageName = Bukkit.getServer().getClass().getPackage().getName();
            // // Retourne seulement la partie version (ex: v1_20_R1)
            return packageName.substring(packageName.lastIndexOf('.') + 1);
            // return "v1_20_R4"; // Version par défaut pour les tests, à remplacer par la détection dynamique
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
            // Convertir ItemStack Bukkit en ItemStack NMS
            Object nmsItemStack = asNMSCopyMethod.invoke(null, item);
            
            // Obtenir l'Item à partir de ItemStack
            Object nmsItem = getItemMethod.invoke(nmsItemStack);
            
            // Obtenir le Component à partir de Item
            Object nameComponent = getDescriptionMethod.invoke(nmsItem);
            
            // Obtenir la chaîne à partir du Component
            return (String) getStringMethod.invoke(nameComponent);
        } catch (Exception e) {
            Bukkit.getLogger().warning("[DynaShopGUI+] Erreur lors de l'obtention du nom localisé: " + e.getMessage());
            return null;
        }
    }
    
    @Override
    public boolean isCompatible() {
        return detectedVersion != null && craftItemStackClass != null &&
               asNMSCopyMethod != null && getItemMethod != null && getDescriptionMethod != null && getStringMethod != null;
    }
}