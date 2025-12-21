package com.nonxedy.nonchat.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

import com.nonxedy.nonchat.Nonchat;
import com.nonxedy.nonchat.config.DatabaseConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public class DatabaseManager {

    private final Nonchat plugin;
    private final DatabaseConfig config;
    private HikariDataSource dataSource;

    public DatabaseManager(Nonchat plugin, DatabaseConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void initialize() {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl("jdbc:mariadb://" + config.getHost() + ":" + config.getPort() + "/" + config.getDatabase());
        hikariConfig.setUsername(config.getUsername());
        hikariConfig.setPassword(config.getPassword());
        hikariConfig.setDriverClassName("org.mariadb.jdbc.Driver");
        
        // Recommended settings
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        dataSource = new HikariDataSource(hikariConfig);
        
        createTables();
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    private Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    private void createTables() {
        try (Connection connection = getConnection()) {
            try (Statement stmt = connection.createStatement()) {
                // Player Tags Table
                stmt.execute("CREATE TABLE IF NOT EXISTS nonchat_player_tags (" +
                        "uuid VARCHAR(36) NOT NULL," +
                        "category VARCHAR(64) NOT NULL," +
                        "tag_id VARCHAR(64) NOT NULL," +
                        "PRIMARY KEY (uuid, category))");
                        
                // Tag Configs Table (Sync)
                stmt.execute("CREATE TABLE IF NOT EXISTS nonchat_tag_configs (" +
                        "category_id VARCHAR(64) NOT NULL," +
                        "config_data LONGTEXT NOT NULL," +
                        "PRIMARY KEY (category_id))");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not create database tables", e);
        }
    }
    
    // --- Config Sync Methods ---
    
    public void saveTagConfig(String category, String data) {
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(
                     "INSERT INTO nonchat_tag_configs (category_id, config_data) VALUES (?, ?) " +
                     "ON DUPLICATE KEY UPDATE config_data = ?")) {
            stmt.setString(1, category);
            stmt.setString(2, data);
            stmt.setString(3, data);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save tag config for " + category, e);
        }
    }
    
    public void deleteTagConfig(String category) {
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement("DELETE FROM nonchat_tag_configs WHERE category_id = ?")) {
            stmt.setString(1, category);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not delete tag config for " + category, e);
        }
    }
    
    public String loadTagConfig(String category) {
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement("SELECT config_data FROM nonchat_tag_configs WHERE category_id = ?")) {
            stmt.setString(1, category);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("config_data");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not load tag config for " + category, e);
        }
        return null;
    }
    
    public Map<String, String> getAllTagConfigs() {
        Map<String, String> configs = new HashMap<>();
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement("SELECT category_id, config_data FROM nonchat_tag_configs");
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                configs.put(rs.getString("category_id"), rs.getString("config_data"));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not load all tag configs", e);
        }
        return configs;
    }

    // --- Sync / Versioning Methods ---

    public void updateTagSyncVersion() {
        saveTagConfig("__SYNC_TIMESTAMP__", String.valueOf(System.currentTimeMillis()));
    }

    public long getTagSyncVersion() {
        String val = loadTagConfig("__SYNC_TIMESTAMP__");
        if (val == null) return 0L;
        try {
            return Long.parseLong(val);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    // --- Player Tag Methods ---

    public void setPlayerTag(String uuid, String category, String tagId) {
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(
                     "INSERT INTO nonchat_player_tags (uuid, category, tag_id) VALUES (?, ?, ?) " +
                     "ON DUPLICATE KEY UPDATE tag_id = ?")) {
            stmt.setString(1, uuid);
            stmt.setString(2, category);
            stmt.setString(3, tagId);
            stmt.setString(4, tagId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not set player tag", e);
        }
    }

    public void removePlayerTag(String uuid, String category) {
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(
                     "DELETE FROM nonchat_player_tags WHERE uuid = ? AND category = ?")) {
            stmt.setString(1, uuid);
            stmt.setString(2, category);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not remove player tag", e);
        }
    }

    public String getPlayerTag(String uuid, String category) {
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(
                     "SELECT tag_id FROM nonchat_player_tags WHERE uuid = ? AND category = ?")) {
            stmt.setString(1, uuid);
            stmt.setString(2, category);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("tag_id");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not get player tag", e);
        }
        return null;
    }
}