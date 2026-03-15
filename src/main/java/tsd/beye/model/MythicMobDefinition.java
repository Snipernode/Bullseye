package tsd.beye.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public record MythicMobDefinition(
    String id,
    String mythicMobId,
    String spawnEggItemId,
    int level,
    boolean consumeSpawnEgg,
    String entityType,
    String displayName,
    double health,
    double damage,
    List<String> spawnSkills,
    Map<String, List<String>> generatedSkills
) {
    public MythicMobDefinition {
        spawnSkills = spawnSkills == null ? Collections.emptyList() : List.copyOf(spawnSkills);
        generatedSkills = generatedSkills == null ? Collections.emptyMap() : Collections.unmodifiableMap(generatedSkills);
    }
}
