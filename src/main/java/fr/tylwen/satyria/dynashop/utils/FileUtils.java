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
