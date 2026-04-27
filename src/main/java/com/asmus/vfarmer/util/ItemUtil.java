package com.asmus.vfarmer.util;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.net.URL;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for creating custom skull items.
 * Supports:
 *   - Base64 textures (standard Minecraft skin format)
 *   - HeadDatabase IDs (prefix "hdb-" e.g. "hdb-12345")
 *   - Direct texture URLs
 *   - Player names (prefix "player-" e.g. "player-Notch")
 */
public class ItemUtil {

    private static final Pattern URL_PATTERN = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"");
    private static boolean headDatabaseChecked = false;
    private static boolean headDatabaseAvailable = false;
    private static Object headDatabaseAPI = null;

    /**
     * Initializes HeadDatabase hook if available.
     * Called lazily on first hdb- request, or manually on plugin enable.
     */
    public static void init() {
        if (headDatabaseChecked) return;
        try {
            org.bukkit.plugin.Plugin hdbPlugin = Bukkit.getPluginManager().getPlugin("HeadDatabase");
            if (hdbPlugin != null && hdbPlugin.isEnabled()) {
                Class<?> apiClass = Class.forName("me.arcaniax.hdb.api.HeadDatabaseAPI");
                headDatabaseAPI = apiClass.getDeclaredConstructor().newInstance();
                headDatabaseAvailable = true;
                headDatabaseChecked = true;
            }
        } catch (Exception e) {
            headDatabaseAvailable = false;
        }
    }

    /**
     * Lazy init — tries to hook HeadDatabase if not yet checked.
     * Supports both old and new HeadDatabase API class locations.
     */
    private static void ensureHDB() {
        if (headDatabaseAvailable) return;

        org.bukkit.plugin.Plugin hdbPlugin = Bukkit.getPluginManager().getPlugin("HeadDatabase");
        if (hdbPlugin == null || !hdbPlugin.isEnabled()) return;

        // Try multiple known API class names (different HDB versions)
        String[] apiClasses = {
            "me.arcaniax.hdb.api.HeadDatabaseAPI",      // HeadDatabase v4+
            "me.arcaniax.hdb.api.DatabaseHead",          // Some forks
            "me.arcaniax.hdb.HeadDatabaseAPI",            // Older versions
        };

        for (String className : apiClasses) {
            try {
                Class<?> apiClass = Class.forName(className);
                headDatabaseAPI = apiClass.getDeclaredConstructor().newInstance();
                headDatabaseAvailable = true;
                headDatabaseChecked = true;
                return;
            } catch (ClassNotFoundException ignored) {
                // Try next class name
            } catch (Exception e) {
            }
        }

        // Last resort: try to use the plugin instance directly as API
        try {
            var method = hdbPlugin.getClass().getMethod("getItemHead", String.class);
            if (method != null) {
                headDatabaseAPI = hdbPlugin;
                headDatabaseAvailable = true;
                headDatabaseChecked = true;
                return;
            }
        } catch (NoSuchMethodException ignored) {
        } catch (Exception e) {
        }

        headDatabaseChecked = true;
        // Debug: list what classes are available
        try {
        } catch (Exception ignored) {}
    }

    /**
     * Smart skull creator — auto-detects the format:
     *   "hdb-12345"         → HeadDatabase ID
     *   "player-Notch"      → Player skull
     *   "eyJ0ZXh0..."       → Base64 texture
     *   "http://..."        → Direct URL
     */
    public static ItemStack createSkull(String texture) {
        if (texture == null || texture.isEmpty()) return new ItemStack(Material.PLAYER_HEAD);

        // HeadDatabase: hdb-<id>
        if (texture.startsWith("hdb-")) {
            return createHeadDatabaseSkull(texture.substring(4));
        }

        // Base64 with explicit prefix: basehead-<base64>
        if (texture.startsWith("basehead-")) {
            return createSkullFromBase64(texture.substring(9));
        }

        // Player name: player-<name>
        if (texture.startsWith("player-")) {
            return createPlayerSkull(texture.substring(7));
        }

        // Direct URL
        if (texture.startsWith("http://") || texture.startsWith("https://")) {
            return createSkullFromURL(texture);
        }

        // Base64 texture (default)
        return createSkullFromBase64(texture);
    }

    /**
     * Creates a skull from HeadDatabase by ID.
     * Falls back to default skull if HeadDatabase is not installed.
     */
    public static ItemStack createHeadDatabaseSkull(String id) {
        ensureHDB();
        if (!headDatabaseAvailable || headDatabaseAPI == null) {
            return new ItemStack(Material.PLAYER_HEAD);
        }
        try {
            var method = headDatabaseAPI.getClass().getMethod("getItemHead", String.class);
            ItemStack head = (ItemStack) method.invoke(headDatabaseAPI, id);
            if (head != null && head.getType() != Material.AIR) {
                return head;
            }
        } catch (Exception e) {
        }
        return new ItemStack(Material.PLAYER_HEAD);
    }

    /**
     * Checks if HeadDatabase is available.
     */
    public static boolean isHeadDatabaseAvailable() {
        ensureHDB();
        return headDatabaseAvailable;
    }

    /**
     * Creates a player head with a custom texture from base64.
     */
    public static ItemStack createSkullFromBase64(String base64Texture) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        if (base64Texture == null || base64Texture.isEmpty()) return skull;

        try {
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID());
            PlayerTextures textures = profile.getTextures();

            String decoded = new String(Base64.getDecoder().decode(base64Texture));
            Matcher matcher = URL_PATTERN.matcher(decoded);
            if (matcher.find()) {
                String url = matcher.group(1);
                textures.setSkin(new URL(url));
                profile.setTextures(textures);
                meta.setOwnerProfile(profile);
                skull.setItemMeta(meta);
            }
        } catch (Exception e) {
            // Fall back to default skull
        }
        return skull;
    }

    /**
     * Creates a player head from a texture URL directly.
     */
    public static ItemStack createSkullFromURL(String textureUrl) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        try {
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID());
            PlayerTextures textures = profile.getTextures();
            textures.setSkin(new URL(textureUrl));
            profile.setTextures(textures);
            meta.setOwnerProfile(profile);
            skull.setItemMeta(meta);
        } catch (Exception ignored) {}
        return skull;
    }

    /**
     * Creates a skull with a specific player's skin.
     */
    public static ItemStack createPlayerSkull(String playerName) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        try {
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(playerName));
            skull.setItemMeta(meta);
        } catch (Exception ignored) {}
        return skull;
    }
}
