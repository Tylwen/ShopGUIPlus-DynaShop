package fr.tylwen.satyria.dynashop.data;

// import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.*;
import org.bukkit.configuration.ConfigurationSection;

import fr.tylwen.satyria.dynashop.DynaShopPlugin;

import java.util.Optional;

public class CustomRecipeManager {
    private final DynaShopPlugin plugin;

    /**
     * Constructeur de CustomRecipeManager.
     *
     * @param plugin L'instance du plugin.
     */
    public CustomRecipeManager(DynaShopPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Charge une recette à partir de la configuration du shop.
     *
     * @param shopID      L'ID du shop.
     * @param itemID      L'ID de l'item.
     * @param itemSection La section de configuration de l'item.
     * @return Une Optional contenant la recette si elle existe, sinon une Optional vide.
     */
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
                // plugin.getLogger().warning("Type de recette inconnu pour l'item " + itemID + " dans le shop " + shopID);
                return Optional.empty();
        }
    }

    /**
     * Charge une recette à partir de la configuration du shop.
     *
     * @param itemID      L'ID de l'item.
     * @param recipeSection La section de configuration de la recette.
     * @return La recette chargée.
     */
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

    /**
     * Charge une recette de four à partir de la configuration du shop.
     *
     * @param itemID      L'ID de l'item.
     * @param recipeSection La section de configuration de la recette.
     * @return La recette chargée.
     */
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