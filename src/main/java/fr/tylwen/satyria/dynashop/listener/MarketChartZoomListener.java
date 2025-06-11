package fr.tylwen.satyria.dynashop.listener;

import fr.tylwen.satyria.dynashop.DynaShopPlugin;
import fr.tylwen.satyria.dynashop.command.DualChartSubCommand;
import fr.tylwen.satyria.dynashop.system.chart.DualPriceChartRenderer;
import fr.tylwen.satyria.dynashop.system.chart.MarketChartRenderer;
import fr.tylwen.satyria.dynashop.system.chart.ZoomableChartRenderer;

import org.bukkit.Material;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapRenderer;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;

public class MarketChartZoomListener implements Listener {

    private final DynaShopPlugin plugin;

    public MarketChartZoomListener(DynaShopPlugin plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        ItemStack dropped = event.getItemDrop().getItemStack();
        if (dropped.getType() == Material.FILLED_MAP && dropped.hasItemMeta()) {
            MapMeta meta = (MapMeta) dropped.getItemMeta();
            if (meta.getPersistentDataContainer().has(new NamespacedKey(plugin, "chart_shop_id"), PersistentDataType.STRING)
                && meta.getPersistentDataContainer().has(new NamespacedKey(plugin, "chart_item_id"), PersistentDataType.STRING)) {
                // Supprimer l'item du monde
                event.getItemDrop().remove();
                // event.setCancelled(true); // Optionnel : empêche le drop
                // event.getPlayer().sendMessage("§cCette carte de marché ne peut pas être jetée.");
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() != Material.FILLED_MAP) return;
        // plugin.info("Player " + player.getName() + " clicked on a market chart map.");

        if (!(clicked.getItemMeta() instanceof MapMeta meta)) return;
        if (!meta.getPersistentDataContainer().has(new NamespacedKey(plugin, "chart_shop_id"), PersistentDataType.STRING)) {
            // plugin.info("Clicked item is not a market chart map.");
            return; // L'item n'est pas une carte de marché
        }

        NamespacedKey shopIdKey = new NamespacedKey(plugin, "chart_shop_id");
        NamespacedKey itemIdKey = new NamespacedKey(plugin, "chart_item_id");
        String shopId = meta.getPersistentDataContainer().get(shopIdKey, PersistentDataType.STRING);
        String itemId = meta.getPersistentDataContainer().get(itemIdKey, PersistentDataType.STRING);

        if (shopId == null || itemId == null) return;
        // plugin.info("Market chart for shop ID: " + shopId + ", item ID: " + itemId);
        
        // if (event.getClickedInventory() != event.getWhoClicked().getInventory()) {
        // if (event.getClickedInventory() != event.getWhoClicked().getInventory() ||
        //     (event.getAction().toString().contains("MOVE") && event.getWhoClicked().getOpenInventory().getTopInventory() != null)) {
        if (event.getClickedInventory() != event.getWhoClicked().getInventory()
            || event.getAction().toString().contains("MOVE")
            || event.getSlotType() == org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER
            || (event.getView().getTopInventory() != null && event.getView().getTopInventory() != event.getWhoClicked().getInventory())) {
            event.setCancelled(true);
            // ((Player) event.getWhoClicked()).sendMessage("§cCette carte de marché ne peut pas être déplacée.");
        }

        // Récupère le renderer associé à cette carte
        // MarketChartRenderer renderer = null;
        // if (meta.hasMapView() && meta.getMapView() != null) {
        //     for (org.bukkit.map.MapRenderer r : meta.getMapView().getRenderers()) {
        //         if (r instanceof MarketChartRenderer mcr) {
        //             handleMarketChartZoom(event, clicked, player, mcr);
        //             return;
        //         } else if (r instanceof DualPriceChartRenderer dcr) {
        //             handleDualChartZoom(event, clicked, player, dcr);
        //             return;
        //         }
        //     }
        // }
        if (meta.hasMapView() && meta.getMapView() != null) {
            for (MapRenderer r : meta.getMapView().getRenderers()) {
                if (r instanceof ZoomableChartRenderer chart) {
                    // Clic gauche = zoom in, clic droit = zoom out
                    switch (event.getClick()) {
                        case LEFT:
                            chart.zoomIn();
                            chart.updateMapItemLore(clicked, player);
                            player.updateInventory();
                            break;
                        case RIGHT:
                            chart.zoomOut();
                            chart.updateMapItemLore(clicked, player);
                            player.updateInventory();
                            break;
                        default:
                            break;
                    }
                    return;
                }
            }
        }
        // if (renderer == null) return;
        // plugin.info("Found MarketChartRenderer for shop ID: " + shopId + ", item ID: " + itemId);

        // // Clic gauche = zoom in, clic droit = zoom out
        // switch (event.getClick()) {
        //     case LEFT:
        //         renderer.zoomIn();
        //         renderer.updateMapItemLore(clicked, player);
        //         // event.setCancelled(true);
        //         // player.getInventory().setItem(event.getSlot(), clicked);
        //         player.updateInventory();
        //         break;
        //     case RIGHT:
        //         renderer.zoomOut();
        //         renderer.updateMapItemLore(clicked, player);
        //         // event.setCancelled(true);
        //         // player.getInventory().setItem(event.getSlot(), clicked);
        //         player.updateInventory();
        //         break;
        //     // case MIDDLE:
        //     //     renderer.zoomOut();
        //     //     renderer.updateMapItemLore(clicked, player);
        //     //     // event.setCancelled(true);
        //     //     player.updateInventory();
        //     //     break;
        //     default:
        //         // Si un autre clic est effectué, on ne fait rien
        //         break;
        // }

        // // Met à jour la carte pour refléter le nouveau zoom
    }
    
    /**
     * Gère le zoom pour les cartes MarketChartRenderer
     */
    private void handleMarketChartZoom(InventoryClickEvent event, ItemStack clicked, Player player, MarketChartRenderer renderer) {
        // Clic gauche = zoom in, clic droit = zoom out
        switch (event.getClick()) {
            case LEFT:
                renderer.zoomIn();
                renderer.updateMapItemLore(clicked, player);
                player.updateInventory();
                break;
            case RIGHT:
                renderer.zoomOut();
                renderer.updateMapItemLore(clicked, player);
                player.updateInventory();
                break;
            default:
                // Si un autre clic est effectué, on ne fait rien
                break;
        }
    }
    
    /**
     * Gère le zoom pour les cartes DualPriceChartRenderer
     */
    private void handleDualChartZoom(InventoryClickEvent event, ItemStack clicked, Player player, DualPriceChartRenderer renderer) {
        // Clic gauche = zoom in, clic droit = zoom out
        switch (event.getClick()) {
            case LEFT:
                renderer.zoomIn();
                renderer.updateMapItemLore(clicked, player);
                player.updateInventory();
                break;
            case RIGHT:
                renderer.zoomOut();
                renderer.updateMapItemLore(clicked, player);
                player.updateInventory();
                break;
            default:
                // Si un autre clic est effectué, on ne fait rien
                break;
        }
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof ItemFrame)) return;
        ItemStack item = event.getPlayer().getInventory().getItem(event.getHand());
        if (item == null || item.getType() != Material.FILLED_MAP || !item.hasItemMeta()) return;

        MapMeta meta = (MapMeta) item.getItemMeta();
        if (meta.getPersistentDataContainer().has(new NamespacedKey(plugin, "chart_shop_id"), PersistentDataType.STRING)
            && meta.getPersistentDataContainer().has(new NamespacedKey(plugin, "chart_item_id"), PersistentDataType.STRING)) {
            event.setCancelled(true);
            // event.getPlayer().sendMessage("§cCette carte de marché ne peut pas être placée dans un item frame.");
        }
    }
}