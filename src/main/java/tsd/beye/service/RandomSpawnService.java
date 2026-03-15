package tsd.beye.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import tsd.beye.Bullseye;
import tsd.beye.model.RandomSpawnDefinition;

public class RandomSpawnService {
    private final Bullseye plugin;
    private final Map<String, RandomSpawnDefinition> definitions = new LinkedHashMap<>();

    private MobService mobService;
    private BukkitTask task;
    private long tickCounter;

    public RandomSpawnService(Bullseye plugin) {
        this.plugin = plugin;
    }

    public void setMobService(MobService mobService) {
        this.mobService = mobService;
    }

    public void load(YamlConfiguration config) {
        definitions.clear();
        ConfigurationSection root = config.getConfigurationSection("random-spawns");
        if (root != null) {
            for (String rawId : root.getKeys(false)) {
                ConfigurationSection section = root.getConfigurationSection(rawId);
                if (section == null) {
                    continue;
                }

                RandomSpawnDefinition definition = new RandomSpawnDefinition(
                    normalize(rawId),
                    normalize(section.getString("mob", "")),
                    section.getStringList("worlds"),
                    section.getLong("interval", 200L),
                    section.getDouble("chance", 0.1D),
                    section.getInt("spawn-count", 1),
                    section.getInt("max-nearby", 3),
                    section.getInt("attempts", 6),
                    section.getDouble("min-distance", 12.0D),
                    section.getDouble("max-distance", 28.0D),
                    section.getDouble("y-offset", 1.0D)
                );
                definitions.put(definition.id(), definition);
            }
        }

        ensureTask();
        plugin.getLogger().info("Loaded " + definitions.size() + " Bullseye random spawn definitions.");
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public Collection<String> getDefinitionIds() {
        return Collections.unmodifiableSet(definitions.keySet());
    }

    public int getDefinitionCount() {
        return definitions.size();
    }

    public void pulse() {
        tickCounter += 20L;
        runSpawnCycle();
    }

    private void ensureTask() {
        if (task != null) {
            return;
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            tickCounter += 20L;
            runSpawnCycle();
        }, 20L, 20L);
    }

    private void runSpawnCycle() {
        if (mobService == null || definitions.isEmpty()) {
            return;
        }

        for (RandomSpawnDefinition definition : definitions.values()) {
            if (definition.mobId().isBlank()) {
                continue;
            }
            if (tickCounter % definition.intervalTicks() != 0L) {
                continue;
            }

            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!player.isOnline() || player.isDead()) {
                    continue;
                }
                if (!isWorldAllowed(definition, player.getWorld())) {
                    continue;
                }
                if (ThreadLocalRandom.current().nextDouble() > definition.chance()) {
                    continue;
                }
                if (countNearbyManagedMobs(player.getLocation(), definition.maxDistance(), definition.mobId()) >= definition.maxNearby()) {
                    continue;
                }

                int spawned = 0;
                for (int i = 0; i < definition.spawnCount(); i++) {
                    Location spawn = findSpawnLocation(player, definition);
                    if (spawn == null) {
                        continue;
                    }
                    if (mobService.spawnMob(definition.mobId(), spawn).success()) {
                        spawned++;
                    }
                }

                if (spawned > 0) {
                    break;
                }
            }
        }
    }

    private boolean isWorldAllowed(RandomSpawnDefinition definition, World world) {
        if (definition.worlds().isEmpty()) {
            return true;
        }
        String worldName = world.getName().toLowerCase(Locale.ROOT);
        return definition.worlds().stream().map(this::normalize).anyMatch(worldName::equals);
    }

    private int countNearbyManagedMobs(Location location, double range, String mobId) {
        return (int) location.getWorld().getNearbyEntities(location, range, range, range).stream()
            .map(mobService::getMobId)
            .filter(Objects::nonNull)
            .filter(mobId::equalsIgnoreCase)
            .count();
    }

    private Location findSpawnLocation(Player player, RandomSpawnDefinition definition) {
        World world = player.getWorld();
        for (int i = 0; i < definition.attempts(); i++) {
            double angle = ThreadLocalRandom.current().nextDouble(0.0D, Math.PI * 2.0D);
            double distance = ThreadLocalRandom.current().nextDouble(definition.minDistance(), definition.maxDistance());
            int x = (int) Math.floor(player.getLocation().getX() + Math.cos(angle) * distance);
            int z = (int) Math.floor(player.getLocation().getZ() + Math.sin(angle) * distance);
            int y = world.getHighestBlockYAt(x, z);
            Location location = new Location(world, x + 0.5D, y + definition.yOffset(), z + 0.5D);
            Block below = world.getBlockAt(x, y - 1, z);
            if (!below.getType().isSolid()) {
                continue;
            }
            return location;
        }
        return null;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
