package com.nonxedy.nonchat.music;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class MusicConnectionListener implements Listener {

    private final MusicManager musicManager;

    public MusicConnectionListener(MusicManager musicManager) {
        this.musicManager = musicManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        musicManager.loadPlayerSettings(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        musicManager.unloadPlayerSettings(event.getPlayer());
    }
}
