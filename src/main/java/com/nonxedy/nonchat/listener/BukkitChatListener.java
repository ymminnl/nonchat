package com.nonxedy.nonchat.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import com.nonxedy.nonchat.Nonchat;
import com.nonxedy.nonchat.core.ChatManager;
import com.nonxedy.nonchat.service.ChatService;

public class BukkitChatListener extends ChatListener {

    private final Nonchat plugin;

    public BukkitChatListener(Nonchat plugin, ChatManager chatManager, ChatService chatService) {
        super(chatManager, chatService);
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAsyncPlayerChat(AsyncPlayerChatEvent event) {
        String message = event.getMessage();
        
        net.kyori.adventure.text.Component result = null;
        if (chatManager != null) {
            result = chatManager.processChat(event.getPlayer(), message);
        }

        if (result != null) {
            // Global chat - Use setFormat with legacy serialization
            // We need to serialize the component to a legacy string
            String legacyFormat = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(result);
            
            // Bukkit's setFormat expects "%1$s" and "%2$s" for player and message
            // But we have already formatted everything into 'result'
            // So we just set the format to our result and ignore the arguments by escaping percentage signs if necessary?
            // Actually, the easiest hack is to set format to our result + ignoring args:
            // But wait, setFormat injects player and message.
            
            // If we formatted it completely, we just want to print it raw.
            // setFormat(legacyFormat.replace("%", "%%")); // Escape percents so it doesn't look for args
            // But wait, setFormat throws exception if it doesn't find %1$s and %2$s? No, usually not strictly required by Spigot but some plugins might complain.
            // Safe way: setFormat("%2$s"); setMessage(legacyFormat);
            
            event.setFormat("%2$s");
            event.setMessage(legacyFormat);
        } else {
            // Handled manually or cancelled
            event.setCancelled(true);
        }
    }
}
