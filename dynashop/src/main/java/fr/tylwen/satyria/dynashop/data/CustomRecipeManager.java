package fr.tylwen.satyria.dynashop.data;

// import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

// import fr.tylwen.satyria.dynashop.DynaShopPlugin;

// import java.util.HashMap;
// import java.util.Map;
import java.util.Optional;

public class CustomRecipeManager {
    private final JavaPlugin plugin;

    public CustomRecipeManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // public void loadRecipes(ConfigurationSection recipesSection) {
    //     if (recipesSection == null) {
    //         plugin.getLogger().warning("Aucune recette personnalisée trouvée.");
    //         return;
    //     }

    //     for (String recipeKey : recipesSection.getKeys(false)) {
    //         ConfigurationSection recipeSection = recipesSection.getConfigurationSection(recipeKey);
    //         if (recipeSection == null) continue;

    //         String type = recipeSection.getString("type", "NONE").toUpperCase();
    //         switch (type) {
    //             case "SHAPED" -> loadShapedRecipe(recipeKey, recipeSection);
    //             case "SHAPELESS" -> loadShapelessRecipe(recipeKey, recipeSection);
    //             case "FURNACE" -> loadFurnaceRecipe(recipeKey, recipeSection);
    //             default -> plugin.getLogger().warning("Type de recette inconnu : " + type);
    //         }
    //     }
    // }

    // private void loadShapedRecipe(String recipeKey, ConfigurationSection recipeSection) {
    //     String[] pattern = recipeSection.getStringList("pattern").toArray(new String[0]);
    //     ConfigurationSection ingredientsSection = recipeSection.getConfigurationSection("ingredients");
    //     if (ingredientsSection == null) return;

    //     Map<Character, ItemStack> ingredientMap = new HashMap<>();
    //     for (String key : ingredientsSection.getKeys(false)) {
    //         ConfigurationSection ingredient = ingredientsSection.getConfigurationSection(key);
    //         if (ingredient == null) continue;

    //         Material material = Material.matchMaterial(ingredient.getString("material", ""));
    //         int quantity = ingredient.getInt("quantity", 1);
    //         if (material != null) {
    //             ingredientMap.put(key.charAt(0), new ItemStack(material, quantity));
    //         }
    //     }

    //     ShapedRecipe recipe = new ShapedRecipe(new NamespacedKey(plugin, recipeKey), new ItemStack(Material.matchMaterial(recipeKey)));
    //     recipe.shape(pattern);
    //     for (Map.Entry<Character, ItemStack> entry : ingredientMap.entrySet()) {
    //         recipe.setIngredient(entry.getKey(), entry.getValue().getType());
    //     }

    //     Bukkit.addRecipe(recipe);
    //     plugin.getLogger().info("Recette SHAPED ajoutée : " + recipeKey);
    // }

    // private void loadShapelessRecipe(String recipeKey, ConfigurationSection recipeSection) {
    //     ConfigurationSection ingredientsSection = recipeSection.getConfigurationSection("ingredients");
    //     if (ingredientsSection == null) return;

    //     ShapelessRecipe recipe = new ShapelessRecipe(new NamespacedKey(plugin, recipeKey), new ItemStack(Material.matchMaterial(recipeKey)));
    //     for (String key : ingredientsSection.getKeys(false)) {
    //         ConfigurationSection ingredient = ingredientsSection.getConfigurationSection(key);
    //         if (ingredient == null) continue;

    //         Material material = Material.matchMaterial(ingredient.getString("material", ""));
    //         int quantity = ingredient.getInt("quantity", 1);
    //         if (material != null) {
    //             for (int i = 0; i < quantity; i++) {
    //                 recipe.addIngredient(material);
    //             }
    //         }
    //     }

    //     Bukkit.addRecipe(recipe);
    //     plugin.getLogger().info("Recette SHAPELESS ajoutée : " + recipeKey);
    // }

    // private void loadFurnaceRecipe(String recipeKey, ConfigurationSection recipeSection) {
    //     ConfigurationSection inputSection = recipeSection.getConfigurationSection("input");
    //     if (inputSection == null) return;

    //     Material inputMaterial = Material.matchMaterial(inputSection.getString("material", ""));
    //     int quantity = inputSection.getInt("quantity", 1);
    //     Material resultMaterial = Material.matchMaterial(recipeKey);

    //     if (inputMaterial != null && resultMaterial != null) {
    //         FurnaceRecipe recipe = new FurnaceRecipe(
    //             new NamespacedKey(plugin, recipeKey),
    //             new ItemStack(resultMaterial),
    //             inputMaterial,
    //             0.1f, // XP
    //             200   // Temps de cuisson (ticks)
    //         );

    //         Bukkit.addRecipe(recipe);
    //         plugin.getLogger().info("Recette FURNACE ajoutée : " + recipeKey);
    //     }
    // }
    public Optional<Recipe> loadRecipeFromShopConfig(String shopID, String itemID, ConfigurationSection itemSection) {
        if (!itemSection.isConfigurationSection("recipe")) {
            return Optional.empty(); // Pas de recette définie
        }

        ConfigurationSection recipeSection = itemSection.getConfigurationSection("recipe");
        String type = recipeSection.getString("type", "NONE").toUpperCase();

        switch (type) {
            case "SHAPED":
                return Optional.of(loadShapedRecipe(itemID, recipeSection));
            case "SHAPELESS":
                return Optional.of(loadShapelessRecipe(itemID, recipeSection));
            case "FURNACE":
                return Optional.of(loadFurnaceRecipe(itemID, recipeSection));
            default:
                plugin.getLogger().warning("Type de recette inconnu pour l'item " + itemID + " dans le shop " + shopID);
                return Optional.empty();
        }
    }

    private ShapedRecipe loadShapedRecipe(String itemID, ConfigurationSection recipeSection) {
        String[] pattern = recipeSection.getStringList("pattern").toArray(new String[0]);
        ConfigurationSection ingredientsSection = recipeSection.getConfigurationSection("ingredients");

        ShapedRecipe recipe = new ShapedRecipe(new NamespacedKey(plugin, itemID), new ItemStack(Material.matchMaterial(itemID)));
        recipe.shape(pattern);

        for (String key : ingredientsSection.getKeys(false)) {
            ConfigurationSection ingredient = ingredientsSection.getConfigurationSection(key);
            Material material = Material.matchMaterial(ingredient.getString("material"));
            if (material != null) {
                recipe.setIngredient(key.charAt(0), material);
            }
        }

        return recipe;
    }

    private ShapelessRecipe loadShapelessRecipe(String itemID, ConfigurationSection recipeSection) {
        ConfigurationSection ingredientsSection = recipeSection.getConfigurationSection("ingredients");

        ShapelessRecipe recipe = new ShapelessRecipe(new NamespacedKey(plugin, itemID), new ItemStack(Material.matchMaterial(itemID)));
        for (String key : ingredientsSection.getKeys(false)) {
            ConfigurationSection ingredient = ingredientsSection.getConfigurationSection(key);
            Material material = Material.matchMaterial(ingredient.getString("material"));
            int quantity = ingredient.getInt("quantity", 1);
            if (material != null) {
                for (int i = 0; i < quantity; i++) {
                    recipe.addIngredient(material);
                }
            }
        }

        return recipe;
    }

    private FurnaceRecipe loadFurnaceRecipe(String itemID, ConfigurationSection recipeSection) {
        ConfigurationSection inputSection = recipeSection.getConfigurationSection("input");
        Material inputMaterial = Material.matchMaterial(inputSection.getString("material"));
        Material resultMaterial = Material.matchMaterial(itemID);

        return new FurnaceRecipe(
            new NamespacedKey(plugin, itemID),
            new ItemStack(resultMaterial),
            inputMaterial,
            0.1f, // XP
            200   // Temps de cuisson (ticks)
        );
    }
}