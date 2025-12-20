package com.nonxedy.nonchat.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import com.nonxedy.nonchat.Nonchat;
import com.nonxedy.nonchat.tags.Tag;
import com.nonxedy.nonchat.tags.TagManager;
import com.nonxedy.nonchat.util.core.colors.ColorUtil;
import com.nonxedy.nonchat.util.integration.external.IntegrationUtil;
import com.nonxedy.nonchat.util.items.ItemUtil;

public class TagMenuJava {

    private final Nonchat plugin;
    private final JavaGUIConfig config;
    private final TagManager tagManager;

    public TagMenuJava(Nonchat plugin, JavaGUIConfig config, TagManager tagManager) {
        this.plugin = plugin;
        this.config = config;
        this.tagManager = tagManager;
    }

    public void open(Player player, String category, int page) {
        Map<String, Tag> tagsMap = tagManager.getTags(category);
        if (tagsMap == null) return;

        // Sort tags by order
        List<Tag> tags = new ArrayList<>(tagsMap.values());
        tags.sort(Comparator.comparingInt(Tag::getOrder));

        // Get category metadata (dynamic title and size)
        TagManager.CategoryMeta meta = tagManager.getCategoryMeta(category);
        
        // Determine Size
        int size = config.getSize();
        if (meta != null && meta.getRows() > 0) {
            size = meta.getRows() * 9;
        }

        // Calculate pagination
        List<Integer> tagSlots = config.getTagSlots();
        if (meta != null && meta.getTagSlots() != null && !meta.getTagSlots().isEmpty()) {
            tagSlots = meta.getTagSlots();
        }
        
        int tagsPerPage = tagSlots.size();
        
        // Filter slots that are out of bounds for the current size
        List<Integer> validTagSlots = new ArrayList<>();
        for (int slot : tagSlots) {
            if (isValidSlot(slot, size)) {
                validTagSlots.add(slot);
            }
        }
        tagsPerPage = validTagSlots.size();
        if (tagsPerPage == 0) tagsPerPage = 1; // Prevent div by zero

        int totalPages = (int) Math.ceil((double) tags.size() / tagsPerPage);
        
        if (page < 1) page = 1;
        if (page > totalPages && totalPages > 0) page = totalPages;

        // Create Inventory with Placeholders in Title
        // Priority: menu-title > title > global config
        String titleFormat = config.getTitle();
        if (meta != null) {
            if (meta.getMenuTitle() != null) {
                titleFormat = meta.getMenuTitle();
            } else if (meta.getDisplayName() != null) {
                titleFormat = meta.getDisplayName();
            }
        }
        
        String titleRaw = titleFormat
                .replace("{category}", category)
                .replace("{page}", String.valueOf(page));
        String title = IntegrationUtil.processPlaceholders(player, titleRaw);
        
        TagHolder holder = new TagHolder(category, page, totalPages);
        Inventory inv = Bukkit.createInventory(holder, size, ColorUtil.parseComponent(title));

        // 1. Explicit Fillers (Borders, etc.)
        List<JavaGUIConfig.GUIItem> backgroundFillers = new ArrayList<>();
        
        // Determine fillers to use (Category specific or Global)
        Map<String, JavaGUIConfig.GUIItem> fillersToUse = config.getFillers();
        if (meta != null && meta.getFillers() != null && !meta.getFillers().isEmpty()) {
            fillersToUse = meta.getFillers();
        }
        
        for (JavaGUIConfig.GUIItem filler : fillersToUse.values()) {
            if (filler.getSlots().isEmpty()) {
                backgroundFillers.add(filler);
            } else {
                for (int slot : filler.getSlots()) {
                    if (isValidSlot(slot, size)) {
                        inv.setItem(slot, filler.getItem());
                    }
                }
            }
        }

        // 2. Buttons (Static & Navigation)
        Map<String, JavaGUIConfig.GUIItem> buttonsToUse = new java.util.HashMap<>(config.getButtons());
        if (meta != null && meta.getButtons() != null) {
            buttonsToUse.putAll(meta.getButtons());
        }

        for (Map.Entry<String, JavaGUIConfig.GUIItem> entry : buttonsToUse.entrySet()) {
            String key = entry.getKey();
            JavaGUIConfig.GUIItem btn = entry.getValue();
            
            if (!btn.isEnabled()) continue;
            
            int slot = btn.getSingleSlot();

            if (!isValidSlot(slot, size)) continue;

            if (key.equalsIgnoreCase("previous")) {
                if (page > 1) {
                    ItemStack item = createButtonWithPlaceholders(btn, player, page, totalPages);
                    inv.setItem(slot, item);
                }
            } else if (key.equalsIgnoreCase("next")) {
                if (page < totalPages) {
                    ItemStack item = createButtonWithPlaceholders(btn, player, page, totalPages);
                    inv.setItem(slot, item);
                }
            } else {
                // Static buttons
                ItemStack item = createButtonWithPlaceholders(btn, player, page, totalPages);
                inv.setItem(slot, item);
            }
        }

        // 3. Tags
        if (!tags.isEmpty()) {
            int startIndex = (page - 1) * tagsPerPage;
            int endIndex = Math.min(startIndex + tagsPerPage, tags.size());

            for (int i = startIndex; i < endIndex; i++) {
                Tag tag = tags.get(i);
                int slotIndex = i - startIndex;
                if (slotIndex >= validTagSlots.size()) break;
                
                int slot = validTagSlots.get(slotIndex);
                ItemStack tagItem = createTagItem(tag, player);
                inv.setItem(slot, tagItem);
                
                // Store mapping for listener
                holder.setTagAtSlot(slot, tag);
            }
        }
        
        // 4. Background Fillers (Fill remaining empty slots)
        for (JavaGUIConfig.GUIItem bgFiller : backgroundFillers) {
            for (int i = 0; i < size; i++) {
                ItemStack current = inv.getItem(i);
                if (current == null || current.getType().isAir()) {
                    inv.setItem(i, bgFiller.getItem());
                }
            }
        }

        player.openInventory(inv);
        playSound(player, config.getOpenSound());
    }

    private ItemStack createTagItem(Tag tag, Player player) {
        boolean hasPermission = tag.getPermission().isEmpty() || player.hasPermission(tag.getPermission());
        boolean useLocked = !hasPermission && tag.getLockedIconMaterial() != null;
        
        String rawName = useLocked ? tag.getLockedIconName() : tag.getIconName();
        if (rawName == null) rawName = tag.getIconName();
        
        List<String> rawLore = useLocked && tag.getLockedIconLore() != null ? tag.getLockedIconLore() : tag.getIconLore();
        
        Material material = useLocked ? tag.getLockedIconMaterial() : tag.getIconMaterial();
        int modelData = useLocked ? tag.getLockedCustomModelData() : tag.getCustomModelData();
        String texture = useLocked ? tag.getLockedTexture() : tag.getTexture();

        // Process placeholders in name and and lore
        String name = IntegrationUtil.processPlaceholders(player, rawName);
        
        // Java Item Name: Remove anything after \n (subtitle for Bedrock)
        if (name.contains("\n")) {
            name = name.split("\n")[0];
        } else if (name.contains("\\n")) {
            name = name.split("\\\\n")[0];
        }

        // Fix shorthands explicitly
        name = name.replace("</>", "<reset>");
        name = ColorUtil.convertCompactGradients(name);
        
        List<String> lore = new ArrayList<>();
        if (rawLore != null) {
            for (String line : rawLore) {
                String processed = IntegrationUtil.processPlaceholders(player, line);
                processed = processed.replace("</>", "<reset>");
                processed = ColorUtil.convertCompactGradients(processed);
                lore.add(processed);
            }
        }

        return ItemUtil.createItem(
            material, 
            name, 
            lore, 
            modelData, 
            texture
        );
    }
    
    private ItemStack createButtonWithPlaceholders(JavaGUIConfig.GUIItem guiItem, Player player, int page, int totalPages) {
        String name = guiItem.getName()
            .replace("{page}", String.valueOf(page))
            .replace("{next_page}", String.valueOf(page + 1))
            .replace("{prev_page}", String.valueOf(page - 1))
            .replace("{total_pages}", String.valueOf(totalPages));
            
        name = IntegrationUtil.processPlaceholders(player, name);
        
        // Java Item Name: Remove anything after \n (subtitle for Bedrock)
        if (name.contains("\n")) {
            name = name.split("\n")[0];
        } else if (name.contains("\\n")) {
            name = name.split("\\\\n")[0];
        }

        name = name.replace("</>", "<reset>");
        name = ColorUtil.convertCompactGradients(name);
        
        List<String> lore = new ArrayList<>();
        if (guiItem.getLore() != null) {
            for (String line : guiItem.getLore()) {
                String processed = line
                    .replace("{page}", String.valueOf(page))
                    .replace("{next_page}", String.valueOf(page + 1))
                    .replace("{prev_page}", String.valueOf(page - 1))
                    .replace("{total_pages}", String.valueOf(totalPages));
                processed = IntegrationUtil.processPlaceholders(player, processed);
                processed = processed.replace("</>", "<reset>");
                processed = ColorUtil.convertCompactGradients(processed);
                lore.add(processed);
            }
        }

        return ItemUtil.createItem(
            guiItem.getMaterial(),
            name,
            lore,
            guiItem.getModelData(),
            guiItem.getTexture()
        );
    }

    private boolean isValidSlot(int slot, int size) {
        return slot >= 0 && slot < size;
    }
    
    private void playSound(Player player, String soundName) {
        try {
            if (soundName != null && !soundName.isEmpty()) {
                player.playSound(player.getLocation(), Sound.valueOf(soundName.toUpperCase().replace(".", "_")), 1f, 1f);
            }
        } catch (Exception ignored) {}
    }

    public static class TagHolder implements InventoryHolder {
        private final String category;
        private final int page;
        private final int totalPages;
        private final Map<Integer, Tag> slotTags = new java.util.HashMap<>();

        public TagHolder(String category, int page, int totalPages) {
            this.category = category;
            this.page = page;
            this.totalPages = totalPages;
        }

        public void setTagAtSlot(int slot, Tag tag) {
            slotTags.put(slot, tag);
        }

        public Tag getTagAtSlot(int slot) {
            return slotTags.get(slot);
        }

        public String getCategory() { return category; }
        public int getPage() { return page; }
        public int getTotalPages() { return totalPages; }

        @Override
        public @NotNull Inventory getInventory() { return null; } 
    }
}