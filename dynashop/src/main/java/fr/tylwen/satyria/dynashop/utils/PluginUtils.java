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
