package com.nonxedy.nonchat.listener;

import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.nonxedy.nonchat.tags.Tag;
import com.nonxedy.nonchat.tags.TagManager;
import com.nonxedy.nonchat.util.core.colors.ColorUtil;
import com.nonxedy.nonchat.util.integration.external.IntegrationUtil;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;

public class TagListener implements Listener {

    private final TagManager tagManager;

    public TagListener(TagManager tagManager) {
        this.tagManager = tagManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        tagManager.loadPlayerTags(player);
        
        // Check for expired permissions
        // We delay slightly to ensure permissions are loaded (if using Vault/LuckPerms) and tags are loaded from DB
        org.bukkit.Bukkit.getScheduler().runTaskLater(org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(TagListener.class), () -> {
            if (!player.isOnline()) return;
            
            for (String category : tagManager.getCategories()) {
                // Get active tag ID directly from DB/Cache to check permission
                // We can't use getPlayerTagDisplay because it returns the formatted string
                // We need to access internal cache or query. TagManager doesn't expose raw ID easily publically.
                // Let's add a method to TagManager to get raw active tag ID.
                String tagId = tagManager.getPlayerActiveTagId(player, category);
                
                if (tagId != null && !tagId.isEmpty() && !tagId.equals("__random__")) {
                    Map<String, Tag> tags = tagManager.getTags(category);
                    if (tags != null && tags.containsKey(tagId)) {
                        Tag tag = tags.get(tagId);
                        
                        // Check if permission is required and missing
                        if (!tag.getPermission().isEmpty() && !player.hasPermission(tag.getPermission())) {
                            // Expired!
                            tagManager.resetPlayerTag(player, category);
                            
                            TagManager.CategoryMeta meta = tagManager.getCategoryMeta(category);
                            if (meta != null) {
                                String display = tag.getDisplay(); // Raw display
                                
                                // Send Message
                                if (meta.getExpirationMessage() != null && !meta.getExpirationMessage().isEmpty()) {
                                    String msg = meta.getExpirationMessage().replace("{tag}", display).replace("{category}", category);
                                    msg = IntegrationUtil.processPlaceholders(player, msg);
                                    player.sendMessage(ColorUtil.parseComponent(msg));
                                }
                                
                                // Send Title
                                if ((meta.getExpirationTitle() != null && !meta.getExpirationTitle().isEmpty()) || 
                                    (meta.getExpirationSubtitle() != null && !meta.getExpirationSubtitle().isEmpty())) {
                                    
                                    String titleStr = meta.getExpirationTitle() != null ? meta.getExpirationTitle() : "";
                                    String subTitleStr = meta.getExpirationSubtitle() != null ? meta.getExpirationSubtitle() : "";
                                    
                                    titleStr = IntegrationUtil.processPlaceholders(player, titleStr.replace("{tag}", display));
                                    subTitleStr = IntegrationUtil.processPlaceholders(player, subTitleStr.replace("{tag}", display));
                                    
                                    Title title = Title.title(
                                        ColorUtil.parseComponent(titleStr),
                                        ColorUtil.parseComponent(subTitleStr)
                                    );
                                    player.showTitle(title);
                                }
                                
                                // Send ActionBar
                                if (meta.getExpirationActionBar() != null && !meta.getExpirationActionBar().isEmpty()) {
                                    String ab = meta.getExpirationActionBar().replace("{tag}", display).replace("{category}", category);
                                    ab = IntegrationUtil.processPlaceholders(player, ab);
                                    player.sendActionBar(ColorUtil.parseComponent(ab));
                                }
                            }
                        }
                    }
                }
            }
        }, 40L); // 2 seconds delay
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        tagManager.unloadPlayerTags(event.getPlayer());
    }
}
