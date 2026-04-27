package com.asmus.vfarmer.menu;

import com.asmus.vfarmer.VFarmer;
import com.asmus.vfarmer.data.Farmer;
import com.asmus.vfarmer.gui.GuiManager;
import com.asmus.vfarmer.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.io.File;
import java.util.*;

public class FarmerSettingsMenu implements Listener, InventoryHolder {
    private final VFarmer plugin; private final Player player; private final Farmer farmer;
    private final Inventory inventory; private final GuiManager.GuiLayout layout;
    private final Map<Integer, String> actionSlots = new HashMap<>();

    public FarmerSettingsMenu(VFarmer plugin, Player player, Farmer farmer) {
        this.plugin = plugin; this.player = player; this.farmer = farmer;
        this.layout = plugin.getGuiManager().getLayout("settings");
        int size = layout != null ? layout.size : 36;
        String title = layout != null ? plugin.getLanguageManager().getRaw(layout.titleKey) : "Settings";
        this.inventory = Bukkit.createInventory(this, size, ColorUtil.toComponent(title));
    }

    public void open() { build(); player.openInventory(inventory); Bukkit.getPluginManager().registerEvents(this, plugin); }

    private void build() {
        inventory.clear(); actionSlots.clear();
        String on = plugin.getLanguageManager().getMessage("messages.state-on");
        String off = plugin.getLanguageManager().getMessage("messages.state-off");

        if (layout != null) {
            if (layout.filler != null) { ItemStack f = plugin.getGuiManager().createFiller(layout.filler); for (int i=0;i<inventory.getSize();i++) inventory.setItem(i,f); }

            // Read raw config for material-off support
            Map<String,Map<String,String>> rawCfg = readRawGuiConfig();

            for (var e : layout.items.entrySet()) {
                GuiManager.GuiItem gi = e.getValue();
                String action = gi.action;
                boolean state = getState(action);
                String matStr = gi.material;
                Map<String,String> raw = rawCfg.getOrDefault(e.getKey(), Map.of());

                // Use material-off if state is false
                if (!state && raw.containsKey("material-off")) matStr = raw.get("material-off");

                // Build placeholders
                Map<String,String> ph = new HashMap<>();
                ph.put("%status%", state ? on : off);
                if (action.equals("age")) {
                    String age = farmer.isBaby() ? plugin.getLanguageManager().getMessage("messages.farmer-age-baby") : plugin.getLanguageManager().getMessage("messages.farmer-age-adult");
                    ph.put("%age%", age);
                }
                if (action.equals("glow")) {
                    ph.put("%status%", farmer.isGlowing() ? on : off);
                }
                if (action.equals("outfit")) {
                    ph.put("%type%", farmer.getVillagerType().name());
                }
                if (action.equals("vacuum")) {
                    ph.put("%radius%", String.valueOf(farmer.getVacuumRadius()));
                    ph.put("%status%", farmer.getVacuumRadius() > 0 ? on : off);
                }

                String name = gi.nameKey.isEmpty() ? "" : plugin.getLanguageManager().getMessage(gi.nameKey);
                List<String> lore;
                if (gi.loreKey.isEmpty()) {
                    lore = new ArrayList<>();
                    if (action.equals("glow")) {
                        lore.add(""); lore.add(applyPH(plugin.getLanguageManager().getMessage("messages.settings-glow-status"), ph));
                        lore.add(""); lore.add(plugin.getLanguageManager().getMessage("messages.settings-glow-click"));
                    } else if (action.equals("outfit")) {
                        lore.add(""); lore.add(applyPH(plugin.getLanguageManager().getMessage("messages.settings-outfit-current"), ph));
                        lore.add(""); lore.add(plugin.getLanguageManager().getMessage("messages.settings-outfit-click"));
                    }
                } else {
                    lore = new ArrayList<>(plugin.getLanguageManager().getMessageList(gi.loreKey));
                }
                for (var p2 : ph.entrySet()) { name = name.replace(p2.getKey(), p2.getValue()); lore = lore.stream().map(s->s.replace(p2.getKey(), p2.getValue())).toList(); }

                ItemStack item = com.asmus.vfarmer.gui.GuiManager.resolveItem(matStr); ItemMeta meta = item.getItemMeta();
                if (meta != null) { meta.displayName(ColorUtil.toComponent(name)); if (!lore.isEmpty()) meta.lore(lore.stream().map(ColorUtil::toComponent).toList()); item.setItemMeta(meta); }
                inventory.setItem(gi.slot, item); actionSlots.put(gi.slot, action);
            }
        }
    }

    private boolean getState(String action) {
        return switch (action) {
            case "player-drops" -> farmer.isCollectPlayerDrops();
            case "spawner-kill" -> farmer.isKillSpawnerMobs();
            case "auto-harvest" -> farmer.isAutoHarvest();
            case "hologram" -> farmer.isShowHologram();
            case "age" -> !farmer.isBaby();
            case "glow" -> farmer.isGlowing();
            default -> true;
        };
    }

    @EventHandler public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() != this) return; event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player p) || !p.equals(player)) return;
        String action = actionSlots.get(event.getRawSlot()); if (action == null) return;

        switch (action) {
            case "player-drops" -> { farmer.setCollectPlayerDrops(!farmer.isCollectPlayerDrops()); build(); }
            case "spawner-kill" -> {
                if (!plugin.getFarmerLevelManager().isUnlocked(farmer, "spawner-kill")) {
                    player.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.farmer-level-locked").replace("%level%", getRequiredLevel("spawner-kill"))));
                    return;
                }
                farmer.setKillSpawnerMobs(!farmer.isKillSpawnerMobs()); build();
            }
            case "auto-harvest" -> {
                if (!plugin.getFarmerLevelManager().isUnlocked(farmer, "auto-harvest")) {
                    player.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.farmer-level-locked").replace("%level%", getRequiredLevel("auto-harvest"))));
                    return;
                }
                farmer.setAutoHarvest(!farmer.isAutoHarvest()); build();
            }
            case "hologram" -> {
                farmer.setShowHologram(!farmer.isShowHologram());
                if (farmer.isShowHologram()) plugin.getFarmerManager().spawnVillager(farmer);
                else { if (farmer.getTextDisplayId()!=null) { var ent = Bukkit.getEntity(farmer.getTextDisplayId()); if (ent!=null) ent.remove(); farmer.setTextDisplayId(null); } }
                build();
            }
            case "age" -> {
                long cd = 10000;
                if (System.currentTimeMillis()-farmer.getLastAgeCooldown()<cd) { player.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.age-change-cooldown").replace("%time%",((cd-(System.currentTimeMillis()-farmer.getLastAgeCooldown()))/1000)+"s"))); return; }
                farmer.setBaby(!farmer.isBaby()); farmer.setLastAgeCooldown(System.currentTimeMillis());
                plugin.getFarmerManager().updateVillager(farmer); plugin.getFarmerManager().spawnVillager(farmer); build();
            }
            case "glow" -> { farmer.setGlowing(!farmer.isGlowing()); plugin.getFarmerManager().updateVillager(farmer); build(); }
            case "outfit" -> { Villager.Type[] t = Villager.Type.values(); farmer.setVillagerType(t[(farmer.getVillagerType().ordinal()+1)%t.length]); plugin.getFarmerManager().updateVillager(farmer); build(); }
            case "vacuum" -> {
                if (!plugin.getFarmerLevelManager().isUnlocked(farmer, "vacuum")) {
                    player.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.farmer-level-locked").replace("%level%", getRequiredLevel("vacuum"))));
                    return;
                }
                if (event.isLeftClick()) {
                    // Upgrade vacuum radius
                    int current = farmer.getVacuumRadius();
                    var levels = plugin.getConfig().getConfigurationSection("vacuum.levels");
                    if (levels == null) { player.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.vacuum-max-level"))); return; }
                    int nextLevel = -1; int nextRadius = -1;
                    for (String key : levels.getKeys(false)) {
                        int lvl = Integer.parseInt(key);
                        int rad = levels.getInt(key);
                        if (rad > current) { nextLevel = lvl; nextRadius = rad; break; }
                    }
                    if (nextLevel == -1) { player.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.vacuum-max-level"))); return; }
                    double cost = plugin.getConfig().getDouble("vacuum.upgrade-cost-per-level", 5000) * nextLevel;
                    if (!plugin.hasEconomy() || !plugin.getEconomy().has(player, cost)) {
                        player.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.insufficient-balance").replace("%cost%", com.asmus.vfarmer.util.Formatter.formatMoney(cost))));
                        return;
                    }
                    plugin.getEconomy().withdrawPlayer(player, cost);
                    farmer.setVacuumRadius(nextRadius);
                    farmer.setVacuumEnabled(true);
                    player.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.vacuum-upgraded")
                            .replace("%radius%", String.valueOf(nextRadius)).replace("%cost%", com.asmus.vfarmer.util.Formatter.formatMoney(cost))));
                } else {
                    // Toggle vacuum on/off without losing purchased radius
                    farmer.setVacuumEnabled(!farmer.isVacuumEnabled());
                }
                build();
            }
            case "back" -> { new FarmerMenu(plugin,player,farmer).open(); HandlerList.unregisterAll(this); }
        }
    }

    @EventHandler public void onInventoryClose(InventoryCloseEvent event) { if (event.getInventory().getHolder()!=this) return; HandlerList.unregisterAll(this); plugin.getFarmerManager().saveFarmer(farmer); }
    @Override public Inventory getInventory() { return inventory; }
    private String applyPH(String s, Map<String,String> ph) { for (var e:ph.entrySet()) s=s.replace(e.getKey(),e.getValue()); return s; }
    private Map<String,Map<String,String>> readRawGuiConfig() {
        Map<String,Map<String,String>> r = new HashMap<>();
        try { File f = new File(plugin.getDataFolder(),"gui/settings.yml"); var cfg = YamlConfiguration.loadConfiguration(f);
            var sec = cfg.getConfigurationSection("items"); if (sec==null) return r;
            for (String k : sec.getKeys(false)) { var s2 = sec.getConfigurationSection(k); if (s2==null) continue;
                Map<String,String> m = new HashMap<>(); for (String k2 : s2.getKeys(false)) m.put(k2, s2.getString(k2,"")); r.put(k, m); }
        } catch (Exception e) {} return r;
    }

    private String getRequiredLevel(String feature) {
        var section = plugin.getConfig().getConfigurationSection("farmer-levels");
        if (section == null) return "?";
        for (String key : section.getKeys(false)) {
            if (key.equals("enabled")) continue;
            String unlock = section.getString(key + ".unlock", "");
            if (unlock.equalsIgnoreCase(feature)) {
                String name = section.getString(key + ".name", "Level " + key);
                return key + " (" + name + ")";
            }
        }
        return "?";
    }
}
