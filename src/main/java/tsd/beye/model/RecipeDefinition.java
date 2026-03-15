package tsd.beye.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public record RecipeDefinition(
    String id,
    String type,
    String resultItemId,
    int resultAmount,
    List<String> shape,
    Map<String, String> ingredients
) {
    public RecipeDefinition {
        shape = shape == null ? Collections.emptyList() : Collections.unmodifiableList(shape);
        ingredients = ingredients == null ? Collections.emptyMap() : Collections.unmodifiableMap(ingredients);
    }
}
