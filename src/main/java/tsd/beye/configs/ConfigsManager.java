package tsd.beye.configs;

import java.io.File;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import tsd.beye.Bullseye;
import tsd.beye.service.ConfigService;
import tsd.beye.utils.Reloadable;

public class ConfigsManager implements Reloadable {
    private final Bullseye plugin;
    private final ConfigService configService;

    private FileConfiguration settings;
    private YamlConfiguration inventory;
    private YamlConfiguration converter;
    private YamlConfiguration sounds;
    private YamlConfiguration languages;
    private YamlConfiguration messages;
    private String activeLocale = "english";

    public ConfigsManager(Bullseye plugin, ConfigService configService) {
        this.plugin = plugin;
        this.configService = configService;
    }

    @Override
    public void reload() {
        configService.reloadMainConfig();
        settings = plugin.getConfig();
        inventory = configService.load("inventory.yml");
        converter = configService.load("converter.yml");
        sounds = configService.load("sounds.yml");
        languages = configService.load("languages.yml");

        String configuredLocale = settings.getString(
            "plugin.locale",
            languages.getString("default-locale", "english")
        );
        activeLocale = sanitizeLocale(configuredLocale);
        messages = loadMessages(activeLocale);
        if (messages == null) {
            activeLocale = "english";
            messages = loadMessages(activeLocale);
        }

        if (messages == null) {
            messages = new YamlConfiguration();
        }
    }

    public FileConfiguration settings() {
        return settings == null ? plugin.getConfig() : settings;
    }

    public YamlConfiguration inventory() {
        return inventory == null ? new YamlConfiguration() : inventory;
    }

    public YamlConfiguration converter() {
        return converter == null ? new YamlConfiguration() : converter;
    }

    public YamlConfiguration sounds() {
        return sounds == null ? new YamlConfiguration() : sounds;
    }

    public YamlConfiguration languages() {
        return languages == null ? new YamlConfiguration() : languages;
    }

    public YamlConfiguration messages() {
        return messages == null ? new YamlConfiguration() : messages;
    }

    public String activeLocale() {
        return activeLocale;
    }

    public String message(String path, String fallback) {
        return messages().getString(path, fallback);
    }

    private YamlConfiguration loadMessages(String locale) {
        File file = new File(plugin.getDataFolder(), "messages/" + locale + ".yml");
        if (!file.exists()) {
            return null;
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    private String sanitizeLocale(String raw) {
        if (raw == null || raw.isBlank()) {
            return "english";
        }
        return raw.trim().toLowerCase().replace(' ', '_');
    }
}
