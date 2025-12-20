package com.nonxedy.nonchat.command.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.geysermc.floodgate.api.FloodgateApi;

import com.nonxedy.nonchat.Nonchat;
import com.nonxedy.nonchat.config.PluginMessages;
import com.nonxedy.nonchat.gui.TagMenuBedrock;
import com.nonxedy.nonchat.tags.Tag;
import com.nonxedy.nonchat.tags.TagManager;
import com.nonxedy.nonchat.util.core.colors.ColorUtil;

import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;

public class TagCommand implements CommandExecutor, TabCompleter {

    private final Nonchat plugin;
    private final TagManager tagManager;
    private final PluginMessages messages;

    public TagCommand(Nonchat plugin, TagManager tagManager, PluginMessages messages) {
        this.plugin = plugin;
        this.tagManager = tagManager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtil.parseColor(messages.getString("player-only")));
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "set" -> handleSet(player, args);
            case "list" -> handleList(player, args);
            case "reset" -> handleReset(player, args);
            case "menu" -> handleMenu(player, args);
            default -> sendHelp(player);
        }

        return true;
    }

    private void handleMenu(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ColorUtil.parseColor(messages.getString("tags-usage-menu")));
            return;
        }
        
        String category = args[1];
        if (tagManager.getTags(category) == null) {
            player.sendMessage(ColorUtil.parseColor(messages.getString("tags-category-not-found").replace("{category}", category)));
            return;
        }
        
        // Check for Bedrock player
        if (plugin.isFloodgateEnabled() && FloodgateApi.getInstance().isFloodgatePlayer(player.getUniqueId())) {
            Object menuObj = plugin.getTagMenuBedrock();
            if (menuObj instanceof TagMenuBedrock menu) {
                menu.open(player, category, 1);
                return;
            }
        }
        
        // Open Java Menu
        plugin.getTagMenuJava().open(player, category, 1);
    }

    private void handleSet(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(ColorUtil.parseColor(messages.getString("tags-usage-set")));
            return;
        }

        String category = args[1];
        String tagId = args[2];
        Player target = player; // Default to self

        // Check for optional target argument
        if (args.length >= 4) {
            if (!player.hasPermission("nonchat.command.tags.set.others")) {
                player.sendMessage(ColorUtil.parseColor(messages.getString("no-permission")));
                return;
            }
            target = org.bukkit.Bukkit.getPlayer(args[3]);
            if (target == null) {
                player.sendMessage(ColorUtil.parseColor(messages.getString("player-not-found")));
                return;
            }
        }

        Map<String, Tag> tags = tagManager.getTags(category);
        if (tags == null) {
            player.sendMessage(ColorUtil.parseColor(messages.getString("tags-category-not-found").replace("{category}", category)));
            return;
        }

        Tag tag = tags.get(tagId);
        if (tag == null) {
            player.sendMessage(ColorUtil.parseColor(messages.getString("tags-tag-not-found")
                .replace("{tag}", tagId)
                .replace("{category}", category)));
            return;
        }

        // Check permissions:
        // If setting for self: check if player has permission for the tag
        // If setting for others: bypass tag permission check (admin override)
        if (target.equals(player) && !tag.getPermission().isEmpty() && !player.hasPermission(tag.getPermission())) {
            player.sendMessage(ColorUtil.parseColor(messages.getString("no-permission")));
            return;
        }

        tagManager.setPlayerTag(target, category, tagId);
        
        if (target.equals(player)) {
            player.sendMessage(ColorUtil.parseColor(messages.getString("tags-set-success")
                .replace("{tag}", tag.getDisplay())
                .replace("{category}", category)));
        } else {
            player.sendMessage(ColorUtil.parseColor(messages.getString("tags-set-other")
                .replace("{tag}", tag.getDisplay())
                .replace("{category}", category)
                .replace("{player}", target.getName())));
            target.sendMessage(ColorUtil.parseColor(messages.getString("tags-set-success")
                .replace("{tag}", tag.getDisplay())
                .replace("{category}", category)));
        }
    }
    
    private void handleReset(Player player, String[] args) {
        if (!player.hasPermission("nonchat.command.tags.reset")) {
            player.sendMessage(ColorUtil.parseColor(messages.getString("no-permission")));
            return;
        }

        if (args.length < 3) {
            player.sendMessage(ColorUtil.parseColor(messages.getString("tags-usage-reset")));
            return;
        }

        String category = args[1];
        String targetName = args[2];

        if (tagManager.getTags(category) == null) {
            player.sendMessage(ColorUtil.parseColor(messages.getString("tags-category-not-found").replace("{category}", category)));
            return;
        }

        Player target = org.bukkit.Bukkit.getPlayer(targetName);
        if (target == null) {
            player.sendMessage(ColorUtil.parseColor(messages.getString("player-not-found")));
            return;
        }

        tagManager.resetPlayerTag(target, category);
        player.sendMessage(ColorUtil.parseColor(messages.getString("tags-reset-target")
            .replace("{player}", target.getName())
            .replace("{category}", category)));
        target.sendMessage(ColorUtil.parseColor(messages.getString("tags-reset-success").replace("{category}", category)));
    }
    
    private void handleList(Player player, String[] args) {
        if (args.length < 2) {
             player.sendMessage(ColorUtil.parseColor(messages.getString("tags-usage-list")));
             return;
        }
        
        String category = args[1];
        Map<String, Tag> tags = tagManager.getTags(category);
        
        if (tags == null) {
            player.sendMessage(ColorUtil.parseColor(messages.getString("tags-category-not-found").replace("{category}", category)));
            return;
        }
        
        player.sendMessage(ColorUtil.parseColor("&8&m------------------------"));
        player.sendMessage(ColorUtil.parseColor(messages.getString("tags-list-header").replace("{category}", category)));
        
        for (Tag tag : tags.values()) {
            boolean hasPerm = tag.getPermission().isEmpty() || player.hasPermission(tag.getPermission());
            String color = hasPerm ? "&a" : "&c";
            
            // Process display
            String display = tag.getDisplay();
            display = display.replace("</>", "<reset>");
            display = ColorUtil.convertCompactGradients(display);
            
            if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
                try {
                    display = PlaceholderAPI.setPlaceholders(player, display);
                } catch (Exception ignored) {}
            }
            
            Component line = ColorUtil.parseComponent(color + "- " + tag.getId() + ": ")
                .append(ColorUtil.parseComponent(display));
                
            player.sendMessage(line);
        }
        player.sendMessage(ColorUtil.parseColor("&8&m------------------------"));
    }

    private void sendHelp(Player player) {
        player.sendMessage(ColorUtil.parseColor(messages.getString("tags-help-header")));
        player.sendMessage(ColorUtil.parseColor(messages.getString("tags-help-list")));
        player.sendMessage(ColorUtil.parseColor(messages.getString("tags-help-set")));
        player.sendMessage(ColorUtil.parseColor(messages.getString("tags-help-menu")));
        if (player.hasPermission("nonchat.command.tags.reset")) {
            player.sendMessage(ColorUtil.parseColor(messages.getString("tags-help-reset")));
        }
        player.sendMessage(ColorUtil.parseColor(messages.getString("tags-help-footer")));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            completions.add("set");
            completions.add("list");
            completions.add("menu");
            if (sender.hasPermission("nonchat.command.tags.reset")) {
                completions.add("reset");
            }
            return filter(completions, args[0]);
        }
        
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("list") || args[0].equalsIgnoreCase("reset") || args[0].equalsIgnoreCase("menu")) {
                return filter(new ArrayList<>(tagManager.getCategories()), args[1]);
            }
        }
        
        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("set")) {
                String category = args[1];
                Map<String, Tag> tags = tagManager.getTags(category);
                if (tags != null) {
                    return filter(new ArrayList<>(tags.keySet()), args[2]);
                }
            } else if (args[0].equalsIgnoreCase("reset")) {
                return null; // Return null to show player list
            }
        }
        
        if (args.length == 4 && args[0].equalsIgnoreCase("set")) {
            if (sender.hasPermission("nonchat.command.tags.set.others")) {
                return null; // Show player list
            }
        }

        return Collections.emptyList();
    }
    
    private List<String> filter(List<String> list, String token) {
        return list.stream()
            .filter(s -> s.toLowerCase().startsWith(token.toLowerCase()))
            .collect(Collectors.toList());
    }
}