package com.asmus.vfarmer.manager;

import com.asmus.vfarmer.VFarmer;

import java.io.File;
import java.sql.*;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Manages SQLite database for persistent farmer storage.
 */
public class DatabaseManager {

    private final VFarmer plugin;
    private Connection connection;
    private final File dbFile;

    public DatabaseManager(VFarmer plugin) {
        this.plugin = plugin;
        this.dbFile = new File(plugin.getDataFolder(), "farmers.db");
        connect();
        createTable();
        migrate();
    }

    private void connect() {
        try {
            if (connection != null && !connection.isClosed()) return;
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to connect to database", e);
        }
    }

    private void createTable() {
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS farmers (" +
                "island_uuid VARCHAR(36) PRIMARY KEY, " +
                "owner_uuid VARCHAR(36), " +
                "world VARCHAR(100), " +
                "x DOUBLE, y DOUBLE, z DOUBLE, " +
                "yaw FLOAT, pitch FLOAT, " +
                "storage TEXT, " +
                "autosell_active BOOLEAN, " +
                "autosell_expiration BIGINT, " +
                "balance DOUBLE, " +
                "collecting BOOLEAN, " +
                "is_baby BOOLEAN, " +
                "villager_type VARCHAR(50), " +
                "is_glowing BOOLEAN, " +
                "collect_player_drops BOOLEAN, " +
                "kill_spawner_mobs BOOLEAN, " +
                "auto_harvest BOOLEAN, " +
                "show_hologram BOOLEAN DEFAULT 1, " +
                "xp BIGINT DEFAULT 0, " +
                "logs TEXT" +
                ")"
            );
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create table", e);
        }
    }

    private void migrate() {
        // Add columns if they don't exist (for upgrades)
        String[] newColumns = {
            "show_hologram BOOLEAN DEFAULT 1",
            "xp BIGINT DEFAULT 0",
            "lifetime_earnings DOUBLE DEFAULT 0",
            "collect_xp BOOLEAN DEFAULT 1",
            "vacuum_radius INTEGER DEFAULT 0",
            "total_collected BIGINT DEFAULT 0",
            "npc_skin TEXT DEFAULT ''",
            "npc_particle TEXT DEFAULT ''",
            "npc_look_at BOOLEAN DEFAULT 1"
        };
        for (String col : newColumns) {
            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate("ALTER TABLE farmers ADD COLUMN " + col);
            } catch (SQLException ignored) {
                // Column already exists
            }
        }
    }

    public Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                connect();
            }
        } catch (SQLException e) {
            connect();
        }
        return connection;
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to close database", e);
        }
    }

    /**
     * Saves or updates a farmer record.
     */
    public void saveFarmer(String islandUuid, String ownerUuid, String world,
                           double x, double y, double z, float yaw, float pitch,
                           String storageJson, boolean autoSellActive, long autoSellExpiration,
                           double balance, boolean collecting, boolean isBaby,
                           String villagerType, boolean isGlowing,
                           boolean collectPlayerDrops, boolean killSpawnerMobs,
                           boolean autoHarvest, boolean showHologram, long xp, String logsJson,
                           double lifetimeEarnings, boolean collectXP, int vacuumRadius,
                           long totalCollected, String npcSkin, String npcParticle, boolean npcLookAt) {
        String sql = "INSERT OR REPLACE INTO farmers VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, islandUuid);
            ps.setString(2, ownerUuid);
            ps.setString(3, world);
            ps.setDouble(4, x);
            ps.setDouble(5, y);
            ps.setDouble(6, z);
            ps.setFloat(7, yaw);
            ps.setFloat(8, pitch);
            ps.setString(9, storageJson);
            ps.setBoolean(10, autoSellActive);
            ps.setLong(11, autoSellExpiration);
            ps.setDouble(12, balance);
            ps.setBoolean(13, collecting);
            ps.setBoolean(14, isBaby);
            ps.setString(15, villagerType);
            ps.setBoolean(16, isGlowing);
            ps.setBoolean(17, collectPlayerDrops);
            ps.setBoolean(18, killSpawnerMobs);
            ps.setBoolean(19, autoHarvest);
            ps.setBoolean(20, showHologram);
            ps.setLong(21, xp);
            ps.setString(22, logsJson);
            ps.setDouble(23, lifetimeEarnings);
            ps.setBoolean(24, collectXP);
            ps.setInt(25, vacuumRadius);
            ps.setLong(26, totalCollected);
            ps.setString(27, npcSkin != null ? npcSkin : "");
            ps.setString(28, npcParticle != null ? npcParticle : "");
            ps.setBoolean(29, npcLookAt);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save farmer: " + islandUuid, e);
        }
    }

    /**
     * Deletes a farmer record.
     */
    public void deleteFarmer(String islandUuid) {
        try (PreparedStatement ps = getConnection().prepareStatement("DELETE FROM farmers WHERE island_uuid = ?")) {
            ps.setString(1, islandUuid);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to delete farmer: " + islandUuid, e);
        }
    }

    /**
     * Gets all farmer records.
     */
    public ResultSet getAllFarmers() {
        try {
            Statement stmt = getConnection().createStatement();
            return stmt.executeQuery("SELECT * FROM farmers");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load farmers", e);
            return null;
        }
    }
}
