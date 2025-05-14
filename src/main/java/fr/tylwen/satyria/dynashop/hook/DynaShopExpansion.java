package fr.tylwen.satyria.dynashop.hook;

import java.lang.foreign.Linker.Option;
import java.util.ArrayList;
// import java.sql.SQLException;
import java.util.Optional;

// import org.bukkit.Bukkit;
// import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
// import org.jetbrains.annotations.NotNull;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import fr.tylwen.satyria.dynashop.DynaShopPlugin;
import fr.tylwen.satyria.dynashop.data.PriceRecipe;
import fr.tylwen.satyria.dynashop.data.ShopConfigManager;
import fr.tylwen.satyria.dynashop.data.param.DynaShopType;
// import fr.tylwen.satyria.dynashop.database.DataManager;
import fr.tylwen.satyria.dynashop.database.ItemDataManager;
import fr.tylwen.satyria.dynashop.listener.DynaShopListener;
import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.brcdev.shopgui.ShopGuiPlusApi;
// import net.brcdev.shopgui.player.PlayerData;

public class DynaShopExpansion extends PlaceholderExpansion {

    private final DynaShopPlugin mainPlugin;
    // private DataManager dataManager;
    // ItemDataManager itemDataManager = new ItemDataManager(dataManager);
    private final ItemDataManager itemDataManager;
    private final ShopConfigManager shopConfigManager;
    private final PriceRecipe priceRecipe;

    public DynaShopExpansion(DynaShopPlugin mainPlugin) {
        this.mainPlugin = mainPlugin;
        this.itemDataManager = mainPlugin.getItemDataManager();
        this.shopConfigManager = mainPlugin.getShopConfigManager();
        this.priceRecipe = mainPlugin.getPriceRecipe();
    }

    // public DynaShopExpansion(ItemDataManager itemDataManager, ShopConfigManager shopConfigManager, PriceRecipe priceRecipe) {
    //     // this.mainPlugin = DynaShopPlugin.getInstance();
    //     this.itemDataManager = itemDataManager;
    //     this.shopConfigManager = shopConfigManager;
    //     this.priceRecipe = priceRecipe;
    // }
    
    public String getIdentifier() {
        return "dynashop";
    }
    
    public String getAuthor() {
        return "Tylwen";
    }
    
    public String getVersion() {
        return this.mainPlugin.getDescription().getVersion();
    }

    public boolean persist() {
        return true;
    }

    public boolean canRegister() {
        return true;
    }

    // // public boolean register() {
    // //     return super.register();
    // // }
    // @Override
    // public boolean register() {
    //     // Enregistrer les placeholders standards
    //     boolean registered = super.register();
        
    //     // Enregistrer notre placeholder générique avec ShopGUI+
    //     if (registered) {
    //         mainPlugin.getLogger().info("Enregistrement des placeholders génériques pour ShopGUI+...");
    //         try {
    //             // Cette ligne dépend de la façon dont ShopGUI+ permet d'enregistrer des placeholders
    //             // Si ShopGUI+ a une méthode, utilisez-la ici
    //             ShopGuiPlusApi.registerPlaceholderHook("dynashop", this);
    //         } catch (Exception e) {
    //             mainPlugin.getLogger().warning("Impossible d'enregistrer les placeholders avec ShopGUI+: " + e.getMessage());
    //         }
    //     }
        
    //     return registered;
    // }
    @Override
    public boolean register() {
        // Enregistrer les placeholders standards
        boolean registered = super.register();
        
        // Log informationnel
        if (registered) {
            mainPlugin.getLogger().info("Placeholders DynaShop enregistrés avec succès");
            mainPlugin.getLogger().info("Utilisez %dynashop_current_buyPrice% pour obtenir le prix d'achat actuel");
            mainPlugin.getLogger().info("Utilisez %dynashop_current_buyMinPrice% pour obtenir le prix d'achat minimum actuel");
            mainPlugin.getLogger().info("Utilisez %dynashop_current_buyMaxPrice% pour obtenir le prix d'achat maximum actuel");
            mainPlugin.getLogger().info("Utilisez %dynashop_current_sellPrice% pour obtenir le prix de vente actuel");
            mainPlugin.getLogger().info("Utilisez %dynashop_current_sellMinPrice% pour obtenir le prix de vente minimum actuel");
            mainPlugin.getLogger().info("Utilisez %dynashop_current_sellMaxPrice% pour obtenir le prix de vente maximum actuel");
            mainPlugin.getLogger().info("Utilisez %dynashop_buy_price_shopID:itemID% pour obtenir le prix d'achat d'un item dans un shop");
        }
        
        return registered;
    }

    // /**
    //  * Obtient le prix selon le type spécifié.
    //  * @param shopID l'ID du shop
    //  * @param itemID l'ID de l'item
    //  * @param priceType le type de prix (buy, sell, buy_min, etc.)
    //  * @return le prix formaté en String
    //  */
    // private String getPriceByType(String shopID, String itemID, String priceType) {
    //     // Vérifier d'abord si "useRecipe" est activé
    //     // boolean useRecipe = shopConfigManager.getItemValue(shopID, itemID, "useRecipe", Boolean.class).orElse(false);
    //     boolean useRecipe = shopConfigManager.getTypeDynaShop(shopID, itemID).orElse(DynaShopType.NONE) == DynaShopType.RECIPE;
        
    //     switch (priceType) {
    //         case "buy":
    //             if (useRecipe) {
    //                 // Calculer le prix via la recette
    //                 ItemStack itemStack = ShopGuiPlusApi.getShop(shopID).getShopItem(itemID).getItem();
    //                 if (itemStack != null) {
    //                     double recipeBuyPrice = priceRecipe.calculateBuyPrice(shopID, itemID, itemStack, new ArrayList<>());
    //                     return String.format("%.2f", recipeBuyPrice);
    //                 }
    //                 return "N/A"; // Aucun prix disponible via recette
    //             }

    //             // Priorité à la base de données
    //             Optional<Double> buyPrice = this.itemDataManager.getBuyPrice(shopID, itemID);
    //             if (buyPrice.isPresent()) {
    //                 return String.format("%.2f", buyPrice.get());
    //             }

    //             // Fallback sur les fichiers de configuration
    //             Optional<Double> configBuyPrice = shopConfigManager.getItemValue(shopID, itemID, "buyPrice", Double.class);
    //             if (configBuyPrice.isPresent()) {
    //                 return String.format("%.2f", configBuyPrice.get());
    //             }
                
    //             return "N/A";
                
    //         case "sell":
    //             if (useRecipe) {
    //                 // Calculer le prix via la recette
    //                 ItemStack itemStack = ShopGuiPlusApi.getShop(shopID).getShopItem(itemID).getItem();
    //                 if (itemStack != null) {
    //                     double recipeSellPrice = priceRecipe.calculateSellPrice(shopID, itemID, itemStack, new ArrayList<>());
    //                     return String.format("%.2f", recipeSellPrice);
    //                 }
    //                 return "N/A"; // Aucun prix disponible via recette
    //             }

    //             // Priorité à la base de données
    //             Optional<Double> sellPrice = this.itemDataManager.getSellPrice(shopID, itemID);
    //             if (sellPrice.isPresent()) {
    //                 return String.format("%.2f", sellPrice.get());
    //             }

    //             // Fallback sur les fichiers de configuration
    //             Optional<Double> configSellPrice = shopConfigManager.getItemValue(shopID, itemID, "sellPrice", Double.class);
    //             if (configSellPrice.isPresent()) {
    //                 return String.format("%.2f", configSellPrice.get());
    //             }
                
    //             return "N/A";
                
    //         case "buy_min":
    //             Optional<Double> minBuyPrice = shopConfigManager.getItemValue(shopID, itemID, "minBuy", Double.class);
    //             if (minBuyPrice.isPresent()) {
    //                 return String.format("%.2f", minBuyPrice.get());
    //             }
    //             return "N/A";
                
    //         case "buy_max":
    //             Optional<Double> maxBuyPrice = shopConfigManager.getItemValue(shopID, itemID, "maxBuy", Double.class);
    //             if (maxBuyPrice.isPresent()) {
    //                 return String.format("%.2f", maxBuyPrice.get());
    //             }
    //             return "N/A";
                
    //         case "sell_min":
    //             Optional<Double> minSellPrice = shopConfigManager.getItemValue(shopID, itemID, "minSell", Double.class);
    //             if (minSellPrice.isPresent()) {
    //                 return String.format("%.2f", minSellPrice.get());
    //             }
    //             return "N/A";
                
    //         case "sell_max":
    //             Optional<Double> maxSellPrice = shopConfigManager.getItemValue(shopID, itemID, "maxSell", Double.class);
    //             if (maxSellPrice.isPresent()) {
    //                 return String.format("%.2f", maxSellPrice.get());
    //             }
    //             return "N/A";
                
    //         default:
    //             return "Type inconnu";
    //     }
    // }
    /**
     * Obtient le prix selon le type spécifié.
     */
    private String getPriceByType(String shopID, String itemID, String priceType) {
        // Vérifier d'abord si "useRecipe" est activé
        // boolean useRecipe = shopConfigManager.getItemValue(shopID, itemID, "useRecipe", Boolean.class).orElse(false);
        boolean useRecipe = shopConfigManager.getTypeDynaShop(shopID, itemID).orElse(DynaShopType.NONE) == DynaShopType.RECIPE;
        ItemStack itemStack = null;
        
        if (useRecipe) {
            itemStack = ShopGuiPlusApi.getShop(shopID).getShopItem(itemID).getItem();
            if (itemStack == null) {
                return "N/A";
            }
        }
        
        switch (priceType) {
            case "buy":
                if (useRecipe && itemStack != null) {
                    double recipeBuyPrice = priceRecipe.calculateBuyPrice(shopID, itemID, itemStack, new ArrayList<>());
                    return String.format("%.2f", recipeBuyPrice);
                }
                // return this.itemDataManager.getBuyPrice(shopID, itemID)
                //     .or(() -> shopConfigManager.getItemValue(shopID, itemID, "buyPrice", Double.class))
                //     .map(price -> String.format("%.2f", price))
                //     .orElse("N/A");

                // Priorité à la base de données
                Optional<Double> buyPrice = this.itemDataManager.getBuyPrice(shopID, itemID);
                if (buyPrice.isPresent()) {
                    return String.format("%.2f", buyPrice.get());
                }

                // Fallback sur les fichiers de configuration
                Optional<Double> configBuyPrice = shopConfigManager.getItemValue(shopID, itemID, "buyPrice", Double.class);
                if (configBuyPrice.isPresent()) {
                    return String.format("%.2f", configBuyPrice.get());
                }
                
                return "N/A";
                    
            case "sell":
                if (useRecipe && itemStack != null) {
                    double recipeSellPrice = priceRecipe.calculateSellPrice(shopID, itemID, itemStack, new ArrayList<>());
                    return String.format("%.2f", recipeSellPrice);
                }
                // return this.itemDataManager.getSellPrice(shopID, itemID)
                //     .or(() -> shopConfigManager.getItemValue(shopID, itemID, "sellPrice", Double.class))
                //     .map(price -> String.format("%.2f", price))
                //     .orElse("N/A");
                
                // Priorité à la base de données
                Optional<Double> sellPrice = this.itemDataManager.getSellPrice(shopID, itemID);
                if (sellPrice.isPresent()) {
                    return String.format("%.2f", sellPrice.get());
                }

                // Fallback sur les fichiers de configuration
                Optional<Double> configSellPrice = shopConfigManager.getItemValue(shopID, itemID, "sellPrice", Double.class);
                if (configSellPrice.isPresent()) {
                    return String.format("%.2f", configSellPrice.get());
                }
                
                return "N/A";
            
            case "buy_min":
                if (useRecipe && itemStack != null) {
                    // Utiliser la valeur en cache si disponible
                    double cachedMin = mainPlugin.getCachedRecipePrice(shopID, itemID, "buyDynamic.min");
                    if (cachedMin >= 0) {
                        return String.format("%.2f", cachedMin);
                    }
                }
                // return shopConfigManager.getItemValue(shopID, itemID, "buyDynamic.min", Double.class)
                //     .map(price -> String.format("%.2f", price))
                //     .orElse("N/A");

                Optional<Double> minBuyPrice = shopConfigManager.getItemValue(shopID, itemID, "buyDynamic.min", Double.class);
                if (minBuyPrice.isPresent()) {
                    return String.format("%.2f", minBuyPrice.get());
                }
                return "N/A";
                    
            case "buy_max":
                if (useRecipe && itemStack != null) {
                    double cachedMax = mainPlugin.getCachedRecipePrice(shopID, itemID, "buyDynamic.max");
                    if (cachedMax >= 0) {
                        return String.format("%.2f", cachedMax);
                    }
                }
                // return shopConfigManager.getItemValue(shopID, itemID, "buyDynamic.max", Double.class)
                //     .map(price -> String.format("%.2f", price))
                //     .orElse("N/A");
                Optional<Double> maxBuyPrice = shopConfigManager.getItemValue(shopID, itemID, "buyDynamic.max", Double.class);
                if (maxBuyPrice.isPresent()) {
                    return String.format("%.2f", maxBuyPrice.get());
                }
                return "N/A";
                    
            case "sell_min":
                if (useRecipe && itemStack != null) {
                    double cachedMin = mainPlugin.getCachedRecipePrice(shopID, itemID, "sellDynamic.min");
                    if (cachedMin >= 0) {
                        return String.format("%.2f", cachedMin);
                    }
                }
                // return shopConfigManager.getItemValue(shopID, itemID, "sellDynamic.min", Double.class)
                //     .map(price -> String.format("%.2f", price))
                //     .orElse("N/A");
                Optional<Double> minSellPrice = shopConfigManager.getItemValue(shopID, itemID, "sellDynamic.min", Double.class);
                if (minSellPrice.isPresent()) {
                    return String.format("%.2f", minSellPrice.get());
                }
                return "N/A";
                    
            case "sell_max":
                if (useRecipe && itemStack != null) {
                    double cachedMax = mainPlugin.getCachedRecipePrice(shopID, itemID, "sellDynamic.max");
                    if (cachedMax >= 0) {
                        return String.format("%.2f", cachedMax);
                    }
                }
                // return shopConfigManager.getItemValue(shopID, itemID, "sellDynamic.max", Double.class)
                //     .map(price -> String.format("%.2f", price))
                //     .orElse("N/A");
                Optional<Double> maxSellPrice = shopConfigManager.getItemValue(shopID, itemID, "sellDynamic.max", Double.class);
                if (maxSellPrice.isPresent()) {
                    return String.format("%.2f", maxSellPrice.get());
                }
                return "N/A";
                
            default:
                return "Type inconnu";
        }
    }
    
    public String onRequest(OfflinePlayer player, String identifier) {
        // %dynashop_current_buyPrice%
        if (identifier.equals("current_buyPrice") && player instanceof Player p) {
            // Player p = (Player) player;
            DynaShopListener listener = mainPlugin.getDynaShopListener();
            
            String shopId = listener.getCurrentShopId(p);
            String itemId = listener.getCurrentItemId(p);
            
            if (shopId != null && itemId != null) {
                // Récupérer le prix actuel
                String price = getPriceByType(shopId, itemId, "buy");
                return price != null ? price : "N/A"; // Si le prix est null, retourner "N/A"
            }
            
            return "N/A"; // Pas de shop ouvert
        }

        // %dynashop_current_buyMinPrice%
        if (identifier.equals("current_buyMinPrice") && player instanceof Player p) {
            // Player p = (Player) player;
            DynaShopListener listener = mainPlugin.getDynaShopListener();
            
            String shopId = listener.getCurrentShopId(p);
            String itemId = listener.getCurrentItemId(p);
            
            if (shopId != null && itemId != null) {
                // Récupérer le prix actuel
                String price = getPriceByType(shopId, itemId, "buy_min");
                return price != null ? price : "N/A"; // Si le prix est null, retourner "N/A"
            }
            
            return "N/A"; // Pas de shop ouvert
        }

        // %dynashop_current_buyMaxPrice%
        if (identifier.equals("current_buyMaxPrice") && player instanceof Player p) {
            // Player p = (Player) player;
            DynaShopListener listener = mainPlugin.getDynaShopListener();
            
            String shopId = listener.getCurrentShopId(p);
            String itemId = listener.getCurrentItemId(p);
            
            if (shopId != null && itemId != null) {
                // Récupérer le prix actuel
                String price = getPriceByType(shopId, itemId, "buy_max");
                return price != null ? price : "N/A"; // Si le prix est null, retourner "N/A"
            }
            
            return "N/A"; // Pas de shop ouvert
        }

        // %dynashop_current_sellPrice%
        if (identifier.equals("current_sellPrice") && player instanceof Player p) {
            // Player p = (Player) player;
            DynaShopListener listener = mainPlugin.getDynaShopListener();
            
            String shopId = listener.getCurrentShopId(p);
            String itemId = listener.getCurrentItemId(p);
            
            if (shopId != null && itemId != null) {
                // Récupérer le prix actuel
                String price = getPriceByType(shopId, itemId, "sell");
                return price != null ? price : "N/A"; // Si le prix est null, retourner "N/A"
            }
            
            return "N/A"; // Pas de shop ouvert
        }

        // %dynashop_current_sellMinPrice%
        if (identifier.equals("current_sellMinPrice") && player instanceof Player p) {
            // Player p = (Player) player;
            DynaShopListener listener = mainPlugin.getDynaShopListener();
            
            String shopId = listener.getCurrentShopId(p);
            String itemId = listener.getCurrentItemId(p);
            
            if (shopId != null && itemId != null) {
                // Récupérer le prix actuel
                String price = getPriceByType(shopId, itemId, "sell_min");
                return price != null ? price : "N/A"; // Si le prix est null, retourner "N/A"
            }
            
            return "N/A"; // Pas de shop ouvert
        }

        // %dynashop_current_sellMaxPrice%
        if (identifier.equals("current_sellMaxPrice") && player instanceof Player p) {
            // Player p = (Player) player;
            DynaShopListener listener = mainPlugin.getDynaShopListener();
            
            String shopId = listener.getCurrentShopId(p);
            String itemId = listener.getCurrentItemId(p);
            
            if (shopId != null && itemId != null) {
                // Récupérer le prix actuel
                String price = getPriceByType(shopId, itemId, "sell_max");
                return price != null ? price : "N/A"; // Si le prix est null, retourner "N/A"
            }
            
            return "N/A"; // Pas de shop ouvert
        }
        
        // %dynashop_buy_price_shopID:itemID%
        // // if (identifier.contains("buy_price")) {
        // if (identifier.startsWith("buy_price")) {
        //     // String itemID = identifier.substring(11, identifier.length() - 1).toUpperCase().replace("-", "_").replace(" ", "_");
        //     // String itemID = identifier.substring(10).toUpperCase().replace("-", "_").replace(" ", "_");
        //     // String[] parts = identifier.split(":");
        //     String[] parts = identifier.substring(10).split(":");
        //     if (parts.length != 2) {
        //         return null; // Invalid format
        //     }
        //     String shopID = parts[0].replace("-", "_").replace(" ", "_"); // Extract shopID from the placeholder
        //     String itemID = parts[1].replace("-", "_").replace(" ", "_"); // Extract itemID from the placeholder

        //     // try {
        //     //     return Double.toString(this.itemDataManager.getBuyPrice(shopID, itemID).orElse(0.0));
        //     // } catch (SQLException e) {
        //     //     e.printStackTrace();
        //     //     return null;
        //     // }

        //     Optional<Double> buyPrice = this.itemDataManager.getBuyPrice(shopID, itemID);
        //     if (buyPrice.isPresent()) {
        //         return Double.toString(buyPrice.get());
        //         // return String.valueOf(buyPrice.get());
        //     }
        //     // on doit sortir un double et pas un string
        //     return shopConfigManager.getItemValue(shopID, itemID, "buyPrice")
        //         .map(String::valueOf)
        //         .orElse("0.0");
        // }
        if (identifier.startsWith("buy_price")) {
            String[] parts = identifier.substring(10).split(":");
            if (parts.length != 2) {
                return null; // Format invalide
            }
            String shopID = parts[0].replace("-", "_").replace(" ", "_");
            String itemID = parts[1].replace("-", "_").replace(" ", "_");
            
            // Vérifier si "useRecipe" est activé dans la configuration
            boolean useRecipe = shopConfigManager.getItemValue(shopID, itemID, "useRecipe", Boolean.class).orElse(false);
            if (useRecipe) {
                // Calculer le prix via la recette
                ItemStack itemStack = ShopGuiPlusApi.getShop(shopID).getShopItem(itemID).getItem();
                if (itemStack != null) {
                    double recipeBuyPrice = priceRecipe.calculateBuyPrice(shopID, itemID, itemStack, new ArrayList<>());
                    return String.valueOf(recipeBuyPrice);
                }
                return "N/A"; // Aucun prix disponible via recette
            }

            // Priorité à la base de données
            Optional<Double> buyPrice = this.itemDataManager.getBuyPrice(shopID, itemID);
            if (buyPrice.isPresent()) {
                return String.valueOf(buyPrice.get());
            }

            // Fallback sur les fichiers de configuration
            Optional<Double> configBuyPrice = shopConfigManager.getItemValue(shopID, itemID, "buyPrice", Double.class);
            if (configBuyPrice.isPresent()) {
                return String.valueOf(configBuyPrice.get());
            }

            // // Calculer le prix via la recette si activé
            // // ItemStack itemStack = shopConfigManager.getItemStack(shopID, itemID);
            // ItemStack itemStack = ShopGuiPlusApi.getShop(shopID).getShopItem(itemID).getItem();
            // if (itemStack != null) {
            //     double recipeBuyPrice = priceRecipe.calculateBuyPrice(itemStack);
            //     return String.valueOf(recipeBuyPrice);
            // }

            return "N/A"; // Aucun prix disponible
        }

        // %dynashop_sell_price_shopID:itemID%
        if (identifier.startsWith("sell_price")) {
            String[] parts = identifier.substring(11).split(":");
            if (parts.length != 2) {
                return null; // Format invalide
            }
            String shopID = parts[0].replace("-", "_").replace(" ", "_");
            String itemID = parts[1].replace("-", "_").replace(" ", "_");

            // Vérifier si "useRecipe" est activé dans la configuration
            boolean useRecipe = shopConfigManager.getItemValue(shopID, itemID, "useRecipe", Boolean.class).orElse(false);
            if (useRecipe) {
                // Calculer le prix via la recette
                ItemStack itemStack = ShopGuiPlusApi.getShop(shopID).getShopItem(itemID).getItem();
                if (itemStack != null) {
                    double recipeSellPrice = priceRecipe.calculateSellPrice(shopID, itemID, itemStack, new ArrayList<>());
                    return String.valueOf(recipeSellPrice);
                }
                return "N/A"; // Aucun prix disponible via recette
            }

            // Priorité à la base de données
            Optional<Double> sellPrice = this.itemDataManager.getSellPrice(shopID, itemID);
            if (sellPrice.isPresent()) {
                return String.valueOf(sellPrice.get());
            }

            // Fallback sur les fichiers de configuration
            Optional<Double> configSellPrice = shopConfigManager.getItemValue(shopID, itemID, "sellPrice", Double.class);
            if (configSellPrice.isPresent()) {
                return String.valueOf(configSellPrice.get());
            }

            return "N/A"; // Aucun prix disponible
        }

        // %dynashop_buy_min_price_shopID:itemID%
        if (identifier.startsWith("buy_min_price")) {
            String[] parts = identifier.substring(15).split(":");
            if (parts.length != 2) {
                return null; // Format invalide
            }
            String shopID = parts[0];
            String itemID = parts[1];

            // Vérifier si "useRecipe" est activé dans la configuration
            boolean useRecipe = shopConfigManager.getItemValue(shopID, itemID, "useRecipe", Boolean.class).orElse(false);
            if (useRecipe) {
                // Calculer le prix minimum via la recette
                ItemStack itemStack = ShopGuiPlusApi.getShop(shopID).getShopItem(itemID).getItem();
                if (itemStack != null) {
                    double recipeMinBuyPrice = priceRecipe.calculateBuyPrice(shopID, itemID, itemStack, new ArrayList<>()); // Utilisez un calcul spécifique si nécessaire
                    return String.valueOf(recipeMinBuyPrice);
                }
                return "N/A"; // Aucun prix disponible via recette
            }

            // Fallback sur les fichiers de configuration
            Optional<Double> minBuyPrice = shopConfigManager.getItemValue(shopID, itemID, "minBuy", Double.class);
            if (minBuyPrice.isPresent()) {
                return String.valueOf(minBuyPrice.get());
            }

            return "N/A"; // Aucun prix disponible
        }

        // %dynashop_buy_max_price_shopID:itemID%
        if (identifier.startsWith("buy_max_price")) {
            String[] parts = identifier.substring(15).split(":");
            if (parts.length != 2) {
                return null; // Format invalide
            }
            String shopID = parts[0];
            String itemID = parts[1];

            // Vérifier si "useRecipe" est activé dans la configuration
            boolean useRecipe = shopConfigManager.getItemValue(shopID, itemID, "useRecipe", Boolean.class).orElse(false);
            if (useRecipe) {
                // Calculer le prix maximum via la recette
                ItemStack itemStack = ShopGuiPlusApi.getShop(shopID).getShopItem(itemID).getItem();
                if (itemStack != null) {
                    double recipeMaxBuyPrice = priceRecipe.calculateBuyPrice(shopID, itemID, itemStack, new ArrayList<>()); // Utilisez un calcul spécifique si nécessaire
                    return String.valueOf(recipeMaxBuyPrice);
                }
                return "N/A"; // Aucun prix disponible via recette
            }

            // Fallback sur les fichiers de configuration
            Optional<Double> maxBuyPrice = shopConfigManager.getItemValue(shopID, itemID, "maxBuy", Double.class);
            if (maxBuyPrice.isPresent()) {
                return String.valueOf(maxBuyPrice.get());
            }

            return "N/A"; // Aucun prix disponible
        }

        // %dynashop_sell_min_price_shopID:itemID%
        if (identifier.startsWith("sell_min_price")) {
            String[] parts = identifier.substring(16).split(":");
            if (parts.length != 2) {
                return null; // Format invalide
            }
            String shopID = parts[0];
            String itemID = parts[1];

            // Vérifier si "useRecipe" est activé dans la configuration
            boolean useRecipe = shopConfigManager.getItemValue(shopID, itemID, "useRecipe", Boolean.class).orElse(false);
            if (useRecipe) {
                // Calculer le prix minimum via la recette
                ItemStack itemStack = ShopGuiPlusApi.getShop(shopID).getShopItem(itemID).getItem();
                if (itemStack != null) {
                    double recipeMinSellPrice = priceRecipe.calculateSellPrice(shopID, itemID, itemStack, new ArrayList<>()); // Utilisez un calcul spécifique si nécessaire
                    return String.valueOf(recipeMinSellPrice);
                }
                return "N/A"; // Aucun prix disponible via recette
            }

            // Fallback sur les fichiers de configuration
            Optional<Double> minSellPrice = shopConfigManager.getItemValue(shopID, itemID, "minSell", Double.class);
            if (minSellPrice.isPresent()) {
                return String.valueOf(minSellPrice.get());
            }

            return "N/A"; // Aucun prix disponible
        }

        // %dynashop_sell_max_price_shopID:itemID%
        if (identifier.startsWith("sell_max_price")) {
            String[] parts = identifier.substring(16).split(":");
            if (parts.length != 2) {
                return null; // Format invalide
            }
            String shopID = parts[0];
            String itemID = parts[1];

            // Vérifier si "useRecipe" est activé dans la configuration
            boolean useRecipe = shopConfigManager.getItemValue(shopID, itemID, "useRecipe", Boolean.class).orElse(false);
            if (useRecipe) {
                // Calculer le prix maximum via la recette
                ItemStack itemStack = ShopGuiPlusApi.getShop(shopID).getShopItem(itemID).getItem();
                if (itemStack != null) {
                    double recipeMaxSellPrice = priceRecipe.calculateSellPrice(shopID, itemID, itemStack, new ArrayList<>()); // Utilisez un calcul spécifique si nécessaire
                    return String.valueOf(recipeMaxSellPrice);
                }
                return "N/A"; // Aucun prix disponible via recette
            }

            // Fallback sur les fichiers de configuration
            Optional<Double> maxSellPrice = shopConfigManager.getItemValue(shopID, itemID, "maxSell", Double.class);
            if (maxSellPrice.isPresent()) {
                return String.valueOf(maxSellPrice.get());
            }

            return "N/A"; // Aucun prix disponible
        }

        return null; // Placeholder non reconnu

        // // %dynashop_buy_min_price_shopID:itemID%
        // if (identifier.contains("buy_min_price")) {
        //     // String itemID = identifier.substring(15).toUpperCase().replace("-", "_").replace(" ", "_");
        //     if (identifier.split(":").length != 2) {
        //         return null; // Invalid format
        //     }
        //     String shopID = identifier.substring(15, identifier.indexOf(":"));
        //     String itemID = identifier.substring(identifier.indexOf(":") + 1).replace("-", "_").replace(" ", "_");
        //     try {
        //         // la valeur est dans le fichier de config de ShopGui+
        //         return Double.toString(this.itemDataManager.getBuyMinPrice(shopID, itemID).orElse(0.0));
        //     } catch (SQLException e) {
        //         e.printStackTrace();
        //         return null;
        //     }
        // }

        // // %dynashop_buy_max_price_shopID:itemID%
        // if (identifier.contains("buy_max_price")) {
        //     String itemID = identifier.substring(15).toUpperCase().replace("-", "_").replace(" ", "_");
        //     try {
        //         return Double.toString(this.itemDataManager.getBuyMaxPrice(itemID));
        //     } catch (SQLException e) {
        //         e.printStackTrace();
        //         return null;
        //     }
        // }
        
        // // %dynashop_sell_price_shopID:itemID%
        // if (identifier.contains("sell_price")) {
        //     String itemID = identifier.substring(11).toUpperCase().replace("-", "_").replace(" ", "_");
        //     try {
        //         return Double.toString(this.itemDataManager.getSellPrice(itemID));
        //     } catch (SQLException e) {
        //         e.printStackTrace();
        //         return null;
        //     }
        // }

        // // %dynashop_sell_min_price_shopID:itemID%
        // if (identifier.contains("sell_min_price")) {
        //     String itemID = identifier.substring(16).toUpperCase().replace("-", "_").replace(" ", "_");
        //     try {
        //         return Double.toString(this.itemDataManager.getSellMinPrice(itemID));
        //     } catch (SQLException e) {
        //         e.printStackTrace();
        //         return null;
        //     }
        // }

        // // %dynashop_sell_max_price_shopID:itemID%
        // if (identifier.contains("sell_max_price")) {
        //     String itemID = identifier.substring(16).toUpperCase().replace("-", "_").replace(" ", "_");
        //     try {
        //         return Double.toString(this.itemDataManager.getSellMaxPrice(itemID));
        //     } catch (SQLException e) {
        //         e.printStackTrace();
        //         return null;
        //     }
        // }
        
        // // We return null if an invalid placeholder (f.e. %someplugin_placeholder3%)
        // // was provided
        // return "";
    }

    public String setPlaceholders(Player player, String identifier) {
        return PlaceholderAPI.setPlaceholders(player, identifier);
    }


    // /**
    //  * Cette méthode spéciale est appelée directement par ShopGUI+ via PlaceholderAPI
    //  * pour obtenir automatiquement les prix pour l'item actuellement affiché.
    //  */
    // public String onPlaceholderRequest(Player player, String identifier) {
    //     if (player == null) {
    //         return "";
    //     }
        
    //     // Format: shop_price_TYPE - où TYPE peut être: buy, sell, buy_min, buy_max, sell_min, sell_max
    //     if (identifier.startsWith("shop_price_")) {
    //         String priceType = identifier.substring(11); // Extraire le type après "shop_price_"
            
    //         // Récupérer l'item et le shop actuellement visualisés par le joueur
    //         // Utilisation de la méthode getNativeShopItemFromContext de l'API ShopGUI+
    //         try {
    //             // Object[] context = ShopGuiPlusApi.getOpenShopContext(player);
    //             Object[] context = ShopGuiPlusApi.getPlugin().getPlayerManager().getPlayerData(player).getOpenGui().toString().split(",");
    //             if (context == null || context.length < 2) {
    //                 return "N/A"; // Aucun contexte disponible
    //             }
                
    //             String shopID = (String) context[0];
    //             String itemID = (String) context[1];
                
    //             // Maintenant que nous avons le shopID et itemID, on peut obtenir le prix
    //             return getPriceByType(shopID, itemID, priceType);
    //         } catch (Exception e) {
    //             mainPlugin.getLogger().warning("Erreur lors de la récupération du contexte ShopGUI+: " + e.getMessage());
    //             return "Erreur";
    //         }
    //     }
        
    //     return null; // Pas notre placeholder
    // }

}

// public class DynaShopExpansion extends PlaceholderExpansion {

//     private final ItemDataManager itemDataManager;
//     private final ShopConfigManager shopConfigManager;
//     private final PriceRecipe priceRecipe;

//     private final Map<String, BiFunction<String[], OfflinePlayer, String>> placeholderHandlers = new HashMap<>();

//     public DynaShopExpansion(ItemDataManager itemDataManager, ShopConfigManager shopConfigManager, PriceRecipe priceRecipe) {
//         this.itemDataManager = itemDataManager;
//         this.shopConfigManager = shopConfigManager;
//         this.priceRecipe = priceRecipe;

//         // Register placeholder handlers
//         placeholderHandlers.put("buy_price", this::handleBuyPrice);
//         placeholderHandlers.put("sell_price", this::handleSellPrice);
//         placeholderHandlers.put("buy_min_price", this::handleBuyMinPrice);
//         placeholderHandlers.put("buy_max_price", this::handleBuyMaxPrice);
//         placeholderHandlers.put("sell_min_price", this::handleSellMinPrice);
//         placeholderHandlers.put("sell_max_price", this::handleSellMaxPrice);
//     }

//     @Override
//     public String onRequest(OfflinePlayer player, String identifier) {
//         String[] parts = identifier.split("_", 2);
//         if (parts.length < 2) {
//             return null; // Invalid format
//         }

//         String placeholderType = parts[0] + "_" + parts[1];
//         String[] args = parts.length > 2 ? parts[2].split(":") : new String[0];

//         BiFunction<String[], OfflinePlayer, String> handler = placeholderHandlers.get(placeholderType);
//         if (handler != null) {
//             return handler.apply(args, player);
//         }

//         return null; // Placeholder not recognized
//     }

//     private String handleBuyPrice(String[] args, OfflinePlayer player) {
//         return handlePrice(args, "buyPrice", priceRecipe::calculateBuyPrice);
//     }

//     private String handleSellPrice(String[] args, OfflinePlayer player) {
//         return handlePrice(args, "sellPrice", priceRecipe::calculateSellPrice);
//     }

//     private String handleBuyMinPrice(String[] args, OfflinePlayer player) {
//         return handlePrice(args, "minBuy", priceRecipe::calculateBuyPrice);
//     }

//     private String handleBuyMaxPrice(String[] args, OfflinePlayer player) {
//         return handlePrice(args, "maxBuy", priceRecipe::calculateBuyPrice);
//     }

//     private String handleSellMinPrice(String[] args, OfflinePlayer player) {
//         return handlePrice(args, "minSell", priceRecipe::calculateSellPrice);
//     }

//     private String handleSellMaxPrice(String[] args, OfflinePlayer player) {
//         return handlePrice(args, "maxSell", priceRecipe::calculateSellPrice);
//     }

//     private String handlePrice(String[] args, String configKey, QuadFunction<String, String, ItemStack, List<String>, Double> recipeCalculator) {
//         if (args.length != 2) {
//             return null; // Invalid format
//         }

//         String shopID = args[0].replace("-", "_").replace(" ", "_");
//         String itemID = args[1].replace("-", "_").replace(" ", "_");

//         // Check if "useRecipe" is enabled
//         boolean useRecipe = shopConfigManager.getItemBooleanValue(shopID, itemID, "useRecipe").orElse(false);
//         if (useRecipe) {
//             ItemStack itemStack = ShopGuiPlusApi.getShop(shopID).getShopItem(itemID).getItem();
//             if (itemStack != null) {
//                 double recipePrice = recipeCalculator.apply(shopID, itemID, itemStack, new ArrayList<>());
//                 return String.valueOf(recipePrice);
//             }
//             return "N/A"; // No price available via recipe
//         }

//         // Fallback to database or configuration
//         Optional<Double> price = itemDataManager.getPrice(shopID, itemID, configKey);
//         if (price.isPresent()) {
//             return String.valueOf(price.get());
//         }

//         Optional<Double> configPrice = shopConfigManager.getItemValue(shopID, itemID, configKey);
//         return configPrice.map(String::valueOf).orElse("N/A");
//     }

//     @FunctionalInterface
//     private interface QuadFunction<T, U, V, W, R> {
//         R apply(T t, U u, V v, W w);
//     }
// }