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

    public void addDataPoint(double openPrice, double closePrice, double highPrice, double lowPrice) {
        PriceDataPoint dataPoint = new PriceDataPoint(
            LocalDateTime.now(), openPrice, closePrice, highPrice, lowPrice);
        
        dataPoints.add(dataPoint);
        
        // Limiter la taille de l'historique
        if (dataPoints.size() > maxDataPoints) {
            dataPoints.remove(0);
        }
        
        // Sauvegarder l'historique dans la base de données
        DynaShopPlugin.getInstance().getDataManager().savePriceHistory(this);
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
        private final double openPrice;
        private final double closePrice;
        private final double highPrice;
        private final double lowPrice;
        
        public PriceDataPoint(LocalDateTime timestamp, double openPrice, double closePrice, 
                double highPrice, double lowPrice) {
            this.timestamp = timestamp;
            this.openPrice = openPrice;
            this.closePrice = closePrice;
            this.highPrice = highPrice;
            this.lowPrice = lowPrice;
        }
        
        public LocalDateTime getTimestamp() {
            return timestamp;
        }
        
        public double getOpenPrice() {
            return openPrice;
        }
        
        public double getClosePrice() {
            return closePrice;
        }
        
        public double getHighPrice() {
            return highPrice;
        }
        
        public double getLowPrice() {
            return lowPrice;
        }
        
        @Override
        public Map<String, Object> serialize() {
            Map<String, Object> result = new HashMap<>();
            result.put("timestamp", timestamp.toString());
            result.put("openPrice", openPrice);
            result.put("closePrice", closePrice);
            result.put("highPrice", highPrice);
            result.put("lowPrice", lowPrice);
            return result;
        }
        
        public static PriceDataPoint deserialize(Map<String, Object> map) {
            return new PriceDataPoint(
                LocalDateTime.parse((String) map.get("timestamp")),
                (double) map.get("openPrice"),
                (double) map.get("closePrice"),
                (double) map.get("highPrice"),
                (double) map.get("lowPrice")
            );
        }
    }

    public void recordPriceForHistory(String shopId, String itemId, double price) {
        // Récupérer l'historique existant
        PriceHistory history = DynaShopPlugin.getInstance().getDataManager().getPriceHistory(shopId, itemId);
        
        // Si aucun point de données n'existe encore, utiliser le prix actuel comme référence
        if (history.getDataPoints().isEmpty()) {
            history.addDataPoint(price, price, price, price);
            return;
        }
        
        // Récupérer le dernier point de données
        PriceDataPoint lastPoint = history.getDataPoints().get(history.getDataPoints().size() - 1);
        
        // Si le dernier point date de moins d'une heure, mettre à jour ce point
        LocalDateTime now = LocalDateTime.now();
        if (lastPoint.getTimestamp().plusHours(1).isAfter(now)) {
            double open = lastPoint.getOpenPrice();
            double close = price;
            double high = Math.max(lastPoint.getHighPrice(), price);
            double low = Math.min(lastPoint.getLowPrice(), price);
            
            // Supprimer le dernier point et ajouter le point mis à jour
            history.getDataPoints().remove(history.getDataPoints().size() - 1);
            history.addDataPoint(open, close, high, low);
        } else {
            // Sinon, ajouter un nouveau point
            history.addDataPoint(lastPoint.getClosePrice(), price, Math.max(lastPoint.getClosePrice(), price), Math.min(lastPoint.getClosePrice(), price));
        }
    }
}