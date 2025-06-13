package fr.tylwen.satyria.dynashop.listener;

import java.time.Duration;
import java.time.LocalDateTime;
// import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
// import java.util.HashMap;
import java.util.List;
import java.util.Map;
// import java.util.Map;
// import java.util.Map;
// import java.util.Map;
// import java.security.cert.PKIXRevocationChecker.Option;
import java.util.Optional;
// import java.util.concurrent.CompletableFuture;
// import java.util.UUID;
// import java.util.concurrent.ConcurrentHashMap;
// import java.util.stream.Collectors;
// import java.util.concurrent.ConcurrentHashMap;
// import java.util.concurrent.ExecutionException;
// import java.util.concurrent.atomic.AtomicBoolean;
// import java.util.concurrent.TimeUnit;
// import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;

// import javax.swing.text.StyledEditorKit.BoldAction;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
// import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
// import org.bukkit.block.Block;
// import org.bukkit.entity.Player;
// import org.bukkit.Bukkit;
// import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
// import org.bukkit.event.inventory.InventoryCloseEvent;
// import org.bukkit.event.inventory.InventoryOpenEvent;
// import org.bukkit.inventory.Inventory;
// import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

import fr.tylwen.satyria.dynashop.DynaShopPlugin;
import fr.tylwen.satyria.dynashop.price.DynamicPrice;
import fr.tylwen.satyria.dynashop.data.ItemPriceData;
import fr.tylwen.satyria.dynashop.data.ShopConfigManager;
import fr.tylwen.satyria.dynashop.data.cache.LimitCacheEntry;
import fr.tylwen.satyria.dynashop.data.param.DynaShopType;
import fr.tylwen.satyria.dynashop.price.PriceRecipe;
import fr.tylwen.satyria.dynashop.price.PriceRecipe.FoundItem;
import fr.tylwen.satyria.dynashop.system.TransactionLimiter;
import fr.tylwen.satyria.dynashop.system.chart.PriceHistory;
import fr.tylwen.satyria.dynashop.system.chart.PriceHistory.PriceDataPoint;
// import fr.tylwen.satyria.dynashop.price.PriceRecipe.RecipeCalculationResult;
// import fr.tylwen.satyria.dynashop.data.param.RecipeType;
import fr.tylwen.satyria.dynashop.config.DataConfig;
import net.brcdev.shopgui.ShopGuiPlusApi;
// import net.brcdev.shopgui.ShopGuiPlusApi;
import net.brcdev.shopgui.event.ShopPostTransactionEvent;
import net.brcdev.shopgui.event.ShopPreTransactionEvent;
import net.brcdev.shopgui.exception.player.PlayerDataNotLoadedException;
// import net.brcdev.shopgui.modifier.PriceModifier;
import net.brcdev.shopgui.modifier.PriceModifierActionType;
// import net.brcdev.shopgui.modifier.PriceModifierType;
// import net.brcdev.shopgui.gui.gui.OpenGui;
// import net.brcdev.shopgui.player.PlayerData;
import net.brcdev.shopgui.shop.item.ShopItem;
// import net.brcdev.shopgui.shop.Shop;
// import net.brcdev.shopgui.shop.Shop;
// import net.md_5.bungee.api.ChatColor;
// import net.brcdev.shopgui.shop.Shop;
import net.brcdev.shopgui.shop.ShopManager.ShopAction;
import net.brcdev.shopgui.shop.ShopTransactionResult.ShopTransactionResultType;

public class DynaShopListener implements Listener {
    private DynaShopPlugin plugin;
    private final PriceRecipe priceRecipe;
    private final DataConfig dataConfig;
    private final ShopConfigManager shopConfigManager;
    // private final Map<String, LimitCacheEntry> limitsCache = new ConcurrentHashMap<>();
    // private final Map<String, TransactionInfo> pendingLimitChecks = new ConcurrentHashMap<>();
    
    // private long lastPriceUpdate = 0;
    // private static final long PRICE_UPDATE_COOLDOWN = 500; // 500ms minimum entre les mises à jour

    // Map pour stocker le shop actuellement ouvert par chaque joueur
    // private final Map<UUID, Pair<String, String>> openShopMap = new ConcurrentHashMap<>();
    // Puis remplacer toutes les occurrences de Pair par SimpleEntry
    // private final Map<UUID, SimpleEntry<String, String>> openShopMap = new ConcurrentHashMap<>();
    
    // private final Map<String, DynamicPrice> priceCache = new ConcurrentHashMap<>();
    // private final Map<String, Long> cacheTimes = new ConcurrentHashMap<>();
    // private static final long CACHE_DURATION = 30000; // 30 secondes de durée de cache
    // private static final long CACHE_DURATION = 5000; // 5 secondes de durée de cache
    // private static final long CACHE_DURATION = 10L; // 10 ticks de durée de cache

    public DynaShopListener(DynaShopPlugin plugin) {
        this.plugin = plugin;
        this.priceRecipe = new PriceRecipe(plugin);
        this.dataConfig = new DataConfig(plugin.getConfigMain());
        this.shopConfigManager = plugin.getShopConfigManager();
    }


    /**
     * Événement déclenché avant une transaction de shop.
     * @param event
     * @throws PlayerDataNotLoadedException 
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onShopPreTransaction(ShopPreTransactionEvent event) throws PlayerDataNotLoadedException {
        Player player = event.getPlayer();
        ShopItem item = event.getShopItem();
        int amount = event.getAmount();
        String shopID = item.getShop().getId();
        String itemID = item.getId();
        ItemStack itemStack = item.getItem();
        boolean isBuy = event.getShopAction() == ShopAction.BUY;

        if (plugin.getShopConfigManager().hasSection(shopID, itemID, "limit")) {
            boolean canPerform = plugin.getTransactionLimiter().canPerformTransactionSync(player, shopID, itemID, isBuy, amount);
            if (!canPerform) {
                // event.setCancelled(true);
                handleLimitExceeded(player, shopID, itemID, isBuy, event);
                return;
            }
        }

        // // Si on arrive ici, c'est qu'il n'y a pas de limite à vérifier
        // processRegularTransaction(event, player, item, amount, shopID, itemID, itemStack, isBuy);

        // if (!shopConfigManager.hasDynaShopSection(shopID, itemID)) {
        if (!shopConfigManager.getItemValue(shopID, itemID, "typeDynaShop", String.class).isPresent()) {
            return; // Ignorer les items non configurés pour DynaShop
        }
        // if (!shopConfigManager.hasStockSection(shopID, itemID) && !shopConfigManager.hasDynamicSection(shopID, itemID) && !shopConfigManager.hasRecipeSection(shopID, itemID)) {
        //     return; // Ignorer les items sans les sections requises
        // }

        // DynaShopType typeDynaShop = shopConfigManager.getTypeDynaShop(shopID, itemID);

        // DynamicPrice price = getOrLoadPrice(shopID, itemID, itemStack);
        DynamicPrice price = getOrLoadPriceInternal(null, shopID, itemID, itemStack, new HashSet<>(), new HashMap<>(), false);
        if (price == null) {
            return;
        }
        DynaShopType typeDynaShop = price.getDynaShopType();
        
        // Vérifier le mode STOCK et les limites de stock
        // if (typeDynaShop == DynaShopType.STOCK || typeDynaShop == DynaShopType.STATIC_STOCK || price.isFromStock()) {
        if (typeDynaShop == DynaShopType.STOCK || typeDynaShop == DynaShopType.STATIC_STOCK) {
            // Si c'est un achat et que le stock est vide
            // if (event.getShopAction() == ShopAction.BUY && price.getStock() <= 0) {
            if (event.getShopAction() == ShopAction.BUY && !plugin.getPriceStock().canBuy(shopID, itemID, amount)) {
                event.setCancelled(true);
                if (event.getPlayer() != null) {
                    event.getPlayer().sendMessage(ChatColor.translateAlternateColorCodes('&', this.plugin.getLangConfig().getMsgOutOfStock()));
                }
                return;
            }
            
            // Si c'est une vente et que le stock est plein
            // if ((event.getShopAction() == ShopAction.SELL || event.getShopAction() == ShopAction.SELL_ALL) && price.getStock() >= price.getMaxStock()) {
            if ((event.getShopAction() == ShopAction.SELL || event.getShopAction() == ShopAction.SELL_ALL) && !plugin.getPriceStock().canSell(shopID, itemID, amount)) {
                event.setCancelled(true);
                if (event.getPlayer() != null) {
                    event.getPlayer().sendMessage(ChatColor.translateAlternateColorCodes('&', this.plugin.getLangConfig().getMsgFullStock()));
                }
                return;
            }
        }

        // Vérifier le mode RECIPE et s'il y a des ingrédients en mode STOCK
        if (typeDynaShop == DynaShopType.RECIPE) {
            int stockAmount = priceRecipe.calculateStock(shopID, itemID, new ArrayList<>());
            int maxStock = priceRecipe.calculateMaxStock(shopID, itemID, new ArrayList<>());
            // plugin.getLogger().info("Stock amount for " + itemID + " in shop " + shopID + ": " + stockAmount + ", Max stock: " + maxStock);
            // Vérifier si le stock est suffisant pour l'achat
            if (maxStock > 0) {
                if (event.getShopAction() == ShopAction.BUY && stockAmount < amount) {
                    event.setCancelled(true);
                    if (event.getPlayer() != null) {
                        event.getPlayer().sendMessage(ChatColor.translateAlternateColorCodes('&', this.plugin.getLangConfig().getMsgOutOfStock()));
                        // event.getPlayer().sendMessage(ChatColor.translateAlternateColorCodes('&', this.plugin.getLangConfig().getMsgRecipeOutOfStock()));
                    }
                    return;
                }
                // Vérifier si le stock est suffisant pour la vente
                if ((event.getShopAction() == ShopAction.SELL || event.getShopAction() == ShopAction.SELL_ALL) && stockAmount >= maxStock) {
                    event.setCancelled(true);
                    if (event.getPlayer() != null) {
                        event.getPlayer().sendMessage(ChatColor.translateAlternateColorCodes('&', this.plugin.getLangConfig().getMsgFullStock()));
                    }
                    return;
                }
            }
        }
        
        recordPriceForHistory(shopID, itemID, price, isBuy, amount);

        // if (event.getShopAction() == ShopAction.BUY) {
        //     event.setPrice(price.getBuyPriceForAmount(amount));
        // } else if (event.getShopAction() == ShopAction.SELL || event.getShopAction() == ShopAction.SELL_ALL) {
        //     event.setPrice(price.getSellPriceForAmount(amount));
        // }
        // Appliquer les modificateurs spécifiques au joueur
        double basePrice;
        if (event.getShopAction() == ShopAction.BUY) {
            basePrice = price.getBuyPriceForAmount(amount);
            // Appliquer le modificateur de prix d'achat spécifique au joueur
            // double playerBuyModifier = ShopGuiPlusApi.getBuyPriceModifier(player, item.getShop(), item);
            double playerBuyModifier = ShopGuiPlusApi.getPriceModifier(player, item, PriceModifierActionType.BUY).getModifier();
            event.setPrice(basePrice * playerBuyModifier);
        } else if (event.getShopAction() == ShopAction.SELL || event.getShopAction() == ShopAction.SELL_ALL) {
            basePrice = price.getSellPriceForAmount(amount);
            // Appliquer le modificateur de prix de vente spécifique au joueur
            // double playerSellModifier = ShopGuiPlusApi.getSellPriceModifier(player, item.getShop(), item);
            double playerSellModifier = ShopGuiPlusApi.getPriceModifier(player, item, PriceModifierActionType.SELL).getModifier();
            event.setPrice(basePrice * playerSellModifier);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onShopPostTransaction(ShopPostTransactionEvent event) {
        // // Limiter la fréquence des mises à jour de prix
        // long currentTime = System.currentTimeMillis();
        // if (currentTime - lastPriceUpdate < PRICE_UPDATE_COOLDOWN) {
        //     return; // Ignorer cette transaction si une mise à jour récente a été effectuée
        // }
        // lastPriceUpdate = currentTime;

        if (event.getResult().getResult() != ShopTransactionResultType.SUCCESS) {
            return; // Ignorer les transactions échouées
        }

        // Capturer toutes les données nécessaires dans le thread principal
        final Player player = event.getResult().getPlayer();
        final ShopItem item = event.getResult().getShopItem();
        final int amount = event.getResult().getAmount();
        final String shopID = item.getShop().getId();
        final String itemID = item.getId();
        final ItemStack itemStack = item.getItem().clone(); // Cloner pour éviter des problèmes de concurrence
        final ShopAction action = event.getResult().getShopAction();
        final double resultPrice = event.getResult().getPrice();
        final boolean isBuy = action == ShopAction.BUY;

        // AJOUTEZ LE CODE DE TAXATION ICI
        if (plugin.getTaxService() != null && plugin.getTaxService().isEnabled()) {
            // double taxAmount = 0;
            if (isBuy) {
                // taxAmount = plugin.getTaxService().applyBuyTax(player, resultPrice, shopID, itemID);
                plugin.getTaxService().applyBuyTax(player, resultPrice, shopID, itemID);
            } else {
                // taxAmount = plugin.getTaxService().applySellTax(player, resultPrice, shopID, itemID);
                plugin.getTaxService().applySellTax(player, resultPrice, shopID, itemID);
            }
            
            // // Enregistrer dans les logs si la taxe est significative
            // if (taxAmount > 0) {
            //     plugin.getLogger().info(String.format(
            //         "Taxe de %.2f appliquée sur une transaction %s de %s par %s (item: %s:%s)",
            //         taxAmount,
            //         isBuy ? "d'achat" : "de vente",
            //         plugin.getPriceFormatter().formatPrice(resultPrice),
            //         player.getName(),
            //         shopID,
            //         itemID
            //     ));
            // }
        }
        
        // Après avoir modifié le prix
        // Bukkit.getScheduler().runTask(plugin, () -> {
        //     plugin.invalidatePriceCache(shopID, itemID, null); // Invalider le cache pour ce joueur et cet item
        // });
        // Invalider le cache pour ce joueur et cet item
        plugin.invalidatePriceCache(shopID, itemID, player); // Invalider le cache pour ce joueur et cet item

        // DynaShopType typeDynaShop = shopConfigManager.getTypeDynaShop(shopID, itemID);
        DynaShopType typeDynaShop = shopConfigManager.resolveTypeDynaShop(shopID, itemID, isBuy);
        
        // Si c'est une recette, ajouter une tâche asynchrone pour invalider les ingrédients
        if (typeDynaShop == DynaShopType.RECIPE) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                // invalidateRecipeIngredients(shopID, itemID, itemStack);
                invalidateRecipeIngredients(shopID, itemID);
            });
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            processTransactionAsync(shopID, itemID, itemStack, amount, action);
        });
        
        // // Après avoir modifié le prix dans le cache :
        // DynamicPrice updatedPrice = plugin.getPriceCache().getIfPresent(shopID + ":" + itemID);
        // if (updatedPrice != null) {
        //     plugin.getPriceDataManager().set(shopID + ":" + itemID, updatedPrice);
        //     plugin.getPriceDataManager().save(); // Sauvegarde asynchrone (voir PriceDataManager)
        // }
        // Integer stock = plugin.getStockCache().getIfPresent(shopID + ":" + itemID);
        // if (stock != null && stock >= 0) {
        //     plugin.getStockDataManager().set(shopID + ":" + itemID, stock);
        //     plugin.getStockDataManager().save(); // Sauvegarde asynchrone (voir StockDataManager)
        // }

        // // Enregistrer la transaction si l'item a des limites
        // if (plugin.getShopConfigManager().hasSection(shopID, itemID, "limit")) {
        //     // plugin.getTransactionLimiter().recordTransaction(player, shopID, itemID, isBuy, amount);
        //     plugin.getTransactionLimiter().queueTransaction(player, shopID, itemID, isBuy, amount);
            
        //     // Récupérer la limite du cache et la sauvegarder
        //     final String cacheKey = DynaShopPlugin.getLimitCacheKey(player.getUniqueId(), shopID, itemID, isBuy);
        //     LimitCacheEntry limitEntry = plugin.getLimitCache().getIfPresent(cacheKey);
            
        //     if (limitEntry != null) {
        //         // Gérer le cache des limites avec la nouvelle structure
        //         plugin.getLimitDataManager().set(cacheKey, limitEntry);
        //         plugin.getLimitDataManager().save(); // Sauvegarde asynchrone (voir LimitDataManager)
        //     }
        // }

        // Après avoir modifié le prix dans le cache :
        DynamicPrice updatedPrice = plugin.getPriceCache().getIfPresent(shopID + ":" + itemID);
        if (updatedPrice != null) {
            // Utiliser directement le StorageManager au lieu de PriceDataManager
            plugin.getStorageManager().savePrice(shopID, itemID, updatedPrice.getBuyPrice(), updatedPrice.getSellPrice(), updatedPrice.getStock());
        }
        Integer stock = plugin.getStockCache().getIfPresent(shopID + ":" + itemID);
        if (stock != null && stock >= 0) {
            // Utiliser directement le StorageManager au lieu de StockDataManager
            plugin.getStorageManager().saveStock(shopID, itemID, stock);
        }
        // Enregistrer la transaction si l'item a des limites
        if (plugin.getShopConfigManager().hasSection(shopID, itemID, "limit")) {
            // Pas de changement ici - le TransactionLimiter utilise déjà le StorageManager en interne
            plugin.getTransactionLimiter().queueTransaction(player, shopID, itemID, isBuy, amount);
        }

        // // Enregistrer la transaction dans les logs
        // plugin.getLogger().info(String.format(
        //     "%s %s %d de %s dans le shop %s (item: %s:%s, prix: %.2f)",
        //     player.getName(),
        //     isBuy ? "a acheté" : "a vendu",
        //     amount,
        //     plugin.getPriceFormatter().formatPrice(resultPrice),
        //     shopID,
        //     item.getShop().getName(),
        //     itemID,
        //     resultPrice
        // ));

        // recordPriceForHistory(shopID, itemID, resultPrice, isBuy, amount);
            
    }
    
    private void invalidateRecipeIngredients(String shopId, String itemId) {
        // Récupérer les ingrédients
        List<ItemStack> ingredients = plugin.getPriceRecipe().getIngredients(shopId, itemId);
        
        for (ItemStack ingredient : ingredients) {
            // Trouver l'ID de shop et d'item pour chaque ingrédient
            FoundItem foundItem = plugin.getPriceRecipe().findItemInShops(shopId, ingredient);
            if (foundItem.isFound()) {
                // Invalider le cache pour cet ingrédient
                plugin.invalidatePriceCache(foundItem.getShopID(), foundItem.getItemID(), null);
            }
        }
    }
    
    private void processTransactionAsync(String shopID, String itemID, ItemStack itemStack, int amount, ShopAction action) {
        // if (!shopConfigManager.hasDynaShopSection(shopID, itemID)) {
        if (!shopConfigManager.getItemValue(shopID, itemID, "typeDynaShop", String.class).isPresent()) {
            // plugin.warning(itemID + " : Pas de section DynaShop dans le shop " + shopID);
            return; // Ignorer les items non configurés pour DynaShop
        }
        // if (!shopConfigManager.hasStockSection(shopID, itemID) &&
        //     !shopConfigManager.hasDynamicSection(shopID, itemID) &&
        //     !shopConfigManager.hasRecipeSection(shopID, itemID)) {
        //     // plugin.warning(itemID + " : Pas de section dynamique, recette ou stock dans le shop " + shopID);
        //     return; // Ignorer les items sans les sections requises
        // }

        // DynamicPrice price = getOrLoadPrice(shopID, itemID, itemStack);
        DynamicPrice price = getOrLoadPriceInternal(null, shopID, itemID, itemStack, new HashSet<>(), new HashMap<>(), false);
        if (price == null) {
            // plugin.warning(itemID + " : Pas de prix dynamique trouvé dans le shop " + shopID);
            return;
        }

        // DynaShopType type = shopConfigManager.resolveTypeDynaShop(shopID, itemID, ShopAction.BUY == action);
        DynaShopType typeDynaShop = price.getDynaShopType();
        DynaShopType buyTypeDynaShop = price.getBuyTypeDynaShop();
        DynaShopType sellTypeDynaShop = price.getSellTypeDynaShop();
        
        if (buyTypeDynaShop == DynaShopType.NONE || buyTypeDynaShop == DynaShopType.UNKNOWN) buyTypeDynaShop = typeDynaShop;
        if (sellTypeDynaShop == DynaShopType.NONE || sellTypeDynaShop == DynaShopType.UNKNOWN) sellTypeDynaShop = typeDynaShop;

        // DynaShopType typeDynaShop = shopConfigManager.resolveTypeDynaShop(shopID, itemID, ShopAction.BUY == action);

        // if (typeDynaShop == DynaShopType.DYNAMIC) {
        //     handleDynamicPrice(price, action, amount); // Gérer les prix dynamiques
        // // } else if (typeDynaShop == DynaShopType.RECIPE || price.isFromRecipe()) {
        // } else if (typeDynaShop == DynaShopType.RECIPE) {
        //     handleRecipePrice(shopID, itemID, itemStack, amount, action); // Gérer les prix basés sur les recettes
        // // } else if (typeDynaShop == DynaShopType.STOCK || typeDynaShop == DynaShopType.STATIC_STOCK || price.isFromStock()) {
        // } else if (typeDynaShop == DynaShopType.STOCK || typeDynaShop == DynaShopType.STATIC_STOCK) {
        //     handleStockPrice(price, shopID, itemID, action, amount); // Gérer les prix basés sur le stock
        // } else if (typeDynaShop == DynaShopType.LINK) {
        //     handleLinkedPrice(shopID, itemID, itemStack, action, amount);
        // }
        
        if (buyTypeDynaShop == DynaShopType.DYNAMIC) {
            handleDynamicPrice(price, action, amount); // Gérer les prix dynamiques
        } else if (buyTypeDynaShop == DynaShopType.RECIPE) {
            handleRecipePrice(shopID, itemID, amount, action); // Gérer les prix basés sur les recettes
        // } else if (buyTypeDynaShop == DynaShopType.STOCK || buyTypeDynaShop == DynaShopType.STATIC_STOCK) {
        //     handleStockPrice(price, shopID, itemID, action, amount); // Gérer les prix basés sur le stock
        } else if (buyTypeDynaShop == DynaShopType.LINK) {
            handleLinkedPrice(shopID, itemID, itemStack, action, amount);
        }

        if (sellTypeDynaShop == DynaShopType.DYNAMIC) {
            handleDynamicPrice(price, action, amount); // Gérer les prix dynamiques
        } else if (sellTypeDynaShop == DynaShopType.RECIPE) {
            handleRecipePrice(shopID, itemID, amount, action); // Gérer les prix basés sur les recettes
        // } else if (sellTypeDynaShop == DynaShopType.STOCK || sellTypeDynaShop == DynaShopType.STATIC_STOCK) {
        //     handleStockPrice(price, shopID, itemID, action, amount); // Gérer les prix basés sur le stock
        } else if (sellTypeDynaShop == DynaShopType.LINK) {
            handleLinkedPrice(shopID, itemID, itemStack, action, amount);
        }

        if (typeDynaShop == DynaShopType.STOCK || typeDynaShop == DynaShopType.STATIC_STOCK) {
            handleStockPrice(price, shopID, itemID, action, amount); // Gérer les prix basés sur le stock
        }


        // plugin.info(action + " - Prix mis à jour pour l'item " + itemID + " dans le shop " + shopID);
        // plugin.info("Prix : " + resultPrice + ", amount : " + amount + ", growth : " + price.getGrowthBuy() + ", decay : " + price.getDecaySell());
        // plugin.info("Next BUY : " + price.getBuyPrice() + ", Min : " + price.getMinBuyPrice() + ", Max : " + price.getMaxBuyPrice());
        // plugin.info("Next SELL : " + price.getSellPrice() + ", Min : " + price.getMinSellPrice() + ", Max : " + price.getMaxSellPrice());

        // Sauvegarder les nouveaux prix dans la base de données
        // savePriceIfNeeded(price, shopID, itemID);
        // if (!price.isFromRecipe()) {
        // if (price.getDynaShopType() != DynaShopType.RECIPE) {
        // if (typeDynaShop != DynaShopType.RECIPE) {
        //     plugin.getBatchDatabaseUpdater().queueUpdate(shopID, itemID, price, true);
        // }
        // plugin.getBatchDatabaseUpdater().queueUpdate(shopID, itemID, price);
        // if (buyTypeDynaShop != DynaShopType.RECIPE) {
        //     plugin.getBatchDatabaseUpdater().queueUpdate(shopID, itemID, price, true);
        // }
        // if (sellTypeDynaShop != DynaShopType.RECIPE) {
        //     plugin.getBatchDatabaseUpdater().queueUpdate(shopID, itemID, price, true);
        // }
        if (sellTypeDynaShop != DynaShopType.RECIPE || buyTypeDynaShop != DynaShopType.RECIPE) {
            plugin.getBatchDatabaseUpdater().queueUpdate(shopID, itemID, price, true);
        }

    }

    private void handleDynamicPrice(DynamicPrice price, ShopAction action, int amount) {
        if (action == ShopAction.BUY) {
            price.applyGrowth(amount);
        } else if (action == ShopAction.SELL || action == ShopAction.SELL_ALL) {
            price.applyDecay(amount);
        }
    }

    private void handleRecipePrice(String shopID, String itemID, int amount, ShopAction action) {
        // if (amount <= 0) {
        //     return; // Ignorer les transactions avec une quantité nulle ou négative
        // }

        boolean isGrowth = action == ShopAction.BUY;
        // boolean isGrowth;
        // if (action == ShopAction.BUY) {
        //     isGrowth = true;
        // } else if (action == ShopAction.SELL || action == ShopAction.SELL_ALL) {
        //     isGrowth = false;
        // }

        // Exécuter dans un thread asynchrone pour éviter de bloquer le thread principal
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            // // Invalider tous les caches liés à cette recette
            // for (String key : new ArrayList<>(priceCache.keySet())) {
            //     if (key.startsWith(shopID + ":" + itemID)) {
            //         priceCache.remove(key);
            //     }
            // }
            applyGrowthOrDecayToIngredients(shopID, itemID, amount, isGrowth, new HashSet<>(), new HashMap<>(), 0);
        });
    }

    // private void handleStockPrice(DynamicPrice price, ShopAction action, int amount) {
    private void handleStockPrice(DynamicPrice price, String shopID, String itemID, ShopAction action, int amount) {
        // plugin.getLogger().info("AVANT - Stock: " + price.getStock() + ", Buy: " + price.getBuyPrice() + ", Sell: " + price.getSellPrice());
        
        if (action == ShopAction.BUY) {
            // plugin.getLogger().info("Diminution du stock de " + amount + " unités");
            // price.decreaseStock(amount);
            plugin.getPriceStock().processBuyTransaction(shopID, itemID, amount);
            // price.adjustPricesBasedOnStock();
            // price.adjustPricesBasedOnPrices();
        } else if (action == ShopAction.SELL || action == ShopAction.SELL_ALL) {
            // plugin.getLogger().info("Augmentation du stock de " + amount + " unités");
            // price.increaseStock(amount);
            plugin.getPriceStock().processSellTransaction(shopID, itemID, amount);
            // price.adjustPricesBasedOnStock();
            // price.adjustPricesBasedOnPrices();
        }

        // Mettre à jour l'objet price
        double newBuyPrice = plugin.getPriceStock().calculatePrice(shopID, itemID, "buyPrice");
        double newSellPrice = plugin.getPriceStock().calculatePrice(shopID, itemID, "sellPrice");
        
        price.setBuyPrice(newBuyPrice);
        price.setSellPrice(newSellPrice);
        price.setStock(plugin.getStorageManager().getStock(shopID, itemID).orElse(0));
        
        // plugin.getLogger().info("APRÈS - Stock: " + price.getStock() + ", Buy: " + price.getBuyPrice() + ", Sell: " + price.getSellPrice());
    }

    // private void handleLinkedPrice(String shopID, String itemID, ItemStack itemStack, ShopAction action, int amount) {
    //     // Implémenter la logique pour les prix liés si nécessaire
    //     // plugin.getLogger().info("Traitement des prix liés pour l'item " + itemID + " dans le shop " + shopID);
    //     // Vous pouvez ajouter votre logique ici pour gérer les prix liés
    //     String linkedItemRef = shopConfigManager.getItemValue(shopID, itemID, "link", String.class).orElse(null);
    //     if (linkedItemRef != null && linkedItemRef.contains(":")) {
    //         String[] parts = linkedItemRef.split(":");
    //         if (parts.length == 2) {
    //             String linkedShopID = parts[0];
    //             String linkedItemID = parts[1];
                
    //             // Charger le prix du shop lié
    //             // DynamicPrice linkedPrice = getOrLoadPrice(linkedShopID, linkedItemID, itemStack);
    //             DynamicPrice linkedPrice = getOrLoadPriceInternal(null, linkedShopID, linkedItemID, itemStack, new HashSet<>(), new HashMap<>(), false);
    //             if (linkedPrice != null) {
    //                 DynaShopType linkedType = shopConfigManager.resolveTypeDynaShop(linkedShopID, linkedItemID, ShopAction.BUY == action);
    //                 if (linkedType == DynaShopType.DYNAMIC) {
    //                     // Gérer les prix dynamiques liés
    //                     handleDynamicPrice(linkedPrice, action, amount);
    //                 // } else if (linkedType == DynaShopType.RECIPE || linkedPrice.isFromRecipe()) {
    //                 } else if (linkedType == DynaShopType.RECIPE) {
    //                     // Gérer les prix basés sur les recettes liés
    //                     handleRecipePrice(linkedShopID, linkedItemID, amount, action);
    //                 // } else if (linkedType == DynaShopType.STOCK || linkedType == DynaShopType.STATIC_STOCK || linkedPrice.isFromStock()) {
    //                 } else if (linkedType == DynaShopType.STOCK || linkedType == DynaShopType.STATIC_STOCK) {
    //                     // Gérer les prix basés sur le stock lié
    //                     handleStockPrice(linkedPrice, linkedShopID, linkedItemID, action, amount);
    //                 }
                    
    //                 // Sauvegarder les modifications de l'item lié
    //                 // if (!linkedPrice.isFromRecipe()) {
    //                 if (linkedPrice.getDynaShopType() != DynaShopType.RECIPE) {
    //                     plugin.getBatchDatabaseUpdater().queueUpdate(linkedShopID, linkedItemID, linkedPrice, true);
    //                 }
                    
    //                 // AJOUT: Créer une copie du prix lié pour l'item principal
    //                 DynamicPrice copyForMainItem = new DynamicPrice(
    //                     linkedPrice.getBuyPrice(), linkedPrice.getSellPrice(),
    //                     linkedPrice.getMinBuyPrice(), linkedPrice.getMaxBuyPrice(),
    //                     linkedPrice.getMinSellPrice(), linkedPrice.getMaxSellPrice(),
    //                     linkedPrice.getGrowthBuy(), linkedPrice.getDecayBuy(),
    //                     linkedPrice.getGrowthSell(), linkedPrice.getDecaySell(),
    //                     linkedPrice.getStock(), linkedPrice.getMinStock(), linkedPrice.getMaxStock(),
    //                     linkedPrice.getStockBuyModifier(), linkedPrice.getStockSellModifier()
    //                 );
                    
    //                 // Conserver les flags spéciaux
    //                 // copyForMainItem.setFromRecipe(linkedPrice.isFromRecipe());
    //                 // copyForMainItem.setFromStock(linkedPrice.isFromStock());
    //                 copyForMainItem.setDynaShopType(linkedPrice.getDynaShopType());
                    
    //                 // AJOUT: Sauvegarder également cette copie pour l'item principal
    //                 // if (!copyForMainItem.isFromRecipe()) {
    //                 if (copyForMainItem.getDynaShopType() != DynaShopType.RECIPE) {
    //                     plugin.getBatchDatabaseUpdater().queueUpdate(shopID, itemID, copyForMainItem, true);
    //                 }
                    
    //                 // AJOUT: Invalider le cache pour l'item lié ET l'item qui a le lien
    //                 plugin.invalidatePriceCache(linkedShopID, linkedItemID, null);
    //                 plugin.invalidatePriceCache(shopID, itemID, null);
    //             }
    //         } else {
    //             plugin.getLogger().warning("Invalid link reference for item " + itemID + " in shop " + shopID + ": " + linkedItemRef);
    //         }
    //     } else {
    //         plugin.getLogger().warning("No link reference found for item " + itemID + " in shop " + shopID);
    //     }
    // }
    // private void handleLinkedPrice(String shopID, String itemID, ItemStack itemStack, ShopAction action, int amount) {
    //     String linkedItemRef = shopConfigManager.getItemValue(shopID, itemID, "link", String.class).orElse(null);
    //     if (linkedItemRef != null && linkedItemRef.contains(":")) {
    //         String[] parts = linkedItemRef.split(":");
    //         if (parts.length == 2) {
    //             String linkedShopID = parts[0];
    //             String linkedItemID = parts[1];
                
    //             // Charger le prix du shop lié
    //             DynamicPrice linkedPrice = getOrLoadPriceInternal(null, linkedShopID, linkedItemID, itemStack, new HashSet<>(), new HashMap<>(), false);
    //             if (linkedPrice != null) {
    //                 DynaShopType linkedType = shopConfigManager.resolveTypeDynaShop(linkedShopID, linkedItemID, ShopAction.BUY == action);
    //                 if (linkedType == DynaShopType.DYNAMIC) {
    //                     handleDynamicPrice(linkedPrice, action, amount);
    //                 } else if (linkedType == DynaShopType.RECIPE) {
    //                     handleRecipePrice(linkedShopID, linkedItemID, amount, action);
    //                 } else if (linkedType == DynaShopType.STOCK || linkedType == DynaShopType.STATIC_STOCK) {
    //                     handleStockPrice(linkedPrice, linkedShopID, linkedItemID, action, amount);
    //                 }
                    
    //                 // Sauvegarder les modifications de l'item lié
    //                 if (linkedPrice.getDynaShopType() != DynaShopType.RECIPE) {
    //                     plugin.getBatchDatabaseUpdater().queueUpdate(linkedShopID, linkedItemID, linkedPrice, true);
    //                 }
                    
    //                 // AJOUT: Créer une copie du prix lié pour l'item principal
    //                 DynamicPrice copyForMainItem = new DynamicPrice(
    //                     linkedPrice.getBuyPrice(), linkedPrice.getSellPrice(),
    //                     linkedPrice.getMinBuyPrice(), linkedPrice.getMaxBuyPrice(),
    //                     linkedPrice.getMinSellPrice(), linkedPrice.getMaxSellPrice(),
    //                     linkedPrice.getGrowthBuy(), linkedPrice.getDecayBuy(),
    //                     linkedPrice.getGrowthSell(), linkedPrice.getDecaySell(),
    //                     linkedPrice.getStock(), linkedPrice.getMinStock(), linkedPrice.getMaxStock(),
    //                     linkedPrice.getStockBuyModifier(), linkedPrice.getStockSellModifier()
    //                 );
                    
    //                 // Conserver les flags spéciaux
    //                 // IMPORTANT: Pour un item LINK, on doit conserver le type LINK
    //                 copyForMainItem.setDynaShopType(DynaShopType.LINK);
                    
    //                 // Mais aussi conserver l'information sur les types réels pour buy/sell
    //                 copyForMainItem.setBuyTypeDynaShop(linkedPrice.getBuyTypeDynaShop());
    //                 copyForMainItem.setSellTypeDynaShop(linkedPrice.getSellTypeDynaShop());
                    
    //                 // Sauvegarder directement via le StorageManager pour garantir la persistance
    //                 plugin.getStorageManager().savePrice(
    //                     shopID, 
    //                     itemID, 
    //                     copyForMainItem.getBuyPrice(), 
    //                     copyForMainItem.getSellPrice(), 
    //                     copyForMainItem.getStock()
    //                 );
                    
    //                 // AJOUT: Invalider le cache pour l'item lié ET l'item qui a le lien
    //                 plugin.invalidatePriceCache(linkedShopID, linkedItemID, null);
    //                 plugin.invalidatePriceCache(shopID, itemID, null);
                    
    //                 // plugin.getLogger().info("Prix lié mis à jour pour " + shopID + ":" + itemID + " lié à " + linkedShopID + ":" + linkedItemID);
    //             }
    //         } else {
    //             plugin.getLogger().warning("Format de lien invalide pour " + shopID + ":" + itemID + ": " + linkedItemRef);
    //         }
    //     } else {
    //         plugin.getLogger().warning("Pas de référence de lien trouvée pour " + shopID + ":" + itemID);
    //     }
    // }
    private void handleLinkedPrice(String shopID, String itemID, ItemStack itemStack, ShopAction action, int amount) {
        String linkedItemRef = shopConfigManager.getItemValue(shopID, itemID, "link", String.class).orElse(null);
        if (linkedItemRef != null && linkedItemRef.contains(":")) {
            String[] parts = linkedItemRef.split(":");
            if (parts.length == 2) {
                String linkedShopID = parts[0];
                String linkedItemID = parts[1];
                
                // Charger le prix du shop lié
                DynamicPrice linkedPrice = getOrLoadPriceInternal(null, linkedShopID, linkedItemID, itemStack, new HashSet<>(), new HashMap<>(), false);
                if (linkedPrice != null) {
                    // Traiter l'item lié selon son type
                    DynaShopType linkedType = linkedPrice.getDynaShopType();
                    DynaShopType buyTypeDynaShop = linkedPrice.getBuyTypeDynaShop();
                    DynaShopType sellTypeDynaShop = linkedPrice.getSellTypeDynaShop();
                    
                    if (buyTypeDynaShop == DynaShopType.NONE || buyTypeDynaShop == DynaShopType.UNKNOWN) { buyTypeDynaShop = linkedType; }
                    if (sellTypeDynaShop == DynaShopType.NONE || sellTypeDynaShop == DynaShopType.UNKNOWN) { sellTypeDynaShop = linkedType; }
                    
                    // Appliquer les modifications à l'item lié selon son type
                    if (action == ShopAction.BUY && buyTypeDynaShop == DynaShopType.DYNAMIC) {
                        linkedPrice.applyGrowth(amount);
                    } else if ((action == ShopAction.SELL || action == ShopAction.SELL_ALL) && sellTypeDynaShop == DynaShopType.DYNAMIC) {
                        linkedPrice.applyDecay(amount);
                    } else if (linkedType == DynaShopType.STOCK || linkedType == DynaShopType.STATIC_STOCK) {
                        if (action == ShopAction.BUY) {
                            plugin.getPriceStock().processBuyTransaction(linkedShopID, linkedItemID, amount);
                        } else {
                            plugin.getPriceStock().processSellTransaction(linkedShopID, linkedItemID, amount);
                        }
                        updatePriceFromStock(linkedShopID, linkedItemID, linkedPrice);
                    } else if (linkedType == DynaShopType.RECIPE) {
                        // Appliquer la récursion pour les items RECIPE
                        handleRecipePrice(linkedShopID, linkedItemID, amount, action);
                    }
                    
                    // Sauvegarder les modifications sur l'item lié
                    if (linkedType != DynaShopType.RECIPE) {
                        plugin.getBatchDatabaseUpdater().queueUpdate(linkedShopID, linkedItemID, linkedPrice, true);
                    }
                    
                    // Créer une copie pour l'item principal
                    DynamicPrice copyForMainItem = new DynamicPrice(
                        linkedPrice.getBuyPrice(), linkedPrice.getSellPrice(),
                        linkedPrice.getMinBuyPrice(), linkedPrice.getMaxBuyPrice(),
                        linkedPrice.getMinSellPrice(), linkedPrice.getMaxSellPrice(),
                        linkedPrice.getGrowthBuy(), linkedPrice.getDecayBuy(),
                        linkedPrice.getGrowthSell(), linkedPrice.getDecaySell(),
                        linkedPrice.getStock(), linkedPrice.getMinStock(), linkedPrice.getMaxStock(),
                        linkedPrice.getStockBuyModifier(), linkedPrice.getStockSellModifier()
                    );
                    
                    // Conserver les flags spéciaux
                    copyForMainItem.setDynaShopType(DynaShopType.LINK);
                    copyForMainItem.setBuyTypeDynaShop(linkedPrice.getBuyTypeDynaShop());
                    copyForMainItem.setSellTypeDynaShop(linkedPrice.getSellTypeDynaShop());
                    
                    // Sauvegarder directement via le StorageManager
                    plugin.getStorageManager().savePrice(
                        shopID, 
                        itemID, 
                        copyForMainItem.getBuyPrice(), 
                        copyForMainItem.getSellPrice(), 
                        copyForMainItem.getStock()
                    );
                    
                    // Invalider les caches
                    plugin.invalidatePriceCache(linkedShopID, linkedItemID, null);
                    plugin.invalidatePriceCache(shopID, itemID, null);
                }
            } else {
                plugin.getLogger().warning("Format de lien invalide pour " + shopID + ":" + itemID + ": " + linkedItemRef);
            }
        } else {
            plugin.getLogger().warning("Pas de référence de lien trouvée pour " + shopID + ":" + itemID);
        }
    }
    
    // public DynamicPrice getOrLoadPrice(String shopID, String itemID, ItemStack itemStack) {
    //     return getOrLoadPrice(null, shopID, itemID, itemStack);
    // }

    // public DynamicPrice getOrLoadPrice(Player player, String shopID, String itemID, ItemStack itemStack) {
    //     return getOrLoadPriceInternal(player, shopID, itemID, itemStack, new HashSet<>(), false);
    // }
    public DynamicPrice getOrLoadPrice(Player player, String shopID, String itemID, ItemStack itemStack, Set<String> visited, Map<String, DynamicPrice> lastResults) {
        return getOrLoadPriceInternal(player, shopID, itemID, itemStack, visited, lastResults, false);
    }
    
    // Version interne avec bypass cache et détection de cycle
    public DynamicPrice getOrLoadPriceInternal(Player player, String shopID, String itemID, ItemStack itemStack, Set<String> visited, Map<String, DynamicPrice> lastResults, boolean bypassCache) {
        // String key = shopID + ":" + itemID + (player != null ? ":" + player.getUniqueId().toString() : "");
        String key = shopID + ":" + itemID;
        if (visited.contains(key)) {
            DynamicPrice last = lastResults.get(key);
            if (last != null) return last;
            plugin.getLogger().warning("Cycle détecté pour " + key + " (lien ou recette) !");
            return null;
        }
        visited.add(key);

        // try {
        //     if (!bypassCache) {
        //         // Utiliser le cache
        //         // final String cacheKey = player != null 
        //         //     ? shopID + ":" + itemID + ":" + player.getUniqueId().toString()
        //         //     : shopID + ":" + itemID;
        //         final String cacheKey = shopID + ":" + itemID + (player != null ? ":" + player.getUniqueId().toString() : "");
        //         return plugin.getPriceCache().get(cacheKey, () -> {
        //             // DynaShopPlugin.getInstance().getLogger().info("1: visited: " + visited);
        //             // Set<String> visitedCopy = new HashSet<>(visited);
        //             // visitedCopy.add(key);
        //             DynamicPrice price = loadPriceFromSourceInternal(player, shopID, itemID, itemStack, visited);
        //             if (price != null && player != null) {
        //                 price.applyShopGuiPlusModifiers(player, shopID, itemID);
        //             }
        //             return price;
        //         });
        //     } else {
        //         // DynaShopPlugin.getInstance().getLogger().info("2: visited: " + visited);
        //         // Set<String> visitedCopy = new HashSet<>(visited);
        //         // visitedCopy.add(key);
        //         // Bypass le cache pour ce calcul spécifique
        //         DynamicPrice price = loadPriceFromSourceInternal(player, shopID, itemID, itemStack, visited);
        //         if (price != null && player != null) {
        //             price.applyShopGuiPlusModifiers(player, shopID, itemID);
        //         }
        //         return price;
        //     }
        // } finally {
        //     visited.remove(key); // <-- Pour éviter les effets de bord sur d'autres branches
        // }
        try {
            DynamicPrice price;
            if (!bypassCache) {
                // final String cacheKey = key;
                final String cacheKey = shopID + ":" + itemID + (player != null ? ":" + player.getUniqueId().toString() : "");
                price = plugin.getPriceCache().get(cacheKey, () -> {
                    DynamicPrice p = loadPriceFromSourceInternal(player, shopID, itemID, itemStack, visited, lastResults);
                    // Ne pas appliquer les modificateurs si c'est un LINK (ils seront appliqués plus tard)
                    DynaShopType type = p != null ? p.getDynaShopType() : null;
                    if (p != null && player != null && type != DynaShopType.LINK) {
                        p.applyShopGuiPlusModifiers(player, shopID, itemID);
                    }
                    return p;
                });
            } else {
                price = loadPriceFromSourceInternal(player, shopID, itemID, itemStack, visited, lastResults);
                // Ne pas appliquer les modificateurs si c'est un LINK (ils seront appliqués plus tard)
                DynaShopType type = price != null ? price.getDynaShopType() : null;
                if (price != null && player != null && type != DynaShopType.LINK) {
                    price.applyShopGuiPlusModifiers(player, shopID, itemID);
                }
            }
            if (price != null) {
                lastResults.put(key, price);
            // } else {
            //     visited.remove(key); // Retirer la clé si le prix est null pour éviter les cycles
            }
            return price;
        } catch (Exception e) {
            // En cas d'exception, retourner la dernière valeur connue si possible
            DynamicPrice last = lastResults.get(key);
            if (last != null) return last;
            plugin.getLogger().warning("Erreur lors du calcul du prix pour " + key + " : " + e.getMessage());
            return null;
        } finally {
            visited.remove(key);
        }
    }

    // public DynamicPrice getOrLoadPrice(Player player, String shopID, String itemID, ItemStack itemStack) {
    //     // Créer une clé unique incluant le joueur si nécessaire
    //     final String cacheKey = player != null 
    //         ? shopID + ":" + itemID + ":" + player.getUniqueId().toString()
    //         : shopID + ":" + itemID;

    //     // Utiliser le cache manager pour récupérer le prix
    //     return plugin.getPriceCache().get(cacheKey, () -> {
    //         // Cette fonction n'est exécutée que si le prix n'est pas en cache
    //         DynamicPrice price = loadPriceFromSource(shopID, itemID, itemStack);
    //         // DynamicPrice price = loadPriceFromSource(shopID, itemID, itemStack, visited);
            
    //         // Appliquer les modificateurs spécifiques au joueur
    //         if (price != null && player != null) {
    //             price.applyShopGuiPlusModifiers(player, shopID, itemID);
    //         }
            
    //         return price;
    //     });
    // }

    // private DynamicPrice loadPriceFromSource(String shopID, String itemID, ItemStack itemStack) {
    private DynamicPrice loadPriceFromSourceInternal(Player player, String shopID, String itemID, ItemStack itemStack, Set<String> visited, Map<String, DynamicPrice> lastResults) {
        // Déterminer le type de l'item
        DynaShopType typeDynaShop = shopConfigManager.getTypeDynaShop(shopID, itemID);
        DynaShopType buyTypeDynaShop = shopConfigManager.getTypeDynaShop(shopID, itemID, "buy");
        DynaShopType sellTypeDynaShop = shopConfigManager.getTypeDynaShop(shopID, itemID, "sell");

        if (buyTypeDynaShop == DynaShopType.NONE || buyTypeDynaShop == DynaShopType.UNKNOWN) buyTypeDynaShop = typeDynaShop;
        if (sellTypeDynaShop == DynaShopType.NONE || sellTypeDynaShop == DynaShopType.UNKNOWN) sellTypeDynaShop = typeDynaShop;

        // plugin.getLogger().info("Chargement du prix pour l'item " + itemID + " dans le shop " + shopID + 
        //     " (buyType: " + buyTypeDynaShop + ", sellType: " + sellTypeDynaShop + ")");

        double buyPrice = -1, sellPrice = -1, minBuy = -1, maxBuy = -1, minSell = -1, maxSell = -1,
            growthBuy = 1, decayBuy = 1, growthSell = 1, decaySell = 1,
            stockBuyModifier = 1, stockSellModifier = 1;
        int stock = 0, minStock = 0, maxStock = 0;

        if (buyTypeDynaShop == DynaShopType.RECIPE) {
            // DynamicPrice recipePrice = plugin.getPriceRecipe().createRecipePrice(shopID, itemID);
            // DynamicPrice recipePrice = plugin.getPriceRecipe().createRecipePrice(shopID, itemID, lastResults);
            DynamicPrice recipePrice = plugin.getPriceRecipe().createRecipePrice(shopID, itemID, visited, lastResults);
            // DynamicPrice recipePrice = plugin.getPriceRecipe().createRecipePrice(shopID, itemID, new HashSet<>(), lastResults);
            buyPrice = recipePrice.getBuyPrice();
            minBuy = recipePrice.getMinBuyPrice();
            maxBuy = recipePrice.getMaxBuyPrice();
            growthBuy = recipePrice.getGrowthBuy();
            decayBuy = recipePrice.getDecayBuy();
            stock = recipePrice.getStock();
            minStock = recipePrice.getMinStock();
            maxStock = recipePrice.getMaxStock();
        } else if (buyTypeDynaShop == DynaShopType.STOCK) {
            DynamicPrice stockPrice = plugin.getPriceStock().createStockPrice(shopID, itemID);
            buyPrice = stockPrice.getBuyPrice();
            minBuy = stockPrice.getMinBuyPrice();
            maxBuy = stockPrice.getMaxBuyPrice();
            stock = stockPrice.getStock();
            minStock = stockPrice.getMinStock();
            maxStock = stockPrice.getMaxStock();
            // stockBuyModifier = stockPrice.getStockBuyModifier();
        } else if (typeDynaShop == DynaShopType.STATIC_STOCK) {
            DynamicPrice staticStockPrice = plugin.getPriceStock().createStaticStockPrice(shopID, itemID);
            buyPrice = staticStockPrice.getBuyPrice();
            minBuy = staticStockPrice.getMinBuyPrice();
            maxBuy = staticStockPrice.getMaxBuyPrice();
            stock = staticStockPrice.getStock();
            minStock = staticStockPrice.getMinStock();
            maxStock = staticStockPrice.getMaxStock();
            // stockBuyModifier = staticStockPrice.getStockBuyModifier();
        } else if (typeDynaShop == DynaShopType.LINK) {
            // Récupérer l'item lié
            String linkedItemID = shopConfigManager.getItemValue(shopID, itemID, "link", String.class)
                .orElse(null);

            if (linkedItemID != null) {
                // Extraire les parties du lien (shopID:itemID)
                String[] parts = linkedItemID.split(":");
                if (parts.length == 2) {
                    String linkedShopID = parts[0];
                    String linkedItemID2 = parts[1];
                    
                    // Récupérer le prix de l'item lié
                    ItemStack linkedItemStack = ShopGuiPlusApi.getShop(linkedShopID).getShopItem(linkedItemID2).getItem();
                    // DynamicPrice linkedPrice = getOrLoadPrice(linkedShopID, linkedItemID2, linkedItemStack);
                    DynamicPrice linkedPrice = getOrLoadPriceInternal(player, linkedShopID, linkedItemID2, linkedItemStack, visited, lastResults, true);
                    // DynaShopPlugin.getInstance().getLogger().info("Chargement du prix lié pour l'item " + itemID + " dans le shop " + shopID + " (linkedShop: " + linkedShopID + ", linkedItem: " + linkedItemID2 + ")");
                    if (linkedPrice != null) {
                        buyPrice = linkedPrice.getBuyPrice();
                        minBuy = linkedPrice.getMinBuyPrice();
                        maxBuy = linkedPrice.getMaxBuyPrice();
                        growthBuy = linkedPrice.getGrowthBuy();
                        decayBuy = linkedPrice.getDecayBuy();
                        stock = linkedPrice.getStock();
                        minStock = linkedPrice.getMinStock();
                        maxStock = linkedPrice.getMaxStock();
                        // stockBuyModifier = linkedPrice.getStockBuyModifier();
                    }
                }
            } else {
                plugin.getLogger().warning("Item " + itemID + " in shop " + shopID + " is linked but no linked item found.");
                return null;
            }
        } else {
            // Charger les prix dynamiques depuis la base de données
            Optional<DynamicPrice> priceFromDatabase = plugin.getStorageManager().getPrices(shopID, itemID);

            // Charger les données supplémentaires depuis les fichiers de configuration
            ItemPriceData priceData = shopConfigManager.getItemAllValues(shopID, itemID);

            buyPrice = priceFromDatabase.map(DynamicPrice::getBuyPrice).orElse(priceData.buyPrice.orElse(-1.0));
            minBuy = priceData.minBuy.orElse(buyPrice);
            maxBuy = priceData.maxBuy.orElse(buyPrice);
            
            if (priceData.minBuyLink.isPresent() && priceData.minBuyLink.get().contains(":")) {
                String[] parts = priceData.minBuyLink.get().split(":");
                if (parts.length == 2) {
                    String linkShop = parts[0];
                    String linkItem = parts[1];
                    ItemStack linkedItemStack = ShopGuiPlusApi.getShop(linkShop).getShopItem(linkItem).getItem();
                    DynamicPrice linkedPrice = getOrLoadPriceInternal(player, linkShop, linkItem, linkedItemStack, visited, lastResults, true);
                    if (linkedPrice != null) {
                        double linkedMin = linkedPrice.getMinBuyPrice();
                        // DynaShopPlugin.getInstance().getLogger().info("Linked minBuy for " + itemID + " in shop " + shopID + ": " + linkedMin);
                        if (linkedMin > 0) {
                            minBuy = Math.max(minBuy, linkedMin);
                        }
                    }
                }
            }
            if (priceData.maxBuyLink.isPresent() && priceData.maxBuyLink.get().contains(":")) {
                String[] parts = priceData.maxBuyLink.get().split(":");
                if (parts.length == 2) {
                    String linkShop = parts[0];
                    String linkItem = parts[1];
                    ItemStack linkedItemStack = ShopGuiPlusApi.getShop(linkShop).getShopItem(linkItem).getItem();
                    // DynaShopPlugin.getInstance().getLogger().info("Linking maxBuy for " + itemID + " in shop " + shopID + ": " + linkShop + ":" + linkItem + " (visited: " + visited + ")" + " (lastResults: " + lastResults + ")");
                    DynamicPrice linkedPrice = getOrLoadPriceInternal(player, linkShop, linkItem, linkedItemStack, visited, lastResults, true);
                    // DynaShopPlugin.getInstance().getLogger().info("2: (visited: " + visited + ")" + " (lastResults: " + lastResults + ")");
                    if (linkedPrice != null) {
                        double linkedMax = linkedPrice.getMaxBuyPrice();
                        // DynaShopPlugin.getInstance().getLogger().info("Linked maxBuy for " + itemID + " in shop " + shopID + ": " + linkedMax);
                        if (linkedMax > 0) {
                            maxBuy = Math.min(maxBuy, linkedMax);
                        }
                    }
                }
            }
            // // Ajout : lecture des minLink/maxLink
            // Optional<String> minBuyLink = shopConfigManager.getItemValue(shopID, itemID, "buyDynamic.minLink", String.class);
            // if (minBuyLink.isPresent() && minBuyLink.get().contains(":")) {
            //     String[] parts = minBuyLink.get().split(":");
            //     if (parts.length == 2) {
            //         String linkShop = parts[0];
            //         String linkItem = parts[1];
            //         ItemStack linkedItemStack = ShopGuiPlusApi.getShop(linkShop).getShopItem(linkItem).getItem();
            //         // double linkedMin = getOrLoadPrice(linkShop, linkItem, linkedItemStack).getMinBuyPrice();
            //         double linkedMin = getOrLoadPrice(linkShop, linkItem, linkedItemStack).getBuyPrice();
            //         DynaShopPlugin.getInstance().getLogger().info("Linked minBuy for " + itemID + " in shop " + shopID + ": " + linkedMin);  
            //         minBuy = Math.max(minBuy, linkedMin);
            //     }
            // }
            // Optional<String> maxBuyLink = shopConfigManager.getItemValue(shopID, itemID, "buyDynamic.maxLink", String.class);
            // if (maxBuyLink.isPresent() && maxBuyLink.get().contains(":")) {
            //     String[] parts = maxBuyLink.get().split(":");
            //     if (parts.length == 2) {
            //         String linkShop = parts[0];
            //         String linkItem = parts[1];
            //         ItemStack linkedItemStack = ShopGuiPlusApi.getShop(linkShop).getShopItem(linkItem).getItem();
            //         // double linkedMax = getOrLoadPrice(linkShop, linkItem, linkedItemStack).getMaxBuyPrice();
            //         double linkedMax = getOrLoadPrice(linkShop, linkItem, linkedItemStack).getBuyPrice();
            //         maxBuy = Math.min(maxBuy, linkedMax);
            //     }
            // }
            // if (priceData.minBuyLink.isPresent() && priceData.minBuyLink.get().contains(":")) {
            //     String[] parts = priceData.minBuyLink.get().split(":");
            //     if (parts.length == 2) {
            //         String linkShop = parts[0];
            //         String linkItem = parts[1];
            //         ItemStack linkedItemStack = ShopGuiPlusApi.getShop(linkShop).getShopItem(linkItem).getItem();
            //         // Passe le set visited pour éviter les cycles
            //         // double linkedMin = getOrLoadPrice(null, linkShop, linkItem, linkedItemStack, visited).getMinBuyPrice();
            //         // double linkedMin = getOrLoadPrice(linkShop, linkItem, linkedItemStack).getMinBuyPrice();
            //         DynamicPrice linkedPrice = getOrLoadPrice(null, linkShop, linkItem, linkedItemStack, visited);
            //         double linkedMin = (linkedPrice != null) ? linkedPrice.getMinBuyPrice() : minBuy; // ou une valeur par défaut
            //         DynaShopPlugin.getInstance().getLogger().info("Linked minBuy for " + itemID + " in shop " + shopID + ": " + linkedMin);  
            //         minBuy = Math.max(minBuy, linkedMin);
            //     }
            // }
            // if (priceData.minBuyLink.isPresent() && priceData.minBuyLink.get().contains(":")) {
            //     String[] parts = priceData.minBuyLink.get().split(":");
            //     if (parts.length == 2) {
            //         String linkShop = parts[0];
            //         String linkItem = parts[1];
            //         ItemStack linkedItemStack = ShopGuiPlusApi.getShop(linkShop).getShopItem(linkItem).getItem();
            //         // DynamicPrice linkedPrice = getOrLoadPrice(null, linkShop, linkItem, linkedItemStack, visited);
            //         DynamicPrice linkedPrice = getOrLoadPriceInternal(linkShop, linkItem, linkedItemStack, visited);
            //         // if (linkedPrice == null) {
            //         //     plugin.getLogger().warning("Cycle ou erreur lors du calcul du minBuy link pour " + itemID + " in shop " + shopID + " (lien: " + linkShop + ":" + linkItem + ")");
            //         //     // On ne touche pas à minBuy, on sort de la logique de lien
            //         //     return null;
            //         // } else {
            //         //     double linkedMin = linkedPrice.getMinBuyPrice();
            //         //     // STOPPE TOUT si la valeur est -1.0 (valeur par défaut)
            //         //     if (linkedMin == -1.0) {
            //         //         plugin.getLogger().warning("Valeur -1.0 détectée pour minBuy link " + linkShop + ":" + linkItem + " (cycle ou erreur probable)");
            //         //         return null;
            //         //     }
            //         //     DynaShopPlugin.getInstance().getLogger().info("Linked minBuy for " + itemID + " in shop " + shopID + ": " + linkedMin + " (lien: " + linkShop + ":" + linkItem + ")" + visited);
            //         //     minBuy = Math.max(minBuy, linkedMin);
            //         // }
            //         if (linkedPrice != null) {
            //             double linkedMin = linkedPrice.getMinBuyPrice();
            //             if (linkedMin > 0) {
            //                 minBuy = Math.max(minBuy, linkedMin);
            //                 DynaShopPlugin.getInstance().getLogger().info("Linked minBuy for " + itemID + " in shop " + shopID + ": " + linkedMin + " (lien: " + linkShop + ":" + linkItem + ")" + visited);
            //             } else {
            //                 plugin.getLogger().warning("minBuy link " + linkShop + ":" + linkItem + " non exploitable (cycle ou valeur négative)");
            //                 // On garde la valeur locale
            //             }
            //         } else {
            //             plugin.getLogger().warning("minBuy link " + linkShop + ":" + linkItem + " non exploitable (cycle ou erreur)");
            //             // On garde la valeur locale
            //         }
            //     }
            // }
            // if (priceData.maxBuyLink.isPresent() && priceData.maxBuyLink.get().contains(":")) {
            //     String[] parts = priceData.maxBuyLink.get().split(":");
            //     if (parts.length == 2) {
            //         String linkShop = parts[0];
            //         String linkItem = parts[1];
            //         ItemStack linkedItemStack = ShopGuiPlusApi.getShop(linkShop).getShopItem(linkItem).getItem();
            //         // double linkedMax = getOrLoadPrice(null, linkShop, linkItem, linkedItemStack, visited).getMaxBuyPrice();
            //         // double linkedMax = getOrLoadPrice(linkShop, linkItem, linkedItemStack).getBuyPrice();
            //         DynamicPrice linkedPrice = getOrLoadPrice(null, linkShop, linkItem, linkedItemStack, visited);
            //         double linkedMax = (linkedPrice != null) ? linkedPrice.getMaxBuyPrice() : maxBuy; // ou une valeur par défaut
            //         DynaShopPlugin.getInstance().getLogger().info("Linked maxBuy for " + itemID + " in shop " + shopID + ": " + linkedMax);
            //         maxBuy = Math.min(maxBuy, linkedMax);
            //     }
            // }
            // if (priceData.maxBuyLink.isPresent() && priceData.maxBuyLink.get().contains(":")) {
            //     String[] parts = priceData.maxBuyLink.get().split(":");
            //     if (parts.length == 2) {
            //         String linkShop = parts[0];
            //         String linkItem = parts[1];
            //         ItemStack linkedItemStack = ShopGuiPlusApi.getShop(linkShop).getShopItem(linkItem).getItem();
            //         // DynamicPrice linkedPrice = getOrLoadPrice(null, linkShop, linkItem, linkedItemStack, visited);
            //         DynamicPrice linkedPrice = getOrLoadPriceInternal(linkShop, linkItem, linkedItemStack, visited);
            //         // if (linkedPrice == null) {
            //         //     plugin.getLogger().warning("Cycle ou erreur lors du calcul du maxBuy link pour " + itemID + " in shop " + shopID + " (lien: " + linkShop + ":" + linkItem + ")");
            //         //     // On ne touche pas à maxBuy, on sort de la logique de lien
            //         //     return null;
            //         // } else {
            //         //     double linkedMax = linkedPrice.getMaxBuyPrice();
            //         //     // STOPPE TOUT si la valeur est -1.0 (valeur par défaut)
            //         //     if (linkedMax == -1.0) {
            //         //         plugin.getLogger().warning("Valeur -1.0 détectée pour maxBuy link " + linkShop + ":" + linkItem + " (cycle ou erreur probable)");
            //         //         return null;
            //         //     }
            //         //     DynaShopPlugin.getInstance().getLogger().info("Linked maxBuy for " + itemID + " in shop " + shopID + ": " + linkedMax);
            //         //     maxBuy = Math.min(maxBuy, linkedMax);
            //         // }
            //         if (linkedPrice != null) {
            //             double linkedMax = linkedPrice.getMaxBuyPrice();
            //             if (linkedMax > 0) {
            //                 maxBuy = Math.min(maxBuy, linkedMax);
            //                 DynaShopPlugin.getInstance().getLogger().info("Linked maxBuy for " + itemID + " in shop " + shopID + ": " + linkedMax + " (lien: " + linkShop + ":" + linkItem + ")" + visited);
            //             } else {
            //                 plugin.getLogger().warning("maxBuy link " + linkShop + ":" + linkItem + " non exploitable (cycle ou valeur négative)");
            //                 // On garde la valeur locale
            //             }
            //         } else {
            //             plugin.getLogger().warning("maxBuy link " + linkShop + ":" + linkItem + " non exploitable (cycle ou erreur)");
            //             // On garde la valeur locale
            //         }
            //     }
            // }

            growthBuy = priceData.growthBuy.orElseGet(() -> {
                boolean hasBuyDynamic = shopConfigManager.hasSection(shopID, itemID, "buyDynamic");
                return hasBuyDynamic ? plugin.getDataConfig().getBuyGrowthRate() : 1.0;
            });

            decayBuy = priceData.decayBuy.orElseGet(() -> {
                boolean hasBuyDynamic = shopConfigManager.hasSection(shopID, itemID, "buyDynamic");
                return hasBuyDynamic ? plugin.getDataConfig().getBuyDecayRate() : 1.0;
            });

            stock = priceFromDatabase.map(DynamicPrice::getStock).orElse(priceData.stock.orElse(0));
            minStock = priceData.minStock.orElseGet(() -> {
                boolean hasStock = shopConfigManager.hasSection(shopID, itemID, "stock");
                return hasStock ? plugin.getDataConfig().getStockMin() : 0;
            });
            maxStock = priceData.maxStock.orElseGet(() -> {
                boolean hasStock = shopConfigManager.hasSection(shopID, itemID, "stock");
                return hasStock ? plugin.getDataConfig().getStockMax() : Integer.MAX_VALUE;
            });
            stockBuyModifier = priceData.stockBuyModifier.orElseGet(() -> {
                boolean hasStock = shopConfigManager.hasSection(shopID, itemID, "stock");
                return hasStock ? plugin.getDataConfig().getStockBuyModifier() : 1.0;
            });
        }

        if (sellTypeDynaShop == DynaShopType.RECIPE) {
            // DynamicPrice recipePrice = plugin.getPriceRecipe().createRecipePrice(shopID, itemID);
            // DynamicPrice recipePrice = plugin.getPriceRecipe().createRecipePrice(shopID, itemID,lastResults);
            DynamicPrice recipePrice = plugin.getPriceRecipe().createRecipePrice(shopID, itemID, visited, lastResults);
            // DynamicPrice recipePrice = plugin.getPriceRecipe().createRecipePrice(shopID, itemID, new HashSet<>(), lastResults);
            sellPrice = recipePrice.getSellPrice();
            minSell = recipePrice.getMinSellPrice();
            maxSell = recipePrice.getMaxSellPrice();
            growthSell = recipePrice.getGrowthSell();
            decaySell = recipePrice.getDecaySell();
            stock = recipePrice.getStock();
            minStock = recipePrice.getMinStock();
            maxStock = recipePrice.getMaxStock();
        } else if (sellTypeDynaShop == DynaShopType.STOCK) {
            DynamicPrice stockPrice = plugin.getPriceStock().createStockPrice(shopID, itemID);
            sellPrice = stockPrice.getSellPrice();
            minSell = stockPrice.getMinSellPrice();
            maxSell = stockPrice.getMaxSellPrice();
            stock = stockPrice.getStock();
            minStock = stockPrice.getMinStock();
            maxStock = stockPrice.getMaxStock();
            // stockSellModifier = stockPrice.getStockSellModifier();
        } else if (typeDynaShop == DynaShopType.STATIC_STOCK) {
            DynamicPrice staticStockPrice = plugin.getPriceStock().createStaticStockPrice(shopID, itemID);
            sellPrice = staticStockPrice.getSellPrice();
            minSell = staticStockPrice.getMinSellPrice();
            maxSell = staticStockPrice.getMaxSellPrice();
            stock = staticStockPrice.getStock();
            minStock = staticStockPrice.getMinStock();
            maxStock = staticStockPrice.getMaxStock();
            // stockSellModifier = staticStockPrice.getStockSellModifier();
        } else if (typeDynaShop == DynaShopType.LINK) {
            // Récupérer l'item lié
            String linkedItemID = shopConfigManager.getItemValue(shopID, itemID, "link", String.class)
                .orElse(null);
            
            if (linkedItemID != null) {
                // Extraire les parties du lien (shopID:itemID)
                String[] parts = linkedItemID.split(":");
                if (parts.length == 2) {
                    String linkedShopID = parts[0];
                    String linkedItemID2 = parts[1];
                    
                    // Récupérer le prix de l'item lié
                    ItemStack linkedItemStack = ShopGuiPlusApi.getShop(linkedShopID).getShopItem(linkedItemID2).getItem();
                    // DynamicPrice linkedPrice = getOrLoadPrice(linkedShopID, linkedItemID2, linkedItemStack);
                    DynamicPrice linkedPrice = getOrLoadPriceInternal(player, linkedShopID, linkedItemID2, linkedItemStack, visited, lastResults, false);
                    if (linkedPrice != null) {
                        sellPrice = linkedPrice.getSellPrice();
                        minSell = linkedPrice.getMinSellPrice();
                        maxSell = linkedPrice.getMaxSellPrice();
                        growthSell = linkedPrice.getGrowthSell();
                        decaySell = linkedPrice.getDecaySell();
                        stock = linkedPrice.getStock();
                        minStock = linkedPrice.getMinStock();
                        maxStock = linkedPrice.getMaxStock();
                        // stockSellModifier = linkedPrice.getStockSellModifier();
                    }
                }
            } else {
                plugin.getLogger().warning("Item " + itemID + " in shop " + shopID + " is linked but no linked item found.");
                return null;
            }
        } else {
            // Charger les prix dynamiques depuis la base de données
            Optional<DynamicPrice> priceFromDatabase = plugin.getStorageManager().getPrices(shopID, itemID);
            
            // Charger les données supplémentaires depuis les fichiers de configuration
            ItemPriceData priceData = shopConfigManager.getItemAllValues(shopID, itemID);
            
            sellPrice = priceFromDatabase.map(DynamicPrice::getSellPrice).orElse(priceData.sellPrice.orElse(-1.0));
            minSell = priceData.minSell.orElse(sellPrice);
            maxSell = priceData.maxSell.orElse(sellPrice);

            if (priceData.minSellLink.isPresent() && priceData.minSellLink.get().contains(":")) {
                String[] parts = priceData.minSellLink.get().split(":");
                if (parts.length == 2) {
                    String linkShop = parts[0];
                    String linkItem = parts[1];
                    ItemStack linkedItemStack = ShopGuiPlusApi.getShop(linkShop).getShopItem(linkItem).getItem();
                    DynamicPrice linkedPrice = getOrLoadPriceInternal(player, linkShop, linkItem, linkedItemStack, visited, lastResults, false);
                    if (linkedPrice != null) {
                        double linkedMin = linkedPrice.getMinSellPrice();
                        // DynaShopPlugin.getInstance().getLogger().info("Linked minSell for " + itemID + " in shop " + shopID + ": " + linkedMin);  
                        if (linkedMin > 0) {
                            minSell = Math.max(minSell, linkedMin);
                        }
                    }
                }
            }
            if (priceData.maxSellLink.isPresent() && priceData.maxSellLink.get().contains(":")) {
                String[] parts = priceData.maxSellLink.get().split(":");
                if (parts.length == 2) {
                    String linkShop = parts[0];
                    String linkItem = parts[1];
                    ItemStack linkedItemStack = ShopGuiPlusApi.getShop(linkShop).getShopItem(linkItem).getItem();
                    // DynaShopPlugin.getInstance().getLogger().info("Linking maxSell for " + itemID + " in shop " + shopID + ": " + linkShop + ":" + linkItem + " (visited: " + visited + ")" + " (lastResults: " + lastResults + ")");
                    DynamicPrice linkedPrice = getOrLoadPriceInternal(player, linkShop, linkItem, linkedItemStack, visited, lastResults, false);
                    // DynaShopPlugin.getInstance().getLogger().info("2: (visited: " + visited + ")" + " (lastResults: " + lastResults + ")");
                    if (linkedPrice != null) {
                        double linkedMax = linkedPrice.getMaxSellPrice();
                        // DynaShopPlugin.getInstance().getLogger().info("Linked maxSell for " + itemID + " in shop " + shopID + ": " + linkedMax);
                        if (linkedMax > 0) {
                            maxSell = Math.min(maxSell, linkedMax);
                        }
                    }
                }
            }

            growthSell = priceData.growthSell.orElseGet(() -> {
                boolean hasSellDynamic = shopConfigManager.hasSection(shopID, itemID, "sellDynamic");
                return hasSellDynamic ? plugin.getDataConfig().getSellGrowthRate() : 1.0;
            });

            decaySell = priceData.decaySell.orElseGet(() -> {
                boolean hasSellDynamic = shopConfigManager.hasSection(shopID, itemID, "sellDynamic");
                return hasSellDynamic ? plugin.getDataConfig().getSellDecayRate() : 1.0;
            });

            // stock = priceFromDatabase.map(DynamicPrice::getStock).orElse(priceData.stock.orElse(0));
            // minStock = priceData.minStock.orElseGet(() -> {
            //     boolean hasStock = shopConfigManager.hasSection(shopID, itemID, "stock");
            //     return hasStock ? dataConfig.getStockMin() : 0;
            // });
            // maxStock = priceData.maxStock.orElseGet(() -> {
            //     boolean hasStock = shopConfigManager.hasSection(shopID, itemID, "stock");
            //     return hasStock ? dataConfig.getStockMax() : Integer.MAX_VALUE;
            // });
            stockSellModifier = priceData.stockSellModifier.orElseGet(() -> {
                boolean hasStock = shopConfigManager.hasSection(shopID, itemID, "stock");
                return hasStock ? plugin.getDataConfig().getStockSellModifier() : 1.0;
            });
        }

        // // Traiter les prix basés sur les recettes
        // if (typeDynaShop == DynaShopType.RECIPE) {
        //     price = plugin.getPriceRecipe().createRecipePrice(shopID, itemID, itemStack);
        // } else if (typeDynaShop == DynaShopType.STOCK) {
        //     price = plugin.getPriceStock().createStockPrice(shopID, itemID);
        // } else if (typeDynaShop == DynaShopType.STATIC_STOCK) {
        //     price = plugin.getPriceStock().createStaticStockPrice(shopID, itemID);
        // } else if (typeDynaShop == DynaShopType.LINK) {
        //     // Récupérer l'item lié
        //     String linkedItemID = shopConfigManager.getItemValue(shopID, itemID, "link", String.class)
        //         .orElse(null);
            
        //     if (linkedItemID != null) {
        //         // Extraire les parties du lien (shopID:itemID)
        //         String[] parts = linkedItemID.split(":");
        //         if (parts.length == 2) {
        //             String linkedShopID = parts[0];
        //             String linkedItemID2 = parts[1];
                    
        //             // Récupérer le prix de l'item lié
        //             ItemStack linkedItemStack = ShopGuiPlusApi.getShop(linkedShopID).getShopItem(linkedItemID2).getItem();
        //             price = getOrLoadPrice(linkedShopID, linkedItemID2, linkedItemStack);
        //             // price.setDynaShopType(DynaShopType.LINK);
        //         }
        //     } else {
        //         plugin.getLogger().warning("Item " + itemID + " in shop " + shopID + " is linked but no linked item found.");
        //     }
        // } else {
        //     // Pour les types DYNAMIC et autres
        //     // Charger les prix depuis la base de données
        //     Optional<DynamicPrice> priceFromDatabase = plugin.getItemDataManager().getItemValues(shopID, itemID);
        
        //     // Charger les données supplémentaires depuis les fichiers de configuration
        //     ItemPriceData priceData = shopConfigManager.getItemAllValues(shopID, itemID);

        //     // Si aucune donnée n'est trouvée dans la base de données ou les fichiers de configuration, retourner null
        //     if (priceFromDatabase.isEmpty() && (priceData.buyPrice.isEmpty() || priceData.sellPrice.isEmpty())) {
        //         return null;
        //     }
            
        //     double buyPrice = -1.0; // Valeur par défaut si non spécifiée
        //     double sellPrice = -1.0; // Valeur par défaut si non spécifiée
        //     if (!priceData.buyPrice.isEmpty()) {
        //         buyPrice = priceFromDatabase.map(DynamicPrice::getBuyPrice).orElse(priceData.buyPrice.orElse(-1.0));
        //     }
        //     if (!priceData.sellPrice.isEmpty()) {
        //         sellPrice = priceFromDatabase.map(DynamicPrice::getSellPrice).orElse(priceData.sellPrice.orElse(-1.0));
        //     }
        
        //     double minBuy = priceData.minBuy.orElse(buyPrice);
        //     double maxBuy = priceData.maxBuy.orElse(buyPrice);
        //     double minSell = priceData.minSell.orElse(sellPrice);
        //     double maxSell = priceData.maxSell.orElse(sellPrice);
        
        //     double growthBuy = priceData.growthBuy.orElseGet(() -> {
        //         boolean hasBuyDynamic = shopConfigManager.hasSection(shopID, itemID, "buyDynamic");
        //         return hasBuyDynamic ? dataConfig.getBuyGrowthRate() : 1.0;
        //     });
        
        //     double decayBuy = priceData.decayBuy.orElseGet(() -> {
        //         boolean hasBuyDynamic = shopConfigManager.hasSection(shopID, itemID, "buyDynamic");
        //         return hasBuyDynamic ? dataConfig.getBuyDecayRate() : 1.0;
        //     });
        
        //     double growthSell = priceData.growthSell.orElseGet(() -> {
        //         boolean hasSellDynamic = shopConfigManager.hasSection(shopID, itemID, "sellDynamic");
        //         return hasSellDynamic ? dataConfig.getSellGrowthRate() : 1.0;
        //     });
        
        //     double decaySell = priceData.decaySell.orElseGet(() -> {
        //         boolean hasSellDynamic = shopConfigManager.hasSection(shopID, itemID, "sellDynamic");
        //         return hasSellDynamic ? dataConfig.getSellDecayRate() : 1.0;
        //     });
        
        //     int stock = priceFromDatabase.map(DynamicPrice::getStock).orElse(priceData.stock.orElse(0));
        //     int minStock = priceData.minStock.orElseGet(() -> {
        //         boolean hasStock = shopConfigManager.hasSection(shopID, itemID, "stock");
        //         return hasStock ? dataConfig.getStockMin() : 0;
        //     });
        //     int maxStock = priceData.maxStock.orElseGet(() -> {
        //         boolean hasStock = shopConfigManager.hasSection(shopID, itemID, "stock");
        //         return hasStock ? dataConfig.getStockMax() : Integer.MAX_VALUE;
        //     });
        //     double stockBuyModifier = priceData.stockBuyModifier.orElseGet(() -> {
        //         boolean hasStock = shopConfigManager.hasSection(shopID, itemID, "stock");
        //         return hasStock ? dataConfig.getStockBuyModifier() : 1.0;
        //     });
        //     double stockSellModifier = priceData.stockSellModifier.orElseGet(() -> {
        //         boolean hasStock = shopConfigManager.hasSection(shopID, itemID, "stock");
        //         return hasStock ? dataConfig.getStockSellModifier() : 1.0;
        //     });

        //     // Créer l'objet DynamicPrice avec les valeurs fusionnées
        //     price = new DynamicPrice(buyPrice, sellPrice, minBuy, maxBuy, minSell, maxSell, 
        //                             growthBuy, decayBuy, growthSell, decaySell, 
        //                             stock, minStock, maxStock, stockBuyModifier, stockSellModifier);
        //     // price.setDynaShopType(typeDynaShop);
        // }

        DynamicPrice price = new DynamicPrice(
            buyPrice, sellPrice, minBuy, maxBuy, minSell, maxSell,
            growthBuy, decayBuy, growthSell, decaySell,
            stock, minStock, maxStock,
            stockBuyModifier, stockSellModifier
        );
        
        // Stocker les types dans l'objet
        // if (price != null) {
            price.setDynaShopType(typeDynaShop);
            price.setBuyTypeDynaShop(buyTypeDynaShop);
            price.setSellTypeDynaShop(sellTypeDynaShop);
        // }
        
        boolean enchantmentEnabled = shopConfigManager
            .getItemValue(shopID, itemID, "dynaShop.enchantment", Boolean.class)
            .orElse(false);
        if (enchantmentEnabled && itemStack != null && price != null) {
            double multiplier = plugin.getPriceRecipe().getEnchantMultiplier(itemStack);
            if (multiplier != 1.0) {
                price.setBuyPrice(price.getBuyPrice() * multiplier);
                price.setSellPrice(price.getSellPrice() * multiplier);
                price.setMinBuyPrice(price.getMinBuyPrice() * multiplier);
                price.setMaxBuyPrice(price.getMaxBuyPrice() * multiplier);
                price.setMinSellPrice(price.getMinSellPrice() * multiplier);
                price.setMaxSellPrice(price.getMaxSellPrice() * multiplier);
            }
        }
        
        // Appliquer l'inflation après le chargement du prix
        price.applyInflation(shopID, itemID);
        
        return price;
    }


    private void applyGrowthOrDecayToIngredients(String shopID, String itemID, int amount, boolean isGrowth, Set<String> visitedItems, Map<String, DynamicPrice> lastResults, int depth) {
        // Limiter la profondeur de récursion
        if (depth > 5) return;

        // Éviter les boucles infinies - utiliser une clé composée
        String itemKey = shopID + ":" + itemID;
        plugin.info("Applying growth/decay to ingredients for " + itemKey + " (depth: " + depth + ")");

        if (visitedItems.contains(itemKey)) {
            // plugin.getLogger().info("Boucle de recette détectée pour " + itemKey + ", arrêt de la récursion");
            return;
        }
        visitedItems.add(itemKey);

        // Récupérer directement les ingrédients depuis PriceRecipe
        List<ItemStack> ingredients = plugin.getPriceRecipe().getIngredients(shopID, itemID);
        
        if (ingredients.isEmpty()) {
            // plugin.getLogger().warning("Aucun ingrédient trouvé pour " + shopID + ":" + itemID);
            return;
        }

        // Traiter chaque ingrédient
        for (ItemStack ingredient : ingredients) {
            if (ingredient == null || ingredient.getType() == Material.AIR) continue;

            // Rechercher l'ID du shop et de l'item pour cet ingrédient
            FoundItem foundItem = plugin.getPriceRecipe().findItemInShops(shopID, ingredient);
            if (!foundItem.isFound()) {
                // plugin.getLogger().warning("Impossible de trouver l'ingrédient " + ingredient.getType() + " dans les shops");
                continue;
            }

            String ingredientShopID = foundItem.getShopID();
            String ingredientID = foundItem.getItemID();
            int ingredientQuantity = ingredient.getAmount() * amount;
            
            // plugin.getLogger().info("Ingrédient trouvé: " + ingredientShopID + ":" + ingredientID + " x" + ingredientQuantity);

            // Vérifier si l'ingrédient est lui-même une recette
            // boolean isIngredientRecipe = plugin.getShopConfigManager().getTypeDynaShop(ingredientShopID, ingredientID) == DynaShopType.RECIPE;
            
            // DynaShopType ingredientType = plugin.getShopConfigManager().getTypeDynaShop(ingredientShopID, ingredientID);
            // DynamicPrice ingredientPrice = getOrLoadPrice(ingredientShopID, ingredientID, ingredient);
            DynamicPrice ingredientPrice = getOrLoadPriceInternal(null, ingredientShopID, ingredientID, ingredient, new HashSet<>(visitedItems), lastResults, false);

            if (ingredientPrice == null) {
                // plugin.getLogger().warning("Prix non trouvé pour " + ingredientShopID + ":" + ingredientID);
                continue;
            }
            DynaShopType ingredientType = ingredientPrice.getDynaShopType();
            DynaShopType buyTypeDynaShop = ingredientPrice.getBuyTypeDynaShop();
            DynaShopType sellTypeDynaShop = ingredientPrice.getSellTypeDynaShop();

            if (buyTypeDynaShop == DynaShopType.NONE || buyTypeDynaShop == DynaShopType.UNKNOWN) buyTypeDynaShop = ingredientType;
            if (sellTypeDynaShop == DynaShopType.NONE || sellTypeDynaShop == DynaShopType.UNKNOWN) sellTypeDynaShop = ingredientType;

            // // Si l'ingrédient est une recette, appliquer la récursion d'abord
            // if (isIngredientRecipe) {
            //     // plugin.getLogger().info("Ingrédient " + ingredientID + " est une recette, récursion");
            //     applyGrowthOrDecayToIngredients(ingredientShopID, ingredientID, ingredient, ingredientQuantity, isGrowth, new ArrayList<>(visitedItems), depth + 1);
            // }

            // // Traiter selon le type d'ingrédient (même s'il s'agit d'une recette)
            // processIngredient(ingredientShopID, ingredientID, ingredientPrice, ingredientType, ingredientQuantity, isGrowth);
            
            // // Sauvegarder les modifications si ce n'est pas une recette
            // if (!isIngredientRecipe) {
            //     // plugin.getLogger().info("Sauvegarde des modifications pour " + ingredientShopID + ":" + ingredientID);
            //     plugin.getBatchDatabaseUpdater().queueUpdate(ingredientShopID, ingredientID, ingredientPrice);
            // }
            // Si l'ingrédient est une recette, appliquer la récursion d'abord
            // if (isIngredientRecipe) {

            // if (ingredientType == DynaShopType.RECIPE) {
            //     // Appliquer la récursion uniquement, sans modifier le prix directement
            //     applyGrowthOrDecayToIngredients(ingredientShopID, ingredientID, ingredientQuantity, isGrowth, new HashSet<>(visitedItems), lastResults, depth + 1);
            // } else if (ingredientType == DynaShopType.LINK) {
            //     // Pour les liens, on applique la récursion mais on ne modifie pas le prix
            //     applyGrowthOrDecayToIngredients(ingredientShopID, ingredientID, ingredientQuantity, isGrowth, new HashSet<>(visitedItems), lastResults, depth + 1);
            // } else {
            //     // Traiter selon le type d'ingrédient (uniquement pour les non-recettes)
            //     processIngredient(ingredientShopID, ingredientID, ingredientPrice, ingredientType, ingredientQuantity, isGrowth);
                
            //     // Sauvegarder les modifications
            //     plugin.getBatchDatabaseUpdater().queueUpdate(ingredientShopID, ingredientID, ingredientPrice, true);
            // }

            if (buyTypeDynaShop == DynaShopType.RECIPE || sellTypeDynaShop == DynaShopType.RECIPE) {
                // Appliquer la récursion uniquement, sans modifier le prix directement
                applyGrowthOrDecayToIngredients(ingredientShopID, ingredientID, ingredientQuantity, isGrowth, new HashSet<>(visitedItems), lastResults, depth + 1);
            } else if (buyTypeDynaShop == DynaShopType.LINK || sellTypeDynaShop == DynaShopType.LINK) {
                // Pour les liens, on applique la récursion mais on ne modifie pas le prix
                handleLinkedPrice(ingredientShopID, ingredientID, ingredient, isGrowth ? ShopAction.BUY : ShopAction.SELL, ingredientQuantity);
            } else {
                // Traiter selon le type d'ingrédient (uniquement pour les non-recettes)
                processIngredient(ingredientShopID, ingredientID, ingredientPrice, ingredientType, ingredientQuantity, isGrowth);
                
                // Sauvegarder les modifications
                plugin.getBatchDatabaseUpdater().queueUpdate(ingredientShopID, ingredientID, ingredientPrice, true);
            }

        }
    }

    /**
     * Traite un ingrédient selon son type
     */
    private void processIngredient(String shopID, String itemID, DynamicPrice price, DynaShopType type, int quantity, boolean isGrowth) {
        if (type == DynaShopType.STOCK || type == DynaShopType.STATIC_STOCK) {
            if (isGrowth) {
                // Achat: diminuer le stock des ingrédients
                plugin.getPriceStock().processBuyTransaction(shopID, itemID, quantity);
            } else {
                // Vente: augmenter le stock des ingrédients
                plugin.getPriceStock().processSellTransaction(shopID, itemID, quantity);
            }
            
            // Mettre à jour l'objet price
            updatePriceFromStock(shopID, itemID, price);
        } else {
            // Pour les types non-stock, appliquer growth/decay directement
            if (isGrowth) {
                price.applyGrowth(quantity);
            } else {
                price.applyDecay(quantity);
            }
        }
    }

    /**
     * Met à jour un prix dynamique avec les valeurs actuelles du stock
     */
    private void updatePriceFromStock(String shopID, String itemID, DynamicPrice price) {
        double newBuyPrice = plugin.getPriceStock().calculatePrice(shopID, itemID, "buyPrice");
        double newSellPrice = plugin.getPriceStock().calculatePrice(shopID, itemID, "sellPrice");
        
        price.setBuyPrice(newBuyPrice);
        price.setSellPrice(newSellPrice);
        price.setStock(plugin.getStorageManager().getStock(shopID, itemID).orElse(0));
    }

    // // Méthode utilitaire pour formater le temps
    // private String formatTime(long seconds) {
    //     if (seconds < 60) {
    //         return seconds + " sec";
    //     } else if (seconds < 3600) {
    //         return (seconds / 60) + " min";
    //     } else if (seconds < 86400) {
    //         return (seconds / 3600) + " h";
    //     } else {
    //         return (seconds / 86400) + " d";
    //     }
    // }
    // private String formatTime(long seconds) {
    //     if (seconds < 0) {
    //         return "0 sec"; // Gérer les valeurs négatives
    //     }
        
    //     if (seconds < 60) {
    //         // Moins d'une minute
    //         return seconds + " sec";
    //     } else if (seconds < 3600) {
    //         // Moins d'une heure: afficher minutes et secondes
    //         long minutes = seconds / 60;
    //         long remainingSeconds = seconds % 60;
            
    //         if (remainingSeconds == 0) {
    //             return minutes + " min";
    //         } else {
    //             return minutes + " min " + remainingSeconds + " sec";
    //         }
    //     } else if (seconds < 86400) {
    //         // Moins d'un jour: afficher heures et minutes
    //         long hours = seconds / 3600;
    //         long remainingMinutes = (seconds % 3600) / 60;
            
    //         if (remainingMinutes == 0) {
    //             return hours + " h";
    //         } else {
    //             return hours + " h " + remainingMinutes + " min";
    //         }
    //     } else {
    //         // Un jour ou plus: afficher jours et heures
    //         long days = seconds / 86400;
    //         long remainingHours = (seconds % 86400) / 3600;
            
    //         if (remainingHours == 0) {
    //             return days + " j";
    //         } else {
    //             return days + " j " + remainingHours + " h";
    //         }
    //     }
    // }
    /**
     * Méthode utilitaire pour formater le temps de manière lisible en utilisant l'API Duration
     */
    private String formatTime(long seconds) {
        if (seconds < 0) {
            return "0 sec";
        }
        
        // Option 1: Pour Java 9+ (utilise les méthodes XXXPart)
        Duration duration = Duration.ofSeconds(seconds);
        long years = duration.toDaysPart() / 365;
        long months = (duration.toDaysPart() % 365) / 30;
        long weeks = (duration.toDaysPart() % 30) / 7;
        long days = duration.toDaysPart() % 7;
        // long days = duration.toDaysPart();
        long hours = duration.toHoursPart();
        long minutes = duration.toMinutesPart();
        long remainingSeconds = duration.toSecondsPart();
        
        // // Option 2: Pour Java 8 (compatible avec toutes les versions Bukkit)
        // long days = TimeUnit.SECONDS.toDays(seconds);
        // long hours = TimeUnit.SECONDS.toHours(seconds) % 24;
        // long minutes = TimeUnit.SECONDS.toMinutes(seconds) % 60;
        // long remainingSeconds = seconds % 60;
        
        StringBuilder formatted = new StringBuilder();
        
        // if (days > 0) {
        //     formatted.append(days).append(" j");
        //     if (hours > 0) {
        //         formatted.append(" ").append(hours).append(" h");
        //     }
        //     if (minutes > 0) {
        //         formatted.append(" ").append(minutes).append(" min");
        //     }
        // } else if (hours > 0) {
        //     formatted.append(hours).append(" h");
        //     if (minutes > 0) {
        //         formatted.append(" ").append(minutes).append(" min");
        //     }
        // } else if (minutes > 0) {
        //     formatted.append(minutes).append(" min");
        //     if (remainingSeconds > 0) {
        //         formatted.append(" ").append(remainingSeconds).append(" sec");
        //     }
        // } else {
        //     formatted.append(remainingSeconds).append(" sec");
        // }
        // if (days > 0) {
        //     formatted.append(days).append("d");
        //     if (hours > 0) {
        //         formatted.append(" ").append(hours).append("h");
        //     }
        //     if (minutes > 0) {
        //         formatted.append(" ").append(minutes).append("min");
        //     }
        // } else if (hours > 0) {
        //     formatted.append(hours).append("h");
        //     if (minutes > 0) {
        //         formatted.append(" ").append(minutes).append("min");
        //     }
        // } else if (minutes > 0) {
        //     formatted.append(minutes).append("min");
        //     if (remainingSeconds > 0) {
        //         formatted.append(" ").append(remainingSeconds).append("sec");
        //     }
        // } else {
        //     formatted.append(remainingSeconds).append("sec");
        // }
        if (years > 0) {
            formatted.append(years).append("years");
            // if (months > 0) {
            //     formatted.append(" ").append(months).append("months");
            // }
            // if (weeks > 0) {
            //     formatted.append(" ").append(weeks).append("weeks");
            // }
            // if (days > 0) {
            //     formatted.append(" ").append(days).append("days");
            // }
        }
        if (months > 0) {
            formatted.append(" ").append(months).append("months");
        }
        if (weeks > 0) {
            formatted.append(" ").append(weeks).append("weeks");
        }
        if (days > 0) {
            formatted.append(" ").append(days).append("d");
        }
        if (hours > 0) {
            formatted.append(" ").append(hours).append("h");
        }
        if (minutes > 0) {
            formatted.append(" ").append(minutes).append("min");
        }
        if (remainingSeconds > 0) {
            formatted.append(" ").append(remainingSeconds).append("sec");
        }


        return formatted.toString();
    }

    // private static class TransactionInfo {
    //     final Player player;
    //     final ShopItem item;
    //     final ShopAction action;
    //     final int amount;
        
    //     TransactionInfo(Player player, ShopItem item, ShopAction action, int amount) {
    //         this.player = player;
    //         this.item = item;
    //         this.action = action;
    //         this.amount = amount;
    //     }
    // }

    /**
     * Gestion des cas où la limite est dépassée
     */
    private void handleLimitExceeded(Player player, String shopID, String itemID, boolean isBuy, ShopPreTransactionEvent event) {
        // Annuler l'événement si fourni
        if (event != null) {
            event.setCancelled(true);
        }
        
        // Extraire les informations de limite de manière asynchrone
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Récupérer directement du cache pour plus d'efficacité
                final String cacheKey = DynaShopPlugin.getLimitCacheKey(player.getUniqueId(), shopID, itemID, isBuy);
                LimitCacheEntry limitEntry = plugin.getLimitCache().getIfPresent(cacheKey);

                int remaining = 0;
                long nextAvailable = 0;
                if (limitEntry != null) {
                    remaining = limitEntry.remaining;
                    nextAvailable = limitEntry.nextAvailable;
                } else {
                    // Récupérer les limites depuis le TransactionLimiter
                    remaining = plugin.getTransactionLimiter().getRemainingAmountSync(player, shopID, itemID, isBuy);
                    nextAvailable = plugin.getTransactionLimiter().getNextAvailableTimeSync(player, shopID, itemID, isBuy);
                }
                
                final String message;
                if (remaining > 0) {
                    message = isBuy 
                        ? plugin.getLangConfig().getMsgLimitCannotBuy().replace("%limit%", String.valueOf(remaining))
                        : plugin.getLangConfig().getMsgLimitCannotSell().replace("%limit%", String.valueOf(remaining));
                } else {
                    if (nextAvailable > 0) {
                        message = plugin.getLangConfig().getMsgLimitReached().replace("%time%", formatTime(nextAvailable / 1000));
                    } else {
                        message = plugin.getLangConfig().getMsgLimit();
                    }
                }
                
                // Envoyer le message de manière synchrone
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                });
                
            } catch (Exception e) {
            // } catch (InterruptedException e) {
            //     Thread.currentThread().interrupt(); // Restaurer l'état d'interruption
                plugin.getLogger().severe("Error retrieving limits: " + e.getMessage());
            }
        });
    }
    
    /**
     * Enregistre un nouveau point de données pour l'historique des prix
     * @param shopId ID du shop
     * @param itemId ID de l'item
     * @param price Le prix à enregistrer
     * @param isBuy true pour un prix d'achat, false pour un prix de vente
     * @param amount Quantité échangée (volume)
     */
    public void recordPriceForHistory(String shopId, String itemId, DynamicPrice price, boolean isBuy, double amount) {
        // Définir l'intervalle de regroupement (en minutes)
        final int INTERVAL_MINUTES = 15; // Par exemple, 15 minutes
        
        LocalDateTime now = LocalDateTime.now();
        
        // Créer un nouveau point de données
        PriceDataPoint newPoint = new PriceDataPoint(
            now,
            price.getBuyPrice(), price.getBuyPrice(), price.getBuyPrice(), price.getBuyPrice(),
            price.getSellPrice(), price.getSellPrice(), price.getSellPrice(), price.getSellPrice(),
            amount
        );
        
        // Utiliser la nouvelle méthode avec regroupement temporel
        plugin.getStorageManager().savePriceDataPoint(shopId, itemId, newPoint, INTERVAL_MINUTES);
    }
    // public void recordPriceForHistory(String shopId, String itemId, DynamicPrice price, boolean isBuy, double amount) {
    //     // plugin.getLogger().info("Volume pour " + shopId + ":" + itemId + " - Valeur reçue: " + amount);
    //     // Récupérer l'historique existant
    //     PriceHistory history = DynaShopPlugin.getInstance().getStorageManager().getPriceHistory(shopId, itemId);
        
    //     // Si aucun point de données n'existe encore, utiliser le prix actuel comme référence
    //     if (history.getDataPoints().isEmpty()) {
    //         // if (isBuy) {
    //         //     history.addDataPoint(price.getBuyPrice(), price.getBuyPrice(), price.getBuyPrice(), price.getBuyPrice(), 0, 0, 0, 0, amount);
    //         // } else {
    //         //     history.addDataPoint(0, 0, 0, 0, price.getSellPrice(), price.getSellPrice(), price.getSellPrice(), price.getSellPrice(), amount);
    //         // }
    //         history.addDataPoint(price.getBuyPrice(), price.getBuyPrice(), price.getBuyPrice(), price.getBuyPrice(), price.getSellPrice(), price.getSellPrice(), price.getSellPrice(), price.getSellPrice(), amount);
    //         return;
    //     }
        
    //     // Récupérer le dernier point de données
    //     PriceDataPoint lastPoint = history.getDataPoints().get(history.getDataPoints().size() - 1);
        
    //     // Si le dernier point date de moins d'une heure, mettre à jour ce point
    //     LocalDateTime now = LocalDateTime.now();
    //     if (lastPoint.getTimestamp().plusHours(1).isAfter(now)) {
    //         // Garder les valeurs existantes pour l'autre type de prix
    //         double openBuy = lastPoint.getOpenBuyPrice();
    //         double closeBuy = lastPoint.getCloseBuyPrice();
    //         double highBuy = lastPoint.getHighBuyPrice();
    //         double lowBuy = lastPoint.getLowBuyPrice();
            
    //         double openSell = lastPoint.getOpenSellPrice();
    //         double closeSell = lastPoint.getCloseSellPrice(); 
    //         double highSell = lastPoint.getHighSellPrice();
    //         double lowSell = lastPoint.getLowSellPrice();

    //         // double volume = lastPoint.getVolume() + amount;
    //         double volume = amount;
            
    //         if (isBuy) {
    //             // Mise à jour des valeurs d'achat uniquement
    //             if (openBuy == 0) openBuy = price.getBuyPrice(); // Premier prix d'achat enregistré
    //             closeBuy = price.getBuyPrice();
    //             highBuy = Math.max(highBuy == 0 ? price.getBuyPrice() : highBuy, price.getBuyPrice());
    //             lowBuy = lowBuy == 0 ? price.getBuyPrice() : Math.min(lowBuy, price.getBuyPrice());
    //         } else {
    //             // Mise à jour des valeurs de vente uniquement
    //             if (openSell == 0) openSell = price.getSellPrice(); // Premier prix de vente enregistré
    //             closeSell = price.getSellPrice();
    //             highSell = Math.max(highSell == 0 ? price.getSellPrice() : highSell, price.getSellPrice());
    //             lowSell = lowSell == 0 ? price.getSellPrice() : Math.min(lowSell, price.getSellPrice());
    //         }
            
    //         // Supprimer le dernier point et ajouter le point mis à jour
    //         history.getDataPoints().remove(history.getDataPoints().size() - 1);
    //         history.addDataPoint(openBuy, closeBuy, highBuy, lowBuy, openSell, closeSell, highSell, lowSell, volume);
    //     } else {
    //         // Sinon, ajouter un nouveau point en conservant les dernières valeurs de l'autre type
    //         if (isBuy) {
    //             history.addDataPoint(
    //                 lastPoint.getCloseBuyPrice() > 0 ? lastPoint.getCloseBuyPrice() : price.getBuyPrice(),
    //                 price.getBuyPrice(),
    //                 Math.max(lastPoint.getCloseBuyPrice() > 0 ? lastPoint.getCloseBuyPrice() : price.getBuyPrice(), price.getBuyPrice()),
    //                 Math.min(lastPoint.getCloseBuyPrice() > 0 ? lastPoint.getCloseBuyPrice() : price.getBuyPrice(), price.getBuyPrice()),
    //                 lastPoint.getCloseSellPrice(),
    //                 lastPoint.getCloseSellPrice(),
    //                 lastPoint.getHighSellPrice(),
    //                 lastPoint.getLowSellPrice(),
    //                 amount
    //             );
    //         } else {
    //             history.addDataPoint(
    //                 lastPoint.getCloseBuyPrice(),
    //                 lastPoint.getCloseBuyPrice(),
    //                 lastPoint.getHighBuyPrice(),
    //                 lastPoint.getLowBuyPrice(),
    //                 lastPoint.getCloseSellPrice() > 0 ? lastPoint.getCloseSellPrice() : price.getSellPrice(),
    //                 price.getSellPrice(),
    //                 Math.max(lastPoint.getCloseSellPrice() > 0 ? lastPoint.getCloseSellPrice() : price.getSellPrice(), price.getSellPrice()),
    //                 Math.min(lastPoint.getCloseSellPrice() > 0 ? lastPoint.getCloseSellPrice() : price.getSellPrice(), price.getSellPrice()),
    //                 amount
    //             );
    //         }
    //     }
    // }

}