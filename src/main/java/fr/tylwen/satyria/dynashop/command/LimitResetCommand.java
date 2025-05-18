package fr.tylwen.satyria.dynashop.command;

import fr.tylwen.satyria.dynashop.DynaShopPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class LimitResetCommand implements CommandExecutor {
    private final DynaShopPlugin plugin;
    
    public LimitResetCommand(DynaShopPlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("dynashop.admin.resetlimits")) {
            sender.sendMessage(ChatColor.RED + "Vous n'avez pas la permission d'utiliser cette commande.");
            return true;
        }
        
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /resetlimits <joueur> [shopID] [itemID]");
            return true;
        }
        
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Joueur non trouvé.");
            return true;
        }
        
        if (args.length >= 3) {
            // Réinitialiser un item spécifique
            String shopID = args[1];
            String itemID = args[2];
            
            plugin.getTransactionLimiter().resetLimits(target, shopID, itemID)
                .thenAccept(success -> {
                    if (success) {
                        sender.sendMessage(ChatColor.GREEN + "Limites réinitialisées pour " + target.getName() + 
                                          " sur " + shopID + ":" + itemID);
                    } else {
                        sender.sendMessage(ChatColor.RED + "Erreur lors de la réinitialisation des limites.");
                    }
                });
        } else {
            sender.sendMessage(ChatColor.RED + "Veuillez spécifier un shopID et un itemID.");
        }
        
        return true;
    }
}