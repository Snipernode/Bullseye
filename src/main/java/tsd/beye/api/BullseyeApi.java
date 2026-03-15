package tsd.beye.api;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public interface BullseyeApi {
    boolean isCustomItem(ItemStack item);

    String getCustomItemId(ItemStack item);

    ItemStack createCustomItem(String itemId, int amount);

    boolean isCustomBlock(Block block);

    String getCustomBlockId(Block block);

    void setCustomBlock(Block block, String blockId);

    Entity spawnFurniture(Location location, String furnitureId);

    String getFurnitureId(Entity entity);

    boolean openMenu(Player player, String menuId);
}
