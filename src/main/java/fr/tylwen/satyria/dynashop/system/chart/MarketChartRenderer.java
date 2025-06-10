package fr.tylwen.satyria.dynashop.system.chart;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapPalette;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataType;

import fr.tylwen.satyria.dynashop.DynaShopPlugin;
import fr.tylwen.satyria.dynashop.system.chart.PriceHistory.PriceDataPoint;

import java.awt.Color;
import java.awt.Font;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MarketChartRenderer extends MapRenderer {

    private static final int MAP_WIDTH = 128;
    private static final int MAP_HEIGHT = 128;
    private static final int MARGIN = 10;
    private static final int CANDLE_WIDTH = 3;
    private static final int CANDLE_SPACING = 1;

    private final DynaShopPlugin plugin;
    private final String shopId;
    private final String itemId;
    private BufferedImage cachedImage;
    private long lastUpdateTime;
    private static final long UPDATE_INTERVAL = 5 * 60 * 1000; // 5 minutes
    
    // Couleurs pour les chandeliers
    private final Color bullishColor = new Color(138, 194, 38); // Vert
    private final Color bearishColor = new Color(192, 57, 56); // Rouge
    private final Color volumeColor = new Color(100, 100, 180); // Bleu-gris
    private final Color gridColor = new Color(220, 220, 220); // Gris clair
    private final Color textColor = Color.BLACK;
    
    // Gestion de la granularité
    private int granularityMinutes = 15; // Par défaut 15 minutes par chandelle
    private static final int[] GRANULARITIES = {5, 15, 60, 180, 360, 720, 1440}; // en minutes
    private int granularityIndex = 1; // index courant dans la liste (par défaut 15min)
    
    private static final Map<String, Integer> mapIdCache = new HashMap<>();
    
    public MarketChartRenderer(DynaShopPlugin plugin, String shopId, String itemId) {
        this.plugin = plugin;
        this.shopId = shopId;
        this.itemId = itemId;
        this.lastUpdateTime = 0;
    }
    
    public void setGranularityMinutes(int minutes) {
        this.granularityMinutes = Math.max(1, minutes);
    }

    @Override
    public void render(MapView map, MapCanvas canvas, Player player) {
        // Vérifier si nous devons mettre à jour l'image
        long currentTime = System.currentTimeMillis();
        if (cachedImage == null || currentTime - lastUpdateTime > UPDATE_INTERVAL) {
            cachedImage = renderChart(player);
            lastUpdateTime = currentTime;
        }
        
        // Dessiner l'image sur le canvas
        if (cachedImage != null) {
            for (int x = 0; x < MAP_WIDTH; x++) {
                for (int y = 0; y < MAP_HEIGHT; y++) {
                    int argb = cachedImage.getRGB(x, y);
                    int alpha = (argb >> 24) & 0xff;
                    if (alpha < 128) continue; // Ne dessine pas ce pixel (transparence)
                    Color color = new Color(argb, true);
                    canvas.setPixel(x, y, MapPalette.matchColor(color.getRed(), color.getGreen(), color.getBlue()));
                }
            }
        }
    }
    
    private BufferedImage renderChart(Player player) {
        BufferedImage image = new BufferedImage(MAP_WIDTH, MAP_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = image.createGraphics();
        
        // Activer l'antialiasing
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // Fond blanc
        g.setColor(new Color(240, 240, 240));
        g.fillRect(0, 0, MAP_WIDTH, MAP_HEIGHT);
        
        // Récupérer les données historiques
        PriceHistory history = plugin.getDataManager().getPriceHistory(shopId, itemId);
        List<PriceDataPoint> dataPoints = history.getDataPoints();
        
        if (dataPoints.isEmpty()) {
            // Pas de données, afficher un message
            g.setColor(Color.BLACK);
            g.setFont(new Font("SansSerif", Font.BOLD, 12));
            g.drawString("Pas de données", 30, MAP_HEIGHT / 2);
            g.dispose();
            return image;
        }
        
        // Grouper les données selon la granularité
        List<CandleData> candles = groupDataByGranularity(dataPoints);
        
        // Trouver les valeurs min et max pour l'échelle
        double minPrice = Double.MAX_VALUE;
        double maxPrice = Double.MIN_VALUE;
        double maxVolume = 0;
        
        for (CandleData candle : candles) {
            minPrice = Math.min(minPrice, candle.low);
            maxPrice = Math.max(maxPrice, candle.high);
            maxVolume = Math.max(maxVolume, candle.volume);
        }
        
        // Ajouter une marge à l'échelle
        double priceDiff = maxPrice - minPrice;
        minPrice = Math.max(0, minPrice - priceDiff * 0.1);
        maxPrice = maxPrice + priceDiff * 0.1;
        
        // Dessiner la grille
        drawGrid(g);
        
        // Dessiner les prix sur l'axe Y
        drawYAxis(g, minPrice, maxPrice);
        
        // Définir la zone pour les chandeliers et le volume
        int volumeAreaHeight = 24;
        int priceChartHeight = MAP_HEIGHT - 2 * MARGIN - volumeAreaHeight;
        int volumeBaseY = MAP_HEIGHT - MARGIN;
        
        // Dessiner les chandeliers
        int maxCandlesVisible = (MAP_WIDTH - 2 * MARGIN) / (CANDLE_WIDTH + CANDLE_SPACING);
        int startIndex = Math.max(0, candles.size() - maxCandlesVisible);
        
        for (int i = startIndex; i < candles.size(); i++) {
            CandleData candle = candles.get(i);
            int x = MARGIN + (i - startIndex) * (CANDLE_WIDTH + CANDLE_SPACING);
            
            // Dessiner le volume
            if (maxVolume > 0) {
                int volumeHeight = (int)(volumeAreaHeight * candle.volume / maxVolume);
                g.setColor(volumeColor);
                g.fillRect(x, volumeBaseY - volumeHeight, CANDLE_WIDTH, volumeHeight);
            }
            
            // Convertir les prix en coordonnées y
            double priceRange = maxPrice - minPrice;
            int openY = MARGIN + (int)(priceChartHeight * (1 - (candle.open - minPrice) / priceRange));
            int closeY = MARGIN + (int)(priceChartHeight * (1 - (candle.close - minPrice) / priceRange));
            int highY = MARGIN + (int)(priceChartHeight * (1 - (candle.high - minPrice) / priceRange));
            int lowY = MARGIN + (int)(priceChartHeight * (1 - (candle.low - minPrice) / priceRange));
            
            // Dessiner la mèche
            g.setColor(Color.BLACK);
            g.drawLine(x + CANDLE_WIDTH / 2, highY, x + CANDLE_WIDTH / 2, lowY);
            
            // Dessiner le corps
            boolean isBullish = candle.close >= candle.open;
            g.setColor(isBullish ? bullishColor : bearishColor);
            
            int bodyTop = Math.min(openY, closeY);
            int bodyHeight = Math.max(1, Math.abs(closeY - openY));
            g.fillRect(x, bodyTop, CANDLE_WIDTH, bodyHeight);
        }
        
        // Dessiner le nom de l'item et le prix actuel
        String itemName = plugin.getShopConfigManager().getItemName(player, shopId, itemId);
        if (itemName == null) itemName = itemId;
        
        g.setColor(Color.BLACK);
        g.setFont(new Font("SansSerif", Font.BOLD, 10));
        g.drawString(itemName, MARGIN, MARGIN - 2);
        
        // Afficher le prix actuel
        if (!candles.isEmpty()) {
            CandleData lastCandle = candles.get(candles.size() - 1);
            String priceText = String.format("%.1f", lastCandle.close);
            g.drawString(priceText, MAP_WIDTH - MARGIN - g.getFontMetrics().stringWidth(priceText) - 2, MARGIN + 10);
        }
        
        g.dispose();
        return image;
    }
    
    private void drawGrid(java.awt.Graphics2D g) {
        g.setColor(gridColor);
        
        // Lignes horizontales
        for (int i = 0; i <= 4; i++) {
            int y = MARGIN + i * (MAP_HEIGHT - 2 * MARGIN - 24) / 4;
            g.drawLine(MARGIN, y, MAP_WIDTH - MARGIN, y);
        }
        
        // Ligne séparatrice pour la zone de volume
        g.drawLine(MARGIN, MAP_HEIGHT - MARGIN - 24, MAP_WIDTH - MARGIN, MAP_HEIGHT - MARGIN - 24);
    }
    
    private void drawYAxis(java.awt.Graphics2D g, double minPrice, double maxPrice) {
        g.setColor(textColor);
        g.setFont(new Font("SansSerif", Font.PLAIN, 8));
        
        for (int i = 0; i <= 4; i++) {
            double price = minPrice + (maxPrice - minPrice) * (4 - i) / 4;
            int y = MARGIN + i * (MAP_HEIGHT - 2 * MARGIN - 24) / 4;
            g.drawString(String.format("%.1f", price), 2, y + 4);
        }
    }
    
    private List<CandleData> groupDataByGranularity(List<PriceDataPoint> dataPoints) {
        List<CandleData> grouped = new ArrayList<>();
        
        if (dataPoints.isEmpty()) {
            return grouped;
        }
        
        // Grouper les points par intervalle de temps
        LocalDateTime bucketStart = dataPoints.get(0).getTimestamp().withSecond(0).withNano(0);
        LocalDateTime bucketEnd = bucketStart.plusMinutes(granularityMinutes);
        
        double openBuy = 0, closeBuy = 0, highBuy = Double.MIN_VALUE, lowBuy = Double.MAX_VALUE;
        double volume = 0;
        boolean first = true;
        
        for (PriceDataPoint p : dataPoints) {
            while (p.getTimestamp().isAfter(bucketEnd)) {
                if (!first) {
                    grouped.add(new CandleData(openBuy, highBuy, lowBuy, closeBuy, volume));
                }
                // Passe au bucket suivant
                bucketStart = bucketEnd;
                bucketEnd = bucketStart.plusMinutes(granularityMinutes);
                openBuy = 0; closeBuy = 0; highBuy = Double.MIN_VALUE; lowBuy = Double.MAX_VALUE;
                volume = 0;
                first = true;
            }
            
            if (first) {
                openBuy = p.getOpenBuyPrice();
                first = false;
            }
            
            closeBuy = p.getCloseBuyPrice();
            highBuy = Math.max(highBuy, p.getHighBuyPrice());
            lowBuy = Math.min(lowBuy, p.getLowBuyPrice());
            volume += p.getVolume();
        }
        
        // Ajouter le dernier groupe
        if (!first) {
            grouped.add(new CandleData(openBuy, highBuy, lowBuy, closeBuy, volume));
        }
        
        return grouped;
    }
    
    /**
     * Crée et retourne une carte avec le graphique boursier
     */
    public ItemStack createMapItem(Player player) {
        MapView view = Bukkit.createMap(player.getWorld());
        
        // Supprimer tous les renderers par défaut et ajouter le nôtre
        view.getRenderers().forEach(view::removeRenderer);
        view.addRenderer(this);
        
        // Créer l'item de la carte
        ItemStack mapItem = new ItemStack(Material.FILLED_MAP);
        MapMeta mapMeta = (MapMeta) mapItem.getItemMeta();
        mapMeta.setMapView(view);
        
        // Configurer le nom et la description
        String itemName = plugin.getShopConfigManager().getItemName(player, shopId, itemId);
        if (itemName == null) itemName = itemId;
        
        mapMeta.setDisplayName("§6Marché: §f" + itemName);
        
        // Ajout de la granularité dans le lore
        String granularityLabel = getGranularityLabel();
        
        List<String> lore = List.of(
            "§7Graphique d'évolution des prix",
            "§7Item: §f" + itemName,
            "§7Shop: §f" + shopId,
            "§7Granularité: §f" + granularityLabel,
            "§7Mise à jour: §f" + UPDATE_INTERVAL / (60 * 1000) + " minutes"
        );
        mapMeta.setLore(lore);
        
        // Stocker les métadonnées pour identifier cette carte
        NamespacedKey shopIdKey = new NamespacedKey(plugin, "chart_shop_id");
        NamespacedKey itemIdKey = new NamespacedKey(plugin, "chart_item_id");
        mapMeta.getPersistentDataContainer().set(shopIdKey, PersistentDataType.STRING, shopId);
        mapMeta.getPersistentDataContainer().set(itemIdKey, PersistentDataType.STRING, itemId);
        
        mapItem.setItemMeta(mapMeta);
        
        // Sauvegarder l'ID de la carte pour les futures références
        mapIdCache.put(shopId + ":" + itemId, view.getId());
        
        return mapItem;
    }
    
    /**
     * Récupère une carte existante ou en crée une nouvelle
     */
    public static ItemStack getOrCreateMapItem(DynaShopPlugin plugin, Player player, String shopId, String itemId) {
        String key = shopId + ":" + itemId;
        Integer mapId = mapIdCache.get(key);
        
        if (mapId != null) {
            MapView view = Bukkit.getMap(mapId);
            if (view != null) {
                // // Retire les anciens renderers et ajoute le renderer actuel
                // view.getRenderers().forEach(view::removeRenderer);
                // MarketChartRenderer renderer = new MarketChartRenderer(plugin, shopId, itemId);
                // view.addRenderer(renderer);
                
                // ItemStack mapItem = new ItemStack(Material.FILLED_MAP);
                // MapMeta mapMeta = (MapMeta) mapItem.getItemMeta();
                // mapMeta.setMapView(view);
                
                // // Ajoute displayName, lore et tags NBT
                // String itemName = plugin.getShopConfigManager().getItemName(player, shopId, itemId);
                // if (itemName == null) itemName = itemId;
                // mapMeta.setDisplayName("§6Marché: §f" + itemName);
                
                // String granularityLabel = renderer.getGranularityLabel();
                
                // List<String> lore = List.of(
                //     "§7Graphique d'évolution des prix",
                //     "§7Item: §f" + itemName,
                //     "§7Shop: §f" + shopId,
                //     "§7Granularité: §f" + granularityLabel,
                //     "§7Mise à jour: §f" + UPDATE_INTERVAL / (60 * 1000) + " minutes"
                // );
                // mapMeta.setLore(lore);
                
                // NamespacedKey shopIdKey = new NamespacedKey(plugin, "chart_shop_id");
                // NamespacedKey itemIdKey = new NamespacedKey(plugin, "chart_item_id");
                // mapMeta.getPersistentDataContainer().set(shopIdKey, PersistentDataType.STRING, shopId);
                // mapMeta.getPersistentDataContainer().set(itemIdKey, PersistentDataType.STRING, itemId);
                
                // mapItem.setItemMeta(mapMeta);
                
                ItemStack mapItem = new ItemStack(Material.FILLED_MAP);
                // ItemStack mapItem = new ItemStack(Material.MAP);
                MapMeta mapMeta = (MapMeta) mapItem.getItemMeta();
                mapMeta.setMapView(view);
                mapItem.setItemMeta(mapMeta);
                
                return mapItem;
            }
        }
        
        // Créer une nouvelle carte
        return new MarketChartRenderer(plugin, shopId, itemId).createMapItem(player);
    }
    
    private String getGranularityLabel() {
        if (granularityMinutes < 60) {
            return granularityMinutes + " min";
        } else if (granularityMinutes % 60 == 0 && granularityMinutes < 1440) {
            return (granularityMinutes / 60) + " h";
        } else if (granularityMinutes == 1440) {
            return "1 jour";
        } else {
            return granularityMinutes + " min";
        }
    }
    
    public void updateMapItemLore(ItemStack mapItem, Player player) {
        if (mapItem == null || !(mapItem.getItemMeta() instanceof MapMeta mapMeta)) return;
        
        String itemName = plugin.getShopConfigManager().getItemName(player, shopId, itemId);
        if (itemName == null) itemName = itemId;
        
        String granularityLabel = getGranularityLabel();
        
        List<String> lore = List.of(
            "§7Graphique d'évolution des prix",
            "§7Item: §f" + itemName,
            "§7Shop: §f" + shopId,
            "§7Granularité: §f" + granularityLabel,
            "§7Mise à jour: §f" + UPDATE_INTERVAL / (60 * 1000) + " minutes"
        );
        mapMeta.setLore(lore);
        mapItem.setItemMeta(mapMeta);
    }
    
    public void zoomIn() {
        if (granularityIndex > 0) {
            granularityIndex--;
            cachedImage = null; // force le refresh
            setGranularityMinutes(GRANULARITIES[granularityIndex]);
        }
    }
    
    public void zoomOut() {
        if (granularityIndex < GRANULARITIES.length - 1) {
            granularityIndex++;
            cachedImage = null; // force le refresh
            setGranularityMinutes(GRANULARITIES[granularityIndex]);
        }
    }
    
    public static void clearMapCache() {
        mapIdCache.clear();
    }
    
    /**
     * Classe pour représenter les données d'un chandelier
     */
    public static class CandleData {
        public final double open;
        public final double high;
        public final double low; 
        public final double close;
        public final double volume;
        
        public CandleData(double open, double high, double low, double close, double volume) {
            this.open = open;
            this.high = high;
            this.low = low;
            this.close = close;
            this.volume = volume;
        }
    }
}