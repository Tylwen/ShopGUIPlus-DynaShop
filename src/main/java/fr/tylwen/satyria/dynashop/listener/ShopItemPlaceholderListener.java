package fr.tylwen.satyria.dynashop.listener;

import org.bukkit.ChatColor;
// import org.bukkit.Material;
// import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
// import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
// import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.InventoryView;

import fr.tylwen.satyria.dynashop.DynaShopPlugin;
import me.clip.placeholderapi.PlaceholderAPI;
import net.brcdev.shopgui.shop.Shop;
import net.brcdev.shopgui.ShopGuiPlusApi;
// import net.brcdev.shopgui.exception.player.PlayerDataNotLoadedException;
// import net.brcdev.shopgui.gui.gui.OpenGui;
// import net.brcdev.shopgui.shop.item.ShopItem;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.HashMap;
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
    
    // @EventHandler(priority = EventPriority.LOWEST)
    // public void onInventoryOpen(InventoryOpenEvent event) {
    //     if (!(event.getPlayer() instanceof Player))
    //         return;
            
    //     Player player = (Player) event.getPlayer();
        
    //     // Vérifier si c'est une interface de ShopGUI+
    //     // String nameShop = ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getString("shopMenuName");
    //     // if (event.getView().getTitle().contains("Shop") || event.getView().getTitle().contains("Magasin")) {
    //     // il faut replace toutes les couleurs "&7" par rien
    //     String nameShop = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getString("shopMenuName")));
    //     // DynaShopPlugin.getInstance().getLogger().info("Nom du shop: " + nameShop);
    //     // String nameShop = ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getString("shopMenuName").replace("&7", "");
    //     if (event.getView().getTitle().contains(nameShop)) {
            
    //         // // Essayer de déterminer le shop et l'item
    //         // try {
    //         //     String shopId = determineShopId(event.getView());
                
    //         //     if (shopId != null) {
    //         //         // Pour l'item, on peut soit le détecter immédiatement, soit mettre une valeur nulle
    //         //         // et le mettre à jour au fur et à mesure que le joueur navigue dans le shop
    //         //         String itemId = determineSelectedItem(player, event.getView());
                    
    //         //         openShopMap.put(player.getUniqueId(), new SimpleEntry<>(shopId, itemId));
                    
    //         //         // Log pour le débogage
    //         //         plugin.getLogger().info("Shop détecté pour " + player.getName() + ": " + shopId + (itemId != null ? ", item: " + itemId : ""));
    //         //     }
    //         // } catch (Exception e) {
    //         //     plugin.getLogger().warning("Erreur lors de la détection du shop: " + e.getMessage());
    //         // }

    //         // try {
    //         //     OpenGui openGui = ShopGuiPlusApi.getPlugin().getPlayerManager().getPlayerData(player).getOpenGui();
    //         //     if (openGui != null) {
    //         //         // On peut aussi essayer de récupérer l'item sélectionné dans le shop
    //         //         ItemStack cursorItem = openGui.
    //         //         if (cursorItem != null && cursorItem.getType() != Material.AIR) {
    //         //             ShopItem shopItem = ShopGuiPlusApi.getItemStackShopItem(cursorItem);
    //         //             if (shopItem != null) {
    //         //                 String itemId = shopItem.getId();
    //         //                 openShopMap.put(player.getUniqueId(), new SimpleEntry<>(shopId, itemId));
    //         //             }
    //         //         }
    //         //     }
    //         // } catch (PlayerDataNotLoadedException e) {
    //         //     // TODO Auto-generated catch block
    //         //     e.printStackTrace();
    //         // }
            
    //         // Attendre 1 tick pour que l'inventaire soit rempli
    //         // plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
    //         plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () -> {
    //             String shopID = determineShopId(event.getView());
    //             if (shopID == null) {
    //                 // plugin.getLogger().warning("Shop ID non trouvé pour " + player.getName());
    //                 return;
    //             }
    //             if (!shopID.contains("#")) {
    //                 // plugin.getLogger().warning("Shop ID non valide pour " + player.getName());
    //                 return;
    //             }
    //             // int page = shopID.split("#")[1].equals("0") ? 0 : Integer.parseInt(shopID.split("#")[1]);
    //             int page = Integer.parseInt(shopID.split("#")[1]);
    //             shopID = shopID.split("#")[0];
    //             // Parcourir tous les items de l'inventaire
    //             for (int i = 0; i < event.getInventory().getSize(); i++) {
    //                 ItemStack item = event.getInventory().getItem(i);
    //                 if (item != null && item.hasItemMeta() && item.getItemMeta().hasLore()) {
    //                     // Mettre à jour le lore avec les placeholders
    //                     ItemMeta meta = item.getItemMeta();
    //                     List<String> lore = meta.getLore();
    //                     List<String> newLore = new ArrayList<>();

    //                     // String itemId = determineSelectedItem(player, event.getView());
    //                     String itemId = ShopGuiPlusApi.getPlugin().getShopManager().getShopById(shopID).getShopItem(page, i).getId();
    //                     openShopMap.put(player.getUniqueId(), new SimpleEntry<>(shopID, itemId));
                        
    //                     for (String line : lore) {
    //                         // Éviter d'utiliser nos propres placeholders pour éviter une boucle
    //                         if (line.contains("%dynashop_current_")) {
    //                             // Traiter manuellement nos placeholders ici
    //                             // Pour le moment, laissez-les intacts pour éviter la boucle
    //                             // line = line.replace("%dynashop_current_buyPrice%", String.valueOf(DynaShopPlugin.getInstance().getPlaceholderExpansion().getPriceByType(shopID, itemId, "buy")));
    //                             // line = line.replace("%dynashop_current_buyMinPrice%", String.valueOf(DynaShopPlugin.getInstance().getPlaceholderExpansion().getPriceByType(shopID, itemId, "buy_min")));
    //                             // line = line.replace("%dynashop_current_buyMaxPrice%", String.valueOf(DynaShopPlugin.getInstance().getPlaceholderExpansion().getPriceByType(shopID, itemId, "buy_max")));
    //                             // line = line.replace("%dynashop_current_sellPrice%", String.valueOf(DynaShopPlugin.getInstance().getPlaceholderExpansion().getPriceByType(shopID, itemId, "sell")));
    //                             // line = line.replace("%dynashop_current_sellMinPrice%", String.valueOf(DynaShopPlugin.getInstance().getPlaceholderExpansion().getPriceByType(shopID, itemId, "sell_min")));
    //                             // line = line.replace("%dynashop_current_sellMaxPrice%", String.valueOf(DynaShopPlugin.getInstance().getPlaceholderExpansion().getPriceByType(shopID, itemId, "sell_max")));
    //                             // line = line.replace("%dynashop_current_stock%", String.valueOf(DynaShopPlugin.getInstance().getPlaceholderExpansion().getStock(shopID, itemId)));
    //                             // line = line.replace("%dynashop_current_maxStock%", String.valueOf(DynaShopPlugin.getInstance().getPlaceholderExpansion().getMaxStock(shopID, itemId)));

    //                             line = line.replace("%dynashop_current_buyPrice%", String.valueOf(DynaShopPlugin.getInstance().getPlaceholderExpansion().getPriceByType(shopID, itemId, "buy")))
    //                                 .replace("%dynashop_current_buyMinPrice%", String.valueOf(DynaShopPlugin.getInstance().getPlaceholderExpansion().getPriceByType(shopID, itemId, "buy_min")))
    //                                 .replace("%dynashop_current_buyMaxPrice%", String.valueOf(DynaShopPlugin.getInstance().getPlaceholderExpansion().getPriceByType(shopID, itemId, "buy_max")))
    //                                 .replace("%dynashop_current_sellPrice%", String.valueOf(DynaShopPlugin.getInstance().getPlaceholderExpansion().getPriceByType(shopID, itemId, "sell")))
    //                                 .replace("%dynashop_current_sellMinPrice%", String.valueOf(DynaShopPlugin.getInstance().getPlaceholderExpansion().getPriceByType(shopID, itemId, "sell_min")))
    //                                 .replace("%dynashop_current_sellMaxPrice%", String.valueOf(DynaShopPlugin.getInstance().getPlaceholderExpansion().getPriceByType(shopID, itemId, "sell_max")));

    //                             newLore.add(line);
    //                         // }
    //                         } else {
    //                             // // Pour les autres placeholders, utiliser PlaceholderAPI normalement
    //                             // String processed = PlaceholderAPI.setPlaceholders(player, line);
    //                             // newLore.add(processed);
    //                             newLore.add(line);
    //                         }
    //                     }
                        
    //                     meta.setLore(newLore);
    //                     item.setItemMeta(meta);
    //                 }
    //             }
    //         }, 1L);
    //     }
        
    //     //     // Remplacer votre bloc actuel de mise à jour par:
    //     //     plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () -> {
    //     //         String fullShopId = determineShopId(event.getView());
    //     //         if (fullShopId == null) {
    //     //             return;
    //     //         }
    //     //         String shopId = fullShopId;
    //     //         if (fullShopId.contains("#")) {
    //     //             shopId = fullShopId.split("#")[0];
    //     //         }
                
    //     //         updateShopInventory(player, event.getView(), shopId);
    //     //     }, 1L);
    //     // }
    // }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player))
            return;
            
        Player player = (Player) event.getPlayer();
        
        // Vérifier si c'est une interface de ShopGUI+
        String nameShop = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getString("shopMenuName")));
            
        if (event.getView().getTitle().contains(nameShop)) {
            // Attendre 1 tick puis mettre à jour l'inventaire avec notre méthode optimisée
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                String fullShopId = determineShopId(event.getView());
                if (fullShopId == null) return;
                
                String shopId = fullShopId;
                if (fullShopId.contains("#")) {
                    shopId = fullShopId.split("#")[0];
                }
                
                updateShopInventory(player, event.getView(), shopId);
            }, 1L);
        }
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
        } else if (title.contains("-")) {
            String[] parts = title.split("-");
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

    // @EventHandler(priority = EventPriority.MONITOR)
    // public void onInventoryClick(InventoryClickEvent event) {
    //     if (!(event.getWhoClicked() instanceof Player))
    //         return;
            
    //     Player player = (Player) event.getWhoClicked();
        
    //     // Ne traiter que les clics dans les shops
    //     SimpleEntry<String, String> shopData = openShopMap.get(player.getUniqueId());
    //     if (shopData == null) 
    //         return;
        
    //     // Mettre à jour uniquement l'item cliqué après une courte pause
    //     plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
    //         int slot = event.getSlot();
    //         updateSingleItem(player, player.getOpenInventory(), shopData.getKey(), slot);
    //     }, 2L);
    // }

    // // Méthode pour mettre à jour un seul item
    // private void updateSingleItem(Player player, InventoryView view, String shopId, int slot) {
    //     if (view == null || view.getTopInventory() == null) return;
        
    //     try {
    //         String fullShopId = determineShopId(view);
    //         if (fullShopId == null) return;
            
    //         int page = 0;
    //         if (fullShopId.contains("#")) {
    //             page = Integer.parseInt(fullShopId.split("#")[1]);
    //             shopId = fullShopId.split("#")[0];
    //         }
            
    //         ItemStack item = view.getTopInventory().getItem(slot);
    //         if (item != null && item.hasItemMeta() && item.getItemMeta().hasLore()) {
    //             ItemMeta meta = item.getItemMeta();
    //             List<String> lore = meta.getLore();
    //             List<String> newLore = new ArrayList<>();
                
    //             // Essayer de trouver l'itemId pour cet item
    //             String itemId = null;
    //             try {
    //                 itemId = ShopGuiPlusApi.getPlugin().getShopManager().getShopById(shopId).getShopItem(page, slot).getId();
    //                 if (itemId != null) {
    //                     openShopMap.put(player.getUniqueId(), new SimpleEntry<>(shopId, itemId));
                        
    //                     for (String line : lore) {
    //                         if (line.contains("%dynashop_current_")) {
    //                             // Cache des valeurs pour éviter des appels répétés
    //                             String buyPrice = plugin.getPlaceholderExpansion().getPriceByType(shopId, itemId, "buy");
    //                             String sellPrice = plugin.getPlaceholderExpansion().getPriceByType(shopId, itemId, "sell");
                                
    //                             line = line.replace("%dynashop_current_buyPrice%", buyPrice)
    //                                 .replace("%dynashop_current_sellPrice%", sellPrice)
    //                                 .replace("%dynashop_current_buyMinPrice%", plugin.getPlaceholderExpansion().getPriceByType(shopId, itemId, "buy_min"))
    //                                 .replace("%dynashop_current_buyMaxPrice%", plugin.getPlaceholderExpansion().getPriceByType(shopId, itemId, "buy_max"))
    //                                 .replace("%dynashop_current_sellMinPrice%", plugin.getPlaceholderExpansion().getPriceByType(shopId, itemId, "sell_min"))
    //                                 .replace("%dynashop_current_sellMaxPrice%", plugin.getPlaceholderExpansion().getPriceByType(shopId, itemId, "sell_max"))
    //                                 .replace("%dynashop_current_buy%", plugin.getPlaceholderExpansion().getPriceByType(shopId, itemId, "buy"))
    //                                 .replace("%dynashop_current_sell%", plugin.getPlaceholderExpansion().getPriceByType(shopId, itemId, "sell"));
    //                         }
    //                         newLore.add(line);
    //                     }
                        
    //                     meta.setLore(newLore);
    //                     item.setItemMeta(meta);
    //                 }
    //             } catch (Exception e) {
    //                 // Item slot peut être vide ou ne pas correspondre à un item de shop
    //             }
    //         }
    //     } catch (Exception e) {
    //         plugin.getLogger().warning("Erreur lors de la mise à jour de l'item: " + e.getMessage());
    //     }
    // }

    // @EventHandler(priority = EventPriority.MONITOR)
    // public void onInventoryClick(InventoryClickEvent event) {
    //     if (!(event.getWhoClicked() instanceof Player))
    //         return;
            
    //     Player player = (Player) event.getWhoClicked();
        
    //     // Ne traiter que les clics dans les shops
    //     SimpleEntry<String, String> shopData = openShopMap.get(player.getUniqueId());
    //     if (shopData == null)
    //         return;
        
    //     // Vérifier si c'est un clic qui pourrait déclencher un achat/vente
    //     // Par exemple, sur certains boutons d'achat ou de vente
    //     // Cela dépend beaucoup de la configuration de ShopGUI+
        
    //     // Planifier une mise à jour différée après le clic
    //     plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () -> {
    //         if (player.getOpenInventory() != null) {
    //             String shopId = shopData.getKey();
    //             updateShopInventory(player, player.getOpenInventory(), shopId);
    //         }
    //     }, 2L); // Plus long délai (2 ticks) pour laisser le temps aux transactions de se terminer
    // }

    private void updateShopInventory(Player player, InventoryView view, String shopId) {
        if (view == null || view.getTopInventory() == null) return;
        
        try {
            String fullShopId = determineShopId(view);
            int pageValue = 0;
            String shopIdValue = shopId;
            
            if (fullShopId != null && fullShopId.contains("#")) {
                String[] parts = fullShopId.split("#");
                pageValue = Integer.parseInt(parts[1]);
                shopIdValue = parts[0];
            }
            
            // Capturer les valeurs finales pour utilisation dans les lambdas
            final int finalPage = pageValue;
            final String finalShopId = shopIdValue;
            String currencyPrefix = ShopGuiPlusApi.getPlugin().getEconomyManager().getEconomyProvider(ShopGuiPlusApi.getShop(finalShopId).getEconomyType()).getCurrencyPrefix();
            String currencySuffix = ShopGuiPlusApi.getPlugin().getEconomyManager().getEconomyProvider(ShopGuiPlusApi.getShop(finalShopId).getEconomyType()).getCurrencySuffix();
            
            // Cache global des prix pour cette mise à jour spécifique
            Map<String, Map<String, String>> priceCache = new HashMap<>();
            
            // Traitement par lots - mettre à jour 9 items à la fois
            for (int batchStart = 0; batchStart < view.getTopInventory().getSize(); batchStart += 9) {
                final int start = batchStart;
                plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () -> {
                    for (int i = start; i < Math.min(start + 9, view.getTopInventory().getSize()); i++) {
                        ItemStack item = view.getTopInventory().getItem(i);
                        if (item != null && item.hasItemMeta() && item.getItemMeta().hasLore()) {
                            ItemMeta meta = item.getItemMeta();
                            List<String> lore = meta.getLore();
                            if (!containsDynaShopPlaceholder(lore)) continue; // Évite les traitements inutiles
                            
                            String itemId = null;
                            try {
                                itemId = ShopGuiPlusApi.getPlugin().getShopManager().getShopById(finalShopId).getShopItem(finalPage, i).getId();
                                if (itemId == null) continue;

                                // Mettre en cache les valeurs de prix pour cet item
                                if (!priceCache.containsKey(itemId)) {
                                    Map<String, String> itemPrices = new HashMap<>();
                                    itemPrices.put("buy", plugin.getPlaceholderExpansion().getPriceByType(finalShopId, itemId, "buy"));
                                    itemPrices.put("sell", plugin.getPlaceholderExpansion().getPriceByType(finalShopId, itemId, "sell"));
                                    itemPrices.put("buy_min", plugin.getPlaceholderExpansion().getPriceByType(finalShopId, itemId, "buy_min"));
                                    itemPrices.put("buy_max", plugin.getPlaceholderExpansion().getPriceByType(finalShopId, itemId, "buy_max"));
                                    itemPrices.put("sell_min", plugin.getPlaceholderExpansion().getPriceByType(finalShopId, itemId, "sell_min"));
                                    itemPrices.put("sell_max", plugin.getPlaceholderExpansion().getPriceByType(finalShopId, itemId, "sell_max"));
                                    // itemPrices.put("base_buy", String.format(currencyPrefix + "%s" + currencySuffix +" §f(%s - %s)", 
                                    //     plugin.getPlaceholderExpansion().getPriceByType(finalShopId, itemId, "buy"),
                                    //     plugin.getPlaceholderExpansion().getPriceByType(finalShopId, itemId, "buy_min"),
                                    //     plugin.getPlaceholderExpansion().getPriceByType(finalShopId, itemId, "buy_max")));
                                    // // itemPrices.put("base_buy", String.valueOf(plugin.getPlaceholderExpansion().getPriceByType(finalShopId, itemId, "buy")));
                                    // itemPrices.put("base_sell", String.format(currencyPrefix + "%s" + currencySuffix + " §f(%s - %s)", 
                                    //     plugin.getPlaceholderExpansion().getPriceByType(finalShopId, itemId, "sell"),
                                    //     plugin.getPlaceholderExpansion().getPriceByType(finalShopId, itemId, "sell_min"),
                                    //     plugin.getPlaceholderExpansion().getPriceByType(finalShopId, itemId, "sell_max")));
                                    // // itemPrices.put("base_sell", String.valueOf(plugin.getPlaceholderExpansion().getPriceByType(finalShopId, itemId, "sell")));
                                    // Remplacer les deux lignes pour base_buy et base_sell par ce code conditionnel
                                    String buyPrice = plugin.getPlaceholderExpansion().getPriceByType(finalShopId, itemId, "buy");
                                    String buyMinPrice = plugin.getPlaceholderExpansion().getPriceByType(finalShopId, itemId, "buy_min");
                                    String buyMaxPrice = plugin.getPlaceholderExpansion().getPriceByType(finalShopId, itemId, "buy_max");

                                    // Si les prix min et max sont disponibles et différents de N/A
                                    if (!buyMinPrice.equals("N/A") && !buyMaxPrice.equals("N/A")) {
                                        itemPrices.put("base_buy", String.format(currencyPrefix + "%s" + currencySuffix + " §7(%s - %s) ", 
                                            buyPrice, buyMinPrice, buyMaxPrice));
                                    } else {
                                        // Sinon, on affiche uniquement le prix d'achat
                                        itemPrices.put("base_buy", currencyPrefix + buyPrice + currencySuffix);
                                    }

                                    // Même logique pour le prix de vente
                                    String sellPrice = plugin.getPlaceholderExpansion().getPriceByType(finalShopId, itemId, "sell");
                                    String sellMinPrice = plugin.getPlaceholderExpansion().getPriceByType(finalShopId, itemId, "sell_min");
                                    String sellMaxPrice = plugin.getPlaceholderExpansion().getPriceByType(finalShopId, itemId, "sell_max");

                                    if (!sellMinPrice.equals("N/A") && !sellMaxPrice.equals("N/A")) {
                                        itemPrices.put("base_sell", String.format(currencyPrefix + "%s" + currencySuffix + " §7(%s - %s) ", 
                                            sellPrice, sellMinPrice, sellMaxPrice));
                                    } else {
                                        itemPrices.put("base_sell", currencyPrefix + sellPrice + currencySuffix);
                                    }
                                    priceCache.put(itemId, itemPrices);
                                }
                                
                                List<String> newLore = replacePlaceholders(lore, priceCache.get(itemId), player);
                                meta.setLore(newLore);
                                item.setItemMeta(meta);
                            } catch (Exception e) {
                                // Ignorer cet item en cas d'erreur
                            }
                        }
                    }
                // }, (start / 9) * 1L); // 1 tick de délai pour chaque lot
                }, 1L); // 1 tick de délai pour chaque lot
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Erreur lors de la mise à jour de l'inventaire: " + e.getMessage());
        }
    }

    // Vérifier rapidement si le lore contient des placeholders DynaShop
    private boolean containsDynaShopPlaceholder(List<String> lore) {
        for (String line : lore) {
            if (line.contains("%dynashop_current_")) {
                return true;
            }
        }
        return false;
    }

    // Remplacer les placeholders avec des valeurs pré-calculées
    // et filtrer les lignes avec valeurs N/A
    private List<String> replacePlaceholders(List<String> lore, Map<String, String> prices, Player player) {
        List<String> newLore = new ArrayList<>();
        
        for (String line : lore) {
            boolean skipLine = false;
            
            // Vérifier si la ligne contient des placeholders spécifiques
            if (line.contains("%dynashop_current_")) {
                // Vérifier les placeholders individuels
                if (line.contains("%dynashop_current_buyPrice%") && 
                    (prices.get("buy").equals("N/A") || prices.get("buy").equals("0.0"))) {
                    skipLine = true;
                }
                
                if (line.contains("%dynashop_current_sellPrice%") && 
                    (prices.get("sell").equals("N/A") || prices.get("sell").equals("0.0"))) {
                    skipLine = true;
                }
                
                // Vérifier le placeholder composite buy
                if (line.contains("%dynashop_current_buy%") && 
                    (prices.get("buy").equals("N/A") || prices.get("buy").equals("0.0"))) {
                    skipLine = true;
                }
                
                // Vérifier le placeholder composite sell
                if (line.contains("%dynashop_current_sell%") && 
                    (prices.get("sell").equals("N/A") || prices.get("sell").equals("0.0"))) {
                    skipLine = true;
                }
                
                // Si la ligne doit être conservée, remplacer les placeholders
                if (!skipLine) {
                    line = line.replace("%dynashop_current_buyPrice%", prices.get("buy"))
                        .replace("%dynashop_current_sellPrice%", prices.get("sell"))
                        .replace("%dynashop_current_buyMinPrice%", prices.get("buy_min"))
                        .replace("%dynashop_current_buyMaxPrice%", prices.get("buy_max"))
                        .replace("%dynashop_current_sellMinPrice%", prices.get("sell_min"))
                        .replace("%dynashop_current_sellMaxPrice%", prices.get("sell_max"))
                        .replace("%dynashop_current_buy%", prices.get("base_buy"))
                        .replace("%dynashop_current_sell%", prices.get("base_sell"));
                }
            }
            
            // Traiter les autres placeholders via PlaceholderAPI
            if (!skipLine && line.contains("%")) {
                line = PlaceholderAPI.setPlaceholders(player, line);
            }

            // Ajouter la ligne uniquement si elle ne doit pas être ignorée
            if (!skipLine) {
                newLore.add(line);
            }
        }
        return newLore;
    }

    // // Méthode utilitaire pour mettre à jour l'inventaire du shop
    // private void updateShopInventory(Player player, InventoryView view, String shopId) {
    //     if (view == null || view.getTopInventory() == null) return;
        
    //     try {
    //         // Récupérer la page actuelle (si vous avez cette info)
    //         String fullShopId = determineShopId(view);
    //         int page = 0;
            
    //         if (fullShopId != null && fullShopId.contains("#")) {
    //             page = Integer.parseInt(fullShopId.split("#")[1]);
    //             shopId = fullShopId.split("#")[0];
    //         }
            
    //         // Mettre à jour tous les items
    //         for (int i = 0; i < view.getTopInventory().getSize(); i++) {
    //             ItemStack item = view.getTopInventory().getItem(i);
    //             if (item != null && item.hasItemMeta() && item.getItemMeta().hasLore()) {
    //                 ItemMeta meta = item.getItemMeta();
    //                 List<String> lore = meta.getLore();
    //                 List<String> newLore = new ArrayList<>();
                    
    //                 // Essayer de trouver l'itemId pour cet item
    //                 String itemId = null;
    //                 try {
    //                     itemId = ShopGuiPlusApi.getPlugin().getShopManager().getShopById(shopId).getShopItem(page, i).getId();
    //                     openShopMap.put(player.getUniqueId(), new SimpleEntry<>(shopId, itemId));
    //                 } catch (Exception e) {
    //                     // Item slot peut être vide ou ne pas correspondre à un item de shop
    //                     continue;
    //                 }
                    
    //                 // if (itemId == null) continue;
                    
    //                 for (String line : lore) {
    //                     // Remplacer tous les placeholders dynashop_current_
    //                     if (line.contains("%dynashop_current_")) {
    //                         line = line.replace("%dynashop_current_buyPrice%", String.valueOf(plugin.getPlaceholderExpansion().getPriceByType(shopId, itemId, "buy")));
    //                         line = line.replace("%dynashop_current_buyMinPrice%", String.valueOf(plugin.getPlaceholderExpansion().getPriceByType(shopId, itemId, "buy_min")));
    //                         line = line.replace("%dynashop_current_buyMaxPrice%", String.valueOf(plugin.getPlaceholderExpansion().getPriceByType(shopId, itemId, "buy_max")));
    //                         line = line.replace("%dynashop_current_sellPrice%", String.valueOf(plugin.getPlaceholderExpansion().getPriceByType(shopId, itemId, "sell")));
    //                         line = line.replace("%dynashop_current_sellMinPrice%", String.valueOf(plugin.getPlaceholderExpansion().getPriceByType(shopId, itemId, "sell_min")));
    //                         line = line.replace("%dynashop_current_sellMaxPrice%", String.valueOf(plugin.getPlaceholderExpansion().getPriceByType(shopId, itemId, "sell_max")));
    //                     } else {
    //                         // // Pour les autres placeholders, utiliser PlaceholderAPI normalement
    //                         // String processed = PlaceholderAPI.setPlaceholders(player, line);
    //                         // newLore.add(processed);
    //                         newLore.add(line);
    //                     }
    //                 }

    //                 meta.setLore(newLore);
    //                 item.setItemMeta(meta);
    //             }
    //         }
    //     } catch (Exception e) {
    //         plugin.getLogger().warning("Erreur lors de la mise à jour de l'inventaire du shop: " + e.getMessage());
    //     }
    // }

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