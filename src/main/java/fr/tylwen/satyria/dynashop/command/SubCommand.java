package fr.tylwen.satyria.dynashop.command;

import org.bukkit.command.CommandSender;

public interface SubCommand {
    /**
     * Exécute la sous-commande
     * @param sender L'expéditeur de la commande
     * @param args Les arguments de la commande (sans le premier qui est le nom de la sous-commande)
     * @return true si la commande a été exécutée avec succès, false sinon
     */
    boolean execute(CommandSender sender, String[] args);
    
    /**
     * Retourne le nom de la sous-commande
     * @return Le nom de la sous-commande
     */
    String getName();
    
    /**
     * Retourne la permission requise pour utiliser cette commande
     * @return La permission requise
     */
    String getPermission();
    
    /**
     * Retourne la description de la commande pour l'aide
     * @return La description
     */
    String getDescription();
    
    /**
     * Retourne l'usage de la commande
     * @return L'usage
     */
    String getUsage();
}