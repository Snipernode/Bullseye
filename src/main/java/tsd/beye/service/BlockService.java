package tsd.beye.service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import tsd.beye.Bullseye;
import tsd.beye.model.BlockPosition;
import tsd.beye.model.CustomBlockDefinition;
import tsd.beye.utils.TextUtil;

public class BlockService {
    private static final String STORAGE_FILE = "data/custom-blocks.db.yml";

    private final Bullseye plugin;
    private final ItemService itemService;
    private final Map<String, CustomBlockDefinition> definitions = new HashMap<>();
    private final Map<String, String> blockByItemId = new HashMap<>();
    private final Map<BlockPosition, String> placedBlocks = new HashMap<>();

    public BlockService(Bullseye plugin, ItemService itemService) {
        this.plugin = plugin;
        this.itemService = itemService;
    }

    public void loadDefinitions(YamlConfiguration config) {
        definitions.clear();
        blockByItemId.clear();

        ConfigurationSection section = config.getConfigurationSection("blocks");
        if (section == null) {
            return;
        }

        for (String blockId : section.getKeys(false)) {
            ConfigurationSection blockSection = section.getConfigurationSection(blockId);
            if (blockSection == null) {
                continue;
            }

            String itemId = blockSection.getString("item-id", "").toLowerCase(Locale.ROOT);
            Material backingMaterial = parseMaterial(blockSection.getString("backing-material", "NOTE_BLOCK"), Material.NOTE_BLOCK);
            String dropItemId = blockSection.getString("drop-item", itemId).toLowerCase(Locale.ROOT);
            String requiredToolId = blockSection.getString("required-tool", "").toLowerCase(Locale.ROOT);
            boolean cancelInteraction = blockSection.getBoolean("cancel-vanilla-interaction", true);
            List<String> mechanics = blockSection.getStringList("mechanics");

            CustomBlockDefinition definition = new CustomBlockDefinition(
                blockId.toLowerCase(Locale.ROOT),
                itemId,
                backingMaterial,
                dropItemId,
                requiredToolId,
                cancelInteraction,
                mechanics
            );

            definitions.put(definition.id(), definition);
            if (!itemId.isBlank()) {
                blockByItemId.put(itemId, definition.id());
            }
        }

        plugin.getLogger().info("Loaded " + definitions.size() + " custom blocks.");
    }

    public Set<String> getBlockIds() {
        return Collections.unmodifiableSet(definitions.keySet());
    }

    public CustomBlockDefinition getDefinition(String blockId) {
        if (blockId == null) {
            return null;
        }
        return definitions.get(blockId.toLowerCase(Locale.ROOT));
    }

    public CustomBlockDefinition getDefinitionByItemId(String itemId) {
        if (itemId == null) {
            return null;
        }

        String blockId = blockByItemId.get(itemId.toLowerCase(Locale.ROOT));
        if (blockId == null) {
            return null;
        }

        return getDefinition(blockId);
    }

    public boolean isCustomBlock(Block block) {
        return getBlockId(block) != null;
    }

    public String getBlockId(Block block) {
        if (block == null) {
            return null;
        }

        return placedBlocks.get(BlockPosition.fromBlock(block));
    }

    public void setCustomBlock(Block block, String blockId) {
        if (block == null || blockId == null) {
            return;
        }

        CustomBlockDefinition definition = getDefinition(blockId);
        if (definition == null) {
            return;
        }

        block.setType(definition.backingMaterial(), false);
        placedBlocks.put(BlockPosition.fromBlock(block), definition.id());
    }

    public Optional<CustomBlockDefinition> removeCustomBlock(Block block) {
        if (block == null) {
            return Optional.empty();
        }

        String blockId = placedBlocks.remove(BlockPosition.fromBlock(block));
        if (blockId == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(getDefinition(blockId));
    }

    public boolean breakCustomBlock(Player player, Block block, ItemStack tool) {
        String blockId = getBlockId(block);
        if (blockId == null) {
            return false;
        }

        CustomBlockDefinition definition = getDefinition(blockId);
        if (definition == null) {
            placedBlocks.remove(BlockPosition.fromBlock(block));
            return false;
        }

        if (!itemService.matchesRequiredTool(tool, definition.requiredToolId())) {
            player.sendMessage(TextUtil.colorize("&cYou need tool '&f" + definition.requiredToolId() + "&c' to break this."));
            return true;
        }

        removeCustomBlock(block);
        block.setType(Material.AIR, false);

        if (definition.dropItemId() != null && !definition.dropItemId().isBlank()) {
            ItemStack drop = itemService.createItem(definition.dropItemId(), 1);
            if (drop != null) {
                block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.2, 0.5), drop);
            }
        }

        return true;
    }

    public void loadPlacedBlocks() {
        placedBlocks.clear();

        File file = new File(plugin.getDataFolder(), STORAGE_FILE);
        if (!file.exists()) {
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        List<Map<?, ?>> data = config.getMapList("blocks");
        for (Map<?, ?> entry : data) {
            try {
                BlockPosition position = BlockPosition.fromMap(entry);
                String blockId = String.valueOf(entry.get("id")).toLowerCase(Locale.ROOT);
                if (getDefinition(blockId) != null) {
                    placedBlocks.put(position, blockId);
                }
            } catch (Exception ex) {
                plugin.getLogger().warning("Skipping invalid custom block entry: " + entry);
            }
        }

        plugin.getLogger().info("Loaded " + placedBlocks.size() + " placed custom blocks.");
    }

    public void savePlacedBlocks() {
        File file = new File(plugin.getDataFolder(), STORAGE_FILE);
        if (!file.getParentFile().exists() && !file.getParentFile().mkdirs()) {
            plugin.getLogger().warning("Could not create custom block data directory.");
            return;
        }

        YamlConfiguration config = new YamlConfiguration();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<BlockPosition, String> entry : placedBlocks.entrySet()) {
            BlockPosition position = entry.getKey();
            Map<String, Object> map = new HashMap<>();
            map.put("world", position.getWorldId().toString());
            map.put("x", position.getX());
            map.put("y", position.getY());
            map.put("z", position.getZ());
            map.put("id", entry.getValue());
            list.add(map);
        }
        config.set("blocks", list);

        try {
            config.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to save custom blocks: " + ex.getMessage());
        }
    }

    private Material parseMaterial(String value, Material fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }

        try {
            return Material.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Unknown block material '" + value + "', using " + fallback.name());
            return fallback;
        }
    }
}
