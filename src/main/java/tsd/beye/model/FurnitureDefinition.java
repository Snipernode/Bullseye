package tsd.beye.model;

import java.util.Collections;
import java.util.List;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;

public record FurnitureDefinition(
    String id,
    String itemId,
    String displayItemId,
    String modelId,
    boolean seat,
    List<String> mechanics,
    DisplayDefinition display
) {
    public FurnitureDefinition {
        itemId = itemId == null ? "" : itemId.trim().toLowerCase();
        displayItemId = displayItemId == null ? "" : displayItemId.trim().toLowerCase();
        modelId = modelId == null ? "" : modelId.trim().toLowerCase();
        mechanics = mechanics == null ? Collections.emptyList() : Collections.unmodifiableList(mechanics);
        display = display == null ? DisplayDefinition.defaults() : display;
    }

    public record DisplayDefinition(
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
        public DisplayDefinition {
            billboard = billboard == null ? Display.Billboard.FIXED : billboard;
            transform = transform == null ? ItemDisplay.ItemDisplayTransform.FIXED : transform;
        }

        public static DisplayDefinition defaults() {
            return new DisplayDefinition(
                0.0D,
                0.0D,
                0.0D,
                1.0D,
                1.0D,
                1.0D,
                0.0F,
                0.0F,
                0.0F,
                Display.Billboard.FIXED,
                ItemDisplay.ItemDisplayTransform.FIXED,
                1.0F,
                0.0F,
                0.0F
            );
        }
    }
}
