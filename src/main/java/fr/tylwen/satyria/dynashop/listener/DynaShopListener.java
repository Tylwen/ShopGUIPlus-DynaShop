package fr.tylwen.satyria.dynashop.listener;

import fr.tylwen.satyria.dynashop.DynaShopPlugin;
import fr.tylwen.satyria.dynashop.config.DataConfig;
import fr.tylwen.satyria.dynashop.data.ItemPriceData;
import fr.tylwen.satyria.dynashop.data.ShopConfigManager;
import fr.tylwen.satyria.dynashop.data.cache.LimitCacheEntry;
import fr.tylwen.satyria.dynashop.data.param.DynaShopType;
import fr.tylwen.satyria.dynashop.price.DynamicPrice;
import fr.tylwen.satyria.dynashop.price.PriceRecipe;
import fr.tylwen.satyria.dynashop.price.PriceRecipe.FoundItem;
import fr.tylwen.satyria.dynashop.system.chart.PriceHistory;
import fr.tylwen.satyria.dynashop.system.chart.PriceHistory.PriceDataPoint;
import net.brcdev.shopgui.ShopGuiPlusApi;
import net.brcdev.shopgui.event.ShopPostTransactionEvent;
import net.brcdev.shopgui.event.ShopPreTransactionEvent;
import net.brcdev.shopgui.exception.player.PlayerDataNotLoadedException;
import net.brcdev.shopgui.modifier.PriceModifierActionType;
import net.brcdev.shopgui.shop.ShopManager.ShopAction;
import net.brcdev.shopgui.shop.ShopTransactionResult.ShopTransactionResultType;
import net.brcdev.shopgui.shop.item.ShopItem;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Gère tous les événements liés aux boutiques dynamiques
 */
public class DynaShopListener implements Listener {
    
    // ============= CHAMPS PRIVÉS =============
    
    private final DynaShopPlugin plugin;
    private final PriceRecipe priceRecipe;
    private final DataConfig dataConfig;
    private final ShopConfigManager shopConfigManager;
    
    // ============= CONSTRUCTEUR =============
    
    public DynaShopListener(DynaShopPlugin plugin) {
        this.plugin = plugin;
        this.priceRecipe = new PriceRecipe(plugin);
        this.dataConfig = new DataConfig(plugin.getConfigMain());
        this.shopConfigManager = plugin.getShopConfigManager();
    }

    // ============= CONSTANTES =============

    /**
     * Classe conteneur pour stocker les valeurs de prix à modifier
     */
    private static class PriceParams {
        public double buyPrice = -1;
        public double sellPrice = -1;
        public double minBuy = -1;
        public double maxBuy = -1;
        public double minSell = -1;
        public double maxSell = -1;
        public double growthBuy = 1;
        public double decayBuy = 1;
        public double growthSell = 1;
        public double decaySell = 1;
        public int stock = 0;
        public int minStock = 0;
        public int maxStock = 0;
        public double stockBuyModifier = 1;
        public double stockSellModifier = 1;
    }
    
    // ============= ÉVÉNEMENTS =============
    
    /**
     * Événement déclenché avant une transaction de shop.
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

        // Vérification des limites de transaction
        if (checkTransactionLimits(player, shopID, itemID, isBuy, amount, event)) {
            return; // Transaction annulée en raison des limites
        }

        // Vérifier si l'item est configuré pour DynaShop
        if (!shopConfigManager.getItemValue(shopID, itemID, "typeDynaShop", String.class).isPresent()) {
            return;
        }

        // Charger les prix dynamiques
        DynamicPrice price = getOrLoadPriceInternal(null, shopID, itemID, itemStack, new HashSet<>(), new HashMap<>(), false);
        if (price == null) {
            return;
        }
        
        DynaShopType typeDynaShop = price.getDynaShopType();
        
        // Vérification de stock pour les items en mode STOCK
        if (checkStockLimits(event, typeDynaShop, price, shopID, itemID, amount)) {
            return; // Transaction annulée en raison des limites de stock
        }
        
        // Vérification du stock pour les items de type RECIPE
        if (checkRecipeStockLimits(event, typeDynaShop, shopID, itemID, amount)) {
            return; // Transaction annulée en raison des limites de stock pour les recettes
        }
        
        // Enregistrement des prix pour l'historique
        recordPriceForHistory(shopID, itemID, price, isBuy, amount);

        // Application des prix et des modificateurs
        applyPriceModifiers(event, price, player, item);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onShopPostTransaction(ShopPostTransactionEvent event) {
        // Ignorer les transactions échouées
        if (event.getResult().getResult() != ShopTransactionResultType.SUCCESS) {
            return;
        }

        // Capturer toutes les données nécessaires
        final Player player = event.getResult().getPlayer();
        final ShopItem item = event.getResult().getShopItem();
        final int amount = event.getResult().getAmount();
        final String shopID = item.getShop().getId();
        final String itemID = item.getId();
        final ItemStack itemStack = item.getItem().clone();
        final ShopAction action = event.getResult().getShopAction();
        final double resultPrice = event.getResult().getPrice();
        final boolean isBuy = action == ShopAction.BUY;

        // Appliquer les taxes si le service est activé
        applyTaxes(player, resultPrice, shopID, itemID, isBuy);
        
        // Invalider le cache pour cet item
        plugin.invalidatePriceCache(shopID, itemID, player);

        // Gestion des items de type RECIPE
        DynaShopType typeDynaShop = shopConfigManager.resolveTypeDynaShop(shopID, itemID, isBuy);
        handleRecipeTypeItems(shopID, itemID, typeDynaShop);
        
        // Traitement asynchrone de la transaction
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            processTransactionAsync(shopID, itemID, itemStack, amount, action);
        });
        
        // Mise à jour des données dans le stockage
        updateStorageData(player, shopID, itemID, isBuy, amount);
    }
    
    // ============= MÉTHODES DE VÉRIFICATION =============
    
    /**
     * Vérifie si la transaction respecte les limites configurées
     */
    private boolean checkTransactionLimits(Player player, String shopID, String itemID, boolean isBuy, int amount, ShopPreTransactionEvent event) {
        if (plugin.getShopConfigManager().hasSection(shopID, itemID, "limit")) {
            boolean canPerform = plugin.getTransactionLimiter().canPerformTransactionSync(player, shopID, itemID, isBuy, amount);
            if (!canPerform) {
                handleLimitExceeded(player, shopID, itemID, isBuy, event);
                return true; // Limite dépassée
            }
        }
        return false; // Pas de limite dépassée
    }
    
    /**
     * Vérifie les limites de stock pour les items en mode STOCK
     */
    private boolean checkStockLimits(ShopPreTransactionEvent event, DynaShopType typeDynaShop, DynamicPrice price, String shopID, String itemID, int amount) {
        if (typeDynaShop == DynaShopType.STOCK || typeDynaShop == DynaShopType.STATIC_STOCK) {
            // Vérifier si l'achat est possible (stock suffisant)
            if (event.getShopAction() == ShopAction.BUY && !plugin.getPriceStock().canBuy(shopID, itemID, amount)) {
                event.setCancelled(true);
                if (event.getPlayer() != null) {
                    event.getPlayer().sendMessage(ChatColor.translateAlternateColorCodes('&', this.plugin.getLangConfig().getMsgOutOfStock()));
                }
                return true;
            }
            
            // Vérifier si la vente est possible (stock pas plein)
            if ((event.getShopAction() == ShopAction.SELL || event.getShopAction() == ShopAction.SELL_ALL) && 
                !plugin.getPriceStock().canSell(shopID, itemID, amount)) {
                event.setCancelled(true);
                if (event.getPlayer() != null) {
                    event.getPlayer().sendMessage(ChatColor.translateAlternateColorCodes('&', this.plugin.getLangConfig().getMsgFullStock()));
                }
                return true;
            }
        }
        return false;
    }
    
    /**
     * Vérifie les limites de stock pour les items de type RECIPE
     */
    private boolean checkRecipeStockLimits(ShopPreTransactionEvent event, DynaShopType typeDynaShop, String shopID, String itemID, int amount) {
        if (typeDynaShop == DynaShopType.RECIPE) {
            int stockAmount = priceRecipe.calculateStock(shopID, itemID, new ArrayList<>());
            int maxStock = priceRecipe.calculateMaxStock(shopID, itemID, new ArrayList<>());
            
            if (maxStock > 0) {
                // Vérifier si le stock est suffisant pour l'achat
                if (event.getShopAction() == ShopAction.BUY && stockAmount < amount) {
                    event.setCancelled(true);
                    if (event.getPlayer() != null) {
                        event.getPlayer().sendMessage(ChatColor.translateAlternateColorCodes('&', this.plugin.getLangConfig().getMsgOutOfStock()));
                    }
                    return true;
                }
                
                // Vérifier si le stock est suffisant pour la vente
                if ((event.getShopAction() == ShopAction.SELL || event.getShopAction() == ShopAction.SELL_ALL) && stockAmount >= maxStock) {
                    event.setCancelled(true);
                    if (event.getPlayer() != null) {
                        event.getPlayer().sendMessage(ChatColor.translateAlternateColorCodes('&', this.plugin.getLangConfig().getMsgFullStock()));
                    }
                    return true;
                }
            }
        }
        return false;
    }
    
    // ============= TRAITEMENT DES TRANSACTIONS =============
    
    /**
     * Applique les taxes sur une transaction
     */
    private void applyTaxes(Player player, double price, String shopID, String itemID, boolean isBuy) {
        if (plugin.getTaxService() != null && plugin.getTaxService().isEnabled()) {
            if (isBuy) {
                plugin.getTaxService().applyBuyTax(player, price, shopID, itemID);
            } else {
                plugin.getTaxService().applySellTax(player, price, shopID, itemID);
            }
        }
    }
    
    /**
     * Gère les items de type RECIPE pour invalider les caches des ingrédients
     */
    private void handleRecipeTypeItems(String shopID, String itemID, DynaShopType typeDynaShop) {
        if (typeDynaShop == DynaShopType.RECIPE) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                invalidateRecipeIngredients(shopID, itemID);
            });
        }
    }
    
    /**
     * Mets à jour les données dans le stockage après une transaction
     */
    private void updateStorageData(Player player, String shopID, String itemID, boolean isBuy, int amount) {
        // Mise à jour des prix
        DynamicPrice updatedPrice = plugin.getPriceCache().getIfPresent(shopID + ":" + itemID);
        if (updatedPrice != null) {
            plugin.getStorageManager().savePrice(shopID, itemID, updatedPrice.getBuyPrice(), updatedPrice.getSellPrice(), updatedPrice.getStock());
        }
        
        // Mise à jour du stock
        Integer stock = plugin.getStockCache().getIfPresent(shopID + ":" + itemID);
        if (stock != null && stock >= 0) {
            plugin.getStorageManager().saveStock(shopID, itemID, stock);
        }
        
        // Enregistrer la transaction si l'item a des limites
        if (plugin.getShopConfigManager().hasSection(shopID, itemID, "limit")) {
            plugin.getTransactionLimiter().queueTransaction(player, shopID, itemID, isBuy, amount);
        }
    }
    
    /**
     * Traite la transaction de manière asynchrone
     */
    private void processTransactionAsync(String shopID, String itemID, ItemStack itemStack, int amount, ShopAction action) {
        if (!shopConfigManager.getItemValue(shopID, itemID, "typeDynaShop", String.class).isPresent()) {
            return;
        }

        DynamicPrice price = getOrLoadPriceInternal(null, shopID, itemID, itemStack, new HashSet<>(), new HashMap<>(), false);
        if (price == null) {
            return;
        }

        DynaShopType typeDynaShop = price.getDynaShopType();
        DynaShopType buyTypeDynaShop = price.getBuyTypeDynaShop();
        DynaShopType sellTypeDynaShop = price.getSellTypeDynaShop();
        
        if (buyTypeDynaShop == DynaShopType.NONE || buyTypeDynaShop == DynaShopType.UNKNOWN) buyTypeDynaShop = typeDynaShop;
        if (sellTypeDynaShop == DynaShopType.NONE || sellTypeDynaShop == DynaShopType.UNKNOWN) sellTypeDynaShop = typeDynaShop;

        // Traiter les prix selon le type de transaction
        handlePriceChanges(action, shopID, itemID, price, typeDynaShop, buyTypeDynaShop, sellTypeDynaShop, amount, itemStack);
        
        // Sauvegarder les modifications si nécessaire
        if (sellTypeDynaShop != DynaShopType.RECIPE || buyTypeDynaShop != DynaShopType.RECIPE) {
            plugin.getBatchDatabaseUpdater().queueUpdate(shopID, itemID, price, true);
        }
    }
    
    /**
     * Gère les changements de prix selon le type d'action et le type de boutique
     */
    private void handlePriceChanges(ShopAction action, String shopID, String itemID, DynamicPrice price, DynaShopType typeDynaShop, DynaShopType buyTypeDynaShop, DynaShopType sellTypeDynaShop, int amount, ItemStack itemStack) {
        // Traiter les stocks pour l'achat et la vente
        if (action == ShopAction.BUY) {
            if (buyTypeDynaShop == DynaShopType.STOCK || buyTypeDynaShop == DynaShopType.STATIC_STOCK) {
                handleStockPrice(price, shopID, itemID, action, amount);
            }
        } else if (action == ShopAction.SELL || action == ShopAction.SELL_ALL) {
            if (sellTypeDynaShop == DynaShopType.STOCK || sellTypeDynaShop == DynaShopType.STATIC_STOCK) {
                handleStockPrice(price, shopID, itemID, action, amount);
            }
        }
        
        // Traiter les prix dynamiques
        if (buyTypeDynaShop == DynaShopType.DYNAMIC) {
            handleDynamicPrice(price, action, amount);
        } else if (buyTypeDynaShop == DynaShopType.RECIPE) {
            handleRecipePrice(shopID, itemID, amount, action);
        } else if (buyTypeDynaShop == DynaShopType.LINK) {
            handleLinkedPrice(shopID, itemID, itemStack, action, amount);
        }

        if (sellTypeDynaShop == DynaShopType.DYNAMIC) {
            handleDynamicPrice(price, action, amount);
        } else if (sellTypeDynaShop == DynaShopType.RECIPE) {
            handleRecipePrice(shopID, itemID, amount, action);
        } else if (sellTypeDynaShop == DynaShopType.LINK) {
            handleLinkedPrice(shopID, itemID, itemStack, action, amount);
        }
    }
    
    /**
     * Applique les modificateurs de prix à une transaction
     */
    private void applyPriceModifiers(ShopPreTransactionEvent event, DynamicPrice price, Player player, ShopItem item) throws PlayerDataNotLoadedException {
        double basePrice;
        if (event.getShopAction() == ShopAction.BUY) {
            basePrice = price.getBuyPriceForAmount(event.getAmount());
            double playerBuyModifier = ShopGuiPlusApi.getPriceModifier(player, item, PriceModifierActionType.BUY).getModifier();
            event.setPrice(basePrice * playerBuyModifier);
        } else if (event.getShopAction() == ShopAction.SELL || event.getShopAction() == ShopAction.SELL_ALL) {
            basePrice = price.getSellPriceForAmount(event.getAmount());
            double playerSellModifier = ShopGuiPlusApi.getPriceModifier(player, item, PriceModifierActionType.SELL).getModifier();
            event.setPrice(basePrice * playerSellModifier);
        }
    }
    
    // ============= GESTIONNAIRES DE PRIX PAR TYPE =============
    
    /**
     * Gère les prix dynamiques
     */
    private void handleDynamicPrice(DynamicPrice price, ShopAction action, int amount) {
        if (action == ShopAction.BUY) {
            price.applyGrowth(amount);
        } else if (action == ShopAction.SELL || action == ShopAction.SELL_ALL) {
            price.applyDecay(amount);
        }
    }

    /**
     * Gère les prix basés sur des recettes
     */
    private void handleRecipePrice(String shopID, String itemID, int amount, ShopAction action) {
        boolean isGrowth = action == ShopAction.BUY;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            applyGrowthOrDecayToIngredients(shopID, itemID, amount, isGrowth, new HashSet<>(), new HashMap<>(), 0);
        });
    }

    /**
     * Gère les prix basés sur le stock
     */
    private void handleStockPrice(DynamicPrice price, String shopID, String itemID, ShopAction action, int amount) {
        if (action == ShopAction.BUY) {
            plugin.getPriceStock().processBuyTransaction(shopID, itemID, amount);
        } else if (action == ShopAction.SELL || action == ShopAction.SELL_ALL) {
            plugin.getPriceStock().processSellTransaction(shopID, itemID, amount);
        }

        // Mettre à jour l'objet prix
        double newBuyPrice = plugin.getPriceStock().calculatePrice(shopID, itemID, "buyPrice");
        double newSellPrice = plugin.getPriceStock().calculatePrice(shopID, itemID, "sellPrice");
        
        price.setBuyPrice(newBuyPrice);
        price.setSellPrice(newSellPrice);
        price.setStock(plugin.getStorageManager().getStock(shopID, itemID).orElse(0));
    }

    /**
     * Gère les prix liés à d'autres items
     */
    private void handleLinkedPrice(String shopID, String itemID, ItemStack itemStack, ShopAction action, int amount) {
        String linkedItemRef = shopConfigManager.getItemValue(shopID, itemID, "link", String.class).orElse(null);
        if (linkedItemRef != null && linkedItemRef.contains(":")) {
            String[] parts = linkedItemRef.split(":");
            if (parts.length == 2) {
                String linkedShopID = parts[0];
                String linkedItemID = parts[1];
                
                // Traiter l'item lié
                processLinkedItem(linkedShopID, linkedItemID, itemStack, action, amount, shopID, itemID);
            } else {
                plugin.getLogger().warning("Format de lien invalide pour " + shopID + ":" + itemID + ": " + linkedItemRef);
            }
        } else {
            plugin.getLogger().warning("Pas de référence de lien trouvée pour " + shopID + ":" + itemID);
        }
    }
    
    /**
     * Traite un item lié
     */
    private void processLinkedItem(String linkedShopID, String linkedItemID, ItemStack itemStack, ShopAction action, int amount, String originalShopID, String originalItemID) {
        // Charger le prix du shop lié
        DynamicPrice linkedPrice = getOrLoadPriceInternal(null, linkedShopID, linkedItemID, itemStack, new HashSet<>(), new HashMap<>(), false);
        if (linkedPrice != null) {
            // Traiter l'item lié selon son type
            DynaShopType linkedType = linkedPrice.getDynaShopType();
            DynaShopType buyTypeDynaShop = linkedPrice.getBuyTypeDynaShop();
            DynaShopType sellTypeDynaShop = linkedPrice.getSellTypeDynaShop();
            
            if (buyTypeDynaShop == DynaShopType.NONE || buyTypeDynaShop == DynaShopType.UNKNOWN) buyTypeDynaShop = linkedType;

            if (sellTypeDynaShop == DynaShopType.NONE || sellTypeDynaShop == DynaShopType.UNKNOWN) sellTypeDynaShop = linkedType;

            // Traiter l'item lié selon son type
            processLinkedItemTypes(linkedShopID, linkedItemID, linkedPrice, linkedType, buyTypeDynaShop, sellTypeDynaShop, action, amount, itemStack);

            // Copier les prix pour l'item principal
            copyLinkedPriceToMainItem(linkedShopID, linkedItemID, linkedPrice, originalShopID, originalItemID);
        }
    }
    
    /**
     * Traite les différents types d'items liés
     */
    private void processLinkedItemTypes(String linkedShopID, String linkedItemID, DynamicPrice linkedPrice, DynaShopType linkedType, DynaShopType buyTypeDynaShop, DynaShopType sellTypeDynaShop, ShopAction action, int amount, ItemStack itemStack) {
        // // Appliquer les modifications à l'item lié selon son type d'achat
        // if (buyTypeDynaShop == DynaShopType.DYNAMIC) {
        //     handleDynamicPrice(linkedPrice, action, amount);
        // } else if (buyTypeDynaShop == DynaShopType.RECIPE) {
        //     handleRecipePrice(linkedShopID, linkedItemID, amount, action);
        // } else if (buyTypeDynaShop == DynaShopType.LINK) {
        //     handleLinkedPrice(linkedShopID, linkedItemID, itemStack, action, amount);
        // }

        // // Appliquer les modifications à l'item lié selon son type de vente
        // if (sellTypeDynaShop == DynaShopType.DYNAMIC) {
        //     handleDynamicPrice(linkedPrice, action, amount);
        // } else if (sellTypeDynaShop == DynaShopType.RECIPE) {
        //     handleRecipePrice(linkedShopID, linkedItemID, amount, action);
        // } else if (sellTypeDynaShop == DynaShopType.LINK) {
        //     handleLinkedPrice(linkedShopID, linkedItemID, itemStack, action, amount);
        // }

        // // Gérer le stock si nécessaire
        // if (linkedType == DynaShopType.STOCK || linkedType == DynaShopType.STATIC_STOCK) {
        //     handleStockPrice(linkedPrice, linkedShopID, linkedItemID, action, amount);
        // }

        handlePriceChanges(action, linkedShopID, linkedItemID, linkedPrice, linkedType, buyTypeDynaShop, sellTypeDynaShop, amount, itemStack);

        // Sauvegarder les modifications sur l'item lié
        if (linkedType != DynaShopType.RECIPE) {
            plugin.getBatchDatabaseUpdater().queueUpdate(linkedShopID, linkedItemID, linkedPrice, true);
        }
    }
    
    /**
     * Copie les valeurs d'un prix lié vers l'item principal
     */
    private void copyLinkedPriceToMainItem(String linkedShopID, String linkedItemID, DynamicPrice linkedPrice, String shopID, String itemID) {
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
    
    // ============= GESTION DES RECETTES =============
    
    /**
     * Invalide les caches des ingrédients d'une recette
     */
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
    
    /**
     * Applique la croissance/décroissance aux ingrédients d'une recette
     */
    private void applyGrowthOrDecayToIngredients(String shopID, String itemID, int amount, boolean isGrowth, Set<String> visitedItems, Map<String, DynamicPrice> lastResults, int depth) {
        // Limiter la profondeur de récursion
        if (depth > 5) return;

        // Éviter les boucles infinies
        String itemKey = shopID + ":" + itemID;
        if (visitedItems.contains(itemKey)) {
            DynamicPrice last = lastResults.get(itemKey);
            if (last != null) return;
            plugin.getLogger().warning("Cycle détecté pour " + itemKey + " (lien ou recette) !");
            return;
        }
        visitedItems.add(itemKey);

        // Récupérer les ingrédients
        List<ItemStack> ingredients = plugin.getPriceRecipe().getIngredients(shopID, itemID);
        
        if (ingredients.isEmpty()) return;

        // Traiter chaque ingrédient
        for (ItemStack ingredient : ingredients) {
            if (ingredient == null || ingredient.getType() == Material.AIR) continue;

            // Rechercher l'ingrédient dans les shops
            FoundItem foundItem = plugin.getPriceRecipe().findItemInShops(shopID, ingredient);
            if (!foundItem.isFound()) continue;

            processRecipeIngredient(foundItem.getShopID(), foundItem.getItemID(), ingredient, ingredient.getAmount() * amount, isGrowth, visitedItems, lastResults, depth);
        }
    }

    /**
     * Traite un ingrédient de recette
     */
    private void processRecipeIngredient(String ingredientShopID, String ingredientID, ItemStack ingredient, int ingredientQuantity, boolean isGrowth, Set<String> visitedItems, Map<String, DynamicPrice> lastResults, int depth) {
        // Charger le prix de l'ingrédient
        DynamicPrice ingredientPrice = getOrLoadPriceInternal(null, ingredientShopID, ingredientID, ingredient, new HashSet<>(visitedItems), lastResults, false);
        if (ingredientPrice == null) return;
        
        DynaShopType ingredientType = ingredientPrice.getDynaShopType();
        DynaShopType buyTypeDynaShop = ingredientPrice.getBuyTypeDynaShop();
        DynaShopType sellTypeDynaShop = ingredientPrice.getSellTypeDynaShop();

        if (buyTypeDynaShop == DynaShopType.NONE || buyTypeDynaShop == DynaShopType.UNKNOWN) buyTypeDynaShop = ingredientType;
        if (sellTypeDynaShop == DynaShopType.NONE || sellTypeDynaShop == DynaShopType.UNKNOWN) sellTypeDynaShop = ingredientType;

        // Traiter selon le type et le mode d'action (achat/vente)
        if (isGrowth) {
            processGrowthIngredient(ingredientShopID, ingredientID, ingredient, ingredientPrice, buyTypeDynaShop, ingredientType, ingredientQuantity, visitedItems, lastResults, depth);
        } else {
            processDecayIngredient(ingredientShopID, ingredientID, ingredient, ingredientPrice, sellTypeDynaShop, ingredientType, ingredientQuantity, visitedItems, lastResults, depth);
        }
    }

    /**
     * Traite un ingrédient pour la croissance (achat)
     */
    private void processGrowthIngredient(String shopID, String itemID, ItemStack itemStack, DynamicPrice price, DynaShopType buyType, DynaShopType ingredientType, int quantity, Set<String> visitedItems, Map<String, DynamicPrice> lastResults, int depth) {
        switch (buyType) {
            case RECIPE -> 
                applyGrowthOrDecayToIngredients(shopID, itemID, quantity, true, visitedItems, lastResults, depth + 1);
            case LINK -> 
                handleLinkedPrice(shopID, itemID, itemStack, ShopAction.BUY, quantity);
            case STOCK, STATIC_STOCK -> {
                processIngredient(shopID, itemID, price, ingredientType, quantity, true);
                plugin.getBatchDatabaseUpdater().queueUpdate(shopID, itemID, price, true);
            }
            default -> {
                processIngredient(shopID, itemID, price, ingredientType, quantity, true);
                plugin.getBatchDatabaseUpdater().queueUpdate(shopID, itemID, price, true);
            }
        }
    }
    
    /**
     * Traite un ingrédient pour la décroissance (vente)
     */
    private void processDecayIngredient(String shopID, String itemID, ItemStack itemStack, DynamicPrice price, DynaShopType sellType, DynaShopType ingredientType, int quantity, Set<String> visitedItems, Map<String, DynamicPrice> lastResults, int depth) {
        switch (sellType) {
            case RECIPE ->
                applyGrowthOrDecayToIngredients(shopID, itemID, quantity, false, visitedItems, lastResults, depth + 1);
            case LINK ->
                handleLinkedPrice(shopID, itemID, itemStack, ShopAction.SELL, quantity);
            case STOCK, STATIC_STOCK -> {
                processIngredient(shopID, itemID, price, ingredientType, quantity, false);
                plugin.getBatchDatabaseUpdater().queueUpdate(shopID, itemID, price, true);
            }
            default -> {
                processIngredient(shopID, itemID, price, ingredientType, quantity, false);
                plugin.getBatchDatabaseUpdater().queueUpdate(shopID, itemID, price, true);
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
    
    // ============= CHARGEMENT DES PRIX =============
    
    /**
     * Charge le prix d'un item
     */
    public DynamicPrice getOrLoadPrice(Player player, String shopID, String itemID, ItemStack itemStack, Set<String> visited, Map<String, DynamicPrice> lastResults) {
        return getOrLoadPriceInternal(player, shopID, itemID, itemStack, visited, lastResults, false);
    }
    
    /**
     * Version interne qui charge le prix d'un item avec gestion de cycle
     */
    public DynamicPrice getOrLoadPriceInternal(Player player, String shopID, String itemID, ItemStack itemStack, Set<String> visited, Map<String, DynamicPrice> lastResults, boolean bypassCache) {
        // Gestion des cycles
        String key = shopID + ":" + itemID;
        if (visited.contains(key)) {
            DynamicPrice last = lastResults.get(key);
            if (last != null) return last;
            plugin.getLogger().warning("Cycle détecté pour " + key + " (lien ou recette) !");
            return null;
        }
        visited.add(key);

        try {
            DynamicPrice price;
            if (!bypassCache) {
                final String cacheKey = shopID + ":" + itemID + (player != null ? ":" + player.getUniqueId().toString() : "");
                price = plugin.getPriceCache().get(cacheKey, () -> {
                    DynamicPrice p = loadPriceFromSourceInternal(player, shopID, itemID, itemStack, visited, lastResults);
                    // Appliquer les modificateurs si nécessaire
                    if (p != null && player != null && p.getDynaShopType() != DynaShopType.LINK) {
                        p.applyShopGuiPlusModifiers(player, shopID, itemID);
                    }
                    return p;
                });
            } else {
                price = loadPriceFromSourceInternal(player, shopID, itemID, itemStack, visited, lastResults);
                if (price != null && player != null && price.getDynaShopType() != DynaShopType.LINK) {
                    price.applyShopGuiPlusModifiers(player, shopID, itemID);
                }
            }
            
            if (price != null) {
                lastResults.put(key, price);
            }
            return price;
        } catch (Exception e) {
            // En cas d'erreur, essayer d'utiliser une valeur précédente
            DynamicPrice last = lastResults.get(key);
            if (last != null) return last;
            plugin.getLogger().warning("Erreur lors du calcul du prix pour " + key + " : " + e.getMessage());
            return null;
        } finally {
            visited.remove(key);
        }
    }

    /**
     * Charge le prix d'un item depuis sa source de données
     */
    private DynamicPrice loadPriceFromSourceInternal(Player player, String shopID, String itemID, ItemStack itemStack, Set<String> visited, Map<String, DynamicPrice> lastResults) {
        // Déterminer le type de l'item
        DynaShopType typeDynaShop = shopConfigManager.getTypeDynaShop(shopID, itemID);
        DynaShopType buyTypeDynaShop = shopConfigManager.getTypeDynaShop(shopID, itemID, "buy");
        DynaShopType sellTypeDynaShop = shopConfigManager.getTypeDynaShop(shopID, itemID, "sell");

        // Utiliser le type général comme fallback si nécessaire
        if (buyTypeDynaShop == DynaShopType.NONE || buyTypeDynaShop == DynaShopType.UNKNOWN) buyTypeDynaShop = typeDynaShop;
        if (sellTypeDynaShop == DynaShopType.NONE || sellTypeDynaShop == DynaShopType.UNKNOWN) sellTypeDynaShop = typeDynaShop;

        // Initialiser l'objet de paramètres
        PriceParams params = new PriceParams();

        // Charger les prix selon le type d'achat
        loadBuyPrices(player, shopID, itemID, buyTypeDynaShop, visited, lastResults, params);

        // Charger les prix selon le type de vente
        loadSellPrices(player, shopID, itemID, sellTypeDynaShop, visited, lastResults, params);

        // Créer l'objet prix avec toutes les valeurs chargées
        DynamicPrice price = new DynamicPrice(
            params.buyPrice, params.sellPrice, params.minBuy, params.maxBuy, params.minSell, params.maxSell,
            params.growthBuy, params.decayBuy, params.growthSell, params.decaySell,
            params.stock, params.minStock, params.maxStock,
            params.stockBuyModifier, params.stockSellModifier
        );
        
        // Stocker les types dans l'objet
        price.setDynaShopType(typeDynaShop);
        price.setBuyTypeDynaShop(buyTypeDynaShop);
        price.setSellTypeDynaShop(sellTypeDynaShop);
        
        // Appliquer les modificateurs d'enchantement si nécessaire
        applyEnchantmentModifiers(shopID, itemID, itemStack, price);
        
        // Appliquer l'inflation après le chargement du prix
        price.applyInflation(shopID, itemID);
        
        return price;
    }
    
    /**
     * Charge les prix d'achat selon le type d'achat
     */
    private void loadBuyPrices(Player player, String shopID, String itemID, DynaShopType buyTypeDynaShop, Set<String> visited, Map<String, DynamicPrice> lastResults, PriceParams params) {
        switch (buyTypeDynaShop) {
            case RECIPE -> {
                DynamicPrice recipePrice = plugin.getPriceRecipe().createRecipePrice(shopID, itemID, visited, lastResults);
                params.buyPrice = recipePrice.getBuyPrice();
                params.minBuy = recipePrice.getMinBuyPrice();
                params.maxBuy = recipePrice.getMaxBuyPrice();
                params.growthBuy = recipePrice.getGrowthBuy();
                params.decayBuy = recipePrice.getDecayBuy();
                params.stock = recipePrice.getStock();
                params.minStock = recipePrice.getMinStock();
                params.maxStock = recipePrice.getMaxStock();
            }
            case STOCK -> {
                DynamicPrice stockPrice = plugin.getPriceStock().createStockPrice(shopID, itemID);
                params.buyPrice = stockPrice.getBuyPrice();
                params.minBuy = stockPrice.getMinBuyPrice();
                params.maxBuy = stockPrice.getMaxBuyPrice();
                params.stock = stockPrice.getStock();
                params.minStock = stockPrice.getMinStock();
                params.maxStock = stockPrice.getMaxStock();
            }
            case STATIC_STOCK -> {
                DynamicPrice staticStockPrice = plugin.getPriceStock().createStaticStockPrice(shopID, itemID);
                params.buyPrice = staticStockPrice.getBuyPrice();
                params.minBuy = staticStockPrice.getMinBuyPrice();
                params.maxBuy = staticStockPrice.getMaxBuyPrice();
                params.stock = staticStockPrice.getStock();
                params.minStock = staticStockPrice.getMinStock();
                params.maxStock = staticStockPrice.getMaxStock();
            }
            case LINK -> {
                loadLinkedBuyPrices(player, shopID, itemID, visited, lastResults, params);
            }
            default -> {
                loadDefaultBuyPrices(player, shopID, itemID, visited, lastResults, params);
            }
        }
    }
    
    /**
     * Charge les prix de vente selon le type de vente
     */
    private void loadSellPrices(Player player,String shopID, String itemID, DynaShopType sellTypeDynaShop, Set<String> visited, Map<String, DynamicPrice> lastResults, PriceParams params) {
        switch (sellTypeDynaShop) {
            case RECIPE -> {
                DynamicPrice recipePrice = plugin.getPriceRecipe().createRecipePrice(shopID, itemID, visited, lastResults);
                params.sellPrice = recipePrice.getSellPrice();
                params.minSell = recipePrice.getMinSellPrice();
                params.maxSell = recipePrice.getMaxSellPrice();
                params.growthSell = recipePrice.getGrowthSell();
                params.decaySell = recipePrice.getDecaySell();
                params.stock = recipePrice.getStock();
                params.minStock = recipePrice.getMinStock();
                params.maxStock = recipePrice.getMaxStock();
            }
            case STOCK -> {
                DynamicPrice stockPrice = plugin.getPriceStock().createStockPrice(shopID, itemID);
                params.sellPrice = stockPrice.getSellPrice();
                params.minSell = stockPrice.getMinSellPrice();
                params.maxSell = stockPrice.getMaxSellPrice();
                params.stock = stockPrice.getStock();
                params.minStock = stockPrice.getMinStock();
                params.maxStock = stockPrice.getMaxStock();
            }
            case STATIC_STOCK -> {
                DynamicPrice staticStockPrice = plugin.getPriceStock().createStaticStockPrice(shopID, itemID);
                params.sellPrice = staticStockPrice.getSellPrice();
                params.minSell = staticStockPrice.getMinSellPrice();
                params.maxSell = staticStockPrice.getMaxSellPrice();
                params.stock = staticStockPrice.getStock();
                params.minStock = staticStockPrice.getMinStock();
                params.maxStock = staticStockPrice.getMaxStock();
            }
            case LINK -> {
                loadLinkedSellPrices(player, shopID, itemID, visited, lastResults, params);
            }
            default -> {
                loadDefaultSellPrices(player, shopID, itemID, visited, lastResults, params);
            }
        }
    }
    
    /**
     * Charge les prix d'achat à partir d'un item lié
     */
    private void loadLinkedBuyPrices(Player player, String shopID, String itemID, Set<String> visited, Map<String, DynamicPrice> lastResults, PriceParams params) {
        String linkedItemRef = shopConfigManager.getItemValue(shopID, itemID, "link", String.class).orElse(null);
        if (linkedItemRef != null && linkedItemRef.contains(":")) {
            String[] parts = linkedItemRef.split(":");
            if (parts.length == 2) {
                String linkedShopID = parts[0];
                String linkedItemID = parts[1];
                
                // Récupérer le prix de l'item lié
                ItemStack linkedItemStack = ShopGuiPlusApi.getShop(linkedShopID).getShopItem(linkedItemID).getItem();
                DynamicPrice linkedPrice = getOrLoadPriceInternal(player, linkedShopID, linkedItemID, linkedItemStack, visited, lastResults, true);
                
                if (linkedPrice != null) {
                    params.buyPrice = linkedPrice.getBuyPrice();
                    params.minBuy = linkedPrice.getMinBuyPrice();
                    params.maxBuy = linkedPrice.getMaxBuyPrice();
                    params.growthBuy = linkedPrice.getGrowthBuy();
                    params.decayBuy = linkedPrice.getDecayBuy();
                    params.stock = linkedPrice.getStock();
                    params.minStock = linkedPrice.getMinStock();
                    params.maxStock = linkedPrice.getMaxStock();
                    // params.stockBuyModifier = linkedPrice.getStockBuyModifier();
                }
            }
        } else if (linkedItemRef != null && !linkedItemRef.contains(":")) {
            String linkedShopID = shopID;
            String linkedItemID = linkedItemRef;
            ItemStack linkedItemStack = ShopGuiPlusApi.getShop(linkedShopID).getShopItem(linkedItemID).getItem();
            DynamicPrice linkedPrice = getOrLoadPriceInternal(player, linkedShopID, linkedItemID, linkedItemStack, visited, lastResults, true);
            if (linkedPrice != null) {
                params.buyPrice = linkedPrice.getBuyPrice();
                params.minBuy = linkedPrice.getMinBuyPrice();
                params.maxBuy = linkedPrice.getMaxBuyPrice();
                params.growthBuy = linkedPrice.getGrowthBuy();
                params.decayBuy = linkedPrice.getDecayBuy();
                params.stock = linkedPrice.getStock();
                params.minStock = linkedPrice.getMinStock();
                params.maxStock = linkedPrice.getMaxStock();
                // params.stockBuyModifier = linkedPrice.getStockBuyModifier();
            }
        } else {
            plugin.getLogger().warning("Item " + itemID + " in shop " + shopID + " is linked but no linked item found.");
        }
    }
    
    /**
     * Charge les prix de vente à partir d'un item lié
     */
    private void loadLinkedSellPrices(Player player, String shopID, String itemID, Set<String> visited, Map<String, DynamicPrice> lastResults, PriceParams params) {
        String linkedItemRef = shopConfigManager.getItemValue(shopID, itemID, "link", String.class).orElse(null);
        if (linkedItemRef != null && linkedItemRef.contains(":")) {
            String[] parts = linkedItemRef.split(":");
            if (parts.length == 2) {
                String linkedShopID = parts[0];
                String linkedItemID = parts[1];
                
                // Récupérer le prix de l'item lié
                ItemStack linkedItemStack = ShopGuiPlusApi.getShop(linkedShopID).getShopItem(linkedItemID).getItem();
                DynamicPrice linkedPrice = getOrLoadPriceInternal(player, linkedShopID, linkedItemID, linkedItemStack, visited, lastResults, false);
                
                if (linkedPrice != null) {
                    params.sellPrice = linkedPrice.getSellPrice();
                    params.minSell = linkedPrice.getMinSellPrice();
                    params.maxSell = linkedPrice.getMaxSellPrice();
                    params.growthSell = linkedPrice.getGrowthSell();
                    params.decaySell = linkedPrice.getDecaySell();
                    params.stock = linkedPrice.getStock();
                    params.minStock = linkedPrice.getMinStock();
                    params.maxStock = linkedPrice.getMaxStock();
                    // params.stockSellModifier = linkedPrice.getStockSellModifier();
                }
            }
        } else if (linkedItemRef != null && !linkedItemRef.contains(":")) {
            String linkedShopID = shopID;
            String linkedItemID = linkedItemRef;
            ItemStack linkedItemStack = ShopGuiPlusApi.getShop(linkedShopID).getShopItem(linkedItemID).getItem();
            DynamicPrice linkedPrice = getOrLoadPriceInternal(player, linkedShopID, linkedItemID, linkedItemStack, visited, lastResults, false);
            if (linkedPrice != null) {
                params.sellPrice = linkedPrice.getSellPrice();
                params.minSell = linkedPrice.getMinSellPrice();
                params.maxSell = linkedPrice.getMaxSellPrice();
                params.growthSell = linkedPrice.getGrowthSell();
                params.decaySell = linkedPrice.getDecaySell();
                params.stock = linkedPrice.getStock();
                params.minStock = linkedPrice.getMinStock();
                params.maxStock = linkedPrice.getMaxStock();
                // params.stockSellModifier = linkedPrice.getStockSellModifier();
            }
        } else {
            plugin.getLogger().warning("Item " + itemID + " in shop " + shopID + " is linked but no linked item found.");
        }
    }
    
    /**
     * Charge les prix d'achat par défaut depuis la configuration et la base de données
     */
    private void loadDefaultBuyPrices(Player player, String shopID, String itemID, Set<String> visited, Map<String, DynamicPrice> lastResults, PriceParams params) {
        // Charger les prix dynamiques depuis la base de données
        Optional<DynamicPrice> priceFromDatabase = plugin.getStorageManager().getPrices(shopID, itemID);
        
        // Charger les données supplémentaires depuis les fichiers de configuration
        ItemPriceData priceData = shopConfigManager.getItemAllValues(shopID, itemID);
        
        params.buyPrice = priceFromDatabase.map(DynamicPrice::getBuyPrice).orElse(priceData.buyPrice.orElse(-1.0));
        params.minBuy = priceData.minBuy.orElse(params.buyPrice);
        params.maxBuy = priceData.maxBuy.orElse(params.buyPrice);
        
        // Gérer les min/max liés
        handleLinkedMinMaxBuy(player, shopID, priceData, visited, lastResults, params);

        // Charger le taux de croissance
        params.growthBuy = priceData.growthBuy.orElseGet(() -> {
            boolean hasBuyDynamic = shopConfigManager.hasSection(shopID, itemID, "buyDynamic");
            return hasBuyDynamic ? plugin.getDataConfig().getBuyGrowthRate() : 1.0;
        });
        
        // Charger le taux de décroissance
        params.decayBuy = priceData.decayBuy.orElseGet(() -> {
            boolean hasBuyDynamic = shopConfigManager.hasSection(shopID, itemID, "buyDynamic");
            return hasBuyDynamic ? plugin.getDataConfig().getBuyDecayRate() : 1.0;
        });
        
        // Charger le stock
        params.stock = priceFromDatabase.map(DynamicPrice::getStock).orElse(priceData.stock.orElse(0));
        params.minStock = priceData.minStock.orElseGet(() -> {
            boolean hasStock = shopConfigManager.hasSection(shopID, itemID, "stock");
            return hasStock ? plugin.getDataConfig().getStockMin() : 0;
        });
        params.maxStock = priceData.maxStock.orElseGet(() -> {
            boolean hasStock = shopConfigManager.hasSection(shopID, itemID, "stock");
            return hasStock ? plugin.getDataConfig().getStockMax() : Integer.MAX_VALUE;
        });
        
        // Charger le modificateur de stock
        params.stockBuyModifier = priceData.stockBuyModifier.orElseGet(() -> {
            boolean hasStock = shopConfigManager.hasSection(shopID, itemID, "stock");
            return hasStock ? plugin.getDataConfig().getStockBuyModifier() : 1.0;
        });
    }
    
    /**
     * Charge les prix de vente par défaut depuis la configuration et la base de données
     */
    private void loadDefaultSellPrices(Player player, String shopID, String itemID, Set<String> visited, Map<String, DynamicPrice> lastResults, PriceParams params) {
        // Charger les prix dynamiques depuis la base de données
        Optional<DynamicPrice> priceFromDatabase = plugin.getStorageManager().getPrices(shopID, itemID);

        // Charger les données supplémentaires depuis les fichiers de configuration
        ItemPriceData priceData = shopConfigManager.getItemAllValues(shopID, itemID);

        params.sellPrice = priceFromDatabase.map(DynamicPrice::getSellPrice).orElse(priceData.sellPrice.orElse(-1.0));
        params.minSell = priceData.minSell.orElse(params.sellPrice);
        params.maxSell = priceData.maxSell.orElse(params.sellPrice);

        // Gérer les min/max liés
        handleLinkedMinMaxSell(player, shopID, priceData, visited, lastResults, params);

        // Charger le taux de croissance
        params.growthSell = priceData.growthSell.orElseGet(() -> {
            boolean hasSellDynamic = shopConfigManager.hasSection(shopID, itemID, "sellDynamic");
            return hasSellDynamic ? plugin.getDataConfig().getSellGrowthRate() : 1.0;
        });
        
        // Charger le taux de décroissance
        params.decaySell = priceData.decaySell.orElseGet(() -> {
            boolean hasSellDynamic = shopConfigManager.hasSection(shopID, itemID, "sellDynamic");
            return hasSellDynamic ? plugin.getDataConfig().getSellDecayRate() : 1.0;
        });
        
        // Stock déjà chargé par loadBuyPrices
        
        // Charger le modificateur de stock
        params.stockSellModifier = priceData.stockSellModifier.orElseGet(() -> {
            boolean hasStock = shopConfigManager.hasSection(shopID, itemID, "stock");
            return hasStock ? plugin.getDataConfig().getStockSellModifier() : 1.0;
        });
    }
    
    /**
     * Gère les min/max liés pour les prix d'achat
     */
    private void handleLinkedMinMaxBuy(Player player, String shopID, ItemPriceData priceData, Set<String> visited, Map<String, DynamicPrice> lastResults, PriceParams params) {
        if (priceData.minBuyLink.isPresent() && priceData.minBuyLink.get().contains(":")) {
            String[] parts = priceData.minBuyLink.get().split(":");
            if (parts.length == 2) {
                String linkShop = parts[0];
                String linkItem = parts[1];
                ItemStack linkedItemStack = ShopGuiPlusApi.getShop(linkShop).getShopItem(linkItem).getItem();
                DynamicPrice linkedPrice = getOrLoadPriceInternal(player, linkShop, linkItem, linkedItemStack, visited, lastResults, true);
                if (linkedPrice != null) {
                    double linkedMin = linkedPrice.getMinBuyPrice();
                    if (linkedMin > 0) {
                        params.minBuy = Math.max(params.minBuy, linkedMin);
                    }
                }
            }
        } else if (priceData.minBuyLink.isPresent() && !priceData.minBuyLink.get().contains(":")) {
            String linkShop = shopID;
            String linkItem = priceData.minBuyLink.get();
            ItemStack linkedItemStack = ShopGuiPlusApi.getShop(linkShop).getShopItem(linkItem).getItem();
            DynamicPrice linkedPrice = getOrLoadPriceInternal(player, linkShop, linkItem, linkedItemStack, visited, lastResults, true);
            if (linkedPrice != null) {
                double linkedMin = linkedPrice.getMinBuyPrice();
                if (linkedMin > 0) {
                    params.minBuy = Math.max(params.minBuy, linkedMin);
                }
            }
        }

        if (priceData.maxBuyLink.isPresent() && priceData.maxBuyLink.get().contains(":")) {
            String[] parts = priceData.maxBuyLink.get().split(":");
            if (parts.length == 2) {
                String linkShop = parts[0];
                String linkItem = parts[1];
                ItemStack linkedItemStack = ShopGuiPlusApi.getShop(linkShop).getShopItem(linkItem).getItem();
                DynamicPrice linkedPrice = getOrLoadPriceInternal(player, linkShop, linkItem, linkedItemStack, visited, lastResults, true);
                if (linkedPrice != null) {
                    double linkedMax = linkedPrice.getMaxBuyPrice();
                    if (linkedMax > 0) {
                        params.maxBuy = Math.min(params.maxBuy, linkedMax);
                    }
                }
            }
        } else if (priceData.maxBuyLink.isPresent() && !priceData.maxBuyLink.get().contains(":")) {
            String linkShop = shopID;
            String linkItem = priceData.maxBuyLink.get();
            ItemStack linkedItemStack = ShopGuiPlusApi.getShop(linkShop).getShopItem(linkItem).getItem();
            DynamicPrice linkedPrice = getOrLoadPriceInternal(player, linkShop, linkItem, linkedItemStack, visited, lastResults, true);
            if (linkedPrice != null) {
                double linkedMax = linkedPrice.getMaxBuyPrice();
                if (linkedMax > 0) {
                    params.maxBuy = Math.min(params.maxBuy, linkedMax);
                }
            }
        }
    }
    
    /**
     * Gère les min/max liés pour les prix de vente
     */
    private void handleLinkedMinMaxSell(Player player, String shopID, ItemPriceData priceData, Set<String> visited, Map<String, DynamicPrice> lastResults, PriceParams params) {
        if (priceData.minSellLink.isPresent() && priceData.minSellLink.get().contains(":")) {
            String[] parts = priceData.minSellLink.get().split(":");
            if (parts.length == 2) {
                String linkShop = parts[0];
                String linkItem = parts[1];
                ItemStack linkedItemStack = ShopGuiPlusApi.getShop(linkShop).getShopItem(linkItem).getItem();
                DynamicPrice linkedPrice = getOrLoadPriceInternal(player, linkShop, linkItem, linkedItemStack, visited, lastResults, false);
                if (linkedPrice != null) {
                    double linkedMin = linkedPrice.getMinSellPrice();
                    if (linkedMin > 0) {
                        params.minSell = Math.max(params.minSell, linkedMin);
                    }

                }
            }
        } else if (priceData.minSellLink.isPresent() && !priceData.minSellLink.get().contains(":")) {
            String linkShop = shopID;
            String linkItem = priceData.minSellLink.get();
            ItemStack linkedItemStack = ShopGuiPlusApi.getShop(linkShop).getShopItem(linkItem).getItem();
            DynamicPrice linkedPrice = getOrLoadPriceInternal(player, linkShop, linkItem, linkedItemStack, visited, lastResults, false);
            if (linkedPrice != null) {
                double linkedMin = linkedPrice.getMinSellPrice();
                if (linkedMin > 0) {
                    params.minSell = Math.max(params.minSell, linkedMin);
                }
            }
        }
        
        if (priceData.maxSellLink.isPresent() && priceData.maxSellLink.get().contains(":")) {
            String[] parts = priceData.maxSellLink.get().split(":");
            if (parts.length == 2) {
                String linkShop = parts[0];
                String linkItem = parts[1];
                ItemStack linkedItemStack = ShopGuiPlusApi.getShop(linkShop).getShopItem(linkItem).getItem();
                DynamicPrice linkedPrice = getOrLoadPriceInternal(player, linkShop, linkItem, linkedItemStack, visited, lastResults, false);
                if (linkedPrice != null) {
                    double linkedMax = linkedPrice.getMaxSellPrice();
                    if (linkedMax > 0) {
                        params.maxSell = Math.min(params.maxSell, linkedMax);
                    }
                }
            }
        } else if (priceData.maxSellLink.isPresent() && !priceData.maxSellLink.get().contains(":")) {
            String linkShop = shopID;
            String linkItem = priceData.maxSellLink.get();
            ItemStack linkedItemStack = ShopGuiPlusApi.getShop(linkShop).getShopItem(linkItem).getItem();
            DynamicPrice linkedPrice = getOrLoadPriceInternal(player, linkShop, linkItem, linkedItemStack, visited, lastResults, false);
            if (linkedPrice != null) {
                double linkedMax = linkedPrice.getMaxSellPrice();
                if (linkedMax > 0) {
                    params.maxSell = Math.min(params.maxSell, linkedMax);
                }
            }
        }
    }
    
    /**
     * Applique des modificateurs de prix basés sur les enchantements
     */
    private void applyEnchantmentModifiers(String shopID, String itemID, ItemStack itemStack, DynamicPrice price) {
        // Vérifier si les enchantements doivent être pris en compte
        boolean enchantmentEnabled = shopConfigManager.getItemValue(shopID, itemID, "dynaShop.enchantment", Boolean.class).orElse(false);

        if (enchantmentEnabled && itemStack != null && itemStack.getType() != Material.AIR) {
            // Appliquer les modificateurs d'enchantement
            double enchantmentModifier = plugin.getPriceRecipe().getEnchantMultiplier(itemStack);
            if (enchantmentModifier != 1.0) {
                price.setBuyPrice(price.getBuyPrice() * enchantmentModifier);
                price.setSellPrice(price.getSellPrice() * enchantmentModifier);
                price.setMinBuyPrice(price.getMinBuyPrice() * enchantmentModifier);
                price.setMaxBuyPrice(price.getMaxBuyPrice() * enchantmentModifier);
                price.setMinSellPrice(price.getMinSellPrice() * enchantmentModifier);
                price.setMaxSellPrice(price.getMaxSellPrice() * enchantmentModifier);
            }
        }
    }

    // /**
    //  * Enregistre le prix pour l'historique
    //  */
    // private void recordPriceForHistory(String shopID, String itemID, DynamicPrice price, boolean isBuy, int amount) {
    //     // Ne pas enregistrer si la fonctionnalité est désactivée
    //     if (!plugin.getConfigMain().getBoolean("price-history.enabled", true)) {
    //         return;
    //     }
        
    //     // Récupérer l'historique des prix pour cet item
    //     PriceHistory history = plugin.getStorageManager().getPriceHistory(shopID, itemID);
        
    //     // Ajouter un nouveau point si nécessaire
    //     LocalDateTime now = LocalDateTime.now();
        
    //     // Vérifier si nous devons ajouter un nouveau point ou mettre à jour le dernier
    //     if (history.getDataPoints().isEmpty() || Duration.between(history.getLastPoint().getTimestamp(), now).toMinutes() >= plugin.getConfigMain().getInt("history.save-interval", 15)) {
            
    //         // Créer un nouveau point avec les prix actuels
    //         double buyPrice = price.getBuyPrice();
    //         double sellPrice = price.getSellPrice();
            
    //         history.addDataPoint(
    //             buyPrice, buyPrice, buyPrice, buyPrice,
    //             sellPrice, sellPrice, sellPrice, sellPrice,
    //             amount
    //         );
            
    //         // Sauvegarder le point dans l'historique
    //         plugin.getStorageManager().savePriceHistory(shopID, itemID, history);
    //     } else {
    //         // Mettre à jour le volume dans le dernier point
    //         PriceDataPoint lastPoint = history.getLastPoint();
    //         lastPoint.addVolume(amount);
            
    //         // Sauvegarder le point mis à jour
    //         plugin.getStorageManager().savePriceHistory(shopID, itemID, history);
    //     }
    // }
    
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
        final int INTERVAL_MINUTES = plugin.getConfigMain().getInt("history.save-interval", 15);
        
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
    
    /**
     * Gère le cas où une limite de transaction est dépassée
     */
    private void handleLimitExceeded(Player player, String shopID, String itemID, boolean isBuy, ShopPreTransactionEvent event) {
        event.setCancelled(true);
        
        // Récupérer les informations sur la limite
        LimitCacheEntry limit = plugin.getTransactionLimiter().getTransactionLimit(player, shopID, itemID, isBuy);
        
        if (limit != null) {
            int remaining = limit.remaining;
            long nextAvailable = limit.nextAvailable;
            
            // Message personnalisé selon le type de limite
            String message;
            // Ajouter le temps avant reset si disponible
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
            
            // Envoyer le message au joueur
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));

            // Jouer un son d'erreur si configuré
            Sound errorSound = Sound.valueOf(plugin.getConfigMain().getString("limit.sound", "ENTITY_VILLAGER_NO"));
            player.playSound(player.getLocation(), errorSound, 1.0F, 1.0F);
        }
    }
    
    /**
     * Formate un temps en millisecondes en une chaîne lisible
     */
    private String formatTime(long millisRemaining) {
        // long seconds = millisRemaining / 1000;
        // long minutes = seconds / 60;
        // long hours = minutes / 60;
        // long days = hours / 24;
        
        // seconds %= 60;
        // minutes %= 60;
        // hours %= 24;
        
        // if (days > 0) {
        //     return String.format("%dj %02dh %02dm", days, hours, minutes);
        // } else if (hours > 0) {
        //     return String.format("%dh %02dm %02ds", hours, minutes, seconds);
        // } else if (minutes > 0) {
        //     return String.format("%dm %02ds", minutes, seconds);
        // } else {
        //     return String.format("%ds", seconds);
        // }
        
        Duration duration = Duration.ofSeconds(millisRemaining);
        long years = duration.toDays() / 365;
        long months = (duration.toDays() % 365) / 30; // Approximation, car les mois ne sont pas tous de 30 jours
        // long weeks = (duration.toDays() % 365) % 30 / 7; // Approximation, car les mois ne sont pas tous de 30 jours
        long days = duration.toDaysPart() % 7;
        // long days = duration.toDays() % 365;
        long hours = duration.toHoursPart();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();

        StringBuilder timeFormatted = new StringBuilder();
        if (years > 0) {
            timeFormatted.append(years).append(" years");
        }
        if (months > 0) {
            timeFormatted.append(" ").append(months).append(" months");
        }
        // if (weeks > 0) {
        //     timeFormatted.append(" ").append(weeks).append(" weeks");
        // }
        if (days > 0) {
            timeFormatted.append(" ").append(days).append(" days");
        }
        if (hours > 0) {
            timeFormatted.append(" ").append(hours).append(" h");
        }
        if (minutes > 0) {
            timeFormatted.append(" ").append(minutes).append(" min");
        }
        if (seconds > 0) {
            timeFormatted.append(" ").append(seconds).append(" sec");
        }

        return timeFormatted.toString();
    }
}