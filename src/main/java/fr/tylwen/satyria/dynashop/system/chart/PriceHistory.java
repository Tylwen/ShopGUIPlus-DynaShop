package fr.tylwen.satyria.dynashop.system.chart;

import java.time.LocalDateTime;
// import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
// import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
// import java.util.UUID;
// import java.util.stream.Collectors;

import org.bukkit.configuration.serialization.ConfigurationSerializable;

// import fr.tylwen.satyria.dynashop.DynaShopPlugin;

public class PriceHistory implements ConfigurationSerializable {
    
    private final String shopId;
    private final String itemId;
    private final List<PriceDataPoint> dataPoints;
    private final int maxDataPoints;

    public PriceHistory(String shopId, String itemId) {
        // this(shopId, itemId, 50); // Par défaut, on garde 50 points de données
        this(shopId, itemId, 1000000); // Par défaut, on garde 1 000 000 points de données
        // this(shopId, itemId, 100); // Par défaut, on garde 100 points de données
    }

    public int getMaxDataPoints() {
        return maxDataPoints;
    }

    public PriceHistory(String shopId, String itemId, int maxDataPoints) {
        this.shopId = shopId;
        this.itemId = itemId;
        this.dataPoints = new ArrayList<>();
        this.maxDataPoints = maxDataPoints;
    }

    // public void addDataPoint(double openPrice, double closePrice, double highPrice, double lowPrice) {
    //     PriceDataPoint dataPoint = new PriceDataPoint(
    //         LocalDateTime.now(), openPrice, closePrice, highPrice, lowPrice);
        
    //     dataPoints.add(dataPoint);
        
    //     // Limiter la taille de l'historique
    //     if (dataPoints.size() > maxDataPoints) {
    //         dataPoints.remove(0);
    //     }
        
    //     // Sauvegarder l'historique dans la base de données
    //     DynaShopPlugin.getInstance().getDataManager().savePriceHistory(this);
    // }
    // public void addDataPoint(double openBuyPrice, double closeBuyPrice, double highBuyPrice, double lowBuyPrice,
    //                         double openSellPrice, double closeSellPrice, double highSellPrice, double lowSellPrice,
    //                         double volume) {
    //     PriceDataPoint dataPoint = new PriceDataPoint(
    //         LocalDateTime.now(), 
    //         openBuyPrice, closeBuyPrice, highBuyPrice, lowBuyPrice,
    //         openSellPrice, closeSellPrice, highSellPrice, lowSellPrice,
    //         volume);
        
    //     dataPoints.add(dataPoint);
        
    //     // Limiter la taille de l'historique
    //     if (dataPoints.size() > maxDataPoints) {
    //         dataPoints.remove(0);
    //     }
        
    //     // Sauvegarder l'historique dans la base de données
    //     // DynaShopPlugin.getInstance().getDataManager().savePriceHistory(this);
    //     // DynaShopPlugin.getInstance().getDataManager().saveSinglePriceDataPoint(this.shopId, this.itemId, dataPoint);
    //     DynaShopPlugin.getInstance().getStorageManager().savePriceDataPoint(this.shopId, this.itemId, dataPoint, 15);
    // }

    // public void addDataPoint(double openBuyPrice, double closeBuyPrice, double highBuyPrice, double lowBuyPrice,
    //                         double openSellPrice, double closeSellPrice, double highSellPrice, double lowSellPrice) {
    //     addDataPoint(openBuyPrice, closeBuyPrice, highBuyPrice, lowBuyPrice,
    //                 openSellPrice, closeSellPrice, highSellPrice, lowSellPrice, 0.0);
    // }

    // // Méthode de compatibilité pour l'ancien format
    // public void addDataPoint(double openPrice, double closePrice, double highPrice, double lowPrice) {
    //     // On suppose que c'est un prix d'achat par défaut
    //     addDataPoint(openPrice, closePrice, highPrice, lowPrice, 0, 0, 0, 0);
    // }

    public void addDataPoint(PriceDataPoint dataPoint) {
        dataPoints.add(dataPoint);
        
        // Limiter la taille de l'historique
        if (dataPoints.size() > maxDataPoints) {
            dataPoints.remove(0);
        }
        
        // // Sauvegarder l'historique dans la base de données
        // DynaShopPlugin.getInstance().getStorageManager().savePriceHistory(this);
    }

    public void setDataPoints(List<PriceDataPoint> newDataPoints) {
        this.dataPoints.clear();
        if (newDataPoints != null) {
            this.dataPoints.addAll(newDataPoints);
        }
    }

    public void updateDataPoint(int index, PriceDataPoint dataPoint) {
        if (index >= 0 && index < dataPoints.size()) {
            dataPoints.set(index, dataPoint);
        } else {
            throw new IndexOutOfBoundsException("Index out of bounds for data points list.");
        }
    }
    
    public List<PriceDataPoint> getDataPoints() {
        return new ArrayList<>(dataPoints);
    }

    public PriceDataPoint getLastPoint() {
        if (dataPoints.isEmpty()) {
            // return null; // ou vous pouvez lancer une exception si vous préférez
            throw new IllegalStateException("No data points available in the price history.");
        }
        return dataPoints.get(dataPoints.size() - 1);
    }
    
    public String getShopId() {
        return shopId;
    }
    
    public String getItemId() {
        return itemId;
    }
    
    public String getKey() {
        return shopId + ":" + itemId;
    }

    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> result = new HashMap<>();
        result.put("shopId", shopId);
        result.put("itemId", itemId);
        result.put("dataPoints", dataPoints);
        return result;
    }
    
    public static PriceHistory deserialize(Map<String, Object> map) {
        String shopId = (String) map.get("shopId");
        String itemId = (String) map.get("itemId");
        PriceHistory history = new PriceHistory(shopId, itemId);
        
        @SuppressWarnings("unchecked")
        List<PriceDataPoint> dataPoints = (List<PriceDataPoint>) map.get("dataPoints");
        if (dataPoints != null) {
            history.dataPoints.addAll(dataPoints);
        }
        
        return history;
    }
    
    public static class PriceDataPoint implements ConfigurationSerializable {
        private final LocalDateTime timestamp;
        // Prix d'achat
        private final double openBuyPrice, closeBuyPrice, highBuyPrice, lowBuyPrice;
        // Prix de vente
        private final double openSellPrice, closeSellPrice, highSellPrice, lowSellPrice;
        // Volume
        private final double volume; 
        
        public PriceDataPoint(LocalDateTime timestamp,
                            double openBuy, double closeBuy, double highBuy, double lowBuy,
                            double openSell, double closeSell, double highSell, double lowSell,
                            double volume) {
            this.timestamp = timestamp;
            this.openBuyPrice = openBuy;
            this.closeBuyPrice = closeBuy;
            this.highBuyPrice = highBuy;
            this.lowBuyPrice = lowBuy;
            this.openSellPrice = openSell;
            this.closeSellPrice = closeSell;
            this.highSellPrice = highSell;
            this.lowSellPrice = lowSell;
            this.volume = volume;
        }

        public LocalDateTime getTimestamp() { return timestamp; }

        // Getters pour les nouveaux champs
        public double getOpenBuyPrice() { return openBuyPrice; }
        public double getCloseBuyPrice() { return closeBuyPrice; }
        public double getHighBuyPrice() { return highBuyPrice; }
        public double getLowBuyPrice() { return lowBuyPrice; }
        public double getOpenSellPrice() { return openSellPrice; }
        public double getCloseSellPrice() { return closeSellPrice; }
        public double getHighSellPrice() { return highSellPrice; }
        public double getLowSellPrice() { return lowSellPrice; }
        
        // Pour la compatibilité avec l'ancien code
        public double getOpenPrice() { return openBuyPrice > 0 ? openBuyPrice : openSellPrice; }
        public double getClosePrice() { return closeBuyPrice > 0 ? closeBuyPrice : closeSellPrice; }
        public double getHighPrice() { return highBuyPrice > 0 ? highBuyPrice : highSellPrice; }
        public double getLowPrice() { return lowBuyPrice > 0 ? lowBuyPrice : lowSellPrice; }

        public double getVolume() { return volume; }
        
        @Override
        public Map<String, Object> serialize() {
            Map<String, Object> result = new HashMap<>();
            result.put("timestamp", timestamp.toString());
            // Prix d'achat
            result.put("openBuyPrice", openBuyPrice);
            result.put("closeBuyPrice", closeBuyPrice);
            result.put("highBuyPrice", highBuyPrice);
            result.put("lowBuyPrice", lowBuyPrice);
            // Prix de vente
            result.put("openSellPrice", openSellPrice);
            result.put("closeSellPrice", closeSellPrice);
            result.put("highSellPrice", highSellPrice);
            result.put("lowSellPrice", lowSellPrice);
            // Volume
            result.put("volume", volume);
            return result;
        }
        
        public static PriceDataPoint deserialize(Map<String, Object> map) {
            return new PriceDataPoint(
                LocalDateTime.parse((String) map.get("timestamp")),
                // Prix d'achat (avec fallback sur l'ancien format)
                map.containsKey("openBuyPrice") ? (double) map.get("openBuyPrice") : (double) map.get("openPrice"),
                map.containsKey("closeBuyPrice") ? (double) map.get("closeBuyPrice") : (double) map.get("closePrice"),
                map.containsKey("highBuyPrice") ? (double) map.get("highBuyPrice") : (double) map.get("highPrice"),
                map.containsKey("lowBuyPrice") ? (double) map.get("lowBuyPrice") : (double) map.get("lowPrice"),
                // Prix de vente (avec valeurs par défaut si non présents)
                map.containsKey("openSellPrice") ? (double) map.get("openSellPrice") : 0.0,
                map.containsKey("closeSellPrice") ? (double) map.get("closeSellPrice") : 0.0,
                map.containsKey("highSellPrice") ? (double) map.get("highSellPrice") : 0.0,
                map.containsKey("lowSellPrice") ? (double) map.get("lowSellPrice") : 0.0,
                map.containsKey("volume") ? (double) map.get("volume") : 0.0
            );
        }
    }

    // public void recordPriceForHistory(String shopId, String itemId, double price) {
    //     // Récupérer l'historique existant
    //     PriceHistory history = DynaShopPlugin.getInstance().getDataManager().getPriceHistory(shopId, itemId);
        
    //     // Si aucun point de données n'existe encore, utiliser le prix actuel comme référence
    //     if (history.getDataPoints().isEmpty()) {
    //         history.addDataPoint(price, price, price, price);
    //         return;
    //     }
        
    //     // Récupérer le dernier point de données
    //     PriceDataPoint lastPoint = history.getDataPoints().get(history.getDataPoints().size() - 1);
        
    //     // Si le dernier point date de moins d'une heure, mettre à jour ce point
    //     LocalDateTime now = LocalDateTime.now();
    //     if (lastPoint.getTimestamp().plusHours(1).isAfter(now)) {
    //         double open = lastPoint.getOpenPrice();
    //         double close = price;
    //         double high = Math.max(lastPoint.getHighPrice(), price);
    //         double low = Math.min(lastPoint.getLowPrice(), price);
            
    //         // Supprimer le dernier point et ajouter le point mis à jour
    //         history.getDataPoints().remove(history.getDataPoints().size() - 1);
    //         history.addDataPoint(open, close, high, low);
    //     } else {
    //         // Sinon, ajouter un nouveau point
    //         history.addDataPoint(lastPoint.getClosePrice(), price, Math.max(lastPoint.getClosePrice(), price), Math.min(lastPoint.getClosePrice(), price));
    //     }
    // }

    // /**
    //  * Agrège les données d'historique selon un intervalle spécifié
    //  * @param intervalMinutes Intervalle en minutes
    //  * @param startTime Date de début (optionnelle)
    //  * @param maxPoints Nombre maximum de points à retourner
    //  * @return Liste de points de données agrégés
    //  */
    // public List<PriceDataPoint> getAggregatedData(int intervalMinutes, LocalDateTime startTime, int maxPoints) {
    //     List<PriceDataPoint> points = new ArrayList<>(dataPoints);
        
    //     // 1. Filtrer par date de début si spécifiée
    //     if (startTime != null) {
    //         points = points.stream()
    //             .filter(p -> p.getTimestamp().isAfter(startTime))
    //             .collect(Collectors.toList());
    //     }
        
    //     // 2. Agréger par intervalle
    //     Map<String, List<PriceDataPoint>> groupedPoints = new HashMap<>();
    //     for (PriceDataPoint point : points) {
    //         // Tronquer le timestamp à l'intervalle spécifié
    //         LocalDateTime truncated = truncateToInterval(point.getTimestamp(), intervalMinutes);
    //         String key = truncated.toString();
            
    //         // Regrouper les points par intervalle
    //         groupedPoints.computeIfAbsent(key, k -> new ArrayList<>()).add(point);
    //     }
        
    //     // 3. Calculer les valeurs agrégées pour chaque groupe
    //     List<PriceDataPoint> aggregatedPoints = new ArrayList<>();
    //     for (Map.Entry<String, List<PriceDataPoint>> entry : groupedPoints.entrySet()) {
    //         List<PriceDataPoint> group = entry.getValue();
    //         LocalDateTime timestamp = LocalDateTime.parse(entry.getKey());
            
    //         // Ignorer les groupes vides
    //         if (group.isEmpty()) continue;
            
    //         // 3.1 Calculer les valeurs pour le prix d'achat
    //         double openBuy = group.get(0).getOpenBuyPrice();
    //         double closeBuy = group.get(group.size() - 1).getCloseBuyPrice();
    //         double highBuy = group.stream().mapToDouble(PriceDataPoint::getHighBuyPrice).max().orElse(0);
    //         double lowBuy = group.stream().mapToDouble(PriceDataPoint::getLowBuyPrice).filter(p -> p > 0).min().orElse(0);
            
    //         // 3.2 Calculer les valeurs pour le prix de vente
    //         double openSell = group.get(0).getOpenSellPrice();
    //         double closeSell = group.get(group.size() - 1).getCloseSellPrice();
    //         double highSell = group.stream().mapToDouble(PriceDataPoint::getHighSellPrice).max().orElse(0);
    //         double lowSell = group.stream().mapToDouble(PriceDataPoint::getLowSellPrice).filter(p -> p > 0).min().orElse(0);
            
    //         // 3.3 Calculer le volume total
    //         double volume = group.stream().mapToDouble(PriceDataPoint::getVolume).sum();
            
    //         // 3.4 Créer le point agrégé
    //         PriceDataPoint aggregatedPoint = new PriceDataPoint(
    //             timestamp, 
    //             openBuy, closeBuy, highBuy, lowBuy, 
    //             openSell, closeSell, highSell, lowSell, 
    //             volume
    //         );
            
    //         aggregatedPoints.add(aggregatedPoint);
    //     }
        
    //     // 4. Trier par ordre chronologique
    //     aggregatedPoints.sort(Comparator.comparing(PriceDataPoint::getTimestamp));
        
    //     // 5. Limiter le nombre de points
    //     if (aggregatedPoints.size() > maxPoints) {
    //         // Prendre les N derniers points (les plus récents)
    //         aggregatedPoints = aggregatedPoints.subList(
    //             Math.max(0, aggregatedPoints.size() - maxPoints), 
    //             aggregatedPoints.size()
    //         );
    //     }
        
    //     return aggregatedPoints;
    // }

    // /**
    //  * Tronque un timestamp à un intervalle de minutes spécifié
    //  */
    // private LocalDateTime truncateToInterval(LocalDateTime dateTime, int intervalMinutes) {
    //     int totalMinutes = dateTime.getHour() * 60 + dateTime.getMinute();
    //     int truncatedMinutes = (totalMinutes / intervalMinutes) * intervalMinutes;
        
    //     return dateTime
    //         .withHour(truncatedMinutes / 60)
    //         .withMinute(truncatedMinutes % 60)
    //         .withSecond(0)
    //         .withNano(0);
    // }
}