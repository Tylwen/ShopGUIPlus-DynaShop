package fr.tylwen.satyria.dynashop.listener;

import java.time.Duration;
// import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
// import java.util.Map;
// import java.security.cert.PKIXRevocationChecker.Option;
import java.util.Optional;
// import java.util.UUID;
// import java.util.concurrent.ConcurrentHashMap;
// import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;
// import java.util.concurrent.ExecutionException;
// import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;

// import javax.swing.text.StyledEditorKit.BoldAction;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
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
import fr.tylwen.satyria.dynashop.data.DynamicPrice;
import fr.tylwen.satyria.dynashop.data.ItemPriceData;
import fr.tylwen.satyria.dynashop.data.PriceRecipe;
import fr.tylwen.satyria.dynashop.data.ShopConfigManager;
import fr.tylwen.satyria.dynashop.data.param.DynaShopType;
import fr.tylwen.satyria.dynashop.config.DataConfig;

import net.brcdev.shopgui.ShopGuiPlusApi;
import net.brcdev.shopgui.event.ShopPostTransactionEvent;
import net.brcdev.shopgui.event.ShopPreTransactionEvent;
// import net.brcdev.shopgui.gui.gui.OpenGui;
// import net.brcdev.shopgui.player.PlayerData;
import net.brcdev.shopgui.shop.item.ShopItem;
// import net.md_5.bungee.api.ChatColor;
// import net.brcdev.shopgui.shop.Shop;
import net.brcdev.shopgui.shop.ShopManager.ShopAction;
import net.brcdev.shopgui.shop.ShopTransactionResult.ShopTransactionResultType;

public class DynaShopListener implements Listener {
    private DynaShopPlugin mainPlugin;
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

    public DynaShopListener(DynaShopPlugin mainPlugin) {
        this.mainPlugin = mainPlugin;
        this.priceRecipe = new PriceRecipe(mainPlugin.getConfigMain());
        this.dataConfig = new DataConfig(mainPlugin.getConfigMain());
        this.shopConfigManager = mainPlugin.getShopConfigManager();
    }


    /**
     * Événement déclenché avant une transaction de shop.
     * @param event
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onShopPreTransaction(ShopPreTransactionEvent event) {
        Player player = event.getPlayer();
        ShopItem item = event.getShopItem();
        int amount = event.getAmount();
        String shopID = item.getShop().getId();
        String itemID = item.getId();
        ItemStack itemStack = item.getItem();
        boolean isBuy = event.getShopAction() == ShopAction.BUY;

        // if (mainPlugin.getShopConfigManager().hasSection(shopID, itemID, "limit")) {
        //     // // Annuler immédiatement pour éviter le traitement par ShopGUI+
        //     // event.setCancelled(true);
            
        //     // Vérifier d'abord le cache pour une réponse rapide
        //     String cacheKey = player.getUniqueId() + ":" + shopID + ":" + itemID + ":" + (isBuy ? "buy" : "sell");
            
        //     // Vérifier d'abord dans le cache
        //     if (limitsCache.containsKey(cacheKey)) {
        //         LimitCacheEntry entry = limitsCache.get(cacheKey);
        //         // Si le cache est récent (moins de 5 secondes), l'utiliser
        //         if (System.currentTimeMillis() - entry.timestamp < 5000) {
        //             if (!entry.canPerform) {
        //                 // Si la limite est atteinte, annuler l'événement
        //                 event.setCancelled(true);
        //                 handleLimitExceeded(player, shopID, itemID, isBuy, event);
        //                 return;
        //             }
        //             // Limite respectée (selon le cache), laisser passer la transaction
        //             // return;
        //             // Limite respectée selon le cache, continuer directement avec le reste du traitement
        //             processRegularTransaction(event, player, item, amount, shopID, itemID, itemStack, isBuy);
        //             return;
        //         } else {
        //             // Le cache est périmé, on le supprime
        //             limitsCache.remove(cacheKey);
        //         }
        //     }
            
        //     // Stocker des informations de transaction pour utilisation ultérieure
        //     TransactionInfo transactionInfo = new TransactionInfo(player, item, event.getShopAction(), amount);
        //     pendingLimitChecks.put(cacheKey, transactionInfo);
            
        //     // Aucun cache valide, on effectue la vérification de manière asynchrone
        //     // et on annule l'événement par sécurité en attendant la réponse
        //     event.setCancelled(true);
            
            
        //     mainPlugin.getTransactionLimiter().canPerformTransaction(player, shopID, itemID, isBuy, amount).thenAcceptAsync(canPerform -> {
        //         // Cache le résultat pour les prochaines requêtes
        //         limitsCache.put(cacheKey, new LimitCacheEntry(canPerform, System.currentTimeMillis()));
                
        //         if (Boolean.TRUE.equals(canPerform)) {
        //             // // Replanifier la transaction
        //             mainPlugin.getLogger().info("Transaction autorisée pour " + player.getName() + " sur " + shopID + ":" + itemID);
        //             Bukkit.getScheduler().runTask(mainPlugin, () -> {
        //                 pendingLimitChecks.remove(cacheKey);
        //                 event.setCancelled(false); // Annuler l'annulation de l'événement
        //                 // Appeler directement la méthode qui traite le reste de la logique
        //                 processRegularTransaction(event, player, item, amount, shopID, itemID, itemStack, isBuy);
        //             });
        //         } else {
        //             // Obtient les informations supplémentaires de manière asynchrone
        //             handleLimitExceeded(player, shopID, itemID, isBuy, null);
        //             pendingLimitChecks.remove(cacheKey);
        //             // if (event.isCancelled()) {
        //             //     // // Annuler l'événement si la limite est atteinte
        //             //     // event.setCancelled(true);
        //             //     return;
        //             // }
        //             // event.setCancelled(true);
        //             // return;
        //         }
        //     });
        //     return;
        // }
        
        // if (mainPlugin.getShopConfigManager().hasSection(shopID, itemID, "limit")) {
        //     // Annuler l'événement en attendant la vérification
        //     // event.setCancelled(true);
            
        //     // Créer une clé pour identifier cette transaction
        //     String transactionKey = player.getUniqueId() + ":" + shopID + ":" + itemID + ":" + (isBuy ? "buy" : "sell");
            
        //     // Stocker des informations de transaction pour utilisation ultérieure
        //     TransactionInfo transactionInfo = new TransactionInfo(player, item, event.getShopAction(), amount);
        //     pendingLimitChecks.put(transactionKey, transactionInfo);
            
        //     // Vérifier directement en BDD sans utiliser de cache
        //     mainPlugin.getTransactionLimiter().canPerformTransaction(player, shopID, itemID, isBuy, amount).thenAcceptAsync(canPerform -> {
        //         if (Boolean.TRUE.equals(canPerform)) {
        //             // Transaction autorisée
        //             // mainPlugin.getLogger().info("Transaction autorisée pour " + player.getName() + " sur " + shopID + ":" + itemID);
        //             Bukkit.getScheduler().runTask(mainPlugin, () -> {
        //                 pendingLimitChecks.remove(transactionKey);
        //                 // Traiter la transaction normalement
        //                 // event.setCancelled(false); // Annuler l'annulation de l'événement
        //                 // mainPlugin.getLogger().info("État de l'événement : " + event.isCancelled());
        //                 processRegularTransaction(event, player, item, amount, shopID, itemID, itemStack, isBuy);
        //                 // mainPlugin.getLogger().info("État de l'événement : " + event.isCancelled());
        //             });
        //         } else {
        //             // Limite atteinte, afficher message d'erreur
        //             // Si la transaction n'est pas autorisée, annuler l'événement
        //             Bukkit.getScheduler().runTask(mainPlugin, () -> {
        //                 event.setCancelled(true);
        //                 handleLimitExceeded(player, shopID, itemID, isBuy, event);
        //                 pendingLimitChecks.remove(transactionKey);
        //             });
        //         }
        //     });
        //     // IMPORTANT: Annuler temporairement l'événement, il sera rétabli si la vérification réussit
        //     // event.setCancelled(true);
        //     return;
        // }
        // if (mainPlugin.getShopConfigManager().hasSection(shopID, itemID, "limit")) {
        //     // // Annuler l'événement en attendant la vérification
        //     // event.setCancelled(true);
            
        //     // Créer une clé pour identifier cette transaction
        //     String transactionKey = player.getUniqueId() + ":" + shopID + ":" + itemID + ":" + (isBuy ? "buy" : "sell");
            
        //     // Stocker des informations de transaction pour utilisation ultérieure
        //     TransactionInfo transactionInfo = new TransactionInfo(player, item, event.getShopAction(), amount);
        //     pendingLimitChecks.put(transactionKey, transactionInfo);

        //     // final AtomicBoolean isCancelled = new AtomicBoolean(false);
            
        //     // Vérifier directement en BDD sans utiliser de cache
        //     mainPlugin.getTransactionLimiter().canPerformTransaction(player, shopID, itemID, isBuy, amount).thenAccept(canPerform -> {
        //         Bukkit.getScheduler().runTask(mainPlugin, () -> {
        //             if (Boolean.FALSE.equals(canPerform)) {
        //                 // event.setCancelled(true);
        //                 handleLimitExceeded(player, shopID, itemID, isBuy, event);
        //             }
        //             pendingLimitChecks.remove(transactionKey);
        //         });
        //     });
        //     mainPlugin.getLogger().info("Etat de l'événement isCancelled ? : " + event.isCancelled());
        //     // mainPlugin.getLogger().info(pendingLimitChecks.get(transactionKey).toString());
        //     // event.setCancelled(false);
        //     if (event.isCancelled()) {
        //         // // Annuler l'événement si la limite est atteinte
        //         // event.setCancelled(true);
        //         return;
        //     }
        // }
        if (mainPlugin.getShopConfigManager().hasSection(shopID, itemID, "limit")) {
            // mainPlugin.getServer().getScheduler().runTaskAsynchronously(mainPlugin, () -> {
            //     try {
            //         boolean canPerform = mainPlugin.getTransactionLimiter().canPerformTransaction(player, shopID, itemID, isBuy, amount).get();
            //         if (!canPerform) {
            //             // event.setCancelled(true);
            //             handleLimitExceeded(player, shopID, itemID, isBuy, event);
            //             return;
            //         }
            //     } catch (InterruptedException | ExecutionException e) {
            //         // Gérer l'exception si nécessaire
            //         e.printStackTrace();
            //     }
            // });
            boolean canPerform = mainPlugin.getTransactionLimiter().canPerformTransactionSync(player, shopID, itemID, isBuy, amount);
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
        if (!shopConfigManager.hasStockSection(shopID, itemID) && !shopConfigManager.hasDynamicSection(shopID, itemID) && !shopConfigManager.hasRecipeSection(shopID, itemID)) {
            return; // Ignorer les items sans les sections requises
        }

        DynamicPrice price = getOrLoadPrice(shopID, itemID, itemStack);
        if (price == null) {
            return;
        }
        
        // Vérifier le mode STOCK et les limites de stock
        if (shopConfigManager.getTypeDynaShop(shopID, itemID) == DynaShopType.STOCK) {
            // Si c'est un achat et que le stock est vide
            // if (event.getShopAction() == ShopAction.BUY && price.getStock() <= 0) {
            if (event.getShopAction() == ShopAction.BUY && !DynaShopPlugin.getInstance().getPriceStock().canBuy(shopID, itemID, amount)) {
                event.setCancelled(true);
                if (event.getPlayer() != null) {
                    // event.getPlayer().sendMessage("§c[DynaShop] Cet item est en rupture de stock !");
                    event.getPlayer().sendMessage(ChatColor.translateAlternateColorCodes('&', this.mainPlugin.getLangConfig().getMsgOutOfStock()));
                }
                return;
            }
            
            // Si c'est une vente et que le stock est plein
            // if ((event.getShopAction() == ShopAction.SELL || event.getShopAction() == ShopAction.SELL_ALL) && price.getStock() >= price.getMaxStock()) {
            if ((event.getShopAction() == ShopAction.SELL || event.getShopAction() == ShopAction.SELL_ALL) && !DynaShopPlugin.getInstance().getPriceStock().canSell(shopID, itemID, amount)) {
                event.setCancelled(true);
                if (event.getPlayer() != null) {
                    // event.getPlayer().sendMessage("§c[DynaShop] Le stock de cet item est complet, impossible de vendre plus !");
                    // event.getPlayer().sendMessage(this.mainPlugin.getConfigLang().getString("messages.stockFull")
                    //     .replace("%item%", itemID)
                    //     .replace("%shop%", shopID));
                    // event.getPlayer().sendMessage(this.mainPlugin.getConfigLang().getString("stock.full-stock"));
                    event.getPlayer().sendMessage(ChatColor.translateAlternateColorCodes('&', this.mainPlugin.getLangConfig().getMsgFullStock()));
                }
                return;
            }
        }

        // // Vérifier le mode RECIPE et s'il y a des ingrédients en mode STOCK
        // if (shopConfigManager.getTypeDynaShop(shopID, itemID) == DynaShopType.RECIPE) {
        //     // Vérifier si tous les ingrédients sont en stock
        //     boolean allIngredientsInStock = true;
        //     for (ItemStack ingredient : price.getIngredients()) {
        //         if (!DynaShopPlugin.getInstance().getPriceStock().canBuy(shopID, ingredient.getType().name(), ingredient.getAmount())) {
        //             allIngredientsInStock = false;
        //             break;
        //         }
        //     }
        //     if (!allIngredientsInStock) {
        //         event.setCancelled(true);
        //         if (event.getPlayer() != null) {
        //             event.getPlayer().sendMessage(ChatColor.translateAlternateColorCodes('&', this.mainPlugin.getLangConfig().getMsgOutOfStock()));
        //         }
        //         return;
        //     }
        // }
        // // Vérifier le mode RECIPE et s'il y a des ingrédients en mode STOCK
        // if (shopConfigManager.getTypeDynaShop(shopID, itemID) == DynaShopType.RECIPE) {
        //     // Vérifier si tous les ingrédients sont en stock
        //     boolean allIngredientsInStock = true;
        //     List<ItemStack> ingredients = priceRecipe.getIngredients(shopID, itemID, itemStack);
        //     ingredients = priceRecipe.consolidateIngredients(ingredients);
        //     for (ItemStack ingredient : ingredients) {
        //         // Vérifier si cet ingrédient est en mode STOCK
        //         DynaShopType ingredientType = priceRecipe.getIngredientType(ingredient);
        //         if (ingredientType == DynaShopType.STOCK && !DynaShopPlugin.getInstance().getPriceStock().canBuy(shopID, ingredient.getType().name(), ingredient.getAmount())) {
        //             allIngredientsInStock = false;
        //             break;
        //         }
        //     }
        //     if (!allIngredientsInStock) {
        //         event.setCancelled(true);
        //         if (event.getPlayer() != null) {
        //             event.getPlayer().sendMessage(ChatColor.translateAlternateColorCodes('&', this.mainPlugin.getLangConfig().getMsgOutOfStock()));
        //             // event.getPlayer().sendMessage(ChatColor.translateAlternateColorCodes('&', this.mainPlugin.getLangConfig().getMsgRecipeOutOfStock()));
        //         }
        //         return;
        //     }
        // }
        // Vérifier le mode RECIPE et s'il y a des ingrédients en mode STOCK
        if (shopConfigManager.getTypeDynaShop(shopID, itemID) == DynaShopType.RECIPE) {
            int stockAmount = priceRecipe.calculateStock(shopID, itemID, itemStack, new ArrayList<>());
            int maxStock = priceRecipe.calculateMaxStock(shopID, itemID, itemStack, new ArrayList<>());
            // mainPlugin.getLogger().info("Stock amount for " + itemID + " in shop " + shopID + ": " + stockAmount + ", Max stock: " + maxStock);
            // Vérifier si le stock est suffisant pour l'achat
            if (event.getShopAction() == ShopAction.BUY && stockAmount < amount) {
                event.setCancelled(true);
                if (event.getPlayer() != null) {
                    event.getPlayer().sendMessage(ChatColor.translateAlternateColorCodes('&', this.mainPlugin.getLangConfig().getMsgOutOfStock()));
                    // event.getPlayer().sendMessage(ChatColor.translateAlternateColorCodes('&', this.mainPlugin.getLangConfig().getMsgRecipeOutOfStock()));
                }
                return;
            }
            // Vérifier si le stock est suffisant pour la vente
            if ((event.getShopAction() == ShopAction.SELL || event.getShopAction() == ShopAction.SELL_ALL) && stockAmount >= maxStock) {
                event.setCancelled(true);
                if (event.getPlayer() != null) {
                    event.getPlayer().sendMessage(ChatColor.translateAlternateColorCodes('&', this.mainPlugin.getLangConfig().getMsgFullStock()));
                }
                return;
            }
        }

        if (event.getShopAction() == ShopAction.BUY) {
            event.setPrice(price.getBuyPriceForAmount(amount));
        } else if (event.getShopAction() == ShopAction.SELL || event.getShopAction() == ShopAction.SELL_ALL) {
            event.setPrice(price.getSellPriceForAmount(amount));
        }
    }

    // /**
    //  * Traite une transaction normale après vérification des limites ou si aucune limite n'est définie.
    //  */
    // private void processRegularTransaction(ShopPreTransactionEvent event, Player player, ShopItem item, int amount, String shopID, String itemID, ItemStack itemStack, boolean isBuy) {
    //     // Vérifier si l'item est configuré pour DynaShop
    //     if (!shopConfigManager.getItemValue(shopID, itemID, "typeDynaShop", String.class).isPresent()) {
    //         // mainPlugin.warning(itemID + " : Pas de section DynaShop dans le shop " + shopID);
    //         return; // Ignorer les items non configurés pour DynaShop
    //     }
        
    //     // Vérifier les sections requises
    //     if (!shopConfigManager.hasStockSection(shopID, itemID) && !shopConfigManager.hasDynamicSection(shopID, itemID) && !shopConfigManager.hasRecipeSection(shopID, itemID)) {
    //         // mainPlugin.warning(itemID + " : Pas de section dynamique, recette ou stock dans le shop " + shopID);
    //         return; // Ignorer les items sans les sections requises
    //     }
        
    //     // Le reste de votre logique de traitement des transactions dynamiques
    //     DynamicPrice price = getOrLoadPrice(shopID, itemID, itemStack);
    //     if (price == null) {
    //         // mainPlugin.warning(itemID + " : Pas de prix dynamique trouvé dans le shop " + shopID);
    //         return;
    //     }
        
    //     // Vérifier le mode STOCK et les limites de stock
    //     if (shopConfigManager.getTypeDynaShop(shopID, itemID) == DynaShopType.STOCK) {
    //         // Si c'est un achat et que le stock est vide
    //         // if (event.getShopAction() == ShopAction.BUY && price.getStock() <= 0) {
    //         if (event.getShopAction() == ShopAction.BUY && !DynaShopPlugin.getInstance().getPriceStock().canBuy(shopID, itemID, amount)) {
    //             event.setCancelled(true);
    //             if (player != null) {
    //                 // event.getPlayer().sendMessage("§c[DynaShop] Cet item est en rupture de stock !");
    //                 player.sendMessage(this.mainPlugin.getLangConfig().getMsgOutOfStock());
    //             }
    //             return;
    //         }
            
    //         // Si c'est une vente et que le stock est plein
    //         // if ((event.getShopAction() == ShopAction.SELL || event.getShopAction() == ShopAction.SELL_ALL) && price.getStock() >= price.getMaxStock()) {
    //         if ((event.getShopAction() == ShopAction.SELL || event.getShopAction() == ShopAction.SELL_ALL) && !DynaShopPlugin.getInstance().getPriceStock().canSell(shopID, itemID, amount)) {
    //             event.setCancelled(true);
    //             if (player != null) {
    //                 // event.getPlayer().sendMessage("§c[DynaShop] Le stock de cet item est complet, impossible de vendre plus !");
    //                 // event.getPlayer().sendMessage(this.mainPlugin.getConfigLang().getString("messages.stockFull")
    //                 //     .replace("%item%", itemID)
    //                 //     .replace("%shop%", shopID));
    //                 // event.getPlayer().sendMessage(this.mainPlugin.getConfigLang().getString("stock.full-stock"));
    //                 player.sendMessage(this.mainPlugin.getLangConfig().getMsgFullStock());
    //             }
    //             return;
    //         }
    //     }

    //     if (event.getShopAction() == ShopAction.BUY) {
    //         event.setPrice(price.getBuyPriceForAmount(amount));
    //     } else if (event.getShopAction() == ShopAction.SELL || event.getShopAction() == ShopAction.SELL_ALL) {
    //         event.setPrice(price.getSellPriceForAmount(amount));
    //     }
    //     // mainPlugin.getLogger().info("Transaction traitée pour " + player.getName() + " sur " + shopID + ":" + itemID);
    // }

    // // @EventHandler
    // @EventHandler(priority = EventPriority.HIGHEST)
    // public void onShopPostTransaction(ShopPostTransactionEvent event) {
    //     ShopItem item = event.getResult().getShopItem();
    //     int amount = event.getResult().getAmount();
    //     String shopID = item.getShop().getId();
    //     String itemID = item.getId();
    //     ItemStack itemStack = item.getItem();

    //     // Vérifier si l'item a un prix dynamique
    //     // if (!shopConfigManager.hasDynamicSection(shopID, itemID)) {
    //     if (!shopConfigManager.hasDynamicSection(shopID, itemID) && !shopConfigManager.getItemValue(shopID, itemID, "recipe.enabled", Boolean.class).orElse(false)) {
    //     // if (!shopConfigManager.hasDynamicSection(shopID, itemID) && !shopConfigManager.getItemBooleanValue(shopID, itemID, "useRecipe").orElse(false)) {
    //         return; // Ignorer les items sans prix dynamique
    //     }

    //     DynamicPrice price = getOrLoadPrice(shopID, itemID, itemStack);
    //     if (price == null) {
    //         return;
    //     }

    //     if (event.getResult().getResult() == ShopTransactionResultType.SUCCESS) {
    //         if (event.getResult().getShopAction() == ShopAction.BUY) {
    //             // price.incrementBuy();
    //             price.decreaseStock(amount);
    //             price.applyGrowth(amount);
    //             if (price.isFromRecipe()) { applyGrowthOrDecayToIngredients(shopID, itemID, itemStack, amount, true, new ArrayList<>()); }
    //         } else if (event.getResult().getShopAction() == ShopAction.SELL || event.getResult().getShopAction() == ShopAction.SELL_ALL) {
    //             // price.incrementSell();
    //             price.increaseStock(amount);
    //             price.applyDecay(amount);
    //             if (price.isFromRecipe()) { applyGrowthOrDecayToIngredients(shopID, itemID, itemStack, amount, false, new ArrayList<>()); }
    //         }

    //         mainPlugin.getLogger().info(event.getResult().getShopAction() + " - Prix mis à jour pour l'item " + itemID + " dans le shop " + shopID);
    //         mainPlugin.getLogger().info("Prix : " + event.getResult().getPrice() + ", amount : " + amount + ", growth : " + price.getGrowthBuy() + ", decay : " + price.getDecaySell());
    //         mainPlugin.getLogger().info("Next BUY : " + price.getBuyPrice() + ", Min : " + price.getMinBuyPrice() + ", Max : " + price.getMaxBuyPrice());
    //         mainPlugin.getLogger().info("Next SELL : " + price.getSellPrice() + ", Min : " + price.getMinSellPrice() + ", Max : " + price.getMaxSellPrice());
            
    //         // Sauvegarder les nouveaux prix dans la base de données
    //         if (!price.isFromRecipe()) {
    //             DynaShopPlugin.getInstance().getItemDataManager().savePrice(shopID, itemID, price.getBuyPrice(), price.getSellPrice());
    //         }
    //     }
    //     // Mettre à jour le lore de l'item dans le shop
    //     // DynaShopPlugin.getInstance().getGuiManager().updateShopItemLore(shopID, itemID, price);

    //     // Rafraîchir l'interface utilisateur des joueurs
    //     // DynaShopPlugin.getInstance().getGuiManager().refreshShopForPlayers(shopID);
    // }

    // @EventHandler(priority = EventPriority.HIGHEST)
    // public void onShopPostTransaction(ShopPostTransactionEvent event) {
    //     ShopItem item = event.getResult().getShopItem();
    //     int amount = event.getResult().getAmount();
    //     String shopID = item.getShop().getId();
    //     String itemID = item.getId();
    //     ItemStack itemStack = item.getItem();

    //     if (!shopConfigManager.hasDynaShopSection(shopID, itemID)) {
    //         return; // Ignorer les items non configurés pour DynaShop
    //     }
    //     if (!shopConfigManager.hasStockSection(shopID, itemID) && !shopConfigManager.hasDynamicSection(shopID, itemID) && !shopConfigManager.hasRecipeSection(shopID, itemID)) {
    //         return; // Ignorer les items sans les sections requises
    //     }

    //     // // Vérifier si l'item a un prix dynamique
    //     // // if (!shopConfigManager.hasDynamicSection(shopID, itemID)) {
    //     // if (!shopConfigManager.hasDynamicSection(shopID, itemID) && !shopConfigManager.getItemValue(shopID, itemID, "recipe.enabled", Boolean.class).orElse(false)) {
    //     // // if (!shopConfigManager.hasDynamicSection(shopID, itemID) && !shopConfigManager.getItemBooleanValue(shopID, itemID, "useRecipe").orElse(false)) {
    //     //     return; // Ignorer les items sans prix dynamique
    //     // }

    //     DynamicPrice price = getOrLoadPrice(shopID, itemID, itemStack);
    //     if (price == null) {
    //         return;
    //     }

    //     if (event.getResult().getResult() == ShopTransactionResultType.SUCCESS) {
    //         if (event.getResult().getShopAction() == ShopAction.BUY) {
    //             if (price.isFromStock()) { price.decreaseStock(amount); }
    //             price.applyGrowth(amount);
    //             if (price.isFromRecipe()) { applyGrowthOrDecayToIngredients(shopID, itemID, itemStack, amount, true, new ArrayList<>()); }

    //         } else if (event.getResult().getShopAction() == ShopAction.SELL || event.getResult().getShopAction() == ShopAction.SELL_ALL) {
    //             if (price.isFromStock()) { price.increaseStock(amount); }
    //             price.applyDecay(amount);
    //             if (price.isFromRecipe()) { applyGrowthOrDecayToIngredients(shopID, itemID, itemStack, amount, false, new ArrayList<>()); }
    //         }

    //         mainPlugin.getLogger().info(event.getResult().getShopAction() + " - Prix mis à jour pour l'item " + itemID + " dans le shop " + shopID);
    //         mainPlugin.getLogger().info("Prix : " + event.getResult().getPrice() + ", amount : " + amount + ", growth : " + price.getGrowthBuy() + ", decay : " + price.getDecaySell());
    //         mainPlugin.getLogger().info("Next BUY : " + price.getBuyPrice() + ", Min : " + price.getMinBuyPrice() + ", Max : " + price.getMaxBuyPrice());
    //         mainPlugin.getLogger().info("Next SELL : " + price.getSellPrice() + ", Min : " + price.getMinSellPrice() + ", Max : " + price.getMaxSellPrice());
            
    //         // Sauvegarder les nouveaux prix dans la base de données
    //         if (!price.isFromRecipe()) {
    //             DynaShopPlugin.getInstance().getItemDataManager().savePrice(shopID, itemID, price.getBuyPrice(), price.getSellPrice());
    //         }
    //     }
    // }
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

        Bukkit.getScheduler().runTaskAsynchronously(mainPlugin, () -> {
            processTransactionAsync(shopID, itemID, itemStack, amount, action, resultPrice);
        });
        
        // Enregistrer la transaction si l'item a des limites
        if (mainPlugin.getShopConfigManager().hasSection(shopID, itemID, "limit")) {
            mainPlugin.getTransactionLimiter().recordTransaction(player, shopID, itemID, isBuy, amount);
        }
    }
    
    private void processTransactionAsync(String shopID, String itemID, ItemStack itemStack, int amount, ShopAction action, double resultPrice) {
        // if (!shopConfigManager.hasDynaShopSection(shopID, itemID)) {
        if (!shopConfigManager.getItemValue(shopID, itemID, "typeDynaShop", String.class).isPresent()) {
            // mainPlugin.warning(itemID + " : Pas de section DynaShop dans le shop " + shopID);
            return; // Ignorer les items non configurés pour DynaShop
        }
        if (!shopConfigManager.hasStockSection(shopID, itemID) &&
            !shopConfigManager.hasDynamicSection(shopID, itemID) &&
            !shopConfigManager.hasRecipeSection(shopID, itemID)) {
            // mainPlugin.warning(itemID + " : Pas de section dynamique, recette ou stock dans le shop " + shopID);
            return; // Ignorer les items sans les sections requises
        }

        DynamicPrice price = getOrLoadPrice(shopID, itemID, itemStack);
        if (price == null) {
            // mainPlugin.warning(itemID + " : Pas de prix dynamique trouvé dans le shop " + shopID);
            return;
        }

        DynaShopType type = shopConfigManager.getTypeDynaShop(shopID, itemID);
        // switch (type) {
        //     case DYNAMIC -> {
        //         handleDynamicPrice(price, action, amount); // Gérer les prix dynamiques
        //     }
        //     case RECIPE -> {
        //         handleRecipePrice(price, shopID, itemID, itemStack, amount, action); // Gérer les prix basés sur les recettes
        //     }
        //     case STOCK -> {
        //         handleStockPrice(price, shopID, itemID, action, amount); // Gérer les prix basés sur le stock
        //     }
        //     default -> {
        //         mainPlugin.getLogger().warning("Type de gestion inconnu pour l'item " + itemID + " dans le shop " + shopID);
        //     }
        // }
        if (type == DynaShopType.DYNAMIC) {
            handleDynamicPrice(price, action, amount); // Gérer les prix dynamiques
        } else if (type == DynaShopType.RECIPE || price.isFromRecipe()) {
            handleRecipePrice(price, shopID, itemID, itemStack, amount, action); // Gérer les prix basés sur les recettes
        } else if (type == DynaShopType.STOCK || price.isFromStock()) {
            handleStockPrice(price, shopID, itemID, action, amount); // Gérer les prix basés sur le stock
        // } else {
        //     mainPlugin.getLogger().warning("Type de gestion inconnu pour l'item " + itemID + " dans le shop " + shopID);
        }

        // mainPlugin.info(action + " - Prix mis à jour pour l'item " + itemID + " dans le shop " + shopID);
        // mainPlugin.info("Prix : " + resultPrice + ", amount : " + amount + ", growth : " + price.getGrowthBuy() + ", decay : " + price.getDecaySell());
        // mainPlugin.info("Next BUY : " + price.getBuyPrice() + ", Min : " + price.getMinBuyPrice() + ", Max : " + price.getMaxBuyPrice());
        // mainPlugin.info("Next SELL : " + price.getSellPrice() + ", Min : " + price.getMinSellPrice() + ", Max : " + price.getMaxSellPrice());

        // Sauvegarder les nouveaux prix dans la base de données
        // savePriceIfNeeded(price, shopID, itemID);
        if (!price.isFromRecipe()) {
            DynaShopPlugin.getInstance().getBatchDatabaseUpdater().queueUpdate(shopID, itemID, price);
        }
        // DynaShopPlugin.getInstance().getBatchDatabaseUpdater().queueUpdate(shopID, itemID, price);
    }

    private void handleDynamicPrice(DynamicPrice price, ShopAction action, int amount) {
        if (action == ShopAction.BUY) {
            price.applyGrowth(amount);
        } else if (action == ShopAction.SELL || action == ShopAction.SELL_ALL) {
            price.applyDecay(amount);
        }
    }

    private void handleRecipePrice(DynamicPrice price, String shopID, String itemID, ItemStack itemStack, int amount, ShopAction action) {
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
        Bukkit.getScheduler().runTaskAsynchronously(mainPlugin, () -> {
            applyGrowthOrDecayToIngredients(shopID, itemID, itemStack, amount, isGrowth, new ArrayList<>(), 0);
        });
    }

    // private void handleStockPrice(DynamicPrice price, ShopAction action, int amount) {
    private void handleStockPrice(DynamicPrice price, String shopID, String itemID, ShopAction action, int amount) {
        // mainPlugin.getLogger().info("AVANT - Stock: " + price.getStock() + ", Buy: " + price.getBuyPrice() + ", Sell: " + price.getSellPrice());
        
        if (action == ShopAction.BUY) {
            // mainPlugin.getLogger().info("Diminution du stock de " + amount + " unités");
            // price.decreaseStock(amount);
            mainPlugin.getPriceStock().processBuyTransaction(shopID, itemID, amount);
            // price.adjustPricesBasedOnStock();
            // price.adjustPricesBasedOnPrices();
        } else if (action == ShopAction.SELL || action == ShopAction.SELL_ALL) {
            // mainPlugin.getLogger().info("Augmentation du stock de " + amount + " unités");
            // price.increaseStock(amount);
            mainPlugin.getPriceStock().processSellTransaction(shopID, itemID, amount);
            // price.adjustPricesBasedOnStock();
            // price.adjustPricesBasedOnPrices();
        }

        // Mettre à jour l'objet price
        double newBuyPrice = DynaShopPlugin.getInstance().getPriceStock().calculatePrice(shopID, itemID, "buyPrice");
        double newSellPrice = DynaShopPlugin.getInstance().getPriceStock().calculatePrice(shopID, itemID, "sellPrice");
        
        price.setBuyPrice(newBuyPrice);
        price.setSellPrice(newSellPrice);
        price.setStock(DynaShopPlugin.getInstance().getItemDataManager().getStock(shopID, itemID).orElse(0));
        
        // mainPlugin.getLogger().info("APRÈS - Stock: " + price.getStock() + ", Buy: " + price.getBuyPrice() + ", Sell: " + price.getSellPrice());
    }

    // private void savePriceIfNeeded(DynamicPrice price, String shopID, String itemID) {
    //     if (!price.isFromRecipe()) {
    //         // DynaShopPlugin.getInstance().getItemDataManager().savePrice(shopID, itemID, price.getBuyPrice(), price.getSellPrice());
    //         DynaShopPlugin.getInstance().getBatchDatabaseUpdater().queueUpdate(shopID, itemID, price);
    //     }
    // }


    public DynamicPrice getOrLoadPrice(String shopID, String itemID, ItemStack itemStack) {
        // // Vérifier d'abord dans le cache pour les prix de recette
        // String cacheKeyBuy = shopID + ":" + itemID + ":buyPrice";
        // String cacheKeySell = shopID + ":" + itemID + ":sellPrice";
        
        // Charger les prix depuis la base de données
        Optional<DynamicPrice> priceFromDatabase = DynaShopPlugin.getInstance().getItemDataManager().getItemValues(shopID, itemID);
    
        // Charger les données supplémentaires depuis les fichiers de configuration
        ItemPriceData priceData = shopConfigManager.getItemAllValues(shopID, itemID);
        
        // Déterminer le type de l'item
        DynaShopType type = shopConfigManager.getTypeDynaShop(shopID, itemID);
        
        // // Traiter les prix basés sur les recettes
        // if (type == DynaShopType.RECIPE) {
        //     // Vérifier si les prix sont en cache
        //     double buyPrice = DynaShopPlugin.getInstance().getCachedRecipePrice(shopID, itemID, "buyPrice");
        //     double sellPrice = DynaShopPlugin.getInstance().getCachedRecipePrice(shopID, itemID, "sellPrice");
            
        //     // Si les prix ne sont pas en cache, les calculer
        //     if (buyPrice < 0 || sellPrice < 0) {
        //         // Utiliser les calculs synchrones pour l'initialisation
        //         buyPrice = priceRecipe.calculatePrice(shopID, itemID, itemStack, "buyPrice", new ArrayList<>());
        //         sellPrice = priceRecipe.calculatePrice(shopID, itemID, itemStack, "sellPrice", new ArrayList<>());
                
        //         // Mettre en cache les résultats calculés
        //         DynaShopPlugin.getInstance().cacheRecipePrice(shopID, itemID, "buyPrice", buyPrice);
        //         DynaShopPlugin.getInstance().cacheRecipePrice(shopID, itemID, "sellPrice", sellPrice);
                
        //         // Planifier un calcul asynchrone pour mettre à jour le cache
        //         priceRecipe.calculatePriceAsync(shopID, itemID, itemStack, "buyPrice", newPrice -> {
        //             DynaShopPlugin.getInstance().cacheRecipePrice(shopID, itemID, "buyPrice", newPrice);
        //         });
                
        //         priceRecipe.calculatePriceAsync(shopID, itemID, itemStack, "sellPrice", newPrice -> {
        //             DynaShopPlugin.getInstance().cacheRecipePrice(shopID, itemID, "sellPrice", newPrice);
        //         });
        //     }
            
        //     // Calculer ou récupérer les valeurs min/max
        //     double minBuy, maxBuy, minSell, maxSell;
            
        //     // Vérifier si les bornes sont en cache
        //     double cachedMinBuy = DynaShopPlugin.getInstance().getCachedRecipePrice(shopID, itemID, "buyDynamic.min");
        //     double cachedMaxBuy = DynaShopPlugin.getInstance().getCachedRecipePrice(shopID, itemID, "buyDynamic.max");
        //     double cachedMinSell = DynaShopPlugin.getInstance().getCachedRecipePrice(shopID, itemID, "sellDynamic.min");
        //     double cachedMaxSell = DynaShopPlugin.getInstance().getCachedRecipePrice(shopID, itemID, "sellDynamic.max");
            
        //     if (cachedMinBuy >= 0) {
        //         minBuy = cachedMinBuy;
        //     } else {
        //         minBuy = priceData.minBuy.orElse(buyPrice * 0.5); // Valeur par défaut
        //         // Calculer la valeur en arrière-plan et mettre à jour le cache
        //         priceRecipe.calculatePriceAsync(shopID, itemID, itemStack, "buyDynamic.min", newPrice -> {
        //             DynaShopPlugin.getInstance().cacheRecipePrice(shopID, itemID, "buyDynamic.min", newPrice);
        //         });
        //     }
            
        //     if (cachedMaxBuy >= 0) {
        //         maxBuy = cachedMaxBuy;
        //     } else {
        //         maxBuy = priceData.maxBuy.orElse(buyPrice * 2.0); // Valeur par défaut
        //         // Calculer la valeur en arrière-plan et mettre à jour le cache
        //         priceRecipe.calculatePriceAsync(shopID, itemID, itemStack, "buyDynamic.max", newPrice -> {
        //             DynaShopPlugin.getInstance().cacheRecipePrice(shopID, itemID, "buyDynamic.max", newPrice);
        //         });
        //     }
            
        //     if (cachedMinSell >= 0) {
        //         minSell = cachedMinSell;
        //     } else {
        //         minSell = priceData.minSell.orElse(sellPrice * 0.5); // Valeur par défaut
        //         // Calculer la valeur en arrière-plan et mettre à jour le cache
        //         priceRecipe.calculatePriceAsync(shopID, itemID, itemStack, "sellDynamic.min", newPrice -> {
        //             DynaShopPlugin.getInstance().cacheRecipePrice(shopID, itemID, "sellDynamic.min", newPrice);
        //         });
        //     }
            
        //     if (cachedMaxSell >= 0) {
        //         maxSell = cachedMaxSell;
        //     } else {
        //         maxSell = priceData.maxSell.orElse(sellPrice * 2.0); // Valeur par défaut
        //         // Calculer la valeur en arrière-plan et mettre à jour le cache
        //         priceRecipe.calculatePriceAsync(shopID, itemID, itemStack, "sellDynamic.max", newPrice -> {
        //             DynaShopPlugin.getInstance().cacheRecipePrice(shopID, itemID, "sellDynamic.max", newPrice);
        //         });
        //     }
            
        //     DynamicPrice recipePrice = new DynamicPrice(buyPrice, sellPrice, minBuy, maxBuy, minSell, maxSell, 1.0, 1.0, 1.0, 1.0, 0, 0, 0, 1.0, 1.0);
        //     recipePrice.setFromRecipe(true); // Marquer comme provenant d'une recette
        //     return recipePrice;
        // }
        // Traiter les prix basés sur les recettes
        if (type == DynaShopType.RECIPE) {
            double buyPrice = priceRecipe.calculatePrice(shopID, itemID, itemStack, "buyPrice", new ArrayList<>());
            double sellPrice = priceRecipe.calculatePrice(shopID, itemID, itemStack, "sellPrice", new ArrayList<>());
            
            double minBuy = priceData.minBuy.orElse(buyPrice * 0.5);
            double maxBuy = priceData.maxBuy.orElse(buyPrice * 2.0);
            double minSell = priceData.minSell.orElse(sellPrice * 0.5);
            double maxSell = priceData.maxSell.orElse(sellPrice * 2.0);

            DynamicPrice recipePrice = new DynamicPrice(buyPrice, sellPrice, minBuy, maxBuy, minSell, maxSell, 1.0, 1.0, 1.0, 1.0, 0, 0, 0, 1.0, 1.0);
            recipePrice.setFromRecipe(true); // Marquer comme provenant d'une recette
            return recipePrice;
        }
    
        
        if (type == DynaShopType.STOCK) {
            return DynaShopPlugin.getInstance().getPriceStock().createStockPrice(shopID, itemID);
            // price.setFromStock(true);
        }
        
        // Si aucune donnée n'est trouvée dans la base de données ou les fichiers de configuration, retourner null
        if (priceFromDatabase.isEmpty() && (priceData.buyPrice.isEmpty() || priceData.sellPrice.isEmpty())) {
            return null;
        }
        
        // Fusionner les données
        double buyPrice = priceFromDatabase.map(DynamicPrice::getBuyPrice).orElse(priceData.buyPrice.orElse(-1.0));
        double sellPrice = priceFromDatabase.map(DynamicPrice::getSellPrice).orElse(priceData.sellPrice.orElse(-1.0));
    
        double minBuy = priceData.minBuy.orElse(buyPrice);
        double maxBuy = priceData.maxBuy.orElse(buyPrice);
        double minSell = priceData.minSell.orElse(sellPrice);
        double maxSell = priceData.maxSell.orElse(sellPrice);
    
        double growthBuy = priceData.growthBuy.orElseGet(() -> {
            boolean hasBuyDynamic = shopConfigManager.hasSection(shopID, itemID, "buyDynamic");
            return hasBuyDynamic ? dataConfig.getBuyGrowthRate() : 1.0; // Valeur par défaut pour growthBuy
        });
    
        double decayBuy = priceData.decayBuy.orElseGet(() -> {
            boolean hasBuyDynamic = shopConfigManager.hasSection(shopID, itemID, "buyDynamic");
            return hasBuyDynamic ? dataConfig.getBuyDecayRate() : 1.0; // Valeur par défaut pour decayBuy
        });
    
        double growthSell = priceData.growthSell.orElseGet(() -> {
            boolean hasSellDynamic = shopConfigManager.hasSection(shopID, itemID, "sellDynamic");
            return hasSellDynamic ? dataConfig.getSellGrowthRate() : 1.0; // Valeur par défaut pour growthSell
        });
    
        double decaySell = priceData.decaySell.orElseGet(() -> {
            boolean hasSellDynamic = shopConfigManager.hasSection(shopID, itemID, "sellDynamic");
            return hasSellDynamic ? dataConfig.getSellDecayRate() : 1.0; // Valeur par défaut pour decaySell
        });
    
        int stock = priceFromDatabase.map(DynamicPrice::getStock).orElse(priceData.stock.orElse(0));

        int minStock = priceData.minStock.orElseGet(() -> {
            boolean hasStock = shopConfigManager.hasSection(shopID, itemID, "stock");
            return hasStock ? dataConfig.getStockMin() : 0; // Valeur par défaut pour minStock
        });
    
        int maxStock = priceData.maxStock.orElseGet(() -> {
            boolean hasStock = shopConfigManager.hasSection(shopID, itemID, "stock");
            return hasStock ? dataConfig.getStockMax() : Integer.MAX_VALUE; // Valeur par défaut pour maxStock
        });
    
        double stockBuyModifier = priceData.stockBuyModifier.orElseGet(() -> {
            boolean hasStock = shopConfigManager.hasSection(shopID, itemID, "stock");
            return hasStock ? dataConfig.getStockBuyModifier() : 1.0; // Valeur par défaut pour stockBuyModifier
        });
    
        double stockSellModifier = priceData.stockSellModifier.orElseGet(() -> {
            boolean hasStock = shopConfigManager.hasSection(shopID, itemID, "stock");
            return hasStock ? dataConfig.getStockSellModifier() : 1.0; // Valeur par défaut pour stockSellModifier
        });
    
        return new DynamicPrice(buyPrice, sellPrice, minBuy, maxBuy, minSell, maxSell, growthBuy, decayBuy, growthSell, decaySell, stock, minStock, maxStock, stockBuyModifier, stockSellModifier);
        // DynamicPrice price = new DynamicPrice(buyPrice, sellPrice, minBuy, maxBuy, minSell, maxSell, growthBuy, decayBuy, growthSell, decaySell, stock, minStock, maxStock, stockBuyModifier, stockSellModifier);
        
        // return price;
    }

    /**
     * Applique la croissance ou la décroissance aux ingrédients d'une recette.
     * Cette méthode est appelée de manière récursive pour chaque ingrédient trouvé dans la recette.
     * @param shopID
     * @param itemID
     * @param itemStack
     * @param amount
     * @param isGrowth
     * @param visitedItems
     */
    private void applyGrowthOrDecayToIngredients(String shopID, String itemID, ItemStack itemStack, int amount, boolean isGrowth, List<String> visitedItems, int depth) {

        // Limiter la profondeur de récursion
        if (depth > 5) {
            // mainPlugin.getLogger().warning("Profondeur de récursion maximale atteinte pour " + itemID);
            return;
        }

        // Vérifier si l'item a déjà été visité pour éviter les boucles infinies
        if (visitedItems.contains(itemID)) {
            return;
        }
        visitedItems.add(itemID); // Ajouter l'item à la liste des items visités

        // Récupérer la liste des ingrédients de la recette
        List<ItemStack> ingredients = priceRecipe.getIngredients(shopID, itemID, itemStack);
        ingredients = priceRecipe.consolidateIngredients(ingredients); // Consolider les ingrédients

        for (ItemStack ingredient : ingredients) {
            if (ingredient == null || ingredient.getType() == Material.AIR) {
                continue; // Ignorer les ingrédients invalides
            // } else {
            //     mainPlugin.getLogger().info("Ingrédient trouvé : " + ingredient.getType() + " x " + ingredient.getAmount());
            }

            String ingredientID = ShopGuiPlusApi.getItemStackShopItem(ingredient).getId(); // Utiliser l'ID de l'item dans le shop
            String shopIngredientID = ShopGuiPlusApi.getItemStackShopItem(ingredient).getShop().getId(); // Utiliser l'ID du shop de l'item

            // Récupérer le type de l'ingrédient
            DynaShopType ingredientType = DynaShopPlugin.getInstance().getShopConfigManager().getTypeDynaShop(shopIngredientID, ingredientID);

            // Récupérer le prix dynamique de l'ingrédient
            // Optional<DynamicPrice> ingredientPriceOpt = DynaShopPlugin.getInstance().getItemDataManager().getPrice(shopID, ingredientID);
            DynamicPrice ingredientPrice = getOrLoadPrice(shopIngredientID, ingredientID, itemStack);

            // if (ingredientPriceOpt.isPresent()) {
            if (ingredientPrice != null) {
                // DynamicPrice ingredientPrice = ingredientPriceOpt.get();
                int ingredientAmount = amount * ingredient.getAmount();


                // Selon le type d'ingrédient, traiter différemment
                if (ingredientType == DynaShopType.STOCK) {
                    // mainPlugin.getLogger().info("Ingrédient " + ingredientID + " est en mode STOCK");
                    
                    if (isGrowth) {
                        // Quand on achète un item, on réduit le stock des ingrédients
                        // mainPlugin.getLogger().info("Diminution du stock de l'ingrédient de " + ingredientAmount + " unités");
                        DynaShopPlugin.getInstance().getPriceStock().processBuyTransaction(shopIngredientID, ingredientID, ingredientAmount);
                    } else {
                        // Quand on vend un item, on augmente le stock des ingrédients
                        // mainPlugin.getLogger().info("Augmentation du stock de l'ingrédient de " + ingredientAmount + " unités");
                        DynaShopPlugin.getInstance().getPriceStock().processSellTransaction(shopIngredientID, ingredientID, ingredientAmount);
                    }
                    
                    // Mettre à jour l'objet ingredientPrice avec les nouvelles valeurs de prix
                    double newBuyPrice = DynaShopPlugin.getInstance().getPriceStock().calculatePrice(shopIngredientID, ingredientID, "buyPrice");
                    double newSellPrice = DynaShopPlugin.getInstance().getPriceStock().calculatePrice(shopIngredientID, ingredientID, "sellPrice");
                    
                    ingredientPrice.setBuyPrice(newBuyPrice);
                    ingredientPrice.setSellPrice(newSellPrice);
                    ingredientPrice.setStock(DynaShopPlugin.getInstance().getItemDataManager().getStock(shopIngredientID, ingredientID).orElse(0));
                } else {
                    // Pour les items non-STOCK, appliquer la croissance/décroissance
                    if (isGrowth) {
                        ingredientPrice.applyGrowth(ingredientAmount);
                    } else {
                        ingredientPrice.applyDecay(ingredientAmount);
                    }
                }

                // // Appliquer growth ou decay
                // if (isGrowth) {
                //     ingredientPrice.applyGrowth(amount * ingredient.getAmount());
                // } else {
                //     ingredientPrice.applyDecay(amount * ingredient.getAmount());
                // }

                // Log pour vérifier les changements
                // mainPlugin.getLogger().info("Prix mis à jour pour l'ingrédient " + ingredientID + " x " + amount * ingredient.getAmount() + ": " + "Buy = " + ingredientPrice.getBuyPrice() + ", Sell = " + ingredientPrice.getSellPrice());

                // Sauvegarder les nouveaux prix dans la base de données
                // Si l'ingrédient est lui-même basé sur une recette, appliquer récursivement
                // if (!ingredientPrice.isFromRecipe() && !ingredientPrice.isFromStock()) {
                if (!ingredientPrice.isFromRecipe()) {
                    // DynaShopPlugin.getInstance().getItemDataManager().savePrice(shopingredientID, ingredientID, ingredientPrice.getBuyPrice(), ingredientPrice.getSellPrice());
                    DynaShopPlugin.getInstance().getBatchDatabaseUpdater().queueUpdate(shopIngredientID, ingredientID, ingredientPrice);
                // } else if (ingredientPrice.isFromRecipe()) {
                } else {
                    // Appliquer la croissance ou la décroissance aux ingrédients de la recette de l'ingrédient
                    applyGrowthOrDecayToIngredients(shopIngredientID, ingredientID, ingredient, ingredient.getAmount(), isGrowth, visitedItems, depth + 1);
                }
            } else {
                // mainPlugin.getLogger().warning("Prix dynamique introuvable pour l'ingrédient " + ingredientID + " dans le shop " + shopIngredientID);
            }
        }
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
        mainPlugin.getServer().getScheduler().runTaskAsynchronously(mainPlugin, () -> {
            try {
                int remaining = mainPlugin.getTransactionLimiter()
                        .getRemainingAmount(player, shopID, itemID, isBuy)
                        .get();
                
                final String message;
                if (remaining > 0) {
                    message = isBuy 
                        ? ChatColor.translateAlternateColorCodes('&', mainPlugin.getLangConfig().getMsgLimitCannotBuy().replace("%limit%", String.valueOf(remaining)))
                        : ChatColor.translateAlternateColorCodes('&', mainPlugin.getLangConfig().getMsgLimitCannotSell().replace("%limit%", String.valueOf(remaining)));
                } else {
                    long nextAvailable = mainPlugin.getTransactionLimiter()
                            .getNextAvailableTime(player, shopID, itemID, isBuy)
                            .get();
                    
                    if (nextAvailable > 0) {
                        long seconds = nextAvailable / 1000;
                        message = ChatColor.translateAlternateColorCodes('&', mainPlugin.getLangConfig().getMsgLimitReached().replace("%time%", formatTime(seconds)));
                    } else {
                        message = ChatColor.translateAlternateColorCodes('&', mainPlugin.getLangConfig().getMsgLimit());
                    }
                }
                
                // Envoyer le message de manière synchrone
                Bukkit.getScheduler().runTask(mainPlugin, () -> {
                    player.sendMessage(message);
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                });
                
            } catch (Exception e) {
                mainPlugin.getLogger().severe("Erreur lors de la récupération des limites: " + e.getMessage());
            }
        });
    }

    /**
    //  * Exécute manuellement une transaction en utilisant l'API de ShopGUI+
    //  */
    // private void executeTransactionManually(Player player, ShopItem item, ShopAction action, int amount) {
    //     try {
    //         // Utiliser l'API de ShopGUI+ pour exécuter la transaction
    //         ShopGuiPlusApi.processTransaction(player, item, action, amount);
    //     } catch (Exception e) {
    //         mainPlugin.getLogger().severe("Erreur lors de l'exécution manuelle de la transaction: " + e.getMessage());
    //         player.sendMessage("§cUne erreur est survenue lors de la transaction.");
    //     }
    // }

    // Ajouter cette classe interne pour le cache
    private static class LimitCacheEntry {
        final boolean canPerform;
        final long timestamp;
        
        LimitCacheEntry(boolean canPerform, long timestamp) {
            this.canPerform = canPerform;
            this.timestamp = timestamp;
        }
    }


    // @EventHandler
    // public void onInventoryOpen(InventoryOpenEvent event) {
    //     if (!(event.getPlayer() instanceof Player)) return;
    //     Player player = (Player) event.getPlayer();
        
    //     // Vérifier si c'est une interface de ShopGUI+
    //     if (event.getView().getTitle().contains("Shop")) {
    //         // Essayer de déterminer le shop et l'item
    //         try {
    //             // Logique pour déterminer le shopID et itemID basée sur l'inventaire
    //             // Ceci est une approximation et dépend de comment ShopGUI+ implémente ses interfaces
    //             String shopId = determineShopId(event.getView());
    //             String itemId = determineSelectedItem(player, event.getView());
                
    //             if (shopId != null) {
    //                 openShopMap.put(player.getUniqueId(), new SimpleEntry<>(shopId, itemId));
    //             }
    //         } catch (Exception e) {
    //             mainPlugin.getLogger().warning("Erreur lors de la détection du shop: " + e.getMessage());
    //         }
    //     }
    // }
    // @EventHandler
    // public void onInventoryOpen(InventoryOpenEvent event) {
    //     if (!(event.getPlayer() instanceof Player)) return;
    //     Player player = (Player) event.getPlayer();
    //             // OpenGui gui = ShopGuiPlusApi.getPlugin().getPlayerManager().getPlayerData(player).getOpenGui();
        
    //     // Vérifier si c'est une interface de ShopGUI+
    //     if (event.getView().getTitle().contains("Shop") || (event.getView().getTitle().contains("Magasin") && event.getInventory().getHolder() != player)) {
            
    //         // Essayer de déterminer le shop et l'item
    //         try {
    //             String shopId = determineShopId(event.getView());
                
    //             if (shopId != null) {
    //                 // Pour l'item, on peut soit le détecter immédiatement, soit mettre une valeur nulle
    //                 // et le mettre à jour au fur et à mesure que le joueur navigue dans le shop
    //                 String itemId = determineSelectedItem(player, event.getView());
                    
    //                 openShopMap.put(player.getUniqueId(), new SimpleEntry<>(shopId, itemId));
                    
    //                 // Log pour le débogage
    //                 // if (mainPlugin.isDebug()) {
    //                     mainPlugin.getLogger().info("Shop détecté pour " + player.getName() + ": " + shopId + (itemId != null ? ", item: " + itemId : ""));
    //                 // }
    //             }
    //         } catch (Exception e) {
    //             // if (mainPlugin.isDebug()) {
    //                 mainPlugin.getLogger().warning("Erreur lors de la détection du shop: " + e.getMessage());
    //             // }
    //         }
    //     }
    // }

    // @EventHandler
    // public void onInventoryClose(InventoryCloseEvent event) {
    //     if (!(event.getPlayer() instanceof Player)) return;
    //     Player player = (Player) event.getPlayer();
        
    //     // Supprimer l'entrée lorsque le joueur ferme l'inventaire
    //     openShopMap.remove(player.getUniqueId());
    // }

    // // Méthodes pour exposer ces informations
    // public String getCurrentShopId(Player player) {
    //     SimpleEntry<String, String> shopData = openShopMap.get(player.getUniqueId());
    //     return shopData == null ? null : shopData.getKey();
    // }

    // public String getCurrentItemId(Player player) {
    //     SimpleEntry<String, String> shopData = openShopMap.get(player.getUniqueId());
    //     return shopData == null ? null : shopData.getValue();
    // }

    // /**
    //  * Détermine l'ID du shop à partir du titre de l'inventaire.
    //  * Cette méthode suppose que le titre contient l'ID du shop dans un format particulier.
    //  * 
    //  * @param view L'InventoryView à analyser
    //  * @return L'ID du shop ou null si non trouvé
    //  */
    // private String determineShopId(InventoryView view) {
    //     String title = view.getTitle();
        
    //     // Méthode 1: Extraire du titre (en supposant un format comme "Shop - {shopId}")
    //     if (title.contains("»")) {
    //         String[] parts = title.split("»");
    //         if (parts.length > 0) {
    //             // Nettoyer le texte pour obtenir l'ID du shop
    //             String shopName = ChatColor.stripColor(parts[0].trim());
                
    //             // Convertir le nom affiché en ID de shop (en supposant que l'ID est en minuscules sans espaces)
    //             return shopName.toLowerCase().replace(" ", "_").replace("-", "_");
    //         }
    //     }
        
    //     // Méthode 2: Essayer d'utiliser l'API de ShopGUI+ si disponible
    //     try {
    //         // Tenter de récupérer le shop actif du joueur via l'API ShopGUI+
    //         // for (String shopId : ShopGuiPlusApi.getShops().keySet()) {
    //         for (String shopId : ShopGuiPlusApi.getPlugin().getShopManager().getShops().stream().map(Shop::getId).toList()) {
    //             if (title.contains(ShopGuiPlusApi.getShop(shopId).getName())) {
    //                 return shopId;
    //             }
    //         }
    //     } catch (Exception e) {
    //         mainPlugin.getLogger().warning("Erreur lors de la récupération du shop via l'API: " + e.getMessage());
    //     }
        
    //     return null;
    // }

    // // /**
    // //  * Détermine l'ID de l'item sélectionné dans l'inventaire du shop.
    // //  * 
    // //  * @param player Le joueur qui a ouvert l'inventaire
    // //  * @param view L'InventoryView à analyser
    // //  * @return L'ID de l'item ou null si non trouvé
    // //  */
    // // private String determineSelectedItem(Player player, InventoryView view) {
    // //     // Méthode 1: Essayer de déterminer l'item actuellement visé par le curseur
    // //     try {
    // //         // int slot = view.getCursor().getType() != Material.AIR ? view.getConverter().convertSlot(view.getCursorSlot()) : -1;
    // //         int slot = view.getCursor().getType() != Material.AIR ? view.getConverter().convertSlot(view.getCursorSlot()) : -1;
            
    // //         if (slot >= 0 && slot < view.getTopInventory().getSize()) {
    // //             ItemStack item = view.getItem(slot);
    // //             if (item != null && item.getType() != Material.AIR) {
    // //                 // Essayer d'obtenir l'ID de l'item via l'API ShopGUI+
    // //                 ShopItem shopItem = ShopGuiPlusApi.getItemStackShopItem(item);
    // //                 if (shopItem != null) {
    // //                     return shopItem.getId();
    // //                 }
    // //             }
    // //         }
    // //     } catch (Exception e) {
    // //         // Ignorer les erreurs, essayer d'autres méthodes
    // //     }
        
    // //     // Méthode 2: Si le joueur a un item dans son viseur, essayer cet item
    // //     try {
    // //         ItemStack targetItem = player.getTargetBlock(null, 5).getType().isBlock() ? 
    // //                                 new ItemStack(player.getTargetBlock(null, 5).getType()) : 
    // //                                 player.getInventory().getItemInMainHand();
                                    
    // //         ShopItem shopItem = ShopGuiPlusApi.getItemStackShopItem(targetItem);
    // //         if (shopItem != null) {
    // //             return shopItem.getId();
    // //         }
    // //     } catch (Exception e) {
    // //         // Ignorer les erreurs
    // //     }
        
    // //     // Méthode 3: En dernier recours, essayer d'utiliser les données de transaction récentes
    // //     // Cette logique pourrait être ajoutée si vous conservez un historique de transactions
        
    // //     return null;
    // // }
    // /**
    //  * Détermine l'ID de l'item sélectionné dans l'inventaire du shop.
    //  * 
    //  * @param player Le joueur qui a ouvert l'inventaire
    //  * @param view L'InventoryView à analyser
    //  * @return L'ID de l'item ou null si non trouvé
    //  */
    // private String determineSelectedItem(Player player, InventoryView view) {
    //     // Méthode 1: Vérifier l'item sur le curseur du joueur
    //     try {
    //         ItemStack cursorItem = view.getCursor();
    //         if (cursorItem != null && cursorItem.getType() != Material.AIR) {
    //             ShopItem shopItem = ShopGuiPlusApi.getItemStackShopItem(cursorItem);
    //             if (shopItem != null) {
    //                 return shopItem.getId();
    //             }
    //         }
    //     } catch (Exception e) {
    //         // Ignorer les erreurs, essayer d'autres méthodes
    //     }
        
    //     // Méthode 2: Parcourir les items de l'inventaire supérieur
    //     try {
    //         Inventory topInventory = view.getTopInventory();
            
    //         // Vérifier d'abord le slot central (souvent utilisé pour l'item principal)
    //         int centerSlot = topInventory.getSize() / 2;
    //         ItemStack centerItem = topInventory.getItem(centerSlot);
    //         if (centerItem != null && centerItem.getType() != Material.AIR) {
    //             ShopItem shopItem = ShopGuiPlusApi.getItemStackShopItem(centerItem);
    //             if (shopItem != null) {
    //                 return shopItem.getId();
    //             }
    //         }
            
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
        
    //     // Méthode 3: Utiliser l'item dans la main du joueur
    //     try {
    //         ItemStack handItem = player.getInventory().getItemInMainHand();
    //         if (handItem != null && handItem.getType() != Material.AIR) {
    //             ShopItem shopItem = ShopGuiPlusApi.getItemStackShopItem(handItem);
    //             if (shopItem != null) {
    //                 return shopItem.getId();
    //             }
    //         }
    //     } catch (Exception e) {
    //         // Ignorer les erreurs
    //     }
        
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