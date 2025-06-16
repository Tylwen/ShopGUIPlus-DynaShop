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
package fr.tylwen.satyria.dynashop.utils;

import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;

public class PluginUtils {

    public static boolean isPluginInstalled(String pluginName) {
        PluginManager pluginManager = Bukkit.getPluginManager();
        return pluginManager.getPlugin(pluginName) != null;
    }
    
    public static boolean isPluginEnabled(String pluginName) {
        PluginManager pluginManager = Bukkit.getPluginManager();
        return pluginManager.getPlugin(pluginName) != null && pluginManager.getPlugin(pluginName).isEnabled();
    }

    public static boolean isPluginInstalledAndEnabled(String pluginName) {
        return isPluginInstalled(pluginName) && isPluginEnabled(pluginName);
    }

}
