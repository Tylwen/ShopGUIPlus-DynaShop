package fr.tylwen.satyria.dynashop.system.chart;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.configuration.serialization.ConfigurationSerializable;

import fr.tylwen.satyria.dynashop.DynaShopPlugin;

public class PriceHistory implements ConfigurationSerializable {
    
    private final String shopId;
    private final String itemId;
    private final List<PriceDataPoint> dataPoints;
    private final int maxDataPoints;

    public PriceHistory(String shopId, String itemId) {
        this(shopId, itemId, 50); // Par défaut, on garde 50 points de données
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
    public void addDataPoint(double openBuyPrice, double closeBuyPrice, double highBuyPrice, double lowBuyPrice,
                            double openSellPrice, double closeSellPrice, double highSellPrice, double lowSellPrice) {
        PriceDataPoint dataPoint = new PriceDataPoint(
            LocalDateTime.now(), 
            openBuyPrice, closeBuyPrice, highBuyPrice, lowBuyPrice,
            openSellPrice, closeSellPrice, highSellPrice, lowSellPrice);
        
        dataPoints.add(dataPoint);
        
        // Limiter la taille de l'historique
        if (dataPoints.size() > maxDataPoints) {
            dataPoints.remove(0);
        }
        
        // Sauvegarder l'historique dans la base de données
        DynaShopPlugin.getInstance().getDataManager().savePriceHistory(this);
    }

    // Méthode de compatibilité pour l'ancien format
    public void addDataPoint(double openPrice, double closePrice, double highPrice, double lowPrice) {
        // On suppose que c'est un prix d'achat par défaut
        addDataPoint(openPrice, closePrice, highPrice, lowPrice, 0, 0, 0, 0);
    }
    
    public List<PriceDataPoint> getDataPoints() {
        return new ArrayList<>(dataPoints);
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
        
        public PriceDataPoint(LocalDateTime timestamp,
                            double openBuy, double closeBuy, double highBuy, double lowBuy,
                            double openSell, double closeSell, double highSell, double lowSell) {
            this.timestamp = timestamp;
            this.openBuyPrice = openBuy;
            this.closeBuyPrice = closeBuy;
            this.highBuyPrice = highBuy;
            this.lowBuyPrice = lowBuy;
            this.openSellPrice = openSell;
            this.closeSellPrice = closeSell;
            this.highSellPrice = highSell;
            this.lowSellPrice = lowSell;
        }

        public LocalDateTime getTimestamp() {
            return timestamp;
        }
        
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
                map.containsKey("lowSellPrice") ? (double) map.get("lowSellPrice") : 0.0
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
}