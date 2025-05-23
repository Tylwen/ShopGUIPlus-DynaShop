// package fr.tylwen.satyria.dynashop.command;

// public class ReloadCommand extends CommandBase {
//     public ReloadCommand() {
//         super("reload", "Reload the plugin configuration and shops.", "/dynashop reload", "dynashop.reload");
//     }

//     @Override
//     public void execute(CommandSender sender, String[] args) {
//         if (!sender.hasPermission(getPermission())) {
//             sender.sendMessage(Lang.getConfig().getString("no_permission"));
//             return;
//         }

//         // Reload the configuration files
//         Settings.getInstance().setConfig(YamlConfiguration.loadConfiguration(new File(DynaShopPlugin.getInstance().getDataFolder(), "config.yml")));
//         Lang.setConfig(YamlConfiguration.loadConfiguration(new File(DynaShopPlugin.getInstance().getDataFolder(), "lang.yml")));

//         // Reload the shop files
//         ShopFile.loadShopFiles(new File(DynaShopPlugin.getInstance().getDataFolder(), "shops"));

//         sender.sendMessage(Lang.getConfig().getString("config_reload_success"));
//     }
    
// }

package fr.tylwen.satyria.dynashop.command;

import fr.tylwen.satyria.dynashop.DynaShopPlugin;

// import java.io.File;

// import java.util.Arrays;
// import java.util.Collections;
// import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
// import org.bukkit.configuration.file.FileConfiguration;
// import org.bukkit.configuration.file.YamlConfiguration;

public class ReloadCommand implements CommandExecutor {
    private final DynaShopPlugin plugin;

    public ReloadCommand(DynaShopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("dynashop.reload")) {
            sender.sendMessage("§cVous n'avez pas la permission d'exécuter cette commande.");
            return true;
        }

        sender.sendMessage("§eRechargement du plugin DynaShop...");
        plugin.onDisable(); // Désactiver proprement le plugin
        plugin.onEnable();  // Réactiver le plugin
        plugin.reloadConfig(); // Recharge config.yml
        sender.sendMessage("§aLe plugin DynaShop a été rechargé avec succès !");
        return true;
        
        // sender.sendMessage("§eRechargement du plugin DynaShop...");

        // // Recharger les fichiers de configuration
        // try {
        //     plugin.reloadConfig(); // Recharge config.yml
        //     File langFile = new File(plugin.getDataFolder(), "lang.yml");
        //     if (langFile.exists()) {
        //         FileConfiguration langConfig = YamlConfiguration.loadConfiguration(langFile);
        //         plugin.setLangConfig(langConfig); // Méthode à ajouter dans DynaShopPlugin pour gérer lang.yml
        //     }

        //     // Recharger les shops
        //     plugin.getShopConfigManager().reloadShops(); // Assurez-vous que cette méthode existe dans ShopConfigManager

        //     sender.sendMessage("§aLes fichiers de configuration et de langue ont été rechargés avec succès !");
        // } catch (Exception e) {
        //     sender.sendMessage("§cUne erreur est survenue lors du rechargement des fichiers de configuration.");
        //     e.printStackTrace();
        // }

        // sender.sendMessage("§aLe plugin DynaShop a été rechargé avec succès !");
        // return true;
    }
}