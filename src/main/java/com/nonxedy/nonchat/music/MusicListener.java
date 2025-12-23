package com.nonxedy.nonchat.music;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

import com.nonxedy.nonchat.util.core.colors.ColorUtil;

public class MusicListener implements Listener {

    private final MusicManager musicManager;

    public MusicListener(MusicManager musicManager) {
        this.musicManager = musicManager;
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        String title = event.getView().getTitle();
        String strippedTitle = ColorUtil.stripAllColors(title);

        String mainMenuTitle = ColorUtil.stripAllColors(ColorUtil.parseColor(
            musicManager.getMenuConfig().getString("title", "&8Music Menu")
        ));
        
        String songsMenuTitle = ColorUtil.stripAllColors(ColorUtil.parseColor(
            musicManager.getJavaTitle()
        ));
        
        String optionsMenuTitle = ColorUtil.stripAllColors(ColorUtil.parseColor(
            musicManager.getMenuConfig().getString("java.options-menu.title", "&8Options")
        ));
        
        String queueMenuTitle = ColorUtil.stripAllColors(ColorUtil.parseColor(
            musicManager.getJavaQueueTitle()
        ));

        if (strippedTitle.equals(mainMenuTitle) || 
            strippedTitle.equals(songsMenuTitle) || 
            strippedTitle.equals(optionsMenuTitle) || 
            strippedTitle.equals(queueMenuTitle)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        
        String title = event.getView().getTitle();
        String strippedTitle = ColorUtil.stripAllColors(title);
        
        // Dynamic Titles
        String mainMenuTitle = ColorUtil.stripAllColors(ColorUtil.parseColor(
            musicManager.getMenuConfig().getString("title", "&8Music Menu")
        ));
        
        String songsMenuTitle = ColorUtil.stripAllColors(ColorUtil.parseColor(
            musicManager.getJavaTitle()
        ));
        
        // Check Main Menu
        if (strippedTitle.equals(mainMenuTitle)) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null) return;
            
            String action = musicManager.getMainMenuActions().get(event.getSlot());
            if (action != null) {
                if (action.equalsIgnoreCase("open_songs")) {
                    musicManager.openSongsMenu(player);
                } else if (action.equalsIgnoreCase("open_queue")) {
                    musicManager.openQueueMenu(player);
                } else if (action.equalsIgnoreCase("open_options")) {
                    musicManager.openOptionsMenu(player);
                } else if (action.equalsIgnoreCase("toggle_mute")) {
                    musicManager.toggleMute(player);
                } else if (action.equalsIgnoreCase("admin_skip")) {
                    musicManager.skipSong(player);
                } else if (action.equalsIgnoreCase("admin_stop")) {
                    musicManager.stopMusic(player);
                } else if (action.startsWith("[player] ")) {
                    player.performCommand(action.substring(9).replace("{player}", player.getName()));
                } else if (action.startsWith("[console] ")) {
                    org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(), action.substring(10).replace("{player}", player.getName()));
                } else if (action.equalsIgnoreCase("close")) {
                    player.closeInventory();
                }
            }
            return;
        }
        
        String optionsMenuTitle = ColorUtil.stripAllColors(ColorUtil.parseColor(
            musicManager.getMenuConfig().getString("java.options-menu.title", "&8Options")
        ));

        if (strippedTitle.equals(optionsMenuTitle)) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null) return;
            
            // Iterate options config to find action
            org.bukkit.configuration.ConfigurationSection items = musicManager.getMenuConfig().getConfigurationSection("options-menu.items");
            if (items != null) {
                for (String key : items.getKeys(false)) {
                    if (items.getInt(key + ".slot") == event.getSlot()) {
                        String action = items.getString(key + ".action");
                        if (action != null) {
                            if (action.equalsIgnoreCase("toggle_mute")) {
                                musicManager.toggleMute(player); // This re-opens menu inside method for bedrock but we call it manually for java here
                                musicManager.openOptionsMenu(player); // Refresh
                            } else if (action.equalsIgnoreCase("vol_up")) {
                                musicManager.changeVolume(player, 0.1f);
                                musicManager.openOptionsMenu(player);
                            } else if (action.equalsIgnoreCase("vol_down")) {
                                musicManager.changeVolume(player, -0.1f);
                                musicManager.openOptionsMenu(player);
                            } else if (action.equalsIgnoreCase("back")) {
                                musicManager.openMainMenu(player);
                            }
                        }
                        return;
                    }
                }
            }
            return;
        }

        // Check Queue Menu
        String queueMenuTitle = ColorUtil.stripAllColors(ColorUtil.parseColor(
            musicManager.getJavaQueueTitle()
        ));

        if (strippedTitle.equals(queueMenuTitle)) {
            event.setCancelled(true);
            
            int backSlot = musicManager.getMenuConfig().getInt("queue-menu.items.back.slot", 26);
            if (event.getSlot() == backSlot) {
                String action = musicManager.getMenuConfig().getString("queue-menu.items.back.action", "back");
                
                if (action.startsWith("[player] ")) {
                    player.performCommand(action.substring(9).replace("{player}", player.getName()));
                } else if (action.startsWith("[console] ")) {
                    org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(), action.substring(10).replace("{player}", player.getName()));
                } else {
                    // Default behavior for "back" or unknown
                    musicManager.openMainMenu(player);
                }
            }
            return;
        }
        
        // Check Songs Menu
        if (strippedTitle.equals(songsMenuTitle)) {
            event.setCancelled(true);
            
            if (event.getCurrentItem() == null || event.getCurrentItem().getType().isAir()) return;
            
            // Check Back Button
            if (event.getSlot() == musicManager.getJavaBackSlot()) {
                player.closeInventory();
                if (musicManager.getJavaBackCommand().equalsIgnoreCase("menu")) {
                    musicManager.openMainMenu(player);
                }
                return;
            }
            
            // Find which song was clicked using NBT
            ItemMeta meta = event.getCurrentItem().getItemMeta();
            if (meta != null) {
                String songId = meta.getPersistentDataContainer().get(
                    new NamespacedKey(musicManager.getPlugin(), "nonchat_song_id"), 
                    PersistentDataType.STRING
                );
                
                if (songId != null) {
                    player.closeInventory();
                    musicManager.playSong(player, songId);
                    return;
                }
            }
        }
    }
}