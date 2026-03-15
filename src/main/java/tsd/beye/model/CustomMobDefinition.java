package tsd.beye.model;

import java.util.Collections;
import java.util.List;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;

public record CustomMobDefinition(
    String id,
    EntityType entityType,
    String displayName,
    String spawnEggItemId,
    boolean consumeSpawnEgg,
    double health,
    double damage,
    double movementSpeed,
    boolean useAi,
    boolean silent,
    boolean hideBaseEntity,
    String dropTableId,
    String modelId,
    List<String> skills,
    List<String> mechanics,
    ModelDefinition model
) {
    public CustomMobDefinition {
        dropTableId = dropTableId == null ? "" : dropTableId.trim().toLowerCase();
        modelId = modelId == null ? "" : modelId.trim().toLowerCase();
        skills = skills == null ? Collections.emptyList() : Collections.unmodifiableList(skills);
        mechanics = mechanics == null ? Collections.emptyList() : Collections.unmodifiableList(mechanics);
        model = model == null ? ModelDefinition.disabled() : model;
    }

    public record ModelDefinition(
        String itemId,
        double offsetX,
        double offsetY,
        double offsetZ,
        double scaleX,
        double scaleY,
        double scaleZ,
        float pitch,
        float yaw,
        float roll,
        Display.Billboard billboard,
        ItemDisplay.ItemDisplayTransform transform,
        float viewRange,
        float shadowRadius,
        float shadowStrength
    ) {
        public ModelDefinition {
            itemId = itemId == null ? "" : itemId.trim().toLowerCase();
            billboard = billboard == null ? Display.Billboard.FIXED : billboard;
            transform = transform == null ? ItemDisplay.ItemDisplayTransform.FIXED : transform;
            viewRange = viewRange <= 0.0F ? 1.0F : viewRange;
            shadowRadius = Math.max(0.0F, shadowRadius);
            shadowStrength = Math.max(0.0F, shadowStrength);
        }

        public boolean enabled() {
            return !itemId.isBlank();
        }

        public static ModelDefinition disabled() {
            return new ModelDefinition("", 0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D, 0.0F, 0.0F, 0.0F,
                Display.Billboard.FIXED, ItemDisplay.ItemDisplayTransform.FIXED, 1.0F, 0.0F, 0.0F);
        }
    }
}
