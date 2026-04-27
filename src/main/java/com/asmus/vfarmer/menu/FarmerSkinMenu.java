package com.asmus.vfarmer.menu;

import com.asmus.vfarmer.VFarmer;
import com.asmus.vfarmer.data.Farmer;
import com.asmus.vfarmer.gui.GuiManager;
import com.asmus.vfarmer.util.ColorUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Farmer Skin/NPC management menu.
 * VIP feature — requires nfarmer.skin permission.
 *
 * Features:
 *   - Set player skin (NPC looks like any player)
 *   - Reset to default villager
 *   - Select particle effects
 *   - Toggle look-at-player
 */
public class FarmerSkinMenu implements Listener, InventoryHolder {

    private final VFarmer plugin;
    private final Player player;
    private final Farmer farmer;
    private final Inventory inventory;
    private final GuiManager.GuiLayout layout;
    private final Map<Integer, String> actionSlots = new HashMap<>();
    private boolean waitingForSkinInput = false;
    private boolean waitingForParticleInput = false;

    // Available particle effects
    private static final String[] PARTICLES = {
            "FLAME", "END_ROD", "HEART", "ENCHANT",
            "COMPOSTER", "WAX_ON", "CHERRY_LEAVES", "SMOKE"
    };

    public FarmerSkinMenu(VFarmer plugin, Player player, Farmer farmer) {
        this.plugin = plugin;
        this.player = player;
        this.farmer = farmer;
        this.layout = plugin.getGuiManager().getLayout("skin");
        int size = layout != null ? layout.size : 45;
        String title = layout != null ? plugin.getLanguageManager().getRaw(layout.titleKey) : "Farmer Skin";
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

        // Filler
        if (layout != null && layout.filler != null) {
            ItemStack filler = plugin.getGuiManager().createFiller(layout.filler);
            for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, filler);
        }

        String on = plugin.getLanguageManager().getMessage("messages.state-on");
        String off = plugin.getLanguageManager().getMessage("messages.state-off");
        String currentSkin = farmer.hasNpcSkin() ? farmer.getNpcSkin() : plugin.getLanguageManager().getMessage("messages.none");
        String currentParticle = farmer.getNpcParticle() != null ? farmer.getNpcParticle() : plugin.getLanguageManager().getMessage("messages.none");
        String npcProvider = plugin.getNpcHook().isAvailable() ? plugin.getNpcHook().getProvider().name() : "Villager";

        Map<String, String> ph = new HashMap<>();
        ph.put("%skin%", currentSkin);
        ph.put("%particle%", currentParticle);
        ph.put("%look_at%", farmer.isNpcLookAtPlayer() ? on : off);
        ph.put("%provider%", npcProvider);

        if (layout != null) {
            for (var e : layout.items.entrySet()) {
                GuiManager.GuiItem gi = e.getValue();

                // Special: info head shows current skin
                ItemStack item;
                if (gi.action.equals("none") && farmer.hasNpcSkin()) {
                    item = com.asmus.vfarmer.util.ItemUtil.createPlayerSkull(farmer.getNpcSkin());
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null && !gi.nameKey.isEmpty()) {
                        String name = plugin.getLanguageManager().getMessage(gi.nameKey);
                        for (var p2 : ph.entrySet()) name = name.replace(p2.getKey(), p2.getValue());
                        meta.displayName(ColorUtil.toComponent(name));
                        if (!gi.loreKey.isEmpty()) {
                            List<String> lore = plugin.getLanguageManager().getMessageList(gi.loreKey);
                            meta.lore(lore.stream().map(s -> { String r = s; for (var p2 : ph.entrySet()) r = r.replace(p2.getKey(), p2.getValue()); return ColorUtil.toComponent(r); }).toList());
                        }
                        item.setItemMeta(meta);
                    }
                } else {
                    item = plugin.getGuiManager().buildItem(gi, ph);
                }

                inventory.setItem(gi.slot, item);
                actionSlots.put(gi.slot, gi.action);
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

        switch (action) {
            case "set-skin" -> {
                waitingForSkinInput = true;
                player.closeInventory();
                player.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.skin-enter-name")));
            }
            case "reset-skin" -> {
                farmer.setNpcSkin(null);
                farmer.setNpcParticle(null);
                // Respawn as villager
                plugin.getFarmerManager().spawnVillager(farmer);
                player.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.skin-reset")));
                build();
            }
            case "particle" -> {
                if (event.isLeftClick()) {
                    // Cycle through particles
                    String current = farmer.getNpcParticle();
                    int idx = -1;
                    if (current != null) {
                        for (int i = 0; i < PARTICLES.length; i++) {
                            if (PARTICLES[i].equalsIgnoreCase(current)) { idx = i; break; }
                        }
                    }
                    idx = (idx + 1) % PARTICLES.length;
                    farmer.setNpcParticle(PARTICLES[idx]);
                    player.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.skin-particle-set")
                            .replace("%particle%", PARTICLES[idx])));
                } else {
                    // Remove particle
                    farmer.setNpcParticle(null);
                    player.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.skin-particle-removed")));
                }
                build();
            }
            case "look-at" -> {
                farmer.setNpcLookAtPlayer(!farmer.isNpcLookAtPlayer());
                build();
            }
            case "back" -> {
                new FarmerMenu(plugin, player, farmer).open();
                HandlerList.unregisterAll(this);
            }
        }
    }

    /**
     * Listens for chat input when player is setting skin name.
     */
    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        if (!event.getPlayer().equals(player)) return;
        if (!waitingForSkinInput && !waitingForParticleInput) return;
        event.setCancelled(true);

        String input = event.getMessage().trim();

        if (input.equalsIgnoreCase("cancel") || input.equalsIgnoreCase("iptal")) {
            waitingForSkinInput = false;
            waitingForParticleInput = false;
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.skin-cancelled")));
                open();
            });
            return;
        }

        if (waitingForSkinInput) {
            waitingForSkinInput = false;
            String skinName = input;

            Bukkit.getScheduler().runTask(plugin, () -> {
                farmer.setNpcSkin(skinName);

                // Respawn — spawnVillager handles NPC vs villager logic
                plugin.getFarmerManager().spawnVillager(farmer);
                player.sendMessage(ColorUtil.toComponent(plugin.getLanguageManager().getMessage("messages.skin-set")
                        .replace("%skin%", skinName)));
                plugin.getFarmerManager().saveFarmer(farmer);
                open();
            });
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() != this) return;
        if (!waitingForSkinInput && !waitingForParticleInput) {
            HandlerList.unregisterAll(this);
        }
        plugin.getFarmerManager().saveFarmer(farmer);
    }

    @Override
    public Inventory getInventory() { return inventory; }
}
