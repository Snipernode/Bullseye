package tsd.beye.compatibilities;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import tsd.beye.Bullseye;
import tsd.beye.service.ConversionService;
import tsd.beye.utils.Reloadable;

public class CompatibilitiesManager implements Reloadable {
    private static final List<String> KNOWN_PLUGINS = List.of(
        "Nexo",
        "Oraxen",
        "ChampionAllies",
        "MythicMobs",
        "ModelEngine",
        "ModelEngineR4",
        "WorldEdit",
        "WorldGuard",
        "PlaceholderAPI",
        "TAB",
        "MMOItems"
    );

    private final Bullseye plugin;
    private final ConversionService conversionService;
    private final Map<String, Boolean> compatibilities = new LinkedHashMap<>();

    public CompatibilitiesManager(Bullseye plugin, ConversionService conversionService) {
        this.plugin = plugin;
        this.conversionService = conversionService;
    }

    @Override
    public void reload() {
        compatibilities.clear();
        for (String pluginName : KNOWN_PLUGINS) {
            Plugin external = Bukkit.getPluginManager().getPlugin(pluginName);
            compatibilities.put(pluginName, external != null && external.isEnabled());
        }
    }

    public Map<String, Boolean> states() {
        return Collections.unmodifiableMap(compatibilities);
    }

    public boolean isEnabled(String pluginName) {
        return compatibilities.getOrDefault(pluginName, false);
    }

    public List<String> enabledPlugins() {
        return compatibilities.entrySet().stream()
            .filter(Map.Entry::getValue)
            .map(Map.Entry::getKey)
            .toList();
    }

    public List<String> suggestedConversions() {
        conversionService.scanInstalledPlugins();
        return conversionService.getDetectedPluginNames().stream()
            .map(name -> name.toLowerCase(Locale.ROOT))
            .sorted()
            .toList();
    }
}
