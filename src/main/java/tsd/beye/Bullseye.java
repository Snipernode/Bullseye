package tsd.beye;

import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import tsd.beye.api.BullseyeApi;
import tsd.beye.api.BullseyeApiImpl;
import tsd.beye.compatibilities.CompatibilitiesManager;
import tsd.beye.configs.ConfigsManager;
import tsd.beye.configs.SoundManager;
import tsd.beye.core.PluginBootstrap;
import tsd.beye.converter.Converter;
import tsd.beye.fonts.FontManager;
import tsd.beye.pack.PackGenerator;
import tsd.beye.pack.server.BullseyePackServer;
import tsd.beye.service.BrowserService;
import tsd.beye.service.DropTableService;
import tsd.beye.service.EditorService;
import tsd.beye.service.GeneratorService;
import tsd.beye.service.ModelEngineService;
import tsd.beye.service.MobService;
import tsd.beye.service.MythicMobsService;
import tsd.beye.service.RandomSpawnService;
import tsd.beye.service.SkillService;
import tsd.beye.service.SpawnerService;
import tsd.beye.utils.actions.ClickActionManager;
import tsd.beye.utils.breaker.BreakerManager;
import tsd.beye.utils.inventories.InventoryManager;

public class Bullseye extends JavaPlugin {
    private static Bullseye instance;

    private PluginBootstrap bootstrap;
    private BullseyeApi api;

    @Override
    public void onEnable() {
        instance = this;
        bootstrap = new PluginBootstrap(this);
        bootstrap.start();
        if (!isEnabled()) {
            return;
        }

        api = new BullseyeApiImpl(bootstrap);
        Bukkit.getServicesManager().register(BullseyeApi.class, api, this, ServicePriority.Normal);

        getLogger().info("Bullseye enabled.");
    }

    @Override
    public void onDisable() {
        if (bootstrap != null) {
            bootstrap.stop();
        }
        Bukkit.getServicesManager().unregisterAll(this);
        getLogger().info("Bullseye disabled.");
    }

    public static Bullseye getInstance() {
        return instance;
    }

    public PluginBootstrap getBootstrap() {
        return bootstrap;
    }

    public BullseyeApi getApi() {
        return api;
    }

    public void reloadPlugin() {
        if (bootstrap != null) {
            bootstrap.reload();
        }
    }

    public ConfigsManager configsManager() {
        return bootstrap.getConfigsManager();
    }

    public FontManager fontManager() {
        return bootstrap.getFontManager();
    }

    public SoundManager soundManager() {
        return bootstrap.getSoundManager();
    }

    public InventoryManager invManager() {
        return bootstrap.getInventoryManager();
    }

    public ClickActionManager clickActionManager() {
        return bootstrap.getClickActionManager();
    }

    public BreakerManager breakerManager() {
        return bootstrap.getBreakerManager();
    }

    public Converter converter() {
        return bootstrap.getConverter();
    }

    public PackGenerator packGenerator() {
        return bootstrap.getPackGenerator();
    }

    public BullseyePackServer packServer() {
        return bootstrap.getPackServer();
    }

    public CompatibilitiesManager compatibilitiesManager() {
        return bootstrap.getCompatibilitiesManager();
    }

    public MythicMobsService mythicMobsService() {
        return bootstrap.getMythicMobsService();
    }

    public MobService mobService() {
        return bootstrap.getMobService();
    }

    public DropTableService dropTableService() {
        return bootstrap.getDropTableService();
    }

    public ModelEngineService modelEngineService() {
        return bootstrap.getModelEngineService();
    }

    public SpawnerService spawnerService() {
        return bootstrap.getSpawnerService();
    }

    public RandomSpawnService randomSpawnService() {
        return bootstrap.getRandomSpawnService();
    }

    public BrowserService browserService() {
        return bootstrap.getBrowserService();
    }

    public SkillService skillService() {
        return bootstrap.getSkillService();
    }

    public EditorService editorService() {
        return bootstrap.getEditorService();
    }

    public GeneratorService generatorService() {
        return bootstrap.getGeneratorService();
    }
}
