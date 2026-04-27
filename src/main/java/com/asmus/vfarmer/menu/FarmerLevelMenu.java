package com.asmus.vfarmer.menu;

import com.asmus.vfarmer.VFarmer;
import com.asmus.vfarmer.data.Farmer;
import com.asmus.vfarmer.gui.GuiManager;
import com.asmus.vfarmer.manager.FarmerLevelManager;
import com.asmus.vfarmer.util.ColorUtil;
import com.asmus.vfarmer.util.Formatter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Farmer Level menu — shows level progression, current level,
 * unlocked features, and progress to next level.
 */
public class FarmerLevelMenu implements Listener, InventoryHolder {

    private final VFarmer plugin;
    private final Player player;
    private final Farmer farmer;
    private final Inventory inventory;
    private final GuiManager.GuiLayout layout;
    private final Map<Integer, String> actionSlots = new HashMap<>();

    public FarmerLevelMenu(VFarmer plugin, Player player, Farmer farmer) {
        this.plugin = plugin;
        this.player = player;
        this.farmer = farmer;
        this.layout = plugin.getGuiManager().getLayout("level");
        int size = layout != null ? layout.size : 45;
        String title = layout != null ? plugin.getLanguageManager().getRaw(layout.titleKey) : "Farmer Level";
        this.inventory = Bukkit.createInventory(this, size, ColorUtil.toComponent(title));
    }

    public void open() {
        build();
        player.openInventory(inventory);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private void build() {
        inventory.clear();
        actionSlots.clear();

        FarmerLevelManager lm = plugin.getFarmerLevelManager();
        int currentLevel = lm.getLevel(farmer);

        // Filler
        if (layout != null && layout.filler != null) {
            ItemStack filler = plugin.getGuiManager().createFiller(layout.filler);
            for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, filler);
        }

        // Level items in level-slots
        List<Integer> levelSlots;
        try {
            var f = new java.io.File(plugin.getDataFolder(), "gui/level.yml");
            var cfg = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(f);
            levelSlots = cfg.getIntegerList("level-slots");
        } catch (Exception e) {
            levelSlots = List.of(10, 11, 12, 13, 14, 15, 16);
        }

        ConfigurationSection levelsConfig = plugin.getConfig().getConfigurationSection("farmer-levels");
        if (levelsConfig != null) {
            List<String> levelKeys = new ArrayList<>();
            for (String key : levelsConfig.getKeys(false)) {
                if (key.equals("enabled")) continue;
                levelKeys.add(key);
            }
            // Sort numerically
            levelKeys.sort(Comparator.comparingInt(Integer::parseInt));

            for (int i = 0; i < Math.min(levelKeys.size(), levelSlots.size()); i++) {
                String key = levelKeys.get(i);
                int lvl = Integer.parseInt(key);
                int slot = levelSlots.get(i);
                long reqXp = levelsConfig.getLong(key + ".xp", 0);
                String name = levelsConfig.getString(key + ".name", "Level " + lvl);
                String unlock = levelsConfig.getString(key + ".unlock", "");

                boolean reached = currentLevel >= lvl;
                boolean isCurrent = currentLevel == lvl;

                // Choose material based on state
                Material mat;
                if (reached) {
                    mat = Material.LIME_STAINED_GLASS_PANE;
                } else if (currentLevel == lvl - 1) {
                    mat = Material.YELLOW_STAINED_GLASS_PANE; // Next level
                } else {
                    mat = Material.RED_STAINED_GLASS_PANE;
                }

                ItemStack item = new ItemStack(mat);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    // Name
                    String color = reached ? "&#03FF00" : (currentLevel == lvl - 1 ? "&#E6CF00" : "&#FF0000");
                    String statusIcon = reached ? " &#03FF00✔" : "";
                    meta.displayName(ColorUtil.toComponent(color + "&lLevel " + lvl + " &8— &f" + name + statusIcon));

                    // Lore
                    List<String> lore = new ArrayList<>();
                    lore.add("");
                    lore.add("&7" + plugin.getLanguageManager().getMessage("menus.level.items.level-item.xp-required")
                            .replace("%xp%", Formatter.formatInteger(reqXp)));

                    if (!unlock.isEmpty()) {
                        lore.add("&7" + plugin.getLanguageManager().getMessage("menus.level.items.level-item.unlocks")
                                .replace("%feature%", unlock));
                    }

                    // Show rewards
                    List<String> rewardDescs = lm.getRewardDescriptions(lvl);
                    if (!rewardDescs.isEmpty()) {
                        lore.add("");
                        lore.add("&#E6CF00" + plugin.getLanguageManager().getMessage("menus.level.items.level-item.rewards-header"));
                        lore.addAll(rewardDescs);
                        if (reached) {
                            lore.add("  &#03FF00✔ " + plugin.getLanguageManager().getMessage("menus.level.items.level-item.rewards-claimed"));
                        }
                    }

                    lore.add("");
                    if (isCurrent) {
                        lore.add("&#E6CF00▸ " + plugin.getLanguageManager().getMessage("menus.level.items.level-item.current"));
                    } else if (reached) {
                        lore.add("&#03FF00✔ " + plugin.getLanguageManager().getMessage("menus.level.items.level-item.completed"));
                    } else {
                        lore.add("&#FF0000✖ " + plugin.getLanguageManager().getMessage("menus.level.items.level-item.locked"));
                    }

                    meta.lore(lore.stream().map(ColorUtil::toComponent).toList());

                    // Enchant glow for current level
                    if (isCurrent) {
                        meta.addEnchant(org.bukkit.enchantments.Enchantment.getByKey(org.bukkit.NamespacedKey.minecraft("unbreaking")), 1, true);
                        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                    }

                    item.setItemMeta(meta);
                }
                inventory.setItem(slot, item);
            }
        }

        // Info head
        if (layout != null) {
            GuiManager.GuiItem info = layout.getItem("info");
            if (info != null) {
                String levelName = lm.getLevelName(currentLevel);
                Map<String, String> ph = Map.of(
                        "%level%", String.valueOf(currentLevel),
                        "%name%", levelName,
                        "%collected%", Formatter.formatInteger(farmer.getTotalCollected())
                );
                inventory.setItem(info.slot, plugin.getGuiManager().buildItem(info, ph));
            }

            // Progress bar item
            GuiManager.GuiItem prog = layout.getItem("progress");
            if (prog != null) {
                double progress = lm.getProgress(farmer) * 100;
                long nextXp = lm.getNextLevelXP(farmer);
                String bar = lm.getProgressBar(farmer, 20);
                String nextStr = nextXp < 0 ? "MAX" : Formatter.formatInteger(nextXp);
                Map<String, String> ph = Map.of(
                        "%bar%", bar,
                        "%percent%", String.format("%.0f", progress),
                        "%next_xp%", nextStr,
                        "%current_xp%", Formatter.formatInteger(farmer.getTotalCollected())
                );
                inventory.setItem(prog.slot, plugin.getGuiManager().buildItem(prog, ph));
            }

            // Back button
            GuiManager.GuiItem back = layout.getItem("back");
            if (back != null) {
                inventory.setItem(back.slot, plugin.getGuiManager().buildItem(back, Map.of()));
                actionSlots.put(back.slot, "back");
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() != this) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player p) || !p.equals(player)) return;

        String action = actionSlots.get(event.getRawSlot());
        if (action == null) return;

        if (action.equals("back")) {
            new FarmerMenu(plugin, player, farmer).open();
            HandlerList.unregisterAll(this);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() != this) return;
        HandlerList.unregisterAll(this);
    }

    @Override
    public Inventory getInventory() { return inventory; }
}
