package tsd.beye.model;

import java.util.Collections;
import java.util.List;

public record DropTableDefinition(String id, List<Entry> entries) {
    public DropTableDefinition {
        entries = entries == null ? Collections.emptyList() : List.copyOf(entries);
    }

    public record Entry(String itemId, int minAmount, int maxAmount, double chance) {
        public Entry {
            itemId = itemId == null ? "" : itemId.trim().toLowerCase();
            minAmount = Math.max(0, minAmount);
            maxAmount = Math.max(minAmount, maxAmount);
            chance = Math.max(0.0D, Math.min(1.0D, chance));
        }
    }
}
