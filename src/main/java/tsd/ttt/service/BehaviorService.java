package tsd.ttt.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import tsd.ttt.TTT;
import tsd.ttt.core.Keychain;
import tsd.ttt.model.CustomMobDefinition;
import tsd.ttt.model.TriggerContext;
import tsd.ttt.model.TriggerType;
import tsd.ttt.utils.TextUtil;

/**
 * The brain: phase-driven boss AI, living boss bars, and damage holograms.
 *
 * <p>Phases are evaluated on every damaging hit. Boss bars track health live. Hit holograms
 * float damage numbers above struck mobs. Everything is config-driven per mob and inert
 * when unconfigured.</p>
 */
public class BehaviorService {
    private static final double BAR_VIEW_DISTANCE = 40.0D;
    private static final double HOLOGRAM_RISE = 1.0D;
    private static final long HOLOGRAM_LIFETIME_TICKS = 20L;

    private final TTT plugin;
    private final Keychain keychain;
    private MechanicService mechanicService;
    private final Map<UUID, BossBar> bars = new LinkedHashMap<>();
    private final Map<UUID, Hologram> holograms = new LinkedHashMap<>();
    private BukkitTask hologramTask;

    public BehaviorService(TTT plugin, Keychain keychain) {
        this.plugin = plugin;
        this.keychain = keychain;
    }

    public void setMechanicService(MechanicService mechanicService) {
        this.mechanicService = mechanicService;
    }

    public void onSpawn(LivingEntity host, CustomMobDefinition definition) {
        host.getPersistentDataContainer().set(keychain.mobPhase(), PersistentDataType.INTEGER, 0);
        if (definition.bossbar() != null) {
            BossBar bar = Bukkit.createBossBar(
                TextUtil.colorize(definition.bossbar().title().isBlank() ? definition.displayName() : definition.bossbar().title()),
                parseColor(definition.bossbar().color()),
                parseStyle(definition.bossbar().style())
            );
            bar.setProgress(1.0D);
            bars.put(host.getUniqueId(), bar);
            refreshViewers(host, bar);
        }
    }

    public void onDamage(LivingEntity host, CustomMobDefinition definition, Player damager, double finalDamage) {
        double max = definition.health();
        if (host.getAttribute(Attribute.MAX_HEALTH) != null) {
            max = Math.max(1.0D, host.getAttribute(Attribute.MAX_HEALTH).getBaseValue());
        }
        double remaining = Math.max(0.0D, host.getHealth() - finalDamage);
        double fraction = Math.min(1.0D, remaining / Math.max(1.0D, max));

        BossBar bar = bars.get(host.getUniqueId());
        if (bar != null) {
            bar.setProgress(Math.max(0.0D, Math.min(1.0D, fraction)));
            refreshViewers(host, bar);
        }

        evaluatePhases(host, definition, damager, fraction);

        if (definition.hitHologram()) {
            spawnHologram(host, definition.hitHologramFormat().replace("{damage}", trimDouble(finalDamage)));
        }
    }

    public void onDeath(Entity host) {
        BossBar bar = bars.remove(host.getUniqueId());
        if (bar != null) {
            bar.removeAll();
        }
    }

    public void shutdown() {
        for (BossBar bar : bars.values()) {
            bar.removeAll();
        }
        bars.clear();
        for (Hologram hologram : holograms.values()) {
            if (hologram.display().isValid()) {
                hologram.display().remove();
            }
        }
        holograms.clear();
        if (hologramTask != null) {
            hologramTask.cancel();
            hologramTask = null;
        }
    }

    private void evaluatePhases(LivingEntity host, CustomMobDefinition definition, Player damager, double fraction) {
        if (definition.phases().isEmpty()) {
            return;
        }
        int current = host.getPersistentDataContainer().getOrDefault(keychain.mobPhase(), PersistentDataType.INTEGER, 0);
        int reached = current;
        for (int i = 0; i < definition.phases().size(); i++) {
            if (fraction <= definition.phases().get(i).belowFraction()) {
                reached = i + 1;
            }
        }
        if (reached == current) {
            return;
        }
        host.getPersistentDataContainer().set(keychain.mobPhase(), PersistentDataType.INTEGER, reached);
        CustomMobDefinition.PhaseData phase = definition.phases().get(reached - 1);

        if (phase.speed() > 0.0D && host.getAttribute(Attribute.MOVEMENT_SPEED) != null) {
            host.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(phase.speed());
        }

        if ((!phase.title().isBlank() || !phase.subtitle().isBlank()) && mechanicService != null) {
            for (Entity nearby : host.getNearbyEntities(BAR_VIEW_DISTANCE, 16.0D, BAR_VIEW_DISTANCE)) {
                if (nearby instanceof Player viewer) {
                    viewer.sendTitle(TextUtil.colorize(phase.title()), TextUtil.colorize(phase.subtitle()), 10, 50, 10);
                }
            }
        }

        if (!phase.mechanics().isEmpty() && mechanicService != null) {
            mechanicService.executeMechanics(
                phase.mechanics(),
                TriggerType.MOB_PHASE,
                TriggerContext.builder().player(damager).target(host).sourceId(definition.id()).build()
            );
        }
        plugin.getLogger().info("Mob '" + definition.id() + "' entered phase " + reached + " at " + (int) (fraction * 100) + "% health.");
    }

    private void refreshViewers(LivingEntity host, BossBar bar) {
        for (Entity nearby : host.getNearbyEntities(BAR_VIEW_DISTANCE, 16.0D, BAR_VIEW_DISTANCE)) {
            if (nearby instanceof Player viewer && !bar.getPlayers().contains(viewer)) {
                bar.addPlayer(viewer);
            }
        }
        for (Player viewer : new ArrayList<>(bar.getPlayers())) {
            if (!viewer.isOnline() || viewer.isDead()
                || viewer.getWorld() != host.getWorld()
                || viewer.getLocation().distanceSquared(host.getLocation()) > BAR_VIEW_DISTANCE * BAR_VIEW_DISTANCE) {
                bar.removePlayer(viewer);
            }
        }
    }

    private void spawnHologram(LivingEntity host, String text) {
        if (text.isBlank() || host.getWorld() == null) {
            return;
        }
        Location location = host.getLocation().clone().add(
            (Math.random() - 0.5D) * 0.6D,
            host.getHeight() + 0.4D,
            (Math.random() - 0.5D) * 0.6D
        );
        TextDisplay display;
        try {
            display = location.getWorld().spawn(location, TextDisplay.class);
        } catch (RuntimeException ex) {
            return;
        }
        display.setText(TextUtil.colorize(text));
        display.setBillboard(Display.Billboard.CENTER);
        display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        display.setSeeThrough(false);
        display.setShadowed(true);
        display.setInterpolationDuration(4);
        display.setPersistent(false);
        display.setGravity(false);
        display.setInvulnerable(true);
        holograms.put(display.getUniqueId(), new Hologram(display, currentTick()));
        startHologramTaskIfNeeded();
    }

    private void startHologramTaskIfNeeded() {
        if (hologramTask == null && !holograms.isEmpty()) {
            hologramTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickHolograms, 2L, 2L);
        }
    }

    private void tickHolograms() {
        long now = currentTick();
        List<UUID> done = new ArrayList<>();
        for (Map.Entry<UUID, Hologram> entry : holograms.entrySet()) {
            TextDisplay display = entry.getValue().display();
            if (!display.isValid() || display.isDead()) {
                done.add(entry.getKey());
                continue;
            }
            long age = now - entry.getValue().startTick();
            if (age >= HOLOGRAM_LIFETIME_TICKS) {
                display.remove();
                done.add(entry.getKey());
                continue;
            }
            double alpha = (double) age / (double) HOLOGRAM_LIFETIME_TICKS;
            display.setTransformation(new Transformation(
                new Vector3f(0.0F, (float) (HOLOGRAM_RISE * alpha), 0.0F),
                new Quaternionf(),
                new Vector3f(1.0F, 1.0F, 1.0F),
                new Quaternionf()
            ));
        }
        for (UUID id : done) {
            holograms.remove(id);
        }
        if (holograms.isEmpty() && hologramTask != null) {
            hologramTask.cancel();
            hologramTask = null;
        }
    }

    private long currentTick() {
        return plugin.getServer().getWorlds().isEmpty()
            ? 0L
            : plugin.getServer().getWorlds().getFirst().getFullTime();
    }

    private BarColor parseColor(String value) {
        try {
            return BarColor.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            return BarColor.RED;
        }
    }

    private BarStyle parseStyle(String value) {
        try {
            return BarStyle.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            return BarStyle.SOLID;
        }
    }

    private String trimDouble(double value) {
        if (value == Math.rint(value)) {
            return Long.toString((long) value);
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private record Hologram(TextDisplay display, long startTick) {
    }
}
