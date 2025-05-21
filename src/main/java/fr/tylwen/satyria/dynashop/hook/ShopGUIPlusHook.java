package fr.tylwen.satyria.dynashop.hook;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import fr.tylwen.satyria.dynashop.DynaShopPlugin;
// import net.brcdev.shopgui.ShopGuiPlusApi;
import net.brcdev.shopgui.event.ShopGUIPlusPostEnableEvent;

public class ShopGUIPlusHook implements Listener {
    
    private DynaShopPlugin mainPlugin;

    public ShopGUIPlusHook(DynaShopPlugin mainPlugin) {
        this.mainPlugin = mainPlugin;
    }

    @EventHandler
    public void onShopGUIPlusPostEnable(ShopGUIPlusPostEnableEvent event) {
        // mainPlugin.getLogger().info("Plugin ShopGUIPlus is enabled, registering DynaShop as a shop type.");
        mainPlugin.getLogger().info("ShopGUIPlus plugin is enabled, logging DynaShop");
        // Register DynaShop as a shop type in ShopGUIPlus
        // ShopGuiPlusApi.getInstance().getShopTypeManager().registerShopType(new DynaShopShopType(mainPlugin));

        // // Register the DynaShopItemProvider
        // ShopGuiPlusApi.registerItemProvider(new DynaShopItemProvider(mainPlugin, null));
    }
}
