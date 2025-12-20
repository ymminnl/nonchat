package com.nonxedy.nonchat.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.nonxedy.nonchat.tags.TagManager;

public class TagListener implements Listener {

    private final TagManager tagManager;

    public TagListener(TagManager tagManager) {
        this.tagManager = tagManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        tagManager.loadPlayerTags(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        tagManager.unloadPlayerTags(event.getPlayer());
    }
}
