package tsd.ttt.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import tsd.ttt.TTT;

/**
 * Procedural idle animations for display entities.
 *
 * <p>Instead of bone keyframes (which need per-bone entities like ModelEngine), this animates the
 * whole display in transformation space and leans on the client's own interpolation for
 * smoothness. Mob and furniture visuals attach here right after spawning their display.</p>
 */
public class DisplayAnimationService {
    private static final long TICK_PERIOD = 4L;

    private final TTT plugin;
    private final Map<String, AnimationSpec> specs = new LinkedHashMap<>();
    private final Map<UUID, AnimatedDisplay> active = new LinkedHashMap<>();
    private BukkitTask task;

    public DisplayAnimationService(TTT plugin) {
        this.plugin = plugin;
    }

    public void load(YamlConfiguration modelsConfig) {
        specs.clear();
        ConfigurationSection root = modelsConfig == null ? null : modelsConfig.getConfigurationSection("models");
        if (root == null) {
            return;
        }

        for (String modelId : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(modelId + ".idle-animation");
            AnimationSpec spec = parseSpec(section);
            if (spec.enabled()) {
                specs.put(modelId.toLowerCase(Locale.ROOT), spec);
            }
        }
    }

    public void attach(ItemDisplay display, String modelId, BasePose base) {
        if (display == null || modelId == null || modelId.isBlank() || base == null) {
            return;
        }
        AnimationSpec spec = specs.get(modelId.toLowerCase(Locale.ROOT));
        if (spec == null || !spec.enabled()) {
            return;
        }
        active.put(display.getUniqueId(), new AnimatedDisplay(display, spec, base, currentTick()));
        startTaskIfNeeded();
    }

    public void detach(UUID displayId) {
        if (displayId == null) {
            return;
        }
        active.remove(displayId);
        stopTaskIfEmpty();
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        active.clear();
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
        List<UUID> dead = new ArrayList<>();
        for (Map.Entry<UUID, AnimatedDisplay> entry : active.entrySet()) {
            AnimatedDisplay animated = entry.getValue();
            if (!animated.display().isValid() || animated.display().isDead()) {
                dead.add(entry.getKey());
                continue;
            }
            applyPose(animated, now);
        }
        for (UUID id : dead) {
            active.remove(id);
        }
        stopTaskIfEmpty();
    }

    private void applyPose(AnimatedDisplay animated, long now) {
        AnimationSpec spec = animated.spec();
        double phase = (now - animated.startTick() + spec.phaseTicks()) % Math.max(1, spec.periodTicks());
        double angle = (2.0D * Math.PI * phase) / Math.max(1, spec.periodTicks());

        float yaw = animated.base().yawDeg();
        float roll = animated.base().rollDeg();
        float bobY = 0.0F;
        float scale = 1.0F;
        switch (spec.type()) {
            case "spin" -> yaw += (float) (360.0D * phase / Math.max(1, spec.periodTicks()));
            case "bob" -> bobY = (float) (spec.amount() * Math.sin(angle));
            case "pulse" -> scale = (float) (1.0D + spec.amount() * Math.sin(angle));
            case "sway" -> roll += (float) (spec.amount() * Math.sin(angle));
            default -> {
                return;
            }
        }

        ItemDisplay display = animated.display();
        display.setInterpolationDuration((int) Math.min(Integer.MAX_VALUE, TICK_PERIOD + 1));
        display.setTransformation(new Transformation(
            new Vector3f(0.0F, bobY, 0.0F),
            new Quaternionf().rotateXYZ(
                (float) Math.toRadians(animated.base().pitchDeg()),
                (float) Math.toRadians(yaw),
                (float) Math.toRadians(roll)
            ),
            new Vector3f(
                (float) (animated.base().scaleX() * scale),
                (float) (animated.base().scaleY() * scale),
                (float) (animated.base().scaleZ() * scale)
            ),
            new Quaternionf()
        ));
    }

    private long currentTick() {
        return plugin.getServer().getWorlds().isEmpty()
            ? 0L
            : plugin.getServer().getWorlds().getFirst().getFullTime();
    }

    private AnimationSpec parseSpec(ConfigurationSection section) {
        if (section == null) {
            return AnimationSpec.none();
        }
        String type = section.getString("type", "none").trim().toLowerCase(Locale.ROOT);
        if (!List.of("spin", "bob", "pulse", "sway").contains(type)) {
            return AnimationSpec.none();
        }
        long period = Math.max(10L, section.getLong("period-ticks", 80L));
        double amount = section.getDouble("amount", type.equals("bob") ? 0.15D : 1.0D);
        long phase = Math.max(0L, section.getLong("phase-ticks", 0L));
        return new AnimationSpec(type, period, amount, phase);
    }

    public record AnimationSpec(String type, long periodTicks, double amount, long phaseTicks) {
        public static AnimationSpec none() {
            return new AnimationSpec("none", 80L, 0.0D, 0L);
        }

        public boolean enabled() {
            return !"none".equals(type);
        }
    }

    public record BasePose(float pitchDeg, float yawDeg, float rollDeg, float scaleX, float scaleY, float scaleZ) {
    }

    private record AnimatedDisplay(ItemDisplay display, AnimationSpec spec, BasePose base, long startTick) {
    }
}
