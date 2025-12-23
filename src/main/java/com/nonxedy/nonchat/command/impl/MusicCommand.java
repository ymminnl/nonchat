package com.nonxedy.nonchat.command.impl;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.nonxedy.nonchat.music.MusicManager;
import com.nonxedy.nonchat.util.core.colors.ColorUtil;

import com.nonxedy.nonchat.Nonchat;

public class MusicCommand implements CommandExecutor {

    private final Nonchat plugin;
    private final MusicManager musicManager;

    public MusicCommand(Nonchat plugin, MusicManager musicManager) {
        this.plugin = plugin;
        this.musicManager = musicManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            String msg = plugin.getConfigService().getMessages().getString("player-only");
            if (msg == null) msg = "&cOnly players can use this command.";
            sender.sendMessage(ColorUtil.parseColor(msg));
            return true;
        }

        if (args.length > 0) {
            String sub = args[0].toLowerCase();
            String noPerm = plugin.getConfigService().getMessages().getString("no-permission");
            if (noPerm == null) noPerm = "&cNo permission.";
            
            if (sub.equals("skip")) {
                if (!player.hasPermission("nonchat.music.admin")) {
                    player.sendMessage(ColorUtil.parseColor(noPerm));
                    return true;
                }
                musicManager.skipSong(player);
                return true;
            }
            if (sub.equals("stop")) {
                if (!player.hasPermission("nonchat.music.admin")) {
                    player.sendMessage(ColorUtil.parseColor(noPerm));
                    return true;
                }
                musicManager.stopMusic(player);
                return true;
            }
        }

        musicManager.openMainMenu(player);
        return true;
    }
}
