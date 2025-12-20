package com.nonxedy.nonchat.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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
        if (!config.isEnabled()) {
            return;
        }

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl("jdbc:mariadb://" + config.getHost() + ":" + config.getPort() + "/" + config.getDatabase());
        hikariConfig.setUsername(config.getUsername());
        hikariConfig.setPassword(config.getPassword());
        hikariConfig.setMaximumPoolSize(config.getPoolSize());
        hikariConfig.setMaxLifetime(config.getMaxLifetime());
        hikariConfig.setConnectionTimeout(config.getConnectionTimeout());
        hikariConfig.setDriverClassName("org.mariadb.jdbc.Driver");
        
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        try {
            dataSource = new HikariDataSource(hikariConfig);
            createTables();
            plugin.getLogger().info("Successfully connected to the database.");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to connect to the database", e);
        }
    }

    private void createTables() {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            // Table for player tags: uuid, category, tag_id
            String sql = "CREATE TABLE IF NOT EXISTS nonchat_player_tags (" +
                         "uuid VARCHAR(36) NOT NULL, " +
                         "category VARCHAR(64) NOT NULL, " +
                         "tag_id VARCHAR(64) NOT NULL, " +
                         "PRIMARY KEY (uuid, category))";
            stmt.execute(sql);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create database tables", e);
        }
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("Database is not connected");
        }
        return dataSource.getConnection();
    }

    public void close() {
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
        }
    }

    public void setPlayerTag(String uuid, String category, String tagId) {
        if (dataSource == null) return;

        String sql = "INSERT INTO nonchat_player_tags (uuid, category, tag_id) VALUES (?, ?, ?) " +
                     "ON DUPLICATE KEY UPDATE tag_id = ?";
        
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setString(2, category);
            ps.setString(3, tagId);
            ps.setString(4, tagId);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save player tag", e);
        }
    }

    public void removePlayerTag(String uuid, String category) {
        if (dataSource == null) return;

        String sql = "DELETE FROM nonchat_player_tags WHERE uuid = ? AND category = ?";
        
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setString(2, category);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to remove player tag", e);
        }
    }

    public String getPlayerTag(String uuid, String category) {
        if (dataSource == null) return null;

        String sql = "SELECT tag_id FROM nonchat_player_tags WHERE uuid = ? AND category = ?";
        
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setString(2, category);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("tag_id");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load player tag", e);
        }
        return null;
    }
}
