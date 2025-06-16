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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import fr.tylwen.satyria.dynashop.DynaShopPlugin;

public class FileUtils {
    
    public static void copy(InputStream inputStream, File file) {
        try {
            FileOutputStream fileOutputStream = new FileOutputStream(file);
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                fileOutputStream.write(buffer, 0, length);
            }
            fileOutputStream.close();
            inputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void mkdir(File file) {
        if (!file.exists()) {
            file.mkdir();
        }
    }

    public static File loadFile(String name) {
        // File file = new File(name);
        // if (!file.exists()) {
        //     try {
        //         file.createNewFile();
        //     } catch (IOException e) {
        //         e.printStackTrace();
        //     }
        // }
        // return file;
        if (!DynaShopPlugin.getInstance().getDataFolder().exists()) {
            mkdir(DynaShopPlugin.getInstance().getDataFolder());
        }
        File file = new File(DynaShopPlugin.getInstance().getDataFolder(), name);
        if (!file.exists()) {
            copy(DynaShopPlugin.getInstance().getResource(name), file);
        }
        return file;
    }

}
