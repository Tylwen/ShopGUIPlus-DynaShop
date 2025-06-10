package fr.tylwen.satyria.dynashop.system.chart;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.Font;
import java.awt.BasicStroke;
import java.awt.RenderingHints;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
// import java.util.UUID;

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
    
    // static Color volumeColor = new Color(0x9B939393, true);
    // static Color graphColor = new Color(0xFFFFFFFF, true);
    
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
            cachedImage = renderChart(player);
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
    
    // private BufferedImage renderChart(Player player) {
    //     BufferedImage image = new BufferedImage(MAP_WIDTH, MAP_HEIGHT, BufferedImage.TYPE_INT_ARGB);
    //     Graphics2D g = image.createGraphics();
        
    //     // Fond blanc
    //     g.setColor(Color.WHITE);
    //     g.fillRect(0, 0, MAP_WIDTH, MAP_HEIGHT);
        
    //     // Dessiner le cadre
    //     g.setColor(Color.BLACK);
    //     g.drawRect(MARGIN, MARGIN, MAP_WIDTH - 2 * MARGIN, MAP_HEIGHT - 2 * MARGIN);
        
    //     // Récupérer les données historiques
    //     PriceHistory history = plugin.getDataManager().getPriceHistory(shopId, itemId);
    //     List<PriceDataPoint> dataPoints = history.getDataPoints();
    //     // plugin.info("Rendering chart for " + shopId + ":" + itemId + " with " + dataPoints.size() + " data points.");
    //     // if (history == null || history.getDataPoints().isEmpty()) {
    //     if (dataPoints.isEmpty()) {
    //         // Pas de données, afficher un message
    //         g.drawString("No data", 15, MAP_HEIGHT / 2);
    //         g.dispose();
    //         return image;
    //     }
        
    //     // List<PriceDataPoint> dataPoints = history.getDataPoints();
        
    //     // Trouver les valeurs min et max pour l'échelle
    //     double minPrice = Double.MAX_VALUE;
    //     double maxPrice = Double.MIN_VALUE;
    //     for (PriceDataPoint point : dataPoints) {
    //         minPrice = Math.min(minPrice, point.getLowPrice());
    //         maxPrice = Math.max(maxPrice, point.getHighPrice());
    //     }
    //     // double minPrice = Double.MAX_VALUE;
    //     // double maxPrice = Double.MIN_VALUE;
    //     // for (PriceDataPoint point : dataPoints) {
    //     //     // Utiliser les prix d'achat ou de vente selon ce qu'on veut afficher
    //     //     // Ici on prend le minimum des prix bas et le maximum des prix hauts
    //     //     double lowPrice = Math.min(point.getLowBuyPrice(), point.getLowSellPrice());
    //     //     double highPrice = Math.max(point.getHighBuyPrice(), point.getHighSellPrice());
            
    //     //     minPrice = Math.min(minPrice, lowPrice);
    //     //     maxPrice = Math.max(maxPrice, highPrice);
    //     // }
        
    //     // Ajouter une marge à l'échelle
    //     double priceDiff = maxPrice - minPrice;
    //     minPrice = Math.max(0, minPrice - priceDiff * 0.1);
    //     maxPrice = maxPrice + priceDiff * 0.1;
        
    //     // Calculer l'échelle
    //     double priceRange = maxPrice - minPrice;
    //     double yScale = (MAP_HEIGHT - 2 * MARGIN) / priceRange;
        
    //     // Nombre de chandeliers à afficher
    //     int maxCandles = (MAP_WIDTH - 2 * MARGIN) / (CANDLE_WIDTH + CANDLE_SPACING);
    //     int startIndex = Math.max(0, dataPoints.size() - maxCandles);
        
    //     // Dessiner les chandeliers
    //     for (int i = startIndex; i < dataPoints.size(); i++) {
    //         PriceDataPoint point = dataPoints.get(i);
    //         int x = MARGIN + (i - startIndex) * (CANDLE_WIDTH + CANDLE_SPACING);
            
    //         // Convertir les prix en coordonnées y
    //         int openY = MAP_HEIGHT - MARGIN - (int)((point.getOpenPrice() - minPrice) * yScale);
    //         int closeY = MAP_HEIGHT - MARGIN - (int)((point.getClosePrice() - minPrice) * yScale);
    //         int highY = MAP_HEIGHT - MARGIN - (int)((point.getHighPrice() - minPrice) * yScale);
    //         int lowY = MAP_HEIGHT - MARGIN - (int)((point.getLowPrice() - minPrice) * yScale);
    //         // int openY = MAP_HEIGHT - MARGIN - (int)((point.getOpenBuyPrice() - minPrice) * yScale);
    //         // int closeY = MAP_HEIGHT - MARGIN - (int)((point.getCloseBuyPrice() - minPrice) * yScale);
    //         // int highY = MAP_HEIGHT - MARGIN - (int)((point.getHighBuyPrice() - minPrice) * yScale);
    //         // int lowY = MAP_HEIGHT - MARGIN - (int)((point.getLowBuyPrice() - minPrice) * yScale);
            
    //         // Dessiner la mèche
    //         g.drawLine(x + CANDLE_WIDTH / 2, highY, x + CANDLE_WIDTH / 2, lowY);
            
    //         // Dessiner le corps
    //         // if (point.getClosePrice() >= point.getOpenPrice()) {
    //         if (point.getCloseBuyPrice() >= point.getOpenBuyPrice()) {
    //             // Hausse - corps vert
    //             g.setColor(Color.GREEN);
    //         } else {
    //             // Baisse - corps rouge
    //             g.setColor(Color.RED);
    //         }
            
    //         int bodyTop = Math.min(openY, closeY);
    //         int bodyHeight = Math.abs(closeY - openY);
    //         g.fillRect(x, bodyTop, CANDLE_WIDTH, Math.max(1, bodyHeight));
    //     }
        
    //     // Dessiner les axes et légendes
    //     g.setColor(Color.BLACK);
        
    //     // Axe vertical avec quelques graduations
    //     for (int i = 0; i <= 5; i++) {
    //         double price = minPrice + (priceRange * i / 5);
    //         int y = MAP_HEIGHT - MARGIN - (int)((price - minPrice) * yScale);
    //         g.drawLine(MARGIN - 2, y, MARGIN, y);
    //         g.drawString(String.format("%.1f", price), 2, y + 4);
    //     }
        
    //     // Ajouter le nom de l'item et la dernière valeur
    //     if (!dataPoints.isEmpty()) {
    //         PriceDataPoint lastPoint = dataPoints.get(dataPoints.size() - 1);
    //         String itemName = plugin.getShopConfigManager().getItemName(player, shopId, itemId);
    //         if (itemName == null) itemName = itemId;
            
    //         g.drawString(itemName, MARGIN + 5, 8);
            
    //         // String priceText = String.format("%.2f", lastPoint.getClosePrice());
    //         String priceText = String.format("%.2f", lastPoint.getCloseBuyPrice());
    //         g.drawString(priceText, MAP_WIDTH - MARGIN - priceText.length() * 6, 8);
    //     }
        
    //     g.dispose();
    //     return image;
    // }

//     private BufferedImage renderChart(Player player) {
//         BufferedImage image = new BufferedImage(MAP_WIDTH, MAP_HEIGHT, BufferedImage.TYPE_INT_ARGB);
//         Graphics2D g = image.createGraphics();
        
//         // Activer l'antialiasing pour un meilleur rendu
//         g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
//         // Fond gris clair
//         g.setColor(new Color(220, 220, 220));
//         g.fillRect(0, 0, MAP_WIDTH, MAP_HEIGHT);
        
//         // Dessiner le cadre
//         g.setColor(Color.BLACK);
//         g.drawRect(MARGIN, MARGIN, MAP_WIDTH - 2 * MARGIN, MAP_HEIGHT - 2 * MARGIN);
        
//         // Récupérer les données historiques
//         PriceHistory history = plugin.getDataManager().getPriceHistory(shopId, itemId);
//         List<PriceDataPoint> dataPoints = history.getDataPoints();
        
//         if (dataPoints.isEmpty()) {
//             // Pas de données, afficher un message
//             g.setColor(Color.BLACK);
//             g.drawString("Pas de données disponibles", 15, MAP_HEIGHT / 2);
//             g.dispose();
//             return image;
//         }
        
//         // Afficher le nom de l'item en haut
//         String itemName = plugin.getShopConfigManager().getItemName(player, shopId, itemId);
//         if (itemName == null) itemName = itemId;
//         g.setColor(Color.BLACK);
//         g.setFont(new Font("SansSerif", Font.BOLD, 10));
//         g.drawString(itemName, MARGIN, MARGIN - 2);
        
//         // Trouver les valeurs min et max pour l'échelle
//         double minPrice = Double.MAX_VALUE;
//         double maxPrice = Double.MIN_VALUE;
//         for (PriceDataPoint point : dataPoints) {
//             // Utiliser le prix d'achat pour simplicité
//             minPrice = Math.min(minPrice, point.getLowBuyPrice());
//             maxPrice = Math.max(maxPrice, point.getHighBuyPrice());
//         }
        
//         // Éviter les divisions par zéro et arrondir pour plus de lisibilité
//         if (maxPrice == minPrice) maxPrice = minPrice + 1;
//         minPrice = Math.max(0, Math.floor(minPrice * 0.9));
//         maxPrice = Math.ceil(maxPrice * 1.1);
        
//         // Calculer l'échelle
//         double priceRange = maxPrice - minPrice;
//         double yScale = (MAP_HEIGHT - 2 * MARGIN) / priceRange;
        
//         // Dessiner les graduations et valeurs sur l'axe Y (5 niveaux)
//         g.setFont(new Font("SansSerif", Font.PLAIN, 8));
// g.setColor(new Color(66, 135, 245));
// g.drawString("Min", MAP_WIDTH - MARGIN - 30, MAP_HEIGHT - MARGIN - 5);
// g.setColor(new Color(220, 50, 47));
// g.drawString("Max", MAP_WIDTH - MARGIN - 30, MAP_HEIGHT - MARGIN - 15);
//         int numLevels = 5;
//         for (int i = 0; i <= numLevels; i++) {
//             double price = minPrice + (priceRange * i / numLevels);
//             int y = MAP_HEIGHT - MARGIN - (int)((price - minPrice) * yScale);
            
//             // Ligne horizontale de graduation (pointillée)
//             g.setColor(new Color(200, 200, 200));
//             for (int x = MARGIN; x < MAP_WIDTH - MARGIN; x += 4) {
//                 g.drawLine(x, y, x + 2, y);
//             }
            
//             // Valeur numérique
//             g.setColor(Color.BLACK);
//             String priceText = String.format("%.1f", price);
//             g.drawString(priceText, 2, y + 3);
//         }
        
//         // Tracer le graphique linéaire pour le prix d'achat
//         g.setColor(new Color(34, 139, 34)); // Vert forêt
//         g.setStroke(new BasicStroke(1.5f)); // Ligne plus épaisse
        
//         int numPoints = dataPoints.size();
//         int xStep = (MAP_WIDTH - 2 * MARGIN) / Math.max(1, numPoints - 1);
        
//         // int[] xPoints = new int[numPoints];
//         // int[] yPoints = new int[numPoints];
        
//         // for (int i = 0; i < numPoints; i++) {
//         //     PriceDataPoint point = dataPoints.get(i);
//         //     xPoints[i] = MARGIN + i * xStep;
//         //     yPoints[i] = MAP_HEIGHT - MARGIN - (int)((point.getCloseBuyPrice() - minPrice) * yScale);
//         // }
        
//         // // Tracer la ligne continue
//         // g.drawPolyline(xPoints, yPoints, numPoints);

//         // Tracer deux courbes : min (lowBuyPrice) et max (highBuyPrice)
//         int[] xPoints = new int[numPoints];
//         int[] yMinPoints = new int[numPoints];
//         int[] yMaxPoints = new int[numPoints];

//         for (int i = 0; i < numPoints; i++) {
//             PriceDataPoint point = dataPoints.get(i);
//             xPoints[i] = MARGIN + i * xStep;
//             yMinPoints[i] = MAP_HEIGHT - MARGIN - (int)((point.getLowBuyPrice() - minPrice) * yScale);
//             yMaxPoints[i] = MAP_HEIGHT - MARGIN - (int)((point.getHighBuyPrice() - minPrice) * yScale);
//         }

//         // Courbe min en bleu
//         g.setColor(new Color(66, 135, 245));
//         g.setStroke(new BasicStroke(1.5f));
//         g.drawPolyline(xPoints, yMinPoints, numPoints);

//         // Courbe max en rouge
//         g.setColor(new Color(220, 50, 47));
//         g.setStroke(new BasicStroke(1.5f));
//         g.drawPolyline(xPoints, yMaxPoints, numPoints);
        
//         // Ajouter le dernier prix en haut à droite
//         if (!dataPoints.isEmpty()) {
//             PriceDataPoint lastPoint = dataPoints.get(dataPoints.size() - 1);
//             String priceText = String.format("%.1f", lastPoint.getCloseBuyPrice());
//             g.drawString(priceText, MAP_WIDTH - MARGIN - g.getFontMetrics().stringWidth(priceText) - 2, MARGIN + 10);
//         }
        
//         g.dispose();
//         return image;
//     }

    // private BufferedImage renderChart(Player player) {
    //     BufferedImage image = new BufferedImage(MAP_WIDTH, MAP_HEIGHT, BufferedImage.TYPE_INT_ARGB);
    //     Graphics2D g = image.createGraphics();

    //     // Antialiasing
    //     g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    //     // Fond blanc
    //     g.setColor(Color.WHITE);
    //     g.fillRect(0, 0, MAP_WIDTH, MAP_HEIGHT);

    //     // Grille
    //     g.setColor(new Color(230, 230, 230));
    //     int numGridX = 6, numGridY = 6;
    //     for (int i = 0; i <= numGridX; i++) {
    //         int x = MARGIN + i * (MAP_WIDTH - 2 * MARGIN) / numGridX;
    //         g.drawLine(x, MARGIN, x, MAP_HEIGHT - MARGIN);
    //     }
    //     for (int i = 0; i <= numGridY; i++) {
    //         int y = MARGIN + i * (MAP_HEIGHT - 2 * MARGIN) / numGridY;
    //         g.drawLine(MARGIN, y, MAP_WIDTH - MARGIN, y);
    //     }

    //     // Cadre
    //     g.setColor(Color.BLACK);
    //     g.drawRect(MARGIN, MARGIN, MAP_WIDTH - 2 * MARGIN, MAP_HEIGHT - 2 * MARGIN);

    //     // Données
    //     PriceHistory history = plugin.getDataManager().getPriceHistory(shopId, itemId);
    //     List<PriceDataPoint> dataPoints = history.getDataPoints();
    //     if (dataPoints.isEmpty()) {
    //         g.setColor(Color.BLACK);
    //         g.drawString("Pas de données", 15, MAP_HEIGHT / 2);
    //         g.dispose();
    //         return image;
    //     }

    //     // Axe Y : min/max
    //     double minPrice = Double.MAX_VALUE, maxPrice = Double.MIN_VALUE;
    //     for (PriceDataPoint point : dataPoints) {
    //         minPrice = Math.min(minPrice, point.getLowBuyPrice());
    //         maxPrice = Math.max(maxPrice, point.getHighBuyPrice());
    //     }
    //     if (maxPrice == minPrice) maxPrice = minPrice + 1;
    //     double priceRange = maxPrice - minPrice;
    //     double yScale = (MAP_HEIGHT - 2 * MARGIN) / priceRange;

    //     // Axe X : combien de chandeliers ?
    //     int maxCandles = (MAP_WIDTH - 2 * MARGIN) / (CANDLE_WIDTH + CANDLE_SPACING);
    //     int startIndex = Math.max(0, dataPoints.size() - maxCandles);

    //     // Chandeliers
    //     for (int i = startIndex; i < dataPoints.size(); i++) {
    //         PriceDataPoint point = dataPoints.get(i);
    //         int x = MARGIN + (i - startIndex) * (CANDLE_WIDTH + CANDLE_SPACING);

    //         int openY = MAP_HEIGHT - MARGIN - (int)((point.getOpenBuyPrice() - minPrice) * yScale);
    //         int closeY = MAP_HEIGHT - MARGIN - (int)((point.getCloseBuyPrice() - minPrice) * yScale);
    //         int highY = MAP_HEIGHT - MARGIN - (int)((point.getHighBuyPrice() - minPrice) * yScale);
    //         int lowY = MAP_HEIGHT - MARGIN - (int)((point.getLowBuyPrice() - minPrice) * yScale);

    //         // Mèche
    //         g.setColor(Color.DARK_GRAY);
    //         g.drawLine(x + CANDLE_WIDTH / 2, highY, x + CANDLE_WIDTH / 2, lowY);

    //         // Corps
    //         boolean isUp = point.getCloseBuyPrice() >= point.getOpenBuyPrice();
    //         g.setColor(isUp ? new Color(34, 139, 34) : new Color(220, 50, 47));
    //         int bodyTop = Math.min(openY, closeY);
    //         int bodyHeight = Math.max(1, Math.abs(closeY - openY));
    //         g.fillRect(x, bodyTop, CANDLE_WIDTH, bodyHeight);
    //         g.setColor(Color.BLACK);
    //         g.drawRect(x, bodyTop, CANDLE_WIDTH, bodyHeight);
    //     }

    //     // Graduation Y
    //     g.setFont(new Font("SansSerif", Font.PLAIN, 8));
    //     g.setColor(Color.GRAY);
    //     int numLevels = 6;
    //     for (int i = 0; i <= numLevels; i++) {
    //         double price = minPrice + (priceRange * i / numLevels);
    //         int y = MAP_HEIGHT - MARGIN - (int)((price - minPrice) * yScale);
    //         String priceText = String.format("%.1f", price);
    //         g.drawString(priceText, 2, y + 3);
    //     }

    //     // Nom de l'item
    //     String itemName = plugin.getShopConfigManager().getItemName(player, shopId, itemId);
    //     if (itemName == null) itemName = itemId;
    //     g.setColor(Color.BLACK);
    //     g.setFont(new Font("SansSerif", Font.BOLD, 10));
    //     g.drawString(itemName, MARGIN, MARGIN - 2);

    //     g.dispose();
    //     return image;
    // }

    private BufferedImage renderChart(Player player) {
        BufferedImage image = new BufferedImage(MAP_WIDTH, MAP_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Fond blanc
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, MAP_WIDTH, MAP_HEIGHT);

        // Grille
        g.setColor(new Color(230, 230, 230));
        int gridX = 5, gridY = 6;
        for (int i = 0; i <= gridX; i++) {
            int x = MARGIN + i * (MAP_WIDTH - 2 * MARGIN) / gridX;
            g.drawLine(x, MARGIN, x, MAP_HEIGHT - MARGIN);
        }
        for (int i = 0; i <= gridY; i++) {
            int y = MARGIN + i * (MAP_HEIGHT - 2 * MARGIN) / gridY;
            g.drawLine(MARGIN, y, MAP_WIDTH - MARGIN, y);
        }

        // Cadre
        g.setColor(Color.BLACK);
        g.drawRect(MARGIN, MARGIN, MAP_WIDTH - 2 * MARGIN, MAP_HEIGHT - 2 * MARGIN);

        // Données
        PriceHistory history = plugin.getDataManager().getPriceHistory(shopId, itemId);
        List<PriceDataPoint> dataPoints = history.getDataPoints();
        if (dataPoints.isEmpty()) {
            g.setColor(Color.BLACK);
            g.drawString("Pas de données", 15, MAP_HEIGHT / 2);
            g.dispose();
            return image;
        }

        // Axe Y : min/max
        double minPrice = Double.MAX_VALUE, maxPrice = Double.MIN_VALUE;
        for (PriceDataPoint point : dataPoints) {
            minPrice = Math.min(minPrice, point.getLowBuyPrice());
            maxPrice = Math.max(maxPrice, point.getHighBuyPrice());
        }
        if (maxPrice == minPrice) maxPrice = minPrice + 1;
        double priceRange = maxPrice - minPrice;
        double yScale = (MAP_HEIGHT - 2 * MARGIN) / priceRange;

        // Axe X : combien de chandeliers ?
        int maxCandles = (MAP_WIDTH - 2 * MARGIN) / (CANDLE_WIDTH + CANDLE_SPACING);
        int startIndex = Math.max(0, dataPoints.size() - maxCandles);

        // Chandeliers
        for (int i = startIndex; i < dataPoints.size(); i++) {
            PriceDataPoint point = dataPoints.get(i);
            int x = MARGIN + (i - startIndex) * (CANDLE_WIDTH + CANDLE_SPACING);

            int openY = MAP_HEIGHT - MARGIN - (int)((point.getOpenBuyPrice() - minPrice) * yScale);
            int closeY = MAP_HEIGHT - MARGIN - (int)((point.getCloseBuyPrice() - minPrice) * yScale);
            int highY = MAP_HEIGHT - MARGIN - (int)((point.getHighBuyPrice() - minPrice) * yScale);
            int lowY = MAP_HEIGHT - MARGIN - (int)((point.getLowBuyPrice() - minPrice) * yScale);

            // Mèche
            g.setColor(Color.DARK_GRAY);
            g.drawLine(x + CANDLE_WIDTH / 2, highY, x + CANDLE_WIDTH / 2, lowY);

            // Corps
            boolean isUp = point.getCloseBuyPrice() >= point.getOpenBuyPrice();
            g.setColor(isUp ? new Color(34, 139, 34) : new Color(220, 50, 47));
            int bodyTop = Math.min(openY, closeY);
            int bodyHeight = Math.max(1, Math.abs(closeY - openY));
            g.fillRect(x, bodyTop, CANDLE_WIDTH, bodyHeight);
            g.setColor(Color.BLACK);
            g.drawRect(x, bodyTop, CANDLE_WIDTH, bodyHeight);
        }

        // Graduation Y
        g.setFont(new Font("SansSerif", Font.PLAIN, 8));
        g.setColor(Color.GRAY);
        int numLevels = 6;
        for (int i = 0; i <= numLevels; i++) {
            double price = minPrice + (priceRange * i / numLevels);
            int y = MAP_HEIGHT - MARGIN - (int)((price - minPrice) * yScale);
            String priceText = String.format("%.1f", price);
            g.drawString(priceText, 2, y + 3);
        }

        // Nom de l'item
        String itemName = plugin.getShopConfigManager().getItemName(player, shopId, itemId);
        if (itemName == null) itemName = itemId;
        g.setColor(Color.BLACK);
        g.setFont(new Font("SansSerif", Font.BOLD, 10));
        g.drawString(itemName, MARGIN, MARGIN - 2);

        // Dernier prix à droite
        PriceDataPoint lastPoint = dataPoints.get(dataPoints.size() - 1);
        String priceText = String.format("%.1f", lastPoint.getCloseBuyPrice());
        g.drawString(priceText, MAP_WIDTH - MARGIN - g.getFontMetrics().stringWidth(priceText) - 2, MARGIN + 10);

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
        String itemName = plugin.getShopConfigManager().getItemName(player, shopId, itemId);
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
        
        // // Assurez-vous qu'il y a au moins un point de données dans l'historique
        // ensureHistoryExists(plugin, shopId, itemId);
        
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

    // private static void ensureHistoryExists(DynaShopPlugin plugin, String shopId, String itemId) {
    //     // Récupérer l'historique actuel
    //     PriceHistory history = plugin.getDataManager().getPriceHistory(shopId, itemId);
        
    //     // Si l'historique est vide, créer un point initial
    //     if (history.getDataPoints().isEmpty()) {
    //         // Obtenir le prix actuel via le plugin
    //         double currentBuyPrice = plugin.getShopConfigManager().getBuyPrice(shopId, itemId);
    //         double currentSellPrice = plugin.getShopConfigManager().getSellPrice(shopId, itemId);
            
    //         if (currentBuyPrice > 0 || currentSellPrice > 0) {
    //             // Ajouter un point avec le prix actuel
    //             history.addDataPoint(
    //                 currentBuyPrice, currentBuyPrice, currentBuyPrice, currentBuyPrice,
    //                 currentSellPrice, currentSellPrice, currentSellPrice, currentSellPrice
    //             );
                
    //             plugin.getLogger().info("Point initial ajouté à l'historique pour " + shopId + ":" + itemId);
    //         }
    //     }
    // }
}