package fr.tylwen.satyria.dynashop.compatibility;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import de.tr7zw.changeme.nbtapi.utils.MinecraftVersion;
// import de.tr7zw.changeme.nbtapi.utils.VersionChecker;
import fr.tylwen.satyria.dynashop.DynaShopPlugin;
import fr.tylwen.satyria.dynashop.compatibility.versions.*;
// import net.minecraft.server.MinecraftServer;

// import org.bukkit.craftbukkit.v1_16_R3.*;

// import java.util.ArrayList;
// import java.util.List;
import java.util.logging.Logger;

public class ItemNameManager {
    
    private static ItemNameProvider provider;
    private static final Logger logger = Bukkit.getLogger();
    private static MinecraftVersion version;


    // static {
    //     // Liste des fournisseurs disponibles par version
    //     List<ItemNameProvider> providers = new ArrayList<>();
    //     providers.add(new ItemNameProvider_1_16());
    //     providers.add(new ItemNameProvider_1_17());
    //     providers.add(new ItemNameProvider_1_18());
    //     providers.add(new ItemNameProvider_1_19());
    //     providers.add(new ItemNameProvider_1_20());
    //     providers.add(new ItemNameProvider_1_21());
        
    //     // Trouver le premier fournisseur compatible
    //     for (ItemNameProvider p : providers) {
    //         if (p.isCompatible()) {
    //             provider = p;
    //             String className = p.getClass().getSimpleName();
    //             logger.info("[DynaShopGUI+] Utilisation du fournisseur de noms d'items: " + className);
    //             break;
    //         }
    //     }
        
    //     // Si aucun fournisseur n'est trouvé, utiliser une solution par défaut
    //     if (provider == null) {
    //         provider = new ItemNameProviderFallback();
    //         logger.warning("[DynaShopGUI+] Aucun fournisseur de noms d'items compatible trouvé. Utilisation du fallback.");
    //     }
    // }
    static {
        // logger.warning("Bukkit.getVersion(): " + Bukkit.getVersion() + " | Bukkit.getBukkitVersion(): " + Bukkit.getBukkitVersion());
        // String packageName = Bukkit.getServer().getClass().getPackage().getImplementationVersion();
        // if (packageName != null) {
        //     logger.warning("Bukkit.getServer().getClass().getPackage().getImplementationVersion(): " + packageName);
        // } else {
        //     logger.warning("Bukkit.getServer().getClass().getPackage().getImplementationVersion() is null");
        // }
        // logger.warning("Bukkit.getServer().getClass().getPackage().getName(): " + Bukkit.getServer().getClass().getPackage().getName());
        // logger.warning("Bukkit.getServer().getClass().getName(): " + Bukkit.getServer().getClass().getName());
        // MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
        // logger.warning("MinecraftServer: " + server.getClass().getName() + " | Version: " + server.toString());
        
        // String version = getServerVersion();
        String version = MinecraftVersion.getVersion().getPackageName();
        DynaShopPlugin.getInstance().info(version);
        // ItemNameProvider provider = null;
        
        if (version != null) {
            try {
                if (version.startsWith("v1_21_")) {
                    provider = new ItemNameProvider_1_21();
                } else if (version.startsWith("v1_20_")) {
                    provider = new ItemNameProvider_1_20();
                } else if (version.startsWith("v1_19_")) {
                    provider = new ItemNameProvider_1_19();
                } else if (version.startsWith("v1_18_")) {
                    provider = new ItemNameProvider_1_18();
                } else if (version.startsWith("v1_17_")) {
                    provider = new ItemNameProvider_1_17();
                } else if (version.startsWith("v1_16_")) {
                    provider = new ItemNameProvider_1_16();
                }
            } catch (Exception e) {
                logger.warning("[DynaShopGUI+] Erreur lors de la détection du fournisseur de noms d'items: " + e.getMessage());
            }
        }

        // Si aucun fournisseur n'est trouvé, utiliser une solution par défaut
        if (provider == null) {
            provider = new ItemNameProviderFallback();
            logger.warning("[DynaShopGUI+] Aucun fournisseur de noms d'items compatible trouvé. Utilisation du fallback.");
        } else {
            logger.info("[DynaShopGUI+] Utilisation du fournisseur: " + provider.getClass().getSimpleName());
        }
    }

    /**
     * Récupère le nom localisé d'un item selon la version du serveur
     */
    public static String getLocalizedName(ItemStack item, Player player) {
        if (item == null) return "Item inconnu";
        
        try {
            String name = provider.getLocalizedName(item, player);
            DynaShopPlugin.getInstance().info("Nom localisé récupéré: " + name);
            return name != null ? name : formatMaterialName(item.getType().name());
        } catch (Exception e) {
            logger.warning("[DynaShopGUI+] Erreur lors de la récupération du nom localisé: " + e.getMessage());
            return formatMaterialName(item.getType().name());
        }
    }
    
    /**
     * Formate le nom d'un matériau pour un affichage plus lisible
     */
    private static String formatMaterialName(String materialName) {
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

    
    // private static String getServerVersion() {
    //     try {
    //         // String packageName = Bukkit.getServer().getClass().getPackage().getName();
    //         // return packageName.substring(packageName.lastIndexOf('.') + 1);
    //         return Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
    //         // // Méthode 1: Essayer d'obtenir directement depuis la classe CraftServer
    //         // String packageName = Bukkit.getServer().getClass().getPackage().getName();
    //         // if (packageName.contains("craftbukkit.v")) {
    //         //     // Format attendu: org.bukkit.craftbukkit.v1_XX_RY
    //         //     return packageName.substring(packageName.lastIndexOf('.') + 1);
    //         // }
    //         // Si aucune version n'est trouvée, retourner null
    //         // return null;
    //     } catch (Exception e) {
    //         return null;
    //     }
    // }
    // private static String getServerVersion() {
    //     try {
    //         // Obtenir la version de Bukkit (format: "1.20.6-R0.1-SNAPSHOT")
    //         String bukkitVersion = Bukkit.getBukkitVersion();
            
    //         // Extraire la partie numérique majeure (1.20.6)
    //         String[] parts = bukkitVersion.split("-");
    //         String versionNumber = parts[0]; // "1.20.6"
            
    //         // Convertir au format "v1_20_RX"
    //         String[] versionParts = versionNumber.split("\\.");
    //         String majorVersion = versionParts[0]; // "1"
    //         String minorVersion = versionParts[1]; // "20"
            
    //         // Déterminer la révision (R1, R2, R3) en fonction de la version complète
    //         String revision = "R1"; // Par défaut
            
    //         // Logique spécifique pour déterminer la révision
    //         if (minorVersion.equals("20")) {
    //             // Pour Minecraft 1.20.x
    //             if (versionParts.length > 2) {
    //                 int patch = Integer.parseInt(versionParts[2]);
    //                 if (patch >= 5) {
    //                     revision = "R3"; // 1.20.5+ est généralement R3
    //                 } else if (patch >= 2) {
    //                     revision = "R2"; // 1.20.2-1.20.4 est généralement R2
    //                 } else {
    //                     revision = "R1"; // 1.20.0-1.20.1 est généralement R1
    //                 }
    //             }
    //         } else if (minorVersion.equals("19")) {
    //             // Pour Minecraft 1.19.x
    //             if (versionParts.length > 2) {
    //                 int patch = Integer.parseInt(versionParts[2]);
    //                 if (patch >= 3) {
    //                     revision = "R3"; // 1.19.3+ est R3
    //                 } else if (patch >= 1) {
    //                     revision = "R2"; // 1.19.1-1.19.2 est R2
    //                 } else {
    //                     revision = "R1"; // 1.19.0 est R1
    //                 }
    //             }
    //         }
            
    //         // Construire la version au format attendu
    //         String formattedVersion = "v" + majorVersion + "_" + minorVersion + "_" + revision;
    //         logger.info("[DynaShopGUI+] Version détectée: " + formattedVersion + " (de " + bukkitVersion + ")");
            
    //         return formattedVersion;
    //     } catch (Exception e) {
    //         logger.warning("[DynaShopGUI+] Erreur lors de la détection de version: " + e.getMessage());
            
    //         // Fallback basé sur Bukkit.getVersion()
    //         String version = Bukkit.getVersion();
    //         if (version.contains("1.20")) return "v1_20_R3";
    //         if (version.contains("1.19")) return "v1_19_R3";
    //         if (version.contains("1.18")) return "v1_18_R2";
    //         if (version.contains("1.17")) return "v1_17_R1";
    //         if (version.contains("1.16")) return "v1_16_R3";
            
    //         return null;
    //     }
    // }
}