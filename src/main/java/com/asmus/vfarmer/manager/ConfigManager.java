package com.asmus.vfarmer.manager;

import com.asmus.vfarmer.VFarmer;
import com.asmus.vfarmer.data.ProductConfig;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

/**
 * Manages product configurations from products.yml.
 */
public class ConfigManager {

    private final VFarmer plugin;
    private final Map<Material, ProductConfig> products;
    private FileConfiguration productsConfig;

    public ConfigManager(VFarmer plugin) {
        this.plugin = plugin;
        this.products = new LinkedHashMap<>();
    }

    public void load() {
        products.clear();
        File file = new File(plugin.getDataFolder(), "products.yml");
        if (!file.exists()) {
            plugin.saveResource("products.yml", false);
        }
        productsConfig = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection productsSection = productsConfig.getConfigurationSection("products");
        if (productsSection == null) {
            plugin.getLogger().warning("No products section found in products.yml!");
            return;
        }

        Set<String> processedMaterials = new HashSet<>();
        for (String key : productsSection.getKeys(false)) {
            Material material = Material.matchMaterial(key);
            if (material == null) {
                plugin.getLogger().warning("Invalid material in products.yml: " + key);
                continue;
            }

            // YAML allows duplicate keys - last one wins
            String materialKey = material.name();
            if (processedMaterials.contains(materialKey)) {
                // Overwrite with new values
                products.remove(material);
            }
            processedMaterials.add(materialKey);

            ConfigurationSection section = productsSection.getConfigurationSection(key);
            if (section == null) continue;

            double price = section.getDouble("price", 0);
            long defaultLimit = section.getLong("default-limit", 2500);

            Map<Integer, ProductConfig.LevelConfig> levels = new LinkedHashMap<>();
            ConfigurationSection levelsSection = section.getConfigurationSection("levels");
            if (levelsSection != null) {
                for (String levelKey : levelsSection.getKeys(false)) {
                    try {
                        int level = Integer.parseInt(levelKey);
                        ConfigurationSection levelSection = levelsSection.getConfigurationSection(levelKey);
                        if (levelSection != null) {
                            double cost = levelSection.getDouble("cost", 0);
                            long limit = levelSection.getLong("limit", defaultLimit);
                            levels.put(level, new ProductConfig.LevelConfig(cost, limit));
                        }
                    } catch (NumberFormatException e) {
                        plugin.getLogger().warning("Invalid level number in products.yml for " + key + ": " + levelKey);
                    }
                }
            }

            products.put(material, new ProductConfig(material, price, defaultLimit, levels));
        }

        plugin.getLogger().info("Loaded " + products.size() + " products from products.yml");
    }

    public ProductConfig getProduct(Material material) {
        return products.get(material);
    }

    public Map<Material, ProductConfig> getProducts() {
        return Collections.unmodifiableMap(products);
    }

    public boolean isProduct(Material material) {
        return products.containsKey(material);
    }
}
