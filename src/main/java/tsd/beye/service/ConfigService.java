package tsd.beye.service;

import java.io.File;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import tsd.beye.Bullseye;

public class ConfigService {
    private static final List<String> DEFAULT_FILES = List.of(
        "config.yml",
        "inventory.yml",
        "converter.yml",
        "sounds.yml",
        "languages.yml",
        "mythicmobs.yml",
        "modelengine.yml",
        "models.yml",
        "drops.yml",
        "spawners.yml",
        "randomspawns.yml",
        "skills.yml",
        "mobs.yml",
        "items.yml",
        "blocks.yml",
        "furniture.yml",
        "mechanics.yml",
        "glyphs.yml",
        "menus.yml",
        "recipes.yml",
        "messages/english.yml",
        "resourcepack/base/pack.mcmeta",
        "resourcepack/base/assets/minecraft/font/default.json",
        "resourcepack/base/assets/bullseye/textures/gui/main_panel.png",
        "resourcepack/base/assets/bullseye/textures/gui/main_panel_raw.png",
        "resourcepack/base/assets/bullseye/textures/gui/items_panel.png",
        "resourcepack/base/assets/bullseye/textures/gui/items_panel_raw.png",
        "resourcepack/base/assets/bullseye/textures/gui/spawn_panel.png",
        "resourcepack/base/assets/bullseye/textures/gui/spawn_panel_raw.png",
        "resourcepack/base/assets/bullseye/textures/gui/hud_panel.png",
        "resourcepack/base/assets/bullseye/textures/gui/hud_panel_raw.png",
        "resourcepack/base/assets/bullseye/textures/item/.keep",
        "resourcepack/base/assets/bullseye/models/item/.keep",
        "resourcepack/addons/.keep",
        "addons/.keep"
    );
    private static final List<String> GUI_SYNC_FILES = List.of(
        "resourcepack/base/assets/minecraft/font/default.json",
        "resourcepack/base/assets/bullseye/textures/gui/main_panel.png",
        "resourcepack/base/assets/bullseye/textures/gui/main_panel_raw.png",
        "resourcepack/base/assets/bullseye/textures/gui/items_panel.png",
        "resourcepack/base/assets/bullseye/textures/gui/items_panel_raw.png",
        "resourcepack/base/assets/bullseye/textures/gui/spawn_panel.png",
        "resourcepack/base/assets/bullseye/textures/gui/spawn_panel_raw.png",
        "resourcepack/base/assets/bullseye/textures/gui/hud_panel.png",
        "resourcepack/base/assets/bullseye/textures/gui/hud_panel_raw.png"
    );

    private final Bullseye plugin;

    public ConfigService(Bullseye plugin) {
        this.plugin = plugin;
    }

    public void ensureDefaults() {
        plugin.saveDefaultConfig();

        ensureDirectory("resourcepack/base/assets/bullseye/textures/item");
        ensureDirectory("resourcepack/base/assets/bullseye/textures/gui");
        ensureDirectory("resourcepack/base/assets/bullseye/models/item");
        ensureDirectory("resourcepack/base/assets/minecraft/font");
        ensureDirectory("resourcepack/addons");
        ensureDirectory("addons");
        ensureDirectory("messages");

        for (String file : DEFAULT_FILES) {
            File target = new File(plugin.getDataFolder(), file);
            if (!target.exists()) {
                plugin.saveResource(file, false);
            }
        }

        if (plugin.getConfig().getBoolean("resource-pack.sync-bundled-gui-assets", true)) {
            for (String file : GUI_SYNC_FILES) {
                plugin.saveResource(file, true);
            }
        }
    }

    public YamlConfiguration load(String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);
        return YamlConfiguration.loadConfiguration(file);
    }

    public void reloadMainConfig() {
        plugin.reloadConfig();
    }

    private void ensureDirectory(String relativePath) {
        File directory = new File(plugin.getDataFolder(), relativePath);
        if (!directory.exists() && !directory.mkdirs()) {
            plugin.getLogger().warning("Failed to create directory: " + relativePath);
        }
    }
}
