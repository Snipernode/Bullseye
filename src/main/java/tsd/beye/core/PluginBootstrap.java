package tsd.beye.core;

import java.util.Objects;
import org.bukkit.command.PluginCommand;
import tsd.beye.Bullseye;
import tsd.beye.command.BullseyeCommand;
import tsd.beye.compatibilities.CompatibilitiesManager;
import tsd.beye.configs.ConfigsManager;
import tsd.beye.configs.SoundManager;
import tsd.beye.converter.Converter;
import tsd.beye.fonts.FontManager;
import tsd.beye.listeners.BullseyeListener;
import tsd.beye.pack.PackGenerator;
import tsd.beye.pack.server.BullseyePackServer;
import tsd.beye.pack.server.SelfHostServer;
import tsd.beye.service.BlockService;
import tsd.beye.service.BrowserService;
import tsd.beye.service.ConfigService;
import tsd.beye.service.ConversionService;
import tsd.beye.service.ContentService;
import tsd.beye.service.DropTableService;
import tsd.beye.service.EditorService;
import tsd.beye.service.FurnitureService;
import tsd.beye.service.GeneratorService;
import tsd.beye.service.GlyphService;
import tsd.beye.service.ItemService;
import tsd.beye.service.MechanicService;
import tsd.beye.service.MenuService;
import tsd.beye.service.ModelEngineService;
import tsd.beye.service.MobService;
import tsd.beye.service.MythicMobsService;
import tsd.beye.service.RecipeService;
import tsd.beye.service.ResourcePackService;
import tsd.beye.service.RandomSpawnService;
import tsd.beye.service.SignatureService;
import tsd.beye.service.SkillService;
import tsd.beye.service.SpawnerService;
import tsd.beye.utils.actions.ClickActionManager;
import tsd.beye.utils.breaker.BreakerManager;
import tsd.beye.utils.inventories.InventoryManager;

public class PluginBootstrap {
    private final Bullseye plugin;

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
    private BullseyePackServer packServer;
    private SignatureService signatureService;
    private ConversionService conversionService;
    private Converter converter;
    private CompatibilitiesManager compatibilitiesManager;
    private ContentService contentService;

    public PluginBootstrap(Bullseye plugin) {
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
            recipeService
        );

        signatureService.reloadSettings();
        if (!signatureService.validateConfiguredSignature() && signatureService.isStopOnFailure()) {
            plugin.getServer().getPluginManager().disablePlugin(plugin);
            return;
        }

        resourcePackService.reloadSettings();
        packServer.start();
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
        packServer.start();
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
        packServer.stop();
        mobService.stop();
        furnitureService.stop();
        spawnerService.stop();
        randomSpawnService.stop();
    }

    private void registerCommands() {
        PluginCommand command = Objects.requireNonNull(plugin.getCommand("bullseye"), "bullseye command missing in plugin.yml");
        BullseyeCommand executor = new BullseyeCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    private void registerListeners() {
        plugin.getServer().getPluginManager().registerEvents(new BullseyeListener(this), plugin);
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

    public BullseyePackServer getPackServer() {
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
