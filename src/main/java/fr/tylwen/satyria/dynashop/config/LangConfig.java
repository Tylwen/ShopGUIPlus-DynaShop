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
package fr.tylwen.satyria.dynashop.config;

import org.bukkit.configuration.file.FileConfiguration;
// import org.bukkit.entity.Player;

public class LangConfig {
    // Variables pour la configuration de la langue
    // private final String lang;
    // private final String langFile;
    // Variables pour les messages
    // private final String messagePrefix;
    // private final String messageNoPermission;
    // private final String messageNoPlayer;
    // private final String messageNoItem;
    // private final String messageNoShop;
    // private final String messageNoShopItem;
    // private final String messageNoShopItemPrice;
    // private final String messageNoShopItemStock;
    // private final String messageNoShopItemPriceMin;
    // private final String messageNoShopItemPriceMax;
    // private final String messageNoShopItemPriceMargin;
    // private final String messageNoShopItemPriceMinMultiply;
    // private final String messageNoShopItemPriceMaxMultiply;

    private final String msgOutOfStock;
    private final String msgFullStock;
    private final String placeholderOutOfStock;
    private final String placeholderStockFull; // Not used, but kept for future use

    private final String msgLimit;
    private final String msgLimitReached;
    private final String msgLimitCannotBuy;
    private final String msgLimitCannotSell;
    // private final String msgLimitExceeded;
    private final String placeholderNoLimit;
    private final String placeholderLimitBuyReached;
    private final String placeholderLimitSellReached;
    private final String placeholderLimitRemaining;

    // private final String msgTaxBuy;
    // private final String msgTaxSell;

    public LangConfig(FileConfiguration config) {
        // // Load the language configuration
        // this.lang = config.getString("lang", "en");
        // this.langFile = config.getString("lang-file", "en.yml");

        // Load the messages from the configuration file
        // this.messagePrefix = config.getString("messages.prefix", "&7[&6Dynashop&7] ");
        // this.messageNoPermission = config.getString("messages.no-permission", "&cYou don't have permission to use this command.");
        // this.messageNoPlayer = config.getString("messages.no-player", "&cYou must be a player to use this command.");
        // this.messageNoItem = config.getString("messages.no-item", "&cYou must specify an item.");
        // this.messageNoShop = config.getString("messages.no-shop", "&cYou must specify a shop.");
        // this.messageNoShopItem = config.getString("messages.no-shop-item", "&cYou must specify a shop item.");
        // this.messageNoShopItemPrice = config.getString("messages.no-shop-item-price", "&cYou must specify a price for the shop item.");
        // this.messageNoShopItemStock = config.getString("messages.no-shop-item-stock", "&cYou must specify a stock for the shop item.");
        // this.messageNoShopItemPriceMin = config.getString("messages.no-shop-item-price-min", "&cThe minimum price for the shop item must be greater than 0.");
        // this.messageNoShopItemPriceMax = config.getString("messages.no-shop-item-price-max", "&cThe maximum price for the shop item must be greater than 0.");
        // this.messageNoShopItemPriceMargin = config.getString("messages.no-shop-item-price-margin", "&cThe price margin for the shop item must be greater than 0.");
        // this.messageNoShopItemPriceMinMultiply = config.getString("messages.no-shop-item-price-min-multiply", "&cThe minimum price multiply for the shop item must be greater than 0.");
        // this.messageNoShopItemPriceMaxMultiply = config.getString("messages.no-shop-item-price-max-multiply", "&cThe maximum price multiply for the shop item must be greater than 0.");
        this.msgOutOfStock = config.getString("stock.out-of-stock", "&cThis item is out of stock.");
        this.msgFullStock = config.getString("stock.full-stock", "&cThis item is full stock we cannot sell more.");
        this.placeholderOutOfStock = config.getString("placeholder.out-of-stock", "&cOut of stock"); // "&cÉpuisé"
        this.placeholderStockFull = config.getString("placeholder.full-stock", "&cFull stock"); // "&cStock plein"

        this.msgLimitReached = config.getString("limit.reached", "&cYou have reached your limit. Try again in %time%");
        this.msgLimit = config.getString("limit.limit", "&cYou have reached your limit for this item.");
        this.msgLimitCannotBuy = config.getString("limit.cannotbuy", "&cLimit reached! You can only buy %limit% more of this item at this time.");
        this.msgLimitCannotSell = config.getString("limit.cannotSell", "&cLimit reached! You can only sell %limit% more of this item at this time.");
        // this.placeholderNoLimit = config.getString("placeholder.no-limit", "&aNo limit");
        this.placeholderNoLimit = config.getString("limit.placeholder.no-limit", "∞");
        this.placeholderLimitBuyReached = config.getString("limit.placeholder.buy_reached", "&cLimit reached! Next buy in %time%");
        this.placeholderLimitSellReached = config.getString("limit.placeholder.sell_reached", "&cLimit reached! Next sell in %time%");
        this.placeholderLimitRemaining = config.getString("limit.placeholder.remaining", "&aRemaining: %limit%");

        // this.msgTaxBuy = config.getString("tax.buy", "&aYou paid &e%tax% &afor the tax on this purchase.");
        // // this.msgTaxSell = config.getString("tax.sell", "&aYou paid &e%tax% &afor the tax on this sale.");
        // this.msgTaxBuy = config.getString("tax.buy", "&7A tax of &c%amount%$ &7(%rate%%) was charged on your purchase.");
        // this.msgTaxSell = config.getString("tax.sell", "&7A tax of &c%amount%$ &7(%rate%%) was charged on your sale.");
    }

    // /**
    //  * Envoie un message au joueur avec remplacement de variables
    //  * @param player Le joueur à qui envoyer le message
    //  * @param key La clé du message dans le fichier de langue
    //  * @param replacements Tableau de paires [variable, valeur] à remplacer
    //  */
    // public void sendMessage(Player player, String key, String[][] replacements) {
    //     String message = getMsgString(key, "");
    //     if (message.isEmpty()) return;
        
    //     // Effectuer les remplacements
    //     for (String[] replacement : replacements) {
    //         message = message.replace(replacement[0], replacement[1]);
    //     }
        
    //     player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
    // }

    // Getters for the messages
    // public String getLang() {
    //     return lang;
    // }
    // public String getLangFile() {
    //     return langFile;
    // }

    public String getMsgOutOfStock() {
        return msgOutOfStock;
    }
    public String getMsgFullStock() {
        return msgFullStock;
    }
    public String getPlaceholderOutOfStock() {
        return placeholderOutOfStock;
    }
    public String getPlaceholderStockFull() {
        return placeholderStockFull;
    }

    public String getMsgLimit() {
        return msgLimit;
    }
    public String getMsgLimitReached() {
        return msgLimitReached;
    }
    public String getMsgLimitCannotBuy() {
        return msgLimitCannotBuy;
    }
    public String getMsgLimitCannotSell() {
        return msgLimitCannotSell;
    }

    public String getPlaceholderNoLimit() {
        return this.placeholderNoLimit;
    }
    public String getPlaceholderLimitBuyReached() {
        return this.placeholderLimitBuyReached;
    }
    public String getPlaceholderLimitSellReached() {
        return this.placeholderLimitSellReached;
    }
    public String getPlaceholderLimitRemaining() {
        return this.placeholderLimitRemaining;
    }

    // public String getMsgTaxBuy() {
    //     return msgTaxBuy;
    // }
    // public String getMsgTaxSell() {
    //     return msgTaxSell;
    // }
}
