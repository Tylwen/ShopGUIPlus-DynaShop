package fr.tylwen.satyria.dynashop.data.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;

public class JsonStorage {
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static <T> void saveToFile(File file, T data) throws IOException {
        try (Writer writer = new FileWriter(file)) {
            gson.toJson(data, writer);
        }
    }

    public static <T> T loadFromFile(File file, Type type, T defaultValue) throws IOException {
        if (!file.exists()) return defaultValue;
        try (Reader reader = new FileReader(file)) {
            T data = gson.fromJson(reader, type);
            return data != null ? data : defaultValue;
        }
    }
}