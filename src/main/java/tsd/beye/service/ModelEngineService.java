package tsd.beye.service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import tsd.beye.Bullseye;
import tsd.beye.model.BullseyeModelDefinition;
import tsd.beye.utils.FileUtil;

public class ModelEngineService {
    private final Bullseye plugin;
    private final Map<String, BullseyeModelDefinition> models = new LinkedHashMap<>();
    private final Map<String, BlueprintAsset> discoveredBlueprintAssets = new LinkedHashMap<>();

    private String namespace = "bullseye";
    private boolean createZip = true;
    private boolean createAtlas = true;
    private boolean createMcMeta = true;
    private Material itemModel = Material.LEATHER_HORSE_ARMOR;
    private List<String> itemModels = new ArrayList<>();
    private boolean syncReferencePackAssets = true;
    private boolean overwriteSyncedAssets = true;
    private List<String> blueprintRoots = new ArrayList<>();
    private List<String> referencePackRoots = new ArrayList<>();
    private boolean autoImportBlueprintDefinitions;
    private boolean overwriteImportedDefinitions;
    private String generatedAddonFolder = "generated-blueprints";
    private int generatedStartingCustomModelData = 24000;

    public ModelEngineService(Bullseye plugin) {
        this.plugin = plugin;
    }

    public void reloadSettings() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(
            plugin.getDataFolder().toPath().resolve("modelengine.yml").toFile()
        );
        ConfigurationSection root = config.getConfigurationSection("model-engine");
        if (root == null) {
            return;
        }

        namespace = sanitizeNamespace(root.getString("namespace", "bullseye"));
        createZip = root.getBoolean("create-zip", true);
        createAtlas = root.getBoolean("create-atlas", true);
        createMcMeta = root.getBoolean("create-mc-meta", true);
        itemModel = parseMaterial(root.getString("item-model"), Material.LEATHER_HORSE_ARMOR);
        itemModels = new ArrayList<>(root.getStringList("item-models"));
        syncReferencePackAssets = root.getBoolean("sync-reference-pack-assets", true);
        overwriteSyncedAssets = root.getBoolean("overwrite-synced-assets", true);
        blueprintRoots = new ArrayList<>(root.getStringList("blueprint-roots"));
        referencePackRoots = new ArrayList<>(root.getStringList("reference-pack-roots"));

        ConfigurationSection importSection = root.getConfigurationSection("blueprint-import");
        autoImportBlueprintDefinitions = importSection != null && importSection.getBoolean("auto-import", false);
        overwriteImportedDefinitions = importSection != null && importSection.getBoolean("overwrite-existing", false);
        generatedAddonFolder = importSection == null ? "generated-blueprints" : importSection.getString("generated-addon-folder", "generated-blueprints");
        generatedStartingCustomModelData = importSection == null ? 24000 : Math.max(1, importSection.getInt("starting-custom-model-data", 24000));

        if (blueprintRoots.isEmpty()) {
            blueprintRoots = List.of(
                "BBModels",
                "../Champions/BBModels",
                "G:/Peach Tree Studios Clients/Champions/BBModels",
                "/mnt/g/Peach Tree Studios Clients/Champions/BBModels"
            );
        }
        if (referencePackRoots.isEmpty()) {
            referencePackRoots = List.of(
                "Reference/ModelEngine-Reference/pack",
                "/mnt/g/Peach Tree Studios Clients/Bullseye/Bullseye/Reference/ModelEngine-Reference/pack"
            );
        }

        scanBlueprints();
        if (syncReferencePackAssets) {
            syncReferencePackAssets();
        }
        if (autoImportBlueprintDefinitions) {
            importDiscoveredBlueprints(overwriteImportedDefinitions);
        }
    }

    public void load(YamlConfiguration config) {
        models.clear();
        ConfigurationSection root = config.getConfigurationSection("models");
        if (root == null) {
            return;
        }

        for (String rawId : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(rawId);
            if (section == null) {
                continue;
            }

            BullseyeModelDefinition definition = new BullseyeModelDefinition(
                normalize(rawId),
                normalize(section.getString("item", "")),
                section.getString("blueprint", rawId),
                section.getString("default-animation", "idle"),
                section.getDouble("offset.x", 0.0D),
                section.getDouble("offset.y", 0.0D),
                section.getDouble("offset.z", 0.0D),
                section.getDouble("scale.x", 1.0D),
                section.getDouble("scale.y", 1.0D),
                section.getDouble("scale.z", 1.0D),
                (float) section.getDouble("rotation.pitch", 0.0D),
                (float) section.getDouble("rotation.yaw", 0.0D),
                (float) section.getDouble("rotation.roll", 0.0D),
                parseEnum(Display.Billboard.class, section.getString("billboard"), Display.Billboard.FIXED),
                parseEnum(ItemDisplay.ItemDisplayTransform.class, section.getString("transform"), ItemDisplay.ItemDisplayTransform.FIXED),
                (float) section.getDouble("view-range", 1.0D),
                (float) section.getDouble("shadow-radius", 0.0D),
                (float) section.getDouble("shadow-strength", 0.0D),
                section.getStringList("states")
            );
            models.put(definition.id(), definition);
        }

        plugin.getLogger().info("Loaded " + models.size() + " Bullseye model definitions.");
    }

    public Collection<String> getModelIds() {
        return Collections.unmodifiableSet(models.keySet());
    }

    public Collection<String> getDiscoveredBlueprints() {
        return Collections.unmodifiableSet(discoveredBlueprintAssets.keySet());
    }

    public Collection<BlueprintAsset> getDiscoveredBlueprintAssets() {
        return Collections.unmodifiableCollection(discoveredBlueprintAssets.values());
    }

    public BullseyeModelDefinition getDefinition(String id) {
        if (id == null) {
            return null;
        }
        return models.get(normalize(id));
    }

    public String getNamespace() {
        return namespace;
    }

    public Material getItemModel() {
        return itemModel;
    }

    public List<String> getItemModels() {
        return List.copyOf(itemModels);
    }

    public boolean isCreateZip() {
        return createZip;
    }

    public boolean isCreateAtlas() {
        return createAtlas;
    }

    public boolean isCreateMcMeta() {
        return createMcMeta;
    }

    public boolean syncReferencePackAssets() {
        List<Path> roots = resolveExistingPaths(referencePackRoots);
        if (roots.isEmpty()) {
            return false;
        }

        Path targetRoot = plugin.getDataFolder().toPath().resolve("resourcepack/addons/modelengine-reference");
        try {
            if (overwriteSyncedAssets && Files.exists(targetRoot)) {
                FileUtil.deleteDirectory(targetRoot);
            }
            Files.createDirectories(targetRoot);
            for (Path root : roots) {
                copyDirectoryContents(root, targetRoot);
            }
            return true;
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to sync ModelEngine reference pack assets: " + ex.getMessage());
            return false;
        }
    }

    public BlueprintImportResult importDiscoveredBlueprints(boolean overwrite) {
        scanBlueprints();
        if (discoveredBlueprintAssets.isEmpty()) {
            return new BlueprintImportResult(0, 0, 0, 0, "No BBModel blueprints were discovered.");
        }

        File itemsFile = plugin.getDataFolder().toPath().resolve("items.yml").toFile();
        File modelsFile = plugin.getDataFolder().toPath().resolve("models.yml").toFile();
        YamlConfiguration items = YamlConfiguration.loadConfiguration(itemsFile);
        YamlConfiguration modelConfig = YamlConfiguration.loadConfiguration(modelsFile);

        int nextCustomModelData = nextCustomModelData(items);
        int importedBlueprints = 0;
        int generatedItems = 0;
        int generatedModels = 0;
        int copiedTextures = 0;

        Path addonRoot = plugin.getDataFolder().toPath()
            .resolve("resourcepack/addons")
            .resolve(generatedAddonFolder)
            .resolve("assets")
            .resolve(namespace);
        Path textureRoot = addonRoot.resolve("textures/item");
        Path modelRoot = addonRoot.resolve("models/item");
        Path blueprintTextureRoot = addonRoot.resolve("textures/blueprints");
        Path metadataFile = plugin.getDataFolder().toPath().resolve("generated/blueprints.yml");
        YamlConfiguration metadata = new YamlConfiguration();

        try {
            Files.createDirectories(textureRoot);
            Files.createDirectories(modelRoot);
            Files.createDirectories(blueprintTextureRoot);
        } catch (IOException ex) {
            return new BlueprintImportResult(0, 0, 0, 0, "Could not prepare generated blueprint asset folders: " + ex.getMessage());
        }

        for (BlueprintAsset asset : discoveredBlueprintAssets.values()) {
            String modelId = asset.id() + "_model";
            String itemId = asset.id() + "_blueprint";

            if (overwrite || items.getConfigurationSection("items." + itemId) == null) {
                items.set("items." + itemId + ".base", itemModel.name());
                items.set("items." + itemId + ".custom-model-data", nextCustomModelData++);
                items.set("items." + itemId + ".name", "&b" + prettify(asset.id()));
                items.set("items." + itemId + ".lore", List.of(
                    "&7Generated from Bullseye blueprint import.",
                    "&7Blueprint: &f" + asset.id()
                ));
                generatedItems++;
            }

            if (overwrite || modelConfig.getConfigurationSection("models." + modelId) == null) {
                modelConfig.set("models." + modelId + ".item", itemId);
                modelConfig.set("models." + modelId + ".blueprint", asset.id());
                modelConfig.set("models." + modelId + ".default-animation", "idle");
                modelConfig.set("models." + modelId + ".offset.x", 0.0D);
                modelConfig.set("models." + modelId + ".offset.y", 0.0D);
                modelConfig.set("models." + modelId + ".offset.z", 0.0D);
                modelConfig.set("models." + modelId + ".scale.x", 1.0D);
                modelConfig.set("models." + modelId + ".scale.y", 1.0D);
                modelConfig.set("models." + modelId + ".scale.z", 1.0D);
                modelConfig.set("models." + modelId + ".rotation.pitch", 0.0D);
                modelConfig.set("models." + modelId + ".rotation.yaw", 0.0D);
                modelConfig.set("models." + modelId + ".rotation.roll", 0.0D);
                modelConfig.set("models." + modelId + ".transform", "FIXED");
                modelConfig.set("models." + modelId + ".billboard", "FIXED");
                modelConfig.set("models." + modelId + ".view-range", 1.2D);
                generatedModels++;
            }

            try {
                copiedTextures += exportBlueprintAssets(asset, itemId, textureRoot, modelRoot, blueprintTextureRoot);
                metadata.set("blueprints." + asset.id() + ".item-id", itemId);
                metadata.set("blueprints." + asset.id() + ".model-id", modelId);
                metadata.set("blueprints." + asset.id() + ".source", asset.source().toString());
                metadata.set("blueprints." + asset.id() + ".textures", asset.textureNames());
                metadata.set("blueprints." + asset.id() + ".elements", asset.elementCount());
                importedBlueprints++;
            } catch (IOException ex) {
                plugin.getLogger().warning("Failed to export blueprint '" + asset.id() + "': " + ex.getMessage());
            }
        }

        try {
            items.save(itemsFile);
            modelConfig.save(modelsFile);
            Files.createDirectories(metadataFile.getParent());
            metadata.save(metadataFile.toFile());
        } catch (IOException ex) {
            return new BlueprintImportResult(importedBlueprints, generatedItems, generatedModels, copiedTextures, "Imported, but failed saving generated definitions: " + ex.getMessage());
        }

        plugin.getBootstrap().getContentService().loadAll(false);
        plugin.getBootstrap().getPackGenerator().generatePack(false);
        return new BlueprintImportResult(importedBlueprints, generatedItems, generatedModels, copiedTextures, "Imported BBModels into Bullseye definitions and generated assets.");
    }

    private int exportBlueprintAssets(
        BlueprintAsset asset,
        String itemId,
        Path textureRoot,
        Path modelRoot,
        Path blueprintTextureRoot
    ) throws IOException {
        int copied = 0;

        Path primaryTextureTarget = textureRoot.resolve(itemId + ".png");
        Path referenceModel = resolveReferenceItemModel(asset.id());
        if (referenceModel != null) {
            Files.createDirectories(modelRoot);
            Files.copy(referenceModel, modelRoot.resolve(itemId + ".json"), StandardCopyOption.REPLACE_EXISTING);
        } else {
            Files.writeString(
                modelRoot.resolve(itemId + ".json"),
                """
                {
                  "parent": "minecraft:item/generated",
                  "textures": {
                    "layer0": "%s:item/%s"
                  }
                }
                """.formatted(namespace, itemId).trim(),
                StandardCharsets.UTF_8
            );
        }

        for (Path texture : asset.textureFiles()) {
            String fileName = texture.getFileName().toString().toLowerCase(Locale.ROOT);
            Path blueprintTarget = blueprintTextureRoot.resolve(asset.id()).resolve(fileName);
            Files.createDirectories(blueprintTarget.getParent());
            Files.copy(texture, blueprintTarget, StandardCopyOption.REPLACE_EXISTING);
            copied++;
        }

        if (asset.primaryTexture() != null) {
            Files.createDirectories(primaryTextureTarget.getParent());
            Files.copy(asset.primaryTexture(), primaryTextureTarget, StandardCopyOption.REPLACE_EXISTING);
            copied++;
        }

        return copied;
    }

    private Path resolveReferenceItemModel(String blueprintId) {
        for (Path root : resolveExistingPaths(referencePackRoots)) {
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.filter(Files::isRegularFile).toList()) {
                    String normalized = normalize(stripExtension(file.getFileName().toString()));
                    if (!normalized.equals(normalize(blueprintId))) {
                        continue;
                    }
                    String path = file.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
                    if (path.contains("/models/item/") && path.endsWith(".json")) {
                        return file;
                    }
                }
            } catch (IOException ex) {
                plugin.getLogger().warning("Failed to search reference item models in " + root + ": " + ex.getMessage());
            }
        }
        return null;
    }

    private void scanBlueprints() {
        discoveredBlueprintAssets.clear();
        for (Path root : resolveExistingPaths(blueprintRoots)) {
            try (Stream<Path> stream = Files.walk(root)) {
                for (Path file : stream.filter(Files::isRegularFile).toList()) {
                    String lower = file.getFileName().toString().toLowerCase(Locale.ROOT);
                    if (!lower.endsWith(".bbmodel")) {
                        continue;
                    }

                    String base = normalize(stripExtension(file.getFileName().toString()));
                    List<Path> textures = discoverSiblingTextures(file);
                    Path primaryTexture = selectPrimaryTexture(base, textures);
                    int elementCount = countOccurrences(Files.readString(file, StandardCharsets.UTF_8), "\"from\"");
                    List<String> textureNames = textures.stream().map(path -> stripExtension(path.getFileName().toString())).toList();
                    discoveredBlueprintAssets.put(base, new BlueprintAsset(base, file, primaryTexture, textures, textureNames, elementCount));
                }
            } catch (IOException ex) {
                plugin.getLogger().warning("Failed to scan blueprint root " + root + ": " + ex.getMessage());
            }
        }
    }

    private List<Path> discoverSiblingTextures(Path bbModelFile) {
        Path parent = bbModelFile.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            return List.of();
        }

        try (Stream<Path> children = Files.list(parent)) {
            return children
                .filter(Files::isRegularFile)
                .filter(this::isTextureAsset)
                .sorted()
                .toList();
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to discover blueprint textures near " + bbModelFile + ": " + ex.getMessage());
            return List.of();
        }
    }

    private Path selectPrimaryTexture(String base, List<Path> textures) {
        for (Path texture : textures) {
            if (normalize(stripExtension(texture.getFileName().toString())).equals(base)) {
                return texture;
            }
        }
        return textures.isEmpty() ? null : textures.getFirst();
    }

    private int nextCustomModelData(YamlConfiguration items) {
        ConfigurationSection section = items.getConfigurationSection("items");
        int highest = generatedStartingCustomModelData - 1;
        if (section == null) {
            return generatedStartingCustomModelData;
        }

        for (String id : section.getKeys(false)) {
            highest = Math.max(highest, section.getInt(id + ".custom-model-data", 0));
        }
        return highest + 1;
    }

    private void copyDirectoryContents(Path source, Path target) throws IOException {
        try (Stream<Path> children = Files.list(source)) {
            for (Path child : children.toList()) {
                Path destination = target.resolve(child.getFileName().toString());
                if (Files.isDirectory(child)) {
                    FileUtil.copyDirectory(child, destination);
                } else {
                    Files.copy(child, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private List<Path> resolveExistingPaths(List<String> rawPaths) {
        Set<Path> resolved = new LinkedHashSet<>();
        for (String raw : rawPaths) {
            Path path = resolvePath(raw);
            if (path != null && Files.exists(path)) {
                resolved.add(path.normalize().toAbsolutePath());
            }
        }
        return new ArrayList<>(resolved);
    }

    private Path resolvePath(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        try {
            Path path = Path.of(raw.trim());
            if (!path.isAbsolute()) {
                Path dataRelative = plugin.getDataFolder().toPath().resolve(path).normalize();
                if (Files.exists(dataRelative)) {
                    return dataRelative;
                }
                Path workingDir = Path.of("").toAbsolutePath().resolve(path).normalize();
                if (Files.exists(workingDir)) {
                    return workingDir;
                }
            } else if (Files.exists(path)) {
                return path;
            }

            String value = raw.trim();
            if (value.matches("^[A-Za-z]:[\\\\/].*")) {
                char drive = Character.toLowerCase(value.charAt(0));
                String tail = value.substring(2).replace('\\', '/');
                Path wsl = Path.of("/mnt/" + drive + tail);
                if (Files.exists(wsl)) {
                    return wsl;
                }
            }

            return null;
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean isTextureAsset(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".png");
    }

    private int countOccurrences(String text, String needle) {
        if (text == null || text.isBlank() || needle == null || needle.isBlank()) {
            return 0;
        }
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private String prettify(String id) {
        String[] split = normalize(id).split("[_\\-\\s]+");
        StringBuilder builder = new StringBuilder();
        for (String part : split) {
            if (part.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.isEmpty() ? id : builder.toString();
    }

    private Material parseMaterial(String value, Material fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Material.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    private <E extends Enum<E>> E parseEnum(Class<E> enumType, String value, E fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(enumType, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    private String sanitizeNamespace(String value) {
        if (value == null || value.isBlank()) {
            return "bullseye";
        }
        return value.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public record BlueprintAsset(
        String id,
        Path source,
        Path primaryTexture,
        List<Path> textureFiles,
        List<String> textureNames,
        int elementCount
    ) {
        public BlueprintAsset {
            textureFiles = textureFiles == null ? List.of() : List.copyOf(textureFiles);
            textureNames = textureNames == null ? List.of() : List.copyOf(textureNames);
        }
    }

    public record BlueprintImportResult(
        int importedBlueprints,
        int generatedItems,
        int generatedModels,
        int copiedTextures,
        String message
    ) {
        public boolean success() {
            return importedBlueprints > 0;
        }
    }
}
