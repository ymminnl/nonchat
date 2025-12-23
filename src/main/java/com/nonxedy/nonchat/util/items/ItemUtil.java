package com.nonxedy.nonchat.util.items;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nonxedy.nonchat.util.core.colors.ColorUtil;

import net.kyori.adventure.text.Component;

public class ItemUtil {

    private static final Pattern URL_PATTERN = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"");
    // Cache for PlayerProfiles to avoid repeated Base64 decoding and JSON parsing
    private static final java.util.Map<String, PlayerProfile> profileCache = new java.util.concurrent.ConcurrentHashMap<>();

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
     * Applies a Base64 texture to a player head item.
     */
    public static void applyHeadTexture(ItemStack head, String base64) {
        if (base64 == null || base64.isEmpty()) return;

        try {
            // Sanitize whitespace once
            String cleanBase64 = base64.replaceAll("\\s+", "");
            
            SkullMeta meta = (SkullMeta) head.getItemMeta();

            // 1. Check cache first
            PlayerProfile cachedProfile = profileCache.get(cleanBase64);
            if (cachedProfile != null) {
                meta.setOwnerProfile(cachedProfile);
                head.setItemMeta(meta);
                return;
            }

            UUID uuid = new UUID(cleanBase64.hashCode(), cleanBase64.hashCode());
            
            // 2. Extract URL using GSON
            String urlString = getUrlFromBase64(cleanBase64);
            
            if (urlString != null) {
                // Try Modern Bukkit API (Preferred)
                try {
                    PlayerProfile profile = Bukkit.createPlayerProfile(uuid, "Nonchat");
                    PlayerTextures textures = profile.getTextures();
                    textures.setSkin(new URL(urlString));
                    profile.setTextures(textures);
                    
                    // Cache the successful profile
                    profileCache.put(cleanBase64, profile);
                    
                    meta.setOwnerProfile(profile);
                    head.setItemMeta(meta);
                    return; 
                } catch (Throwable t) {
                    // Modern API failed
                }
            } else {
                // Debug log for bad base64
                // Bukkit.getLogger().warning("[Nonchat] Bad Base64 (Len: " + cleanBase64.length() + ")");
            }

            // 3. Fallback: Basic Reflection (If Modern API fails)
            try {
                Class<?> gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
                Class<?> propertyClass = Class.forName("com.mojang.authlib.properties.Property");
                
                Constructor<?> gameProfileConstructor = gameProfileClass.getConstructor(UUID.class, String.class);
                Object profile = gameProfileConstructor.newInstance(uuid, "Nonchat");
                
                Constructor<?> propertyConstructor = propertyClass.getConstructor(String.class, String.class);
                Object property = propertyConstructor.newInstance("textures", cleanBase64);
                
                // Try to get properties map and put. If immutable, we catch and ignore.
                Method getPropertiesMethod = gameProfileClass.getMethod("getProperties");
                Object propertyMap = getPropertiesMethod.invoke(profile);
                
                Method putMethod = propertyMap.getClass().getMethod("put", Object.class, Object.class);
                putMethod.invoke(propertyMap, "textures", property);

                Field profileField = meta.getClass().getDeclaredField("profile");
                profileField.setAccessible(true);
                profileField.set(meta, profile);
                head.setItemMeta(meta);
            } catch (Exception e) {
                // Fallback failed (e.g. immutable map). We stop here to avoid crashes.
            }

        } catch (Exception e) {
            Bukkit.getLogger().log(Level.WARNING, "Error preparing head texture: " + e.getMessage());
        }
    }

    private static String getUrlFromBase64(String base64) {
        String decoded = null;
        try {
            byte[] decodedBytes = Base64.getDecoder().decode(base64);
            decoded = new String(decodedBytes, StandardCharsets.UTF_8);
            
            // 1. Try Regex First (Most robust for partial/malformed JSON)
            Matcher matcher = URL_PATTERN.matcher(decoded);
            if (matcher.find()) {
                return matcher.group(1);
            }
            
            // 2. Try GSON as backup (if regex fails for some reason)
            JsonObject jsonObject = new JsonParser().parse(decoded).getAsJsonObject();
            JsonObject textures = jsonObject.getAsJsonObject("textures");
            if (textures != null) {
                JsonObject skin = textures.getAsJsonObject("SKIN");
                if (skin != null && skin.has("url")) {
                    return skin.get("url").getAsString();
                }
            }
        } catch (Exception e) {
            // Only log if both methods failed
            if (decoded != null) {
               // Bukkit.getLogger().warning("[Nonchat] Failed to extract URL. Decoded start: " + decoded.substring(0, Math.min(50, decoded.length())));
            }
        }
        return null;
    }
}
