package com.asmus.vfarmer.listener;

import com.asmus.vfarmer.VFarmer;
import com.asmus.vfarmer.data.Farmer;
import com.asmus.vfarmer.manager.FarmerManager;
import com.asmus.vfarmer.menu.FarmerBuyMenu;
import com.asmus.vfarmer.menu.FarmerMenu;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.List;
import java.util.UUID;

/**
 * Handles all game events for farmer functionality.
 */
public class FarmerListener implements Listener {

    private final VFarmer plugin;

    public FarmerListener(VFarmer plugin) {
        this.plugin = plugin;
    }

    /**
     * Right-click on farmer villager to open menu.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Villager)) return;

        Farmer farmer = plugin.getFarmerManager().getFarmerByEntity(event.getRightClicked().getUniqueId());
        if (farmer == null) return;

        event.setCancelled(true);
        Player player = event.getPlayer();

        if (!plugin.getFarmerManager().isReady()) {
            player.sendMessage(plugin.getLanguageManager().getMessage("messages.system-loading"));
            return;
        }

        // Check if player is on the island
        if (!plugin.getIslandHook().isPlayerOnOwnIsland(player, event.getRightClicked().getLocation())
                && !plugin.getFarmerManager().isBypassing(player.getUniqueId())) {
            player.sendMessage(plugin.getLanguageManager().getMessage("messages.no-permission-gui"));
            return;
        }

        new FarmerMenu(plugin, player, farmer).open();
    }

    /**
     * Prevent farmer villager from taking damage.
     */
    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Villager)) return;
        Farmer farmer = plugin.getFarmerManager().getFarmerByEntity(event.getEntity().getUniqueId());
        if (farmer != null) {
            event.setCancelled(true);
        }
    }

    /**
     * Mark player-dropped items with metadata.
     */
    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        event.getItemDrop().setMetadata("player_drop",
                new FixedMetadataValue(plugin, true));
    }

    /**
     * Collect naturally spawned items on islands.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        Item item = event.getEntity();
        ItemStack stack = item.getItemStack();
        Material material = stack.getType();

        // Check if this is a farmer product
        if (!plugin.getConfigManager().isProduct(material)) return;

        Location loc = item.getLocation();
        UUID islandId = plugin.getFarmerManager().getIslandIdCached(loc);
        if (islandId == null) return;

        Farmer farmer = plugin.getFarmerManager().getFarmerByIsland(islandId);
        if (farmer == null || !farmer.isCollecting()) return;

        // Check if this is a player drop
        if (item.hasMetadata("player_drop") && !farmer.isCollectPlayerDrops()) return;

        // Try to collect
        if (plugin.getFarmerManager().collectItem(islandId, material, stack.getAmount())) {
            event.setCancelled(true);
        }
    }

    /**
     * Collect XP orbs on islands.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntitySpawn(EntitySpawnEvent event) {
        if (!(event.getEntity() instanceof ExperienceOrb orb)) return;

        Location loc = event.getLocation();
        UUID islandId = plugin.getFarmerManager().getIslandIdCached(loc);
        if (islandId == null) return;

        Farmer farmer = plugin.getFarmerManager().getFarmerByIsland(islandId);
        if (farmer == null || !farmer.isCollectXP()) return;

        int xp = orb.getExperience();
        farmer.addXP(xp);
        event.setCancelled(true);
    }

    /**
     * Kill mobs from spawners if enabled.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawnerSpawn(SpawnerSpawnEvent event) {
        Location loc = event.getLocation();
        UUID islandId = plugin.getFarmerManager().getIslandIdCached(loc);
        if (islandId == null) return;

        Farmer farmer = plugin.getFarmerManager().getFarmerByIsland(islandId);
        if (farmer == null || !farmer.isKillSpawnerMobs()) return;
        if (!plugin.getFarmerLevelManager().isUnlocked(farmer, "spawner-kill")) return;

        Entity entity = event.getEntity();
        if (entity instanceof LivingEntity living) {
            // Kill with a delay to allow drops
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (living.isValid() && !living.isDead()) {
                    living.setHealth(0);
                }
            }, 1L);
        }
    }

    /**
     * Collect mob drops on death.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Location loc = entity.getLocation();
        UUID islandId = plugin.getFarmerManager().getIslandIdCached(loc);
        if (islandId == null) return;

        Farmer farmer = plugin.getFarmerManager().getFarmerByIsland(islandId);
        if (farmer == null || !farmer.isCollecting()) return;

        // Collect drops
        List<ItemStack> drops = event.getDrops();
        drops.removeIf(drop -> {
            Material mat = drop.getType();
            if (plugin.getConfigManager().isProduct(mat)) {
                return plugin.getFarmerManager().collectItem(islandId, mat, drop.getAmount());
            }
            return false;
        });

        // Collect XP
        int xp = event.getDroppedExp();
        if (xp > 0) {
            farmer.addXP(xp);
            event.setDroppedExp(0);
        }
    }

    /**
     * Auto-harvest crops if enabled.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockGrow(BlockGrowEvent event) {
        Block block = event.getBlock();
        Location loc = block.getLocation();
        UUID islandId = plugin.getFarmerManager().getIslandIdCached(loc);
        if (islandId == null) return;

        Farmer farmer = plugin.getFarmerManager().getFarmerByIsland(islandId);
        if (farmer == null || !farmer.isAutoHarvest()) return;
        if (!plugin.getFarmerLevelManager().isUnlocked(farmer, "auto-harvest")) return;

        // Check if crop is fully grown
        if (event.getNewState().getBlockData() instanceof Ageable ageable) {
            if (ageable.getAge() >= ageable.getMaximumAge()) {
                // Harvest on next tick
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    harvestBlock(block, islandId);
                }, 1L);
            }
        }
    }

    private void harvestBlock(Block block, UUID islandId) {
        if (block.getBlockData() instanceof Ageable ageable) {
            if (ageable.getAge() < ageable.getMaximumAge()) return;

            Material cropType = block.getType();
            Material dropType = getCropDrop(cropType);
            if (dropType == null) return;

            if (plugin.getFarmerManager().collectItem(islandId, dropType, 1)) {
                // Reset crop age
                ageable.setAge(0);
                block.setBlockData(ageable);
            }
        }
    }

    private Material getCropDrop(Material crop) {
        return switch (crop) {
            case WHEAT -> Material.WHEAT;
            case CARROTS -> Material.CARROT;
            case POTATOES -> Material.POTATO;
            case BEETROOTS -> Material.BEETROOT;
            case NETHER_WART -> Material.NETHER_WART;
            case COCOA -> Material.COCOA_BEANS;
            case SWEET_BERRY_BUSH -> Material.SWEET_BERRIES;
            default -> null;
        };
    }

    /**
     * Re-spawn farmer entity when chunk loads.
     * O(1) lookup using chunk-indexed map instead of iterating all farmers.
     */
    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        long chunkKey = FarmerManager.chunkKeyFromChunk(event.getChunk());
        UUID islandId = plugin.getFarmerManager().getFarmerIslandInChunk(chunkKey);
        if (islandId == null) return;

        Farmer farmer = plugin.getFarmerManager().getFarmerByIsland(islandId);
        if (farmer == null) return;

        // Check if entity still exists
        if (farmer.getEntityId() != null) {
            Entity entity = Bukkit.getEntity(farmer.getEntityId());
            if (entity == null || !entity.isValid() || entity.isDead()) {
                plugin.getFarmerManager().spawnVillager(farmer);
            }
        }
    }

    /**
     * Show zero-tax boss bar when player joins.
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        plugin.getZeroTaxManager().showBossBarToPlayer(event.getPlayer());
        plugin.getBoosterManager().showBossBarToPlayer(event.getPlayer());

        // Re-render farmer if player teleported to island
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            checkFarmerVisibility(event.getPlayer());
        }, 20L);
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            checkFarmerVisibility(event.getPlayer());
        }, 20L);
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            checkFarmerVisibility(event.getPlayer());
        }, 20L);
    }

    private void checkFarmerVisibility(Player player) {
        Location loc = player.getLocation();
        UUID islandId = plugin.getFarmerManager().getIslandIdCached(loc);
        if (islandId == null) return;

        Farmer farmer = plugin.getFarmerManager().getFarmerByIsland(islandId);
        if (farmer == null) return;

        // Re-spawn if entity is missing
        if (farmer.getEntityId() != null) {
            Entity entity = Bukkit.getEntity(farmer.getEntityId());
            if (entity == null || !entity.isValid() || entity.isDead()) {
                plugin.getFarmerManager().spawnVillager(farmer);
            }
        }
    }

    /**
     * Prevent creature spawn override for farmer villagers.
     */
    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM) return;
        if (!(event.getEntity() instanceof Villager)) return;
        // Don't interfere with our spawns
    }

    /**
     * Starts vacuum tick — periodically collects nearby items for farmers with vacuum radius > 0.
     * Runs every 2 seconds (40 ticks) to balance performance.
     */
    public void startVacuumTick() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Farmer farmer : plugin.getFarmerManager().getAllFarmers().values()) {
                if (!farmer.isCollecting() || !farmer.isVacuumEnabled()) continue;
                if (!plugin.getFarmerLevelManager().isUnlocked(farmer, "vacuum")) continue;
                Location loc = farmer.getLocation();
                if (loc == null || loc.getWorld() == null) continue;
                if (!loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) continue;

                int radius = farmer.getVacuumRadius();
                UUID islandId = farmer.getIslandId();

                for (Entity entity : loc.getWorld().getNearbyEntities(loc, radius, radius, radius)) {
                    if (!(entity instanceof Item item)) continue;
                    if (item.isDead() || !item.isValid()) continue;
                    if (item.hasMetadata("player_drop") && !farmer.isCollectPlayerDrops()) continue;

                    ItemStack stack = item.getItemStack();
                    Material mat = stack.getType();
                    if (!plugin.getConfigManager().isProduct(mat)) continue;

                    if (plugin.getFarmerManager().collectItem(islandId, mat, stack.getAmount())) {
                        item.remove();
                    }
                }
            }
        }, 40L, 40L); // Every 2 seconds
    }
}
