package tsd.ttt.service;

import java.io.File;
import java.util.Arrays;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import tsd.ttt.TTT;

public class ContentService {
    private final TTT plugin;
    private final ConfigService configService;
    private final ItemService itemService;
    private final BlockService blockService;
    private final FurnitureService furnitureService;
    private final MobService mobService;
    private final DropTableService dropTableService;
    private final ModelEngineService modelEngineService;
    private final SpawnerService spawnerService;
    private final RandomSpawnService randomSpawnService;
    private final SkillService skillService;
    private final MechanicService mechanicService;
    private final MenuService menuService;
    private final GlyphService glyphService;
    private final RecipeService recipeService;
    private final CutsceneService cutsceneService;
    private final DisplayAnimationService displayAnimationService;
    private final IllusionService illusionService;
    private final RigService rigService;

    public ContentService(
        TTT plugin,
        ConfigService configService,
        ItemService itemService,
        BlockService blockService,
        FurnitureService furnitureService,
        MobService mobService,
        DropTableService dropTableService,
        ModelEngineService modelEngineService,
        SpawnerService spawnerService,
        RandomSpawnService randomSpawnService,
        SkillService skillService,
        MechanicService mechanicService,
        MenuService menuService,
        GlyphService glyphService,
        RecipeService recipeService,
        CutsceneService cutsceneService,
        DisplayAnimationService displayAnimationService,
        IllusionService illusionService,
        RigService rigService
    ) {
        this.plugin = plugin;
        this.configService = configService;
        this.itemService = itemService;
        this.blockService = blockService;
        this.furnitureService = furnitureService;
        this.mobService = mobService;
        this.dropTableService = dropTableService;
        this.modelEngineService = modelEngineService;
        this.spawnerService = spawnerService;
        this.randomSpawnService = randomSpawnService;
        this.skillService = skillService;
        this.mechanicService = mechanicService;
        this.menuService = menuService;
        this.glyphService = glyphService;
        this.recipeService = recipeService;
        this.cutsceneService = cutsceneService;
        this.displayAnimationService = displayAnimationService;
        this.illusionService = illusionService;
        this.rigService = rigService;
    }

    public void loadAll(boolean initialLoad) {
        configService.reloadMainConfig();

        YamlConfiguration itemsConfig = loadMerged("items.yml");
        YamlConfiguration blocksConfig = loadMerged("blocks.yml");
        YamlConfiguration furnitureConfig = loadMerged("furniture.yml");
        YamlConfiguration mobsConfig = loadMerged("mobs.yml");
        YamlConfiguration dropsConfig = loadMerged("drops.yml");
        YamlConfiguration modelsConfig = loadMerged("models.yml");
        YamlConfiguration spawnersConfig = loadMerged("spawners.yml");
        YamlConfiguration randomSpawnsConfig = loadMerged("randomspawns.yml");
        YamlConfiguration skillsConfig = loadMerged("skills.yml");
        YamlConfiguration mechanicsConfig = loadMerged("mechanics.yml");
        YamlConfiguration glyphsConfig = loadMerged("glyphs.yml");
        YamlConfiguration menusConfig = loadMerged("menus.yml");
        YamlConfiguration recipesConfig = loadMerged("recipes.yml");
        YamlConfiguration cutscenesConfig = loadMerged("cutscenes.yml");
        YamlConfiguration illusionsConfig = loadMerged("illusions.yml");

        // Rig part provisioning writes generated items before ItemService reads them.
        rigService.load(modelsConfig);

        itemService.load(itemsConfig);        blockService.loadDefinitions(blocksConfig);
        furnitureService.loadDefinitions(furnitureConfig);
        modelEngineService.reloadSettings();
        modelEngineService.load(modelsConfig);
        dropTableService.load(dropsConfig);
        mobService.load(mobsConfig);
        skillService.load(skillsConfig);
        mechanicService.load(mechanicsConfig);
        spawnerService.load(spawnersConfig);
        randomSpawnService.load(randomSpawnsConfig);
        menuService.load(menusConfig);
        glyphService.load(glyphsConfig);
        recipeService.load(recipesConfig);
        cutsceneService.load(cutscenesConfig);
        displayAnimationService.load(modelsConfig);
        illusionService.load(illusionsConfig);

        if (initialLoad) {
            blockService.loadPlacedBlocks();
        }

        plugin.getLogger().info("TTT content loaded.");
    }

    private YamlConfiguration loadMerged(String fileName) {
        YamlConfiguration merged = new YamlConfiguration();
        mergeInto(merged, configService.load(fileName));

        if (!plugin.getConfig().getBoolean("addons.enabled", true)) {
            return merged;
        }

        File addonRoot = new File(plugin.getDataFolder(), plugin.getConfig().getString("addons.folder", "addons"));
        if (!addonRoot.exists() || !addonRoot.isDirectory()) {
            return merged;
        }

        File[] addonDirs = addonRoot.listFiles(File::isDirectory);
        if (addonDirs == null) {
            return merged;
        }

        Arrays.sort(addonDirs);
        for (File addonDir : addonDirs) {
            File addonFile = new File(addonDir, fileName);
            if (!addonFile.exists()) {
                continue;
            }

            YamlConfiguration addonConfig = YamlConfiguration.loadConfiguration(addonFile);
            mergeInto(merged, addonConfig);
            plugin.getLogger().info("Merged addon config: " + addonDir.getName() + "/" + fileName);
        }

        return merged;
    }

    private void mergeInto(YamlConfiguration target, YamlConfiguration source) {
        for (String path : source.getKeys(true)) {
            Object value = source.get(path);
            if (value instanceof ConfigurationSection) {
                continue;
            }
            target.set(path, value);
        }
    }
}
