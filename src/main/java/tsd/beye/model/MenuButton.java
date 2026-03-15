package tsd.beye.model;

import java.util.Collections;
import java.util.List;
import org.bukkit.Material;

public record MenuButton(
    int slot,
    String customItemId,
    Material material,
    String name,
    List<String> lore,
    List<String> mechanics
) {
    public MenuButton {
        lore = lore == null ? Collections.emptyList() : Collections.unmodifiableList(lore);
        mechanics = mechanics == null ? Collections.emptyList() : Collections.unmodifiableList(mechanics);
    }
}
