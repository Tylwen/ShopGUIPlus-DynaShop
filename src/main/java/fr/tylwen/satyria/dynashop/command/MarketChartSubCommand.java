package fr.tylwen.satyria.dynashop.command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import fr.tylwen.satyria.dynashop.DynaShopPlugin;
import fr.tylwen.satyria.dynashop.system.chart.MarketChartRenderer;

public class MarketChartSubCommand implements SubCommand {
    private final DynaShopPlugin plugin;
    
    public MarketChartSubCommand(DynaShopPlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cCette commande ne peut être utilisée que par un joueur.");
            return true;
        }
        
        Player player = (Player) sender;
        
        if (args.length < 2) {
            player.sendMessage("§cUtilisation: /dynashop chart <shop_id> <item_id>");
            return true;
        }
        
        String shopId = args[0];
        String itemId = args[1];
        
        // Vérifier que le shop et l'item existent
        if (!plugin.getShopConfigManager().shopItemExists(shopId, itemId)) {
            player.sendMessage("§cL'item §f" + itemId + "§c dans le shop §f" + shopId + "§c n'existe pas.");
            return true;
        }
        
        // Créer et donner la carte au joueur
        ItemStack mapItem = MarketChartRenderer.getOrCreateMapItem(plugin, player, shopId, itemId);
        player.getInventory().addItem(mapItem);
        
        player.sendMessage("§aVous avez reçu une carte du marché pour §f" + itemId + "§a du shop §f" + shopId);
        
        return true;
    }

    @Override
    public String getName() {
        return "chart";
    }

    @Override
    public String getPermission() {
        return "dynashop.chart";
    }

    @Override
    public String getDescription() {
        return "Obtenir une carte affichant l'évolution des prix d'un item";
    }

    @Override
    public String getUsage() {
        return "/dynashop chart <shop_id> <item_id>";
    }
}