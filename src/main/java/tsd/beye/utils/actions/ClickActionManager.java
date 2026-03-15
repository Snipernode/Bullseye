package tsd.beye.utils.actions;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import tsd.beye.configs.ConfigsManager;
import tsd.beye.utils.Reloadable;

public class ClickActionManager implements Reloadable {
    private final ConfigsManager configsManager;
    private final Map<String, List<String>> mechanicBindings = new LinkedHashMap<>();

    public ClickActionManager(ConfigsManager configsManager) {
        this.configsManager = configsManager;
    }

    @Override
    public void reload() {
        mechanicBindings.clear();
        ConfigurationSection section = configsManager.inventory().getConfigurationSection("click-actions");
        if (section == null) {
            return;
        }

        for (String actionId : section.getKeys(false)) {
            List<String> mechanics = section.getStringList(actionId + ".mechanics");
            if (!mechanics.isEmpty()) {
                mechanicBindings.put(actionId.toLowerCase(), List.copyOf(mechanics));
            }
        }
    }

    public List<String> mechanics(String actionId) {
        return mechanicBindings.getOrDefault(actionId.toLowerCase(), List.of());
    }

    public Map<String, List<String>> bindings() {
        return Collections.unmodifiableMap(mechanicBindings);
    }
}
