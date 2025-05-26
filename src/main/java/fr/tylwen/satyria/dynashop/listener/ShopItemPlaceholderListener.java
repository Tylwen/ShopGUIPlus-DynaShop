package fr.tylwen.satyria.dynashop.listener;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.inventory.InventoryView;

import fr.tylwen.satyria.dynashop.DynaShopPlugin;
import fr.tylwen.satyria.dynashop.data.DynamicPrice;
import fr.tylwen.satyria.dynashop.data.param.DynaShopType;
import me.clip.placeholderapi.PlaceholderAPI;
import net.brcdev.shopgui.shop.Shop;
import net.brcdev.shopgui.ShopGuiPlusApi;

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

    private BukkitTask refreshTask;

    private final Map<String, Map<String, String>> globalPriceCache = new ConcurrentHashMap<>();
    private final Map<String, Long> cacheTimestamps = new ConcurrentHashMap<>();
    private static final long CACHE_EXPIRY = 20; // 20 ticks (1 seconde)
    private final Map<UUID, UUID> playerRefreshTasks = new ConcurrentHashMap<>();
    
    public ShopItemPlaceholderListener(DynaShopPlugin plugin) {
        this.plugin = plugin;

        // // Démarrer le planificateur de rafraîchissement
        // startRefreshScheduler();
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

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player))
            return;
            
        Player player = (Player) event.getPlayer();
        
        // Arrêter la tâche de refresh
        playerRefreshTasks.remove(player.getUniqueId());
        
        // Nettoyer les autres maps
        openShopMap.remove(player.getUniqueId());
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
                    
                    // Délai progressif très léger (1 tick par tranche de 5 slots)
                    long delay = slot / 5;

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
                                    itemPrices = getCachedPrices(finalShopId, itemId, item, false);
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
                    } catch (Throwable t) {
                        // Ignorer les erreurs de PlaceholderAPI et conserver le texte original
                        // plugin.getLogger().warning("Erreur lors de l'utilisation de PlaceholderAPI: " + t.getMessage());
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
    private Map<String, String> getCachedPrices(String shopId, String itemId, ItemStack itemStack, boolean forceRefresh) {
        String cacheKey = shopId + ":" + itemId;
        
        // Vérifier si les données sont en cache et encore valides
        if (!forceRefresh && globalPriceCache.containsKey(cacheKey)) {
            Long timestamp = cacheTimestamps.get(cacheKey);
            if (timestamp != null && System.currentTimeMillis() - timestamp < CACHE_EXPIRY) {
                return globalPriceCache.get(cacheKey);
            }
        }
        
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

        DynamicPrice price = DynaShopPlugin.getInstance().getDynaShopListener().getOrLoadPrice(shopId, itemId, itemStack);
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
            // // // Ajouter les informations de stock
            // String currentStock = plugin.getPriceFormatter().getStockByType(shopId, itemId, "stock");
            // String maxStock = plugin.getPriceFormatter().getStockByType(shopId, itemId, "stock_max");
            // // String currentStock = plugin.getPriceFormatter().formatStock(price.getCurrentStock());
            // // if (currentStock.equals("0")) {
            // //     currentStock = "N/A"; // Si le stock est 0, on le remplace par N/A
            // // }

            // // plugin.getLogger().info("Current stock for " + itemId + ": " + price.getStock() + 
            // //     ", Max stock: " + price.getMaxStock() + 
            // //     ", Formatted current stock: " + plugin.getPriceFormatter().formatStock(price.getStock()) +
            // //     ", Formatted max stock: " + plugin.getPriceFormatter().formatStock(price.getMaxStock()));

            // // String maxStock = plugin.getPriceFormatter().formatStock(price.getMaxStock());
            // // if (maxStock.equals("0")) {
            // //     maxStock = "N/A"; // Si le stock est 0, on le remplace par N/A
            // // }
            String currentStock, maxStock, fCurrentStock, fMaxStock;
            if (price != null) {
                currentStock = String.valueOf(price.getStock());
                fCurrentStock = plugin.getPriceFormatter().formatStock(price.getStock());
                // if (currentStock.equals("0")) {
                //     currentStock = "N/A"; // Si le stock est 0, on le remplace par N/A
                // }
                maxStock = String.valueOf(price.getMaxStock());
                fMaxStock = plugin.getPriceFormatter().formatStock(price.getMaxStock());
                // if (maxStock.equals("0")) {
                //     maxStock = "N/A"; // Si le stock est 0, on le remplace par N/A
                // }
                // plugin.getLogger().info("Current stock for " + itemId + ": " + price.getStock() + 
                //     ", Max stock: " + price.getMaxStock() + 
                //     ", Formatted current stock: " + plugin.getPriceFormatter().formatStock(price.getStock()) +
                //     ", Formatted max stock: " + plugin.getPriceFormatter().formatStock(price.getMaxStock()));
            } else {
                currentStock = plugin.getPriceFormatter().getStockByType(shopId, itemId, "stock");
                fCurrentStock = plugin.getPriceFormatter().formatStock(Integer.parseInt(currentStock));
                maxStock = plugin.getPriceFormatter().getStockByType(shopId, itemId, "stock_max");
                fMaxStock = plugin.getPriceFormatter().formatStock(Integer.parseInt(maxStock));
            }

            prices.put("stock", fCurrentStock);
            prices.put("stock_max", fMaxStock);

            // Format pour le stock
            if (currentStock.equals("N/A") || currentStock.equals("0")) {
                // prices.put("base_stock", ChatColor.translateAlternateColorCodes('&', "&cOut of stock"));
                prices.put("base_stock", ChatColor.translateAlternateColorCodes('&', this.plugin.getLangConfig().getPlaceholderOutOfStock()));
                prices.put("colored_stock_ratio", ChatColor.translateAlternateColorCodes('&', this.plugin.getLangConfig().getPlaceholderOutOfStock()));
            } else {
                prices.put("base_stock", String.format("%s/%s", fCurrentStock, fMaxStock));

                // Format avec couleurs selon le niveau de stock
                int current = Integer.parseInt(currentStock);
                int max = Integer.parseInt(maxStock);
                String colorCode = (current < max * 0.25) ? "&c" : (current < max * 0.5) ? "&e" : "&a";
                prices.put("colored_stock_ratio", ChatColor.translateAlternateColorCodes('&', 
                    String.format("%s%s&7/%s", colorCode, fCurrentStock, fMaxStock)));
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

        // Mettre en cache avec timestamp
        globalPriceCache.put(cacheKey, prices);
        cacheTimestamps.put(cacheKey, System.currentTimeMillis());
        
        return prices;
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

    public void shutdown() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        // openInventories.clear();
        openShopMap.clear();
    }
}