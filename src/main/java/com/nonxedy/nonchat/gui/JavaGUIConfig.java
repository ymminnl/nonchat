package com.nonxedy.nonchat.gui;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import com.nonxedy.nonchat.Nonchat;
import com.nonxedy.nonchat.util.items.ItemUtil;

import lombok.Getter;

@Getter
public class JavaGUIConfig {
    private final Nonchat plugin;
    private final File configFile;
    private FileConfiguration config;

    private String title;
    private int size;
    private int rows; // Default rows
    private List<Integer> tagSlots;
    private Map<String, GUIItem> buttons;
    private Map<String, GUIItem> fillers;
    private String openSound;
    private String clickSound;
    private String closeSound;

    public JavaGUIConfig(Nonchat plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "gui/java.yml");
        load();
    }

    public void load() {
        if (!configFile.exists()) {
            plugin.saveResource("gui/java.yml", false);
        }
        
        config = YamlConfiguration.loadConfiguration(configFile);
        
        this.title = config.getString("title", "Tags - {category}");
        this.rows = config.getInt("rows", 6);
        this.size = rows * 9;
        
        this.openSound = config.getString("open-sound", "block.chest.open");
        this.clickSound = config.getString("click-sound", "ui.button.click");
        this.closeSound = config.getString("close-sound", "block.chest.close");
        
        loadTagSlots();
        loadButtons();
        loadFillers();
    }

    private void loadTagSlots() {
        this.tagSlots = new ArrayList<>();
        List<String> rawSlots = config.getStringList("tag-slots");
        
        for (String slotStr : rawSlots) {
            GUIUtil.parseSlots(slotStr, this.tagSlots);
        }
    }

    private void loadButtons() {
        this.buttons = new HashMap<>();
        ConfigurationSection section = config.getConfigurationSection("buttons");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            ConfigurationSection btnSection = section.getConfigurationSection(key);
            if (btnSection != null) {
                buttons.put(key, GUIUtil.parseGUIItem(btnSection));
            }
        }
    }

    private void loadFillers() {
        this.fillers = new HashMap<>();
        ConfigurationSection section = config.getConfigurationSection("fillers");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            ConfigurationSection fillerSection = section.getConfigurationSection(key);
            if (fillerSection != null) {
                fillers.put(key, GUIUtil.parseGUIItem(fillerSection));
            }
        }
    }

    @Getter
    public static class GUIItem {
        private final ItemStack item;
        private final List<Integer> slots;
        private final List<String> actions;
        
        // Raw properties for dynamic updates
        private final Material material;
        private final String name;
        private final List<String> lore;
        private final int modelData;
        private final String texture;
        private final String bedrockIcon;

        public GUIItem(ItemStack item, List<Integer> slots, List<String> actions, 
                       Material material, String name, List<String> lore, int modelData, String texture, String bedrockIcon) {
            this.item = item;
            this.slots = slots;
            this.actions = actions;
            this.material = material;
            this.name = name;
            this.lore = lore;
            this.modelData = modelData;
            this.texture = texture;
            this.bedrockIcon = bedrockIcon;
        }
        
        public GUIItem(ItemStack item, List<Integer> slots, List<String> actions) {
            this(item, slots, actions, Material.AIR, "", new ArrayList<>(), 0, "", "");
        }
        
        public int getSingleSlot() {
            return slots.isEmpty() ? -1 : slots.get(0);
        }
    }
}
