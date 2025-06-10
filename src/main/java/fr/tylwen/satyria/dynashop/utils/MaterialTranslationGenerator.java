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
        System.out.println("Fichier translations_fr.yml généré !");
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