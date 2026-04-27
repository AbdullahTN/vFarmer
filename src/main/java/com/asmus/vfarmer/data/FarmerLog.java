package com.asmus.vfarmer.data;

import org.bukkit.Material;

/**
 * Represents a log entry for farmer actions.
 */
public class FarmerLog {

    private final long timestamp;
    private final String user;
    private final String action;
    private final String detail;
    private final Material material;

    public FarmerLog(String user, String action, String detail, Material material) {
        this.timestamp = System.currentTimeMillis();
        this.user = user;
        this.action = action;
        this.detail = detail;
        this.material = material;
    }

    public FarmerLog(long timestamp, String user, String action, String detail, Material material) {
        this.timestamp = timestamp;
        this.user = user;
        this.action = action;
        this.detail = detail;
        this.material = material;
    }

    public FarmerLog(String user, String action, String detail) {
        this(user, action, detail, null);
    }

    public long getTimestamp() { return timestamp; }
    public String getUser() { return user; }
    public String getAction() { return action; }
    public String getDetail() { return detail; }
    public Material getMaterial() { return material; }
}
