package com.asmus.vfarmer.manager;

import com.asmus.vfarmer.VFarmer;
import com.asmus.vfarmer.util.ColorUtil;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.util.*;

/**
 * Manages multi-language support from lang/*.yml files.
 */
public class LanguageManager {

    private final VFarmer plugin;
    private final Map<String, FileConfiguration> langFiles;
    private final Map<String, FileConfiguration> defaultLangFiles;
    private String currentLang;
    private final String defaultLang = "en";

    public LanguageManager(VFarmer plugin) {
        this.plugin = plugin;
        this.langFiles = new HashMap<>();
        this.defaultLangFiles = new HashMap<>();
    }

    public void load() {
        currentLang = plugin.getConfig().getString("language", "en");

        // Save default lang files
        String[] languages = {"en", "tr", "de"};
        for (String lang : languages) {
            saveDefaultLang(lang);
        }

        // Load all lang files
        File langDir = new File(plugin.getDataFolder(), "lang");
        if (langDir.exists() && langDir.isDirectory()) {
            File[] files = langDir.listFiles((dir, name) -> name.endsWith(".yml"));
            if (files != null) {
                for (File file : files) {
                    String lang = file.getName().replace(".yml", "");
                    langFiles.put(lang, YamlConfiguration.loadConfiguration(file));
                }
            }
        }

        // Load embedded defaults for fallback
        for (String lang : languages) {
            InputStream is = plugin.getResource("lang/" + lang + ".yml");
            if (is != null) {
                defaultLangFiles.put(lang, YamlConfiguration.loadConfiguration(new InputStreamReader(is)));
            }
        }

        plugin.getLogger().info("Language loaded: " + currentLang);
    }

    private void saveDefaultLang(String lang) {
        File langDir = new File(plugin.getDataFolder(), "lang");
        File langFile = new File(langDir, lang + ".yml");
        if (!langFile.exists()) {
            try {
                InputStream is = plugin.getResource("lang/" + lang + ".yml");
                if (is != null) {
                    langDir.mkdirs();
                    java.nio.file.Files.copy(is, langFile.toPath());
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to save language file: " + lang + ".yml");
            }
        }
    }

    /**
     * Gets a translated message with color codes applied.
     */
    public String getMessage(String path) {
        String msg = getRaw(path);
        // Replace %prefix% placeholder
        if (msg.contains("%prefix%")) {
            String prefix = getRaw("messages.prefix");
            msg = msg.replace("%prefix%", prefix);
        }
        return ColorUtil.colorize(msg);
    }

    /**
     * Gets a raw (uncolored) string from the lang file.
     */
    public String getRaw(String path) {
        FileConfiguration config = langFiles.get(currentLang);
        if (config != null && config.contains(path)) {
            return config.getString(path, path);
        }
        // Fallback to default lang
        FileConfiguration def = langFiles.get(defaultLang);
        if (def != null && def.contains(path)) {
            return def.getString(path, path);
        }
        // Fallback to embedded defaults
        FileConfiguration embedded = defaultLangFiles.get(currentLang);
        if (embedded != null && embedded.contains(path)) {
            return embedded.getString(path, path);
        }
        return path;
    }

    /**
     * Gets a list of strings from the lang file.
     */
    public List<String> getMessageList(String path) {
        FileConfiguration config = langFiles.get(currentLang);
        List<String> list = null;
        if (config != null) {
            list = config.getStringList(path);
        }
        if (list == null || list.isEmpty()) {
            FileConfiguration def = langFiles.get(defaultLang);
            if (def != null) {
                list = def.getStringList(path);
            }
        }
        if (list == null) return Collections.emptyList();
        List<String> colored = new ArrayList<>();
        for (String s : list) {
            colored.add(ColorUtil.colorize(s));
        }
        return colored;
    }

    /**
     * Gets an integer from the lang file.
     */
    public int getInt(String path, int def) {
        FileConfiguration config = langFiles.get(currentLang);
        if (config != null && config.contains(path)) {
            return config.getInt(path, def);
        }
        return def;
    }

    /**
     * Gets a list of integers.
     */
    public List<Integer> getIntList(String path) {
        FileConfiguration config = langFiles.get(currentLang);
        if (config != null) {
            return config.getIntegerList(path);
        }
        return Collections.emptyList();
    }

    public String getCurrentLang() {
        return currentLang;
    }
}
