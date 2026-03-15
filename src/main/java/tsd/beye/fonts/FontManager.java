package tsd.beye.fonts;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import tsd.beye.service.ConfigService;
import tsd.beye.service.GlyphService;
import tsd.beye.utils.Reloadable;

public class FontManager implements Reloadable {
    private final ConfigService configService;
    private final GlyphService glyphService;
    private final Map<String, String> placeholderGlyphMap = new LinkedHashMap<>();
    private final Map<Character, String> unicodeGlyphMap = new LinkedHashMap<>();

    public FontManager(ConfigService configService, GlyphService glyphService) {
        this.configService = configService;
        this.glyphService = glyphService;
    }

    @Override
    public void reload() {
        placeholderGlyphMap.clear();
        unicodeGlyphMap.clear();

        YamlConfiguration config = configService.load("glyphs.yml");
        ConfigurationSection section = config.getConfigurationSection("glyphs");
        if (section == null) {
            return;
        }

        for (String placeholder : section.getKeys(false)) {
            String glyph = section.getString(placeholder, "");
            if (glyph == null || glyph.isBlank()) {
                continue;
            }

            placeholderGlyphMap.put(placeholder, glyph);
            if (glyph.length() == 1) {
                unicodeGlyphMap.put(glyph.charAt(0), placeholder);
            }
        }
    }

    public Collection<String> glyphPlaceholders() {
        return Collections.unmodifiableSet(placeholderGlyphMap.keySet());
    }

    public Map<String, String> placeholderGlyphMap() {
        return Collections.unmodifiableMap(placeholderGlyphMap);
    }

    public String glyphFromPlaceholder(String placeholder) {
        return placeholderGlyphMap.get(placeholder);
    }

    public String placeholderFromUnicode(char character) {
        return unicodeGlyphMap.get(character);
    }

    public String applyGlyphs(String text) {
        return glyphService.applyGlyphs(text);
    }
}
