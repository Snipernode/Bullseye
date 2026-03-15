package tsd.beye.service;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import tsd.beye.Bullseye;
import tsd.beye.model.CustomItemDefinition;

public class GeneratorService {
    private static final String GENERATED_ADDON_ID = "generated";
    private static final String NAMESPACE = "bullseye";
    private static final int TEXTURE_SIZE = 32;
    private static final int MIN_GENERATED_CUSTOM_MODEL_DATA = 14000;

    private final Bullseye plugin;

    public GeneratorService(Bullseye plugin) {
        this.plugin = plugin;
    }

    public GenerationResult generateItem(String rawId, String rawBaseMaterial, String rawCustomModelData, String displayName, boolean overwrite) {
        String itemId = sanitizeId(rawId);
        if (itemId.isBlank()) {
            return GenerationResult.failure("Item id cannot be blank.");
        }

        Material baseMaterial = parseMaterial(rawBaseMaterial);
        if (baseMaterial == null) {
            return GenerationResult.failure("Unknown base material: " + rawBaseMaterial);
        }

        File itemsFile = addonConfigFile("items.yml");
        YamlConfiguration items = YamlConfiguration.loadConfiguration(itemsFile);
        String path = "items." + itemId;
        ConfigurationSection existing = items.getConfigurationSection(path);
        if (existing != null && !overwrite) {
            return GenerationResult.failure("Generated item already exists. Use overwrite if you want to replace it.");
        }

        int customModelData = resolveCustomModelData(items, itemId, baseMaterial, rawCustomModelData, existing);
        if (customModelData <= 0) {
            return GenerationResult.failure("Custom model data must be a positive number or 'auto'.");
        }

        String conflict = findCustomModelDataConflict(items, itemId, baseMaterial, customModelData);
        if (conflict != null) {
            return GenerationResult.failure("Custom model data " + customModelData + " already belongs to " + conflict + ".");
        }

        List<String> details = new ArrayList<>();
        String visibleName = displayName == null || displayName.isBlank() ? prettify(itemId) : displayName.trim();
        items.set(path + ".base", baseMaterial.name());
        items.set(path + ".custom-model-data", customModelData);
        items.set(path + ".name", "&f" + visibleName);
        items.set(path + ".lore", defaultItemLore(baseMaterial));

        try {
            saveYaml(itemsFile, items);
            ensureItemModelJson(itemId, baseMaterial, overwrite, details);
        } catch (IOException ex) {
            return GenerationResult.failure("Failed to generate item assets: " + ex.getMessage());
        }

        details.add("Generated addon item: " + path);
        details.add("Assigned custom-model-data: " + customModelData);
        recordManifestEntry("items", itemId, details);
        return finalizeGeneration("Generated Bullseye item " + itemId + ".", details);
    }

    public GenerationResult generateTexture(String rawId, String rawStyle, boolean overwrite) {
        String itemId = sanitizeId(rawId);
        if (itemId.isBlank()) {
            return GenerationResult.failure("Texture id cannot be blank.");
        }

        Material baseMaterial = resolveBaseMaterial(itemId);
        TextureStyle style = TextureStyle.parse(rawStyle, baseMaterial);
        if (style == null) {
            return GenerationResult.failure("Unknown texture style: " + rawStyle);
        }

        Path texturePath = itemTexturePath(itemId);
        if (Files.exists(texturePath) && !overwrite) {
            return GenerationResult.failure("Generated texture already exists. Use overwrite if you want to replace it.");
        }

        List<String> details = new ArrayList<>();
        try {
            Files.createDirectories(Objects.requireNonNull(texturePath.getParent()));
            ImageIO.write(generateTextureImage(itemId, style), "png", texturePath.toFile());
            ensureItemModelJson(itemId, baseMaterial, overwrite, details);
        } catch (IOException ex) {
            return GenerationResult.failure("Failed to generate texture: " + ex.getMessage());
        }

        details.add("Generated addon texture: " + texturePath.getFileName());
        details.add("Texture style: " + style.name().toLowerCase(Locale.ROOT));
        recordManifestEntry("textures", itemId, details);
        return finalizeGeneration("Generated Bullseye texture " + itemId + ".", details);
    }

    public GenerationResult generateModel(String rawModelId, String rawItemId, String rawBlueprint, boolean overwrite) {
        String modelId = sanitizeId(rawModelId);
        String itemId = sanitizeId(rawItemId);
        if (modelId.isBlank() || itemId.isBlank()) {
            return GenerationResult.failure("Model id and item id are required.");
        }

        File modelsFile = addonConfigFile("models.yml");
        YamlConfiguration models = YamlConfiguration.loadConfiguration(modelsFile);
        String path = "models." + modelId;
        if (models.getConfigurationSection(path) != null && !overwrite) {
            return GenerationResult.failure("Generated model already exists. Use overwrite if you want to replace it.");
        }

        String blueprint = rawBlueprint == null || rawBlueprint.isBlank() ? itemId : sanitizeId(rawBlueprint);
        writeModelDefinition(models, modelId, itemId, blueprint, defaultModelYOffset(itemId));

        try {
            saveYaml(modelsFile, models);
        } catch (IOException ex) {
            return GenerationResult.failure("Failed to save generated model: " + ex.getMessage());
        }

        List<String> details = List.of(
            "Generated addon model: " + path,
            "Linked item: " + itemId,
            "Blueprint id: " + blueprint
        );
        recordManifestEntry("models", modelId, details);
        return finalizeGeneration("Generated Bullseye model " + modelId + ".", details);
    }

    public GenerationResult generateAll(String rawId, String rawBaseMaterial, String rawCustomModelData, String displayName, boolean overwrite) {
        String itemId = sanitizeId(rawId);
        if (itemId.isBlank()) {
            return GenerationResult.failure("Asset id cannot be blank.");
        }

        Material baseMaterial = parseMaterial(rawBaseMaterial);
        if (baseMaterial == null) {
            return GenerationResult.failure("Unknown base material: " + rawBaseMaterial);
        }

        File itemsFile = addonConfigFile("items.yml");
        File modelsFile = addonConfigFile("models.yml");
        YamlConfiguration items = YamlConfiguration.loadConfiguration(itemsFile);
        YamlConfiguration models = YamlConfiguration.loadConfiguration(modelsFile);

        String itemPath = "items." + itemId;
        String modelId = itemId + "_model";
        String modelPath = "models." + modelId;
        if ((items.getConfigurationSection(itemPath) != null || models.getConfigurationSection(modelPath) != null
            || Files.exists(itemTexturePath(itemId)) || Files.exists(itemModelJsonPath(itemId))) && !overwrite) {
            return GenerationResult.failure("One or more generated assets already exist. Use overwrite if you want to replace them.");
        }

        int customModelData = resolveCustomModelData(items, itemId, baseMaterial, rawCustomModelData, items.getConfigurationSection(itemPath));
        if (customModelData <= 0) {
            return GenerationResult.failure("Custom model data must be a positive number or 'auto'.");
        }

        String conflict = findCustomModelDataConflict(items, itemId, baseMaterial, customModelData);
        if (conflict != null) {
            return GenerationResult.failure("Custom model data " + customModelData + " already belongs to " + conflict + ".");
        }

        String visibleName = displayName == null || displayName.isBlank() ? prettify(itemId) : displayName.trim();
        items.set(itemPath + ".base", baseMaterial.name());
        items.set(itemPath + ".custom-model-data", customModelData);
        items.set(itemPath + ".name", "&f" + visibleName);
        items.set(itemPath + ".lore", defaultItemLore(baseMaterial));
        writeModelDefinition(models, modelId, itemId, itemId, defaultModelYOffset(itemId));

        List<String> details = new ArrayList<>();
        try {
            saveYaml(itemsFile, items);
            saveYaml(modelsFile, models);
            writeTexture(itemId, TextureStyle.auto(baseMaterial), true);
            ensureItemModelJson(itemId, baseMaterial, true, details);
        } catch (IOException ex) {
            return GenerationResult.failure("Failed to generate asset bundle: " + ex.getMessage());
        }

        details.add("Generated addon item: " + itemPath);
        details.add("Generated addon model: " + modelPath);
        details.add("Assigned custom-model-data: " + customModelData);
        details.add("Generated addon texture: " + itemTexturePath(itemId).getFileName());
        recordManifestEntry("bundles", itemId, details);
        return finalizeGeneration("Generated Bullseye item, model, and texture for " + itemId + ".", details);
    }

    public GenerationResult generateBlock(String rawId, String rawCustomModelData, String displayName, boolean overwrite) {
        String blockId = sanitizeId(rawId);
        if (blockId.isBlank()) {
            return GenerationResult.failure("Block id cannot be blank.");
        }

        String itemId = blockId + "_item";
        String mechanicId = blockId + "_interact";
        File itemsFile = addonConfigFile("items.yml");
        File blocksFile = addonConfigFile("blocks.yml");
        File mechanicsFile = addonConfigFile("mechanics.yml");
        YamlConfiguration items = YamlConfiguration.loadConfiguration(itemsFile);
        YamlConfiguration blocks = YamlConfiguration.loadConfiguration(blocksFile);
        YamlConfiguration mechanics = YamlConfiguration.loadConfiguration(mechanicsFile);

        if ((items.getConfigurationSection("items." + itemId) != null
            || blocks.getConfigurationSection("blocks." + blockId) != null
            || mechanics.getConfigurationSection("mechanics." + mechanicId) != null
            || Files.exists(itemTexturePath(itemId))
            || Files.exists(itemModelJsonPath(itemId))) && !overwrite) {
            return GenerationResult.failure("Generated block scaffold already exists. Use overwrite if you want to replace it.");
        }

        int customModelData = resolveCustomModelData(items, itemId, Material.NOTE_BLOCK, rawCustomModelData, items.getConfigurationSection("items." + itemId));
        if (customModelData <= 0) {
            return GenerationResult.failure("Custom model data must be a positive number or 'auto'.");
        }

        String conflict = findCustomModelDataConflict(items, itemId, Material.NOTE_BLOCK, customModelData);
        if (conflict != null) {
            return GenerationResult.failure("Custom model data " + customModelData + " already belongs to " + conflict + ".");
        }

        String visibleName = displayName == null || displayName.isBlank() ? prettify(blockId) : displayName.trim();
        items.set("items." + itemId + ".base", Material.NOTE_BLOCK.name());
        items.set("items." + itemId + ".custom-model-data", customModelData);
        items.set("items." + itemId + ".name", "&f" + visibleName);
        items.set("items." + itemId + ".lore", List.of("&7Place to create a Bullseye block."));

        blocks.set("blocks." + blockId + ".item-id", itemId);
        blocks.set("blocks." + blockId + ".backing-material", Material.NOTE_BLOCK.name());
        blocks.set("blocks." + blockId + ".drop-item", itemId);
        blocks.set("blocks." + blockId + ".cancel-vanilla-interaction", true);
        blocks.set("blocks." + blockId + ".mechanics", List.of(mechanicId));

        mechanics.set("mechanics." + mechanicId + ".block_interact", List.of(
            action("type", "message", "text", "&f" + visibleName + " &7generated by Bullseye. Add your own mechanics next."),
            action("type", "sound", "sound", "UI_BUTTON_CLICK", "volume", 0.8D, "pitch", 1.1D)
        ));

        List<String> details = new ArrayList<>();
        try {
            saveYaml(itemsFile, items);
            saveYaml(blocksFile, blocks);
            saveYaml(mechanicsFile, mechanics);
            writeTexture(itemId, TextureStyle.BLOCK, true);
            ensureItemModelJson(itemId, Material.NOTE_BLOCK, true, details);
        } catch (IOException ex) {
            return GenerationResult.failure("Failed to generate block scaffold: " + ex.getMessage());
        }

        details.add("Generated block item: " + itemId);
        details.add("Generated block definition: blocks." + blockId);
        details.add("Generated starter mechanic: " + mechanicId);
        details.add("Assigned custom-model-data: " + customModelData);
        recordManifestEntry("blocks", blockId, details);
        return finalizeGeneration("Generated Bullseye block scaffold " + blockId + ".", details);
    }

    public GenerationResult generateFurniture(String rawId, String rawCustomModelData, String displayName, boolean overwrite) {
        String furnitureId = sanitizeId(rawId);
        if (furnitureId.isBlank()) {
            return GenerationResult.failure("Furniture id cannot be blank.");
        }

        String itemId = furnitureId + "_item";
        String modelId = furnitureId + "_model";
        String mechanicId = furnitureId + "_interact";
        File itemsFile = addonConfigFile("items.yml");
        File modelsFile = addonConfigFile("models.yml");
        File furnitureFile = addonConfigFile("furniture.yml");
        File mechanicsFile = addonConfigFile("mechanics.yml");
        YamlConfiguration items = YamlConfiguration.loadConfiguration(itemsFile);
        YamlConfiguration models = YamlConfiguration.loadConfiguration(modelsFile);
        YamlConfiguration furniture = YamlConfiguration.loadConfiguration(furnitureFile);
        YamlConfiguration mechanics = YamlConfiguration.loadConfiguration(mechanicsFile);

        if ((items.getConfigurationSection("items." + itemId) != null
            || models.getConfigurationSection("models." + modelId) != null
            || furniture.getConfigurationSection("furniture." + furnitureId) != null
            || mechanics.getConfigurationSection("mechanics." + mechanicId) != null
            || Files.exists(itemTexturePath(itemId))
            || Files.exists(itemModelJsonPath(itemId))) && !overwrite) {
            return GenerationResult.failure("Generated furniture scaffold already exists. Use overwrite if you want to replace it.");
        }

        int customModelData = resolveCustomModelData(items, itemId, Material.ARMOR_STAND, rawCustomModelData, items.getConfigurationSection("items." + itemId));
        if (customModelData <= 0) {
            return GenerationResult.failure("Custom model data must be a positive number or 'auto'.");
        }

        String conflict = findCustomModelDataConflict(items, itemId, Material.ARMOR_STAND, customModelData);
        if (conflict != null) {
            return GenerationResult.failure("Custom model data " + customModelData + " already belongs to " + conflict + ".");
        }

        String visibleName = displayName == null || displayName.isBlank() ? prettify(furnitureId) : displayName.trim();
        items.set("items." + itemId + ".base", Material.ARMOR_STAND.name());
        items.set("items." + itemId + ".custom-model-data", customModelData);
        items.set("items." + itemId + ".name", "&6" + visibleName);
        items.set("items." + itemId + ".lore", List.of("&7Placeable Bullseye furniture."));

        writeModelDefinition(models, modelId, itemId, furnitureId, 0.0D);

        furniture.set("furniture." + furnitureId + ".item-id", itemId);
        furniture.set("furniture." + furnitureId + ".display-item", itemId);
        furniture.set("furniture." + furnitureId + ".model-id", modelId);
        furniture.set("furniture." + furnitureId + ".seat", true);
        furniture.set("furniture." + furnitureId + ".mechanics", List.of(mechanicId));

        mechanics.set("mechanics." + mechanicId + ".furniture_interact", List.of(
            action("type", "message", "text", "&6" + visibleName + " &7generated by Bullseye. Add custom behavior next."),
            action("type", "sound", "sound", "BLOCK_WOOD_PLACE", "volume", 0.7D, "pitch", 1.15D)
        ));

        List<String> details = new ArrayList<>();
        try {
            saveYaml(itemsFile, items);
            saveYaml(modelsFile, models);
            saveYaml(furnitureFile, furniture);
            saveYaml(mechanicsFile, mechanics);
            writeTexture(itemId, TextureStyle.BLOCK, true);
            ensureItemModelJson(itemId, Material.ARMOR_STAND, true, details);
        } catch (IOException ex) {
            return GenerationResult.failure("Failed to generate furniture scaffold: " + ex.getMessage());
        }

        details.add("Generated furniture item: " + itemId);
        details.add("Generated furniture model: " + modelId);
        details.add("Generated furniture definition: furniture." + furnitureId);
        details.add("Generated starter mechanic: " + mechanicId);
        details.add("Assigned custom-model-data: " + customModelData);
        recordManifestEntry("furniture", furnitureId, details);
        return finalizeGeneration("Generated Bullseye furniture scaffold " + furnitureId + ".", details);
    }

    public GenerationResult generateMob(String rawId, String rawEntityType, String displayName, boolean overwrite) {
        String mobId = sanitizeId(rawId);
        if (mobId.isBlank()) {
            return GenerationResult.failure("Mob id cannot be blank.");
        }

        EntityType entityType = parseEntityType(rawEntityType);
        if (entityType == null) {
            return GenerationResult.failure("Unknown entity type: " + rawEntityType);
        }

        Material spawnEggMaterial = resolveSpawnEggMaterial(entityType);
        String eggItemId = mobId + "_spawn_egg";
        String modelId = mobId + "_model";
        String dropTableId = mobId + "_loot";
        String skillId = mobId + "_passive_skill";

        File itemsFile = addonConfigFile("items.yml");
        File modelsFile = addonConfigFile("models.yml");
        File mobsFile = addonConfigFile("mobs.yml");
        File dropsFile = addonConfigFile("drops.yml");
        File skillsFile = addonConfigFile("skills.yml");
        YamlConfiguration items = YamlConfiguration.loadConfiguration(itemsFile);
        YamlConfiguration models = YamlConfiguration.loadConfiguration(modelsFile);
        YamlConfiguration mobs = YamlConfiguration.loadConfiguration(mobsFile);
        YamlConfiguration drops = YamlConfiguration.loadConfiguration(dropsFile);
        YamlConfiguration skills = YamlConfiguration.loadConfiguration(skillsFile);

        if ((items.getConfigurationSection("items." + eggItemId) != null
            || models.getConfigurationSection("models." + modelId) != null
            || mobs.getConfigurationSection("mobs." + mobId) != null
            || drops.getConfigurationSection("drop-tables." + dropTableId) != null
            || skills.getConfigurationSection("skills." + skillId) != null
            || Files.exists(itemTexturePath(eggItemId))
            || Files.exists(itemModelJsonPath(eggItemId))) && !overwrite) {
            return GenerationResult.failure("Generated mob scaffold already exists. Use overwrite if you want to replace it.");
        }

        int customModelData = resolveCustomModelData(items, eggItemId, spawnEggMaterial, "auto", items.getConfigurationSection("items." + eggItemId));
        if (customModelData <= 0) {
            return GenerationResult.failure("Could not allocate custom model data for the generated spawn egg.");
        }

        String conflict = findCustomModelDataConflict(items, eggItemId, spawnEggMaterial, customModelData);
        if (conflict != null) {
            return GenerationResult.failure("Custom model data " + customModelData + " already belongs to " + conflict + ".");
        }

        String visibleName = displayName == null || displayName.isBlank() ? prettify(mobId) : displayName.trim();
        items.set("items." + eggItemId + ".base", spawnEggMaterial.name());
        items.set("items." + eggItemId + ".custom-model-data", customModelData);
        items.set("items." + eggItemId + ".name", "&f" + visibleName + " Spawn Egg");
        items.set("items." + eggItemId + ".lore", List.of("&7Generated Bullseye spawn egg."));

        writeModelDefinition(models, modelId, eggItemId, mobId, 0.25D);

        mobs.set("mobs." + mobId + ".entity-type", entityType.name());
        mobs.set("mobs." + mobId + ".display-name", "&f" + visibleName);
        mobs.set("mobs." + mobId + ".spawn-egg-item", eggItemId);
        mobs.set("mobs." + mobId + ".consume-spawn-egg", true);
        mobs.set("mobs." + mobId + ".health", defaultMobHealth(entityType));
        mobs.set("mobs." + mobId + ".damage", defaultMobDamage(entityType));
        mobs.set("mobs." + mobId + ".movement-speed", defaultMobSpeed(entityType));
        mobs.set("mobs." + mobId + ".use-ai", true);
        mobs.set("mobs." + mobId + ".silent", false);
        mobs.set("mobs." + mobId + ".hide-base-entity", false);
        mobs.set("mobs." + mobId + ".drop-table", dropTableId);
        mobs.set("mobs." + mobId + ".model-id", modelId);
        mobs.set("mobs." + mobId + ".skills", List.of(skillId));

        drops.set("drop-tables." + dropTableId + ".entries", List.of());

        skills.set("skills." + skillId + ".triggers", List.of("mob_spawn", "mob_hit"));
        skills.set("skills." + skillId + ".targeter", "self");
        skills.set("skills." + skillId + ".cooldown", 20);
        skills.set("skills." + skillId + ".actions", List.of(
            action("type", "particle", "target", "target", "particle", defaultMobParticle(entityType), "count", 12, "spread", 0.25D),
            action("type", "sound", "target", "target", "sound", "ENTITY_EXPERIENCE_ORB_PICKUP", "volume", 0.7D, "pitch", 1.1D)
        ));

        List<String> details = new ArrayList<>();
        try {
            saveYaml(itemsFile, items);
            saveYaml(modelsFile, models);
            saveYaml(mobsFile, mobs);
            saveYaml(dropsFile, drops);
            saveYaml(skillsFile, skills);
            writeTexture(eggItemId, TextureStyle.EGG, true);
            ensureItemModelJson(eggItemId, spawnEggMaterial, true, details);
        } catch (IOException ex) {
            return GenerationResult.failure("Failed to generate mob scaffold: " + ex.getMessage());
        }

        details.add("Generated mob spawn egg: " + eggItemId);
        details.add("Generated mob model: " + modelId);
        details.add("Generated mob definition: mobs." + mobId);
        details.add("Generated empty drop table: " + dropTableId);
        details.add("Generated starter skill: " + skillId);
        details.add("Assigned custom-model-data: " + customModelData);
        recordManifestEntry("mobs", mobId, details);
        return finalizeGeneration("Generated Bullseye mob scaffold " + mobId + ".", details);
    }

    public GenerationResult generateRecipe(String rawResultId, String rawType, boolean overwrite) {
        String resultId = sanitizeId(rawResultId);
        if (resultId.isBlank()) {
            return GenerationResult.failure("Recipe result id cannot be blank.");
        }
        if (!itemExists(resultId)) {
            return GenerationResult.failure("Unknown Bullseye item: " + rawResultId);
        }

        String type = normalizeRecipeType(rawType);
        if (type == null) {
            return GenerationResult.failure("Recipe type must be shaped or shapeless.");
        }

        File recipesFile = addonConfigFile("recipes.yml");
        YamlConfiguration recipes = YamlConfiguration.loadConfiguration(recipesFile);
        String path = "recipes." + resultId;
        if (recipes.getConfigurationSection(path) != null && !overwrite) {
            return GenerationResult.failure("Generated recipe already exists. Use overwrite if you want to replace it.");
        }

        RecipeTemplate template = defaultRecipeTemplate(resultId, resolveBaseMaterial(resultId), type);
        recipes.set(path + ".type", template.type());
        recipes.set(path + ".result", resultId);
        recipes.set(path + ".amount", 1);
        if ("shaped".equals(template.type())) {
            recipes.set(path + ".shape", template.shape());
            recipes.set(path + ".ingredients", template.ingredientsMap());
        } else {
            recipes.set(path + ".ingredients", template.ingredientsList());
        }

        try {
            saveYaml(recipesFile, recipes);
        } catch (IOException ex) {
            return GenerationResult.failure("Failed to save generated recipe: " + ex.getMessage());
        }

        List<String> details = new ArrayList<>();
        details.add("Generated recipe: " + path);
        if ("shaped".equals(template.type())) {
            details.add("Recipe shape: " + String.join(" / ", template.shape()));
        } else {
            details.add("Recipe ingredients: " + String.join(", ", template.ingredientsList()));
        }
        recordManifestEntry("recipes", resultId, details);
        return finalizeGeneration("Generated Bullseye recipe scaffold for " + resultId + ".", details);
    }

    public GenerationResult generateMenu(String rawMenuId, String rawSize, String rawTitle, boolean overwrite) {
        String menuId = sanitizeId(rawMenuId);
        if (menuId.isBlank()) {
            return GenerationResult.failure("Menu id cannot be blank.");
        }

        int size = parseMenuSize(rawSize);
        if (size <= 0) {
            return GenerationResult.failure("Menu size must be a positive multiple of 9 up to 54.");
        }

        File menusFile = addonConfigFile("menus.yml");
        File mechanicsFile = addonConfigFile("mechanics.yml");
        YamlConfiguration menus = YamlConfiguration.loadConfiguration(menusFile);
        YamlConfiguration mechanics = YamlConfiguration.loadConfiguration(mechanicsFile);
        String path = "menus." + menuId;
        if (menus.getConfigurationSection(path) != null && !overwrite) {
            return GenerationResult.failure("Generated menu already exists. Use overwrite if you want to replace it.");
        }

        String title = rawTitle == null || rawTitle.isBlank() ? "&8" + prettify(menuId) : rawTitle.trim();
        String closeMechanic = menuId + "_close";
        int closeSlot = size - 5;

        menus.set(path + ".title", title);
        menus.set(path + ".size", size);
        menus.set(path + ".buttons." + closeSlot + ".material", "BARRIER");
        menus.set(path + ".buttons." + closeSlot + ".name", "&cClose");
        menus.set(path + ".buttons." + closeSlot + ".lore", List.of("&7Close this generated Bullseye menu."));
        menus.set(path + ".buttons." + closeSlot + ".mechanics", List.of(closeMechanic));

        mechanics.set("mechanics." + closeMechanic + ".menu_click", List.of(
            action("type", "sound", "sound", "UI_BUTTON_CLICK", "volume", 0.9D, "pitch", 0.9D),
            action("type", "close_menu")
        ));

        try {
            saveYaml(menusFile, menus);
            saveYaml(mechanicsFile, mechanics);
        } catch (IOException ex) {
            return GenerationResult.failure("Failed to save generated menu scaffold: " + ex.getMessage());
        }

        List<String> details = List.of(
            "Generated menu: " + path,
            "Menu size: " + size,
            "Generated close button at slot " + closeSlot,
            "Generated menu-close mechanic: " + closeMechanic
        );
        recordManifestEntry("menus", menuId, details);
        return finalizeGeneration("Generated Bullseye menu scaffold " + menuId + ".", details);
    }

    public GenerationResult generateButton(
        String rawMenuId,
        String rawSlot,
        String rawButtonToken,
        String rawActionType,
        String rawTarget,
        boolean overwrite
    ) {
        String menuId = sanitizeId(rawMenuId);
        if (menuId.isBlank()) {
            return GenerationResult.failure("Menu id cannot be blank.");
        }

        int slot;
        try {
            slot = Integer.parseInt(rawSlot);
        } catch (NumberFormatException ex) {
            return GenerationResult.failure("Button slot must be a number.");
        }
        if (slot < 0 || slot > 53) {
            return GenerationResult.failure("Button slot must be between 0 and 53.");
        }

        File menusFile = addonConfigFile("menus.yml");
        File mechanicsFile = addonConfigFile("mechanics.yml");
        YamlConfiguration menus = YamlConfiguration.loadConfiguration(menusFile);
        YamlConfiguration mechanics = YamlConfiguration.loadConfiguration(mechanicsFile);
        if (menus.getConfigurationSection("menus." + menuId) == null) {
            return GenerationResult.failure("Generated menu not found: " + menuId + ". Create it first with /bullseye generate menu.");
        }

        String actionType = normalizeButtonAction(rawActionType);
        if (actionType == null) {
            return GenerationResult.failure("Button action must be give, open_menu, open_browser, or close.");
        }

        String buttonItemId = sanitizeId(rawButtonToken);
        Material buttonMaterial = parseMaterial(rawButtonToken);
        if (!itemExists(buttonItemId) && buttonMaterial == null) {
            return GenerationResult.failure("Button icon must be a Bullseye item id or a valid material.");
        }

        String buttonPath = "menus." + menuId + ".buttons." + slot;
        if (menus.getConfigurationSection(buttonPath) != null && !overwrite) {
            return GenerationResult.failure("A generated button already exists in that slot. Use overwrite if you want to replace it.");
        }

        String mechanicId = menuId + "_button_" + slot;
        List<java.util.Map<String, Object>> actions = new ArrayList<>();
        switch (actionType) {
            case "give" -> {
                String targetItem = rawTarget == null || rawTarget.isBlank() ? buttonItemId : sanitizeId(rawTarget);
                if (!itemExists(targetItem)) {
                    return GenerationResult.failure("Give-button target must be an existing Bullseye item id.");
                }
                actions.add(action("type", "give_item", "item", targetItem, "amount", 1));
                actions.add(action("type", "sound", "sound", "ENTITY_ITEM_PICKUP", "volume", 1.0D, "pitch", 1.1D));
                menus.set(buttonPath + ".name", itemExists(buttonItemId) ? null : "&6" + prettify(targetItem));
                menus.set(buttonPath + ".lore", List.of("&7Click to receive &f" + prettify(targetItem) + "&7."));
            }
            case "open_menu" -> {
                String targetMenu = sanitizeId(rawTarget);
                if (targetMenu.isBlank()) {
                    return GenerationResult.failure("open_menu buttons require a target menu id.");
                }
                actions.add(action("type", "open_menu", "menu", targetMenu));
                actions.add(action("type", "sound", "sound", "UI_BUTTON_CLICK", "volume", 1.0D, "pitch", 1.1D));
                menus.set(buttonPath + ".name", itemExists(buttonItemId) ? null : "&6" + prettify(targetMenu));
                menus.set(buttonPath + ".lore", List.of("&7Open menu &f" + targetMenu + "&7."));
            }
            case "open_browser" -> {
                String browser = sanitizeId(rawTarget);
                if (browser.isBlank()) {
                    return GenerationResult.failure("open_browser buttons require a target browser id.");
                }
                actions.add(action("type", "open_browser", "browser", browser, "page", 0));
                actions.add(action("type", "sound", "sound", "UI_BUTTON_CLICK", "volume", 1.0D, "pitch", 1.1D));
                menus.set(buttonPath + ".name", itemExists(buttonItemId) ? null : "&6" + prettify(browser));
                menus.set(buttonPath + ".lore", List.of("&7Open the &f" + browser + " &7browser."));
            }
            case "close" -> {
                actions.add(action("type", "close_menu"));
                actions.add(action("type", "sound", "sound", "UI_BUTTON_CLICK", "volume", 0.9D, "pitch", 0.9D));
                menus.set(buttonPath + ".name", itemExists(buttonItemId) ? null : "&cClose");
                menus.set(buttonPath + ".lore", List.of("&7Close this menu."));
            }
            default -> {
                return GenerationResult.failure("Unsupported button action: " + actionType);
            }
        }

        if (itemExists(buttonItemId)) {
            menus.set(buttonPath + ".item", buttonItemId);
            menus.set(buttonPath + ".material", null);
        } else {
            menus.set(buttonPath + ".item", null);
            menus.set(buttonPath + ".material", buttonMaterial.name());
        }
        menus.set(buttonPath + ".mechanics", List.of(mechanicId));
        mechanics.set("mechanics." + mechanicId + ".menu_click", actions);

        try {
            saveYaml(menusFile, menus);
            saveYaml(mechanicsFile, mechanics);
        } catch (IOException ex) {
            return GenerationResult.failure("Failed to save generated button scaffold: " + ex.getMessage());
        }

        List<String> details = List.of(
            "Generated button in menu " + menuId + " at slot " + slot,
            "Button action: " + actionType,
            "Generated button mechanic: " + mechanicId
        );
        recordManifestEntry("buttons", menuId + "_" + slot, details);
        return finalizeGeneration("Generated Bullseye button scaffold for menu " + menuId + " slot " + slot + ".", details);
    }

    public GenerationResult importFolder(String rawFolder, String rawMode, String rawHint, boolean overwrite) {
        String mode = rawMode == null ? "" : rawMode.trim().toLowerCase(Locale.ROOT);
        Path source = resolveImportPath(rawFolder);
        if (source == null || !Files.exists(source)) {
            return GenerationResult.failure("Import path not found: " + rawFolder);
        }

        return switch (mode) {
            case "names" -> importNames(source, rawHint, overwrite);
            case "textures" -> importTextures(source, rawHint, overwrite);
            case "bbmodels" -> importBbModels(source, rawHint, overwrite);
            default -> GenerationResult.failure("Import mode must be names, textures, or bbmodels.");
        };
    }

    public GenerationResult generateBrowser(String rawType, String rawPrefix, boolean overwrite) {
        String type = rawType == null ? "" : rawType.trim().toLowerCase(Locale.ROOT);
        BrowserSnapshotLayout layout = browserLayout(type);
        if (layout == null) {
            return GenerationResult.failure("Browser type must be items or mobs.");
        }

        List<String> entries = switch (type) {
            case "items" -> plugin.getBootstrap().getItemService().getItemIds().stream().sorted().toList();
            case "mobs" -> plugin.getBootstrap().getMobService().getMobIds().stream().sorted().toList();
            default -> List.of();
        };
        if (entries.isEmpty()) {
            return GenerationResult.failure("No Bullseye " + type + " are loaded to generate a browser.");
        }

        String prefix = sanitizeId(rawPrefix);
        if (prefix.isBlank()) {
            prefix = "generated_" + type;
        }

        File menusFile = addonConfigFile("menus.yml");
        File mechanicsFile = addonConfigFile("mechanics.yml");
        YamlConfiguration menus = YamlConfiguration.loadConfiguration(menusFile);
        YamlConfiguration mechanics = YamlConfiguration.loadConfiguration(mechanicsFile);

        if (!overwrite && hasGeneratedBrowser(menus, prefix)) {
            return GenerationResult.failure("Generated browser pages already exist for prefix " + prefix + ". Use overwrite if you want to replace them.");
        }

        clearGeneratedBrowser(menus, mechanics, prefix);

        int pageSize = layout.contentSlots().size();
        int pageCount = Math.max(1, (entries.size() + pageSize - 1) / pageSize);
        for (int page = 0; page < pageCount; page++) {
            String menuId = prefix + "_page_" + (page + 1);
            String menuPath = "menus." + menuId;
            menus.set(menuPath + ".title", layout.title());
            menus.set(menuPath + ".size", layout.size());

            int offset = page * pageSize;
            for (int index = 0; index < pageSize; index++) {
                int entryIndex = offset + index;
                if (entryIndex >= entries.size()) {
                    break;
                }

                String contentId = entries.get(entryIndex);
                int slot = layout.contentSlots().get(index);
                String buttonPath = menuPath + ".buttons." + slot;
                String mechanicId = menuId + "_slot_" + slot;
                menus.set(buttonPath + ".mechanics", List.of(mechanicId));

                if ("items".equals(type)) {
                    menus.set(buttonPath + ".item", contentId);
                    mechanics.set("mechanics." + mechanicId + ".menu_click", List.of(
                        action("type", "give_item", "item", contentId, "amount", 1),
                        action("type", "sound", "sound", "ENTITY_ITEM_PICKUP", "volume", 1.0D, "pitch", 1.1D)
                    ));
                } else {
                    String iconItemId = mobBrowserIconItem(contentId);
                    if (iconItemId != null && !iconItemId.isBlank()) {
                        menus.set(buttonPath + ".item", iconItemId);
                    } else {
                        menus.set(buttonPath + ".material", "DRAGON_HEAD");
                    }
                    menus.set(buttonPath + ".name", mobBrowserDisplayName(contentId));
                    menus.set(buttonPath + ".lore", List.of("&7Generated Bullseye mob browser entry.", "&7ID: &f" + contentId));
                    mechanics.set("mechanics." + mechanicId + ".menu_click", List.of(
                        mobBrowserAction(contentId),
                        action("type", "sound", "sound", "UI_BUTTON_CLICK", "volume", 1.0D, "pitch", 1.1D)
                    ));
                }
            }

            String previousMenu = page > 0 ? prefix + "_page_" + page : "";
            String nextMenu = page + 1 < pageCount ? prefix + "_page_" + (page + 2) : "";

            writeBrowserControl(menus, mechanics, menuPath, menuId + "_prev", layout.previousSlot(), "ARROW", page > 0 ? "&6Previous Page" : "&8Previous Page",
                page > 0 ? List.of(action("type", "open_menu", "menu", previousMenu)) : List.of());
            writeBrowserControl(menus, mechanics, menuPath, menuId + "_next", layout.nextSlot(), "ARROW", page + 1 < pageCount ? "&6Next Page" : "&8Next Page",
                page + 1 < pageCount ? List.of(action("type", "open_menu", "menu", nextMenu)) : List.of());
            writeBrowserControl(menus, mechanics, menuPath, menuId + "_search", layout.searchSlot(), "COMPASS", "&bLive Browser",
                List.of(action("type", "open_browser", "browser", type, "page", page)));
            writeBrowserControl(menus, mechanics, menuPath, menuId + "_close", layout.closeSlot(), "BARRIER", "&cClose",
                List.of(action("type", "close_menu")));
        }

        try {
            saveYaml(menusFile, menus);
            saveYaml(mechanicsFile, mechanics);
        } catch (IOException ex) {
            return GenerationResult.failure("Failed to save generated browser menus: " + ex.getMessage());
        }

        List<String> details = List.of(
            "Generated browser type: " + type,
            "Generated page prefix: " + prefix,
            "Generated page count: " + pageCount,
            "Entries included: " + entries.size()
        );
        recordManifestEntry("browsers", prefix, details);
        return finalizeGeneration("Generated Bullseye " + type + " browser menus for prefix " + prefix + ".", details);
    }

    private GenerationResult finalizeGeneration(String message, List<String> details) {
        plugin.getBootstrap().getContentService().loadAll(false);
        plugin.getBootstrap().getPackGenerator().generatePack(false);
        List<String> output = new ArrayList<>(details);
        output.add("Generated addon root: addons/" + GENERATED_ADDON_ID);
        output.add("Generated pack root: resourcepack/addons/" + GENERATED_ADDON_ID);
        output.add("Reloaded Bullseye content.");
        output.add("Rebuilt generated resource pack.");
        return new GenerationResult(true, message, List.copyOf(output));
    }

    private void writeModelDefinition(YamlConfiguration models, String modelId, String itemId, String blueprint, double yOffset) {
        String path = "models." + modelId;
        models.set(path + ".item", itemId);
        models.set(path + ".blueprint", blueprint);
        models.set(path + ".default-animation", "idle");
        models.set(path + ".offset.x", 0.0D);
        models.set(path + ".offset.y", yOffset);
        models.set(path + ".offset.z", 0.0D);
        models.set(path + ".scale.x", 1.0D);
        models.set(path + ".scale.y", 1.0D);
        models.set(path + ".scale.z", 1.0D);
        models.set(path + ".rotation.pitch", 0.0D);
        models.set(path + ".rotation.yaw", 0.0D);
        models.set(path + ".rotation.roll", 0.0D);
        models.set(path + ".transform", "FIXED");
        models.set(path + ".billboard", "FIXED");
        models.set(path + ".view-range", 1.2D);
        models.set(path + ".shadow-radius", 0.0D);
        models.set(path + ".shadow-strength", 0.0D);
    }

    private void writeTexture(String itemId, TextureStyle style, boolean overwrite) throws IOException {
        Path texturePath = itemTexturePath(itemId);
        if (Files.exists(texturePath) && !overwrite) {
            return;
        }
        Files.createDirectories(Objects.requireNonNull(texturePath.getParent()));
        ImageIO.write(generateTextureImage(itemId, style), "png", texturePath.toFile());
    }

    private void ensureItemModelJson(String itemId, Material baseMaterial, boolean overwrite, List<String> details) throws IOException {
        Path modelPath = itemModelJsonPath(itemId);
        if (Files.exists(modelPath) && !overwrite) {
            return;
        }

        Files.createDirectories(Objects.requireNonNull(modelPath.getParent()));
        Files.writeString(modelPath, """
            {
              "parent": "%s",
              "textures": {
                "layer0": "%s:item/%s"
              }
            }
            """.formatted(itemModelParent(baseMaterial), NAMESPACE, itemId).trim(), StandardCharsets.UTF_8);
        details.add("Generated item model json: " + modelPath.getFileName());
    }

    private void recordManifestEntry(String type, String id, List<String> details) {
        File manifestFile = addonConfigFile("generator.yml");
        YamlConfiguration manifest = YamlConfiguration.loadConfiguration(manifestFile);
        String path = "generated." + type + "." + id;
        manifest.set(path + ".updated-at", Instant.now().toString());
        manifest.set(path + ".details", details);
        try {
            saveYaml(manifestFile, manifest);
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to write generator manifest entry: " + ex.getMessage());
        }
    }

    private GenerationResult importNames(Path source, String rawHint, boolean overwrite) {
        Material baseMaterial = parseMaterial(rawHint);
        if (baseMaterial == null) {
            baseMaterial = Material.PAPER;
        }

        List<String> details = new ArrayList<>();
        int imported = 0;
        int skipped = 0;

        try {
            List<Path> entries;
            if (Files.isDirectory(source)) {
                try (Stream<Path> stream = Files.list(source)) {
                    entries = stream.toList();
                }
            } else {
                entries = List.of(source);
            }

            File itemsFile = addonConfigFile("items.yml");
            File modelsFile = addonConfigFile("models.yml");
            YamlConfiguration items = YamlConfiguration.loadConfiguration(itemsFile);
            YamlConfiguration models = YamlConfiguration.loadConfiguration(modelsFile);

            for (Path entry : entries) {
                String id = sourceNameId(entry);
                if (id.isBlank()) {
                    skipped++;
                    continue;
                }
                if (!overwrite && (items.getConfigurationSection("items." + id) != null || models.getConfigurationSection("models." + id + "_model") != null)) {
                    skipped++;
                    continue;
                }

                int cmd = resolveCustomModelData(items, id, baseMaterial, "auto", items.getConfigurationSection("items." + id));
                String conflict = findCustomModelDataConflict(items, id, baseMaterial, cmd);
                if (conflict != null) {
                    skipped++;
                    continue;
                }

                items.set("items." + id + ".base", baseMaterial.name());
                items.set("items." + id + ".custom-model-data", cmd);
                items.set("items." + id + ".name", "&f" + prettify(id));
                items.set("items." + id + ".lore", defaultItemLore(baseMaterial));
                writeModelDefinition(models, id + "_model", id, id, defaultModelYOffset(id));
                writeTexture(id, TextureStyle.auto(baseMaterial), true);
                ensureItemModelJson(id, baseMaterial, true, details);
                imported++;
            }

            saveYaml(itemsFile, items);
            saveYaml(modelsFile, models);
        } catch (IOException ex) {
            return GenerationResult.failure("Failed importing names: " + ex.getMessage());
        }

        details.add("Imported name entries: " + imported);
        details.add("Skipped entries: " + skipped);
        details.add("Import mode: names");
        recordManifestEntry("imports", "names_" + source.getFileName(), details);
        return finalizeGeneration("Imported Bullseye name scaffolds from " + source + ".", details);
    }

    private GenerationResult importTextures(Path source, String rawHint, boolean overwrite) {
        Material baseMaterial = parseMaterial(rawHint);
        if (baseMaterial == null) {
            baseMaterial = Material.PAPER;
        }

        List<String> details = new ArrayList<>();
        int imported = 0;
        int skipped = 0;

        try (Stream<Path> stream = Files.walk(source)) {
            List<Path> textureFiles = stream
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                .toList();

            File itemsFile = addonConfigFile("items.yml");
            File modelsFile = addonConfigFile("models.yml");
            YamlConfiguration items = YamlConfiguration.loadConfiguration(itemsFile);
            YamlConfiguration models = YamlConfiguration.loadConfiguration(modelsFile);

            for (Path texture : textureFiles) {
                String id = stripExtension(texture.getFileName().toString());
                id = sanitizeId(id);
                if (id.isBlank()) {
                    skipped++;
                    continue;
                }
                if (!overwrite && (items.getConfigurationSection("items." + id) != null || Files.exists(itemTexturePath(id)))) {
                    skipped++;
                    continue;
                }

                int cmd = resolveCustomModelData(items, id, baseMaterial, "auto", items.getConfigurationSection("items." + id));
                String conflict = findCustomModelDataConflict(items, id, baseMaterial, cmd);
                if (conflict != null) {
                    skipped++;
                    continue;
                }

                items.set("items." + id + ".base", baseMaterial.name());
                items.set("items." + id + ".custom-model-data", cmd);
                items.set("items." + id + ".name", "&f" + prettify(id));
                items.set("items." + id + ".lore", defaultItemLore(baseMaterial));
                writeModelDefinition(models, id + "_model", id, id, defaultModelYOffset(id));

                Files.createDirectories(Objects.requireNonNull(itemTexturePath(id).getParent()));
                Files.copy(texture, itemTexturePath(id), StandardCopyOption.REPLACE_EXISTING);
                ensureItemModelJson(id, baseMaterial, true, details);
                imported++;
            }

            saveYaml(itemsFile, items);
            saveYaml(modelsFile, models);
        } catch (IOException ex) {
            return GenerationResult.failure("Failed importing textures: " + ex.getMessage());
        }

        details.add("Imported texture assets: " + imported);
        details.add("Skipped assets: " + skipped);
        details.add("Import mode: textures");
        recordManifestEntry("imports", "textures_" + source.getFileName(), details);
        return finalizeGeneration("Imported Bullseye textures from " + source + ".", details);
    }

    private GenerationResult importBbModels(Path source, String rawHint, boolean overwrite) {
        Material baseMaterial = parseMaterial(rawHint);
        if (baseMaterial == null) {
            baseMaterial = plugin.getBootstrap().getModelEngineService().getItemModel();
        }

        List<String> details = new ArrayList<>();
        int imported = 0;
        int skipped = 0;

        try (Stream<Path> stream = Files.walk(source)) {
            List<Path> bbmodelFiles = stream
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".bbmodel"))
                .toList();

            File itemsFile = addonConfigFile("items.yml");
            File modelsFile = addonConfigFile("models.yml");
            YamlConfiguration items = YamlConfiguration.loadConfiguration(itemsFile);
            YamlConfiguration models = YamlConfiguration.loadConfiguration(modelsFile);

            for (Path bbmodel : bbmodelFiles) {
                String blueprintId = sanitizeId(stripExtension(bbmodel.getFileName().toString()));
                if (blueprintId.isBlank()) {
                    skipped++;
                    continue;
                }
                String itemId = blueprintId;
                String modelId = blueprintId + "_model";
                if (!overwrite && (items.getConfigurationSection("items." + itemId) != null || models.getConfigurationSection("models." + modelId) != null)) {
                    skipped++;
                    continue;
                }

                int cmd = resolveCustomModelData(items, itemId, baseMaterial, "auto", items.getConfigurationSection("items." + itemId));
                String conflict = findCustomModelDataConflict(items, itemId, baseMaterial, cmd);
                if (conflict != null) {
                    skipped++;
                    continue;
                }

                items.set("items." + itemId + ".base", baseMaterial.name());
                items.set("items." + itemId + ".custom-model-data", cmd);
                items.set("items." + itemId + ".name", "&f" + prettify(itemId));
                items.set("items." + itemId + ".lore", List.of("&7Imported from BBModel blueprint.", "&7Blueprint: &f" + blueprintId));
                writeModelDefinition(models, modelId, itemId, blueprintId, defaultModelYOffset(itemId));

                List<Path> siblingTextures = discoverSiblingTextures(bbmodel);
                Path primaryTexture = selectPrimaryTexture(blueprintId, siblingTextures);
                if (primaryTexture != null) {
                    Files.createDirectories(Objects.requireNonNull(itemTexturePath(itemId).getParent()));
                    Files.copy(primaryTexture, itemTexturePath(itemId), StandardCopyOption.REPLACE_EXISTING);
                } else {
                    writeTexture(itemId, TextureStyle.auto(baseMaterial), true);
                }

                for (Path texture : siblingTextures) {
                    Path blueprintTarget = blueprintTexturePath(blueprintId).resolve(texture.getFileName().toString());
                    Files.createDirectories(Objects.requireNonNull(blueprintTarget.getParent()));
                    Files.copy(texture, blueprintTarget, StandardCopyOption.REPLACE_EXISTING);
                }

                Path blueprintDump = generatedBlueprintPath(blueprintId);
                Files.createDirectories(Objects.requireNonNull(blueprintDump.getParent()));
                Files.copy(bbmodel, blueprintDump, StandardCopyOption.REPLACE_EXISTING);

                ensureItemModelJson(itemId, baseMaterial, true, details);
                imported++;
            }

            saveYaml(itemsFile, items);
            saveYaml(modelsFile, models);
        } catch (IOException ex) {
            return GenerationResult.failure("Failed importing BBModels: " + ex.getMessage());
        }

        details.add("Imported BBModels: " + imported);
        details.add("Skipped blueprints: " + skipped);
        details.add("Import mode: bbmodels");
        recordManifestEntry("imports", "bbmodels_" + source.getFileName(), details);
        return finalizeGeneration("Imported Bullseye BBModels from " + source + ".", details);
    }

    private String normalizeRecipeType(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return "shaped";
        }
        String normalized = rawType.trim().toLowerCase(Locale.ROOT);
        return "shaped".equals(normalized) || "shapeless".equals(normalized) ? normalized : null;
    }

    private BrowserSnapshotLayout browserLayout(String rawType) {
        return switch (rawType) {
            case "items" -> new BrowserSnapshotLayout("\uE901", 54, List.of(10, 11, 12, 13, 14, 15, 19, 20, 21, 22, 23, 24, 28, 29, 30, 31, 32, 33), 38, 40, 42, 49);
            case "mobs" -> new BrowserSnapshotLayout("\uE902", 54, List.of(10, 11, 12, 13, 14, 15, 19, 20, 21, 22, 23, 24, 28, 29, 30, 31, 32, 33, 37, 38, 39, 40, 41, 42), 46, 48, 50, 49);
            default -> null;
        };
    }

    private boolean hasGeneratedBrowser(YamlConfiguration menus, String prefix) {
        ConfigurationSection root = menus.getConfigurationSection("menus");
        if (root == null) {
            return false;
        }
        for (String menuId : root.getKeys(false)) {
            if (menuId.startsWith(prefix + "_page_")) {
                return true;
            }
        }
        return false;
    }

    private void clearGeneratedBrowser(YamlConfiguration menus, YamlConfiguration mechanics, String prefix) {
        ConfigurationSection menuRoot = menus.getConfigurationSection("menus");
        if (menuRoot != null) {
            for (String menuId : new ArrayList<>(menuRoot.getKeys(false))) {
                if (menuId.startsWith(prefix + "_page_")) {
                    menus.set("menus." + menuId, null);
                }
            }
        }
        ConfigurationSection mechanicRoot = mechanics.getConfigurationSection("mechanics");
        if (mechanicRoot != null) {
            for (String mechanicId : new ArrayList<>(mechanicRoot.getKeys(false))) {
                if (mechanicId.startsWith(prefix + "_page_")) {
                    mechanics.set("mechanics." + mechanicId, null);
                }
            }
        }
    }

    private void writeBrowserControl(
        YamlConfiguration menus,
        YamlConfiguration mechanics,
        String menuPath,
        String mechanicId,
        int slot,
        String material,
        String name,
        List<java.util.Map<String, Object>> actions
    ) {
        String buttonPath = menuPath + ".buttons." + slot;
        menus.set(buttonPath + ".item", null);
        menus.set(buttonPath + ".material", material);
        menus.set(buttonPath + ".name", name);
        menus.set(buttonPath + ".lore", List.of());
        if (actions.isEmpty()) {
            menus.set(buttonPath + ".mechanics", List.of());
            mechanics.set("mechanics." + mechanicId, null);
            return;
        }
        menus.set(buttonPath + ".mechanics", List.of(mechanicId));
        mechanics.set("mechanics." + mechanicId + ".menu_click", actions);
    }

    private String mobBrowserIconItem(String mobId) {
        var mob = plugin.getBootstrap().getMobService().getDefinition(mobId);
        if (mob == null) {
            return "";
        }
        if (mob.spawnEggItemId() != null && !mob.spawnEggItemId().isBlank()) {
            return mob.spawnEggItemId();
        }
        if (mob.modelId() != null && !mob.modelId().isBlank()) {
            var model = plugin.getBootstrap().getModelEngineService().getDefinition(mob.modelId());
            if (model != null && model.itemId() != null && !model.itemId().isBlank()) {
                return model.itemId();
            }
        }
        return "";
    }

    private String mobBrowserDisplayName(String mobId) {
        var mob = plugin.getBootstrap().getMobService().getDefinition(mobId);
        return mob == null ? "&f" + prettify(mobId) : mob.displayName();
    }

    private java.util.Map<String, Object> mobBrowserAction(String mobId) {
        var mob = plugin.getBootstrap().getMobService().getDefinition(mobId);
        if (mob != null && mob.spawnEggItemId() != null && !mob.spawnEggItemId().isBlank()) {
            return action("type", "give_item", "item", mob.spawnEggItemId(), "amount", 1);
        }
        return action("type", "spawn_mob", "mob", mobId);
    }

    private RecipeTemplate defaultRecipeTemplate(String resultId, Material baseMaterial, String type) {
        String materialKey = recipeIngredientFor(baseMaterial);
        if ("shapeless".equals(type)) {
            return new RecipeTemplate("shapeless", List.of(), java.util.Map.of(), List.of(materialKey, "PAPER", "STICK"));
        }

        String name = baseMaterial.name().toLowerCase(Locale.ROOT);
        if (name.endsWith("_sword")) {
            return new RecipeTemplate("shaped", List.of(" M ", " M ", " S "), java.util.Map.of("M", materialKey, "S", "STICK"), List.of());
        }
        if (name.endsWith("_pickaxe")) {
            return new RecipeTemplate("shaped", List.of("MMM", " S ", " S "), java.util.Map.of("M", materialKey, "S", "STICK"), List.of());
        }
        if (name.endsWith("_axe")) {
            return new RecipeTemplate("shaped", List.of("MM ", "MS ", " S "), java.util.Map.of("M", materialKey, "S", "STICK"), List.of());
        }
        if (name.endsWith("_shovel")) {
            return new RecipeTemplate("shaped", List.of(" M ", " S ", " S "), java.util.Map.of("M", materialKey, "S", "STICK"), List.of());
        }
        if (name.endsWith("_hoe")) {
            return new RecipeTemplate("shaped", List.of("MM ", " S ", " S "), java.util.Map.of("M", materialKey, "S", "STICK"), List.of());
        }
        if (name.endsWith("_spawn_egg")) {
            return new RecipeTemplate("shaped", List.of(" P ", " E ", " G "), java.util.Map.of("P", "PAPER", "E", "EGG", "G", "GLOW_INK_SAC"), List.of());
        }
        if (baseMaterial == Material.NOTE_BLOCK || baseMaterial.name().endsWith("_BLOCK")) {
            return new RecipeTemplate("shaped", List.of("MM", "MM"), java.util.Map.of("M", materialKey), List.of());
        }
        if (baseMaterial == Material.ARMOR_STAND) {
            return new RecipeTemplate("shaped", List.of(" S ", "SWS", " S "), java.util.Map.of("S", "STICK", "W", "OAK_PLANKS"), List.of());
        }
        return new RecipeTemplate("shaped", List.of(" M ", " P ", " S "), java.util.Map.of("M", materialKey, "P", "PAPER", "S", "STICK"), List.of());
    }

    private String recipeIngredientFor(Material baseMaterial) {
        return switch (baseMaterial) {
            case IRON_SWORD, IRON_PICKAXE, IRON_AXE, IRON_SHOVEL, IRON_HOE -> "IRON_INGOT";
            case GOLDEN_SWORD, GOLDEN_PICKAXE, GOLDEN_AXE, GOLDEN_SHOVEL, GOLDEN_HOE -> "GOLD_INGOT";
            case DIAMOND_SWORD, DIAMOND_PICKAXE, DIAMOND_AXE, DIAMOND_SHOVEL, DIAMOND_HOE -> "DIAMOND";
            case NETHERITE_SWORD, NETHERITE_PICKAXE, NETHERITE_AXE, NETHERITE_SHOVEL, NETHERITE_HOE -> "NETHERITE_INGOT";
            case STONE_SWORD, STONE_PICKAXE, STONE_AXE, STONE_SHOVEL, STONE_HOE -> "COBBLESTONE";
            case WOODEN_SWORD, WOODEN_PICKAXE, WOODEN_AXE, WOODEN_SHOVEL, WOODEN_HOE -> "OAK_PLANKS";
            case NOTE_BLOCK -> "STONE";
            case ARMOR_STAND -> "OAK_PLANKS";
            default -> {
                String name = baseMaterial.name();
                if (name.endsWith("_SPAWN_EGG")) {
                    yield "EGG";
                }
                yield name;
            }
        };
    }

    private int parseMenuSize(String rawSize) {
        if (rawSize == null || rawSize.isBlank()) {
            return 54;
        }
        try {
            int size = Integer.parseInt(rawSize.trim());
            if (size < 9 || size > 54 || size % 9 != 0) {
                return -1;
            }
            return size;
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private String normalizeButtonAction(String rawActionType) {
        if (rawActionType == null || rawActionType.isBlank()) {
            return "give";
        }
        String normalized = rawActionType.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "give", "open_menu", "open_browser", "close" -> normalized;
            default -> null;
        };
    }

    private boolean itemExists(String itemId) {
        String normalized = sanitizeId(itemId);
        if (normalized.isBlank()) {
            return false;
        }
        if (plugin.getBootstrap().getItemService().getDefinition(normalized) != null) {
            return true;
        }
        YamlConfiguration generatedItems = YamlConfiguration.loadConfiguration(addonConfigFile("items.yml"));
        return generatedItems.getConfigurationSection("items." + normalized) != null;
    }

    private Path resolveImportPath(String rawFolder) {
        if (rawFolder == null || rawFolder.isBlank()) {
            return null;
        }
        Path path = Path.of(rawFolder.trim());
        if (path.isAbsolute()) {
            return path.normalize();
        }
        Path dataRelative = plugin.getDataFolder().toPath().resolve(rawFolder.trim()).normalize();
        if (Files.exists(dataRelative)) {
            return dataRelative;
        }
        return Path.of(rawFolder.trim()).toAbsolutePath().normalize();
    }

    private String sourceNameId(Path path) {
        if (path == null) {
            return "";
        }
        String fileName = path.getFileName().toString();
        return sanitizeId(stripExtension(fileName));
    }

    private String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot <= 0 ? fileName : fileName.substring(0, dot);
    }

    private List<Path> discoverSiblingTextures(Path bbmodelFile) throws IOException {
        Path parent = bbmodelFile.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(parent)) {
            return stream
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                .sorted()
                .toList();
        }
    }

    private Path selectPrimaryTexture(String blueprintId, List<Path> textures) {
        for (Path texture : textures) {
            if (sanitizeId(stripExtension(texture.getFileName().toString())).equals(blueprintId)) {
                return texture;
            }
        }
        return textures.isEmpty() ? null : textures.getFirst();
    }

    private Path blueprintTexturePath(String blueprintId) {
        return generatedPackNamespaceRoot().resolve("textures").resolve("blueprints").resolve(blueprintId);
    }

    private Path generatedBlueprintPath(String blueprintId) {
        return addonConfigRoot().resolve("blueprints").resolve(blueprintId + ".bbmodel");
    }

    private void saveYaml(File file, YamlConfiguration yaml) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create directory " + parent.getPath());
        }
        yaml.save(file);
    }

    private BufferedImage generateTextureImage(String id, TextureStyle style) {
        BufferedImage image = new BufferedImage(TEXTURE_SIZE, TEXTURE_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        graphics.setColor(new Color(0, 0, 0, 0));
        graphics.fillRect(0, 0, TEXTURE_SIZE, TEXTURE_SIZE);

        Palette palette = paletteFor(id);
        switch (style) {
            case BLADE -> drawBlade(graphics, palette);
            case BLOCK -> drawBlock(graphics, palette);
            case EGG -> drawEgg(graphics, palette);
            case ORB -> drawOrb(graphics, palette);
            case SCROLL -> drawScroll(graphics, palette);
            default -> drawGem(graphics, palette);
        }

        graphics.dispose();
        return image;
    }

    private void drawGem(Graphics2D graphics, Palette palette) {
        graphics.setColor(palette.shadow());
        graphics.fillPolygon(new Polygon(new int[] {16, 8, 12, 20, 24}, new int[] {3, 11, 27, 27, 11}, 5));
        graphics.setColor(palette.primary());
        graphics.fillPolygon(new Polygon(new int[] {16, 9, 13, 19, 23}, new int[] {4, 11, 25, 25, 11}, 5));
        graphics.setColor(palette.highlight());
        graphics.fillPolygon(new Polygon(new int[] {16, 11, 14, 18, 21}, new int[] {6, 11, 18, 18, 11}, 5));
        graphics.setColor(palette.outline());
        graphics.drawPolygon(new Polygon(new int[] {16, 9, 13, 19, 23}, new int[] {4, 11, 25, 25, 11}, 5));
    }

    private void drawBlade(Graphics2D graphics, Palette palette) {
        graphics.setColor(palette.shadow());
        graphics.fillRect(7, 19, 5, 8);
        graphics.fillRect(11, 21, 8, 4);
        graphics.fillPolygon(new Polygon(new int[] {12, 23, 25, 14}, new int[] {18, 7, 9, 20}, 4));
        graphics.setColor(palette.primary());
        graphics.fillPolygon(new Polygon(new int[] {13, 24, 22, 11}, new int[] {18, 7, 5, 16}, 4));
        graphics.setColor(palette.highlight());
        graphics.fillPolygon(new Polygon(new int[] {15, 22, 20, 13}, new int[] {16, 9, 8, 15}, 4));
        graphics.setColor(palette.accent());
        graphics.fillRect(9, 20, 10, 3);
        graphics.fillRect(8, 22, 3, 7);
        graphics.setColor(palette.outline());
        graphics.drawPolygon(new Polygon(new int[] {13, 24, 22, 11}, new int[] {18, 7, 5, 16}, 4));
        graphics.drawRect(8, 22, 2, 6);
    }

    private void drawEgg(Graphics2D graphics, Palette palette) {
        graphics.setColor(palette.shadow());
        graphics.fillOval(7, 4, 18, 24);
        graphics.setColor(palette.primary());
        graphics.fillOval(8, 4, 16, 23);
        graphics.setColor(palette.highlight());
        graphics.fillOval(11, 7, 6, 6);
        graphics.setColor(palette.accent());
        graphics.fillOval(17, 12, 4, 4);
        graphics.fillOval(12, 18, 5, 4);
        graphics.fillOval(18, 20, 3, 3);
        graphics.setColor(palette.outline());
        graphics.drawOval(8, 4, 16, 23);
    }

    private void drawOrb(Graphics2D graphics, Palette palette) {
        graphics.setColor(palette.shadow());
        graphics.fillOval(6, 6, 20, 20);
        graphics.setColor(palette.primary());
        graphics.fillOval(7, 6, 18, 18);
        graphics.setColor(palette.highlight());
        graphics.fillOval(10, 9, 7, 7);
        graphics.setColor(palette.accent());
        graphics.fillOval(16, 16, 5, 5);
        graphics.setColor(palette.outline());
        graphics.drawOval(7, 6, 18, 18);
    }

    private void drawScroll(Graphics2D graphics, Palette palette) {
        graphics.setColor(palette.shadow());
        graphics.fillRoundRect(6, 7, 20, 18, 4, 4);
        graphics.setColor(palette.primary());
        graphics.fillRoundRect(7, 6, 18, 18, 4, 4);
        graphics.setColor(palette.accent());
        graphics.fillRect(10, 11, 12, 2);
        graphics.fillRect(10, 15, 10, 2);
        graphics.fillRect(10, 19, 8, 2);
        graphics.setColor(palette.highlight());
        graphics.fillRect(7, 8, 18, 3);
        graphics.setColor(palette.outline());
        graphics.drawRoundRect(7, 6, 18, 18, 4, 4);
    }

    private void drawBlock(Graphics2D graphics, Palette palette) {
        graphics.setColor(palette.shadow());
        graphics.fillRect(6, 6, 20, 20);
        graphics.setColor(palette.primary());
        graphics.fillRect(7, 7, 18, 18);
        graphics.setColor(palette.highlight());
        graphics.fillRect(7, 7, 18, 4);
        graphics.fillRect(7, 11, 4, 14);
        graphics.setColor(palette.accent());
        graphics.fillRect(13, 13, 6, 3);
        graphics.fillRect(17, 18, 5, 2);
        graphics.setColor(palette.outline());
        graphics.drawRect(7, 7, 17, 17);
    }

    private Palette paletteFor(String id) {
        int hash = Math.abs(id.hashCode());
        float hue = (hash % 360) / 360.0F;
        Color primary = Color.getHSBColor(hue, 0.65F, 0.92F);
        Color shadow = Color.getHSBColor(hue, 0.72F, 0.45F);
        Color highlight = Color.getHSBColor((hue + 0.02F) % 1.0F, 0.35F, 1.0F);
        Color accent = Color.getHSBColor((hue + 0.55F) % 1.0F, 0.55F, 0.95F);
        Color outline = new Color(38, 24, 20, 255);
        return new Palette(primary, shadow, highlight, accent, outline);
    }

    private List<String> defaultItemLore(Material baseMaterial) {
        if (baseMaterial.name().endsWith("_SPAWN_EGG")) {
            return List.of("&7Generated Bullseye spawn egg.");
        }
        return List.of("&7Generated by Bullseye.");
    }

    private Material resolveBaseMaterial(String itemId) {
        CustomItemDefinition liveDefinition = plugin.getBootstrap().getItemService().getDefinition(itemId);
        if (liveDefinition != null) {
            return liveDefinition.baseMaterial();
        }

        YamlConfiguration generatedItems = YamlConfiguration.loadConfiguration(addonConfigFile("items.yml"));
        String configuredBase = generatedItems.getString("items." + itemId + ".base");
        Material configured = parseMaterial(configuredBase);
        return configured == null ? Material.PAPER : configured;
    }

    private String itemModelParent(Material material) {
        String name = material.name().toLowerCase(Locale.ROOT);
        if (name.endsWith("_spawn_egg")) {
            return "minecraft:item/template_spawn_egg";
        }
        if (name.endsWith("_sword") || name.endsWith("_axe") || name.endsWith("_pickaxe") || name.endsWith("_shovel") || name.endsWith("_hoe")
            || material == Material.STICK || material == Material.BLAZE_ROD) {
            return "minecraft:item/handheld";
        }
        return "minecraft:item/generated";
    }

    private double defaultModelYOffset(String itemId) {
        return itemId.contains("egg") ? 0.25D : 0.0D;
    }

    private double defaultMobHealth(EntityType entityType) {
        return switch (entityType) {
            case IRON_GOLEM, RAVAGER, WARDEN -> 40.0D;
            case BLAZE, ENDERMAN, WITHER_SKELETON, PIGLIN_BRUTE -> 30.0D;
            case SPIDER, CAVE_SPIDER, CREEPER, ZOMBIE, SKELETON, HUSK, DROWNED -> 24.0D;
            default -> 20.0D;
        };
    }

    private double defaultMobDamage(EntityType entityType) {
        return switch (entityType) {
            case IRON_GOLEM, RAVAGER, WARDEN -> 9.0D;
            case BLAZE, ENDERMAN, WITHER_SKELETON, PIGLIN_BRUTE -> 7.0D;
            case SPIDER, CAVE_SPIDER, CREEPER, ZOMBIE, SKELETON, HUSK, DROWNED -> 5.0D;
            default -> 4.0D;
        };
    }

    private double defaultMobSpeed(EntityType entityType) {
        return switch (entityType) {
            case IRON_GOLEM, RAVAGER, WARDEN -> 0.22D;
            case BLAZE, WITHER_SKELETON -> 0.28D;
            case ENDERMAN -> 0.30D;
            case SPIDER, CAVE_SPIDER, CREEPER, ZOMBIE, SKELETON, HUSK, DROWNED -> 0.32D;
            default -> 0.26D;
        };
    }

    private String defaultMobParticle(EntityType entityType) {
        return switch (entityType) {
            case BLAZE -> "FLAME";
            case ENDERMAN -> "DRAGON_BREATH";
            case IRON_GOLEM -> "END_ROD";
            case SLIME -> "SLIME";
            default -> "CRIT";
        };
    }

    private int resolveCustomModelData(
        YamlConfiguration items,
        String id,
        Material baseMaterial,
        String rawCustomModelData,
        ConfigurationSection existing
    ) {
        String value = rawCustomModelData == null ? "auto" : rawCustomModelData.trim();
        if (value.equalsIgnoreCase("auto")) {
            if (existing != null) {
                int current = existing.getInt("custom-model-data", 0);
                if (current > 0) {
                    return current;
                }
            }
            return nextCustomModelData(items, baseMaterial);
        }

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private int nextCustomModelData(YamlConfiguration items, Material baseMaterial) {
        ConfigurationSection root = items.getConfigurationSection("items");
        int max = MIN_GENERATED_CUSTOM_MODEL_DATA - 1;
        if (root == null) {
            return MIN_GENERATED_CUSTOM_MODEL_DATA;
        }

        for (String itemId : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(itemId);
            if (section == null) {
                continue;
            }
            Material existingBase = parseMaterial(section.getString("base"));
            if (existingBase == baseMaterial) {
                max = Math.max(max, section.getInt("custom-model-data", 0));
            }
        }
        return Math.max(MIN_GENERATED_CUSTOM_MODEL_DATA, max + 1);
    }

    private String findCustomModelDataConflict(YamlConfiguration items, String id, Material baseMaterial, int customModelData) {
        ConfigurationSection root = items.getConfigurationSection("items");
        if (root == null) {
            return null;
        }

        for (String otherId : root.getKeys(false)) {
            if (otherId.equalsIgnoreCase(id)) {
                continue;
            }
            ConfigurationSection section = root.getConfigurationSection(otherId);
            if (section == null) {
                continue;
            }
            Material otherBase = parseMaterial(section.getString("base"));
            if (otherBase == baseMaterial && section.getInt("custom-model-data", 0) == customModelData) {
                return otherId;
            }
        }
        return null;
    }

    private File addonConfigFile(String name) {
        return addonConfigRoot().resolve(name).toFile();
    }

    private Path addonConfigRoot() {
        return plugin.getDataFolder().toPath().resolve("addons").resolve(GENERATED_ADDON_ID);
    }

    private Path generatedPackNamespaceRoot() {
        return plugin.getDataFolder().toPath()
            .resolve("resourcepack")
            .resolve("addons")
            .resolve(GENERATED_ADDON_ID)
            .resolve("assets")
            .resolve(NAMESPACE);
    }

    private Path itemTexturePath(String itemId) {
        return generatedPackNamespaceRoot().resolve("textures").resolve("item").resolve(itemId + ".png");
    }

    private Path itemModelJsonPath(String itemId) {
        return generatedPackNamespaceRoot().resolve("models").resolve("item").resolve(itemId + ".json");
    }

    private Material parseMaterial(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Material.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private EntityType parseEntityType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return EntityType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private Material resolveSpawnEggMaterial(EntityType entityType) {
        try {
            return Material.valueOf(entityType.name() + "_SPAWN_EGG");
        } catch (IllegalArgumentException ex) {
            return Material.ZOMBIE_SPAWN_EGG;
        }
    }

    private String sanitizeId(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().toLowerCase(Locale.ROOT)
            .replace(' ', '_')
            .replace('-', '_')
            .replaceAll("[^a-z0-9_]", "");
    }

    private String prettify(String id) {
        String[] words = sanitizeId(id).split("_");
        List<String> parts = new ArrayList<>();
        for (String word : words) {
            if (!word.isBlank()) {
                parts.add(Character.toUpperCase(word.charAt(0)) + word.substring(1));
            }
        }
        return String.join(" ", parts);
    }

    private java.util.Map<String, Object> action(Object... keyValues) {
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
        for (int index = 0; index + 1 < keyValues.length; index += 2) {
            map.put(String.valueOf(keyValues[index]), keyValues[index + 1]);
        }
        return map;
    }

    public record GenerationResult(boolean success, String message, List<String> details) {
        public static GenerationResult failure(String message) {
            return new GenerationResult(false, message, List.of());
        }
    }

    private record RecipeTemplate(String type, List<String> shape, java.util.Map<String, String> ingredientsMap, List<String> ingredientsList) {
    }

    private record BrowserSnapshotLayout(String title, int size, List<Integer> contentSlots, int previousSlot, int nextSlot, int searchSlot, int closeSlot) {
    }

    private record Palette(Color primary, Color shadow, Color highlight, Color accent, Color outline) {
    }

    public enum TextureStyle {
        AUTO,
        GEM,
        BLADE,
        EGG,
        ORB,
        SCROLL,
        BLOCK;

        public static TextureStyle parse(String raw, Material baseMaterial) {
            if (raw == null || raw.isBlank() || raw.equalsIgnoreCase("auto")) {
                return auto(baseMaterial);
            }
            try {
                return TextureStyle.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }

        private static TextureStyle auto(Material baseMaterial) {
            if (baseMaterial == null) {
                return GEM;
            }
            String name = baseMaterial.name().toLowerCase(Locale.ROOT);
            if (name.endsWith("_spawn_egg")) {
                return EGG;
            }
            if (name.endsWith("_sword") || name.endsWith("_axe") || name.endsWith("_pickaxe") || name.endsWith("_shovel") || name.endsWith("_hoe")) {
                return BLADE;
            }
            if (baseMaterial == Material.PAPER || baseMaterial == Material.BOOK || baseMaterial == Material.WRITABLE_BOOK || baseMaterial == Material.MAP) {
                return SCROLL;
            }
            if (baseMaterial == Material.NOTE_BLOCK || baseMaterial.name().endsWith("_BLOCK") || baseMaterial == Material.ARMOR_STAND) {
                return BLOCK;
            }
            if (baseMaterial == Material.ENDER_PEARL || baseMaterial == Material.SLIME_BALL || baseMaterial == Material.FIRE_CHARGE) {
                return ORB;
            }
            return GEM;
        }
    }
}
