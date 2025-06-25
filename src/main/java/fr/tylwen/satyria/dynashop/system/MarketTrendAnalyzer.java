/*
 * ShopGUI+ DynaShop - Dynamic Economy Addon for Minecraft
 * Copyright (C) 2025 Tylwen
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package fr.tylwen.satyria.dynashop.system;

import fr.tylwen.satyria.dynashop.DynaShopPlugin;
import fr.tylwen.satyria.dynashop.system.chart.PriceHistory;
import fr.tylwen.satyria.dynashop.system.chart.PriceHistory.PriceDataPoint;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Analyseur avancé de tendances du marché
 */
public class MarketTrendAnalyzer {
    
    private final DynaShopPlugin plugin;
    
    public MarketTrendAnalyzer(DynaShopPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Analyse la tendance du marché pour un item spécifique
     * 
     * @param shopId ID du shop
     * @param itemId ID de l'item
     * @param days Nombre de jours à analyser
     * @return Objet contenant les données de tendance
     */
    public MarketTrend analyzeTrend(String shopId, String itemId, int days) {
        // Récupérer l'historique des prix
        PriceHistory history = plugin.getStorageManager().getPriceHistory(shopId, itemId);
        List<PriceDataPoint> dataPoints = history.getDataPoints();
        
        // Filtrer les points de données pour la période demandée
        LocalDateTime startDate = LocalDateTime.now().minusDays(days);
        List<PriceDataPoint> filteredPoints = dataPoints.stream()
            .filter(p -> p.getTimestamp().isAfter(startDate))
            .collect(Collectors.toList());
        
        // S'il n'y a pas assez de données, retourner une tendance UNKNOWN
        if (filteredPoints.size() < 5) {
            return new MarketTrend(TrendType.UNKNOWN, 0, 0, 0, 0, 0,
                                  null, null, null, null, 
                                  List.of(), List.of(), List.of(), List.of());
        }
        
        // Analyse séparée pour prix d'achat et de vente
        TechnicalAnalysis buyAnalysis = analyzeBuyPrices(filteredPoints);
        TechnicalAnalysis sellAnalysis = analyzeSellPrices(filteredPoints);
        
        // Calculer les variations de prix et volume
        double buyPriceChange = calculatePriceChange(filteredPoints, true);
        double sellPriceChange = calculatePriceChange(filteredPoints, false);
        double volumeChange = calculateVolumeChange(filteredPoints);
        
        // Générer des prévisions pour achat et vente
        List<Map<String, Object>> buyForecastData = generateForecastData(filteredPoints, 7, true);
        List<Map<String, Object>> sellForecastData = generateForecastData(filteredPoints, 7, false);
        
        // Calculer les niveaux de support et résistance pour achat et vente
        List<Double> buySupportLevels = calculateSupportLevels(filteredPoints, true);
        List<Double> buyResistanceLevels = calculateResistanceLevels(filteredPoints, true);
        List<Double> sellSupportLevels = calculateSupportLevels(filteredPoints, false);
        List<Double> sellResistanceLevels = calculateResistanceLevels(filteredPoints, false);
        
        // Création de la tendance complète
        return new MarketTrend(
            determineCombinedTrend(buyAnalysis.getTrendType(), sellAnalysis.getTrendType()),
            Math.max(buyAnalysis.getStrength(), sellAnalysis.getStrength()),
            Math.max(buyAnalysis.getVolatility(), sellAnalysis.getVolatility()),
            buyPriceChange, 
            sellPriceChange,
            volumeChange,
            buyAnalysis,
            sellAnalysis,
            buyForecastData,
            sellForecastData,
            buySupportLevels,
            buyResistanceLevels,
            sellSupportLevels,
            sellResistanceLevels
        );
    }
    
    /**
     * Détermine la tendance combinée à partir des tendances d'achat et de vente
     */
    private TrendType determineCombinedTrend(TrendType buyTrend, TrendType sellTrend) {
        // Si les deux tendances sont identiques, c'est simple
        if (buyTrend == sellTrend) return buyTrend;
        
        // Si l'une des tendances est inconnue, on prend l'autre
        if (buyTrend == TrendType.UNKNOWN) return sellTrend;
        if (sellTrend == TrendType.UNKNOWN) return buyTrend;
        
        // Si l'une est volatile, la tendance générale est volatile
        if (buyTrend == TrendType.VOLATILE || sellTrend == TrendType.VOLATILE) 
            return TrendType.VOLATILE;
        
        // Si l'une monte et l'autre descend, c'est que le spread (écart) change
        if ((buyTrend == TrendType.RISING && sellTrend == TrendType.FALLING) ||
            (buyTrend == TrendType.FALLING && sellTrend == TrendType.RISING))
            return TrendType.SPREAD_CHANGING;
        
        // Si l'une est stable et l'autre monte ou descend, on privilégie le mouvement
        if (buyTrend == TrendType.STABLE) return sellTrend;
        if (sellTrend == TrendType.STABLE) return buyTrend;
        
        // Par défaut
        return TrendType.STABLE;
    }
    
    /**
     * Analyse technique complète des prix d'achat
     */
    private TechnicalAnalysis analyzeBuyPrices(List<PriceDataPoint> points) {
        // Extraire les prix d'achat
        double[] closePrices = points.stream()
            .mapToDouble(PriceDataPoint::getCloseBuyPrice)
            .filter(price -> price > 0)
            .toArray();
        
        if (closePrices.length < 5) {
            return new TechnicalAnalysis(TrendType.UNKNOWN, 0, 0);
        }
        
        // Tendance de base
        TrendType trendType = calculateBasicTrend(points, true);
        
        // Force de la tendance
        double strength = calculateTrendStrength(points, true);
        
        // Volatilité
        double volatility = calculateVolatility(points, true);
        
        // Indicateurs techniques
        double[] sma5 = calculateSMA(closePrices, 5);
        double[] sma20 = calculateSMA(closePrices, Math.min(20, closePrices.length / 2));
        double[] rsi = calculateRSI(closePrices, 14);
        MacdResult macd = calculateMACD(closePrices);
        BollingerBands bands = calculateBollingerBands(closePrices, 20, 2.0);
        
        // Détecter les divergences
        boolean priceRsiDivergence = detectPriceRsiDivergence(closePrices, rsi);
        
        // Générer les signaux d'achat/vente
        List<TradingSignal> signals = generateTradingSignals(
            closePrices, sma5, sma20, rsi, macd, bands, points
        );
        
        return new TechnicalAnalysis(
            trendType, strength, volatility,
            sma5, sma20, rsi, macd, bands, signals, priceRsiDivergence
        );
    }
    
    /**
     * Analyse technique complète des prix de vente
     */
    private TechnicalAnalysis analyzeSellPrices(List<PriceDataPoint> points) {
        // Code similaire à analyzeBuyPrices mais pour les prix de vente
        // Extraire les prix de vente
        double[] closePrices = points.stream()
            .mapToDouble(PriceDataPoint::getCloseSellPrice)
            .filter(price -> price > 0)
            .toArray();
        
        if (closePrices.length < 5) {
            return new TechnicalAnalysis(TrendType.UNKNOWN, 0, 0);
        }
        
        // Tendance de base
        TrendType trendType = calculateBasicTrend(points, false);
        
        // Force de la tendance
        double strength = calculateTrendStrength(points, false);
        
        // Volatilité
        double volatility = calculateVolatility(points, false);
        
        // Indicateurs techniques
        double[] sma5 = calculateSMA(closePrices, 5);
        double[] sma20 = calculateSMA(closePrices, Math.min(20, closePrices.length / 2));
        double[] rsi = calculateRSI(closePrices, 14);
        MacdResult macd = calculateMACD(closePrices);
        BollingerBands bands = calculateBollingerBands(closePrices, 20, 2.0);
        
        // Détecter les divergences
        boolean priceRsiDivergence = detectPriceRsiDivergence(closePrices, rsi);
        
        // Générer les signaux d'achat/vente
        List<TradingSignal> signals = generateTradingSignals(
            closePrices, sma5, sma20, rsi, macd, bands, points
        );
        
        return new TechnicalAnalysis(
            trendType, strength, volatility,
            sma5, sma20, rsi, macd, bands, signals, priceRsiDivergence
        );
    }
    
    /**
     * Détermine la tendance de base (hausse, baisse, stable, volatile)
     */
    private TrendType calculateBasicTrend(List<PriceDataPoint> points, boolean isBuy) {
        // Utiliser la régression linéaire pour déterminer la tendance
        double[] prices = isBuy ?
            points.stream().mapToDouble(PriceDataPoint::getCloseBuyPrice).filter(price -> price > 0).toArray() :
            points.stream().mapToDouble(PriceDataPoint::getCloseSellPrice).filter(price -> price > 0).toArray();
        
        if (prices.length < 3) {
            return TrendType.UNKNOWN;
        }
        
        double[] trend = calculateLinearTrend(prices);
        double slope = trend[0]; // coefficient directeur
        
        // Calculer la volatilité
        double volatility = calculateVolatility(points, isBuy);
        
        // Déterminer le type de tendance
        if (Math.abs(slope) < 0.001) {
            return TrendType.STABLE;
        } else if (volatility > 0.15) { // Seuil arbitraire de volatilité
            return TrendType.VOLATILE;
        } else if (slope > 0) {
            return TrendType.RISING;
        } else {
            return TrendType.FALLING;
        }
    }
    
    /**
     * Calcule la force de la tendance (0-1)
     */
    private double calculateTrendStrength(List<PriceDataPoint> points, boolean isBuy) {
        double[] prices = isBuy ?
            points.stream().mapToDouble(PriceDataPoint::getCloseBuyPrice).filter(price -> price > 0).toArray() :
            points.stream().mapToDouble(PriceDataPoint::getCloseSellPrice).filter(price -> price > 0).toArray();
        
        if (prices.length < 3) {
            return 0;
        }
        
        double[] trend = calculateLinearTrend(prices);
        double slope = trend[0]; // coefficient directeur
        double intercept = trend[1]; // ordonnée à l'origine
        
        // Calculer le R² (coefficient de détermination)
        double rSquared = calculateRSquared(prices, slope, intercept);
        
        // La force de la tendance est directement liée au R²
        return Math.min(1.0, Math.abs(rSquared));
    }
    
    /**
     * Calcule la volatilité du prix (écart-type relatif)
     */
    private double calculateVolatility(List<PriceDataPoint> points, boolean isBuy) {
        double[] prices = isBuy ?
            points.parallelStream().mapToDouble(PriceDataPoint::getCloseBuyPrice).filter(price -> price > 0).toArray() :
            points.parallelStream().mapToDouble(PriceDataPoint::getCloseSellPrice).filter(price -> price > 0).toArray();

        if (prices.length < 3) {
            return 0;
        }
        
        // Calculer l'écart-type
        double mean = Arrays.stream(prices).parallel().average().orElse(0);
        double variance = Arrays.stream(prices).parallel()
            .map(p -> Math.pow(p - mean, 2))
            .sum() / prices.length;
        double stdDev = Math.sqrt(variance);
        
        // Normaliser par le prix moyen pour obtenir une volatilité relative
        return mean > 0 ? stdDev / mean : 0;
    }
    
    /**
     * Calcule le pourcentage de variation de prix
     */
    private double calculatePriceChange(List<PriceDataPoint> points, boolean isBuy) {
        if (points.isEmpty()) return 0;
        
        double firstPrice = isBuy ? 
            points.get(0).getCloseBuyPrice() : 
            points.get(0).getCloseSellPrice();
            
        double lastPrice = isBuy ? 
            points.get(points.size() - 1).getCloseBuyPrice() : 
            points.get(points.size() - 1).getCloseSellPrice();
        
        if (firstPrice <= 0) return 0;
        
        return ((lastPrice - firstPrice) / firstPrice) * 100;
    }

    /**
     * Calcule le pourcentage de variation du volume
     */
    private double calculateVolumeChange(List<PriceDataPoint> points) {
        if (points.size() < 2) return 0;
        
        // Diviser la période en deux pour comparer les volumes
        int midPoint = points.size() / 2;
        
        double volumeFirstHalf = points.subList(0, midPoint).stream()
            .mapToDouble(PriceDataPoint::getVolume)
            .sum();
        
        double volumeSecondHalf = points.subList(midPoint, points.size()).stream()
            .mapToDouble(PriceDataPoint::getVolume)
            .sum();
        
        if (volumeFirstHalf <= 0) return 0;
        
        return ((volumeSecondHalf - volumeFirstHalf) / volumeFirstHalf) * 100;
    }
    
    // Nouvelles méthodes pour les indicateurs techniques
    
    /**
     * Calcule la moyenne mobile simple (SMA)
     */
    private double[] calculateSMA(double[] prices, int period) {
        if (prices.length < period) {
            return new double[0];
        }
        
        double[] sma = new double[prices.length - period + 1];
        
        for (int i = 0; i < sma.length; i++) {
            double sum = 0;
            for (int j = 0; j < period; j++) {
                sum += prices[i + j];
            }
            sma[i] = sum / period;
        }
        
        return sma;
    }
    
    /**
     * Calcule le RSI (Relative Strength Index)
     */
    private double[] calculateRSI(double[] prices, int period) {
        if (prices.length <= period) {
            return new double[0];
        }
        
        double[] rsi = new double[prices.length - period];
        double[] gains = new double[prices.length - 1];
        double[] losses = new double[prices.length - 1];
        
        // Calculer les gains et pertes
        for (int i = 1; i < prices.length; i++) {
            double change = prices[i] - prices[i - 1];
            gains[i - 1] = Math.max(0, change);
            losses[i - 1] = Math.max(0, -change);
        }
        
        // Calculer le RSI
        for (int i = 0; i < rsi.length; i++) {
            double avgGain = 0;
            double avgLoss = 0;
            
            // Premier RSI basé sur la moyenne simple
            if (i == 0) {
                for (int j = 0; j < period; j++) {
                    avgGain += gains[j];
                    avgLoss += losses[j];
                }
                avgGain /= period;
                avgLoss /= period;
            } 
            // RSI suivants basés sur la moyenne mobile
            else {
                avgGain = (rsi[i - 1] * (period - 1) + gains[i + period - 1]) / period;
                avgLoss = (rsi[i - 1] * (period - 1) + losses[i + period - 1]) / period;
            }
            
            // Formule du RSI
            double rs = avgGain / (avgLoss > 0 ? avgLoss : 0.001); // Éviter division par zéro
            rsi[i] = 100 - (100 / (1 + rs));
        }
        
        return rsi;
    }
    
    /**
     * Calcule le MACD (Moving Average Convergence Divergence)
     */
    private MacdResult calculateMACD(double[] prices) {
        if (prices.length < 26) {
            return new MacdResult(new double[0], new double[0], new double[0]);
        }
        
        // Paramètres standard du MACD
        int fastPeriod = 12;
        int slowPeriod = 26;
        int signalPeriod = 9;
        
        // Calculer les moyennes mobiles exponentielles
        double[] emaFast = calculateEMA(prices, fastPeriod);
        double[] emaSlow = calculateEMA(prices, slowPeriod);
        
        // Calculer la ligne MACD (différence entre les deux EMA)
        double[] macdLine = new double[emaSlow.length];
        for (int i = 0; i < macdLine.length; i++) {
            int fastIndex = i + (emaFast.length - emaSlow.length);
            macdLine[i] = emaFast[fastIndex] - emaSlow[i];
        }
        
        // Calculer la ligne de signal (EMA de la ligne MACD)
        double[] signalLine = calculateEMA(macdLine, signalPeriod);
        
        // Calculer l'histogramme (différence entre MACD et signal)
        double[] histogram = new double[signalLine.length];
        for (int i = 0; i < histogram.length; i++) {
            int macdIndex = i + (macdLine.length - signalLine.length);
            histogram[i] = macdLine[macdIndex] - signalLine[i];
        }
        
        return new MacdResult(macdLine, signalLine, histogram);
    }
    
    /**
     * Calcule la moyenne mobile exponentielle (EMA)
     */
    private double[] calculateEMA(double[] prices, int period) {
        if (prices.length < period) {
            return new double[0];
        }
        
        double[] ema = new double[prices.length - period + 1];
        // Facteur de lissage
        double k = 2.0 / (period + 1);
        
        // Premier EMA est une SMA
        double sum = 0;
        for (int i = 0; i < period; i++) {
            sum += prices[i];
        }
        ema[0] = sum / period;
        
        // Calculer les EMA suivants
        for (int i = 1; i < ema.length; i++) {
            ema[i] = prices[i + period - 1] * k + ema[i - 1] * (1 - k);
        }
        
        return ema;
    }
    
    /**
     * Calcule les bandes de Bollinger
     */
    private BollingerBands calculateBollingerBands(double[] prices, int period, double multiplier) {
        if (prices.length < period) {
            return new BollingerBands(new double[0], new double[0], new double[0]);
        }
        
        // Calculer la SMA
        double[] sma = calculateSMA(prices, period);
        
        // Calculer les bandes supérieure et inférieure
        double[] upper = new double[sma.length];
        double[] lower = new double[sma.length];
        
        for (int i = 0; i < sma.length; i++) {
            // Calculer l'écart-type
            double sum = 0;
            for (int j = 0; j < period; j++) {
                sum += Math.pow(prices[i + j] - sma[i], 2);
            }
            double stdDev = Math.sqrt(sum / period);
            
            // Calculer les bandes
            upper[i] = sma[i] + (multiplier * stdDev);
            lower[i] = sma[i] - (multiplier * stdDev);
        }
        
        return new BollingerBands(sma, upper, lower);
    }
    
    /**
     * Détecte les divergences entre le prix et le RSI
     */
    private boolean detectPriceRsiDivergence(double[] prices, double[] rsi) {
        if (prices.length < 10 || rsi.length < 5) {
            return false;
        }
        
        // Prendre les 10 dernières valeurs
        int startPrice = prices.length - 10;
        int startRsi = rsi.length - 5;
        
        // Vérifier si le prix monte mais le RSI descend (divergence baissière)
        boolean priceUptrend = prices[prices.length - 1] > prices[startPrice];
        boolean rsiDowntrend = rsi[rsi.length - 1] < rsi[startRsi];
        
        if (priceUptrend && rsiDowntrend) {
            return true;
        }
        
        // Vérifier si le prix descend mais le RSI monte (divergence haussière)
        boolean priceDowntrend = prices[prices.length - 1] < prices[startPrice];
        boolean rsiUptrend = rsi[rsi.length - 1] > rsi[startRsi];
        
        return priceDowntrend && rsiUptrend;
    }
    
    /**
     * Génère des signaux d'achat/vente basés sur les indicateurs techniques
     */
    private List<TradingSignal> generateTradingSignals(
            double[] prices, double[] sma5, double[] sma20, 
            double[] rsi, MacdResult macd, BollingerBands bands,
            List<PriceDataPoint> points) {
        
        List<TradingSignal> signals = new ArrayList<>();
        
        // Pas assez de données pour générer des signaux
        if (prices.length < 30 || sma5.length < 2 || sma20.length < 2 || 
            rsi.length < 2 || macd.macdLine.length < 2 || bands.middle.length < 2) {
            return signals;
        }
        
        // Dernier indice pour chaque indicateur
        int lastPrice = prices.length - 1;
        int lastSma5 = sma5.length - 1;
        int lastSma20 = sma20.length - 1;
        int lastRsi = rsi.length - 1;
        int lastMacd = macd.macdLine.length - 1;
        int lastSignal = macd.signalLine.length - 1;
        int lastBands = bands.middle.length - 1;
        
        // Croix de la mort/dorée (SMA5 croise SMA20)
        if (sma5[lastSma5 - 1] < sma20[lastSma20 - 1] && sma5[lastSma5] > sma20[lastSma20]) {
            signals.add(new TradingSignal(
                TradingSignalType.GOLDEN_CROSS, 
                "Croisement haussier SMA5 > SMA20",
                points.get(points.size() - 1).getTimestamp()
            ));
        } else if (sma5[lastSma5 - 1] > sma20[lastSma20 - 1] && sma5[lastSma5] < sma20[lastSma20]) {
            signals.add(new TradingSignal(
                TradingSignalType.DEATH_CROSS, 
                "Croisement baissier SMA5 < SMA20",
                points.get(points.size() - 1).getTimestamp()
            ));
        }
        
        // Signaux RSI (surachat/survente)
        if (rsi[lastRsi] > 70) {
            signals.add(new TradingSignal(
                TradingSignalType.OVERBOUGHT, 
                "RSI en zone de surachat (>" + Math.round(rsi[lastRsi]) + ")",
                points.get(points.size() - 1).getTimestamp()
            ));
        } else if (rsi[lastRsi] < 30) {
            signals.add(new TradingSignal(
                TradingSignalType.OVERSOLD, 
                "RSI en zone de survente (<" + Math.round(rsi[lastRsi]) + ")",
                points.get(points.size() - 1).getTimestamp()
            ));
        }
        
        // Signaux MACD
        if (lastMacd > 0 && lastSignal > 0) {
            if (macd.macdLine[lastMacd - 1] < macd.signalLine[lastSignal - 1] && 
                macd.macdLine[lastMacd] > macd.signalLine[lastSignal]) {
                signals.add(new TradingSignal(
                    TradingSignalType.MACD_BULLISH_CROSS, 
                    "Croisement haussier du MACD",
                    points.get(points.size() - 1).getTimestamp()
                ));
            } else if (macd.macdLine[lastMacd - 1] > macd.signalLine[lastSignal - 1] && 
                       macd.macdLine[lastMacd] < macd.signalLine[lastSignal]) {
                signals.add(new TradingSignal(
                    TradingSignalType.MACD_BEARISH_CROSS, 
                    "Croisement baissier du MACD",
                    points.get(points.size() - 1).getTimestamp()
                ));
            }
        }
        
        // Signaux Bollinger Bands
        if (prices[lastPrice] > bands.upper[lastBands]) {
            signals.add(new TradingSignal(
                TradingSignalType.PRICE_ABOVE_UPPER_BAND, 
                "Prix au-dessus de la bande supérieure de Bollinger",
                points.get(points.size() - 1).getTimestamp()
            ));
        } else if (prices[lastPrice] < bands.lower[lastBands]) {
            signals.add(new TradingSignal(
                TradingSignalType.PRICE_BELOW_LOWER_BAND, 
                "Prix en-dessous de la bande inférieure de Bollinger",
                points.get(points.size() - 1).getTimestamp()
            ));
        }
        
        return signals;
    }
    
    /**
     * Génère des données de prévision basées sur l'historique
     */
    private List<Map<String, Object>> generateForecastData(List<PriceDataPoint> points, int daysToForecast, boolean isBuy) {
        List<Map<String, Object>> forecast = new ArrayList<>();
        
        // Extraire les prix pour la régression
        double[] prices = isBuy ?
            points.stream().mapToDouble(PriceDataPoint::getCloseBuyPrice).filter(price -> price > 0).toArray() :
            points.stream().mapToDouble(PriceDataPoint::getCloseSellPrice).filter(price -> price > 0).toArray();
        
        if (prices.length < 5) {
            return forecast; // Pas assez de données pour la prévision
        }
        
        // Calculer la tendance linéaire
        double[] trend = calculateLinearTrend(prices);
        double slope = trend[0];
        double intercept = trend[1];
        
        // Calculer le R² pour évaluer la qualité de la prédiction
        double rSquared = calculateRSquared(prices, slope, intercept);
        
        // Ajuster la marge d'erreur en fonction de R²
        double errorMultiplier = 0.1 + (1.0 - rSquared) * 0.2;
        
        // Dernière date connue
        LocalDateTime lastDate = points.get(points.size() - 1).getTimestamp();
        
        // Générer les prévisions
        for (int i = 1; i <= daysToForecast; i++) {
            LocalDateTime forecastDate = lastDate.plusDays(i);
            
            // Prévision de prix (position x dans la série)
            double forecastPrice = intercept + slope * (prices.length + i);
            
            // Ajouter une marge d'erreur adaptative
            double errorMargin = forecastPrice * errorMultiplier;
            
            Map<String, Object> forecastPoint = new HashMap<>();
            forecastPoint.put("date", forecastDate.toString());
            forecastPoint.put("price", Math.max(0.01, forecastPrice));
            forecastPoint.put("lowEstimate", Math.max(0.01, forecastPrice - errorMargin));
            forecastPoint.put("highEstimate", forecastPrice + errorMargin);
            forecastPoint.put("confidence", rSquared);
            
            forecast.add(forecastPoint);
        }
        
        return forecast;
    }
    
    /**
     * Calcule les niveaux de support (prix où les baisses ont tendance à s'arrêter)
     */
    private List<Double> calculateSupportLevels(List<PriceDataPoint> points, boolean isBuy) {
        // Trouver les minima locaux
        List<Double> minimaPrices = findLocalMinima(points, isBuy);
        
        // Regrouper les minima proches
        return clusterPriceLevels(minimaPrices);
    }
    
    /**
     * Calcule les niveaux de résistance (prix où les hausses ont tendance à s'arrêter)
     */
    private List<Double> calculateResistanceLevels(List<PriceDataPoint> points, boolean isBuy) {
        // Trouver les maxima locaux
        List<Double> maximaPrices = findLocalMaxima(points, isBuy);
        
        // Regrouper les maxima proches
        return clusterPriceLevels(maximaPrices);
    }
    
    /**
     * Trouve les minima locaux dans les données de prix
     */
    private List<Double> findLocalMinima(List<PriceDataPoint> points, boolean isBuy) {
        List<Double> minima = new ArrayList<>();
        
        // On a besoin d'au moins 3 points pour trouver un minimum local
        if (points.size() < 3) return minima;
        
        for (int i = 1; i < points.size() - 1; i++) {
            double prev = isBuy ? points.get(i - 1).getLowBuyPrice() : points.get(i - 1).getLowSellPrice();
            double current = isBuy ? points.get(i).getLowBuyPrice() : points.get(i).getLowSellPrice();
            double next = isBuy ? points.get(i + 1).getLowBuyPrice() : points.get(i + 1).getLowSellPrice();
            
            // Si le point courant est un minimum local
            if (current > 0 && current < prev && current < next) {
                minima.add(current);
            }
        }
        
        return minima;
    }
    
    /**
     * Trouve les maxima locaux dans les données de prix
     */
    private List<Double> findLocalMaxima(List<PriceDataPoint> points, boolean isBuy) {
        List<Double> maxima = new ArrayList<>();
        
        // On a besoin d'au moins 3 points pour trouver un maximum local
        if (points.size() < 3) return maxima;
        
        for (int i = 1; i < points.size() - 1; i++) {
            double prev = isBuy ? points.get(i - 1).getHighBuyPrice() : points.get(i - 1).getHighSellPrice();
            double current = isBuy ? points.get(i).getHighBuyPrice() : points.get(i).getHighSellPrice();
            double next = isBuy ? points.get(i + 1).getHighBuyPrice() : points.get(i + 1).getHighSellPrice();
            
            // Si le point courant est un maximum local
            if (current > prev && current > next) {
                maxima.add(current);
            }
        }
        
        return maxima;
    }
    
    /**
     * Regroupe les niveaux de prix proches pour identifier les zones de support/résistance
     */
    private List<Double> clusterPriceLevels(List<Double> prices) {
        if (prices.isEmpty()) return List.of();
        
        // Trier les prix
        List<Double> sortedPrices = new ArrayList<>(prices);
        Collections.sort(sortedPrices);
        
        // Calculer le prix moyen pour déterminer la tolérance de regroupement
        double avgPrice = sortedPrices.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double clusterTolerance = avgPrice * 0.05; // 5% de tolérance
        
        // Regrouper les niveaux proches
        List<Double> clusters = new ArrayList<>();
        List<Double> currentCluster = new ArrayList<>();
        
        for (double price : sortedPrices) {
            if (currentCluster.isEmpty()) {
                currentCluster.add(price);
            } else {
                double clusterAvg = currentCluster.stream().mapToDouble(Double::doubleValue).average().orElse(0);
                
                if (Math.abs(price - clusterAvg) <= clusterTolerance) {
                    // Ajouter au cluster existant
                    currentCluster.add(price);
                } else {
                    // Finaliser le cluster actuel et en commencer un nouveau
                    clusters.add(currentCluster.stream().mapToDouble(Double::doubleValue).average().orElse(0));
                    currentCluster = new ArrayList<>();
                    currentCluster.add(price);
                }
            }
        }
        
        // Ajouter le dernier cluster
        if (!currentCluster.isEmpty()) {
            clusters.add(currentCluster.stream().mapToDouble(Double::doubleValue).average().orElse(0));
        }
        
        return clusters;
    }
    
    // Méthodes mathématiques pour les analyses
    
    /**
     * Calcule la tendance linéaire (régression linéaire)
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
        double denominator = n * sumX2 - sumX * sumX;
        if (Math.abs(denominator) < 1e-10) {
            // Éviter la division par zéro
            return new double[] { 0, prices.length > 0 ? prices[0] : 0 };
        }
        
        double a = (n * sumXY - sumX * sumY) / denominator;
        double b = (sumY - a * sumX) / n;
        
        return new double[] { a, b };
    }
    
    /**
     * Calcule le coefficient de détermination R²
     */
    private double calculateRSquared(double[] prices, double slope, double intercept) {
        int n = prices.length;
        
        // Calcul de la moyenne
        double mean = Arrays.stream(prices).average().orElse(0);
        
        // Calcul de SST (Sum of Squares Total)
        double sst = 0;
        for (double price : prices) {
            sst += Math.pow(price - mean, 2);
        }
        
        // Calcul de SSR (Sum of Squares Regression)
        double ssr = 0;
        for (int i = 0; i < n; i++) {
            double fitted = intercept + slope * i;
            ssr += Math.pow(fitted - mean, 2);
        }
        
        // R² = SSR/SST
        return sst > 0 ? ssr / sst : 0;
    }
    
    /**
     * Types de tendances possibles
     */
    public enum TrendType {
        RISING,         // Tendance à la hausse
        FALLING,        // Tendance à la baisse
        STABLE,         // Prix stable
        VOLATILE,       // Prix volatil (hausse et baisse fréquentes)
        SPREAD_CHANGING, // L'écart entre prix d'achat et de vente change
        UNKNOWN         // Pas assez de données pour déterminer
    }
    
    /**
     * Types de signaux de trading
     */
    public enum TradingSignalType {
        GOLDEN_CROSS,        // SMA courte croise SMA longue vers le haut
        DEATH_CROSS,         // SMA courte croise SMA longue vers le bas
        OVERBOUGHT,          // RSI en zone de surachat (>70)
        OVERSOLD,            // RSI en zone de survente (<30)
        MACD_BULLISH_CROSS,  // MACD croise sa ligne de signal vers le haut
        MACD_BEARISH_CROSS,  // MACD croise sa ligne de signal vers le bas
        PRICE_ABOVE_UPPER_BAND,  // Prix au-dessus de la bande supérieure de Bollinger
        PRICE_BELOW_LOWER_BAND   // Prix en-dessous de la bande inférieure de Bollinger
    }
    
    /**
     * Classe représentant un signal de trading
     */
    public static class TradingSignal {
        private final TradingSignalType type;
        private final String description;
        private final LocalDateTime timestamp;
        
        public TradingSignal(TradingSignalType type, String description, LocalDateTime timestamp) {
            this.type = type;
            this.description = description;
            this.timestamp = timestamp;
        }
        
        public TradingSignalType getType() {
            return type;
        }
        
        public String getDescription() {
            return description;
        }
        
        public LocalDateTime getTimestamp() {
            return timestamp;
        }
        
        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("type", type.name());
            map.put("description", description);
            map.put("timestamp", timestamp.toString());
            return map;
        }
    }
    
    /**
     * Classe contenant les résultats du calcul du MACD
     */
    public static class MacdResult {
        public final double[] macdLine;
        public final double[] signalLine;
        public final double[] histogram;
        
        public MacdResult(double[] macdLine, double[] signalLine, double[] histogram) {
            this.macdLine = macdLine;
            this.signalLine = signalLine;
            this.histogram = histogram;
        }
        
        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            
            // Ne prendre que les X derniers points pour alléger la réponse
            int pointsToTake = Math.min(30, macdLine.length);
            int start = Math.max(0, macdLine.length - pointsToTake);
            
            double[] macdSubset = Arrays.copyOfRange(macdLine, start, macdLine.length);
            double[] signalSubset = signalLine.length >= pointsToTake ? 
                Arrays.copyOfRange(signalLine, Math.max(0, signalLine.length - pointsToTake), signalLine.length) : 
                signalLine;
            double[] histogramSubset = histogram.length >= pointsToTake ? 
                Arrays.copyOfRange(histogram, Math.max(0, histogram.length - pointsToTake), histogram.length) : 
                histogram;
                
            map.put("macd", macdSubset);
            map.put("signal", signalSubset);
            map.put("histogram", histogramSubset);
            
            return map;
        }
    }
    
    /**
     * Classe contenant les résultats des bandes de Bollinger
     */
    public static class BollingerBands {
        public final double[] middle;
        public final double[] upper;
        public final double[] lower;
        
        public BollingerBands(double[] middle, double[] upper, double[] lower) {
            this.middle = middle;
            this.upper = upper;
            this.lower = lower;
        }
        
        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            
            // Ne prendre que les X derniers points pour alléger la réponse
            int pointsToTake = Math.min(30, middle.length);
            int start = Math.max(0, middle.length - pointsToTake);
            
            double[] middleSubset = Arrays.copyOfRange(middle, start, middle.length);
            double[] upperSubset = Arrays.copyOfRange(upper, start, upper.length);
            double[] lowerSubset = Arrays.copyOfRange(lower, start, lower.length);
            
            map.put("middle", middleSubset);
            map.put("upper", upperSubset);
            map.put("lower", lowerSubset);
            
            return map;
        }
    }
    
    /**
     * Classe contenant l'analyse technique complète
     */
    public static class TechnicalAnalysis {
        private final TrendType trendType;
        private final double strength;
        private final double volatility;
        private final double[] sma5;
        private final double[] sma20;
        private final double[] rsi;
        private final MacdResult macd;
        private final BollingerBands bollingerBands;
        private final List<TradingSignal> signals;
        private final boolean priceRsiDivergence;
        
        public TechnicalAnalysis(TrendType trendType, double strength, double volatility) {
            this(trendType, strength, volatility, new double[0], new double[0], 
                 new double[0], new MacdResult(new double[0], new double[0], new double[0]), 
                 new BollingerBands(new double[0], new double[0], new double[0]), 
                 List.of(), false);
        }
        
        public TechnicalAnalysis(
                TrendType trendType, double strength, double volatility,
                double[] sma5, double[] sma20, double[] rsi, 
                MacdResult macd, BollingerBands bollingerBands,
                List<TradingSignal> signals, boolean priceRsiDivergence) {
            this.trendType = trendType;
            this.strength = strength;
            this.volatility = volatility;
            this.sma5 = sma5;
            this.sma20 = sma20;
            this.rsi = rsi;
            this.macd = macd;
            this.bollingerBands = bollingerBands;
            this.signals = signals;
            this.priceRsiDivergence = priceRsiDivergence;
        }
        
        public TrendType getTrendType() {
            return trendType;
        }
        
        public double getStrength() {
            return strength;
        }
        
        public double getVolatility() {
            return volatility;
        }
        
        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("trend", trendType.name());
            map.put("strength", strength);
            map.put("volatility", volatility);
            
            // Sélectionner les derniers points de chaque indicateur
            int pointsToTake = 30;
            
            double[] sma5Subset = sma5.length > pointsToTake ? 
                Arrays.copyOfRange(sma5, sma5.length - pointsToTake, sma5.length) : sma5;
            double[] sma20Subset = sma20.length > pointsToTake ? 
                Arrays.copyOfRange(sma20, sma20.length - pointsToTake, sma20.length) : sma20;
            double[] rsiSubset = rsi.length > pointsToTake ? 
                Arrays.copyOfRange(rsi, rsi.length - pointsToTake, rsi.length) : rsi;
            
            map.put("sma5", sma5Subset);
            map.put("sma20", sma20Subset);
            map.put("rsi", rsiSubset);
            map.put("macd", macd.toMap());
            map.put("bollingerBands", bollingerBands.toMap());
            map.put("priceRsiDivergence", priceRsiDivergence);
            
            // Convertir les signaux en map
            List<Map<String, Object>> signalMaps = signals.stream()
                .map(TradingSignal::toMap)
                .collect(Collectors.toList());
            map.put("signals", signalMaps);
            
            return map;
        }
    }
    
    /**
     * Classe représentant une tendance de marché
     */
    public static class MarketTrend {
        private final TrendType trendType;
        private final double strength;         // Force de la tendance (0-1)
        private final double volatility;       // Volatilité (0-1)
        private final double buyPriceChangePercent;  // Variation de prix d'achat en %
        private final double sellPriceChangePercent; // Variation de prix de vente en %
        private final double volumeChangePercent; // Variation du volume en %
        public final TechnicalAnalysis buyAnalysis;  // Analyse technique des prix d'achat
        public final TechnicalAnalysis sellAnalysis; // Analyse technique des prix de vente
        private final List<Map<String, Object>> buyForecastData;  // Prévisions pour les prix d'achat
        private final List<Map<String, Object>> sellForecastData; // Prévisions pour les prix de vente
        private final List<Double> buySupportLevels;    // Niveaux de support pour les prix d'achat
        private final List<Double> buyResistanceLevels; // Niveaux de résistance pour les prix d'achat
        private final List<Double> sellSupportLevels;   // Niveaux de support pour les prix de vente
        private final List<Double> sellResistanceLevels; // Niveaux de résistance pour les prix de vente
        
        public MarketTrend(TrendType trendType, double strength, double volatility, 
                          double buyPriceChangePercent, double sellPriceChangePercent,
                          double volumeChangePercent, TechnicalAnalysis buyAnalysis, TechnicalAnalysis sellAnalysis,
                          List<Map<String, Object>> buyForecastData, List<Map<String, Object>> sellForecastData,
                          List<Double> buySupportLevels, List<Double> buyResistanceLevels,
                          List<Double> sellSupportLevels, List<Double> sellResistanceLevels) {
            this.trendType = trendType;
            this.strength = strength;
            this.volatility = volatility;
            this.buyPriceChangePercent = buyPriceChangePercent;
            this.sellPriceChangePercent = sellPriceChangePercent;
            this.volumeChangePercent = volumeChangePercent;
            this.buyAnalysis = buyAnalysis;
            this.sellAnalysis = sellAnalysis;
            this.buyForecastData = buyForecastData;
            this.sellForecastData = sellForecastData;
            this.buySupportLevels = buySupportLevels;
            this.buyResistanceLevels = buyResistanceLevels;
            this.sellSupportLevels = sellSupportLevels;
            this.sellResistanceLevels = sellResistanceLevels;
        }
        
        public TrendType getTrendType() {
            return trendType;
        }
        
        public double getStrength() {
            return strength;
        }
        
        public double getVolatility() {
            return volatility;
        }
        
        public double getBuyPriceChangePercent() {
            return buyPriceChangePercent;
        }
        
        public double getSellPriceChangePercent() {
            return sellPriceChangePercent;
        }

        public double getPriceChangePercent() {
            // Retourne la moyenne des deux variations ou la plus significative
            if (Math.abs(buyPriceChangePercent) > Math.abs(sellPriceChangePercent)) {
                return buyPriceChangePercent;
            }
            return sellPriceChangePercent;
        }
        
        public double getVolumeChangePercent() {
            return volumeChangePercent;
        }
        
        public List<Map<String, Object>> getBuyForecastData() {
            return buyForecastData;
        }
        
        public List<Map<String, Object>> getSellForecastData() {
            return sellForecastData;
        }
        
        public List<Double> getBuySupportLevels() {
            return buySupportLevels;
        }
        
        public List<Double> getBuyResistanceLevels() {
            return buyResistanceLevels;
        }
        
        public List<Double> getSellSupportLevels() {
            return sellSupportLevels;
        }
        
        public List<Double> getSellResistanceLevels() {
            return sellResistanceLevels;
        }
        
        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("trend", trendType.name());
            map.put("strength", strength);
            map.put("volatility", volatility);
            map.put("buyPriceChange", buyPriceChangePercent);
            map.put("sellPriceChange", sellPriceChangePercent);
            
            map.put("buyAnalysis", buyAnalysis != null ? buyAnalysis.toMap() : null);
            map.put("sellAnalysis", sellAnalysis != null ? sellAnalysis.toMap() : null);
            
            map.put("buyForecast", buyForecastData);
            map.put("sellForecast", sellForecastData);
            
            map.put("buySupportLevels", buySupportLevels);
            map.put("buyResistanceLevels", buyResistanceLevels);
            map.put("sellSupportLevels", sellSupportLevels);
            map.put("sellResistanceLevels", sellResistanceLevels);
            
            return map;
        }
    }
}