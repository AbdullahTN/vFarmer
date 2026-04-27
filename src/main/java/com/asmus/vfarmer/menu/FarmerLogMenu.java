package com.asmus.vfarmer.menu;

import com.asmus.vfarmer.VFarmer;
import com.asmus.vfarmer.data.Farmer;
import com.asmus.vfarmer.data.FarmerLog;
import com.asmus.vfarmer.gui.GuiManager;
import com.asmus.vfarmer.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.text.SimpleDateFormat;
import java.util.*;

public class FarmerLogMenu implements Listener, InventoryHolder {
    private final VFarmer plugin; private final Player player; private final Farmer farmer;
    private final Inventory inventory; private final GuiManager.GuiLayout layout;
    private final Map<Integer, String> actionSlots = new HashMap<>();
    private final SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm");

    public FarmerLogMenu(VFarmer plugin, Player player, Farmer farmer) {
        this.plugin = plugin; this.player = player; this.farmer = farmer;
        this.layout = plugin.getGuiManager().getLayout("log");
        int size = layout != null ? layout.size : 54;
        String title = layout != null ? plugin.getLanguageManager().getRaw(layout.titleKey) : "Log";
        this.inventory = Bukkit.createInventory(this, size, ColorUtil.toComponent(title));
    }

    public void open() { build(); player.openInventory(inventory); Bukkit.getPluginManager().registerEvents(this, plugin); }

    private void build() {
        inventory.clear(); actionSlots.clear();
        // Buttons
        if (layout != null) for (var e : layout.items.entrySet()) {
            GuiManager.GuiItem gi = e.getValue();
            inventory.setItem(gi.slot, plugin.getGuiManager().buildItem(gi, Map.of())); actionSlots.put(gi.slot, gi.action);
        }
        // Log entries - read log-slots from config
        List<Integer> logSlots;
        try { var f = new java.io.File(plugin.getDataFolder(),"gui/log.yml"); var cfg = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(f); logSlots = cfg.getIntegerList("log-slots"); }
        catch (Exception e) { logSlots = new ArrayList<>(); for (int i=0;i<45;i++) logSlots.add(i); }

        List<FarmerLog> logs = farmer.getLogs();
        String lang = "menus.log.items.entry.";
        for (int i = 0; i < Math.min(logs.size(), logSlots.size()); i++) {
            FarmerLog log = logs.get(i); int slot = logSlots.get(i);
            ItemStack item = new ItemStack(log.getMaterial() != null ? log.getMaterial() : Material.PAPER);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.displayName(ColorUtil.toComponent(plugin.getLanguageManager().getMessage(lang + "name")));
                List<String> lore = new ArrayList<>();
                lore.add(plugin.getLanguageManager().getMessage(lang+"date").replace("%date%", df.format(new Date(log.getTimestamp()))));
                lore.add(plugin.getLanguageManager().getMessage(lang+"user").replace("%user%", log.getUser()));
                lore.add(plugin.getLanguageManager().getMessage(lang+"action").replace("%action%", log.getAction()));
                if (log.getDetail()!=null&&!log.getDetail().isEmpty()) lore.add(plugin.getLanguageManager().getMessage(lang+"detail-prefix")+log.getDetail());
                meta.lore(lore.stream().map(ColorUtil::toComponent).toList()); item.setItemMeta(meta);
            }
            inventory.setItem(slot, item);
        }
    }

    @EventHandler public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder()!=this) return; event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player p) || !p.equals(player)) return;
        String action = actionSlots.get(event.getRawSlot()); if (action==null) return;
        switch (action) {
            case "back" -> { new FarmerMenu(plugin,player,farmer).open(); HandlerList.unregisterAll(this); }
            case "clear" -> { farmer.getLogs().clear(); player.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.logs-cleared"))); build(); }
        }
    }

    @EventHandler public void onInventoryClose(InventoryCloseEvent event) { if (event.getInventory().getHolder()!=this) return; HandlerList.unregisterAll(this); }
    @Override public Inventory getInventory() { return inventory; }
}
