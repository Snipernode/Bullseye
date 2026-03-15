package tsd.beye.service;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public class GlyphService {
    private final Map<String, String> glyphs = new HashMap<>();

    public void load(YamlConfiguration config) {
        glyphs.clear();
        ConfigurationSection section = config.getConfigurationSection("glyphs");
        if (section == null) {
            return;
        }

        for (String key : section.getKeys(false)) {
            String value = section.getString(key, "");
            glyphs.put(key.toLowerCase(Locale.ROOT), value);
        }
    }

    public String applyGlyphs(String message) {
        if (message == null || message.isBlank()) {
            return message;
        }

        String output = message;
        for (Map.Entry<String, String> entry : glyphs.entrySet()) {
            output = output.replace(entry.getKey(), entry.getValue());
        }
        return output;
    }
}
