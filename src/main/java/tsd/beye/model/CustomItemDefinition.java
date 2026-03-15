package tsd.beye.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;

public record CustomItemDefinition(
    String id,
    Material baseMaterial,
    int customModelData,
    String displayName,
    List<String> lore,
    boolean unbreakable,
    List<ItemFlag> itemFlags,
    Map<Attribute, AttributeData> attributes,
    List<String> mechanics
) {
    public CustomItemDefinition {
        lore = lore == null ? Collections.emptyList() : Collections.unmodifiableList(lore);
        itemFlags = itemFlags == null ? Collections.emptyList() : Collections.unmodifiableList(itemFlags);
        attributes = attributes == null ? Collections.emptyMap() : Collections.unmodifiableMap(attributes);
        mechanics = mechanics == null ? Collections.emptyList() : Collections.unmodifiableList(mechanics);
    }

    public record AttributeData(double amount, AttributeModifier.Operation operation, EquipmentSlot slot) {
    }
}
