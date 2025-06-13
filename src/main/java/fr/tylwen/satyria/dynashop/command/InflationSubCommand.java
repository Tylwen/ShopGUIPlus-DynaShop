package fr.tylwen.satyria.dynashop.command;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.bukkit.command.CommandSender;

import fr.tylwen.satyria.dynashop.DynaShopPlugin;

public class InflationSubCommand implements SubCommand {
    private final DynaShopPlugin plugin;
    
    public InflationSubCommand(DynaShopPlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (plugin.getInflationManager() == null) {
            sender.sendMessage("§cLe système d'inflation n'est pas disponible.");
            return true;
        }
        
        if (args.length == 0) {
            sender.sendMessage("§c" + getUsage());
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "info":
                double factor = plugin.getInflationManager().getInflationFactor();
                String status = plugin.getInflationManager().isEnabled() ? "§aactivée" : "§cdésactivée";
                
                sender.sendMessage("§e--- Information sur l'inflation ---");
                sender.sendMessage("§7Status: " + status);
                sender.sendMessage("§7Facteur actuel: §e" + String.format("%.4f", factor) + "x");
                sender.sendMessage("§7Dernier ajustement: §e" + 
                    new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(
                        new Date(plugin.getStorageManager().getLastInflationUpdate())));
                break;
                
            case "enable":
                if (!plugin.getInflationManager().isEnabled()) {
                    plugin.getInflationManager().setEnabled(true);
                    sender.sendMessage("§aLe système d'inflation a été activé.");
                } else {
                    sender.sendMessage("§cLe système d'inflation est déjà actif.");
                }
                break;
                
            case "disable":
                if (plugin.getInflationManager().isEnabled()) {
                    plugin.getInflationManager().setEnabled(false);
                    sender.sendMessage("§aLe système d'inflation a été désactivé.");
                } else {
                    sender.sendMessage("§cLe système d'inflation est déjà inactif.");
                }
                break;
                
            case "reset":
                plugin.getInflationManager().resetInflation();
                sender.sendMessage("§aLe facteur d'inflation a été réinitialisé à 1.0.");
                break;
                
            case "update":
                plugin.getInflationManager().updateInflation();
                sender.sendMessage("§aL'inflation a été mise à jour manuellement.");
                break;
                
            default:
                sender.sendMessage("§c" + getUsage());
                break;
        }
        
        return true;
    }
    
    @Override
    public String getName() {
        return "inflation";
    }
    
    @Override
    public String getPermission() {
        return "dynashop.admin";
    }
    
    @Override
    public String getDescription() {
        return "Gère le système d'inflation";
    }
    
    @Override
    public String getUsage() {
        return "/dynashop inflation [info|enable|disable|reset|update]";
    }
}