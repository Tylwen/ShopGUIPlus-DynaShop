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
package fr.tylwen.satyria.dynashop.command;

import org.bukkit.command.CommandSender;

public interface SubCommand {
    /**
     * Exécute la sous-commande
     * @param sender L'expéditeur de la commande
     * @param args Les arguments de la commande (sans le premier qui est le nom de la sous-commande)
     * @return true si la commande a été exécutée avec succès, false sinon
     */
    boolean execute(CommandSender sender, String[] args);
    
    /**
     * Retourne le nom de la sous-commande
     * @return Le nom de la sous-commande
     */
    String getName();
    
    /**
     * Retourne la permission requise pour utiliser cette commande
     * @return La permission requise
     */
    String getPermission();
    
    /**
     * Retourne la description de la commande pour l'aide
     * @return La description
     */
    String getDescription();
    
    /**
     * Retourne l'usage de la commande
     * @return L'usage
     */
    String getUsage();
}