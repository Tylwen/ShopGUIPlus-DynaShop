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
package fr.tylwen.satyria.dynashop.utils;

import org.bukkit.Material;
import java.io.FileWriter;
import java.io.IOException;

public class MaterialTranslationGenerator {
    public static void main(String[] args) throws IOException {
        try (FileWriter writer = new FileWriter("translations_fr.yml")) {
            for (Material mat : Material.values()) {
                // Ligne au format: GOLD_BLOCK: "Gold Block"
                writer.write(mat.name() + ": \"" + formatMaterialName(mat.name()) + "\"\n");
            }
        }
        // System.out.println("Fichier translations_fr.yml généré !");
    }

    private static String formatMaterialName(String materialName) {
        String[] words = materialName.toLowerCase().split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)))
                      .append(word.substring(1))
                      .append(" ");
            }
        }
        return result.toString().trim();
    }
}