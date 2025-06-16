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
