package fr.tylwen.satyria.dynashop.listener;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.InventoryView;

import fr.tylwen.satyria.dynashop.DynaShopPlugin;
import me.clip.placeholderapi.PlaceholderAPI;
import net.brcdev.shopgui.shop.Shop;
import net.brcdev.shopgui.ShopGuiPlusApi;
import net.brcdev.shopgui.exception.player.PlayerDataNotLoadedException;
import net.brcdev.shopgui.gui.gui.OpenGui;
import net.brcdev.shopgui.shop.item.ShopItem;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ShopItemPlaceholderListener implements Listener {
    
    private final DynaShopPlugin plugin;
    
    // Map pour stocker le shop actuellement ouvert par chaque joueur
    private final Map<UUID, SimpleEntry<String, String>> openShopMap = new ConcurrentHashMap<>();
    
    public ShopItemPlaceholderListener(DynaShopPlugin plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player))
            return;
            
        Player player = (Player) event.getPlayer();
        
        // Vérifier si c'est une interface de ShopGUI+
        // String nameShop = ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getString("shopMenuName");
        // if (event.getView().getTitle().contains("Shop") || event.getView().getTitle().contains("Magasin")) {
        // il faut replace toutes les couleurs "&7" par rien
        String nameShop = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getString("shopMenuName")));
        // DynaShopPlugin.getInstance().getLogger().info("Nom du shop: " + nameShop);
        // String nameShop = ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getString("shopMenuName").replace("&7", "");
        if (event.getView().getTitle().contains(nameShop)) {
            
            // // Essayer de déterminer le shop et l'item
            // try {
            //     String shopId = determineShopId(event.getView());
                
            //     if (shopId != null) {
            //         // Pour l'item, on peut soit le détecter immédiatement, soit mettre une valeur nulle
            //         // et le mettre à jour au fur et à mesure que le joueur navigue dans le shop
            //         String itemId = determineSelectedItem(player, event.getView());
                    
            //         openShopMap.put(player.getUniqueId(), new SimpleEntry<>(shopId, itemId));
                    
            //         // Log pour le débogage
            //         plugin.getLogger().info("Shop détecté pour " + player.getName() + ": " + shopId + (itemId != null ? ", item: " + itemId : ""));
            //     }
            // } catch (Exception e) {
            //     plugin.getLogger().warning("Erreur lors de la détection du shop: " + e.getMessage());
            // }

            // try {
            //     OpenGui openGui = ShopGuiPlusApi.getPlugin().getPlayerManager().getPlayerData(player).getOpenGui();
            //     if (openGui != null) {
            //         // On peut aussi essayer de récupérer l'item sélectionné dans le shop
            //         ItemStack cursorItem = openGui.
            //         if (cursorItem != null && cursorItem.getType() != Material.AIR) {
            //             ShopItem shopItem = ShopGuiPlusApi.getItemStackShopItem(cursorItem);
            //             if (shopItem != null) {
            //                 String itemId = shopItem.getId();
            //                 openShopMap.put(player.getUniqueId(), new SimpleEntry<>(shopId, itemId));
            //             }
            //         }
            //     }
            // } catch (PlayerDataNotLoadedException e) {
            //     // TODO Auto-generated catch block
            //     e.printStackTrace();
            // }
            
            // Attendre 1 tick pour que l'inventaire soit rempli
            // plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () -> {
                String shopID = determineShopId(event.getView());
                if (shopID == null) {
                    // plugin.getLogger().warning("Shop ID non trouvé pour " + player.getName());
                    return;
                }
                if (!shopID.contains("#")) {
                    // plugin.getLogger().warning("Shop ID non valide pour " + player.getName());
                    return;
                }
                // int page = shopID.split("#")[1].equals("0") ? 0 : Integer.parseInt(shopID.split("#")[1]);
                int page = Integer.parseInt(shopID.split("#")[1]);
                shopID = shopID.split("#")[0];
                // Parcourir tous les items de l'inventaire
                for (int i = 0; i < event.getInventory().getSize(); i++) {
                    ItemStack item = event.getInventory().getItem(i);
                    if (item != null && item.hasItemMeta() && item.getItemMeta().hasLore()) {
                        // Mettre à jour le lore avec les placeholders
                        ItemMeta meta = item.getItemMeta();
                        List<String> lore = meta.getLore();
                        List<String> newLore = new ArrayList<>();

                        // String itemId = determineSelectedItem(player, event.getView());
                        String itemId = ShopGuiPlusApi.getPlugin().getShopManager().getShopById(shopID).getShopItem(page, i).getId();
                        openShopMap.put(player.getUniqueId(), new SimpleEntry<>(shopID, itemId));
                        
                        for (String line : lore) {
                            // Éviter d'utiliser nos propres placeholders pour éviter une boucle
                            if (line.contains("%dynashop_current_")) {
                                // Traiter manuellement nos placeholders ici
                                // Pour le moment, laissez-les intacts pour éviter la boucle
                                line = line.replace("%dynashop_current_buyPrice%", String.valueOf(DynaShopPlugin.getInstance().getPlaceholderExpansion().getPriceByType(shopID, itemId, "buy")));
                                line = line.replace("%dynashop_current_buyMinPrice%", String.valueOf(DynaShopPlugin.getInstance().getPlaceholderExpansion().getPriceByType(shopID, itemId, "buy_min")));
                                line = line.replace("%dynashop_current_buyMaxPrice%", String.valueOf(DynaShopPlugin.getInstance().getPlaceholderExpansion().getPriceByType(shopID, itemId, "buy_max")));
                                line = line.replace("%dynashop_current_sellPrice%", String.valueOf(DynaShopPlugin.getInstance().getPlaceholderExpansion().getPriceByType(shopID, itemId, "sell")));
                                line = line.replace("%dynashop_current_sellMinPrice%", String.valueOf(DynaShopPlugin.getInstance().getPlaceholderExpansion().getPriceByType(shopID, itemId, "sell_min")));
                                line = line.replace("%dynashop_current_sellMaxPrice%", String.valueOf(DynaShopPlugin.getInstance().getPlaceholderExpansion().getPriceByType(shopID, itemId, "sell_max")));
                                // line = line.replace("%dynashop_current_stock%", String.valueOf(DynaShopPlugin.getInstance().getPlaceholderExpansion().getStock(shopID, itemId)));
                                // line = line.replace("%dynashop_current_maxStock%", String.valueOf(DynaShopPlugin.getInstance().getPlaceholderExpansion().getMaxStock(shopID, itemId)));

                                newLore.add(line);
                            // }
                            } else {
                                // // Pour les autres placeholders, utiliser PlaceholderAPI normalement
                                // String processed = PlaceholderAPI.setPlaceholders(player, line);
                                // newLore.add(processed);
                                newLore.add(line);
                            }
                        }
                        
                        meta.setLore(newLore);
                        item.setItemMeta(meta);
                    }
                }
            }, 1L);
        }
        
        // Remplacer votre bloc actuel de mise à jour par:
        plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            String fullShopId = determineShopId(event.getView());
            if (fullShopId == null) {
                return;
            }
            String shopId = fullShopId;
            if (fullShopId.contains("#")) {
                shopId = fullShopId.split("#")[0];
            }
            
            updateShopInventory(player, event.getView(), shopId);
        }, 1L);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player))
            return;
            
        Player player = (Player) event.getPlayer();
        
        // Vérifier si le joueur était dans un inventaire de shop
        SimpleEntry<String, String> shopData = openShopMap.get(player.getUniqueId());
        if (shopData != null) {
            // Logs pour le débogage
            // plugin.getLogger().info("Fermeture du shop pour " + player.getName() + ": " + shopData.getKey() + (shopData.getValue() != null ? ", dernier item consulté: " + shopData.getValue() : ""));
            
            // Nettoyer les données associées au joueur
            openShopMap.remove(player.getUniqueId());
        }
    }

    // Méthodes pour exposer ces informations
    public String getCurrentShopId(Player player) {
        SimpleEntry<String, String> shopData = openShopMap.get(player.getUniqueId());
        return shopData == null ? null : shopData.getKey();
    }

    public String getCurrentItemId(Player player) {
        SimpleEntry<String, String> shopData = openShopMap.get(player.getUniqueId());
        return shopData == null ? null : shopData.getValue();
    }

    /**
     * Détermine l'ID du shop à partir du titre de l'inventaire.
     * Cette méthode suppose que le titre contient l'ID du shop dans un format particulier.
     * 
     * @param view L'InventoryView à analyser
     * @return L'ID du shop ou null si non trouvé
     */
    private String determineShopId(InventoryView view) {
        String title = view.getTitle();
        
        // Méthode 1: Extraire du titre (en supposant un format comme "Shop - {shopId}")
        if (title.contains("»")) {
            String[] parts = title.split("»");
            if (parts.length > 0) {
                // Nettoyer le texte pour obtenir l'ID du shop
                String shopName = ChatColor.stripColor(parts[1].trim());
                
                // Convertir le nom affiché en ID de shop (en supposant que l'ID est en minuscules sans espaces)
                return shopName.toLowerCase().replace(" ", "").replace("-", "").replace("_", "");
            }
        }
        
        // Méthode 2: Essayer d'utiliser l'API de ShopGUI+ si disponible
        try {
            // Tenter de récupérer le shop actif du joueur via l'API ShopGUI+
            for (String shopId : ShopGuiPlusApi.getPlugin().getShopManager().getShops().stream().map(Shop::getId).toList()) {
                if (title.contains(ShopGuiPlusApi.getShop(shopId).getName())) {
                    return shopId;
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Erreur lors de la récupération du shop via l'API: " + e.getMessage());
        }
        
        return null;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player))
            return;
            
        Player player = (Player) event.getWhoClicked();
        
        // Ne traiter que les clics dans les shops
        SimpleEntry<String, String> shopData = openShopMap.get(player.getUniqueId());
        if (shopData == null)
            return;
        
        // Vérifier si c'est un clic qui pourrait déclencher un achat/vente
        // Par exemple, sur certains boutons d'achat ou de vente
        // Cela dépend beaucoup de la configuration de ShopGUI+
        
        // Planifier une mise à jour différée après le clic
        plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            if (player.getOpenInventory() != null) {
                String shopId = shopData.getKey();
                updateShopInventory(player, player.getOpenInventory(), shopId);
            }
        }, 2L); // Plus long délai (2 ticks) pour laisser le temps aux transactions de se terminer
    }

    // Méthode utilitaire pour mettre à jour l'inventaire du shop
    private void updateShopInventory(Player player, InventoryView view, String shopId) {
        if (view == null || view.getTopInventory() == null) return;
        
        try {
            // Récupérer la page actuelle (si vous avez cette info)
            String fullShopId = determineShopId(view);
            int page = 0;
            
            if (fullShopId != null && fullShopId.contains("#")) {
                page = Integer.parseInt(fullShopId.split("#")[1]);
                shopId = fullShopId.split("#")[0];
            }
            
            // Mettre à jour tous les items
            for (int i = 0; i < view.getTopInventory().getSize(); i++) {
                ItemStack item = view.getTopInventory().getItem(i);
                if (item != null && item.hasItemMeta() && item.getItemMeta().hasLore()) {
                    ItemMeta meta = item.getItemMeta();
                    List<String> lore = meta.getLore();
                    List<String> newLore = new ArrayList<>();
                    
                    // Essayer de trouver l'itemId pour cet item
                    String itemId = null;
                    try {
                        itemId = ShopGuiPlusApi.getPlugin().getShopManager().getShopById(shopId).getShopItem(page, i).getId();
                    } catch (Exception e) {
                        // Item slot peut être vide ou ne pas correspondre à un item de shop
                        continue;
                    }
                    
                    if (itemId == null) continue;
                    
                    for (String line : lore) {
                        // Remplacer tous les placeholders dynashop_current_
                        if (line.contains("%dynashop_current_")) {
                            line = line.replace("%dynashop_current_buyPrice%", String.valueOf(plugin.getPlaceholderExpansion().getPriceByType(shopId, itemId, "buy")));
                            line = line.replace("%dynashop_current_buyMinPrice%", String.valueOf(plugin.getPlaceholderExpansion().getPriceByType(shopId, itemId, "buy_min")));
                            line = line.replace("%dynashop_current_buyMaxPrice%", String.valueOf(plugin.getPlaceholderExpansion().getPriceByType(shopId, itemId, "buy_max")));
                            line = line.replace("%dynashop_current_sellPrice%", String.valueOf(plugin.getPlaceholderExpansion().getPriceByType(shopId, itemId, "sell")));
                            line = line.replace("%dynashop_current_sellMinPrice%", String.valueOf(plugin.getPlaceholderExpansion().getPriceByType(shopId, itemId, "sell_min")));
                            line = line.replace("%dynashop_current_sellMaxPrice%", String.valueOf(plugin.getPlaceholderExpansion().getPriceByType(shopId, itemId, "sell_max")));
                        }
                        newLore.add(line);
                    }

                    meta.setLore(newLore);
                    item.setItemMeta(meta);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Erreur lors de la mise à jour de l'inventaire du shop: " + e.getMessage());
        }
    }

    // /**
    //  * Détermine l'ID de l'item sélectionné dans l'inventaire du shop.
    //  * 
    //  * @param player Le joueur qui a ouvert l'inventaire
    //  * @param view L'InventoryView à analyser
    //  * @return L'ID de l'item ou null si non trouvé
    //  */
    // private String determineSelectedItem(Player player, InventoryView view) {
    //     // // Méthode 1: Vérifier l'item sur le curseur du joueur
    //     // try {
    //     //     ItemStack cursorItem = view.getCursor();
    //     //     if (cursorItem != null && cursorItem.getType() != Material.AIR) {
    //     //         ShopItem shopItem = ShopGuiPlusApi.getItemStackShopItem(cursorItem);
    //     //         if (shopItem != null) {
    //     //             return shopItem.getId();
    //     //         }
    //     //     }
    //     // } catch (Exception e) {
    //     //     // Ignorer les erreurs, essayer d'autres méthodes
    //     // }
        
    //     // Méthode 2: Parcourir les items de l'inventaire supérieur
    //     try {
    //         Inventory topInventory = view.getTopInventory();
            
    //         // // Vérifier d'abord le slot central (souvent utilisé pour l'item principal)
    //         // int centerSlot = topInventory.getSize() / 2;
    //         // ItemStack centerItem = topInventory.getItem(centerSlot);
    //         // if (centerItem != null && centerItem.getType() != Material.AIR) {
    //         //     ShopItem shopItem = ShopGuiPlusApi.getItemStackShopItem(centerItem);
    //         //     if (shopItem != null) {
    //         //         return shopItem.getId();
    //         //     }
    //         // }
            
    //         // Parcourir tous les slots et trouver un item qui n'est pas un élément d'interface
    //         for (int i = 0; i < topInventory.getSize(); i++) {
    //             ItemStack item = topInventory.getItem(i);
    //             if (item != null && item.getType() != Material.AIR) {
    //                 ShopItem shopItem = ShopGuiPlusApi.getItemStackShopItem(item);
    //                 if (shopItem != null) {
    //                     return shopItem.getId();
    //                 }
    //             }
    //         }
    //     } catch (Exception e) {
    //         // Ignorer les erreurs, essayer d'autres méthodes
    //     }
        
    //     // // Méthode 3: Utiliser l'item dans la main du joueur
    //     // try {
    //     //     ItemStack handItem = player.getInventory().getItemInMainHand();
    //     //     if (handItem != null && handItem.getType() != Material.AIR) {
    //     //         ShopItem shopItem = ShopGuiPlusApi.getItemStackShopItem(handItem);
    //     //         if (shopItem != null) {
    //     //             return shopItem.getId();
    //     //         }
    //     //     }
    //     // } catch (Exception e) {
    //     //     // Ignorer les erreurs
    //     // }
        
    //     // Méthode 4: Utiliser le bloc visé par le joueur
    //     try {
    //         Block targetBlock = player.getTargetBlockExact(5);
    //         if (targetBlock != null && targetBlock.getType() != Material.AIR) {
    //             ItemStack targetItem = new ItemStack(targetBlock.getType());
    //             ShopItem shopItem = ShopGuiPlusApi.getItemStackShopItem(targetItem);
    //             if (shopItem != null) {
    //                 return shopItem.getId();
    //             }
    //         }
    //     } catch (Exception e) {
    //         // Ignorer les erreurs
    //     }
        
    //     return null;
    // }
}