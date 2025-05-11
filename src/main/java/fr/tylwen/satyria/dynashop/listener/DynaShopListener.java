package fr.tylwen.satyria.dynashop.listener;

import java.util.ArrayList;
import java.util.List;
// import java.security.cert.PKIXRevocationChecker.Option;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.Material;
// import org.bukkit.Bukkit;
// import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
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
import net.brcdev.shopgui.shop.item.ShopItem;
import net.brcdev.shopgui.shop.ShopManager.ShopAction;
import net.brcdev.shopgui.shop.ShopTransactionResult.ShopTransactionResultType;

public class DynaShopListener implements Listener {
    private DynaShopPlugin mainPlugin;
    private final PriceRecipe priceRecipe;
    private final DataConfig dataConfig;
    private final ShopConfigManager shopConfigManager;
    
    // private long lastPriceUpdate = 0;
    // private static final long PRICE_UPDATE_COOLDOWN = 500; // 500ms minimum entre les mises à jour

    public DynaShopListener(DynaShopPlugin mainPlugin) {
        this.mainPlugin = mainPlugin;
        this.priceRecipe = new PriceRecipe(mainPlugin.getConfigMain());
        this.dataConfig = new DataConfig(mainPlugin.getConfigMain());
        this.shopConfigManager = mainPlugin.getShopConfigManager();
    }

    // public void onPlayerJoin(PlayerJoinEvent event) {
    //     Player player = event.getPlayer();
    // }

    // public void onPlayerQuit(PlayerJoinEvent event) {
    //     Player player = event.getPlayer();
    // }

    // @EventHandler(priority = EventPriority.HIGHEST)
    // // @EventHandler
    // public void onShopPreTransaction(ShopPreTransactionEvent event) {
    //     ShopItem item = event.getShopItem();
    //     int amount = event.getAmount();
    //     String shopID = item.getShop().getId();
    //     String itemID = item.getId();
    //     ItemStack itemStack = item.getItem();

    //     // Vérifier si l'item a un prix dynamique
    //     // if (!shopConfigManager.hasDynamicSection(shopID, itemID)) {
    //     // if (!shopConfigManager.hasDynamicSection(shopID, itemID) && !shopConfigManager.getItemBooleanValue(shopID, itemID, "useRecipe").orElse(false)) {
    //     // if (!shopConfigManager.hasDynamicSection(shopID, itemID) && !shopConfigManager.hasSection(shopID, itemID, "recipe")) {
    //     if (!shopConfigManager.hasDynamicSection(shopID, itemID) && !shopConfigManager.getItemValue(shopID, itemID, "recipe.enabled", Boolean.class).orElse(false)) {
    //         mainPlugin.getLogger().info("Section : Pas de prix dynamique pour l'item " + itemID + " dans le shop " + shopID);
    //         return; // Ignorer les items sans prix dynamique
    //     }

    //     DynamicPrice price = getOrLoadPrice(shopID, itemID, itemStack);
    //     if (price == null) {
    //         mainPlugin.getLogger().info("Price : Pas de prix dynamique pour l'item " + itemID + " dans le shop " + shopID);
    //         return;
    //     }

    //     if (event.getShopAction() == ShopAction.BUY) {
    //         // price.incrementBuy();
    //         // event.setPrice(price.getBuyPrice());
    //         event.setPrice(price.getBuyPriceForAmount(amount));
    //     } else if (event.getShopAction() == ShopAction.SELL || event.getShopAction() == ShopAction.SELL_ALL) {
    //         // price.incrementSell();
    //         // event.setPrice(price.getSellPrice());
    //         event.setPrice(price.getSellPriceForAmount(amount));
    //     }
        
    //     // Mettre à jour le lore de l'item dans le shop
    //     // DynaShopPlugin.getInstance().getGuiManager().updateShopItemLore(shopID, itemID, price);

    //     // Rafraîchir l'interface utilisateur des joueurs
    //     // DynaShopPlugin.getInstance().getGuiManager().refreshShopForPlayers(shopID);
    // }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onShopPreTransaction(ShopPreTransactionEvent event) {
        ShopItem item = event.getShopItem();
        int amount = event.getAmount();
        String shopID = item.getShop().getId();
        String itemID = item.getId();
        ItemStack itemStack = item.getItem();

        // if (!shopConfigManager.hasDynaShopSection(shopID, itemID)) {
        if (!shopConfigManager.getItemValue(shopID, itemID, "typeDynaShop", String.class).isPresent()) {
            return; // Ignorer les items non configurés pour DynaShop
        }
        if (!shopConfigManager.hasStockSection(shopID, itemID) && !shopConfigManager.hasDynamicSection(shopID, itemID) && !shopConfigManager.hasRecipeSection(shopID, itemID)) {
            return; // Ignorer les items sans les sections requises
        }

        // if (!shopConfigManager.hasDynamicSection(shopID, itemID) && !shopConfigManager.getItemValue(shopID, itemID, "recipe.enabled", Boolean.class).orElse(false)) {
        //     mainPlugin.getLogger().info("Section : Pas de prix dynamique pour l'item " + itemID + " dans le shop " + shopID);
        //     return; // Ignorer les items sans prix dynamique
        // }

        DynamicPrice price = getOrLoadPrice(shopID, itemID, itemStack);
        if (price == null) {
            return;
        }
        
        // Vérifier le mode STOCK et les limites de stock
        // if (shopConfigManager.getTypeDynaShop(shopID, itemID) == DynaShopType.STOCK && price.isFromStock()) {
        if (shopConfigManager.getTypeDynaShop(shopID, itemID) == DynaShopType.STOCK) {
            // Si c'est un achat et que le stock est vide
            // if (event.getShopAction() == ShopAction.BUY && price.getStock() <= 0) {
            if (event.getShopAction() == ShopAction.BUY && !DynaShopPlugin.getInstance().getPriceStock().canBuy(shopID, itemID, amount)) {
                event.setCancelled(true);
                if (event.getPlayer() != null) {
                    event.getPlayer().sendMessage("§c[DynaShop] Cet item est en rupture de stock !");
                }
                return;
            }
            
            // Si c'est une vente et que le stock est plein
            // if ((event.getShopAction() == ShopAction.SELL || event.getShopAction() == ShopAction.SELL_ALL) && price.getStock() >= price.getMaxStock()) {
            if ((event.getShopAction() == ShopAction.SELL || event.getShopAction() == ShopAction.SELL_ALL) && !DynaShopPlugin.getInstance().getPriceStock().canSell(shopID, itemID, amount)) {
                event.setCancelled(true);
                if (event.getPlayer() != null) {
                    event.getPlayer().sendMessage("§c[DynaShop] Le stock de cet item est complet, impossible de vendre plus !");
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
        final ShopItem item = event.getResult().getShopItem();
        final int amount = event.getResult().getAmount();
        final String shopID = item.getShop().getId();
        final String itemID = item.getId();
        final ItemStack itemStack = item.getItem().clone(); // Cloner pour éviter des problèmes de concurrence
        final ShopAction action = event.getResult().getShopAction();
        final double resultPrice = event.getResult().getPrice();

        Bukkit.getScheduler().runTaskAsynchronously(mainPlugin, () -> {
            processTransactionAsync(shopID, itemID, itemStack, amount, action, resultPrice);
        });
    }

    private void handleDynamicPrice(DynamicPrice price, ShopAction action, int amount) {
        if (action == ShopAction.BUY) {
            price.applyGrowth(amount);
        } else if (action == ShopAction.SELL || action == ShopAction.SELL_ALL) {
            price.applyDecay(amount);
        }
    }

    private void handleRecipePrice(DynamicPrice price, String shopID, String itemID, ItemStack itemStack, int amount, ShopAction action) {
        if (amount <= 0) {
            return; // Ignorer les transactions avec une quantité nulle ou négative
        }

        boolean isGrowth = action == ShopAction.BUY;

        // Exécuter dans un thread asynchrone pour éviter de bloquer le thread principal
        Bukkit.getScheduler().runTaskAsynchronously(mainPlugin, () -> {
            applyGrowthOrDecayToIngredients(shopID, itemID, itemStack, amount, isGrowth, new ArrayList<>(), 0);
        });
    }

    // private void handleStockPrice(DynamicPrice price, ShopAction action, int amount) {
    private void handleStockPrice(DynamicPrice price, String shopID, String itemID, ShopAction action, int amount) {
        mainPlugin.getLogger().info("AVANT - Stock: " + price.getStock() + ", Buy: " + price.getBuyPrice() + ", Sell: " + price.getSellPrice());
        
        if (action == ShopAction.BUY) {
            mainPlugin.getLogger().info("Diminution du stock de " + amount + " unités");
            // price.decreaseStock(amount);
            mainPlugin.getPriceStock().processBuyTransaction(shopID, itemID, amount);
            // price.adjustPricesBasedOnStock();
            // price.adjustPricesBasedOnPrices();
        } else if (action == ShopAction.SELL || action == ShopAction.SELL_ALL) {
            mainPlugin.getLogger().info("Augmentation du stock de " + amount + " unités");
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
        
        mainPlugin.getLogger().info("APRÈS - Stock: " + price.getStock() + ", Buy: " + price.getBuyPrice() + ", Sell: " + price.getSellPrice());
    }

    // private void savePriceIfNeeded(DynamicPrice price, String shopID, String itemID) {
    //     if (!price.isFromRecipe()) {
    //         // DynaShopPlugin.getInstance().getItemDataManager().savePrice(shopID, itemID, price.getBuyPrice(), price.getSellPrice());
    //         DynaShopPlugin.getInstance().getBatchDatabaseUpdater().queueUpdate(shopID, itemID, price);
    //     }
    // }



    // public DynamicPrice getOrLoadPrice(String shopID, String itemID, ItemStack itemStack) {
    //     // Charger les prix depuis la base de données
    //     Optional<DynamicPrice> priceFromDatabase = DynaShopPlugin.getInstance().getItemDataManager().getPrices(shopID, itemID);

    //     // Charger les données supplémentaires depuis les fichiers de configuration
    //     ItemPriceData priceData = shopConfigManager.getItemAllValues(shopID, itemID);
        
    //     // if (shopConfigManager.hasSection(shopID, itemID, "buyDynamic.useRecipe")) {
    //     // if (shopConfigManager.hasSection(shopID, itemID, "useRecipe")) {
    //         // if (shopConfigManager.getItemValue(shopID, itemID, "recipe.enabled", Boolean.class).orElse(false)) {
    //         if (shopConfigManager.getTypeDynaShop(shopID, itemID) == DynaShopType.RECIPE) {
    //             double buyPrice = priceRecipe.calculatePrice(shopID, itemID, itemStack, "buyPrice", new ArrayList<>());
    //             double sellPrice = priceRecipe.calculatePrice(shopID, itemID, itemStack, "sellPrice", new ArrayList<>());
    //             double minBuy = priceRecipe.calculatePrice(shopID, itemID, itemStack, "buyDynamic.min", new ArrayList<>());
    //             double maxBuy = priceRecipe.calculatePrice(shopID, itemID, itemStack, "buyDynamic.max", new ArrayList<>());
    //             double minSell = priceRecipe.calculatePrice(shopID, itemID, itemStack, "sellDynamic.min", new ArrayList<>());
    //             double maxSell = priceRecipe.calculatePrice(shopID, itemID, itemStack, "sellDynamic.max", new ArrayList<>());
    //             DynamicPrice recipePrice = new DynamicPrice(buyPrice, sellPrice, minBuy, maxBuy, minSell, maxSell, 1.0, 1.0, 1.0, 1.0, 0, 0, 0, 0.0, 0.0);
    //             // DynamicPrice recipePrice = new DynamicPrice(buyPrice, sellPrice);
    //             recipePrice.setFromRecipe(true); // Marquer comme provenant d'une recette
    //             return recipePrice;
    //         }
    //         // // Si la recette est activée, calculer le prix via la recette
    //         // double buyPrice = priceRecipe.calculateBuyPrice(itemStack);
    //         // double sellPrice = priceRecipe.calculateSellPrice(itemStack);
    //         // return new DynamicPrice(buyPrice, sellPrice, buyPrice, buyPrice, sellPrice, sellPrice, 1.0, 1.0, 1.0, 1.0);
    //     // }

    //     // Si aucune donnée n'est trouvée dans la base de données ou les fichiers de configuration, retourner null
    //     if (priceFromDatabase.isEmpty() && (priceData.buyPrice.isEmpty() || priceData.sellPrice.isEmpty())) {
    //         // if (shopConfigManager.hasSection(shopID, itemID, "useRecipe")) {
    //         //     // Si la recette est activée, calculer le prix via la recette
    //         //     double buyPrice = priceRecipe.calculateBuyPrice(itemStack);
    //         //     double sellPrice = priceRecipe.calculateSellPrice(itemStack);
    //         //     return new DynamicPrice(buyPrice, sellPrice, buyPrice, buyPrice, sellPrice, sellPrice, 1.0, 1.0, 1.0, 1.0);
    //         // }
    //         return null;
    //     }
        
    //     // // Si aucune donnée n'est trouvée dans la base de données ou les fichiers de configuration, calculer via la recette
    //     // if (priceFromDatabase.isEmpty() && (priceData.buyPrice.isEmpty() || priceData.sellPrice.isEmpty())) {
    //     //     ItemStack itemStack = shopConfigManager.getItemStack(shopID, itemID);
    //     //     if (itemStack != null) {
    //     //         double buyPrice = priceRecipe.calculateBuyPrice(itemStack);
    //     //         double sellPrice = priceRecipe.calculateSellPrice(itemStack);
    //     //         return new DynamicPrice(buyPrice, sellPrice, buyPrice, buyPrice, sellPrice, sellPrice, 1.0, 1.0, 1.0, 1.0);
    //     //     }
    //     //     return null; // Aucun prix disponible
    //     // }

    //     // Fusionner les données
    //     double buyPrice = priceFromDatabase.map(DynamicPrice::getBuyPrice).orElse(priceData.buyPrice.orElse(-1.0));
    //     double sellPrice = priceFromDatabase.map(DynamicPrice::getSellPrice).orElse(priceData.sellPrice.orElse(-1.0));

    //     double minBuy = priceData.minBuy.orElse(buyPrice);
    //     double maxBuy = priceData.maxBuy.orElse(buyPrice);
    //     double minSell = priceData.minSell.orElse(sellPrice);
    //     double maxSell = priceData.maxSell.orElse(sellPrice);

    //     double growthBuy = priceData.growthBuy.orElseGet(() -> {
    //         boolean hasBuyDynamic = shopConfigManager.hasSection(shopID, itemID, "buyDynamic");
    //         return hasBuyDynamic ? dataConfig.getBuyGrowthRate() : 1.0; // Valeur par défaut pour growthBuy
    //     });

    //     double decayBuy = priceData.decayBuy.orElseGet(() -> {
    //         boolean hasBuyDynamic = shopConfigManager.hasSection(shopID, itemID, "buyDynamic");
    //         return hasBuyDynamic ? dataConfig.getBuyDecayRate() : 1.0; // Valeur par défaut pour decayBuy
    //     });

    //     double growthSell = priceData.growthSell.orElseGet(() -> {
    //         boolean hasSellDynamic = shopConfigManager.hasSection(shopID, itemID, "sellDynamic");
    //         return hasSellDynamic ? dataConfig.getSellGrowthRate() : 1.0; // Valeur par défaut pour growthSell
    //     });

    //     double decaySell = priceData.decaySell.orElseGet(() -> {
    //         boolean hasSellDynamic = shopConfigManager.hasSection(shopID, itemID, "sellDynamic");
    //         return hasSellDynamic ? dataConfig.getSellDecayRate() : 1.0; // Valeur par défaut pour decaySell
    //     });

    //     int stock = priceFromDatabase.map(DynamicPrice::getStock).orElse(priceData.stock.orElse(0));
    //     // int minStock = priceFromDatabase.map(DynamicPrice::getMinStock).orElse(priceData.minStock.orElse(0));
    //     // int maxStock = priceFromDatabase.map(DynamicPrice::getMaxStock).orElse(priceData.maxStock.orElse(0));
    //     // double stockBuyModifier = priceFromDatabase.map(DynamicPrice::getStockBuyModifier).orElse(priceData.stockBuyModifier.orElse(0.0));
    //     // double stockSellModifier = priceFromDatabase.map(DynamicPrice::getStockSellModifier).orElse(priceData.stockSellModifier.orElse(0.0));
    //     int minStock = priceData.minStock.orElseGet( () -> {
    //         boolean hasStock = shopConfigManager.hasSection(shopID, itemID, "stock");
    //         return hasStock ? dataConfig.getStockMin() : 0; // Valeur par défaut pour minStock
    //     });

    //     int maxStock = priceData.maxStock.orElseGet( () -> {
    //         boolean hasStock = shopConfigManager.hasSection(shopID, itemID, "stock");
    //         return hasStock ? dataConfig.getStockMax() : 0; // Valeur par défaut pour maxStock
    //     });

    //     double stockBuyModifier = priceData.stockBuyModifier.orElseGet( () -> {
    //         boolean hasStock = shopConfigManager.hasSection(shopID, itemID, "stock");
    //         return hasStock ? dataConfig.getStockBuyModifier() : 0.0; // Valeur par défaut pour stockBuyModifier
    //     });

    //     double stockSellModifier = priceData.stockSellModifier.orElseGet( () -> {
    //         boolean hasStock = shopConfigManager.hasSection(shopID, itemID, "stock");
    //         return hasStock ? dataConfig.getStockSellModifier() : 0.0; // Valeur par défaut pour stockSellModifier
    //     });

    //     // return new DynamicPrice(buyPrice, sellPrice, minBuy, maxBuy, minSell, maxSell, growthBuy, decayBuy, growthSell, decaySell);
    //     return new DynamicPrice(buyPrice, sellPrice, minBuy, maxBuy, minSell, maxSell, growthBuy, decayBuy, growthSell, decaySell, stock, minStock, maxStock, stockBuyModifier, stockSellModifier);
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
        
        // Traiter les prix basés sur les recettes
        if (type == DynaShopType.RECIPE) {
            // Vérifier si les prix sont en cache
            double buyPrice = DynaShopPlugin.getInstance().getCachedRecipePrice(shopID, itemID, "buyPrice");
            double sellPrice = DynaShopPlugin.getInstance().getCachedRecipePrice(shopID, itemID, "sellPrice");
            
            // Si les prix ne sont pas en cache, les calculer
            if (buyPrice < 0 || sellPrice < 0) {
                // Utiliser les calculs synchrones pour l'initialisation
                buyPrice = priceRecipe.calculatePrice(shopID, itemID, itemStack, "buyPrice", new ArrayList<>());
                sellPrice = priceRecipe.calculatePrice(shopID, itemID, itemStack, "sellPrice", new ArrayList<>());
                
                // Mettre en cache les résultats calculés
                DynaShopPlugin.getInstance().cacheRecipePrice(shopID, itemID, "buyPrice", buyPrice);
                DynaShopPlugin.getInstance().cacheRecipePrice(shopID, itemID, "sellPrice", sellPrice);
                
                // Planifier un calcul asynchrone pour mettre à jour le cache
                priceRecipe.calculatePriceAsync(shopID, itemID, itemStack, "buyPrice", newPrice -> {
                    DynaShopPlugin.getInstance().cacheRecipePrice(shopID, itemID, "buyPrice", newPrice);
                });
                
                priceRecipe.calculatePriceAsync(shopID, itemID, itemStack, "sellPrice", newPrice -> {
                    DynaShopPlugin.getInstance().cacheRecipePrice(shopID, itemID, "sellPrice", newPrice);
                });
            }
            
            // Calculer ou récupérer les valeurs min/max
            double minBuy, maxBuy, minSell, maxSell;
            
            // Vérifier si les bornes sont en cache
            double cachedMinBuy = DynaShopPlugin.getInstance().getCachedRecipePrice(shopID, itemID, "buyDynamic.min");
            double cachedMaxBuy = DynaShopPlugin.getInstance().getCachedRecipePrice(shopID, itemID, "buyDynamic.max");
            double cachedMinSell = DynaShopPlugin.getInstance().getCachedRecipePrice(shopID, itemID, "sellDynamic.min");
            double cachedMaxSell = DynaShopPlugin.getInstance().getCachedRecipePrice(shopID, itemID, "sellDynamic.max");
            
            if (cachedMinBuy >= 0) {
                minBuy = cachedMinBuy;
            } else {
                minBuy = priceData.minBuy.orElse(buyPrice * 0.5); // Valeur par défaut
                // Calculer la valeur en arrière-plan et mettre à jour le cache
                priceRecipe.calculatePriceAsync(shopID, itemID, itemStack, "buyDynamic.min", newPrice -> {
                    DynaShopPlugin.getInstance().cacheRecipePrice(shopID, itemID, "buyDynamic.min", newPrice);
                });
            }
            
            if (cachedMaxBuy >= 0) {
                maxBuy = cachedMaxBuy;
            } else {
                maxBuy = priceData.maxBuy.orElse(buyPrice * 2.0); // Valeur par défaut
                // Calculer la valeur en arrière-plan et mettre à jour le cache
                priceRecipe.calculatePriceAsync(shopID, itemID, itemStack, "buyDynamic.max", newPrice -> {
                    DynaShopPlugin.getInstance().cacheRecipePrice(shopID, itemID, "buyDynamic.max", newPrice);
                });
            }
            
            if (cachedMinSell >= 0) {
                minSell = cachedMinSell;
            } else {
                minSell = priceData.minSell.orElse(sellPrice * 0.5); // Valeur par défaut
                // Calculer la valeur en arrière-plan et mettre à jour le cache
                priceRecipe.calculatePriceAsync(shopID, itemID, itemStack, "sellDynamic.min", newPrice -> {
                    DynaShopPlugin.getInstance().cacheRecipePrice(shopID, itemID, "sellDynamic.min", newPrice);
                });
            }
            
            if (cachedMaxSell >= 0) {
                maxSell = cachedMaxSell;
            } else {
                maxSell = priceData.maxSell.orElse(sellPrice * 2.0); // Valeur par défaut
                // Calculer la valeur en arrière-plan et mettre à jour le cache
                priceRecipe.calculatePriceAsync(shopID, itemID, itemStack, "sellDynamic.max", newPrice -> {
                    DynaShopPlugin.getInstance().cacheRecipePrice(shopID, itemID, "sellDynamic.max", newPrice);
                });
            }
            
            DynamicPrice recipePrice = new DynamicPrice(buyPrice, sellPrice, minBuy, maxBuy, minSell, maxSell, 1.0, 1.0, 1.0, 1.0, 0, 0, 0, 1.0, 1.0);
            recipePrice.setFromRecipe(true); // Marquer comme provenant d'une recette
            return recipePrice;
        }
    
        
        if (type == DynaShopType.STOCK) {
            return DynaShopPlugin.getInstance().getPriceStock().createStockPrice(shopID, itemID, itemStack);
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
            mainPlugin.getLogger().warning("Profondeur de récursion maximale atteinte pour " + itemID);
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
            }

            String ingredientID = ShopGuiPlusApi.getItemStackShopItem(ingredient).getId(); // Utiliser l'ID de l'item dans le shop
            String shopIngredientID = ShopGuiPlusApi.getItemStackShopItem(ingredient).getShop().getId(); // Utiliser l'ID du shop de l'item

            // Récupérer le prix dynamique de l'ingrédient
            // Optional<DynamicPrice> ingredientPriceOpt = DynaShopPlugin.getInstance().getItemDataManager().getPrice(shopID, ingredientID);
            DynamicPrice ingredientPrice = getOrLoadPrice(shopIngredientID, ingredientID, itemStack);

            // if (ingredientPriceOpt.isPresent()) {
            if (ingredientPrice != null) {
                // DynamicPrice ingredientPrice = ingredientPriceOpt.get();

                // Appliquer growth ou decay
                if (isGrowth) {
                    ingredientPrice.applyGrowth(amount * ingredient.getAmount());
                } else {
                    ingredientPrice.applyDecay(amount * ingredient.getAmount());
                }

                // Log pour vérifier les changements
                mainPlugin.getLogger().info("Prix mis à jour pour l'ingrédient " + ingredientID + " x " + amount * ingredient.getAmount() + ": " +
                    "Buy = " + ingredientPrice.getBuyPrice() + ", Sell = " + ingredientPrice.getSellPrice());

                // Sauvegarder les nouveaux prix dans la base de données
                // Si l'ingrédient est lui-même basé sur une recette, appliquer récursivement
                if (!ingredientPrice.isFromRecipe()) {
                    // DynaShopPlugin.getInstance().getItemDataManager().savePrice(shopingredientID, ingredientID, ingredientPrice.getBuyPrice(), ingredientPrice.getSellPrice());
                    DynaShopPlugin.getInstance().getBatchDatabaseUpdater().queueUpdate(shopIngredientID, ingredientID, ingredientPrice);
                } else {
                    // Appliquer la croissance ou la décroissance aux ingrédients de la recette de l'ingrédient
                    applyGrowthOrDecayToIngredients(shopIngredientID, ingredientID, ingredient, ingredient.getAmount(), isGrowth, visitedItems, depth + 1);
                }
            } else {
                mainPlugin.getLogger().warning("Prix dynamique introuvable pour l'ingrédient " + ingredientID + " dans le shop " + shopIngredientID);
            }
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
            mainPlugin.warning(itemID + " : Pas de prix dynamique trouvé dans le shop " + shopID);
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
        } else {
            mainPlugin.getLogger().warning("Type de gestion inconnu pour l'item " + itemID + " dans le shop " + shopID);
        }

        mainPlugin.info(action + " - Prix mis à jour pour l'item " + itemID + " dans le shop " + shopID);
        mainPlugin.info("Prix : " + resultPrice + ", amount : " + amount + ", growth : " + price.getGrowthBuy() + ", decay : " + price.getDecaySell());
        mainPlugin.info("Next BUY : " + price.getBuyPrice() + ", Min : " + price.getMinBuyPrice() + ", Max : " + price.getMaxBuyPrice());
        mainPlugin.info("Next SELL : " + price.getSellPrice() + ", Min : " + price.getMinSellPrice() + ", Max : " + price.getMaxSellPrice());

        // Sauvegarder les nouveaux prix dans la base de données
        // savePriceIfNeeded(price, shopID, itemID);
        if (!price.isFromRecipe()) {
            DynaShopPlugin.getInstance().getBatchDatabaseUpdater().queueUpdate(shopID, itemID, price);
        }
        // DynaShopPlugin.getInstance().getBatchDatabaseUpdater().queueUpdate(shopID, itemID, price);
    }
}
