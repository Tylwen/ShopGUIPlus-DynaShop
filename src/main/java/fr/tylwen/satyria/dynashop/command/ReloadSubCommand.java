/*
 * ShopGUI+ DynaShop - Dynamic Economy Addon for Minecraft
 * Copyright (C) 2025 Tylwen
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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
        plugin.reloadData();
        
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