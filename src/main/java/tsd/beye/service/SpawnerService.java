package tsd.beye.service;

import java.io.File;
import java.io.IOException;
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
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import tsd.beye.Bullseye;
import tsd.beye.model.SpawnerDefinition;

public class SpawnerService {
    private final Bullseye plugin;
    private final Map<String, SpawnerDefinition> definitions = new LinkedHashMap<>();
    private final Map<String, PlacedSpawner> placedSpawners = new LinkedHashMap<>();

    private MobService mobService;
    private BukkitTask task;
    private long tickCounter;

    public SpawnerService(Bullseye plugin) {
        this.plugin = plugin;
    }

    public void setMobService(MobService mobService) {
        this.mobService = mobService;
    }

    public void load(YamlConfiguration config) {
        definitions.clear();
        ConfigurationSection root = config.getConfigurationSection("spawners");
        if (root != null) {
            for (String rawId : root.getKeys(false)) {
                ConfigurationSection section = root.getConfigurationSection(rawId);
                if (section == null) {
                    continue;
                }

                SpawnerDefinition definition = new SpawnerDefinition(
                    normalize(rawId),
                    normalize(section.getString("mob", "")),
                    section.getLong("interval", 200L),
                    section.getInt("spawn-count", 1),
                    section.getInt("max-nearby", 3),
                    section.getDouble("activation-range", 20.0D),
                    section.getDouble("check-range", 16.0D),
                    section.getDouble("spawn-radius", 2.0D),
                    section.getDouble("y-offset", 1.0D),
                    section.getStringList("mechanics")
                );
                definitions.put(definition.id(), definition);
            }
        }

        loadPlacedSpawners();
        ensureTask();
        plugin.getLogger().info("Loaded " + definitions.size() + " Bullseye spawner definitions.");
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        savePlacedSpawners();
    }

    public Collection<String> getSpawnerIds() {
        return Collections.unmodifiableSet(definitions.keySet());
    }

    public int getPlacedSpawnerCount() {
        return placedSpawners.size();
    }

    public SpawnerDefinition getDefinition(String id) {
        if (id == null) {
            return null;
        }
        return definitions.get(normalize(id));
    }

    public boolean placeSpawner(Location location, String spawnerId) {
        SpawnerDefinition definition = getDefinition(spawnerId);
        if (definition == null || location == null || location.getWorld() == null) {
            return false;
        }

        String key = toKey(location);
        placedSpawners.put(key, new PlacedSpawner(
            key,
            definition.id(),
            location.getWorld().getName(),
            location.getBlockX(),
            location.getBlockY(),
            location.getBlockZ(),
            0L
        ));
        savePlacedSpawners();
        return true;
    }

    public boolean removeSpawner(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }

        PlacedSpawner removed = placedSpawners.remove(toKey(location));
        if (removed != null) {
            savePlacedSpawners();
            return true;
        }
        return false;
    }

    private void ensureTask() {
        if (task != null) {
            return;
        }

        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private void tick() {
        tickCounter += 20L;
        if (mobService == null || placedSpawners.isEmpty()) {
            return;
        }

        for (PlacedSpawner placed : new ArrayList<>(placedSpawners.values())) {
            SpawnerDefinition definition = getDefinition(placed.definitionId());
            if (definition == null) {
                continue;
            }

            World world = Bukkit.getWorld(placed.worldName());
            if (world == null) {
                continue;
            }

            Location base = new Location(world, placed.x() + 0.5D, placed.y() + definition.yOffset(), placed.z() + 0.5D);
            if (!hasNearbyPlayer(base, definition.activationRange())) {
                continue;
            }

            if (countNearbyManagedMobs(base, definition.checkRange(), definition.mobId()) >= definition.maxNearby()) {
                continue;
            }

            if (tickCounter - placed.lastSpawnTick() < definition.intervalTicks()) {
                continue;
            }

            for (int i = 0; i < definition.spawnCount(); i++) {
                mobService.spawnMob(definition.mobId(), randomize(base, definition.spawnRadius()));
            }
            placed.lastSpawnTick = tickCounter;
        }
    }

    private boolean hasNearbyPlayer(Location location, double range) {
        for (Player player : location.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(location) <= range * range) {
                return true;
            }
        }
        return false;
    }

    private int countNearbyManagedMobs(Location location, double range, String mobId) {
        return (int) location.getWorld().getNearbyEntities(location, range, range, range).stream()
            .map(mobService::getMobId)
            .filter(Objects::nonNull)
            .filter(mobId::equalsIgnoreCase)
            .count();
    }

    private Location randomize(Location location, double radius) {
        if (radius <= 0.0D) {
            return location.clone();
        }

        double angle = ThreadLocalRandom.current().nextDouble(0.0D, Math.PI * 2.0D);
        double distance = ThreadLocalRandom.current().nextDouble(0.0D, radius);
        return location.clone().add(Math.cos(angle) * distance, 0.0D, Math.sin(angle) * distance);
    }

    private void loadPlacedSpawners() {
        placedSpawners.clear();
        File file = getDataFile();
        if (!file.exists()) {
            return;
        }

        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = data.getConfigurationSection("placed-spawners");
        if (root == null) {
            return;
        }

        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                continue;
            }

            placedSpawners.put(key, new PlacedSpawner(
                key,
                normalize(section.getString("definition", "")),
                section.getString("world", ""),
                section.getInt("x"),
                section.getInt("y"),
                section.getInt("z"),
                0L
            ));
        }
    }

    private void savePlacedSpawners() {
        File file = getDataFile();
        YamlConfiguration data = new YamlConfiguration();
        for (PlacedSpawner placed : placedSpawners.values()) {
            String path = "placed-spawners." + placed.key();
            data.set(path + ".definition", placed.definitionId());
            data.set(path + ".world", placed.worldName());
            data.set(path + ".x", placed.x());
            data.set(path + ".y", placed.y());
            data.set(path + ".z", placed.z());
        }

        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            data.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to save placed Bullseye spawners: " + ex.getMessage());
        }
    }

    private File getDataFile() {
        return plugin.getDataFolder().toPath().resolve("generated/placed-spawners.yml").toFile();
    }

    private String toKey(Location location) {
        return location.getWorld().getName().toLowerCase(Locale.ROOT) + "_"
            + location.getBlockX() + "_" + location.getBlockY() + "_" + location.getBlockZ();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static final class PlacedSpawner {
        private final String key;
        private final String definitionId;
        private final String worldName;
        private final int x;
        private final int y;
        private final int z;
        private long lastSpawnTick;

        private PlacedSpawner(String key, String definitionId, String worldName, int x, int y, int z, long lastSpawnTick) {
            this.key = key;
            this.definitionId = definitionId;
            this.worldName = worldName;
            this.x = x;
            this.y = y;
            this.z = z;
            this.lastSpawnTick = lastSpawnTick;
        }

        public String key() {
            return key;
        }

        public String definitionId() {
            return definitionId;
        }

        public String worldName() {
            return worldName;
        }

        public int x() {
            return x;
        }

        public int y() {
            return y;
        }

        public int z() {
            return z;
        }

        public long lastSpawnTick() {
            return lastSpawnTick;
        }
    }
}
