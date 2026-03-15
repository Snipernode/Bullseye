package tsd.beye.model;

import java.util.Collections;
import java.util.List;
import org.bukkit.Material;

public record CustomBlockDefinition(
    String id,
    String itemId,
    Material backingMaterial,
    String dropItemId,
    String requiredToolId,
    boolean cancelVanillaInteraction,
    List<String> mechanics
) {
    public CustomBlockDefinition {
        mechanics = mechanics == null ? Collections.emptyList() : Collections.unmodifiableList(mechanics);
    }
}
