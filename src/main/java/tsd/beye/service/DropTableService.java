package tsd.beye.service;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import tsd.beye.Bullseye;
import tsd.beye.model.DropTableDefinition;

public class DropTableService {
    private final Bullseye plugin;
    private final ItemService itemService;
    private final Map<String, DropTableDefinition> tables = new LinkedHashMap<>();

    public DropTableService(Bullseye plugin, ItemService itemService) {
        this.plugin = plugin;
        this.itemService = itemService;
    }

    public void load(YamlConfiguration config) {
        tables.clear();
        ConfigurationSection root = config.getConfigurationSection("drop-tables");
        if (root == null) {
            return;
        }

        for (String rawId : root.getKeys(false)) {
            ConfigurationSection tableSection = root.getConfigurationSection(rawId);
            if (tableSection == null) {
                continue;
            }

            List<DropTableDefinition.Entry> entries = tableSection.getMapList("entries").stream()
                .map(map -> new DropTableDefinition.Entry(
                    String.valueOf(map.containsKey("item") ? map.get("item") : ""),
                    parseInt(map.get("min"), parseInt(map.get("amount"), 1)),
                    parseInt(map.get("max"), parseInt(map.get("amount"), 1)),
                    parseDouble(map.get("chance"), 1.0D)
                ))
                .filter(entry -> !entry.itemId().isBlank())
                .toList();

            tables.put(normalize(rawId), new DropTableDefinition(normalize(rawId), entries));
        }

        plugin.getLogger().info("Loaded " + tables.size() + " Bullseye drop tables.");
    }

    public Collection<String> getDropTableIds() {
        return Collections.unmodifiableSet(tables.keySet());
    }

    public DropTableDefinition getDefinition(String id) {
        if (id == null) {
            return null;
        }
        return tables.get(normalize(id));
    }

    public void dropTable(String id, Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }

        DropTableDefinition definition = getDefinition(id);
        if (definition == null) {
            return;
        }

        for (DropTableDefinition.Entry entry : definition.entries()) {
            if (ThreadLocalRandom.current().nextDouble() > entry.chance()) {
                continue;
            }

            int amount = entry.minAmount() == entry.maxAmount()
                ? entry.minAmount()
                : ThreadLocalRandom.current().nextInt(entry.minAmount(), entry.maxAmount() + 1);
            if (amount <= 0) {
                continue;
            }

            ItemStack item = itemService.createItem(entry.itemId(), amount);
            if (item == null) {
                plugin.getLogger().warning("Drop table '" + definition.id() + "' references unknown item '" + entry.itemId() + "'.");
                continue;
            }

            location.getWorld().dropItemNaturally(location, item);
        }
    }

    private int parseInt(Object raw, int fallback) {
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(raw));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private double parseDouble(Object raw, double fallback) {
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        if (raw == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(String.valueOf(raw));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
