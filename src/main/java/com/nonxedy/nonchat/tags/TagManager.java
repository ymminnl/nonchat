package com.nonxedy.nonchat.tags;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import com.nonxedy.nonchat.Nonchat;
import com.nonxedy.nonchat.database.DatabaseManager;
import com.nonxedy.nonchat.util.integration.external.IntegrationUtil;

public class TagManager {

    private final Nonchat plugin;
    private final DatabaseManager databaseManager;
    private final Map<String, Map<String, Tag>> tagsByCategory = new HashMap<>(); // Category -> (TagID -> Tag)
    private final Map<String, Tag> defaultTags = new HashMap<>(); // Category -> DefaultTag
    
    // Player Cache: UUID -> (Category -> TagID)
    private final Map<UUID, Map<String, String>> playerActiveTags = new ConcurrentHashMap<>();

    public TagManager(Nonchat plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    public void loadTags() {
        tagsByCategory.clear();
        defaultTags.clear();
        File tagsFolder = new File(plugin.getDataFolder(), "tags");
        if (!tagsFolder.exists()) {
            tagsFolder.mkdirs();
            createDefaultTagFile(tagsFolder);
        }

        File[] files = tagsFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;

        for (File file : files) {
            String category = file.getName().replace(".yml", "");
            loadTagsFromFile(file, category);
        }
        
        plugin.getLogger().info("Loaded " + tagsByCategory.size() + " tag categories.");
    }

    private void createDefaultTagFile(File folder) {
        try {
            File ranksFile = new File(folder, "ranks.yml");
            if (ranksFile.createNewFile()) {
                YamlConfiguration config = YamlConfiguration.loadConfiguration(ranksFile);
                config.set("id", "ranks");
                config.set("title", "Ranks");
                config.set("default", "member"); // Default tag ID
                
                // Member Tag
                String path = "tags.member";
                config.set(path + ".value", "&7[Member]");
                config.set(path + ".permission", "");
                config.set(path + ".display-item.material", "PAPER");
                config.set(path + ".display-item.name", "&7Member Tag");
                
                // VIP Tag
                path = "tags.vip";
                config.set(path + ".value", "&6[VIP]");
                config.set(path + ".permission", "nonchat.tags.ranks.vip");
                config.set(path + ".display-item.material", "GOLD_INGOT");
                config.set(path + ".display-item.name", "&6VIP Tag");
                
                // Admin Tag
                path = "tags.admin";
                config.set(path + ".value", "&c[Admin]");
                config.set(path + ".permission", "nonchat.tags.ranks.admin");
                config.set(path + ".display-item.material", "DIAMOND_SWORD");
                config.set(path + ".display-item.name", "&cAdmin Tag");
                
                config.save(ranksFile);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create default tags file", e);
        }
    }

    private void loadTagsFromFile(File file, String category) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        
        // Read the default tag ID from the root (e.g., default: "notag")
        String defaultTagId = config.getString("default", "");
        
        ConfigurationSection tagsSection = config.getConfigurationSection("tags");
        if (tagsSection == null) {
            plugin.getLogger().warning("File " + file.getName() + " does not have a 'tags' section.");
            return;
        }

        Map<String, Tag> categoryTags = new HashMap<>();

        for (String key : tagsSection.getKeys(false)) {
            ConfigurationSection section = tagsSection.getConfigurationSection(key);
            String display, permission;
            
            if (section != null) {
                // Map 'value' from YAML to display
                display = section.getString("value", "");
                permission = section.getString("permission", "");
            } else {
                // Fallback (unlikely with new structure but good for safety)
                display = "";
                permission = "";
            }

            // Determine if this specific tag is the default one
            boolean isDefault = key.equalsIgnoreCase(defaultTagId);

            Tag tag = new Tag(key, display, permission, category, isDefault);
            categoryTags.put(key, tag);
            
            if (isDefault) {
                defaultTags.put(category, tag);
            }
        }

        tagsByCategory.put(category, categoryTags);
    }

    public void loadPlayerTags(Player player) {
        if (databaseManager == null) return;
        
        CompletableFuture.runAsync(() -> {
            Map<String, String> loadedTags = new HashMap<>();
            for (String category : tagsByCategory.keySet()) {
                String tagId = databaseManager.getPlayerTag(player.getUniqueId().toString(), category);
                if (tagId != null) {
                    loadedTags.put(category, tagId);
                }
            }
            playerActiveTags.put(player.getUniqueId(), loadedTags);
        });
    }

    public void unloadPlayerTags(Player player) {
        playerActiveTags.remove(player.getUniqueId());
    }

    public void setPlayerTag(Player player, String category, String tagId) {
        if (!tagsByCategory.containsKey(category)) return;
        if (!tagsByCategory.get(category).containsKey(tagId)) return;

        // Update Cache
        playerActiveTags.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>())
                        .put(category, tagId);

        // Update Database Async
        if (databaseManager != null) {
            CompletableFuture.runAsync(() -> {
                databaseManager.setPlayerTag(player.getUniqueId().toString(), category, tagId);
            });
        }
    }

    public void resetPlayerTag(Player player, String category) {
        if (!tagsByCategory.containsKey(category)) return;

        // Remove from Cache
        if (playerActiveTags.containsKey(player.getUniqueId())) {
            playerActiveTags.get(player.getUniqueId()).remove(category);
        }

        // Remove from Database Async
        if (databaseManager != null) {
            CompletableFuture.runAsync(() -> {
                databaseManager.removePlayerTag(player.getUniqueId().toString(), category);
            });
        }
    }

    public String getPlayerTagDisplay(Player player, String category) {
        Map<String, String> activeTags = playerActiveTags.get(player.getUniqueId());
        String display = "";
        
        // 1. Try to find the user selected tag
        if (activeTags != null && activeTags.containsKey(category)) {
            String tagId = activeTags.get(category);
            Map<String, Tag> categoryTags = tagsByCategory.get(category);
            if (categoryTags != null && categoryTags.containsKey(tagId)) {
                display = categoryTags.get(tagId).getDisplay();
            }
        }
        
        // 2. Fallback to default tag for this category if display is empty
        if (display.isEmpty() && defaultTags.containsKey(category)) {
            display = defaultTags.get(category).getDisplay();
        }

        // Process placeholders if display is not empty
        if (!display.isEmpty()) {
            return IntegrationUtil.processPlaceholders(player, display);
        }

        return "";
    }
    
    public Map<String, Tag> getTags(String category) {
        return tagsByCategory.get(category);
    }
    
    public java.util.Set<String> getCategories() {
        return tagsByCategory.keySet();
    }
}
