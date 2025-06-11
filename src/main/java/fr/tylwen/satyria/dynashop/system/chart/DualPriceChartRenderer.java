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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DualPriceChartRenderer extends MapRenderer implements ZoomableChartRenderer {

    private static final int MAP_WIDTH = 128;
    private static final int MAP_HEIGHT = 128;
    private static final int MARGIN = 10;
    private static final int RIGHT_MARGIN = 20; // Marge supplémentaire à droite pour le 2ème axe

    private final DynaShopPlugin plugin;
    private final String shopId;
    private final String itemId;
    private BufferedImage cachedImage;
    private long lastUpdateTime;
    private static final long UPDATE_INTERVAL = 5 * 60 * 1000; // 5 minutes
    
    // Couleurs pour les courbes
    private final Color sellColor = new Color(0, 153, 51); // Vert
    private final Color buyColor = new Color(204, 51, 51); // Rouge
    private final Color gridColor = new Color(220, 220, 220); // Gris clair
    private final Color textColor = Color.BLACK;
    
    // Gestion de la granularité
    private int granularityMinutes = 15; // Par défaut 15 minutes par point
    private static final int[] GRANULARITIES = {5, 15, 60, 180, 360, 720, 1440}; // en minutes
    private int granularityIndex = 1; // index courant dans la liste (par défaut 15min)
    
    private static final Map<String, Integer> mapIdCache = new HashMap<>();
    private static final String MAP_TYPE_KEY = "dual_price_chart";
    
    public DualPriceChartRenderer(DynaShopPlugin plugin, String shopId, String itemId) {
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
        List<PricePoint> points = groupDataByGranularity(dataPoints);
        
        // Trouver les valeurs min et max pour les échelles
        double minSellPrice = Double.MAX_VALUE;
        double maxSellPrice = Double.MIN_VALUE;
        double minBuyPrice = Double.MAX_VALUE;
        double maxBuyPrice = Double.MIN_VALUE;
        
        for (PricePoint point : points) {
            if (point.sellPrice > 0) {
                minSellPrice = Math.min(minSellPrice, point.sellPrice);
                maxSellPrice = Math.max(maxSellPrice, point.sellPrice);
            }
            if (point.buyPrice > 0) {
                minBuyPrice = Math.min(minBuyPrice, point.buyPrice);
                maxBuyPrice = Math.max(maxBuyPrice, point.buyPrice);
            }
        }
        
        // Ajouter une marge aux échelles
        double sellPriceDiff = maxSellPrice - minSellPrice;
        minSellPrice = Math.max(0, minSellPrice - sellPriceDiff * 0.1);
        maxSellPrice = maxSellPrice + sellPriceDiff * 0.1;
        
        double buyPriceDiff = maxBuyPrice - minBuyPrice;
        minBuyPrice = Math.max(0, minBuyPrice - buyPriceDiff * 0.1);
        maxBuyPrice = maxBuyPrice + buyPriceDiff * 0.1;
        
        // Éviter les divisions par zéro
        if (minSellPrice == maxSellPrice) maxSellPrice = minSellPrice + 1;
        if (minBuyPrice == maxBuyPrice) maxBuyPrice = minBuyPrice + 1;
        
        // Dessiner la grille
        drawGrid(g);
        
        // Dessiner les axes Y avec les prix
        drawYAxes(g, minSellPrice, maxSellPrice, minBuyPrice, maxBuyPrice);
        
        // Dessiner les courbes
        drawPriceCurves(g, points, minSellPrice, maxSellPrice, minBuyPrice, maxBuyPrice);
        
        // Dessiner le nom de l'item
        String itemName = plugin.getShopConfigManager().getItemName(player, shopId, itemId);
        if (itemName == null) itemName = itemId;
        
        g.setColor(Color.BLACK);
        g.setFont(new Font("SansSerif", Font.BOLD, 10));
        g.drawString(itemName, MARGIN, MARGIN - 2);
        
        // Afficher les prix actuels
        if (!points.isEmpty()) {
            PricePoint lastPoint = points.get(points.size() - 1);
            
            g.setColor(sellColor);
            String sellPriceText = String.format("Vente: %.1f", lastPoint.sellPrice);
            g.drawString(sellPriceText, MARGIN, MARGIN + 10);
            
            g.setColor(buyColor);
            String buyPriceText = String.format("Achat: %.1f", lastPoint.buyPrice);
            g.drawString(buyPriceText, MARGIN, MARGIN + 22);
        }
        
        g.dispose();
        return image;
    }
    
    private void drawGrid(java.awt.Graphics2D g) {
        g.setColor(gridColor);
        
        // Lignes horizontales
        for (int i = 0; i <= 4; i++) {
            int y = MARGIN + i * (MAP_HEIGHT - 2 * MARGIN) / 4;
            g.drawLine(MARGIN, y, MAP_WIDTH - RIGHT_MARGIN, y);
        }
        
        // Lignes verticales optionnelles
        for (int i = 0; i <= 4; i++) {
            int x = MARGIN + i * (MAP_WIDTH - MARGIN - RIGHT_MARGIN) / 4;
            g.drawLine(x, MARGIN, x, MAP_HEIGHT - MARGIN);
        }
    }
    
    private void drawYAxes(java.awt.Graphics2D g, double minSellPrice, double maxSellPrice, double minBuyPrice, double maxBuyPrice) {
        // Axe gauche (prix de vente)
        g.setColor(sellColor);
        g.setFont(new Font("SansSerif", Font.PLAIN, 8));
        
        for (int i = 0; i <= 4; i++) {
            double price = minSellPrice + (maxSellPrice - minSellPrice) * (4 - i) / 4;
            int y = MARGIN + i * (MAP_HEIGHT - 2 * MARGIN) / 4;
            g.drawString(String.format("%.1f", price), 2, y + 4);
        }
        
        // Axe droit (prix d'achat)
        g.setColor(buyColor);
        
        for (int i = 0; i <= 4; i++) {
            double price = minBuyPrice + (maxBuyPrice - minBuyPrice) * (4 - i) / 4;
            int y = MARGIN + i * (MAP_HEIGHT - 2 * MARGIN) / 4;
            String priceText = String.format("%.1f", price);
            int textWidth = g.getFontMetrics().stringWidth(priceText);
            g.drawString(priceText, MAP_WIDTH - RIGHT_MARGIN + 5, y + 4);
        }
    }
    
    private void drawPriceCurves(java.awt.Graphics2D g, List<PricePoint> points, 
                            double minSellPrice, double maxSellPrice, 
                            double minBuyPrice, double maxBuyPrice) {
        
        if (points.isEmpty()) return;
        
        int chartWidth = MAP_WIDTH - MARGIN - RIGHT_MARGIN;
        int chartHeight = MAP_HEIGHT - 2 * MARGIN;
        
        // Si nous n'avons qu'un seul point, dessiner un point au lieu d'une ligne
        if (points.size() == 1) {
            PricePoint point = points.get(0);
            
            // Dessiner le point de vente
            if (point.sellPrice > 0) {
                g.setColor(sellColor);
                int y = MARGIN + (int)(chartHeight * (1 - (point.sellPrice - minSellPrice) / (maxSellPrice - minSellPrice)));
                g.fillOval(MARGIN + chartWidth/2 - 3, y - 3, 6, 6);
            }
            
            // Dessiner le point d'achat
            if (point.buyPrice > 0) {
                g.setColor(buyColor);
                int y = MARGIN + (int)(chartHeight * (1 - (point.buyPrice - minBuyPrice) / (maxBuyPrice - minBuyPrice)));
                g.fillOval(MARGIN + chartWidth/2 - 3, y - 3, 6, 6);
            }
            
            return;
        }
        
        // Pour plusieurs points, dessiner des lignes
        g.setColor(sellColor);
        g.setStroke(new BasicStroke(2.0f));
        
        int[] xPoints = new int[points.size()];
        int[] ySellPoints = new int[points.size()];
        
        for (int i = 0; i < points.size(); i++) {
            PricePoint point = points.get(i);
            // Calcul sécurisé de la position X
            xPoints[i] = MARGIN + (points.size() > 1 ? i * chartWidth / (points.size() - 1) : chartWidth / 2);
            
            if (point.sellPrice > 0) {
                ySellPoints[i] = MARGIN + (int)(chartHeight * (1 - (point.sellPrice - minSellPrice) / (maxSellPrice - minSellPrice)));
            } else {
                // Si pas de prix de vente, utiliser la dernière valeur valide ou le milieu
                ySellPoints[i] = (i > 0) ? ySellPoints[i-1] : MARGIN + chartHeight / 2;
            }
        }
        
        // Dessiner la ligne de vente
        g.drawPolyline(xPoints, ySellPoints, points.size());
        
        // Dessiner la courbe de prix d'achat (rouge)
        g.setColor(buyColor);
        g.setStroke(new BasicStroke(2.0f));
        
        int[] yBuyPoints = new int[points.size()];
        
        for (int i = 0; i < points.size(); i++) {
            PricePoint point = points.get(i);
            
            if (point.buyPrice > 0) {
                yBuyPoints[i] = MARGIN + (int)(chartHeight * (1 - (point.buyPrice - minBuyPrice) / (maxBuyPrice - minBuyPrice)));
            } else {
                // Si pas de prix d'achat, utiliser la dernière valeur valide ou le milieu
                yBuyPoints[i] = (i > 0) ? yBuyPoints[i-1] : MARGIN + chartHeight / 2;
            }
        }
        
        // Dessiner la ligne d'achat
        g.drawPolyline(xPoints, yBuyPoints, points.size());
    }
    
    private List<PricePoint> groupDataByGranularity(List<PriceDataPoint> dataPoints) {
        List<PricePoint> grouped = new ArrayList<>();
        
        if (dataPoints.isEmpty()) {
            return grouped;
        }
        
        // Grouper les points par intervalle de temps
        LocalDateTime bucketStart = dataPoints.get(0).getTimestamp().withSecond(0).withNano(0);
        LocalDateTime bucketEnd = bucketStart.plusMinutes(granularityMinutes);
        
        double sumSellPrice = 0, sumBuyPrice = 0;
        int countSell = 0, countBuy = 0;
        boolean first = true;
        
        for (PriceDataPoint p : dataPoints) {
            while (p.getTimestamp().isAfter(bucketEnd)) {
                if (!first) {
                    double avgSellPrice = countSell > 0 ? sumSellPrice / countSell : 0;
                    double avgBuyPrice = countBuy > 0 ? sumBuyPrice / countBuy : 0;
                    grouped.add(new PricePoint(bucketStart, avgSellPrice, avgBuyPrice));
                }
                // Passe au bucket suivant
                bucketStart = bucketEnd;
                bucketEnd = bucketStart.plusMinutes(granularityMinutes);
                sumSellPrice = 0; sumBuyPrice = 0;
                countSell = 0; countBuy = 0;
                first = true;
            }
            
            // Ajouter les prix au bucket courant s'ils sont valides
            if (p.getCloseSellPrice() > 0) {
                sumSellPrice += p.getCloseSellPrice();
                countSell++;
            }
            
            if (p.getCloseBuyPrice() > 0) {
                sumBuyPrice += p.getCloseBuyPrice();
                countBuy++;
            }
            
            first = false;
        }
        
        // Ajouter le dernier groupe
        if (!first) {
            double avgSellPrice = countSell > 0 ? sumSellPrice / countSell : 0;
            double avgBuyPrice = countBuy > 0 ? sumBuyPrice / countBuy : 0;
            grouped.add(new PricePoint(bucketStart, avgSellPrice, avgBuyPrice));
        }
        
        return grouped;
    }
    
    /**
     * Crée et retourne une carte avec le graphique à double courbe
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
        mapItem.setItemMeta(mapMeta);
        
        // Configurer le nom et la description
        String itemName = plugin.getShopConfigManager().getItemName(player, shopId, itemId);
        if (itemName == null) itemName = itemId;
        
        mapMeta.setDisplayName("§6Évolution des prix: §f" + itemName);
        
        // Ajout de la granularité dans le lore
        String granularityLabel = getGranularityLabel();
        
        List<String> lore = List.of(
            "§7Graphique d'évolution des prix",
            "§7Item: §f" + itemName,
            "§7Shop: §f" + shopId,
            "§7Granularité: §f" + granularityLabel,
            "§7Vente: §a← axe gauche §7| Achat: §c→ axe droit",
            "§7Mise à jour: §f" + UPDATE_INTERVAL / (60 * 1000) + " minutes"
        );
        mapMeta.setLore(lore);
        
        // Stocker les métadonnées pour identifier cette carte
        NamespacedKey shopIdKey = new NamespacedKey(plugin, "chart_shop_id");
        NamespacedKey itemIdKey = new NamespacedKey(plugin, "chart_item_id");
        NamespacedKey typeKey = new NamespacedKey(plugin, "chart_type");
        mapMeta.getPersistentDataContainer().set(shopIdKey, PersistentDataType.STRING, shopId);
        mapMeta.getPersistentDataContainer().set(itemIdKey, PersistentDataType.STRING, itemId);
        mapMeta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, MAP_TYPE_KEY);
        
        mapItem.setItemMeta(mapMeta);
        
        // Sauvegarder l'ID de la carte pour les futures références
        String cacheKey = MAP_TYPE_KEY + ":" + shopId + ":" + itemId;
        mapIdCache.put(cacheKey, view.getId());
        
        return mapItem;
    }
    
    /**
     * Récupère une carte existante ou en crée une nouvelle
     */
    public static ItemStack getOrCreateMapItem(DynaShopPlugin plugin, Player player, String shopId, String itemId) {
        String cacheKey = MAP_TYPE_KEY + ":" + shopId + ":" + itemId;
        Integer mapId = mapIdCache.get(cacheKey);
        
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
        return new DualPriceChartRenderer(plugin, shopId, itemId).createMapItem(player);
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
            "§7Vente: §a← axe gauche §7| Achat: §c→ axe droit",
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
     * Classe pour représenter un point de prix avec achat et vente
     */
    private static class PricePoint {
        public final LocalDateTime timestamp;
        public final double sellPrice;
        public final double buyPrice;
        
        public PricePoint(LocalDateTime timestamp, double sellPrice, double buyPrice) {
            this.timestamp = timestamp;
            this.sellPrice = sellPrice;
            this.buyPrice = buyPrice;
        }
    }
}