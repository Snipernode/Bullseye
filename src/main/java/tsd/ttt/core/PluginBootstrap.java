package tsd.ttt.core;

import java.util.Objects;
import org.bukkit.command.PluginCommand;
import tsd.ttt.TTT;
import tsd.ttt.command.TTTCommand;
import tsd.ttt.compatibilities.CompatibilitiesManager;
import tsd.ttt.configs.ConfigsManager;
import tsd.ttt.configs.SoundManager;
import tsd.ttt.converter.Converter;
import tsd.ttt.fonts.FontManager;
import tsd.ttt.listeners.TTTListener;
import tsd.ttt.pack.PackGenerator;
import tsd.ttt.pack.server.TTTPackServer;
import tsd.ttt.pack.server.SelfHostServer;
import tsd.ttt.service.BlockService;
import tsd.ttt.service.BehaviorService;
import tsd.ttt.service.BrowserService;
import tsd.ttt.service.ConfigService;
import tsd.ttt.service.ConversionService;
import tsd.ttt.service.ContentService;
import tsd.ttt.service.CutsceneService;
import tsd.ttt.service.DisplayAnimationService;
import tsd.ttt.service.DropTableService;
import tsd.ttt.service.EditorService;
import tsd.ttt.service.FurnitureService;
import tsd.ttt.service.GeneratorService;
import tsd.ttt.service.GlyphService;
import tsd.ttt.service.IllusionService;
import tsd.ttt.service.ItemService;
import tsd.ttt.service.RigService;
import tsd.ttt.service.MechanicService;
import tsd.ttt.service.MenuService;
import tsd.ttt.service.ModelEngineService;
import tsd.ttt.service.MobService;
import tsd.ttt.service.MythicMobsService;
import tsd.ttt.service.RecipeService;
import tsd.ttt.service.ResourcePackService;
import tsd.ttt.service.RandomSpawnService;
import tsd.ttt.service.SignatureService;
import tsd.ttt.service.SkillService;
import tsd.ttt.service.SpawnerService;
import tsd.ttt.utils.actions.ClickActionManager;
import tsd.ttt.utils.breaker.BreakerManager;
import tsd.ttt.utils.inventories.InventoryManager;

public class PluginBootstrap {
    private final TTT plugin;

    private Keychain keychain;
    private ConfigService configService;
    private ConfigsManager configsManager;
    private ItemService itemService;
    private BlockService blockService;
    private FurnitureService furnitureService;
    private MobService mobService;
    private DropTableService dropTableService;
    private ModelEngineService modelEngineService;
    private SpawnerService spawnerService;
    private RandomSpawnService randomSpawnService;
    private BrowserService browserService;
    private SkillService skillService;
    private EditorService editorService;
    private GeneratorService generatorService;
    private GlyphService glyphService;
    private FontManager fontManager;
    private SoundManager soundManager;
    private MenuService menuService;
    private InventoryManager inventoryManager;
    private ClickActionManager clickActionManager;
    private BreakerManager breakerManager;
    private MechanicService mechanicService;
    private RecipeService recipeService;
    private MythicMobsService mythicMobsService;
    private ResourcePackService resourcePackService;
    private PackGenerator packGenerator;
    private TTTPackServer packServer;
    private SignatureService signatureService;
    private ConversionService conversionService;
    private Converter converter;
    private CompatibilitiesManager compatibilitiesManager;
    private ContentService contentService;
    private CutsceneService cutsceneService;
    private DisplayAnimationService displayAnimationService;
    private BehaviorService behaviorService;
    private IllusionService illusionService;
    private RigService rigService;

    public PluginBootstrap(TTT plugin) {
        this.plugin = plugin;
    }

    public void start() {
        keychain = new Keychain(plugin);
        configService = new ConfigService(plugin);
        configService.ensureDefaults();
        configsManager = new ConfigsManager(plugin, configService);
        configsManager.reload();

        itemService = new ItemService(plugin, keychain);
        blockService = new BlockService(plugin, itemService);
        furnitureService = new FurnitureService(plugin, itemService, keychain);
        mobService = new MobService(plugin, itemService, keychain);
        dropTableService = new DropTableService(plugin, itemService);
        modelEngineService = new ModelEngineService(plugin);
        spawnerService = new SpawnerService(plugin);
        randomSpawnService = new RandomSpawnService(plugin);
        browserService = new BrowserService(plugin, itemService, mobService, spawnerService, modelEngineService);
        skillService = new SkillService(plugin, itemService);
        editorService = new EditorService(plugin, keychain, itemService, furnitureService, spawnerService, modelEngineService);
        generatorService = new GeneratorService(plugin);
        glyphService = new GlyphService();
        fontManager = new FontManager(configService, glyphService);
        soundManager = new SoundManager(configsManager);
        menuService = new MenuService(plugin, itemService);
        inventoryManager = new InventoryManager(menuService, configsManager);
        clickActionManager = new ClickActionManager(configsManager);
        breakerManager = new BreakerManager(plugin);
        mechanicService = new MechanicService(plugin, itemService);
        recipeService = new RecipeService(plugin, itemService);
        mythicMobsService = new MythicMobsService(plugin);
        resourcePackService = new ResourcePackService(plugin);
        resourcePackService.setItemService(itemService);
        packGenerator = new PackGenerator(plugin, resourcePackService);
        packServer = new SelfHostServer(plugin, resourcePackService);
        signatureService = new SignatureService(plugin);
        conversionService = new ConversionService(plugin);
        converter = new Converter(conversionService);
        compatibilitiesManager = new CompatibilitiesManager(plugin, conversionService);
        cutsceneService = new CutsceneService(plugin);
        displayAnimationService = new DisplayAnimationService(plugin);
        behaviorService = new BehaviorService(plugin, keychain);
        illusionService = new IllusionService(plugin);
        rigService = new RigService(plugin);

        menuService.setMechanicService(mechanicService);
        mechanicService.setMenuService(menuService);
        mechanicService.setSoundManager(soundManager);
        mechanicService.setMobService(mobService);
        mechanicService.setBrowserService(browserService);
        skillService.setMechanicService(mechanicService);
        furnitureService.setModelEngineService(modelEngineService);
        mobService.setMechanicService(mechanicService);
        mobService.setSkillService(skillService);
        mobService.setDropTableService(dropTableService);
        mobService.setModelEngineService(modelEngineService);
        spawnerService.setMobService(mobService);
        randomSpawnService.setMobService(mobService);
        mythicMobsService.setMobService(mobService);
        cutsceneService.setMechanicService(mechanicService);
        behaviorService.setMechanicService(mechanicService);

        contentService = new ContentService(
            plugin,
            configService,
            itemService,
            blockService,
            furnitureService,
            mobService,
            dropTableService,
            modelEngineService,
            spawnerService,
            randomSpawnService,
            skillService,
            mechanicService,
            menuService,
            glyphService,
            recipeService,
            cutsceneService,
            displayAnimationService,
            illusionService,
            rigService
        );

        signatureService.reloadSettings();
        if (!signatureService.validateConfiguredSignature() && signatureService.isStopOnFailure()) {
            plugin.getServer().getPluginManager().disablePlugin(plugin);
            return;
        }

        resourcePackService.reloadSettings();
        converter.reload();
        compatibilitiesManager.reload();
        conversionService.logDetectionPrompt();
        contentService.loadAll(true);
        fontManager.reload();
        soundManager.reload();
        inventoryManager.reload();
        clickActionManager.reload();
        breakerManager.reload();
        mythicMobsService.reload();
        packServer.start();

        registerCommands();
        registerListeners();

        if (plugin.getConfig().getBoolean("resource-pack.rebuild-on-startup", false)) {
            packGenerator.generatePack(false);
        }
        if (mythicMobsService.shouldAutoInjectOnStartup()) {
            mythicMobsService.inject(true);
        }
    }

    public void reload() {
        configService.ensureDefaults();
        configsManager.reload();
        signatureService.reloadSettings();
        if (!signatureService.validateConfiguredSignature() && signatureService.isStopOnFailure()) {
            plugin.getServer().getPluginManager().disablePlugin(plugin);
            return;
        }
        resourcePackService.reloadSettings();
        converter.reload();
        compatibilitiesManager.reload();
        conversionService.logDetectionPrompt();
        contentService.loadAll(false);
        fontManager.reload();
        soundManager.reload();
        inventoryManager.reload();
        clickActionManager.reload();
        breakerManager.reload();
        mythicMobsService.reload();
        packServer.start();

        if (resourcePackService.isEnabled() && plugin.getConfig().getBoolean("resource-pack.rebuild-on-reload", true)) {
            packGenerator.generatePack(false);
        }
        if (mythicMobsService.shouldAutoInjectOnReload()) {
            mythicMobsService.inject(true);
        }
    }

    public void stop() {
        blockService.savePlacedBlocks();
        recipeService.unregisterAll();
        cutsceneService.shutdown();
        displayAnimationService.shutdown();
        behaviorService.shutdown();
        mechanicService.shutdown();
        illusionService.shutdown();
        rigService.shutdown();
        packServer.stop();
        mobService.stop();
        furnitureService.stop();
        spawnerService.stop();
        randomSpawnService.stop();
    }

    private void registerCommands() {
        PluginCommand command = Objects.requireNonNull(plugin.getCommand("ttt"), "ttt command missing in plugin.yml");
        TTTCommand executor = new TTTCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    private void registerListeners() {
        plugin.getServer().getPluginManager().registerEvents(new TTTListener(this), plugin);
    }

    public Keychain getKeychain() {
        return keychain;
    }

    public ConfigsManager getConfigsManager() {
        return configsManager;
    }

    public ItemService getItemService() {
        return itemService;
    }

    public BlockService getBlockService() {
        return blockService;
    }

    public FurnitureService getFurnitureService() {
        return furnitureService;
    }

    public MobService getMobService() {
        return mobService;
    }

    public DropTableService getDropTableService() {
        return dropTableService;
    }

    public ModelEngineService getModelEngineService() {
        return modelEngineService;
    }

    public SpawnerService getSpawnerService() {
        return spawnerService;
    }

    public RandomSpawnService getRandomSpawnService() {
        return randomSpawnService;
    }

    public BrowserService getBrowserService() {
        return browserService;
    }

    public SkillService getSkillService() {
        return skillService;
    }

    public EditorService getEditorService() {
        return editorService;
    }

    public GeneratorService getGeneratorService() {
        return generatorService;
    }

    public GlyphService getGlyphService() {
        return glyphService;
    }

    public FontManager getFontManager() {
        return fontManager;
    }

    public SoundManager getSoundManager() {
        return soundManager;
    }

    public MenuService getMenuService() {
        return menuService;
    }

    public InventoryManager getInventoryManager() {
        return inventoryManager;
    }

    public ClickActionManager getClickActionManager() {
        return clickActionManager;
    }

    public BreakerManager getBreakerManager() {
        return breakerManager;
    }

    public MechanicService getMechanicService() {
        return mechanicService;
    }

    public CutsceneService getCutsceneService() {
        return cutsceneService;
    }

    public DisplayAnimationService getDisplayAnimationService() {
        return displayAnimationService;
    }

    public BehaviorService getBehaviorService() {
        return behaviorService;
    }

    public IllusionService getIllusionService() {
        return illusionService;
    }

    public RigService getRigService() {
        return rigService;
    }

    public RecipeService getRecipeService() {
        return recipeService;
    }

    public MythicMobsService getMythicMobsService() {
        return mythicMobsService;
    }

    public ResourcePackService getResourcePackService() {
        return resourcePackService;
    }

    public PackGenerator getPackGenerator() {
        return packGenerator;
    }

    public TTTPackServer getPackServer() {
        return packServer;
    }

    public SignatureService getSignatureService() {
        return signatureService;
    }

    public ConversionService getConversionService() {
        return conversionService;
    }

    public Converter getConverter() {
        return converter;
    }

    public CompatibilitiesManager getCompatibilitiesManager() {
        return compatibilitiesManager;
    }

    public ContentService getContentService() {
        return contentService;
    }
}
