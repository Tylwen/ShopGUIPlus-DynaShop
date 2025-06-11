package fr.tylwen.satyria.dynashop.system.chart;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Interface pour les renderers de cartes qui supportent le zoom
 */
public interface ZoomableChartRenderer {
    /**
     * Augmente le niveau de zoom (période plus courte)
     */
    void zoomIn();
    
    /**
     * Diminue le niveau de zoom (période plus longue)
     */
    void zoomOut();
    
    /**
     * Met à jour les informations de l'item de la carte
     */
    void updateMapItemLore(ItemStack mapItem, Player player);
}