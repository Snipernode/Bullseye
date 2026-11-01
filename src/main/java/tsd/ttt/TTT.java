package tsd.ttt;

import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import tsd.ttt.api.TTTApi;
import tsd.ttt.api.TTTApiImpl;
import tsd.ttt.compatibilities.CompatibilitiesManager;
import tsd.ttt.configs.ConfigsManager;
import tsd.ttt.configs.SoundManager;
import tsd.ttt.core.PluginBootstrap;
import tsd.ttt.converter.Converter;
import tsd.ttt.fonts.FontManager;
import tsd.ttt.pack.PackGenerator;
import tsd.ttt.pack.server.TTTPackServer;
import tsd.ttt.service.BrowserService;
import tsd.ttt.service.DropTableService;
import tsd.ttt.service.EditorService;
import tsd.ttt.service.GeneratorService;
import tsd.ttt.service.ModelEngineService;
import tsd.ttt.service.MobService;
import tsd.ttt.service.MythicMobsService;
import tsd.ttt.service.RandomSpawnService;
import tsd.ttt.service.SkillService;
import tsd.ttt.service.SpawnerService;
import tsd.ttt.utils.actions.ClickActionManager;
import tsd.ttt.utils.breaker.BreakerManager;
import tsd.ttt.utils.inventories.InventoryManager;

public class TTT extends JavaPlugin {
    private static TTT instance;

    private PluginBootstrap bootstrap;
    private TTTApi api;

    @Override
    public void onEnable() {
        instance = this;
        bootstrap = new PluginBootstrap(this);
        bootstrap.start();
        if (!isEnabled()) {
            return;
        }

        api = new TTTApiImpl(bootstrap);
        Bukkit.getServicesManager().register(TTTApi.class, api, this, ServicePriority.Normal);

        getLogger().info("TTT enabled.");
    }

    @Override
    public void onDisable() {
        if (bootstrap != null) {
            bootstrap.stop();
        }
        Bukkit.getServicesManager().unregisterAll(this);
        getLogger().info("TTT disabled.");
    }

    public static TTT getInstance() {
        return instance;
    }

    public PluginBootstrap getBootstrap() {
        return bootstrap;
    }

    public TTTApi getApi() {
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

    public TTTPackServer packServer() {
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
