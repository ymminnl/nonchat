package com.nonxedy.nonchat.music;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.cumulus.util.FormImage;

import com.nonxedy.nonchat.Nonchat;
import com.nonxedy.nonchat.util.core.colors.ColorUtil;
import com.nonxedy.nonchat.util.integration.external.IntegrationUtil;
import com.nonxedy.nonchat.util.items.ItemUtil;

import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

public class MusicManager {

    @Getter
    private final Nonchat plugin;
    private final File configFile;
    
    @Getter
    private final Map<String, Song> songs = new HashMap<>();
    private final Map<String, EffectManager.Preset> effectPresets = new HashMap<>();
    private final EffectManager effectManager;
    
    // Main Menu Config
    private final File menuConfigFile;
    @Getter
    private YamlConfiguration menuConfig;
    @Getter
    private final Map<Integer, String> mainMenuActions = new HashMap<>();
    
    // Session System
    private final List<MusicSession> activeSessions = new ArrayList<>();
    
    // Muted Players
    private final Set<UUID> mutedPlayers = new HashSet<>();
    private final Map<UUID, Float> playerVolumes = new HashMap<>();
    
    // Settings
    private double radius;
    private float volume;
    private float pitch;
    
    // DJ Effect Settings
    private boolean djEffectEnabled;
    private Particle djEffectParticle;
    private int djEffectAmount;
    private double djEffectHeight;
    private double djEffectSpreadX;
    private double djEffectSpreadY;
    private double djEffectSpreadZ;
    
    // GUI Settings (Java)
    @Getter
    private String javaTitle;
    @Getter
    private String javaQueueTitle;
    private int javaRows;
    private int javaQueueRows;
    private Material javaFillMaterial;
    private final List<Integer> songSlots = new ArrayList<>();
    
    // Java Back Button
    @Getter private int javaBackSlot;
    private Material javaBackMaterial;
    private String javaBackName;
    private List<String> javaBackLore;
    private String javaBackTexture;
    @Getter private String javaBackCommand;

    public MusicManager(Nonchat plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "music.yml");
        this.menuConfigFile = new File(plugin.getDataFolder(), "gui/menu_music.yml");
        this.effectManager = new EffectManager(plugin);
        startSessionTask();
    }
    
    public boolean isMuted(Player player) {
        return mutedPlayers.contains(player.getUniqueId());
    }
    
    public float getPlayerVolume(Player player) {
        return playerVolumes.getOrDefault(player.getUniqueId(), 1.0f);
    }
    
    public void setVolume(Player player, float volume) {
        float newVol = Math.max(0.0f, Math.min(1.0f, volume));
        playerVolumes.put(player.getUniqueId(), newVol);
        saveSettingAsync(player, "music_volume", String.valueOf(newVol));
        
        if (newVol <= 0.001f) {
            player.stopSound(SoundCategory.RECORDS);
        }
    }

    public void changeVolume(Player player, float change) {
        float current = getPlayerVolume(player);
        setVolume(player, current + change);
        
        float newVol = getPlayerVolume(player);
        String msg = getMsg("music-volume-changed", "&aVolume set to: &e{volume}%");
        player.sendMessage(ColorUtil.parseColor(msg.replace("{volume}", String.valueOf((int)(newVol * 100)))));
    }
    
    public void toggleMute(Player player) {
        if (mutedPlayers.contains(player.getUniqueId())) {
            mutedPlayers.remove(player.getUniqueId());
            String msg = getMsg("music-toggle-on", "&aMusic enabled.");
            player.sendMessage(ColorUtil.parseColor(msg));
            saveSettingAsync(player, "music_muted", "false");
        } else {
            mutedPlayers.add(player.getUniqueId());
            String msg = getMsg("music-toggle-off", "&cMusic disabled.");
            player.sendMessage(ColorUtil.parseColor(msg));
            saveSettingAsync(player, "music_muted", "true");
            player.stopSound(SoundCategory.RECORDS);
        }
        
        if (isBedrockPlayer(player)) {
            openBedrockOptionsMenu(player);
        } else {
            openOptionsMenu(player);
        }
    }
    
    public void skipSong(Player player) {
        MusicSession session = findNearbySession(player.getLocation());
        if (session != null) {
            session.next();
            String msg = getMsg("music-admin-skip", "&aSkipped current song.");
            player.sendMessage(ColorUtil.parseColor(msg));
        } else {
            String msg = getMsg("music-no-music-nearby", "&cNo music playing nearby.");
            player.sendMessage(ColorUtil.parseColor(msg));
        }
    }
    
    public void stopMusic(Player player) {
        MusicSession session = findNearbySession(player.getLocation());
        if (session != null) {
            session.clear();
            
            // Stop sound packet explicitly for players in radius
            double radSq = radius * radius;
            Location loc = player.getLocation();
            for (Player p : loc.getWorld().getPlayers()) {
                if (p.getLocation().distanceSquared(loc) <= radSq) {
                    p.stopSound(SoundCategory.RECORDS);
                }
            }
                
            String msg = getMsg("music-admin-stop", "&cMusic stopped and queue cleared.");
            player.sendMessage(ColorUtil.parseColor(msg));
        } else {
            String msg = getMsg("music-no-music-nearby", "&cNo music playing nearby.");
            player.sendMessage(ColorUtil.parseColor(msg));
        }
    }
    
    private String getMsg(String key, String def) {
        String msg = plugin.getConfigService().getMessages().getString(key);
        return msg != null ? msg : def;
    }
    
    public void loadPlayerSettings(Player player) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String valMute = plugin.getDatabaseManager().loadPlayerSetting(player.getUniqueId().toString(), "music_muted");
            if ("true".equals(valMute)) {
                mutedPlayers.add(player.getUniqueId());
            }
            
            String valVol = plugin.getDatabaseManager().loadPlayerSetting(player.getUniqueId().toString(), "music_volume");
            if (valVol != null) {
                try {
                    playerVolumes.put(player.getUniqueId(), Float.parseFloat(valVol));
                } catch (NumberFormatException ignored) {}
            }
        });
    }
    
    public void unloadPlayerSettings(Player player) {
        mutedPlayers.remove(player.getUniqueId());
        playerVolumes.remove(player.getUniqueId());
    }
    
    private void saveSettingAsync(Player player, String key, String value) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.getDatabaseManager().savePlayerSetting(player.getUniqueId().toString(), key, value);
        });
    }
    
    private void startSessionTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Iterator<MusicSession> it = activeSessions.iterator();
            while (it.hasNext()) {
                MusicSession session = it.next();
                session.tick();
                
                if (session.isFinished()) {
                    it.remove(); // Clean up empty sessions
                }
            }
        }, 20L, 20L); // Tick every second
    }

    public void load() {
        if (!configFile.exists()) {
            plugin.saveResource("music.yml", false);
        }
        if (!menuConfigFile.exists()) {
            plugin.saveResource("gui/menu_music.yml", false);
        }
        
        songs.clear();
        effectPresets.clear();
        activeSessions.clear();
        mainMenuActions.clear();
        
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        menuConfig = YamlConfiguration.loadConfiguration(menuConfigFile);
        
        // Load DJ Effect
        djEffectEnabled = config.getBoolean("dj-effect.enabled", true);
        String pName = config.getString("dj-effect.particle", "NOTE");
        try {
            djEffectParticle = Particle.valueOf(pName.toUpperCase());
        } catch (Exception e) {
            djEffectParticle = Particle.NOTE;
        }
        djEffectAmount = config.getInt("dj-effect.amount", 1);
        djEffectHeight = config.getDouble("dj-effect.height", 2.2);
        
        String[] djOffset = config.getString("dj-effect.offset", "0.5, 0.5, 0.5").replace(" ", "").split(",");
        try {
            djEffectSpreadX = Double.parseDouble(djOffset[0]);
            djEffectSpreadY = Double.parseDouble(djOffset[1]);
            djEffectSpreadZ = Double.parseDouble(djOffset[2]);
        } catch (Exception e) {
            djEffectSpreadX = 0.5;
            djEffectSpreadY = 0.5;
            djEffectSpreadZ = 0.5;
        }
        
        // Load Main Menu Actions
        ConfigurationSection itemsSection = menuConfig.getConfigurationSection("items");
        if (itemsSection != null) {
            for (String key : itemsSection.getKeys(false)) {
                ConfigurationSection item = itemsSection.getConfigurationSection(key);
                if (item != null && item.contains("slot") && item.contains("action")) {
                    mainMenuActions.put(item.getInt("slot"), item.getString("action"));
                }
            }
        }
        
        // Load Settings
        radius = config.getDouble("settings.radius", 500);
        volume = (float) config.getDouble("settings.volume", 1.0);
        pitch = (float) config.getDouble("settings.pitch", 1.0);
        
        // Load GUI (Java)
        javaTitle = config.getString("gui.java.title", "&8Music");
        javaQueueTitle = menuConfig.getString("java.queue-menu.title", "&8Music - Queue");
        javaRows = config.getInt("gui.java.rows", 6);
        javaQueueRows = menuConfig.getInt("java.queue-menu.rows", 3);
        String matName = config.getString("gui.java.fill-material", "GRAY_STAINED_GLASS_PANE");
        try {
            javaFillMaterial = Material.valueOf(matName.toUpperCase());
        } catch (Exception e) {
            javaFillMaterial = Material.GRAY_STAINED_GLASS_PANE;
        }

        // Load Song Slots
        songSlots.clear();
        List<String> slotStrings = config.getStringList("gui.java.song-slots");
        for (String slotStr : slotStrings) {
            if (slotStr.contains("-")) {
                String[] parts = slotStr.split("-");
                if (parts.length == 2) {
                    try {
                        int start = Integer.parseInt(parts[0].trim());
                        int end = Integer.parseInt(parts[1].trim());
                        for (int i = start; i <= end; i++) {
                            songSlots.add(i);
                        }
                    } catch (NumberFormatException ignored) {}
                }
            } else {
                try {
                    songSlots.add(Integer.parseInt(slotStr.trim()));
                } catch (NumberFormatException ignored) {}
            }
        }
        
        // Load Java Back Button
        ConfigurationSection backSection = config.getConfigurationSection("gui.java.back-button");
        if (backSection != null) {
            javaBackSlot = backSection.getInt("slot", 49);
            String bMatName = backSection.getString("material", "ARROW");
            try {
                javaBackMaterial = Material.valueOf(bMatName.toUpperCase());
            } catch(Exception e) {
                javaBackMaterial = Material.ARROW;
            }
            javaBackName = backSection.getString("name", "&cBack");
            javaBackLore = backSection.getStringList("lore");
            javaBackTexture = backSection.getString("texture", null);
            javaBackCommand = backSection.getString("command", "menu");
        } else {
            javaBackSlot = -1;
        }
        
        // Load Effects
        ConfigurationSection effectsSection = config.getConfigurationSection("effects");
        if (effectsSection != null) {
            for (String key : effectsSection.getKeys(false)) {
                effectPresets.put(key, new EffectManager.Preset(effectsSection.getConfigurationSection(key)));
            }
        }
        
        // Load Songs
        ConfigurationSection songsSection = config.getConfigurationSection("songs");
        if (songsSection != null) {
            for (String key : songsSection.getKeys(false)) {
                songs.put(key, new Song(key, songsSection.getConfigurationSection(key)));
            }
        }
        
        plugin.getLogger().info("Loaded " + songs.size() + " songs and " + effectPresets.size() + " effect presets.");
    }
    
    public void openMainMenu(Player player) {
        if (isBedrockPlayer(player)) {
            openBedrockMenu(player);
            return;
        }
        
        String title = ColorUtil.parseColor(menuConfig.getString("title", "&8Music Menu"));
        int rows = menuConfig.getInt("rows", 3);
        org.bukkit.inventory.Inventory inv = Bukkit.createInventory(null, rows * 9, title);
        
        // Fill
        String fillMatName = menuConfig.getString("fill-material", "GRAY_STAINED_GLASS_PANE");
        Material fillMat;
        try {
            fillMat = Material.valueOf(fillMatName.toUpperCase());
        } catch (Exception e) {
            fillMat = Material.GRAY_STAINED_GLASS_PANE;
        }
        
        org.bukkit.inventory.ItemStack filler = new org.bukkit.inventory.ItemStack(fillMat);
        org.bukkit.inventory.meta.ItemMeta fillMeta = filler.getItemMeta();
        if (fillMeta != null) {
            fillMeta.setDisplayName(" ");
            filler.setItemMeta(fillMeta);
        }
        
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }
        
        // Items
        ConfigurationSection itemsSection = menuConfig.getConfigurationSection("items");
        if (itemsSection != null) {
            for (String key : itemsSection.getKeys(false)) {
                ConfigurationSection itemSection = itemsSection.getConfigurationSection(key);
                
                // Permission Check
                String perm = itemSection.getString("permission");
                if (perm != null && !perm.isEmpty() && !player.hasPermission(perm)) {
                    continue;
                }
                
                int slot = itemSection.getInt("slot", -1);
                if (slot >= 0 && slot < inv.getSize()) {
                    String matName = itemSection.getString("material", "STONE");
                    Material mat;
                    try {
                        mat = Material.valueOf(matName.toUpperCase());
                    } catch (Exception e) {
                        mat = Material.STONE;
                    }
                    
                    org.bukkit.inventory.ItemStack item = new org.bukkit.inventory.ItemStack(mat);
                    org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
                    if (meta != null) {
                        meta.setDisplayName(ColorUtil.parseColor(itemSection.getString("name", key)));
                        List<String> lore = itemSection.getStringList("lore");
                        List<String> coloredLore = new ArrayList<>();
                        for (String line : lore) coloredLore.add(ColorUtil.parseColor(line));
                        
                        // Dynamic Mute State
                        if (itemSection.getString("action", "").equalsIgnoreCase("toggle_mute")) {
                            coloredLore.add(" ");
                            if (isMuted(player)) {
                                String status = getMsg("music-menu-status-off", "&cStatus: &4Disabled");
                                coloredLore.add(ColorUtil.parseColor(status));
                            } else {
                                String status = getMsg("music-menu-status-on", "&aStatus: &2Enabled");
                                coloredLore.add(ColorUtil.parseColor(status));
                            }
                        }
                        
                        // Texture Support - Apply AFTER saving meta to avoid overwriting
                        meta.setLore(coloredLore);
                        item.setItemMeta(meta);

                        if (mat == Material.PLAYER_HEAD) {
                            String texture = itemSection.getString("texture");
                            if (texture != null && !texture.isEmpty()) {
                                ItemUtil.applyHeadTexture(item, texture);
                            }
                        }
                    }
                    inv.setItem(slot, item);
                }
            }
        }
        
        player.openInventory(inv);
    }
    
    public void openQueueMenu(Player player) {
        if (isBedrockPlayer(player)) {
            sendBedrockQueueMenu(player);
            return;
        }
        
        MusicSession session = findNearbySession(player.getLocation());
        int size = javaQueueRows * 9;
        org.bukkit.inventory.Inventory inv = Bukkit.createInventory(null, size, ColorUtil.parseColor(javaQueueTitle));
        
        if (session == null || (session.getCurrent() == null && session.getQueue().isEmpty())) {
            ConfigurationSection noMusicSection = menuConfig.getConfigurationSection("queue-menu.items.no-music");
            if (noMusicSection != null) {
                String matName = noMusicSection.getString("material", "BARRIER");
                Material mat = Material.matchMaterial(matName);
                if (mat == null) mat = Material.BARRIER;
                org.bukkit.inventory.ItemStack item = new org.bukkit.inventory.ItemStack(mat);
                
                int slot = noMusicSection.getInt("slot", 13);
                
                org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(ColorUtil.parseColor(noMusicSection.getString("name", "&cNo music playing nearby")));
                    List<String> lore = new ArrayList<>();
                    for (String line : noMusicSection.getStringList("lore")) {
                        lore.add(ColorUtil.parseColor(line));
                    }
                    meta.setLore(lore);
                    item.setItemMeta(meta);
                    
                    if (mat == Material.PLAYER_HEAD) {
                        String texture = noMusicSection.getString("texture");
                        if (texture != null && !texture.isEmpty()) {
                            ItemUtil.applyHeadTexture(item, texture);
                        }
                    }
                }
                inv.setItem(slot, item);
            } else {
                org.bukkit.inventory.ItemStack item = new org.bukkit.inventory.ItemStack(Material.BARRIER);
                org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(ColorUtil.parseColor("&cNo music playing nearby"));
                    item.setItemMeta(meta);
                }
                inv.setItem(13, item);
            }
        } else {
            // Show Current
            if (session.getCurrent() != null) {
                org.bukkit.inventory.ItemStack current = new org.bukkit.inventory.ItemStack(Material.JUKEBOX);
                org.bukkit.inventory.meta.ItemMeta meta = current.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(ColorUtil.parseColor("&a&lNow Playing: &e" + session.getCurrent().getSong().getDisplayName()));
                    List<String> lore = new ArrayList<>();
                    lore.add(ColorUtil.parseColor("&7Requested by: &f" + session.getCurrent().getPlayerName()));
                    long timeLeft = (session.getEndTime() - System.currentTimeMillis()) / 1000;
                    lore.add(ColorUtil.parseColor("&7Time left: &f" + timeLeft + "s"));
                    meta.setLore(lore);
                    current.setItemMeta(meta);
                }
                inv.setItem(4, current);
            }
            
            // Queue List
            int slot = 9;
            for (QueueEntry entry : session.getQueue()) {
                if (slot >= size - 1) break; // Leave space for back button
                
                org.bukkit.inventory.ItemStack item = new org.bukkit.inventory.ItemStack(Material.MUSIC_DISC_11);
                org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(ColorUtil.parseColor("&e" + (slot - 8) + ". " + entry.getSong().getDisplayName()));
                    List<String> lore = new ArrayList<>();
                    lore.add(ColorUtil.parseColor("&7Requested by: &f" + entry.getPlayerName()));
                    meta.setLore(lore);
                    item.setItemMeta(meta);
                }
                inv.setItem(slot++, item);
            }
        }
        
        // Back Button
        ConfigurationSection backSection = menuConfig.getConfigurationSection("queue-menu.items.back");
        org.bukkit.inventory.ItemStack back;
        int backSlot = 26;

        if (backSection != null) {
            String matName = backSection.getString("material", "ARROW");
            Material mat = Material.matchMaterial(matName);
            if (mat == null) mat = Material.ARROW;
            back = new org.bukkit.inventory.ItemStack(mat);
            
            backSlot = backSection.getInt("slot", 26);
            
            org.bukkit.inventory.meta.ItemMeta backMeta = back.getItemMeta();
            if (backMeta != null) {
                backMeta.setDisplayName(ColorUtil.parseColor(backSection.getString("name", "&cBack")));
                List<String> lore = new ArrayList<>();
                for (String line : backSection.getStringList("lore")) {
                    lore.add(ColorUtil.parseColor(line));
                }
                backMeta.setLore(lore);
                back.setItemMeta(backMeta);
                
                if (mat == Material.PLAYER_HEAD) {
                    String texture = backSection.getString("texture");
                    if (texture != null && !texture.isEmpty()) {
                        ItemUtil.applyHeadTexture(back, texture);
                    }
                }
            }
        } else {
            back = new org.bukkit.inventory.ItemStack(Material.ARROW);
            org.bukkit.inventory.meta.ItemMeta backMeta = back.getItemMeta();
            if (backMeta != null) {
                backMeta.setDisplayName(ColorUtil.parseColor("&cBack"));
                back.setItemMeta(backMeta);
            }
        }
        inv.setItem(backSlot, back);
        
        player.openInventory(inv);
    }

    public void openSongsMenu(Player player) {
        if (isBedrockPlayer(player)) {
            sendBedrockSongsMenu(player);
        } else {
            openJavaMenu(player);
        }
    }
    
    public void openOptionsMenu(Player player) {
        if (isBedrockPlayer(player)) {
            openBedrockOptionsMenu(player);
            return;
        }
        
        String title = ColorUtil.parseColor(menuConfig.getString("java.options-menu.title", "&8Options"));
        int rows = menuConfig.getInt("java.options-menu.rows", 3);
        org.bukkit.inventory.Inventory inv = Bukkit.createInventory(null, rows * 9, title);
        
        // Fill
        org.bukkit.inventory.ItemStack filler = new org.bukkit.inventory.ItemStack(javaFillMaterial);
        org.bukkit.inventory.meta.ItemMeta fillMeta = filler.getItemMeta();
        if (fillMeta != null) {
            fillMeta.setDisplayName(" ");
            filler.setItemMeta(fillMeta);
        }
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }
        
        // Items
        ConfigurationSection itemsSection = menuConfig.getConfigurationSection("options-menu.items");
        if (itemsSection != null) {
            for (String key : itemsSection.getKeys(false)) {
                ConfigurationSection itemSection = itemsSection.getConfigurationSection(key);
                int slot = itemSection.getInt("slot", -1);
                if (slot >= 0 && slot < inv.getSize()) {
                    String matName = itemSection.getString("material", "STONE");
                    Material mat;
                    try {
                        mat = Material.valueOf(matName.toUpperCase());
                    } catch (Exception e) {
                        mat = Material.STONE;
                    }
                    
                    org.bukkit.inventory.ItemStack item = new org.bukkit.inventory.ItemStack(mat);
                    org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
                    if (meta != null) {
                        meta.setDisplayName(ColorUtil.parseColor(itemSection.getString("name", key)));
                        List<String> lore = new ArrayList<>();
                        for (String line : itemSection.getStringList("lore")) {
                            String processed = line.replace("{status}", isMuted(player) ? "&cOFF" : "&aON")
                                                   .replace("{volume}", String.valueOf((int)(getPlayerVolume(player) * 100)));
                            lore.add(ColorUtil.parseColor(processed));
                        }
                                                                        meta.setLore(lore);
                                                                        item.setItemMeta(meta);
                                                
                                                                        // Texture Support - Apply AFTER saving meta to avoid overwriting
                                                                        if (mat == Material.PLAYER_HEAD) {
                                                                            String texture = itemSection.getString("texture");
                                                                            if (texture != null && !texture.isEmpty()) {
                                                                                ItemUtil.applyHeadTexture(item, texture);
                                                                            }
                                                                        }                                            }
                                            inv.setItem(slot, item);
                }
            }
        }
        player.openInventory(inv);
    }

    private void openBedrockOptionsMenu(Player player) {
        String title = ColorUtil.parseColor(menuConfig.getString("bedrock.options-menu.title", "Options"));
        String toggleLabel = ColorUtil.parseColor(menuConfig.getString("bedrock.options-menu.toggle-label", "Music Enabled"));
        String sliderLabel = ColorUtil.parseColor(menuConfig.getString("bedrock.options-menu.slider-label", "Volume (%)"));
        
        boolean isMusicOn = !isMuted(player);
        int currentVol = (int)(getPlayerVolume(player) * 100);
        
        CustomForm.Builder builder = CustomForm.builder()
            .title(title)
            .toggle(toggleLabel, isMusicOn)
            .slider(sliderLabel, 0, 100, 5, currentVol);
            
        builder.validResultHandler(response -> {
            boolean newMusicOn = response.asToggle(0);
            float newVol = response.asSlider(1);
            
            // Handle Mute
            if (newMusicOn != !isMuted(player)) {
                toggleMute(player);
            }
            
            // Handle Volume
            float newVolFloat = newVol / 100.0f;
            if (Math.abs(newVolFloat - getPlayerVolume(player)) > 0.01) {
                setVolume(player, newVolFloat);
                String msg = getMsg("music-volume-changed", "&aVolume set to: &e{volume}%");
                player.sendMessage(ColorUtil.parseColor(msg.replace("{volume}", String.valueOf((int)newVol))));
            }
        });
        
        FloodgateApi.getInstance().sendForm(player.getUniqueId(), builder.build());
    }
    
    private boolean isBedrockPlayer(Player player) {
        return plugin.isFloodgateEnabled() && FloodgateApi.getInstance().isFloodgatePlayer(player.getUniqueId());
    }
    
    private void openBedrockMenu(Player player) {
        String title = ColorUtil.parseColor(menuConfig.getString("bedrock.main-menu.title", "Music"));
        String content = ColorUtil.parseColor(menuConfig.getString("bedrock.main-menu.content", "Select an option:"));
        
        SimpleForm.Builder builder = SimpleForm.builder()
            .title(title)
            .content(content);

        List<String> buttonActions = new ArrayList<>();
        ConfigurationSection items = menuConfig.getConfigurationSection("items");
        
        if (items != null) {
            List<String> keys = new ArrayList<>(items.getKeys(false));
            keys.sort((k1, k2) -> Integer.compare(items.getInt(k1 + ".slot"), items.getInt(k2 + ".slot")));

            for (String key : keys) {
                ConfigurationSection item = items.getConfigurationSection(key);
                if (item == null) continue;
                
                String action = item.getString("action", "");
                if (action.equals("close")) continue; 
                
                // Permission Check
                String perm = item.getString("permission");
                if (perm != null && !perm.isEmpty() && !player.hasPermission(perm)) {
                    continue;
                }
                
                String name = item.contains("bedrock_name") ? 
                    ColorUtil.parseColor(item.getString("bedrock_name")) : 
                    ColorUtil.parseColor(item.getString("name", key));

                if (action.equals("toggle_mute")) {
                    String status = isMuted(player) ? 
                        getMsg("music-menu-status-off", "&cDisabled") : 
                        getMsg("music-menu-status-on", "&aEnabled");
                    name += " (" + ColorUtil.stripAllColors(status) + ")";
                }
                
                String icon = item.getString("bedrock_icon", "");
                if (!icon.isEmpty()) {
                    FormImage.Type type = icon.startsWith("http") ? FormImage.Type.URL : FormImage.Type.PATH;
                    builder.button(name, type, icon);
                } else {
                    builder.button(name);
                }
                buttonActions.add(action);
            }
        }

        builder.validResultHandler(response -> {
            int id = response.clickedButtonId();
            if (id < buttonActions.size()) {
                String action = buttonActions.get(id);
                
                if (action.startsWith("[player] ")) {
                    player.performCommand(action.substring(9).replace("{player}", player.getName()));
                    return;
                } else if (action.startsWith("[console] ")) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), action.substring(10).replace("{player}", player.getName()));
                    return;
                }

                switch (action) {
                    case "open_songs" -> sendBedrockSongsMenu(player);
                    case "open_queue" -> sendBedrockQueueMenu(player);
                    case "open_options" -> openBedrockOptionsMenu(player);
                    case "toggle_mute" -> toggleMute(player);
                    case "admin_skip" -> skipSong(player);
                    case "admin_stop" -> stopMusic(player);
                }
            }
        });

        FloodgateApi.getInstance().sendForm(player.getUniqueId(), builder.build());
    }
    
    private void sendBedrockSongsMenu(Player player) {
        String title = ColorUtil.parseColor(menuConfig.getString("bedrock.songs-menu.title", "Select Song"));
        String content = ColorUtil.parseColor(menuConfig.getString("bedrock.songs-menu.content", "Choose a song:"));
        String backBtn = ColorUtil.parseColor(menuConfig.getString("bedrock.songs-menu.back-button", "Back"));

        SimpleForm.Builder builder = SimpleForm.builder()
            .title(title)
            .content(content);

        List<Song> songList = new ArrayList<>(songs.values());
        // Shuffle for randomness
        java.util.Collections.shuffle(songList);

        for (Song song : songList) {
            String btnText = song.getBedrockName();
            String icon = song.getBedrockIcon();
            
            if (icon != null && !icon.isEmpty()) {
                FormImage.Type type = icon.startsWith("http") ? FormImage.Type.URL : FormImage.Type.PATH;
                builder.button(ColorUtil.parseColor(btnText), type, icon);
            } else {
                builder.button(ColorUtil.parseColor(btnText));
            }
        }
        
        String backIcon = menuConfig.getString("items.back.bedrock_icon", "");
        if (!backIcon.isEmpty()) {
             FormImage.Type type = backIcon.startsWith("http") ? FormImage.Type.URL : FormImage.Type.PATH;
             builder.button(backBtn, type, backIcon);
        } else {
             builder.button(backBtn);
        }

        builder.validResultHandler(response -> {
            int index = response.clickedButtonId();
            if (index >= songList.size()) {
                openBedrockMenu(player);
                return;
            }
            
            Song selected = songList.get(index);
            playSong(player, selected.getId());
        });

        FloodgateApi.getInstance().sendForm(player.getUniqueId(), builder.build());
    }
    
    private void sendBedrockQueueMenu(Player player) {
        String title = ColorUtil.parseColor(menuConfig.getString("bedrock.queue-menu.title", "Queue"));
        String backBtn = ColorUtil.parseColor(menuConfig.getString("bedrock.queue-menu.back-button", "Back"));
        
        // Headers
        String hNowPlaying = ColorUtil.parseColor(menuConfig.getString("bedrock.queue-menu.headers.now-playing", "Now Playing:"));
        String hNothing = ColorUtil.parseColor(menuConfig.getString("bedrock.queue-menu.headers.nothing-playing", "Nothing playing."));
        String hInQueue = ColorUtil.parseColor(menuConfig.getString("bedrock.queue-menu.headers.in-queue", "In Queue ({count}):"));
        String hEmptyQueue = ColorUtil.parseColor(menuConfig.getString("bedrock.queue-menu.headers.empty-queue", "Queue is empty."));
        String hNoMusic = ColorUtil.parseColor(menuConfig.getString("bedrock.queue-menu.headers.no-music-nearby", "No music nearby."));
        String hRequestedBy = ColorUtil.parseColor(menuConfig.getString("bedrock.queue-menu.headers.requested-by", "Requested by: {player}"));
        String hTimeLeft = ColorUtil.parseColor(menuConfig.getString("bedrock.queue-menu.headers.time-left", "Time left: {time}s"));

        MusicSession session = findNearbySession(player.getLocation());
        StringBuilder content = new StringBuilder();
        
        if (session != null) {
            content.append(hNowPlaying).append("\n");
            if (session.getCurrent() != null) {
                long timeLeft = (session.getEndTime() - System.currentTimeMillis()) / 1000;
                // Song Name
                content.append(ColorUtil.parseColor("&b" + session.getCurrent().getSong().getBedrockName().replace("\n", " - ")))
                       .append("\n");
                // Requested By
                content.append(hRequestedBy.replace("{player}", session.getCurrent().getPlayerName()))
                       .append("   ")
                // Time Left
                       .append(hTimeLeft.replace("{time}", String.valueOf(timeLeft)))
                       .append("\n");
            } else {
                content.append(hNothing).append("\n");
            }
            
            content.append("\n").append(hInQueue.replace("{count}", String.valueOf(session.getQueue().size()))).append("\n");
            if (!session.getQueue().isEmpty()) {
                int count = 1;
                for (QueueEntry entry : session.getQueue()) {
                    String line = "&7" + (count++) + ". &f" + entry.getSong().getDisplayName() + " &7(" + entry.getPlayerName() + ")";
                    content.append(ColorUtil.parseColor(line)).append("\n");
                }
            } else {
                content.append(hEmptyQueue).append("\n");
            }
        } else {
            content.append(hNoMusic);
        }
        
        SimpleForm.Builder builder = SimpleForm.builder()
                .title(title)
                .content(content.toString());

        String backIcon = menuConfig.getString("items.back.bedrock_icon", "");
        if (!backIcon.isEmpty()) {
             FormImage.Type type = backIcon.startsWith("http") ? FormImage.Type.URL : FormImage.Type.PATH;
             builder.button(backBtn, type, backIcon);
        } else {
             builder.button(backBtn);
        }
        
        builder.validResultHandler(response -> openBedrockMenu(player));
        
        FloodgateApi.getInstance().sendForm(player.getUniqueId(), builder.build());
    }
    
    private void openJavaMenu(Player player) {
        org.bukkit.inventory.Inventory inv = Bukkit.createInventory(null, javaRows * 9, ColorUtil.parseColor(javaTitle));
        
        org.bukkit.inventory.ItemStack filler = new org.bukkit.inventory.ItemStack(javaFillMaterial);
        org.bukkit.inventory.meta.ItemMeta fillMeta = filler.getItemMeta();
        if (fillMeta != null) {
            fillMeta.setDisplayName(" ");
            filler.setItemMeta(fillMeta);
        }
        
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }
        
        // Randomize Songs
        List<Integer> validSlots = new ArrayList<>(songSlots);
        List<Song> songList = new ArrayList<>(songs.values());
        
        java.util.Collections.sort(validSlots); // Sort slots to fill them in order
        java.util.Collections.shuffle(songList); // Shuffle songs
        
        // Fill slots
        for (int i = 0; i < validSlots.size() && i < songList.size(); i++) {
            inv.setItem(validSlots.get(i), songList.get(i).createJavaIcon(plugin));
        }
        
        // Java Back Button
        if (javaBackSlot >= 0 && javaBackSlot < inv.getSize()) {
            org.bukkit.inventory.ItemStack backItem = new org.bukkit.inventory.ItemStack(javaBackMaterial);
            org.bukkit.inventory.meta.ItemMeta backMeta = backItem.getItemMeta();
            if (backMeta != null) {
                backMeta.setDisplayName(ColorUtil.parseColor(javaBackName));
                List<String> coloredBackLore = new ArrayList<>();
                for(String l : javaBackLore) coloredBackLore.add(ColorUtil.parseColor(l));
                backMeta.setLore(coloredBackLore);
                
                // Head Texture
                if (javaBackMaterial == Material.PLAYER_HEAD && javaBackTexture != null && !javaBackTexture.isEmpty()) {
                    try {
                        SkullMeta skullMeta = (SkullMeta) backMeta;
                        PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
                        profile.setProperty(new ProfileProperty("textures", javaBackTexture));
                        skullMeta.setPlayerProfile(profile);
                        backItem.setItemMeta(skullMeta);
                    } catch (Exception e) {
                        // Ignore texture error
                        backItem.setItemMeta(backMeta);
                    }
                } else {
                    backItem.setItemMeta(backMeta);
                }
            }
            inv.setItem(javaBackSlot, backItem);
        }
        
        player.openInventory(inv);
    }
    
    public void playSong(Player player, String songId) {
        if (isMuted(player)) {
            String msg = getMsg("music-muted-error", "&cYou cannot play music while muted!");
            player.sendMessage(ColorUtil.parseColor(msg));
            return;
        }

        Song song = songs.get(songId);
        if (song == null) {
            String msg = getMsg("music-song-not-found", "&cSong not found!");
            player.sendMessage(ColorUtil.parseColor(msg));
            return;
        }
        
        // Permission Check
        if (song.getPermission() != null && !song.getPermission().isEmpty() && !player.hasPermission(song.getPermission())) {
            String msg = getMsg("no-permission", "&cYou do not have permission!");
            player.sendMessage(ColorUtil.parseColor(msg));
            return;
        }
        
        // Economy Check
        if (song.getCost() > 0 && !player.hasPermission("nonchat.music.bypass.cost")) {
            if (!IntegrationUtil.hasEnough(player, song.getCost())) {
                String msg = getMsg("music-not-enough-money", "&cNot enough money! Cost: ${cost}");
                msg = msg.replace("{cost}", String.valueOf(song.getCost()));
                player.sendMessage(ColorUtil.parseColor(msg));
                return;
            }
            
            IntegrationUtil.withdraw(player, song.getCost());
            
            // Payment success message
            String payMsg = getMsg("music-payment-success", "&aPaid ${amount} for the song.");
            payMsg = payMsg.replace("{amount}", String.valueOf(song.getCost()));
            player.sendMessage(ColorUtil.parseColor(payMsg));
            
            if (song.getPaymentReceiver() != null && !song.getPaymentReceiver().isEmpty()) {
                IntegrationUtil.deposit(song.getPaymentReceiver(), song.getCost());
            }
        }
        
        // Find existing session or create new one
        MusicSession session = findNearbySession(player.getLocation());
        
        if (session != null) {
            // Check duplicates
            if (session.hasSong(songId)) {
                String msg = getMsg("music-already-queued", "&cThis song is already playing or in the queue!");
                player.sendMessage(ColorUtil.parseColor(msg));
                return;
            }
            
            // Join existing session queue
            session.queue(player, song);
            
            String msgAdded = getMsg("music-queue-added", "&aAdded to queue: &e{song} &a(Position: &e{position}&a)");
            msgAdded = msgAdded.replace("{position}", String.valueOf(session.getQueueSize()))
                               .replace("{song}", song.getDisplayName());
            player.sendMessage(ColorUtil.parseColor(msgAdded));
            
            String msgInfo = getMsg("music-queue-info", "&7Your song will play after the current ones finish.");
            player.sendMessage(ColorUtil.parseColor(msgInfo));
            
            // Broadcast to others
            String broadcastMsg = getMsg("music-queue-added-broadcast", "&d&lMUSIC &f» &e{player} &7has added to queue: &a{song} &7(In queue: &e{queue_size}&7)")
                    .replace("{player}", player.getName())
                    .replace("{song}", song.getDisplayName())
                    .replace("{queue_size}", String.valueOf(session.getQueueSize()));
            
            double radSq = radius * radius;
            for (Player p : player.getWorld().getPlayers()) {
                if (p.getUniqueId().equals(player.getUniqueId())) continue;
                if (isMuted(p)) continue;
                
                if (p.getLocation().distanceSquared(session.center) <= radSq) {
                    p.sendMessage(ColorUtil.parseColor(broadcastMsg));
                }
            }
        } else {
            // Create new session
            session = new MusicSession(player.getLocation(), radius, this);
            activeSessions.add(session);
            session.playNow(player.getName(), song);
        }
        
        if (!isBedrockPlayer(player)) {
            player.closeInventory();
        }
    }
    
    private MusicSession findNearbySession(Location loc) {
        for (MusicSession session : activeSessions) {
            if (session.overlaps(loc)) {
                return session;
            }
        }
        return null;
    }

    // --- Inner Classes ---
    
    public static class MusicSession {
        private final Location center;
        private final double radius;
        private final MusicManager manager;
        @Getter
        private final Queue<QueueEntry> queue = new LinkedList<>();
        @Getter
        private QueueEntry current = null;
        @Getter
        private long endTime = 0;
        
        public MusicSession(Location center, double radius, MusicManager manager) {
            this.center = center;
            this.radius = radius;
            this.manager = manager;
        }
        
        public boolean overlaps(Location loc) {
            if (!loc.getWorld().equals(center.getWorld())) return false;
            return loc.distanceSquared(center) <= (radius * radius);
        }
        
        public boolean hasSong(String songId) {
            if (current != null && current.getSong().getId().equals(songId)) return true;
            for (QueueEntry entry : queue) {
                if (entry.getSong().getId().equals(songId)) return true;
            }
            return false;
        }
        
        public void queue(Player player, Song song) {
            queue.offer(new QueueEntry(player.getName(), song));
        }
        
        public int getQueueSize() {
            return queue.size();
        }
        
        public void playNow(String playerName, Song song) {
            current = new QueueEntry(playerName, song);
            endTime = System.currentTimeMillis() + (song.getDuration() * 1000L);
            
            // Loop players to play sound respecting mute
            double radSq = radius * radius;
            for (Player p : center.getWorld().getPlayers()) {
                if (p.getLocation().distanceSquared(center) <= radSq) {
                    if (!manager.isMuted(p)) {
                        // Global delay to fix Bedrock overlap issues and keep sync
                        Bukkit.getScheduler().runTaskLater(manager.plugin, () -> {
                            if (p.isOnline() && p.getWorld().equals(center.getWorld()) && p.getLocation().distanceSquared(center) <= radSq) {
                                p.playSound(p.getLocation(), song.getSound(), SoundCategory.RECORDS, manager.getPlayerVolume(p), manager.pitch);
                            }
                        }, 10L);
                    }
                }
            }
            
            // Effects - Synced with sound delay
            if (song.getEffectPreset() != null) {
                EffectManager.Preset preset = manager.effectPresets.get(song.getEffectPreset());
                if (preset != null) {
                    Bukkit.getScheduler().runTaskLater(manager.plugin, () -> {
                        manager.effectManager.playPreset(playerName, center, preset, song, manager.radius, manager, queue.size());
                    }, 10L);
                }
            }
        }
        
        public void tick() {
            if (current != null) {
                // DJ Effect Logic
                if (manager.djEffectEnabled) {
                    Player p = Bukkit.getPlayer(current.getPlayerName());
                    if (p != null && p.isOnline() && p.getWorld().equals(center.getWorld())) {
                        p.getWorld().spawnParticle(
                            manager.djEffectParticle, 
                            p.getLocation().add(0, manager.djEffectHeight, 0), 
                            manager.djEffectAmount, 
                            manager.djEffectSpreadX, 
                            manager.djEffectSpreadY, 
                            manager.djEffectSpreadZ, 
                            0 
                        );
                    }
                }
                
                if (System.currentTimeMillis() >= endTime) {
                    current = null;
                    next();
                }
            } else {
                next();
            }
        }
        
        public void next() {
            // Stop current song
            current = null;
            
            // Physically stop sound for players in range
            double radSq = radius * radius;
            for (Player p : center.getWorld().getPlayers()) {
                if (p.getLocation().distanceSquared(center) <= radSq) {
                    p.stopSound(SoundCategory.RECORDS);
                }
            }

            if (queue.isEmpty()) {
                String msg = manager.getMsg("music-finished", "&d&lMUSIC &f» &7Music has ended.");
                for (Player p : center.getWorld().getPlayers()) {
                    if (p.getLocation().distanceSquared(center) <= radSq) {
                        p.sendMessage(ColorUtil.parseColor(msg));
                    }
                }
                return;
            }
            
            QueueEntry nextEntry = queue.poll();
            playNow(nextEntry.getPlayerName(), nextEntry.getSong());
        }
        
        public void clear() {
            queue.clear();
            current = null;
            endTime = 0;
        }
        
        public boolean isFinished() {
            return current == null && queue.isEmpty();
        }
    }
    
    @Getter
    @RequiredArgsConstructor
    static class QueueEntry {
        private final String playerName;
        private final Song song;
    }

    @Getter
    public static class Song {
        private final String id;
        private final String displayName;
        private final String bedrockName;
        private final String sound;
        private final double cost;
        private final int duration;
        private final String permission;
        private final String paymentReceiver;
        private final Material material;
        private final String bedrockIcon;
        private final String effectPreset;
        private final List<String> lore;

        public Song(String id, ConfigurationSection section) {
            this.id = id;
            this.displayName = section.getString("display_name", id);
            this.bedrockName = section.getString("bedrock_name", displayName);
            this.sound = section.getString("sound", "");
            this.cost = section.getDouble("cost", 0.0);
            this.duration = section.getInt("duration", 180);
            this.permission = section.getString("permission", null);
            this.paymentReceiver = section.getString("payment-receiver", null);
            
            String matName = section.getString("material", "PAPER");
            Material mat;
            try {
                mat = Material.valueOf(matName.toUpperCase());
            } catch (Exception e) {
                mat = Material.PAPER;
            }
            this.material = mat;
            
            this.bedrockIcon = section.getString("bedrock_icon", "");
            this.effectPreset = section.getString("effect_preset", null);
            this.lore = section.getStringList("lore");
        }
        
        public org.bukkit.inventory.ItemStack createJavaIcon(Nonchat plugin) {
            org.bukkit.inventory.ItemStack item = new org.bukkit.inventory.ItemStack(material);
            org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ColorUtil.parseColor(displayName));
                List<String> coloredLore = new ArrayList<>();
                for (String line : lore) {
                    coloredLore.add(ColorUtil.parseColor(line));
                }
                meta.setLore(coloredLore);
                
                // Store ID in NBT
                meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "nonchat_song_id"), PersistentDataType.STRING, id);
                
                item.setItemMeta(meta);
            }
            return item;
        }
    }
}
