package com.nonxedy.nonchat.gui;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import com.nonxedy.nonchat.util.items.ItemUtil;

public class GUIUtil {

    public static JavaGUIConfig.GUIItem parseGUIItem(ConfigurationSection section) {
        if (section == null) return null;

        String matName = section.getString("material", "STONE");
        Material material = Material.matchMaterial(matName);
        if (material == null) material = Material.STONE;

        String name = section.getString("name", "");
        List<String> lore = section.getStringList("lore");
        int modelData = section.getInt("model-data", 0);
        String texture = section.getString("texture", "");
        String bedrockIcon = section.getString("bedrock-icon", "");
        boolean enabled = section.getBoolean("enabled", true);
        
        // Parse slots
        List<Integer> slots = new ArrayList<>();
        if (section.contains("slots")) {
            List<String> slotList = section.getStringList("slots");
            for (String s : slotList) parseSlots(s, slots);
        } else if (section.contains("slot")) {
            slots.add(section.getInt("slot"));
        }
        
        List<String> actions = section.getStringList("actions");

        ItemStack itemStack = ItemUtil.createItem(material, name, lore, modelData, texture);
        return new JavaGUIConfig.GUIItem(itemStack, slots, actions, material, name, lore, modelData, texture, bedrockIcon, enabled);
    }

    public static void parseSlots(String slotStr, List<Integer> targetList) {
        if (slotStr.contains("-")) {
            String[] parts = slotStr.split("-");
            try {
                int start = Integer.parseInt(parts[0].trim());
                int end = Integer.parseInt(parts[1].trim());
                for (int i = start; i <= end; i++) {
                    targetList.add(i);
                }
            } catch (NumberFormatException ignored) {}
        } else {
            try {
                targetList.add(Integer.parseInt(slotStr.trim()));
            } catch (NumberFormatException ignored) {}
        }
    }
}
