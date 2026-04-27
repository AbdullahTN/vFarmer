package com.asmus.vfarmer;

import com.asmus.vfarmer.commands.FarmerCommand;
import com.asmus.vfarmer.gui.GuiManager;
import com.asmus.vfarmer.hook.IslandHook;
import com.asmus.vfarmer.hook.NPCHook;
import com.asmus.vfarmer.listener.FarmerListener;
import com.asmus.vfarmer.manager.*;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Level;

/**
 * vFarmer - Automatic Farmer Plugin for Minecraft 1.20+
 * @author Asmus1990
 */
public class VFarmer extends JavaPlugin {

    private static VFarmer instance;
    private Economy economy;
    private LanguageManager languageManager;
    private ConfigManager configManager;
    private FarmerManager farmerManager;
    private DatabaseManager databaseManager;
    private ZeroTaxManager zeroTaxManager;
    private CreditManager creditManager;
    private IslandHook islandHook;
    private GuiManager guiManager;
    private BoosterManager boosterManager;
    private WebhookManager webhookManager;
    private FarmerLevelManager farmerLevelManager;
    private NPCHook npcHook;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        // Save products.yml if not exists
        File productsFile = new File(getDataFolder(), "products.yml");
        if (!productsFile.exists()) {
            saveResource("products.yml", false);
        }

        // Initialize language manager
        languageManager = new LanguageManager(this);
        languageManager.load();

        // Setup Vault economy
        if (!setupEconomy()) {
            getLogger().severe("Disabled due to no Vault dependency found!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize island hook (supports multiple skyblock plugins)
        islandHook = new IslandHook(this);

        // Initialize HeadDatabase hook
        com.asmus.vfarmer.util.ItemUtil.init();

        // Initialize GUI manager
        guiManager = new GuiManager(this);
        guiManager.load();

        // Initialize managers
        creditManager = new CreditManager(this);
        zeroTaxManager = new ZeroTaxManager(this);
        boosterManager = new BoosterManager(this);
        webhookManager = new WebhookManager(this);
        farmerLevelManager = new FarmerLevelManager(this);
        databaseManager = new DatabaseManager(this);
        configManager = new ConfigManager(this);
        configManager.load();
        farmerManager = new FarmerManager(this);

        // Register command
        FarmerCommand cmd = new FarmerCommand(this);
        getCommand("çiftçi").setExecutor(cmd);
        getCommand("çiftçi").setTabCompleter(cmd);

        // Register listener
        FarmerListener listener = new FarmerListener(this);
        getServer().getPluginManager().registerEvents(listener, this);
        listener.startVacuumTick();

        // Load farmers from database after a delay (wait for worlds to load)
        getServer().getScheduler().runTaskLater(this, () -> {
            farmerManager.loadAllFarmers();
            farmerManager.startAutoSave();

            // Initialize NPC hook after farmers are loaded
            npcHook = new NPCHook(this);
            npcHook.startLookAtPlayerTick();
            npcHook.startParticleTick();

            // Spawn NPCs for farmers with skins
            for (var farmer : farmerManager.getAllFarmers().values()) {
                if (farmer.hasNpcSkin()) {
                    npcHook.spawnNPC(farmer);
                }
            }
        }, 300L);

        // Backup database
        backupDatabase();

        // Register PlaceholderAPI expansion
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new com.asmus.vfarmer.hook.PlaceholderHook(this).register();
        }

        getLogger().info("vFarmer enabled!");
    }

    @Override
    public void onDisable() {
        // Close all farmer menus
        for (Player player : getServer().getOnlinePlayers()) {
            if (player.getOpenInventory() != null
                    && player.getOpenInventory().getTopInventory() != null) {
                InventoryHolder holder = player.getOpenInventory().getTopInventory().getHolder();
                if (holder != null && holder.getClass().getPackageName().startsWith("com.asmus.vfarmer")) {
                    player.closeInventory();
                }
            }
        }

        // Save zerotax state
        if (zeroTaxManager != null) {
            zeroTaxManager.save();
        }

        // Stop booster
        if (boosterManager != null) {
            boosterManager.stop();
        }

        // Shutdown NPC hook
        if (npcHook != null) {
            npcHook.shutdown();
        }

        // Save all farmers
        if (farmerManager != null) {
            farmerManager.saveAllFarmers();
            farmerManager.shutdown();
        }

        // Close credit manager connection
        if (creditManager != null) {
            creditManager.close();
        }

        getServer().getScheduler().cancelTasks(this);
        getLogger().info("vFarmer disabled!");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        // Try immediate registration
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp != null) {
            economy = rsp.getProvider();
            if (economy != null) {
                getLogger().info("Vault economy hooked: " + economy.getName());
                return true;
            }
        }
        // Economy provider might not be registered yet (EssentialsX, CMI etc. load later)
        // Schedule a delayed retry
        getLogger().info("Vault found but economy provider not ready yet, retrying in 3 seconds...");
        getServer().getScheduler().runTaskLater(this, () -> {
            RegisteredServiceProvider<Economy> retry = getServer().getServicesManager().getRegistration(Economy.class);
            if (retry != null) {
                economy = retry.getProvider();
                if (economy != null) {
                    getLogger().info("Vault economy hooked (delayed): " + economy.getName());
                    return;
                }
            }
            getLogger().severe("No economy provider found! Make sure you have EssentialsX, CMI or another economy plugin.");
            getLogger().severe("vFarmer will work but money operations will fail!");
        }, 60L); // 3 seconds delay
        return true; // Don't disable the plugin, let it load and retry
    }

    /**
     * Checks if economy is available. Use this before any money operation.
     */
    public boolean hasEconomy() {
        return economy != null;
    }

    private void backupDatabase() {
        try {
            File dbFile = new File(getDataFolder(), "farmers.db");
            if (dbFile.exists()) {
                File backupDir = new File(getDataFolder(), "backups");
                backupDir.mkdirs();
                String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
                File backupFile = new File(backupDir, "farmers-" + timestamp + ".db");
                java.nio.file.Files.copy(dbFile.toPath(), backupFile.toPath(),
                        java.nio.file.StandardCopyOption.COPY_ATTRIBUTES);
            }
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Failed to create automatic database backup", e);
        }
    }

    // Getters
    public static VFarmer getInstance() { return instance; }
    public Economy getEconomy() { return economy; }
    public LanguageManager getLanguageManager() { return languageManager; }
    public ConfigManager getConfigManager() { return configManager; }
    public FarmerManager getFarmerManager() { return farmerManager; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public ZeroTaxManager getZeroTaxManager() { return zeroTaxManager; }
    public CreditManager getCreditManager() { return creditManager; }
    public IslandHook getIslandHook() { return islandHook; }
    public GuiManager getGuiManager() { return guiManager; }
    public BoosterManager getBoosterManager() { return boosterManager; }
    public WebhookManager getWebhookManager() { return webhookManager; }
    public FarmerLevelManager getFarmerLevelManager() { return farmerLevelManager; }
    public NPCHook getNpcHook() { return npcHook; }
}
