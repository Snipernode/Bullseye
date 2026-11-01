package tsd.ttt.service;

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
import tsd.ttt.TTT;
import tsd.ttt.model.SpawnerDefinition;

public class SpawnerService {
    private final TTT plugin;
    private final Map<String, SpawnerDefinition> definitions = new LinkedHashMap<>();
    private final Map<String, PlacedSpawner> placedSpawners = new LinkedHashMap<>();

    private MobService mobService;
    private BukkitTask task;
    private long tickCounter;

    public SpawnerService(TTT plugin) {
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
        plugin.getLogger().info("Loaded " + definitions.size() + " TTT spawner definitions.");
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
            tickCounter
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

            if (!isChunkLoaded(world, placed)) {
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

    private boolean isChunkLoaded(World world, PlacedSpawner placed) {
        return world.isChunkLoaded(placed.x() >> 4, placed.z() >> 4);
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
        for (Map<?, ?> entry : data.getMapList("placed-spawners")) {
            String world = entry.get("world") == null ? "" : String.valueOf(entry.get("world"));
            if (world.isBlank()) {
                continue;
            }

            String definitionId = normalize(entry.get("definition") == null ? "" : String.valueOf(entry.get("definition")));
            int x = toInt(entry.get("x"));
            int y = toInt(entry.get("y"));
            int z = toInt(entry.get("z"));
            String key = toKey(world, x, y, z);
            placedSpawners.put(key, new PlacedSpawner(key, definitionId, world, x, y, z, tickCounter));
        }
    }

    private int toInt(Object raw) {
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(raw));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private void savePlacedSpawners() {
        File file = getDataFile();
        YamlConfiguration data = new YamlConfiguration();
        List<Map<String, Object>> entries = new ArrayList<>();
        for (PlacedSpawner placed : placedSpawners.values()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("key", placed.key());
            entry.put("definition", placed.definitionId());
            entry.put("world", placed.worldName());
            entry.put("x", placed.x());
            entry.put("y", placed.y());
            entry.put("z", placed.z());
            entries.add(entry);
        }
        data.set("placed-spawners", entries);

        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            data.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to save placed TTT spawners: " + ex.getMessage());
        }
    }

    private File getDataFile() {
        return plugin.getDataFolder().toPath().resolve("generated/placed-spawners.yml").toFile();
    }

    private String toKey(Location location) {
        return toKey(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    private String toKey(String worldName, int x, int y, int z) {
        return worldName.toLowerCase(Locale.ROOT) + "_" + x + "_" + y + "_" + z;
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
