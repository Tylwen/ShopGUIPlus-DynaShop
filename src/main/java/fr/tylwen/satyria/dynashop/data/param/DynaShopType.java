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

public enum DynaShopType {
    NONE,       // Aucun type
    DYNAMIC,    // Dynamique (prix évolutif)
    RECIPE,     // Basé sur une recette
    STOCK,       // Basé sur le stock
    STATIC_STOCK, // Basé sur le stock statique
    LINK,     // Prix et comportement liés à un autre item
    UNKNOWN      // Type inconnu
;

    public DynaShopType orElse(DynaShopType other) {
        return this == NONE ? other : this;
    }
}