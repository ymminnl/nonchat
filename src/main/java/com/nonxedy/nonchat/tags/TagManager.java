package com.nonxedy.nonchat.tags;

import java.io.File;
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
        
        File tagsFolder = new File(plugin.getDataFolder(), "tags");
        if (!tagsFolder.exists()) {
            tagsFolder.mkdirs();
        }
        
        // Copy default resources if they don't exist
        copyDefaultResource("tags/ranks.yml");
        copyDefaultResource("tags/playername.yml");

        File[] files = tagsFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;

        for (File file : files) {
            String category = file.getName().replace(".yml", "");
            loadTagsFromFile(file, category);
        }
        
        plugin.getLogger().info("Loaded " + tagsByCategory.size() + " tag categories.");
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

    private void loadTagsFromFile(File file, String category) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        
        // Ensure default settings exist and update file if needed
        boolean saveNeeded = false;
        
        if (!config.contains("title")) {
            config.set("title", "Tags - " + category);
            saveNeeded = true;
        }
        if (!config.contains("default-value")) {
            config.set("default-value", "");
            saveNeeded = true;
        }
        if (!config.contains("selection-message")) {
            config.set("selection-message", "&aYou selected the tag: {tag}");
            saveNeeded = true;
        }
        
        // Expiration defaults
        if (!config.contains("expiration-message")) {
            config.set("expiration-message", "&cYou no longer have permission for tag: {tag}");
            saveNeeded = true;
        }
        
        // Java GUI Defaults
        if (!config.contains("menu-rows")) {
            config.set("menu-rows", 6);
            saveNeeded = true;
        }
        if (!config.contains("tag-slots")) {
            config.set("tag-slots", java.util.Arrays.asList("10-16", "19-25", "28-34"));
            saveNeeded = true;
        }
        
        // Bedrock GUI Defaults
        if (!config.contains("bedrock-content")) {
            config.set("bedrock-content", "");
            saveNeeded = true;
        }
        if (!config.contains("bedrock-default-icon")) {
            config.set("bedrock-default-icon", "textures/items/paper");
            saveNeeded = true;
        }
        if (!config.contains("tags-per-page")) {
            config.set("tags-per-page", 10);
            saveNeeded = true;
        }
        
        if (saveNeeded) {
            try {
                config.save(file);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to update tag file " + file.getName() + ": " + e.getMessage());
            }
        }
        
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
            plugin.getLogger().warning("File " + file.getName() + " does not have a 'tags' section.");
            return;
        }

        Map<String, Tag> categoryTags = new HashMap<>();

        for (String key : tagsSection.getKeys(false)) {
            ConfigurationSection section = tagsSection.getConfigurationSection(key);
            String display, permission;
            org.bukkit.Material material = org.bukkit.Material.PAPER;
            String iconName = key;
            java.util.List<String> lore = new java.util.ArrayList<>();
            int modelData = 0;
            String texture = "";
            int order = 0;
            String bedrockIcon = "";
            
            if (section != null) {
                display = section.getString("value", "");
                permission = section.getString("permission", "");
                order = section.getInt("order", 0);
                
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
                bedrockIcon = section.getString("display-item.bedrock-icon", "");
                
                // Locked properties
                org.bukkit.Material lockedMaterial = null;
                String lockedIconName = null;
                java.util.List<String> lockedLore = null;
                int lockedModelData = 0;
                String lockedTexture = null;
                String lockedBedrockIcon = null;
                
                if (section.contains("locked-display-item")) {
                    ConfigurationSection lockedSection = section.getConfigurationSection("locked-display-item");
                    if (lockedSection != null) {
                        String lMatName = lockedSection.getString("material", "BARRIER");
                        try {
                            lockedMaterial = org.bukkit.Material.valueOf(lMatName.toUpperCase());
                        } catch (IllegalArgumentException e) {
                            lockedMaterial = org.bukkit.Material.BARRIER;
                        }
                        lockedIconName = lockedSection.getString("name", iconName);
                        lockedLore = lockedSection.getStringList("lore");
                        lockedModelData = lockedSection.getInt("model-data", 0);
                        lockedTexture = lockedSection.getString("texture", "");
                        lockedBedrockIcon = lockedSection.getString("bedrock-icon", "");
                    }
                }

                Tag tag = new Tag(key, display, permission, category, false, material, iconName, lore, modelData, texture, order, bedrockIcon,
                                  lockedMaterial, lockedIconName, lockedLore, lockedModelData, lockedTexture, lockedBedrockIcon);
                categoryTags.put(key, tag);
                
            } else {
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