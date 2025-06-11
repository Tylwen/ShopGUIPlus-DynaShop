package fr.tylwen.satyria.dynashop.command;

import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import fr.tylwen.satyria.dynashop.DynaShopPlugin;
import fr.tylwen.satyria.dynashop.system.chart.DualPriceChartRenderer;

public class DualChartSubCommand implements SubCommand {

    private final DynaShopPlugin plugin;

    public DualChartSubCommand(DynaShopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "dualchart";
    }
    
    @Override
    public String getPermission() {
        return "dynashop.chart";
    }

    @Override
    public String getDescription() {
        return "Affiche un graphique avec les prix d'achat et de vente";
    }

    @Override
    public String getUsage() {
        return "/dynashop dualchart <shop> <item>";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cCette commande est réservée aux joueurs.");
            return false;
        }

        if (args.length < 2) {
            sender.sendMessage("§cUtilisation: " + getUsage());
            return false;
        }

        Player player = (Player) sender;
        String shopId = args[0];
        String itemId = args[1];

        // // Vérifier que le shop et l'item existent
        // if (!plugin.getShopConfigManager().shopExists(shopId)) {
        //     player.sendMessage("§cLe shop §f" + shopId + "§c n'existe pas.");
        //     return false;
        // }

        if (!plugin.getShopConfigManager().shopItemExists(shopId, itemId)) {
            player.sendMessage("§cL'item §f" + itemId + "§c n'existe pas dans le shop §f" + shopId + "§c.");
            return false;
        }

        // Créer et donner la carte au joueur
        ItemStack mapItem = DualPriceChartRenderer.getOrCreateMapItem(plugin, player, shopId, itemId);
        
        // Chercher un slot vide dans l'inventaire
        boolean given = false;
        for (int i = 0; i < 36; i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item == null || item.getType() == Material.AIR) {
                player.getInventory().setItem(i, mapItem);
                given = true;
                break;
            }
        }
        
        if (given) {
            player.sendMessage("§aVous avez reçu une carte d'évolution des prix pour §f" + itemId + "§a du shop §f" + shopId);
        } else {
            player.getWorld().dropItemNaturally(player.getLocation(), mapItem);
            player.sendMessage("§aUne carte d'évolution des prix a été déposée au sol (inventaire plein)");
        }
        
        return true;
    }
}