package com.asmus.vfarmer.manager;

import com.asmus.vfarmer.VFarmer;
import com.asmus.vfarmer.util.ColorUtil;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

/**
 * Manages booster events that multiply sell prices.
 * Config keys in config.yml:
 *   booster-multiplier: 2.0      (default multiplier when event is active)
 *
 * Admin commands:
 *   /farmer booster <duration>   (e.g. 30dk, 1sa, 24sa)
 *   /farmer booster end
 *   /farmer booster <duration> <multiplier>
 */
public class BoosterManager {

    private final VFarmer plugin;
    private double multiplier;
    private long endTime;
    private BossBar bossBar;
    private BukkitTask tickTask;

    public BoosterManager(VFarmer plugin) {
        this.plugin = plugin;
        this.multiplier = 1.0;
        this.endTime = 0;
    }

    /**
     * Starts a booster event.
     * @param durationMs duration in milliseconds
     * @param mult multiplier (e.g. 2.0 for x2)
     */
    public void start(long durationMs, double mult) {
        stop(); // End any existing booster
        this.multiplier = mult;
        this.endTime = System.currentTimeMillis() + durationMs;

        // Create boss bar
        bossBar = BossBar.bossBar(
                Component.empty(),
                1.0f,
                BossBar.Color.PURPLE,
                BossBar.Overlay.PROGRESS
        );

        // Show to all online players
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showBossBar(bossBar);
        }

        // Start tick task for countdown
        tickTask = new BukkitRunnable() {
            @Override
            public void run() {
                long remaining = endTime - System.currentTimeMillis();
                if (remaining <= 0) {
                    stop();
                    // Notify all players
                    String msg = plugin.getLanguageManager().getMessage("messages.booster-ended");
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.sendMessage(ColorUtil.toComponent(msg));
                    }
                    return;
                }

                float progress = Math.max(0, Math.min(1, (float) remaining / (float) (endTime - (endTime - remaining) + remaining)));
                // Recalculate progress from original duration
                bossBar.progress(Math.max(0.01f, Math.min(1f, (float) remaining / (float) (endTime - System.currentTimeMillis() + remaining))));

                String title = plugin.getLanguageManager().getMessage("messages.booster-bossbar-title")
                        .replace("%multiplier%", String.format("%.1f", multiplier))
                        .replace("%time%", plugin.getZeroTaxManager().formatTime(remaining));
                bossBar.name(ColorUtil.toComponent(title));
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    /**
     * Stops the current booster event.
     */
    public void stop() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        if (bossBar != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.hideBossBar(bossBar);
            }
            bossBar = null;
        }
        multiplier = 1.0;
        endTime = 0;
    }

    /**
     * Returns the current sell price multiplier.
     * Returns 1.0 if no booster is active.
     */
    public double getMultiplier() {
        if (!isActive()) return 1.0;
        return multiplier;
    }

    /**
     * Whether a booster event is currently active.
     */
    public boolean isActive() {
        return endTime > System.currentTimeMillis() && multiplier > 1.0;
    }

    /**
     * Gets remaining time in milliseconds.
     */
    public long getRemainingMs() {
        if (!isActive()) return 0;
        return endTime - System.currentTimeMillis();
    }

    /**
     * Shows the booster boss bar to a player (e.g. on join).
     */
    public void showBossBarToPlayer(Player player) {
        if (isActive() && bossBar != null) {
            player.showBossBar(bossBar);
        }
    }
}
