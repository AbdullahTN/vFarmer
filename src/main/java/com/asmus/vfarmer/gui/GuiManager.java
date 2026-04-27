package com.asmus.vfarmer.gui;

import com.asmus.vfarmer.VFarmer;
import com.asmus.vfarmer.util.ColorUtil;
import com.asmus.vfarmer.util.ItemUtil;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.*;
import java.util.logging.Level;

/**
 * Manages GUI layouts loaded from gui/*.yml files.
 * Each gui file defines:
 *   - title (lang key)
 *   - size (9/18/27/36/45/54)
 *   - items: map of button_id -> { slot, material/texture, name (lang key), lore (lang key) }
 *   - filler: material to fill empty slots (optional)
 *   - product-slots: list of slot numbers for product items (for farmer menu)
 */
public class GuiManager {

    private final VFarmer plugin;
    private final Map<String, GuiLayout> layouts;

    public GuiManager(VFarmer plugin) {
        this.plugin = plugin;
        this.layouts = new HashMap<>();
    }

    public void load() {
        layouts.clear();
        File guiDir = new File(plugin.getDataFolder(), "gui");
        if (!guiDir.exists()) {
            guiDir.mkdirs();
        }
        // Save defaults (always check for missing files, not just on first run)
        String[] defaults = {"farmer.yml", "buy.yml", "product-management.yml", "settings.yml", "log.yml", "level.yml", "skin.yml"};
        for (String name : defaults) {
            File f = new File(guiDir, name);
            if (!f.exists()) {
                try {
                    plugin.saveResource("gui/" + name, false);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Failed to save default gui/" + name, e);
                }
            }
        }

        // Load all yml files in gui/
        File[] files = guiDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;

        for (File file : files) {
            String id = file.getName().replace(".yml", "");
            try {
                FileConfiguration config = YamlConfiguration.loadConfiguration(file);
                GuiLayout layout = parseLayout(id, config);
                layouts.put(id, layout);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load gui layout: " + id, e);
            }
        }
    }

    private GuiLayout parseLayout(String id, FileConfiguration config) {
        String titleKey = config.getString("title", "&0" + id);
        int size = config.getInt("size", 54);
        String fillerStr = config.getString("filler", "");
        Material filler = fillerStr.isEmpty() ? null : Material.matchMaterial(fillerStr);

        // Parse product slots
        List<Integer> productSlots = config.getIntegerList("product-slots");

        // Parse items
        Map<String, GuiItem> items = new LinkedHashMap<>();
        ConfigurationSection itemsSection = config.getConfigurationSection("items");
        if (itemsSection != null) {
            for (String key : itemsSection.getKeys(false)) {
                ConfigurationSection sec = itemsSection.getConfigurationSection(key);
                if (sec == null) continue;

                GuiItem item = new GuiItem();
                item.id = key;
                item.slot = sec.getInt("slot", -1);
                item.slots = sec.getIntegerList("slots"); // Multiple slots support
                item.material = sec.getString("material", "STONE");
                item.texture = sec.getString("texture", "");
                item.nameKey = sec.getString("name", "");
                item.loreKey = sec.getString("lore", "");
                item.action = sec.getString("action", key); // Default action = item id

                items.put(key, item);
            }
        }

        return new GuiLayout(id, titleKey, size, filler, items, productSlots);
    }

    public GuiLayout getLayout(String id) {
        return layouts.get(id);
    }

    /**
     * Builds an ItemStack from a GuiItem definition.
     * Resolves name/lore from lang files and applies placeholders.
     */
    public ItemStack buildItem(GuiItem guiItem, Map<String, String> placeholders) {
        ItemStack item;

        // Skull from texture field (priority)
        if (guiItem.texture != null && !guiItem.texture.isEmpty()) {
            item = ItemUtil.createSkull(guiItem.texture);
        } else {
            item = resolveItem(guiItem.material);
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        // Name from lang
        if (!guiItem.nameKey.isEmpty()) {
            String name = plugin.getLanguageManager().getMessage(guiItem.nameKey);
            name = applyPlaceholders(name, placeholders);
            meta.displayName(ColorUtil.toComponent(name));
        }

        // Lore from lang
        if (!guiItem.loreKey.isEmpty()) {
            List<String> lore = plugin.getLanguageManager().getMessageList(guiItem.loreKey);
            List<String> processed = new ArrayList<>();
            for (String line : lore) {
                processed.add(applyPlaceholders(line, placeholders));
            }
            meta.lore(processed.stream().map(ColorUtil::toComponent).toList());
        }

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Resolves a material string to an ItemStack.
     * Supports: hdb-<id>, player-<name>, base64 textures, URLs, and standard Material names.
     */
    public static ItemStack resolveItem(String materialStr) {
        if (materialStr == null || materialStr.isEmpty()) return new ItemStack(Material.STONE);

        // HeadDatabase, player skull, base64, URL — delegate to ItemUtil
        if (materialStr.startsWith("hdb-") || materialStr.startsWith("player-")
                || materialStr.startsWith("http://") || materialStr.startsWith("https://")
                || materialStr.startsWith("basehead-") || materialStr.startsWith("eyJ")) {
            return ItemUtil.createSkull(materialStr);
        }

        // Standard material
        Material mat = Material.matchMaterial(materialStr);
        if (mat == null) mat = Material.STONE;
        return new ItemStack(mat);
    }

    /**
     * Creates filler items for empty slots.
     */
    public ItemStack createFiller(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(ColorUtil.toComponent(" "));
            item.setItemMeta(meta);
        }
        return item;
    }

    private String applyPlaceholders(String text, Map<String, String> placeholders) {
        if (placeholders == null) return text;
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            text = text.replace(e.getKey(), e.getValue());
        }
        return text;
    }

    // ==================== Data Classes ====================

    public static class GuiLayout {
        public final String id;
        public final String titleKey;
        public final int size;
        public final Material filler;
        public final Map<String, GuiItem> items;
        public final List<Integer> productSlots;

        public GuiLayout(String id, String titleKey, int size, Material filler,
                         Map<String, GuiItem> items, List<Integer> productSlots) {
            this.id = id;
            this.titleKey = titleKey;
            this.size = size;
            this.filler = filler;
            this.items = items;
            this.productSlots = productSlots;
        }

        public GuiItem getItem(String key) {
            return items.get(key);
        }

        public int getSlot(String key) {
            GuiItem item = items.get(key);
            return item != null ? item.slot : -1;
        }
    }

    public static class GuiItem {
        public String id;
        public int slot;
        public List<Integer> slots; // For items in multiple slots
        public String material;
        public String texture;
        public String nameKey;
        public String loreKey;
        public String action;
    }
}
