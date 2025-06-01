package fr.tylwen.satyria.dynashop.data;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

import fr.tylwen.satyria.dynashop.DynaShopPlugin;
import net.brcdev.shopgui.ShopGuiPlusApi;
import net.brcdev.shopgui.item.ItemManager;
import net.brcdev.shopgui.provider.item.ItemProvider;
import net.brcdev.shopgui.shop.Shop;
import net.brcdev.shopgui.shop.item.ShopItem;

import java.util.HashMap;
import java.util.Map;
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
     */
    public Optional<Recipe> loadRecipeFromShopConfig(String shopID, String itemID, ConfigurationSection recipeSection) {
        if (recipeSection == null) {
            plugin.getLogger().warning("Section de recette null pour " + shopID + ":" + itemID);
            return Optional.empty();
        }

        // Vérifier le type de recette
        String type = recipeSection.getString("type", "NONE").toUpperCase();
        if (type.equals("NONE")) {
            plugin.getLogger().warning("Type de recette non défini pour " + shopID + ":" + itemID);
            return Optional.empty();
        }
        
        // Vérifications préalables selon le type
        if (type.equals("SHAPED") && !recipeSection.isList("pattern")) {
            plugin.getLogger().warning("Pas de pattern défini pour la recette SHAPED de " + shopID + ":" + itemID);
            return Optional.empty();
        }

        if (type.equals("SHAPELESS") && !recipeSection.isConfigurationSection("ingredients")) {
            plugin.getLogger().warning("Pas d'ingrédients définis pour la recette SHAPELESS de " + shopID + ":" + itemID);
            return Optional.empty();
        }

        if (type.equals("FURNACE") && !recipeSection.isConfigurationSection("input")) {
            plugin.getLogger().warning("Pas d'entrée définie pour la recette FURNACE de " + shopID + ":" + itemID);
            return Optional.empty();
        }

        try {
            switch (type) {
                case "SHAPED":
                    return Optional.of(loadShapedRecipe(shopID, itemID, recipeSection));
                case "SHAPELESS":
                    return Optional.of(loadShapelessRecipe(shopID, itemID, recipeSection));
                case "FURNACE":
                    return Optional.of(loadFurnaceRecipe(shopID, itemID, recipeSection));
                default:
                    plugin.getLogger().warning("Type de recette inconnu pour l'item " + itemID + " dans le shop " + shopID);
                    return Optional.empty();
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Erreur lors du chargement de la recette pour " + shopID + ":" + itemID + " - " + e.getMessage());
            return Optional.empty();
        }
    }

    private ShapedRecipe loadShapedRecipe(String shopID, String itemID, ConfigurationSection recipeSection) {
        String[] pattern = recipeSection.getStringList("pattern").toArray(new String[0]);
        ConfigurationSection ingredientsSection = recipeSection.getConfigurationSection("ingredients");
        
        // Obtenir l'ItemStack du résultat via l'API ShopGUI+
        ItemStack resultItem = getResultItem(shopID, itemID);
        
        // Créer la recette
        ShapedRecipe recipe = new ShapedRecipe(new NamespacedKey(plugin, itemID), resultItem);
        recipe.shape(pattern);
        
        // Stocker les vrais ingrédients dans une map pour utilisation ultérieure
        Map<Character, ItemStack> realIngredients = new HashMap<>();
        
        if (ingredientsSection != null) {
            for (String key : ingredientsSection.getKeys(false)) {
                if (key.length() != 1) {
                    plugin.getLogger().warning("Clé d'ingrédient invalide: " + key);
                    continue;
                }
                
                try {
                    // Récupérer l'ingrédient complet avec métadonnées
                    ItemStack ingredient = getIngredientItemStack(ingredientsSection.getConfigurationSection(key));
                    if (ingredient != null) {
                        // Stocker l'ingrédient réel pour référence future
                        realIngredients.put(key.charAt(0), ingredient);
                        
                        // Pour la recette Bukkit, on utilise juste le matériau de base
                        recipe.setIngredient(key.charAt(0), ingredient.getType());
                        
                        // Logger l'ingrédient pour débogage
                        plugin.getLogger().info("Ingrédient pour " + key + ": " + ingredient.getType() + 
                                            (ingredient.hasItemMeta() ? " avec métadonnées" : " sans métadonnées"));
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Erreur lors du chargement de l'ingrédient " + key + ": " + e.getMessage());
                }
            }
        }
        
        // Stocker les vrais ingrédients pour vérification ultérieure
        plugin.getCustomIngredientsManager().storeRecipeIngredients(shopID, itemID, realIngredients, pattern);
        
        return recipe;
    }

    private ShapelessRecipe loadShapelessRecipe(String shopID, String itemID, ConfigurationSection recipeSection) {
        ConfigurationSection ingredientsSection = recipeSection.getConfigurationSection("ingredients");
        
        // Obtenir l'ItemStack du résultat via l'API ShopGUI+
        ItemStack resultItem = getResultItem(shopID, itemID);
        
        // Créer la recette
        ShapelessRecipe recipe = new ShapelessRecipe(new NamespacedKey(plugin, itemID), resultItem);
        
        if (ingredientsSection != null) {
            for (String key : ingredientsSection.getKeys(false)) {
                try {
                    ItemStack ingredient = getIngredientItemStack(ingredientsSection.getConfigurationSection(key));
                    if (ingredient != null) {
                        int quantity = ingredientsSection.getConfigurationSection(key).getInt("quantity", 1);
                        for (int i = 0; i < quantity; i++) {
                            recipe.addIngredient(ingredient.getType());
                        }
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Erreur lors du chargement de l'ingrédient " + key + ": " + e.getMessage());
                }
            }
        }
        
        return recipe;
    }

    private FurnaceRecipe loadFurnaceRecipe(String shopID, String itemID, ConfigurationSection recipeSection) {
        ConfigurationSection inputSection = recipeSection.getConfigurationSection("input");
        
        // Obtenir l'ItemStack du résultat via l'API ShopGUI+
        ItemStack resultItem = getResultItem(shopID, itemID);
        
        // Obtenir l'ingrédient d'entrée
        ItemStack inputItem = getIngredientItemStack(inputSection);
        if (inputItem == null) {
            // Fallback sur la méthode simple
            Material inputMaterial = Material.matchMaterial(inputSection.getString("material", "STONE"));
            inputItem = new ItemStack(inputMaterial);
        }
        
        float experience = (float) recipeSection.getDouble("experience", 0.1);
        int cookingTime = recipeSection.getInt("cookingTime", 200);
        
        return new FurnaceRecipe(
            new NamespacedKey(plugin, itemID),
            resultItem,
            inputItem.getType(),
            experience,
            cookingTime
        );
    }

    // /**
    //  * Obtient l'ItemStack résultat en utilisant l'API ItemManager de ShopGUI+
    //  */
    // private ItemStack getResultItem(String shopID, String itemID) {
    //     try {
    //         // Utiliser l'API ItemManager de ShopGUI+ pour obtenir l'item
    //         ItemManager itemManager = ShopGuiPlusApi.getPlugin().getItemManager();
    //         Shop shop = ShopGuiPlusApi.getShop(shopID);
            
    //         if (shop == null) {
    //             throw new IllegalArgumentException("Shop introuvable: " + shopID);
    //         }
            
    //         ShopItem shopItem = shop.getShopItem(itemID);
    //         if (shopItem == null) {
    //             throw new IllegalArgumentException("Item introuvable dans le shop: " + itemID);
    //         }
            
    //         ItemStack resultItem = shopItem.getItem().clone();
    //         resultItem.setAmount(1);
    //         return resultItem;
    //     } catch (Exception e) {
    //         plugin.getLogger().warning("Erreur lors de la récupération de l'item " + shopID + ":" + itemID + " - " + e.getMessage());
            
    //         // Fallback sur Material si possible
    //         Material material = Material.matchMaterial(itemID);
    //         if (material != null) {
    //             return new ItemStack(material);
    //         }
            
    //         throw new IllegalArgumentException("Impossible de créer l'item résultat");
    //     }
    // }
    private ItemStack getResultItem(String shopID, String itemID) {
        try {
            // Créer une section de configuration temporaire pour utiliser loadItem
            ConfigurationSection tempSection = new MemoryConfiguration();
            tempSection.set("item", shopID + ":" + itemID);
            
            // Utiliser loadItem pour charger l'item
            ItemStack resultItem = ShopGuiPlusApi.getPlugin().getItemManager().loadItem(tempSection);
            if (resultItem != null) {
                // resultItem.setAmount(1);
                return resultItem;
            }
            
            // Si loadItem a échoué, essayer avec l'ancienne méthode
            Shop shop = ShopGuiPlusApi.getShop(shopID);
            if (shop == null) {
                throw new IllegalArgumentException("Shop introuvable: " + shopID);
            }
            
            ShopItem shopItem = shop.getShopItem(itemID);
            if (shopItem == null) {
                throw new IllegalArgumentException("Item introuvable dans le shop: " + itemID);
            }
            
            resultItem = shopItem.getItem().clone();
            resultItem.setAmount(1);
            return resultItem;
        } catch (Exception e) {
            plugin.getLogger().warning("Erreur lors de la récupération de l'item " + shopID + ":" + itemID + " - " + e.getMessage());
            
            // Fallback sur Material si possible
            Material material = Material.matchMaterial(itemID);
            if (material != null) {
                return new ItemStack(material);
            }
            
            throw new IllegalArgumentException("Impossible de créer l'item résultat");
        }
    }

    /**
     * Méthode pour obtenir un ItemStack d'ingrédient depuis la configuration
     */
    private ItemStack getIngredientItemStack(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        
        try {
            // Essayer d'utiliser directement loadItem sur la section complète
            // Cette méthode gère automatiquement les formats item: shopID:itemID
            ItemStack itemStack = ShopGuiPlusApi.getPlugin().getItemManager().loadItem(section);
            if (itemStack != null) {
                // Appliquer la quantité si définie
                if (section.contains("quantity")) {
                    itemStack.setAmount(section.getInt("quantity", 1));
                }
                return itemStack;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Erreur lors du chargement de l'item avec loadItem: " + e.getMessage());
            // Continuer avec le fallback si loadItem échoue
        }
        
        // Fallback sur le format simple
        Material material = Material.matchMaterial(section.getString("material", "AIR"));
        if (material != null && material != Material.AIR) {
            return new ItemStack(material, section.getInt("quantity", 1));
        }
        
        return null;
    }
}