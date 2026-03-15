package tsd.beye.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public record BullseyeSkillDefinition(
    String id,
    List<TriggerType> triggers,
    String targeter,
    double radius,
    int limit,
    double chance,
    long cooldownTicks,
    List<Condition> conditions,
    List<MechanicAction> actions
) {
    public BullseyeSkillDefinition {
        id = id == null ? "" : id.trim().toLowerCase();
        targeter = targeter == null ? "target" : targeter.trim().toLowerCase();
        radius = Math.max(0.0D, radius);
        limit = limit <= 0 ? Integer.MAX_VALUE : limit;
        chance = Math.max(0.0D, Math.min(1.0D, chance));
        cooldownTicks = Math.max(0L, cooldownTicks);
        triggers = triggers == null ? Collections.emptyList() : List.copyOf(triggers);
        conditions = conditions == null ? Collections.emptyList() : List.copyOf(conditions);
        actions = actions == null ? Collections.emptyList() : List.copyOf(actions);
    }

    public record Condition(String type, Map<String, Object> data) {
        public Condition {
            type = type == null ? "" : type.trim().toLowerCase();
            data = data == null ? Collections.emptyMap() : Collections.unmodifiableMap(data);
        }
    }
}
