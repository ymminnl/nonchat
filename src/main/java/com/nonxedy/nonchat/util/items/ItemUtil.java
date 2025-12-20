package com.nonxedy.nonchat.util.items;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import com.nonxedy.nonchat.util.core.colors.ColorUtil;

import net.kyori.adventure.text.Component;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;

import com.nonxedy.nonchat.util.core.colors.ColorUtil;
import net.kyori.adventure.text.Component;

public class ItemUtil {

    /**
     * Creates an ItemStack with specified properties
     */
    public static ItemStack createItem(Material material, String name, List<String> lore, int modelData, String texture) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta == null) return item;

        // Set Name
        if (name != null && !name.isEmpty()) {
            meta.displayName(ColorUtil.parseComponent(name));
        }

        // Set Lore
        if (lore != null && !lore.isEmpty()) {
            List<Component> loreComponents = new java.util.ArrayList<>();
            for (String line : lore) {
                loreComponents.add(ColorUtil.parseComponent(line));
            }
            meta.lore(loreComponents);
        }

        // Set CustomModelData
        if (modelData > 0) {
            meta.setCustomModelData(modelData);
        }

        // Apply meta so far (needed before casting to SkullMeta)
        item.setItemMeta(meta);

        // Set Texture (only for PLAYER_HEAD)
        if (material == Material.PLAYER_HEAD && texture != null && !texture.isEmpty()) {
            applyHeadTexture(item, texture);
        }

        return item;
    }

    /**
     * Applies a Base64 texture to a player head item using Paper API
     */
    private static void applyHeadTexture(ItemStack head, String base64) {
        if (base64 == null || base64.isEmpty()) return;

        try {
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            
            // Create a PlayerProfile with a random UUID
            PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
            
            // Set the texture property
            profile.setProperty(new ProfileProperty("textures", base64));
            
            // Set the profile on the SkullMeta
            meta.setPlayerProfile(profile);
            
            head.setItemMeta(meta);
        } catch (Exception e) {
            // Fallback or log if Paper API fails (should not happen on Paper servers)
            Bukkit.getLogger().log(Level.WARNING, "Failed to set head texture: " + e.getMessage());
        }
    }
}
