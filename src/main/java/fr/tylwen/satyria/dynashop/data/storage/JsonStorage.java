package fr.tylwen.satyria.dynashop.data.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
// import com.google.gson.reflect.TypeToken;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.*;
import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class JsonStorage {
    private static final Gson gson = createGson();
    
    private static Gson createGson() {
        return new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .create();
    }

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
    
    /**
     * Adaptateur personnalisé pour sérialiser/désérialiser LocalDateTime
     */
    private static class LocalDateTimeAdapter extends TypeAdapter<LocalDateTime> {
        private static final DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

        @Override
        public void write(JsonWriter out, LocalDateTime value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(formatter.format(value));
            }
        }

        @Override
        public LocalDateTime read(JsonReader in) throws IOException {
            String dateString = in.nextString();
            if (dateString == null || dateString.isEmpty()) {
                return null;
            }
            return LocalDateTime.parse(dateString, formatter);
        }
    }
}