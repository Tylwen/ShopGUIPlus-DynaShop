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
package fr.tylwen.satyria.dynashop.data;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.configuration.file.YamlConfiguration;

import fr.tylwen.satyria.dynashop.DynaShopPlugin;
// import net.brcdev.shopgui.ShopGuiPlusApi;

public class ShopFile {
    private static final Map<String, File> shopIDToFile = new HashMap<>();
    private static final Map<File, String> fileToShopID = new HashMap<>();

    /**
     * Charge tous les fichiers de shop et associe les shopIDs à leurs fichiers.
     *
     * @param shopFolder Le dossier contenant les fichiers YAML des shops.
     */
    public static void loadShopFiles(File shopFolder) {
        if (!shopFolder.exists() || !shopFolder.isDirectory()) {
            return;
        }

        for (File file : shopFolder.listFiles((dir, name) -> name.endsWith(".yml"))) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

            for (String shopID : config.getKeys(false)) {
                // Associer le shopID au fichier
                shopIDToFile.put(shopID, file);
                fileToShopID.put(file, shopID);
            }
        }
    }

    /**
     * Récupère le fichier correspondant à un shopID.
     *
     * @param shopID L'ID du shop.
     * @return Le fichier correspondant, ou null si non trouvé.
     */
    public static File getFileByShopID(String shopID) {
        return shopIDToFile.get(shopID);
    }

    /**
     * Récupère le shopID correspondant à un fichier.
     *
     * @param file Le fichier du shop.
     * @return L'ID du shop, ou null si non trouvé.
     */
    public static String getShopIDByFile(File file) {
        return fileToShopID.get(file);
    }

    /**
     * Vérifie si un shopID existe.
     *
     * @param shopID L'ID du shop.
     * @return true si le shopID existe, false sinon.
     */
    public static boolean shopIDExists(String shopID) {
        return shopIDToFile.containsKey(shopID);
    }

    /**
     * Vérifie si un fichier de shop existe.
     *
     * @param file Le fichier du shop.
     * @return true si le fichier existe, false sinon.
     */
    public static boolean fileExists(File file) {
        return fileToShopID.containsKey(file);
    }

    
    /**
     * Récupère tous les fichiers de shop dans un dossier donné.
     *
     * @param shopFolder Le dossier contenant les fichiers de shop.
     * @return Un tableau de fichiers de shop.
     */
    public static File[] getShopFiles(File shopFolder) {
        if (!shopFolder.exists() || !shopFolder.isDirectory()) {
            return new File[0];
        }
        return shopFolder.listFiles((dir, name) -> name.endsWith(".yml"));
    }

    /**
     * Récupère tous les fichiers de shop
     * 
     * @param shopFolder Le dossier contenant les fichiers de shop.
     * @return Un tableau de fichiers de shop.
     */
    public static File[] getShopFiles() {
        return getShopFiles(DynaShopPlugin.getInstance().getShopConfigManager().getShopDirectory());
        // return shopFile.listFiles((dir, name) -> name.endsWith(".yml"));
    }

    // getAllShopIDs
    public static String[] getAllShopIDs() {
        return shopIDToFile.keySet().toArray(new String[0]);
    }

    // public static YamlConfiguration getShopConfig(String shopID) {
    //     // File file = getFileByShopID(shopID);
    //     // if (file != null && file.exists()) {
    //     //     return YamlConfiguration.loadConfiguration(file);
    //     // }
    //     // return null;
    //     ShopGuiPlusApi.getPlugin().getConfigShops().getConfig().getConfigurationSection(shopID);
    //     File file = getFileByShopID(shopID);
    //     if (file != null && file.exists()) {
    //         return YamlConfiguration.loadConfiguration(file);
    //     }
    //     return null;
    // }
}