package com.asmus.vfarmer.data;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores per-product storage data for a farmer.
 */
public class ProductData {

    private double amount;
    private int level;
    private boolean selling;
    private boolean collectEnabled;
    private boolean favorite;

    // Production tracking
    private final Map<Long, Double> productionHistory; // minute -> amount produced
    private long lastCollectTime;

    public ProductData(double amount, int level, boolean selling, boolean collectEnabled) {
        this.amount = amount;
        this.level = level;
        this.selling = selling;
        this.collectEnabled = collectEnabled;
        this.favorite = false;
        this.productionHistory = new ConcurrentHashMap<>();
        this.lastCollectTime = System.currentTimeMillis();
    }

    public ProductData() {
        this(0, 0, true, true);
    }

    // Amount
    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = Math.max(0, amount); }
    public void addAmount(double amount) { this.amount += amount; }
    public void removeAmount(double amount) { this.amount = Math.max(0, this.amount - amount); }

    // Level
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }

    // Selling
    public boolean isSelling() { return selling; }
    public void setSelling(boolean selling) { this.selling = selling; }

    // Collect
    public boolean isCollectEnabled() { return collectEnabled; }
    public void setCollectEnabled(boolean enabled) { this.collectEnabled = enabled; }

    // Favorite
    public boolean isFavorite() { return favorite; }
    public void setFavorite(boolean favorite) { this.favorite = favorite; }

    // Production tracking
    public long getLastCollectTime() { return lastCollectTime; }
    public void setLastCollectTime(long t) { this.lastCollectTime = t; }

    public void recordProduction(double amount) {
        long minuteKey = System.currentTimeMillis() / 60000;
        productionHistory.merge(minuteKey, amount, Double::sum);
        // Clean old entries (keep last 24h)
        long cutoff = minuteKey - 1440;
        productionHistory.keySet().removeIf(k -> k < cutoff);
    }

    /**
     * Gets production rate per minute over the last N minutes.
     */
    public double getProductionPerMinute(int minutes) {
        long now = System.currentTimeMillis() / 60000;
        double total = 0;
        int count = 0;
        for (int i = 1; i <= minutes; i++) {
            Double val = productionHistory.get(now - i);
            if (val != null) {
                total += val;
                count++;
            }
        }
        return count > 0 ? total / count : 0;
    }

    public double getProductionPerHour() {
        return getProductionPerMinute(60) * 60;
    }

    public double getProductionPerDay() {
        return getProductionPerMinute(1440) * 1440;
    }

    // Prediction
    public double getPredictedPerMinute() {
        return getProductionPerMinute(10);
    }

    public double getPredictedPerHour() {
        return getPredictedPerMinute() * 60;
    }

    public double getPredictedPerDay() {
        return getPredictedPerMinute() * 1440;
    }
}
