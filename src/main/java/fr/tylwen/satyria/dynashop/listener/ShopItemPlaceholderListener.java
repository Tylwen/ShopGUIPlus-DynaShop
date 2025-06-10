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
import org.bukkit.scheduler.BukkitTask;
// import org.bukkit.scheduler.BukkitTask;
import org.bukkit.inventory.InventoryView;

import fr.tylwen.satyria.dynashop.DynaShopPlugin;
import fr.tylwen.satyria.dynashop.data.param.DynaShopType;
import fr.tylwen.satyria.dynashop.price.DynamicPrice;
import fr.tylwen.satyria.dynashop.system.TransactionLimiter.LimitPeriod;
import fr.tylwen.satyria.dynashop.system.TransactionLimiter.TransactionLimit;
import me.clip.placeholderapi.PlaceholderAPI;
import net.brcdev.shopgui.shop.Shop;
import net.brcdev.shopgui.shop.item.ShopItem;
import net.brcdev.shopgui.shop.item.ShopItemType;
import net.brcdev.shopgui.ShopGuiPlusApi;
import net.brcdev.shopgui.exception.player.PlayerDataNotLoadedException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
// import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ShopItemPlaceholderListener implements Listener {

    // Constantes
    private static final int CLEANUP_INTERVAL = 5 * 60 * 20; // 5 minutes en ticks
    private static final int BATCH_SIZE = 5; // Nombre de slots à traiter par lot
    
    // Variables de configuration
    private final DynaShopPlugin plugin;
    private final long guiRefreshDefaultItems;
    private final long guiRefreshCriticalItems;
    private final boolean forceRefresh;
    
    // Maps pour le suivi des menus ouverts
    private final Map<UUID, SimpleEntry<String, String>> openShopMap = new ConcurrentHashMap<>();
    private final Map<UUID, AmountSelectionInfo> amountSelectionMenus = new ConcurrentHashMap<>();
    private final Map<UUID, SimpleEntry<String, String>> lastShopMap = new ConcurrentHashMap<>();
    private final Map<UUID, Long> pendingBulkMenuOpens = new ConcurrentHashMap<>();
    
    // Tâches de rafraîchissement
    private final Map<UUID, BukkitTask> playerRefreshBukkitTasks = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> playerSelectionRefreshBukkitTasks = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> playerRefreshTasks = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> playerSelectionRefreshTasks = new ConcurrentHashMap<>();
    
    // Classe interne pour les informations de menu de sélection
    private static class AmountSelectionInfo {
        private final String shopId;
        private final String itemId;
        private final ItemStack itemStack;
        private final boolean isBuying;
        private final String menuType;
        private final Map<Integer, Integer> slotValues;

        public AmountSelectionInfo(String shopId, String itemId, ItemStack itemStack, boolean isBuying, String menuType, Map<Integer, Integer> slotValues) {
            this.shopId = shopId;
            this.itemId = itemId;
            this.itemStack = itemStack.clone();
            this.isBuying = isBuying;
            this.menuType = menuType;
            this.slotValues = slotValues != null ? new HashMap<>(slotValues) : new HashMap<>();
        }

        // Getters
        public String getShopId() { return shopId; }
        public String getItemId() { return itemId; }
        public ItemStack getItemStack() { return itemStack; }
        public boolean isBuying() { return isBuying; }
        public String getMenuType() { return menuType; }
        public Map<Integer, Integer> getSlotValues() { return slotValues; }
        
        public int getValueForSlot(int slot) {
            return slotValues.getOrDefault(slot, 1);
        }
    }
    
    public ShopItemPlaceholderListener(DynaShopPlugin plugin) {
        this.plugin = plugin;
        // this.guiRefreshDefaultItems = plugin.getConfig().getLong("gui-refresh.default-items", 2000); // Augmenté à 2 secondes
        this.guiRefreshDefaultItems = plugin.getConfig().getLong("gui-refresh.default-items", 1000);
        this.guiRefreshCriticalItems = plugin.getConfig().getLong("gui-refresh.critical-items", 300);
        this.forceRefresh = plugin.isRealTimeMode();
        
        // Planifier un nettoyage périodique des maps
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::cleanupMaps, CLEANUP_INTERVAL, CLEANUP_INTERVAL);
    }
    
    /**
     * Nettoie les maps pour éviter les fuites de mémoire
     */
    private void cleanupMaps() {
        Iterator<UUID> iterator = openShopMap.keySet().iterator();
        while (iterator.hasNext()) {
            UUID uuid = iterator.next();
            if (plugin.getServer().getPlayer(uuid) == null) {
                iterator.remove();
                lastShopMap.remove(uuid);
                amountSelectionMenus.remove(uuid);
                playerRefreshTasks.remove(uuid);
                playerSelectionRefreshTasks.remove(uuid);
                
                BukkitTask task = playerRefreshBukkitTasks.remove(uuid);
                if (task != null) task.cancel();
                
                BukkitTask selectionTask = playerSelectionRefreshBukkitTasks.remove(uuid);
                if (selectionTask != null) selectionTask.cancel();
                
                pendingBulkMenuOpens.remove(uuid);
            }
        }
        pendingBulkMenuOpens.clear();
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
        
        // Traitement des menus de sélection
        if (fullShopId.equals("AMOUNT_SELECTION") || fullShopId.equals("AMOUNT_SELECTION_BULK")) {
            SimpleEntry<String, String> shopInfo = openShopMap.get(player.getUniqueId());
            
            if (shopInfo == null) {
                shopInfo = lastShopMap.get(player.getUniqueId());
                if (shopInfo != null) {
                    openShopMap.put(player.getUniqueId(), shopInfo);
                }
            }
            
            AmountSelectionInfo info = extractAmountSelectionInfo(view, fullShopId);
            if (info == null) return;
            
            // Stocker les informations pour ce menu
            amountSelectionMenus.put(player.getUniqueId(), info);
            
            // Pré-traitement pour masquer les placeholders
            Map<Integer, List<String>> originalLores = new HashMap<>();
            for (Map.Entry<Integer, Integer> entry : info.getSlotValues().entrySet()) {
                int slot = entry.getKey();
                ItemStack item = view.getTopInventory().getItem(slot);
                if (item != null && item.hasItemMeta() && item.getItemMeta().hasLore()) {
                    ItemMeta meta = item.getItemMeta();
                    List<String> lore = meta.getLore();
                    
                    originalLores.put(slot, new ArrayList<>(lore));
                    List<String> tempLore = preProcessPlaceholders(lore);
                    meta.setLore(tempLore);
                    item.setItemMeta(meta);
                }
            }
            
            // Mettre à jour les prix dans les boutons
            updateAmountSelectionInventory(player, view, info, originalLores);
            
            // Démarrer l'actualisation continue
            startContinuousAmountSelectionRefresh(player, info, originalLores, fullShopId);
            return;
        } else {
            // C'est un shop normal
            openShopMap.put(player.getUniqueId(), new SimpleEntry<>(fullShopId, null));
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
        
        // Enregistrer le shop ouvert
        openShopMap.put(player.getUniqueId(), new SimpleEntry<>(shopId, null));

        // Stocker les lores originaux
        Map<Integer, List<String>> originalLores = new HashMap<>();
        
        // Pré-traitement pour masquer les placeholders
        for (int slot = 0; slot < view.getTopInventory().getSize(); slot++) {
            ItemStack item = view.getTopInventory().getItem(slot);
            if (item != null && item.hasItemMeta() && item.getItemMeta().hasLore()) {
                ItemMeta meta = item.getItemMeta();
                List<String> lore = meta.getLore();
                
                if (containsDynaShopPlaceholder(lore)) {
                    originalLores.put(slot, new ArrayList<>(lore));
                    List<String> tempLore = preProcessPlaceholders(lore);
                    meta.setLore(tempLore);
                    item.setItemMeta(meta);
                }
            }
        }
        
        updateShopInventory(player, view, shopId, page, originalLores);
        
        // Démarrer l'actualisation continue
        startContinuousRefresh(player, view, shopId, page, originalLores);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        
        // Vérifier si c'est un click dans l'inventaire du haut
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        
        // Récupérer l'item cliqué
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;
        
        int slot = event.getSlot();
        
        try {
            // Vérifier si on est dans un menu de sélection
            String menuType = determineShopId(event.getView());
            if (menuType != null && menuType.equals("AMOUNT_SELECTION")) {
                // Vérifier si c'est un clic sur un bouton d'ajout/retrait de quantité
                if (isQuantityButton(slot)) {
                    int centerSlot = ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getInt("amountSelectionGUI.itemSlot", 22);
                    ItemStack centerItem = event.getView().getTopInventory().getItem(centerSlot);
                    
                    Map<Integer, List<String>> originalLores = extractOriginalLores(event.getView(), player);

                    // Mettre à jour l'interface avec la nouvelle quantité
                    AmountSelectionInfo newInfo = new AmountSelectionInfo(
                        amountSelectionMenus.get(player.getUniqueId()).getShopId(),
                        amountSelectionMenus.get(player.getUniqueId()).getItemId(),
                        centerItem,
                        amountSelectionMenus.get(player.getUniqueId()).isBuying(),
                        menuType,
                        amountSelectionMenus.get(player.getUniqueId()).getSlotValues()
                    );

                    updateAmountSelectionInventory(player, event.getView(), newInfo, originalLores);
                } else if (isBulkButton(slot)) {
                    pendingBulkMenuOpens.put(player.getUniqueId(), System.currentTimeMillis());
                }
                return;
            } else if (menuType != null && menuType.equals("AMOUNT_SELECTION_BULK")) {
                return;
            } else if (menuType != null) {
                // On a extrait le shopId complet (avec numéro de page)
                String shopId = menuType;
                
                // Extraire le shopId de base et la page
                String baseShopId = shopId;
                int page = 1;
                
                if (shopId.contains("#")) {
                    String[] parts = shopId.split("#");
                    baseShopId = parts[0];
                    try {
                        page = Integer.parseInt(parts[1]);
                    } catch (NumberFormatException e) {
                        page = 1;
                    }
                }
                
                // Récupérer l'item dans le shop
                Shop shop = ShopGuiPlusApi.getPlugin().getShopManager().getShopById(baseShopId);
                if (shop == null) return;
                
                ShopItem shopItem = shop.getShopItem(page, slot);
                if (shopItem == null || shopItem.getType() == ShopItemType.DUMMY) {
                    return;
                }
                
                // Mettre à jour l'itemId dans les maps
                openShopMap.put(player.getUniqueId(), new SimpleEntry<>(shopId, shopItem.getId()));
                lastShopMap.put(player.getUniqueId(), new SimpleEntry<>(shopId, shopItem.getId()));
            }
        } catch (Exception e) {
            // Ignorer les erreurs
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
            
        Player player = (Player) event.getPlayer();
        UUID playerId = player.getUniqueId();
        
        // Sauvegarder les informations du shop actuel
        SimpleEntry<String, String> currentShop = openShopMap.get(playerId);
        String shopId = currentShop != null ? currentShop.getKey() : null;

        if (currentShop != null && currentShop.getKey() != null) {
            lastShopMap.put(playerId, new SimpleEntry<>(currentShop.getKey(), currentShop.getValue()));
        }
        
        // Déterminer si c'est un menu de sélection
        String menuType = determineShopId(event.getView());
        boolean isSelectionMenu = menuType != null && (menuType.equals("AMOUNT_SELECTION") || menuType.equals("AMOUNT_SELECTION_BULK"));
        
        // Arrêter les tâches de rafraîchissement
        cancelRefreshTasks(playerId);

        // Ne nettoyer les données que si ce n'est pas un menu de sélection
        if (!isSelectionMenu) {
            scheduleCleanup(player, playerId, shopId);
        } else {
            // Pour les menus de sélection, ne pas nettoyer mais arrêter le refresh
            BukkitTask t = playerRefreshBukkitTasks.remove(playerId);
            if (t != null) t.cancel();
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
            // Vérifier si transition vers BULK menu
            if (pendingBulkMenuOpens.containsKey(playerId)) {
                long timestamp = pendingBulkMenuOpens.get(playerId);
                if (System.currentTimeMillis() - timestamp < 500) {
                    pendingBulkMenuOpens.remove(playerId);
                    return;
                } else {
                    pendingBulkMenuOpens.remove(playerId);
                }
            }

            // Récupérer l'information du dernier shop
            SimpleEntry<String, String> lastShop = lastShopMap.get(player.getUniqueId());
            
            if (lastShop != null && lastShop.getKey() != null && lastShop.getKey().contains("#")) {
                reopenShopAtPage(player, lastShop);
            }
        }
    }
    
    // MÉTHODES UTILITAIRES OPTIMISÉES
    
    /**
     * Vérifie rapidement si un lore contient des placeholders DynaShop
     */
    private boolean containsDynaShopPlaceholder(List<String> lore) {
        if (lore == null || lore.isEmpty()) return false;
        
        for (String line : lore) {
            if (line != null && line.indexOf("%dynashop_current_") >= 0) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Remplace les placeholders avec des valeurs pré-calculées et filtre les lignes
     */
    private List<String> replacePlaceholders(List<String> lore, Map<String, String> prices, Player player) {
        List<String> newLore = new ArrayList<>();
        
        for (String line : lore) {
            // OPTIMISATION : Détection rapide si la ligne contient des placeholders
            if (!line.contains("%dynashop_")) {
                // Si pas de placeholder dynashop, ajouter directement (avec placeholderAPI si nécessaire)
                if (line.contains("%") && canUsePlaceholderAPI()) {
                    try {
                        line = PlaceholderAPI.setPlaceholders(player, line);
                    } catch (Exception e) {
                        // Ignorer les erreurs
                    }
                }
                newLore.add(line);
                continue;
            }
            
            boolean skipLine = false;
            boolean hideBuyPriceForUnbuyable = ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getBoolean("hideBuyPriceForUnbuyable", true);
            boolean hideSellPriceForUnsellable = ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getBoolean("hideSellPriceForUnsellable", true);
            
            // Vérifier les conditions pour masquer la ligne
            if (shouldSkipLine(line, prices, hideBuyPriceForUnbuyable, hideSellPriceForUnsellable)) {
                skipLine = true;
            }
            
            // Si la ligne doit être conservée, remplacer les placeholders
            if (!skipLine) {
                line = replaceDynaShopPlaceholders(line, prices);
                
                // Traiter les autres placeholders via PlaceholderAPI (si disponible)
                if (line.contains("%") && canUsePlaceholderAPI()) {
                    try {
                        line = PlaceholderAPI.setPlaceholders(player, line);
                    } catch (Exception e) {
                        // Ignorer les erreurs
                    }
                }
                
                newLore.add(line);
            }
        }
        return newLore;
    }
    
    /**
     * Vérifie si une ligne doit être ignorée
     */
    private boolean shouldSkipLine(String line, Map<String, String> prices, boolean hideBuyPriceForUnbuyable, boolean hideSellPriceForUnsellable) {
        // Vérifier les placeholders de prix d'achat
        if ((line.contains("%dynashop_current_buyPrice%") || line.contains("%dynashop_current_buy%")) &&
            hideBuyPriceForUnbuyable &&
            (prices.get("buy").equals("N/A") || prices.get("buy").equals("0.0") || prices.get("buy").equals("-1"))) {
            return true;
        }
        
        // Vérifier les placeholders de prix de vente
        if ((line.contains("%dynashop_current_sellPrice%") || line.contains("%dynashop_current_sell%")) && 
            hideSellPriceForUnsellable &&
            (prices.get("sell").equals("N/A") || prices.get("sell").equals("0.0") || prices.get("sell").equals("-1"))) {
            return true;
        }
        
        // Vérifier les placeholders de stock
        if ((line.contains("%dynashop_current_stock%") || 
             line.contains("%dynashop_current_maxstock%") || 
             line.contains("%dynashop_current_stock_ratio%") || 
             line.contains("%dynashop_current_colored_stock_ratio%")) && 
            ((!Boolean.parseBoolean(prices.get("is_stock_mode")) && 
              !Boolean.parseBoolean(prices.get("is_static_stock_mode"))) || 
             prices.get("stock").equals("N/A"))) {
            return true;
        }
        
        // Vérifier les placeholders de limites
        if ((line.contains("%dynashop_current_buy_limit%") && prices.get("buy_limit").equals("∞")) ||
            (line.contains("%dynashop_current_sell_limit%") && prices.get("sell_limit").equals("∞")) ||
            (line.contains("%dynashop_current_buy_reset_time%") && prices.get("buy_reset_time").equals("∞")) ||
            (line.contains("%dynashop_current_sell_reset_time%") && prices.get("sell_reset_time").equals("∞"))) {
            return true;
        }
        
        // Vérifier les statuts de limite
        if ((line.contains("%dynashop_current_buy_limit_status%") && 
             (prices.get("buy_reset_time").equals("∞") || prices.get("buy_limit").equals("∞"))) ||
            (line.contains("%dynashop_current_sell_limit_status%") && 
             (prices.get("sell_reset_time").equals("∞") || prices.get("sell_limit").equals("∞")))) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Remplace tous les placeholders DynaShop dans une ligne
     */
    private String replaceDynaShopPlaceholders(String line, Map<String, String> prices) {
        // Remplacements basiques
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
                  .replace("%dynashop_current_colored_stock_ratio%", prices.get("colored_stock_ratio"))
                  .replace("%dynashop_current_buy_limit%", prices.get("buy_limit"))
                  .replace("%dynashop_current_sell_limit%", prices.get("sell_limit"))
                  .replace("%dynashop_current_buy_reset_time%", prices.get("buy_reset_time"))
                  .replace("%dynashop_current_sell_reset_time%", prices.get("sell_reset_time"));
        
        // Remplacements conditionnels pour les statuts de limite
        if (line.contains("%dynashop_current_buy_limit_status%")) {
            if (prices.get("buy_limit_reached").equals("true")) {
                line = line.replace("%dynashop_current_buy_limit_status%", 
                    ChatColor.translateAlternateColorCodes('&', 
                        plugin.getLangConfig().getPlaceholderLimitBuyReached()
                            .replace("%time%", prices.get("buy_reset_time"))));
            } else {
                line = line.replace("%dynashop_current_buy_limit_status%", 
                    ChatColor.translateAlternateColorCodes('&', 
                        plugin.getLangConfig().getPlaceholderLimitRemaining()
                            .replace("%limit%", prices.get("buy_limit"))));
            }
        }
        
        if (line.contains("%dynashop_current_sell_limit_status%")) {
            if (prices.get("sell_limit_reached").equals("true")) {
                line = line.replace("%dynashop_current_sell_limit_status%", 
                    ChatColor.translateAlternateColorCodes('&', 
                        plugin.getLangConfig().getPlaceholderLimitSellReached()
                            .replace("%time%", prices.get("sell_reset_time"))));
            } else {
                line = line.replace("%dynashop_current_sell_limit_status%", 
                    ChatColor.translateAlternateColorCodes('&', 
                        plugin.getLangConfig().getPlaceholderLimitRemaining()
                            .replace("%limit%", prices.get("sell_limit"))));
            }
        }
        
        return line;
    }
    
    /**
     * Vérifie si PlaceholderAPI est disponible
     */
    private boolean canUsePlaceholderAPI() {
        return plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null;
    }
    
    /**
     * Pré-remplace les placeholders avec des valeurs temporaires
     */
    private List<String> preProcessPlaceholders(List<String> lore) {
        List<String> processed = new ArrayList<>();
        for (String line : lore) {
            if (line.contains("%dynashop_current_")) {
                // Remplacer temporairement par "Chargement..."
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
                    .replace("%dynashop_current_colored_stock_ratio%", "Loading...")
                    .replace("%dynashop_current_buy_limit%", "Loading...")
                    .replace("%dynashop_current_sell_limit%", "Loading...")
                    .replace("%dynashop_current_buy_reset_time%", "Loading...")
                    .replace("%dynashop_current_sell_reset_time%", "Loading...")
                    .replace("%dynashop_current_buy_limit_status%", "Loading...")
                    .replace("%dynashop_current_sell_limit_status%", "Loading...");
                processed.add(tempLine);
            } else {
                processed.add(line);
            }
        }
        return processed;
    }
    
    /**
     * Détermine l'ID du shop à partir du titre de l'inventaire
     */
    private String determineShopId(InventoryView view) {
        if (view == null) return null;
        
        String title = view.getTitle();
        
        // Vérifier les menus de sélection
        if (isAmountSelectionMenu(title)) {
            return "AMOUNT_SELECTION";
        }
        
        if (isBulkSelectionMenu(title)) {
            return "AMOUNT_SELECTION_BULK";
        }
        
        // Chercher parmi les shops enregistrés
        return findShopIdFromTitle(title);
    }
    
    /**
     * Vérifie si c'est un menu de sélection standard
     */
    private boolean isAmountSelectionMenu(String title) {
        String buyName = ShopGuiPlusApi.getPlugin().getConfigLang().getConfig().getString("DIALOG.AMOUNTSELECTION.BUY.NAME");
        String sellName = ShopGuiPlusApi.getPlugin().getConfigLang().getConfig().getString("DIALOG.AMOUNTSELECTION.SELL.NAME");
        
        return title.contains(ChatColor.translateAlternateColorCodes('&', buyName.replace("%item%", ""))) ||
               title.contains(ChatColor.translateAlternateColorCodes('&', sellName.replace("%item%", "")));
    }
    
    /**
     * Vérifie si c'est un menu de sélection bulk
     */
    private boolean isBulkSelectionMenu(String title) {
        String bulkBuyName = ShopGuiPlusApi.getPlugin().getConfigLang().getConfig().getString("DIALOG.AMOUNTSELECTION.BULKBUY.NAME");
        String bulkSellName = ShopGuiPlusApi.getPlugin().getConfigLang().getConfig().getString("DIALOG.AMOUNTSELECTION.BULKSELL.NAME");
        
        return title.contains(ChatColor.translateAlternateColorCodes('&', bulkBuyName.replace("%item%", ""))) ||
               title.contains(ChatColor.translateAlternateColorCodes('&', bulkSellName.replace("%item%", "")));
    }

    /**
     * Cherche l'ID du shop parmi les shops enregistrés
     */
    private String findShopIdFromTitle(String title) {
        try {
            for (Shop shop : ShopGuiPlusApi.getPlugin().getShopManager().getShops()) {
                String shopNameTemplate = shop.getName().replace("%page%", "");
                if (title.contains(shopNameTemplate)) {
                    // Extraire le numéro de page
                    int page = 1;
                    if (shop.getName().contains("%page%")) {
                        page = extractPageNumber(title, shop.getName());
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
    
    /**
     * Extrait le numéro de page à partir du titre
     */
    private int extractPageNumber(String title, String nameTemplate) {
        String before = nameTemplate.split("%page%")[0];
        String after = nameTemplate.split("%page%").length > 1 ? nameTemplate.split("%page%")[1] : "";
        
        if (title.startsWith(before) && (after.isEmpty() || title.endsWith(after))) {
            String pageStr = title.substring(before.length(), after.isEmpty() ? title.length() : title.length() - after.length());
            try {
                return Integer.parseInt(pageStr);
            } catch (NumberFormatException e) {
                return 1;
            }
        }
        
        return 1;
    }
    
    /**
     * Vérifie si un slot correspond à un bouton de quantité
     */
    private boolean isQuantityButton(int slot) {
        return slot == ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getInt("amountSelectionGUI.buttons.set1.slot") ||
               slot == ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getInt("amountSelectionGUI.buttons.remove10.slot") ||
               slot == ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getInt("amountSelectionGUI.buttons.remove1.slot") ||
               slot == ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getInt("amountSelectionGUI.buttons.add1.slot") ||
               slot == ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getInt("amountSelectionGUI.buttons.add10.slot") ||
               slot == ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getInt("amountSelectionGUI.buttons.set16.slot") ||
               slot == ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getInt("amountSelectionGUI.buttons.set64.slot");
    }
    
    /**
     * Vérifie si un slot correspond à un bouton bulk
     */
    private boolean isBulkButton(int slot) {
        return slot == ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getInt("amountSelectionGUI.buttons.buyMore.slot") ||
               slot == ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getInt("amountSelectionGUI.buttons.sellMore.slot");
    }
    
    /**
     * Extrait les lores originaux des items
     */
    private Map<Integer, List<String>> extractOriginalLores(InventoryView view, Player player) {
        Map<Integer, List<String>> originalLores = new HashMap<>();
        
        AmountSelectionInfo info = amountSelectionMenus.get(player.getUniqueId());
        if (info == null) return originalLores;
        
        for (Map.Entry<Integer, Integer> entry : info.getSlotValues().entrySet()) {
            int slot = entry.getKey();
            ItemStack item = view.getTopInventory().getItem(slot);
            if (item != null && item.hasItemMeta() && item.getItemMeta().hasLore()) {
                ItemMeta meta = item.getItemMeta();
                List<String> lore = meta.getLore();
                
                originalLores.put(slot, new ArrayList<>(lore));
                // Pré-remplacer les placeholders pour éviter de les voir bruts
                List<String> tempLore = preProcessPlaceholders(lore);
                meta.setLore(tempLore);
                item.setItemMeta(meta);
            }
        }
        
        return originalLores;
    }
    
    /**
     * Annule les tâches de rafraîchissement
     */
    private void cancelRefreshTasks(UUID playerId) {
        BukkitTask t1 = playerRefreshBukkitTasks.remove(playerId);
        if (t1 != null) t1.cancel();
        playerRefreshTasks.remove(playerId);

        BukkitTask t2 = playerSelectionRefreshBukkitTasks.remove(playerId);
        if (t2 != null) t2.cancel();
        playerSelectionRefreshTasks.remove(playerId);
    }
    
    /**
     * Planifie le nettoyage des données du joueur
     */
    private void scheduleCleanup(Player player, UUID playerId, String shopId) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            
            String newMenuType = player.isOnline() ? determineShopId(player.getOpenInventory()) : null;
            boolean isNewSelectionMenu = newMenuType != null && 
                                        (newMenuType.equals("AMOUNT_SELECTION") || 
                                         newMenuType.equals("AMOUNT_SELECTION_BULK"));
            
            boolean isChangingPage = false;
            if (newMenuType != null && shopId != null) {
                String baseNewShop = newMenuType.contains("#") ? newMenuType.split("#")[0] : newMenuType;
                String baseOldShop = shopId.contains("#") ? shopId.split("#")[0] : shopId;
                isChangingPage = baseNewShop.equals(baseOldShop) && newMenuType.contains("#");
            }

            if (!isNewSelectionMenu && !isChangingPage) {
                openShopMap.remove(playerId);
                amountSelectionMenus.remove(playerId);
            } else if (isChangingPage) {
                SimpleEntry<String, String> currentShop = openShopMap.get(playerId);
                String itemId = currentShop != null ? currentShop.getValue() : null;
                openShopMap.put(playerId, new SimpleEntry<>(newMenuType, itemId));
                lastShopMap.put(playerId, new SimpleEntry<>(newMenuType, itemId));
            }
        }, 10L);
    }
    
    /**
     * Réouvre le shop à une page spécifique
     */
    private void reopenShopAtPage(Player player, SimpleEntry<String, String> lastShop) {
        String[] parts = lastShop.getKey().split("#");
        String shopId = parts[0];
        int page = 1;
        
        try {
            page = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            page = 1;
        }

        final int finalPage = page;
        
        if (finalPage > 1) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    String currentMenuType = determineShopId(player.getOpenInventory());
                    if (currentMenuType == null || 
                        (!currentMenuType.equals("AMOUNT_SELECTION") && !currentMenuType.equals("AMOUNT_SELECTION_BULK"))) {
                        
                        Shop shop = ShopGuiPlusApi.getPlugin().getShopManager().getShopById(shopId);
                        if (shop != null) {
                            try {
                                ShopGuiPlusApi.openShop(player, shopId, finalPage);
                            } catch (PlayerDataNotLoadedException e) {
                                // Ignorer
                            }
                        }
                    }
                }
            }, 2L);
        }
    }
    
    /**
     * Démarre l'actualisation continue d'un inventaire de shop
     */
    private void startContinuousRefresh(Player player, InventoryView view, String shopId, int page, Map<Integer, List<String>> originalLores) {
        // Annuler la tâche précédente
        BukkitTask oldTask = playerRefreshBukkitTasks.remove(player.getUniqueId());
        if (oldTask != null) oldTask.cancel();

        // ID unique pour cette session de refresh
        final UUID refreshId = UUID.randomUUID();
        playerRefreshTasks.put(player.getUniqueId(), refreshId);
        
        // Intervalle de rafraîchissement
        final long refreshInterval = plugin.isRealTimeMode() ? guiRefreshCriticalItems : guiRefreshDefaultItems;

        // Démarrer la tâche de rafraîchissement
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (!player.isOnline() || player.getOpenInventory() == null || playerRefreshTasks.get(player.getUniqueId()) != refreshId) {
                BukkitTask t = playerRefreshBukkitTasks.remove(player.getUniqueId());
                if (t != null) t.cancel();
                playerRefreshTasks.remove(player.getUniqueId());
                return;
            }
            
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (player.isOnline() && player.getOpenInventory() != null) {
                    updateShopInventory(player, view, shopId, page, originalLores);
                }
            });
        }, 0L, refreshInterval / 50);

        playerRefreshBukkitTasks.put(player.getUniqueId(), task);
    }
    
    /**
     * Démarre l'actualisation continue d'un menu de sélection
     */
    private void startContinuousAmountSelectionRefresh(Player player, AmountSelectionInfo info, Map<Integer, List<String>> originalLores, String menuType) {
        // Annuler la tâche précédente
        BukkitTask oldTask = playerSelectionRefreshBukkitTasks.remove(player.getUniqueId());
        if (oldTask != null) oldTask.cancel();
        
        // ID unique pour cette session de refresh
        final UUID refreshId = UUID.randomUUID();
        playerSelectionRefreshTasks.put(player.getUniqueId(), refreshId);
        
        // Variables pour suivre les changements de quantité
        final int[] lastCenterQuantity = {info.getItemStack().getAmount()};
        
        // Déterminer l'intervalle de rafraîchissement
        boolean isCriticalItem = plugin.getConfigMain().getStringList("critical-items").contains(info.getShopId() + ":" + info.getItemId());
        final long refreshInterval = isCriticalItem ? guiRefreshCriticalItems : guiRefreshDefaultItems;

        // Démarrer la tâche de rafraîchissement
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (!player.isOnline() || player.getOpenInventory() == null || playerSelectionRefreshTasks.get(player.getUniqueId()) != refreshId) {
                BukkitTask t = playerSelectionRefreshBukkitTasks.remove(player.getUniqueId());
                if (t != null) t.cancel();
                playerSelectionRefreshTasks.remove(player.getUniqueId());
                return;
            }
            
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (player.isOnline() && player.getOpenInventory() != null) {
                    if (menuType.equals("AMOUNT_SELECTION")) {
                        updateAmountSelectionIfChanged(player, info, originalLores, lastCenterQuantity);
                    } else {
                        // Pour les menus BULK, mettre à jour périodiquement
                        updateAmountSelectionInventory(player, player.getOpenInventory(), info, originalLores);
                    }
                }
            });
        }, 0L, refreshInterval / 50);

        playerSelectionRefreshBukkitTasks.put(player.getUniqueId(), task);
    }
    
    /**
     * Met à jour le menu de sélection si la quantité a changé
     */
    private void updateAmountSelectionIfChanged(Player player, AmountSelectionInfo info, Map<Integer, List<String>> originalLores, int[] lastCenterQuantity) {
        int centerSlot = ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getInt("amountSelectionGUI.itemSlot", 22);
        int inventorySize = player.getOpenInventory().getTopInventory().getSize();

        // Ajuster le slot central si nécessaire
        if (centerSlot >= inventorySize) {
            centerSlot = Math.min(inventorySize - 1, 13);
        }
        
        ItemStack currentItem = player.getOpenInventory().getTopInventory().getItem(centerSlot);
        
        if (currentItem != null && currentItem.getAmount() != lastCenterQuantity[0]) {
            // Mettre à jour la quantité de référence
            lastCenterQuantity[0] = currentItem.getAmount();

            // Mettre à jour l'interface avec la nouvelle quantité
            AmountSelectionInfo newInfo = new AmountSelectionInfo(
                info.getShopId(), 
                info.getItemId(), 
                currentItem, 
                info.isBuying(), 
                "AMOUNT_SELECTION", 
                info.getSlotValues()
            );
            
            updateAmountSelectionInventory(player, player.getOpenInventory(), newInfo, originalLores);
        }
    }
    
    /**
     * Récupère ou calcule les prix mis en cache pour un item
     */
    private Map<String, String> getCachedPlaceholders(Player player, String shopId, String itemId, ItemStack itemStack, int quantity, boolean forceRefresh) {
        final String cacheKey = player != null
            ? shopId + ":" + itemId + ":" + quantity + ":" + player.getUniqueId().toString()
            : shopId + ":" + itemId + ":" + quantity;

        String baseShopId = shopId;
        
        if (shopId != null && shopId.contains("#")) {
            String[] parts = shopId.split("#");
            baseShopId = parts[0];
        }
        final String finalBaseShopId = baseShopId;
        
        // Forcer le rafraîchissement pour les items critiques
        List<String> criticalItems = plugin.getConfigMain().getStringList("critical-items");
        boolean isCriticalItem = criticalItems.contains(baseShopId + ":" + itemId);
        forceRefresh = forceRefresh || isCriticalItem;
        
        if (forceRefresh) {
            plugin.getDisplayPriceCache().invalidate(cacheKey);
        }
        
        // Utiliser des ensembles partagés pour la récursion
        Set<String> visited = new HashSet<>();
        Map<String, DynamicPrice> lastResults = new HashMap<>();
        
        // Utiliser le cache
        return plugin.getDisplayPriceCache().get(cacheKey, () -> {
            return computePrices(player, finalBaseShopId, itemId, itemStack, quantity, visited, lastResults);
        });
    }
    
    /**
     * Version simplifiée qui délègue à la version complète
     */
    private Map<String, String> getCachedPlaceholders(Player player, String shopId, String itemId, ItemStack itemStack, boolean forceRefresh) {
        return getCachedPlaceholders(player, shopId, itemId, itemStack, 1, forceRefresh);
    }
    
    /**
     * Calcule les prix pour un item
     */
    private Map<String, String> computePrices(Player player, String shopId, String itemId, ItemStack itemStack, int quantity, Set<String> visited, Map<String, DynamicPrice> lastResults) {
        Map<String, String> prices = new HashMap<>();
        
        // Récupérer le type d'achat et de vente
        DynaShopType buyType = plugin.getShopConfigManager().getTypeDynaShop(shopId, itemId, "buy");
        DynaShopType sellType = plugin.getShopConfigManager().getTypeDynaShop(shopId, itemId, "sell");

        // Obtenir le prix dynamique
        DynamicPrice price = plugin.getDynaShopListener().getOrLoadPrice(player, shopId, itemId, itemStack, visited, lastResults);

        // Remplir les prix de base
        if (price != null) {
            fillBasicPrices(prices, price, quantity);
        }

        // Identifier les modes
        boolean isStockMode = buyType == DynaShopType.STOCK || sellType == DynaShopType.STOCK;
        boolean isStaticStockMode = buyType == DynaShopType.STATIC_STOCK || sellType == DynaShopType.STATIC_STOCK;
        boolean isRecipeMode = buyType == DynaShopType.RECIPE || sellType == DynaShopType.RECIPE;
        boolean isLinkMode = buyType == DynaShopType.LINK || sellType == DynaShopType.LINK;

        prices.put("is_stock_mode", String.valueOf(isStockMode));
        prices.put("is_static_stock_mode", String.valueOf(isStaticStockMode));
        prices.put("is_recipe_mode", String.valueOf(isRecipeMode));
        prices.put("is_link_mode", String.valueOf(isLinkMode));

        // Vérifier si un item en mode RECIPE a un stock maximum
        if (isRecipeMode) {
            boolean hasMaxStock = plugin.getPriceRecipe().calculateMaxStock(shopId, itemId, new ArrayList<>()) > 0;
            if (hasMaxStock) {
                isStockMode = true;
                prices.put("is_stock_mode", String.valueOf(isStockMode));
            }
        }

        // Gérer les informations de stock
        if (isStockMode || isStaticStockMode) {
            fillStockInfo(prices, price, shopId, itemId);
        } else {
            prices.put("stock", "N/A");
            prices.put("stock_max", "N/A");
            prices.put("base_stock", "N/A");
            prices.put("colored_stock_ratio", "N/A");
        }

        // Récupérer les informations de limites si joueur non null
        if (player != null) {
            fillLimitInfo(prices, player, shopId, itemId);
        }
        
        // Formater les prix pour l'affichage
        formatDisplayPrices(prices, price, shopId);
        
        return prices;
    }

    /**
     * Remplit les prix de base dans la map
     */
    private void fillBasicPrices(Map<String, String> prices, DynamicPrice price, int quantity) {
        if (price.getBuyPrice() < 0) {
            prices.put("buy", "N/A");
        } else {
            prices.put("buy", plugin.getPriceFormatter().formatPrice(price.getBuyPrice() * quantity));
        }
        
        if (price.getSellPrice() < 0) {
            prices.put("sell", "N/A");
        } else {
            prices.put("sell", plugin.getPriceFormatter().formatPrice(price.getSellPrice() * quantity));
        }
        
        if (price.getMinBuyPrice() < 0) {
            prices.put("buy_min", "N/A");
        } else {
            prices.put("buy_min", plugin.getPriceFormatter().formatPrice(price.getMinBuyPrice() * quantity));
        }
        
        if (price.getMaxBuyPrice() < 0) {
            prices.put("buy_max", "N/A");
        } else {
            prices.put("buy_max", plugin.getPriceFormatter().formatPrice(price.getMaxBuyPrice() * quantity));
        }
        
        if (price.getMinSellPrice() < 0) {
            prices.put("sell_min", "N/A");
        } else {
            prices.put("sell_min", plugin.getPriceFormatter().formatPrice(price.getMinSellPrice() * quantity));
        }
        
        if (price.getMaxSellPrice() < 0) {
            prices.put("sell_max", "N/A");
        } else {
            prices.put("sell_max", plugin.getPriceFormatter().formatPrice(price.getMaxSellPrice() * quantity));
        }
    }
    
    /**
     * Remplit les informations de stock
     */
    private void fillStockInfo(Map<String, String> prices, DynamicPrice price, String shopId, String itemId) {
        String currentStock, maxStock, fCurrentStock, fMaxStock;
        
        if (price != null) {
            if (price.getStock() < 0) {
                currentStock = "N/A";
                fCurrentStock = "N/A";
            } else {
                currentStock = String.valueOf(price.getStock());
                fCurrentStock = plugin.getPriceFormatter().formatStock(price.getStock());
            }
            
            if (price.getMaxStock() < 0) {
                maxStock = "N/A";
                fMaxStock = "N/A";
            } else {
                maxStock = String.valueOf(price.getMaxStock());
                fMaxStock = plugin.getPriceFormatter().formatStock(price.getMaxStock());
            }
        } else {
            currentStock = plugin.getPriceFormatter().getStockByType(shopId, itemId, "stock");
            fCurrentStock = currentStock.equals("N/A") || currentStock.equals("-1") ? 
                "N/A" : plugin.getPriceFormatter().formatStock(Integer.parseInt(currentStock));
            
            maxStock = plugin.getPriceFormatter().getStockByType(shopId, itemId, "stock_max");
            fMaxStock = maxStock.equals("N/A") || maxStock.equals("-1") ? 
                "N/A" : plugin.getPriceFormatter().formatStock(Integer.parseInt(maxStock));
        }

        prices.put("stock", fCurrentStock);
        prices.put("stock_max", fMaxStock);

        // Format pour le stock
        if (currentStock.equals("N/A") || currentStock.equals("-1") || currentStock.equals("0")) {
            prices.put("base_stock", ChatColor.translateAlternateColorCodes('&', plugin.getLangConfig().getPlaceholderOutOfStock()));
            prices.put("colored_stock_ratio", ChatColor.translateAlternateColorCodes('&', plugin.getLangConfig().getPlaceholderOutOfStock()));
        } else {
            prices.put("base_stock", String.format("%s/%s", fCurrentStock, fMaxStock));

            try {
                int current = Integer.parseInt(currentStock);
                int max = Integer.parseInt(maxStock);
                
                if (max <= 0) {
                    prices.put("colored_stock_ratio", ChatColor.translateAlternateColorCodes('&', String.format("&7%s", fCurrentStock)));
                } else {
                    String colorCode;
                    
                    if (current < max * 0.10) {
                        colorCode = "&4"; // Rouge foncé pour stock critique
                    } else if (current < max * 0.25) {
                        colorCode = "&c"; // Rouge pour stock faible
                    } else if (current < max * 0.5) {
                        colorCode = "&e"; // Jaune pour stock moyen
                    } else {
                        colorCode = "&a"; // Vert pour stock élevé
                    }
                    prices.put("colored_stock_ratio", ChatColor.translateAlternateColorCodes('&', 
                        String.format("%s%s&7/%s", colorCode, fCurrentStock, fMaxStock)));
                }
            } catch (NumberFormatException e) {
                prices.put("colored_stock_ratio", ChatColor.translateAlternateColorCodes('&', 
                    String.format("&7%s/%s", fCurrentStock, fMaxStock)));
            }
        }
    }
    
    /**
     * Remplit les informations de limites
     */
    private void fillLimitInfo(Map<String, String> prices, Player player, String shopId, String itemId) {
        // Récupérer les limites d'achat
        TransactionLimit buyLimit = plugin.getTransactionLimiter().getTransactionLimit(shopId, itemId, true);
        if (buyLimit != null && buyLimit.getAmount() > 0) {
            try {
                int buyRemaining = plugin.getTransactionLimiter().getRemainingAmountSync(player, shopId, itemId, true);
                prices.put("buy_limit", String.valueOf(buyRemaining));

                // if (buyRemaining <= 0) {
                //     long buyResetTime = plugin.getTransactionLimiter().getNextAvailableTimeSync(player, shopId, itemId, true);
                //     prices.put("buy_reset_time", formatTimeRemaining(buyResetTime, buyLimit));
                //     prices.put("buy_limit_reached", "true");
                //     plugin.info("Checking buy limit for shop: " + shopId + ", item: " + itemId + ", player: " + player.getName() + 
                //                 ", limit: " + (buyLimit != null ? buyLimit.getAmount() : "N/A") + 
                //                 ", remaining: " + buyRemaining + ", reset time: " + formatTimeRemaining(buyResetTime, buyLimit));
                // } else {
                //     prices.put("buy_reset_time", "∞");
                //     prices.put("buy_limit_reached", "false");
                // }
                
                long buyResetTime = plugin.getTransactionLimiter().getNextAvailableTimeSync(player, shopId, itemId, true);
                prices.put("buy_reset_time", formatTimeRemaining(buyResetTime, buyLimit));
                if (buyRemaining <= 0) {
                    prices.put("buy_limit_reached", "true");
                } else {
                    prices.put("buy_limit_reached", "false");
                }
            } catch (Exception e) {
                prices.put("buy_limit", "N/A");
                prices.put("buy_reset_time", "N/A");
                prices.put("buy_limit_reached", "false");
            }
        } else {
            prices.put("buy_limit", "∞");
            prices.put("buy_reset_time", "∞");
            prices.put("buy_limit_reached", "false");
        }
        
        // Récupérer les limites de vente
        TransactionLimit sellLimit = plugin.getTransactionLimiter().getTransactionLimit(shopId, itemId, false);
        if (sellLimit != null && sellLimit.getAmount() > 0) {
            try {
                int sellRemaining = plugin.getTransactionLimiter().getRemainingAmountSync(player, shopId, itemId, false);
                prices.put("sell_limit", String.valueOf(sellRemaining));
                
                if (sellRemaining <= 0) {
                    long sellResetTime = plugin.getTransactionLimiter().getNextAvailableTimeSync(player, shopId, itemId, false);
                    prices.put("sell_reset_time", formatTimeRemaining(sellResetTime, sellLimit));
                    prices.put("sell_limit_reached", "true");
                } else {
                    prices.put("sell_reset_time", "∞");
                    prices.put("sell_limit_reached", "false");
                }
            } catch (Exception e) {
                prices.put("sell_limit", "N/A");
                prices.put("sell_reset_time", "N/A");
                prices.put("sell_limit_reached", "false");
            }
        } else {
            prices.put("sell_limit", "∞");
            prices.put("sell_reset_time", "∞");
            prices.put("sell_limit_reached", "false");
        }
    }
    
    /**
     * Formate les prix pour l'affichage
     */
    private void formatDisplayPrices(Map<String, String> prices, DynamicPrice price, String shopId) {
        String currencyPrefix = "";
        String currencySuffix = " $";
        
        try {
            currencyPrefix = ShopGuiPlusApi.getPlugin().getEconomyManager().getEconomyProvider(
                ShopGuiPlusApi.getShop(shopId).getEconomyType()).getCurrencyPrefix();
            currencySuffix = ShopGuiPlusApi.getPlugin().getEconomyManager().getEconomyProvider(
                ShopGuiPlusApi.getShop(shopId).getEconomyType()).getCurrencySuffix();
        } catch (Exception e) {
            // Valeurs par défaut
        }
        
        // Format pour le prix d'achat avec min-max
        if (!prices.get("buy").equals("N/A") && !prices.get("buy_min").equals("N/A") && !prices.get("buy_max").equals("N/A") &&
            (!prices.get("buy_min").equals(prices.get("buy")) || !prices.get("buy_max").equals(prices.get("buy")))) {
            prices.put("base_buy", String.format(
                currencyPrefix + "%s" + currencySuffix + " §7(%s - %s) ", 
                prices.get("buy"), prices.get("buy_min"), prices.get("buy_max")
            ));
        } else {
            prices.put("base_buy", currencyPrefix + prices.get("buy") + currencySuffix);
        }

        // Format pour le prix de vente avec min-max
        if (!prices.get("sell").equals("N/A") && !prices.get("sell_min").equals("N/A") && !prices.get("sell_max").equals("N/A") &&
            (!prices.get("sell_min").equals(prices.get("sell")) || !prices.get("sell_max").equals(prices.get("sell")))) {
            prices.put("base_sell", String.format(
                currencyPrefix + "%s" + currencySuffix + " §7(%s - %s) ", 
                prices.get("sell"), prices.get("sell_min"), prices.get("sell_max")
            ));
        } else {
            prices.put("base_sell", currencyPrefix + prices.get("sell") + currencySuffix);
        }
    }

    /**
     * Met à jour l'inventaire du shop de manière optimisée
     */
    private void updateShopInventory(Player player, InventoryView view, String shopId, int page, Map<Integer, List<String>> originalLores) {
        if (view == null || view.getTopInventory() == null) return;

        try {
            // Capturer les valeurs finales pour utilisation dans les lambdas
            final String finalShopId = shopId;
            if (finalShopId == null) return;

            // Identifier les slots qui ont besoin d'être mis à jour
            List<Integer> slotsToUpdate = new ArrayList<>(originalLores.keySet());
            
            // Traiter les slots par lots pour éviter de surcharger le serveur
            for (int i = 0; i < slotsToUpdate.size(); i += BATCH_SIZE) {
                final int startIdx = i;
                final int endIdx = Math.min(i + BATCH_SIZE, slotsToUpdate.size());
            
                // Traiter chaque lot de manière asynchrone
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    Map<Integer, ItemStack> updatedItems = new HashMap<>();
                    
                    for (int j = startIdx; j < endIdx; j++) {
                        int slot = slotsToUpdate.get(j);
                        
                        try {
                            ItemStack item = view.getTopInventory().getItem(slot);
                            if (item == null || !item.hasItemMeta()) continue;

                            // Utiliser le lore original pour la détection des placeholders
                            List<String> originalLore = originalLores.get(slot);
                            if (originalLore == null || !containsDynaShopPlaceholder(originalLore)) continue;

                            // Récupérer l'item du shop
                            Shop shop = ShopGuiPlusApi.getPlugin().getShopManager().getShopById(finalShopId);
                            if (shop == null) continue;
                            
                            ShopItem shopItem = shop.getShopItem(page, slot);
                            if (shopItem == null) continue;
                            
                            String itemId = shopItem.getId();
                            if (itemId == null) continue;

                            // Calcul des prix pour cet item
                            Map<String, String> itemPrices = getCachedPlaceholders(player, finalShopId, itemId, item, false);
                            
                            // Appliquer les remplacements
                            List<String> newLore = replacePlaceholders(originalLore, itemPrices, player);
                            ItemMeta meta = item.getItemMeta();
                            meta.setLore(newLore);
                            item.setItemMeta(meta);
                            
                            // Ajouter l'item au lot pour mise à jour groupée
                            updatedItems.put(slot, item.clone());
                        } catch (Exception e) {
                            // Ignorer les erreurs individuelles
                        }
                    }

                    // Mettre à jour tous les items du lot en une seule fois
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
            plugin.getLogger().warning("Error updating inventory: " + e.getMessage());
        }
    }

    /**
     * Met à jour l'interface de sélection de quantité
     */
    private void updateAmountSelectionInventory(Player player, InventoryView view, AmountSelectionInfo info, Map<Integer, List<String>> originalLores) {
        if (view == null || view.getTopInventory() == null) return;

        try {
            // Identifier les slots qui ont besoin d'être mis à jour
            List<Integer> slotsToUpdate = new ArrayList<>(originalLores.keySet());
            
            // Traiter les slots par lots
            for (int i = 0; i < slotsToUpdate.size(); i += BATCH_SIZE) {
                final int startIdx = i;
                final int endIdx = Math.min(i + BATCH_SIZE, slotsToUpdate.size());
                
                // Traiter chaque lot de manière asynchrone
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    Map<Integer, ItemStack> updatedItems = new HashMap<>();
                    
                    for (int j = startIdx; j < endIdx; j++) {
                        int slot = slotsToUpdate.get(j);
                        
                        try {
                            ItemStack button = view.getTopInventory().getItem(slot);
                            if (button == null || !button.hasItemMeta() || !button.getItemMeta().hasLore()) continue;

                            // Déterminer la quantité pour ce slot
                            int quantity;
                            if (info.getMenuType().equals("AMOUNT_SELECTION") && 
                                button.getItemMeta().hasDisplayName() &&
                                slot == ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getInt("amountSelectionGUI.buttons.sellAll.slot")) {
                                // Calculer le nombre total d'items dans l'inventaire pour "Tout vendre"
                                quantity = 0;
                                for (ItemStack item : player.getInventory().getContents()) {
                                    if (item != null && item.getType() == info.getItemStack().getType() && 
                                        plugin.getPriceRecipe().customCompare(item, info.getItemStack())) {
                                        quantity += item.getAmount();
                                    }
                                }
                            } else if (info.getMenuType().equals("AMOUNT_SELECTION_BULK")) {
                                // Pour les menus bulk, utiliser la valeur configurée
                                int stackValue = info.getValueForSlot(slot);
                                quantity = stackValue * button.getMaxStackSize();
                            } else {
                                // Pour les menus standard, utiliser la quantité de l'item
                                quantity = info.getItemStack().getAmount();
                            }
                            
                            // Récupérer le lore original
                            List<String> originalLore = originalLores.get(slot);
                            if (originalLore == null || !containsDynaShopPlaceholder(originalLore)) continue;
                            
                            // Calculer les prix pour cette quantité spécifique
                            Map<String, String> prices = getCachedPlaceholders(
                                player, 
                                info.getShopId(), 
                                info.getItemId(), 
                                info.getItemStack(), 
                                quantity,
                                false
                            );
                            
                            // Appliquer les remplacements
                            List<String> newLore = replacePlaceholders(originalLore, prices, player);
                            ItemMeta meta = button.getItemMeta();
                            meta.setLore(newLore);
                            button.setItemMeta(meta);
                            
                            // Ajouter l'item pour mise à jour groupée
                            updatedItems.put(slot, button.clone());
                        } catch (Exception e) {
                            // Ignorer les erreurs individuelles
                        }
                    }
                    
                    // Mettre à jour tous les items du lot en une seule fois
                    if (!updatedItems.isEmpty()) {
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            if (player.isOnline() && player.getOpenInventory().equals(view)) {
                                for (Map.Entry<Integer, ItemStack> entry : updatedItems.entrySet()) {
                                    view.getTopInventory().setItem(entry.getKey(), entry.getValue());
                                }
                                player.updateInventory();
                            }
                        });
                    }
                });
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error updating amount selection inventory: " + e.getMessage());
        }
    }

    /**
     * Méthode publique pour exposer l'ID du shop actuellement ouvert
     */
    public String getCurrentShopId(Player player) {
        SimpleEntry<String, String> shopData = openShopMap.get(player.getUniqueId());
        return shopData == null ? null : shopData.getKey();
    }

    /**
     * Méthode publique pour exposer l'ID de l'item actuellement sélectionné
     */
    public String getCurrentItemId(Player player) {
        SimpleEntry<String, String> shopData = openShopMap.get(player.getUniqueId());
        return shopData == null ? null : shopData.getValue();
    }

    /**
     * Extracteur optimisé pour les données d'interface de sélection
     */
    private AmountSelectionInfo extractAmountSelectionInfo(InventoryView view, String menuType) {
        // Cache des configurations pour éviter les appels répétés
        final var configMain = ShopGuiPlusApi.getPlugin().getConfigMain().getConfig();
        final var configLang = ShopGuiPlusApi.getPlugin().getConfigLang().getConfig();
        
        // Détermination rapide si c'est un achat ou une vente
        String title = view.getTitle();
        String buyName = configLang.getString("DIALOG.AMOUNTSELECTION.BUY.NAME");
        String bulkBuyName = configLang.getString("DIALOG.AMOUNTSELECTION.BULKBUY.NAME");
        
        String translatedBuyName = ChatColor.translateAlternateColorCodes('&', buyName.replace("%item%", ""));
        String translatedBulkBuyName = ChatColor.translateAlternateColorCodes('&', bulkBuyName.replace("%item%", ""));
        boolean isBuying = title.contains(translatedBuyName) || title.contains(translatedBulkBuyName);
        
        // Récupération des slots et valeurs en une seule passe
        Map<Integer, Integer> slotValues = new HashMap<>();
        int centerSlot;
        
        if (menuType.equals("AMOUNT_SELECTION_BULK")) {
            // Pour les menus bulk, récupérer les boutons configurés
            String buttonPrefix = isBuying ? "amountSelectionGUIBulkBuy.buttons.buy" : "amountSelectionGUIBulkSell.buttons.sell";
            centerSlot = configMain.getInt(buttonPrefix + "1.slot", 0);
            
            // Récupérer tous les boutons en une seule boucle
            for (int i = 1; i <= 9; i++) {
                String slotPath = buttonPrefix + i + ".slot";
                if (configMain.contains(slotPath)) {
                    int slot = configMain.getInt(slotPath);
                    int value = configMain.getInt(buttonPrefix + i + ".value", 1);
                    slotValues.put(slot, value);
                }
            }
        } else {
            // Pour les menus standard
            centerSlot = configMain.getInt("amountSelectionGUI.itemSlot", 22);
            slotValues.put(centerSlot, 0);
            
            // Ajouter le bouton "Vendre tout" s'il existe
            int sellAllSlot = configMain.getInt("amountSelectionGUI.buttons.sellAll.slot", -1);
            if (sellAllSlot >= 0) {
                slotValues.put(sellAllSlot, 1);
            }
            
            // Ajouter les boutons +/- pour les quantités
            for (String button : new String[]{"set1", "remove10", "remove1", "add1", "add10", "set16", "set64"}) {
                int slot = configMain.getInt("amountSelectionGUI.buttons." + button + ".slot", -1);
                if (slot >= 0) {
                    slotValues.put(slot, 0);
                }
            }
        }
        
        // Vérification de sécurité pour le slot central
        int inventorySize = view.getTopInventory().getSize();
        if (centerSlot >= inventorySize) {
            centerSlot = Math.min(inventorySize - 1, 22);
        }
        
        // Récupérer l'item central une seule fois
        ItemStack centerItem = view.getTopInventory().getItem(centerSlot);
        if (centerItem == null) {
            return null;
        }
        
        // Récupérer les infos du shop avec fallback en une seule opération
        Player player = (Player) view.getPlayer();
        SimpleEntry<String, String> shopInfo = openShopMap.get(player.getUniqueId());
        
        // Fallback sur lastShopMap si nécessaire
        if (shopInfo == null) {
            shopInfo = lastShopMap.get(player.getUniqueId());
        }
        
        if (shopInfo == null) {
            return null;
        }
        
        // Créer l'objet résultat en une seule opération
        return new AmountSelectionInfo(
            shopInfo.getKey(),
            shopInfo.getValue(),
            centerItem.clone(),  // Cloner l'item pour éviter les modifications accidentelles
            isBuying,
            menuType,
            slotValues
        );
    }

    /**
     * Formate un temps en millisecondes en une chaîne lisible selon le type de limite
     * @param millisRemaining Temps restant en millisecondes
     * @param limit La limite de transaction pour déterminer le format
     * @return Chaîne formatée (ex: "04.03.2023 00:00:00" ou "01h 30m 45s")
     */
    private String formatTimeRemaining(long millisRemaining, TransactionLimit limit) {
        if (millisRemaining <= 0) {
            return "∞";
        }
        
        // Si c'est une période prédéfinie (DAILY, WEEKLY, etc.), afficher la date complète
        LimitPeriod period = limit.getPeriodEquivalent();
        if (period != LimitPeriod.NONE) {
            LocalDateTime resetTime = LocalDateTime.now().plus(millisRemaining, ChronoUnit.MILLIS);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
            return resetTime.format(formatter);
        }
        
        // Sinon c'est un cooldown numérique, on affiche juste le temps restant
        long secondsRemaining = millisRemaining / 1000;
        long hours = secondsRemaining / 3600;
        long minutes = (secondsRemaining % 3600) / 60;
        long seconds = secondsRemaining % 60;
        
        if (hours > 0) {
            return String.format("%02dh %02dm %02ds", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%02dm %02ds", minutes, seconds);
        } else {
            return String.format("%02ds", seconds);
        }
    }

    /**
     * Arrête proprement toutes les tâches et nettoie les ressources
     */
    public void shutdown() {
        // Annuler toutes les tâches de rafraîchissement
        for (BukkitTask task : playerRefreshBukkitTasks.values()) {
            if (task != null) task.cancel();
        }
        for (BukkitTask task : playerSelectionRefreshBukkitTasks.values()) {
            if (task != null) task.cancel();
        }
        
        // Vider toutes les maps
        playerRefreshTasks.clear();
        playerRefreshBukkitTasks.clear();
        playerSelectionRefreshTasks.clear();
        playerSelectionRefreshBukkitTasks.clear();
        openShopMap.clear();
        lastShopMap.clear();
        amountSelectionMenus.clear();
        pendingBulkMenuOpens.clear();
    }
}