package tsd.ttt.utils.breaker;

import org.bukkit.configuration.ConfigurationSection;
import tsd.ttt.TTT;
import tsd.ttt.utils.Reloadable;

public class BreakerManager implements Reloadable {
    private final TTT plugin;

    private boolean requireConfiguredTools = true;
    private double defaultSpeed = 1.0D;

    public BreakerManager(TTT plugin) {
        this.plugin = plugin;
    }

    @Override
    public void reload() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("breaker");
        if (section == null) {
            requireConfiguredTools = true;
            defaultSpeed = 1.0D;
            return;
        }

        requireConfiguredTools = section.getBoolean("require-configured-tools", true);
        defaultSpeed = Math.max(0.1D, section.getDouble("default-speed", 1.0D));
    }

    public boolean requireConfiguredTools() {
        return requireConfiguredTools;
    }

    public double defaultSpeed() {
        return defaultSpeed;
    }
}
