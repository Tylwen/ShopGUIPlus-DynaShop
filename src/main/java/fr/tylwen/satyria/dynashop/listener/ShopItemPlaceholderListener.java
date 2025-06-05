package fr.tylwen.satyria.dynashop.listener;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
// import org.bukkit.scheduler.BukkitTask;
import org.bukkit.inventory.InventoryView;

import fr.tylwen.satyria.dynashop.DynaShopPlugin;
import fr.tylwen.satyria.dynashop.data.param.DynaShopType;
import fr.tylwen.satyria.dynashop.price.DynamicPrice;
import me.clip.placeholderapi.PlaceholderAPI;
import net.brcdev.shopgui.shop.Shop;
import net.brcdev.shopgui.shop.item.ShopItem;
import net.brcdev.shopgui.ShopGuiPlusApi;
import net.brcdev.shopgui.exception.player.PlayerDataNotLoadedException;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
// import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ShopItemPlaceholderListener implements Listener {
    
    private final DynaShopPlugin plugin;
    
    // Définir des constantes configurables pour les intervalles de rafraîchissement
    private static final long DEFAULT_REFRESH_INTERVAL = 500; // 0.5 seconde en ms
    private static final long CRITICAL_REFRESH_INTERVAL = 100; // 0.1 seconde en ms
    private static final int CLEANUP_INTERVAL = 5 * 60 * 20; // 5 minutes en ticks
    
    // Map pour stocker le shop actuellement ouvert par chaque joueur
    private final Map<UUID, SimpleEntry<String, String>> openShopMap = new ConcurrentHashMap<>();
    private final Map<UUID, AmountSelectionInfo> amountSelectionMenus = new ConcurrentHashMap<>();
    private final Map<UUID, SimpleEntry<String, String>> lastShopMap = new ConcurrentHashMap<>();
    private final Map<UUID, Long> pendingBulkMenuOpens = new ConcurrentHashMap<>();

    // private BukkitTask refreshTask;

    // private final Map<String, Map<String, String>> globalPriceCache = new ConcurrentHashMap<>();
    // private final Map<String, Long> cacheTimestamps = new ConcurrentHashMap<>();
    // // private static final long CACHE_EXPIRY = 20; // 20 ticks (1 seconde)
    // private static final long CACHE_EXPIRY = 10; // 10 ticks (0.5 seconde)
    private final Map<UUID, UUID> playerRefreshTasks = new ConcurrentHashMap<>();
    
    public ShopItemPlaceholderListener(DynaShopPlugin plugin) {
        this.plugin = plugin;

        // // Démarrer le planificateur de rafraîchissement
        // startRefreshScheduler();
        // Planifier un nettoyage périodique des maps
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::cleanupMaps, CLEANUP_INTERVAL, CLEANUP_INTERVAL);
    }
    
    /**
     * Nettoie les maps pour éviter les fuites de mémoire
     */
    private void cleanupMaps() {
        // Supprime les entrées pour les joueurs déconnectés
        Iterator<UUID> iterator = openShopMap.keySet().iterator();
        while (iterator.hasNext()) {
            UUID uuid = iterator.next();
            if (plugin.getServer().getPlayer(uuid) == null) {
                iterator.remove();
                lastShopMap.remove(uuid);
                amountSelectionMenus.remove(uuid);
                playerRefreshTasks.remove(uuid);
            }
        }
        pendingBulkMenuOpens.clear();
    }

    // Classe interne pour stocker les informations du menu de sélection
    private static class AmountSelectionInfo {
        private final String shopId;
        private final String itemId;
        private final ItemStack itemStack;
        private final boolean isBuying; // true pour achat, false pour vente
        private final String menuType; // "AMOUNT_SELECTION" ou "AMOUNT_SELECTION_BULK"
        private final Map<Integer, Integer> slotValues; // Map des slots -> valeurs (nombre de stacks)

        public AmountSelectionInfo(String shopId, String itemId, ItemStack itemStack, boolean isBuying, String menuType, Map<Integer, Integer> slotValues) {
            this.shopId = shopId;
            this.itemId = itemId;
            this.itemStack = itemStack.clone();
            this.isBuying = isBuying;
            this.menuType = menuType;
            this.slotValues = slotValues != null ? new HashMap<>(slotValues) : new HashMap<>();
        }

        // // Constructeurs de compatibilité
        // public AmountSelectionInfo(String shopId, String itemId, ItemStack itemStack, boolean isBuying, String menuType) {
        //     this(shopId, itemId, itemStack, isBuying, menuType, null);
        // }

        // // Ajouter un constructeur de compatibilité pour l'existant
        // public AmountSelectionInfo(String shopId, String itemId, ItemStack itemStack, boolean isBuying) {
        //     this(shopId, itemId, itemStack, isBuying, "AMOUNT_SELECTION", null);
        // }

        // Getters
        public String getShopId() { return shopId; }
        public String getItemId() { return itemId; }
        public ItemStack getItemStack() { return itemStack; }
        public boolean isBuying() { return isBuying; }
        public String getMenuType() { return menuType; }
        public Map<Integer, Integer> getSlotValues() { return slotValues; }
        
        // Méthode utilitaire pour récupérer la valeur (nombre de stacks) pour un slot donné
        public int getValueForSlot(int slot) {
            return slotValues.getOrDefault(slot, 1); // Par défaut 1 stack si non trouvé
        }
    }

    // /**
    //  * Met à jour les informations de l'item actuel pour un joueur.
    //  * Cette méthode est utilisée par l'ItemProvider pour maintenir la cohérence entre l'affichage et les données internes.
    //  */
    // public void updateCurrentItem(Player player, String shopId, String itemId) {
    //     if (player == null || shopId == null) return;
        
    //     openShopMap.put(player.getUniqueId(), new SimpleEntry<>(shopId, itemId));
    // }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;

        Player player = (Player) event.getPlayer();
        InventoryView view = event.getView();

        String fullShopId = determineShopId(view);
        if (fullShopId == null) return;
        
        // Traitement spécial pour les menus de sélection de quantité
        if (fullShopId.equals("AMOUNT_SELECTION") || fullShopId.equals("AMOUNT_SELECTION_BULK")) {
            // // IMPORTANT: Enregistrer le shop complet (AVEC LE NUMÉRO DE PAGE) ouvert par le joueur
            // openShopMap.put(player.getUniqueId(), new SimpleEntry<>(fullShopId, null));
            // plugin.getLogger().info("Detected shop page: " + fullShopId + " for player " + player.getName());
            // NE PAS ÉCRASER l'information du shop existante dans openShopMap
            // Récupérer les informations de shop existantes
            SimpleEntry<String, String> shopInfo = openShopMap.get(player.getUniqueId());
            
            // Si aucune info dans openShopMap, essayer lastShopMap
            if (shopInfo == null) {
                shopInfo = lastShopMap.get(player.getUniqueId());
                plugin.getLogger().info("Restored from lastShopMap for selection menu: " +
                    (shopInfo != null ? shopInfo.getKey() + "=" + shopInfo.getValue() : "null"));

                // Si on a trouvé des infos dans lastShopMap, les mettre dans openShopMap
                if (shopInfo != null) {
                    openShopMap.put(player.getUniqueId(), shopInfo);
                }
            }
            
            // Stocker le type de menu séparément
            plugin.getLogger().info("Opening selection menu type: " + fullShopId + " for shop: " +
                (shopInfo != null ? shopInfo.getKey() : "unknown"));

            // NOUVEAU: Stocker le type de menu séparément (vous devrez ajouter cette map)
            // private final Map<UUID, String> currentMenuTypes = new ConcurrentHashMap<>();
            // currentMenuTypes.put(player.getUniqueId(), menuType);
            
            // DynaShopPlugin.getInstance().getLogger().info("TEST 1: " + fullShopId);
            AmountSelectionInfo info = extractAmountSelectionInfo(view, fullShopId);
            if (info == null) return;
            
            // Stocker les informations pour ce menu
            amountSelectionMenus.put(player.getUniqueId(), info);
            
            // IMPORTANT: Pré-traitement pour masquer les placeholders pendant le chargement
            Map<Integer, List<String>> originalLores = new HashMap<>();
            
            // // Traiter tous les boutons dans l'inventaire
            // for (int slot = 0; slot < view.getTopInventory().getSize(); slot++) {
            //     ItemStack item = view.getTopInventory().getItem(slot);
            //     if (item != null && item.hasItemMeta() && item.getItemMeta().hasLore()) {
            //         ItemMeta meta = item.getItemMeta();
            //         List<String> lore = meta.getLore();
                    
            //         // Stocker le lore original et pré-traiter
            //         originalLores.put(slot, new ArrayList<>(lore));
            //         List<String> tempLore = preProcessPlaceholders(lore);
            //         meta.setLore(tempLore);
            //         item.setItemMeta(meta);
            //     }
            // }
            if (fullShopId.equals("AMOUNT_SELECTION")) {
                // DynaShopPlugin.getInstance().getLogger().info("Detected amount selection menu for item: " + info.getItemId());
                ItemStack itemStack = info.getItemStack();
                if (itemStack != null && itemStack.hasItemMeta() && itemStack.getItemMeta().hasLore()) {
                    ItemMeta meta = itemStack.getItemMeta();
                    List<String> lore = meta.getLore();
                    // Stocker le lore original et pré-traiter
                    int slot = info.getSlotValues().keySet().stream().findFirst().orElse(0); // Utiliser le premier slot disponible
                    originalLores.put(slot, new ArrayList<>(lore)); // Utiliser le slot 0 pour l'item principal
                    // Pré-remplacer les placeholders pour éviter de les voir bruts
                    List<String> tempLore = preProcessPlaceholders(lore);
                    meta.setLore(tempLore);
                    itemStack.setItemMeta(meta);
                }
            } else if (fullShopId.equals("AMOUNT_SELECTION_BULK")) {
                // DynaShopPlugin.getInstance().getLogger().info("Detected bulk amount selection menu for item: " + info.getItemId());
                for (Map.Entry<Integer, Integer> entry : info.getSlotValues().entrySet()) {
                    int slot = entry.getKey();
                    ItemStack item = view.getTopInventory().getItem(slot);
                    if (item != null && item.hasItemMeta() && item.getItemMeta().hasLore()) {
                        ItemMeta meta = item.getItemMeta();
                        List<String> lore = meta.getLore();
                        
                        // Stocker le lore original et pré-traiter
                        originalLores.put(slot, new ArrayList<>(lore));
                        // Pré-remplacer les placeholders pour éviter de les voir bruts
                        List<String> tempLore = preProcessPlaceholders(lore);
                        meta.setLore(tempLore);
                        item.setItemMeta(meta);
                    }
                }
            }
            // DynaShopPlugin.getInstance().getLogger().info("Detected amount selection menu for item: " + info.getItemId());
            
            // Mettre à jour les prix dans les boutons
            updateAmountSelectionInventory(player, view, info, originalLores);
            
            // Démarrer l'actualisation continue
            startContinuousAmountSelectionRefresh(player, view, info, originalLores, fullShopId);
            return;
        } else {
            // C'est un shop normal
            // IMPORTANT: Conserver le shopId complet avec numéro de page dans openShopMap
            openShopMap.put(player.getUniqueId(), new SimpleEntry<>(fullShopId, null));
            plugin.getLogger().info("New menu type for player " + player.getName() + ": " + fullShopId + " openShopMap: " + openShopMap.get(player.getUniqueId()));
            
            // [Reste du code existant...]
        }
        
        String shopId = fullShopId;
        int page = 1;

        if (fullShopId.contains("#")) {
            String[] parts = fullShopId.split("#");
            shopId = parts[0];
            try {
                page = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                page = 1;
            }
        }
        
        // IMPORTANT: Enregistrer le shop ouvert par le joueur
        // Utiliser une valeur par défaut pour l'itemId qui sera mise à jour plus tard
        openShopMap.put(player.getUniqueId(), new SimpleEntry<>(shopId, null));

        // IMPORTANT: Stocker les lores originaux avant de les modifier
        Map<Integer, List<String>> originalLores = new HashMap<>();
        
        // IMPORTANT: Pré-traitement pour masquer les placeholders pendant le chargement
        for (int slot = 0; slot < view.getTopInventory().getSize(); slot++) {
            ItemStack item = view.getTopInventory().getItem(slot);
            if (item != null && item.hasItemMeta() && item.getItemMeta().hasLore()) {
                ItemMeta meta = item.getItemMeta();
                List<String> lore = meta.getLore();
                
                if (containsDynaShopPlaceholder(lore)) {
                    // CRUCIAL: Stocker le lore original avant de le modifier
                    originalLores.put(slot, new ArrayList<>(lore));

                    // Pré-remplacer les placeholders pour éviter de les voir bruts
                    List<String> tempLore = preProcessPlaceholders(lore);
                    meta.setLore(tempLore);
                    item.setItemMeta(meta);
                }
            }
        }
        
        // final String finalShopId = shopId;
        // plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () -> {
        //     // Mettre à jour l'inventaire du shop
        //     updateShopInventory(player, event.getView(), finalShopId);
        // }, 1L);
        updateShopInventory(player, view, shopId, page, originalLores);
        
        // Démarrer l'actualisation continue
        startContinuousRefresh(player, view, shopId, page, originalLores);
    }

    // @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        Player player = (Player) event.getWhoClicked();
        // Obtenir le shopId actuel
        SimpleEntry<String, String> shopData = openShopMap.get(player.getUniqueId());
        if (shopData == null) return;
        
        String shopId = shopData.getKey();
        if (shopId == null) return;
        
        // Vérifier si c'est un click dans l'inventaire du haut (le shop)
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        
        // Récupérer l'item cliqué
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;
        
        try {
            // IMPORTANT: Vérifier d'abord si on est dans un menu de sélection
            String menuType = determineShopId(event.getView());
            if (menuType != null && (menuType.equals("AMOUNT_SELECTION") || menuType.equals("AMOUNT_SELECTION_BULK"))) {
                // Dans un menu de sélection, on ne change pas l'item
                // On conserve l'information précédente
                plugin.getLogger().info("Click in selection menu - preserving item info: " + shopData.getValue());
                return;
            } else if (menuType != null && menuType.equals("AMOUNT_SELECTION")) {
                // Si le joueur clique sur le bouton "BULK" dans un menu de sélection
                if (event.getCurrentItem() != null && event.getCurrentItem().getType() != Material.AIR) {
                    ItemMeta meta = event.getCurrentItem().getItemMeta();
                    if (meta != null && meta.hasDisplayName()) {
                        String bulkButton = ChatColor.translateAlternateColorCodes('&', 
                            ShopGuiPlusApi.getPlugin().getConfigLang().getConfig().getString("GENERAL.BULK"));
                        
                        // Si c'est un bouton BULK, marquer le joueur comme passant au menu BULK
                        if (meta.getDisplayName().contains(bulkButton)) {
                            pendingBulkMenuOpens.put(player.getUniqueId(), System.currentTimeMillis());
                            plugin.getLogger().info("Player " + player.getName() + " is opening a BULK menu, preventing shop reopening");
                        }
                    }
                }
            }

            // // Tenter de récupérer l'ID de l'item à partir du slot
            // Shop shop = ShopGuiPlusApi.getPlugin().getShopManager().getShopById(shopId);
            // if (shop == null) return;
            
            // Extraire le shopId de base et la page
            String baseShopId = shopId;
            
            int page = 1;
            // if (shopData.getKey().contains("#")) {
            //     String[] parts = shopData.getKey().split("#");
            //     try {
            //         page = Integer.parseInt(parts[1]);
            //     } catch (NumberFormatException e) {
            //         page = 1;
            //     }
            // }
            if (shopId.contains("#")) {
                String[] parts = shopId.split("#");
                baseShopId = parts[0];
                try {
                    page = Integer.parseInt(parts[1]);
                } catch (NumberFormatException e) {
                    page = 1;
                }
            }
            
            // ShopItem shopItem = shop.getShopItem(page, event.getSlot());
            
            // Tenter de récupérer l'ID de l'item à partir du slot
            Shop shop = ShopGuiPlusApi.getPlugin().getShopManager().getShopById(baseShopId);
            if (shop == null) return;
            
            ShopItem shopItem = shop.getShopItem(page, event.getSlot());
            // ShopItem shopItem = shop.getShopItem(page, shopData.getValue() != null ? Integer.parseInt(shopData.getValue()) : event.getSlot());

            if (shopItem != null) {
                // Mettre à jour l'itemId dans la map
                openShopMap.put(player.getUniqueId(), new SimpleEntry<>(shopId, shopItem.getId()));
                
                // Mettre également à jour lastShopMap pour éviter de perdre l'information
                lastShopMap.put(player.getUniqueId(), new SimpleEntry<>(shopId, shopItem.getId()));
            }
        } catch (Exception e) {
            // Ignorer les erreurs - garder la dernière valeur connue
        }
    }


    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
            
        Player player = (Player) event.getPlayer();
        UUID playerId = player.getUniqueId();
        
        // AJOUT: Sauvegarder les informations du shop actuel avant de les supprimer
        SimpleEntry<String, String> currentShop = openShopMap.get(playerId);
        
        // IMPORTANT: Définir la variable shopId pour l'utiliser plus tard
        String shopId = currentShop != null ? currentShop.getKey() : null;

        if (currentShop != null && currentShop.getKey() != null) {
            // Créer une NOUVELLE entrée pour éviter les références partagées
            lastShopMap.put(playerId, new SimpleEntry<>(currentShop.getKey(), currentShop.getValue()));
            plugin.getLogger().info("Saved shop info to lastShopMap: " + currentShop.getKey() + "=" + currentShop.getValue());
        }
        
        // NE PAS VIDER openShopMap immédiatement si c'est un menu de sélection
        // car il est probable qu'un nouveau menu s'ouvre tout de suite après
        // String title = event.getView().getTitle();
        // boolean isAmountSelection = false;
        // if (title.contains(ChatColor.translateAlternateColorCodes('&', 
        //         ShopGuiPlusApi.getPlugin().getConfigLang().getConfig().getString("DIALOG.AMOUNTSELECTION.BUY.NAME").replace("%item%", ""))) ||
        //     title.contains(ChatColor.translateAlternateColorCodes('&', 
        //         ShopGuiPlusApi.getPlugin().getConfigLang().getConfig().getString("DIALOG.AMOUNTSELECTION.SELL.NAME").replace("%item%", ""))) ||
        //     title.contains(ChatColor.translateAlternateColorCodes('&', 
        //         ShopGuiPlusApi.getPlugin().getConfigLang().getConfig().getString("DIALOG.AMOUNTSELECTION.BULKBUY.NAME").replace("%item%", ""))) ||
        //     title.contains(ChatColor.translateAlternateColorCodes('&', 
        //         ShopGuiPlusApi.getPlugin().getConfigLang().getConfig().getString("DIALOG.AMOUNTSELECTION.BULKSELL.NAME").replace("%item%", "")))) {
        //     // C'est un menu de sélection de quantité, ne pas vider openShopMap
        //     isAmountSelection = true;
        // }
        
        // Déterminer si c'est un menu de sélection
        String menuType = determineShopId(event.getView());
        boolean isSelectionMenu = menuType != null && (menuType.equals("AMOUNT_SELECTION") || menuType.equals("AMOUNT_SELECTION_BULK"));
        // DynaShopPlugin.getInstance().getLogger().info("Closing inventory for player: " + player.getName() + 
        //     " | Menu type: " + (isSelectionMenu ? "Selection Menu" : "Regular Shop"));
        
        // Arrêter la tâche de refresh
        playerRefreshTasks.remove(playerId);
        // Arrêter la tâche de refresh dans tous les cas
        // UUID taskId = playerRefreshTasks.remove(playerId);
        // if (taskId != null) {
        //     // DynaShopPlugin.getInstance().getLogger().info("Stopping continuous refresh for player: " + player.getName());
        // }
        
        // // Nettoyer les autres maps
        // // Ne nettoyer openShopMap que si ce n'est pas un menu de sélection
        // if (!isSelectionMenu) {
        //     plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
        //         openShopMap.remove(playerId);
        //         // DynaShopPlugin.getInstance().getLogger().info("Cleared openShopMap for player: " + player.getName());
        //     }, 20L); // 20 ticks de délai (1s) pour s'assurer qu'un nouveau menu n'est pas ouvert
        // }
        // // openShopMap.remove(player.getUniqueId());

        // // amountSelectionMenus.remove(player.getUniqueId());
        // // Ne pas vider amountSelectionMenus si on est dans un menu de sélection
        // if (!isSelectionMenu) {  // AJOUT: appliquer la même condition
        //     amountSelectionMenus.remove(playerId);
        // }
        // Ne nettoyer les données que si ce n'est pas un menu de sélection
        if (!isSelectionMenu) {
            // Utiliser une tâche différée avec vérification supplémentaire
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;
                // Vérifier d'abord si le joueur n'a pas ouvert un nouveau menu de sélection
                    // openShopMap.remove(playerId);
                String newMenuType = player.isOnline() ? determineShopId(player.getOpenInventory()) : null;
                boolean isNewSelectionMenu = newMenuType != null && (newMenuType.equals("AMOUNT_SELECTION") || newMenuType.equals("AMOUNT_SELECTION_BULK"));
                DynaShopPlugin.getInstance().getLogger().info("New menu type for player " + player.getName() + ": " + newMenuType + " openShopMap: " + openShopMap.get(playerId));
                
                // Vérifier si c'est juste un changement de page du même shop
                boolean isChangingPage = false;
                if (newMenuType != null && shopId != null) {
                    String baseNewShop = newMenuType.contains("#") ? newMenuType.split("#")[0] : newMenuType;
                    String baseOldShop = shopId.contains("#") ? shopId.split("#")[0] : shopId;
                    isChangingPage = baseNewShop.equals(baseOldShop) && newMenuType.contains("#");
                }

                // // Ne supprimer que si ce n'est toujours pas un menu de sélection
                // if (!isNewSelectionMenu) {
                //     openShopMap.remove(playerId);
                //     amountSelectionMenus.remove(playerId);
                //     DynaShopPlugin.getInstance().getLogger().info("Cleared openShopMap for player: " + player.getName());
                // }
                // Ne nettoyer que si ce n'est pas un menu de sélection ET pas un changement de page
                if (!isNewSelectionMenu && !isChangingPage) {
                    openShopMap.remove(playerId);
                    amountSelectionMenus.remove(playerId);
                    plugin.getLogger().info("Cleared openShopMap for player: " + player.getName());
                } else {
                    // Si c'est un changement de page, mettre à jour openShopMap avec la nouvelle page
                    if (isChangingPage) {
                        // Préserver l'itemId lors du changement de page
                        String itemId = currentShop != null ? currentShop.getValue() : null;
                        openShopMap.put(playerId, new SimpleEntry<>(newMenuType, itemId));
                        plugin.getLogger().info("Updated openShopMap for page change: " + newMenuType + "=" + itemId);
                        
                        // Mettre à jour lastShopMap également
                        lastShopMap.put(playerId, new SimpleEntry<>(newMenuType, itemId));
                    }
                    
                    plugin.getLogger().info("Preserved shop info: " + 
                                        (isChangingPage ? "changing page" : "opening selection menu"));
                }
            }, 10L); // Délai à 10 ticks (0.5s)
        } else {
            // Pour les menus de sélection, ne pas nettoyer mais arrêter le refresh
            playerRefreshTasks.remove(playerId);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSelectionMenuClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        
        Player player = (Player) event.getPlayer();
        UUID playerId = player.getUniqueId();
        String menuType = determineShopId(event.getView());
        
        // Vérifier si c'est un menu de sélection qui se ferme
        if (menuType != null && (menuType.equals("AMOUNT_SELECTION") || menuType.equals("AMOUNT_SELECTION_BULK"))) {
            // IMPORTANT: Vérifier si le joueur est en train d'ouvrir un menu BULK
            if (pendingBulkMenuOpens.containsKey(playerId)) {
                long timestamp = pendingBulkMenuOpens.get(playerId);
                // Si l'entrée a moins de 500ms, c'est une transition vers un menu BULK
                if (System.currentTimeMillis() - timestamp < 500) {
                    pendingBulkMenuOpens.remove(playerId);
                    plugin.getLogger().info("Detected transition to BULK menu, not reopening shop for player " + player.getName());
                    return; // Ne pas continuer avec le code de réouverture du shop
                } else {
                    // Nettoyer les entrées périmées
                    pendingBulkMenuOpens.remove(playerId);
                }
            }

            // Récupérer l'information du dernier shop (avec son numéro de page)
            SimpleEntry<String, String> lastShop = lastShopMap.get(player.getUniqueId());
            
            if (lastShop != null && lastShop.getKey() != null && lastShop.getKey().contains("#")) {
                // Extraire le nom du shop et la page
                String[] parts = lastShop.getKey().split("#");
                String shopId = parts[0];
                int page = 1;
                
                try {
                    page = Integer.parseInt(parts[1]);
                } catch (NumberFormatException e) {
                    page = 1;
                }

                final int finalPage = page;
                
                // Si la page est supérieure à 1, programmer l'ouverture du shop à cette page
                if (finalPage > 1) {
                    // Utiliser un délai pour éviter les conflits avec l'événement de fermeture
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        // Vérifier que le joueur est toujours en ligne
                        // Vérification supplémentaire que le joueur n'est pas dans un menu de sélection
                        if (player.isOnline()) {
                            String currentMenuType = determineShopId(player.getOpenInventory());
                            if (currentMenuType == null || 
                                (!currentMenuType.equals("AMOUNT_SELECTION") && !currentMenuType.equals("AMOUNT_SELECTION_BULK"))) {
                                
                                Shop shop = ShopGuiPlusApi.getPlugin().getShopManager().getShopById(shopId);
                                if (shop != null) {
                                    plugin.getLogger().info("Reopening shop " + shopId + " at page " + finalPage + " for player " + player.getName());
                                    try {
                                        ShopGuiPlusApi.openShop(player, shopId, finalPage);
                                    } catch (PlayerDataNotLoadedException e) {
                                        // Ignorer l'erreur
                                    }
                                }
                            } else {
                                plugin.getLogger().info("Player " + player.getName() + " already in selection menu, not reopening shop");
                            }
                        }
                    }, 2L); // 2 ticks = 100ms
                }
            }
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
        
        // Vérifier si c'est un menu de sélection de quantité
        if (title.contains(ChatColor.translateAlternateColorCodes('&', 
                ShopGuiPlusApi.getPlugin().getConfigLang().getConfig().getString("DIALOG.AMOUNTSELECTION.BUY.NAME").replace("%item%", ""))) ||
            title.contains(ChatColor.translateAlternateColorCodes('&', 
                ShopGuiPlusApi.getPlugin().getConfigLang().getConfig().getString("DIALOG.AMOUNTSELECTION.SELL.NAME").replace("%item%", "")))) {
            // C'est un menu de sélection de quantité
            // DynaShopPlugin.getInstance().getLogger().info("Detected amount selection menu: " + title);
            return "AMOUNT_SELECTION";
        }

        if (title.contains(ChatColor.translateAlternateColorCodes('&', 
                ShopGuiPlusApi.getPlugin().getConfigLang().getConfig().getString("DIALOG.AMOUNTSELECTION.BULKBUY.NAME").replace("%item%", ""))) ||
            title.contains(ChatColor.translateAlternateColorCodes('&', 
                ShopGuiPlusApi.getPlugin().getConfigLang().getConfig().getString("DIALOG.AMOUNTSELECTION.BULKSELL.NAME").replace("%item%", "")))) {
            // C'est un menu de sélection de quantité en mode bulk
            return "AMOUNT_SELECTION_BULK";
        }

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
                            String pageStr = title.substring(before.length(), after.isEmpty() ? title.length() : title.length() - after.length());
                            try {
                                page = Integer.parseInt(pageStr);
                                plugin.getLogger().info("Detected page number: " + page + " for shop " + shop.getId());
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
            plugin.getLogger().warning("Error retrieving shop via API: " + e.getMessage());
        }
        
        return null;
    }

    private void startContinuousRefresh(Player player, InventoryView view, String shopId, int page, Map<Integer, List<String>> originalLores) {
        // ID unique pour cette session de refresh
        final UUID refreshId = UUID.randomUUID();
        // final String taskKey = player.getUniqueId().toString() + ":" + refreshId.toString();
        
        // Stocker l'ID du refresh dans une map pour pouvoir l'arrêter plus tard
        playerRefreshTasks.put(player.getUniqueId(), refreshId);
        
        // Démarrer la tâche asynchrone avec boucle
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Temps d'attente entre les actualisations (en ms)
                // long refreshInterval = 5000; // 5 secondes
                long refreshInterval = 1000; // 1 seconde
                // long refreshInterval = 500; // 10 ticks (0.5 seconde)

                while (
                    player.isOnline() && 
                    player.getOpenInventory() != null && 
                    determineShopId(player.getOpenInventory()) != null &&
                    playerRefreshTasks.get(player.getUniqueId()) == refreshId // Vérifier que ce n'est pas une tâche obsolète
                ) {
                    // Attendre l'intervalle configuré
                    Thread.sleep(refreshInterval);
                    
                    // Vérifier à nouveau que le joueur est toujours en ligne et que l'inventaire est ouvert
                    if (!player.isOnline() || player.getOpenInventory() == null) {
                        break;
                    }
                    
                    // Effectuer la mise à jour sur le thread principal
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (player.isOnline() && player.getOpenInventory() != null) {
                            updateShopInventory(player, view, shopId, page, originalLores);
                        }
                    });
                }
            } catch (InterruptedException e) {
                // La tâche a été interrompue, probablement lors de l'arrêt du serveur
                Thread.currentThread().interrupt();
                // plugin.getLogger().fine("Refresh task interrupted for player " + player.getName());
            } finally {
                // Nettoyer
                playerRefreshTasks.remove(player.getUniqueId());
            }
        });
    }

    private void updateShopInventory(Player player, InventoryView view, String shopId, int page, Map<Integer, List<String>> originalLores) {
        if (view == null || view.getTopInventory() == null) { return; }

        try {
            // // Déterminer l'ID du shop et la page
            // int pageValue = page;
            // String shopIdValue = shopId;
            
            // Capturer les valeurs finales pour utilisation dans les lambdas
            final int finalPage = page;
            final String finalShopId = shopId;
            if (finalShopId == null) { return; }

            // Cache global des prix
            Map<String, Map<String, String>> priceCache = new HashMap<>();
            
            // Identifier les slots qui ont besoin d'être mis à jour
            List<Integer> slotsToUpdate = new ArrayList<>(originalLores.keySet());
            
            // Traiter les slots par lots pour éviter de surcharger le serveur
            final int BATCH_SIZE = 5; // Nombre de slots à traiter par lot
            
            for (int i = 0; i < slotsToUpdate.size(); i += BATCH_SIZE) {
                final int startIdx = i;
                final int endIdx = Math.min(i + BATCH_SIZE, slotsToUpdate.size());
            
                // Traiter chaque item individuellement avec un délai minimal entre chaque
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    Map<Integer, ItemStack> updatedItems = new HashMap<>();
                    
                    for (int j = startIdx; j < endIdx; j++) {
                        int slot = slotsToUpdate.get(j);
                        
                        try {
                            ItemStack item = view.getTopInventory().getItem(slot);
                            // if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasLore()) {
                            if (item == null || !item.hasItemMeta()) { continue; }

                            // IMPORTANT: Utiliser le lore original pour la détection des placeholders
                            List<String> originalLore = originalLores.get(slot);
                            if (originalLore == null || !containsDynaShopPlaceholder(originalLore)) { continue; }

                            String itemId = null;
                            try {
                                Shop shop = ShopGuiPlusApi.getPlugin().getShopManager().getShopById(finalShopId);
                                if (shop == null) continue;
                                
                                ShopItem shopItem = shop.getShopItem(finalPage, slot);
                                if (shopItem == null) continue;
                                
                                itemId = shopItem.getId();
                                if (itemId == null) continue;

                                // Utiliser le cache local pour éviter les calculs redondants
                                Map<String, String> itemPrices;
                                // DynamicPrice dynamicPrice = plugin.getDynaShopListener().getOrLoadPrice(finalShopId, itemId, item);
                                // plugin.getLogger().info("Prix dynamique pour l'item " + itemId + ": " + dynamicPrice);
                                if (priceCache.containsKey(itemId)) {
                                    itemPrices = priceCache.get(itemId);
                                } else {
                                    // Calcul des prix pour cet item
                                    itemPrices = getCachedPrices(player, finalShopId, itemId, item, false);
                                    priceCache.put(itemId, itemPrices);
                                }
                                
                                // Appliquer les remplacements
                                List<String> newLore = replacePlaceholders(originalLore, itemPrices, player);
                                ItemMeta meta = item.getItemMeta();
                                meta.setLore(newLore);
                                item.setItemMeta(meta);
                                
                                // Ajouter l'item au lot pour mise à jour sur le thread principal
                                updatedItems.put(slot, item.clone());

                                // // Mettre à jour l'item dans l'inventaire immédiatement
                                // final ItemStack finalItem = item.clone();
                                //
                                // plugin.getServer().getScheduler().runTask(plugin, () -> {
                                //     try {
                                //         // Vérifier que l'inventaire est toujours ouvert avant de le mettre à jour
                                //         if (player.isOnline() && player.getOpenInventory().equals(view)) {
                                //             view.getTopInventory().setItem(slot, finalItem);
                                //
                                //             // Forcer une mise à jour de l'inventaire pour ce seul item
                                //             // Cela minimise la charge visuelle pour le joueur
                                //             player.updateInventory();
                                //         }
                                //     } catch (Exception e) {
                                //         // Ignorer les erreurs lors de la mise à jour
                                //     }
                                // });
                                
                            } catch (Exception e) {
                                // Ignorer les erreurs individuelles pour ne pas bloquer les autres items
                                // plugin.getLogger().warning("Erreur lors de la mise à jour de l'item " + itemId + ": " + e.getMessage());
                            }
                        } catch (Exception e) {
                            // Capturer toute exception pour éviter d'interrompre la tâche
                        }
                    // }, delay); // Délai progressif pour répartir la charge
                    }

                    // Mettre à jour tous les items du lot en une seule fois sur le thread principal
                    if (!updatedItems.isEmpty()) {
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            if (player.isOnline() && player.getOpenInventory().equals(view)) {
                                for (Map.Entry<Integer, ItemStack> entry : updatedItems.entrySet()) {
                                    view.getTopInventory().setItem(entry.getKey(), entry.getValue());
                                }
                                player.updateInventory(); // Une seule fois pour tout le lot
                            }
                        });
                    }
                });

                // try {
                //     // Attendre un court instant pour éviter de surcharger le serveur
                //     Thread.sleep(50); // 50 ms entre chaque lot
                // } catch (InterruptedException e) {
                //     Thread.currentThread().interrupt();
                // }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error updating inventory: " + e.getMessage());
        }
    }

    /**
     * Version optimisée pour vérifier si un lore contient des placeholders DynaShop
     */
    private boolean containsDynaShopPlaceholder(List<String> lore) {
        if (lore == null || lore.isEmpty()) return false;
        
        // Utiliser un loop plus efficace avec exit rapide
        for (String line : lore) {
            if (line != null && line.indexOf("%dynashop_current_") >= 0) {
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
                // if (line.contains("%dynashop_current_buyPrice%") && 
                //     (prices.get("buy").equals("N/A") || prices.get("buy").equals("0.0"))) {
                //     skipLine = true;
                // }
                if (line.contains("%dynashop_current_buyPrice%") && 
                    (prices.get("buy").equals("N/A") || prices.get("buy").equals("0.0") || prices.get("buy").equals("-1"))) {
                    skipLine = true;
                }
                
                // if (line.contains("%dynashop_current_sellPrice%") && 
                //     (prices.get("sell").equals("N/A") || prices.get("sell").equals("0.0"))) {
                //     skipLine = true;
                // }
                if (line.contains("%dynashop_current_sellPrice%") && 
                    (prices.get("sell").equals("N/A") || prices.get("sell").equals("0.0") || prices.get("sell").equals("-1"))) {
                    skipLine = true;
                }
                
                // Vérifier le placeholder composite buy
                // if (line.contains("%dynashop_current_buy%") && 
                //     (prices.get("buy").equals("N/A") || prices.get("buy").equals("0.0"))) {
                //     skipLine = true;
                // }
                if (line.contains("%dynashop_current_buy%") && 
                    (prices.get("buy").equals("N/A") || prices.get("buy").equals("0.0") || prices.get("buy").equals("-1"))) {
                    skipLine = true;
                }
                
                // Vérifier le placeholder composite sell
                // if (line.contains("%dynashop_current_sell%") && 
                //     (prices.get("sell").equals("N/A") || prices.get("sell").equals("0.0"))) {
                //     skipLine = true;
                // }
                if (line.contains("%dynashop_current_sell%") && 
                    (prices.get("sell").equals("N/A") || prices.get("sell").equals("0.0") || prices.get("sell").equals("-1"))) {
                    skipLine = true;
                }

                // // Vérifier les placeholders de stock
                // if (line.contains("%dynashop_current_stock%") && 
                //     // (prices.get("stock").equals("N/A") || prices.get("stock").equals("0"))) {
                //     (prices.get("stock").equals("N/A"))) {
                //     skipLine = true;
                // }
                // if (line.contains("%dynashop_current_stock_ratio%") && 
                //     // (prices.get("stock").equals("N/A") || prices.get("stock").equals("0"))) {
                //     (prices.get("stock").equals("N/A"))) {
                //     skipLine = true;
                // }
                // Vérifier les placeholders de stock - Ajouter une vérification du mode STOCK
                if ((line.contains("%dynashop_current_stock%") || line.contains("%dynashop_current_maxstock%") || line.contains("%dynashop_current_stock_ratio%") || line.contains("%dynashop_current_colored_stock_ratio%")) && 
                    ((!Boolean.parseBoolean(prices.get("is_stock_mode")) && !Boolean.parseBoolean(prices.get("is_static_stock_mode"))) || prices.get("stock").equals("N/A"))) {
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
                        .replace("%dynashop_current_sell%", prices.get("base_sell"))
                        .replace("%dynashop_current_stock%", prices.get("stock"))
                        .replace("%dynashop_current_maxstock%", prices.get("stock_max"))
                        .replace("%dynashop_current_stock_ratio%", prices.get("base_stock"))
                        .replace("%dynashop_current_colored_stock_ratio%", prices.get("colored_stock_ratio"));
                }
            }
            
            // // Traiter les autres placeholders via PlaceholderAPI
            // if (!skipLine && line.contains("%")) {
            //     line = PlaceholderAPI.setPlaceholders(player, line);
            // }
            // Traiter les autres placeholders via PlaceholderAPI (seulement si disponible)
            if (!skipLine && line.contains("%")) {
                if (plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
                    try {
                        line = PlaceholderAPI.setPlaceholders(player, line);
                    } catch (Exception e) {
                        // Ignorer les erreurs de PlaceholderAPI et conserver le texte original
                        // plugin.getLogger().warning("Erreur lors de l'utilisation de PlaceholderAPI: " + e.getMessage());
                    }
                } else {
                    // Si PlaceholderAPI n'est pas disponible, laisser les placeholders tels quels
                    // ou les remplacer par un texte par défaut
                    // line = line.replaceAll("%\\w+_\\w+%", "[placeholder]");
                    // skipLine = true; // Ignorer la ligne si PlaceholderAPI n'est pas disponible
                }
            }

            // Ajouter la ligne uniquement si elle ne doit pas être ignorée
            if (!skipLine) {
                newLore.add(line);
            }
        }
        return newLore;
    }

    /**
     * Récupère ou calcule les prix mis en cache pour un item spécifique
     * @param shopId ID du shop
     * @param itemId ID de l'item
     * @param forceRefresh Force le rafraîchissement du cache
     * @return Map des valeurs de prix
     */
    private Map<String, String> getCachedPrices(Player player, String shopId, String itemId, ItemStack itemStack, boolean forceRefresh) {
        return getCachedPrices(player, shopId, itemId, itemStack, 1, forceRefresh);
    }

    /**
     * Récupère ou calcule les prix mis en cache pour un item spécifique
     * @param shopId ID du shop
     * @param itemId ID de l'item
     * @param quantity Quantité de l'item
     * @param forceRefresh Force le rafraîchissement du cache
     * @return Map des valeurs de prix
     */
    private Map<String, String> getCachedPrices(Player player, String shopId, String itemId, ItemStack itemStack, int quantity, boolean forceRefresh) {
        // String cacheKey = shopId + ":" + itemId;
        
        // // Ajouter l'UUID du joueur au cache key pour que chaque joueur ait ses propres prix modifiés
        // if (player != null) {
        //     cacheKey += ":" + player.getUniqueId().toString();
        // }
        
        // // Créer une clé unique incluant le joueur si nécessaire
        // final String cacheKey = player != null
        //     ? shopId + ":" + itemId + ":" + player.getUniqueId().toString()
        //     : shopId + ":" + itemId;

        // Créer une clé unique incluant le joueur ET la quantité
        final String cacheKey = player != null
            ? shopId + ":" + itemId + ":" + quantity + ":" + player.getUniqueId().toString()
            : shopId + ":" + itemId + ":" + quantity;

        // String baseShopId = shopId;
        // if (shopId.contains("#")) {
        //     baseShopId = shopId.split("#")[0];
        //     plugin.getLogger().info("Using full shop ID for cache: " + shopId + ", base: " + baseShopId);
        // }
        // Extraire le shopId de base pour les appels à l'API
        String baseShopId = shopId;
        int page = 1;
        
        if (shopId != null && shopId.contains("#")) {
            String[] parts = shopId.split("#");
            baseShopId = parts[0];
            try {
                page = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                page = 1;
            }
            plugin.getLogger().info("Using shop ID with page: " + shopId + " (base=" + baseShopId + ", page=" + page + ")");
        }
        
        // Variables finales pour utilisation dans la lambda
        final String finalBaseShopId = baseShopId;
        final int finalPage = page;
        
        // // Créer une clé unique incluant le joueur ET la quantité
        // final String cacheKey = player != null
        //     ? baseShopId + ":" + itemId + ":" + quantity + ":" + player.getUniqueId().toString()
        //     : baseShopId + ":" + itemId + ":" + quantity;

        // // Forcer le rafraîchissement pour toujours obtenir les prix modifiés les plus récents
        // forceRefresh = true; // Forcer le rafraîchissement à chaque fois
        
        // // Vérifier si les données sont en cache et encore valides
        // if (!forceRefresh && globalPriceCache.containsKey(cacheKey)) {
        //     Long timestamp = cacheTimestamps.get(cacheKey);
        //     if (timestamp != null && System.currentTimeMillis() - timestamp < CACHE_EXPIRY) {
        //         return globalPriceCache.get(cacheKey);
        //     }
        // }
        
        // Forcer le rafraîchissement pour les items très importants
        List<String> criticalItems = plugin.getConfigMain().getStringList("critical-items");
        boolean isCriticalItem = criticalItems.contains(baseShopId + ":" + itemId);
        forceRefresh = forceRefresh || isCriticalItem;
        
        // Utiliser le CacheManager au lieu de la vérification manuelle du cache
        return plugin.getDisplayPriceCache().get(cacheKey, () -> {
            // Si pas en cache ou expiré, calculer et mettre en cache
            String currencyPrefix = "";
            String currencySuffix = " $";
            
            try {
                currencyPrefix = ShopGuiPlusApi.getPlugin().getEconomyManager().getEconomyProvider(ShopGuiPlusApi.getShop(shopId).getEconomyType()).getCurrencyPrefix();
                currencySuffix = ShopGuiPlusApi.getPlugin().getEconomyManager().getEconomyProvider(ShopGuiPlusApi.getShop(shopId).getEconomyType()).getCurrencySuffix();
            } catch (Exception e) {
                // Utiliser les valeurs par défaut en cas d'erreur
            }
            
            Map<String, String> prices = new HashMap<>();

            // String buyPrice, sellPrice, buyMinPrice, buyMaxPrice, sellMinPrice, sellMaxPrice;

            DynamicPrice price = DynaShopPlugin.getInstance().getDynaShopListener().getOrLoadPrice(player, finalBaseShopId, itemId, itemStack);

            if (price != null) {
                // buyPrice = plugin.getPriceFormatter().formatPrice(price.getBuyPrice());
                // sellPrice = plugin.getPriceFormatter().formatPrice(price.getSellPrice());
                // buyMinPrice = plugin.getPriceFormatter().formatPrice(price.getMinBuyPrice());
                // buyMaxPrice = plugin.getPriceFormatter().formatPrice(price.getMaxBuyPrice());
                // sellMinPrice = plugin.getPriceFormatter().formatPrice(price.getMinSellPrice());
                // sellMaxPrice = plugin.getPriceFormatter().formatPrice(price.getMaxSellPrice());
                
                // Calculer les prix totaux en fonction de la quantité
                double buyPriceValue = price.getBuyPrice() * quantity;
                double sellPriceValue = price.getSellPrice() * quantity;
                double buyMinPriceValue = price.getMinBuyPrice() * quantity;
                double buyMaxPriceValue = price.getMaxBuyPrice() * quantity;
                double sellMinPriceValue = price.getMinSellPrice() * quantity;
                double sellMaxPriceValue = price.getMaxSellPrice() * quantity;
                
                // // S'assurer que les prix ne sont pas négatifs
                // buyPriceValue = Math.max(0, buyPriceValue);
                // sellPriceValue = Math.max(0, sellPriceValue);
                // buyMinPriceValue = Math.max(0, buyMinPriceValue);
                // buyMaxPriceValue = Math.max(0, buyMaxPriceValue);
                // sellMinPriceValue = Math.max(0, sellMinPriceValue);
                // sellMaxPriceValue = Math.max(0, sellMaxPriceValue);
                
                // Formater les prix
                String buyPrice = plugin.getPriceFormatter().formatPrice(buyPriceValue);
                String sellPrice = plugin.getPriceFormatter().formatPrice(sellPriceValue);
                String buyMinPrice = plugin.getPriceFormatter().formatPrice(buyMinPriceValue);
                String buyMaxPrice = plugin.getPriceFormatter().formatPrice(buyMaxPriceValue);
                String sellMinPrice = plugin.getPriceFormatter().formatPrice(sellMinPriceValue);
                String sellMaxPrice = plugin.getPriceFormatter().formatPrice(sellMaxPriceValue);
                
                prices.put("buy", buyPrice);
                prices.put("sell", sellPrice);
                prices.put("buy_min", buyMinPrice);
                prices.put("buy_max", buyMaxPrice);
                prices.put("sell_min", sellMinPrice);
                prices.put("sell_max", sellMaxPrice);
            } else {
                // buyPrice = plugin.getPriceFormatter().getPriceByType(shopId, itemId, "buy");
                // sellPrice = plugin.getPriceFormatter().getPriceByType(shopId, itemId, "sell");
                // buyMinPrice = plugin.getPriceFormatter().getPriceByType(shopId, itemId, "buy_min");
                // buyMaxPrice = plugin.getPriceFormatter().getPriceByType(shopId, itemId, "buy_max");
                // sellMinPrice = plugin.getPriceFormatter().getPriceByType(shopId, itemId, "sell_min");
                // sellMaxPrice = plugin.getPriceFormatter().getPriceByType(shopId, itemId, "sell_max");
                
                // Si pas de prix dynamique, récupérer les prix statiques et multiplier
                double buyPriceValue = parseFormattedNumber(plugin.getPriceFormatter().getPriceByType(finalBaseShopId, itemId, "buy")) * quantity;
                double sellPriceValue = parseFormattedNumber(plugin.getPriceFormatter().getPriceByType(finalBaseShopId, itemId, "sell")) * quantity;
                double buyMinPriceValue = parseFormattedNumber(plugin.getPriceFormatter().getPriceByType(finalBaseShopId, itemId, "buy_min")) * quantity;
                double buyMaxPriceValue = parseFormattedNumber(plugin.getPriceFormatter().getPriceByType(finalBaseShopId, itemId, "buy_max")) * quantity;
                double sellMinPriceValue = parseFormattedNumber(plugin.getPriceFormatter().getPriceByType(finalBaseShopId, itemId, "sell_min")) * quantity;
                double sellMaxPriceValue = parseFormattedNumber(plugin.getPriceFormatter().getPriceByType(finalBaseShopId, itemId, "sell_max")) * quantity;
                
                // // S'assurer que les prix ne sont pas négatifs
                // buyPriceValue = Math.max(0, buyPriceValue);
                // sellPriceValue = Math.max(0, sellPriceValue);
                // buyMinPriceValue = Math.max(0, buyMinPriceValue);
                // buyMaxPriceValue = Math.max(0, buyMaxPriceValue);
                // sellMinPriceValue = Math.max(0, sellMinPriceValue);
                // sellMaxPriceValue = Math.max(0, sellMaxPriceValue);
                
                // Formater les prix
                String buyPrice = plugin.getPriceFormatter().formatPrice(buyPriceValue);
                String sellPrice = plugin.getPriceFormatter().formatPrice(sellPriceValue);
                String buyMinPrice = plugin.getPriceFormatter().formatPrice(buyMinPriceValue);
                String buyMaxPrice = plugin.getPriceFormatter().formatPrice(buyMaxPriceValue);
                String sellMinPrice = plugin.getPriceFormatter().formatPrice(sellMinPriceValue);
                String sellMaxPrice = plugin.getPriceFormatter().formatPrice(sellMaxPriceValue);
                
                prices.put("buy", buyPrice);
                prices.put("sell", sellPrice);
                prices.put("buy_min", buyMinPrice);
                prices.put("buy_max", buyMaxPrice);
                prices.put("sell_min", sellMinPrice);
                prices.put("sell_max", sellMaxPrice);
            }

            // Déterminer si l'item est en mode STOCK
            boolean isStockMode = plugin.getShopConfigManager().getTypeDynaShop(shopId, itemId) == DynaShopType.STOCK;
            prices.put("is_stock_mode", String.valueOf(isStockMode));
            
            // Déterminer si l'item est en mode STATIC_STOCK
            boolean isStaticStockMode = plugin.getShopConfigManager().getTypeDynaShop(shopId, itemId) == DynaShopType.STATIC_STOCK;
            prices.put("is_static_stock_mode", String.valueOf(isStaticStockMode));

            // Déterminer si l'item est en mode RECIPE
            boolean isRecipeMode = plugin.getShopConfigManager().getTypeDynaShop(shopId, itemId) == DynaShopType.RECIPE;
            prices.put("is_recipe_mode", String.valueOf(isRecipeMode));
            if (isRecipeMode) {
                // Si l'item est en mode RECIPE, et que un des prix est en mode STOCK, on affiche le stock
                boolean hasMaxStock = DynaShopPlugin.getInstance().getPriceRecipe().calculateMaxStock(shopId, itemId, itemStack, new ArrayList<>()) > 0;
                if (hasMaxStock) {
                    isStockMode = true; // Forcer le mode STOCK si maxStock > 0
                    prices.put("is_stock_mode", String.valueOf(isStockMode));
                }
            }

            // // Stocker les valeurs
            // prices.put("buy", buyPrice);
            // prices.put("sell", sellPrice);
            // prices.put("buy_min", buyMinPrice);
            // prices.put("buy_max", buyMaxPrice);
            // prices.put("sell_min", sellMinPrice);
            // prices.put("sell_max", sellMaxPrice);
            
            // Si l'item n'est pas en mode STOCK, ne pas afficher les informations de stock
            if (!isStockMode && !isStaticStockMode) {
                prices.put("stock", "N/A");
                prices.put("stock_max", "N/A");
                prices.put("base_stock", "N/A");
                prices.put("colored_stock_ratio", "N/A");
            } else {
                // // // // Ajouter les informations de stock
                // // String currentStock = plugin.getPriceFormatter().getStockByType(shopId, itemId, "stock");
                // // String maxStock = plugin.getPriceFormatter().getStockByType(shopId, itemId, "stock_max");
                // // // String currentStock = plugin.getPriceFormatter().formatStock(price.getCurrentStock());
                // // // if (currentStock.equals("0")) {
                // // //     currentStock = "N/A"; // Si le stock est 0, on le remplace par N/A
                // // // }

                // // // plugin.getLogger().info("Current stock for " + itemId + ": " + price.getStock() + 
                // // //     ", Max stock: " + price.getMaxStock() + 
                // // //     ", Formatted current stock: " + plugin.getPriceFormatter().formatStock(price.getStock()) +
                // // //     ", Formatted max stock: " + plugin.getPriceFormatter().formatStock(price.getMaxStock()));

                // // // String maxStock = plugin.getPriceFormatter().formatStock(price.getMaxStock());
                // // // if (maxStock.equals("0")) {
                // // //     maxStock = "N/A"; // Si le stock est 0, on le remplace par N/A
                // // // }
                // String currentStock, maxStock, fCurrentStock, fMaxStock;
                // if (price != null) {
                //     currentStock = String.valueOf(price.getStock());
                //     fCurrentStock = plugin.getPriceFormatter().formatStock(price.getStock());
                //     // if (currentStock.equals("0")) {
                //     //     currentStock = "N/A"; // Si le stock est 0, on le remplace par N/A
                //     // }
                //     maxStock = String.valueOf(price.getMaxStock());
                //     fMaxStock = plugin.getPriceFormatter().formatStock(price.getMaxStock());
                //     // if (maxStock.equals("0")) {
                //     //     maxStock = "N/A"; // Si le stock est 0, on le remplace par N/A
                //     // }
                //     // plugin.getLogger().info("Current stock for " + itemId + ": " + price.getStock() + 
                //     //     ", Max stock: " + price.getMaxStock() + 
                //     //     ", Formatted current stock: " + plugin.getPriceFormatter().formatStock(price.getStock()) +
                //     //     ", Formatted max stock: " + plugin.getPriceFormatter().formatStock(price.getMaxStock()));
                // } else {
                //     currentStock = plugin.getPriceFormatter().getStockByType(shopId, itemId, "stock");
                //     fCurrentStock = plugin.getPriceFormatter().formatStock(Integer.parseInt(currentStock));
                //     maxStock = plugin.getPriceFormatter().getStockByType(shopId, itemId, "stock_max");
                //     fMaxStock = plugin.getPriceFormatter().formatStock(Integer.parseInt(maxStock));
                // }

                // prices.put("stock", fCurrentStock);
                // prices.put("stock_max", fMaxStock);

                // // Format pour le stock
                // if (currentStock.equals("N/A") || currentStock.equals("0")) {
                //     // prices.put("base_stock", ChatColor.translateAlternateColorCodes('&', "&cOut of stock"));
                //     prices.put("base_stock", ChatColor.translateAlternateColorCodes('&', this.plugin.getLangConfig().getPlaceholderOutOfStock()));
                //     prices.put("colored_stock_ratio", ChatColor.translateAlternateColorCodes('&', this.plugin.getLangConfig().getPlaceholderOutOfStock()));
                // } else {
                //     prices.put("base_stock", String.format("%s/%s", fCurrentStock, fMaxStock));

                //     // Format avec couleurs selon le niveau de stock
                //     int current = Integer.parseInt(currentStock);
                //     int max = Integer.parseInt(maxStock);
                //     String colorCode = (current < max * 0.25) ? "&c" : (current < max * 0.5) ? "&e" : "&a";
                //     prices.put("colored_stock_ratio", ChatColor.translateAlternateColorCodes('&', 
                //         String.format("%s%s&7/%s", colorCode, fCurrentStock, fMaxStock)));
                // }
                String currentStock, maxStock, fCurrentStock, fMaxStock;
                if (price != null) {
                    // Vérifier si le stock est désactivé (-1)
                    if (price.getStock() < 0) {
                        currentStock = "N/A";
                        fCurrentStock = "N/A";
                    } else {
                        currentStock = String.valueOf(price.getStock());
                        fCurrentStock = plugin.getPriceFormatter().formatStock(price.getStock());
                    }
                    
                    // Vérifier si le stock max est désactivé (-1)
                    if (price.getMaxStock() < 0) {
                        maxStock = "N/A";
                        fMaxStock = "N/A";
                    } else {
                        maxStock = String.valueOf(price.getMaxStock());
                        fMaxStock = plugin.getPriceFormatter().formatStock(price.getMaxStock());
                    }
                } else {
                    // Récupération depuis les méthodes de formattage
                    currentStock = plugin.getPriceFormatter().getStockByType(shopId, itemId, "stock");
                    fCurrentStock = currentStock.equals("N/A") || currentStock.equals("-1") ? 
                        "N/A" : plugin.getPriceFormatter().formatStock(Integer.parseInt(currentStock));
                    
                    maxStock = plugin.getPriceFormatter().getStockByType(shopId, itemId, "stock_max");
                    fMaxStock = maxStock.equals("N/A") || maxStock.equals("-1") ? 
                        "N/A" : plugin.getPriceFormatter().formatStock(Integer.parseInt(maxStock));
                }

                prices.put("stock", fCurrentStock);
                prices.put("stock_max", fMaxStock);

                // Format pour le stock - Ajouter vérification pour -1
                if (currentStock.equals("N/A") || currentStock.equals("-1") || currentStock.equals("0")) {
                    // Utiliser le texte de configuration pour "épuisé"
                    prices.put("base_stock", ChatColor.translateAlternateColorCodes('&', this.plugin.getLangConfig().getPlaceholderOutOfStock()));
                    prices.put("colored_stock_ratio", ChatColor.translateAlternateColorCodes('&', this.plugin.getLangConfig().getPlaceholderOutOfStock()));
                } else {
                    prices.put("base_stock", String.format("%s/%s", fCurrentStock, fMaxStock));

                    // Format avec couleurs selon le niveau de stock
                    try {
                        int current = Integer.parseInt(currentStock);
                        int max = Integer.parseInt(maxStock);
                        
                        // Vérifier si max est valide
                        if (max <= 0) {
                            // Cas où maxStock est 0 ou négatif - afficher uniquement la valeur actuelle
                            prices.put("colored_stock_ratio", ChatColor.translateAlternateColorCodes('&', String.format("&7%s", fCurrentStock)));
                        } else {
                            // // Calculer la couleur en fonction du ratio
                            // String colorCode = (current < max * 0.25) ? "&c" : (current < max * 0.5) ? "&e" : "&a";
                            // Calculer la couleur en fonction du ratio
                            String colorCode;
                            
                            // Déterminer le code couleur selon le niveau de stock
                            if (current < max * 0.10) {
                                colorCode = "&4"; // Rouge foncé pour stock critique
                            } else if (current < max * 0.25) {
                                colorCode = "&c"; // Rouge pour stock faible
                            } else if (current < max * 0.5) {
                                colorCode = "&e"; // Jaune pour stock moyen
                            } else {
                                colorCode = "&a"; // Vert pour stock élevé
                            }
                            prices.put("colored_stock_ratio", ChatColor.translateAlternateColorCodes('&', String.format("%s%s&7/%s", colorCode, fCurrentStock, fMaxStock)));
                        }
                    } catch (NumberFormatException e) {
                        // En cas d'erreur de conversion, utiliser un format simplifié
                        prices.put("colored_stock_ratio", ChatColor.translateAlternateColorCodes('&', String.format("&7%s/%s", fCurrentStock, fMaxStock)));
                    }
                }
            }
            
            // // Format pour le prix d'achat avec min-max
            // if (!buyMinPrice.equals("N/A") && !buyMaxPrice.equals("N/A")) {
            //     prices.put("base_buy", String.format(
            //         currencyPrefix + "%s" + currencySuffix + " §7(%s - %s) ", 
            //         buyPrice, buyMinPrice, buyMaxPrice
            //     ));
            // } else {
            //     prices.put("base_buy", currencyPrefix + buyPrice + currencySuffix);
            // }
            
            // // Format pour le prix de vente avec min-max
            // if (!sellMinPrice.equals("N/A") && !sellMaxPrice.equals("N/A")) {
            //     prices.put("base_sell", String.format(
            //         currencyPrefix + "%s" + currencySuffix + " §7(%s - %s) ", 
            //         sellPrice, sellMinPrice, sellMaxPrice
            //     ));
            // } else {
            //     prices.put("base_sell", currencyPrefix + sellPrice + currencySuffix);
            // }
            // Format pour le prix d'achat avec min-max
            // if (!buyMinPrice.equals("N/A") && !buyMaxPrice.equals("N/A") &&
            //     (!buyMinPrice.equals(buyPrice) || !buyMaxPrice.equals(buyPrice))) {
            if (!prices.get("buy").equals("N/A") && !prices.get("buy_min").equals("N/A") && !prices.get("buy_max").equals("N/A") &&
                (!prices.get("buy_min").equals(prices.get("buy")) || !prices.get("buy_max").equals(prices.get("buy")))) {
                // Afficher la fourchette uniquement si min ou max diffère du prix actuel
                prices.put("base_buy", String.format(
                    currencyPrefix + "%s" + currencySuffix + " §7(%s - %s) ", 
                    prices.get("buy"), prices.get("buy_min"), prices.get("buy_max")
                ));
            } else {
                // Affichage simplifié quand min=max=prix actuel
                prices.put("base_buy", currencyPrefix + prices.get("buy") + currencySuffix);
            }

            // Format pour le prix de vente avec min-max
            if (!prices.get("sell_min").equals("N/A") && !prices.get("sell_max").equals("N/A") &&
                (!prices.get("sell_min").equals(prices.get("sell")) || !prices.get("sell_max").equals(prices.get("sell")))) {
                // Afficher la fourchette uniquement si min ou max diffère du prix actuel
                prices.put("base_sell", String.format(
                    currencyPrefix + "%s" + currencySuffix + " §7(%s - %s) ", 
                    prices.get("sell"), prices.get("sell_min"), prices.get("sell_max")
                ));
            } else {
                // Affichage simplifié quand min=max=prix actuel
                prices.put("base_sell", currencyPrefix + prices.get("sell") + currencySuffix);
            }

            // // Mettre en cache avec timestamp
            // globalPriceCache.put(cacheKey, prices);
            // cacheTimestamps.put(cacheKey, System.currentTimeMillis());
            
            return prices;
        });
    }

    /**
     * Pré-remplace les placeholders avec des valeurs temporaires pour éviter les textes bruts
     */
    private List<String> preProcessPlaceholders(List<String> lore) {
        List<String> processed = new ArrayList<>();
        for (String line : lore) {
            if (line.contains("%dynashop_current_")) {
                // Remplacer temporairement par "Chargement..." mais UNIQUEMENT pour l'affichage
                String tempLine = line
                    .replace("%dynashop_current_buyPrice%", "Loading...")
                    .replace("%dynashop_current_sellPrice%", "Loading...")
                    .replace("%dynashop_current_buyMinPrice%", "...")
                    .replace("%dynashop_current_buyMaxPrice%", "...")
                    .replace("%dynashop_current_sellMinPrice%", "...")
                    .replace("%dynashop_current_sellMaxPrice%", "...")
                    .replace("%dynashop_current_buy%", "Loading...")
                    .replace("%dynashop_current_sell%", "Loading...")
                    .replace("%dynashop_current_stock%", "Loading...")
                    .replace("%dynashop_current_maxstock%", "Loading...")
                    .replace("%dynashop_current_stock_ratio%", "Loading...")
                    .replace("%dynashop_current_colored_stock_ratio%", "Loading...");
                processed.add(tempLine);
            } else {
                processed.add(line);
            }
        }
        return processed;
    }

    /**
     * Extracteur optimisé pour les données d'interface de sélection
     */
    private AmountSelectionInfo extractAmountSelectionInfo(InventoryView view, String menuType) {
        String title = view.getTitle();
        
        // Récupérer les chaînes de configuration une seule fois
        String buyName = ShopGuiPlusApi.getPlugin().getConfigLang().getConfig().getString("DIALOG.AMOUNTSELECTION.BUY.NAME");
        String sellName = ShopGuiPlusApi.getPlugin().getConfigLang().getConfig().getString("DIALOG.AMOUNTSELECTION.SELL.NAME");
        String bulkBuyName = ShopGuiPlusApi.getPlugin().getConfigLang().getConfig().getString("DIALOG.AMOUNTSELECTION.BULKBUY.NAME");
        String bulkSellName = ShopGuiPlusApi.getPlugin().getConfigLang().getConfig().getString("DIALOG.AMOUNTSELECTION.BULKSELL.NAME");
        
        // Traduire les couleurs une seule fois
        String translatedBuyName = ChatColor.translateAlternateColorCodes('&', buyName.replace("%item%", ""));
        String translatedSellName = ChatColor.translateAlternateColorCodes('&', sellName.replace("%item%", ""));
        String translatedBulkBuyName = ChatColor.translateAlternateColorCodes('&', bulkBuyName.replace("%item%", ""));
        String translatedBulkSellName = ChatColor.translateAlternateColorCodes('&', bulkSellName.replace("%item%", ""));
        
        boolean isBuying = title.contains(translatedBuyName) || title.contains(translatedBulkBuyName);
        
        // Récupérer les configurations plus efficacement
        Map<Integer, Integer> slotValues = new HashMap<>();
        int centerSlot;
        
        // Utiliser une structure conditionnelle plus efficace
        if (menuType.equals("AMOUNT_SELECTION_BULK")) {
            String buttonPrefix = isBuying ? "amountSelectionGUIBulkBuy.buttons.buy" : "amountSelectionGUIBulkSell.buttons.sell";
            String slotSuffix = ".slot";
            String valueSuffix = ".value";
            
            for (int i = 1; i <= 9; i++) {
                String slotPath = buttonPrefix + i + slotSuffix;
                String valuePath = buttonPrefix + i + valueSuffix;
                
                if (ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().contains(slotPath)) {
                    int slot = ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getInt(slotPath);
                    int value = ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getInt(valuePath);
                    slotValues.put(slot, value);
                }
            }
            
            centerSlot = ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getInt(buttonPrefix + "1" + slotSuffix, 0);
        } else {
            String itemSlotPath = "amountSelectionGUI.itemSlot";
            centerSlot = ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getInt(itemSlotPath, 22);
            slotValues.put(centerSlot, 1); // Slot central par défaut avec valeur 1
        }
        
        // Vérification de sécurité pour le slot central
        int inventorySize = view.getTopInventory().getSize();
        if (centerSlot >= inventorySize) {
            centerSlot = Math.min(inventorySize - 1, 22); // Utiliser slot 22 par défaut, sinon dernier slot
        }
        
        // Récupérer l'item central
        ItemStack centerItem = view.getTopInventory().getItem(centerSlot);
        if (centerItem == null) {
            plugin.getLogger().warning("Center item is null in amount selection inventory at slot " + centerSlot);
            return null;
        }
        
        // Récupérer les infos du shop
        Player player = (Player) view.getPlayer();
        SimpleEntry<String, String> shopInfo = openShopMap.get(player.getUniqueId());
        
        // Fallback sur lastShopMap si nécessaire
        if (shopInfo == null) {
            shopInfo = lastShopMap.get(player.getUniqueId());
            plugin.getLogger().info("Using lastShopMap for selection menu: " + 
                (shopInfo != null ? shopInfo.getKey() : "null"));
        }
        
        if (shopInfo == null) return null;
        
        String shopId = shopInfo.getKey();
        String itemId = shopInfo.getValue();
        
        // Log explicite pour débogage
        String baseShopId = shopId;
        int page = 1;
        
        if (shopId != null && shopId.contains("#")) {
            String[] parts = shopId.split("#");
            baseShopId = parts[0];
            try {
                page = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                page = 1;
            }
        }
        
        plugin.getLogger().info("Extracting info for selection menu: type=" + menuType + 
                            ", shop=" + shopId + " (base=" + baseShopId + ", page=" + page + ")" +
                            ", item=" + itemId);
        
        return new AmountSelectionInfo(
            shopId,    // Utiliser le vrai shopId (avec page) et non le type de menu
            itemId,    // Utiliser le vrai itemId
            centerItem,
            isBuying,
            menuType,  // Type de menu uniquement ici
            slotValues
        );
        
        // // Extraire le shopId de base et la page
        // String shopId = shopInfo.getKey();
        // String baseShopId = shopId;
        // int page = 1;
        
        // if (shopId.contains("#")) {
        //     String[] parts = shopId.split("#");
        //     baseShopId = parts[0];
        //     try {
        //         page = Integer.parseInt(parts[1]);
        //     } catch (NumberFormatException e) {
        //         page = 1;
        //     }
        // }
        
        // // Log pour débogage
        // plugin.getLogger().info("Extracting info for selection menu: shop=" + shopId + 
        //                     ", base shop=" + baseShopId + 
        //                     ", page=" + page + 
        //                     ", item=" + shopInfo.getValue());
        
        // return new AmountSelectionInfo(
        //     shopId,              // shopId complet avec page
        //     shopInfo.getValue(), // itemId
        //     centerItem,          // itemStack
        //     isBuying,            // isBuying
        //     menuType,            // menuType
        //     slotValues           // slotValues
        // );
        
        // return new AmountSelectionInfo(
        //     shopInfo.getKey(),    // shopId
        //     shopInfo.getValue(),  // itemId
        //     centerItem,           // itemStack
        //     isBuying,             // isBuying
        //     menuType,             // menuType
        //     slotValues            // slotValues
        // );
    }

    // private AmountSelectionInfo extractAmountSelectionInfo(InventoryView view, String menuType) {
    //     String title = view.getTitle();
    //     // boolean isBuying = title.contains(ChatColor.translateAlternateColorCodes('&', ShopGuiPlusApi.getPlugin().getConfigLang().getConfig().getString("DIALOG.AMOUNTSELECTION.BUY.NAME").replace("%item%", ""))) ||
    //     //                 title.contains(ChatColor.translateAlternateColorCodes('&', ShopGuiPlusApi.getPlugin().getConfigLang().getConfig().getString("DIALOG.AMOUNTSELECTION.BULKBUY.NAME").replace("%item%", "")));
        
    //     boolean isBuying = title.contains(ChatColor.translateAlternateColorCodes('&', 
    //         ShopGuiPlusApi.getPlugin().getConfigLang().getConfig().getString("DIALOG.AMOUNTSELECTION.BUY.NAME").replace("%item%", ""))) ||
    //         title.contains(ChatColor.translateAlternateColorCodes('&', 
    //         ShopGuiPlusApi.getPlugin().getConfigLang().getConfig().getString("DIALOG.AMOUNTSELECTION.BULKBUY.NAME").replace("%item%", "")));
            
    //     // Déterminer l'emplacement de l'item selon le type de menu
    //     int centerSlot;
    //     int inventorySize = view.getTopInventory().getSize();
    //     Map<Integer, Integer> slotValues = new HashMap<>();

    //     // // Séparer clairement les types de menus
    //     // boolean isBulkMenu = title.contains(ChatColor.translateAlternateColorCodes('&', 
    //     //     ShopGuiPlusApi.getPlugin().getConfigLang().getConfig().getString("DIALOG.AMOUNTSELECTION.BULKBUY.NAME").replace("%item%", ""))) ||
    //     //     title.contains(ChatColor.translateAlternateColorCodes('&', 
    //     //     ShopGuiPlusApi.getPlugin().getConfigLang().getConfig().getString("DIALOG.AMOUNTSELECTION.BULKSELL.NAME").replace("%item%", "")));
        
    //     // boolean isRegularMenu = title.contains(ChatColor.translateAlternateColorCodes('&', 
    //     //     ShopGuiPlusApi.getPlugin().getConfigLang().getConfig().getString("DIALOG.AMOUNTSELECTION.BUY.NAME").replace("%item%", ""))) ||
    //     //     title.contains(ChatColor.translateAlternateColorCodes('&', 
    //     //     ShopGuiPlusApi.getPlugin().getConfigLang().getConfig().getString("DIALOG.AMOUNTSELECTION.SELL.NAME").replace("%item%", "")));
        
    //     // // Utiliser exactement votre approche existante avec la config
    //     // if (menuType.equals("AMOUNT_SELECTION_BULK")) {
    //     //     // Traitement différent selon si c'est achat ou vente
    //     //     if (isBuying) {
    //     //         // Configuration pour BULK BUY
    //     //         for (int i = 1; i <= 9; i++) {
    //     //             if (ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().contains("amountSelectionGUIBulkBuy.buttons.buy" + i + ".slot")) {
    //     //                 int slot = ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getInt("amountSelectionGUIBulkBuy.buttons.buy" + i + ".slot");
    //     //                 int value = ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getInt("amountSelectionGUIBulkBuy.buttons.buy" + i + ".value");
    //     //                 slotValues.put(slot, value);
    //     //             }
    //     //         }
    //     //         centerSlot = ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getInt("amountSelectionGUIBulkBuy.buttons.buy1.slot", 0);
    //     //     } else {
    //     //         // Configuration pour BULK SELL
    //     //         for (int i = 1; i <= 9; i++) {
    //     //             if (ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().contains("amountSelectionGUIBulkSell.buttons.sell" + i + ".slot")) {
    //     //                 int slot = ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getInt("amountSelectionGUIBulkSell.buttons.sell" + i + ".slot");
    //     //                 int value = ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getInt("amountSelectionGUIBulkSell.buttons.sell" + i + ".value");
    //     //                 slotValues.put(slot, value);
    //     //             }
    //     //         }
    //     //         centerSlot = ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getInt("amountSelectionGUIBulkSell.buttons.sell1.slot", 0);
    //     //     }
    //     // } else {
    //     //     // Pour les menus standard, utiliser la valeur configurée ou 22 par défaut
    //     //     centerSlot = ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getInt("amountSelectionGUI.itemSlot", 22);
    //     // }
        
    //     if (menuType.equals("AMOUNT_SELECTION_BULK")) {
    //         // Traitement différent selon si c'est achat ou vente
    //         if (isBuying) {
    //             // Configuration pour BULK BUY
    //             try {
    //                 for (int i = 1; i <= 9; i++) {
    //                     String slotPath = "amountSelectionGUIBulkBuy.buttons.buy" + i + ".slot";
    //                     String valuePath = "amountSelectionGUIBulkBuy.buttons.buy" + i + ".value";
                        
    //                     // Vérifier que les deux chemins existent avant d'y accéder
    //                     if (ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().contains(slotPath) && 
    //                         ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().contains(valuePath)) {
                            
    //                         int slot = ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getInt(slotPath);
    //                         int value = ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getInt(valuePath);
    //                         slotValues.put(slot, value);
    //                     }
    //                 }
    //                 // Utiliser get() avec une valeur par défaut pour éviter les NullPointerException
    //                 String centerSlotPath = "amountSelectionGUIBulkBuy.buttons.buy1.slot";
    //                 if (ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().contains(centerSlotPath)) {
    //                     centerSlot = ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getInt(centerSlotPath, 0);
    //                 } else {
    //                     centerSlot = 0; // Valeur par défaut sûre
    //                 }
    //             } catch (Exception e) {
    //                 plugin.getLogger().warning("Error loading BULK BUY configuration: " + e.getMessage());
    //                 centerSlot = 0; // Valeur par défaut en cas d'erreur
    //             }
    //         } else {
    //             // Configuration pour BULK SELL - même approche sécurisée
    //             try {
    //                 for (int i = 1; i <= 9; i++) {
    //                     String slotPath = "amountSelectionGUIBulkSell.buttons.sell" + i + ".slot";
    //                     String valuePath = "amountSelectionGUIBulkSell.buttons.sell" + i + ".value";
                        
    //                     if (ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().contains(slotPath) && 
    //                         ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().contains(valuePath)) {
                            
    //                         int slot = ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getInt(slotPath);
    //                         int value = ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getInt(valuePath);
    //                         slotValues.put(slot, value);
    //                     }
    //                 }
    //                 String centerSlotPath = "amountSelectionGUIBulkSell.buttons.sell1.slot";
    //                 if (ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().contains(centerSlotPath)) {
    //                     centerSlot = ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getInt(centerSlotPath, 0);
    //                 } else {
    //                     centerSlot = 0;
    //                 }
    //             } catch (Exception e) {
    //                 plugin.getLogger().warning("Error loading BULK SELL configuration: " + e.getMessage());
    //                 centerSlot = 0;
    //             }
    //         }
    //     } else {
    //         // Pour les menus standard, sécuriser également
    //         String itemSlotPath = "amountSelectionGUI.itemSlot";
    //         if (ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().contains(itemSlotPath)) {
    //             centerSlot = ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getInt(itemSlotPath, 22);
    //         } else {
    //             centerSlot = 22; // Valeur par défaut standard
    //         }
    //     }
        
    //     // // Vérification de sécurité pour éviter les erreurs d'index
    //     // if (centerSlot >= inventorySize) {
    //     //     plugin.getLogger().warning("Center slot " + centerSlot + " is out of bounds for inventory size " + 
    //     //         inventorySize + " in menu type " + menuType + ". Using fallback slot.");
            
    //     //     // Utiliser un slot de repli sûr
    //     //     centerSlot = Math.min(4, inventorySize - 1); // Position 4 ou dernière position
    //     // }
        
    //     // Récupérer l'item à la position calculée
    //     ItemStack centerItem = view.getTopInventory().getItem(centerSlot);
    //     if (centerItem == null) {
    //         plugin.getLogger().warning("Center item is null in amount selection inventory at slot " + centerSlot);
    //         return null;
    //     }

    //     // // Récupérer l'item central (habituellement en position 23)
    //     // // ItemStack centerItem = view.getTopInventory().getItem(23); // 23 est le slot central dans un inventaire de 54 slots
    //     // // int size = view.getTopInventory().getSize();
    //     // // int rows = size / 9; // Nombre de lignes
    //     // // int centerSlot = (rows * 9) / 2 + (rows % 2 == 0 ? -1 : 0); // Slot central pour un inventaire de taille variable
    //     // // int centerSlot = (rows * 9) / 4 + (rows % 2 == 0 ? -1 : 0); // Slot central pour un inventaire de taille variable, ajusté pour 54 slots
    //     // int centerSlot = ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getInt("amountSelectionGUI.itemSlot", 22); // Slot central par défaut
    //     // ItemStack centerItem = view.getTopInventory().getItem(centerSlot);
    //     // if (centerItem == null) {
    //     //     // plugin.getLogger().warning("Center item is null in amount selection inventory for player: " + view.getPlayer().getName() +
    //     //     //     ". Inventory size: " + size + ", Rows: " + rows + ", Center Slot: " + centerSlot);
    //     //     return null;
    //     // }
        
    //     // // Récupérer les dernières informations du shop ouvert par le joueur
    //     // Player player = (Player) view.getPlayer();
    //     // SimpleEntry<String, String> lastShopInfo = openShopMap.get(player.getUniqueId());
    //     // DynaShopPlugin.getInstance().getLogger().info("openShopMap for player " + player.getName() + ": " + openShopMap.get(player.getUniqueId()));
    //     // DynaShopPlugin.getInstance().getLogger().info("Last shop info for player " + player.getName() + ": " + lastShopInfo + 
    //     //     ", Shop ID: " + (lastShopInfo != null ? lastShopInfo.getKey() : "null") + 
    //     //     ", Item ID: " + (lastShopInfo != null ? lastShopInfo.getValue() : "null"));
    //     // if (lastShopInfo == null) {
    //     //     plugin.getLogger().warning("No last shop info found for player: " + player.getName());
    //     //     return null;
    //     // }
        
    //     // Récupérer les dernières informations du shop (d'abord openShopMap, puis lastShopMap)
    //     // Récupérer les dernières informations du shop (d'abord openShopMap, puis lastShopMap)
    //     Player player = (Player) view.getPlayer();
    //     SimpleEntry<String, String> shopInfo = openShopMap.get(player.getUniqueId());
        
    //     // Si aucune info dans openShopMap, essayer lastShopMap
    //     if (shopInfo == null) {
    //         shopInfo = lastShopMap.get(player.getUniqueId());
    //         // plugin.getLogger().info("Using lastShopMap for player " + player.getName() + ": " + shopInfo);
    //     }
        
    //     if (shopInfo == null) {
    //         // plugin.getLogger().warning("No shop info found for player: " + player.getName());
    //         return null;
    //     }
        
    //     return new AmountSelectionInfo(
    //         shopInfo.getKey(),         // shopId
    //         shopInfo.getValue(),       // itemId
    //         centerItem,                // itemStack
    //         isBuying,                   // isBuying
    //         menuType,                  // menuType
    //         slotValues                 // slotValues - La map des slots et valeurs
    //     );
    // }

    // private void startContinuousAmountSelectionRefresh(Player player, InventoryView view, AmountSelectionInfo info, 
    //                                              Map<Integer, List<String>> originalLores) {
    //     // ID unique pour cette session de refresh
    //     final UUID refreshId = UUID.randomUUID();
        
    //     // Stocker l'ID du refresh dans une map pour pouvoir l'arrêter plus tard
    //     playerRefreshTasks.put(player.getUniqueId(), refreshId);
        
    //     // Démarrer la tâche asynchrone avec boucle
    //     plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
    //         try {
    //             // Temps d'attente entre les actualisations (en ms)
    //             long refreshInterval = 1000; // 1 seconde

    //             while (
    //                 player.isOnline() && 
    //                 player.getOpenInventory() != null && 
    //                 determineShopId(player.getOpenInventory()) != null &&
    //                 determineShopId(player.getOpenInventory()).equals("AMOUNT_SELECTION") &&
    //                 playerRefreshTasks.get(player.getUniqueId()) == refreshId
    //             ) {
    //                 // Attendre l'intervalle configuré
    //                 Thread.sleep(refreshInterval);
                    
    //                 // Vérifier à nouveau que le joueur est toujours en ligne et que l'inventaire est ouvert
    //                 if (!player.isOnline() || player.getOpenInventory() == null) {
    //                     break;
    //                 }
                    
    //                 // Effectuer la mise à jour sur le thread principal
    //                 plugin.getServer().getScheduler().runTask(plugin, () -> {
    //                     if (player.isOnline() && player.getOpenInventory() != null) {
    //                         updateAmountSelectionInventory(player, view, info, originalLores);
    //                     }
    //                 });
    //             }
    //         } catch (InterruptedException e) {
    //             Thread.currentThread().interrupt();
    //         } finally {
    //             // Nettoyer
    //             playerRefreshTasks.remove(player.getUniqueId());
    //             amountSelectionMenus.remove(player.getUniqueId());
    //         }
    //     });
    // }

    private void startContinuousAmountSelectionRefresh(Player player, InventoryView view, AmountSelectionInfo info, Map<Integer, List<String>> originalLores) {
        startContinuousAmountSelectionRefresh(player, view, info, originalLores, info.getMenuType());
    }

    // private void startContinuousAmountSelectionRefresh(Player player, InventoryView view, AmountSelectionInfo info, Map<Integer, List<String>> originalLores, String menuType) {
    //     // ID unique pour cette session de refresh
    //     final UUID refreshId = UUID.randomUUID();
        
    //     // Stocker l'ID du refresh dans une map pour pouvoir l'arrêter plus tard
    //     playerRefreshTasks.put(player.getUniqueId(), refreshId);
        
    //     // Variables pour suivre les changements
    //     final int[] lastCenterQuantity = {info.getItemStack().getAmount()};
    //     // DynaShopPlugin.getInstance().getLogger().info("Starting continuous refresh for player: " + player.getName() + 
    //     //     ", Shop ID: " + info.getShopId() + ", Item ID: " + info.getItemId() + 
    //     //     ", Initial quantity: " + lastCenterQuantity[0]);
        
    //     // Vérifier si l'item est critique pour déterminer la fréquence de rafraîchissement
    //     boolean isCriticalItem = plugin.getConfigMain().getStringList("critical-items").contains(info.getShopId() + ":" + info.getItemId());
    //     final long refreshInterval = isCriticalItem ? CRITICAL_REFRESH_INTERVAL : DEFAULT_REFRESH_INTERVAL;
        
    //     // Démarrer la tâche asynchrone avec boucle
    //     plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
    //         try {
    //             // // Temps d'attente entre les vérifications (en ms)
    //             // long refreshInterval = 100; // 100ms pour une détection rapide

    //             // Flag pour suivre si le joueur est toujours dans le menu de sélection
    //             boolean stillInMenu = true;

    //             while (stillInMenu &&
    //                 player.isOnline() && 
    //                 player.getOpenInventory() != null &&
    //                 determineShopId(player.getOpenInventory()) != null &&
    //                 determineShopId(player.getOpenInventory()).equals(menuType)) {
    //                 // playerRefreshTasks.get(player.getUniqueId()) == refreshId) {

    //                 // Attendre l'intervalle configuré
    //                 Thread.sleep(refreshInterval);
                    
    //                 // Vérifier à nouveau que le joueur est toujours en ligne et que l'inventaire est ouvert
    //                 if (!player.isOnline() || player.getOpenInventory() == null) {
    //                     stillInMenu = false;
    //                     break;
    //                 }
                    
    //                 // Vérifier si le menu ouvert est toujours un menu de sélection
    //                 // String currentTitle = player.getOpenInventory().getTitle();
    //                 String currentMenuType = determineShopId(player.getOpenInventory());
    //                 if (currentMenuType == null || !currentMenuType.equals(menuType)) {
    //                     // DynaShopPlugin.getInstance().getLogger().info("Player " + player.getName() + " no longer in amount selection menu: " + currentTitle);
    //                     stillInMenu = false;
    //                     break;
    //                 }
                    
    //                 // // Vérifier si l'item central a changé (sur le thread principal)
    //                 // plugin.getServer().getScheduler().runTask(plugin, () -> {
    //                 //     // if (!player.isOnline() || player.getOpenInventory() == null) return;
    //                 //     // if (player.isOnline() && player.getOpenInventory() != null) {
    //                 //     // int centerSlot;

    //                 //     if (menuType.equals("AMOUNT_SELECTION_BULK")) {
    //                 //         updateAmountSelectionInventoryImmediately(player, player.getOpenInventory(), info, originalLores);
    //                 //     } else {
    //                 //         // Pour les menus standard, utiliser la valeur configurée ou 22 par défaut
    //                 //         int centerSlot = ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getInt("amountSelectionGUI.itemSlot", 22);
    //                 //         ItemStack currentCenterItem = player.getOpenInventory().getTopInventory().getItem(centerSlot);
    //                 //         if (currentCenterItem != null) {
    //                 //             // Vérifier si la quantité a changé
    //                 //             int currentQuantity = currentCenterItem.getAmount();
    //                 //             if (currentQuantity != lastCenterQuantity[0]) {
    //                 //                 // La quantité a changé, mettre à jour les prix dans l'interface
    //                 //                 lastCenterQuantity[0] = currentQuantity;
                                    
    //                 //                 // Mettre à jour l'ItemStack dans l'info avec la nouvelle quantité
    //                 //                 AmountSelectionInfo newInfo = new AmountSelectionInfo(
    //                 //                     info.getShopId(),
    //                 //                     info.getItemId(),
    //                 //                     currentCenterItem, // avec la nouvelle quantité
    //                 //                     info.isBuying(),
    //                 //                     menuType,
    //                 //                     info.getSlotValues() // conserver les valeurs de slot
    //                 //                 );
                                    
    //                 //                 // Actualiser immédiatement les prix dans tous les boutons
    //                 //                 updateAmountSelectionInventoryImmediately(player, player.getOpenInventory(), newInfo, originalLores);
    //                 //             }
    //                 //         }
    //                 //     }
    //                 // });
    //                 // Effectuer la mise à jour sur le thread principal
    //                 plugin.getServer().getScheduler().runTask(plugin, () -> {
    //                     if (player.isOnline() && player.getOpenInventory() != null) {
    //                         // Vérifier si la quantité a changé avant de mettre à jour l'interface pour éviter des actualisations inutiles
    //                         if (menuType.equals("AMOUNT_SELECTION")) {
    //                             int centerSlot = ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getInt("amountSelectionGUI.itemSlot", 22);
    //                             ItemStack currentItem = player.getOpenInventory().getTopInventory().getItem(centerSlot);
                                
    //                             if (currentItem != null && currentItem.getAmount() != lastCenterQuantity[0]) {
    //                                 lastCenterQuantity[0] = currentItem.getAmount();
    //                                 // Mettre à jour l'ItemStack dans l'info avec la nouvelle quantité
    //                                 AmountSelectionInfo newInfo = new AmountSelectionInfo(info.getShopId(), info.getItemId(), currentItem, info.isBuying(), menuType, info.getSlotValues());
    //                                 updateAmountSelectionInventoryImmediately(player, player.getOpenInventory(), newInfo, originalLores);
    //                             }
    //                         } else {
    //                             // Pour les menus bulk, mettre à jour moins fréquemment car les valeurs sont fixes
    //                             updateAmountSelectionInventoryImmediately(player, player.getOpenInventory(), info, originalLores);
    //                         }
    //                     }
    //                 });
    //             }
    //         } catch (InterruptedException e) {
    //             // DynaShopPlugin.getInstance().getLogger().warning("Continuous refresh interrupted for player: " + player.getName() + " - " + e.getMessage());
    //             Thread.currentThread().interrupt();
    //         } finally {
    //             // DynaShopPlugin.getInstance().getLogger().info("Stopping continuous refresh for player: " + player.getName());
    //             // Nettoyer
    //             playerRefreshTasks.remove(player.getUniqueId());
    //             // amountSelectionMenus.remove(player.getUniqueId());
    //         }
    //     });
    // }
    private void startContinuousAmountSelectionRefresh(Player player, InventoryView view, AmountSelectionInfo info, Map<Integer, List<String>> originalLores, String menuType) {
        // ID unique pour cette session de refresh
        final UUID refreshId = UUID.randomUUID();
        
        // Stocker l'ID du refresh dans une map pour pouvoir l'arrêter plus tard
        playerRefreshTasks.put(player.getUniqueId(), refreshId);
        
        // Variables pour suivre les changements de quantité
        final int[] lastCenterQuantity = {info.getItemStack().getAmount()};
        
        // Vérifier si l'item est critique pour déterminer la fréquence de rafraîchissement
        boolean isCriticalItem = plugin.getConfigMain().getStringList("critical-items").contains(info.getShopId() + ":" + info.getItemId());
        final long refreshInterval = isCriticalItem ? CRITICAL_REFRESH_INTERVAL : DEFAULT_REFRESH_INTERVAL;
        
        // Démarrer la tâche asynchrone avec boucle
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Flag pour suivre si le joueur est toujours dans le menu de sélection
                boolean stillInMenu = true;

                while (
                    stillInMenu &&
                    player.isOnline() && 
                    player.getOpenInventory() != null &&
                    determineShopId(player.getOpenInventory()) != null &&
                    determineShopId(player.getOpenInventory()).equals(menuType)
                    // playerRefreshTasks.get(player.getUniqueId()) == refreshId
                ) {
                    // Attendre l'intervalle configuré
                    Thread.sleep(refreshInterval);
                    
                    // Vérifier si le joueur est toujours en ligne et que l'inventaire est ouvert
                    if (!player.isOnline() || player.getOpenInventory() == null) {
                        stillInMenu = false;
                        break;
                    }
                    
                    // Vérifier si le menu ouvert est toujours un menu de sélection
                    String currentMenuType = determineShopId(player.getOpenInventory());
                    if (currentMenuType == null || !currentMenuType.equals(menuType)) {
                        stillInMenu = false;
                        break;
                    }
                    
                    // Effectuer la mise à jour sur le thread principal
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (player.isOnline() && player.getOpenInventory() != null) {
                            // Pour les menus standards, vérifier si la quantité a changé
                            if (menuType.equals("AMOUNT_SELECTION")) {
                                int centerSlot = ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getInt("amountSelectionGUI.itemSlot", 22);
                                ItemStack currentItem = player.getOpenInventory().getTopInventory().getItem(centerSlot);
                                
                                if (currentItem != null && currentItem.getAmount() != lastCenterQuantity[0]) {
                                    // Mettre à jour la quantité de référence
                                    lastCenterQuantity[0] = currentItem.getAmount();

                                    
                                    // ÉTAPE 1: Masquer immédiatement tous les placeholders visibles
                                    for (int slot = 0; slot < view.getTopInventory().getSize(); slot++) {
                                        ItemStack button = view.getTopInventory().getItem(slot);
                                        if (button == null || !button.hasItemMeta() || !button.getItemMeta().hasLore()) {
                                            continue;
                                        }
                                        
                                        ItemMeta meta = button.getItemMeta();
                                        List<String> currentLore = meta.getLore();
                                        
                                        // Vérifier si le lore contient des placeholders DynaShop
                                        if (containsDynaShopPlaceholder(currentLore)) {
                                            // Pré-remplacer immédiatement pour masquer les placeholders bruts
                                            List<String> tempLore = preProcessPlaceholders(currentLore);
                                            meta.setLore(tempLore);
                                            button.setItemMeta(meta);
                                        }
                                    }
                                    
                                    // Forcer une mise à jour immédiate pour masquer les placeholders
                                    player.updateInventory();
                                    
                                    // Mettre à jour l'interface avec la nouvelle quantité
                                    AmountSelectionInfo newInfo = new AmountSelectionInfo(
                                        info.getShopId(), 
                                        info.getItemId(), 
                                        currentItem, 
                                        info.isBuying(), 
                                        menuType, 
                                        info.getSlotValues()
                                    );
                                    
                                    updateAmountSelectionInventory(player, player.getOpenInventory(), newInfo, originalLores);
                                }
                            } else {
                                // Pour les menus BULK, mettre à jour périodiquement
                                updateAmountSelectionInventory(player, player.getOpenInventory(), info, originalLores);
                            }
                        }
                    });
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                // Nettoyer
                playerRefreshTasks.remove(player.getUniqueId());
            }
        });
    }

    // /**
    //  * Mise à jour immédiate de l'inventaire avec la nouvelle quantité
    //  */
    // private void updateAmountSelectionInventoryImmediately(Player player, InventoryView view, AmountSelectionInfo info, Map<Integer, List<String>> originalLores) {
    //     if (view == null || view.getTopInventory() == null) return;
        
    //     try {
    //         // ÉTAPE 1: Masquer immédiatement tous les placeholders visibles
    //         for (int slot = 0; slot < view.getTopInventory().getSize(); slot++) {
    //             ItemStack button = view.getTopInventory().getItem(slot);
    //             if (button == null || !button.hasItemMeta() || !button.getItemMeta().hasLore()) {
    //                 continue;
    //             }
                
    //             ItemMeta meta = button.getItemMeta();
    //             List<String> currentLore = meta.getLore();
                
    //             // Vérifier si le lore contient des placeholders DynaShop
    //             if (containsDynaShopPlaceholder(currentLore)) {
    //                 // Pré-remplacer immédiatement pour masquer les placeholders bruts
    //                 List<String> tempLore = preProcessPlaceholders(currentLore);
    //                 meta.setLore(tempLore);
    //                 button.setItemMeta(meta);
    //             }
    //         }
            
    //         // // Forcer une mise à jour immédiate pour masquer les placeholders
    //         // player.updateInventory();

    //         // // Obtenir les prix de base pour l'item
    //         // Map<String, String> basePrices = getCachedPrices(
    //         //     player, 
    //         //     info.getShopId(), 
    //         //     info.getItemId(), 
    //         //     info.getItemStack(),
    //         //     true // forceRefresh pour avoir les prix les plus récents
    //         // );
            
    //         // Parcourir tous les boutons de l'inventaire
    //         for (int slot = 0; slot < view.getTopInventory().getSize(); slot++) {
    //             ItemStack button = view.getTopInventory().getItem(slot);
    //             if (button == null || !button.hasItemMeta() || !button.getItemMeta().hasLore()) { continue; }
                
    //             // // Récupérer la quantité du bouton
    //             // int quantity = button.getAmount();

    //             // Déterminer la quantité à utiliser pour ce slot spécifique
    //             int quantity;
                
    //             if (info.getMenuType().equals("AMOUNT_SELECTION_BULK")) {
    //                 // Pour les menus BULK, utiliser la valeur de stack à partir de la map
    //                 int stackValue = info.getValueForSlot(slot);
    //                 // DynaShopPlugin.getInstance().getLogger().info("Using stack value for slot " + slot + ": " + stackValue);
    //                 // quantity = stackValue * 64; // Multiplier par 64 pour obtenir le nombre total d'items
    //                 quantity = stackValue * button.getMaxStackSize(); // Utiliser la taille maximale de stack pour le calcul
    //                 // DynaShopPlugin.getInstance().getLogger().info("Calculated quantity for slot " + slot + ": " + quantity);
    //             } else {
    //                 // Pour les menus standard, utiliser la quantité affichée sur l'item central
    //                 quantity = info.getItemStack().getAmount();
    //             }
                
    //             // Récupérer le lore original si disponible, sinon utiliser le lore actuel
    //             List<String> originalLore = originalLores.containsKey(slot) ? 
    //                                     originalLores.get(slot) : 
    //                                     button.getItemMeta().getLore();
                
    //             if (originalLore == null || originalLore.isEmpty() || !containsDynaShopPlaceholder(originalLore)) {
    //                 continue;
    //             }
                
    //             // Créer une copie des prix pour cet item spécifique avec cette quantité
    //             // Map<String, String> adjustedPrices = adjustPricesForQuantity(basePrices, quantity);
    //             Map<String, String> prices = getCachedPrices(
    //                 player, 
    //                 info.getShopId(), 
    //                 info.getItemId(), 
    //                 info.getItemStack(),
    //                 quantity, // Utiliser la quantité pour ajuster les prix
    //                 true // forceRefresh pour avoir les prix les plus récents
    //             );
                
    //             // Appliquer les remplacements
    //             List<String> newLore = replacePlaceholders(originalLore, prices, player);
    //             ItemMeta meta = button.getItemMeta();
    //             meta.setLore(newLore);
    //             button.setItemMeta(meta);
    //         }
            
    //         // Forcer la mise à jour de l'inventaire pour le joueur
    //         player.updateInventory();
            
    //     } catch (Exception e) {
    //         plugin.getLogger().warning("Error updating amount selection inventory: " + e.getMessage());
    //     }
    // }

    // private void updateAmountSelectionInventory(Player player, InventoryView view, AmountSelectionInfo info, Map<Integer, List<String>> originalLores) {
    //     if (view == null || view.getTopInventory() == null) { return; }

    //     try {
    //         // Cache global des prix pour cet item
    //         final Map<String, String> basePrices = getCachedPrices(
    //             player, 
    //             info.getShopId(), 
    //             info.getItemId(), 
    //             info.getItemStack(), 
    //             true // forceRefresh
    //         );
            
    //         // Traiter chaque bouton individuellement
    //         plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
    //             for (int slot = 0; slot < view.getTopInventory().getSize(); slot++) {
    //                 final int currentSlot = slot;
                    
    //                 // Ne traiter que les slots qui avaient des placeholders originaux
    //                 if (!originalLores.containsKey(currentSlot)) {
    //                     continue;
    //                 }
                    
    //                 plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
    //                     try {
    //                         ItemStack button = view.getTopInventory().getItem(currentSlot);
    //                         if (button == null || !button.hasItemMeta() || !button.getItemMeta().hasLore()) {
    //                             return;
    //                         }
                            
    //                         // Récupérer la quantité associée au bouton
    //                         int quantity = button.getAmount();
                            
    //                         // Créer une copie des prix pour cet item spécifique avec cette quantité
    //                         Map<String, String> adjustedPrices = adjustPricesForQuantity(basePrices, quantity);

    //                         // Utiliser le lore original pour la détection des placeholders
    //                         List<String> originalLore = originalLores.get(currentSlot);
    //                         if (originalLore == null || originalLore.isEmpty()) {
    //                             return;
    //                         }
                            
    //                         // Appliquer les remplacements
    //                         List<String> newLore = replacePlaceholders(originalLore, adjustedPrices, player);
    //                         ItemMeta meta = button.getItemMeta();
    //                         meta.setLore(newLore);
    //                         button.setItemMeta(meta);
                            
    //                         // Mettre à jour l'item dans l'inventaire immédiatement
    //                         final ItemStack finalButton = button.clone();
                            
    //                         plugin.getServer().getScheduler().runTask(plugin, () -> {
    //                             try {
    //                                 // Vérifier que l'inventaire est toujours ouvert avant de le mettre à jour
    //                                 if (player.isOnline() && player.getOpenInventory().equals(view)) {
    //                                     view.getTopInventory().setItem(currentSlot, finalButton);
    //                                     player.updateInventory();
    //                                 }
    //                             } catch (Exception e) {
    //                                 // Ignorer les erreurs lors de la mise à jour
    //                             }
    //                         });
                            
    //                     } catch (Exception e) {
    //                         // Capturer toute exception pour éviter d'interrompre la tâche
    //                     }
    //                 });
    //             }
    //         });
    //     } catch (Exception e) {
    //         plugin.getLogger().warning("Error updating amount selection inventory: " + e.getMessage());
    //     }
    // }
    private void updateAmountSelectionInventory(Player player, InventoryView view, AmountSelectionInfo info, Map<Integer, List<String>> originalLores) {
        if (view == null || view.getTopInventory() == null) { return; }

        try {
            // // Cache global des prix pour cet item
            // final Map<String, String> basePrices = getCachedPrices(
            //     player, 
            //     info.getShopId(), 
            //     info.getItemId(), 
            //     info.getItemStack(), 
            //     true // forceRefresh
            // );
            
            // // Cache global des prix
            // Map<String, Map<String, String>> priceCache = new HashMap<>();
            
            // Identifier les slots qui ont besoin d'être mis à jour
            List<Integer> slotsToUpdate = new ArrayList<>(originalLores.keySet());
            
            // // Ajouter des logs de débogage pour BULK
            // if (info.getMenuType().equals("AMOUNT_SELECTION_BULK")) {
            //     plugin.getLogger().info("BULK menu detected for player " + player.getName());
            //     plugin.getLogger().info("SlotValues map: " + info.getSlotValues());
            // }
            
            // Traiter les slots par lots pour éviter de surcharger le serveur
            final int BATCH_SIZE = 5; // Nombre de slots à traiter par lot
            
            for (int i = 0; i < slotsToUpdate.size(); i += BATCH_SIZE) {
                final int startIdx = i;
                final int endIdx = Math.min(i + BATCH_SIZE, slotsToUpdate.size());
                
                // Traiter chaque lot dans une tâche asynchrone
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    Map<Integer, ItemStack> updatedItems = new HashMap<>();
                    
                    for (int j = startIdx; j < endIdx; j++) {
                        int slot = slotsToUpdate.get(j);
                        
                        try {
                            ItemStack button = view.getTopInventory().getItem(slot);
                            if (button == null || !button.hasItemMeta() || !button.getItemMeta().hasLore()) { continue; }

                            // Déterminer la quantité pour ce slot
                            int quantity;
                            if (info.getMenuType().equals("AMOUNT_SELECTION_BULK")) {
                                int stackValue = info.getValueForSlot(slot);
                                quantity = stackValue * button.getMaxStackSize();
                            } else {
                                quantity = info.getItemStack().getAmount();
                            }
                            // DynaShopPlugin.getInstance().getLogger().info("Processing slot " + slot + " with quantity: " + quantity);
                            // if (info.getMenuType().equals("AMOUNT_SELECTION_BULK")) {
                            //     // CORRECTION: Vérifier si ce slot existe dans la map des valeurs
                            //     if (info.getSlotValues().containsKey(slot)) {
                            //         int stackValue = info.getSlotValues().get(slot);
                            //         quantity = stackValue;  // Utiliser directement la valeur configurée
                            //         plugin.getLogger().info("Slot " + slot + " has stack value: " + stackValue + ", quantity: " + quantity);
                            //     } else {
                            //         // Si le slot n'a pas de valeur associée, utiliser 1 par défaut
                            //         quantity = 1;
                            //         plugin.getLogger().info("Slot " + slot + " not found in slotValues map, using default quantity: " + quantity);
                            //     }
                            // } else {
                            //     quantity = info.getItemStack().getAmount();
                            // }
                            
                            // Récupérer le lore original
                            List<String> originalLore = originalLores.get(slot);
                            if (originalLore == null || originalLore.isEmpty() || !containsDynaShopPlaceholder(originalLore)) {
                                continue;
                            }
                            
                            // Ajuster les prix pour cette quantité
                            // Map<String, String> adjustedPrices = adjustPricesForQuantity(basePrices, quantity);
                            // Map<String, String> itemPrices;
                            // if (priceCache.containsKey(info.getItemId())) {
                            //     itemPrices = priceCache.get(info.getItemId());
                            // } else {
                            //     // Calcul des prix pour cet item
                            //     itemPrices = getCachedPrices(player, info.getShopId(), info.getItemId(), info.getItemStack(), quantity, true);
                            //     priceCache.put(info.getItemId(), itemPrices);
                            // }
                            Map<String, String> prices = getCachedPrices(
                                player, 
                                info.getShopId(), 
                                info.getItemId(), 
                                info.getItemStack(), 
                                quantity,
                                true // forceRefresh
                            );
                            
                            // Appliquer les remplacements
                            List<String> newLore = replacePlaceholders(originalLore, prices, player);
                            // List<String> newLore = replacePlaceholders(originalLore, adjustedPrices, player);
                            ItemMeta meta = button.getItemMeta();
                            meta.setLore(newLore);
                            button.setItemMeta(meta);
                            
                            // Ajouter l'item mis à jour pour traitement groupé
                            updatedItems.put(slot, button.clone());
                        } catch (Exception e) {
                            // // Capturer les erreurs individuelles
                            // plugin.getLogger().warning("Error processing slot " + slot + ": " + e.getMessage());
                            // e.printStackTrace();
                        }
                    }
                    
                    // Mettre à jour tous les items du lot en une seule fois sur le thread principal
                    if (!updatedItems.isEmpty()) {
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            if (player.isOnline() && player.getOpenInventory().equals(view)) {
                                for (Map.Entry<Integer, ItemStack> entry : updatedItems.entrySet()) {
                                    view.getTopInventory().setItem(entry.getKey(), entry.getValue());
                                }
                                player.updateInventory(); // Une seule mise à jour pour tout le lot
                            }
                        });
                    }
                });
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error updating amount selection inventory: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // /**
    //  * Ajuste les prix en fonction de la quantité
    //  * @param basePrices Prix de base (unitaires)
    //  * @param quantity Quantité d'items
    //  * @return Prix ajustés pour la quantité spécifiée
    //  */
    // private Map<String, String> adjustPricesForQuantity(Map<String, String> basePrices, int quantity) {
    //     if (quantity <= 1) {
    //         // Pour la quantité 1, pas besoin d'ajuster
    //         return new HashMap<>(basePrices);
    //     }
        
    //     Map<String, String> adjustedPrices = new HashMap<>();
    //     String currencyPrefix = "";
    //     String currencySuffix = " $";
        
    //     // Récupérer les préfixes et suffixes de monnaie
    //     if (basePrices.get("base_buy") != null) {
    //         String baseBuy = basePrices.get("base_buy");
    //         if (baseBuy.contains("$")) {
    //             int dollarIndex = baseBuy.indexOf("$");
    //             // Chercher le préfixe (tout avant le premier chiffre)
    //             for (int i = 0; i < dollarIndex; i++) {
    //                 if (!Character.isDigit(baseBuy.charAt(i)) && baseBuy.charAt(i) != '.') {
    //                     currencyPrefix += baseBuy.charAt(i);
    //                 } else {
    //                     break;
    //                 }
    //             }
    //             // Le suffixe est " $" ou similaire
    //             currencySuffix = baseBuy.substring(baseBuy.indexOf("$"));
    //             if (currencySuffix.contains(" ")) {
    //                 currencySuffix = currencySuffix.substring(0, currencySuffix.indexOf(" ") + 1) + "$";
    //             }
    //         }
    //     }
        
    //     // Copier les valeurs non-numériques telles quelles
    //     for (Map.Entry<String, String> entry : basePrices.entrySet()) {
    //         if (!isNumericValue(entry.getKey())) {
    //             adjustedPrices.put(entry.getKey(), entry.getValue());
    //         }
    //     }
        
    //     // Ajuster les prix numériques en multipliant par la quantité
    //     try {
    //         // Prix d'achat
    //         if (isValidNumeric(basePrices.get("buy"))) {
    //             double buyPrice = parseFormattedNumber(basePrices.get("buy"));
    //             double totalBuyPrice = buyPrice * quantity;
    //             if (totalBuyPrice < 0) {
    //                 totalBuyPrice = Math.abs(totalBuyPrice);
    //             }
    //             adjustedPrices.put("buy", plugin.getPriceFormatter().formatPrice(totalBuyPrice));
    //         } else {
    //             adjustedPrices.put("buy", basePrices.get("buy"));
    //         }
            
    //         // Prix de vente
    //         if (isValidNumeric(basePrices.get("sell"))) {
    //             double sellPrice = parseFormattedNumber(basePrices.get("sell"));
    //             double totalSellPrice = sellPrice * quantity;
    //             if (totalSellPrice < 0) {
    //                 totalSellPrice = Math.abs(totalSellPrice);
    //             }
    //             adjustedPrices.put("sell", plugin.getPriceFormatter().formatPrice(totalSellPrice));
    //         } else {
    //             adjustedPrices.put("sell", basePrices.get("sell"));
    //         }
            
    //         // Prix min/max d'achat
    //         if (isValidNumeric(basePrices.get("buy_min"))) {
    //             double minBuyPrice = parseFormattedNumber(basePrices.get("buy_min"));
    //             double totalMinBuyPrice = minBuyPrice * quantity;
    //             adjustedPrices.put("buy_min", plugin.getPriceFormatter().formatPrice(totalMinBuyPrice));
    //         } else {
    //             adjustedPrices.put("buy_min", basePrices.get("buy_min"));
    //         }
            
    //         if (isValidNumeric(basePrices.get("buy_max"))) {
    //             double maxBuyPrice = parseFormattedNumber(basePrices.get("buy_max"));
    //             double totalMaxBuyPrice = maxBuyPrice * quantity;
    //             adjustedPrices.put("buy_max", plugin.getPriceFormatter().formatPrice(totalMaxBuyPrice));
    //         } else {
    //             adjustedPrices.put("buy_max", basePrices.get("buy_max"));
    //         }
            
    //         // Prix min/max de vente
    //         if (isValidNumeric(basePrices.get("sell_min"))) {
    //             double minSellPrice = parseFormattedNumber(basePrices.get("sell_min"));
    //             double totalMinSellPrice = minSellPrice * quantity;
    //             adjustedPrices.put("sell_min", plugin.getPriceFormatter().formatPrice(totalMinSellPrice));
    //         } else {
    //             adjustedPrices.put("sell_min", basePrices.get("sell_min"));
    //         }
            
    //         if (isValidNumeric(basePrices.get("sell_max"))) {
    //             double maxSellPrice = parseFormattedNumber(basePrices.get("sell_max"));
    //             double totalMaxSellPrice = maxSellPrice * quantity;
    //             adjustedPrices.put("sell_max", plugin.getPriceFormatter().formatPrice(totalMaxSellPrice));
    //         } else {
    //             adjustedPrices.put("sell_max", basePrices.get("sell_max"));
    //         }
            
    //         // Ajuster les formats de présentation
    //         if (adjustedPrices.containsKey("buy") && adjustedPrices.containsKey("buy_min") && adjustedPrices.containsKey("buy_max") &&
    //             !adjustedPrices.get("buy").equals("N/A") && !adjustedPrices.get("buy_min").equals("N/A") && !adjustedPrices.get("buy_max").equals("N/A") &&
    //             (!adjustedPrices.get("buy_min").equals(adjustedPrices.get("buy")) || !adjustedPrices.get("buy_max").equals(adjustedPrices.get("buy")))) {
    //             adjustedPrices.put("base_buy", String.format(
    //                 currencyPrefix + "%s" + currencySuffix + " §7(%s - %s) ", 
    //                 adjustedPrices.get("buy"), adjustedPrices.get("buy_min"), adjustedPrices.get("buy_max")
    //             ));
    //         } else if (adjustedPrices.containsKey("buy")) {
    //             adjustedPrices.put("base_buy", currencyPrefix + adjustedPrices.get("buy") + currencySuffix);
    //         }
            
    //         if (adjustedPrices.containsKey("sell") && adjustedPrices.containsKey("sell_min") && adjustedPrices.containsKey("sell_max") &&
    //             !adjustedPrices.get("sell").equals("N/A") && !adjustedPrices.get("sell_min").equals("N/A") && !adjustedPrices.get("sell_max").equals("N/A") &&
    //             (!adjustedPrices.get("sell_min").equals(adjustedPrices.get("sell")) || !adjustedPrices.get("sell_max").equals(adjustedPrices.get("sell")))) {
    //             adjustedPrices.put("base_sell", String.format(
    //                 currencyPrefix + "%s" + currencySuffix + " §7(%s - %s) ", 
    //                 adjustedPrices.get("sell"), adjustedPrices.get("sell_min"), adjustedPrices.get("sell_max")
    //             ));
    //         } else if (adjustedPrices.containsKey("sell")) {
    //             adjustedPrices.put("base_sell", currencyPrefix + adjustedPrices.get("sell") + currencySuffix);
    //         }
    //     } catch (Exception e) {
    //         plugin.getLogger().warning("Error adjusting prices for quantity " + quantity + ": " + e.getMessage());
    //         return basePrices; // En cas d'erreur, retourner les prix de base
    //     }
        
    //     return adjustedPrices;
    // }

    // /**
    //  * Vérifie si la clé correspond à une valeur numérique
    //  */
    // private boolean isNumericValue(String key) {
    //     return key.equals("buy") || key.equals("sell") || 
    //         key.equals("buy_min") || key.equals("buy_max") || 
    //         key.equals("sell_min") || key.equals("sell_max");
    // }

    // /**
    //  * Vérifie si une chaîne de caractères représente un nombre valide
    //  */
    // private boolean isValidNumeric(String value) {
    //     // return value != null && !value.equals("N/A") && !value.equals("-1");
    //     if (value == null || value.equals("N/A") || value.equals("-1")) {
    //         return false;
    //     }
        
    //     // Vérifier si la chaîne contient au moins un chiffre
    //     boolean hasDigit = false;
    //     for (char c : value.toCharArray()) {
    //         if (Character.isDigit(c)) {
    //             hasDigit = true;
    //             break;
    //         }
    //     }
        
    //     return hasDigit;
    // }

    /**
     * Convertit une chaîne formatée (ex: "1,234.56") en nombre
     */
    private double parseFormattedNumber(String formatted) {
        if (formatted == null || formatted.equals("N/A") || formatted.isEmpty()) {
            return 0.0;
        }
        
        try {
            // // Supprimer tous les caractères non numériques sauf le point décimal
            // String cleaned = "";
            // boolean hasDecimal = false;
            
            // for (char c : formatted.toCharArray()) {
            //     if (Character.isDigit(c)) {
            //         cleaned += c;
            //     } else if (c == '.' && !hasDecimal) {
            //         cleaned += c;
            //         hasDecimal = true;
            //     }
            // }
            // // Supprimer les préfixes de devise (comme "$" ou "€")
            // String cleaned = formatted.replaceAll("[^0-9.,\\-]", "");
            
            // // Gérer le format standard des nombres (avec virgules comme séparateurs de milliers)
            // cleaned = cleaned.replace(",", "");
            
            // // Si la chaîne est vide après nettoyage, retourner 0
            // if (cleaned.isEmpty() || cleaned.equals("-")) {
            //     return 0.0;
            // }
            
            // return Double.parseDouble(cleaned);
            
            // Approche plus efficace avec StringBuilder
            StringBuilder cleaned = new StringBuilder(formatted.length());
            boolean hasDecimal = false;
            
            for (int i = 0; i < formatted.length(); i++) {
                char c = formatted.charAt(i);
                if (Character.isDigit(c)) {
                    cleaned.append(c);
                } else if ((c == '.' || c == ',') && !hasDecimal) {
                    cleaned.append('.');
                    hasDecimal = true;
                } else if (c == '-' && cleaned.length() == 0) {
                    cleaned.append(c);
                }
            }
            
            return cleaned.length() > 0 ? Double.parseDouble(cleaned.toString()) : 0.0;
        } catch (NumberFormatException e) {
            // plugin.getLogger().warning("Could not parse number from: " + formatted);
            return 0.0;
        }
    }

    public void shutdown() {
        // if (refreshTask != null) {
        //     refreshTask.cancel();
        //     refreshTask = null;
        // }
        // // openInventories.clear();
        // openShopMap.clear();
        // Annuler toutes les tâches de rafraîchissement
        playerRefreshTasks.clear();
        openShopMap.clear();
        amountSelectionMenus.clear();
    }
}