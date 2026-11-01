package tsd.ttt.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import tsd.ttt.TTT;

/**
 * The illusion: client-side fake blocks via {@code Player#sendBlockChange}.
 *
 * <p>Regions of the world that look different (or vanish) for chosen viewers without touching
 * the real blocks. Mirage walls, hidden vaults, ghost bridges. Bounded by a volume cap and
 * restored with the true block data on hide.</p>
 */
public class IllusionService {
    private static final int MAX_VOLUME = 20_000;

    private final TTT plugin;
    private final Map<String, Illusion> illusions = new LinkedHashMap<>();
    private final Map<UUID, Set<String>> appliedByPlayer = new LinkedHashMap<>();

    public IllusionService(TTT plugin) {
        this.plugin = plugin;
    }

    public void load(YamlConfiguration config) {
        illusions.clear();
        ConfigurationSection root = config == null ? null : config.getConfigurationSection("illusions");
        if (root == null) {
            return;
        }

        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            World world = Bukkit.getWorld(section.getString("world", "world"));
            if (world == null) {
                plugin.getLogger().warning("Illusion '" + id + "' references unknown world; skipping.");
                continue;
            }
            int[] from = readPoint(section.getList("from", List.of()));
            int[] to = readPoint(section.getList("to", List.of()));
            if (from == null || to == null) {
                plugin.getLogger().warning("Illusion '" + id + "' needs from/to corners; skipping.");
                continue;
            }
            BlockData fake = parseBlockData(section.getString("material", "STONE"), id);
            if (fake == null) {
                continue;
            }
            long volume = (long) (Math.abs(to[0] - from[0]) + 1) * (Math.abs(to[1] - from[1]) + 1) * (Math.abs(to[2] - from[2]) + 1);
            if (volume > MAX_VOLUME) {
                plugin.getLogger().warning("Illusion '" + id + "' covers " + volume + " blocks (cap " + MAX_VOLUME + "); skipping.");
                continue;
            }
            illusions.put(id.toLowerCase(Locale.ROOT), new Illusion(
                id.toLowerCase(Locale.ROOT),
                world.getName(),
                new int[] {Math.min(from[0], to[0]), Math.min(from[1], to[1]), Math.min(from[2], to[2])},
                new int[] {Math.max(from[0], to[0]), Math.max(from[1], to[1]), Math.max(from[2], to[2])},
                fake,
                section.getString("viewers", "all").trim(),
                section.getBoolean("join-apply", true)
            ));
        }
        plugin.getLogger().info("Loaded " + illusions.size() + " illusions.");
    }

    public List<String> getIllusionIds() {
        return new ArrayList<>(illusions.keySet());
    }

    public boolean isViewer(Player player, Illusion illusion) {
        String viewers = illusion.viewers();
        if (viewers.equalsIgnoreCase("all")) {
            return true;
        }
        if (viewers.toLowerCase(Locale.ROOT).startsWith("permission:")) {
            return player.hasPermission(viewers.substring("permission:".length()).trim());
        }
        for (String name : viewers.split(",")) {
            if (player.getName().equalsIgnoreCase(name.trim())) {
                return true;
            }
        }
        return false;
    }

    public void applyJoinIllusions(Player player) {
        for (Illusion illusion : illusions.values()) {
            if (illusion.joinApply() && isViewer(player, illusion)) {
                show(player, illusion.id());
            }
        }
    }

    public boolean show(Player player, String illusionId) {
        Illusion illusion = illusions.get(illusionId.toLowerCase(Locale.ROOT));
        if (illusion == null || player == null || !player.isOnline()) {
            return false;
        }
        World world = Bukkit.getWorld(illusion.world());
        if (world == null || player.getWorld() != world) {
            return false;
        }
        for (int x = illusion.min()[0]; x <= illusion.max()[0]; x++) {
            for (int y = illusion.min()[1]; y <= illusion.max()[1]; y++) {
                for (int z = illusion.min()[2]; z <= illusion.max()[2]; z++) {
                    player.sendBlockChange(new Location(world, x, y, z), illusion.fake());
                }
            }
        }
        appliedByPlayer.computeIfAbsent(player.getUniqueId(), key -> new LinkedHashSet<>()).add(illusion.id());
        return true;
    }

    public boolean hide(Player player, String illusionId) {
        Illusion illusion = illusions.get(illusionId.toLowerCase(Locale.ROOT));
        if (illusion == null || player == null || !player.isOnline()) {
            return false;
        }
        World world = Bukkit.getWorld(illusion.world());
        if (world == null) {
            return false;
        }
        for (int x = illusion.min()[0]; x <= illusion.max()[0]; x++) {
            for (int y = illusion.min()[1]; y <= illusion.max()[1]; y++) {
                for (int z = illusion.min()[2]; z <= illusion.max()[2]; z++) {
                    Location location = new Location(world, x, y, z);
                    player.sendBlockChange(location, world.getBlockAt(location).getBlockData());
                }
            }
        }
        Set<String> applied = appliedByPlayer.get(player.getUniqueId());
        if (applied != null) {
            applied.remove(illusion.id());
        }
        return true;
    }

    public void handleQuit(Player player) {
        appliedByPlayer.remove(player.getUniqueId());
    }

    public void shutdown() {
        appliedByPlayer.clear();
    }

    private int[] readPoint(List<?> raw) {
        if (raw.size() != 3) {
            return null;
        }
        int[] point = new int[3];
        for (int i = 0; i < 3; i++) {
            if (!(raw.get(i) instanceof Number number)) {
                return null;
            }
            point[i] = number.intValue();
        }
        return point;
    }

    private BlockData parseBlockData(String raw, String illusionId) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Bukkit.createBlockData(raw.trim());
        } catch (IllegalArgumentException first) {
            try {
                return Bukkit.createBlockData(Material.valueOf(raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_')).name());
            } catch (IllegalArgumentException second) {
                plugin.getLogger().warning("Illusion '" + illusionId + "' has invalid material '" + raw + "'; skipping.");
                return null;
            }
        }
    }

    public record Illusion(String id, String world, int[] min, int[] max, BlockData fake, String viewers, boolean joinApply) {
    }
}
