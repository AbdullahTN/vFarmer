package com.asmus.vfarmer.manager;

import com.asmus.vfarmer.VFarmer;

import java.sql.*;
import java.util.logging.Level;

/**
 * Manages external credit system (for auto-sell purchases).
 * Connects to an external MySQL/SQLite database defined in config.yml.
 */
public class CreditManager {

    private final VFarmer plugin;
    private Connection connection;
    private String table;
    private String playerColumn;
    private String creditColumn;
    private boolean enabled;

    public CreditManager(VFarmer plugin) {
        this.plugin = plugin;
        this.enabled = false;
        init();
    }

    public synchronized void init() {
        String host = plugin.getConfig().getString("database.host", "");
        if (host.isEmpty()) {
            enabled = false;
            return;
        }

        String port = plugin.getConfig().getString("database.port", "3306");
        String database = plugin.getConfig().getString("database.database", "");
        String username = plugin.getConfig().getString("database.username", "");
        String password = plugin.getConfig().getString("database.password", "");
        table = plugin.getConfig().getString("database.table", "Accounts");
        playerColumn = plugin.getConfig().getString("database.columns.player", "username");
        creditColumn = plugin.getConfig().getString("database.columns.credit", "credit");

        try {
            String url = "jdbc:mysql://" + host + ":" + port + "/" + database +
                         "?useSSL=false&autoReconnect=true";
            connection = DriverManager.getConnection(url, username, password);
            enabled = true;
            plugin.getLogger().info("Credit database connected.");
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to connect to credit database", e);
            enabled = false;
        }
    }

    public synchronized void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException ignored) {}
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Gets the credit balance for a player.
     */
    public synchronized double getCredits(String playerName) {
        if (!enabled) return 0;
        try {
            String sql = "SELECT " + creditColumn + " FROM " + table + " WHERE " + playerColumn + " = ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, playerName);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    return rs.getDouble(1);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to get credits for " + playerName, e);
        }
        return 0;
    }

    /**
     * Deducts credits from a player.
     * @return true if successful
     */
    public synchronized boolean deductCredits(String playerName, double amount) {
        if (!enabled) return false;
        try {
            double current = getCredits(playerName);
            if (current < amount) return false;
            String sql = "UPDATE " + table + " SET " + creditColumn + " = " + creditColumn + " - ? WHERE " + playerColumn + " = ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setDouble(1, amount);
                ps.setString(2, playerName);
                return ps.executeUpdate() > 0;
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to deduct credits for " + playerName, e);
        }
        return false;
    }

    /**
     * Adds credits to a player.
     */
    public synchronized void addCredits(String playerName, double amount) {
        if (!enabled) return;
        try {
            String sql = "UPDATE " + table + " SET " + creditColumn + " = " + creditColumn + " + ? WHERE " + playerColumn + " = ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setDouble(1, amount);
                ps.setString(2, playerName);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to add credits for " + playerName, e);
        }
    }
}
