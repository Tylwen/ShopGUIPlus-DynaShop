package fr.tylwen.satyria.dynashop.data.storage;

import fr.tylwen.satyria.dynashop.DynaShopPlugin;
import fr.tylwen.satyria.dynashop.data.param.DynaShopType;
// import fr.tylwen.satyria.dynashop.data.storage.limit.TransactionDataManager;
import fr.tylwen.satyria.dynashop.price.DynamicPrice;
import fr.tylwen.satyria.dynashop.system.chart.PriceHistory;
import fr.tylwen.satyria.dynashop.system.chart.PriceHistory.PriceDataPoint;
import fr.tylwen.satyria.dynashop.system.TransactionLimiter.TransactionRecord;
import net.brcdev.shopgui.ShopGuiPlusApi;
import net.brcdev.shopgui.shop.Shop;
import net.brcdev.shopgui.shop.item.ShopItem;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
// import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

public class FlatFileStorageManager implements StorageManager {
    private final DynaShopPlugin plugin;
    private final File baseFolder;
    
    // Gestionnaires de données
    private final PriceDataManager priceManager;
    private final StockDataManager stockManager;
    private final LimitTrackingManager limitManager;
    // private final TransactionDataManager transactionManager;
    private final PriceHistoryDataManager historyManager;
    private final MetadataManager metadataManager;
    
    // Planificateur pour les tâches d'arrière-plan
    private ScheduledExecutorService scheduler;
    
    // Métriques et statistiques
    private final Map<String, Object> metrics = new ConcurrentHashMap<>();
    private boolean scheduledSave = false;
    
    public FlatFileStorageManager(DynaShopPlugin plugin) {
        this.plugin = plugin;
        this.baseFolder = new File(plugin.getDataFolder(), "data");
        
        // Créer les dossiers si nécessaire
        if (!baseFolder.exists()) {
            baseFolder.mkdirs();
        }
        
        // Initialiser les gestionnaires de données
        this.priceManager = new PriceDataManager(new File(baseFolder, "prices.json"));
        this.stockManager = new StockDataManager(new File(baseFolder, "stocks.json"));
        // this.limitManager = new LimitTrackingManager(new File(baseFolder, "transactions"));
        this.limitManager = new LimitTrackingManager(new File(baseFolder, "limit"));
        this.historyManager = new PriceHistoryDataManager(new File(baseFolder, "history"));
        this.metadataManager = new MetadataManager(new File(baseFolder, "metadata.json"));
    }
    
    @Override
    public void initialize() {
        // Charger toutes les données
        priceManager.load();
        stockManager.load();
        limitManager.load();
        historyManager.load();
        metadataManager.load();
        
        // Initialiser le planificateur pour les tâches d'arrière-plan
        this.scheduler = Executors.newScheduledThreadPool(1);
        
        // Planifier les sauvegardes périodiques
        scheduler.scheduleWithFixedDelay(this::saveAll, 5, 5, TimeUnit.MINUTES);
        
        // Planifier le nettoyage des données périmées
        scheduler.scheduleWithFixedDelay(this::cleanupExpiredTransactions, 6, 24, TimeUnit.HOURS);
        
        plugin.getLogger().info("Système de stockage FlatFile initialisé avec succès");
    }
    
    @Override
    public void shutdown() {
        // Sauvegarder toutes les données avant de fermer
        priceManager.save();
        stockManager.save();
        limitManager.save();
        historyManager.save();
        metadataManager.save();

        // Arrêter le planificateur
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        // Sauvegarder toutes les données
        saveAll();
        
        plugin.getLogger().info("Système de stockage FlatFile arrêté avec succès");
    }
    
    private void saveAll() {
        priceManager.save();
        stockManager.save();
        limitManager.save();
        historyManager.save();
        metadataManager.save();
    }
    
    // ============ MÉTHODES DE GESTION DES PRIX ============
    
    @Override
    public Optional<DynamicPrice> getPrices(String shopId, String itemId) {
        String key = getItemKey(shopId, itemId);
        DynamicPrice price = priceManager.get(key);
        Integer stock = stockManager.get(key);
        
        if (price == null && stock == null) {
            return Optional.empty();
            // return Optional.of(new DynamicPrice(-1, -1, -1));
        }
        
        if (price == null) {
            // price = new DynamicPrice(-1, -1, stock != null ? stock : 0);
            price = new DynamicPrice(-1, -1, stock);
        } else if (stock != null) {
            price.setStock(stock);
        }
        
        return Optional.of(price);
    }
    
    @Override
    public Optional<Double> getBuyPrice(String shopId, String itemId) {
        String key = getItemKey(shopId, itemId);
        DynamicPrice price = priceManager.get(key);
        return price != null ? Optional.of(price.getBuyPrice()) : Optional.of(-1.0);
    }
    
    @Override
    public Optional<Double> getSellPrice(String shopId, String itemId) {
        String key = getItemKey(shopId, itemId);
        DynamicPrice price = priceManager.get(key);
        return price != null ? Optional.of(price.getSellPrice()) : Optional.of(-1.0);
    }

    @Override
    public Optional<Integer> getStock(String shopId, String itemId) {
        String key = getItemKey(shopId, itemId);
        Integer stock = stockManager.get(key);
        return stock != null ? Optional.of(stock) : Optional.of(-1);
    }

    // @Override
    // public void savePrice(String shopId, String itemId, double buyPrice, double sellPrice, int stock) {
    //     String key = getItemKey(shopId, itemId);
        
    //     // Sauvegarder le prix
    //     DynamicPrice price = priceManager.get(key);
    //     if (price == null) {
    //         price = new DynamicPrice(buyPrice, sellPrice, stock);
    //     } else {
    //         price.setBuyPrice(buyPrice);
    //         price.setSellPrice(sellPrice);
    //         price.setStock(stock);
    //     }
    //     priceManager.set(key, price);
        
    //     // Sauvegarder aussi le stock séparément
    //     stockManager.set(key, stock);
        
    //     // Planifier la sauvegarde
    //     if (!scheduledSave) {
    //         scheduledSave = true;
    //         Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
    //             priceManager.save();
    //             stockManager.save();
    //             scheduledSave = false;
    //         }, 20L); // Sauvegarde après 1 seconde
    //     }
    // }
    // @Override
    // public void savePrice(String shopId, String itemId, double buyPrice, double sellPrice, int stock) {
    //     final String key = getItemKey(shopId, itemId);
        
    //     synchronized (this) {
    //         // Récupérer le prix existant pour préserver les propriétés
    //         DynamicPrice existingPrice = priceManager.get(key);
    //         DynamicPrice newPrice;
            
    //         if (existingPrice == null) {
    //             // Créer un nouveau prix
    //             // newPrice = new DynamicPrice(buyPrice, sellPrice, stock);
    //             ItemStack itemStack = ShopGuiPlusApi.getPlugin().getShopManager().getShopById(shopId).getShopItem(itemId).getItem();
    //             newPrice = plugin.getDynaShopListener().getOrLoadPrice(null, shopId, itemId, itemStack, new HashSet<>(), new HashMap<>());
    //             newPrice.setBuyPrice(buyPrice);
    //             newPrice.setSellPrice(sellPrice);
    //             newPrice.setStock(stock);
                
    //             // Essayer de déterminer le type depuis la configuration
    //             // DynaShopType type = plugin.getShopConfigManager().getTypeDynaShop(shopId, itemId);
    //             // DynaShopType buyType = plugin.getShopConfigManager().resolveTypeDynaShop(shopId, itemId, true);
    //             // DynaShopType sellType = plugin.getShopConfigManager().resolveTypeDynaShop(shopId, itemId, false);
                
    //             // // Définir les types
    //             // newPrice.setDynaShopType(type);
    //             // newPrice.setBuyTypeDynaShop(buyType);
    //             // newPrice.setSellTypeDynaShop(sellType);
    //         } else {
    //             // Mettre à jour le prix existant tout en préservant les autres propriétés
    //             newPrice = existingPrice.clone(); // Utiliser clone pour éviter les modifications concurrentes
    //             newPrice.setBuyPrice(buyPrice);
    //             newPrice.setSellPrice(sellPrice);
    //             newPrice.setStock(stock);
    //         }
    //         priceManager.set(key, newPrice);

    //         // Sauvegarder aussi le stock séparément
    //         stockManager.set(key, stock);
            
    //         // Planifier la sauvegarde
    //         if (!scheduledSave) {
    //             scheduledSave = true;
    //             Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
    //                 synchronized (this) {
    //                     priceManager.save();
    //                     stockManager.save();
    //                     scheduledSave = false;
    //                 }
    //             }, 20L); // Sauvegarde après 1 seconde
    //         }
    //     }
    // }
    @Override
    public void savePrice(String shopId, String itemId, double buyPrice, double sellPrice, int stock) {
        final String key = getItemKey(shopId, itemId);
        
        synchronized (this) {
            // Récupérer le prix existant pour préserver les propriétés
            DynamicPrice existingPrice = priceManager.get(key);
            DynamicPrice newPrice;
            
            if (existingPrice == null) {
                // Créer un nouveau prix
                try {
                    ItemStack itemStack = ShopGuiPlusApi.getPlugin().getShopManager().getShopById(shopId).getShopItem(itemId).getItem();
                    // Récupérer les informations de type
                    DynaShopType type = plugin.getShopConfigManager().getTypeDynaShop(shopId, itemId);
                    DynaShopType buyType = plugin.getShopConfigManager().resolveTypeDynaShop(shopId, itemId, true);
                    DynaShopType sellType = plugin.getShopConfigManager().resolveTypeDynaShop(shopId, itemId, false);
                    
                    // Si c'est un LINK, on récupère aussi l'item lié
                    String linkedItemRef = null;
                    if (type == DynaShopType.LINK) {
                        linkedItemRef = plugin.getShopConfigManager().getItemValue(shopId, itemId, "link", String.class).orElse(null);
                        // plugin.getLogger().info("Lien détecté pour " + shopId + ":" + itemId + " -> " + linkedItemRef);
                    }
                    
                    // Utiliser getOrLoadPrice pour initialiser correctement
                    newPrice = plugin.getDynaShopListener().getOrLoadPrice(null, shopId, itemId, itemStack, new HashSet<>(), new HashMap<>());
                    
                    // Mettre à jour avec les valeurs fournies
                    newPrice.setBuyPrice(buyPrice);
                    newPrice.setSellPrice(sellPrice);
                    newPrice.setStock(stock);
                    
                    // Assurer que les types sont correctement définis
                    newPrice.setDynaShopType(type);
                    newPrice.setBuyTypeDynaShop(buyType);
                    newPrice.setSellTypeDynaShop(sellType);
                    
                    // plugin.getLogger().info("Nouveau prix créé pour " + shopId + ":" + itemId + " de type " + type);
                } catch (Exception e) {
                    // Fallback en cas d'erreur
                    // plugin.getLogger().warning("Erreur lors de la création du prix pour " + shopId + ":" + itemId + ": " + e.getMessage());
                    newPrice = new DynamicPrice(buyPrice, sellPrice, stock);
                }
            } else {
                // Mettre à jour le prix existant tout en préservant les autres propriétés
                newPrice = new DynamicPrice(
                    buyPrice, sellPrice,
                    existingPrice.getMinBuyPrice(), existingPrice.getMaxBuyPrice(),
                    existingPrice.getMinSellPrice(), existingPrice.getMaxSellPrice(),
                    existingPrice.getGrowthBuy(), existingPrice.getDecayBuy(),
                    existingPrice.getGrowthSell(), existingPrice.getDecaySell(),
                    stock, existingPrice.getMinStock(), existingPrice.getMaxStock(),
                    existingPrice.getStockBuyModifier(), existingPrice.getStockSellModifier()
                );
                
                // Préserver les informations de type
                newPrice.setDynaShopType(existingPrice.getDynaShopType());
                newPrice.setBuyTypeDynaShop(existingPrice.getBuyTypeDynaShop());
                newPrice.setSellTypeDynaShop(existingPrice.getSellTypeDynaShop());
                
                // plugin.getLogger().info("Prix mis à jour pour " + shopId + ":" + itemId + " de type " + existingPrice.getDynaShopType());
            }
            
            // Sauvegarder le prix
            priceManager.set(key, newPrice);
            
            // Sauvegarder aussi le stock séparément
            stockManager.set(key, stock);
            
            // Planifier la sauvegarde
            if (!scheduledSave) {
                scheduledSave = true;
                Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
                    synchronized (this) {
                        priceManager.save();
                        stockManager.save();
                        scheduledSave = false;
                    }
                }, 20L); // Sauvegarde après 1 seconde
            }
        }
    }
    
    // @Override
    // public void saveBuyPrice(String shopId, String itemId, double buyPrice) {
    //     String key = getItemKey(shopId, itemId);
    //     DynamicPrice price = priceManager.get(key);
        
    //     if (price == null) {
    //         price = new DynamicPrice(buyPrice, -1, 0);
    //     } else {
    //         price.setBuyPrice(buyPrice);
    //     }
        
    //     priceManager.set(key, price);
    // }
    
    // @Override
    // public void saveSellPrice(String shopId, String itemId, double sellPrice) {
    //     String key = getItemKey(shopId, itemId);
    //     DynamicPrice price = priceManager.get(key);
        
    //     if (price == null) {
    //         price = new DynamicPrice(-1, sellPrice, 0);
    //     } else {
    //         price.setSellPrice(sellPrice);
    //     }
        
    //     priceManager.set(key, price);
    // }
    
    // @Override
    // public void saveStock(String shopId, String itemId, int stock) {
    //     String key = getItemKey(shopId, itemId);
    //     stockManager.set(key, stock);
        
    //     // Mettre aussi à jour dans le price manager si existant
    //     DynamicPrice price = priceManager.get(key);
    //     if (price != null) {
    //         price.setStock(stock);
    //         priceManager.set(key, price);
    //     }
    // }

    // Éviter l'utilisation directe de saveBuyPrice/saveSellPrice qui causent des problèmes
    @Override
    public void saveBuyPrice(String shopId, String itemId, double price) {
        // Récupérer les autres valeurs
        Optional<DynamicPrice> existingPrice = getPrices(shopId, itemId);
        double sellPrice = existingPrice.map(DynamicPrice::getSellPrice).orElse(-1.0);
        int stock = existingPrice.map(DynamicPrice::getStock).orElse(-1);

        // Utiliser la méthode unifiée
        savePrice(shopId, itemId, price, sellPrice, stock);
    }

    @Override
    public void saveSellPrice(String shopId, String itemId, double price) {
        // Récupérer les autres valeurs
        Optional<DynamicPrice> existingPrice = getPrices(shopId, itemId);
        double buyPrice = existingPrice.map(DynamicPrice::getBuyPrice).orElse(-1.0);
        int stock = existingPrice.map(DynamicPrice::getStock).orElse(-1);
        
        // Utiliser la méthode unifiée
        savePrice(shopId, itemId, buyPrice, price, stock);
    }

    @Override
    public void saveStock(String shopId, String itemId, int stock) {
        // Récupérer les autres valeurs
        Optional<DynamicPrice> existingPrice = getPrices(shopId, itemId);
        double buyPrice = existingPrice.map(DynamicPrice::getBuyPrice).orElse(-1.0);
        double sellPrice = existingPrice.map(DynamicPrice::getSellPrice).orElse(-1.0);

        // Utiliser la méthode unifiée
        savePrice(shopId, itemId, buyPrice, sellPrice, stock);
    }

    @Override
    public void deleteStock(String shopId, String itemId) {
        String key = getItemKey(shopId, itemId);
        stockManager.remove(key);
        
        // Si le stock est supprimé, on peut aussi supprimer le prix si nécessaire
        DynamicPrice price = priceManager.get(key);
        // if (price != null && price.getStock() == 0) {
        if (price != null && price.getStock() <= 0) {
            priceManager.remove(key);
        }
    }

    @Override
    public void cleanupStockTable() {
        // Supprimer les entrées de stock sans prix
        Map<String, Integer> allStocks = stockManager.getAll();
        for (String key : new HashSet<>(allStocks.keySet())) {
            String shopId = key.split(":")[0];
            String itemId = key.split(":")[1];
            DynaShopType typeDynaShop =  plugin.getShopConfigManager().getTypeDynaShop(shopId, itemId);
            if (typeDynaShop != DynaShopType.STOCK && typeDynaShop != DynaShopType.STATIC_STOCK) {
                stockManager.remove(key);
                // Si le stock est supprimé, on peut aussi supprimer le prix si nécessaire
                DynamicPrice price = priceManager.get(key);
                if (price != null) {
                    priceManager.remove(key);
                }
            }
        }
    }
    
    @Override
    public void deleteItem(String shopId, String itemId) {
        String key = getItemKey(shopId, itemId);
        priceManager.remove(key);
        stockManager.remove(key);
    }
    
    @Override
    public boolean itemExists(String shopId, String itemId) {
        String key = getItemKey(shopId, itemId);
        return priceManager.get(key) != null || stockManager.get(key) != null;
    }
    
    @Override
    public Map<ShopItem, DynamicPrice> loadAllPrices() {
        Map<ShopItem, DynamicPrice> result = new HashMap<>();
        Map<String, DynamicPrice> allPrices = priceManager.getAll();
        Map<String, Integer> allStocks = stockManager.getAll();
        
        // Fusionner les prix et les stocks
        for (Map.Entry<String, DynamicPrice> entry : allPrices.entrySet()) {
            String key = entry.getKey();
            DynamicPrice price = entry.getValue();
            
            // Mettre à jour le stock si disponible
            Integer stock = allStocks.get(key);
            if (stock != null) {
                price.setStock(stock);
            }
            
            // Trouver le ShopItem correspondant
            String[] parts = key.split(":");
            if (parts.length != 2) continue;
            
            String shopId = parts[0];
            String itemId = parts[1];
            
            Shop shop = ShopGuiPlusApi.getPlugin().getShopManager().getShopById(shopId);
            if (shop != null) {
                ShopItem item = shop.getShopItems().stream()
                        .filter(i -> i.getId().equals(itemId))
                        .findFirst()
                        .orElse(null);
                
                if (item != null) {
                    result.put(item, price);
                }
            }
        }
        
        return result;
    }
    
    // ============ MÉTHODES DE GESTION DES TRANSACTIONS ============
    
    @Override
    public void saveTransactionsBatch(List<TransactionRecord> transactions) {
        if (transactions == null || transactions.isEmpty()) {
            return;
        }

        for (TransactionRecord record : transactions) {
            limitManager.addTransaction(
                record.playerUuid, 
                record.shopId, 
                record.itemId, 
                record.isBuy, 
                record.amount,
                record.timestamp  // Ajouter le timestamp de la transaction
            );
        }
        
        // Incrémenter les compteurs pour les statistiques
        metrics.merge("total_records", transactions.size(), (oldValue, newValue) -> {
            if (oldValue instanceof Integer && newValue instanceof Integer) {
                return (Integer) oldValue + (Integer) newValue;
            }
            return newValue;
        });

        // limitManager.save(); // Sauvegarder les transactions après ajout
        // Important : sauvegarder les changements immédiatement sur disque
        try {
            limitManager.save();
            plugin.getLogger().fine("Batch de " + transactions.size() + " transactions sauvegardé avec succès");
        } catch (Exception e) {
            plugin.getLogger().severe("Erreur lors de la sauvegarde des transactions: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    @Override
    public int getUsedAmount(UUID playerUuid, String shopId, String itemId, boolean isBuy, LocalDateTime since) {
        return limitManager.getUsedAmount(playerUuid, shopId, itemId, isBuy, since);
    }
    
    @Override
    public Optional<LocalDateTime> getLastTransactionTime(UUID playerUuid, String shopId, String itemId, boolean isBuy) {
        return limitManager.getLastTransactionTime(playerUuid, shopId, itemId, isBuy);
    }
    
    @Override
    public boolean resetLimits(UUID playerUuid, String shopId, String itemId) {
        return limitManager.resetLimits(playerUuid, shopId, itemId);
    }
    
    @Override
    public boolean resetAllLimits(UUID playerUuid) {
        return limitManager.resetAllLimits(playerUuid);
    }
    
    @Override
    public boolean resetAllLimits() {
        return limitManager.resetAllLimits();
    }
    
    @Override
    public void cleanupExpiredTransactions() {
        LocalDateTime now = LocalDateTime.now();
        
        // Configurable via config
        int daysToKeep = plugin.getConfig().getInt("history.transaction-retention-days", 365);
        LocalDateTime cutoffDate = now.minusDays(daysToKeep);
        
        limitManager.cleanupExpiredLimits(cutoffDate);
    }
    
    // ============ MÉTHODES DE GESTION DE L'HISTORIQUE DES PRIX ============
    
    @Override
    public PriceHistory getPriceHistory(String shopId, String itemId) {
        return historyManager.getPriceHistory(shopId, itemId);
    }
    
    // @Override
    // public List<PriceDataPoint> getAggregatedPriceHistory(String shopId, String itemId, int interval, LocalDateTime startTime, int maxPoints) {
    //     PriceHistory history = historyManager.getPriceHistory(shopId, itemId);
    //     if (history == null) {
    //         return Collections.emptyList();
    //     }
        
    //     // Agréger les données par intervalle
    //     return history.getAggregatedData(interval, startTime, maxPoints);
    // }
    @Override
    public List<PriceDataPoint> getAggregatedPriceHistory(String shopId, String itemId, int interval, LocalDateTime startTime, int maxPoints) {
        List<PriceDataPoint> aggregatedPoints = new ArrayList<>();
        
        // Récupérer l'historique complet
        PriceHistory history = historyManager.getPriceHistory(shopId, itemId);
        if (history == null || history.getDataPoints().isEmpty()) {
            return aggregatedPoints;
        }
        
        List<PriceDataPoint> points = new ArrayList<>(history.getDataPoints());
        
        // 1. Filtrer par date de début si spécifiée
        if (startTime != null) {
            points = points.stream()
                .filter(p -> p.getTimestamp().isAfter(startTime))
                .collect(Collectors.toList());
        }
        
        // 2. Regrouper par intervalle de temps
        Map<String, List<PriceDataPoint>> groupedPoints = new HashMap<>();
        for (PriceDataPoint point : points) {
            // Tronquer le timestamp à l'intervalle spécifié
            LocalDateTime truncatedTime = truncateToInterval(point.getTimestamp(), interval);
            String key = truncatedTime.toString();
            
            if (!groupedPoints.containsKey(key)) {
                groupedPoints.put(key, new ArrayList<>());
            }
            groupedPoints.get(key).add(point);
        }
        
        // 3. Calculer les valeurs agrégées pour chaque intervalle
        for (Map.Entry<String, List<PriceDataPoint>> entry : groupedPoints.entrySet()) {
            List<PriceDataPoint> group = entry.getValue();
            if (group.isEmpty()) continue;
            
            LocalDateTime timestamp = LocalDateTime.parse(entry.getKey());
            
            // Valeurs pour les prix d'achat
            double openBuy = group.get(0).getOpenBuyPrice();
            double closeBuy = group.get(group.size() - 1).getCloseBuyPrice();
            double highBuy = group.stream().mapToDouble(PriceDataPoint::getHighBuyPrice).max().orElse(0);
            double lowBuy = group.stream().mapToDouble(PriceDataPoint::getLowBuyPrice).filter(p -> p > 0).min().orElse(0);
            
            // Valeurs pour les prix de vente
            double openSell = group.get(0).getOpenSellPrice();
            double closeSell = group.get(group.size() - 1).getCloseSellPrice();
            double highSell = group.stream().mapToDouble(PriceDataPoint::getHighSellPrice).max().orElse(0);
            double lowSell = group.stream().mapToDouble(PriceDataPoint::getLowSellPrice).filter(p -> p > 0).min().orElse(0);
            
            // Volume total
            double volume = group.stream().mapToDouble(PriceDataPoint::getVolume).sum();
            
            PriceDataPoint aggregatedPoint = new PriceDataPoint(
                timestamp, 
                openBuy, closeBuy, highBuy, lowBuy,
                openSell, closeSell, highSell, lowSell,
                volume
            );
            
            aggregatedPoints.add(aggregatedPoint);
        }
        
        // 4. Trier les points par ordre chronologique
        aggregatedPoints.sort(Comparator.comparing(PriceDataPoint::getTimestamp));
        
        // 5. Limiter le nombre de points si nécessaire
        if (aggregatedPoints.size() > maxPoints) {
            aggregatedPoints = aggregatedPoints.subList(Math.max(0, aggregatedPoints.size() - maxPoints), aggregatedPoints.size());
        }
        
        return aggregatedPoints;
    }

    /**
     * Tronque un timestamp à un intervalle de minutes spécifié
     */
    private LocalDateTime truncateToInterval(LocalDateTime dateTime, int intervalMinutes) {
        // Calculer le nombre total de minutes depuis minuit
        int totalMinutes = dateTime.getHour() * 60 + dateTime.getMinute();
        
        // Tronquer au multiple de intervalMinutes le plus proche
        int truncatedMinutes = (totalMinutes / intervalMinutes) * intervalMinutes;
        
        // Créer un nouveau LocalDateTime avec les minutes tronquées
        return dateTime
            .withHour(truncatedMinutes / 60)
            .withMinute(truncatedMinutes % 60)
            .withSecond(0)
            .withNano(0);
    }
    
    // @Override
    // public void savePriceDataPoint(String shopId, String itemId, PriceDataPoint point) {
    //     PriceHistory history = historyManager.getPriceHistory(shopId, itemId);
    //     history.addDataPoint(point);
    //     historyManager.savePriceHistory(history);
    // }

    @Override
    public void savePriceDataPoint(String shopId, String itemId, PriceDataPoint dataPoint, int intervalMinutes) {
        PriceHistory history = historyManager.getPriceHistory(shopId, itemId);
        List<PriceDataPoint> dataPoints = history.getDataPoints();
        
        // Si aucun point n'existe, simplement ajouter le nouveau
        if (dataPoints.isEmpty()) {
            history.addDataPoint(dataPoint);
            historyManager.savePriceHistory(history);
            return;
        }
        
        // Récupérer le dernier point
        PriceDataPoint lastPoint = dataPoints.get(dataPoints.size() - 1);
        
        // Vérifier si le dernier point est dans l'intervalle défini
        if (lastPoint.getTimestamp().plusMinutes(intervalMinutes).isAfter(dataPoint.getTimestamp())) {
            // Mettre à jour le point existant au lieu d'en ajouter un nouveau
            double openBuy = lastPoint.getOpenBuyPrice();
            double closeBuy = dataPoint.getCloseBuyPrice();
            double highBuy = Math.max(lastPoint.getHighBuyPrice(), dataPoint.getHighBuyPrice());
            double lowBuy = Math.min(lastPoint.getLowBuyPrice() > 0 ? lastPoint.getLowBuyPrice() : Double.MAX_VALUE, 
                                    dataPoint.getLowBuyPrice() > 0 ? dataPoint.getLowBuyPrice() : Double.MAX_VALUE);
            if (lowBuy == Double.MAX_VALUE) lowBuy = dataPoint.getLowBuyPrice() > 0 ? dataPoint.getLowBuyPrice() : 0;
            
            double openSell = lastPoint.getOpenSellPrice();
            double closeSell = dataPoint.getCloseSellPrice();
            double highSell = Math.max(lastPoint.getHighSellPrice(), dataPoint.getHighSellPrice());
            double lowSell = Math.min(lastPoint.getLowSellPrice() > 0 ? lastPoint.getLowSellPrice() : Double.MAX_VALUE, 
                                    dataPoint.getLowSellPrice() > 0 ? dataPoint.getLowSellPrice() : Double.MAX_VALUE);
            if (lowSell == Double.MAX_VALUE) lowSell = dataPoint.getLowSellPrice() > 0 ? dataPoint.getLowSellPrice() : 0;
            
            // Additionner le volume au lieu de remplacer
            double volume = lastPoint.getVolume() + dataPoint.getVolume();
            
            // Créer un point mis à jour
            PriceDataPoint updatedPoint = new PriceDataPoint(
                lastPoint.getTimestamp(), // Garder le timestamp original
                openBuy, closeBuy, highBuy, lowBuy,
                openSell, closeSell, highSell, lowSell,
                volume
            );
            
            // Remplacer le dernier point par le point mis à jour
            history.updateDataPoint(dataPoints.size() - 1, updatedPoint);
        } else {
            // Si hors de l'intervalle, ajouter comme nouveau point
            history.addDataPoint(dataPoint);
        }
        
        // Sauvegarder l'historique mis à jour
        historyManager.savePriceHistory(history);
    }
    
    @Override
    public void purgeOldPriceHistory(int daysToKeep) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(daysToKeep);
        historyManager.purgeOldData(cutoffDate);
    }
    
    // ============ MÉTHODES DE GESTION DE L'INFLATION ============
    
    @Override
    public double getInflationFactor() {
        String value = metadataManager.getValue("inflation_factor");
        if (value != null) {
            try {
                return Double.parseDouble(value);
            } catch (NumberFormatException e) {
                return 1.0;
            }
        }
        return 1.0; // Valeur par défaut
    }
    
    @Override
    public long getLastInflationUpdate() {
        String value = metadataManager.getValue("last_inflation_update");
        if (value != null) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                return System.currentTimeMillis();
            }
        }
        return System.currentTimeMillis(); // Valeur par défaut
    }
    
    @Override
    public void saveInflationData(double factor, long timestamp) {
        metadataManager.setValue("inflation_factor", String.valueOf(factor));
        metadataManager.setValue("last_inflation_update", String.valueOf(timestamp));
        metadataManager.save();
    }
    
    // ============ MÉTHODES UTILITAIRES ============
    
    @Override
    public <T> CompletableFuture<T> executeAsync(DatabaseOperation<T> operation) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return operation.execute();
            } catch (Exception e) {
                plugin.getLogger().severe("Erreur lors de l'exécution d'une opération asynchrone: " + e.getMessage());
                throw new RuntimeException(e);
            }
        });
    }
    
    @Override
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>(metrics);

        // Ajouter les statistiques du gestionnaire de limites
        stats.putAll(limitManager.getStatistics());
        
        // Ajouter le nombre total d'items stockés
        stats.put("stored_items", priceManager.getAll().size());
        stats.put("stored_stocks", stockManager.getAll().size());
        stats.put("stored_histories", historyManager.getAll().size());
        
        return stats;
    }
    
    /**
     * Obtient la clé pour un item spécifique
     */
    private String getItemKey(String shopId, String itemId) {
        return shopId + ":" + itemId;
    }
    
    /**
     * Classe pour gérer les métadonnées du système
     */
    private static class MetadataManager {
        private final File file;
        private Map<String, String> metadata = new HashMap<>();
        
        public MetadataManager(File file) {
            this.file = file;
        }
        
        public void load() {
            try {
                metadata = JsonStorage.loadFromFile(file, 
                    new com.google.gson.reflect.TypeToken<Map<String, String>>(){}.getType(), 
                    new HashMap<>());
            } catch (Exception e) {
                metadata = new HashMap<>();
            }
        }
        
        public void save() {
            CompletableFuture.runAsync(() -> {
                try {
                    JsonStorage.saveToFile(file, metadata);
                } catch (Exception e) {
                    // Gérer l'erreur
                }
            });
        }
        
        public String getValue(String key) {
            return metadata.get(key);
        }
        
        public void setValue(String key, String value) {
            metadata.put(key, value);
        }
    }
    
    /**
     * Classe pour gérer les limites d'achat/vente des joueurs
     */
    private static class LimitTrackingManager {
        private final File baseFolder;
        private final Map<UUID, PlayerLimits> playerLimits = new HashMap<>();
        private final String timeReference; // "first" ou "last"
        
        public LimitTrackingManager(File baseFolder) {
            if (!baseFolder.exists()) {
                baseFolder.mkdirs();
            }
            // Créer le sous-dossier "player" pour les données des joueurs
            File playerLimitFolder = new File(baseFolder, "player");
            if (!playerLimitFolder.exists()) {
                playerLimitFolder.mkdirs();
            }
            this.baseFolder = playerLimitFolder;
            
            // Lire la configuration
            this.timeReference = DynaShopPlugin.getInstance().getConfig().getString("limit.time-reference", "first");
        }
        
        public void load() {
            playerLimits.clear();

            File[] playerFiles = baseFolder.listFiles((dir, name) -> name.endsWith(".json"));
            if (playerFiles == null) return;
            
            for (File file : playerFiles) {
                try {
                    String fileName = file.getName();
                    UUID playerId = UUID.fromString(fileName.substring(0, fileName.length() - 5));
                    
                    PlayerLimits limits = JsonStorage.loadFromFile(file,
                        new com.google.gson.reflect.TypeToken<PlayerLimits>(){}.getType(),
                        // PlayerLimits.class,
                        new PlayerLimits());
                    
                    if (limits != null) {
                        playerLimits.put(playerId, limits);
                    }
                } catch (Exception e) {
                    // Gérer l'erreur
                }
            }
        }
        
        // public void save() {
        //     for (Map.Entry<UUID, PlayerLimits> entry : playerLimits.entrySet()) {
        //         UUID playerId = entry.getKey();
        //         PlayerLimits limits = entry.getValue();
                
        //         try {
        //             File playerFile = new File(baseFolder, playerId.toString() + ".json");
        //             JsonStorage.saveToFile(playerFile, limits);
        //         } catch (Exception e) {
        //             // Gérer l'erreur
        //         }
        //     }
        // }

        /**
         * Sauvegarde les données de limites pour tous les joueurs
         */
        public void save() {
            // Sauvegarder les limites de tous les joueurs qui ont été modifiées
            for (Map.Entry<UUID, PlayerLimits> entry : playerLimits.entrySet()) {
                try {
                    UUID playerId = entry.getKey();
                    PlayerLimits limits = entry.getValue();
                    
                    // Ne sauvegarder que s'il y a des données
                    if (!limits.isEmpty()) {
                        File playerFile = new File(baseFolder, playerId.toString() + ".json");
                        JsonStorage.saveToFile(playerFile, limits);
                    }
                } catch (IOException e) {
                    // Logguer l'erreur mais continuer pour les autres joueurs
                    // System.err.println("Erreur lors de la sauvegarde des limites pour le joueur " + entry.getKey() + ": " + e.getMessage());
                    DynaShopPlugin.getInstance().severe("Erreur lors de la sauvegarde des limites pour le joueur " + entry.getKey() + ": " + e.getMessage());
                }
            }
        }
        
        public void addTransaction(UUID playerId, String shopId, String itemId, boolean isBuy, int amount, LocalDateTime timestamp) {
            PlayerLimits limits = playerLimits.computeIfAbsent(playerId, k -> new PlayerLimits());
            limits.updateLimit(shopId, itemId, isBuy, amount, timestamp);
        }
        
        public int getUsedAmount(UUID playerId, String shopId, String itemId, boolean isBuy, LocalDateTime since) {
            PlayerLimits limits = playerLimits.get(playerId);
            if (limits == null) return 0;
            
            return limits.getUsedAmount(shopId, itemId, isBuy, since);
        }
        
        public Optional<LocalDateTime> getLastTransactionTime(UUID playerId, String shopId, String itemId, boolean isBuy) {
            PlayerLimits limits = playerLimits.get(playerId);
            if (limits == null) return Optional.empty();
            
            return limits.getLastActivityTime(shopId, itemId, isBuy, timeReference);
        }
        
        public boolean resetLimits(UUID playerId, String shopId, String itemId) {
            PlayerLimits limits = playerLimits.get(playerId);
            if (limits == null) return false;
            
            return limits.resetLimits(shopId, itemId);
        }
        
        public boolean resetAllLimits(UUID playerId) {
            playerLimits.remove(playerId);
            File playerFile = new File(baseFolder, playerId.toString() + ".json");
            try {
                java.nio.file.Files.delete(playerFile.toPath());
                return true;
            } catch (java.nio.file.NoSuchFileException e) {
                // File does not exist, consider as success
                return true;
            } catch (Exception e) {
                // Log or handle the error as needed
                return false;
            }
        }
        
        public boolean resetAllLimits() {
            playerLimits.clear();
            File[] playerFiles = baseFolder.listFiles((dir, name) -> name.endsWith(".json"));
            if (playerFiles == null) return false;
            
            boolean success = true;
            for (File file : playerFiles) {
                try {
                    java.nio.file.Files.delete(file.toPath());
                } catch (Exception e) {
                    success = false;
                }
            }
            return success;
        }
        
        public void cleanupExpiredLimits(LocalDateTime cutoffDate) {
            for (PlayerLimits limits : playerLimits.values()) {
                limits.removeExpiredLimits(cutoffDate);
            }
            
            // Supprimer les joueurs sans limites
            playerLimits.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        }
        
        public Map<String, Object> getStatistics() {
            Map<String, Object> stats = new HashMap<>();
            
            int totalBuyLimits = 0;
            int totalSellLimits = 0;
            
            for (PlayerLimits limits : playerLimits.values()) {
                totalBuyLimits += limits.getBuyLimitsCount();
                totalSellLimits += limits.getSellLimitsCount();
            }
            
            stats.put("total_records", totalBuyLimits + totalSellLimits);
            stats.put("count_buy", totalBuyLimits);
            stats.put("count_sell", totalSellLimits);
            stats.put("player_count", playerLimits.size());
            
            return stats;
        }
        
        /**
         * Classe pour stocker les limites d'un joueur
         */
        private static class PlayerLimits {
            // Stocke les limites par item (shopId:itemId) et par type (achat/vente)
            private Map<String, ItemLimit> buyLimits = new HashMap<>();
            private Map<String, ItemLimit> sellLimits = new HashMap<>();
            
            public void updateLimit(String shopId, String itemId, boolean isBuy, int amount, LocalDateTime timestamp) {
                String key = shopId + ":" + itemId;
                Map<String, ItemLimit> limitsMap = isBuy ? buyLimits : sellLimits;
                
                ItemLimit limit = limitsMap.computeIfAbsent(key, k -> new ItemLimit());
                limit.addAmount(amount, timestamp);
            }
            
            public int getUsedAmount(String shopId, String itemId, boolean isBuy, LocalDateTime since) {
                String key = shopId + ":" + itemId;
                Map<String, ItemLimit> limitsMap = isBuy ? buyLimits : sellLimits;
                
                ItemLimit limit = limitsMap.get(key);
                if (limit == null) return 0;
                
                return limit.getAmountSince(since);
            }
            
            public Optional<LocalDateTime> getLastActivityTime(String shopId, String itemId, boolean isBuy, String timeReference) {
                String key = shopId + ":" + itemId;
                Map<String, ItemLimit> limitsMap = isBuy ? buyLimits : sellLimits;
                
                ItemLimit limit = limitsMap.get(key);
                if (limit == null) return Optional.empty();
                
                return Optional.of(limit.getLastTransactionTime(timeReference));
            }
            
            public boolean resetLimits(String shopId, String itemId) {
                String key = shopId + ":" + itemId;
                boolean removed1 = buyLimits.remove(key) != null;
                boolean removed2 = sellLimits.remove(key) != null;
                return removed1 || removed2;
            }
            
            public void removeExpiredLimits(LocalDateTime cutoffDate) {
                buyLimits.entrySet().removeIf(entry -> entry.getValue().isExpired(cutoffDate));
                sellLimits.entrySet().removeIf(entry -> entry.getValue().isExpired(cutoffDate));
            }
            
            public boolean isEmpty() {
                return buyLimits.isEmpty() && sellLimits.isEmpty();
            }
            
            public int getBuyLimitsCount() {
                return buyLimits.size();
            }
            
            public int getSellLimitsCount() {
                return sellLimits.size();
            }
            
            /**
             * Classe représentant la limite pour un item spécifique
             */
            // private static class ItemLimit {
            //     private int totalAmount;
            //     private LocalDateTime lastUpdated;
            //     private Map<LocalDateTime, Integer> dailyAmounts = new HashMap<>();
            //     private Map<LocalDateTime, Integer> weeklyAmounts = new HashMap<>();
            //     private Map<LocalDateTime, Integer> monthlyAmounts = new HashMap<>();
                
            //     public ItemLimit() {
            //         this.totalAmount = 0;
            //         this.lastUpdated = LocalDateTime.now();
            //     }
                
            //     public void addAmount(int amount) {
            //         this.totalAmount += amount;
            //         this.lastUpdated = LocalDateTime.now();
                    
            //         // Mise à jour des périodes
            //         LocalDateTime now = LocalDateTime.now();
            //         LocalDateTime today = now.truncatedTo(java.time.temporal.ChronoUnit.DAYS);
            //         LocalDateTime thisWeek = now.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
            //                                 .truncatedTo(java.time.temporal.ChronoUnit.DAYS);
            //         LocalDateTime thisMonth = now.withDayOfMonth(1).truncatedTo(java.time.temporal.ChronoUnit.DAYS);
                    
            //         // Ajouter aux compteurs par période
            //         dailyAmounts.merge(today, amount, Integer::sum);
            //         weeklyAmounts.merge(thisWeek, amount, Integer::sum);
            //         monthlyAmounts.merge(thisMonth, amount, Integer::sum);
            //     }
                
            //     public int getAmountSince(LocalDateTime since) {
            //         if (since.isAfter(lastUpdated)) {
            //             return 0;
            //         }
                    
            //         // Trouver la période la plus proche
            //         LocalDateTime today = LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.DAYS);
            //         LocalDateTime thisWeek = LocalDateTime.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
            //                                             .truncatedTo(java.time.temporal.ChronoUnit.DAYS);
            //         LocalDateTime thisMonth = LocalDateTime.now().withDayOfMonth(1).truncatedTo(java.time.temporal.ChronoUnit.DAYS);
                    
            //         if (since.isEqual(today) || since.isAfter(today)) {
            //             return dailyAmounts.getOrDefault(today, 0);
            //         } else if (since.isEqual(thisWeek) || since.isAfter(thisWeek)) {
            //             return weeklyAmounts.getOrDefault(thisWeek, 0);
            //         } else if (since.isEqual(thisMonth) || since.isAfter(thisMonth)) {
            //             return monthlyAmounts.getOrDefault(thisMonth, 0);
            //         } else {
            //             return totalAmount;
            //         }
            //     }
                
            //     public boolean isExpired(LocalDateTime cutoffDate) {
            //         return lastUpdated.isBefore(cutoffDate);
            //     }
            // }
            
            private static class ItemLimit {
                private int totalAmount;
                private LocalDateTime lastUpdated;
                private Map<LocalDateTime, Integer> dailyAmounts = new HashMap<>();
                private Map<LocalDateTime, Integer> weeklyAmounts = new HashMap<>();
                private Map<LocalDateTime, Integer> monthlyAmounts = new HashMap<>();
                private List<TransactionEntry> transactions = new ArrayList<>();  // Liste de toutes les transactions avec timestamp
                
                public ItemLimit() {
                    this.totalAmount = 0;
                    this.lastUpdated = LocalDateTime.now();
                }

                public void addAmount(int amount, LocalDateTime timestamp) {
                    this.totalAmount += amount;
                    this.lastUpdated = timestamp;
                    
                    // Stocker chaque transaction avec son timestamp
                    transactions.add(new TransactionEntry(timestamp, amount));

                    // Mise à jour des périodes
                    // LocalDateTime now = LocalDateTime.now();
                    LocalDateTime today = timestamp.truncatedTo(java.time.temporal.ChronoUnit.DAYS);
                    LocalDateTime thisWeek = timestamp.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)).truncatedTo(java.time.temporal.ChronoUnit.DAYS);
                    LocalDateTime thisMonth = timestamp.withDayOfMonth(1).truncatedTo(java.time.temporal.ChronoUnit.DAYS);

                    // Ajouter aux compteurs par période
                    dailyAmounts.merge(today, amount, Integer::sum);
                    weeklyAmounts.merge(thisWeek, amount, Integer::sum);
                    monthlyAmounts.merge(thisMonth, amount, Integer::sum);
                }
                
                public int getAmountSince(LocalDateTime since) {
                    // Calculer directement à partir des transactions
                    return transactions.stream()
                        .filter(entry -> entry.timestamp.isEqual(since) || entry.timestamp.isAfter(since))
                        .mapToInt(entry -> entry.amount)
                        .sum();
                }
                
                // public LocalDateTime getLastTransactionTime() {
                //     if (transactions.isEmpty()) {
                //         return lastUpdated;
                //     }
                //     return transactions.stream()
                //         .map(entry -> entry.timestamp)
                //         .max(LocalDateTime::compareTo)
                //         .orElse(lastUpdated);
                // }
                public LocalDateTime getLastTransactionTime(String timeReference) {
                    if (transactions.isEmpty()) {
                        return lastUpdated;
                    }
                    
                    if ("first".equalsIgnoreCase(timeReference)) {
                        return transactions.stream()
                            .map(entry -> entry.timestamp)
                            .min(LocalDateTime::compareTo)
                            .orElse(lastUpdated);
                    } else {
                        return transactions.stream()
                            .map(entry -> entry.timestamp)
                            .max(LocalDateTime::compareTo)
                            .orElse(lastUpdated);
                    }
                }
                
                public boolean isExpired(LocalDateTime cutoffDate) {
                    return lastUpdated.isBefore(cutoffDate);
                }
                
                // Classe interne pour stocker une transaction
                private static class TransactionEntry {
                    private final LocalDateTime timestamp;
                    private final int amount;
                    
                    public TransactionEntry(LocalDateTime timestamp, int amount) {
                        this.timestamp = timestamp;
                        this.amount = amount;
                    }
                }
            }
        }
    }
}