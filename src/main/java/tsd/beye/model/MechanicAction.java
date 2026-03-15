package tsd.beye.model;

import java.util.Collections;
import java.util.Map;

public record MechanicAction(String type, Map<String, Object> data) {
    public MechanicAction {
        data = data == null ? Collections.emptyMap() : Collections.unmodifiableMap(data);
    }
}
