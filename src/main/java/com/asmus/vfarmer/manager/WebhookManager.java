package com.asmus.vfarmer.manager;

import com.asmus.vfarmer.VFarmer;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Sends Discord webhook notifications for farmer events.
 *
 * Config keys in config.yml:
 *   discord:
 *     enabled: false
 *     webhook-url: ""
 *     notify-sell: true
 *     notify-upgrade: true
 *     notify-booster: true
 *     notify-zerotax: true
 *     embed-color: 15773457   (hex F5AF19 as decimal)
 */
public class WebhookManager {

    private final VFarmer plugin;
    private boolean enabled;
    private String webhookUrl;
    private boolean notifySell;
    private boolean notifyUpgrade;
    private boolean notifyBooster;
    private boolean notifyZerotax;
    private int embedColor;

    public WebhookManager(VFarmer plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        this.enabled = plugin.getConfig().getBoolean("discord.enabled", false);
        this.webhookUrl = plugin.getConfig().getString("discord.webhook-url", "");
        this.notifySell = plugin.getConfig().getBoolean("discord.notify-sell", true);
        this.notifyUpgrade = plugin.getConfig().getBoolean("discord.notify-upgrade", true);
        this.notifyBooster = plugin.getConfig().getBoolean("discord.notify-booster", true);
        this.notifyZerotax = plugin.getConfig().getBoolean("discord.notify-zerotax", true);
        this.embedColor = plugin.getConfig().getInt("discord.embed-color", 15773457);
    }

    // ===== Public notify methods =====

    /**
     * Notifies when a player sells all products.
     */
    public void notifySellAll(String playerName, String earnings) {
        if (!enabled || !notifySell) return;
        sendEmbed("💰 Bulk Sell", playerName + " sold all products.\n**Earnings:** " + earnings, embedColor);
    }

    /**
     * Notifies when a player upgrades all products.
     */
    public void notifyUpgradeAll(String playerName, String cost) {
        if (!enabled || !notifyUpgrade) return;
        sendEmbed("📈 Bulk Upgrade", playerName + " upgraded all products.\n**Cost:** " + cost, 3145472);
    }

    /**
     * Notifies when a product storage is upgraded.
     */
    public void notifyStorageUpgrade(String playerName, String product, int newLevel) {
        if (!enabled || !notifyUpgrade) return;
        sendEmbed("⬆️ Storage Upgrade", playerName + " upgraded **" + product + "** to level **" + newLevel + "**.", 3145472);
    }

    /**
     * Notifies when a booster event starts.
     */
    public void notifyBoosterStart(double multiplier, String duration) {
        if (!enabled || !notifyBooster) return;
        sendEmbed("🚀 Booster Activated", "**x" + String.format("%.1f", multiplier) + "** sell multiplier active!\n**Duration:** " + duration, 10494192);
    }

    /**
     * Notifies when a booster event ends.
     */
    public void notifyBoosterEnd() {
        if (!enabled || !notifyBooster) return;
        sendEmbed("⏹ Booster Ended", "The sell multiplier booster has ended.", 8421504);
    }

    /**
     * Notifies when a zero-tax event starts.
     */
    public void notifyZerotaxStart(String duration) {
        if (!enabled || !notifyZerotax) return;
        sendEmbed("🎉 Zero Tax Event", "**0% farmer tax** is now active!\n**Duration:** " + duration, 1686272);
    }

    /**
     * Notifies when a zero-tax event ends.
     */
    public void notifyZerotaxEnd() {
        if (!enabled || !notifyZerotax) return;
        sendEmbed("⏹ Zero Tax Ended", "The zero-tax event has ended. Normal tax rates apply.", 8421504);
    }

    /**
     * Notifies when a player buys a farmer.
     */
    public void notifyFarmerBought(String playerName) {
        if (!enabled) return;
        sendEmbed("🌾 New Farmer", "**" + playerName + "** bought a new farmer!", 3145472);
    }

    // ===== Internal =====

    private void sendEmbed(String title, String description, int color) {
        if (webhookUrl == null || webhookUrl.isEmpty()) return;

        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    JsonObject embed = new JsonObject();
                    embed.addProperty("title", title);
                    embed.addProperty("description", description);
                    embed.addProperty("color", color);

                    JsonObject footer = new JsonObject();
                    footer.addProperty("text", "vFarmer");
                    embed.add("footer", footer);

                    // Timestamp
                    embed.addProperty("timestamp", java.time.Instant.now().toString());

                    JsonObject payload = new JsonObject();
                    payload.addProperty("username", "vFarmer");
                    com.google.gson.JsonArray embeds = new com.google.gson.JsonArray();
                    embeds.add(embed);
                    payload.add("embeds", embeds);

                    URL url = new URL(webhookUrl);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setDoOutput(true);
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);

                    try (OutputStream os = conn.getOutputStream()) {
                        os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
                    }

                    int responseCode = conn.getResponseCode();
                    if (responseCode != 200 && responseCode != 204) {
                        // Silent fail - don't spam console
                    }
                    conn.disconnect();
                } catch (Exception ignored) {
                    // Silent fail - webhook errors shouldn't affect gameplay
                }
            }
        }.runTaskAsynchronously(plugin);
    }
}
