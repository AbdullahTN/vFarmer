package com.asmus.vfarmer.hook;

import com.asmus.vfarmer.VFarmer;
import com.asmus.vfarmer.data.Farmer;
import com.asmus.vfarmer.data.ProductConfig;
import com.asmus.vfarmer.data.ProductData;
import com.asmus.vfarmer.util.Formatter;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;

/**
 * PlaceholderAPI expansion for vFarmer.
 *
 * Placeholders:
 *   %vfarmer_has_farmer%          → true/false
 *   %vfarmer_balance%             → Farmer auto-sell balance
 *   %vfarmer_total_items%         → Total items across all products
 *   %vfarmer_total_value%         → Total monetary value of all items
 *   %vfarmer_total_earnings%      → Lifetime earnings (balance withdrawn)
 *   %vfarmer_xp%                  → Stored XP
 *   %vfarmer_collecting%          → true/false collection status
 *   %vfarmer_autosell%            → true/false auto-sell status
 *   %vfarmer_autosell_time%       → Remaining auto-sell time
 *   %vfarmer_tax%                 → Current tax rate
 *   %vfarmer_product_<ITEM>%      → Amount of specific product (e.g. %vfarmer_product_wheat%)
 *   %vfarmer_product_<ITEM>_level% → Level of specific product
 *   %vfarmer_product_<ITEM>_limit% → Storage limit of specific product
 *   %vfarmer_product_count%       → Number of different products with items
 *   %vfarmer_filter%              → Current filter mode
 *   %vfarmer_zerotax%             → true/false zero-tax event active
 *   %vfarmer_top_<1-10>%          → Leaderboard position (island name or owner)
 *   %vfarmer_top_<1-10>_value%    → Leaderboard value
 *   %vfarmer_rank%                → Player's rank in leaderboard
 */
public class PlaceholderHook extends PlaceholderExpansion {

    private final VFarmer plugin;

    public PlaceholderHook(VFarmer plugin) {
        this.plugin = plugin;
    }

    @Override public @NotNull String getIdentifier() { return "vfarmer"; }
    @Override public @NotNull String getAuthor() { return "Asmus1990"; }
    @Override public @NotNull String getVersion() { return plugin.getDescription().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
        if (offlinePlayer == null || !offlinePlayer.isOnline()) return "";
        Player player = offlinePlayer.getPlayer();
        if (player == null) return "";

        // Get farmer for this player
        UUID islandId = plugin.getIslandHook().getPlayerIslandId(player);
        Farmer farmer = islandId != null ? plugin.getFarmerManager().getFarmerByIsland(islandId) : null;

        String p = params.toLowerCase();

        // ===== Basic =====
        if (p.equals("has_farmer")) return farmer != null ? "true" : "false";
        if (farmer == null) return "0";

        if (p.equals("balance")) return Formatter.formatMoney(farmer.getBalance());
        if (p.equals("xp")) return Formatter.formatInteger(farmer.getStoredXP());
        if (p.equals("collecting")) return farmer.isCollecting() ? "true" : "false";
        if (p.equals("autosell")) return farmer.isAutoSellActive() ? "true" : "false";
        if (p.equals("filter")) return farmer.getFilterMode().name();

        // ===== Auto-sell time =====
        if (p.equals("autosell_time")) {
            if (!farmer.hasAutoSellTime()) return "0";
            return plugin.getZeroTaxManager().formatTime(farmer.getAutoSellExpiration() - System.currentTimeMillis());
        }

        // ===== Tax =====
        if (p.equals("tax")) return Formatter.formatPercent(plugin.getZeroTaxManager().getTaxRate());
        if (p.equals("zerotax")) return plugin.getZeroTaxManager().isActive() ? "true" : "false";

        // ===== Booster =====
        if (p.equals("booster")) return plugin.getBoosterManager().isActive() ? "true" : "false";
        if (p.equals("booster_multiplier")) return String.format("%.1f", plugin.getBoosterManager().getMultiplier());
        if (p.equals("booster_time")) return plugin.getBoosterManager().isActive() ? plugin.getZeroTaxManager().formatTime(plugin.getBoosterManager().getRemainingMs()) : "0";
        if (p.equals("lifetime_earnings")) return Formatter.formatMoney(farmer.getLifetimeEarnings());
        if (p.equals("vacuum_radius")) return String.valueOf(farmer.getVacuumRadius());

        // ===== Farmer Level =====
        if (p.equals("level")) return String.valueOf(plugin.getFarmerLevelManager().getLevel(farmer));
        if (p.equals("level_name")) return plugin.getFarmerLevelManager().getLevelName(plugin.getFarmerLevelManager().getLevel(farmer));
        if (p.equals("level_progress")) return String.format("%.0f", plugin.getFarmerLevelManager().getProgress(farmer) * 100);
        if (p.equals("total_collected")) return Formatter.formatInteger(farmer.getTotalCollected());
        if (p.equals("next_level_xp")) {
            long next = plugin.getFarmerLevelManager().getNextLevelXP(farmer);
            return next < 0 ? "MAX" : Formatter.formatInteger(next);
        }
        if (p.equals("npc_skin")) return farmer.hasNpcSkin() ? farmer.getNpcSkin() : "";
        if (p.equals("npc_particle")) return farmer.getNpcParticle() != null ? farmer.getNpcParticle() : "";
        if (p.equals("npc_look_at")) return farmer.isNpcLookAtPlayer() ? "true" : "false";

        // ===== Totals =====
        if (p.equals("total_items")) {
            double total = 0;
            for (ProductData d : farmer.getStorage().values()) total += d.getAmount();
            return Formatter.formatInteger(total);
        }

        if (p.equals("total_value")) {
            double total = 0;
            for (Map.Entry<String, ProductData> e : farmer.getStorage().entrySet()) {
                Material mat = Material.matchMaterial(e.getKey());
                if (mat == null) continue;
                ProductConfig cfg = plugin.getConfigManager().getProduct(mat);
                if (cfg == null) continue;
                total += e.getValue().getAmount() * cfg.getPrice();
            }
            return Formatter.formatMoney(total);
        }

        if (p.equals("product_count")) {
            int count = 0;
            for (ProductData d : farmer.getStorage().values()) {
                if (d.getAmount() > 0) count++;
            }
            return String.valueOf(count);
        }

        // ===== Per-product: %vfarmer_product_wheat%, %vfarmer_product_wheat_level%, etc =====
        if (p.startsWith("product_")) {
            String rest = p.substring(8); // remove "product_"
            String materialName;
            String suffix = "";

            if (rest.endsWith("_level")) {
                materialName = rest.substring(0, rest.length() - 6);
                suffix = "level";
            } else if (rest.endsWith("_limit")) {
                materialName = rest.substring(0, rest.length() - 6);
                suffix = "limit";
            } else if (rest.endsWith("_price")) {
                materialName = rest.substring(0, rest.length() - 6);
                suffix = "price";
            } else if (rest.endsWith("_value")) {
                materialName = rest.substring(0, rest.length() - 6);
                suffix = "value";
            } else {
                materialName = rest;
            }

            Material mat = Material.matchMaterial(materialName.toUpperCase());
            if (mat == null) return "0";
            ProductData data = farmer.getProductData(mat);
            ProductConfig cfg = plugin.getConfigManager().getProduct(mat);
            if (cfg == null) return "0";

            return switch (suffix) {
                case "level" -> String.valueOf(data.getLevel());
                case "limit" -> Formatter.formatInteger(cfg.getLimit(data.getLevel()));
                case "price" -> Formatter.formatMoney(cfg.getPrice());
                case "value" -> Formatter.formatMoney(data.getAmount() * cfg.getPrice());
                default -> Formatter.formatInteger(data.getAmount());
            };
        }

        // ===== Leaderboard: %vfarmer_top_1%, %vfarmer_top_1_value%, %vfarmer_rank% =====
        if (p.equals("rank")) {
            return String.valueOf(plugin.getFarmerManager().getLeaderboardRank(islandId));
        }

        if (p.startsWith("top_")) {
            String rest = p.substring(4);
            boolean wantValue = rest.endsWith("_value");
            if (wantValue) rest = rest.substring(0, rest.length() - 6);

            try {
                int position = Integer.parseInt(rest);
                var entry = plugin.getFarmerManager().getLeaderboardEntry(position);
                if (entry == null) return "-";
                return wantValue ? Formatter.formatMoney(entry.getValue()) : entry.getKey();
            } catch (NumberFormatException e) {
                return "0";
            }
        }

        return null;
    }
}
