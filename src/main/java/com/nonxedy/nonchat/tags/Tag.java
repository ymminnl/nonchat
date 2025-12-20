package com.nonxedy.nonchat.tags;

import java.util.List;
import org.bukkit.Material;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class Tag {
    private final String id;
    private final String display; // The actual visual tag
    private final String permission;
    private final String category; // Derived from filename
    private final boolean isDefault;
    
    // GUI Properties
    private final Material iconMaterial;
    private final String iconName;
    private final List<String> iconLore;
    private final int customModelData;
    private final String texture; // Base64 texture for player heads
    private final int order;
    private final String bedrockIcon; // Path or URL for Bedrock form icon
    
    // Locked GUI Properties
    private final Material lockedIconMaterial;
    private final String lockedIconName;
    private final List<String> lockedIconLore;
    private final int lockedCustomModelData;
    private final String lockedTexture;
    private final String lockedBedrockIcon;
}
