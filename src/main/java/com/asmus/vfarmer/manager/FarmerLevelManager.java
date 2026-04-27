package com.asmus.vfarmer.manager;

import com.asmus.vfarmer.VFarmer;
import com.asmus.vfarmer.data.Farmer;
import com.asmus.vfarmer.util.ColorUtil;
import com.asmus.vfarmer.util.Formatter;
import org.bukkit.*;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Farmer leveling system.
 *
 * Farmer XP is earned from every item collected.
 * Level thresholds and unlocks are defined in config.yml:
 *
 * farmer-levels:
 *   1: { xp: 0, name: "Rookie" }
 *   2: { xp: 1000, name: "Apprentice" }
 *   3: { xp: 5000, name: "Farmer", unlock: "auto-harvest" }
 *   4: { xp: 15000, name: "Expert", unlock: "vacuum" }
 *   5: { xp: 50000, name: "Master", unlock: "spawner-kill" }
 *   6: { xp: 100000, name: "Legendary" }
 *
 * Farmer level is calculated from total collected items (not stored XP).
 */
public class FarmerLevelManager {

    private final VFarmer plugin;

    public FarmerLevelManager(VFarmer plugin) {
        this.plugin = plugin;
    }

    /**
     * Whether the leveling system is enabled in config.
     */
    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("farmer-levels.enabled", true);
    }

    /**
     * Gets the farmer's level based on total collected items.
     */
    public int getLevel(Farmer farmer) {
        if (!isEnabled()) return 1;
        long totalCollected = farmer.getTotalCollected();
        var section = plugin.getConfig().getConfigurationSection("farmer-levels");
        if (section == null) return 1;

        int maxLevel = 1;
        for (String key : section.getKeys(false)) {
            try {
                int level = Integer.parseInt(key);
                long reqXp = section.getLong(key + ".xp", 0);
                if (totalCollected >= reqXp && level > maxLevel) {
                    maxLevel = level;
                }
            } catch (NumberFormatException ignored) {}
        }
        return maxLevel;
    }

    /**
     * Gets the level name from config.
     */
    public String getLevelName(int level) {
        return plugin.getConfig().getString("farmer-levels." + level + ".name", "Level " + level);
    }

    /**
     * Gets XP needed for next level.
     */
    public long getNextLevelXP(Farmer farmer) {
        int currentLevel = getLevel(farmer);
        int nextLevel = currentLevel + 1;
        long xp = plugin.getConfig().getLong("farmer-levels." + nextLevel + ".xp", -1);
        return xp;
    }

    /**
     * Gets the progress percentage to next level (0.0 - 1.0).
     */
    public double getProgress(Farmer farmer) {
        int currentLevel = getLevel(farmer);
        long currentXP = farmer.getTotalCollected();
        long currentReq = plugin.getConfig().getLong("farmer-levels." + currentLevel + ".xp", 0);
        long nextReq = plugin.getConfig().getLong("farmer-levels." + (currentLevel + 1) + ".xp", -1);
        if (nextReq < 0) return 1.0; // Max level
        if (nextReq <= currentReq) return 1.0;
        return Math.min(1.0, (double)(currentXP - currentReq) / (double)(nextReq - currentReq));
    }

    /**
     * Checks if a feature is unlocked at the farmer's current level.
     */
    public boolean isUnlocked(Farmer farmer, String feature) {
        if (!isEnabled()) return true; // Everything unlocked when leveling disabled
        int farmerLevel = getLevel(farmer);
        var section = plugin.getConfig().getConfigurationSection("farmer-levels");
        if (section == null) return true; // No levels configured = everything unlocked

        for (String key : section.getKeys(false)) {
            try {
                int level = Integer.parseInt(key);
                String unlock = section.getString(key + ".unlock", "");
                if (unlock.equalsIgnoreCase(feature) && farmerLevel < level) {
                    return false;
                }
            } catch (NumberFormatException ignored) {}
        }
        return true;
    }

    /**
     * Called when items are collected — checks for level up.
     */
    public void checkLevelUp(Farmer farmer, Player nearbyPlayer) {
        if (!isEnabled()) return;
        int oldLevel = farmer.getCachedLevel();
        int newLevel = getLevel(farmer);

        if (newLevel > oldLevel) {
            farmer.setCachedLevel(newLevel);
            onLevelUp(farmer, nearbyPlayer, newLevel);
        }
    }

    /**
     * Called when a farmer levels up. Plays effects and notifies.
     */
    private void onLevelUp(Farmer farmer, Player player, int newLevel) {
        String levelName = getLevelName(newLevel);

        // Notify player
        if (player != null && player.isOnline()) {
            String msg = plugin.getLanguageManager().getMessage("messages.farmer-level-up")
                    .replace("%level%", String.valueOf(newLevel))
                    .replace("%name%", levelName);
            player.sendMessage(ColorUtil.toComponent(msg));

            // Title
            player.sendTitle(
                    ColorUtil.toLegacy("&#E6CF00&l⬆ " + plugin.getLanguageManager().getMessage("messages.farmer-level-up-title")),
                    ColorUtil.toLegacy("&#FCFC9C" + levelName),
                    10, 40, 20
            );
        }

        // Firework effect at farmer location
        Location loc = farmer.getLocation();
        if (loc != null && loc.getWorld() != null) {
            spawnLevelUpFirework(loc);
            // Particles
            loc.getWorld().spawnParticle(Particle.COMPOSTER, loc.clone().add(0, 1.5, 0), 30, 0.5, 0.5, 0.5, 0);
        }

        // Check unlock
        String unlock = plugin.getConfig().getString("farmer-levels." + newLevel + ".unlock", "");
        if (!unlock.isEmpty() && player != null) {
            String unlockMsg = plugin.getLanguageManager().getMessage("messages.farmer-feature-unlocked")
                    .replace("%feature%", unlock);
            player.sendMessage(ColorUtil.toComponent(unlockMsg));
        }

        // Give rewards
        if (player != null && player.isOnline()) {
            giveRewards(player, newLevel);
        }

        // Webhook
        plugin.getWebhookManager().notifyStorageUpgrade(
                player != null ? player.getName() : "System",
                "Farmer Level", newLevel);
    }

    /**
     * Gives level rewards to a player.
     */
    public void giveRewards(Player player, int level) {
        var rewardsList = plugin.getConfig().getList("farmer-levels." + level + ".rewards");
        if (rewardsList == null) return;

        var rewardsSection = plugin.getConfig().getConfigurationSection("farmer-levels." + level);
        if (rewardsSection == null || !rewardsSection.isList("rewards")) return;

        var rewards = rewardsSection.getMapList("rewards");
        for (Map<?, ?> rewardRaw : rewards) {
            String type = rewardRaw.containsKey("type") ? String.valueOf(rewardRaw.get("type")) : "item";

            if (type.equalsIgnoreCase("command")) {
                String cmd = rewardRaw.containsKey("command") ? String.valueOf(rewardRaw.get("command")) : "";
                if (!cmd.isEmpty()) {
                    String finalCmd = cmd.replace("%player%", player.getName());
                    Bukkit.getScheduler().runTask(plugin, () ->
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCmd));
                }
            } else if (type.equalsIgnoreCase("item")) {
                String matName = rewardRaw.containsKey("material") ? String.valueOf(rewardRaw.get("material")) : "STONE";
                Material mat = Material.matchMaterial(matName);
                if (mat == null) mat = Material.STONE;
                int amount = rewardRaw.containsKey("amount") ? Integer.parseInt(String.valueOf(rewardRaw.get("amount"))) : 1;

                ItemStack item = new ItemStack(mat, amount);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    if (rewardRaw.containsKey("name")) {
                        meta.displayName(ColorUtil.toComponent(String.valueOf(rewardRaw.get("name"))));
                    }
                    if (rewardRaw.containsKey("lore") && rewardRaw.get("lore") instanceof java.util.List<?> loreList) {
                        meta.lore(loreList.stream()
                                .map(l -> ColorUtil.toComponent(String.valueOf(l)))
                                .toList());
                    }
                    item.setItemMeta(meta);
                }

                // Give to player, drop if inventory full
                var leftover = player.getInventory().addItem(item);
                if (!leftover.isEmpty()) {
                    for (ItemStack drop : leftover.values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), drop);
                    }
                }

                // Notify
                String rewardMsg = plugin.getLanguageManager().getMessage("messages.level-reward-received")
                        .replace("%amount%", String.valueOf(amount))
                        .replace("%item%", mat.name());
                player.sendMessage(ColorUtil.toComponent(rewardMsg));
            }
        }
    }

    /**
     * Gets the reward descriptions for a level (for display in GUI).
     */
    public List<String> getRewardDescriptions(int level) {
        List<String> descriptions = new ArrayList<>();
        var rewardsSection = plugin.getConfig().getConfigurationSection("farmer-levels." + level);
        if (rewardsSection == null || !rewardsSection.isList("rewards")) return descriptions;

        var rewards = rewardsSection.getMapList("rewards");
        for (Map<?, ?> rewardRaw : rewards) {
            String type = rewardRaw.containsKey("type") ? String.valueOf(rewardRaw.get("type")) : "item";
            if (type.equalsIgnoreCase("item")) {
                String matName = rewardRaw.containsKey("material") ? String.valueOf(rewardRaw.get("material")) : "STONE";
                int amount = rewardRaw.containsKey("amount") ? Integer.parseInt(String.valueOf(rewardRaw.get("amount"))) : 1;
                String name = rewardRaw.containsKey("name") ? String.valueOf(rewardRaw.get("name")) : matName;
                descriptions.add("  &8▫ &f" + amount + "x " + name);
            } else if (type.equalsIgnoreCase("command")) {
                descriptions.add("  &8▫ &d" + plugin.getLanguageManager().getMessage("menus.level.items.level-item.special-reward"));
            }
        }
        return descriptions;
    }

    /**
     * Spawns a firework at the given location.
     */
    private void spawnLevelUpFirework(Location loc) {
        new BukkitRunnable() {
            @Override
            public void run() {
                Firework fw = loc.getWorld().spawn(loc.clone().add(0, 1, 0), Firework.class);
                FireworkMeta meta = fw.getFireworkMeta();
                meta.addEffect(FireworkEffect.builder()
                        .with(FireworkEffect.Type.STAR)
                        .withColor(Color.YELLOW, Color.ORANGE)
                        .withFade(Color.RED)
                        .trail(true)
                        .flicker(true)
                        .build());
                meta.setPower(0);
                fw.setFireworkMeta(meta);
                // Detonate after 1 tick
                new BukkitRunnable() {
                    @Override
                    public void run() { fw.detonate(); }
                }.runTaskLater(plugin, 2L);
            }
        }.runTask(plugin);
    }

    /**
     * Generates a progress bar string.
     */
    public String getProgressBar(Farmer farmer, int length) {
        double progress = getProgress(farmer);
        int filled = (int)(progress * length);
        StringBuilder sb = new StringBuilder();
        sb.append("&#03FF00");
        for (int i = 0; i < filled; i++) sb.append("█");
        sb.append("&7");
        for (int i = filled; i < length; i++) sb.append("█");
        return sb.toString();
    }
}
