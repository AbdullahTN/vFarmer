package com.asmus.vfarmer.data;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Villager;

import java.util.*;

/**
 * Represents a farmer entity tied to an island/claim.
 */
public class Farmer {

    public enum FilterMode {
        CLASSIC,        // Tüm ürünler
        FAVORITES,      // Sadece favoriler
        IN_STOCK,       // Depoda olanlar (amount > 0)
        COLLECTING,     // Toplama açık olanlar
        NOT_COLLECTING, // Toplama kapalı olanlar
        SELLING,        // Satış açık olanlar
        NOT_SELLING     // Satış kapalı olanlar
    }

    private final UUID islandId;
    private final UUID ownerId;
    private UUID entityId;
    private Location location;
    private final Map<String, ProductData> storage; // Material name -> ProductData
    private UUID hologramId;
    private UUID textDisplayId;

    // Auto-sell
    private boolean autoSellActive;
    private long autoSellExpiration;
    private double balance;
    private double lifetimeEarnings;

    // States
    private boolean collecting;
    private boolean isBaby;
    private Villager.Type villagerType;
    private boolean isGlowing;
    private boolean collectPlayerDrops;
    private boolean killSpawnerMobs;
    private boolean autoHarvest;
    private boolean showHologram;
    private int vacuumRadius; // Purchased radius (persists even when toggled off)
    private boolean vacuumEnabled; // Whether vacuum is actively running

    // NPC Skin
    private String npcSkin;           // Player name or texture for NPC skin (null = default villager)
    private String npcParticle;       // Particle effect name (null = none)
    private boolean npcLookAtPlayer;  // Whether NPC turns to face nearest player

    // XP
    private long storedXP;
    private boolean collectXP;
    private long totalCollected; // Total items ever collected (for farmer leveling)
    private int cachedLevel;     // Cached level to detect level-ups

    // Timestamps
    private long lastMoveCooldown;
    private long lastAutoSellCooldown;
    private long lastAgeCooldown;

    // Filter
    private FilterMode filterMode;

    // Logs
    private LinkedList<FarmerLog> logs;

    public Farmer(UUID islandId, UUID ownerId, Location location) {
        this.islandId = islandId;
        this.ownerId = ownerId;
        this.location = location;
        this.storage = new LinkedHashMap<>();
        this.collecting = true;
        this.isBaby = false;
        this.villagerType = Villager.Type.PLAINS;
        this.isGlowing = false;
        this.collectPlayerDrops = false;
        this.killSpawnerMobs = false;
        this.autoHarvest = false;
        this.showHologram = true;
        this.vacuumRadius = 0;
        this.vacuumEnabled = true;
        this.npcSkin = null;
        this.npcParticle = null;
        this.npcLookAtPlayer = true;
        this.autoSellActive = false;
        this.autoSellExpiration = 0;
        this.balance = 0;
        this.lifetimeEarnings = 0;
        this.storedXP = 0;
        this.collectXP = true;
        this.totalCollected = 0;
        this.cachedLevel = 1;
        this.lastMoveCooldown = 0;
        this.lastAutoSellCooldown = 0;
        this.lastAgeCooldown = 0;
        this.filterMode = FilterMode.CLASSIC;
        this.logs = new LinkedList<>();
    }

    // ============= Getters & Setters =============
    public UUID getIslandId() { return islandId; }
    public UUID getOwnerId() { return ownerId; }

    public UUID getEntityId() { return entityId; }
    public void setEntityId(UUID entityId) { this.entityId = entityId; }

    public Location getLocation() { return location; }
    public void setLocation(Location location) { this.location = location; }

    public Map<String, ProductData> getStorage() { return storage; }

    public ProductData getProductData(String materialName) {
        return storage.computeIfAbsent(materialName, k -> new ProductData(0, 0, true, true));
    }

    public ProductData getProductData(Material material) {
        return getProductData(material.name());
    }

    // Auto-sell
    public boolean isAutoSellActive() { return autoSellActive; }
    public void setAutoSellActive(boolean active) { this.autoSellActive = active; }
    public long getAutoSellExpiration() { return autoSellExpiration; }
    public void setAutoSellExpiration(long exp) { this.autoSellExpiration = exp; }
    public boolean hasAutoSellTime() { return autoSellExpiration > System.currentTimeMillis(); }
    public double getBalance() { return balance; }
    public void setBalance(double balance) { this.balance = balance; }
    public void addBalance(double amount) { this.balance += amount; }
    public double getLifetimeEarnings() { return lifetimeEarnings; }
    public void setLifetimeEarnings(double v) { this.lifetimeEarnings = v; }
    public void addLifetimeEarnings(double amount) { this.lifetimeEarnings += amount; }

    // States
    public boolean isCollecting() { return collecting; }
    public void setCollecting(boolean collecting) { this.collecting = collecting; }
    public boolean isBaby() { return isBaby; }
    public void setBaby(boolean baby) { isBaby = baby; }
    public Villager.Type getVillagerType() { return villagerType; }
    public void setVillagerType(Villager.Type type) { this.villagerType = type; }
    public boolean isGlowing() { return isGlowing; }
    public void setGlowing(boolean glowing) { isGlowing = glowing; }
    public boolean isCollectPlayerDrops() { return collectPlayerDrops; }
    public void setCollectPlayerDrops(boolean v) { collectPlayerDrops = v; }
    public boolean isKillSpawnerMobs() { return killSpawnerMobs; }
    public void setKillSpawnerMobs(boolean v) { killSpawnerMobs = v; }
    public boolean isAutoHarvest() { return autoHarvest; }
    public void setAutoHarvest(boolean v) { autoHarvest = v; }
    public boolean isShowHologram() { return showHologram; }
    public void setShowHologram(boolean v) { showHologram = v; }
    public int getVacuumRadius() { return vacuumRadius; }
    public void setVacuumRadius(int r) { vacuumRadius = r; }
    public boolean isVacuumEnabled() { return vacuumEnabled && vacuumRadius > 0; }
    public void setVacuumEnabled(boolean v) { vacuumEnabled = v; }
    public String getNpcSkin() { return npcSkin; }
    public void setNpcSkin(String skin) { this.npcSkin = skin; }
    public boolean hasNpcSkin() { return npcSkin != null && !npcSkin.isEmpty(); }
    public String getNpcParticle() { return npcParticle; }
    public void setNpcParticle(String p) { this.npcParticle = p; }
    public boolean isNpcLookAtPlayer() { return npcLookAtPlayer; }
    public void setNpcLookAtPlayer(boolean v) { this.npcLookAtPlayer = v; }

    // Hologram
    public UUID getHologramId() { return hologramId; }
    public void setHologramId(UUID id) { hologramId = id; }
    public UUID getTextDisplayId() { return textDisplayId; }
    public void setTextDisplayId(UUID id) { textDisplayId = id; }

    // XP
    public long getStoredXP() { return storedXP; }
    public void setStoredXP(long xp) { this.storedXP = xp; }
    public void addXP(long xp) { this.storedXP += xp; }
    public boolean isCollectXP() { return collectXP; }
    public void setCollectXP(boolean v) { this.collectXP = v; }
    public long getTotalCollected() { return totalCollected; }
    public void setTotalCollected(long v) { this.totalCollected = v; }
    public void addTotalCollected(long amount) { this.totalCollected += amount; }
    public int getCachedLevel() { return cachedLevel; }
    public void setCachedLevel(int v) { this.cachedLevel = v; }

    // Cooldowns
    public long getLastMoveCooldown() { return lastMoveCooldown; }
    public void setLastMoveCooldown(long t) { lastMoveCooldown = t; }
    public long getLastAutoSellCooldown() { return lastAutoSellCooldown; }
    public void setLastAutoSellCooldown(long t) { lastAutoSellCooldown = t; }
    public long getLastAgeCooldown() { return lastAgeCooldown; }
    public void setLastAgeCooldown(long t) { lastAgeCooldown = t; }

    // Filter
    public FilterMode getFilterMode() { return filterMode; }
    public void setFilterMode(FilterMode mode) { filterMode = mode; }

    // Logs
    public LinkedList<FarmerLog> getLogs() { return logs; }
    public void setLogs(LinkedList<FarmerLog> logs) { this.logs = logs; }
    public void addLog(FarmerLog log) {
        logs.addFirst(log);
        if (logs.size() > 100) logs.removeLast();
    }
}
