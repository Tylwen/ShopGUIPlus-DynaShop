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
package fr.tylwen.satyria.dynashop.data.storage;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.google.gson.reflect.TypeToken;

import fr.tylwen.satyria.dynashop.price.DynamicPrice;

public class PriceDataManager {
    private final File file;
    private Map<String, DynamicPrice> prices = new HashMap<>();

    public PriceDataManager(File file) {
        this.file = file;
    }

    public void load() {
        try {
            Type type = new TypeToken<Map<String, DynamicPrice>>(){}.getType();
            prices = JsonStorage.loadFromFile(file, type, new HashMap<>());
        } catch (Exception e) {
            prices = new HashMap<>();
        }
    }

    public void save() {
        // try {
        //     JsonStorage.saveToFile(file, prices);
        // } catch (Exception e) {
        //     // log
        // }
        CompletableFuture.runAsync(() -> {
            try {
                JsonStorage.saveToFile(file, prices);
            } catch (IOException e) {
                // plugin.getLogger().severe("Erreur lors de la sauvegarde des prix : " + e.getMessage());
            }
        });
    }

    public DynamicPrice get(String key) {
        return prices.get(key);
    }

    public void set(String key, DynamicPrice price) {
        prices.put(key, price);
    }

    public Map<String, DynamicPrice> getAll() {
        return prices;
    }

    public void remove(String key) {
        prices.remove(key);
        save(); // Sauvegarde après suppression
    }
}