package tsd.beye.model;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

public final class BlockPosition {
    private final UUID worldId;
    private final int x;
    private final int y;
    private final int z;

    public BlockPosition(UUID worldId, int x, int y, int z) {
        this.worldId = worldId;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static BlockPosition fromBlock(Block block) {
        return new BlockPosition(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    public static BlockPosition fromLocation(Location location) {
        World world = location.getWorld();
        if (world == null) {
            throw new IllegalArgumentException("Location has no world");
        }
        return new BlockPosition(world.getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public UUID getWorldId() {
        return worldId;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public String asKey() {
        return worldId + ":" + x + ":" + y + ":" + z;
    }

    public static BlockPosition fromMap(Map<?, ?> map) {
        return new BlockPosition(
            UUID.fromString(String.valueOf(map.get("world"))),
            Integer.parseInt(String.valueOf(map.get("x"))),
            Integer.parseInt(String.valueOf(map.get("y"))),
            Integer.parseInt(String.valueOf(map.get("z")))
        );
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BlockPosition position)) {
            return false;
        }
        return x == position.x && y == position.y && z == position.z && Objects.equals(worldId, position.worldId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(worldId, x, y, z);
    }
}
