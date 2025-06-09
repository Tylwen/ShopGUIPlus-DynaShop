package fr.tylwen.satyria.dynashop.compatibility;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Interface pour obtenir les noms localisés des items selon la version du serveur
 */
public interface ItemNameProvider {
    /**
     * Récupère le nom localisé d'un item pour un joueur spécifique
     * @param item L'item dont on veut le nom
     * @param player Le joueur (pour sa locale)
     * @return Le nom localisé de l'item
     */
    String getLocalizedName(ItemStack item, Player player);
    
    /**
     * Vérifie si cette implémentation est compatible avec la version actuelle du serveur
     * @return true si compatible
     */
    boolean isCompatible();
}