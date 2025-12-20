package com.nonxedy.nonchat.placeholders;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import com.nonxedy.nonchat.Nonchat;
import com.nonxedy.nonchat.tags.TagManager;
import com.nonxedy.nonchat.util.core.colors.ColorUtil;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class TagsExpansion extends PlaceholderExpansion {

    private final Nonchat plugin;
    private final TagManager tagManager;

    public TagsExpansion(Nonchat plugin, TagManager tagManager) {
        this.plugin = plugin;
        this.tagManager = tagManager;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "tags";
    }

    @Override
    public @NotNull String getAuthor() {
        return "nonxedy";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) return "";

        // %nontags_<category>%
        // Returns the display string of the selected tag for that category
        
        String display = tagManager.getPlayerTagDisplay(player, params);
        if (display == null || display.isEmpty()) {
            return "";
        }
        
        // Check export mode from config
        if (plugin.getConfigService().getConfig().isTagsExportLegacy()) {
            // Convert MiniMessage/Gradients to a Component then to Legacy string
            Component component = ColorUtil.parseComponent(display);
            return LegacyComponentSerializer.legacySection().serialize(component);
        }
        
        // Default: Return raw string but convert custom shorthand formats to valid MiniMessage
        // Convert non-standard closing tag
        display = display.replace("</>", "<reset>");
        // Convert compact gradients to standard MiniMessage gradients
        // Then convert any legacy codes (&a, &l) to MiniMessage tags (<green>, <bold>)
        // This ensures the output is 100% MiniMessage compatible without legacy chars
        return ColorUtil.convertToMiniMessageFormat(display);
    }
}
