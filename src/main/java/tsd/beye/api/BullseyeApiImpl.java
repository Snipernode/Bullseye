package tsd.beye.api;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import tsd.beye.core.PluginBootstrap;

public class BullseyeApiImpl implements BullseyeApi {
    private final PluginBootstrap bootstrap;

    public BullseyeApiImpl(PluginBootstrap bootstrap) {
        this.bootstrap = bootstrap;
    }

    @Override
    public boolean isCustomItem(ItemStack item) {
        return bootstrap.getItemService().isCustomItem(item);
    }

    @Override
    public String getCustomItemId(ItemStack item) {
        return bootstrap.getItemService().getItemId(item);
    }

    @Override
    public ItemStack createCustomItem(String itemId, int amount) {
        return bootstrap.getItemService().createItem(itemId, amount);
    }

    @Override
    public boolean isCustomBlock(Block block) {
        return bootstrap.getBlockService().isCustomBlock(block);
    }

    @Override
    public String getCustomBlockId(Block block) {
        return bootstrap.getBlockService().getBlockId(block);
    }

    @Override
    public void setCustomBlock(Block block, String blockId) {
        bootstrap.getBlockService().setCustomBlock(block, blockId);
    }

    @Override
    public Entity spawnFurniture(Location location, String furnitureId) {
        return bootstrap.getFurnitureService().spawnFurniture(location, furnitureId);
    }

    @Override
    public String getFurnitureId(Entity entity) {
        return bootstrap.getFurnitureService().getFurnitureId(entity);
    }

    @Override
    public boolean openMenu(Player player, String menuId) {
        return bootstrap.getMenuService().openMenu(player, menuId);
    }
}
