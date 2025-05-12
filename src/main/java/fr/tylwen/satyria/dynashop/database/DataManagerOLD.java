package fr.tylwen.satyria.dynashop.database;

import net.brcdev.shopgui.ShopGuiPlusApi;
import net.brcdev.shopgui.shop.Shop;
import net.brcdev.shopgui.shop.item.ShopItem;

// import org.bukkit.configuration.file.FileConfiguration;
// import org.bukkit.configuration.file.YamlConfiguration;
// import org.bukkit.plugin.Plugin;

import fr.tylwen.satyria.dynashop.DynaShopPlugin;
import fr.tylwen.satyria.dynashop.config.DataConfig;
import fr.tylwen.satyria.dynashop.data.DynamicPrice;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class DataManagerOLD {
    private final DynaShopPlugin plugin;
    private Connection databaseConnection;
    private final DataConfig dataConfig;

    public DataManagerOLD(DynaShopPlugin plugin) {
        this.plugin = plugin;
        this.dataConfig = new DataConfig(plugin.getConfigMain());
    }

    // Initialisation de la base de données
    public void initDatabase() {
        // FileConfiguration config = plugin.getConfig();
        // String type = config.getString("database.type", "sqlite").toLowerCase();
        String type = dataConfig.getDatabaseType(); // Utilisation de la méthode getDatabaseType() de DataConfig
        try {
            if (type.equals("mysql")) {
                // String host = config.getString("database.mysql.host");
                // int port = config.getInt("database.mysql.port");
                // String name = config.getString("database.mysql.name");
                // String username = config.getString("database.mysql.username");
                // String password = config.getString("database.mysql.password");
                String host = dataConfig.getDatabaseHost();
                int port = dataConfig.getDatabasePort();
                String name = dataConfig.getDatabaseName();
                String username = dataConfig.getDatabaseUsername();
                String password = dataConfig.getDatabasePassword();

                String url = "jdbc:mysql://" + host + ":" + port + "/" + name + "?useSSL=false";
                databaseConnection = DriverManager.getConnection(url, username, password);
            } else if (type.equals("sqlite")) {
                // File databaseFile = new File(plugin.getDataFolder(), "dynashop.db");
                File databaseFile = new File(plugin.getDataFolder(), dataConfig.getDatabaseSqliteFile());
                if (!databaseFile.exists()) {
                    try {
                        boolean fileCreated = databaseFile.createNewFile();
                        if (fileCreated) {
                            plugin.getLogger().info("Le fichier de base de données SQLite a été créé avec succès.");
                        } else {
                            plugin.getLogger().warning("Le fichier de base de données SQLite existe déjà.");
                        }
                    } catch (IOException e) {
                        plugin.getLogger().severe("Erreur lors de la création du fichier de base de données SQLite : " + e.getMessage());
                        e.printStackTrace();
                    }
                }
                // File databaseFile = new File(plugin.getDataFolder(), "dynashop.db");
                // if (!checkDatabaseExists()) {
                //     createDatabase(); // Créer la base de données si elle n'existe pas
                // }
                String url = "jdbc:sqlite:" + databaseFile.getAbsolutePath();
                databaseConnection = DriverManager.getConnection(url);
            }

            createTables();
        } catch (Exception e) {
            e.printStackTrace();
            plugin.getLogger().severe("Impossible de se connecter à la base de données !");
        }
    }

    // Création des tables
    private void createTables() {
        try (Statement stmt = getDatabaseConnection().createStatement()) {
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS " + dataConfig.getDatabaseTablePrefix() + "_prices (" +
                "shopID VARCHAR(255) NOT NULL, " +
                "itemID VARCHAR(255) NOT NULL, " +
                "buyPrice DOUBLE NOT NULL, " +
                "sellPrice DOUBLE NOT NULL, " +
                "stock INT DEFAULT 0, " +
                "PRIMARY KEY (shopID, itemID)" +
                ");"
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // // // Création de la BDD sqlite
    // public void createDatabase() {
    //     try {
    //         File databaseFile = new File(plugin.getDataFolder(), "dynashop.db");
    //         if (!databaseFile.exists()) {
    //             boolean fileCreated = databaseFile.createNewFile();
    //             if (fileCreated) {
    //                 plugin.getLogger().info("Le fichier de base de données SQLite a été créé avec succès.");
    //             } else {
    //                 plugin.getLogger().warning("Le fichier de base de données SQLite existe déjà.");
    //             }
    //         }
    //     } catch (Exception e) {
    //         e.printStackTrace();
    //         plugin.getLogger().severe("Erreur lors de la création du fichier de base de données SQLite : " + e.getMessage());
    //     }
    // }
    // // Vérification de l'existence de la BDD sqlite
    // public boolean checkDatabaseExists() {
    //     File databaseFile = new File(plugin.getDataFolder(), "dynashop.db");
    //     return databaseFile.exists();
    // }

    // Récupération de la connexion à la base de données
    public Connection getDatabaseConnection() {
        try {
            if (databaseConnection == null || databaseConnection.isClosed()) {
                initDatabase(); // Réinitialiser la connexion si elle est fermée
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return databaseConnection;
    }

    public void reloadDatabaseConnection() {
        try {
            if (databaseConnection != null && !databaseConnection.isClosed()) {
                databaseConnection.close();
            }
            initDatabase(); // Réinitialiser la connexion
            plugin.getLogger().info("Connexion à la base de données rechargée avec succès.");
        } catch (Exception e) {
            plugin.getLogger().severe("Erreur lors du rechargement de la connexion à la base de données : " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Sauvegarde des prix dans la base de données
    public void savePricesToDatabase(Map<ShopItem, DynamicPrice> priceMap) {
        try (Connection connection = getDatabaseConnection()) {
            for (Map.Entry<ShopItem, DynamicPrice> entry : priceMap.entrySet()) {
                ShopItem item = entry.getKey();
                DynamicPrice price = entry.getValue();

                try (PreparedStatement stmt = connection.prepareStatement(
                    "REPLACE INTO " + dataConfig.getDatabaseTablePrefix() + "_prices (shopID, itemID, buyPrice, sellPrice, stock) VALUES (?, ?, ?, ?, ?)"
                )) {
                    stmt.setString(1, item.getShop().getId());
                    stmt.setString(2, item.getId());
                    stmt.setDouble(3, price.getBuyPrice());
                    stmt.setDouble(4, price.getSellPrice());
                    stmt.setInt(5, price.getStock());
                    stmt.executeUpdate();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Chargement des prix depuis la base de données
    public Map<ShopItem, DynamicPrice> loadPricesFromDatabase() {
        Map<ShopItem, DynamicPrice> priceMap = new HashMap<>();

        try (PreparedStatement stmt = getDatabaseConnection().prepareStatement(
            "SELECT shopID, itemID, buyPrice, sellPrice, stock FROM " + dataConfig.getDatabaseTablePrefix() + "_prices"
        )) {
            ResultSet resultSet = stmt.executeQuery();
            while (resultSet.next()) {
                String shopID = resultSet.getString("shopID");
                String itemID = resultSet.getString("itemID");
                double buyPrice = resultSet.getDouble("buyPrice");
                double sellPrice = resultSet.getDouble("sellPrice");
                int stock = resultSet.getInt("stock");

                Shop shop = ShopGuiPlusApi.getPlugin().getShopManager().getShopById(shopID);
                if (shop != null) {
                    ShopItem item = shop.getShopItems().stream()
                        .filter(i -> i.getId().equals(itemID))
                        .findFirst()
                        .orElse(null);

                    if (item != null) {
                        // DynamicPrice price = new DynamicPrice(buyPrice, sellPrice);
                        DynamicPrice price = new DynamicPrice(buyPrice, sellPrice, stock);
                        // DynamicPrice price = new DynamicPrice(buyPrice, sellPrice, 0, 0, 0, 0, 1.0, 1.0, 1.0, 1.0);
                        priceMap.put(item, price);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return priceMap;
    }

    // Fermeture de la connexion à la base de données
    public void closeDatabase() {
        try {
            if (databaseConnection != null && !databaseConnection.isClosed()) {
                databaseConnection.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}