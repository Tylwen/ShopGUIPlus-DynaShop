package fr.tylwen.satyria.dynashop.system.chart;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapPalette;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

import fr.tylwen.satyria.dynashop.DynaShopPlugin;
import fr.tylwen.satyria.dynashop.system.chart.PriceHistory.PriceDataPoint;

public class MarketChartRenderer extends MapRenderer {
    
    private static final int MAP_WIDTH = 128;
    private static final int MAP_HEIGHT = 128;
    private static final int MARGIN = 10;
    private static final int CANDLE_WIDTH = 3;
    private static final int CANDLE_SPACING = 2;
    
    private final DynaShopPlugin plugin;
    private final String shopId;
    private final String itemId;
    private BufferedImage cachedImage;
    private long lastUpdateTime;
    private static final long UPDATE_INTERVAL = 5 * 60 * 1000; // 5 minutes
    
    private static final Map<String, Integer> mapIdCache = new HashMap<>();
    
    public MarketChartRenderer(DynaShopPlugin plugin, String shopId, String itemId) {
        this.plugin = plugin;
        this.shopId = shopId;
        this.itemId = itemId;
        this.lastUpdateTime = 0;
    }

    @Override
    public void render(MapView map, MapCanvas canvas, Player player) {
        // Vérifier si nous devons mettre à jour l'image
        long currentTime = System.currentTimeMillis();
        if (cachedImage == null || currentTime - lastUpdateTime > UPDATE_INTERVAL) {
            cachedImage = renderChart();
            lastUpdateTime = currentTime;
        }
        
        // Dessiner l'image sur le canvas
        if (cachedImage != null) {
            for (int x = 0; x < MAP_WIDTH; x++) {
                for (int y = 0; y < MAP_HEIGHT; y++) {
                    if (x < cachedImage.getWidth() && y < cachedImage.getHeight()) {
                        Color color = new Color(cachedImage.getRGB(x, y));
                        canvas.setPixel(x, y, MapPalette.matchColor(color.getRed(), color.getGreen(), color.getBlue()));
                    }
                }
            }
        }
    }
    
    private BufferedImage renderChart() {
        BufferedImage image = new BufferedImage(MAP_WIDTH, MAP_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        
        // Fond blanc
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, MAP_WIDTH, MAP_HEIGHT);
        
        // Dessiner le cadre
        g.setColor(Color.BLACK);
        g.drawRect(MARGIN, MARGIN, MAP_WIDTH - 2 * MARGIN, MAP_HEIGHT - 2 * MARGIN);
        
        // Récupérer les données historiques
        PriceHistory history = plugin.getDataManager().getPriceHistory(shopId, itemId);
        if (history == null || history.getDataPoints().isEmpty()) {
            // Pas de données, afficher un message
            g.drawString("Pas de données disponibles", 15, MAP_HEIGHT / 2);
            g.dispose();
            return image;
        }
        
        List<PriceDataPoint> dataPoints = history.getDataPoints();
        
        // Trouver les valeurs min et max pour l'échelle
        double minPrice = Double.MAX_VALUE;
        double maxPrice = Double.MIN_VALUE;
        for (PriceDataPoint point : dataPoints) {
            minPrice = Math.min(minPrice, point.getLowPrice());
            maxPrice = Math.max(maxPrice, point.getHighPrice());
        }
        
        // Ajouter une marge à l'échelle
        double priceDiff = maxPrice - minPrice;
        minPrice = Math.max(0, minPrice - priceDiff * 0.1);
        maxPrice = maxPrice + priceDiff * 0.1;
        
        // Calculer l'échelle
        double priceRange = maxPrice - minPrice;
        double yScale = (MAP_HEIGHT - 2 * MARGIN) / priceRange;
        
        // Nombre de chandeliers à afficher
        int maxCandles = (MAP_WIDTH - 2 * MARGIN) / (CANDLE_WIDTH + CANDLE_SPACING);
        int startIndex = Math.max(0, dataPoints.size() - maxCandles);
        
        // Dessiner les chandeliers
        for (int i = startIndex; i < dataPoints.size(); i++) {
            PriceDataPoint point = dataPoints.get(i);
            int x = MARGIN + (i - startIndex) * (CANDLE_WIDTH + CANDLE_SPACING);
            
            // Convertir les prix en coordonnées y
            int openY = MAP_HEIGHT - MARGIN - (int)((point.getOpenPrice() - minPrice) * yScale);
            int closeY = MAP_HEIGHT - MARGIN - (int)((point.getClosePrice() - minPrice) * yScale);
            int highY = MAP_HEIGHT - MARGIN - (int)((point.getHighPrice() - minPrice) * yScale);
            int lowY = MAP_HEIGHT - MARGIN - (int)((point.getLowPrice() - minPrice) * yScale);
            
            // Dessiner la mèche
            g.drawLine(x + CANDLE_WIDTH / 2, highY, x + CANDLE_WIDTH / 2, lowY);
            
            // Dessiner le corps
            if (point.getClosePrice() >= point.getOpenPrice()) {
                // Hausse - corps vert
                g.setColor(Color.GREEN);
            } else {
                // Baisse - corps rouge
                g.setColor(Color.RED);
            }
            
            int bodyTop = Math.min(openY, closeY);
            int bodyHeight = Math.abs(closeY - openY);
            g.fillRect(x, bodyTop, CANDLE_WIDTH, Math.max(1, bodyHeight));
        }
        
        // Dessiner les axes et légendes
        g.setColor(Color.BLACK);
        
        // Axe vertical avec quelques graduations
        for (int i = 0; i <= 5; i++) {
            double price = minPrice + (priceRange * i / 5);
            int y = MAP_HEIGHT - MARGIN - (int)((price - minPrice) * yScale);
            g.drawLine(MARGIN - 2, y, MARGIN, y);
            g.drawString(String.format("%.1f", price), 2, y + 4);
        }
        
        // Ajouter le nom de l'item et la dernière valeur
        if (!dataPoints.isEmpty()) {
            PriceDataPoint lastPoint = dataPoints.get(dataPoints.size() - 1);
            String itemName = plugin.getShopConfigManager().getItemName(shopId, itemId);
            if (itemName == null) itemName = itemId;
            
            g.drawString(itemName, MARGIN + 5, 8);
            
            String priceText = String.format("%.2f", lastPoint.getClosePrice());
            g.drawString(priceText, MAP_WIDTH - MARGIN - priceText.length() * 6, 8);
        }
        
        g.dispose();
        return image;
    }

    /**
     * Crée et retourne une carte avec le graphique boursier
     */
    public ItemStack createMapItem(Player player) {
        MapView view = Bukkit.createMap(player.getWorld());
        
        // Supprimer tous les renderers par défaut et ajouter le nôtre
        view.getRenderers().clear();
        view.addRenderer(this);
        
        // Créer l'item de la carte
        ItemStack mapItem = new ItemStack(Material.FILLED_MAP);
        MapMeta mapMeta = (MapMeta) mapItem.getItemMeta();
        mapMeta.setMapView(view);
        
        // Configurer le nom et la description
        String itemName = plugin.getShopConfigManager().getItemName(shopId, itemId);
        if (itemName == null) itemName = itemId;
        
        mapMeta.setDisplayName("§6Marché: §f" + itemName);
        
        List<String> lore = List.of(
            "§7Graphique d'évolution des prix",
            "§7Item: §f" + itemName,
            "§7Shop: §f" + shopId,
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
                ItemStack mapItem = new ItemStack(Material.FILLED_MAP);
                MapMeta mapMeta = (MapMeta) mapItem.getItemMeta();
                mapMeta.setMapView(view);
                mapItem.setItemMeta(mapMeta);
                return mapItem;
            }
        }
        
        // Créer une nouvelle carte
        return new MarketChartRenderer(plugin, shopId, itemId).createMapItem(player);
    }
}