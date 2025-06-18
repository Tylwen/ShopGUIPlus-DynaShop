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
package fr.tylwen.satyria.dynashop.data.param;

public enum RecipeType {
    NONE,           // Aucun type
    SHAPED,         // Recette avec une forme définie
    SHAPELESS,      // Recette sans forme définie
    FURNACE,        // Recette de four
    BLAST_FURNACE,  // Recette de haut fourneau
    SMOKER,         // Recette de fumoir
    BREWING,        // Recette d'alchimie
    STONECUTTER,    // Recette de tailleur de pierre
    SMITHING        // Recette de forgeron
;

    public static RecipeType fromString(String upperCase) {
        switch (upperCase) {
            case "NONE":
                return NONE;
            case "SHAPED":
                return SHAPED;
            case "SHAPELESS":
                return SHAPELESS;
            case "FURNACE":
                return FURNACE;
            case "BLAST_FURNACE":
                return BLAST_FURNACE;
            case "SMOKER":
                return SMOKER;
            case "BREWING":
                return BREWING;
            case "STONECUTTER":
                return STONECUTTER;
            case "SMITHING":
                return SMITHING;
            default:
                throw new IllegalArgumentException("Type de recette inconnu: " + upperCase);
        }
    }
}