package fr.tylwen.satyria.dynashop.utils;

// import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.util.LinkedHashMap;
import java.util.Map;

public class MaterialTranslationMerger {
    public static void main(String[] args) throws IOException {
        File enFile = new File("translations_en.yml");
        File frFile = new File("translations_fr.yml");
        File outFile = new File("translations_fr_merged.yml");
        
        // DumperOptions options = new DumperOptions();
        // options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK); // Important pour le format plat
        // options.setPrettyFlow(false);
        // options.setIndent(2);
        // options.setDefaultScalarStyle(DumperOptions.ScalarStyle.DOUBLE_QUOTED);

        // Yaml yaml = new Yaml(options);
        Yaml yaml = new Yaml();
        Map<String, String> enMap;
        Map<String, String> frMap;

        try (InputStream enIn = new FileInputStream(enFile)) {
            enMap = yaml.load(enIn);
        }
        try (InputStream frIn = new FileInputStream(frFile)) {
            frMap = yaml.load(frIn);
        }
        
        // Création d'une map insensible à la casse pour le FR
        Map<String, String> frMapLower = new LinkedHashMap<>();
        if (frMap != null) {
            for (Map.Entry<String, String> entry : frMap.entrySet()) {
                frMapLower.put(entry.getKey().toLowerCase(), entry.getValue());
            }
        }

        Map<String, String> merged = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : enMap.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            String frValue = frMapLower.get(key.toLowerCase());
            if (frValue != null && !frValue.isEmpty()) {
                merged.put(key, frValue);
            } else {
                merged.put(key, value);
            }
        }

        // try (Writer writer = new FileWriter(outFile)) {
        //     yaml.dump(merged, writer);
        // }
        try (PrintWriter writer = new PrintWriter(new FileWriter(outFile))) {
            for (Map.Entry<String, String> entry : merged.entrySet()) {
                writer.println(entry.getKey() + ": \"" + entry.getValue().replace("\"", "\\\"") + "\"");
            }
        }
        System.out.println("Fichier translations_fr_merged.yml généré !");
    }
}