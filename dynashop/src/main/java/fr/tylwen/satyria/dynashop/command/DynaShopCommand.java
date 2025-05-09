package fr.tylwen.satyria.dynashop.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import fr.tylwen.satyria.dynashop.DynaShopPlugin;

public class DynaShopCommand implements CommandExecutor {
    private final DynaShopPlugin plugin;

    public DynaShopCommand(DynaShopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§eDynaShop Plugin - Version " + plugin.getDescription().getVersion());
            sender.sendMessage("§eUse /dynashop help for a list of commands.");
            return true;
        }

        // Handle subcommands here (e.g., /dynashop reload, /dynashop shop, etc.)
        // Example: if (args[0].equalsIgnoreCase("reload")) { ... }

        return false; // Command not recognized
    }
}
