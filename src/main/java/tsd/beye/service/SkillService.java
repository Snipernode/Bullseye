package tsd.beye.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import tsd.beye.Bullseye;
import tsd.beye.model.BullseyeSkillDefinition;
import tsd.beye.model.MechanicAction;
import tsd.beye.model.TriggerContext;
import tsd.beye.model.TriggerType;

public class SkillService {
    private final Bullseye plugin;
    private final ItemService itemService;
    private final Map<String, BullseyeSkillDefinition> definitions = new LinkedHashMap<>();
    private final Map<String, Long> cooldowns = new HashMap<>();

    private MechanicService mechanicService;

    public SkillService(Bullseye plugin, ItemService itemService) {
        this.plugin = plugin;
        this.itemService = itemService;
    }

    public void setMechanicService(MechanicService mechanicService) {
        this.mechanicService = mechanicService;
    }

    public void load(YamlConfiguration config) {
        definitions.clear();
        cooldowns.clear();

        ConfigurationSection root = config.getConfigurationSection("skills");
        if (root == null) {
            return;
        }

        for (String rawId : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(rawId);
            if (section == null) {
                continue;
            }

            List<TriggerType> triggers = parseTriggers(section);
            List<BullseyeSkillDefinition.Condition> conditions = section.getMapList("conditions").stream()
                .map(this::toCondition)
                .toList();
            List<MechanicAction> actions = section.getMapList("actions").stream()
                .map(this::toAction)
                .filter(action -> action != null)
                .toList();

            BullseyeSkillDefinition definition = new BullseyeSkillDefinition(
                normalize(rawId),
                triggers,
                normalize(section.getString("targeter", "target")),
                section.getDouble("radius", 8.0D),
                section.getInt("limit", Integer.MAX_VALUE),
                section.getDouble("chance", 1.0D),
                section.getLong("cooldown", 0L),
                conditions,
                actions
            );
            definitions.put(definition.id(), definition);
        }

        plugin.getLogger().info("Loaded " + definitions.size() + " Bullseye skills.");
    }

    public Collection<String> getSkillIds() {
        return Collections.unmodifiableSet(definitions.keySet());
    }

    public BullseyeSkillDefinition getDefinition(String skillId) {
        if (skillId == null) {
            return null;
        }
        return definitions.get(normalize(skillId));
    }

    public void executeSkills(Collection<String> skillIds, TriggerType trigger, TriggerContext context) {
        if (mechanicService == null || skillIds == null || skillIds.isEmpty() || trigger == null || context == null) {
            return;
        }

        for (String skillId : skillIds) {
            executeSkill(skillId, trigger, context);
        }
    }

    public void executeSkill(String skillId, TriggerType trigger, TriggerContext context) {
        BullseyeSkillDefinition definition = getDefinition(skillId);
        if (definition == null || !definition.triggers().contains(trigger) || definition.actions().isEmpty()) {
            return;
        }

        if (definition.chance() < 1.0D && ThreadLocalRandom.current().nextDouble() > definition.chance()) {
            return;
        }

        String cooldownKey = cooldownKey(definition, context);
        long now = System.currentTimeMillis();
        if (definition.cooldownTicks() > 0L) {
            long expiresAt = cooldowns.getOrDefault(cooldownKey, 0L);
            if (expiresAt > now) {
                return;
            }
        }

        List<TriggerContext> selectedContexts = selectContexts(definition, context);
        if (selectedContexts.isEmpty()) {
            return;
        }

        boolean executed = false;
        for (TriggerContext selected : selectedContexts) {
            if (!passesConditions(definition.conditions(), selected, context)) {
                continue;
            }
            mechanicService.executeActions(definition.actions(), selected);
            executed = true;
        }

        if (executed && definition.cooldownTicks() > 0L) {
            cooldowns.put(cooldownKey, now + (definition.cooldownTicks() * 50L));
        }
    }

    private List<TriggerType> parseTriggers(ConfigurationSection section) {
        Set<TriggerType> triggers = new LinkedHashSet<>();
        String single = section.getString("trigger");
        if (single != null && !single.isBlank()) {
            TriggerType parsed = parseTrigger(single);
            if (parsed != null) {
                triggers.add(parsed);
            }
        }
        for (String value : section.getStringList("triggers")) {
            TriggerType parsed = parseTrigger(value);
            if (parsed != null) {
                triggers.add(parsed);
            }
        }
        return new ArrayList<>(triggers);
    }

    private BullseyeSkillDefinition.Condition toCondition(Map<?, ?> map) {
        Object typeRaw = map.get("type");
        if (typeRaw == null) {
            return null;
        }

        Map<String, Object> data = new HashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            data.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return new BullseyeSkillDefinition.Condition(String.valueOf(typeRaw), data);
    }

    private MechanicAction toAction(Map<?, ?> map) {
        Object typeRaw = map.get("type");
        if (typeRaw == null) {
            return null;
        }

        Map<String, Object> data = new HashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            data.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return new MechanicAction(String.valueOf(typeRaw).toLowerCase(Locale.ROOT), data);
    }

    private List<TriggerContext> selectContexts(BullseyeSkillDefinition definition, TriggerContext context) {
        String targeter = definition.targeter();
        Location anchor = anchorLocation(context);
        if (anchor == null || anchor.getWorld() == null) {
            return List.of(context);
        }

        return switch (targeter) {
            case "self", "source", "target" -> List.of(context);
            case "player" -> context.getPlayer() == null ? List.of() : List.of(copyContext(context, context.getPlayer(), context.getPlayer(), context.getBlock(), context.getItem()));
            case "nearby_players" -> {
                double radiusSquared = definition.radius() * definition.radius();
                yield anchor.getWorld().getPlayers().stream()
                    .filter(player -> player.getLocation().distanceSquared(anchor) <= radiusSquared)
                    .limit(definition.limit())
                    .map(player -> copyContext(context, player, player, context.getBlock(), context.getItem()))
                    .toList();
            }
            case "nearby_entities" -> anchor.getWorld().getNearbyEntities(anchor, definition.radius(), definition.radius(), definition.radius()).stream()
                .limit(definition.limit())
                .map(entity -> copyContext(context, context.getPlayer(), entity, context.getBlock(), context.getItem()))
                .toList();
            case "around_block" -> context.getBlock() == null ? List.of() : anchor.getWorld().getNearbyEntities(anchor, definition.radius(), definition.radius(), definition.radius()).stream()
                .limit(definition.limit())
                .map(entity -> copyContext(context, context.getPlayer(), entity, context.getBlock(), context.getItem()))
                .toList();
            default -> List.of(context);
        };
    }

    private boolean passesConditions(
        List<BullseyeSkillDefinition.Condition> conditions,
        TriggerContext selected,
        TriggerContext original
    ) {
        for (BullseyeSkillDefinition.Condition condition : conditions) {
            if (condition == null || !passesCondition(condition, selected, original)) {
                return false;
            }
        }
        return true;
    }

    private boolean passesCondition(BullseyeSkillDefinition.Condition condition, TriggerContext selected, TriggerContext original) {
        Map<String, Object> data = condition.data();
        return switch (condition.type()) {
            case "permission" -> selected.getPlayer() != null && selected.getPlayer().hasPermission(getString(data, "permission", ""));
            case "world" -> {
                String world = normalize(getString(data, "world", getString(data, "value", "")));
                Location anchor = anchorLocation(selected);
                yield anchor != null && anchor.getWorld() != null && normalize(anchor.getWorld().getName()).equals(world);
            }
            case "source_id" -> normalize(original.getSourceId()).equals(normalize(getString(data, "value", "")));
            case "has_player" -> selected.getPlayer() != null;
            case "has_target" -> selected.getTarget() != null;
            case "holding_item" -> {
                if (selected.getPlayer() == null) {
                    yield false;
                }
                String itemId = itemService.getItemId(selected.getPlayer().getInventory().getItemInMainHand());
                yield normalize(itemId).equals(normalize(getString(data, "item", getString(data, "value", ""))));
            }
            case "sneaking" -> selected.getPlayer() != null && selected.getPlayer().isSneaking() == getBoolean(data, "value", true);
            case "gamemode" -> selected.getPlayer() != null
                && normalize(selected.getPlayer().getGameMode().name()).equals(normalize(getString(data, "value", "")));
            case "min_health" -> selected.getTarget() instanceof LivingEntity living
                && living.getHealth() >= getDouble(data, "value", getDouble(data, "health", 1.0D));
            case "max_health" -> selected.getTarget() instanceof LivingEntity living
                && living.getHealth() <= getDouble(data, "value", getDouble(data, "health", 20.0D));
            case "entity_type" -> selected.getTarget() != null
                && normalize(selected.getTarget().getType().name()).equals(normalize(getString(data, "value", "")));
            case "random" -> ThreadLocalRandom.current().nextDouble() <= getDouble(data, "chance", getDouble(data, "value", 1.0D));
            default -> true;
        };
    }

    private TriggerContext copyContext(TriggerContext source, Player player, Entity target, org.bukkit.block.Block block, ItemStack item) {
        return TriggerContext.builder()
            .player(player)
            .target(target)
            .block(block)
            .item(item)
            .sourceId(source.getSourceId())
            .build();
    }

    private Location anchorLocation(TriggerContext context) {
        if (context.getTarget() != null) {
            return context.getTarget().getLocation();
        }
        if (context.getBlock() != null) {
            return context.getBlock().getLocation().add(0.5D, 0.5D, 0.5D);
        }
        if (context.getPlayer() != null) {
            return context.getPlayer().getLocation();
        }
        return null;
    }

    private String cooldownKey(BullseyeSkillDefinition definition, TriggerContext context) {
        if (context.getTarget() != null) {
            return definition.id() + "|entity|" + context.getTarget().getUniqueId();
        }
        if (context.getPlayer() != null) {
            return definition.id() + "|player|" + context.getPlayer().getUniqueId();
        }
        return definition.id() + "|source|" + normalize(context.getSourceId());
    }

    private TriggerType parseTrigger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return TriggerType.valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_'));
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Unknown Bullseye skill trigger: " + value);
            return null;
        }
    }

    private String getString(Map<String, Object> data, String key, String fallback) {
        Object value = data.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private double getDouble(Map<String, Object> data, String key, double fallback) {
        Object value = data.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value != null) {
            try {
                return Double.parseDouble(String.valueOf(value));
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private boolean getBoolean(Map<String, Object> data, String key, boolean fallback) {
        Object value = data.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value != null) {
            return Boolean.parseBoolean(String.valueOf(value));
        }
        return fallback;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
