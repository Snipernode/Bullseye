package tsd.beye.utils.inventories;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import tsd.beye.configs.ConfigsManager;
import tsd.beye.service.MenuService;
import tsd.beye.utils.Reloadable;

public class InventoryManager implements Reloadable {
    private final MenuService menuService;
    private final ConfigsManager configsManager;
    private final Map<String, String> configuredViews = new LinkedHashMap<>();

    public InventoryManager(MenuService menuService, ConfigsManager configsManager) {
        this.menuService = menuService;
        this.configsManager = configsManager;
    }

    @Override
    public void reload() {
        configuredViews.clear();
        ConfigurationSection section = configsManager.inventory().getConfigurationSection("views");
        if (section == null) {
            return;
        }

        for (String key : section.getKeys(false)) {
            String value = section.getString(key, "").trim();
            if (!value.isBlank()) {
                configuredViews.put(normalize(key), value);
            }
        }
    }

    public Map<String, String> configuredViews() {
        return Collections.unmodifiableMap(configuredViews);
    }

    public boolean open(Player player, String viewOrMenuId) {
        String resolved = configuredViews.getOrDefault(normalize(viewOrMenuId), viewOrMenuId);
        return menuService.openMenu(player, resolved);
    }

    public boolean openMainMenu(Player player) {
        return open(player, "main-menu");
    }

    public boolean openItemsView(Player player) {
        return open(player, "items-menu");
    }

    public boolean openSpawnMobsView(Player player) {
        return open(player, "spawn-mobs-menu");
    }

    public boolean openHudChecker(Player player) {
        return open(player, "hud-checker-menu");
    }

    private String normalize(String key) {
        return key == null ? "" : key.trim().toLowerCase().replace('_', '-');
    }
}
