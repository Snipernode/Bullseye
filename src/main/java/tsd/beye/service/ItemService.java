package tsd.beye.service;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import tsd.beye.Bullseye;
import tsd.beye.core.Keychain;
import tsd.beye.model.CustomItemDefinition;
import tsd.beye.utils.TextUtil;

public class ItemService {
    private final Bullseye plugin;
    private final Keychain keychain;
    private final Map<String, CustomItemDefinition> items = new HashMap<>();

    public ItemService(Bullseye plugin, Keychain keychain) {
        this.plugin = plugin;
        this.keychain = keychain;
    }

    public void load(YamlConfiguration config) {
        items.clear();
        ConfigurationSection section = config.getConfigurationSection("items");
        if (section == null) {
            return;
        }

        for (String id : section.getKeys(false)) {
            ConfigurationSection itemSection = section.getConfigurationSection(id);
            if (itemSection == null) {
                continue;
            }

            Material base = parseMaterial(itemSection.getString("base", "PAPER"), Material.PAPER);
            int customModelData = itemSection.getInt("custom-model-data", 0);
            String name = itemSection.getString("name", id);
            List<String> lore = itemSection.getStringList("lore");
            boolean unbreakable = itemSection.getBoolean("unbreakable", false);
            List<ItemFlag> flags = itemSection.getStringList("flags").stream()
                .map(this::parseItemFlag)
                .filter(flag -> flag != null)
                .toList();
            List<String> mechanics = itemSection.getStringList("mechanics");

            Map<Attribute, CustomItemDefinition.AttributeData> attributes = new HashMap<>();
            ConfigurationSection attributesSection = itemSection.getConfigurationSection("attributes");
            if (attributesSection != null) {
                for (String key : attributesSection.getKeys(false)) {
                    ConfigurationSection attributeSection = attributesSection.getConfigurationSection(key);
                    if (attributeSection == null) {
                        continue;
                    }

                    Attribute attribute = parseAttribute(key);
                    if (attribute == null) {
                        plugin.getLogger().warning("Unknown attribute '" + key + "' in item '" + id + "'");
                        continue;
                    }

                    double amount = attributeSection.getDouble("amount", 0.0D);
                    AttributeModifier.Operation operation = parseOperation(
                        attributeSection.getString("operation", "ADD_NUMBER"),
                        AttributeModifier.Operation.ADD_NUMBER
                    );
                    EquipmentSlot slot = parseEquipmentSlot(attributeSection.getString("slot", "HAND"), EquipmentSlot.HAND);

                    attributes.put(attribute, new CustomItemDefinition.AttributeData(amount, operation, slot));
                }
            }

            items.put(id.toLowerCase(Locale.ROOT), new CustomItemDefinition(
                id.toLowerCase(Locale.ROOT),
                base,
                customModelData,
                name,
                lore,
                unbreakable,
                flags,
                attributes,
                mechanics
            ));
        }

        plugin.getLogger().info("Loaded " + items.size() + " custom items.");
    }

    public Set<String> getItemIds() {
        return Collections.unmodifiableSet(items.keySet());
    }

    public Collection<CustomItemDefinition> getDefinitions() {
        return Collections.unmodifiableCollection(items.values());
    }

    public CustomItemDefinition getDefinition(String itemId) {
        if (itemId == null) {
            return null;
        }
        return items.get(itemId.toLowerCase(Locale.ROOT));
    }

    public ItemStack createItem(String itemId, int amount) {
        CustomItemDefinition def = getDefinition(itemId);
        if (def == null) {
            return null;
        }

        ItemStack item = new ItemStack(def.baseMaterial(), Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        meta.setDisplayName(TextUtil.colorize(def.displayName()));
        if (!def.lore().isEmpty()) {
            meta.setLore(TextUtil.colorize(def.lore()));
        }

        if (def.customModelData() > 0) {
            meta.setCustomModelData(def.customModelData());
        }

        if (def.unbreakable()) {
            meta.setUnbreakable(true);
        }

        for (ItemFlag flag : def.itemFlags()) {
            meta.addItemFlags(flag);
        }

        for (Map.Entry<Attribute, CustomItemDefinition.AttributeData> entry : def.attributes().entrySet()) {
            CustomItemDefinition.AttributeData attributeData = entry.getValue();
            AttributeModifier modifier = new AttributeModifier(
                UUID.randomUUID(),
                "bullseye_" + entry.getKey().name().toLowerCase(Locale.ROOT),
                attributeData.amount(),
                attributeData.operation(),
                attributeData.slot()
            );
            meta.addAttributeModifier(entry.getKey(), modifier);
        }

        meta.getPersistentDataContainer().set(keychain.itemId(), PersistentDataType.STRING, def.id());
        item.setItemMeta(meta);
        return item;
    }

    public boolean isCustomItem(ItemStack item) {
        return getItemId(item) != null;
    }

    public String getItemId(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }

        String itemId = meta.getPersistentDataContainer().get(keychain.itemId(), PersistentDataType.STRING);
        if (itemId != null) {
            return itemId;
        }

        if (meta.hasCustomModelData()) {
            int cmd = meta.getCustomModelData();
            for (CustomItemDefinition def : items.values()) {
                if (def.baseMaterial() == item.getType() && def.customModelData() == cmd) {
                    return def.id();
                }
            }
        }

        return null;
    }

    public boolean matchesRequiredTool(ItemStack heldItem, String requiredToolId) {
        if (requiredToolId == null || requiredToolId.isBlank()) {
            return true;
        }

        String heldId = getItemId(heldItem);
        return requiredToolId.equalsIgnoreCase(heldId);
    }

    private Material parseMaterial(String value, Material fallback) {
        if (value == null) {
            return fallback;
        }

        try {
            return Material.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Unknown material '" + value + "', using " + fallback.name());
            return fallback;
        }
    }

    private ItemFlag parseItemFlag(String value) {
        if (value == null) {
            return null;
        }

        try {
            return ItemFlag.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Unknown item flag '" + value + "'.");
            return null;
        }
    }

    private Attribute parseAttribute(String value) {
        if (value == null) {
            return null;
        }

        try {
            return Attribute.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private AttributeModifier.Operation parseOperation(String value, AttributeModifier.Operation fallback) {
        if (value == null) {
            return fallback;
        }

        try {
            return AttributeModifier.Operation.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Unknown operation '" + value + "', using " + fallback.name());
            return fallback;
        }
    }

    private EquipmentSlot parseEquipmentSlot(String value, EquipmentSlot fallback) {
        if (value == null) {
            return fallback;
        }

        try {
            return EquipmentSlot.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Unknown equipment slot '" + value + "', using " + fallback.name());
            return fallback;
        }
    }
}
