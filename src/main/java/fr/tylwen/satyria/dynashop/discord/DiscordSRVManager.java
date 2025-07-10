package fr.tylwen.satyria.dynashop.discord;

import fr.tylwen.satyria.dynashop.DynaShopPlugin;
import fr.tylwen.satyria.dynashop.price.DynamicPrice;
import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.api.ListenerPriority;
import github.scarsz.discordsrv.api.Subscribe;
import github.scarsz.discordsrv.api.events.DiscordGuildMessageReceivedEvent;
import github.scarsz.discordsrv.dependencies.jda.api.EmbedBuilder;
import github.scarsz.discordsrv.dependencies.jda.api.entities.Guild;
import github.scarsz.discordsrv.dependencies.jda.api.entities.TextChannel;
import github.scarsz.discordsrv.util.DiscordUtil;
import net.brcdev.shopgui.ShopGuiPlusApi;
import net.brcdev.shopgui.shop.Shop;
import net.brcdev.shopgui.shop.item.ShopItem;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

import java.awt.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.List;
import github.scarsz.discordsrv.dependencies.jda.api.Permission;

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
        this.lowStockThreshold = plugin.getConfigMain().getInt("discord.notifications.low-stock-threshold", 10);
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
                    event.getChannel().sendMessage(plugin.getLangConfig().getDiscordMessage("discord.commands.errors.usage_prices", "❌ Usage: `!dynashop prix <shop> <item>`")).queue();
                    return;
                }
                handlePricesCommand(event, args[2], args[3]);
            }
            case "stock" -> {
                if (args.length < 4) {
                    event.getChannel().sendMessage(plugin.getLangConfig().getDiscordMessage("discord.commands.errors.usage_stock", "❌ Usage: `!dynashop stock <shop> <item>`")).queue();
                    return;
                }
                handleStockCommand(event, args[2], args[3]);
            }
            case "shops", "boutiques" -> handleShopsCommand(event);
            case "lowstock", "stockfaible" -> handleLowStockCommand(event);
            case "search", "chercher" -> {
                if (args.length < 3) {
                    event.getChannel().sendMessage(plugin.getLangConfig().getDiscordMessage("discord.commands.errors.usage_search", "❌ Usage: `!dynashop search <terme>`")).queue();
                    return;
                }
                handleSearchCommand(event, String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)));
            }
            case "compare", "comparer" -> {
                if (args.length < 3) {
                    event.getChannel().sendMessage(plugin.getLangConfig().getDiscordMessage("discord.commands.errors.usage_compare", "❌ Usage: `!dynashop compare <item>`")).queue();
                    return;
                }
                handleCompareCommand(event, String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)));
            }
            case "top" -> {
                if (args.length < 3) {
                    event.getChannel().sendMessage(plugin.getLangConfig().getDiscordMessage("discord.commands.errors.usage_top", "❌ Usage: `!dynashop top <expensive|cheap>`")).queue();
                    return;
                }
                handleTopCommand(event, args[2]);
            }
            case "stats", "statistiques" -> {
                String target = args.length >= 3 ? args[2] : "global";
                handleStatsCommand(event, target);
            }
            case "reload" -> {
                handleReloadCommand(event);
            }
            case "info" -> {
                if (args.length < 3) {
                    event.getChannel().sendMessage(plugin.getLangConfig().getDiscordMessage("discord.commands.errors.usage_info", "❌ Usage: `!dynashop info <shop>`")).queue();
                    return;
                }
                handleInfoCommand(event, args[2]);
            }
            case "admin" -> {
                if (args.length < 3) {
                    event.getChannel().sendMessage(plugin.getLangConfig().getDiscordMessage("discord.commands.errors.usage_admin", "❌ Usage: `!dynashop admin <restock|clear|reset>`")).queue();
                    return;
                }
                handleAdminCommand(event, args[2], args.length > 3 ? args[3] : null, args.length > 4 ? args[4] : null);
            }
            case "trend", "tendance" -> {
                if (args.length < 4) {
                    event.getChannel().sendMessage(plugin.getLangConfig().getDiscordMessage("discord.commands.errors.usage_trend", "❌ Usage: `!dynashop trend <shop> <item>`")).queue();
                    return;
                }
                handleTrendCommand(event, args[2], args[3]);
            }
            case "help", "aide" -> sendHelpMessage(event.getChannel());
            default -> {
                // Amélioration des messages d'erreur avec suggestions
                String suggestion = findSimilarCommand(subcommand);
                String errorMessage = plugin.getLangConfig().getDiscordMessage("discord.commands.errors.unknown_command", "❌ Commande inconnue. Utilisez `!dynashop help`");
                if (suggestion != null) {
                    errorMessage += "\n" + plugin.getLangConfig().getDiscordMessage("discord.commands.errors.did_you_mean", "💡 Peut-être vouliez-vous dire: `!dynashop %suggestion%`", new String[]{"%suggestion%", suggestion});
                }
                event.getChannel().sendMessage(errorMessage).queue();
            }
        }
    }
    
    private void sendHelpMessage(TextChannel channel) {
        EmbedBuilder embed = new EmbedBuilder()
            .setTitle(plugin.getLangConfig().getDiscordMessage("discord.commands.help.title", "🏪 DynaShop - Commandes Discord"))
            .setDescription(plugin.getLangConfig().getDiscordMessage("discord.commands.help.description", "Voici les commandes disponibles :"))
            .addField(plugin.getLangConfig().getDiscordMessage("discord.commands.help.field_prices", "📊 Prix"), 
                     plugin.getLangConfig().getDiscordMessage("discord.commands.help.field_prices_desc", "`!dynashop prix <shop> <item>` - Voir les prix d'un item"), false)
            .addField(plugin.getLangConfig().getDiscordMessage("discord.commands.help.field_stock", "📦 Stock"), 
                     plugin.getLangConfig().getDiscordMessage("discord.commands.help.field_stock_desc", "`!dynashop stock <shop> <item>` - Voir le stock d'un item"), false)
            .addField(plugin.getLangConfig().getDiscordMessage("discord.commands.help.field_shops", "🏪 Shops"), 
                     plugin.getLangConfig().getDiscordMessage("discord.commands.help.field_shops_desc", "`!dynashop shops` - Lister tous les shops"), false)
            .addField(plugin.getLangConfig().getDiscordMessage("discord.commands.help.field_lowstock", "⚠️ Stock faible"), 
                     plugin.getLangConfig().getDiscordMessage("discord.commands.help.field_lowstock_desc", "`!dynashop lowstock` - Voir les items avec stock faible"), false)
            .addField(plugin.getLangConfig().getDiscordMessage("discord.commands.help.field_search", "🔍 Recherche"), 
                     plugin.getLangConfig().getDiscordMessage("discord.commands.help.field_search_desc", "`!dynashop search <terme>` - Rechercher des items"), false)
            .addField(plugin.getLangConfig().getDiscordMessage("discord.commands.help.field_compare", "⚖️ Comparaison"), 
                     plugin.getLangConfig().getDiscordMessage("discord.commands.help.field_compare_desc", "`!dynashop compare <item>` - Comparer les prix"), false)
            .addField(plugin.getLangConfig().getDiscordMessage("discord.commands.help.field_top", "🏆 Top"), 
                     plugin.getLangConfig().getDiscordMessage("discord.commands.help.field_top_desc", "`!dynashop top <expensive|cheap>` - Items les + chers/moins chers"), false)
            .addField(plugin.getLangConfig().getDiscordMessage("discord.commands.help.field_stats", "📈 Statistiques"), 
                     plugin.getLangConfig().getDiscordMessage("discord.commands.help.field_stats_desc", "`!dynashop stats [shop]` - Voir les statistiques"), false)
            .addField(plugin.getLangConfig().getDiscordMessage("discord.commands.help.field_info", "ℹ️ Informations"), 
                     plugin.getLangConfig().getDiscordMessage("discord.commands.help.field_info_desc", "`!dynashop info <shop>` - Infos détaillées d'un shop"), false)
            .addField(plugin.getLangConfig().getDiscordMessage("discord.commands.help.field_trend", "📊 Tendances"), 
                     plugin.getLangConfig().getDiscordMessage("discord.commands.help.field_trend_desc", "`!dynashop trend <shop> <item>` - Voir les tendances de prix"), false)
            .addField(plugin.getLangConfig().getDiscordMessage("discord.commands.help.field_admin", "🔧 Admin"), 
                     plugin.getLangConfig().getDiscordMessage("discord.commands.help.field_admin_desc", "`!dynashop admin <action>` - Commandes d'administration"), false)
            .addField(plugin.getLangConfig().getDiscordMessage("discord.commands.help.field_reload", "🔄 Reload"), 
                     plugin.getLangConfig().getDiscordMessage("discord.commands.help.field_reload_desc", "`!dynashop reload` - Recharger la configuration"), false)
            .setColor(Color.BLUE)
            .setTimestamp(LocalDateTime.now())
            .setFooter(plugin.getLangConfig().getDiscordMessage("discord.commands.help.footer", "DynaShop"), null);
        
        channel.sendMessageEmbeds(embed.build()).queue();
    }
    
    private void handlePricesCommand(DiscordGuildMessageReceivedEvent event, String shopId, String itemId) {
        // Exécuter de manière asynchrone pour éviter de bloquer Discord
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            // Rechercher les IDs exacts
            String actualShopId = findShopId(shopId);
            String actualItemId = findItemId(actualShopId, itemId);
            
            if (actualShopId == null) {
                event.getChannel().sendMessage(plugin.getLangConfig().getDiscordMessage("discord.commands.errors.shop_not_found", "❌ Shop non trouvé: %shop%", new String[]{"%shop%", shopId})).queue();
                return;
            }
            
            if (actualItemId == null) {
                event.getChannel().sendMessage(plugin.getLangConfig().getDiscordMessage("discord.commands.errors.item_not_found", "❌ Item non trouvé: %item% dans le shop %shop%", new String[]{"%item%", itemId}, new String[]{"%shop%", shopId})).queue();
                return;
            }
            
            try {
                // Récupérer les prix
                // DynamicPrice price = plugin.getDynaShopListener().getOrLoadPrice(null, actualShopId, actualItemId, null);
                DynamicPrice price = plugin.getDynaShopListener().getOrLoadPrice(null, actualShopId, actualItemId, null, new HashSet<>(), new HashMap<>());
                if (price == null) {
                    event.getChannel().sendMessage(plugin.getLangConfig().getDiscordMessage("discord.commands.errors.price_error", "❌ Impossible de récupérer les prix pour cet item.")).queue();
                    return;
                }
                
                String itemName = getItemDisplayName(actualShopId, actualItemId);
                String shopName = getShopDisplayName(actualShopId);
                
                EmbedBuilder embed = new EmbedBuilder()
                    .setTitle(plugin.getLangConfig().getDiscordMessage("discord.commands.prices.title", "💰 Prix de %item%", new String[]{"%item%", itemName}))
                    .setDescription(plugin.getLangConfig().getDiscordMessage("discord.commands.prices.description", "Shop: **%shop%**", new String[]{"%shop%", shopName}))
                    .setColor(Color.BLUE)
                    .setTimestamp(LocalDateTime.now());
                
                // Prix d'achat
                if (price.getBuyPrice() > 0) {
                    embed.addField(plugin.getLangConfig().getDiscordMessage("discord.commands.prices.field_buy", "💳 Prix d'achat"), 
                        plugin.getPriceFormatter().formatPrice(price.getBuyPrice()), true);
                }
                
                // Prix de vente
                if (price.getSellPrice() > 0) {
                    embed.addField(plugin.getLangConfig().getDiscordMessage("discord.commands.prices.field_sell", "💰 Prix de vente"), 
                        plugin.getPriceFormatter().formatPrice(price.getSellPrice()), true);
                }
                
                // Type de shop
                var type = price.getDynaShopType();
                embed.addField(plugin.getLangConfig().getDiscordMessage("discord.commands.prices.field_type", "🏪 Type"), type.toString(), true);
                
                event.getChannel().sendMessage(embed.build()).queue();
                
            } catch (Exception e) {
                plugin.getLogger().warning("Erreur lors de la récupération des prix: " + e.getMessage());
                event.getChannel().sendMessage(plugin.getLangConfig().getDiscordMessage("discord.commands.errors.price_error", "❌ Erreur lors de la récupération des prix.")).queue();
            }
        });
    }
    
    private void handleStockCommand(DiscordGuildMessageReceivedEvent event, String shopId, String itemId) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String actualShopId = findShopId(shopId);
            String actualItemId = findItemId(actualShopId, itemId);
            
            if (actualShopId == null || actualItemId == null) {
                event.getChannel().sendMessage(plugin.getLangConfig().getDiscordMessage("discord.commands.errors.shop_or_item_not_found", "❌ Shop ou item non trouvé.")).queue();
                return;
            }
            
            try {
                var type = plugin.getShopConfigManager().getTypeDynaShop(actualShopId, actualItemId);
                
                if (!type.name().contains("STOCK") && !type.name().contains("RECIPE")) {
                    event.getChannel().sendMessage(plugin.getLangConfig().getDiscordMessage("discord.commands.errors.no_stock_system", "❌ Cet item n'a pas de système de stock.")).queue();
                    return;
                }
                
                int stock = plugin.getStorageManager().getStock(actualShopId, actualItemId).orElse(0);
                int maxStock = plugin.getShopConfigManager()
                    .getItemValue(actualShopId, actualItemId, "stock.max", Integer.class)
                    .orElse(plugin.getDataConfig().getStockMax());
                
                String itemName = getItemDisplayName(actualShopId, actualItemId);
                String shopName = getShopDisplayName(actualShopId);
                
                double percentage = maxStock > 0 ? (stock * 100.0 / maxStock) : 0;
                Color embedColor;
                String titleKey;
                
                if (percentage < 25) {
                    embedColor = Color.RED;
                    titleKey = "discord.commands.stock.title_critical";
                } else if (percentage < 50) {
                    embedColor = Color.ORANGE;
                    titleKey = "discord.commands.stock.title_warning";
                } else {
                    embedColor = Color.GREEN;
                    titleKey = "discord.commands.stock.title_good";
                }
                
                EmbedBuilder embed = new EmbedBuilder()
                    .setTitle(plugin.getLangConfig().getDiscordMessage(titleKey, "✅ Stock de %item%", new String[]{"%item%", itemName}))
                    .setDescription(plugin.getLangConfig().getDiscordMessage("discord.commands.stock.description", "Shop: **%shop%**", new String[]{"%shop%", shopName}))
                    .addField(plugin.getLangConfig().getDiscordMessage("discord.commands.stock.field_current", "📦 Stock Actuel"), String.valueOf(stock), true)
                    .addField(plugin.getLangConfig().getDiscordMessage("discord.commands.stock.field_max", "📊 Stock Maximum"), String.valueOf(maxStock), true)
                    .addField(plugin.getLangConfig().getDiscordMessage("discord.commands.stock.field_percentage", "📈 Pourcentage"), String.format("%.1f%%", percentage), true)
                    .setColor(embedColor)
                    .setTimestamp(LocalDateTime.now());
                
                event.getChannel().sendMessage(embed.build()).queue();
                
            } catch (Exception e) {
                plugin.getLogger().warning("Erreur lors de la récupération du stock: " + e.getMessage());
                event.getChannel().sendMessage(plugin.getLangConfig().getDiscordMessage("discord.commands.errors.stock_error", "❌ Erreur lors de la récupération du stock.")).queue();
            }
        });
    }
    
    private void handleShopsCommand(DiscordGuildMessageReceivedEvent event) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Set<String> shopIds = plugin.getShopConfigManager().getShops();
                
                if (shopIds.isEmpty()) {
                    event.getChannel().sendMessage(plugin.getLangConfig().getDiscordMessage("discord.commands.errors.no_shops", "❌ Aucun shop trouvé.")).queue();
                    return;
                }
                
                StringBuilder description = new StringBuilder();
                int count = 0;
                
                for (String shopId : shopIds) {
                    if (count >= 20) { // Limiter à 20 shops
                        description.append("\n").append(plugin.getLangConfig().getDiscordMessage("discord.commands.shops.more_shops", "... et %count% autres shops", new String[]{"%count%", String.valueOf(shopIds.size() - count)}));
                        break;
                    }
                    
                    String shopName = getShopDisplayName(shopId);
                    description.append("🏪 **").append(shopName).append("** (`").append(shopId).append("`)\n");
                    count++;
                }
                
                EmbedBuilder embed = new EmbedBuilder()
                    .setTitle(plugin.getLangConfig().getDiscordMessage("discord.commands.shops.title", "🏪 Liste des Shops"))
                    .setDescription(description.toString())
                    .setColor(Color.CYAN)
                    .setTimestamp(LocalDateTime.now())
                    .setFooter(plugin.getLangConfig().getDiscordMessage("discord.commands.shops.footer", "Total: %total% shops", new String[]{"%total%", String.valueOf(shopIds.size())}));
                
                event.getChannel().sendMessage(embed.build()).queue();
                
            } catch (Exception e) {
                plugin.getLogger().warning("Erreur lors de la récupération des shops: " + e.getMessage());
                event.getChannel().sendMessage(plugin.getLangConfig().getDiscordMessage("discord.commands.errors.shops_error", "❌ Erreur lors de la récupération des shops.")).queue();
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
                                
                                lowStockItems.append(plugin.getLangConfig().getDiscordMessage("discord.commands.lowstock.item_format", "⚠️ **%item%** dans %shop%: %stock%",
                                    new String[]{"%item%", itemName}, new String[]{"%shop%", shopName}, new String[]{"%stock%", String.valueOf(stock)})).append("\n");
                                count++;
                                
                                if (count >= 15) break; // Limiter l'affichage
                            }
                        }
                    }
                    if (count >= 15) break;
                }
                
                if (lowStockItems.length() == 0) {
                    event.getChannel().sendMessage(plugin.getLangConfig().getDiscordMessage("discord.commands.lowstock.no_items", "✅ Aucun item avec un stock faible trouvé !")).queue();
                    return;
                }
                
                EmbedBuilder embed = new EmbedBuilder()
                    .setTitle(plugin.getLangConfig().getDiscordMessage("discord.commands.lowstock.title", "⚠️ Items avec Stock Faible"))
                    .setDescription(lowStockItems.toString())
                    .setColor(Color.ORANGE)
                    .setTimestamp(LocalDateTime.now())
                    .setFooter(plugin.getLangConfig().getDiscordMessage("discord.commands.lowstock.footer", "Seuil: %threshold%", new String[]{"%threshold%", String.valueOf(lowStockThreshold)}));
                
                event.getChannel().sendMessage(embed.build()).queue();
                
            } catch (Exception e) {
                plugin.getLogger().warning("Erreur lors de la récupération du stock faible: " + e.getMessage());
                event.getChannel().sendMessage(plugin.getLangConfig().getDiscordMessage("discord.commands.errors.lowstock_error", "❌ Erreur lors de la récupération du stock faible.")).queue();
            }
        });
    }
    
    // =================== NOUVELLES COMMANDES AVANCÉES ===================
    
    /**
     * Commande de recherche d'items
     */
    private void handleSearchCommand(DiscordGuildMessageReceivedEvent event, String searchTerm) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                List<SearchResult> results = new ArrayList<>();
                
                // Rechercher dans tous les shops
                for (String shopId : plugin.getShopConfigManager().getShops()) {
                    for (String itemId : plugin.getShopConfigManager().getShopItems(shopId)) {
                        String itemName = getItemDisplayName(shopId, itemId);
                        String shopName = getShopDisplayName(shopId);
                        
                        // Recherche dans le nom de l'item
                        if (itemName.toLowerCase().contains(searchTerm.toLowerCase()) || 
                            itemId.toLowerCase().contains(searchTerm.toLowerCase())) {
                            
                            // Récupérer les prix si possible
                            try {
                                var price = plugin.getDynaShopListener().getOrLoadPrice(null, shopId, itemId, null, new HashSet<>(), new HashMap<>());
                                double buyPrice = price != null ? price.getBuyPrice() : 0;
                                double sellPrice = price != null ? price.getSellPrice() : 0;
                                
                                results.add(new SearchResult(shopId, shopName, itemId, itemName, buyPrice, sellPrice));
                            } catch (Exception e) {
                                // Ignorer les erreurs de prix et continuer
                                results.add(new SearchResult(shopId, shopName, itemId, itemName, 0, 0));
                            }
                        }
                    }
                }
                
                if (results.isEmpty()) {
                    event.getChannel().sendMessage(plugin.getLangConfig().getDiscordMessage("discord.commands.search.no_results", "❌ Aucun résultat trouvé pour: **%term%**", new String[]{"%term%", searchTerm})).queue();
                    return;
                }
                
                // Limiter les résultats et construire la description
                StringBuilder description = new StringBuilder();
                int maxResults = Math.min(results.size(), 15);
                
                for (int i = 0; i < maxResults; i++) {
                    SearchResult result = results.get(i);
                    description.append(String.format("🏪 **%s** dans %s", result.itemName, result.shopName));
                    
                    if (result.buyPrice > 0 || result.sellPrice > 0) {
                        description.append(" - ");
                        if (result.buyPrice > 0) {
                            description.append(String.format("Achat: %s", plugin.getPriceFormatter().formatPrice(result.buyPrice)));
                        }
                        if (result.sellPrice > 0) {
                            if (result.buyPrice > 0) description.append(" | ");
                            description.append(String.format("Vente: %s", plugin.getPriceFormatter().formatPrice(result.sellPrice)));
                        }
                    }
                    description.append("\n");
                }
                
                if (results.size() > maxResults) {
                    description.append(plugin.getLangConfig().getDiscordMessage("discord.commands.search.more_results", "\n... et %count% autres résultats", new String[]{"%count%", String.valueOf(results.size() - maxResults)}));
                }
                
                EmbedBuilder embed = new EmbedBuilder()
                    .setTitle(plugin.getLangConfig().getDiscordMessage("discord.commands.search.title", "🔍 Résultats de recherche: %term%", new String[]{"%term%", searchTerm}))
                    .setDescription(description.toString())
                    .setColor(Color.CYAN)
                    .setTimestamp(LocalDateTime.now())
                    .setFooter(plugin.getLangConfig().getDiscordMessage("discord.commands.search.footer", "Trouvé: %count% résultats", new String[]{"%count%", String.valueOf(results.size())}));
                
                event.getChannel().sendMessageEmbeds(embed.build()).queue();
                
            } catch (Exception e) {
                plugin.getLogger().warning("Erreur lors de la recherche: " + e.getMessage());
                event.getChannel().sendMessage(plugin.getLangConfig().getDiscordMessage("discord.commands.errors.search_error", "❌ Erreur lors de la recherche.")).queue();
            }
        });
    }
    
    /**
     * Commande de comparaison de prix entre shops
     */
    private void handleCompareCommand(DiscordGuildMessageReceivedEvent event, String itemName) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                List<CompareResult> results = new ArrayList<>();
                
                // Rechercher l'item dans tous les shops
                for (String shopId : plugin.getShopConfigManager().getShops()) {
                    for (String itemId : plugin.getShopConfigManager().getShopItems(shopId)) {
                        String displayName = getItemDisplayName(shopId, itemId);
                        
                        if (displayName.toLowerCase().contains(itemName.toLowerCase()) || 
                            itemId.toLowerCase().contains(itemName.toLowerCase())) {
                            
                            try {
                                var price = plugin.getDynaShopListener().getOrLoadPrice(null, shopId, itemId, null, new HashSet<>(), new HashMap<>());
                                if (price != null && (price.getBuyPrice() > 0 || price.getSellPrice() > 0)) {
                                    String shopName = getShopDisplayName(shopId);
                                    results.add(new CompareResult(shopId, shopName, itemId, displayName, price.getBuyPrice(), price.getSellPrice()));
                                }
                            } catch (Exception e) {
                                // Ignorer les erreurs et continuer
                            }
                        }
                    }
                }
                
                if (results.isEmpty()) {
                    event.getChannel().sendMessage(plugin.getLangConfig().getDiscordMessage("discord.commands.compare.no_results", "❌ Aucun item trouvé pour: **%item%**", new String[]{"%item%", itemName})).queue();
                    return;
                }
                
                // Trier par prix d'achat (plus bas en premier)
                results.sort((a, b) -> {
                    if (a.buyPrice <= 0 && b.buyPrice <= 0) return 0;
                    if (a.buyPrice <= 0) return 1;
                    if (b.buyPrice <= 0) return -1;
                    return Double.compare(a.buyPrice, b.buyPrice);
                });
                
                StringBuilder description = new StringBuilder();
                String bestBuyShop = "";
                String bestSellShop = "";
                double bestBuyPrice = Double.MAX_VALUE;
                double bestSellPrice = 0;
                
                for (CompareResult result : results) {
                    description.append(String.format("🏪 **%s**\n", result.shopName));
                    
                    if (result.buyPrice > 0) {
                        description.append(String.format("  💳 Achat: %s", plugin.getPriceFormatter().formatPrice(result.buyPrice)));
                        if (result.buyPrice < bestBuyPrice) {
                            bestBuyPrice = result.buyPrice;
                            bestBuyShop = result.shopName;
                        }
                    }
                    
                    if (result.sellPrice > 0) {
                        if (result.buyPrice > 0) description.append("\n");
                        description.append(String.format("  💰 Vente: %s", plugin.getPriceFormatter().formatPrice(result.sellPrice)));
                        if (result.sellPrice > bestSellPrice) {
                            bestSellPrice = result.sellPrice;
                            bestSellShop = result.shopName;
                        }
                    }
                    description.append("\n\n");
                }
                
                EmbedBuilder embed = new EmbedBuilder()
                    .setTitle(plugin.getLangConfig().getDiscordMessage("discord.commands.compare.title", "⚖️ Comparaison: %item%", new String[]{"%item%", results.get(0).itemName}))
                    .setDescription(description.toString())
                    .setColor(Color.ORANGE);
                
                if (bestBuyPrice < Double.MAX_VALUE) {
                    embed.addField(plugin.getLangConfig().getDiscordMessage("discord.commands.compare.best_buy", "🏆 Meilleur prix d'achat"), 
                        String.format("%s - %s", bestBuyShop, plugin.getPriceFormatter().formatPrice(bestBuyPrice)), true);
                }
                
                if (bestSellPrice > 0) {
                    embed.addField(plugin.getLangConfig().getDiscordMessage("discord.commands.compare.best_sell", "🏆 Meilleur prix de vente"), 
                        String.format("%s - %s", bestSellShop, plugin.getPriceFormatter().formatPrice(bestSellPrice)), true);
                }
                
                embed.setTimestamp(LocalDateTime.now())
                     .setFooter(plugin.getLangConfig().getDiscordMessage("discord.commands.compare.footer", "Shops comparés: %count%", new String[]{"%count%", String.valueOf(results.size())}));
                
                event.getChannel().sendMessageEmbeds(embed.build()).queue();
                
            } catch (Exception e) {
                plugin.getLogger().warning("Erreur lors de la comparaison: " + e.getMessage());
                event.getChannel().sendMessage(plugin.getLangConfig().getDiscordMessage("discord.commands.errors.compare_error", "❌ Erreur lors de la comparaison.")).queue();
            }
        });
    }
    
    /**
     * Commande pour les items les plus chers/moins chers
     */
    private void handleTopCommand(DiscordGuildMessageReceivedEvent event, String type) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                List<TopResult> results = new ArrayList<>();
                boolean expensive = type.equalsIgnoreCase("expensive") || type.equalsIgnoreCase("cher");
                
                // Collecter tous les items avec leurs prix
                for (String shopId : plugin.getShopConfigManager().getShops()) {
                    for (String itemId : plugin.getShopConfigManager().getShopItems(shopId)) {
                        try {
                            var price = plugin.getDynaShopListener().getOrLoadPrice(null, shopId, itemId, null, new HashSet<>(), new HashMap<>());
                            if (price != null && price.getBuyPrice() > 0) {
                                String itemName = getItemDisplayName(shopId, itemId);
                                String shopName = getShopDisplayName(shopId);
                                results.add(new TopResult(shopId, shopName, itemId, itemName, price.getBuyPrice()));
                            }
                        } catch (Exception e) {
                            // Ignorer les erreurs et continuer
                        }
                    }
                }
                
                if (results.isEmpty()) {
                    event.getChannel().sendMessage(plugin.getLangConfig().getDiscordMessage("discord.commands.top.no_results", "❌ Aucun item avec prix trouvé.")).queue();
                    return;
                }
                
                // Trier selon le type demandé
                if (expensive) {
                    results.sort((a, b) -> Double.compare(b.price, a.price)); // Plus cher en premier
                } else {
                    results.sort((a, b) -> Double.compare(a.price, b.price)); // Moins cher en premier
                }
                
                StringBuilder description = new StringBuilder();
                int maxResults = Math.min(results.size(), 10);
                
                for (int i = 0; i < maxResults; i++) {
                    TopResult result = results.get(i);
                    String medal = switch (i) {
                        case 0 -> "🥇";
                        case 1 -> "🥈";
                        case 2 -> "🥉";
                        default -> "🏅";
                    };
                    description.append(String.format("%s **%s** - %s\n   🏪 %s\n\n", 
                        medal, result.itemName, plugin.getPriceFormatter().formatPrice(result.price), result.shopName));
                }
                
                String titleKey = expensive ? "discord.commands.top.title_expensive" : "discord.commands.top.title_cheap";
                String defaultTitle = expensive ? "🏆 Items les plus chers" : "🏆 Items les moins chers";
                
                EmbedBuilder embed = new EmbedBuilder()
                    .setTitle(plugin.getLangConfig().getDiscordMessage(titleKey, defaultTitle))
                    .setDescription(description.toString())
                    .setColor(expensive ? Color.RED : Color.GREEN)
                    .setTimestamp(LocalDateTime.now())
                    .setFooter(plugin.getLangConfig().getDiscordMessage("discord.commands.top.footer", "Top %count% sur %total% items", new String[]{"%count%", String.valueOf(maxResults)}, new String[]{"%total%", String.valueOf(results.size())}));
                
                event.getChannel().sendMessageEmbeds(embed.build()).queue();
                
            } catch (Exception e) {
                plugin.getLogger().warning("Erreur lors du classement: " + e.getMessage());
                event.getChannel().sendMessage(plugin.getLangConfig().getDiscordMessage("discord.commands.errors.top_error", "❌ Erreur lors du classement.")).queue();
            }
        });
    }
    
    /**
     * Commande de statistiques
     */
    private void handleStatsCommand(DiscordGuildMessageReceivedEvent event, String target) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (target.equalsIgnoreCase("global")) {
                    handleGlobalStats(event);
                } else {
                    handleShopStats(event, target);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Erreur lors des statistiques: " + e.getMessage());
                event.getChannel().sendMessage(plugin.getLangConfig().getDiscordMessage("discord.commands.errors.stats_error", "❌ Erreur lors de la récupération des statistiques.")).queue();
            }
        });
    }
    
    private void handleGlobalStats(DiscordGuildMessageReceivedEvent event) {
        int totalShops = plugin.getShopConfigManager().getShops().size();
        int totalItems = 0;
        int stockItems = 0;
        int dynamicItems = 0;
        double totalStockValue = 0;
        int lowStockItems = 0;
        
        for (String shopId : plugin.getShopConfigManager().getShops()) {
            Set<String> items = plugin.getShopConfigManager().getShopItems(shopId);
            totalItems += items.size();
            
            for (String itemId : items) {
                var type = plugin.getShopConfigManager().getTypeDynaShop(shopId, itemId);
                
                if (type.name().contains("STOCK")) {
                    stockItems++;
                    int stock = plugin.getStorageManager().getStock(shopId, itemId).orElse(0);
                    if (stock <= lowStockThreshold) {
                        lowStockItems++;
                    }
                    
                    try {
                        var price = plugin.getDynaShopListener().getOrLoadPrice(null, shopId, itemId, null, new HashSet<>(), new HashMap<>());
                        if (price != null && price.getBuyPrice() > 0) {
                            totalStockValue += stock * price.getBuyPrice();
                        }
                    } catch (Exception e) {
                        // Ignorer les erreurs
                    }
                } else if (type.name().contains("DYNAMIC")) {
                    dynamicItems++;
                }
            }
        }
        
        EmbedBuilder embed = new EmbedBuilder()
            .setTitle(plugin.getLangConfig().getDiscordMessage("discord.commands.stats.global_title", "📈 Statistiques Globales"))
            .setColor(Color.BLUE)
            .addField(plugin.getLangConfig().getDiscordMessage("discord.commands.stats.total_shops", "🏪 Total Shops"), String.valueOf(totalShops), true)
            .addField(plugin.getLangConfig().getDiscordMessage("discord.commands.stats.total_items", "📦 Total Items"), String.valueOf(totalItems), true)
            .addField(plugin.getLangConfig().getDiscordMessage("discord.commands.stats.stock_items", "📊 Items avec Stock"), String.valueOf(stockItems), true)
            .addField(plugin.getLangConfig().getDiscordMessage("discord.commands.stats.dynamic_items", "⚡ Items Dynamiques"), String.valueOf(dynamicItems), true)
            .addField(plugin.getLangConfig().getDiscordMessage("discord.commands.stats.low_stock_items", "⚠️ Stock Faible"), String.valueOf(lowStockItems), true)
            .addField(plugin.getLangConfig().getDiscordMessage("discord.commands.stats.total_value", "💰 Valeur Stock Total"), plugin.getPriceFormatter().formatPrice(totalStockValue), true)
            .setTimestamp(LocalDateTime.now())
            .setFooter(plugin.getLangConfig().getDiscordMessage("discord.commands.stats.footer", "DynaShop Stats"));
        
        event.getChannel().sendMessageEmbeds(embed.build()).queue();
    }
    
    private void handleShopStats(DiscordGuildMessageReceivedEvent event, String shopId) {
        String actualShopId = findShopId(shopId);
        
        if (actualShopId == null) {
            event.getChannel().sendMessage(plugin.getLangConfig().getDiscordMessage("discord.commands.errors.shop_not_found", "❌ Shop non trouvé: %shop%", new String[]{"%shop%", shopId})).queue();
            return;
        }
        
        String shopName = getShopDisplayName(actualShopId);
        Set<String> items = plugin.getShopConfigManager().getShopItems(actualShopId);
        int totalItems = items.size();
        int stockItems = 0;
        int dynamicItems = 0;
        int lowStockItems = 0;
        double avgPrice = 0;
        int priceCount = 0;
        
        for (String itemId : items) {
            var type = plugin.getShopConfigManager().getTypeDynaShop(actualShopId, itemId);
            
            if (type.name().contains("STOCK")) {
                stockItems++;
                int stock = plugin.getStorageManager().getStock(actualShopId, itemId).orElse(0);
                if (stock <= lowStockThreshold) {
                    lowStockItems++;
                }
            } else if (type.name().contains("DYNAMIC")) {
                dynamicItems++;
            }
            
            try {
                var price = plugin.getDynaShopListener().getOrLoadPrice(null, actualShopId, itemId, null, new HashSet<>(), new HashMap<>());
                if (price != null && price.getBuyPrice() > 0) {
                    avgPrice += price.getBuyPrice();
                    priceCount++;
                }
            } catch (Exception e) {
                // Ignorer les erreurs
            }
        }
        
        if (priceCount > 0) {
            avgPrice /= priceCount;
        }
        
        EmbedBuilder embed = new EmbedBuilder()
            .setTitle(plugin.getLangConfig().getDiscordMessage("discord.commands.stats.shop_title", "📈 Statistiques: %shop%", new String[]{"%shop%", shopName}))
            .setColor(Color.GREEN)
            .addField(plugin.getLangConfig().getDiscordMessage("discord.commands.stats.total_items", "📦 Total Items"), String.valueOf(totalItems), true)
            .addField(plugin.getLangConfig().getDiscordMessage("discord.commands.stats.stock_items", "📊 Items avec Stock"), String.valueOf(stockItems), true)
            .addField(plugin.getLangConfig().getDiscordMessage("discord.commands.stats.dynamic_items", "⚡ Items Dynamiques"), String.valueOf(dynamicItems), true)
            .addField(plugin.getLangConfig().getDiscordMessage("discord.commands.stats.low_stock_items", "⚠️ Stock Faible"), String.valueOf(lowStockItems), true)
            .addField(plugin.getLangConfig().getDiscordMessage("discord.commands.stats.avg_price", "💰 Prix Moyen"), plugin.getPriceFormatter().formatPrice(avgPrice), true)
            .setTimestamp(LocalDateTime.now())
            .setFooter(plugin.getLangConfig().getDiscordMessage("discord.commands.stats.footer", "DynaShop Stats"));
        
        event.getChannel().sendMessageEmbeds(embed.build()).queue();
    }
    
    // =================== CLASSES UTILITAIRES ===================
    
    private static class SearchResult {
        String shopId, shopName, itemId, itemName;
        double buyPrice, sellPrice;
        
        SearchResult(String shopId, String shopName, String itemId, String itemName, double buyPrice, double sellPrice) {
            this.shopId = shopId;
            this.shopName = shopName;
            this.itemId = itemId;
            this.itemName = itemName;
            this.buyPrice = buyPrice;
            this.sellPrice = sellPrice;
        }
    }
    
    private static class CompareResult {
        String shopId, shopName, itemId, itemName;
        double buyPrice, sellPrice;
        
        CompareResult(String shopId, String shopName, String itemId, String itemName, double buyPrice, double sellPrice) {
            this.shopId = shopId;
            this.shopName = shopName;
            this.itemId = itemId;
            this.itemName = itemName;
            this.buyPrice = buyPrice;
            this.sellPrice = sellPrice;
        }
    }
    
    private static class TopResult {
        String shopId, shopName, itemId, itemName;
        double price;
        
        TopResult(String shopId, String shopName, String itemId, String itemName, double price) {
            this.shopId = shopId;
            this.shopName = shopName;
            this.itemId = itemId;
            this.itemName = itemName;
            this.price = price;
        }
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
                    .setTitle(plugin.getLangConfig().getDiscordMessage("discord.announcements.restock.title", "🔄 Shop Restocké !"))
                    .setDescription(plugin.getLangConfig().getDiscordMessage("discord.announcements.restock.description", "**%item%** a été restocké dans **%shop%**", new String[]{"%item%", itemName}, new String[]{"%shop%", shopName}))
                    .addField(plugin.getLangConfig().getDiscordMessage("discord.announcements.restock.field_new_stock", "Nouveau Stock"), String.format("%d/%d", newStock, maxStock), true)
                    .addField(plugin.getLangConfig().getDiscordMessage("discord.announcements.restock.field_percentage", "Pourcentage"), String.format("%.1f%%", (newStock * 100.0 / maxStock)), true)
                    .setColor(Color.GREEN)
                    .setTimestamp(LocalDateTime.now())
                    .setFooter(plugin.getLangConfig().getDiscordMessage("discord.announcements.restock.footer", "DynaShop"), null);
                
                channel.sendMessageEmbeds(embed.build()).queue();
                
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
        // if (currentStock > lowStockThreshold) return;
        if (currentStock > lowStockThreshold * maxStock / 100) return; // Utiliser le seuil configuré
        
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                TextChannel channel = getTextChannel(announcementChannelId);
                if (channel == null) return;
                
                String itemName = getItemDisplayName(shopId, itemId);
                String shopName = getShopDisplayName(shopId);
                
                double percentage = (currentStock * 100.0 / maxStock);
                Color embedColor = percentage < 5 ? Color.RED : Color.ORANGE;
                String titleKey = percentage < 5 ? "discord.announcements.lowstock.title_critical" : "discord.announcements.lowstock.title_warning";
                
                EmbedBuilder embed = new EmbedBuilder()
                    .setTitle(plugin.getLangConfig().getDiscordMessage(titleKey, "⚠️ Stock Faible !"))
                    .setDescription(plugin.getLangConfig().getDiscordMessage("discord.announcements.lowstock.description", "**%item%** dans **%shop%** a un stock faible", new String[]{"%item%", itemName}, new String[]{"%shop%", shopName}))
                    .addField(plugin.getLangConfig().getDiscordMessage("discord.announcements.lowstock.field_current", "Stock Actuel"), String.format("%d/%d", currentStock, maxStock), true)
                    .addField(plugin.getLangConfig().getDiscordMessage("discord.announcements.lowstock.field_percentage", "Pourcentage"), String.format("%.1f%%", percentage), true)
                    .setColor(embedColor)
                    .setTimestamp(LocalDateTime.now())
                    .setFooter(plugin.getLangConfig().getDiscordMessage("discord.announcements.lowstock.footer", "DynaShop"), null);
                
                channel.sendMessageEmbeds(embed.build()).queue();
                
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
        // if (changePercent < 5.0) return;
        if (changePercent < plugin.getConfigMain().getDouble("discord.notifications.price-change-threshold", 5.0)) return;
        
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                TextChannel channel = getTextChannel(announcementChannelId);
                if (channel == null) return;
                
                String itemName = getItemDisplayName(shopId, itemId);
                String shopName = getShopDisplayName(shopId);
                String priceType = isBuy ? plugin.getLangConfig().getDiscordMessage("discord.announcements.price_change.type_buy", "achat") : plugin.getLangConfig().getDiscordMessage("discord.announcements.price_change.type_sell", "vente");
                
                boolean isIncrease = newPrice > oldPrice;
                String titleKey = isIncrease ? "discord.announcements.price_change.title_increase" : "discord.announcements.price_change.title_decrease";
                String descriptionKey = isIncrease ? "discord.announcements.price_change.description_increase" : "discord.announcements.price_change.description_decrease";
                Color embedColor = isIncrease ? Color.GREEN : Color.RED;
                
                EmbedBuilder embed = new EmbedBuilder()
                    .setTitle(plugin.getLangConfig().getDiscordMessage(titleKey, "📈 Changement de Prix !"))
                    .setDescription(plugin.getLangConfig().getDiscordMessage(descriptionKey, "Le prix de **%type%** de **%item%** dans **%shop%** a augmenté", 
                        new String[]{"%type%", priceType}, new String[]{"%item%", itemName}, new String[]{"%shop%", shopName}))
                    .addField(plugin.getLangConfig().getDiscordMessage("discord.announcements.price_change.field_old_price", "Ancien Prix"), plugin.getPriceFormatter().formatPrice(oldPrice), true)
                    .addField(plugin.getLangConfig().getDiscordMessage("discord.announcements.price_change.field_new_price", "Nouveau Prix"), plugin.getPriceFormatter().formatPrice(newPrice), true)
                    .addField(plugin.getLangConfig().getDiscordMessage("discord.announcements.price_change.field_change", "Changement"), String.format("%.1f%%", changePercent), true)
                    .setColor(embedColor)
                    .setTimestamp(LocalDateTime.now())
                    .setFooter(plugin.getLangConfig().getDiscordMessage("discord.announcements.price_change.footer", "DynaShop"), null);
                
                channel.sendMessageEmbeds(embed.build()).queue();
                
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to send price change announcement: " + e.getMessage());
            }
        });
    }
    
    // =================== NOUVELLES COMMANDES AVANCÉES (MÉTHODES MANQUANTES) ===================
    
    /**
     * Commande pour recharger la configuration
     */
    private void handleReloadCommand(DiscordGuildMessageReceivedEvent event) {
        // Vérifier les permissions (basique pour Discord)
        if (!hasAdminPermission(event)) {
            event.getChannel().sendMessage(plugin.getLangConfig().getDiscordMessage("discord.commands.errors.no_permission", "❌ Vous n'avez pas la permission d'utiliser cette commande.")).queue();
            return;
        }
        
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                plugin.reloadConfig();
                loadConfig();
                
                EmbedBuilder embed = new EmbedBuilder()
                    .setTitle(plugin.getLangConfig().getDiscordMessage("discord.commands.reload.title", "🔄 Configuration Rechargée"))
                    .setDescription(plugin.getLangConfig().getDiscordMessage("discord.commands.reload.description", "La configuration de DynaShop a été rechargée avec succès !"))
                    .setColor(Color.GREEN)
                    .setTimestamp(LocalDateTime.now());
                
                event.getChannel().sendMessageEmbeds(embed.build()).queue();
                
            } catch (Exception e) {
                plugin.getLogger().warning("Erreur lors du rechargement: " + e.getMessage());
                event.getChannel().sendMessage(plugin.getLangConfig().getDiscordMessage("discord.commands.errors.reload_error", "❌ Erreur lors du rechargement de la configuration.")).queue();
            }
        });
    }
    
    /**
     * Commande pour afficher les informations détaillées d'un shop
     */
    private void handleInfoCommand(DiscordGuildMessageReceivedEvent event, String shopId) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String actualShopId = findShopId(shopId);
                
                if (actualShopId == null) {
                    event.getChannel().sendMessage(plugin.getLangConfig().getDiscordMessage("discord.commands.errors.shop_not_found", "❌ Shop non trouvé: %shop%", new String[]{"%shop%", shopId})).queue();
                    return;
                }
                
                String shopName = getShopDisplayName(actualShopId);
                Set<String> items = plugin.getShopConfigManager().getShopItems(actualShopId);
                
                int totalItems = items.size();
                int stockItems = 0;
                int dynamicItems = 0;
                int lowStockItems = 0;
                double totalValue = 0;
                
                StringBuilder popularItems = new StringBuilder();
                List<String> topItems = new ArrayList<>();
                
                for (String itemId : items) {
                    var type = plugin.getShopConfigManager().getTypeDynaShop(actualShopId, itemId);
                    
                    if (type.name().contains("STOCK")) {
                        stockItems++;
                        int stock = plugin.getStorageManager().getStock(actualShopId, itemId).orElse(0);
                        if (stock <= lowStockThreshold) {
                            lowStockItems++;
                        }
                        
                        try {
                            var price = plugin.getDynaShopListener().getOrLoadPrice(null, actualShopId, itemId, null, new HashSet<>(), new HashMap<>());
                            if (price != null && price.getBuyPrice() > 0) {
                                totalValue += stock * price.getBuyPrice();
                                if (topItems.size() < 5) {
                                    topItems.add(String.format("• %s (%s)", getItemDisplayName(actualShopId, itemId), plugin.getPriceFormatter().formatPrice(price.getBuyPrice())));
                                }
                            }
                        } catch (Exception e) {
                            // Ignorer les erreurs
                        }
                    } else if (type.name().contains("DYNAMIC")) {
                        dynamicItems++;
                    }
                }
                
                if (!topItems.isEmpty()) {
                    popularItems.append(String.join("\n", topItems));
                } else {
                    popularItems.append(plugin.getLangConfig().getDiscordMessage("discord.commands.info.no_items", "Aucun item disponible"));
                }
                
                EmbedBuilder embed = new EmbedBuilder()
                    .setTitle(plugin.getLangConfig().getDiscordMessage("discord.commands.info.title", "🏪 Informations: %shop%", new String[]{"%shop%", shopName}))
                    .setDescription(plugin.getLangConfig().getDiscordMessage("discord.commands.info.description", "Détails complets du shop **%shop%**", new String[]{"%shop%", shopName}))
                    .addField(plugin.getLangConfig().getDiscordMessage("discord.commands.info.field_total_items", "📦 Total Items"), String.valueOf(totalItems), true)
                    .addField(plugin.getLangConfig().getDiscordMessage("discord.commands.info.field_stock_items", "📊 Items avec Stock"), String.valueOf(stockItems), true)
                    .addField(plugin.getLangConfig().getDiscordMessage("discord.commands.info.field_dynamic_items", "⚡ Items Dynamiques"), String.valueOf(dynamicItems), true)
                    .addField(plugin.getLangConfig().getDiscordMessage("discord.commands.info.field_low_stock", "⚠️ Stock Faible"), String.valueOf(lowStockItems), true)
                    .addField(plugin.getLangConfig().getDiscordMessage("discord.commands.info.field_total_value", "💰 Valeur Totale"), plugin.getPriceFormatter().formatPrice(totalValue), true)
                    .addField(plugin.getLangConfig().getDiscordMessage("discord.commands.info.field_popular_items", "🔥 Items Populaires"), popularItems.toString(), false)
                    .setColor(Color.BLUE)
                    .setTimestamp(LocalDateTime.now())
                    .setFooter(plugin.getLangConfig().getDiscordMessage("discord.commands.info.footer", "Shop ID: %id%", new String[]{"%id%", actualShopId}));
                
                event.getChannel().sendMessageEmbeds(embed.build()).queue();
                
            } catch (Exception e) {
                plugin.getLogger().warning("Erreur lors de la récupération des informations: " + e.getMessage());
                event.getChannel().sendMessage(plugin.getLangConfig().getDiscordMessage("discord.commands.errors.info_error", "❌ Erreur lors de la récupération des informations.")).queue();
            }
        });
    }
    
    /**
     * Commandes d'administration
     */
    private void handleAdminCommand(DiscordGuildMessageReceivedEvent event, String action, String shopId, String itemId) {
        if (!hasAdminPermission(event)) {
            event.getChannel().sendMessage(plugin.getLangConfig().getDiscordMessage("discord.commands.errors.no_permission", "❌ Vous n'avez pas la permission d'utiliser cette commande.")).queue();
            return;
        }
        
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                switch (action.toLowerCase()) {
                    case "restock" -> {
                        if (shopId == null || itemId == null) {
                            event.getChannel().sendMessage(plugin.getLangConfig().getDiscordMessage("discord.commands.errors.usage_admin_restock", "❌ Usage: `!dynashop admin restock <shop> <item>`")).queue();
                            return;
                        }
                        handleAdminRestock(event, shopId, itemId);
                    }
                    case "clear" -> {
                        if (shopId == null) {
                            event.getChannel().sendMessage(plugin.getLangConfig().getDiscordMessage("discord.commands.errors.usage_admin_clear", "❌ Usage: `!dynashop admin clear <shop>`")).queue();
                            return;
                        }
                        handleAdminClear(event, shopId);
                    }
                    case "reset" -> {
                        handleAdminReset(event);
                    }
                    default -> {
                        event.getChannel().sendMessage(plugin.getLangConfig().getDiscordMessage("discord.commands.errors.usage_admin", "❌ Usage: `!dynashop admin <restock|clear|reset>`")).queue();
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Erreur lors de l'action admin: " + e.getMessage());
                event.getChannel().sendMessage(plugin.getLangConfig().getDiscordMessage("discord.commands.errors.admin_error", "❌ Erreur lors de l'exécution de la commande admin.")).queue();
            }
        });
    }
    
    /**
     * Commande pour afficher les tendances de prix
     */
    private void handleTrendCommand(DiscordGuildMessageReceivedEvent event, String shopId, String itemId) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String actualShopId = findShopId(shopId);
                String actualItemId = findItemId(actualShopId, itemId);
                
                if (actualShopId == null || actualItemId == null) {
                    event.getChannel().sendMessage(plugin.getLangConfig().getDiscordMessage("discord.commands.errors.shop_or_item_not_found", "❌ Shop ou item non trouvé.")).queue();
                    return;
                }
                
                String itemName = getItemDisplayName(actualShopId, actualItemId);
                String shopName = getShopDisplayName(actualShopId);
                
                // Récupérer les données historiques (simulation car pas d'historique réel)
                var price = plugin.getDynaShopListener().getOrLoadPrice(null, actualShopId, actualItemId, null, new HashSet<>(), new HashMap<>());
                if (price == null) {
                    event.getChannel().sendMessage(plugin.getLangConfig().getDiscordMessage("discord.commands.errors.price_error", "❌ Impossible de récupérer les prix pour cet item.")).queue();
                    return;
                }
                
                // Simuler des données de tendance
                String trend = "→"; // Stable par défaut
                Color trendColor = Color.GRAY;
                String trendText = plugin.getLangConfig().getDiscordMessage("discord.commands.trend.stable", "Prix stable");
                
                // Ici, vous pourriez implémenter une vraie logique de tendance
                // basée sur un historique de prix stocké
                
                EmbedBuilder embed = new EmbedBuilder()
                    .setTitle(plugin.getLangConfig().getDiscordMessage("discord.commands.trend.title", "📊 Tendance: %item%", new String[]{"%item%", itemName}))
                    .setDescription(plugin.getLangConfig().getDiscordMessage("discord.commands.trend.description", "Shop: **%shop%**", new String[]{"%shop%", shopName}))
                    .addField(plugin.getLangConfig().getDiscordMessage("discord.commands.trend.field_current_buy", "💳 Prix d'achat actuel"), plugin.getPriceFormatter().formatPrice(price.getBuyPrice()), true)
                    .addField(plugin.getLangConfig().getDiscordMessage("discord.commands.trend.field_current_sell", "💰 Prix de vente actuel"), plugin.getPriceFormatter().formatPrice(price.getSellPrice()), true)
                    .addField(plugin.getLangConfig().getDiscordMessage("discord.commands.trend.field_trend", "📈 Tendance"), trend + " " + trendText, true)
                    .setColor(trendColor)
                    .setTimestamp(LocalDateTime.now())
                    .setFooter(plugin.getLangConfig().getDiscordMessage("discord.commands.trend.footer", "Données basées sur les 24 dernières heures"));
                
                event.getChannel().sendMessageEmbeds(embed.build()).queue();
                
            } catch (Exception e) {
                plugin.getLogger().warning("Erreur lors de la récupération des tendances: " + e.getMessage());
                event.getChannel().sendMessage(plugin.getLangConfig().getDiscordMessage("discord.commands.errors.trend_error", "❌ Erreur lors de la récupération des tendances.")).queue();
            }
        });
    }
    
    /**
     * Méthode pour trouver une commande similaire
     */
    private String findSimilarCommand(String input) {
        String[] commands = {"prix", "stock", "shops", "lowstock", "search", "compare", "top", "stats", "help", "reload", "info", "admin", "trend"};
        String bestMatch = null;
        int bestScore = Integer.MAX_VALUE;
        
        for (String command : commands) {
            int score = calculateLevenshteinDistance(input.toLowerCase(), command.toLowerCase());
            if (score < bestScore && score <= 2) { // Seuil de similarité
                bestScore = score;
                bestMatch = command;
            }
        }
        
        return bestMatch;
    }
    
    /**
     * Calcul de la distance de Levenshtein pour les suggestions
     */
    private int calculateLevenshteinDistance(String a, String b) {
        if (a.isEmpty()) return b.length();
        if (b.isEmpty()) return a.length();
        
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        
        for (int i = 0; i <= a.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            dp[0][j] = j;
        }
        
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
            }
        }
        
        return dp[a.length()][b.length()];
    }
    
    /**
     * Vérifie les permissions d'administration
     */
    private boolean hasAdminPermission(DiscordGuildMessageReceivedEvent event) {
        // Vérifier les rôles Discord pour les permissions d'admin
        return event.getMember() != null && 
               (event.getMember().hasPermission(Permission.ADMINISTRATOR) ||
                event.getMember().getRoles().stream().anyMatch(role -> 
                    role.getName().toLowerCase().contains("admin") || 
                    role.getName().toLowerCase().contains("moderator")));
    }
    
    /**
     * Gestion du restock admin
     */
    private void handleAdminRestock(DiscordGuildMessageReceivedEvent event, String shopId, String itemId) {
        String actualShopId = findShopId(shopId);
        String actualItemId = findItemId(actualShopId, itemId);
        
        if (actualShopId == null || actualItemId == null) {
            event.getChannel().sendMessage(plugin.getLangConfig().getDiscordMessage("discord.commands.errors.shop_or_item_not_found", "❌ Shop ou item non trouvé.")).queue();
            return;
        }
        
        try {
            int maxStock = plugin.getShopConfigManager()
                .getItemValue(actualShopId, actualItemId, "stock.max", Integer.class)
                .orElse(plugin.getDataConfig().getStockMax());
            
            plugin.getStorageManager().saveStock(actualShopId, actualItemId, maxStock);
            
            String itemName = getItemDisplayName(actualShopId, actualItemId);
            String shopName = getShopDisplayName(actualShopId);
            
            EmbedBuilder embed = new EmbedBuilder()
                .setTitle(plugin.getLangConfig().getDiscordMessage("discord.commands.admin.restock_success", "✅ Restock Effectué"))
                .setDescription(plugin.getLangConfig().getDiscordMessage("discord.commands.admin.restock_description", "**%item%** dans **%shop%** a été restocké à %stock%", 
                    new String[]{"%item%", itemName}, new String[]{"%shop%", shopName}, new String[]{"%stock%", String.valueOf(maxStock)}))
                .setColor(Color.GREEN)
                .setTimestamp(LocalDateTime.now());
            
            event.getChannel().sendMessageEmbeds(embed.build()).queue();
            
        } catch (Exception e) {
            plugin.getLogger().warning("Erreur lors du restock admin: " + e.getMessage());
            event.getChannel().sendMessage(plugin.getLangConfig().getDiscordMessage("discord.commands.errors.admin_restock_error", "❌ Erreur lors du restock.")).queue();
        }
    }
    
    /**
     * Gestion du clear admin
     */
    private void handleAdminClear(DiscordGuildMessageReceivedEvent event, String shopId) {
        String actualShopId = findShopId(shopId);
        
        if (actualShopId == null) {
            event.getChannel().sendMessage(plugin.getLangConfig().getDiscordMessage("discord.commands.errors.shop_not_found", "❌ Shop non trouvé: %shop%", new String[]{"%shop%", shopId})).queue();
            return;
        }
        
        try {
            Set<String> items = plugin.getShopConfigManager().getShopItems(actualShopId);
            int clearedItems = 0;
            
            for (String itemId : items) {
                var type = plugin.getShopConfigManager().getTypeDynaShop(actualShopId, itemId);
                if (type.name().contains("STOCK")) {
                    plugin.getStorageManager().saveStock(actualShopId, itemId, 0);
                    clearedItems++;
                }
            }
            
            String shopName = getShopDisplayName(actualShopId);
            
            EmbedBuilder embed = new EmbedBuilder()
                .setTitle(plugin.getLangConfig().getDiscordMessage("discord.commands.admin.clear_success", "✅ Shop Vidé"))
                .setDescription(plugin.getLangConfig().getDiscordMessage("discord.commands.admin.clear_description", "**%shop%** a été vidé. %count% items ont été remis à zéro.", 
                    new String[]{"%shop%", shopName}, new String[]{"%count%", String.valueOf(clearedItems)}))
                .setColor(Color.ORANGE)
                .setTimestamp(LocalDateTime.now());
            
            event.getChannel().sendMessageEmbeds(embed.build()).queue();
            
        } catch (Exception e) {
            plugin.getLogger().warning("Erreur lors du clear admin: " + e.getMessage());
            event.getChannel().sendMessage(plugin.getLangConfig().getDiscordMessage("discord.commands.errors.admin_clear_error", "❌ Erreur lors du vidage.")).queue();
        }
    }
    
    /**
     * Gestion du reset admin
     */
    private void handleAdminReset(DiscordGuildMessageReceivedEvent event) {
        try {
            // Confirmer l'action dangereuse
            EmbedBuilder embed = new EmbedBuilder()
                .setTitle(plugin.getLangConfig().getDiscordMessage("discord.commands.admin.reset_confirm", "⚠️ Confirmation Reset"))
                .setDescription(plugin.getLangConfig().getDiscordMessage("discord.commands.admin.reset_description", "Cette action va remettre à zéro TOUS les stocks de TOUS les shops.\n**Cette action est irréversible !**\n\nPour confirmer, utilisez: `!dynashop admin reset CONFIRM`"))
                .setColor(Color.RED)
                .setTimestamp(LocalDateTime.now());
            
            event.getChannel().sendMessageEmbeds(embed.build()).queue();
            
        } catch (Exception e) {
            plugin.getLogger().warning("Erreur lors du reset admin: " + e.getMessage());
            event.getChannel().sendMessage(plugin.getLangConfig().getDiscordMessage("discord.commands.errors.admin_reset_error", "❌ Erreur lors du reset.")).queue();
        }
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