package tsd.ttt.model;

import java.util.Collections;
import java.util.Map;

public record MenuDefinition(String id, int size, String title, Map<Integer, MenuButton> buttons) {
    public MenuDefinition {
        buttons = buttons == null ? Collections.emptyMap() : Collections.unmodifiableMap(buttons);
    }
}
