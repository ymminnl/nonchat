package com.nonxedy.nonchat.config;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import com.nonxedy.nonchat.Nonchat;

import lombok.Getter;

@Getter
public class DatabaseConfig {
    private final Nonchat plugin;
    private final File configFile;
    private FileConfiguration config;

    private boolean enabled;
    private String host;
    private int port;
    private String database;
    private String username;
    private String password;
    private int poolSize;
    private long maxLifetime;
    private long connectionTimeout;

    public DatabaseConfig(Nonchat plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "database.yml");
        load();
    }

    public void load() {
        if (!configFile.exists()) {
            createDefaultConfig();
        }
        
        config = YamlConfiguration.loadConfiguration(configFile);
        
        this.enabled = config.getBoolean("enabled", false);
        this.host = config.getString("host", "localhost");
        this.port = config.getInt("port", 3306);
        this.database = config.getString("database", "nonchat");
        this.username = config.getString("username", "root");
        this.password = config.getString("password", "password");
        this.poolSize = config.getInt("pool-size", 10);
        this.maxLifetime = config.getLong("max-lifetime", 1800000);
        this.connectionTimeout = config.getLong("connection-timeout", 5000);
    }

    public void reload() {
        load();
    }

    private void createDefaultConfig() {
        try {
            configFile.getParentFile().mkdirs();
            configFile.createNewFile();
            
            config = YamlConfiguration.loadConfiguration(configFile);
            config.set("enabled", false);
            config.set("host", "localhost");
            config.set("port", 3306);
            config.set("database", "nonchat");
            config.set("username", "root");
            config.set("password", "password");
            config.set("pool-size", 10);
            config.set("max-lifetime", 1800000);
            config.set("connection-timeout", 5000);
            
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not create database.yml", e);
        }
    }
}
