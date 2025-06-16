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