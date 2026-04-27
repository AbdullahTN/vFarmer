package com.asmus.vfarmer.manager;

import com.asmus.vfarmer.VFarmer;
import com.asmus.vfarmer.util.ColorUtil;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * Manages zero-tax events with boss bar display.
 */
public class ZeroTaxManager {

    private final VFarmer plugin;
    long startTime;
    long endTime;
    private boolean active;
    BossBar bossBar;
    private BukkitRunnable timerTask;
    private File configFile;
    private YamlConfiguration config;

    public ZeroTaxManager(VFarmer plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "zerotax.yml");
        loadState();
    }

    private void loadState() {
        if (!configFile.exists()) {
            active = false;
            return;
        }
        config = YamlConfiguration.loadConfiguration(configFile);
        active = config.getBoolean("isActive", false);
        startTime = config.getLong("startTime", 0);
        endTime = config.getLong("endTime", 0);

        if (active && endTime > System.currentTimeMillis()) {
            startBossBar();
        } else {
            active = false;
        }
    }

    public void save() {
        try {
            config = new YamlConfiguration();
            config.set("isActive", active);
            config.set("startTime", startTime);
            config.set("endTime", endTime);
            config.save(configFile);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save zerotax state");
        }
    }

    /**
     * Starts a zero-tax event with a duration string (e.g., "30dk", "1sa", "7gn").
     */
    public void start(String durationStr) {
        long duration = parseDuration(durationStr);
        if (duration <= 0) return;
        startTime = System.currentTimeMillis();
        endTime = startTime + duration;
        active = true;
        save();
        startBossBar();
    }

    /**
     * Ends the zero-tax event.
     */
    public void end() {
        active = false;
        startTime = 0;
        endTime = 0;
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }
        if (bossBar != null) {
            Bukkit.getOnlinePlayers().forEach(p -> p.hideBossBar(bossBar));
            bossBar = null;
        }
        save();
    }

    public boolean isActive() {
        if (active && endTime <= System.currentTimeMillis()) {
            end();
        }
        return active;
    }

    /**
     * Gets the effective tax multiplier (0 if zero tax active, config tax otherwise).
     */
    public double getTaxRate() {
        if (isActive()) return 0;
        return plugin.getConfig().getDouble("farmer-tax", 25.0);
    }

    private void startBossBar() {
        String titleTemplate = plugin.getLanguageManager().getRaw("messages.zerotax-bossbar-title");
        bossBar = BossBar.bossBar(
                Component.text("0% Tax Active"),
                1.0f,
                BossBar.Color.GREEN,
                BossBar.Overlay.PROGRESS
        );

        // Show to all online players
        Bukkit.getOnlinePlayers().forEach(p -> p.showBossBar(bossBar));

        // Timer to update boss bar
        timerTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!active || endTime <= System.currentTimeMillis()) {
                    end();
                    cancel();
                    return;
                }

                long remaining = endTime - System.currentTimeMillis();
                float progress = Math.max(0, Math.min(1, (float) remaining / (endTime - startTime)));
                bossBar.progress(progress);

                String timeStr = formatTime(remaining);
                String title = titleTemplate.replace("%time%", timeStr);
                bossBar.name(ColorUtil.toComponent(title));

                // Show to new players
                Bukkit.getOnlinePlayers().forEach(p -> p.showBossBar(bossBar));
            }
        };
        timerTask.runTaskTimer(plugin, 0L, 20L);
    }

    /**
     * Parses duration strings like "30dk", "1sa", "7gn", "30gn-24sa".
     */
    public long parseDuration(String input) {
        try {
            long total = 0;
            input = input.toLowerCase().trim();

            // Handle combined format like "7gn-24sa"
            String[] parts = input.split("-");
            for (String part : parts) {
                part = part.trim();
                if (part.endsWith("sn") || part.endsWith("s")) {
                    total += Long.parseLong(part.replaceAll("[^0-9]", "")) * 1000;
                } else if (part.endsWith("dk") || part.endsWith("m")) {
                    total += Long.parseLong(part.replaceAll("[^0-9]", "")) * 60000;
                } else if (part.endsWith("sa") || part.endsWith("h")) {
                    total += Long.parseLong(part.replaceAll("[^0-9]", "")) * 3600000;
                } else if (part.endsWith("gn") || part.endsWith("d")) {
                    total += Long.parseLong(part.replaceAll("[^0-9]", "")) * 86400000;
                } else {
                    // Assume seconds
                    total += Long.parseLong(part.replaceAll("[^0-9]", "")) * 1000;
                }
            }
            return total;
        } catch (Exception e) {
            return -1;
        }
    }

    public String formatTime(long millis) {
        LanguageManager lang = plugin.getLanguageManager();
        String dayStr = lang.getRaw("countdown-placeholder.day");
        String hourStr = lang.getRaw("countdown-placeholder.hour");
        String minStr = lang.getRaw("countdown-placeholder.minute");
        String secStr = lang.getRaw("countdown-placeholder.second");

        long days = TimeUnit.MILLISECONDS.toDays(millis);
        long hours = TimeUnit.MILLISECONDS.toHours(millis) % 24;
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append(dayStr).append(" ");
        if (hours > 0) sb.append(hours).append(hourStr).append(" ");
        if (minutes > 0) sb.append(minutes).append(minStr).append(" ");
        sb.append(seconds).append(secStr);
        return sb.toString().trim();
    }

    public void showBossBarToPlayer(org.bukkit.entity.Player player) {
        if (bossBar != null && active) {
            player.showBossBar(bossBar);
        }
    }
}
