package fr.tylwen.satyria.dynashop.web;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;

import fr.tylwen.satyria.dynashop.DynaShopPlugin;
import fr.tylwen.satyria.dynashop.data.param.DynaShopType;
import fr.tylwen.satyria.dynashop.data.model.TransactionRecord;
import fr.tylwen.satyria.dynashop.system.chart.PriceHistory;
import fr.tylwen.satyria.dynashop.system.chart.PriceHistory.PriceDataPoint;
import net.brcdev.shopgui.ShopGuiPlusApi;
import net.brcdev.shopgui.shop.Shop;
import net.brcdev.shopgui.shop.item.ShopItem;
import net.brcdev.shopgui.shop.item.ShopItemType;

// import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
// import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
// import org.bukkit.entity.Player;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.concurrent.ThreadPoolExecutor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class MarketWebServer {
    
    private final DynaShopPlugin plugin;
    private HttpServer server;
    private final int port;
    private final Gson gson;
    private static final String WEB_DIR = "/web";
    
    public MarketWebServer(DynaShopPlugin plugin, int port) {
        this.plugin = plugin;
        this.port = port;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }
    
    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            
            // Configurez les routes pour les fichiers statiques
            server.createContext("/", this::handleRoot);
            server.createContext("/assets", this::handleAssets);
            
            // API pour les données
            server.createContext("/api/shops", this::handleShopsList);
            server.createContext("/api/items", this::handleItemsList);
            server.createContext("/api/prices", this::handlePricesData);
            server.createContext("/api/price-stats", this::handlePriceStats);
            server.createContext("/api/shop-type", this::handleShopType);
            
            server.createContext("/api/price-forecast", this::handlePriceForecast);
            server.createContext("/api/item-correlations", this::handleItemCorrelations);
            server.createContext("/api/player-transactions", this::handlePlayerTransactions);
            server.createContext("/api/heatmap-data", this::handleHeatmapData);
            
            // Création d'un pool de threads pour gérer les requêtes
            ThreadPoolExecutor threadPoolExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(10);
            server.setExecutor(threadPoolExecutor);
            
            server.start();
            // plugin.getLogger().info("§aServeur web démarré sur le port " + port);
            // plugin.getLogger().info("Web server started on port " + port);

            // Extraire les fichiers web s'ils n'existent pas
            extractWebFiles();
        } catch (IOException e) {
            // plugin.getLogger().severe("§cErreur lors du démarrage du serveur web: " + e.getMessage());
            plugin.getLogger().severe("Error starting web server: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public void stop() {
        if (server != null) {
            server.stop(0);
            // plugin.getLogger().info("§aServeur web arrêté");
            plugin.getLogger().info("Web server stopped");
        }
    }
    
    private void extractWebFiles() {
        Path webDir = Paths.get(plugin.getDataFolder().getPath(), "web");
        if (!Files.exists(webDir)) {
            try {
                Files.createDirectories(webDir);
                
                // Extraire les fichiers HTML, CSS, JS
                extractResourceFile("index.html", webDir);
                
                // Créer les sous-répertoires
                Path assetsDir = Paths.get(webDir.toString(), "assets");
                Files.createDirectories(assetsDir);

                // Extraire les assets
                extractResourceFile("assets/style.css", webDir);
                extractResourceFile("assets/script.js", webDir);
                // extractResourceFile("assets/date-fns.min.js", webDir);
                // extractResourceFile("assets/chartjs-adapter-date-fns.min.js", webDir);
                // extractResourceFile("assets/chart.min.js", webDir);
                
                // plugin.getLogger().info("Fichiers web extraits avec succès");
                plugin.getLogger().info("Web files extracted successfully");
            } catch (IOException e) {
                plugin.getLogger().severe("Error extracting web files: " + e.getMessage());
            }
        }
    }
    
    private void extractResourceFile(String resourcePath, Path targetDir) {
        try {
            Path targetPath = Paths.get(targetDir.toString(), resourcePath);
            
            // Créer les répertoires parent si nécessaire
            Files.createDirectories(targetPath.getParent());
            
            // Copier la ressource
            try (OutputStream os = Files.newOutputStream(targetPath)) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                
                try (var is = getClass().getResourceAsStream(WEB_DIR + "/" + resourcePath)) {
                    if (is == null) {
                        // plugin.getLogger().warning("Ressource non trouvée: " + resourcePath);
                        plugin.getLogger().warning("Resource not found: " + resourcePath);
                        return;
                    }
                    
                    while ((bytesRead = is.read(buffer)) != -1) {
                        os.write(buffer, 0, bytesRead);
                    }
                }
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Error extracting " + resourcePath + ": " + e.getMessage());
        }
    }
    
    // Gestionnaires de routes
    
    private void handleRoot(HttpExchange exchange) throws IOException {
        Path indexPath = Paths.get(plugin.getDataFolder().getPath(), "web", "index.html");
        serveFile(exchange, indexPath, "text/html");
    }
    
    private void handleAssets(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath().substring(1); // Enlever le / initial
        Path filePath = Paths.get(plugin.getDataFolder().getPath(), "web", path);
        
        if (!Files.exists(filePath)) {
            exchange.sendResponseHeaders(404, 0);
            exchange.getResponseBody().close();
            return;
        }
        
        String contentType = "application/octet-stream";
        if (path.endsWith(".css")) contentType = "text/css";
        else if (path.endsWith(".js")) contentType = "application/javascript";
        else if (path.endsWith(".html")) contentType = "text/html";
        else if (path.endsWith(".json")) contentType = "application/json";
        
        serveFile(exchange, filePath, contentType);
    }
    
    /**
    /* Gère la liste des shops disponibles
     * @param exchange L'échange HTTP
     * @throws IOException Si une erreur d'E/S se produit
     */
    private void handleShopsList(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            exchange.sendResponseHeaders(405, 0);
            exchange.getResponseBody().close();
            return;
        }
        
        Set<String> shopIds = plugin.getShopConfigManager().getShops();
        List<Map<String, String>> shopsList = new ArrayList<>();

        FileConfiguration sgpConfig = ShopGuiPlusApi.getPlugin().getConfigMain().getConfig();
        Map<String, String> shopNamesMap = new HashMap<>();
        ConfigurationSection shopMenuItems = sgpConfig.getConfigurationSection("shopMenuItems");
        
        if (shopMenuItems != null) {
            for (String key : shopMenuItems.getKeys(false)) {
                String shopId = shopMenuItems.getString(key + ".shop");
                String shopName = shopMenuItems.getString(key + ".item.name");
                
                if (shopId != null && shopName != null) {
                    // Nettoyer le nom (enlever codes couleur et espaces superflus)
                    shopName = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', shopName)).trim();
                    shopNamesMap.put(shopId, shopName);
                }
            }
        }
        
        for (String shopId : shopIds) {
            // Obtenir le shop depuis l'API ShopGUIPlus
            Shop shop = ShopGuiPlusApi.getPlugin().getShopManager().getShopById(shopId);
            if (shop != null) {
                Map<String, String> shopData = new HashMap<>();
                shopData.put("id", shopId);
                
                // Utiliser le nom de la config si disponible, sinon fallback sur le nom du shop
                String shopName = shopNamesMap.getOrDefault(shopId, null);
                if (shopName == null || shopName.isEmpty()) {
                    // Fallback sur le nom du shop si pas trouvé dans la config
                    shopName = ChatColor.stripColor(shop.getName().replace("%page%", "")).trim();
                }

                if (shopName.isEmpty()) {
                    shopName = shopId; // Fallback si le nom est vide
                }
                
                shopData.put("name", shopName);
                shopsList.add(shopData);
            }
        }

        // Trier par nom pour une meilleure présentation
        shopsList.sort(Comparator.comparing(map -> map.get("name")));

        String jsonResponse = gson.toJson(shopsList);
        sendJsonResponse(exchange, jsonResponse);
    }
    
    /**
     * Gère la liste des items d'un shop spécifique
     * @param exchange L'échange HTTP
     * @throws IOException Si une erreur d'E/S se produit
     */
    private void handleItemsList(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            exchange.sendResponseHeaders(405, 0);
            exchange.getResponseBody().close();
            return;
        }
        
        Map<String, String[]> queryParams = parseQueryParams(exchange.getRequestURI().getQuery());
        String shopId = queryParams.containsKey("shop") ? queryParams.get("shop")[0] : null;
        if (shopId == null) {
            exchange.sendResponseHeaders(400, 0);
            exchange.getResponseBody().close();
            return;
        }

        Shop shop = ShopGuiPlusApi.getPlugin().getShopManager().getShopById(shopId);
        if (shop == null) {
            exchange.sendResponseHeaders(404, 0);
            exchange.getResponseBody().close();
            return;
        }
        
        // Player player = (Player) new OfflinePlayer() {
        //     @Override
        //     public String getName() {
        //         return exchange.getPrincipal().getName();
        //     }
        // };

        // Player player = Bukkit.getPlayer(exchange.getPrincipal().getName());
        // if (player == null) {
        //     // Si le joueur n'est pas connecté, on peut soit renvoyer une erreur, soit utiliser un joueur offline
        //     // exchange.sendResponseHeaders(403, 0);
        //     // exchange.getResponseBody().close();
        //     // return;
        // }
        
        // Extraire la langue du navigateur
        String locale = "en"; // Langue par défaut
        
        // Récupérer l'en-tête Accept-Language
        String acceptLanguage = exchange.getRequestHeaders().getFirst("Accept-Language");
        if (acceptLanguage != null && !acceptLanguage.isEmpty()) {
            // Exemple: fr-FR,fr;q=0.9,en-US;q=0.8,en;q=0.7
            // Extraire le code de langue principale
            String[] langs = acceptLanguage.split(",");
            if (langs.length > 0) {
                String primaryLang = langs[0].split(";")[0].split("-")[0];
                locale = primaryLang; // fr, en, de, etc.
            }
        }
        // plugin.getLogger().info("Langue du navigateur détectée: " + locale);

        final String finalLocale = locale;

        Map<String, Map<String, Object>> uniqueItemsMap = new HashMap<>();
        
        plugin.getShopConfigManager().getShopItems(shopId).stream()
            .filter(itemId -> {
                // Vérifier si l'item existe et n'est pas un DUMMY
                ShopItem shopItem = shop.getShopItem(itemId);
                return shopItem != null && shopItem.getType() != ShopItemType.DUMMY && shopItem.getType() != ShopItemType.SPECIAL;
            })
            .forEach(itemId -> {
                String itemName = plugin.getShopConfigManager().getItemNameWithLocale(shopId, itemId, finalLocale);
                
                // Créer un identifiant unique basé sur le nom normalisé
                String normalizedName = itemName.toLowerCase().trim();
                
                // Utiliser l'identifiant normalisé comme clé de map pour éliminer les doublons
                Map<String, Object> itemData = new HashMap<>();
                itemData.put("id", itemId);
                itemData.put("name", itemName);
                
                // Ne remplacer que si l'item n'existe pas déjà ou si l'ID est plus court (généralement plus lisible)
                if (!uniqueItemsMap.containsKey(normalizedName) || 
                    ((String)uniqueItemsMap.get(normalizedName).get("id")).length() > itemId.length()) {
                    uniqueItemsMap.put(normalizedName, itemData);
                }
            });
        
        // Étape 2: Convertir la Map en List pour le résultat final
        List<Map<String, Object>> items = new ArrayList<>(uniqueItemsMap.values());

        // List<Map<String, Object>> items = plugin.getShopConfigManager().getShopItems(shopId).stream()
        //     .filter(itemId -> {
        //         // Vérifier si l'item existe et n'est pas un DUMMY
        //         ShopItem shopItem = shop.getShopItem(itemId);
        //         return shopItem != null && shopItem.getType() != ShopItemType.DUMMY && shopItem.getType() != ShopItemType.SPECIAL;
        //     })
        //     .map(itemId -> {
        //         Map<String, Object> itemData = new HashMap<>();
        //         itemData.put("id", itemId);
        //         // itemData.put("name", plugin.getShopConfigManager().getItemName(null, shopId, itemId));
        //         itemData.put("name", plugin.getShopConfigManager().getItemNameWithLocale(shopId, itemId, finalLocale));
        //         return itemData;
        //     })
        //     .collect(Collectors.toList());

        // Trier les items par nom
        items.sort(Comparator.comparing(map -> (String) map.get("name")));

        String jsonResponse = gson.toJson(items);
        sendJsonResponse(exchange, jsonResponse);
    }
    
    /**
     * Gère la récupération des données de prix pour un item spécifique dans un shop
     * @param exchange L'échange HTTP
     * @throws IOException Si une erreur d'E/S se produit
     */
    private void handlePricesData(HttpExchange exchange) throws IOException {
        try {
            if (!exchange.getRequestMethod().equals("GET")) {
                exchange.sendResponseHeaders(405, 0);
                exchange.getResponseBody().close();
                return;
            }
            
            Map<String, String[]> queryParams = parseQueryParams(exchange.getRequestURI().getQuery());
            String shopId = queryParams.containsKey("shop") ? queryParams.get("shop")[0] : null;
            String itemId = queryParams.containsKey("item") ? queryParams.get("item")[0] : null;
            
            // Nouveaux paramètres
            String period = queryParams.containsKey("period") ? queryParams.get("period")[0] : "all";
            String granularity = queryParams.containsKey("granularity") ? queryParams.get("granularity")[0] : "auto";
            int maxPoints = queryParams.containsKey("maxPoints") ? Integer.parseInt(queryParams.get("maxPoints")[0]) : 2000; // Limiter par défaut à 2000 points
            
            // Convertir la période en date de début
            LocalDateTime startTime = null;
            if (!period.equals("all")) {
                LocalDateTime now = LocalDateTime.now();
                switch (period) {
                    case "1h": startTime = now.minusHours(1); break;
                    case "6h": startTime = now.minusHours(6); break;
                    case "12h": startTime = now.minusHours(12); break;
                    case "1d": startTime = now.minusDays(1); break;
                    case "1w": startTime = now.minusWeeks(1); break;
                    case "1m": startTime = now.minusMonths(1); break;
                }
            }
            
            // Déterminer l'intervalle d'agrégation en minutes
            final int INTERVAL_MINUTES = plugin.getConfigMain().getInt("history.save-interval", 15);
            int interval;
            if (granularity.equals("auto")) {
                // // Sélection automatique basée sur la période
                // if (period.equals("1h")) interval = 1;
                // else if (period.equals("6h")) interval = 5;
                // else if (period.equals("12h")) interval = 10;
                // else if (period.equals("1d")) interval = 15;
                // else if (period.equals("1w")) interval = 60;
                // else if (period.equals("1m")) interval = 240; // 4 heures
                // else interval = 15; // valeur par défaut
                // Sélection automatique basée sur la période
                if (period.equals("1h")) interval = INTERVAL_MINUTES; // 15 minutes (0.25h)
                else if (period.equals("6h")) interval = INTERVAL_MINUTES; // 15 minutes (0.25h)
                else if (period.equals("12h")) interval = INTERVAL_MINUTES * 2; // 30 minutes (0.5h)
                else if (period.equals("1d")) interval = INTERVAL_MINUTES * 4; // 60 minutes (1h)
                else if (period.equals("1w")) interval = INTERVAL_MINUTES * 16; // 240 minutes (4h)
                else if (period.equals("1m")) interval = INTERVAL_MINUTES * 48; // 720 minutes (12h)
                else interval = INTERVAL_MINUTES; // Valeur par défaut (15 minutes)
            } else {
                // Intervalle explicite
                interval = switch(granularity) {
                    // case "minute" -> 1;
                    // case "5min" -> 5;
                    // case "15min" -> 15;
                    // case "30min" -> 30;
                    // case "hour" -> 60;
                    // case "day" -> 1440;
                    // default -> 15;
                    case "minute" -> INTERVAL_MINUTES; // 15 minutes (0.25h)
                    case "5min" -> INTERVAL_MINUTES; // 15 minutes (0.25h)
                    case "15min" -> INTERVAL_MINUTES; // 15 minutes (0.25h)
                    case "30min" -> INTERVAL_MINUTES * 2; // 30 minutes (0.5h)
                    case "hour" -> INTERVAL_MINUTES * 4; // 60 minutes (1h)
                    case "day" -> INTERVAL_MINUTES * 96; // 1440 minutes (24h)
                    default -> INTERVAL_MINUTES;
                };
            }
            
            if (shopId == null || itemId == null) {
                exchange.sendResponseHeaders(400, 0);
                exchange.getResponseBody().close();
                return;
            }
            
            List<PriceDataPoint> dataPoints;
            if (plugin.getDataConfig().getDatabaseType().equalsIgnoreCase("sqlite")) {
                PriceHistory history = plugin.getStorageManager().getPriceHistory(shopId, itemId);
                dataPoints = history.getDataPoints();
            } else {
                dataPoints = plugin.getStorageManager().getAggregatedPriceHistory(shopId, itemId, interval, startTime, maxPoints);
            }

            // PriceHistory history = plugin.getDataManager().getPriceHistory(shopId, itemId);
            // Récupérer les données agrégées
            // List<PriceDataPoint> dataPoints = plugin.getDataManager().getAggregatedPriceHistory(shopId, itemId, interval, startTime, maxPoints);
            // List<PriceDataPoint> dataPoints = history.getDataPoints();
            
            // // Convertir les points de données en format adapté pour Chart.js
            // List<Map<String, Object>> chartData = dataPoints.stream()
            //     .map(point -> {
            //         Map<String, Object> pointData = new HashMap<>();
            //         pointData.put("timestamp", point.getTimestamp().toString());
            //         pointData.put("openBuy", point.getOpenBuyPrice());
            //         pointData.put("closeBuy", point.getCloseBuyPrice());
            //         pointData.put("highBuy", point.getHighBuyPrice());
            //         pointData.put("lowBuy", point.getLowBuyPrice());
            //         pointData.put("openSell", point.getOpenSellPrice());
            //         pointData.put("closeSell", point.getCloseSellPrice());
            //         pointData.put("highSell", point.getHighSellPrice());
            //         pointData.put("lowSell", point.getLowSellPrice());
            //         pointData.put("volume", point.getVolume());
            //         return pointData;
            //     })
            //     .collect(Collectors.toList());
            
            // 1. Filtrer par période si nécessaire
            List<PriceDataPoint> filteredPoints = filterByPeriod(dataPoints, period);
            
            // 2. Appliquer la granularité ou l'échantillonnage
            List<Map<String, Object>> chartData = aggregateOrSampleData(filteredPoints, granularity, maxPoints);
            
            String jsonResponse = gson.toJson(chartData);
            sendJsonResponse(exchange, jsonResponse);
        } catch (Exception ex) {
            plugin.getLogger().severe("Erreur dans /api/prices : " + ex.getMessage());
            ex.printStackTrace();
            // Réponse JSON d'erreur
            String errorJson = "{\"error\": \"Internal server error: " + ex.getMessage() + "\"}";
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(500, errorJson.length());
            exchange.getResponseBody().write(errorJson.getBytes());
            exchange.getResponseBody().close();
        }
    }

    private void handlePriceStats(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            exchange.sendResponseHeaders(405, 0);
            exchange.getResponseBody().close();
            return;
        }
        
        Map<String, String[]> queryParams = parseQueryParams(exchange.getRequestURI().getQuery());
        String shopId = queryParams.containsKey("shop") ? queryParams.get("shop")[0] : null;
        String itemId = queryParams.containsKey("item") ? queryParams.get("item")[0] : null;
        
        if (shopId == null || itemId == null) {
            exchange.sendResponseHeaders(400, 0);
            exchange.getResponseBody().close();
            return;
        }
        
        PriceHistory history = plugin.getStorageManager().getPriceHistory(shopId, itemId);
        List<PriceDataPoint> dataPoints = history.getDataPoints();
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalPoints", dataPoints.size());
        
        if (!dataPoints.isEmpty()) {
            PriceDataPoint first = dataPoints.get(0);
            PriceDataPoint last = dataPoints.get(dataPoints.size() - 1);
            
            stats.put("firstTimestamp", first.getTimestamp().toString());
            stats.put("lastTimestamp", last.getTimestamp().toString());
            stats.put("timeSpanHours", ChronoUnit.HOURS.between(first.getTimestamp(), last.getTimestamp()));
            
            // Statistiques de prix
            OptionalDouble minBuyOpt = dataPoints.stream().mapToDouble(PriceDataPoint::getLowBuyPrice).min();
            OptionalDouble maxBuyOpt = dataPoints.stream().mapToDouble(PriceDataPoint::getHighBuyPrice).max();
            OptionalDouble minSellOpt = dataPoints.stream().mapToDouble(PriceDataPoint::getLowSellPrice).min();
            OptionalDouble maxSellOpt = dataPoints.stream().mapToDouble(PriceDataPoint::getHighSellPrice).max();
            double totalVolume = dataPoints.stream().mapToDouble(PriceDataPoint::getVolume).sum();
            
            stats.put("minBuyPrice", minBuyOpt.orElse(0));
            stats.put("maxBuyPrice", maxBuyOpt.orElse(0));
            stats.put("minSellPrice", minSellOpt.orElse(0));
            stats.put("maxSellPrice", maxSellOpt.orElse(0));
            stats.put("currentBuyPrice", last.getCloseBuyPrice());
            stats.put("currentSellPrice", last.getCloseSellPrice());
            stats.put("totalVolume", totalVolume);
            stats.put("recommendedGranularity", recommendGranularity(dataPoints.size()));
        }
        
        // Ajouter les informations de stock pour les types STOCK/STATIC_STOCK
        DynaShopType dynaShopType = plugin.getShopConfigManager().getTypeDynaShop(shopId, itemId);
        DynaShopType buyType = plugin.getShopConfigManager().getTypeDynaShop(shopId, itemId, "buy");
        DynaShopType sellType = plugin.getShopConfigManager().getTypeDynaShop(shopId, itemId, "sell");
        if (dynaShopType == DynaShopType.STOCK || dynaShopType == DynaShopType.STATIC_STOCK) {
            Optional<Integer> stockOpt = plugin.getStorageManager().getStock(shopId, itemId);
            int stock = stockOpt.orElse(0);
            
            int maxStock = plugin.getShopConfigManager()
                .getItemValue(shopId, itemId, "stock.max", Integer.class)
                .orElse(plugin.getDataConfig().getStockMax());
            
            stats.put("currentStock", stock);
            stats.put("maxStock", maxStock);
            stats.put("isStockItem", true);
        } else if (dynaShopType == DynaShopType.RECIPE) {
            // Si l'item est de type RECIPE, calculer son stock potentiel à partir des ingrédients
            // Utiliser la méthode calculateStock de PriceRecipe pour obtenir le stock disponible
            int recipeStock = plugin.getPriceRecipe().calculateStock(shopId, itemId, new ArrayList<>());
            
            if (recipeStock > 0) {
                // Récupérer le stock maximum (si configuré)
                int maxStock = plugin.getPriceRecipe().calculateMaxStock(shopId, itemId, new ArrayList<>());
                
                stats.put("currentStock", recipeStock);
                stats.put("maxStock", maxStock > 0 ? maxStock : recipeStock * 2); // Valeur par défaut si maxStock n'est pas défini
                stats.put("isRecipeStock", true);
            }
        }
        
        // Ajouter le type d'item
        stats.put("shopType", dynaShopType.toString());
        stats.put("buyType", buyType.toString());
        stats.put("sellType", sellType.toString());
        
        String jsonResponse = gson.toJson(stats);
        sendJsonResponse(exchange, jsonResponse);
    }

    private String recommendGranularity(int dataPointCount) {
        if (dataPointCount > 100000) return "day";
        if (dataPointCount > 10000) return "hour";
        if (dataPointCount > 2000) return "minute";
        return "raw";
    }
    
    // Méthodes utilitaires

    // Méthode pour filtrer les données par période
    private List<PriceDataPoint> filterByPeriod(List<PriceDataPoint> dataPoints, String period) {
        if (dataPoints.isEmpty() || period.equals("all")) {
            return dataPoints;
        }
        
        LocalDateTime cutoff;
        LocalDateTime now = LocalDateTime.now();
        
        switch (period) {
            case "1h":
                cutoff = now.minusHours(1);
                break;
            case "6h":
                cutoff = now.minusHours(6);
                break;
            case "12h":
                cutoff = now.minusHours(12);
                break;
            case "1d":
                cutoff = now.minusHours(24);
                break;
            case "1w":
                cutoff = now.minusWeeks(1);
                break;
            case "1m":
                cutoff = now.minusMonths(1);
                break;
            case "3m":
                cutoff = now.minusMonths(3);
                break;
            default:
                return dataPoints;
        }
        
        return dataPoints.stream()
            .filter(p -> p.getTimestamp().isAfter(cutoff))
            .collect(Collectors.toList());
    }

    // Méthode pour agréger ou échantillonner les données
    private List<Map<String, Object>> aggregateOrSampleData(List<PriceDataPoint> dataPoints, String granularity, int maxPoints) {
        if (dataPoints.isEmpty()) {
            return List.of();
        }
        
        // Si nombre de points raisonnable et granularité "raw", pas besoin d'agréger
        if (dataPoints.size() <= maxPoints && granularity.equals("raw")) {
            return dataPoints.stream()
                .map(this::convertDataPointToMap)
                .collect(Collectors.toList());
        }
        
        // Déterminer la granularité automatiquement si "auto"
        if (granularity.equals("auto")) {
            int size = dataPoints.size();
            if (size > maxPoints * 10) {
                granularity = "day"; 
            } else if (size > maxPoints * 3) {
                granularity = "hour";
            } else if (size > maxPoints) {
                granularity = "minute";
            } else {
                granularity = "raw";
            }
        }
        
        // Regrouper et agréger selon la granularité
        switch (granularity) {
            case "minute":
                return aggregateByTimeUnit(dataPoints, ChronoUnit.MINUTES);
            case "hour": 
                return aggregateByTimeUnit(dataPoints, ChronoUnit.HOURS);
            case "day":
                return aggregateByTimeUnit(dataPoints, ChronoUnit.DAYS);
            case "raw":
            default:
                // Simple échantillonnage si pas d'agrégation mais trop de points
                return sampleDataPoints(dataPoints, maxPoints);
        }
    }

    // Convertir un PriceDataPoint en Map
    private Map<String, Object> convertDataPointToMap(PriceDataPoint point) {
        Map<String, Object> pointData = new HashMap<>();
        pointData.put("timestamp", point.getTimestamp().toString());
        pointData.put("openBuy", point.getOpenBuyPrice());
        pointData.put("closeBuy", point.getCloseBuyPrice());
        pointData.put("highBuy", point.getHighBuyPrice());
        pointData.put("lowBuy", point.getLowBuyPrice());
        pointData.put("openSell", point.getOpenSellPrice());
        pointData.put("closeSell", point.getCloseSellPrice());
        pointData.put("highSell", point.getHighSellPrice());
        pointData.put("lowSell", point.getLowSellPrice());
        pointData.put("volume", point.getVolume());
        return pointData;
    }

    // Échantillonnage de données pour réduire le nombre de points
    private List<Map<String, Object>> sampleDataPoints(List<PriceDataPoint> points, int maxPoints) {
        if (points.size() <= maxPoints) {
            return points.stream().map(this::convertDataPointToMap).collect(Collectors.toList());
        }
        
        List<Map<String, Object>> sampled = new ArrayList<>(maxPoints);
        
        // Toujours inclure le premier et dernier point
        sampled.add(convertDataPointToMap(points.get(0)));
        
        // Calculer l'intervalle d'échantillonnage pour obtenir environ maxPoints
        double step = (double) (points.size() - 2) / (maxPoints - 2);
        
        // Échantillonner les points intermédiaires
        for (double i = step; i < points.size() - 1; i += step) {
            int index = (int) Math.floor(i);
            sampled.add(convertDataPointToMap(points.get(index)));
        }
        
        // Ajouter le dernier point
        if (points.size() > 1) {
            sampled.add(convertDataPointToMap(points.get(points.size() - 1)));
        }
        
        return sampled;
    }

    // Agrégation des données par unité de temps
    private List<Map<String, Object>> aggregateByTimeUnit(List<PriceDataPoint> points, ChronoUnit unit) {
        Map<String, List<PriceDataPoint>> groups = new HashMap<>();
        
        // Grouper par l'unité de temps spécifiée
        for (PriceDataPoint point : points) {
            LocalDateTime truncated = point.getTimestamp().truncatedTo(unit);
            String key = truncated.toString();
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(point);
        }
        
        // Agréger chaque groupe
        List<Map<String, Object>> result = new ArrayList<>();
        
        for (Map.Entry<String, List<PriceDataPoint>> entry : groups.entrySet()) {
            List<PriceDataPoint> group = entry.getValue();
            
            // Trouver open/close/high/low
            PriceDataPoint first = group.get(0);
            PriceDataPoint last = group.get(group.size() - 1);
            
            double highBuy = group.stream().mapToDouble(PriceDataPoint::getHighBuyPrice).max().orElse(0);
            double lowBuy = group.stream().mapToDouble(PriceDataPoint::getLowBuyPrice).min().orElse(Double.MAX_VALUE);
            if (lowBuy == Double.MAX_VALUE) lowBuy = 0;
            
            double highSell = group.stream().mapToDouble(PriceDataPoint::getHighSellPrice).max().orElse(0);
            double lowSell = group.stream().mapToDouble(PriceDataPoint::getLowSellPrice).min().orElse(Double.MAX_VALUE);
            if (lowSell == Double.MAX_VALUE) lowSell = 0;
            
            double totalVolume = group.stream().mapToDouble(PriceDataPoint::getVolume).sum();
            
            Map<String, Object> aggregated = new HashMap<>();
            aggregated.put("timestamp", entry.getKey());
            aggregated.put("openBuy", first.getOpenBuyPrice());
            aggregated.put("closeBuy", last.getCloseBuyPrice());
            aggregated.put("highBuy", highBuy);
            aggregated.put("lowBuy", lowBuy);
            aggregated.put("openSell", first.getOpenSellPrice());
            aggregated.put("closeSell", last.getCloseSellPrice());
            aggregated.put("highSell", highSell);
            aggregated.put("lowSell", lowSell);
            aggregated.put("volume", totalVolume);
            
            result.add(aggregated);
        }
        
        // Trier par timestamp
        result.sort(Comparator.comparing(m -> m.get("timestamp").toString()));
        
        return result;
    }

    private void handleShopType(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            exchange.sendResponseHeaders(405, 0);
            exchange.getResponseBody().close();
            return;
        }
        
        Map<String, String[]> queryParams = parseQueryParams(exchange.getRequestURI().getQuery());
        String shopId = queryParams.containsKey("shop") ? queryParams.get("shop")[0] : null;
        String itemId = queryParams.containsKey("item") ? queryParams.get("item")[0] : null;
        
        if (shopId == null || itemId == null) {
            exchange.sendResponseHeaders(400, 0);
            exchange.getResponseBody().close();
            return;
        }
        
        DynaShopType generalType = plugin.getShopConfigManager().getTypeDynaShop(shopId, itemId);
        DynaShopType buyType = plugin.getShopConfigManager().getTypeDynaShop(shopId, itemId, "buy");
        DynaShopType sellType = plugin.getShopConfigManager().getTypeDynaShop(shopId, itemId, "sell");
        DynaShopType realBuyType = plugin.getShopConfigManager().getRealTypeDynaShop(shopId, itemId, "buy");
        DynaShopType realSellType = plugin.getShopConfigManager().getRealTypeDynaShop(shopId, itemId, "sell");
        
        Map<String, String> types = new HashMap<>();
        types.put("general", generalType.name());
        types.put("buy", buyType.name());
        types.put("sell", sellType.name());
        types.put("realBuy", realBuyType.name());
        types.put("realSell", realSellType.name());

        String jsonResponse = gson.toJson(types);
        sendJsonResponse(exchange, jsonResponse);
    }
    
    private void serveFile(HttpExchange exchange, Path filePath, String contentType) throws IOException {
        if (!Files.exists(filePath)) {
            exchange.sendResponseHeaders(404, 0);
            exchange.getResponseBody().close();
            return;
        }
        
        byte[] fileContent = Files.readAllBytes(filePath);
        
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, fileContent.length);
        
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(fileContent);
        }
    }
    
    private void sendJsonResponse(HttpExchange exchange, String jsonResponse) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        
        byte[] responseBytes = jsonResponse.getBytes();
        exchange.sendResponseHeaders(200, responseBytes.length);
        
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }
    
    private Map<String, String[]> parseQueryParams(String query) {
        Map<String, String[]> result = new HashMap<>();
        if (query == null) return result;
        
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=");
            if (keyValue.length == 2) {
                String key = keyValue[0];
                String value = keyValue[1];
                
                if (result.containsKey(key)) {
                    String[] values = result.get(key);
                    String[] newValues = new String[values.length + 1];
                    System.arraycopy(values, 0, newValues, 0, values.length);
                    newValues[values.length] = value;
                    result.put(key, newValues);
                } else {
                    result.put(key, new String[]{value});
                }
            }
        }
        
        return result;
    }
    
    // /**
    //  * Génère une URL de QR Code vers le dashboard
    //  */
    // public String generateQrCodeUrl(String shopId, String itemId) {
    //     String serverIp = Bukkit.getServer().getIp();
    //     if (serverIp == null || serverIp.isEmpty() || serverIp.equals("0.0.0.0")) {
    //         // Essayer d'obtenir une IP plus pertinente
    //         try {
    //             serverIp = java.net.InetAddress.getLocalHost().getHostAddress();
    //         } catch (Exception e) {
    //             serverIp = "localhost";
    //         }
    //     }
        
    //     String baseUrl = "http://" + serverIp + ":" + port;
    //     String dashboardUrl = baseUrl + "/?shop=" + shopId + "&item=" + itemId;
        
    //     // URL de l'API Google Charts pour générer un QR code
    //     return "https://chart.googleapis.com/chart?cht=qr&chs=300x300&chld=L|0&chl=" + java.net.URLEncoder.encode(dashboardUrl, java.nio.charset.StandardCharsets.UTF_8);
    // }

    /**
     * Gère les prévisions de prix basées sur l'historique
     */
    private void handlePriceForecast(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            exchange.sendResponseHeaders(405, 0);
            exchange.getResponseBody().close();
            return;
        }
        
        Map<String, String[]> queryParams = parseQueryParams(exchange.getRequestURI().getQuery());
        String shopId = queryParams.containsKey("shop") ? queryParams.get("shop")[0] : null;
        String itemId = queryParams.containsKey("item") ? queryParams.get("item")[0] : null;
        String periodStr = queryParams.containsKey("period") ? queryParams.get("period")[0] : "1w";
        int forecastDays = queryParams.containsKey("days") ? Integer.parseInt(queryParams.get("days")[0]) : 7;
        
        if (shopId == null || itemId == null) {
            exchange.sendResponseHeaders(400, 0);
            exchange.getResponseBody().close();
            return;
        }
        
        try {
            // Récupérer l'historique des prix
            PriceHistory history = plugin.getStorageManager().getPriceHistory(shopId, itemId);
            List<PriceDataPoint> dataPoints = history.getDataPoints();
            
            // Filtrer par période
            List<PriceDataPoint> filteredPoints = filterByPeriod(dataPoints, periodStr);
            
            // Calculer les prévisions
            List<Map<String, Object>> forecastData = generatePriceForecast(filteredPoints, forecastDays);
            
            // Réponse
            Map<String, Object> response = new HashMap<>();
            response.put("historicalData", filteredPoints.stream()
                .map(this::convertDataPointToMap)
                .collect(Collectors.toList()));
            response.put("forecastData", forecastData);
            
            String jsonResponse = gson.toJson(response);
            sendJsonResponse(exchange, jsonResponse);
        } catch (Exception e) {
            plugin.getLogger().severe("Error generating price forecast: " + e.getMessage());
            e.printStackTrace();
            String errorJson = "{\"error\": \"Failed to generate price forecast\"}";
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(500, errorJson.length());
            exchange.getResponseBody().write(errorJson.getBytes());
            exchange.getResponseBody().close();
        }
    }

    /**
     * Génère des prévisions de prix basées sur l'historique
     */
    private List<Map<String, Object>> generatePriceForecast(List<PriceDataPoint> historyPoints, int daysToForecast) {
        // Si pas assez de données historiques pour faire une prévision
        if (historyPoints.size() < 5) {
            return List.of();
        }
        
        List<Map<String, Object>> forecastPoints = new ArrayList<>();
        
        // 1. Calculer la tendance (moyenne mobile simple)
        double[] buyPrices = historyPoints.stream()
            .mapToDouble(PriceDataPoint::getCloseBuyPrice)
            .filter(price -> price > 0)
            .toArray();
        
        double[] sellPrices = historyPoints.stream()
            .mapToDouble(PriceDataPoint::getCloseSellPrice)
            .filter(price -> price > 0)
            .toArray();
        
        if (buyPrices.length == 0 && sellPrices.length == 0) {
            return List.of();
        }
        
        // 2. Calculer les coefficients de tendance linéaire (y = ax + b)
        double buyTrendCoef = 0;
        double buyYIntercept = 0;
        double sellTrendCoef = 0;
        double sellYIntercept = 0;
        
        if (buyPrices.length > 1) {
            double[] buyTrend = calculateLinearTrend(buyPrices);
            buyTrendCoef = buyTrend[0];
            buyYIntercept = buyTrend[1];
        }
        
        if (sellPrices.length > 1) {
            double[] sellTrend = calculateLinearTrend(sellPrices);
            sellTrendCoef = sellTrend[0];
            sellYIntercept = sellTrend[1];
        }
        
        // 3. Générer les points de prévision
        LocalDateTime lastTimestamp = historyPoints.get(historyPoints.size() - 1).getTimestamp();
        double lastBuyPrice = buyPrices.length > 0 ? buyPrices[buyPrices.length - 1] : -1;
        double lastSellPrice = sellPrices.length > 0 ? sellPrices[sellPrices.length - 1] : -1;
        
        // Générer un point par jour
        for (int i = 1; i <= daysToForecast; i++) {
            LocalDateTime forecastTime = lastTimestamp.plusDays(i);
            
            double forecastBuyPrice = -1;
            double forecastSellPrice = -1;
            
            // Appliquer la tendance linéaire
            if (buyPrices.length > 0) {
                forecastBuyPrice = buyYIntercept + buyTrendCoef * (buyPrices.length + i);
                // Assurer que le prix ne descend pas en dessous de 0
                forecastBuyPrice = Math.max(0.01, forecastBuyPrice);
            }
            
            if (sellPrices.length > 0) {
                forecastSellPrice = sellYIntercept + sellTrendCoef * (sellPrices.length + i);
                // Assurer que le prix ne descend pas en dessous de 0
                forecastSellPrice = Math.max(0.01, forecastSellPrice);
            }
            
            Map<String, Object> point = new HashMap<>();
            point.put("timestamp", forecastTime.toString());
            point.put("openBuy", forecastBuyPrice);
            point.put("closeBuy", forecastBuyPrice);
            point.put("highBuy", forecastBuyPrice * 1.05); // Ajouter un peu de variabilité
            point.put("lowBuy", forecastBuyPrice * 0.95);  // Ajouter un peu de variabilité
            point.put("openSell", forecastSellPrice);
            point.put("closeSell", forecastSellPrice);
            point.put("highSell", forecastSellPrice * 1.05);
            point.put("lowSell", forecastSellPrice * 0.95);
            point.put("volume", 0); // Pas de volume pour les prévisions
            point.put("forecast", true); // Indicateur de prévision
            
            forecastPoints.add(point);
        }
        
        return forecastPoints;
    }

    /**
     * Calcule la tendance linéaire d'une série de données
     * @return double[] avec [0] = coefficient directeur, [1] = ordonnée à l'origine
     */
    private double[] calculateLinearTrend(double[] prices) {
        int n = prices.length;
        
        // Calcul des sommes nécessaires
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        for (int i = 0; i < n; i++) {
            sumX += i;
            sumY += prices[i];
            sumXY += i * prices[i];
            sumX2 += i * i;
        }
        
        // Calcul des coefficients de régression (y = ax + b)
        double a = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
        double b = (sumY - a * sumX) / n;
        
        return new double[] { a, b };
    }

    /**
     * Gère les données de corrélation entre items
     */
    private void handleItemCorrelations(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            exchange.sendResponseHeaders(405, 0);
            exchange.getResponseBody().close();
            return;
        }
        
        Map<String, String[]> queryParams = parseQueryParams(exchange.getRequestURI().getQuery());
        String shopId = queryParams.containsKey("shop") ? queryParams.get("shop")[0] : null;
        String itemId = queryParams.containsKey("item") ? queryParams.get("item")[0] : null;
        int maxItems = queryParams.containsKey("max") ? Integer.parseInt(queryParams.get("max")[0]) : 10;
        
        if (shopId == null || itemId == null) {
            exchange.sendResponseHeaders(400, 0);
            exchange.getResponseBody().close();
            return;
        }
        
        try {
            // Récupérer l'historique des prix pour l'item de référence
            PriceHistory targetHistory = plugin.getStorageManager().getPriceHistory(shopId, itemId);
            List<PriceDataPoint> targetPoints = targetHistory.getDataPoints();
            
            if (targetPoints.isEmpty()) {
                String errorJson = "{\"error\": \"No price history found for the item\"}";
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(404, errorJson.length());
                exchange.getResponseBody().write(errorJson.getBytes());
                exchange.getResponseBody().close();
                return;
            }
            
            // Obtenir tous les items du même shop
            Shop shop = ShopGuiPlusApi.getPlugin().getShopManager().getShopById(shopId);
            if (shop == null) {
                exchange.sendResponseHeaders(404, 0);
                exchange.getResponseBody().close();
                return;
            }
            
            // Liste pour stocker les corrélations
            List<Map<String, Object>> correlations = new ArrayList<>();
            
            // Extraire les timestamps et prix de l'item cible
            Map<LocalDateTime, Double> targetPrices = new HashMap<>();
            for (PriceDataPoint point : targetPoints) {
                if (point.getCloseBuyPrice() > 0) {
                    targetPrices.put(point.getTimestamp(), point.getCloseBuyPrice());
                }
            }
            
            // Comparer avec d'autres items du même shop
            for (ShopItem shopItem : shop.getShopItems()) {
                String compareItemId = shopItem.getId();
                
                // Ne pas comparer l'item avec lui-même
                if (compareItemId.equals(itemId)) {
                    continue;
                }
                
                PriceHistory compareHistory = plugin.getStorageManager().getPriceHistory(shopId, compareItemId);
                List<PriceDataPoint> comparePoints = compareHistory.getDataPoints();
                
                if (comparePoints.isEmpty()) {
                    continue;
                }
                
                // Extraire les timestamps et prix de l'item comparé
                Map<LocalDateTime, Double> comparePrices = new HashMap<>();
                for (PriceDataPoint point : comparePoints) {
                    if (point.getCloseBuyPrice() > 0) {
                        comparePrices.put(point.getTimestamp(), point.getCloseBuyPrice());
                    }
                }
                
                // Calculer la corrélation entre les deux séries temporelles
                double correlationCoefficient = calculateCorrelationCoefficient(targetPrices, comparePrices);
                
                // Ne garder que les corrélations significatives (positives ou négatives)
                if (!Double.isNaN(correlationCoefficient) && Math.abs(correlationCoefficient) > 0.3) {
                    Map<String, Object> correlationData = new HashMap<>();
                    correlationData.put("itemId", compareItemId);
                    correlationData.put("itemName", plugin.getShopConfigManager().getItemName(null, shopId, compareItemId));
                    correlationData.put("correlation", correlationCoefficient);
                    correlationData.put("correlationType", correlationCoefficient > 0 ? "positive" : "negative");
                    correlationData.put("strength", Math.abs(correlationCoefficient));
                    
                    correlations.add(correlationData);
                }
            }
            
            // Trier par force de corrélation décroissante
            correlations.sort((a, b) -> Double.compare((double) b.get("strength"), (double) a.get("strength")));
            
            // Limiter au nombre maximum demandé
            if (correlations.size() > maxItems) {
                correlations = correlations.subList(0, maxItems);
            }
            
            // Préparer la réponse
            Map<String, Object> response = new HashMap<>();
            response.put("targetItem", plugin.getShopConfigManager().getItemName(null, shopId, itemId));
            response.put("correlations", correlations);
            
            String jsonResponse = gson.toJson(response);
            sendJsonResponse(exchange, jsonResponse);
        } catch (Exception e) {
            plugin.getLogger().severe("Error calculating item correlations: " + e.getMessage());
            e.printStackTrace();
            String errorJson = "{\"error\": \"Failed to calculate item correlations\"}";
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(500, errorJson.length());
            exchange.getResponseBody().write(errorJson.getBytes());
            exchange.getResponseBody().close();
        }
    }

    /**
     * Calcule le coefficient de corrélation entre deux séries temporelles de prix
     */
    private double calculateCorrelationCoefficient(Map<LocalDateTime, Double> series1, Map<LocalDateTime, Double> series2) {
        // Identifier les timestamps communs aux deux séries
        Set<LocalDateTime> commonTimestamps = new HashSet<>(series1.keySet());
        commonTimestamps.retainAll(series2.keySet());
        
        // S'il y a moins de 5 points communs, la corrélation n'est pas fiable
        if (commonTimestamps.size() < 5) {
            return Double.NaN;
        }
        
        // Extraire les valeurs communes aux deux séries
        List<Double> values1 = new ArrayList<>();
        List<Double> values2 = new ArrayList<>();
        
        for (LocalDateTime timestamp : commonTimestamps) {
            values1.add(series1.get(timestamp));
            values2.add(series2.get(timestamp));
        }
        
        // Calculer les moyennes
        double mean1 = values1.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double mean2 = values2.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        
        // Calculer la covariance et les écarts-types
        double covariance = 0;
        double variance1 = 0;
        double variance2 = 0;
        
        for (int i = 0; i < values1.size(); i++) {
            double diff1 = values1.get(i) - mean1;
            double diff2 = values2.get(i) - mean2;
            
            covariance += diff1 * diff2;
            variance1 += diff1 * diff1;
            variance2 += diff2 * diff2;
        }
        
        // Calculer le coefficient de corrélation de Pearson
        if (variance1 == 0 || variance2 == 0) {
            return 0; // Pas de corrélation si l'une des séries est constante
        }
        
        return covariance / Math.sqrt(variance1 * variance2);
    }

    /**
     * Gère les requêtes d'historique de transactions par joueur
     */
    private void handlePlayerTransactions(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            exchange.sendResponseHeaders(405, 0);
            exchange.getResponseBody().close();
            return;
        }
        
        Map<String, String[]> queryParams = parseQueryParams(exchange.getRequestURI().getQuery());
        String playerUuid = queryParams.containsKey("player") ? queryParams.get("player")[0] : null;
        String shopId = queryParams.containsKey("shop") ? queryParams.get("shop")[0] : null;
        String period = queryParams.containsKey("period") ? queryParams.get("period")[0] : "7d";
        int limit = queryParams.containsKey("limit") ? Integer.parseInt(queryParams.get("limit")[0]) : 100;
        
        // Le paramètre player est obligatoire
        if (playerUuid == null) {
            exchange.sendResponseHeaders(400, 0);
            exchange.getResponseBody().close();
            return;
        }
        
        try {
            // Déterminer la date de début en fonction de la période
            LocalDateTime startDate = null;
            switch (period) {
                case "1d":
                    startDate = LocalDateTime.now().minusDays(1);
                    break;
                case "7d":
                    startDate = LocalDateTime.now().minusDays(7);
                    break;
                case "30d":
                    startDate = LocalDateTime.now().minusDays(30);
                    break;
                case "all":
                default:
                    startDate = LocalDateTime.now().minusYears(10); // Pratiquement illimité
                    break;
            }
            
            // Récupérer les transactions du joueur
            List<TransactionRecord> transactions = plugin.getStorageManager().getPlayerTransactions(playerUuid, shopId, startDate, limit);
            
            // Convertir les transactions en format approprié pour le JSON
            List<Map<String, Object>> transactionsData = new ArrayList<>();
            
            for (TransactionRecord transaction : transactions) {
                Map<String, Object> transactionMap = new HashMap<>();
                transactionMap.put("id", transaction.getId());
                transactionMap.put("timestamp", transaction.getTimestamp().toString());
                transactionMap.put("playerName", transaction.getPlayerName());
                transactionMap.put("playerUuid", transaction.getPlayerUuid().toString());
                transactionMap.put("shopId", transaction.getShopId());
                transactionMap.put("itemId", transaction.getItemId());
                transactionMap.put("itemName", transaction.getItemName());
                transactionMap.put("quantity", transaction.getQuantity());
                transactionMap.put("unitPrice", transaction.getUnitPrice());
                transactionMap.put("totalPrice", transaction.getTotalPrice());
                transactionMap.put("transactionType", transaction.isBuy() ? "BUY" : "SELL");
                
                transactionsData.add(transactionMap);
            }
            
            // Préparer des statistiques agrégées
            Map<String, Object> stats = new HashMap<>();
            
            // Nombre total de transactions
            stats.put("totalTransactions", transactions.size());
            
            // Montant total dépensé (achats)
            double totalSpent = transactions.stream()
                .filter(TransactionRecord::isBuy)
                .mapToDouble(TransactionRecord::getTotalPrice)
                .sum();
            stats.put("totalSpent", totalSpent);
            
            // Montant total gagné (ventes)
            double totalEarned = transactions.stream()
                .filter(t -> !t.isBuy())
                .mapToDouble(TransactionRecord::getTotalPrice)
                .sum();
            stats.put("totalEarned", totalEarned);
            
            // Items les plus achetés
            Map<String, Integer> topBoughtItems = transactions.stream()
                .filter(TransactionRecord::isBuy)
                .collect(Collectors.groupingBy(TransactionRecord::getItemId, Collectors.summingInt(TransactionRecord::getQuantity)));
            
            // Convertir en liste triée pour le JSON
            List<Map<String, Object>> topBoughtList = topBoughtItems.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .map(entry -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("itemId", entry.getKey());
                    item.put("quantity", entry.getValue());
                    // Trouver le nom de l'item à partir des transactions
                    String itemName = transactions.stream()
                        .filter(t -> t.getItemId().equals(entry.getKey()))
                        .map(TransactionRecord::getItemName)
                        .findFirst()
                        .orElse(entry.getKey());
                    item.put("itemName", itemName);
                    return item;
                })
                .collect(Collectors.toList());
            stats.put("topBoughtItems", topBoughtList);
            
            // Items les plus vendus (même approche)
            Map<String, Integer> topSoldItems = transactions.stream()
                .filter(t -> !t.isBuy())
                .collect(Collectors.groupingBy(TransactionRecord::getItemId, Collectors.summingInt(TransactionRecord::getQuantity)));
            
            List<Map<String, Object>> topSoldList = topSoldItems.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .map(entry -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("itemId", entry.getKey());
                    item.put("quantity", entry.getValue());
                    String itemName = transactions.stream()
                        .filter(t -> t.getItemId().equals(entry.getKey()))
                        .map(TransactionRecord::getItemName)
                        .findFirst()
                        .orElse(entry.getKey());
                    item.put("itemName", itemName);
                    return item;
                })
                .collect(Collectors.toList());
            stats.put("topSoldItems", topSoldList);
            
            // Créer la réponse finale
            Map<String, Object> response = new HashMap<>();
            response.put("transactions", transactionsData);
            response.put("stats", stats);
            
            String jsonResponse = gson.toJson(response);
            sendJsonResponse(exchange, jsonResponse);
        } catch (Exception e) {
            plugin.getLogger().severe("Error retrieving player transactions: " + e.getMessage());
            e.printStackTrace();
            String errorJson = "{\"error\": \"Failed to retrieve player transactions\"}";
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(500, errorJson.length());
            exchange.getResponseBody().write(errorJson.getBytes());
            exchange.getResponseBody().close();
        }
    }

    /**
     * Gère les requêtes de données pour la carte thermique des produits
     */
    private void handleHeatmapData(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            exchange.sendResponseHeaders(405, 0);
            exchange.getResponseBody().close();
            return;
        }
        
        Map<String, String[]> queryParams = parseQueryParams(exchange.getRequestURI().getQuery());
        String shopId = queryParams.containsKey("shop") ? queryParams.get("shop")[0] : null;
        String period = queryParams.containsKey("period") ? queryParams.get("period")[0] : "7d";
        String type = queryParams.containsKey("type") ? queryParams.get("type")[0] : "volume"; // volume, price, variation
        
        try {
            // Déterminer la date de début en fonction de la période
            LocalDateTime startDate = LocalDateTime.now();
            switch (period) {
                case "1d":
                    startDate = startDate.minusDays(1);
                    break;
                case "7d":
                    startDate = startDate.minusDays(7);
                    break;
                case "30d":
                    startDate = startDate.minusDays(30);
                    break;
                case "all":
                    startDate = startDate.minusYears(10); // Pratiquement illimité
                    break;
            }
            
            // Récupérer toutes les transactions de la période
            List<TransactionRecord> allTransactions = plugin.getStorageManager().getAllTransactions(shopId, startDate);
            
            // Si aucun shop spécifié, regrouper par shop et par item
            Map<String, Map<String, HeatmapItemData>> shopItemsData = new HashMap<>();
            
            for (TransactionRecord transaction : allTransactions) {
                String currentShopId = transaction.getShopId();
                String itemId = transaction.getItemId();
                
                // Initialiser les maps si nécessaire
                shopItemsData.putIfAbsent(currentShopId, new HashMap<>());
                
                Map<String, HeatmapItemData> itemsData = shopItemsData.get(currentShopId);
                itemsData.putIfAbsent(itemId, new HeatmapItemData(itemId, transaction.getItemName()));
                
                HeatmapItemData itemData = itemsData.get(itemId);
                
                // Mettre à jour les statistiques
                if (transaction.isBuy()) {
                    itemData.addBuyTransaction(transaction.getQuantity(), transaction.getTotalPrice());
                } else {
                    itemData.addSellTransaction(transaction.getQuantity(), transaction.getTotalPrice());
                }
            }
            
            // Convertir les données pour le heatmap
            List<Map<String, Object>> heatmapData = new ArrayList<>();
            
            for (Map.Entry<String, Map<String, HeatmapItemData>> shopEntry : shopItemsData.entrySet()) {
                String currentShopId = shopEntry.getKey();
                
                // Obtenir le nom du shop
                String shopName = "Unknown Shop";
                Shop shop = ShopGuiPlusApi.getPlugin().getShopManager().getShopById(currentShopId);
                if (shop != null) {
                    shopName = shop.getName();
                }
                
                // Traiter tous les items du shop
                for (HeatmapItemData itemData : shopEntry.getValue().values()) {
                    Map<String, Object> dataPoint = new HashMap<>();
                    dataPoint.put("shopId", currentShopId);
                    dataPoint.put("shopName", shopName);
                    dataPoint.put("itemId", itemData.getItemId());
                    dataPoint.put("itemName", itemData.getItemName());
                    
                    // Sélectionner la valeur à utiliser pour la carte thermique selon le type demandé
                    switch (type) {
                        case "volume":
                            dataPoint.put("value", itemData.getTotalVolume());
                            dataPoint.put("buyVolume", itemData.getBuyVolume());
                            dataPoint.put("sellVolume", itemData.getSellVolume());
                            break;
                        case "price":
                            dataPoint.put("value", itemData.getAveragePrice());
                            dataPoint.put("buyPrice", itemData.getAverageBuyPrice());
                            dataPoint.put("sellPrice", itemData.getAverageSellPrice());
                            break;
                        case "variation":
                            // Récupérer l'historique des prix pour calculer la variation
                            PriceHistory history = plugin.getStorageManager().getPriceHistory(currentShopId, itemData.getItemId());
                            List<PriceDataPoint> points = history.getDataPoints();
                            
                            double variation = 0;
                            if (points.size() >= 2) {
                                PriceDataPoint first = points.get(0);
                                PriceDataPoint last = points.get(points.size() - 1);
                                
                                double firstPrice = first.getCloseBuyPrice();
                                double lastPrice = last.getCloseBuyPrice();
                                
                                if (firstPrice > 0) {
                                    variation = ((lastPrice - firstPrice) / firstPrice) * 100;
                                }
                            }
                            
                            dataPoint.put("value", variation);
                            break;
                        default:
                            dataPoint.put("value", itemData.getTotalVolume());
                            break;
                    }
                    
                    heatmapData.add(dataPoint);
                }
            }
            
            // Trier les données selon la valeur (décroissante)
            heatmapData.sort((a, b) -> Double.compare((double) b.get("value"), (double) a.get("value")));
            
            Map<String, Object> response = new HashMap<>();
            response.put("period", period);
            response.put("type", type);
            response.put("data", heatmapData);
            
            String jsonResponse = gson.toJson(response);
            sendJsonResponse(exchange, jsonResponse);
        } catch (Exception e) {
            plugin.getLogger().severe("Error generating heatmap data: " + e.getMessage());
            e.printStackTrace();
            String errorJson = "{\"error\": \"Failed to generate heatmap data\"}";
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(500, errorJson.length());
            exchange.getResponseBody().write(errorJson.getBytes());
            exchange.getResponseBody().close();
        }
    }

    // Classe auxiliaire pour les données de heatmap
    private static class HeatmapItemData {
        private final String itemId;
        private final String itemName;
        private int buyVolume = 0;
        private int sellVolume = 0;
        private double buyValue = 0;
        private double sellValue = 0;
        
        public HeatmapItemData(String itemId, String itemName) {
            this.itemId = itemId;
            this.itemName = itemName;
        }
        
        public void addBuyTransaction(int quantity, double value) {
            buyVolume += quantity;
            buyValue += value;
        }
        
        public void addSellTransaction(int quantity, double value) {
            sellVolume += quantity;
            sellValue += value;
        }
        
        public String getItemId() {
            return itemId;
        }
        
        public String getItemName() {
            return itemName;
        }
        
        public int getBuyVolume() {
            return buyVolume;
        }
        
        public int getSellVolume() {
            return sellVolume;
        }
        
        public int getTotalVolume() {
            return buyVolume + sellVolume;
        }
        
        public double getAverageBuyPrice() {
            return buyVolume > 0 ? buyValue / buyVolume : 0;
        }
        
        public double getAverageSellPrice() {
            return sellVolume > 0 ? sellValue / sellVolume : 0;
        }
        
        public double getAveragePrice() {
            int totalVolume = getTotalVolume();
            return totalVolume > 0 ? (buyValue + sellValue) / totalVolume : 0;
        }
    }
}