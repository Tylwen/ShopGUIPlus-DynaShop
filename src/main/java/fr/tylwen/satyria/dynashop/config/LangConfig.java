package fr.tylwen.satyria.dynashop.config;

import org.bukkit.configuration.file.FileConfiguration;

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

    private final String msgLimit;
    private final String msgLimitReached;
    private final String msgLimitCannotBuy;
    private final String msgLimitCannotSell;
    // private final String msgLimitExceeded;

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

        this.msgLimitReached = config.getString("limit.reached", "&cYou have reached your limit. Try again in %time%");
        this.msgLimit = config.getString("limit.limit", "&cYou have reached your limit for this item.");

        this.msgLimitCannotBuy = config.getString("limit.cannotbuy", "&cLimit reached! You can only buy %limit% more of this item at this time.");
        this.msgLimitCannotSell = config.getString("limit.cannotSell", "&cLimit reached! You can only sell %limit% more of this item at this time.");
    }

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
}
