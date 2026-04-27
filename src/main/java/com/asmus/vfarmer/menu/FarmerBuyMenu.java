package com.asmus.vfarmer.menu;

import com.asmus.vfarmer.VFarmer;
import com.asmus.vfarmer.gui.GuiManager;
import com.asmus.vfarmer.util.ColorUtil;
import com.asmus.vfarmer.util.Formatter;
import org.bukkit.Bukkit;
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
import java.util.*;

public class FarmerBuyMenu implements Listener, InventoryHolder {
    private final VFarmer plugin; private final Player player; private final Inventory inventory;
    private final GuiManager.GuiLayout layout;
    private final Map<Integer, String> actionSlots = new HashMap<>();

    public FarmerBuyMenu(VFarmer plugin, Player player) {
        this.plugin = plugin; this.player = player;
        this.layout = plugin.getGuiManager().getLayout("buy");
        int size = layout != null ? layout.size : 27;
        String title = layout != null ? plugin.getLanguageManager().getRaw(layout.titleKey) : "Buy Farmer";
        this.inventory = Bukkit.createInventory(this, size, ColorUtil.toComponent(title));
    }

    public void open() { build(); player.openInventory(inventory); Bukkit.getPluginManager().registerEvents(this, plugin); }

    private void build() {
        inventory.clear(); actionSlots.clear();
        double cost = plugin.getConfig().getDouble("farmer-cost", 8000.0);
        if (layout != null) {
            if (layout.filler != null) { ItemStack f = plugin.getGuiManager().createFiller(layout.filler); for (int i=0;i<inventory.getSize();i++) inventory.setItem(i,f); }
            for (var e : layout.items.entrySet()) {
                GuiManager.GuiItem gi = e.getValue();
                ItemStack item = plugin.getGuiManager().buildItem(gi, Map.of("%cost%", Formatter.formatMoney(cost)));
                inventory.setItem(gi.slot, item); actionSlots.put(gi.slot, gi.action);
            }
        }
    }

    @EventHandler public void onClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() != this) return; event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player p) || !p.equals(player)) return;
        String action = actionSlots.get(event.getRawSlot()); if (action == null) return;
        switch (action) {
            case "buy" -> { player.closeInventory(); plugin.getFarmerManager().buyFarmer(player); }
            case "cancel" -> player.closeInventory();
        }
    }

    @EventHandler public void onInventoryClose(InventoryCloseEvent event) { if (event.getInventory().getHolder() != this) return; HandlerList.unregisterAll(this); }
    @Override public Inventory getInventory() { return inventory; }
}
