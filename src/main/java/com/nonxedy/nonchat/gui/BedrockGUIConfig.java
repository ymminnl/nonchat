package com.nonxedy.nonchat.gui;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import com.nonxedy.nonchat.Nonchat;

import lombok.Getter;

@Getter
public class BedrockGUIConfig {
    private final Nonchat plugin;
    private final File configFile;
    private FileConfiguration config;

    private String title;
    private String content;
    private String defaultIcon;
    private Map<String, BedrockButton> buttons;

    public BedrockGUIConfig(Nonchat plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "gui/bedrock.yml");
        load();
    }

    public void load() {
        if (!configFile.exists()) {
            plugin.saveResource("gui/bedrock.yml", false);
        }
        
        config = YamlConfiguration.loadConfiguration(configFile);
        
        this.title = config.getString("title", "Tags - {category}");
        this.content = config.getString("content", "");
        this.defaultIcon = config.getString("default-icon", "");
        
        loadButtons();
    }

    private void loadButtons() {
        this.buttons = new HashMap<>();
        ConfigurationSection section = config.getConfigurationSection("buttons");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            ConfigurationSection btnSection = section.getConfigurationSection(key);
            if (btnSection != null) {
                String text = btnSection.getString("text", key);
                String icon = btnSection.getString("icon", "");
                buttons.put(key, new BedrockButton(text, icon));
            }
        }
    }

    @Getter
    public static class BedrockButton {
        private final String text;
        private final String icon;

        public BedrockButton(String text, String icon) {
            this.text = text;
            this.icon = icon;
        }
    }
}
