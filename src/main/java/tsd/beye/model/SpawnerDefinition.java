package tsd.beye.model;

import java.util.Collections;
import java.util.List;

public record SpawnerDefinition(
    String id,
    String mobId,
    long intervalTicks,
    int spawnCount,
    int maxNearby,
    double activationRange,
    double checkRange,
    double spawnRadius,
    double yOffset,
    List<String> mechanics
) {
    public SpawnerDefinition {
        mobId = mobId == null ? "" : mobId.trim().toLowerCase();
        intervalTicks = Math.max(20L, intervalTicks);
        spawnCount = Math.max(1, spawnCount);
        maxNearby = Math.max(1, maxNearby);
        activationRange = Math.max(1.0D, activationRange);
        checkRange = Math.max(1.0D, checkRange);
        spawnRadius = Math.max(0.0D, spawnRadius);
        mechanics = mechanics == null ? Collections.emptyList() : List.copyOf(mechanics);
    }
}
