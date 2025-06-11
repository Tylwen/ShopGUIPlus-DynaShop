package fr.tylwen.satyria.dynashop.command;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import fr.tylwen.satyria.dynashop.DynaShopPlugin;

public class DynaShopCommand implements CommandExecutor {
    private final DynaShopPlugin plugin;
    private final Map<String, SubCommand> subCommands = new HashMap<>();
    
    public DynaShopCommand(DynaShopPlugin plugin) {
        this.plugin = plugin;
        
        // Enregistrer les sous-commandes
        registerSubCommand(new ReloadSubCommand(plugin));
        registerSubCommand(new InflationSubCommand(plugin));
        registerSubCommand(new LimitSubCommand(plugin));
        registerSubCommand(new MarketChartSubCommand(plugin));
        registerSubCommand(new DualChartSubCommand(plugin));
        // Ajouter d'autres sous-commandes ici
        // if (plugin.getConfig().getBoolean("web-dashboard.enabled", true)) {
        //     registerSubCommand(new WebChartSubCommand(plugin));
        // }
    }
    
    private void registerSubCommand(SubCommand subCommand) {
        subCommands.put(subCommand.getName().toLowerCase(), subCommand);
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§eDynaShop Plugin - Version " + plugin.getDescription().getVersion());
            sender.sendMessage("§eUtilisez /dynashop help pour la liste des commandes.");
            return true;
        }
        
        String subCommandName = args[0].toLowerCase();
        
        if (subCommandName.equals("help")) {
            showHelp(sender);
            return true;
        }

        if (args.length >= 1 && subCommandName.equals("purgehistory")) {
            int days = args.length >= 2 ? Integer.parseInt(args[1]) : 30;
            sender.sendMessage("§aSuppression des données d'historique plus anciennes que " + days + " jours...");
            
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                plugin.getDataManager().purgeOldPriceHistory(days);
                Bukkit.getScheduler().runTask(plugin, () -> 
                    sender.sendMessage("§aNettoyage terminé!")
                );
            });
            return true;
        }
        
        SubCommand subCommand = subCommands.get(subCommandName);
        
        if (subCommand == null) {
            sender.sendMessage("§cCommande inconnue. Utilisez /dynashop help pour la liste des commandes.");
            return true;
        }
        
        // Vérifier la permission
        if (!subCommand.getPermission().isEmpty() && !sender.hasPermission(subCommand.getPermission())) {
            sender.sendMessage("§cVous n'avez pas la permission d'utiliser cette commande.");
            return true;
        }
        
        // Créer un nouveau tableau d'arguments sans le premier (qui est le nom de la sous-commande)
        String[] subArgs = new String[args.length - 1];
        System.arraycopy(args, 1, subArgs, 0, args.length - 1);
        
        // Exécuter la sous-commande
        return subCommand.execute(sender, subArgs);
    }
    
    private void showHelp(CommandSender sender) {
        sender.sendMessage("§e--- DynaShop Commands ---");
        
        for (SubCommand subCommand : subCommands.values()) {
            // Afficher uniquement les commandes pour lesquelles le joueur a la permission
            if (subCommand.getPermission().isEmpty() || sender.hasPermission(subCommand.getPermission())) {
                sender.sendMessage("§e" + subCommand.getUsage() + " §7- " + subCommand.getDescription());
            }
        }
    }
}