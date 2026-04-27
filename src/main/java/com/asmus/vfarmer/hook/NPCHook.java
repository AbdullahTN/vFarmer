package com.asmus.vfarmer.hook;

import com.asmus.vfarmer.VFarmer;
import com.asmus.vfarmer.data.Farmer;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * NPC Hook — supports FancyNPCs and Citizens for farmer skin system.
 *
 * Priority: FancyNPCs > Citizens > Vanilla Villager (fallback)
 *
 * Features:
 *   - Custom player skin on NPC
 *   - NPC looks at nearest player (smooth rotation)
 *   - Particle effects around NPC
 *   - Permission-based: nfarmer.skin (VIP feature)
 */
public class NPCHook {

    private final VFarmer plugin;
    private NPCProvider provider;
    private BukkitTask lookTask;
    private BukkitTask particleTask;
    private final Map<UUID, Object> npcMap; // islandId -> NPC object (provider-specific)

    public enum NPCProvider { FANCYNPCS, CITIZENS, NONE }

    public NPCHook(VFarmer plugin) {
        this.plugin = plugin;
        this.npcMap = new ConcurrentHashMap<>();
        detectProvider();
    }

    private void detectProvider() {
        if (Bukkit.getPluginManager().isPluginEnabled("FancyNpcs")) {
            provider = NPCProvider.FANCYNPCS;
        } else if (Bukkit.getPluginManager().isPluginEnabled("Citizens")) {
            provider = NPCProvider.CITIZENS;
        } else {
            provider = NPCProvider.NONE;
        }
    }

    public NPCProvider getProvider() { return provider; }
    public boolean isAvailable() { return provider != NPCProvider.NONE; }

    /**
     * Spawns an NPC with player skin at the farmer location.
     * Returns true if NPC was spawned, false if falling back to villager.
     */
    public boolean spawnNPC(Farmer farmer) {
        if (!farmer.hasNpcSkin() || !isAvailable()) return false;

        Location loc = farmer.getLocation();
        if (loc == null || loc.getWorld() == null) return false;

        removeNPC(farmer);

        try {
            switch (provider) {
                case FANCYNPCS -> spawnFancyNPC(farmer, loc);
                case CITIZENS -> spawnCitizensNPC(farmer, loc);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Removes the NPC for a farmer.
     */
    public void removeNPC(Farmer farmer) {
        Object npc = npcMap.remove(farmer.getIslandId());
        if (npc == null) return;

        try {
            switch (provider) {
                case FANCYNPCS -> removeFancyNPC(npc);
                case CITIZENS -> removeCitizensNPC(npc);
            }
        } catch (Exception ignored) {}
    }

    // ==================== FancyNPCs ====================

    private void spawnFancyNPC(Farmer farmer, Location loc) throws Exception {
        // FancyNPCs API: de.oliver.fancynpcs.api.FancyNpcsApi
        Class<?> apiClass = Class.forName("de.oliver.fancynpcs.api.FancyNpcsApi");
        Object api = apiClass.getMethod("get").invoke(null);

        // Get NPC manager
        Object npcManager = api.getClass().getMethod("getNpcManager").invoke(api);

        // Create NPC builder
        String npcName = "vfarmer_" + farmer.getIslandId().toString().substring(0, 8);

        // FancyNpcsApi.get().getNpcManager().getNpc(name) — check if exists
        Method getNpc = npcManager.getClass().getMethod("getNpc", String.class);
        Object existing = getNpc.invoke(npcManager, npcName);
        if (existing != null) {
            Method removeMethod = npcManager.getClass().getMethod("removeNpc", existing.getClass());
            removeMethod.invoke(npcManager, existing);
        }

        // Create NPC via builder
        Class<?> npcClass = Class.forName("de.oliver.fancynpcs.api.Npc");
        Class<?> npcBuilderClass = Class.forName("de.oliver.fancynpcs.api.NpcData");

        // Use NpcData constructor
        Object npcData = npcBuilderClass.getConstructor(String.class, UUID.class, Location.class)
                .newInstance(npcName, UUID.randomUUID(), loc);

        // Set skin
        if (farmer.getNpcSkin() != null) {
            Class<?> skinClass = Class.forName("de.oliver.fancynpcs.api.utils.SkinFetcher");
            // SkinFetcher.SkinData
            Method fetchSkin = skinClass.getMethod("fetchSkin", String.class);
            Object skinData = fetchSkin.invoke(null, farmer.getNpcSkin());
            if (skinData != null) {
                Method setSkin = npcData.getClass().getMethod("setSkin", skinData.getClass());
                setSkin.invoke(npcData, skinData);
            }
        }

        // Set properties
        try { npcData.getClass().getMethod("setTurnToPlayer", boolean.class).invoke(npcData, farmer.isNpcLookAtPlayer()); } catch (Exception ignored) {}

        // Create and register NPC
        Method createNpc = npcManager.getClass().getMethod("create", npcBuilderClass);
        Object npc = createNpc.invoke(npcManager, npcData);

        // Register and spawn
        Method register = npcManager.getClass().getMethod("registerNpc", npcClass);
        register.invoke(npcManager, npc);

        Method spawn = npc.getClass().getMethod("spawnForAll");
        spawn.invoke(npc);

        npcMap.put(farmer.getIslandId(), npc);
    }

    private void removeFancyNPC(Object npc) throws Exception {
        Method removeForAll = npc.getClass().getMethod("removeForAll");
        removeForAll.invoke(npc);

        Class<?> apiClass = Class.forName("de.oliver.fancynpcs.api.FancyNpcsApi");
        Object api = apiClass.getMethod("get").invoke(null);
        Object npcManager = api.getClass().getMethod("getNpcManager").invoke(api);
        Method remove = npcManager.getClass().getMethod("removeNpc", npc.getClass());
        remove.invoke(npcManager, npc);
    }

    // ==================== Citizens ====================

    private void spawnCitizensNPC(Farmer farmer, Location loc) throws Exception {
        // Citizens API: net.citizensnpcs.api.CitizensAPI
        Class<?> apiClass = Class.forName("net.citizensnpcs.api.CitizensAPI");
        Object registry = apiClass.getMethod("getNPCRegistry").invoke(null);

        // Create NPC
        Class<?> entityType = Class.forName("org.bukkit.entity.EntityType");
        Object playerType = entityType.getField("PLAYER").get(null);

        String npcName = farmer.getNpcSkin() != null ? farmer.getNpcSkin() : "Farmer";
        Method createNPC = registry.getClass().getMethod("createNPC", entityType, String.class);
        Object npc = createNPC.invoke(registry, playerType, npcName);

        // Set skin trait
        try {
            Method getOrAddTrait = npc.getClass().getMethod("getOrAddTrait", Class.class);
            Class<?> skinTraitClass = Class.forName("net.citizensnpcs.trait.SkinTrait");
            Object skinTrait = getOrAddTrait.invoke(npc, skinTraitClass);

            if (farmer.getNpcSkin() != null) {
                Method setSkinName = skinTrait.getClass().getMethod("setSkinName", String.class);
                setSkinName.invoke(skinTrait, farmer.getNpcSkin());
            }
        } catch (Exception ignored) {}

        // Set look close trait
        try {
            Method getOrAddTrait = npc.getClass().getMethod("getOrAddTrait", Class.class);
            Class<?> lookCloseClass = Class.forName("net.citizensnpcs.trait.LookClose");
            Object lookClose = getOrAddTrait.invoke(npc, lookCloseClass);
            Method setLooking = lookClose.getClass().getMethod("lookClose", boolean.class);
            setLooking.invoke(lookClose, farmer.isNpcLookAtPlayer());
        } catch (Exception ignored) {}

        // Spawn
        Method spawn = npc.getClass().getMethod("spawn", Location.class);
        spawn.invoke(npc, loc);

        // Make NPC protected
        try {
            Method setProtected = npc.getClass().getMethod("setProtected", boolean.class);
            setProtected.invoke(npc, true);
        } catch (Exception ignored) {}

        npcMap.put(farmer.getIslandId(), npc);
    }

    private void removeCitizensNPC(Object npc) throws Exception {
        Method despawn = npc.getClass().getMethod("despawn");
        despawn.invoke(npc);
        Method destroy = npc.getClass().getMethod("destroy");
        destroy.invoke(npc);
    }

    // ==================== Look At Player (Vanilla Fallback) ====================

    /**
     * Starts the look-at-player tick for vanilla villagers.
     * NPC plugins handle this natively, this is only for vanilla villager fallback.
     */
    public void startLookAtPlayerTick() {
        if (lookTask != null) lookTask.cancel();
        lookTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Farmer farmer : plugin.getFarmerManager().getAllFarmers().values()) {
                    if (!farmer.isNpcLookAtPlayer()) continue;
                    // Skip NPC-managed farmers (they handle look-at natively)
                    if (farmer.hasNpcSkin() && isAvailable()) continue;

                    if (farmer.getEntityId() == null) continue;
                    Entity entity = Bukkit.getEntity(farmer.getEntityId());
                    if (entity == null || !entity.isValid()) continue;

                    Location eLoc = entity.getLocation();
                    Player nearest = null;
                    double nearestDist = 16 * 16; // 16 blocks radius squared

                    for (Player p : eLoc.getWorld().getPlayers()) {
                        double distSq = p.getLocation().distanceSquared(eLoc);
                        if (distSq < nearestDist) {
                            nearestDist = distSq;
                            nearest = p;
                        }
                    }

                    if (nearest != null) {
                        Location pLoc = nearest.getLocation();
                        double dx = pLoc.getX() - eLoc.getX();
                        double dz = pLoc.getZ() - eLoc.getZ();
                        float yaw = (float)(Math.toDegrees(Math.atan2(-dx, dz)));
                        eLoc.setYaw(yaw);
                        entity.teleport(eLoc);
                    }
                }
            }
        }.runTaskTimer(plugin, 5L, 5L); // Every 0.25 seconds for smooth rotation
    }

    /**
     * Starts the particle effect tick for farmers with particles.
     */
    public void startParticleTick() {
        if (particleTask != null) particleTask.cancel();
        particleTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Farmer farmer : plugin.getFarmerManager().getAllFarmers().values()) {
                    String particleName = farmer.getNpcParticle();
                    if (particleName == null || particleName.isEmpty()) continue;

                    Location loc = farmer.getLocation();
                    if (loc == null || loc.getWorld() == null) continue;
                    if (!loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) continue;

                    // Check if any player is nearby (don't waste particles if no one sees)
                    boolean playerNearby = false;
                    for (Player p : loc.getWorld().getPlayers()) {
                        if (p.getLocation().distanceSquared(loc) < 32 * 32) {
                            playerNearby = true;
                            break;
                        }
                    }
                    if (!playerNearby) continue;

                    try {
                        Particle particle = Particle.valueOf(particleName.toUpperCase());
                        Location particleLoc = loc.clone().add(0, 1.2, 0);

                        // Different effects based on particle type
                        switch (particleName.toUpperCase()) {
                            case "FLAME" -> {
                                // Ring of flames
                                for (int i = 0; i < 8; i++) {
                                    double angle = (System.currentTimeMillis() / 50.0 + i * 45) * Math.PI / 180.0;
                                    double x = Math.cos(angle) * 0.6;
                                    double z = Math.sin(angle) * 0.6;
                                    loc.getWorld().spawnParticle(particle, particleLoc.clone().add(x, 0, z), 1, 0, 0, 0, 0);
                                }
                            }
                            case "END_ROD" -> {
                                // Spiral upward
                                double angle = (System.currentTimeMillis() / 30.0) * Math.PI / 180.0;
                                double x = Math.cos(angle) * 0.5;
                                double z = Math.sin(angle) * 0.5;
                                loc.getWorld().spawnParticle(particle, particleLoc.clone().add(x, 0.5, z), 1, 0, 0.05, 0, 0);
                            }
                            case "HEART" -> {
                                // Occasional hearts
                                if (System.currentTimeMillis() % 3000 < 100) {
                                    loc.getWorld().spawnParticle(particle, particleLoc.clone().add(0, 0.5, 0), 3, 0.3, 0.3, 0.3, 0);
                                }
                            }
                            case "ENCHANT" -> {
                                // Enchantment table effect
                                loc.getWorld().spawnParticle(Particle.ENCHANTMENT_TABLE, particleLoc, 5, 0.3, 0.5, 0.3, 1);
                            }
                            default -> {
                                // Generic particle circle
                                loc.getWorld().spawnParticle(particle, particleLoc, 3, 0.3, 0.3, 0.3, 0.01);
                            }
                        }
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        }.runTaskTimer(plugin, 2L, 2L); // Every 0.1 seconds for smooth particles
    }

    /**
     * Stops all NPC tasks.
     */
    public void shutdown() {
        if (lookTask != null) { lookTask.cancel(); lookTask = null; }
        if (particleTask != null) { particleTask.cancel(); particleTask = null; }
        // Remove all NPCs
        for (Farmer farmer : plugin.getFarmerManager().getAllFarmers().values()) {
            removeNPC(farmer);
        }
    }
}
