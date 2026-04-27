package com.asmus.vfarmer.menu;

import com.asmus.vfarmer.VFarmer;
import com.asmus.vfarmer.data.*;
import com.asmus.vfarmer.gui.GuiManager;
import com.asmus.vfarmer.util.ColorUtil;
import com.asmus.vfarmer.util.Formatter;
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
import java.util.*;

public class ProductManagementMenu implements Listener, InventoryHolder {
    private final VFarmer plugin; private final Player player; private final Farmer farmer; private final Material material;
    private final Inventory inventory; private final GuiManager.GuiLayout layout;
    private final Map<Integer, String> actionSlots = new HashMap<>();

    public ProductManagementMenu(VFarmer plugin, Player player, Farmer farmer, Material material) {
        this.plugin = plugin; this.player = player; this.farmer = farmer; this.material = material;
        this.layout = plugin.getGuiManager().getLayout("product-management");
        int size = layout != null ? layout.size : 27;
        String title = layout != null ? plugin.getLanguageManager().getRaw(layout.titleKey) : "Product";
        this.inventory = Bukkit.createInventory(this, size, ColorUtil.toComponent(title));
    }

    public void open() { build(); player.openInventory(inventory); Bukkit.getPluginManager().registerEvents(this, plugin); }

    private void build() {
        inventory.clear(); actionSlots.clear();
        ProductConfig cfg = plugin.getConfigManager().getProduct(material);
        ProductData d = farmer.getProductData(material);
        if (cfg == null) return;
        long limit = cfg.getLimit(d.getLevel()); int maxLvl = cfg.getMaxLevel();
        int nextLvl = d.getLevel()+1; long nextCap = cfg.hasLevel(nextLvl) ? cfg.getLimit(nextLvl) : limit;
        double upCost = cfg.hasLevel(nextLvl) ? cfg.getCost(nextLvl) : 0;
        double taxRate = plugin.getZeroTaxManager().getTaxRate();
        String sellSt = d.isSelling()?"§a✔":"§c✖"; String collSt = d.isCollectEnabled()?"§a✔":"§c✖";

        Map<String,String> ph = Map.ofEntries(
            Map.entry("%stock%", Formatter.formatInteger(d.getAmount())), Map.entry("%capacity%", Formatter.formatInteger(limit)),
            Map.entry("%level%", String.valueOf(d.getLevel())), Map.entry("%max_level%", String.valueOf(maxLvl)),
            Map.entry("%status%", sellSt), Map.entry("%tax%", Formatter.formatPercent(taxRate)),
            Map.entry("%next_level%", String.valueOf(nextLvl)), Map.entry("%next_capacity%", Formatter.formatInteger(nextCap)),
            Map.entry("%cost%", Formatter.formatMoney(upCost))
        );

        if (layout != null) {
            if (layout.filler != null) { ItemStack f = plugin.getGuiManager().createFiller(layout.filler); for (int i=0;i<inventory.getSize();i++) inventory.setItem(i,f); }
            for (var e : layout.items.entrySet()) {
                GuiManager.GuiItem gi = e.getValue();
                ItemStack item;
                if (gi.material.equals("DYNAMIC")) { item = new ItemStack(material); ItemMeta m = item.getItemMeta(); if (m!=null) { m.displayName(ColorUtil.toComponent(applyPH(plugin.getLanguageManager().getMessage(gi.nameKey),ph))); m.lore(plugin.getLanguageManager().getMessageList(gi.loreKey).stream().map(s->ColorUtil.toComponent(applyPH(s,ph))).toList()); item.setItemMeta(m); }
                } else { item = plugin.getGuiManager().buildItem(gi, ph); }
                inventory.setItem(gi.slot, item); actionSlots.put(gi.slot, gi.action);
            }
        }
    }

    @EventHandler public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() != this) return; event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player p) || !p.equals(player)) return;
        String action = actionSlots.get(event.getRawSlot()); if (action == null) return;
        ProductData d = farmer.getProductData(material);
        switch (action) {
            case "sell" -> { if (event.isLeftClick()) { double e2 = plugin.getFarmerManager().sellProduct(farmer,material,d,player); player.sendMessage(ColorUtil.toComponent(e2>0 ? plugin.getLanguageManager().getMessage("messages.items-sold").replace("%amount%",Formatter.formatMoney(e2)) : plugin.getLanguageManager().getMessage("messages.no-items-to-sell"))); } else d.setSelling(!d.isSelling()); build(); }
            case "withdraw" -> { if (event.isRightClick()) plugin.getFarmerManager().withdrawItems(farmer,player,material,(int)d.getAmount()); else plugin.getFarmerManager().withdrawItems(farmer,player,material,64); build(); }
            case "storage" -> { if (event.isLeftClick()) d.setCollectEnabled(!d.isCollectEnabled()); else plugin.getFarmerManager().upgradeStorage(farmer,material,player); build(); }
            case "back" -> { new FarmerMenu(plugin,player,farmer).open(); HandlerList.unregisterAll(this); }
        }
    }

    @EventHandler public void onInventoryClose(InventoryCloseEvent event) { if (event.getInventory().getHolder() != this) return; HandlerList.unregisterAll(this); plugin.getFarmerManager().saveFarmer(farmer); }
    @Override public Inventory getInventory() { return inventory; }
    private String applyPH(String s, Map<String,String> ph) { for (var e:ph.entrySet()) s=s.replace(e.getKey(),e.getValue()); return s; }
}
