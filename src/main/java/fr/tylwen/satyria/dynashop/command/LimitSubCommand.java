package fr.tylwen.satyria.dynashop.command;

import fr.tylwen.satyria.dynashop.DynaShopPlugin;
// import fr.tylwen.satyria.dynashop.system.TransactionLimiter;
import fr.tylwen.satyria.dynashop.system.TransactionLimiter.TransactionLimit;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.concurrent.CompletableFuture;
import java.util.UUID;

public class LimitSubCommand implements SubCommand {
    private final DynaShopPlugin plugin;
    
    public LimitSubCommand(DynaShopPlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            showUsage(sender);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "reset":
                return handleResetCommand(sender, args);
            case "info":
                return handleInfoCommand(sender, args);
            case "resetall":
                return handleResetAllCommand(sender, args);
            default:
                showUsage(sender);
                return true;
        }
    }
    
    private boolean handleResetCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /dynashop limit reset <joueur> [shopID] [itemID]");
            return true;
        }
        
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Joueur non trouvé.");
            return true;
        }
        
        if (args.length >= 4) {
            // Réinitialiser un item spécifique
            String shopID = args[2];
            String itemID = args[3];
            
            plugin.getTransactionLimiter().resetLimits(target, shopID, itemID)
                .thenAccept(success -> {
                    if (Boolean.TRUE.equals(success)) {
                        sender.sendMessage(ChatColor.GREEN + "Limites réinitialisées pour " + target.getName() + " sur " + shopID + ":" + itemID);
                    } else {
                        sender.sendMessage(ChatColor.RED + "Erreur lors de la réinitialisation des limites.");
                    }
                });
        } else {
            // Réinitialiser toutes les limites du joueur
            plugin.getTransactionLimiter().resetAllLimits(target.getUniqueId())
                .thenAccept(count -> {
                    sender.sendMessage(ChatColor.GREEN + "Toutes les limites de " + target.getName() + 
                                    " ont été réinitialisées (" + count + " entrées affectées).");
                });
        }
        
        return true;
    }
    
    private boolean handleInfoCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /dynashop limit info <joueur> [shopID] [itemID]");
            return true;
        }
        
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Joueur non trouvé.");
            return true;
        }
        
        if (args.length >= 4) {
            // Afficher les informations pour un item spécifique
            String shopID = args[2];
            String itemID = args[3];
            
            // Vérifier les limites pour l'achat
            TransactionLimit buyLimit = plugin.getTransactionLimiter().getTransactionLimit(shopID, itemID, true);
            if (buyLimit != null && buyLimit.getAmount() > 0) {
                CompletableFuture<Integer> remainingBuy = plugin.getTransactionLimiter().getRemainingAmount(target, shopID, itemID, true);
                CompletableFuture<Long> nextAvailableBuy = plugin.getTransactionLimiter().getNextAvailableTime(target, shopID, itemID, true);
                // Integer remainingBuy = plugin.getTransactionLimiter().getRemainingAmountSync(target, shopID, itemID, true);
                // Long nextAvailableBuy = plugin.getTransactionLimiter().getNextAvailableTimeSync(target, shopID, itemID, true);
                
                remainingBuy.thenCombine(nextAvailableBuy, (remaining, nextTime) -> {
                    sender.sendMessage(ChatColor.YELLOW + "Limites d'achat pour " + target.getName() + " sur " + shopID + ":" + itemID + ":");
                    sender.sendMessage(ChatColor.YELLOW + "  Limite totale: " + ChatColor.WHITE + buyLimit.getAmount());
                    sender.sendMessage(ChatColor.YELLOW + "  Restant: " + ChatColor.WHITE + remaining);
                    
                    if (nextTime > 0) {
                        String formattedTime = formatTime(nextTime / 1000);
                        sender.sendMessage(ChatColor.YELLOW + "  Prochain reset dans: " + ChatColor.WHITE + formattedTime);
                    } else {
                        sender.sendMessage(ChatColor.YELLOW + "  Prochain reset: " + ChatColor.WHITE + "Disponible");
                    }
                    
                    return null;
                });
            } else {
                sender.sendMessage(ChatColor.YELLOW + "Aucune limite d'achat définie pour " + shopID + ":" + itemID);
            }
            
            // Vérifier les limites pour la vente
            TransactionLimit sellLimit = plugin.getTransactionLimiter().getTransactionLimit(shopID, itemID, false);
            if (sellLimit != null && sellLimit.getAmount() > 0) {
                CompletableFuture<Integer> remainingSell = plugin.getTransactionLimiter().getRemainingAmount(target, shopID, itemID, false);
                CompletableFuture<Long> nextAvailableSell = plugin.getTransactionLimiter().getNextAvailableTime(target, shopID, itemID, false);
                
                remainingSell.thenCombine(nextAvailableSell, (remaining, nextTime) -> {
                    sender.sendMessage(ChatColor.YELLOW + "Limites de vente pour " + target.getName() + " sur " + shopID + ":" + itemID + ":");
                    sender.sendMessage(ChatColor.YELLOW + "  Limite totale: " + ChatColor.WHITE + sellLimit.getAmount());
                    sender.sendMessage(ChatColor.YELLOW + "  Restant: " + ChatColor.WHITE + remaining);
                    
                    if (nextTime > 0) {
                        String formattedTime = formatTime(nextTime / 1000);
                        sender.sendMessage(ChatColor.YELLOW + "  Prochain reset dans: " + ChatColor.WHITE + formattedTime);
                    } else {
                        sender.sendMessage(ChatColor.YELLOW + "  Prochain reset: " + ChatColor.WHITE + "Disponible");
                    }
                    
                    return null;
                });
            } else {
                sender.sendMessage(ChatColor.YELLOW + "Aucune limite de vente définie pour " + shopID + ":" + itemID);
            }
        } else {
            // Afficher les statistiques générales pour le joueur
            plugin.getTransactionLimiter().getStatistics().thenAccept(stats -> {
                sender.sendMessage(ChatColor.YELLOW + "Statistiques globales pour " + target.getName() + ":");
                
                if (stats.containsKey("total_records")) {
                    sender.sendMessage(ChatColor.YELLOW + "  Transactions totales: " + ChatColor.WHITE + stats.get("total_records"));
                }
                
                if (stats.containsKey("count_buy")) {
                    sender.sendMessage(ChatColor.YELLOW + "  Achats: " + ChatColor.WHITE + stats.get("count_buy"));
                }
                
                if (stats.containsKey("count_sell")) {
                    sender.sendMessage(ChatColor.YELLOW + "  Ventes: " + ChatColor.WHITE + stats.get("count_sell"));
                }
                
                if (stats.containsKey("oldest_record")) {
                    sender.sendMessage(ChatColor.YELLOW + "  Plus ancienne transaction: " + ChatColor.WHITE + stats.get("oldest_record"));
                }
            });
        }
        
        return true;
    }
    
    private boolean handleResetAllCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /dynashop limit resetall <all|joueur>");
            return true;
        }
        
        String target = args[1].toLowerCase();
        
        if ("all".equals(target)) {
            sender.sendMessage(ChatColor.YELLOW + "Réinitialisation de toutes les limites pour tous les joueurs...");
            plugin.getTransactionLimiter().cleanupExpiredTransactions();
            sender.sendMessage(ChatColor.GREEN + "Toutes les limites ont été réinitialisées.");
        } else {
            Player targetPlayer = Bukkit.getPlayer(target);
            if (targetPlayer == null) {
                // Essayer de chercher par UUID
                UUID targetUUID = null;
                try {
                    targetUUID = UUID.fromString(target);
                } catch (IllegalArgumentException e) {
                    // Ce n'est pas un UUID valide
                    sender.sendMessage(ChatColor.RED + "Joueur non trouvé.");
                    return true;
                }
                
                final UUID finalUUID = targetUUID;
                plugin.getTransactionLimiter().resetAllLimits(finalUUID)
                    .thenAccept(count -> {
                        sender.sendMessage(ChatColor.GREEN + "Toutes les limites du joueur UUID:" + finalUUID + 
                                          " ont été réinitialisées (" + count + " entrées affectées).");
                    });
            } else {
                plugin.getTransactionLimiter().resetAllLimits(targetPlayer.getUniqueId())
                    .thenAccept(count -> {
                        sender.sendMessage(ChatColor.GREEN + "Toutes les limites de " + targetPlayer.getName() + 
                                          " ont été réinitialisées (" + count + " entrées affectées).");
                    });
            }
        }
        
        return true;
    }
    
    private void showUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "=== Commandes de gestion des limites ===");
        sender.sendMessage(ChatColor.YELLOW + "/dynashop limit reset <joueur> [shopID] [itemID]" + ChatColor.GRAY + " - Réinitialise les limites");
        sender.sendMessage(ChatColor.YELLOW + "/dynashop limit info <joueur> [shopID] [itemID]" + ChatColor.GRAY + " - Affiche les informations sur les limites");
        sender.sendMessage(ChatColor.YELLOW + "/dynashop limit resetall <all|joueur>" + ChatColor.GRAY + " - Réinitialise toutes les limites");
    }
    
    private String formatTime(long seconds) {
        if (seconds < 60) {
            return seconds + " secondes";
        } else if (seconds < 3600) {
            return (seconds / 60) + " minutes " + (seconds % 60) + " secondes";
        } else if (seconds < 86400) {
            return (seconds / 3600) + " heures " + ((seconds % 3600) / 60) + " minutes";
        } else {
            return (seconds / 86400) + " jours " + ((seconds % 86400) / 3600) + " heures";
        }
    }

    @Override
    public String getName() {
        return "limit";
    }

    @Override
    public String getPermission() {
        return "dynashop.admin.limit";
    }

    @Override
    public String getDescription() {
        return "Gère les limites de transaction des joueurs";
    }

    @Override
    public String getUsage() {
        return "/dynashop limit [reset|info|resetall] <joueur> [shopID] [itemID]";
    }
}