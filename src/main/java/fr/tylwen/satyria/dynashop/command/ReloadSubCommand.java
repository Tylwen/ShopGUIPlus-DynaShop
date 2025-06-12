package fr.tylwen.satyria.dynashop.command;

import org.bukkit.command.CommandSender;

import fr.tylwen.satyria.dynashop.DynaShopPlugin;

public class ReloadSubCommand implements SubCommand {
    private final DynaShopPlugin plugin;
    
    public ReloadSubCommand(DynaShopPlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean execute(CommandSender sender, String[] args) {
        // Logique de rechargement
        plugin.reloadConfig();
        // Recharger d'autres configurations si nécessaire
        plugin.load();
        plugin.getShopConfigManager().reloadCache(); // Recharger les shops de DynaShop
        plugin.reloadDatabase();
        
        sender.sendMessage("§aConfiguration DynaShop rechargée avec succès!");
        return true;
    }
    
    @Override
    public String getName() {
        return "reload";
    }
    
    @Override
    public String getPermission() {
        return "dynashop.reload";
    }
    
    @Override
    public String getDescription() {
        return "Recharge la configuration du plugin";
    }
    
    @Override
    public String getUsage() {
        return "/dynashop reload";
    }
}