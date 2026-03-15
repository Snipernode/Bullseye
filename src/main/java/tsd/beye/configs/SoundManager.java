package tsd.beye.configs;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import tsd.beye.utils.Reloadable;

public class SoundManager implements Reloadable {
    private final ConfigsManager configsManager;
    private final Map<String, String> aliases = new LinkedHashMap<>();

    public SoundManager(ConfigsManager configsManager) {
        this.configsManager = configsManager;
    }

    @Override
    public void reload() {
        aliases.clear();
        ConfigurationSection section = configsManager.sounds().getConfigurationSection("sounds.aliases");
        if (section == null) {
            return;
        }

        for (String key : section.getKeys(false)) {
            String value = section.getString(key, "").trim();
            if (value.isBlank()) {
                continue;
            }
            aliases.put(normalize(key), value.trim());
        }
    }

    public Map<String, String> aliases() {
        return Collections.unmodifiableMap(aliases);
    }

    public String resolveSoundName(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        return aliases.getOrDefault(normalize(value), value);
    }

    public Sound resolveSound(String value, Sound fallback) {
        String resolved = resolveSoundName(value);
        if (resolved == null || resolved.isBlank()) {
            return fallback;
        }

        try {
            return Sound.valueOf(resolved.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    private String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }
}
