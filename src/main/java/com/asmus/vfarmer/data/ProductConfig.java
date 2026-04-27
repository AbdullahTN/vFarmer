package com.asmus.vfarmer.data;

import org.bukkit.Material;
import java.util.Map;

/**
 * Stores product configuration from products.yml (price, default-limit, levels).
 */
public class ProductConfig {

    private final Material material;
    private final double price;
    private final long defaultLimit;
    private final Map<Integer, LevelConfig> levels;

    public ProductConfig(Material material, double price, long defaultLimit, Map<Integer, LevelConfig> levels) {
        this.material = material;
        this.price = price;
        this.defaultLimit = defaultLimit;
        this.levels = levels;
    }

    public Material getMaterial() { return material; }
    public double getPrice() { return price; }

    /**
     * Gets the storage limit for a given level.
     */
    public long getLimit(int level) {
        if (level <= 0) return defaultLimit;
        LevelConfig lc = levels.get(level);
        return lc != null ? lc.limit : defaultLimit;
    }

    /**
     * Gets the upgrade cost for a given level.
     */
    public double getCost(int level) {
        LevelConfig lc = levels.get(level);
        return lc != null ? lc.cost : 0;
    }

    /**
     * Checks if a level exists.
     */
    public boolean hasLevel(int level) {
        return levels.containsKey(level);
    }

    /**
     * Returns the max level available.
     */
    public int getMaxLevel() {
        return levels.isEmpty() ? 0 : levels.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
    }

    /**
     * Level configuration (cost and limit).
     */
    public static class LevelConfig {
        public final double cost;
        public final long limit;

        public LevelConfig(double cost, long limit) {
            this.cost = cost;
            this.limit = limit;
        }
    }
}
