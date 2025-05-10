package fr.tylwen.satyria.dynashop.task;

import fr.tylwen.satyria.dynashop.DynaShopPlugin;

public class ReloadDatabaseTask implements Runnable {
    private final DynaShopPlugin plugin;

    public ReloadDatabaseTask(DynaShopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        plugin.getDataManager().reloadDatabaseConnection();
    }
}