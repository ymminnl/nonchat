package com.nonxedy.nonchat.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.util.FormImage;
import org.geysermc.floodgate.api.FloodgateApi;

import com.nonxedy.nonchat.Nonchat;
import com.nonxedy.nonchat.config.PluginMessages;
import com.nonxedy.nonchat.gui.JavaGUIConfig;
import com.nonxedy.nonchat.tags.Tag;
import com.nonxedy.nonchat.tags.TagManager;
import com.nonxedy.nonchat.util.core.colors.ColorUtil;
import com.nonxedy.nonchat.util.integration.external.IntegrationUtil;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class TagMenuBedrock {

    private final Nonchat plugin;
    private final BedrockGUIConfig config;
    private final TagManager tagManager;
    private final PluginMessages messages;

    public TagMenuBedrock(Nonchat plugin, BedrockGUIConfig config, TagManager tagManager, PluginMessages messages) {
        this.plugin = plugin;
        this.config = config;
        this.tagManager = tagManager;
        this.messages = messages;
    }

    public void open(Player player, String category, int page) {
        Map<String, Tag> tagsMap = tagManager.getTags(category);
        if (tagsMap == null) return;

        // Sort tags
        List<Tag> tags = new ArrayList<>(tagsMap.values());
        tags.sort(Comparator.comparingInt(Tag::getOrder));
        
        // Get metadata
        TagManager.CategoryMeta meta = tagManager.getCategoryMeta(category);

        // Pagination
        int tagsPerPage = (meta != null && meta.getTagsPerPage() > 0) ? meta.getTagsPerPage() : config.getConfig().getInt("tags-per-page", 5);
        int totalPages = (int) Math.ceil((double) tags.size() / tagsPerPage);
        
        if (page < 1) page = 1;
        if (page > totalPages && totalPages > 0) page = totalPages;

        // Title Priority: bedrock-title > menu-title > title > global config
        String titleRaw = config.getTitle();
        if (config.getTitle() != null && config.getTitle().contains("{category}")) {
             // If global config is a format, we use it as base if no specific override
             // BUT user requested strict override from category file.
        }
        
        // Strict override logic
        if (meta != null) {
            if (meta.getBedrockTitle() != null) titleRaw = meta.getBedrockTitle();
            else if (meta.getMenuTitle() != null) titleRaw = meta.getMenuTitle();
            else if (meta.getDisplayName() != null) titleRaw = meta.getDisplayName();
        }
        
        titleRaw = titleRaw.replace("{category}", category).replace("{page}", String.valueOf(page));
        String title = formatForBedrock(IntegrationUtil.processPlaceholders(player, titleRaw));
        
        String contentRaw = (meta != null && meta.getBedrockContent() != null) 
                ? meta.getBedrockContent() : config.getContent();
        contentRaw = contentRaw
                .replace("{category}", category)
                .replace("{page}", String.valueOf(page))
                .replace("{total_pages}", String.valueOf(totalPages));
        String content = formatForBedrock(IntegrationUtil.processPlaceholders(player, contentRaw));

        SimpleForm.Builder builder = SimpleForm.builder()
                .title(title)
                .content(content);

        List<Runnable> actions = new ArrayList<>();

        // 1. Random Button
        BedrockGUIConfig.BedrockButton randomBtn = getButton("random", meta);
        if (randomBtn != null && isButtonEnabled("random", meta)) {
            addButton(builder, randomBtn);
            actions.add(() -> {
                tagManager.setPlayerTag(player, category, "__random__");
                player.sendMessage(ColorUtil.parseColor("&aRandom tag mode enabled for " + category));
            });
        }

        // 2. Reset Button
        BedrockGUIConfig.BedrockButton resetBtn = getButton("reset", meta);
        if (resetBtn != null && isButtonEnabled("reset", meta)) {
            addButton(builder, resetBtn);
            actions.add(() -> {
                tagManager.resetPlayerTag(player, category);
                String msg = messages.getString("tags-reset-success").replace("{category}", category);
                player.sendMessage(ColorUtil.parseComponent(msg));
            });
        }

        // 3. Tags
        String defaultIcon = (meta != null && meta.getBedrockDefaultIcon() != null && !meta.getBedrockDefaultIcon().isEmpty())
                ? meta.getBedrockDefaultIcon() : config.getDefaultIcon();
                
        if (!tags.isEmpty()) {
            int startIndex = (page - 1) * tagsPerPage;
            int endIndex = Math.min(startIndex + tagsPerPage, tags.size());

            for (int i = startIndex; i < endIndex; i++) {
                Tag tag = tags.get(i);
                
                boolean hasPermission = tag.getPermission().isEmpty() || player.hasPermission(tag.getPermission());
                // Check if locked properties exist (we check material as a proxy for existence)
                boolean useLocked = !hasPermission && tag.getLockedIconMaterial() != null;
                
                String rawNameConfig = useLocked ? tag.getLockedIconName() : tag.getIconName();
                if (rawNameConfig == null) rawNameConfig = tag.getIconName();
                
                String rawName = IntegrationUtil.processPlaceholders(player, rawNameConfig);
                String tagName = formatForBedrock(rawName);
                
                // Ensure newlines are processed for Bedrock buttons and reset colors using section symbol
                tagName = tagName.replace("\\n", "\n").replace("\n", "§r\n");
                
                String icon = useLocked && tag.getLockedBedrockIcon() != null && !tag.getLockedBedrockIcon().isEmpty() 
                            ? tag.getLockedBedrockIcon() : tag.getBedrockIcon();
                            
                if (icon == null || icon.isEmpty()) icon = defaultIcon;

                if (icon != null && !icon.isEmpty()) {
                    FormImage.Type type = icon.startsWith("http") ? FormImage.Type.URL : FormImage.Type.PATH;
                    builder.button(tagName, type, icon);
                } else {
                    builder.button(tagName);
                }

                actions.add(() -> handleTagClick(player, tag, category));
            }
        }

        // 4. Navigation
        BedrockGUIConfig.BedrockButton prevBtn = getButton("previous", meta);
        if (page > 1 && prevBtn != null && isButtonEnabled("previous", meta)) {
            addButton(builder, prevBtn);
            int finalPage = page;
            actions.add(() -> open(player, category, finalPage - 1));
        }

        BedrockGUIConfig.BedrockButton nextBtn = getButton("next", meta);
        if (page < totalPages && nextBtn != null && isButtonEnabled("next", meta)) {
            addButton(builder, nextBtn);
            int finalPage = page;
            actions.add(() -> open(player, category, finalPage + 1));
        }

        // 5. Close
        BedrockGUIConfig.BedrockButton closeBtn = getButton("close", meta);
        if (closeBtn != null && isButtonEnabled("close", meta)) {
            addButton(builder, closeBtn);
            actions.add(() -> {}); // Do nothing, just close
        }

        builder.validResultHandler(response -> {
            int index = response.clickedButtonId();
            if (index >= 0 && index < actions.size()) {
                actions.get(index).run();
            }
        });

        FloodgateApi.getInstance().sendForm(player.getUniqueId(), builder.build());
    }
    
    private boolean isButtonEnabled(String key, TagManager.CategoryMeta meta) {
        if (meta != null && meta.getButtons() != null && meta.getButtons().containsKey(key)) {
            return meta.getButtons().get(key).isEnabled();
        }
        return true;
    }

    private BedrockGUIConfig.BedrockButton getButton(String key, TagManager.CategoryMeta meta) {
        // Check overrides in category meta
        if (meta != null && meta.getButtons() != null && meta.getButtons().containsKey(key)) {
            JavaGUIConfig.GUIItem item = meta.getButtons().get(key);
            // Use 'name' from Java config as 'text' for Bedrock
            String text = item.getName();
            String icon = item.getBedrockIcon();
            return new BedrockGUIConfig.BedrockButton(text, icon);
        }
        
        // Fallback to global bedrock config
        if (config.getButtons().containsKey(key)) {
            return config.getButtons().get(key);
        }
        
        return null;
    }

    private void addButton(SimpleForm.Builder builder, BedrockGUIConfig.BedrockButton btn) {
        String text = formatForBedrock(btn.getText());
        if (btn.getIcon() != null && !btn.getIcon().isEmpty()) {
            FormImage.Type type = btn.getIcon().startsWith("http") ? FormImage.Type.URL : FormImage.Type.PATH;
            builder.button(text, type, btn.getIcon());
        } else {
            builder.button(text);
        }
    }
    
    private String formatForBedrock(String text) {
        if (text == null) return "";
        // Pre-process MiniMessage shorthands
        text = text.replace("</>", "<reset>");
        text = ColorUtil.convertCompactGradients(text);
        // Convert to Component
        Component component = ColorUtil.parseComponent(text);
        // Serialize to Legacy for Bedrock support
        return LegacyComponentSerializer.legacySection().serialize(component);
    }

    private void handleTagClick(Player player, Tag tag, String category) {
        if (!tag.getPermission().isEmpty() && !player.hasPermission(tag.getPermission())) {
            player.sendMessage(ColorUtil.parseColor(messages.getString("no-permission")));
            return;
        }

        tagManager.setPlayerTag(player, category, tag.getId());
        
        // Same logic as MenuListener
        String rawTagDisplay = tag.getDisplay();
        rawTagDisplay = rawTagDisplay.replace("</>", "<reset>");
        rawTagDisplay = ColorUtil.convertCompactGradients(rawTagDisplay);
        rawTagDisplay = IntegrationUtil.processPlaceholders(player, rawTagDisplay);
        
        Component tagComponent = ColorUtil.parseComponent(rawTagDisplay);
        
        String customMsg = tagManager.getCategorySelectionMessage(category);
        String rawMsg = (customMsg != null && !customMsg.isEmpty()) ? customMsg : messages.getString("tags-set-success");
        
        rawMsg = rawMsg.replace("{category}", category).replace("{player}", player.getName());
        Component msgComponent = ColorUtil.parseComponent(rawMsg);
        
        msgComponent = msgComponent.replaceText(TextReplacementConfig.builder()
            .matchLiteral("{tag}")
            .replacement(tagComponent)
            .build());
            
        player.sendMessage(msgComponent);
    }
}