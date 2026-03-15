package tsd.beye.model;

import java.util.Collections;
import java.util.List;

public record RandomSpawnDefinition(
    String id,
    String mobId,
    List<String> worlds,
    long intervalTicks,
    double chance,
    int spawnCount,
    int maxNearby,
    int attempts,
    double minDistance,
    double maxDistance,
    double yOffset
) {
    public RandomSpawnDefinition {
        mobId = mobId == null ? "" : mobId.trim().toLowerCase();
        worlds = worlds == null ? Collections.emptyList() : List.copyOf(worlds);
        intervalTicks = Math.max(20L, intervalTicks);
        chance = Math.max(0.0D, Math.min(1.0D, chance));
        spawnCount = Math.max(1, spawnCount);
        maxNearby = Math.max(1, maxNearby);
        attempts = Math.max(1, attempts);
        minDistance = Math.max(0.0D, minDistance);
        maxDistance = Math.max(minDistance, maxDistance);
    }
}
