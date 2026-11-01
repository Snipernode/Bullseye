package tsd.ttt.core;

import org.bukkit.NamespacedKey;
import tsd.ttt.TTT;

public final class Keychain {
    private final NamespacedKey itemId;
    private final NamespacedKey furnitureId;
    private final NamespacedKey blockId;
    private final NamespacedKey mobId;
    private final NamespacedKey mobModelHost;
    private final NamespacedKey mobModelDisplay;
    private final NamespacedKey furnitureModelHost;
    private final NamespacedKey furnitureModelDisplay;
    private final NamespacedKey editorToolType;
    private final NamespacedKey editorToolId;
    private final NamespacedKey mobPhase;
    private final NamespacedKey rigRoot;

    public Keychain(TTT plugin) {
        this.itemId = new NamespacedKey(plugin, "item_id");
        this.furnitureId = new NamespacedKey(plugin, "furniture_id");
        this.blockId = new NamespacedKey(plugin, "block_id");
        this.mobId = new NamespacedKey(plugin, "mob_id");
        this.mobModelHost = new NamespacedKey(plugin, "mob_model_host");
        this.mobModelDisplay = new NamespacedKey(plugin, "mob_model_display");
        this.furnitureModelHost = new NamespacedKey(plugin, "furniture_model_host");
        this.furnitureModelDisplay = new NamespacedKey(plugin, "furniture_model_display");
        this.editorToolType = new NamespacedKey(plugin, "editor_tool_type");
        this.editorToolId = new NamespacedKey(plugin, "editor_tool_id");
        this.mobPhase = new NamespacedKey(plugin, "mob_phase");
        this.rigRoot = new NamespacedKey(plugin, "rig_root");
    }

    public NamespacedKey itemId() {
        return itemId;
    }

    public NamespacedKey furnitureId() {
        return furnitureId;
    }

    public NamespacedKey blockId() {
        return blockId;
    }

    public NamespacedKey mobId() {
        return mobId;
    }

    public NamespacedKey mobModelHost() {
        return mobModelHost;
    }

    public NamespacedKey mobModelDisplay() {
        return mobModelDisplay;
    }

    public NamespacedKey furnitureModelHost() {
        return furnitureModelHost;
    }

    public NamespacedKey furnitureModelDisplay() {
        return furnitureModelDisplay;
    }

    public NamespacedKey editorToolType() {
        return editorToolType;
    }

    public NamespacedKey editorToolId() {
        return editorToolId;
    }

    public NamespacedKey mobPhase() {
        return mobPhase;
    }

    public NamespacedKey rigRoot() {
        return rigRoot;
    }
}
