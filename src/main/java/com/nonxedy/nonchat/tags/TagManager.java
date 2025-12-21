package com.nonxedy.nonchat.tags;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
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
import com.nonxedy.nonchat.gui.GUIUtil;
import com.nonxedy.nonchat.gui.JavaGUIConfig;
import com.nonxedy.nonchat.util.integration.external.IntegrationUtil;

public class TagManager {

    private final Nonchat plugin;
    private final DatabaseManager databaseManager;
    private final Map<String, Map<String, Tag>> tagsByCategory = new HashMap<>();
    private final Map<String, String> defaultValues = new HashMap<>();
    private final Map<String, String> categorySelectionMessages = new HashMap<>();
    private final Map<String, CategoryMeta> categoryMeta = new HashMap<>();
    
    private final Map<UUID, Map<String, String>> playerActiveTags = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, String>> playerRandomSelections = new ConcurrentHashMap<>();

    public TagManager(Nonchat plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    public void loadTags() {
        tagsByCategory.clear();
        defaultValues.clear();
        categorySelectionMessages.clear();
        categoryMeta.clear();
        
        // 1. Try to load from Database (Primary Source)
        boolean loadedFromDB = false;
        if (databaseManager != null) {
            Map<String, String> dbConfigs = databaseManager.getAllTagConfigs();
            if (!dbConfigs.isEmpty()) {
                plugin.getLogger().info("Loading tags from database (" + dbConfigs.size() + " categories)...");
                for (Map.Entry<String, String> entry : dbConfigs.entrySet()) {
                    String category = entry.getKey();
                    String content = entry.getValue();
                    try {
                        YamlConfiguration config = YamlConfiguration.loadConfiguration(new StringReader(content));
                        loadTagsFromConfig(config, category);
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.SEVERE, "Failed to parse tag config from DB for category: " + category, e);
                    }
                }
                loadedFromDB = true;
            }
        }
        
        // 2. Fallback to local files (Only for initial setup/importing or if DB is empty)
        File tagsFolder = new File(plugin.getDataFolder(), "tags");
        if (!tagsFolder.exists()) {
            tagsFolder.mkdirs();
        }
        
        // Copy defaults for admin convenience (to edit and import)
        copyDefaultResource("tags/ranks.yml");
        copyDefaultResource("tags/playername.yml");

        if (!loadedFromDB) {
            plugin.getLogger().warning("No tags found in database. Please use '/tags import <category>' to load tags from local files.");
        }
        
        plugin.getLogger().info("Loaded " + tagsByCategory.size() + " tag categories.");
    }
    
    public void importToDatabase(String category) {
        if (databaseManager == null) return;
        
        CompletableFuture.runAsync(() -> {
            File file = new File(plugin.getDataFolder(), "tags/" + category + ".yml");
            if (file.exists()) {
                try {
                    String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                    databaseManager.saveTagConfig(category, content);
                    plugin.getLogger().info("Imported category '" + category + "' to database.");
                    // Optional: Reload tags to reflect changes immediately
                    org.bukkit.Bukkit.getScheduler().runTask(plugin, this::loadTags);
                } catch (IOException e) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to read tag file for import: " + category, e);
                }
            } else {
                plugin.getLogger().warning("File not found for import: " + category);
            }
        });
    }
    
    public void deleteCategoryFromDatabase(String category) {
        if (databaseManager == null) return;
        
        CompletableFuture.runAsync(() -> {
            databaseManager.deleteTagConfig(category);
            plugin.getLogger().info("Deleted category '" + category + "' from database.");
            
            // Reload tags to reflect changes (remove from memory)
            org.bukkit.Bukkit.getScheduler().runTask(plugin, this::loadTags);
        });
    }

    private void copyDefaultResource(String path) {
        File file = new File(plugin.getDataFolder(), path);
        if (!file.exists()) {
            try {
                plugin.saveResource(path, false);
            } catch (Exception e) {
                // Resource might not exist in jar, which is fine
            }
        }
    }

    private void loadTagsFromConfig(YamlConfiguration config, String category) {
        String selectionMsg = config.getString("selection-message");
        if (selectionMsg != null && !selectionMsg.isEmpty()) {
            categorySelectionMessages.put(category, selectionMsg);
        }
        
        String defaultValue = config.getString("default-value", "");
        defaultValues.put(category, defaultValue);
        
        // Metadata
        String displayName = config.getString("title", category);
        String menuTitle = config.getString("menu-title", null);
        int menuRows = config.getInt("menu-rows", 0);
        
        String bedrockTitle = config.getString("bedrock-title", null);
        String bedrockContent = config.getString("bedrock-content", null);
        String bedrockDefaultIcon = config.getString("bedrock-default-icon", "");
        int tagsPerPage = config.getInt("tags-per-page", 0);
        
        // Expiration Messages
        String expirationMessage = config.getString("expiration-message", "");
        String expirationTitle = config.getString("expiration-title", "");
        String expirationSubtitle = config.getString("expiration-subtitle", "");
        String expirationActionBar = config.getString("expiration-actionbar", "");
        
        // Default Display Items
        String defMat = config.getString("default-display-item.material", "PAPER");
        String defName = config.getString("default-display-item.name", "{tag}");
        List<String> defLore = config.getStringList("default-display-item.lore");
        int defModel = config.getInt("default-display-item.model-data", 0);
        String defTexture = config.getString("default-display-item.texture", "");
        String defBedrock = config.getString("default-display-item.bedrock-icon", "");
        
        String defLockMat = config.getString("default-locked-display-item.material", "BARRIER");
        String defLockName = config.getString("default-locked-display-item.name", "{tag}");
        List<String> defLockLore = config.getStringList("default-locked-display-item.lore");
        int defLockModel = config.getInt("default-locked-display-item.model-data", 0);
        String defLockTexture = config.getString("default-locked-display-item.texture", "");
        String defLockBedrock = config.getString("default-locked-display-item.bedrock-icon", "");
        
        // Fillers
        Map<String, JavaGUIConfig.GUIItem> fillers = new HashMap<>();
        ConfigurationSection fillersSection = config.getConfigurationSection("fillers");
        if (fillersSection != null) {
            for (String key : fillersSection.getKeys(false)) {
                fillers.put(key, GUIUtil.parseGUIItem(fillersSection.getConfigurationSection(key)));
            }
        }
        
        // Buttons
        Map<String, JavaGUIConfig.GUIItem> buttons = new HashMap<>();
        ConfigurationSection buttonsSection = config.getConfigurationSection("buttons");
        if (buttonsSection != null) {
            for (String key : buttonsSection.getKeys(false)) {
                buttons.put(key, GUIUtil.parseGUIItem(buttonsSection.getConfigurationSection(key)));
            }
        }
        
        // Tag Slots
        List<Integer> tagSlots = new java.util.ArrayList<>();
        List<String> rawSlots = config.getStringList("tag-slots");
        for (String slotStr : rawSlots) {
            GUIUtil.parseSlots(slotStr, tagSlots);
        }
        
        categoryMeta.put(category, new CategoryMeta(displayName, menuTitle, menuRows, tagSlots, fillers, buttons, bedrockTitle, bedrockContent, bedrockDefaultIcon, tagsPerPage, expirationMessage, expirationTitle, expirationSubtitle, expirationActionBar));
        
        ConfigurationSection tagsSection = config.getConfigurationSection("tags");
        if (tagsSection == null) {
            plugin.getLogger().warning("Configuration for " + category + " does not have a 'tags' section.");
            return;
        }

        Map<String, Tag> categoryTags = new HashMap<>();

        for (String key : tagsSection.getKeys(false)) {
            ConfigurationSection section = tagsSection.getConfigurationSection(key);
            String display, permission, tagName;
            org.bukkit.Material material = org.bukkit.Material.PAPER;
            String iconName = "";
            java.util.List<String> lore = new java.util.ArrayList<>();
            int modelData = 0;
            String texture = "";
            int order = 0;
            String bedrockIcon = "";
            
            if (section != null) {
                display = section.getString("value", "");
                permission = section.getString("permission", "");
                order = section.getInt("order", 0);
                tagName = section.getString("name", key); // Friendly name for placeholders
                
                // Display Item
                if (section.contains("display-item")) {
                    String matName = section.getString("display-item.material", defMat);
                    try {
                        material = org.bukkit.Material.valueOf(matName.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        material = org.bukkit.Material.PAPER;
                    }
                    iconName = section.getString("display-item.name", defName).replace("{tag}", tagName);
                    lore = section.contains("display-item.lore") ? section.getStringList("display-item.lore") : defLore;
                    modelData = section.getInt("display-item.model-data", defModel);
                    texture = section.getString("display-item.texture", defTexture);
                    bedrockIcon = section.getString("display-item.bedrock-icon", defBedrock);
                } else {
                    // Use Defaults
                    try {
                        material = org.bukkit.Material.valueOf(defMat.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        material = org.bukkit.Material.PAPER;
                    }
                    iconName = defName.replace("{tag}", tagName);
                    lore = defLore;
                    modelData = defModel;
                    texture = defTexture;
                    bedrockIcon = defBedrock;
                }
                
                // Locked properties
                org.bukkit.Material lockedMaterial;
                String lockedIconName;
                java.util.List<String> lockedLore;
                int lockedModelData;
                String lockedTexture;
                String lockedBedrockIcon;
                
                if (section.contains("locked-display-item")) {
                    ConfigurationSection lockedSection = section.getConfigurationSection("locked-display-item");
                    String lMatName = lockedSection.getString("material", defLockMat);
                    try {
                        lockedMaterial = org.bukkit.Material.valueOf(lMatName.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        lockedMaterial = org.bukkit.Material.BARRIER;
                    }
                    lockedIconName = lockedSection.getString("name", defLockName).replace("{tag}", tagName);
                    lockedLore = lockedSection.contains("lore") ? lockedSection.getStringList("lore") : defLockLore;
                    lockedModelData = lockedSection.getInt("model-data", defLockModel);
                    lockedTexture = lockedSection.getString("texture", defLockTexture);
                    lockedBedrockIcon = lockedSection.getString("bedrock-icon", defLockBedrock);
                } else {
                    // Use Defaults
                    try {
                        lockedMaterial = org.bukkit.Material.valueOf(defLockMat.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        lockedMaterial = org.bukkit.Material.BARRIER;
                    }
                    lockedIconName = defLockName.replace("{tag}", tagName);
                    lockedLore = defLockLore;
                    lockedModelData = defLockModel;
                    lockedTexture = defLockTexture;
                    lockedBedrockIcon = defLockBedrock;
                }

                Tag tag = new Tag(key, display, permission, category, false, material, iconName, lore, modelData, texture, order, bedrockIcon,
                                  lockedMaterial, lockedIconName, lockedLore, lockedModelData, lockedTexture, lockedBedrockIcon);
                categoryTags.put(key, tag);
                
            } else {
                // Fallback for empty tag
                display = "";
                permission = "";
                Tag tag = new Tag(key, display, permission, category, false, material, iconName, lore, modelData, texture, order, bedrockIcon,
                                  null, null, null, 0, null, null);
                categoryTags.put(key, tag);
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
        
        if (!tagId.equals("__random__") && !tagsByCategory.get(category).containsKey(tagId)) return;

        playerActiveTags.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>())
                        .put(category, tagId);
        
        if (!tagId.equals("__random__") && playerRandomSelections.containsKey(player.getUniqueId())) {
            playerRandomSelections.get(player.getUniqueId()).remove(category);
        }

        if (databaseManager != null) {
            CompletableFuture.runAsync(() -> {
                databaseManager.setPlayerTag(player.getUniqueId().toString(), category, tagId);
            });
        }
    }

    public void resetPlayerTag(Player player, String category) {
        if (!tagsByCategory.containsKey(category)) return;

        if (playerActiveTags.containsKey(player.getUniqueId())) {
            playerActiveTags.get(player.getUniqueId()).remove(category);
        }
        if (playerRandomSelections.containsKey(player.getUniqueId())) {
            playerRandomSelections.get(player.getUniqueId()).remove(category);
        }

        if (databaseManager != null) {
            CompletableFuture.runAsync(() -> {
                databaseManager.removePlayerTag(player.getUniqueId().toString(), category);
            });
        }
    }

    public String getPlayerTagDisplay(Player player, String category) {
        Map<String, String> activeTags = playerActiveTags.get(player.getUniqueId());
        String display = "";
        
        if (activeTags != null && activeTags.containsKey(category)) {
            String tagId = activeTags.get(category);
            
            if (tagId.equals("__random__")) {
                String randomTagId = null;
                if (playerRandomSelections.containsKey(player.getUniqueId())) {
                    randomTagId = playerRandomSelections.get(player.getUniqueId()).get(category);
                }
                
                Map<String, Tag> categoryTags = tagsByCategory.get(category);
                if (categoryTags != null && !categoryTags.isEmpty()) {
                    if (randomTagId == null || !categoryTags.containsKey(randomTagId)) {
                        java.util.List<String> keys = new java.util.ArrayList<>(categoryTags.keySet());
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
                Map<String, Tag> categoryTags = tagsByCategory.get(category);
                if (categoryTags != null && categoryTags.containsKey(tagId)) {
                    display = categoryTags.get(tagId).getDisplay();
                }
            }
        }
        
        if (display.isEmpty()) {
            display = defaultValues.getOrDefault(category, "");
        }

        if (!display.isEmpty()) {
            return IntegrationUtil.processPlaceholders(player, display);
        }

        return "";
    }
    
    public String getPlayerActiveTagId(Player player, String category) {
        Map<String, String> activeTags = playerActiveTags.get(player.getUniqueId());
        if (activeTags != null) {
            return activeTags.get(category);
        }
        return null;
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
    
    public CategoryMeta getCategoryMeta(String category) {
        return categoryMeta.get(category);
    }
    
    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class CategoryMeta {
        private final String displayName;
        private final String menuTitle;
        private final int rows;
        private final List<Integer> tagSlots;
        private final Map<String, JavaGUIConfig.GUIItem> fillers;
        private final Map<String, JavaGUIConfig.GUIItem> buttons;
        private final String bedrockTitle;
        private final String bedrockContent;
        private final String bedrockDefaultIcon;
        private final int tagsPerPage;
        
        // Expiration
        private final String expirationMessage;
        private final String expirationTitle;
        private final String expirationSubtitle;
        private final String expirationActionBar;
    }
}
