package com.nonxedy.nonchat.tags;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

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
    private final Map<String, String> categorySelectionMessages = new HashMap<>();
    
    // Player Cache: UUID -> (Category -> TagID)
    private final Map<UUID, Map<String, String>> playerActiveTags = new ConcurrentHashMap<>();
    // Cache for random selections per session: UUID -> (Category -> RandomTagID)
    private final Map<UUID, Map<String, String>> playerRandomSelections = new ConcurrentHashMap<>();

    public TagManager(Nonchat plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    public void loadTags() {
        tagsByCategory.clear();
        defaultTags.clear();
        categorySelectionMessages.clear();
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
                config.set("title", "Ranks");
                config.set("default", "member"); // Default tag ID
                
                // Custom selection message for this category
                // Supports placeholders and MiniMessage
                config.set("selection-message", "&aYou have selected the &e{tag} &arank!");
                
                // Member Tag (Basic example)
                String path = "tags.member";
                config.set(path + ".value", "&7[Member]");
                config.set(path + ".permission", "");
                config.set(path + ".order", 1);
                
                config.set(path + ".display-item.material", "PAPER");
                config.set(path + ".display-item.name", "&7Member Tag");
                config.set(path + ".display-item.lore", java.util.Arrays.asList(
                    "&7Default tag for new players.",
                    "&7Click to select."
                ));
                config.set(path + ".display-item.bedrock-icon", "textures/items/paper"); // Bedrock path
                
                // VIP Tag (Model Data example)
                path = "tags.vip";
                config.set(path + ".value", "<gradient:gold:yellow>[VIP]</gradient>");
                config.set(path + ".permission", "nonchat.tags.ranks.vip");
                config.set(path + ".order", 2);
                
                config.set(path + ".display-item.material", "GOLD_INGOT");
                config.set(path + ".display-item.name", "<gradient:gold:yellow>VIP Tag</gradient>");
                config.set(path + ".display-item.lore", java.util.Arrays.asList(
                    "&7Exclusive tag for VIPs.",
                    "&e✨ Shiny!"
                ));
                config.set(path + ".display-item.model-data", 1001); // Custom Model Data example
                config.set(path + ".display-item.bedrock-icon", "textures/items/gold_ingot");
                
                // Admin Tag (Custom Head example)
                path = "tags.admin";
                config.set(path + ".value", "<bold><red>[ADMIN]</red></bold>");
                config.set(path + ".permission", "nonchat.tags.ranks.admin");
                config.set(path + ".order", 3);
                
                config.set(path + ".display-item.material", "PLAYER_HEAD");
                config.set(path + ".display-item.name", "&c&lAdmin Tag");
                config.set(path + ".display-item.lore", java.util.Arrays.asList("&cRestricted to staff."));
                // Base64 Texture (Red 'A' head or similar)
                config.set(path + ".display-item.texture", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmFkY2VmZDIYMTkyMmJkMzQ2ZThkODU4Y2QyZTM2NmM4YmFhMmUzNzlhN2M4ZTRiYjVlM2U3NTgyY2E1OCJ9fX0=");
                config.set(path + ".display-item.bedrock-icon", "textures/items/diamond_sword"); // Fallback icon for Bedrock
                
                config.save(ranksFile);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create default tags file", e);
        }
    }

    private void loadTagsFromFile(File file, String category) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        
        String selectionMsg = config.getString("selection-message");
        if (selectionMsg != null && !selectionMsg.isEmpty()) {
            categorySelectionMessages.put(category, selectionMsg);
        }
        
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
            
            // GUI properties
            org.bukkit.Material material = org.bukkit.Material.PAPER;
            String iconName = key;
            java.util.List<String> lore = new java.util.ArrayList<>();
            int modelData = 0;
            String texture = "";
            int order = 0;
            
            if (section != null) {
                // Map 'value' from YAML to display
                display = section.getString("value", "");
                permission = section.getString("permission", "");
                order = section.getInt("order", 0);
                
                // Load display item
                String matName = section.getString("display-item.material", "PAPER");
                try {
                    material = org.bukkit.Material.valueOf(matName.toUpperCase());
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid material '" + matName + "' for tag " + key + ". Using PAPER.");
                }
                
                iconName = section.getString("display-item.name", display);
                lore = section.getStringList("display-item.lore");
                modelData = section.getInt("display-item.model-data", 0);
                texture = section.getString("display-item.texture", "");
                
            } else {
                // Fallback
                display = "";
                permission = "";
            }
            
            // Bedrock icon
            String bedrockIcon = "";
            if (section != null) {
                bedrockIcon = section.getString("display-item.bedrock-icon", "");
            }

            // Determine if this specific tag is the default one
            boolean isDefault = key.equalsIgnoreCase(defaultTagId);

            Tag tag = new Tag(key, display, permission, category, isDefault, material, iconName, lore, modelData, texture, order, bedrockIcon);
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
        playerRandomSelections.remove(player.getUniqueId());
    }

    public void setPlayerTag(Player player, String category, String tagId) {
        if (!tagsByCategory.containsKey(category)) return;
        
        // Allow __random__ as a valid tag ID
        if (!tagId.equals("__random__") && !tagsByCategory.get(category).containsKey(tagId)) return;

        // Update Cache
        playerActiveTags.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>())
                        .put(category, tagId);
        
        // Clear cached random selection if switching to manual
        if (!tagId.equals("__random__") && playerRandomSelections.containsKey(player.getUniqueId())) {
            playerRandomSelections.get(player.getUniqueId()).remove(category);
        }

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
        if (playerRandomSelections.containsKey(player.getUniqueId())) {
            playerRandomSelections.get(player.getUniqueId()).remove(category);
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
            
            // Handle Random Tag Logic
            if (tagId.equals("__random__")) {
                // Check if we already selected a random tag for this session
                String randomTagId = null;
                if (playerRandomSelections.containsKey(player.getUniqueId())) {
                    randomTagId = playerRandomSelections.get(player.getUniqueId()).get(category);
                }
                
                Map<String, Tag> categoryTags = tagsByCategory.get(category);
                if (categoryTags != null && !categoryTags.isEmpty()) {
                    // If no selection yet, pick one
                    if (randomTagId == null || !categoryTags.containsKey(randomTagId)) {
                        java.util.List<String> keys = new java.util.ArrayList<>(categoryTags.keySet());
                        // Filter by permission
                        keys.removeIf(key -> {
                            Tag t = categoryTags.get(key);
                            return !t.getPermission().isEmpty() && !player.hasPermission(t.getPermission());
                        });
                        
                        if (!keys.isEmpty()) {
                            randomTagId = keys.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(keys.size()));
                            playerRandomSelections.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>())
                                                .put(category, randomTagId);
                        }
                    }
                    
                    if (randomTagId != null && categoryTags.containsKey(randomTagId)) {
                        display = categoryTags.get(randomTagId).getDisplay();
                    }
                }
            } else {
                // Normal tag
                Map<String, Tag> categoryTags = tagsByCategory.get(category);
                if (categoryTags != null && categoryTags.containsKey(tagId)) {
                    display = categoryTags.get(tagId).getDisplay();
                }
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
    
    public String getCategorySelectionMessage(String category) {
        return categorySelectionMessages.get(category);
    }
    
    public Map<String, Tag> getTags(String category) {
        return tagsByCategory.get(category);
    }
    
    public java.util.Set<String> getCategories() {
        return tagsByCategory.keySet();
    }
}