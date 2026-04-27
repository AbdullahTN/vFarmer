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
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class FarmerMenu implements Listener, InventoryHolder {

    private final VFarmer plugin;
    private final Player player;
    private final Farmer farmer;
    private final Inventory inventory;
    private final GuiManager.GuiLayout layout;
    private final Map<Integer, Material> productSlots = new HashMap<>();
    private final Map<Integer, String> actionSlots = new HashMap<>(); // slot -> action id
    private final Map<Integer, Double> lastAmounts = new HashMap<>();
    private int page;
    private int maxPages;
    private BukkitTask liveTask;
    private boolean closed;

    public FarmerMenu(VFarmer plugin, Player player, Farmer farmer) {
        this.plugin = plugin; this.player = player; this.farmer = farmer;
        this.layout = plugin.getGuiManager().getLayout("farmer");
        int size = layout != null ? layout.size : 54;
        String title = layout != null ? plugin.getLanguageManager().getRaw(layout.titleKey) : "Farmer";
        this.inventory = Bukkit.createInventory(this, size, ColorUtil.toComponent(title));
    }

    public void open() { build(); player.openInventory(inventory); Bukkit.getPluginManager().registerEvents(this, plugin); startLive(); }

    // ===== LIVE UPDATE =====
    private void startLive() {
        liveTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (closed || !player.isOnline() || player.getOpenInventory().getTopInventory().getHolder() != this) { stopLive(); return; }
            boolean changed = false;
            for (var e : productSlots.entrySet()) {
                ProductData d = farmer.getProductData(e.getValue());
                Double last = lastAmounts.get(e.getKey());
                if (last == null || Math.abs(last - d.getAmount()) >= 0.01) {
                    ProductConfig cfg = plugin.getConfigManager().getProduct(e.getValue());
                    if (cfg != null) { inventory.setItem(e.getKey(), buildProductItem(e.getValue(), cfg, d)); lastAmounts.put(e.getKey(), d.getAmount()); changed = true; }
                }
            }
            if (changed) updateDynamic();
        }, 20L, 20L);
    }
    private void stopLive() { if (liveTask != null) { liveTask.cancel(); liveTask = null; } }

    // ===== BUILD =====
    private void build() {
        inventory.clear(); productSlots.clear(); actionSlots.clear(); lastAmounts.clear();

        // Filler
        if (layout != null && layout.filler != null) {
            ItemStack filler = plugin.getGuiManager().createFiller(layout.filler);
            for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, filler);
        }

        // Products
        List<Map.Entry<Material, ProductConfig>> prods = getFiltered();
        List<Integer> pSlots = layout != null ? layout.productSlots : List.of(1,2,3,4,5,6,7,10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34);
        maxPages = Math.max(1, (int)Math.ceil((double)prods.size() / pSlots.size()));
        if (page >= maxPages) page = maxPages - 1;

        int start = page * pSlots.size();
        for (int i = 0; i < pSlots.size(); i++) {
            int idx = start + i; int slot = pSlots.get(i);
            if (idx < prods.size()) {
                var e = prods.get(idx); ProductConfig cfg = e.getValue(); ProductData d = farmer.getProductData(e.getKey());
                inventory.setItem(slot, buildProductItem(e.getKey(), cfg, d));
                productSlots.put(slot, e.getKey()); lastAmounts.put(slot, d.getAmount());
            } else { inventory.setItem(slot, null); }
        }

        // Buttons from config
        if (layout != null) for (var e : layout.items.entrySet()) {
            GuiManager.GuiItem gi = e.getValue();
            int slot = gi.slot;
            if (pSlots.contains(slot)) continue; // Don't overwrite product slots
            ItemStack item = buildButton(gi);
            if (item != null) { inventory.setItem(slot, item); actionSlots.put(slot, gi.action); }
        }

        // Dynamic buttons that need state
        placeStateful();
    }

    private void placeStateful() {
        if (layout == null) return;
        // Collection
        GuiManager.GuiItem coll = layout.getItem("collection");
        if (coll != null) {
            boolean on = farmer.isCollecting();
            String matStr = on ? coll.material : (layout.items.get("collection") != null ? layout.items.get("collection").material : "RED_DYE");
            // Read material-off from config if available
            var collSection = getGuiConfig("collection");
            if (!on && collSection != null) { String mOff = collSection.get("material-off"); if (mOff != null) matStr = mOff; }
            String nameKey = on ? coll.nameKey : getGuiConfigValue("collection", "name-off", coll.nameKey);
            String loreKey = on ? coll.loreKey : getGuiConfigValue("collection", "lore-off", coll.loreKey);
            inventory.setItem(coll.slot, buildLangButton(matStr, nameKey, loreKey, Map.of()));
            actionSlots.put(coll.slot, "collection");
        }

        // Filter
        GuiManager.GuiItem flt = layout.getItem("filter");
        if (flt != null) {
            var mode = farmer.getFilterMode();
            Material fIcon = switch(mode) { case CLASSIC->Material.HOPPER; case FAVORITES->Material.NETHER_STAR; case IN_STOCK->Material.CHEST; case COLLECTING->Material.LIME_DYE; case NOT_COLLECTING->Material.RED_DYE; case SELLING->Material.GOLD_INGOT; case NOT_SELLING->Material.BARRIER; };
            List<String> fLore = new ArrayList<>(plugin.getLanguageManager().getMessageList(flt.loreKey).stream().map(s->s.replace("%mode%",filterName(mode))).toList());
            fLore.add(""); fLore.add("§7─────────────────");
            for (var fm : Farmer.FilterMode.values()) fLore.add((fm==mode?" §a▸ ":" §7▹ §8")+filterName(fm));
            fLore.add(""); fLore.add(plugin.getLanguageManager().getMessage("messages.filter-left-click")); fLore.add(plugin.getLanguageManager().getMessage("messages.filter-right-click"));
            inventory.setItem(flt.slot, btn(fIcon, plugin.getLanguageManager().getMessage(flt.nameKey).replace("%mode%",filterName(mode)), fLore));
            actionSlots.put(flt.slot, "filter");
        }

        // XP / AutoSell
        updateDynamic();

        // Sell All
        GuiManager.GuiItem sa = layout.getItem("sell-all");
        if (sa != null) {
            inventory.setItem(sa.slot, buildLangButton(sa.material, sa.nameKey, sa.loreKey, Map.of("%tax%", Formatter.formatPercent(plugin.getZeroTaxManager().getTaxRate()))));
            actionSlots.put(sa.slot, "sell-all");
        }

        // Upgrade All
        GuiManager.GuiItem ua = layout.getItem("upgrade-all");
        if (ua != null) {
            double upgCost = calculateTotalUpgradeCost();
            int nextLvl = calculateNextUpgradeLevel();
            String loreKey = upgCost > 0 ? ua.loreKey : getGuiConfigValue("upgrade-all", "lore-max", ua.loreKey);
            inventory.setItem(ua.slot, buildLangButton(ua.material, ua.nameKey, loreKey, Map.of("%cost%", Formatter.formatMoney(upgCost), "%next%", String.valueOf(nextLvl))));
            actionSlots.put(ua.slot, "upgrade-all");
        }

        // Page nav
        if (maxPages > 1) {
            GuiManager.GuiItem pi = layout.getItem("page-info");
            if (pi != null) inventory.setItem(pi.slot, btn(Material.PAPER, "§e" + (page+1) + "/" + maxPages, List.of("§7" + getFiltered().size() + " " + plugin.getLanguageManager().getMessage("messages.products-total"))));
            if (page > 0) { GuiManager.GuiItem pp = layout.getItem("previous-page"); if (pp != null) { inventory.setItem(pp.slot, btn(Material.ARROW, plugin.getLanguageManager().getMessage(pp.nameKey), List.of())); actionSlots.put(pp.slot, "previous-page"); } }
            if (page < maxPages-1) { GuiManager.GuiItem np = layout.getItem("next-page"); if (np != null) { inventory.setItem(np.slot, btn(Material.ARROW, plugin.getLanguageManager().getMessage(np.nameKey), List.of())); actionSlots.put(np.slot, "next-page"); } }
        } else {
            // Hide page nav when only 1 page — show filler instead
            GuiManager.GuiItem pp = layout.getItem("previous-page");
            GuiManager.GuiItem np = layout.getItem("next-page");
            if (pp != null && layout.filler != null) inventory.setItem(pp.slot, plugin.getGuiManager().createFiller(layout.filler));
            if (np != null && layout.filler != null) inventory.setItem(np.slot, plugin.getGuiManager().createFiller(layout.filler));
        }

        // Age button — always use material from config
        GuiManager.GuiItem ageItem = layout.getItem("age");
        if (ageItem != null) {
            String age = farmer.isBaby() ? plugin.getLanguageManager().getMessage("messages.farmer-age-baby") : plugin.getLanguageManager().getMessage("messages.farmer-age-adult");
            inventory.setItem(ageItem.slot, buildLangButton(ageItem.material, ageItem.nameKey, ageItem.loreKey, Map.of("%age%", age)));
            actionSlots.put(ageItem.slot, "age");
        }

        // Appearance button — always use material from config
        GuiManager.GuiItem appItem = layout.getItem("appearance");
        if (appItem != null) {
            String on = plugin.getLanguageManager().getMessage("messages.state-on");
            String off = plugin.getLanguageManager().getMessage("messages.state-off");
            List<String> appLore = new ArrayList<>();
            appLore.add("");
            appLore.add(plugin.getLanguageManager().getMessage("messages.settings-glow-status").replace("%status%", farmer.isGlowing() ? on : off));
            appLore.add(plugin.getLanguageManager().getMessage("messages.settings-outfit-current").replace("%type%", farmer.getVillagerType().name()));
            appLore.add("");
            appLore.add(plugin.getLanguageManager().getMessage("messages.appearance-lore-glow-toggle"));
            appLore.add(plugin.getLanguageManager().getMessage("messages.appearance-lore-outfit-change"));
            if (farmer.hasNpcSkin()) {
                appLore.add("");
                appLore.add("&7Skin: &#E6CF00" + farmer.getNpcSkin());
            }
            if (player.hasPermission("nfarmer.skin")) {
                appLore.add("");
                appLore.add(plugin.getLanguageManager().getMessage("messages.appearance-lore-skin-menu"));
            }
            String appName = plugin.getLanguageManager().getMessage(appItem.nameKey.isEmpty() ? "messages.settings-outfit-name" : appItem.nameKey);
            ItemStack appStack = GuiManager.resolveItem(appItem.material);
            inventory.setItem(appItem.slot, btnItem(appStack, appName, appLore));
            actionSlots.put(appItem.slot, "appearance");
        }
    }

    private void updateDynamic() {
        if (layout == null) return;
        GuiManager.GuiItem xpI = layout.getItem("xp");
        if (xpI != null) {
            String st = farmer.isCollectXP() ? plugin.getLanguageManager().getMessage("messages.state-on") : plugin.getLanguageManager().getMessage("messages.state-off");
            Map<String, String> xpPh = new HashMap<>(Map.of("%status%", st, "%xp%", Formatter.formatInteger(farmer.getStoredXP())));
            if (plugin.getFarmerLevelManager().isEnabled()) {
                int lvl = plugin.getFarmerLevelManager().getLevel(farmer);
                String lvlName = plugin.getFarmerLevelManager().getLevelName(lvl);
                xpPh.put("%level%", String.valueOf(lvl));
                xpPh.put("%level_name%", lvlName);
            }
            inventory.setItem(xpI.slot, buildLangButton(xpI.material, xpI.nameKey, xpI.loreKey, xpPh));
        }
        GuiManager.GuiItem asI = layout.getItem("auto-sell");
        if (asI != null) {
            String st = farmer.isAutoSellActive() ? plugin.getLanguageManager().getMessage("messages.state-on") : plugin.getLanguageManager().getMessage("messages.state-off");
            String time = farmer.hasAutoSellTime() ? plugin.getZeroTaxManager().formatTime(farmer.getAutoSellExpiration()-System.currentTimeMillis()) : plugin.getLanguageManager().getMessage("messages.none");
            String act = plugin.getLanguageManager().getRaw(farmer.hasAutoSellTime() ? "messages.auto-sell-action-extend" : "messages.auto-sell-action-buy");
            inventory.setItem(asI.slot, buildLangButton(asI.material, asI.nameKey, asI.loreKey, Map.of("%status%", st, "%balance%", Formatter.formatMoney(farmer.getBalance()), "%time%", time, "%action%", act)));
        }
    }

    // ===== FILTER =====
    private List<Map.Entry<Material, ProductConfig>> getFiltered() {
        List<Map.Entry<Material, ProductConfig>> r = new ArrayList<>();
        for (var e : plugin.getConfigManager().getProducts().entrySet()) {
            ProductData d = farmer.getProductData(e.getKey());
            boolean p = switch(farmer.getFilterMode()) { case CLASSIC->true; case FAVORITES->d.isFavorite(); case IN_STOCK->d.getAmount()>0; case COLLECTING->d.isCollectEnabled(); case NOT_COLLECTING->!d.isCollectEnabled(); case SELLING->d.isSelling(); case NOT_SELLING->!d.isSelling(); };
            if (p) r.add(e);
        } return r;
    }
    private String filterName(Farmer.FilterMode m) {
        return switch(m) { case CLASSIC->plugin.getLanguageManager().getMessage("messages.filter-mode-classic"); case FAVORITES->plugin.getLanguageManager().getMessage("messages.filter-mode-favorites");
            case IN_STOCK->plugin.getLanguageManager().getMessage("messages.filter-mode-in-stock"); case COLLECTING->plugin.getLanguageManager().getMessage("messages.filter-mode-collecting");
            case NOT_COLLECTING->plugin.getLanguageManager().getMessage("messages.filter-mode-not-collecting"); case SELLING->plugin.getLanguageManager().getMessage("messages.filter-mode-selling"); case NOT_SELLING->plugin.getLanguageManager().getMessage("messages.filter-mode-not-selling"); };
    }

    // ===== PRODUCT ITEM =====
    private ItemStack buildProductItem(Material mat, ProductConfig cfg, ProductData d) {
        ItemStack item = new ItemStack(mat, Math.max(1, Math.min(64, (int)d.getAmount())));
        ItemMeta meta = item.getItemMeta(); if (meta == null) return item;
        long limit = cfg.getLimit(d.getLevel()); double ratio = limit > 0 ? (d.getAmount()/limit)*100 : 0;
        double net = d.getAmount() * cfg.getPrice() * (1-plugin.getZeroTaxManager().getTaxRate()/100.0);
        String rc = ratio<50?"§a":ratio<80?"§e":ratio<95?"§6":"§c";
        List<String> lore = plugin.getLanguageManager().getMessageList("menus.farmer.product-item.lore").stream().map(s -> s
            .replace("%amount%", Formatter.formatInteger(d.getAmount())).replace("%limit%", Formatter.formatInteger(limit))
            .replace("%ratio%", rc+Formatter.formatPercent(ratio)+"%").replace("%price%", Formatter.formatMoney(cfg.getPrice()))
            .replace("%net_value%", Formatter.formatMoney(net)).replace("%status_sell%", d.isSelling()?"§a✔":"§c✖")
            .replace("%status_collect%", d.isCollectEnabled()?"§a✔":"§c✖").replace("%status_favorite%", d.isFavorite()?"§a★":"§7☆")
            .replace("%prod_min%", Formatter.formatCompact(d.getProductionPerMinute(10))).replace("%pred_min%", Formatter.formatCompact(d.getPredictedPerMinute()))
            .replace("%prod_hour%", Formatter.formatCompact(d.getProductionPerHour())).replace("%pred_hour%", Formatter.formatCompact(d.getPredictedPerHour()))
            .replace("%prod_day%", Formatter.formatCompact(d.getProductionPerDay())).replace("%pred_day%", Formatter.formatCompact(d.getPredictedPerDay()))
        ).toList();
        meta.lore(lore.stream().map(ColorUtil::toComponent).toList()); item.setItemMeta(meta); return item;
    }

    // ===== CLICK =====
    @EventHandler public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() != this) return; event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player p) || !p.equals(player)) return;
        int slot = event.getRawSlot(); if (slot < 0 || slot >= inventory.getSize()) return;

        if (productSlots.containsKey(slot)) {
            Material mat = productSlots.get(slot); ProductData d = farmer.getProductData(mat);
            if (event.isShiftClick() && event.isLeftClick()) {
                d.setFavorite(!d.isFavorite());
                String msg = d.isFavorite()
                    ? plugin.getLanguageManager().getMessage("messages.favorite-added").replace("%product%", mat.name())
                    : plugin.getLanguageManager().getMessage("messages.favorite-removed").replace("%product%", mat.name());
                player.sendMessage(ColorUtil.toComponent(msg));
                build();
            }
            else if (event.isShiftClick() && event.isRightClick()) { plugin.getFarmerManager().withdrawItems(farmer,player,mat,(int)d.getAmount()); build(); }
            else if (event.isRightClick()) { plugin.getFarmerManager().withdrawItems(farmer,player,mat,64); build(); }
            else if (event.isLeftClick()) { stopLive(); new ProductManagementMenu(plugin,player,farmer,mat).open(); HandlerList.unregisterAll(this); }
            return;
        }

        String action = actionSlots.get(slot);
        if (action == null) return;
        switch (action) {
            case "collection" -> { farmer.setCollecting(!farmer.isCollecting()); build(); }
            case "filter" -> { var v = Farmer.FilterMode.values(); farmer.setFilterMode(v[(farmer.getFilterMode().ordinal() + (event.isLeftClick()?1:v.length-1)) % v.length]); page=0; build(); }
            case "history" -> { stopLive(); new FarmerLogMenu(plugin,player,farmer).open(); HandlerList.unregisterAll(this); }
            case "sell-all" -> { double e2 = plugin.getFarmerManager().sellAll(farmer,player); player.sendMessage(ColorUtil.toComponent(e2>0 ? plugin.getLanguageManager().getMessage("messages.bulk-sell-earned").replace("%amount%",Formatter.formatMoney(e2)) : plugin.getLanguageManager().getMessage("messages.bulk-sell-none"))); build(); }
            case "upgrade-all" -> { plugin.getFarmerManager().upgradeAll(farmer,player); build(); }
            case "xp" -> {
                if (event.isShiftClick() && event.isRightClick() && plugin.getFarmerLevelManager().isEnabled()) {
                    // Open level menu
                    stopLive(); new FarmerLevelMenu(plugin, player, farmer).open(); HandlerList.unregisterAll(this); return;
                } else if (event.isShiftClick() && event.isLeftClick()) {
                    farmer.setCollectXP(!farmer.isCollectXP());
                } else if (event.isLeftClick()) {
                    plugin.getFarmerManager().withdrawXP(farmer, player, farmer.getStoredXP());
                } else if (event.isRightClick()) {
                    plugin.getFarmerManager().withdrawXP(farmer, player, 100);
                }
                build();
            }
            case "auto-sell" -> { if (event.isLeftClick() && farmer.hasAutoSellTime()) { farmer.setAutoSellActive(!farmer.isAutoSellActive()); if (farmer.isAutoSellActive()) plugin.getFarmerManager().startAutoSell(farmer); else plugin.getFarmerManager().stopAutoSell(farmer); } else if (event.isRightClick()) plugin.getFarmerManager().withdrawBalance(farmer,player); build(); }
            case "settings" -> { stopLive(); new FarmerSettingsMenu(plugin,player,farmer).open(); HandlerList.unregisterAll(this); }
            case "move" -> { long cd=30000; if (System.currentTimeMillis()-farmer.getLastMoveCooldown()<cd) { player.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.move-cooldown").replace("%time%",((cd-(System.currentTimeMillis()-farmer.getLastMoveCooldown()))/1000)+"s"))); return; } if (plugin.getFarmerManager().moveFarmer(farmer,player.getLocation())) { farmer.setLastMoveCooldown(System.currentTimeMillis()); player.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.farmer-moved"))); } else player.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.cannot-move-outside-island"))); player.closeInventory(); }
            case "age" -> {
                long ageCd = 10000;
                if (System.currentTimeMillis()-farmer.getLastAgeCooldown()<ageCd) { player.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.age-change-cooldown").replace("%time%",((ageCd-(System.currentTimeMillis()-farmer.getLastAgeCooldown()))/1000)+"s"))); return; }
                farmer.setBaby(!farmer.isBaby()); farmer.setLastAgeCooldown(System.currentTimeMillis());
                plugin.getFarmerManager().updateVillager(farmer); plugin.getFarmerManager().spawnVillager(farmer); build();
            }
            case "appearance" -> {
                if (event.isShiftClick() && player.hasPermission("nfarmer.skin")) {
                    // Open skin menu (VIP)
                    stopLive(); new FarmerSkinMenu(plugin, player, farmer).open(); HandlerList.unregisterAll(this); return;
                }
                if (event.isLeftClick()) { farmer.setGlowing(!farmer.isGlowing()); plugin.getFarmerManager().updateVillager(farmer); }
                else if (event.isRightClick()) { org.bukkit.entity.Villager.Type[] types = org.bukkit.entity.Villager.Type.values(); farmer.setVillagerType(types[(farmer.getVillagerType().ordinal()+1)%types.length]); plugin.getFarmerManager().updateVillager(farmer); }
                build();
            }
            case "previous-page" -> { if (page > 0) { page--; build(); } }
            case "next-page" -> { if (page < maxPages-1) { page++; build(); } }
        }
    }

    @EventHandler public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() != this) return; closed = true; stopLive(); HandlerList.unregisterAll(this); plugin.getFarmerManager().saveFarmer(farmer);
    }
    @Override public Inventory getInventory() { return inventory; }

    // ===== HELPERS =====
    private ItemStack btn(Material m, String name, List<String> lore) { ItemStack i = new ItemStack(m); ItemMeta meta = i.getItemMeta(); if (meta!=null) { meta.displayName(ColorUtil.toComponent(name)); if (lore!=null&&!lore.isEmpty()) meta.lore(lore.stream().map(ColorUtil::toComponent).toList()); i.setItemMeta(meta); } return i; }
    private ItemStack btnItem(ItemStack i, String name, List<String> lore) { ItemMeta meta = i.getItemMeta(); if (meta!=null) { meta.displayName(ColorUtil.toComponent(name)); if (lore!=null&&!lore.isEmpty()) meta.lore(lore.stream().map(ColorUtil::toComponent).toList()); i.setItemMeta(meta); } return i; }
    private ItemStack buildButton(GuiManager.GuiItem gi) { return buildLangButton(gi.material, gi.nameKey, gi.loreKey, Map.of()); }
    private ItemStack buildLangButton(String matStr, String nameKey, String loreKey, Map<String,String> ph) {
        ItemStack item = GuiManager.resolveItem(matStr);
        String name = nameKey.isEmpty() ? "" : plugin.getLanguageManager().getMessage(nameKey);
        List<String> lore = loreKey.isEmpty() ? List.of() : new ArrayList<>(plugin.getLanguageManager().getMessageList(loreKey));
        for (var e : ph.entrySet()) { name = name.replace(e.getKey(), e.getValue()); lore = lore.stream().map(s->s.replace(e.getKey(),e.getValue())).toList(); }
        return btnItem(item, name, lore);
    }
    private Map<String,String> getGuiConfig(String itemId) {
        if (layout == null) return null; var gi = layout.getItem(itemId); if (gi == null) return null;
        // Read raw config for extra keys like material-off, name-off, lore-off
        try { var file = new java.io.File(plugin.getDataFolder(), "gui/farmer.yml"); var cfg = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
            var sec = cfg.getConfigurationSection("items." + itemId); if (sec == null) return null;
            Map<String,String> map = new HashMap<>(); for (String k : sec.getKeys(false)) map.put(k, sec.getString(k, "")); return map;
        } catch (Exception e) { return null; }
    }
    private String getGuiConfigValue(String itemId, String key, String def) { var m = getGuiConfig(itemId); return m != null && m.containsKey(key) ? m.get(key) : def; }

    private double calculateTotalUpgradeCost() {
        double total = 0;
        for (var e : farmer.getStorage().entrySet()) {
            Material mat = Material.matchMaterial(e.getKey()); if (mat == null) continue;
            ProductConfig cfg = plugin.getConfigManager().getProduct(mat); if (cfg == null) continue;
            int next = e.getValue().getLevel() + 1;
            if (cfg.hasLevel(next)) total += cfg.getCost(next);
        }
        return total;
    }

    private int calculateNextUpgradeLevel() {
        int min = Integer.MAX_VALUE;
        for (var e : farmer.getStorage().entrySet()) {
            Material mat = Material.matchMaterial(e.getKey()); if (mat == null) continue;
            ProductConfig cfg = plugin.getConfigManager().getProduct(mat); if (cfg == null) continue;
            int next = e.getValue().getLevel() + 1;
            if (cfg.hasLevel(next) && next < min) min = next;
        }
        return min == Integer.MAX_VALUE ? 0 : min;
    }
}
