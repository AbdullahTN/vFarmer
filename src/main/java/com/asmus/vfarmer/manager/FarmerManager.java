package com.asmus.vfarmer.manager;

import com.asmus.vfarmer.VFarmer;
import com.asmus.vfarmer.data.*;
import com.asmus.vfarmer.util.ColorUtil;
import com.asmus.vfarmer.util.Formatter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Type;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Core manager for all farmer operations.
 */
public class FarmerManager {

    private final VFarmer plugin;
    private final Map<UUID, Farmer> farmersByIsland; // islandUUID -> Farmer
    private final Map<UUID, Farmer> farmersByEntity;  // entityUUID -> Farmer
    private final Map<Long, UUID> chunkToIsland;      // chunkKey -> islandUUID (cache)
    private final Map<Long, UUID> farmerChunks;       // chunkKey -> islandUUID (farmers' chunks for fast ChunkLoad)
    private final Map<UUID, BossBar> autoSellBars;
    private final Map<UUID, BukkitTask> autoSellTasks;
    private final Set<UUID> bypassPlayers;
    private final Set<UUID> dirtyFarmers;             // Farmers that need saving
    private final Gson gson;
    private boolean ready;

    public FarmerManager(VFarmer plugin) {
        this.plugin = plugin;
        this.farmersByIsland = new ConcurrentHashMap<>();
        this.farmersByEntity = new ConcurrentHashMap<>();
        this.chunkToIsland = new ConcurrentHashMap<>();
        this.farmerChunks = new ConcurrentHashMap<>();
        this.autoSellBars = new ConcurrentHashMap<>();
        this.autoSellTasks = new ConcurrentHashMap<>();
        this.bypassPlayers = ConcurrentHashMap.newKeySet();
        this.dirtyFarmers = ConcurrentHashMap.newKeySet();
        this.gson = new GsonBuilder().create();
        this.ready = false;
    }

    // ==================== Chunk Cache ====================

    /**
     * Gets the island UUID at a location using a chunk-level cache.
     * First check: O(1) ConcurrentHashMap lookup.
     * Cache miss: call IslandHook (reflection) and cache the result.
     * Cache TTL is managed by periodic cleanup.
     */
    public UUID getIslandIdCached(Location loc) {
        long chunkKey = chunkKey(loc);
        UUID cached = chunkToIsland.get(chunkKey);
        if (cached != null) return cached;

        // Cache miss - do the expensive lookup
        UUID islandId = plugin.getIslandHook().getIslandId(loc);
        if (islandId != null) {
            chunkToIsland.put(chunkKey, islandId);
        }
        return islandId;
    }

    /**
     * Registers a farmer's chunk for fast ChunkLoad lookup.
     */
    private void registerFarmerChunk(Farmer farmer) {
        Location loc = farmer.getLocation();
        if (loc != null && loc.getWorld() != null) {
            farmerChunks.put(chunkKey(loc), farmer.getIslandId());
        }
    }

    private void unregisterFarmerChunk(Farmer farmer) {
        Location loc = farmer.getLocation();
        if (loc != null && loc.getWorld() != null) {
            farmerChunks.remove(chunkKey(loc));
        }
    }

    /**
     * Fast check: is there a farmer in this chunk?
     */
    public UUID getFarmerIslandInChunk(long chunkKey) {
        return farmerChunks.get(chunkKey);
    }

    /** Packs world UUID + chunk X/Z into a single long key. */
    public static long chunkKey(Location loc) {
        return chunkKey(loc.getWorld().getUID().getMostSignificantBits(),
                loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
    }

    public static long chunkKey(long worldBits, int chunkX, int chunkZ) {
        // Mix world bits into the key to avoid cross-world collisions
        return (worldBits & 0xFFFF_0000_0000L) | ((long)(chunkX & 0xFFFF) << 16) | (chunkZ & 0xFFFFL);
    }

    public static long chunkKeyFromChunk(org.bukkit.Chunk chunk) {
        return chunkKey(chunk.getWorld().getUID().getMostSignificantBits(),
                chunk.getX(), chunk.getZ());
    }

    /**
     * Periodic cache cleanup — removes stale entries every 10 minutes.
     */
    private void startCacheCleanup() {
        new BukkitRunnable() {
            @Override
            public void run() {
                // Keep cache under control — remove entries for unloaded chunks
                if (chunkToIsland.size() > 10000) {
                    chunkToIsland.clear(); // Nuclear option for memory safety
                    plugin.getLogger().info("Chunk cache cleared (size exceeded 10000).");
                }
            }
        }.runTaskTimerAsynchronously(plugin, 12000L, 12000L); // Every 10 min
    }

    // ==================== Loading & Saving ====================

    public void loadAllFarmers() {
        try {
            ResultSet rs = plugin.getDatabaseManager().getAllFarmers();
            if (rs == null) return;
            int count = 0;
            while (rs.next()) {
                try {
                    UUID islandId = UUID.fromString(rs.getString("island_uuid"));
                    UUID ownerId = UUID.fromString(rs.getString("owner_uuid"));
                    String worldName = rs.getString("world");
                    World world = Bukkit.getWorld(worldName);
                    if (world == null) continue;

                    Location loc = new Location(world,
                            rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
                            rs.getFloat("yaw"), rs.getFloat("pitch"));

                    Farmer farmer = new Farmer(islandId, ownerId, loc);

                    // Load storage
                    String storageJson = rs.getString("storage");
                    if (storageJson != null && !storageJson.isEmpty()) {
                        Type type = new TypeToken<Map<String, ProductData>>(){}.getType();
                        Map<String, ProductData> storage = gson.fromJson(storageJson, type);
                        if (storage != null) {
                            farmer.getStorage().putAll(storage);
                        }
                    }

                    // Load states
                    farmer.setAutoSellActive(rs.getBoolean("autosell_active"));
                    farmer.setAutoSellExpiration(rs.getLong("autosell_expiration"));
                    farmer.setBalance(rs.getDouble("balance"));
                    farmer.setCollecting(rs.getBoolean("collecting"));
                    farmer.setBaby(rs.getBoolean("is_baby"));
                    try {
                        farmer.setVillagerType(Villager.Type.valueOf(rs.getString("villager_type")));
                    } catch (Exception e) {
                        farmer.setVillagerType(Villager.Type.PLAINS);
                    }
                    farmer.setGlowing(rs.getBoolean("is_glowing"));
                    farmer.setCollectPlayerDrops(rs.getBoolean("collect_player_drops"));
                    farmer.setKillSpawnerMobs(rs.getBoolean("kill_spawner_mobs"));
                    farmer.setAutoHarvest(rs.getBoolean("auto_harvest"));
                    try { farmer.setShowHologram(rs.getBoolean("show_hologram")); } catch (Exception e) { farmer.setShowHologram(true); }
                    try { farmer.setStoredXP(rs.getLong("xp")); } catch (Exception e) {}
                    try { farmer.setLifetimeEarnings(rs.getDouble("lifetime_earnings")); } catch (Exception e) {}
                    try { farmer.setCollectXP(rs.getBoolean("collect_xp")); } catch (Exception e) { farmer.setCollectXP(true); }
                    try { farmer.setVacuumRadius(rs.getInt("vacuum_radius")); } catch (Exception e) { farmer.setVacuumRadius(0); }
                    try { farmer.setTotalCollected(rs.getLong("total_collected")); } catch (Exception e) { farmer.setTotalCollected(0); }

                    // Load NPC skin data
                    try {
                        String skin = rs.getString("npc_skin");
                        if (skin != null && !skin.isEmpty()) farmer.setNpcSkin(skin);
                    } catch (Exception e) {}
                    try {
                        String particle = rs.getString("npc_particle");
                        if (particle != null && !particle.isEmpty()) farmer.setNpcParticle(particle);
                    } catch (Exception e) {}
                    try { farmer.setNpcLookAtPlayer(rs.getBoolean("npc_look_at")); } catch (Exception e) { farmer.setNpcLookAtPlayer(true); }

                    // Migrate: if totalCollected is 0 but storage has items, count existing stock
                    if (farmer.getTotalCollected() == 0) {
                        long existing = 0;
                        for (ProductData pd : farmer.getStorage().values()) {
                            existing += (long) pd.getAmount();
                        }
                        if (existing > 0) {
                            farmer.setTotalCollected(existing);
                        }
                    }

                    // Set cached level
                    if (plugin.getFarmerLevelManager() != null) {
                        farmer.setCachedLevel(plugin.getFarmerLevelManager().getLevel(farmer));
                    }

                    // Load logs
                    String logsJson = rs.getString("logs");
                    if (logsJson != null && !logsJson.isEmpty()) {
                        Type logType = new TypeToken<LinkedList<FarmerLog>>(){}.getType();
                        LinkedList<FarmerLog> logs = gson.fromJson(logsJson, logType);
                        if (logs != null) farmer.setLogs(logs);
                    }

                    farmersByIsland.put(islandId, farmer);
                    registerFarmerChunk(farmer);
                    spawnVillager(farmer);

                    // Start auto-sell if active
                    if (farmer.isAutoSellActive() && farmer.hasAutoSellTime()) {
                        startAutoSell(farmer);
                    }

                    count++;
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Failed to load a farmer", e);
                }
            }
            rs.close();
            ready = true;
            plugin.getLogger().info("Loaded " + count + " farmers.");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load farmers from database", e);
        }
    }

    public void saveFarmer(Farmer farmer) {
        dirtyFarmers.add(farmer.getIslandId());
    }

    /**
     * Actually writes a farmer to the database (called from async thread).
     */
    private void doSaveFarmer(Farmer farmer) {
        Location loc = farmer.getLocation();
        if (loc == null || loc.getWorld() == null) return;

        String storageJson = gson.toJson(farmer.getStorage());
        String logsJson = gson.toJson(farmer.getLogs());

        plugin.getDatabaseManager().saveFarmer(
                farmer.getIslandId().toString(),
                farmer.getOwnerId().toString(),
                loc.getWorld().getName(),
                loc.getX(), loc.getY(), loc.getZ(),
                loc.getYaw(), loc.getPitch(),
                storageJson,
                farmer.isAutoSellActive(),
                farmer.getAutoSellExpiration(),
                farmer.getBalance(),
                farmer.isCollecting(),
                farmer.isBaby(),
                farmer.getVillagerType().name(),
                farmer.isGlowing(),
                farmer.isCollectPlayerDrops(),
                farmer.isKillSpawnerMobs(),
                farmer.isAutoHarvest(),
                farmer.isShowHologram(),
                farmer.getStoredXP(),
                logsJson,
                farmer.getLifetimeEarnings(),
                farmer.isCollectXP(),
                farmer.getVacuumRadius(),
                farmer.getTotalCollected(),
                farmer.getNpcSkin(),
                farmer.getNpcParticle(),
                farmer.isNpcLookAtPlayer()
        );
    }

    public void startAutoSave() {
        // Async dirty-save: only save farmers that changed
        new BukkitRunnable() {
            @Override
            public void run() {
                if (dirtyFarmers.isEmpty()) return;
                Set<UUID> toSave = new HashSet<>(dirtyFarmers);
                dirtyFarmers.clear();
                for (UUID id : toSave) {
                    Farmer farmer = farmersByIsland.get(id);
                    if (farmer != null) {
                        doSaveFarmer(farmer);
                    }
                }
            }
        }.runTaskTimerAsynchronously(plugin, 6000L, 6000L); // Every 5 minutes

        startCacheCleanup();
    }

    /**
     * Saves all farmers synchronously (used on shutdown).
     */
    public void saveAllFarmers() {
        for (Farmer farmer : farmersByIsland.values()) {
            doSaveFarmer(farmer);
        }
        dirtyFarmers.clear();
    }

    // ==================== Villager Management ====================

    public void spawnVillager(Farmer farmer) {
        Location loc = farmer.getLocation();
        if (loc == null || loc.getWorld() == null) return;

        // Remove old entity if exists
        removeVillagerEntity(farmer);

        // If farmer has NPC skin and NPC hook is available, spawn NPC instead
        if (farmer.hasNpcSkin() && plugin.getNpcHook() != null && plugin.getNpcHook().isAvailable()) {
            boolean spawned = plugin.getNpcHook().spawnNPC(farmer);
            if (spawned) {
                // Spawn hologram above NPC
                if (farmer.isShowHologram()) {
                    spawnHologram(farmer);
                }
                return;
            }
            // If NPC spawn failed, fall through to vanilla villager
        }

        // Also remove NPC if skin was cleared
        if (!farmer.hasNpcSkin() && plugin.getNpcHook() != null) {
            plugin.getNpcHook().removeNPC(farmer);
        }

        loc.getWorld().spawn(loc, Villager.class, villager -> {
            villager.setAI(false);
            villager.setInvulnerable(true);
            villager.setSilent(true);
            villager.setCollidable(false);
            villager.setGravity(false);
            villager.setPersistent(true);
            villager.setRemoveWhenFarAway(false);
            villager.setVillagerType(farmer.getVillagerType());
            villager.setGlowing(farmer.isGlowing());

            if (farmer.isBaby()) {
                villager.setBaby();
            } else {
                villager.setAdult();
            }

            farmer.setEntityId(villager.getUniqueId());
            farmersByEntity.put(villager.getUniqueId(), farmer);
        });

        // Spawn hologram
        if (farmer.isShowHologram()) {
            spawnHologram(farmer);
        }
    }

    public void removeVillagerEntity(Farmer farmer) {
        // Remove NPC
        if (plugin.getNpcHook() != null) {
            plugin.getNpcHook().removeNPC(farmer);
        }
        // Remove vanilla villager entity
        if (farmer.getEntityId() != null) {
            Entity entity = Bukkit.getEntity(farmer.getEntityId());
            if (entity != null && entity.isValid()) {
                entity.remove();
            }
            farmersByEntity.remove(farmer.getEntityId());
        }
        removeHologram(farmer);
    }

    private void spawnHologram(Farmer farmer) {
        removeHologram(farmer);
        Location loc = farmer.getLocation();
        if (loc == null || loc.getWorld() == null) return;

        List<String> lines = plugin.getLanguageManager().getMessageList("farmer-hologram");
        if (lines.isEmpty()) return;

        // Position above NPC/villager
        Location hologramLoc = loc.clone().add(0, farmer.isBaby() ? 1.5 : 2.2, 0);
        StringBuilder combined = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            combined.append(lines.get(i));
            if (i < lines.size() - 1) combined.append("\n");
        }

        loc.getWorld().spawn(hologramLoc, org.bukkit.entity.TextDisplay.class, display -> {
            display.text(ColorUtil.toComponent(combined.toString()));
            display.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
            display.setShadowed(true);
            display.setAlignment(org.bukkit.entity.TextDisplay.TextAlignment.CENTER);
            display.setPersistent(true);
            display.setBackgroundColor(org.bukkit.Color.fromARGB(100, 0, 0, 0)); // Semi-transparent black background
            display.setDefaultBackground(false);
            display.setSeeThrough(false);
            display.setLineWidth(200);

            // 3D transformation — slightly larger text
            org.bukkit.util.Transformation transform = display.getTransformation();
            org.joml.Vector3f scale = new org.joml.Vector3f(1.0f, 1.0f, 1.0f);
            display.setTransformation(new org.bukkit.util.Transformation(
                    transform.getTranslation(),
                    transform.getLeftRotation(),
                    scale,
                    transform.getRightRotation()
            ));

            farmer.setTextDisplayId(display.getUniqueId());
        });
    }

    private void removeHologram(Farmer farmer) {
        if (farmer.getTextDisplayId() != null) {
            Entity entity = Bukkit.getEntity(farmer.getTextDisplayId());
            if (entity != null) entity.remove();
            farmer.setTextDisplayId(null);
        }
    }

    public void updateVillager(Farmer farmer) {
        if (farmer.getEntityId() == null) return;
        Entity entity = Bukkit.getEntity(farmer.getEntityId());
        if (entity instanceof Villager villager) {
            villager.setVillagerType(farmer.getVillagerType());
            villager.setGlowing(farmer.isGlowing());
            if (farmer.isBaby()) villager.setBaby();
            else villager.setAdult();
        }
    }

    // ==================== Farmer Operations ====================

    /**
     * Buys a farmer for the player on their island.
     */
    public void buyFarmer(Player player) {
        UUID islandId = plugin.getIslandHook().getPlayerIslandId(player);
        if (islandId == null) {
            player.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.not-on-island")));
            return;
        }

        if (farmersByIsland.containsKey(islandId)) {
            player.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.farmer-already-exists")));
            return;
        }

        double cost = plugin.getConfig().getDouble("farmer-cost", 8000.0);
        if (!plugin.hasEconomy()) {
            player.sendMessage(ColorUtil.toComponent("§cEconomy provider not found! Install EssentialsX or similar."));
            return;
        }
        if (!ecoHas(player, cost)) {
            String msg = plugin.getLanguageManager().getMessage("messages.insufficient-funds")
                    .replace("%cost%", Formatter.formatMoney(cost));
            player.sendMessage(ColorUtil.toComponent(msg));
            return;
        }

        ecoWithdraw(player, cost);

        Farmer farmer = new Farmer(islandId, player.getUniqueId(), player.getLocation());

        // Initialize product data for all configured products
        for (Material mat : plugin.getConfigManager().getProducts().keySet()) {
            farmer.getProductData(mat.name());
        }

        farmersByIsland.put(islandId, farmer);
        registerFarmerChunk(farmer);
        spawnVillager(farmer);
        saveFarmer(farmer);

        player.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.farmer-bought")));
        plugin.getWebhookManager().notifyFarmerBought(player.getName());
    }

    /**
     * Gives a farmer via admin command.
     */
    public void giveFarmer(Player player, UUID islandId, Location location) {
        if (farmersByIsland.containsKey(islandId)) return;

        Farmer farmer = new Farmer(islandId, player.getUniqueId(), location);
        for (Material mat : plugin.getConfigManager().getProducts().keySet()) {
            farmer.getProductData(mat.name());
        }

        farmersByIsland.put(islandId, farmer);
        registerFarmerChunk(farmer);
        spawnVillager(farmer);
        saveFarmer(farmer);
    }

    /**
     * Removes a farmer from an island.
     */
    public void removeFarmer(UUID islandId) {
        Farmer farmer = farmersByIsland.remove(islandId);
        if (farmer == null) return;
        unregisterFarmerChunk(farmer);
        removeVillagerEntity(farmer);
        stopAutoSell(farmer);
        plugin.getDatabaseManager().deleteFarmer(islandId.toString());
    }

    /**
     * Moves a farmer to a new location.
     */
    public boolean moveFarmer(Farmer farmer, Location newLoc) {
        UUID locIsland = plugin.getIslandHook().getIslandId(newLoc);
        if (locIsland == null || !locIsland.equals(farmer.getIslandId())) {
            return false;
        }
        farmer.setLocation(newLoc);
        removeVillagerEntity(farmer);
        spawnVillager(farmer);
        return true;
    }

    // ==================== Item Collection ====================

    /**
     * Tries to add items to a farmer's storage.
     * @return true if the item was collected
     */
    public boolean collectItem(UUID islandId, Material material, int amount) {
        Farmer farmer = farmersByIsland.get(islandId);
        if (farmer == null || !farmer.isCollecting()) return false;

        ProductConfig config = plugin.getConfigManager().getProduct(material);
        if (config == null) return false;

        ProductData data = farmer.getProductData(material);
        if (!data.isCollectEnabled()) return false;

        int level = data.getLevel();
        long limit = config.getLimit(level);
        double current = data.getAmount();

        if (current >= limit) return false;

        double toAdd = Math.min(amount, limit - current);
        data.addAmount(toAdd);
        data.recordProduction(toAdd);
        farmer.addTotalCollected((long) toAdd);
        dirtyFarmers.add(islandId);

        // Check farmer level up
        if (plugin.getFarmerLevelManager() != null) {
            // Find nearest online player on this island for notification
            Player nearest = null;
            for (Player p : Bukkit.getOnlinePlayers()) {
                UUID pIsland = plugin.getIslandHook().getPlayerIslandId(p);
                if (islandId.equals(pIsland)) { nearest = p; break; }
            }
            plugin.getFarmerLevelManager().checkLevelUp(farmer, nearest);
        }

        return true;
    }

    /**
     * Collects XP to the farmer.
     */
    public long collectXP(Farmer farmer, Material material, long xp) {
        // XP collection via ExperienceOrb
        farmer.addXP(xp);
        return xp;
    }

    // ==================== Selling ====================

    /** Safe economy check */
    private boolean hasEco() { return plugin.hasEconomy(); }
    private boolean ecoHas(Player p, double amount) { return hasEco() && plugin.getEconomy().has(p, amount); }
    private void ecoWithdraw(Player p, double amount) { if (hasEco()) plugin.getEconomy().withdrawPlayer(p, amount); }
    private void ecoDeposit(Player p, double amount) { if (hasEco()) plugin.getEconomy().depositPlayer(p, amount); }

    /**
     * Sells all products in a farmer and returns earnings.
     */
    public double sellAll(Farmer farmer, Player player) {
        if (!hasEco()) return 0;
        double totalEarnings = 0;
        double taxRate = plugin.getZeroTaxManager().getTaxRate() / 100.0;

        for (Map.Entry<String, ProductData> entry : farmer.getStorage().entrySet()) {
            Material mat = Material.matchMaterial(entry.getKey());
            if (mat == null) continue;

            ProductConfig config = plugin.getConfigManager().getProduct(mat);
            if (config == null) continue;

            ProductData data = entry.getValue();
            if (!data.isSelling() || data.getAmount() <= 0) continue;

            double earnings = data.getAmount() * config.getPrice() * plugin.getBoosterManager().getMultiplier();
            double tax = earnings * taxRate;
            double net = earnings - tax;

            totalEarnings += net;
            data.setAmount(0);
        }

        if (totalEarnings > 0) {
            ecoDeposit(player, totalEarnings);
            farmer.addLifetimeEarnings(totalEarnings);
            farmer.addLog(new FarmerLog(player.getName(),
                    plugin.getLanguageManager().getRaw("logs.sell-all"),
                    Formatter.formatMoney(totalEarnings)));
            plugin.getWebhookManager().notifySellAll(player.getName(), Formatter.formatMoney(totalEarnings));
        }

        return totalEarnings;
    }

    /**
     * Sells a single product.
     */
    public double sellProduct(Farmer farmer, Material material, ProductData data, Player player) {
        if (!hasEco()) return 0;
        ProductConfig config = plugin.getConfigManager().getProduct(material);
        if (config == null || data.getAmount() <= 0) return 0;

        double taxRate = plugin.getZeroTaxManager().getTaxRate() / 100.0;
        double earnings = data.getAmount() * config.getPrice() * plugin.getBoosterManager().getMultiplier();
        double tax = earnings * taxRate;
        double net = earnings - tax;

        ecoDeposit(player, net);
        farmer.addLifetimeEarnings(net);
        data.setAmount(0);

        farmer.addLog(new FarmerLog(player.getName(),
                plugin.getLanguageManager().getRaw("logs.product-sell"),
                material.name() + " - " + Formatter.formatMoney(net), material));

        return net;
    }

    // ==================== Auto-Sell ====================

    public void startAutoSell(Farmer farmer) {
        if (!farmer.isAutoSellActive() || !farmer.hasAutoSellTime()) return;

        UUID islandId = farmer.getIslandId();
        stopAutoSell(farmer); // Stop existing

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!farmer.isAutoSellActive() || !farmer.hasAutoSellTime()) {
                    stopAutoSell(farmer);
                    cancel();
                    return;
                }
                autoSellTick(farmer);
            }
        }.runTaskTimer(plugin, 1200L, 1200L); // Every minute

        autoSellTasks.put(islandId, task);
    }

    private void autoSellTick(Farmer farmer) {
        double taxRate = plugin.getZeroTaxManager().getTaxRate() / 100.0;
        double totalEarnings = 0;

        for (Map.Entry<String, ProductData> entry : farmer.getStorage().entrySet()) {
            Material mat = Material.matchMaterial(entry.getKey());
            if (mat == null) continue;
            ProductConfig config = plugin.getConfigManager().getProduct(mat);
            if (config == null) continue;
            ProductData data = entry.getValue();
            if (!data.isSelling() || data.getAmount() <= 0) continue;

            double earnings = data.getAmount() * config.getPrice() * plugin.getBoosterManager().getMultiplier();
            double tax = earnings * taxRate;
            double net = earnings - tax;
            totalEarnings += net;
            data.setAmount(0);
        }

        if (totalEarnings > 0) {
            farmer.addBalance(totalEarnings);
            farmer.addLifetimeEarnings(totalEarnings);
            farmer.addLog(new FarmerLog(
                    plugin.getLanguageManager().getRaw("logs.system"),
                    plugin.getLanguageManager().getRaw("logs.auto-sell"),
                    Formatter.formatMoney(totalEarnings)));
        }
    }

    public void stopAutoSell(Farmer farmer) {
        UUID islandId = farmer.getIslandId();
        BukkitTask task = autoSellTasks.remove(islandId);
        if (task != null) task.cancel();
        BossBar bar = autoSellBars.remove(islandId);
        if (bar != null) {
            Bukkit.getOnlinePlayers().forEach(p -> p.hideBossBar(bar));
        }
    }

    /**
     * Withdraws farmer balance to player.
     */
    public void withdrawBalance(Farmer farmer, Player player) {
        double balance = farmer.getBalance();
        if (balance <= 0) {
            player.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.farmer-account-empty")));
            return;
        }
        farmer.setBalance(0);
        ecoDeposit(player, balance);

        farmer.addLog(new FarmerLog(player.getName(), "Balance Withdraw", Formatter.formatMoney(balance)));

        // Notify island members
        notifyIsland(farmer, plugin.getLanguageManager().getMessage("messages.farmer-balance-withdrawn-notify")
                .replace("%player%", player.getName())
                .replace("%amount%", Formatter.formatMoney(balance)));
    }

    /**
     * Withdraws XP to player.
     */
    public void withdrawXP(Farmer farmer, Player player, long amount) {
        if (farmer.getStoredXP() <= 0) {
            player.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.xp-storage-empty")));
            return;
        }
        long toWithdraw = Math.min(amount, farmer.getStoredXP());
        farmer.setStoredXP(farmer.getStoredXP() - toWithdraw);
        player.giveExp((int) toWithdraw);

        player.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.xp-withdrawn")
                .replace("%amount%", String.valueOf(toWithdraw))));

        notifyIsland(farmer, plugin.getLanguageManager().getMessage("messages.xp-withdrawn-notify")
                .replace("%player%", player.getName())
                .replace("%amount%", String.valueOf(toWithdraw)));
    }

    /**
     * Withdraws items to player inventory.
     */
    public void withdrawItems(Farmer farmer, Player player, Material material, int amount) {
        ProductData data = farmer.getProductData(material);
        if (data.getAmount() <= 0) {
            player.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.no-items-to-withdraw")));
            return;
        }

        int toWithdraw = (int) Math.min(amount, data.getAmount());
        int remaining = toWithdraw;

        while (remaining > 0) {
            int stackSize = Math.min(remaining, material.getMaxStackSize());
            ItemStack item = new ItemStack(material, stackSize);
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
            if (!leftover.isEmpty()) {
                // Inventory full - put back what couldn't fit
                int notAdded = leftover.values().stream().mapToInt(ItemStack::getAmount).sum();
                remaining -= (stackSize - notAdded);
                break;
            }
            remaining -= stackSize;
        }

        int withdrawn = toWithdraw - remaining;
        if (withdrawn > 0) {
            data.removeAmount(withdrawn);
            player.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.item-withdrawn")
                    .replace("%amount%", String.valueOf(withdrawn))
                    .replace("%material%", getMaterialDisplayName(material))));

            farmer.addLog(new FarmerLog(player.getName(),
                    plugin.getLanguageManager().getRaw("logs.product-withdraw"),
                    withdrawn + "x " + material.name(), material));

            notifyIsland(farmer, plugin.getLanguageManager().getMessage("messages.item-withdrawn-notify")
                    .replace("%player%", player.getName())
                    .replace("%amount%", String.valueOf(withdrawn))
                    .replace("%material%", getMaterialDisplayName(material)));
        } else {
            player.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.inventory-full")));
        }
    }

    /**
     * Upgrades storage level for a product.
     */
    public boolean upgradeStorage(Farmer farmer, Material material, Player player) {
        ProductData data = farmer.getProductData(material);
        ProductConfig config = plugin.getConfigManager().getProduct(material);
        if (config == null) return false;

        int nextLevel = data.getLevel() + 1;
        if (!config.hasLevel(nextLevel)) {
            player.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.max-level")));
            return false;
        }

        double cost = config.getCost(nextLevel);
        if (!ecoHas(player, cost)) {
            player.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.insufficient-balance")
                    .replace("%cost%", Formatter.formatMoney(cost))));
            return false;
        }

        ecoWithdraw(player, cost);
        data.setLevel(nextLevel);

        player.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.storage-upgrade-success")
                .replace("%level%", String.valueOf(nextLevel))));

        return true;
    }

    /**
     * Upgrades all products to next level.
     */
    public boolean upgradeAll(Farmer farmer, Player player) {
        double totalCost = 0;
        Map<Material, Integer> upgrades = new LinkedHashMap<>();

        for (Map.Entry<String, ProductData> entry : farmer.getStorage().entrySet()) {
            Material mat = Material.matchMaterial(entry.getKey());
            if (mat == null) continue;
            ProductConfig config = plugin.getConfigManager().getProduct(mat);
            if (config == null) continue;

            int nextLevel = entry.getValue().getLevel() + 1;
            if (config.hasLevel(nextLevel)) {
                totalCost += config.getCost(nextLevel);
                upgrades.put(mat, nextLevel);
            }
        }

        if (upgrades.isEmpty()) {
            player.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.all-products-max-level")));
            return false;
        }

        if (!ecoHas(player, totalCost)) {
            player.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.bulk-upgrade-insufficient-funds")
                    .replace("%cost%", Formatter.formatMoney(totalCost))));
            return false;
        }

        ecoWithdraw(player, totalCost);
        for (Map.Entry<Material, Integer> entry : upgrades.entrySet()) {
            farmer.getProductData(entry.getKey()).setLevel(entry.getValue());
        }

        player.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.bulk-upgrade-success")
                .replace("%cost%", Formatter.formatMoney(totalCost))));

        notifyIsland(farmer, plugin.getLanguageManager().getMessage("messages.bulk-upgrade-notify")
                .replace("%player%", player.getName())
                .replace("%cost%", Formatter.formatMoney(totalCost)));

        return true;
    }

    // ==================== Lookup ====================

    public Farmer getFarmerByIsland(UUID islandId) { return farmersByIsland.get(islandId); }
    public Farmer getFarmerByEntity(UUID entityId) { return farmersByEntity.get(entityId); }
    public Map<UUID, Farmer> getAllFarmers() { return Collections.unmodifiableMap(farmersByIsland); }
    public boolean hasFarmer(UUID islandId) { return farmersByIsland.containsKey(islandId); }
    public boolean isReady() { return ready; }
    public void setReady(boolean ready) { this.ready = ready; }

    // Bypass
    public boolean isBypassing(UUID playerId) { return bypassPlayers.contains(playerId); }
    public void toggleBypass(UUID playerId) {
        if (bypassPlayers.contains(playerId)) bypassPlayers.remove(playerId);
        else bypassPlayers.add(playerId);
    }

    // ==================== Helpers ====================

    public void shutdown() {
        for (Farmer farmer : farmersByIsland.values()) {
            removeVillagerEntity(farmer);
        }
        farmersByEntity.clear();
        autoSellTasks.values().forEach(BukkitTask::cancel);
        autoSellTasks.clear();
    }

    private String getMaterialDisplayName(Material material) {
        // Use translatable component for localized names
        return material.name().toLowerCase().replace('_', ' ');
    }

    private void notifyIsland(Farmer farmer, String message) {
        // Notify all online players on the same island
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID playerIsland = plugin.getIslandHook().getIslandId(p.getLocation());
            if (playerIsland != null && playerIsland.equals(farmer.getIslandId())) {
                p.sendMessage(ColorUtil.toComponent(message));
            }
        }
    }

    /**
     * Fills a product storage via admin command.
     */
    public boolean fillStorage(Farmer farmer, Material material, int amount) {
        ProductConfig config = plugin.getConfigManager().getProduct(material);
        if (config == null) return false;
        ProductData data = farmer.getProductData(material);
        long limit = config.getLimit(data.getLevel());
        if (data.getAmount() + amount > limit) return false;
        data.addAmount(amount);
        return true;
    }

    // ==================== Leaderboard ====================

    /**
     * Gets sorted leaderboard by total item value (descending).
     */
    public List<Map.Entry<String, Double>> getLeaderboard(int limit) {
        List<Map.Entry<String, Double>> entries = new ArrayList<>();
        for (Map.Entry<UUID, Farmer> e : farmersByIsland.entrySet()) {
            Farmer f = e.getValue();
            double totalValue = calculateTotalValue(f);
            // Use owner UUID as name (can be resolved to player name)
            String ownerName = Bukkit.getOfflinePlayer(f.getOwnerId()).getName();
            if (ownerName == null) ownerName = f.getOwnerId().toString().substring(0, 8);
            entries.add(Map.entry(ownerName, totalValue));
        }
        entries.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        return entries.subList(0, Math.min(limit, entries.size()));
    }

    /**
     * Gets a specific leaderboard entry (1-indexed).
     */
    public Map.Entry<String, Double> getLeaderboardEntry(int position) {
        var lb = getLeaderboard(position);
        if (position < 1 || position > lb.size()) return null;
        return lb.get(position - 1);
    }

    /**
     * Gets a player's rank in the leaderboard.
     */
    public int getLeaderboardRank(UUID islandId) {
        if (islandId == null) return -1;
        var lb = getLeaderboard(farmersByIsland.size());
        Farmer target = farmersByIsland.get(islandId);
        if (target == null) return -1;
        double targetValue = calculateTotalValue(target);

        for (int i = 0; i < lb.size(); i++) {
            if (Math.abs(lb.get(i).getValue() - targetValue) < 0.01) {
                return i + 1;
            }
        }
        return -1;
    }

    /**
     * Gets sorted leaderboard by lifetime earnings.
     */
    public List<Map.Entry<String, Double>> getEarningsLeaderboard(int limit) {
        List<Map.Entry<String, Double>> entries = new ArrayList<>();
        for (Farmer f : farmersByIsland.values()) {
            String ownerName = Bukkit.getOfflinePlayer(f.getOwnerId()).getName();
            if (ownerName == null) ownerName = f.getOwnerId().toString().substring(0, 8);
            entries.add(Map.entry(ownerName, f.getLifetimeEarnings()));
        }
        entries.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        return entries.subList(0, Math.min(limit, entries.size()));
    }

    /**
     * Calculates total monetary value of all items in a farmer.
     */
    public double calculateTotalValue(Farmer farmer) {
        double total = 0;
        for (Map.Entry<String, ProductData> e : farmer.getStorage().entrySet()) {
            Material mat = Material.matchMaterial(e.getKey());
            if (mat == null) continue;
            ProductConfig cfg = plugin.getConfigManager().getProduct(mat);
            if (cfg == null) continue;
            total += e.getValue().getAmount() * cfg.getPrice();
        }
        return total;
    }
}
