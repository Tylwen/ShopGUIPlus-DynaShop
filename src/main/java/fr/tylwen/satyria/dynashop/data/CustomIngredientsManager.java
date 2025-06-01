package fr.tylwen.satyria.dynashop.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.inventory.ItemStack;

public class CustomIngredientsManager {
    private final Map<String, Map<Character, ItemStack>> recipeIngredients = new HashMap<>();
    private final Map<String, String[]> recipePatterns = new HashMap<>();
    
    public void storeRecipeIngredients(String shopID, String itemID, Map<Character, ItemStack> ingredients, String[] pattern) {
        String key = shopID + ":" + itemID;
        recipeIngredients.put(key, ingredients);
        recipePatterns.put(key, pattern);
    }
    
    public List<ItemStack> getRecipeIngredients(String shopID, String itemID) {
        String key = shopID + ":" + itemID;
        if (!recipeIngredients.containsKey(key)) {
            return new ArrayList<>();
        }
        
        List<ItemStack> result = new ArrayList<>();
        Map<Character, ItemStack> ingredients = recipeIngredients.get(key);
        String[] pattern = recipePatterns.get(key);
        
        // Parcourir le pattern et ajouter les ingrédients
        for (String row : pattern) {
            for (char c : row.toCharArray()) {
                if (c != ' ' && ingredients.containsKey(c)) {
                    result.add(ingredients.get(c).clone());
                }
            }
        }
        
        return result;
    }
}
