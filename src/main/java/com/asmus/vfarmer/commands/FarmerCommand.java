package com.asmus.vfarmer.commands;

import com.asmus.vfarmer.VFarmer;
import com.asmus.vfarmer.data.Farmer;
import com.asmus.vfarmer.data.ProductData;
import com.asmus.vfarmer.menu.FarmerBuyMenu;
import com.asmus.vfarmer.menu.FarmerLevelMenu;
import com.asmus.vfarmer.menu.FarmerMenu;
import com.asmus.vfarmer.util.ColorUtil;
import com.asmus.vfarmer.util.Formatter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Main command handler for /çiftçi (aliases: /ciftci, /farmer)
 */
public class FarmerCommand implements CommandExecutor, TabCompleter {

    private final VFarmer plugin;

    public FarmerCommand(VFarmer plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // No args = open menu or buy
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.only-players")));
                return true;
            }

            if (!plugin.getFarmerManager().isReady()) {
                player.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.system-preparing")));
                return true;
            }

            UUID islandId = plugin.getIslandHook().getPlayerIslandId(player);
            if (islandId == null) {
                player.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.not-on-island")));
                return true;
            }

            Farmer farmer = plugin.getFarmerManager().getFarmerByIsland(islandId);
            if (farmer != null) {
                if (!plugin.getIslandHook().isPlayerOnOwnIsland(player, player.getLocation())
                        && !plugin.getFarmerManager().isBypassing(player.getUniqueId())) {
                    player.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.not-on-island")));
                    return true;
                }
                new FarmerMenu(plugin, player, farmer).open();
            } else {
                new FarmerBuyMenu(plugin, player).open();
            }
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "reload" -> handleReload(sender);
            case "bypass" -> handleBypass(sender);
            case "zerotax" -> handleZeroTax(sender, args);
            case "give" -> handleGive(sender, args);
            case "autosell" -> handleAutoSell(sender, args);
            case "storage" -> handleStorage(sender, args);
            case "balance" -> handleBalance(sender, args);
            case "top" -> handleTop(sender, args);
            case "stats" -> handleStats(sender);
            case "booster" -> handleBooster(sender, args);
            case "level" -> handleLevel(sender);
            case "levelreward" -> handleLevelReward(sender, args);
            case "skin" -> handleSkin(sender, args);
            default -> {
                if (sender instanceof Player player) {
                    onCommand(sender, command, label, new String[0]);
                }
            }
        }

        return true;
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("nfarmer.admin")) {
            sender.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.no-permission")));
            return;
        }
        plugin.reloadConfig();
        plugin.getLanguageManager().load();
        plugin.getConfigManager().load();
        plugin.getGuiManager().load();
        plugin.getWebhookManager().reload();
        sender.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.reload-complete")));
    }

    private void handleBypass(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.only-players")));
            return;
        }
        if (!player.hasPermission("nfarmer.admin")) {
            player.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.no-permission")));
            return;
        }

        UUID islandId = plugin.getIslandHook().getIslandId(player.getLocation());
        if (islandId == null) {
            player.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.bypass-not-on-island")));
            return;
        }

        plugin.getFarmerManager().toggleBypass(player.getUniqueId());
        boolean bypassing = plugin.getFarmerManager().isBypassing(player.getUniqueId());
        player.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage(
                bypassing ? "messages.bypass-enabled" : "messages.bypass-disabled")));
    }

    private void handleZeroTax(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nfarmer.admin")) {
            sender.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.no-permission")));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.zerotax-usage")));
            return;
        }

        if (args[1].equalsIgnoreCase("end")) {
            if (!plugin.getZeroTaxManager().isActive()) {
                sender.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.zerotax-no-active")));
                return;
            }
            plugin.getZeroTaxManager().end();
            sender.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.zerotax-ended")));
        } else {
            if (plugin.getZeroTaxManager().isActive()) {
                sender.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.zerotax-already-active")));
                return;
            }
            plugin.getZeroTaxManager().start(args[1]);
            sender.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.zerotax-started")));
        }
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nfarmer.admin")) {
            sender.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.no-permission")));
            return;
        }

        if (args.length < 2) return;

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.player-not-found")));
            return;
        }

        UUID islandId = plugin.getIslandHook().getPlayerIslandId(target);
        if (islandId == null) {
            sender.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.player-no-island")));
            return;
        }

        if (plugin.getFarmerManager().hasFarmer(islandId)) {
            sender.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.farmer-already-exists")));
            return;
        }

        plugin.getFarmerManager().giveFarmer(target, islandId, target.getLocation());
        sender.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.farmer-given-admin")));
    }

    private void handleAutoSell(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nfarmer.admin")) {
            sender.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.no-permission")));
            return;
        }

        if (args.length < 3) return;

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.player-not-found")));
            return;
        }

        UUID islandId = plugin.getIslandHook().getPlayerIslandId(target);
        if (islandId == null) {
            sender.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.player-no-island")));
            return;
        }

        Farmer farmer = plugin.getFarmerManager().getFarmerByIsland(islandId);
        if (farmer == null) {
            sender.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.player-no-farmer")));
            return;
        }

        try {
            long duration = Long.parseLong(args[2]) * 86400000L; // Days to millis
            if (farmer.hasAutoSellTime()) {
                farmer.setAutoSellExpiration(farmer.getAutoSellExpiration() + duration);
            } else {
                farmer.setAutoSellExpiration(System.currentTimeMillis() + duration);
            }
            sender.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.autosell-added-sender")));
            target.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.autosell-added-target")));
        } catch (NumberFormatException e) {
            sender.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.invalid-duration")));
        }
    }

    private void handleStorage(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nfarmer.admin")) {
            sender.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.no-permission")));
            return;
        }

        // /farmer storage fill <player> <product> <amount>
        if (args.length < 5 || !args[1].equalsIgnoreCase("fill")) {
            sender.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.storage-usage")));
            return;
        }

        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.player-not-found")));
            return;
        }

        UUID islandId = plugin.getIslandHook().getPlayerIslandId(target);
        if (islandId == null) {
            sender.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.player-no-island")));
            return;
        }

        Farmer farmer = plugin.getFarmerManager().getFarmerByIsland(islandId);
        if (farmer == null) {
            sender.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.player-no-farmer")));
            return;
        }

        Material material = Material.matchMaterial(args[3].toUpperCase());
        if (material == null) {
            sender.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.invalid-product-name")));
            return;
        }

        if (!plugin.getConfigManager().isProduct(material)) {
            sender.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.not-farmer-product")));
            return;
        }

        try {
            int amount = Integer.parseInt(args[4]);
            if (amount <= 0) {
                sender.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.amount-must-be-positive")));
                return;
            }

            if (plugin.getFarmerManager().fillStorage(farmer, material, amount)) {
                sender.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.storage-filled-console")
                        .replace("%amount%", String.valueOf(amount))
                        .replace("%item%", material.name())));
            } else {
                sender.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.storage-full-or-failed")));
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.invalid-amount")));
        }
    }

    private void handleBalance(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nfarmer.admin")) {
            sender.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.no-permission")));
            return;
        }

        // /farmer balance <add|set|remove> <player> <amount>
        if (args.length < 4) return;

        String action = args[1].toLowerCase();
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.player-not-found")));
            return;
        }

        UUID islandId = plugin.getIslandHook().getPlayerIslandId(target);
        if (islandId == null) return;
        Farmer farmer = plugin.getFarmerManager().getFarmerByIsland(islandId);
        if (farmer == null) return;

        try {
            double amount = Double.parseDouble(args[3]);
            double oldBalance = farmer.getBalance();

            switch (action) {
                case "add" -> farmer.addBalance(amount);
                case "set" -> farmer.setBalance(amount);
                case "remove" -> farmer.setBalance(Math.max(0, farmer.getBalance() - amount));
            }

            sender.sendMessage(ColorUtil.toComponent("§aBalance updated."));
        } catch (NumberFormatException e) {
            sender.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.invalid-amount")));
        }
    }

    private void handleTop(CommandSender sender, String[] args) {
        String type = args.length > 1 ? args[1].toLowerCase() : "value";
        int limit = 10;

        List<Map.Entry<String, Double>> leaderboard;
        String title;

        if (type.equals("earnings")) {
            leaderboard = plugin.getFarmerManager().getEarningsLeaderboard(limit);
            title = plugin.getLanguageManager().getMessage("messages.top-header-earnings");
        } else {
            leaderboard = plugin.getFarmerManager().getLeaderboard(limit);
            title = plugin.getLanguageManager().getMessage("messages.top-header-value");
        }

        sender.sendMessage(ColorUtil.toComponent(""));
        sender.sendMessage(ColorUtil.toComponent(title));
        sender.sendMessage(ColorUtil.toComponent("§7§m                                        "));

        if (leaderboard.isEmpty()) {
            sender.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.top-empty")));
        } else {
            for (int i = 0; i < leaderboard.size(); i++) {
                var entry = leaderboard.get(i);
                String rankColor = switch (i) {
                    case 0 -> "&#FFD700"; // Gold
                    case 1 -> "&#C0C0C0"; // Silver
                    case 2 -> "&#CD7F32"; // Bronze
                    default -> "&#E6CF00";
                };
                sender.sendMessage(ColorUtil.toComponent(
                        " " + rankColor + "&l#" + (i + 1) + " &7" + entry.getKey() + " &8- &f" + Formatter.formatMoney(entry.getValue())));
            }
        }

        sender.sendMessage(ColorUtil.toComponent("§7§m                                        "));

        // Show player's own rank
        if (sender instanceof Player player) {
            UUID islandId = plugin.getIslandHook().getPlayerIslandId(player);
            int rank = plugin.getFarmerManager().getLeaderboardRank(islandId);
            if (rank > 0) {
                sender.sendMessage(ColorUtil.toComponent(
                        plugin.getLanguageManager().getMessage("messages.top-your-rank").replace("%rank%", String.valueOf(rank))));
            }
        }
        sender.sendMessage(ColorUtil.toComponent(""));
    }

    private void handleStats(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.only-players")));
            return;
        }

        UUID islandId = plugin.getIslandHook().getPlayerIslandId(player);
        if (islandId == null) {
            player.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.not-on-island")));
            return;
        }

        Farmer farmer = plugin.getFarmerManager().getFarmerByIsland(islandId);
        if (farmer == null) {
            player.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.player-no-farmer")));
            return;
        }

        double totalValue = plugin.getFarmerManager().calculateTotalValue(farmer);
        double totalItems = 0;
        int productCount = 0;
        for (ProductData d : farmer.getStorage().values()) {
            totalItems += d.getAmount();
            if (d.getAmount() > 0) productCount++;
        }

        int rank = plugin.getFarmerManager().getLeaderboardRank(islandId);

        player.sendMessage(ColorUtil.toComponent(""));
        player.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.stats-header")));
        player.sendMessage(ColorUtil.toComponent("§7§m                                        "));
        player.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.stats-rank").replace("%rank%", rank > 0 ? String.valueOf(rank) : "-")));
        // Farmer level info
        int fLevel = plugin.getFarmerLevelManager().getLevel(farmer);
        String fName = plugin.getFarmerLevelManager().getLevelName(fLevel);
        double progress = plugin.getFarmerLevelManager().getProgress(farmer) * 100;
        String bar = plugin.getFarmerLevelManager().getProgressBar(farmer, 15);
        player.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.stats-level").replace("%level%", String.valueOf(fLevel)).replace("%name%", fName)));
        player.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.stats-collected").replace("%amount%", Formatter.formatInteger(farmer.getTotalCollected()))));
        player.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.stats-progress").replace("%bar%", bar).replace("%percent%", String.format("%.0f", progress))));
        player.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.stats-total-items").replace("%amount%", Formatter.formatInteger(totalItems))));
        player.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.stats-total-value").replace("%value%", Formatter.formatMoney(totalValue))));
        player.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.stats-lifetime").replace("%earnings%", Formatter.formatMoney(farmer.getLifetimeEarnings()))));
        player.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.stats-products").replace("%count%", String.valueOf(productCount))));
        player.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.stats-balance").replace("%balance%", Formatter.formatMoney(farmer.getBalance()))));
        player.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.stats-xp").replace("%xp%", Formatter.formatInteger(farmer.getStoredXP()))));
        player.sendMessage(ColorUtil.toComponent("§7§m                                        "));
        player.sendMessage(ColorUtil.toComponent(""));
    }

    private void handleBooster(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nfarmer.admin")) {
            sender.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.no-permission")));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.booster-usage")));
            return;
        }

        String action = args[1].toLowerCase();

        if (action.equals("end")) {
            if (!plugin.getBoosterManager().isActive()) {
                sender.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.booster-no-active")));
                return;
            }
            plugin.getBoosterManager().stop();
            plugin.getWebhookManager().notifyBoosterEnd();
            sender.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.booster-ended")));
            return;
        }

        if (plugin.getBoosterManager().isActive()) {
            sender.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.booster-already-active")));
            return;
        }

        // Parse duration
        long durationMs = plugin.getZeroTaxManager().parseDuration(action);
        if (durationMs <= 0) {
            sender.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.invalid-duration")));
            return;
        }

        // Parse optional multiplier (default 2.0)
        double mult = 2.0;
        if (args.length >= 3) {
            try {
                mult = Double.parseDouble(args[2]);
                if (mult <= 1.0) mult = 2.0;
            } catch (NumberFormatException e) {
                mult = 2.0;
            }
        }

        plugin.getBoosterManager().start(durationMs, mult);
        String durationStr = plugin.getZeroTaxManager().formatTime(durationMs);
        plugin.getWebhookManager().notifyBoosterStart(mult, durationStr);

        String msg = plugin.getLanguageManager().getMessage("messages.booster-started")
                .replace("%multiplier%", String.format("%.1f", mult))
                .replace("%duration%", durationStr);
        sender.sendMessage(ColorUtil.toComponent(msg));

        // Notify all online players
        String broadcast = plugin.getLanguageManager().getMessage("messages.booster-broadcast")
                .replace("%multiplier%", String.format("%.1f", mult))
                .replace("%duration%", durationStr);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(ColorUtil.toComponent(broadcast));
        }
    }

    private void handleLevel(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.only-players")));
            return;
        }
        if (!plugin.getFarmerLevelManager().isEnabled()) {
            player.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.farmer-level-disabled")));
            return;
        }
        UUID islandId = plugin.getIslandHook().getPlayerIslandId(player);
        if (islandId == null) {
            player.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.not-on-island")));
            return;
        }
        Farmer farmer = plugin.getFarmerManager().getFarmerByIsland(islandId);
        if (farmer == null) {
            player.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.player-no-farmer")));
            return;
        }
        new FarmerLevelMenu(plugin, player, farmer).open();
    }

    private void handleLevelReward(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nfarmer.admin")) {
            sender.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.no-permission")));
            return;
        }
        // /farmer levelreward <player> <amount>
        if (args.length < 3) {
            sender.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.levelreward-usage")));
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.player-not-found")));
            return;
        }
        long amount;
        try { amount = Long.parseLong(args[2]); } catch (NumberFormatException e) {
            sender.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.invalid-amount")));
            return;
        }
        if (amount <= 0) {
            sender.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.amount-must-be-positive")));
            return;
        }

        UUID islandId = plugin.getIslandHook().getPlayerIslandId(target);
        if (islandId == null) {
            sender.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.player-no-island")));
            return;
        }
        Farmer farmer = plugin.getFarmerManager().getFarmerByIsland(islandId);
        if (farmer == null) {
            sender.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.player-no-farmer")));
            return;
        }

        farmer.addTotalCollected(amount);
        plugin.getFarmerLevelManager().checkLevelUp(farmer, target);
        plugin.getFarmerManager().saveFarmer(farmer);

        sender.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.levelreward-given")
                .replace("%player%", target.getName()).replace("%amount%", Formatter.formatInteger(amount))));
        target.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.levelreward-received")
                .replace("%amount%", Formatter.formatInteger(amount))));
    }

    // /farmer skin <player> <skin_name|reset>
    private void handleSkin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nfarmer.admin")) {
            sender.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.no-permission")));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.skin-admin-usage")));
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.player-not-found")));
            return;
        }
        UUID islandId = plugin.getIslandHook().getPlayerIslandId(target);
        if (islandId == null) {
            sender.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.player-no-island")));
            return;
        }
        Farmer farmer = plugin.getFarmerManager().getFarmerByIsland(islandId);
        if (farmer == null) {
            sender.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.player-no-farmer")));
            return;
        }

        String skinName = args[2];
        if (skinName.equalsIgnoreCase("reset") || skinName.equalsIgnoreCase("remove")) {
            farmer.setNpcSkin(null);
            farmer.setNpcParticle(null);
            plugin.getFarmerManager().spawnVillager(farmer);
            plugin.getFarmerManager().saveFarmer(farmer);
            sender.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.skin-admin-reset")
                    .replace("%player%", target.getName())));
        } else {
            farmer.setNpcSkin(skinName);
            plugin.getFarmerManager().spawnVillager(farmer);
            plugin.getFarmerManager().saveFarmer(farmer);
            sender.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.skin-admin-set")
                    .replace("%player%", target.getName()).replace("%skin%", skinName)));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("nfarmer.admin")) {
            // Non-admin players can use top and stats
            if (args.length == 1) {
                return filterStartsWith(args[0], List.of("top", "stats", "level"));
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("top")) {
                return filterStartsWith(args[1], List.of("value", "earnings"));
            }
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return filterStartsWith(args[0], List.of("reload", "bypass", "zerotax", "give", "autosell", "storage", "balance", "top", "stats", "booster", "level", "levelreward", "skin"));
        }

        if (args.length == 2) {
            return switch (args[0].toLowerCase()) {
                case "zerotax" -> filterStartsWith(args[1], List.of("end", "30dk", "60dk", "24sa", "7gn", "30gn"));
                case "booster" -> filterStartsWith(args[1], List.of("end", "30dk", "60dk", "24sa", "7gn"));
                case "top" -> filterStartsWith(args[1], List.of("value", "earnings"));
                case "give", "autosell", "storage", "balance", "skin", "levelreward" ->
                        Bukkit.getOnlinePlayers().stream().map(Player::getName)
                                .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                                .collect(Collectors.toList());
                default -> Collections.emptyList();
            };
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("storage")) {
            return filterStartsWith(args[2], List.of("fill"));
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("balance")) {
            return filterStartsWith(args[2], List.of("add", "set", "remove"));
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("skin")) {
            List<String> options = new ArrayList<>(List.of("reset"));
            Bukkit.getOnlinePlayers().forEach(p -> options.add(p.getName()));
            return filterStartsWith(args[2], options);
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("storage") && args[1].equalsIgnoreCase("fill")) {
            return plugin.getConfigManager().getProducts().keySet().stream()
                    .map(m -> m.name().toLowerCase())
                    .filter(n -> n.startsWith(args[3].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 5 && args[0].equalsIgnoreCase("storage")) {
            return filterStartsWith(args[4], List.of("1000", "5000", "10000"));
        }

        return Collections.emptyList();
    }

    private List<String> filterStartsWith(String input, List<String> options) {
        return options.stream()
                .filter(o -> o.toLowerCase().startsWith(input.toLowerCase()))
                .collect(Collectors.toList());
    }
}
