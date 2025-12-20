package com.nonxedy.nonchat.listener;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

import com.nonxedy.nonchat.config.PluginMessages;
import com.nonxedy.nonchat.gui.JavaGUIConfig;
import com.nonxedy.nonchat.gui.TagMenuJava;
import com.nonxedy.nonchat.tags.Tag;
import com.nonxedy.nonchat.tags.TagManager;
import com.nonxedy.nonchat.util.core.colors.ColorUtil;
import com.nonxedy.nonchat.util.integration.external.IntegrationUtil;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;

public class MenuListener implements Listener {

    private final TagMenuJava menu;
    private final JavaGUIConfig config;
    private final TagManager tagManager;
    private final PluginMessages messages;

    public MenuListener(TagMenuJava menu, JavaGUIConfig config, TagManager tagManager, PluginMessages messages) {
        this.menu = menu;
        this.config = config;
        this.tagManager = tagManager;
        this.messages = messages;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof TagMenuJava.TagHolder holder)) return;

        event.setCancelled(true); // Prevent stealing items

        int slot = event.getRawSlot();
        if (slot >= event.getInventory().getSize()) return; // Clicked bottom inventory

        // Check for Tag click
        Tag tag = holder.getTagAtSlot(slot);
        if (tag != null) {
            handleTagClick(player, tag, holder.getCategory());
            return;
        }

        // Check for Button click
        handleButtonClick(player, slot, holder);
    }
    
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof TagMenuJava.TagHolder && event.getPlayer() instanceof Player player) {
            playSound(player, config.getCloseSound());
        }
    }

    private void handleTagClick(Player player, Tag tag, String category) {
        // Check permission
        if (!tag.getPermission().isEmpty() && !player.hasPermission(tag.getPermission())) {
            player.sendMessage(ColorUtil.parseColor(messages.getString("no-permission")));
            playSound(player, "entity.villager.no");
            return;
        }

        tagManager.setPlayerTag(player, category, tag.getId());
        
        // Process tag display independently
        String rawTagDisplay = tag.getDisplay();
        rawTagDisplay = rawTagDisplay.replace("</>", "<reset>");
        rawTagDisplay = ColorUtil.convertCompactGradients(rawTagDisplay);
        rawTagDisplay = IntegrationUtil.processPlaceholders(player, rawTagDisplay);
        
        Component tagComponent = ColorUtil.parseComponent(rawTagDisplay);
        
        // Prepare base message (Custom per category or default)
        String customMsg = tagManager.getCategorySelectionMessage(category);
        String rawMsg = (customMsg != null && !customMsg.isEmpty()) ? customMsg : messages.getString("tags-set-success");
        
        // Replace simple placeholders first
        rawMsg = rawMsg.replace("{category}", category)
                       .replace("{player}", player.getName());
        
        // Parse base message
        Component msgComponent = ColorUtil.parseComponent(rawMsg);
        
        // Inject tag component safely
        msgComponent = msgComponent.replaceText(TextReplacementConfig.builder()
            .matchLiteral("{tag}")
            .replacement(tagComponent)
            .build());
            
        player.sendMessage(msgComponent);
        playSound(player, config.getClickSound());
        player.closeInventory();
    }

    private void handleButtonClick(Player player, int slot, TagMenuJava.TagHolder holder) {
        // Get category metadata to access custom buttons
        TagManager.CategoryMeta meta = tagManager.getCategoryMeta(holder.getCategory());
        
        // Merge buttons (Global + Category specific)
        Map<String, JavaGUIConfig.GUIItem> buttons = new HashMap<>(config.getButtons());
        if (meta != null && meta.getButtons() != null) {
            buttons.putAll(meta.getButtons());
        }

        // Find which button was clicked
        for (Map.Entry<String, JavaGUIConfig.GUIItem> entry : buttons.entrySet()) {
            String key = entry.getKey();
            JavaGUIConfig.GUIItem btn = entry.getValue();

            // Check if this button is in the clicked slot
            if (btn.getSingleSlot() == slot) {
                // Execute internal navigation logic
                if (key.equalsIgnoreCase("next")) {
                    if (holder.getPage() < holder.getTotalPages()) {
                        menu.open(player, holder.getCategory(), holder.getPage() + 1);
                        playSound(player, config.getClickSound());
                    }
                } else if (key.equalsIgnoreCase("previous")) {
                    if (holder.getPage() > 1) {
                        menu.open(player, holder.getCategory(), holder.getPage() - 1);
                        playSound(player, config.getClickSound());
                    }
                } else if (key.equalsIgnoreCase("reset")) {
                    tagManager.resetPlayerTag(player, holder.getCategory());
                    player.sendMessage(ColorUtil.parseColor(messages.getString("tags-reset-success")
                        .replace("{category}", holder.getCategory())));
                    playSound(player, config.getClickSound());
                    player.closeInventory();
                } else if (key.equalsIgnoreCase("random")) {
                    tagManager.setPlayerTag(player, holder.getCategory(), "__random__");
                    player.sendMessage(ColorUtil.parseColor("&aRandom tag mode enabled for " + holder.getCategory()));
                    playSound(player, config.getClickSound());
                    player.closeInventory();
                }

                // Execute configured actions
                if (btn.getActions() != null) {
                    for (String action : btn.getActions()) {
                        executeAction(player, action);
                    }
                }
                return;
            }
        }
    }

    private void executeAction(Player player, String action) {
        if (action.equalsIgnoreCase("[close]")) {
            player.closeInventory();
        } else if (action.startsWith("[player] ")) {
            player.performCommand(action.substring(9));
        } else if (action.startsWith("[console] ")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), action.substring(10).replace("{player}", player.getName()));
        } else if (action.startsWith("[message] ")) {
            player.sendMessage(ColorUtil.parseColor(action.substring(10)));
        } else if (action.startsWith("[msg] ")) {
            player.sendMessage(ColorUtil.parseColor(action.substring(6)));
        } else if (action.startsWith("[open] ")) {
            player.performCommand(action.substring(7));
        } else if (action.startsWith("[sound] ")) {
            playSound(player, action.substring(8));
        } else if (action.startsWith("[opencp] ")) {
            String panel = action.substring(9);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "cp open " + panel + " " + player.getName());
        }
    }
    
    private void playSound(Player player, String soundName) {
        try {
            if (soundName != null && !soundName.isEmpty()) {
                player.playSound(player.getLocation(), Sound.valueOf(soundName.toUpperCase().replace(".", "_")), 1f, 1f);
            }
        } catch (Exception ignored) {}
    }
}