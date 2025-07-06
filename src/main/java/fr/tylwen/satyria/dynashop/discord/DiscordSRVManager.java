package fr.tylwen.satyria.dynashop.discord;

import fr.tylwen.satyria.dynashop.DynaShopPlugin;
import fr.tylwen.satyria.dynashop.price.DynamicPrice;
import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.api.ListenerPriority;
import github.scarsz.discordsrv.api.Subscribe;
import github.scarsz.discordsrv.api.events.DiscordGuildMessageReceivedEvent;
import github.scarsz.discordsrv.dependencies.jda.api.EmbedBuilder;
import github.scarsz.discordsrv.dependencies.jda.api.entities.Guild;
import github.scarsz.discordsrv.dependencies.jda.api.entities.MessageEmbed;
import github.scarsz.discordsrv.dependencies.jda.api.entities.TextChannel;
import github.scarsz.discordsrv.util.DiscordUtil;
import net.brcdev.shopgui.ShopGuiPlusApi;
import net.brcdev.shopgui.shop.Shop;
import net.brcdev.shopgui.shop.item.ShopItem;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class DiscordSRVManager {
    
    private final DynaShopPlugin plugin;
    private boolean enabled = false;
    private String announcementChannelId;
    private String commandChannelId;
    private int lowStockThreshold;
    
    public DiscordSRVManager(DynaShopPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }
    
    private void loadConfig() {
        this.enabled = plugin.getConfigMain().getBoolean("discord.enabled", false);
        this.announcementChannelId = plugin.getConfigMain().getString("discord.channels.announcements", "");
        this.commandChannelId = plugin.getConfigMain().getString("discord.channels.commands", "");
        this.lowStockThreshold = plugin.getConfigMain().getInt("discord.low-stock-threshold", 10);
    }
    
    public void initialize() {
        if (!enabled) {
            plugin.getLogger().info("Discord integration disabled");
            return;
        }
        
        // Vérifier si DiscordSRV est disponible
        if (!Bukkit.getPluginManager().isPluginEnabled("DiscordSRV")) {
            plugin.getLogger().warning("DiscordSRV not found! Discord integration disabled.");
            enabled = false;
            return;
        }
        
        // Enregistrer les listeners pour les commandes
        DiscordSRV.api.subscribe(this);
        
        plugin.getLogger().info("DiscordSRV integration initialized successfully!");
    }
    
    public void shutdown() {
        if (enabled) {
            DiscordSRV.api.unsubscribe(this);
        }
    }
    
    // =================== GESTIONNAIRE DE COMMANDES ===================
    
    @Subscribe(priority = ListenerPriority.NORMAL)
    public void onDiscordMessage(DiscordGuildMessageReceivedEvent event) {
        // Ignorer les messages des bots
        if (event.getAuthor().isBot()) return;
        
        // Vérifier si c'est dans le bon canal
        if (!commandChannelId.isEmpty() && !event.getChannel().getId().equals(commandChannelId)) {
            return;
        }
        
        String message = event.getMessage().getContentRaw().trim();
        
        // Traiter les commandes
        if (message.startsWith("!dynashop ") || message.equals("!dynashop")) {
            handleDynaShopCommand(event, message);
        }
    }
    
    private void handleDynaShopCommand(DiscordGuildMessageReceivedEvent event, String message) {
        String[] args = message.split(" ");
        
        if (args.length == 1) {
            // Commande !dynashop sans arguments - aide
            sendHelpMessage(event.getChannel());
            return;
        }
        
        String subcommand = args[1].toLowerCase();
        
        switch (subcommand) {
            case "prix", "prices" -> {
                if (args.length < 4) {
                    event.getChannel().sendMessage("❌ Usage: `!dynashop prix <shop> <item>`").queue();
                    return;
                }
                handlePricesCommand(event, args[2], args[3]);
            }
            case "stock" -> {
                if (args.length < 4) {
                    event.getChannel().sendMessage("❌ Usage: `!dynashop stock <shop> <item>`").queue();
                    return;
                }
                handleStockCommand(event, args[2], args[3]);
            }
            case "shops", "boutiques" -> handleShopsCommand(event);
            case "lowstock", "stockfaible" -> handleLowStockCommand(event);
            case "help", "aide" -> sendHelpMessage(event.getChannel());
            default -> event.getChannel().sendMessage("❌ Commande inconnue. Utilisez `!dynashop help`").queue();
        }
    }
    
    private void sendHelpMessage(TextChannel channel) {
        EmbedBuilder embed = new EmbedBuilder()
            .setTitle("🏪 DynaShop - Commandes Discord")
            .setDescription("Voici les commandes disponibles :")
            .addField("📊 Prix", "`!dynashop prix <shop> <item>` - Voir les prix d'un item", false)
            .addField("📦 Stock", "`!dynashop stock <shop> <item>` - Voir le stock d'un item", false)
            .addField("🏪 Shops", "`!dynashop shops` - Lister tous les shops", false)
            .addField("⚠️ Stock faible", "`!dynashop lowstock` - Voir les items avec stock faible", false)
            .setColor(Color.BLUE)
            .setTimestamp(LocalDateTime.now())
            .setFooter("DynaShop", null);
        
        channel.sendMessage(embed.build()).queue();
    }
    
    private void handlePricesCommand(DiscordGuildMessageReceivedEvent event, String shopId, String itemId) {
        // Exécuter de manière asynchrone pour éviter de bloquer Discord
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            // Rechercher les IDs exacts
            String actualShopId = findShopId(shopId);
            String actualItemId = findItemId(actualShopId, itemId);
            
            if (actualShopId == null) {
                event.getChannel().sendMessage("❌ Shop non trouvé: " + shopId).queue();
                return;
            }
            
            if (actualItemId == null) {
                event.getChannel().sendMessage("❌ Item non trouvé: " + itemId + " dans le shop " + shopId).queue();
                return;
            }
            
            try {
                // Récupérer les prix
                // DynamicPrice price = plugin.getDynaShopListener().getOrLoadPrice(null, actualShopId, actualItemId, null);
                DynamicPrice price = plugin.getDynaShopListener().getOrLoadPrice(null, actualShopId, actualItemId, null, new HashSet<>(), new HashMap<>());
                if (price == null) {
                    event.getChannel().sendMessage("❌ Impossible de récupérer les prix pour cet item.").queue();
                    return;
                }
                
                String itemName = getItemDisplayName(actualShopId, actualItemId);
                String shopName = getShopDisplayName(actualShopId);
                
                EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("💰 Prix de " + itemName)
                    .setDescription("Shop: **" + shopName + "**")
                    .setColor(Color.BLUE)
                    .setTimestamp(LocalDateTime.now());
                
                // Prix d'achat
                if (price.getBuyPrice() > 0) {
                    embed.addField("💳 Prix d'achat", 
                        plugin.getPriceFormatter().formatPrice(price.getBuyPrice()), true);
                }
                
                // Prix de vente
                if (price.getSellPrice() > 0) {
                    embed.addField("💰 Prix de vente", 
                        plugin.getPriceFormatter().formatPrice(price.getSellPrice()), true);
                }
                
                // Type de shop
                var type = price.getDynaShopType();
                embed.addField("🏪 Type", type.toString(), true);
                
                event.getChannel().sendMessage(embed.build()).queue();
                
            } catch (Exception e) {
                plugin.getLogger().warning("Erreur lors de la récupération des prix: " + e.getMessage());
                event.getChannel().sendMessage("❌ Erreur lors de la récupération des prix.").queue();
            }
        });
    }
    
    private void handleStockCommand(DiscordGuildMessageReceivedEvent event, String shopId, String itemId) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String actualShopId = findShopId(shopId);
            String actualItemId = findItemId(actualShopId, itemId);
            
            if (actualShopId == null || actualItemId == null) {
                event.getChannel().sendMessage("❌ Shop ou item non trouvé.").queue();
                return;
            }
            
            try {
                var type = plugin.getShopConfigManager().getTypeDynaShop(actualShopId, actualItemId);
                
                if (!type.name().contains("STOCK") && !type.name().contains("RECIPE")) {
                    event.getChannel().sendMessage("❌ Cet item n'a pas de système de stock.").queue();
                    return;
                }
                
                int stock = plugin.getStorageManager().getStock(actualShopId, actualItemId).orElse(0);
                int maxStock = plugin.getShopConfigManager()
                    .getItemValue(actualShopId, actualItemId, "stock.max", Integer.class)
                    .orElse(plugin.getDataConfig().getStockMax());
                
                String itemName = getItemDisplayName(actualShopId, actualItemId);
                String shopName = getShopDisplayName(actualShopId);
                
                double percentage = maxStock > 0 ? (stock * 100.0 / maxStock) : 0;
                Color embedColor = percentage < 25 ? Color.RED : percentage < 50 ? Color.ORANGE : Color.GREEN;
                String emoji = percentage < 25 ? "🚨" : percentage < 50 ? "⚠️" : "✅";
                
                EmbedBuilder embed = new EmbedBuilder()
                    .setTitle(emoji + " Stock de " + itemName)
                    .setDescription("Shop: **" + shopName + "**")
                    .addField("📦 Stock Actuel", String.valueOf(stock), true)
                    .addField("📊 Stock Maximum", String.valueOf(maxStock), true)
                    .addField("📈 Pourcentage", String.format("%.1f%%", percentage), true)
                    .setColor(embedColor)
                    .setTimestamp(LocalDateTime.now());
                
                event.getChannel().sendMessage(embed.build()).queue();
                
            } catch (Exception e) {
                plugin.getLogger().warning("Erreur lors de la récupération du stock: " + e.getMessage());
                event.getChannel().sendMessage("❌ Erreur lors de la récupération du stock.").queue();
            }
        });
    }
    
    private void handleShopsCommand(DiscordGuildMessageReceivedEvent event) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Set<String> shopIds = plugin.getShopConfigManager().getShops();
                
                if (shopIds.isEmpty()) {
                    event.getChannel().sendMessage("❌ Aucun shop trouvé.").queue();
                    return;
                }
                
                StringBuilder description = new StringBuilder();
                int count = 0;
                
                for (String shopId : shopIds) {
                    if (count >= 20) { // Limiter à 20 shops
                        description.append("\n... et ").append(shopIds.size() - count).append(" autres shops");
                        break;
                    }
                    
                    String shopName = getShopDisplayName(shopId);
                    description.append("🏪 **").append(shopName).append("** (`").append(shopId).append("`)\n");
                    count++;
                }
                
                EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("🏪 Liste des Shops")
                    .setDescription(description.toString())
                    .setColor(Color.CYAN)
                    .setTimestamp(LocalDateTime.now())
                    .setFooter("Total: " + shopIds.size() + " shops");
                
                event.getChannel().sendMessage(embed.build()).queue();
                
            } catch (Exception e) {
                plugin.getLogger().warning("Erreur lors de la récupération des shops: " + e.getMessage());
                event.getChannel().sendMessage("❌ Erreur lors de la récupération des shops.").queue();
            }
        });
    }
    
    private void handleLowStockCommand(DiscordGuildMessageReceivedEvent event) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                StringBuilder lowStockItems = new StringBuilder();
                int count = 0;
                
                for (String shopId : plugin.getShopConfigManager().getShops()) {
                    for (String itemId : plugin.getShopConfigManager().getShopItems(shopId)) {
                        var type = plugin.getShopConfigManager().getTypeDynaShop(shopId, itemId);
                        
                        if (type.name().contains("STOCK")) {
                            int stock = plugin.getStorageManager().getStock(shopId, itemId).orElse(0);
                            
                            if (stock <= lowStockThreshold && stock >= 0) {
                                String itemName = getItemDisplayName(shopId, itemId);
                                String shopName = getShopDisplayName(shopId);
                                
                                lowStockItems.append(String.format("⚠️ **%s** dans %s: %d\n", 
                                    itemName, shopName, stock));
                                count++;
                                
                                if (count >= 15) break; // Limiter l'affichage
                            }
                        }
                    }
                    if (count >= 15) break;
                }
                
                if (lowStockItems.length() == 0) {
                    event.getChannel().sendMessage("✅ Aucun item avec un stock faible trouvé !").queue();
                    return;
                }
                
                EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("⚠️ Items avec Stock Faible")
                    .setDescription(lowStockItems.toString())
                    .setColor(Color.ORANGE)
                    .setTimestamp(LocalDateTime.now())
                    .setFooter("Seuil: " + lowStockThreshold);
                
                event.getChannel().sendMessage(embed.build()).queue();
                
            } catch (Exception e) {
                plugin.getLogger().warning("Erreur lors de la récupération du stock faible: " + e.getMessage());
                event.getChannel().sendMessage("❌ Erreur lors de la récupération du stock faible.").queue();
            }
        });
    }
    
    // =================== MÉTHODES D'ANNONCE ===================
    
    /**
     * Annonce le restockage d'un shop
     */
    public void announceRestock(String shopId, String itemId, int newStock, int maxStock) {
        if (!enabled || announcementChannelId.isEmpty()) return;
        
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                TextChannel channel = getTextChannel(announcementChannelId);
                if (channel == null) return;
                
                String itemName = getItemDisplayName(shopId, itemId);
                String shopName = getShopDisplayName(shopId);
                
                EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("🔄 Shop Restocké !")
                    .setDescription(String.format("**%s** a été restocké dans **%s**", itemName, shopName))
                    .addField("Nouveau Stock", String.format("%d/%d", newStock, maxStock), true)
                    .addField("Pourcentage", String.format("%.1f%%", (newStock * 100.0 / maxStock)), true)
                    .setColor(Color.GREEN)
                    .setTimestamp(LocalDateTime.now())
                    .setFooter("DynaShop", null);
                
                channel.sendMessage(embed.build()).queue();
                
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to send restock announcement: " + e.getMessage());
            }
        });
    }
    
    /**
     * Annonce un stock faible
     */
    public void announceLowStock(String shopId, String itemId, int currentStock, int maxStock) {
        if (!enabled || announcementChannelId.isEmpty()) return;
        
        // Ne pas spammer - vérifier si on a déjà annoncé récemment
        if (currentStock > lowStockThreshold) return;
        
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                TextChannel channel = getTextChannel(announcementChannelId);
                if (channel == null) return;
                
                String itemName = getItemDisplayName(shopId, itemId);
                String shopName = getShopDisplayName(shopId);
                
                double percentage = (currentStock * 100.0 / maxStock);
                Color embedColor = percentage < 5 ? Color.RED : Color.ORANGE;
                String emoji = percentage < 5 ? "🚨" : "⚠️";
                
                EmbedBuilder embed = new EmbedBuilder()
                    .setTitle(emoji + " Stock Faible !")
                    .setDescription(String.format("**%s** dans **%s** a un stock faible", itemName, shopName))
                    .addField("Stock Actuel", String.format("%d/%d", currentStock, maxStock), true)
                    .addField("Pourcentage", String.format("%.1f%%", percentage), true)
                    .setColor(embedColor)
                    .setTimestamp(LocalDateTime.now())
                    .setFooter("DynaShop", null);
                
                channel.sendMessage(embed.build()).queue();
                
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to send low stock announcement: " + e.getMessage());
            }
        });
    }
    
    /**
     * Annonce un changement de prix significatif
     */
    public void announcePriceChange(String shopId, String itemId, double oldPrice, double newPrice, boolean isBuy) {
        if (!enabled || announcementChannelId.isEmpty()) return;
        
        // Seulement annoncer les changements significatifs (>5%)
        double changePercent = Math.abs((newPrice - oldPrice) / oldPrice * 100);
        if (changePercent < 5.0) return;
        
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                TextChannel channel = getTextChannel(announcementChannelId);
                if (channel == null) return;
                
                String itemName = getItemDisplayName(shopId, itemId);
                String shopName = getShopDisplayName(shopId);
                String priceType = isBuy ? "achat" : "vente";
                
                boolean isIncrease = newPrice > oldPrice;
                String emoji = isIncrease ? "📈" : "📉";
                Color embedColor = isIncrease ? Color.GREEN : Color.RED;
                String changeText = isIncrease ? "augmenté" : "diminué";
                
                EmbedBuilder embed = new EmbedBuilder()
                    .setTitle(emoji + " Changement de Prix !")
                    .setDescription(String.format("Le prix de **%s** de **%s** dans **%s** a %s", 
                        priceType, itemName, shopName, changeText))
                    .addField("Ancien Prix", plugin.getPriceFormatter().formatPrice(oldPrice), true)
                    .addField("Nouveau Prix", plugin.getPriceFormatter().formatPrice(newPrice), true)
                    .addField("Changement", String.format("%.1f%%", changePercent), true)
                    .setColor(embedColor)
                    .setTimestamp(LocalDateTime.now())
                    .setFooter("DynaShop", null);
                
                channel.sendMessage(embed.build()).queue();
                
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to send price change announcement: " + e.getMessage());
            }
        });
    }
    
    // =================== MÉTHODES UTILITAIRES ===================
    
    private TextChannel getTextChannel(String channelId) {
        try {
            Guild guild = DiscordUtil.getJda().getGuilds().get(0); // Premier serveur
            return guild.getTextChannelById(channelId);
        } catch (Exception e) {
            plugin.getLogger().warning("Impossible de récupérer le canal Discord: " + e.getMessage());
            return null;
        }
    }
    
    private String findShopId(String searchTerm) {
        // Recherche exacte d'abord
        if (plugin.getShopConfigManager().getShops().contains(searchTerm)) {
            return searchTerm;
        }
        
        // Recherche par nom
        for (String shopId : plugin.getShopConfigManager().getShops()) {
            String shopName = getShopDisplayName(shopId);
            if (shopName.toLowerCase().contains(searchTerm.toLowerCase())) {
                return shopId;
            }
        }
        
        return null;
    }
    
    private String findItemId(String shopId, String searchTerm) {
        if (shopId == null) return null;
        
        Set<String> itemIds = plugin.getShopConfigManager().getShopItems(shopId);
        
        // Recherche exacte d'abord
        if (itemIds.contains(searchTerm)) {
            return searchTerm;
        }
        
        // Recherche par nom
        for (String itemId : itemIds) {
            String itemName = getItemDisplayName(shopId, itemId);
            if (itemName.toLowerCase().contains(searchTerm.toLowerCase())) {
                return itemId;
            }
        }
        
        return null;
    }
    
    // private String getItemDisplayName(String shopId, String itemId) {
    //     try {
    //         return plugin.getShopConfigManager().getItemName(null, shopId, itemId);
    //     } catch (Exception e) {
    //         return itemId;
    //     }
    // }
    private String getItemDisplayName(String shopId, String itemId) {
        try {
            // ✅ CORRECTION : Récupération directe sans passer par NBT-API
            Shop shop = ShopGuiPlusApi.getPlugin().getShopManager().getShopById(shopId);
            if (shop == null) return itemId;
            
            ShopItem shopItem = shop.getShopItem(itemId);
            if (shopItem == null) return itemId;
            
            // Vérifier si l'item a un displayName personnalisé
            if (shopItem.getItem().hasItemMeta() && shopItem.getItem().getItemMeta() != null) {
                String displayName = shopItem.getItem().getItemMeta().getDisplayName();
                if (displayName != null && !displayName.isEmpty()) {
                    return ChatColor.stripColor(displayName).trim();
                }
            }
            
            // Formatage simple du nom de matériau
            String materialName = shopItem.getItem().getType().name();
            String[] words = materialName.toLowerCase().split("_");
            StringBuilder formatted = new StringBuilder();
            for (String word : words) {
                if (!word.isEmpty()) {
                    formatted.append(Character.toUpperCase(word.charAt(0)))
                            .append(word.substring(1))
                            .append(" ");
                }
            }
            return formatted.toString().trim();
            
        } catch (Exception e) {
            plugin.getLogger().fine("Erreur lors de la récupération du nom d'item: " + e.getMessage());
            return itemId;
        }
    }
    
    private String getShopDisplayName(String shopId) {
        try {
            // return plugin.getShopConfigManager().getShopName(shopId);
            return plugin.getShopConfigManager().getShops().stream()
                .filter(s -> s.equalsIgnoreCase(shopId))
                .findFirst()
                .orElse(shopId);
        } catch (Exception e) {
            return shopId;
        }
    }
    
    public boolean isEnabled() {
        return enabled && Bukkit.getPluginManager().isPluginEnabled("DiscordSRV");
    }
}