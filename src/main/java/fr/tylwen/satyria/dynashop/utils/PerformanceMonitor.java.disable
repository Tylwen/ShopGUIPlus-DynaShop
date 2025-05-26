package fr.tylwen.satyria.dynashop.utils;

import fr.tylwen.satyria.dynashop.DynaShopPlugin;
import org.bukkit.Bukkit;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class PerformanceMonitor {
    private final DynaShopPlugin plugin;
    private final Map<String, Long> executionTimes = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> operationCounts = new ConcurrentHashMap<>();
    private final Map<String, Long> peakExecutionTimes = new ConcurrentHashMap<>();
    
    // Seuils pour les avertissements (en ms)
    private static final long WARNING_THRESHOLD = 50;
    private static final long SEVERE_THRESHOLD = 200;
    
    public PerformanceMonitor(DynaShopPlugin plugin) {
        this.plugin = plugin;
    }
    
    // Méthode pour suivre le temps d'exécution d'une opération
    public void trackExecution(String operation, long timeMs) {
        executionTimes.merge(operation, timeMs, Long::sum);
        operationCounts.computeIfAbsent(operation, k -> new AtomicInteger(0)).incrementAndGet();
        
        // Suivre les temps d'exécution maximaux
        peakExecutionTimes.compute(operation, (k, v) -> v == null ? timeMs : Math.max(v, timeMs));
        
        // Alerter en cas d'opération anormalement longue
        if (timeMs > SEVERE_THRESHOLD) {
            plugin.severe("Opération très lente: " + operation + " a pris " + timeMs + "ms");
        } else if (timeMs > WARNING_THRESHOLD) {
            plugin.warning("Opération lente: " + operation + " a pris " + timeMs + "ms");
        }
    }
    
    // Suivre le temps d'exécution d'un bloc de code
    public void trackOperation(String operation, Runnable task) {
        long startTime = System.currentTimeMillis();
        try {
            task.run();
        } finally {
            long executionTime = System.currentTimeMillis() - startTime;
            trackExecution(operation, executionTime);
        }
    }
    
    // Journaliser les métriques
    public void logMetrics() {
        // Calculer les moyennes
        Map<String, Double> averageTimes = new HashMap<>();
        
        for (Map.Entry<String, Long> entry : executionTimes.entrySet()) {
            String operation = entry.getKey();
            long totalTime = entry.getValue();
            int count = operationCounts.getOrDefault(operation, new AtomicInteger(0)).get();
            
            if (count > 0) {
                double averageTime = (double) totalTime / count;
                averageTimes.put(operation, averageTime);
            }
        }
        
        // Journal de performance
        plugin.info("=== Performance DynaShop ===");
        plugin.info("TPS actuel du serveur: " + getTPS());
        
        for (Map.Entry<String, Double> entry : averageTimes.entrySet()) {
            String operation = entry.getKey();
            double avgTime = entry.getValue();
            int count = operationCounts.getOrDefault(operation, new AtomicInteger(0)).get();
            long peakTime = peakExecutionTimes.getOrDefault(operation, 0L);
            
            plugin.info(String.format(
                "%s: %d exécutions, moyenne %.2fms, pic %dms",
                operation, count, avgTime, peakTime
            ));
        }
        
        // Réinitialiser les compteurs pour la prochaine période
        executionTimes.clear();
        operationCounts.clear();
        // Ne pas réinitialiser peakExecutionTimes pour garder un historique
    }
    
    // Obtenir le TPS actuel du serveur
    private double getTPS() {
        try {
            // Accéder aux TPS à travers la réflection (fonctionne sur la plupart des serveurs)
            Object serverInstance = Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
            double[] tps = (double[]) serverInstance.getClass().getField("recentTps").get(serverInstance);
            return Math.round(tps[0] * 100.0) / 100.0; // TPS sur 1 minute, arrondi à 2 décimales
        } catch (Exception e) {
            return -1.0; // Échec de l'accès aux TPS
        }
    }
}