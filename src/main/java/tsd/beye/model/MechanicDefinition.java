package tsd.beye.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public record MechanicDefinition(String id, Map<TriggerType, List<MechanicAction>> actionsByTrigger) {
    public MechanicDefinition {
        actionsByTrigger = actionsByTrigger == null ? Collections.emptyMap() : Collections.unmodifiableMap(actionsByTrigger);
    }
}
