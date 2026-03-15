package tsd.beye.model;

import java.util.Collections;
import java.util.List;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;

public record BullseyeModelDefinition(
    String id,
    String itemId,
    String blueprint,
    String defaultAnimation,
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
    float shadowStrength,
    List<String> states
) {
    public BullseyeModelDefinition {
        itemId = itemId == null ? "" : itemId.trim().toLowerCase();
        blueprint = blueprint == null ? "" : blueprint.trim();
        defaultAnimation = defaultAnimation == null ? "" : defaultAnimation.trim();
        billboard = billboard == null ? Display.Billboard.FIXED : billboard;
        transform = transform == null ? ItemDisplay.ItemDisplayTransform.FIXED : transform;
        states = states == null ? Collections.emptyList() : List.copyOf(states);
    }

    public CustomMobDefinition.ModelDefinition toRuntimeModel() {
        return new CustomMobDefinition.ModelDefinition(
            itemId,
            offsetX,
            offsetY,
            offsetZ,
            scaleX,
            scaleY,
            scaleZ,
            pitch,
            yaw,
            roll,
            billboard,
            transform,
            viewRange,
            shadowRadius,
            shadowStrength
        );
    }
}
