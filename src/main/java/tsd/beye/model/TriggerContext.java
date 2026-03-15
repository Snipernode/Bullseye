package tsd.beye.model;

import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class TriggerContext {
    private final Player player;
    private final Entity target;
    private final Block block;
    private final ItemStack item;
    private final String sourceId;

    private TriggerContext(Builder builder) {
        this.player = builder.player;
        this.target = builder.target;
        this.block = builder.block;
        this.item = builder.item;
        this.sourceId = builder.sourceId;
    }

    public Player getPlayer() {
        return player;
    }

    public Entity getTarget() {
        return target;
    }

    public Block getBlock() {
        return block;
    }

    public ItemStack getItem() {
        return item;
    }

    public String getSourceId() {
        return sourceId;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Player player;
        private Entity target;
        private Block block;
        private ItemStack item;
        private String sourceId;

        private Builder() {
        }

        public Builder player(Player player) {
            this.player = player;
            return this;
        }

        public Builder target(Entity target) {
            this.target = target;
            return this;
        }

        public Builder block(Block block) {
            this.block = block;
            return this;
        }

        public Builder item(ItemStack item) {
            this.item = item;
            return this;
        }

        public Builder sourceId(String sourceId) {
            this.sourceId = sourceId;
            return this;
        }

        public TriggerContext build() {
            return new TriggerContext(this);
        }
    }
}
