// package fr.tylwen.satyria.dynashop.gui;

// import fr.tylwen.satyria.dynashop.DynaShopPlugin;
// import net.brcdev.shopgui.ShopGuiPlusApi;
// import net.brcdev.shopgui.exception.shop.ShopsNotLoadedException;
// import net.brcdev.shopgui.shop.Shop;
// import net.brcdev.shopgui.shop.ShopManager;
// import org.bukkit.Bukkit;
// import org.bukkit.entity.Player;
// import org.bukkit.inventory.InventoryView;
// import org.bukkit.scheduler.BukkitTask;

// import java.util.HashMap;
// import java.util.Map;
// import java.util.UUID;
// import java.util.concurrent.ConcurrentHashMap;
// import java.util.regex.Matcher;
// import java.util.regex.Pattern;

// public class ShopRefreshManager {
//     private final DynaShopPlugin plugin;
//     private final Map<UUID, ShopSession> activeSessions = new ConcurrentHashMap<>();
//     private BukkitTask refreshTask;
    
//     // Modèle pour extraire le numéro de page du titre de l'inventaire
//     private static final Pattern PAGE_PATTERN = Pattern.compile("#(\\d+)");
    
//     public ShopRefreshManager(DynaShopPlugin plugin) {
//         this.plugin = plugin;
//         startRefreshTask();
//     }
    
//     /**
//      * Démarre une tâche planifiée pour rafraîchir les inventaires périodiquement
//      */
//     private void startRefreshTask() {
//         // Annuler toute tâche existante
//         if (refreshTask != null) {
//             refreshTask.cancel();
//         }
        
//         // Planifier une nouvelle tâche toutes les 5 secondes
//         refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
//             // Pour chaque joueur avec une session active
//             for (Map.Entry<UUID, ShopSession> entry : new HashMap<>(activeSessions).entrySet()) {
//                 UUID playerId = entry.getKey();
//                 ShopSession session = entry.getValue();
//                 Player player = Bukkit.getPlayer(playerId);
                
//                 // Si le joueur est toujours connecté et a un inventaire ouvert
//                 if (player != null && player.isOnline() && player.getOpenInventory() != null) {
//                     // Si l'inventaire ouvert est toujours un shop et n'a pas changé
//                     String currentShop;
//                     try {
//                         currentShop = extractShopId(player.getOpenInventory());
//                     } catch (ShopsNotLoadedException e) {
//                         e.printStackTrace();
//                         continue;
//                     }
//                     if (currentShop != null && currentShop.equals(session.shopId)) {
//                         // Rafraîchir l'inventaire
//                         refreshShop(player, session);
//                     } else {
//                         // Le joueur a changé d'inventaire, supprimer la session
//                         activeSessions.remove(playerId);
//                     }
//                 } else {
//                     // Le joueur s'est déconnecté, supprimer la session
//                     activeSessions.remove(playerId);
//                 }
//             }
//         }, 100L, 100L); // 5 secondes (100 ticks)
//     }
    
//     /**
//      * Arrête proprement le gestionnaire de rafraîchissement
//      */
//     public void shutdown() {
//         if (refreshTask != null) {
//             refreshTask.cancel();
//             refreshTask = null;
//         }
//         activeSessions.clear();
//     }
    
//     /**
//      * Enregistre une nouvelle session de shop pour un joueur
//      */
//     public void registerSession(Player player, String shopId, int page) {
//         ShopSession session = new ShopSession(shopId, page);
//         activeSessions.put(player.getUniqueId(), session);
//         plugin.getLogger().info("Session de shop enregistrée pour " + player.getName() + ": " + shopId + " (page " + page + ")");
//     }
    
//     /**
//      * Désenregistre une session de shop pour un joueur
//      */
//     public void unregisterSession(Player player) {
//         activeSessions.remove(player.getUniqueId());
//     }
    
//     /**
//      * Extrait l'ID du shop et le numéro de page à partir du titre de l'inventaire
//      * @throws ShopsNotLoadedException 
//      */
//     public String extractShopId(InventoryView view) throws ShopsNotLoadedException {
//         if (view == null) return null;
        
//         String title = view.getTitle();
        
//         // Extraire le shopId et la page
//         for (Shop shop : ShopGuiPlusApi.getPlugin().getShopManager().getShops()) {
//             String shopName = shop.getName();
//             if (title.contains(shopName)) {
//                 // Trouver le numéro de page s'il existe
//                 Matcher matcher = PAGE_PATTERN.matcher(title);
//                 if (matcher.find()) {
//                     return shop.getId() + "#" + matcher.group(1);
//                 }
//                 return shop.getId() + "#1"; // Page 1 par défaut
//             }
//         }
        
//         return null;
//     }
    
//     /**
//      * Rafraîchit l'inventaire du shop pour un joueur
//      */
//     private void refreshShop(Player player, ShopSession session) {
//         // Mémoriser l'inventaire actuel
//         InventoryView currentView = player.getOpenInventory();
        
//         // Planifier la réouverture avec un délai pour éviter les problèmes de timing
//         Bukkit.getScheduler().runTask(plugin, () -> {
//             try {
//                 // Si l'inventaire a changé entre-temps, abandonner
//                 if (!extractShopId(player.getOpenInventory()).equals(session.shopId)) {
//                     return;
//                 }
                
//                 // Fermer l'inventaire actuel
//                 player.closeInventory();
                
//                 // Réouvrir le même shop avec la même page
//                 String shopIdPart = session.shopId.split("#")[0];
                
//                 // Éxécuter avec un léger délai pour s'assurer que l'inventaire est bien fermé
//                 Bukkit.getScheduler().runTaskLater(plugin, () -> {
//                     try {
//                         ShopManager shopManager = ShopGuiPlusApi.getPlugin().getShopManager();
//                         Shop shop = shopManager.getShopById(shopIdPart);
                        
//                         if (shop != null) {
//                             // Ouvrir le shop à la page spécifiée
//                             shopManager.openShopMenu(player, shop.getId(), session.page, false);
//                             plugin.getLogger().info("Shop rafraîchi pour " + player.getName() + ": " + shop.getName() + " (page " + session.page + ")");
//                         }
//                     } catch (Exception e) {
//                         plugin.getLogger().warning("Erreur lors de la réouverture du shop: " + e.getMessage());
//                     }
//                 }, 2L); // Délai de 2 ticks
//             } catch (Exception e) {
//                 plugin.getLogger().warning("Erreur lors du rafraîchissement du shop: " + e.getMessage());
//             }
//         });
//     }
    
//     /**
//      * Classe pour stocker les données d'une session de shop
//      */
//     private static class ShopSession {
//         final String shopId;
//         final int page;
        
//         ShopSession(String shopId, int page) {
//             this.shopId = shopId;
//             this.page = page;
//         }
//     }
// }



package fr.tylwen.satyria.dynashop.gui;

import fr.tylwen.satyria.dynashop.DynaShopPlugin;
import net.brcdev.shopgui.ShopGuiPlusApi;
import net.brcdev.shopgui.exception.shop.ShopsNotLoadedException;
import net.brcdev.shopgui.shop.Shop;
import net.brcdev.shopgui.shop.ShopManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryView;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ShopRefreshManager {
    private final DynaShopPlugin plugin;
    private final Map<UUID, ShopSession> activeSessions = new ConcurrentHashMap<>();
    private BukkitTask refreshTask;
    
    // Modèle pour extraire le numéro de page du titre de l'inventaire
    private static final Pattern PAGE_PATTERN = Pattern.compile("#(\\d+)");
    
    public ShopRefreshManager(DynaShopPlugin plugin) {
        this.plugin = plugin;
        // plugin.getLogger().info("[DEBUG] ShopRefreshManager initialisé");
        startRefreshTask();
    }
    
    /**
     * Démarre une tâche planifiée pour rafraîchir les inventaires périodiquement
     */
    private void startRefreshTask() {
        // Annuler toute tâche existante
        if (refreshTask != null) {
            refreshTask.cancel();
            // plugin.getLogger().info("[DEBUG] Tâche de rafraîchissement précédente annulée");
        }
        
        // Planifier une nouvelle tâche toutes les 5 secondes
        // plugin.getLogger().info("[DEBUG] Démarrage de la tâche de rafraîchissement...");
        refreshTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            // plugin.getLogger().info("[DEBUG] Exécution de la tâche de rafraîchissement - Sessions actives: " + activeSessions.size());
            
            // Pour chaque joueur avec une session active
            for (Map.Entry<UUID, ShopSession> entry : new HashMap<>(activeSessions).entrySet()) {
                UUID playerId = entry.getKey();
                ShopSession session = entry.getValue();
                Player player = Bukkit.getPlayer(playerId);
                
                // plugin.getLogger().info("[DEBUG] Vérification du joueur: " + (player != null ? player.getName() : "null") + 
                //                        " - Session: " + session.shopId + " page " + session.page);
                
                // Si le joueur est toujours connecté et a un inventaire ouvert
                if (player != null && player.isOnline() && player.getOpenInventory() != null) {
                    // Si l'inventaire ouvert est toujours un shop et n'a pas changé
                    // String currentShop = null;
                    // try {
                        String currentShop = extractShopId(player.getOpenInventory());
                        // plugin.getLogger().info("[DEBUG] ShopID actuel détecté: " + currentShop);
                    // } catch (Exception e) {
                    //     // plugin.getLogger().severe("[DEBUG] Exception inattendue lors de l'extraction du shopId: " + e.getMessage());
                    //     e.printStackTrace();
                    //     continue;
                    // }
                    
                    if (currentShop != null && currentShop.equals(session.shopId)) {
                        // plugin.getLogger().info("[DEBUG] Rafraîchissement requis pour " + player.getName() + " - Shop: " + session.shopId);
                        // Rafraîchir l'inventaire
                        refreshShop(player, session);
                    } else {
                        // Le joueur a changé d'inventaire, supprimer la session
                        // plugin.getLogger().info("[DEBUG] Le joueur " + player.getName() + " a changé d'inventaire, suppression de la session");
                        activeSessions.remove(playerId);
                    }
                } else {
                    // Le joueur s'est déconnecté, supprimer la session
                    // plugin.getLogger().info("[DEBUG] Le joueur UUID:" + playerId + " n'est plus connecté, suppression de la session");
                    activeSessions.remove(playerId);
                }
            }
        }, 100L, 100L); // 5 secondes (100 ticks)
        
        // plugin.getLogger().info("[DEBUG] Tâche de rafraîchissement démarrée avec ID: " + refreshTask.getTaskId());
    }
    
    /**
     * Arrête proprement le gestionnaire de rafraîchissement
     */
    public void shutdown() {
        // plugin.getLogger().info("[DEBUG] Shutdown du ShopRefreshManager");
        if (refreshTask != null) {
            // plugin.getLogger().info("[DEBUG] Annulation de la tâche de rafraîchissement ID: " + refreshTask.getTaskId());
            refreshTask.cancel();
            refreshTask = null;
        }
        // plugin.getLogger().info("[DEBUG] Suppression de " + activeSessions.size() + " sessions actives");
        activeSessions.clear();
    }
    
    /**
     * Enregistre une nouvelle session de shop pour un joueur
     */
    public void registerSession(Player player, String shopId, int page) {
        ShopSession session = new ShopSession(shopId, page);
        // plugin.getLogger().info("[DEBUG] Enregistrement d'une session pour " + player.getName() + " - Shop: " + shopId + " Page: " + page);
        activeSessions.put(player.getUniqueId(), session);
    }
    
    /**
     * Désenregistre une session de shop pour un joueur
     */
    public void unregisterSession(Player player) {
        if (player == null) {
            // plugin.getLogger().warning("[DEBUG] Tentative de désenregistrement d'une session pour un joueur null");
            return;
        }
        
        if (activeSessions.containsKey(player.getUniqueId())) {
            // plugin.getLogger().info("[DEBUG] Désenregistrement de la session pour " + player.getName());
            activeSessions.remove(player.getUniqueId());
        } else {
            // plugin.getLogger().info("[DEBUG] Pas de session à désenregistrer pour " + player.getName());
        }
    }
    
    // /**
    //  * Extrait l'ID du shop et le numéro de page à partir du titre de l'inventaire
    //  * @throws ShopsNotLoadedException 
    //  */
    // public String extractShopId(InventoryView view) throws ShopsNotLoadedException {
    //     if (view == null) {
    //         plugin.getLogger().warning("[DEBUG] extractShopId: InventoryView est null");
    //         return null;
    //     }
        
    //     String title = view.getTitle();
    //     plugin.getLogger().info("[DEBUG] Extraction du shopId à partir du titre: '" + title + "'");
        
    //     // Extraire le shopId et la page
    //     try {
    //         for (Shop shop : ShopGuiPlusApi.getPlugin().getShopManager().getShops()) {
    //             String shopName = shop.getName();
    //             if (title.contains(shopName)) {
    //                 plugin.getLogger().info("[DEBUG] Shop trouvé: " + shop.getId() + " (Nom: " + shopName + ")");
                    
    //                 // Trouver le numéro de page s'il existe
    //                 Matcher matcher = PAGE_PATTERN.matcher(title);
    //                 if (matcher.find()) {
    //                     String result = shop.getId() + "#" + matcher.group(1);
    //                     plugin.getLogger().info("[DEBUG] ShopId avec page extrait: " + result);
    //                     return result;
    //                 }
                    
    //                 String result = shop.getId() + "#1"; // Page 1 par défaut
    //                 plugin.getLogger().info("[DEBUG] ShopId avec page par défaut: " + result);
    //                 return result;
    //             }
    //         }
    //     } catch (Exception e) {
    //         plugin.getLogger().severe("[DEBUG] Exception lors de l'extraction du shopId: " + e.getMessage());
    //         e.printStackTrace();
    //     }
        
    //     plugin.getLogger().warning("[DEBUG] Aucun shop trouvé pour le titre: '" + title + "'");
    //     return null;
    // }
    // /**
    //  * Extrait l'ID du shop et le numéro de page à partir du titre de l'inventaire
    //  * @throws ShopsNotLoadedException 
    //  */
    // public String extractShopId(InventoryView view) throws ShopsNotLoadedException {
    //     if (view == null) {
    //         // plugin.getLogger().warning("[DEBUG] extractShopId: InventoryView est null");
    //         return null;
    //     }
        
    //     String title = view.getTitle();
    //     // plugin.getLogger().info("[DEBUG] Extraction du shopId à partir du titre: '" + title + "'");
        
    //     // Nettoyer le titre pour le comparer
    //     String cleanTitle = ChatColor.stripColor(title);
    //     // plugin.getLogger().info("[DEBUG] Titre nettoyé: '" + cleanTitle + "'");
        
    //     // Extraire le numéro de page du titre s'il existe
    //     int page = 1;
    //     Matcher matcher = PAGE_PATTERN.matcher(cleanTitle);
    //     if (matcher.find()) {
    //         try {
    //             page = Integer.parseInt(matcher.group(1));
    //             // plugin.getLogger().info("[DEBUG] Numéro de page extrait: " + page);
    //         } catch (NumberFormatException e) {
    //             // plugin.getLogger().warning("[DEBUG] Erreur lors de l'extraction du numéro de page: " + e.getMessage());
    //         }
    //     }
        
    //     // Essayer de trouver le shop par son ID dans le titre
    //     String shopId = null;
        
    //     try {
    //         // Vérifier d'abord par correspondance directe de l'ID
    //         for (Shop shop : ShopGuiPlusApi.getPlugin().getShopManager().getShops()) {
    //             if (cleanTitle.contains(shop.getId())) {
    //                 shopId = shop.getId();
    //                 // plugin.getLogger().info("[DEBUG] Shop trouvé par ID dans le titre: " + shopId);
    //                 return shopId + "#" + page;
    //             }
    //         }
            
    //         // Si ça échoue, essayer par correspondance du nom
    //         for (Shop shop : ShopGuiPlusApi.getPlugin().getShopManager().getShops()) {
    //             String shopName = ChatColor.stripColor(shop.getName());
    //             if (cleanTitle.contains(shopName)) {
    //                 shopId = shop.getId();
    //                 // plugin.getLogger().info("[DEBUG] Shop trouvé par nom dans le titre: " + shopId + " (Nom: " + shopName + ")");
    //                 return shopId + "#" + page;
    //             }
    //         }
            
    //         // Dernier recours: extraction manuelle basée sur le format connu du titre
    //         // Supposons que le format est "Magasin » NomDuShop #Page"
    //         String[] parts = cleanTitle.split("»");
    //         if (parts.length > 1) {
    //             String shopPart = parts[1].trim();
    //             // Supprimer le numéro de page s'il existe
    //             if (shopPart.contains("#")) {
    //                 shopPart = shopPart.split("#")[0].trim();
    //             }
                
    //             // plugin.getLogger().info("[DEBUG] Extraction manuelle du nom de shop: '" + shopPart + "'");
                
    //             // Chercher le shop avec ce nom
    //             for (Shop shop : ShopGuiPlusApi.getPlugin().getShopManager().getShops()) {
    //                 String shopName = ChatColor.stripColor(shop.getName());
    //                 if (shopName.equalsIgnoreCase(shopPart) || shopPart.contains(shopName)) {
    //                     shopId = shop.getId();
    //                     // plugin.getLogger().info("[DEBUG] Shop trouvé par extraction manuelle: " + shopId);
    //                     return shopId + "#" + page;
    //                 }
    //             }
    //         }
    //     } catch (Exception e) {
    //         // plugin.getLogger().severe("[DEBUG] Exception lors de l'extraction du shopId: " + e.getMessage());
    //         e.printStackTrace();
    //     }
        
    //     // Si c'est "Minerais" spécifiquement (d'après vos logs)
    //     if (cleanTitle.contains("Minerais")) {
    //         // plugin.getLogger().info("[DEBUG] Shop 'Minerais' détecté manuellement");
    //         return "minerais#" + page;
    //     }
        
    //     // plugin.getLogger().warning("[DEBUG] Aucun shop trouvé pour le titre: '" + cleanTitle + "'");
    //     return null;
    // }
    
    /**
     * Détermine l'ID du shop à partir du titre de l'inventaire.
     * Cette méthode suppose que le titre contient l'ID du shop dans un format particulier.
     * 
     * @param view L'InventoryView à analyser
     * @return L'ID du shop ou null si non trouvé
     */
    private String extractShopId(InventoryView view) {
        String title = view.getTitle();
        
        // Méthode 2: Essayer d'utiliser l'API de ShopGUI+ si disponible
        try {
            for (Shop shop : ShopGuiPlusApi.getPlugin().getShopManager().getShops()) {
                // Vérifier si le titre correspond au modèle du shop
                String shopNameTemplate = shop.getName().replace("%page%", "");
                if (title.contains(shopNameTemplate)) {
                    // Extraire le numéro de page
                    int page = 1;
                    if (shop.getName().contains("%page%")) {
                        // Trouver où se trouve %page% dans le nom du shop
                        String before = shop.getName().split("%page%")[0];
                        String after = shop.getName().split("%page%").length > 1 ? shop.getName().split("%page%")[1] : "";
                        
                        // Extraire la partie du titre qui correspond à la page
                        if (title.startsWith(before) && (after.isEmpty() || title.endsWith(after))) {
                            String pageStr = title.substring(before.length(), 
                                            after.isEmpty() ? title.length() : title.length() - after.length());
                            try {
                                page = Integer.parseInt(pageStr);
                            } catch (NumberFormatException e) {
                                page = 1;
                            }
                        }
                        return shop.getId() + "#" + page;
                    }
                    return shop.getId();
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Erreur lors de la récupération du shop via l'API: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Rafraîchit l'inventaire du shop pour un joueur
     */
    private void refreshShop(Player player, ShopSession session) {
        // plugin.getLogger().info("[DEBUG] Début du rafraîchissement pour " + player.getName() + " - Shop: " + session.shopId);

        // Planifier la réouverture avec un délai pour éviter les problèmes de timing
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                // plugin.getLogger().info("[DEBUG] Vérification si l'inventaire est toujours le même...");
                // Si l'inventaire a changé entre-temps, abandonner
                String currentShop = null;
                currentShop = extractShopId(player.getOpenInventory());
                
                if (currentShop == null) {
                    // plugin.getLogger().warning("[DEBUG] L'inventaire actuel n'est pas un shop, abandon du rafraîchissement");
                    return;
                }
                
                if (!currentShop.equals(session.shopId)) {
                    // plugin.getLogger().warning("[DEBUG] L'inventaire a changé depuis le début du rafraîchissement, abandon");
                    // plugin.getLogger().warning("[DEBUG] Actuel: " + currentShop + " vs Session: " + session.shopId);
                    return;
                }
                
                // plugin.getLogger().info("[DEBUG] Fermeture de l'inventaire actuel pour " + player.getName());
                // // Fermer l'inventaire actuel
                // player.closeInventory();
                
                // Réouvrir le même shop avec la même page
                String shopIdPart = session.shopId.split("#")[0];
                // plugin.getLogger().info("[DEBUG] Shop à rouvrir: " + shopIdPart + " à la page " + session.page);
                
                // Exécuter avec un léger délai pour s'assurer que l'inventaire est bien fermé
                // Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    try {
                        // plugin.getLogger().info("[DEBUG] Tentative de réouverture du shop " + shopIdPart + " pour " + player.getName());
                        ShopManager shopManager = ShopGuiPlusApi.getPlugin().getShopManager();
                        Shop shop = shopManager.getShopById(shopIdPart);
                        
                        if (shop != null) {
                            // plugin.getLogger().info("[DEBUG] Shop trouvé, ouverture de " + shop.getName() + " à la page " + session.page);
                            // Ouvrir le shop à la page spécifiée
                            shopManager.openShopMenu(player, shop.getId(), session.page, false);
                            // player.updateInventory();
                            // plugin.getLogger().info("[DEBUG] Shop ouvert avec succès pour " + player.getName());
                            
                            // Réenregistrer la session car la fermeture l'a probablement supprimée
                            registerSession(player, session.shopId, session.page);
                        } else {
                            // plugin.getLogger().warning("[DEBUG] Shop non trouvé avec ID: " + shopIdPart);
                        }
                    } catch (Exception e) {
                        // plugin.getLogger().severe("[DEBUG] Erreur lors de la réouverture du shop: " + e.getMessage());
                        e.printStackTrace();
                    }
                // }, 1L); // Délai de 1 tick
            } catch (Exception e) {
                // plugin.getLogger().severe("[DEBUG] Exception générale lors du rafraîchissement du shop: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
    
    /**
     * Classe pour stocker les données d'une session de shop
     */
    private static class ShopSession {
        final String shopId;
        final int page;
        
        ShopSession(String shopId, int page) {
            this.shopId = shopId;
            this.page = page;
        }
        
        @Override
        public String toString() {
            return "ShopSession{shopId='" + shopId + "', page=" + page + "}";
        }
    }
}


// package fr.tylwen.satyria.dynashop.gui;

// import fr.tylwen.satyria.dynashop.DynaShopPlugin;
// import fr.tylwen.satyria.dynashop.placeholders.PlaceholderExpansion;
// import net.brcdev.shopgui.ShopGuiPlusApi;
// import net.brcdev.shopgui.exception.shop.ShopsNotLoadedException;
// import net.brcdev.shopgui.shop.Shop;
// import net.brcdev.shopgui.shop.item.ShopItem;
// import net.brcdev.shopgui.shop.ShopManager;
// import org.bukkit.Bukkit;
// import org.bukkit.ChatColor;
// import org.bukkit.Material;
// import org.bukkit.entity.Player;
// import org.bukkit.inventory.Inventory;
// import org.bukkit.inventory.InventoryView;
// import org.bukkit.inventory.ItemStack;
// import org.bukkit.inventory.meta.ItemMeta;
// import org.bukkit.scheduler.BukkitTask;

// import java.util.*;
// import java.util.concurrent.ConcurrentHashMap;
// import java.util.regex.Matcher;
// import java.util.regex.Pattern;
// import java.util.stream.Collectors;

// public class ShopRefreshManager {
//     private final DynaShopPlugin plugin;
//     private final Map<UUID, ShopSession> activeSessions = new ConcurrentHashMap<>();
//     private BukkitTask refreshTask;
    
//     // Modèle pour extraire le numéro de page du titre de l'inventaire
//     private static final Pattern PAGE_PATTERN = Pattern.compile("#(\\d+)");
    
//     // Intervalle entre les rafraîchissements (en ticks, 200 = 10 secondes)
//     private static final long REFRESH_INTERVAL = 200L;
    
//     public ShopRefreshManager(DynaShopPlugin plugin) {
//         this.plugin = plugin;
//         plugin.getLogger().info("[DEBUG] ShopRefreshManager initialisé");
//         startRefreshTask();
//     }
    
//     /**
//      * Démarre une tâche planifiée pour rafraîchir les inventaires périodiquement
//      */
//     private void startRefreshTask() {
//         // Annuler toute tâche existante
//         if (refreshTask != null) {
//             refreshTask.cancel();
//             plugin.getLogger().info("[DEBUG] Tâche de rafraîchissement précédente annulée");
//         }
        
//         // Planifier une nouvelle tâche toutes les 10 secondes
//         plugin.getLogger().info("[DEBUG] Démarrage de la tâche de rafraîchissement...");
//         refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
//             plugin.getLogger().info("[DEBUG] Exécution de la tâche de rafraîchissement - Sessions actives: " + activeSessions.size());
            
//             // Pour chaque joueur avec une session active
//             for (Map.Entry<UUID, ShopSession> entry : new HashMap<>(activeSessions).entrySet()) {
//                 UUID playerId = entry.getKey();
//                 ShopSession session = entry.getValue();
//                 Player player = Bukkit.getPlayer(playerId);
                
//                 plugin.getLogger().info("[DEBUG] Vérification du joueur: " + (player != null ? player.getName() : "null") + 
//                                        " - Session: " + session.shopId + " page " + session.page);
                
//                 // Si le joueur est toujours connecté et a un inventaire ouvert
//                 if (player != null && player.isOnline() && player.getOpenInventory() != null) {
//                     // Si l'inventaire ouvert est toujours un shop et n'a pas changé
//                     String currentShop = null;
//                     try {
//                         currentShop = extractShopId(player.getOpenInventory());
//                         plugin.getLogger().info("[DEBUG] ShopID actuel détecté: " + currentShop);
//                     } catch (ShopsNotLoadedException e) {
//                         plugin.getLogger().warning("[DEBUG] Erreur lors de l'extraction du shopId: " + e.getMessage());
//                         continue;
//                     } catch (Exception e) {
//                         plugin.getLogger().severe("[DEBUG] Exception inattendue lors de l'extraction du shopId: " + e.getMessage());
//                         continue;
//                     }
                    
//                     if (currentShop != null && currentShop.equals(session.shopId)) {
//                         plugin.getLogger().info("[DEBUG] Rafraîchissement requis pour " + player.getName() + " - Shop: " + session.shopId);
//                         // Rafraîchir l'inventaire sans le fermer
//                         refreshShopInPlace(player, session);
//                     } else {
//                         // Le joueur a changé d'inventaire, supprimer la session
//                         plugin.getLogger().info("[DEBUG] Le joueur " + player.getName() + " a changé d'inventaire, suppression de la session");
//                         activeSessions.remove(playerId);
//                     }
//                 } else {
//                     // Le joueur s'est déconnecté, supprimer la session
//                     plugin.getLogger().info("[DEBUG] Le joueur UUID:" + playerId + " n'est plus connecté, suppression de la session");
//                     activeSessions.remove(playerId);
//                 }
//             }
//         }, REFRESH_INTERVAL, REFRESH_INTERVAL);
        
//         plugin.getLogger().info("[DEBUG] Tâche de rafraîchissement démarrée avec ID: " + refreshTask.getTaskId());
//     }
    
//     /**
//      * Méthode pour rafraîchir un shop sans fermer l'inventaire
//      */
//     private void refreshShopInPlace(Player player, ShopSession session) {
//         String shopId = session.shopId.split("#")[0];
//         int page = session.page;
        
//         plugin.getLogger().info("[DEBUG] Rafraîchissement en place pour " + player.getName() + " - Shop: " + shopId + " Page: " + page);
        
//         try {
//             // Obtenir l'inventaire actuel
//             Inventory inventory = player.getOpenInventory().getTopInventory();
            
//             // Obtenir le shop
//             ShopManager shopManager = ShopGuiPlusApi.getPlugin().getShopManager();
//             Shop shop = shopManager.getShopById(shopId);
            
//             if (shop == null) {
//                 plugin.getLogger().warning("[DEBUG] Shop non trouvé: " + shopId);
//                 return;
//             }
            
//             // Mettre à jour les items un par un
//             for (int slot = 0; slot < inventory.getSize(); slot++) {
//                 final int finalSlot = slot;
//                 // Obtenir l'item du slot
//                 ItemStack currentItem = inventory.getItem(slot);
                
//                 if (currentItem == null || currentItem.getType() == Material.AIR) continue;
                
//                 // Tenter de trouver le shopItem correspondant
//                 ShopItem shopItem = null;
//                 try {
//                     shopItem = shop.getShopItem(page, slot);
//                 } catch (Exception e) {
//                     // Pas d'item de shop à cet emplacement, ignorer
//                     continue;
//                 }
                
//                 if (shopItem == null) continue;
                
//                 // Mettre à jour le lore de l'item avec les nouveaux prix
//                 Bukkit.getScheduler().runTask(plugin, () -> {
//                     try {
//                         updateItemLore(player, inventory, finalSlot, shopId, shopItem.getId());
//                     } catch (Exception e) {
//                         plugin.getLogger().warning("[DEBUG] Erreur lors de la mise à jour de l'item " + shopItem.getId() + ": " + e.getMessage());
//                     }
//                 });
//             }
            
//             // Forcer la mise à jour de l'inventaire côté client
//             Bukkit.getScheduler().runTaskLater(plugin, player::updateInventory, 1L);
            
//             plugin.getLogger().info("[DEBUG] Inventaire rafraîchi en place pour " + player.getName());
            
//         } catch (Exception e) {
//             plugin.getLogger().severe("[DEBUG] Erreur lors du rafraîchissement en place: " + e.getMessage());
//             e.printStackTrace();
//         }
//     }
    
//     /**
//      * Met à jour le lore d'un item avec les nouveaux prix
//      */
//     private void updateItemLore(Player player, Inventory inventory, int slot, String shopId, String itemId) {
//         ItemStack item = inventory.getItem(slot);
//         if (item == null) return;
        
//         try {
//             ItemMeta meta = item.getItemMeta();
//             if (meta == null) return;
            
//             List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
//             if (lore == null) lore = new ArrayList<>();
            
//             // Parcourir le lore et mettre à jour les placeholders
//             List<String> updatedLore = new ArrayList<>();
//             boolean changed = false;
            
//             for (String line : lore) {
//                 String updatedLine = line;
                
//                 // Si la ligne contient un placeholder de prix, le mettre à jour
//                 if (line.contains("%dynashop_") && (line.contains("_price") || line.contains("_stock"))) {
//                     // Obtenir les prix/stocks actuels via l'API
//                     if (line.contains("_buy_price")) {
//                         String newPrice = plugin.getPlaceholderExpansion().getPriceByType(shopId, itemId, "buy");
//                         updatedLine = line.replace(getPlaceholderValue(line), newPrice);
//                         changed = true;
//                     } else if (line.contains("_sell_price")) {
//                         String newPrice = plugin.getPlaceholderExpansion().getPriceByType(shopId, itemId, "sell");
//                         updatedLine = line.replace(getPlaceholderValue(line), newPrice);
//                         changed = true;
//                     // } else if (line.contains("_stock")) {
//                     //     String newStock = plugin.getPlaceholderExpansion().getStockByType(shopId, itemId);
//                     //     updatedLine = line.replace(getPlaceholderValue(line), newStock);
//                     //     changed = true;
//                     }
//                 }
                
//                 updatedLore.add(updatedLine);
//             }
            
//             if (changed) {
//                 meta.setLore(updatedLore);
//                 item.setItemMeta(meta);
//                 inventory.setItem(slot, item);
//             }
            
//         } catch (Exception e) {
//             plugin.getLogger().warning("[DEBUG] Erreur lors de la mise à jour du lore: " + e.getMessage());
//         }
//     }
    
//     /**
//      * Extrait la valeur actuelle d'un placeholder dans une ligne de texte
//      */
//     private String getPlaceholderValue(String text) {
//         // Cette méthode est approximative - il faudrait l'adapter à votre format exact
//         int start = text.indexOf(": ") + 2;
//         if (start < 2) return "";
        
//         int end = text.indexOf(" ", start);
//         if (end == -1) end = text.length();
        
//         return text.substring(start, end);
//     }
    
//     // Code existant pour extractShopId, registerSession, unregisterSession, etc.
    
//     /**
//      * Arrête proprement le gestionnaire de rafraîchissement
//      */
//     public void shutdown() {
//         plugin.getLogger().info("[DEBUG] Shutdown du ShopRefreshManager");
//         if (refreshTask != null) {
//             plugin.getLogger().info("[DEBUG] Annulation de la tâche de rafraîchissement ID: " + refreshTask.getTaskId());
//             refreshTask.cancel();
//             refreshTask = null;
//         }
//         plugin.getLogger().info("[DEBUG] Suppression de " + activeSessions.size() + " sessions actives");
//         activeSessions.clear();
//     }
    
//     /**
//      * Enregistre une nouvelle session de shop pour un joueur
//      */
//     public void registerSession(Player player, String shopId, int page) {
//         ShopSession session = new ShopSession(shopId, page);
//         plugin.getLogger().info("[DEBUG] Enregistrement d'une session pour " + player.getName() + " - Shop: " + shopId + " Page: " + page);
//         activeSessions.put(player.getUniqueId(), session);
//     }
    
//     /**
//      * Désenregistre une session de shop pour un joueur
//      */
//     public void unregisterSession(Player player) {
//         if (player == null) {
//             plugin.getLogger().warning("[DEBUG] Tentative de désenregistrement d'une session pour un joueur null");
//             return;
//         }
        
//         if (activeSessions.containsKey(player.getUniqueId())) {
//             plugin.getLogger().info("[DEBUG] Désenregistrement de la session pour " + player.getName());
//             activeSessions.remove(player.getUniqueId());
//         } else {
//             plugin.getLogger().info("[DEBUG] Pas de session à désenregistrer pour " + player.getName());
//         }
//     }
    
//     // Garder votre méthode extractShopId telle quelle
    
//     /**
//      * Classe pour stocker les données d'une session de shop
//      */
//     private static class ShopSession {
//         final String shopId;
//         final int page;
        
//         ShopSession(String shopId, int page) {
//             this.shopId = shopId;
//             this.page = page;
//         }
        
//         @Override
//         public String toString() {
//             return "ShopSession{shopId='" + shopId + "', page=" + page + "}";
//         }
//     }
// }