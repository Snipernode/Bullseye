package tsd.ttt.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import tsd.ttt.TTT;
import tsd.ttt.model.TriggerContext;
import tsd.ttt.model.TriggerType;
import tsd.ttt.utils.TextUtil;

/**
 * Data-driven cutscenes: camera paths with smooth interpolation, dialogue, titles and timed
 * mechanics. Viewers are frozen in place and restored afterwards; sneak skips when allowed.
 */
public class CutsceneService {
    private static final long TICK_PERIOD = 2L;

    private final TTT plugin;
    private MechanicService mechanicService;
    private final Map<String, CutsceneDefinition> cutscenes = new LinkedHashMap<>();
    private final Map<UUID, ActiveCutscene> active = new LinkedHashMap<>();
    private BukkitTask task;

    public CutsceneService(TTT plugin) {
        this.plugin = plugin;
    }

    public void setMechanicService(MechanicService mechanicService) {
        this.mechanicService = mechanicService;
    }

    public void load(YamlConfiguration config) {
        stopAll(false);
        cutscenes.clear();
        ConfigurationSection root = config == null ? null : config.getConfigurationSection("cutscenes");
        if (root == null) {
            return;
        }

        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            List<Keyframe> keyframes = new ArrayList<>();
            List<?> rawFrames = section.getList("keyframes", List.of());
            for (Object raw : rawFrames) {
                if (raw instanceof Map<?, ?> map) {
                    Keyframe frame = parseKeyframe(map);
                    if (frame != null) {
                        keyframes.add(frame);
                    }
                }
            }
            keyframes.sort((left, right) -> Long.compare(left.atTicks(), right.atTicks()));
            if (keyframes.size() < 2) {
                plugin.getLogger().warning("Cutscene '" + id + "' needs at least 2 keyframes; skipping.");
                continue;
            }
            cutscenes.put(id.toLowerCase(Locale.ROOT), new CutsceneDefinition(
                id.toLowerCase(Locale.ROOT),
                keyframes,
                section.getBoolean("skippable", true),
                section.getBoolean("freeze-movement", true)
            ));
        }
        plugin.getLogger().info("Loaded " + cutscenes.size() + " cutscenes.");
    }

    public List<String> getCutsceneIds() {
        return new ArrayList<>(cutscenes.keySet());
    }

    public boolean isWatching(UUID playerId) {
        return active.containsKey(playerId);
    }

    public boolean play(Player player, String cutsceneId) {
        if (player == null || !player.isOnline() || player.isDead()) {
            return false;
        }
        CutsceneDefinition definition = cutscenes.get(cutsceneId.toLowerCase(Locale.ROOT));
        if (definition == null) {
            return false;
        }
        stop(player, false);
        ActiveCutscene session = new ActiveCutscene(
            definition,
            player.getUniqueId(),
            player.getLocation().clone(),
            currentTick(),
            new ArrayList<>()
        );
        active.put(player.getUniqueId(), session);
        startTaskIfNeeded();
        player.sendMessage(TextUtil.colorize("&7Cutscene &f" + definition.id() + " &7started."
            + (definition.skippable() ? " &8(Sneak to skip)" : "")));
        return true;
    }

    public void stop(Player player) {
        stop(player, true);
    }

    public void stopAll(boolean restore) {
        for (UUID playerId : new ArrayList<>(active.keySet())) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                stop(player, restore);
            } else {
                active.remove(playerId);
            }
        }
    }

    public void shutdown() {
        stopAll(false);
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public boolean handleMove(Player player, Location to) {
        ActiveCutscene session = active.get(player.getUniqueId());
        if (session == null || !session.definition().freezeMovement()) {
            return false;
        }
        Location from = player.getLocation();
        if (to == null || to.getWorld() == null || !to.getWorld().equals(from.getWorld())) {
            return true;
        }
        return to.getBlockX() != from.getBlockX() || to.getBlockY() != from.getBlockY() || to.getBlockZ() != from.getBlockZ();
    }

    public boolean handleSneak(Player player) {
        ActiveCutscene session = active.get(player.getUniqueId());
        if (session == null || !session.definition().skippable()) {
            return false;
        }
        stop(player, true);
        player.sendMessage(TextUtil.colorize("&7Cutscene skipped."));
        return true;
    }

    public void handleQuit(Player player) {
        stop(player, false);
    }

    private void stop(Player player, boolean restore) {
        ActiveCutscene session = active.remove(player.getUniqueId());
        if (session == null) {
            return;
        }
        if (restore && player.isOnline() && !player.isDead()) {
            player.teleport(session.returnTo());
        }
        stopTaskIfEmpty();
    }

    private void startTaskIfNeeded() {
        if (task == null && !active.isEmpty()) {
            task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, TICK_PERIOD, TICK_PERIOD);
        }
    }

    private void stopTaskIfEmpty() {
        if (active.isEmpty() && task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        long now = currentTick();
        for (UUID playerId : new ArrayList<>(active.keySet())) {
            Player player = Bukkit.getPlayer(playerId);
            ActiveCutscene session = active.get(playerId);
            if (player == null || !player.isOnline() || player.isDead() || session == null) {
                active.remove(playerId);
                continue;
            }
            long elapsed = now - session.startTick();
            advanceSession(player, session, elapsed);
        }
        stopTaskIfEmpty();
    }

    private void advanceSession(Player player, ActiveCutscene session, long elapsed) {
        CutsceneDefinition definition = session.definition();
        List<Keyframe> frames = definition.keyframes();

        for (int i = 0; i < frames.size(); i++) {
            Keyframe frame = frames.get(i);
            if (frame.atTicks() <= elapsed && !session.fired().contains(i)) {
                session.fired().add(i);
                fireFrame(player, definition, frame);
            }
        }

        Keyframe last = frames.get(frames.size() - 1);
        if (elapsed >= last.atTicks()) {
            stop(player, true);
            player.sendMessage(TextUtil.colorize("&7Cutscene &f" + definition.id() + " &7finished."));
            return;
        }

        Keyframe from = frames.get(0);
        Keyframe to = last;
        for (int i = 0; i < frames.size() - 1; i++) {
            if (elapsed >= frames.get(i).atTicks() && elapsed < frames.get(i + 1).atTicks()) {
                from = frames.get(i);
                to = frames.get(i + 1);
                break;
            }
        }
        player.teleport(interpolate(from.location(), to.location(), segmentAlpha(from.atTicks(), to.atTicks(), elapsed)));
    }

    private void fireFrame(Player player, CutsceneDefinition definition, Keyframe frame) {
        if (!frame.say().isBlank()) {
            player.sendMessage(TextUtil.colorize(frame.say()));
        }
        if (!frame.title().isBlank() || !frame.subtitle().isBlank()) {
            player.sendTitle(TextUtil.colorize(frame.title()), TextUtil.colorize(frame.subtitle()), 10, 40, 10);
        }
        if (!frame.actionbar().isBlank()) {
            player.spigot().sendMessage(
                net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                net.md_5.bungee.api.chat.TextComponent.fromLegacyText(TextUtil.colorize(frame.actionbar()))
            );
        }
        if (!frame.mechanics().isEmpty() && mechanicService != null) {
            mechanicService.executeMechanics(
                frame.mechanics(),
                TriggerType.CUTSCENE,
                TriggerContext.builder().player(player).sourceId(definition.id()).build()
            );
        }
    }

    private double segmentAlpha(long start, long end, long now) {
        if (end <= start) {
            return 1.0D;
        }
        double alpha = (double) (now - start) / (double) (end - start);
        alpha = Math.min(1.0D, Math.max(0.0D, alpha));
        return alpha * alpha * (3.0D - 2.0D * alpha);
    }

    private Location interpolate(Location from, Location to, double alpha) {
        if (!to.getWorld().equals(from.getWorld())) {
            return to.clone();
        }
        Location result = from.clone();
        result.setX(from.getX() + (to.getX() - from.getX()) * alpha);
        result.setY(from.getY() + (to.getY() - from.getY()) * alpha);
        result.setZ(from.getZ() + (to.getZ() - from.getZ()) * alpha);
        result.setYaw((float) (from.getYaw() + angleDelta(from.getYaw(), to.getYaw()) * alpha));
        result.setPitch((float) (from.getPitch() + (to.getPitch() - from.getPitch()) * alpha));
        return result;
    }

    private double angleDelta(float from, float to) {
        double delta = (to - from) % 360.0D;
        if (delta > 180.0D) {
            delta -= 360.0D;
        } else if (delta < -180.0D) {
            delta += 360.0D;
        }
        return delta;
    }

    private long currentTick() {
        return plugin.getServer().getWorlds().isEmpty()
            ? 0L
            : plugin.getServer().getWorlds().getFirst().getFullTime();
    }

    @SuppressWarnings("unchecked")
    private Keyframe parseKeyframe(Map<?, ?> map) {
        Object atRaw = map.get("at");
        long at = atRaw instanceof Number number ? number.longValue() : parseLong(String.valueOf(atRaw), -1L);
        if (at < 0) {
            return null;
        }
        Object locationRaw = map.get("location");
        if (!(locationRaw instanceof Map<?, ?> locationMap)) {
            return null;
        }
        Location location = parseLocation((Map<?, ?>) locationMap);
        if (location == null) {
            return null;
        }
        Object mechanicsRaw = map.get("mechanics");
        List<String> mechanics = new ArrayList<>();
        if (mechanicsRaw instanceof List<?> list) {
            for (Object entry : list) {
                if (entry != null && !String.valueOf(entry).isBlank()) {
                    mechanics.add(String.valueOf(entry));
                }
            }
        } else if (mechanicsRaw != null && !String.valueOf(mechanicsRaw).isBlank()) {
            mechanics.add(String.valueOf(mechanicsRaw));
        }
        return new Keyframe(
            at,
            location,
            stringOf(map.get("say")),
            stringOf(map.get("title")),
            stringOf(map.get("subtitle")),
            stringOf(map.get("actionbar")),
            List.copyOf(mechanics)
        );
    }

    private Location parseLocation(Map<?, ?> map) {
        Object worldRaw = map.get("world");
        if (worldRaw == null || String.valueOf(worldRaw).isBlank()) {
            return null;
        }
        World world = Bukkit.getWorld(String.valueOf(worldRaw));
        if (world == null) {
            plugin.getLogger().warning("Cutscene keyframe references unknown world '" + worldRaw + "'.");
            return null;
        }
        return new Location(
            world,
            parseDouble(map.get("x"), 0.0D),
            parseDouble(map.get("y"), 64.0D),
            parseDouble(map.get("z"), 0.0D),
            (float) parseDouble(map.get("yaw"), 0.0D),
            (float) parseDouble(map.get("pitch"), 0.0D)
        );
    }

    private String stringOf(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private long parseLong(String value, long fallback) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private double parseDouble(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    public record CutsceneDefinition(String id, List<Keyframe> keyframes, boolean skippable, boolean freezeMovement) {
    }

    public record Keyframe(long atTicks, Location location, String say, String title, String subtitle, String actionbar,
        List<String> mechanics) {
    }

    private record ActiveCutscene(CutsceneDefinition definition, UUID viewerId, Location returnTo, long startTick,
        List<Integer> fired) {
    }
}
