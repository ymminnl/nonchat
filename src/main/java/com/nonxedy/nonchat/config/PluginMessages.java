package com.nonxedy.nonchat.config;

import com.nonxedy.nonchat.Nonchat;
import com.nonxedy.nonchat.util.chat.formatting.MessageFormatter;
import com.nonxedy.nonchat.util.lang.LanguageManager;

import net.kyori.adventure.text.Component;

/**
 * Central manager for plugin messages and translations
 * Handles message loading, formatting and language selection
 */
public class PluginMessages {
    private final MessageFormatter formatter;
    private final LanguageManager languageManager;
    private final Nonchat plugin;
    private final PluginConfig pluginConfig;

    // Constructor initializes the messages system
    public PluginMessages(Nonchat plugin, PluginConfig pluginConfig) {
        this.plugin = plugin;
        this.pluginConfig = pluginConfig;
        this.languageManager = new LanguageManager(plugin.getDataFolder());
        this.formatter = new MessageFormatter(this);
    }

    /**
     * Loads language configuration from plugin settings
     */
    public void loadLanguage() {
        String lang = pluginConfig.getLanguage();
        languageManager.setLanguage(lang);
    }

    /**
     * Reloads language configuration from file
     */
    public void reloadConfig() {
        // Reload the actual language files from disk
        languageManager.reload();
        // Reload the current language setting
        loadLanguage();
    }

    /**
     * Gets raw message string from configuration
     * @param path Message identifier path
     * @return Raw message string
     */
    public String getString(String path) {
        return languageManager.getMessage(path);
    }

    /**
     * Gets formatted message with variables replaced
     * @param path Message identifier path
     * @param args Variables to insert into message
     * @return Formatted component
     */
    public Component getFormatted(String path, Object... args) {
        return formatter.format(path, args);
    }
}
