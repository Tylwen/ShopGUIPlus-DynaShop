package fr.tylwen.satyria.dynashop.system;

import fr.tylwen.satyria.dynashop.DynaShopPlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
// import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.UUID;
import java.util.logging.Level;

public class TaxService {

    private final DynaShopPlugin plugin;
    private Economy economy;
    
    private boolean enabled;
    private String taxReceiverName;
    private UUID taxReceiverUUID;
    private double buyTaxRate;
    private double sellTaxRate;
    private boolean logTransactions;
    private String taxMode;
    
    public TaxService(DynaShopPlugin plugin) {
        this.plugin = plugin;
        setupEconomy();
        loadConfig();
    }
    
    private boolean setupEconomy() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().warning("Vault not found - Tax system will be disabled");
            return false;
        }
        
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            plugin.getLogger().warning("Economy provider not found - Tax system will be disabled");
            return false;
        }
        
        economy = rsp.getProvider();
        return economy != null;
    }
    
    public void loadConfig() {
        ConfigurationSection taxConfig = plugin.getConfig().getConfigurationSection("tax");
        
        if (taxConfig == null) {
            plugin.getLogger().info("No tax configuration found. Creating default config.");
            plugin.getConfig().set("tax.enabled", false);
            plugin.getConfig().set("tax.mode", "system");
            plugin.getConfig().set("tax.receiver", "admin");
            plugin.getConfig().set("tax.buy-rate", 5.0);
            plugin.getConfig().set("tax.sell-rate", 3.0);
            plugin.getConfig().set("tax.log-transactions", true);
            plugin.saveConfig();
            
            enabled = false;
            taxMode = "system"; // Default mode
            taxReceiverName = "admin";
            buyTaxRate = 5.0;
            sellTaxRate = 3.0;
            logTransactions = true;
        } else {
            enabled = taxConfig.getBoolean("enabled", false);
            taxMode = taxConfig.getString("mode", "system").toLowerCase();
            taxReceiverName = taxConfig.getString("receiver", "admin");
            buyTaxRate = taxConfig.getDouble("buy-rate", 5.0);
            sellTaxRate = taxConfig.getDouble("sell-rate", 3.0);
            logTransactions = taxConfig.getBoolean("log-transactions", true);
            
            // Récupérer l'UUID du receveur de taxe
            if (enabled) {
                resolveTaxReceiverUUID();
            }
        }
    }
    
    private void resolveTaxReceiverUUID() {
        // Essayer de récupérer le UUID depuis la configuration
        String uuidString = plugin.getConfig().getString("tax.receiver-uuid");
        if (uuidString != null && !uuidString.isEmpty()) {
            try {
                taxReceiverUUID = UUID.fromString(uuidString);
                return;
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid UUID format in config for tax receiver");
            }
        }

        // Si pas de UUID valide, essayer de récupérer depuis le nom
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(taxReceiverName);
        if (offlinePlayer.hasPlayedBefore()) {
            taxReceiverUUID = offlinePlayer.getUniqueId();
            // Sauvegarder l'UUID pour éviter de le rechercher à chaque fois
            plugin.getConfig().set("tax.receiver-uuid", taxReceiverUUID.toString());
            plugin.saveConfig();
        } else {
            // plugin.getLogger().warning("Tax receiver player '" + taxReceiverName + "' not found!");
            // enabled = false;
            taxMode = "system"; // Revenir au mode système si le joueur n'est pas trouvé
        }
    }
    
    /**
     * Applique la taxe sur une transaction d'achat
     * @param player Joueur effectuant l'achat
     * @param totalPrice Prix total de la transaction
     * @param shopId ID du shop
     * @param itemId ID de l'item
     * @return Montant de la taxe prélevée
     */
    public double applyBuyTax(Player player, double totalPrice, String shopId, String itemId) {
        if (!enabled || economy == null) {
            return 0.0;
        }
        
        double taxAmount = calculateTaxAmount(totalPrice, buyTaxRate);
        if (taxAmount <= 0) {
            return 0.0;
        }
        
        processTaxPayment(player, taxAmount, true, shopId, itemId);
        return taxAmount;
    }
    
    /**
     * Applique la taxe sur une transaction de vente
     * @param player Joueur effectuant la vente
     * @param totalPrice Prix total de la transaction
     * @param shopId ID du shop
     * @param itemId ID de l'item
     * @return Montant de la taxe prélevée
     */
    public double applySellTax(Player player, double totalPrice, String shopId, String itemId) {
        if (!enabled || economy == null) {
            return 0.0;
        }
        
        double taxAmount = calculateTaxAmount(totalPrice, sellTaxRate);
        if (taxAmount <= 0) {
            return 0.0;
        }
        
        processTaxPayment(player, taxAmount, false, shopId, itemId);
        return taxAmount;
    }
    
    private double calculateTaxAmount(double price, double rate) {
        return Math.max(0, price * (rate / 100.0));
    }
    
    private void processTaxPayment(Player player, double taxAmount, boolean isBuy, String shopId, String itemId) {
        // Arrondir à 2 décimales pour éviter les problèmes de précision
        taxAmount = Math.round(taxAmount * 100.0) / 100.0;
        
        // Ne pas traiter les montants trop petits
        if (taxAmount < 0.01) {
            return;
        }
        
        // OfflinePlayer receiver = Bukkit.getOfflinePlayer(taxReceiverUUID);
        // economy.depositPlayer(receiver, taxAmount);
        
        // Traiter selon le mode
        switch (taxMode) {
            case "player":
                // La taxe va à un joueur réel
                if (taxReceiverUUID != null) {
                    OfflinePlayer receiver = Bukkit.getOfflinePlayer(taxReceiverUUID);
                    economy.depositPlayer(receiver, taxAmount);
                }
                break;
                
            case "system":
                // La taxe va à un compte système - on utilise le nom directement
                // Certains plugins d'économie permettent d'utiliser des noms de comptes qui ne sont pas des joueurs
                economy.depositPlayer(taxReceiverName, taxAmount);
                break;
                
            case "remove":
                // La taxe est simplement retirée de l'économie
                // Aucune action supplémentaire nécessaire car l'argent est déjà retiré
                break;
        }
        
        // Log la transaction si activé
        if (logTransactions) {
            // String transactionType = isBuy ? "achat" : "vente";
            String transactionType = isBuy ? "buy" : "sell";
            String destination = "";
            switch (taxMode) {
                case "player":
                    // destination = "joueur " + taxReceiverName;
                    destination = "player " + taxReceiverName;
                    break;
                case "system":
                    // destination = "compte système " + taxReceiverName;
                    destination = "system account " + taxReceiverName;
                    break;
                case "remove":
                    // destination = "supprimé de l'économie";
                    destination = "removed from the economy";
                    break;
            }
            
            // plugin.getLogger().log(Level.INFO, 
            //     String.format("[TAX] %s a payé %.2f de taxe sur %s de %s:%s. Montant %s.", 
            //         player.getName(), taxAmount, transactionType, shopId, itemId, 
            //         taxMode.equals("remove") ? "supprimé de l'économie" : "versé à " + destination));
            plugin.getLogger().log(Level.INFO, 
                String.format("[TAX] %s paid %.2f tax on %s of %s:%s. Amount %s.", 
                    player.getName(), taxAmount, transactionType, shopId, itemId, 
                    taxMode.equals("remove") ? "removed from the economy" : "paid to " + destination));
        }
        
        // // // Informer le joueur
        // // plugin.getLangConfig().sendMessage(player, "tax." + (isBuy ? "buy" : "sell"), 
        // //     new String[][]{
        // //         {"%amount%", String.format("%.2f", taxAmount)},
        // //         {"%rate%", String.format("%.1f", isBuy ? buyTaxRate : sellTaxRate)},
        // //         {"%receiver%", taxReceiverName}
        // //     });
        // if (isBuy) {
        //     player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getLangConfig().getMsgTaxBuy()
        //         .replace("%amount%", String.format("%.2f", taxAmount))
        //         .replace("%rate%", String.format("%.1f", buyTaxRate))
        //         .replace("%receiver%", taxMode.equals("remove") ? "l'État" : taxReceiverName)));
        // } else {
        //     player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getLangConfig().getMsgTaxSell()
        //         .replace("%amount%", String.format("%.2f", taxAmount))
        //         .replace("%rate%", String.format("%.1f", sellTaxRate))
        //         .replace("%receiver%", taxMode.equals("remove") ? "l'État" : taxReceiverName)));
        // }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public double getBuyTaxRate() {
        return buyTaxRate;
    }
    
    public double getSellTaxRate() {
        return sellTaxRate;
    }
    
    public String getTaxReceiverName() {
        return taxReceiverName;
    }

    public String getTaxMode() {
        return taxMode;
    }

    public Economy getEconomy() {
        return economy;
    }
}