package com.nonxedy.nonchat.music;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.geysermc.floodgate.api.FloodgateApi;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import lombok.Getter;

public class EffectManager {

    private final JavaPlugin plugin;

    public EffectManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void playPreset(String djName, Location center, Preset preset, MusicManager.Song song, double radius, MusicManager musicManager, int queueSize) {
        double radSq = radius * radius;
        
        // Determine effects location source (Dynamic Stage)
        Location effectLocation = center;
        Player djPlayer = Bukkit.getPlayer(djName);
        if (djPlayer != null && djPlayer.isOnline() && djPlayer.getWorld().equals(center.getWorld()) && djPlayer.getLocation().distanceSquared(center) <= radSq) {
            effectLocation = djPlayer.getLocation();
        }

        // 1. Fireworks (Spawn ONCE, visible to everyone)
        for (FireworkData fw : preset.getFireworks()) {
            Location finalLoc = effectLocation;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                spawnFirework(finalLoc, fw); 
            }, fw.getDelay());
        }

        // 2. Particles
        for (ParticleData pData : preset.getParticles()) {
            Location loc = effectLocation.clone().add(pData.getOffsetX(), pData.getOffsetY(), pData.getOffsetZ());
            try {
                Particle particle = Particle.valueOf(pData.getType());
                loc.getWorld().spawnParticle(
                    particle, 
                    loc, 
                    pData.getAmount(), 
                    pData.getSpreadX(), 
                    pData.getSpreadY(), 
                    pData.getSpreadZ(), 
                    pData.getSpeed()
                );
            } catch (Exception ignored) {}
        }

        // Logic for all players in range of the SESSION CENTER
        for (Player p : center.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(center) <= radSq) {
                if (musicManager.isMuted(p)) continue;

                boolean isBedrock = isBedrockPlayer(p);

                // Prepare dynamic strings
                String ownerInfo = " &7- &e" + djName;
                String queueInfo = queueSize > 0 ? " &7(&e+" + queueSize + " en cola&7)" : "";

                // 2. Titles & Messages
                for (String msg : preset.getMessages()) {
                    String processed = msg.replace("%player_name%", djName)
                                          .replace("%song_name%", song.getDisplayName())
                                          .replace("%owner_info%", ownerInfo)
                                          .replace("%queue_info%", queueInfo)
                                          .replace("%queue_amount%", String.valueOf(queueSize));
                                          
                    p.sendMessage(ChatColor.translateAlternateColorCodes('&', processed));
                }
                
                for (TitleData title : preset.getTitles()) {
                     String processed = title.getText().replace("%player_name%", djName)
                                          .replace("%song_name%", song.getDisplayName())
                                          .replace("%owner_info%", ownerInfo)
                                          .replace("%queue_info%", queueInfo)
                                          .replace("%queue_amount%", String.valueOf(queueSize));
                     
                     String colored = ChatColor.translateAlternateColorCodes('&', processed);

                     if (title.getType().equalsIgnoreCase("ACTIONBAR")) {
                         p.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(colored));
                     } else if (title.getType().equalsIgnoreCase("TITLE")) {
                         p.sendTitle(colored, "", 10, 70, 20);
                     } else if (title.getType().equalsIgnoreCase("SUBTITLE")) {
                         p.sendTitle("", colored, 10, 70, 20);
                     }
                }
            }
        }
    }
    
    private boolean isBedrockPlayer(Player player) {
        return Bukkit.getPluginManager().getPlugin("floodgate") != null && 
               FloodgateApi.getInstance().isFloodgatePlayer(player.getUniqueId());
    }

    private void spawnFirework(Location center, FireworkData data) {
        Location loc = center.clone().add(
            data.getOffsetX(), 
            data.getOffsetY(), 
            data.getOffsetZ()
        );

        Firework fw = (Firework) loc.getWorld().spawnEntity(loc, EntityType.FIREWORK_ROCKET);
        FireworkMeta meta = fw.getFireworkMeta();

        FireworkEffect.Builder builder = FireworkEffect.builder();
        
        // Type
        try {
            builder.with(FireworkEffect.Type.valueOf(data.getType()));
        } catch (Exception e) {
            builder.with(FireworkEffect.Type.STAR);
        }

        // Colors
        for (String hex : data.getColors()) {
            builder.withColor(parseColor(hex));
        }

        if (data.isTrail()) builder.withTrail();
        if (data.isFlicker()) builder.withFlicker();

        meta.addEffect(builder.build());
        meta.setPower(1); 
        fw.setFireworkMeta(meta);
    }

    private Color parseColor(String hex) {
        if (hex == null) return Color.WHITE;
        if (hex.startsWith("#")) {
            return Color.fromRGB(
                Integer.valueOf(hex.substring(1, 3), 16),
                Integer.valueOf(hex.substring(3, 5), 16),
                Integer.valueOf(hex.substring(5, 7), 16)
            );
        }
        return Color.WHITE;
    }

    // --- Data Classes ---

    @Getter
    public static class Preset {
        private final List<FireworkData> fireworks = new ArrayList<>();
        private final List<ParticleData> particles = new ArrayList<>();
        private final List<String> messages = new ArrayList<>();
        private final List<TitleData> titles = new ArrayList<>();

        public Preset(ConfigurationSection section) {
            if (section.contains("fireworks")) {
                for (Map<?, ?> map : section.getMapList("fireworks")) {
                    fireworks.add(new FireworkData(map));
                }
            }
            if (section.contains("particles")) {
                for (Map<?, ?> map : section.getMapList("particles")) {
                    particles.add(new ParticleData(map));
                }
            }
            if (section.contains("messages")) {
                messages.addAll(section.getStringList("messages"));
            }
            if (section.contains("titles")) {
                 for (Map<?, ?> map : section.getMapList("titles")) {
                    titles.add(new TitleData(map));
                }
            }
        }
    }

    @Getter
    public static class FireworkData {
        private final long delay;
        private final double offsetX, offsetY, offsetZ;
        private final String type;
        private final List<String> colors;
        private final boolean trail;
        private final boolean flicker;

        @SuppressWarnings("unchecked")
        public FireworkData(Map<?, ?> map) {
            this.delay = getLong(map, "delay", 0);
            
            String[] offsets = getString(map, "offset", "0,0,0").split(",");
            this.offsetX = Double.parseDouble(offsets[0]);
            this.offsetY = Double.parseDouble(offsets[1]);
            this.offsetZ = Double.parseDouble(offsets[2]);
            
            this.type = getString(map, "type", "STAR");
            
            Object colorsObj = map.get("colors");
            if (colorsObj instanceof List) {
                this.colors = (List<String>) colorsObj;
            } else {
                this.colors = new ArrayList<>();
            }
            
            this.trail = getBoolean(map, "trail", false);
            this.flicker = getBoolean(map, "flicker", false);
        }
        
        private long getLong(Map<?,?> map, String key, long def) {
            Object val = map.get(key);
            return val instanceof Number ? ((Number)val).longValue() : def;
        }
        
        private String getString(Map<?,?> map, String key, String def) {
            Object val = map.get(key);
            return val != null ? val.toString() : def;
        }
        
        private boolean getBoolean(Map<?,?> map, String key, boolean def) {
            Object val = map.get(key);
            return val instanceof Boolean ? (Boolean)val : def;
        }
    }

    @Getter
    public static class ParticleData {
        private final String type;
        private final int amount;
        private final double offsetX, offsetY, offsetZ;
        private final double spreadX, spreadY, spreadZ;
        private final double speed;

        public ParticleData(Map<?, ?> map) {
            this.type = getString(map, "type", "NOTE");
            this.amount = getInt(map, "amount", 10);
            
            String[] offsets = getString(map, "offset", "0,0,0").split(",");
            this.offsetX = parseDouble(offsets, 0);
            this.offsetY = parseDouble(offsets, 1);
            this.offsetZ = parseDouble(offsets, 2);

            String[] spreads = getString(map, "spread", "0.5,0.5,0.5").split(",");
            this.spreadX = parseDouble(spreads, 0);
            this.spreadY = parseDouble(spreads, 1);
            this.spreadZ = parseDouble(spreads, 2);
            
            this.speed = getDouble(map, "speed", 0.0);
        }

        private double parseDouble(String[] arr, int index) {
            if (index < arr.length) {
                try { return Double.parseDouble(arr[index]); } catch (Exception e) {}
            }
            return 0.0;
        }

        private String getString(Map<?,?> map, String key, String def) {
            Object val = map.get(key);
            return val != null ? val.toString() : def;
        }

        private int getInt(Map<?,?> map, String key, int def) {
            Object val = map.get(key);
            return val instanceof Number ? ((Number)val).intValue() : def;
        }

        private double getDouble(Map<?,?> map, String key, double def) {
            Object val = map.get(key);
            return val instanceof Number ? ((Number)val).doubleValue() : def;
        }
    }
    
    @Getter
    public static class TitleData {
        private final String type;
        private final String text;
        
        public TitleData(Map<?, ?> map) {
            Object typeObj = map.get("type");
            this.type = typeObj != null ? typeObj.toString() : "TITLE";
            
            Object textObj = map.get("text");
            this.text = textObj != null ? textObj.toString() : "";
        }
    }
}