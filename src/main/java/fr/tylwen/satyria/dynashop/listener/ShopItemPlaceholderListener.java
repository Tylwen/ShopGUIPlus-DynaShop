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

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ShopItemPlaceholderListener implements Listener {
    
    private final DynaShopPlugin plugin;
    
    // Map pour stocker le shop actuellement ouvert par chaque joueur
    private final Map<UUID, SimpleEntry<String, String>> openShopMap = new ConcurrentHashMap<>();
    private final Map<UUID, AmountSelectionInfo> amountSelectionMenus = new ConcurrentHashMap<>();
    private final Map<UUID, SimpleEntry<String, String>> lastShopMap = new ConcurrentHashMap<>();

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

        // Constructeurs de compatibilité
        public AmountSelectionInfo(String shopId, String itemId, ItemStack itemStack, boolean isBuying, String menuType) {
            this(shopId, itemId, itemStack, isBuying, menuType, null);
        }

        // Ajouter un constructeur de compatibilité pour l'existant
        public AmountSelectionInfo(String shopId, String itemId, ItemStack itemStack, boolean isBuying) {
            this(shopId, itemId, itemStack, isBuying, "AMOUNT_SELECTION", null);
        }

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
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getPlayer();
        InventoryView view = event.getView();

        String fullShopId = determineShopId(view);
        if (fullShopId == null) return;
        
        // Traitement spécial pour les menus de sélection de quantité
        if (fullShopId.equals("AMOUNT_SELECTION") || fullShopId.equals("AMOUNT_SELECTION_BULK")) {
            // DynaShopPlugin.getInstance().getLogger().info("TEST 1: " + fullShopId);
            AmountSelectionInfo info = extractAmountSelectionInfo(view, fullShopId);
            if (info == null) {
                // DynaShopPlugin.getInstance().getLogger().warning("Failed to extract amount selection info for player: " + player.getName());
                return;
            }
            // if (info == null) return;
            
            // Stocker les informations pour ce menu
            amountSelectionMenus.put(player.getUniqueId(), info);
            
            // IMPORTANT: Pré-traitement pour masquer les placeholders pendant le chargement
            Map<Integer, List<String>> originalLores = new HashMap<>();
            
            // Traiter tous les boutons dans l'inventaire
            for (int slot = 0; slot < view.getTopInventory().getSize(); slot++) {
                ItemStack item = view.getTopInventory().getItem(slot);
                if (item != null && item.hasItemMeta() && item.getItemMeta().hasLore()) {
                    ItemMeta meta = item.getItemMeta();
                    List<String> lore = meta.getLore();
                    
                    // Stocker le lore original et pré-traiter
                    originalLores.put(slot, new ArrayList<>(lore));
                    List<String> tempLore = preProcessPlaceholders(lore);
                    meta.setLore(tempLore);
                    item.setItemMeta(meta);
                }
            }
            // DynaShopPlugin.getInstance().getLogger().info("Detected amount selection menu for item: " + info.getItemId());
            
            // Mettre à jour les prix dans les boutons
            updateAmountSelectionInventory(player, view, info, originalLores);
            
            // Démarrer l'actualisation continue
            startContinuousAmountSelectionRefresh(player, view, info, originalLores);
            return;
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
        // DynaShopPlugin.getInstance().getLogger().info("TEST 2: " + event.getView().getTitle());
        
        Player player = (Player) event.getWhoClicked();
        // Obtenir le shopId actuel
        SimpleEntry<String, String> shopData = openShopMap.get(player.getUniqueId());
        if (shopData == null) return;
        // DynaShopPlugin.getInstance().getLogger().info("TEST 3: " + shopData.getKey());
        
        String shopId = shopData.getKey();
        if (shopId == null) return;
        // DynaShopPlugin.getInstance().getLogger().info("TEST 4: " + shopId);
        
        // Vérifier si c'est un click dans l'inventaire du haut (le shop)
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        
        // Récupérer l'item cliqué
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;
        // DynaShopPlugin.getInstance().getLogger().info("TEST 5: " + clickedItem.getType().name());
        try {
            // Tenter de récupérer l'ID de l'item à partir du slot
            Shop shop = ShopGuiPlusApi.getPlugin().getShopManager().getShopById(shopId);
            if (shop == null) return;
            // DynaShopPlugin.getInstance().getLogger().info("TEST 6: " + shop.getName());
            
            int page = 1;
            if (shopData.getKey().contains("#")) {
                String[] parts = shopData.getKey().split("#");
                try {
                    page = Integer.parseInt(parts[1]);
                } catch (NumberFormatException e) {
                    page = 1;
                }
            }
            
            ShopItem shopItem = shop.getShopItem(page, event.getSlot());
            if (shopItem != null) {
                // DynaShopPlugin.getInstance().getLogger().info("TEST 7: " + openShopMap.get(player.getUniqueId()).getValue());
                // Mettre à jour l'itemId dans la map
                openShopMap.put(player.getUniqueId(), new SimpleEntry<>(shopId, shopItem.getId()));
                // DynaShopPlugin.getInstance().getLogger().info("TEST 8: " + openShopMap.get(player.getUniqueId()).getValue());
            }
        } catch (Exception e) {
            // Ignorer les erreurs - garder la dernière valeur connue
        }
    }


    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
            
        Player player = (Player) event.getPlayer();
        
        // AJOUT: Sauvegarder les informations du shop actuel avant de les supprimer
        SimpleEntry<String, String> currentShop = openShopMap.get(player.getUniqueId());
        if (currentShop != null && currentShop.getKey() != null) {
            lastShopMap.put(player.getUniqueId(), currentShop);
            // plugin.getLogger().info("Saving shop info for " + player.getName() + ": " + 
            //                     currentShop.getKey() + ":" + currentShop.getValue());
        }
        
        // NE PAS VIDER openShopMap immédiatement si c'est un menu de sélection
        // car il est probable qu'un nouveau menu s'ouvre tout de suite après
        String title = event.getView().getTitle();
        boolean isAmountSelection = true;
        if (title.contains(ChatColor.translateAlternateColorCodes('&', 
                ShopGuiPlusApi.getPlugin().getConfigLang().getConfig().getString("DIALOG.AMOUNTSELECTION.BUY.NAME").replace("%item%", ""))) ||
            title.contains(ChatColor.translateAlternateColorCodes('&', 
                ShopGuiPlusApi.getPlugin().getConfigLang().getConfig().getString("DIALOG.AMOUNTSELECTION.SELL.NAME").replace("%item%", ""))) ||
            title.contains(ChatColor.translateAlternateColorCodes('&', 
                ShopGuiPlusApi.getPlugin().getConfigLang().getConfig().getString("DIALOG.AMOUNTSELECTION.BULKBUY.NAME").replace("%item%", ""))) ||
            title.contains(ChatColor.translateAlternateColorCodes('&', 
                ShopGuiPlusApi.getPlugin().getConfigLang().getConfig().getString("DIALOG.AMOUNTSELECTION.BULKSELL.NAME").replace("%item%", "")))) {
            // C'est un menu de sélection de quantité, ne pas vider openShopMap
            isAmountSelection = false;
        }
        
        // Arrêter la tâche de refresh
        playerRefreshTasks.remove(player.getUniqueId());
        
        // Nettoyer les autres maps
        // Ne nettoyer openShopMap que si ce n'est pas un menu de sélection
        if (!isAmountSelection) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                openShopMap.remove(player.getUniqueId());
                DynaShopPlugin.getInstance().getLogger().info("Cleared openShopMap for player: " + player.getName());
            }, 20L); // 20 ticks de délai (1s) pour s'assurer qu'un nouveau menu n'est pas ouvert
        }
        // openShopMap.remove(player.getUniqueId());

        amountSelectionMenus.remove(player.getUniqueId());
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
            // Déterminer l'ID du shop et la page
            int pageValue = page;
            String shopIdValue = shopId;
            
            // Capturer les valeurs finales pour utilisation dans les lambdas
            final int finalPage = pageValue;
            final String finalShopId = shopIdValue;
            if (finalShopId == null) {
                return;
            }

            // Cache global des prix
            Map<String, Map<String, String>> priceCache = new HashMap<>();
            
            // Traiter chaque item individuellement avec un délai minimal entre chaque
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            // plugin.getServer().getScheduler().runTask(plugin, () -> {
                for (int i = 0; i < view.getTopInventory().getSize(); i++) {
                    final int slot = i;
                    
                    // Ne traiter que les slots qui avaient des placeholders originaux
                    if (!originalLores.containsKey(slot)) {
                        continue;
                    }
                    
                    // // Délai progressif très léger (1 tick par tranche de 5 slots)
                    // long delay = slot / 5;

                    // plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () -> {
                    plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                        try {
                            ItemStack item = view.getTopInventory().getItem(slot);
                            // if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasLore()) {
                            if (item == null || !item.hasItemMeta()) {
                                return;
                            }

                            // IMPORTANT: Utiliser le lore original pour la détection des placeholders
                            List<String> originalLore = originalLores.get(slot);
                            if (originalLore == null || !containsDynaShopPlaceholder(originalLore)) {
                                return;
                            }
                            
                            String itemId = null;
                            try {
                                itemId = ShopGuiPlusApi.getPlugin().getShopManager().getShopById(finalShopId).getShopItem(finalPage, slot).getId();
                                if (itemId == null) {
                                    return;
                                }
                                
                                // Traitement des prix
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
                                
                                // Mettre à jour l'item dans l'inventaire immédiatement
                                final ItemStack finalItem = item.clone();
                                
                                plugin.getServer().getScheduler().runTask(plugin, () -> {
                                    try {
                                        // Vérifier que l'inventaire est toujours ouvert avant de le mettre à jour
                                        if (player.isOnline() && player.getOpenInventory().equals(view)) {
                                            view.getTopInventory().setItem(slot, finalItem);
                                            
                                            // Forcer une mise à jour de l'inventaire pour ce seul item
                                            // Cela minimise la charge visuelle pour le joueur
                                            player.updateInventory();
                                        }
                                    } catch (Exception e) {
                                        // Ignorer les erreurs lors de la mise à jour
                                    }
                                });
                                
                            } catch (Exception e) {
                                // Ignorer les erreurs individuelles pour ne pas bloquer les autres items
                                // plugin.getLogger().warning("Erreur lors de la mise à jour de l'item " + itemId + ": " + e.getMessage());
                            }
                        } catch (Exception e) {
                            // Capturer toute exception pour éviter d'interrompre la tâche
                        }
                    // }, delay); // Délai progressif pour répartir la charge
                    });
                }
            });
        } catch (Exception e) {
            plugin.getLogger().warning("Error updating inventory: " + e.getMessage());
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
        // String cacheKey = shopId + ":" + itemId;
        
        // // Ajouter l'UUID du joueur au cache key pour que chaque joueur ait ses propres prix modifiés
        // if (player != null) {
        //     cacheKey += ":" + player.getUniqueId().toString();
        // }
        
        // Créer une clé unique incluant le joueur si nécessaire
        final String cacheKey = player != null
            ? shopId + ":" + itemId + ":" + player.getUniqueId().toString()
            : shopId + ":" + itemId;

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
        boolean isCriticalItem = criticalItems.contains(shopId + ":" + itemId);
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

            String buyPrice, sellPrice, buyMinPrice, buyMaxPrice, sellMinPrice, sellMaxPrice;

            DynamicPrice price = DynaShopPlugin.getInstance().getDynaShopListener().getOrLoadPrice(player, shopId, itemId, itemStack);
            if (price != null) {
                buyPrice = plugin.getPriceFormatter().formatPrice(price.getBuyPrice());
                sellPrice = plugin.getPriceFormatter().formatPrice(price.getSellPrice());
                buyMinPrice = plugin.getPriceFormatter().formatPrice(price.getMinBuyPrice());
                buyMaxPrice = plugin.getPriceFormatter().formatPrice(price.getMaxBuyPrice());
                sellMinPrice = plugin.getPriceFormatter().formatPrice(price.getMinSellPrice());
                sellMaxPrice = plugin.getPriceFormatter().formatPrice(price.getMaxSellPrice());
            } else {
                buyPrice = plugin.getPriceFormatter().getPriceByType(shopId, itemId, "buy");
                sellPrice = plugin.getPriceFormatter().getPriceByType(shopId, itemId, "sell");
                buyMinPrice = plugin.getPriceFormatter().getPriceByType(shopId, itemId, "buy_min");
                buyMaxPrice = plugin.getPriceFormatter().getPriceByType(shopId, itemId, "buy_max");
                sellMinPrice = plugin.getPriceFormatter().getPriceByType(shopId, itemId, "sell_min");
                sellMaxPrice = plugin.getPriceFormatter().getPriceByType(shopId, itemId, "sell_max");
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

            // Stocker les valeurs
            prices.put("buy", buyPrice);
            prices.put("sell", sellPrice);
            prices.put("buy_min", buyMinPrice);
            prices.put("buy_max", buyMaxPrice);
            prices.put("sell_min", sellMinPrice);
            prices.put("sell_max", sellMaxPrice);
            
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
                    // Sécuriser le parsing numérique
                    fCurrentStock = currentStock.equals("N/A") || currentStock.equals("-1") ? 
                        "N/A" : plugin.getPriceFormatter().formatStock(Integer.parseInt(currentStock));
                    
                    maxStock = plugin.getPriceFormatter().getStockByType(shopId, itemId, "stock_max");
                    // Sécuriser le parsing numérique
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
                            prices.put("colored_stock_ratio", ChatColor.translateAlternateColorCodes('&', 
                                String.format("&7%s", fCurrentStock)));
                        } else {
                            // // Calculer la couleur en fonction du ratio
                            // String colorCode = (current < max * 0.25) ? "&c" : (current < max * 0.5) ? "&e" : "&a";
                            // Calculer la couleur en fonction du ratio
                            String colorCode;
                            
                            // Déterminer le code couleur selon le niveau de stock
                            if (current < max * 0.25) {
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
                        // En cas d'erreur de conversion, utiliser un format simplifié
                        prices.put("colored_stock_ratio", ChatColor.translateAlternateColorCodes('&', 
                            String.format("&7%s/%s", fCurrentStock, fMaxStock)));
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
            if (!buyMinPrice.equals("N/A") && !buyMaxPrice.equals("N/A") &&
                (!buyMinPrice.equals(buyPrice) || !buyMaxPrice.equals(buyPrice))) {
                // Afficher la fourchette uniquement si min ou max diffère du prix actuel
                prices.put("base_buy", String.format(
                    currencyPrefix + "%s" + currencySuffix + " §7(%s - %s) ", 
                    buyPrice, buyMinPrice, buyMaxPrice
                ));
            } else {
                // Affichage simplifié quand min=max=prix actuel
                prices.put("base_buy", currencyPrefix + buyPrice + currencySuffix);
            }

            // Format pour le prix de vente avec min-max
            if (!sellMinPrice.equals("N/A") && !sellMaxPrice.equals("N/A") &&
                (!sellMinPrice.equals(sellPrice) || !sellMaxPrice.equals(sellPrice))) {
                // Afficher la fourchette uniquement si min ou max diffère du prix actuel
                prices.put("base_sell", String.format(
                    currencyPrefix + "%s" + currencySuffix + " §7(%s - %s) ", 
                    sellPrice, sellMinPrice, sellMaxPrice
                ));
            } else {
                // Affichage simplifié quand min=max=prix actuel
                prices.put("base_sell", currencyPrefix + sellPrice + currencySuffix);
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

    private AmountSelectionInfo extractAmountSelectionInfo(InventoryView view, String menuType) {
        String title = view.getTitle();
        // boolean isBuying = title.contains(ChatColor.translateAlternateColorCodes('&', ShopGuiPlusApi.getPlugin().getConfigLang().getConfig().getString("DIALOG.AMOUNTSELECTION.BUY.NAME").replace("%item%", ""))) ||
        //                 title.contains(ChatColor.translateAlternateColorCodes('&', ShopGuiPlusApi.getPlugin().getConfigLang().getConfig().getString("DIALOG.AMOUNTSELECTION.BULKBUY.NAME").replace("%item%", "")));
        
        boolean isBuying = title.contains(ChatColor.translateAlternateColorCodes('&', 
            ShopGuiPlusApi.getPlugin().getConfigLang().getConfig().getString("DIALOG.AMOUNTSELECTION.BUY.NAME").replace("%item%", ""))) ||
            title.contains(ChatColor.translateAlternateColorCodes('&', 
            ShopGuiPlusApi.getPlugin().getConfigLang().getConfig().getString("DIALOG.AMOUNTSELECTION.BULKBUY.NAME").replace("%item%", "")));
            
        // Déterminer l'emplacement de l'item selon le type de menu
        int centerSlot;
        int inventorySize = view.getTopInventory().getSize();
        Map<Integer, Integer> slotValues = new HashMap<>();

        // // Séparer clairement les types de menus
        // boolean isBulkMenu = title.contains(ChatColor.translateAlternateColorCodes('&', 
        //     ShopGuiPlusApi.getPlugin().getConfigLang().getConfig().getString("DIALOG.AMOUNTSELECTION.BULKBUY.NAME").replace("%item%", ""))) ||
        //     title.contains(ChatColor.translateAlternateColorCodes('&', 
        //     ShopGuiPlusApi.getPlugin().getConfigLang().getConfig().getString("DIALOG.AMOUNTSELECTION.BULKSELL.NAME").replace("%item%", "")));
        
        // boolean isRegularMenu = title.contains(ChatColor.translateAlternateColorCodes('&', 
        //     ShopGuiPlusApi.getPlugin().getConfigLang().getConfig().getString("DIALOG.AMOUNTSELECTION.BUY.NAME").replace("%item%", ""))) ||
        //     title.contains(ChatColor.translateAlternateColorCodes('&', 
        //     ShopGuiPlusApi.getPlugin().getConfigLang().getConfig().getString("DIALOG.AMOUNTSELECTION.SELL.NAME").replace("%item%", "")));
        
        // Utiliser exactement votre approche existante avec la config
        if (menuType.equals("AMOUNT_SELECTION_BULK")) {
            // Traitement différent selon si c'est achat ou vente
            if (isBuying) {
                // Configuration pour BULK BUY
                for (int i = 1; i <= 9; i++) {
                    if (ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().contains("amountSelectionGUIBulkBuy.buttons.buy" + i + ".slot")) {
                        int slot = ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getInt("amountSelectionGUIBulkBuy.buttons.buy" + i + ".slot");
                        int value = ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getInt("amountSelectionGUIBulkBuy.buttons.buy" + i + ".value");
                        slotValues.put(slot, value);
                    }
                }
                centerSlot = ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getInt("amountSelectionGUIBulkBuy.buttons.buy1.slot", 0);
            } else {
                // Configuration pour BULK SELL
                for (int i = 1; i <= 9; i++) {
                    if (ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().contains("amountSelectionGUIBulkSell.buttons.sell" + i + ".slot")) {
                        int slot = ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getInt("amountSelectionGUIBulkSell.buttons.sell" + i + ".slot");
                        int value = ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getInt("amountSelectionGUIBulkSell.buttons.sell" + i + ".value");
                        slotValues.put(slot, value);
                    }
                }
                centerSlot = ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getInt("amountSelectionGUIBulkSell.buttons.sell1.slot", 0);
            }
        } else {
            // Pour les menus standard, utiliser la valeur configurée ou 22 par défaut
            centerSlot = ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getInt("amountSelectionGUI.itemSlot", 22);
        }
        
        // // Vérification de sécurité pour éviter les erreurs d'index
        // if (centerSlot >= inventorySize) {
        //     plugin.getLogger().warning("Center slot " + centerSlot + " is out of bounds for inventory size " + 
        //         inventorySize + " in menu type " + menuType + ". Using fallback slot.");
            
        //     // Utiliser un slot de repli sûr
        //     centerSlot = Math.min(4, inventorySize - 1); // Position 4 ou dernière position
        // }
        
        // Récupérer l'item à la position calculée
        ItemStack centerItem = view.getTopInventory().getItem(centerSlot);
        if (centerItem == null) {
            plugin.getLogger().warning("Center item is null in amount selection inventory at slot " + centerSlot);
            return null;
        }

        // // Récupérer l'item central (habituellement en position 23)
        // // ItemStack centerItem = view.getTopInventory().getItem(23); // 23 est le slot central dans un inventaire de 54 slots
        // // int size = view.getTopInventory().getSize();
        // // int rows = size / 9; // Nombre de lignes
        // // int centerSlot = (rows * 9) / 2 + (rows % 2 == 0 ? -1 : 0); // Slot central pour un inventaire de taille variable
        // // int centerSlot = (rows * 9) / 4 + (rows % 2 == 0 ? -1 : 0); // Slot central pour un inventaire de taille variable, ajusté pour 54 slots
        // int centerSlot = ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getInt("amountSelectionGUI.itemSlot", 22); // Slot central par défaut
        // ItemStack centerItem = view.getTopInventory().getItem(centerSlot);
        // if (centerItem == null) {
        //     // plugin.getLogger().warning("Center item is null in amount selection inventory for player: " + view.getPlayer().getName() +
        //     //     ". Inventory size: " + size + ", Rows: " + rows + ", Center Slot: " + centerSlot);
        //     return null;
        // }
        
        // // Récupérer les dernières informations du shop ouvert par le joueur
        // Player player = (Player) view.getPlayer();
        // SimpleEntry<String, String> lastShopInfo = openShopMap.get(player.getUniqueId());
        // DynaShopPlugin.getInstance().getLogger().info("openShopMap for player " + player.getName() + ": " + openShopMap.get(player.getUniqueId()));
        // DynaShopPlugin.getInstance().getLogger().info("Last shop info for player " + player.getName() + ": " + lastShopInfo + 
        //     ", Shop ID: " + (lastShopInfo != null ? lastShopInfo.getKey() : "null") + 
        //     ", Item ID: " + (lastShopInfo != null ? lastShopInfo.getValue() : "null"));
        // if (lastShopInfo == null) {
        //     plugin.getLogger().warning("No last shop info found for player: " + player.getName());
        //     return null;
        // }
        
        // Récupérer les dernières informations du shop (d'abord openShopMap, puis lastShopMap)
        // Récupérer les dernières informations du shop (d'abord openShopMap, puis lastShopMap)
        Player player = (Player) view.getPlayer();
        SimpleEntry<String, String> shopInfo = openShopMap.get(player.getUniqueId());
        
        // Si aucune info dans openShopMap, essayer lastShopMap
        if (shopInfo == null) {
            shopInfo = lastShopMap.get(player.getUniqueId());
            // plugin.getLogger().info("Using lastShopMap for player " + player.getName() + ": " + shopInfo);
        }
        
        if (shopInfo == null) {
            // plugin.getLogger().warning("No shop info found for player: " + player.getName());
            return null;
        }
        
        return new AmountSelectionInfo(
            shopInfo.getKey(),         // shopId
            shopInfo.getValue(),       // itemId
            centerItem,                // itemStack
            isBuying,                   // isBuying
            menuType,                  // menuType
            slotValues                 // slotValues - La map des slots et valeurs
        );
    }

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

    private void startContinuousAmountSelectionRefresh(Player player, InventoryView view, AmountSelectionInfo info, Map<Integer, List<String>> originalLores, String menuType) {
        // ID unique pour cette session de refresh
        final UUID refreshId = UUID.randomUUID();
        
        // Stocker l'ID du refresh dans une map pour pouvoir l'arrêter plus tard
        playerRefreshTasks.put(player.getUniqueId(), refreshId);
        
        // Variables pour suivre les changements
        final int[] lastCenterQuantity = {info.getItemStack().getAmount()};
        // DynaShopPlugin.getInstance().getLogger().info("Starting continuous refresh for player: " + player.getName() + 
        //     ", Shop ID: " + info.getShopId() + ", Item ID: " + info.getItemId() + 
        //     ", Initial quantity: " + lastCenterQuantity[0]);
        
        // Démarrer la tâche asynchrone avec boucle
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Temps d'attente entre les vérifications (en ms)
                long refreshInterval = 100; // 100ms pour une détection rapide

                // Flag pour suivre si le joueur est toujours dans le menu de sélection
                boolean stillInMenu = true;

                while (
                    player.isOnline() && 
                    stillInMenu &&
                    player.getOpenInventory() != null &&
                    determineShopId(player.getOpenInventory()) != null &&
                    determineShopId(player.getOpenInventory()).equals(menuType)
                    // determineShopId(player.getOpenInventory()).equals("AMOUNT_SELECTION")
                    // player.getOpenInventory() != null
                    // determineShopId(player.getOpenInventory()) != null &&
                    // determineShopId(player.getOpenInventory()).equals("AMOUNT_SELECTION") &&
                    // playerRefreshTasks.get(player.getUniqueId()) == refreshId
                ) {
                    // Attendre l'intervalle configuré
                    Thread.sleep(refreshInterval);
                    
                    // Vérifier à nouveau que le joueur est toujours en ligne et que l'inventaire est ouvert
                    if (!player.isOnline() || player.getOpenInventory() == null) {
                        stillInMenu = false;
                        break;
                    }
                    
                    // // Vérifier si le menu ouvert est toujours un menu de sélection
                    // String currentTitle = player.getOpenInventory().getTitle();
                    // if (!determineShopId(player.getOpenInventory()).equals("AMOUNT_SELECTION")) {
                    //     DynaShopPlugin.getInstance().getLogger().info("Player " + player.getName() + " no longer in amount selection menu: " + currentTitle);
                    //     stillInMenu = false;
                    //     break;
                    // }
                    
                    // Vérifier si l'item central a changé (sur le thread principal)
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        // if (!player.isOnline() || player.getOpenInventory() == null) return;
                        // if (player.isOnline() && player.getOpenInventory() != null) {
                        // int centerSlot;

                        if (menuType.equals("AMOUNT_SELECTION_BULK")) {
                            // if (info.isBuying()) {
                            //     // Pour les menus BULK BUY, utiliser la valeur configurée ou 17 par défaut
                            //     centerSlot = ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getInt("amountSelectionGUIBulkBuy.itemSlot", 17);
                            // } else {
                            //     // Pour les menus BULK SELL, utiliser la valeur configurée ou 26 par défaut
                            //     centerSlot = ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getInt("amountSelectionGUIBulkSell.itemSlot", 17);
                            // }
                            updateAmountSelectionInventoryImmediately(player, player.getOpenInventory(), info, originalLores);
                        } else {
                            // Pour les menus standard, utiliser la valeur configurée ou 22 par défaut
                            int centerSlot = ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getInt("amountSelectionGUI.itemSlot", 22);
                        // }
                        // if (menuType.equals("AMOUNT_SELECTION_BULK")) {
                        //     Map<Integer, Integer> slot = new HashMap<>();
                            
                        //     // Traitement différent selon si c'est achat ou vente
                        //     if (info.isBuying()) {
                        //         // Configuration pour BULK BUY
                        //         for (int i = 1; i <= 9; i++) {
                        //             if (ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().contains("amountSelectionGUIBulkBuy.buttons.buy" + i + ".slot")) {
                        //                 slot.put(ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getInt("amountSelectionGUIBulkBuy.buttons.buy" + i + ".slot"), 
                        //                         ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getInt("amountSelectionGUIBulkBuy.buttons.buy" + i + ".value"));
                        //             }
                        //         }
                        //         centerSlot = ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getInt("amountSelectionGUIBulkBuy.buttons.buy1.slot", 0);
                        //     } else {
                        //         // Configuration pour BULK SELL
                        //         for (int i = 1; i <= 9; i++) {
                        //             if (ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().contains("amountSelectionGUIBulkSell.buttons.sell" + i + ".slot")) {
                        //                 slot.put(ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getInt("amountSelectionGUIBulkSell.buttons.sell" + i + ".slot"), 
                        //                         ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getInt("amountSelectionGUIBulkSell.buttons.sell" + i + ".value"));
                        //             }
                        //         }
                        //         centerSlot = ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getInt("amountSelectionGUIBulkSell.buttons.sell1.slot", 0);
                        //     }
                        // } else {
                        //     // Pour les menus standard, utiliser la valeur configurée ou 22 par défaut
                        //     centerSlot = ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getInt("amountSelectionGUI.itemSlot", 22);
                        // }
                            
                            // Trouver l'item central actuel
                            // int size = player.getOpenInventory().getTopInventory().getSize();
                            // int size = ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getInt("amountSelectionGUI.size", 54);
                            // int rows = size / 9;
                            // int centerSlot = (rows * 9) / 4 + (rows % 2 == 0 ? -1 : 0); // Slot central pour un inventaire de taille variable, ajusté pour 54 slots
                            // Slot central pour un inventaire de taille variable de 9 à 54 slots
                            // int centerSlot = ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getInt("amountSelectionGUI.itemSlot", 22); // Slot central par défaut
                            ItemStack currentCenterItem = player.getOpenInventory().getTopInventory().getItem(centerSlot);
                            // DynaShopPlugin.getInstance().getLogger().info("Checking center item for player: " + player.getName() + 
                            //     ", Current center item: " + (currentCenterItem != null ? currentCenterItem.toString() : "null") + 
                            //     ", Last quantity: " + lastCenterQuantity[0]);
                            // if (currentCenterItem == null) return;
                            if (currentCenterItem != null) {
                                // DynaShopPlugin.getInstance().getLogger().warning("Center item is null for player: " + player.getName() + 
                                //     ". Inventory size: " + player.getOpenInventory().getTopInventory().getSize() + 
                                //     ", Center Slot: " + centerSlot);
                                // return;
                            
                                // Vérifier si la quantité a changé
                                int currentQuantity = currentCenterItem.getAmount();
                                // DynaShopPlugin.getInstance().getLogger().info("Current center item quantity for player: " + player.getName() + 
                                //     ", Current quantity: " + currentQuantity + 
                                //     ", Last quantity: " + lastCenterQuantity[0]);
                                if (currentQuantity != lastCenterQuantity[0]) {
                                    // La quantité a changé, mettre à jour les prix dans l'interface
                                    lastCenterQuantity[0] = currentQuantity;
                                    // DynaShopPlugin.getInstance().getLogger().info("Center item quantity changed for player: " + player.getName() + 
                                    //     ", New quantity: " + currentQuantity);
                                    
                                    // Mettre à jour l'ItemStack dans l'info avec la nouvelle quantité
                                    AmountSelectionInfo newInfo = new AmountSelectionInfo(
                                        info.getShopId(),
                                        info.getItemId(),
                                        currentCenterItem, // avec la nouvelle quantité
                                        info.isBuying(),
                                        menuType,
                                        info.getSlotValues() // conserver les valeurs de slot
                                    );
                                    
                                    // Actualiser immédiatement les prix dans tous les boutons
                                    updateAmountSelectionInventoryImmediately(player, player.getOpenInventory(), newInfo, originalLores);
                                }
                            }
                            // DynaShopPlugin.getInstance().getLogger().info("Center item quantity is unchanged for player: " + player.getName() + 
                            //     ", Quantity: " + currentQuantity);
                        }
                    });
                }
            } catch (InterruptedException e) {
                DynaShopPlugin.getInstance().getLogger().warning("Continuous refresh interrupted for player: " + player.getName() + " - " + e.getMessage());
                Thread.currentThread().interrupt();
            } finally {
                DynaShopPlugin.getInstance().getLogger().info("Stopping continuous refresh for player: " + player.getName());
                // Nettoyer
                playerRefreshTasks.remove(player.getUniqueId());
                amountSelectionMenus.remove(player.getUniqueId());
            }
        });
    }

    /**
     * Mise à jour immédiate de l'inventaire avec la nouvelle quantité
     */
    private void updateAmountSelectionInventoryImmediately(Player player, InventoryView view, AmountSelectionInfo info, Map<Integer, List<String>> originalLores) {
        if (view == null || view.getTopInventory() == null) return;
        
        try {
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

            // Obtenir les prix de base pour l'item
            Map<String, String> basePrices = getCachedPrices(
                player, 
                info.getShopId(), 
                info.getItemId(), 
                info.getItemStack(),
                true // forceRefresh pour avoir les prix les plus récents
            );
            
            // Parcourir tous les boutons de l'inventaire
            for (int slot = 0; slot < view.getTopInventory().getSize(); slot++) {
                ItemStack button = view.getTopInventory().getItem(slot);
                if (button == null || !button.hasItemMeta() || !button.getItemMeta().hasLore()) {
                    continue;
                }
                
                // // Récupérer la quantité du bouton
                // int quantity = button.getAmount();

                // Déterminer la quantité à utiliser pour ce slot spécifique
                int quantity;
                
                if (info.getMenuType().equals("AMOUNT_SELECTION_BULK")) {
                    // Pour les menus BULK, utiliser la valeur de stack à partir de la map
                    int stackValue = info.getValueForSlot(slot);
                    DynaShopPlugin.getInstance().getLogger().info("Using stack value for slot " + slot + ": " + stackValue);
                    // quantity = stackValue * 64; // Multiplier par 64 pour obtenir le nombre total d'items
                    quantity = stackValue * button.getMaxStackSize(); // Utiliser la taille maximale de stack pour le calcul
                } else {
                    // Pour les menus standard, utiliser la quantité affichée sur l'item central
                    quantity = info.getItemStack().getAmount();
                }
                
                // Récupérer le lore original si disponible, sinon utiliser le lore actuel
                List<String> originalLore = originalLores.containsKey(slot) ? 
                                        originalLores.get(slot) : 
                                        button.getItemMeta().getLore();
                
                if (originalLore == null || originalLore.isEmpty() || !containsDynaShopPlaceholder(originalLore)) {
                    continue;
                }
                
                // Créer une copie des prix pour cet item spécifique avec cette quantité
                Map<String, String> adjustedPrices = adjustPricesForQuantity(basePrices, quantity);
                
                // Appliquer les remplacements
                List<String> newLore = replacePlaceholders(originalLore, adjustedPrices, player);
                ItemMeta meta = button.getItemMeta();
                meta.setLore(newLore);
                button.setItemMeta(meta);
            }
            
            // Forcer la mise à jour de l'inventaire pour le joueur
            player.updateInventory();
            
        } catch (Exception e) {
            plugin.getLogger().warning("Error updating amount selection inventory: " + e.getMessage());
        }
    }

    private void updateAmountSelectionInventory(Player player, InventoryView view, AmountSelectionInfo info, Map<Integer, List<String>> originalLores) {
        if (view == null || view.getTopInventory() == null) { return; }

        try {
            // Cache global des prix pour cet item
            final Map<String, String> basePrices = getCachedPrices(
                player, 
                info.getShopId(), 
                info.getItemId(), 
                info.getItemStack(), 
                true // forceRefresh
            );
            
            // Traiter chaque bouton individuellement
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                for (int slot = 0; slot < view.getTopInventory().getSize(); slot++) {
                    final int currentSlot = slot;
                    
                    // Ne traiter que les slots qui avaient des placeholders originaux
                    if (!originalLores.containsKey(currentSlot)) {
                        continue;
                    }
                    
                    plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                        try {
                            ItemStack button = view.getTopInventory().getItem(currentSlot);
                            if (button == null || !button.hasItemMeta() || !button.getItemMeta().hasLore()) {
                                return;
                            }
                            
                            // Récupérer la quantité associée au bouton
                            int quantity = button.getAmount();
                            
                            // Créer une copie des prix pour cet item spécifique avec cette quantité
                            Map<String, String> adjustedPrices = adjustPricesForQuantity(basePrices, quantity);

                            // Utiliser le lore original pour la détection des placeholders
                            List<String> originalLore = originalLores.get(currentSlot);
                            if (originalLore == null || originalLore.isEmpty()) {
                                return;
                            }
                            
                            // Appliquer les remplacements
                            List<String> newLore = replacePlaceholders(originalLore, adjustedPrices, player);
                            ItemMeta meta = button.getItemMeta();
                            meta.setLore(newLore);
                            button.setItemMeta(meta);
                            
                            // Mettre à jour l'item dans l'inventaire immédiatement
                            final ItemStack finalButton = button.clone();
                            
                            plugin.getServer().getScheduler().runTask(plugin, () -> {
                                try {
                                    // Vérifier que l'inventaire est toujours ouvert avant de le mettre à jour
                                    if (player.isOnline() && player.getOpenInventory().equals(view)) {
                                        view.getTopInventory().setItem(currentSlot, finalButton);
                                        player.updateInventory();
                                    }
                                } catch (Exception e) {
                                    // Ignorer les erreurs lors de la mise à jour
                                }
                            });
                            
                        } catch (Exception e) {
                            // Capturer toute exception pour éviter d'interrompre la tâche
                        }
                    });
                }
            });
        } catch (Exception e) {
            plugin.getLogger().warning("Error updating amount selection inventory: " + e.getMessage());
        }
    }

    /**
     * Ajuste les prix en fonction de la quantité
     * @param basePrices Prix de base (unitaires)
     * @param quantity Quantité d'items
     * @return Prix ajustés pour la quantité spécifiée
     */
    private Map<String, String> adjustPricesForQuantity(Map<String, String> basePrices, int quantity) {
        if (quantity <= 1) {
            // Pour la quantité 1, pas besoin d'ajuster
            return new HashMap<>(basePrices);
        }
        
        Map<String, String> adjustedPrices = new HashMap<>();
        String currencyPrefix = "";
        String currencySuffix = " $";
        
        // Récupérer les préfixes et suffixes de monnaie
        if (basePrices.get("base_buy") != null) {
            String baseBuy = basePrices.get("base_buy");
            if (baseBuy.contains("$")) {
                int dollarIndex = baseBuy.indexOf("$");
                // Chercher le préfixe (tout avant le premier chiffre)
                for (int i = 0; i < dollarIndex; i++) {
                    if (!Character.isDigit(baseBuy.charAt(i)) && baseBuy.charAt(i) != '.') {
                        currencyPrefix += baseBuy.charAt(i);
                    } else {
                        break;
                    }
                }
                // Le suffixe est " $" ou similaire
                currencySuffix = baseBuy.substring(baseBuy.indexOf("$"));
                if (currencySuffix.contains(" ")) {
                    currencySuffix = currencySuffix.substring(0, currencySuffix.indexOf(" ") + 1) + "$";
                }
            }
        }
        
        // Copier les valeurs non-numériques telles quelles
        for (Map.Entry<String, String> entry : basePrices.entrySet()) {
            if (!isNumericValue(entry.getKey())) {
                adjustedPrices.put(entry.getKey(), entry.getValue());
            }
        }
        
        // Ajuster les prix numériques en multipliant par la quantité
        try {
            // Prix d'achat
            if (isValidNumeric(basePrices.get("buy"))) {
                double buyPrice = parseFormattedNumber(basePrices.get("buy"));
                double totalBuyPrice = buyPrice * quantity;
                adjustedPrices.put("buy", plugin.getPriceFormatter().formatPrice(totalBuyPrice));
            } else {
                adjustedPrices.put("buy", basePrices.get("buy"));
            }
            
            // Prix de vente
            if (isValidNumeric(basePrices.get("sell"))) {
                double sellPrice = parseFormattedNumber(basePrices.get("sell"));
                double totalSellPrice = sellPrice * quantity;
                adjustedPrices.put("sell", plugin.getPriceFormatter().formatPrice(totalSellPrice));
            } else {
                adjustedPrices.put("sell", basePrices.get("sell"));
            }
            
            // Prix min/max d'achat
            if (isValidNumeric(basePrices.get("buy_min"))) {
                double minBuyPrice = parseFormattedNumber(basePrices.get("buy_min"));
                double totalMinBuyPrice = minBuyPrice * quantity;
                adjustedPrices.put("buy_min", plugin.getPriceFormatter().formatPrice(totalMinBuyPrice));
            } else {
                adjustedPrices.put("buy_min", basePrices.get("buy_min"));
            }
            
            if (isValidNumeric(basePrices.get("buy_max"))) {
                double maxBuyPrice = parseFormattedNumber(basePrices.get("buy_max"));
                double totalMaxBuyPrice = maxBuyPrice * quantity;
                adjustedPrices.put("buy_max", plugin.getPriceFormatter().formatPrice(totalMaxBuyPrice));
            } else {
                adjustedPrices.put("buy_max", basePrices.get("buy_max"));
            }
            
            // Prix min/max de vente
            if (isValidNumeric(basePrices.get("sell_min"))) {
                double minSellPrice = parseFormattedNumber(basePrices.get("sell_min"));
                double totalMinSellPrice = minSellPrice * quantity;
                adjustedPrices.put("sell_min", plugin.getPriceFormatter().formatPrice(totalMinSellPrice));
            } else {
                adjustedPrices.put("sell_min", basePrices.get("sell_min"));
            }
            
            if (isValidNumeric(basePrices.get("sell_max"))) {
                double maxSellPrice = parseFormattedNumber(basePrices.get("sell_max"));
                double totalMaxSellPrice = maxSellPrice * quantity;
                adjustedPrices.put("sell_max", plugin.getPriceFormatter().formatPrice(totalMaxSellPrice));
            } else {
                adjustedPrices.put("sell_max", basePrices.get("sell_max"));
            }
            
            // Ajuster les formats de présentation
            if (adjustedPrices.containsKey("buy") && adjustedPrices.containsKey("buy_min") && adjustedPrices.containsKey("buy_max") &&
                !adjustedPrices.get("buy").equals("N/A") && !adjustedPrices.get("buy_min").equals("N/A") && !adjustedPrices.get("buy_max").equals("N/A") &&
                (!adjustedPrices.get("buy_min").equals(adjustedPrices.get("buy")) || !adjustedPrices.get("buy_max").equals(adjustedPrices.get("buy")))) {
                adjustedPrices.put("base_buy", String.format(
                    currencyPrefix + "%s" + currencySuffix + " §7(%s - %s) ", 
                    adjustedPrices.get("buy"), adjustedPrices.get("buy_min"), adjustedPrices.get("buy_max")
                ));
            } else if (adjustedPrices.containsKey("buy")) {
                adjustedPrices.put("base_buy", currencyPrefix + adjustedPrices.get("buy") + currencySuffix);
            }
            
            if (adjustedPrices.containsKey("sell") && adjustedPrices.containsKey("sell_min") && adjustedPrices.containsKey("sell_max") &&
                !adjustedPrices.get("sell").equals("N/A") && !adjustedPrices.get("sell_min").equals("N/A") && !adjustedPrices.get("sell_max").equals("N/A") &&
                (!adjustedPrices.get("sell_min").equals(adjustedPrices.get("sell")) || !adjustedPrices.get("sell_max").equals(adjustedPrices.get("sell")))) {
                adjustedPrices.put("base_sell", String.format(
                    currencyPrefix + "%s" + currencySuffix + " §7(%s - %s) ", 
                    adjustedPrices.get("sell"), adjustedPrices.get("sell_min"), adjustedPrices.get("sell_max")
                ));
            } else if (adjustedPrices.containsKey("sell")) {
                adjustedPrices.put("base_sell", currencyPrefix + adjustedPrices.get("sell") + currencySuffix);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error adjusting prices for quantity " + quantity + ": " + e.getMessage());
            return basePrices; // En cas d'erreur, retourner les prix de base
        }
        
        return adjustedPrices;
    }

    /**
     * Vérifie si la clé correspond à une valeur numérique
     */
    private boolean isNumericValue(String key) {
        return key.equals("buy") || key.equals("sell") || 
            key.equals("buy_min") || key.equals("buy_max") || 
            key.equals("sell_min") || key.equals("sell_max");
    }

    /**
     * Vérifie si une chaîne de caractères représente un nombre valide
     */
    private boolean isValidNumeric(String value) {
        return value != null && !value.equals("N/A") && !value.equals("-1");
    }

    /**
     * Convertit une chaîne formatée (ex: "1,234.56") en nombre
     */
    private double parseFormattedNumber(String formatted) {
        if (formatted == null || formatted.equals("N/A")) {
            return 0.0;
        }
        
        try {
            // Supprimer tous les caractères non numériques sauf le point décimal
            String cleaned = "";
            boolean hasDecimal = false;
            
            for (char c : formatted.toCharArray()) {
                if (Character.isDigit(c)) {
                    cleaned += c;
                } else if (c == '.' && !hasDecimal) {
                    cleaned += c;
                    hasDecimal = true;
                }
            }
            
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            plugin.getLogger().warning("Could not parse number from: " + formatted);
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