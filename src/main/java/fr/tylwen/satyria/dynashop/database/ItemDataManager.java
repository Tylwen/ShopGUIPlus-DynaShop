package fr.tylwen.satyria.dynashop.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
// import java.util.HashMap;
import java.util.Map;
// import java.sql.SQLException;
// import java.util.ArrayList;
// import java.util.List;
import java.util.Optional;

import fr.tylwen.satyria.dynashop.DynaShopPlugin;
import fr.tylwen.satyria.dynashop.config.DataConfig;
import fr.tylwen.satyria.dynashop.data.DynamicPrice;
import net.brcdev.shopgui.shop.item.ShopItem;

// import org.jetbrains.annotations.NotNull;

// import fr.tylwen.satyria.dynashop.DynaShopPlugin;
// import fr.tylwen.satyria.dynashop.item.Item;
// import fr.tylwen.satyria.dynashop.item.ItemManager;
// import fr.tylwen.satyria.dynashop.price.PriceHistory;

public class ItemDataManager {

    private final DataManager dataManager;
    private final DataConfig dataConfig;

    // public ItemDataManager(DataManager dataManager) {
    //     this.dataManager = dataManager;
    // }
    public ItemDataManager(DataManager dataManager) {
        this.dataManager = dataManager;
        this.dataConfig = new DataConfig(DynaShopPlugin.getInstance().getConfigMain());
    }

    public Optional<DynamicPrice> getItemValues(String shopID, String itemID) {
        String query = "SELECT buyPrice, sellPrice, stock FROM " + dataConfig.getDatabaseTablePrefix() + "_prices WHERE shopID = ? AND itemID = ?";
        try (Connection connection = dataManager.getDatabaseConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, shopID);
            stmt.setString(2, itemID);

            ResultSet resultSet = stmt.executeQuery();
            if (resultSet.next()) {
                double buyPrice = resultSet.getDouble("buyPrice");
                double sellPrice = resultSet.getDouble("sellPrice");
                int stock = resultSet.getInt("stock");
                return Optional.of(new DynamicPrice(buyPrice, sellPrice, stock));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    /**
     * Méthode générique pour récupérer un prix (achat ou vente) depuis la base de données.
     *
     * @param shopID L'ID du shop.
     * @param itemID L'ID de l'item.
     * @param column Le nom de la colonne à récupérer ("buyPrice" ou "sellPrice").
     * @return Le prix (Optional.empty() si non trouvé).
     */
    public Optional<Double> getPrice(String shopID, String itemID, String column) {
        String query = "SELECT " + column + " FROM " + dataConfig.getDatabaseTablePrefix() + "_prices WHERE shopID = ? AND itemID = ?";
        try (Connection connection = dataManager.getDatabaseConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, shopID);
            stmt.setString(2, itemID);

            ResultSet resultSet = stmt.executeQuery();
            if (resultSet.next()) {
                return Optional.of(resultSet.getDouble(column));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    /**
     * Récupère le prix d'achat d'un item dans un shop spécifique.
     *
     * @param shopID L'ID du shop.
     * @param itemID L'ID de l'item.
     * @return Le prix d'achat (Optional.empty() si non trouvé).
     */
    public Optional<Double> getBuyPrice(String shopID, String itemID) {
        return getPrice(shopID, itemID, "buyPrice");
    }

    /**
     * Récupère le prix de vente d'un item dans un shop spécifique.
     *
     * @param shopID L'ID du shop.
     * @param itemID L'ID de l'item.
     * @return Le prix de vente (Optional.empty() si non trouvé).
     */
    public Optional<Double> getSellPrice(String shopID, String itemID) {
        return getPrice(shopID, itemID, "sellPrice");
    }


    /**
     * Méthode générique pour récupérer le prix (achat et vente) depuis la base de données.
     *
     * @param shopID L'ID du shop.
     * @param itemID L'ID de l'item.
     * @return Les prix d'achat et de vente (Optional.empty() si non trouvé).
     */
    public Optional<DynamicPrice> getPrices(String shopID, String itemID) {
        String query = "SELECT buyPrice, sellPrice FROM " + dataConfig.getDatabaseTablePrefix() + "_prices WHERE shopID = ? AND itemID = ?";
        try (Connection connection = dataManager.getDatabaseConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, shopID);
            stmt.setString(2, itemID);

            ResultSet resultSet = stmt.executeQuery();
            if (resultSet.next()) {
                double buyPrice = resultSet.getDouble("buyPrice");
                double sellPrice = resultSet.getDouble("sellPrice");
                return Optional.of(new DynamicPrice(buyPrice, sellPrice));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    /**
     * Sauvegarde ou met à jour les prix d'un item dans un shop spécifique.
     *
     * @param shopID   L'ID du shop.
     * @param itemID   L'ID de l'item.
     * @param buyPrice Le prix d'achat.
     * @param sellPrice Le prix de vente.
     */
    public void savePrice(String shopID, String itemID, double buyPrice, double sellPrice) {
        String query = "REPLACE INTO " + dataConfig.getDatabaseTablePrefix() + "_prices (shopID, itemID, buyPrice, sellPrice) VALUES (?, ?, ?, ?)";
        try (Connection connection = dataManager.getDatabaseConnection();
            PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, shopID);
            stmt.setString(2, itemID);
            stmt.setDouble(3, buyPrice);
            stmt.setDouble(4, sellPrice);

            stmt.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Sauvegarde ou met à jour les prix d'un item dans un shop spécifique.
     *
     * @param shopID   L'ID du shop.
     * @param itemID   L'ID de l'item.
     * @param buyPrice Le prix d'achat.
     * @param sellPrice Le prix de vente.
     * @param stock Le stock de l'item.
     */
    public void savePrice(String shopID, String itemID, double buyPrice, double sellPrice, int stock) {
        String query = "REPLACE INTO " + dataConfig.getDatabaseTablePrefix() + "_prices (shopID, itemID, buyPrice, sellPrice, stock) VALUES (?, ?, ?, ?, ?)";
        try (Connection connection = dataManager.getDatabaseConnection();
            PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, shopID);
            stmt.setString(2, itemID);
            stmt.setDouble(3, buyPrice);
            stmt.setDouble(4, sellPrice);
            stmt.setInt(5, stock);

            stmt.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Sauvegarde ou met à jour plusieurs prix dans la base de données.
     *
     * @param priceMap Une map contenant les ShopItem et leurs DynamicPrice associés.
     */
    public void savePrices(Map<ShopItem, DynamicPrice> priceMap) {
        String query = "REPLACE INTO " + dataConfig.getDatabaseTablePrefix() + "_prices (shopID, itemID, buyPrice, sellPrice) VALUES (?, ?, ?, ?)";
        try (Connection connection = dataManager.getDatabaseConnection();
            PreparedStatement stmt = connection.prepareStatement(query)) {
            for (Map.Entry<ShopItem, DynamicPrice> entry : priceMap.entrySet()) {
                ShopItem item = entry.getKey();
                DynamicPrice price = entry.getValue();

                stmt.setString(1, item.getShop().getId());
                stmt.setString(2, item.getId());
                stmt.setDouble(3, price.getBuyPrice());
                stmt.setDouble(4, price.getSellPrice());
                stmt.addBatch();
            }
            stmt.executeBatch();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void saveDynamicPrice(ShopItem item, DynamicPrice price) {
        savePrice(item.getShop().getId(), item.getId(), price.getBuyPrice(), price.getSellPrice(), price.getStock());
    }

    public DynamicPrice loadDynamicPrice(String shopID, String itemID) {
        Optional<DynamicPrice> priceData = getPrices(shopID, itemID);
        if (priceData.isPresent()) {
            DynamicPrice price = priceData.get();
            price.adjustPricesBasedOnStock();
            return price;
        }
        return null;
    }

    public Optional<Integer> getStock(String shopID, String itemID) {
        String query = "SELECT stock FROM " + dataConfig.getDatabaseTablePrefix() + "_prices WHERE shopID = ? AND itemID = ?";
        try (Connection connection = dataManager.getDatabaseConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, shopID);
            stmt.setString(2, itemID);

            ResultSet resultSet = stmt.executeQuery();
            if (resultSet.next()) {
                return Optional.of(resultSet.getInt("stock"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    public Optional<Integer> getStock(ShopItem item) {
        return getStock(item.getShop().getId(), item.getId());
    }

    public void setStock(String shopID, String itemID, int stock) {
        String query = "UPDATE " + dataConfig.getDatabaseTablePrefix() + "_prices SET stock = ? WHERE shopID = ? AND itemID = ?";
        try (Connection connection = dataManager.getDatabaseConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setInt(1, stock);
            stmt.setString(2, shopID);
            stmt.setString(3, itemID);
            stmt.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setStock(ShopItem item, int stock) {
        setStock(item.getShop().getId(), item.getId(), stock);
    }

    /**
     * Supprime un item de la base de données.
     *
     * @param shopID L'ID du shop.
     * @param itemID L'ID de l'item.
     */
    public void deleteItem(String shopID, String itemID) {
        String query = "DELETE FROM " + dataConfig.getDatabaseTablePrefix() + "_prices WHERE shopID = ? AND itemID = ?";
        try (Connection connection = dataManager.getDatabaseConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, shopID);
            stmt.setString(2, itemID);
            stmt.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Supprime tous les items d'un shop de la base de données.
     *
     * @param shopID L'ID du shop.
     */
    public void deleteShopItems(String shopID) {
        String query = "DELETE FROM " + dataConfig.getDatabaseTablePrefix() + "_prices WHERE shopID = ?";
        try (Connection connection = dataManager.getDatabaseConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, shopID);
            stmt.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Supprime tous les items de la base de données.
     */
    public void deleteAllItems() {
        String query = "DELETE FROM " + dataConfig.getDatabaseTablePrefix() + "_prices";
        try (Connection connection = dataManager.getDatabaseConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // /**
    //  * Récupère tous les items d'un shop de la base de données.
    //  *
    //  * @param shopID L'ID du shop.
    //  * @return Une map contenant les items et leurs prix associés.
    //  */
    // public Map<ShopItem, DynamicPrice> getAllItems(String shopID) {
    //     Map<ShopItem, DynamicPrice> items = new HashMap<>();
    //     String query = "SELECT itemID, buyPrice, sellPrice FROM " + dataConfig.getDatabaseTablePrefix() + "_prices WHERE shopID = ?";
    //     try (Connection connection = dataManager.getDatabaseConnection();
    //          PreparedStatement stmt = connection.prepareStatement(query)) {
    //         stmt.setString(1, shopID);
    //         ResultSet resultSet = stmt.executeQuery();
    //         while (resultSet.next()) {
    //             String itemID = resultSet.getString("itemID");
    //             double buyPrice = resultSet.getDouble("buyPrice");
    //             double sellPrice = resultSet.getDouble("sellPrice");
    //             items.put(new ShopItem(shopID, itemID), new DynamicPrice(buyPrice, sellPrice));
    //         }
    //     } catch (Exception e) {
    //         e.printStackTrace();
    //     }
    //     return items;
    // }

    // /**
    //  * Récupère tous les items de la base de données.
    //  *
    //  * @return Une map contenant les items et leurs prix associés.
    //  */
    // public Map<ShopItem, DynamicPrice> getAllItems() {
    //     Map<ShopItem, DynamicPrice> items = new HashMap<>();
    //     String query = "SELECT shopID, itemID, buyPrice, sellPrice FROM " + dataConfig.getDatabaseTablePrefix() + "_prices";
    //     try (Connection connection = dataManager.getDatabaseConnection();
    //          PreparedStatement stmt = connection.prepareStatement(query)) {
    //         ResultSet resultSet = stmt.executeQuery();
    //         while (resultSet.next()) {
    //             String shopID = resultSet.getString("shopID");
    //             String itemID = resultSet.getString("itemID");
    //             double buyPrice = resultSet.getDouble("buyPrice");
    //             double sellPrice = resultSet.getDouble("sellPrice");
    //             items.put(new ShopItem(new Shop(shopID), itemID), new DynamicPrice(buyPrice, sellPrice));
    //         }
    //     } catch (Exception e) {
    //         e.printStackTrace();
    //     }
    //     return items;
    // }
    
    /**
     * Vérifie si un item existe dans la base de données.
     *
     * @param shopID L'ID du shop.
     * @param itemID L'ID de l'item.
     * @return true si l'item existe, false sinon.
     */
    public boolean itemExists(String shopID, String itemID) {
        String query = "SELECT COUNT(*) FROM " + dataConfig.getDatabaseTablePrefix() + "_prices WHERE shopID = ? AND itemID = ?";
        try (Connection connection = dataManager.getDatabaseConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, shopID);
            stmt.setString(2, itemID);
            ResultSet resultSet = stmt.executeQuery();
            if (resultSet.next()) {
                return resultSet.getInt(1) > 0;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    
    /**
     * Vérifie si un item a un prix
     * 
    * @param shopID L'ID du shop.
    * @param itemID L'ID de l'item.
    * @return true si l'item a un prix, false sinon.
    */
    public boolean itemHasPrice(String shopID, String itemID) {
        String query = "SELECT COUNT(*) FROM " + dataConfig.getDatabaseTablePrefix() + "_prices WHERE shopID = ? AND itemID = ? AND (buyPrice > 0 OR sellPrice > 0)";
        try (Connection connection = dataManager.getDatabaseConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, shopID);
            stmt.setString(2, itemID);
            ResultSet resultSet = stmt.executeQuery();
            if (resultSet.next()) {
                return resultSet.getInt(1) > 0;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
    public boolean priceExists(String shopID, String itemID) {
        String query = "SELECT 1 FROM " + dataConfig.getDatabaseTablePrefix() + "_prices WHERE shopID = ? AND itemID = ?";
        try (Connection connection = dataManager.getDatabaseConnection();
            PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, shopID);
            stmt.setString(2, itemID);

            ResultSet resultSet = stmt.executeQuery();
            return resultSet.next(); // Retourne true si une ligne est trouvée
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
}
