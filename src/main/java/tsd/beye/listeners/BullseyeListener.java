package tsd.beye.listeners;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import tsd.beye.core.PluginBootstrap;
import tsd.beye.model.CustomBlockDefinition;
import tsd.beye.model.CustomItemDefinition;
import tsd.beye.model.CustomMobDefinition;
import tsd.beye.model.FurnitureDefinition;
import tsd.beye.model.TriggerContext;
import tsd.beye.model.TriggerType;
import tsd.beye.service.MobService;
import tsd.beye.service.MythicMobsService;

public class BullseyeListener implements Listener {
    private final PluginBootstrap bootstrap;
    private final Map<UUID, String> loginHostByPlayer = new HashMap<>();

    public BullseyeListener(PluginBootstrap bootstrap) {
        this.bootstrap = bootstrap;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerLogin(PlayerLoginEvent event) {
        String hostname = normalizeLoginHostname(event.getHostname());
        if (hostname != null && !hostname.isBlank()) {
            loginHostByPlayer.put(event.getPlayer().getUniqueId(), hostname);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (bootstrap.getResourcePackService().isEnabled() && bootstrap.getResourcePackService().isAutoSend()) {
            String hostOverride = loginHostByPlayer.remove(event.getPlayer().getUniqueId());
            bootstrap.getResourcePackService().sendPack(event.getPlayer(), hostOverride);
        }

        if (event.getPlayer().hasPermission("bullseye.admin") && bootstrap.getConversionService().isConversionRecommended()) {
            event.getPlayer().sendMessage("§6[ Bullseye ] §eDetected §f"
                + String.join(", ", bootstrap.getConversionService().getDetectedPluginNames())
                + "§e. Run §f/bullseye convert §ethen §f/bullseye enable confirm§e.");
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        loginHostByPlayer.remove(event.getPlayer().getUniqueId());
        bootstrap.getBrowserService().clearPlayerState(event.getPlayer().getUniqueId());
        bootstrap.getEditorService().clearPlayerState(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        String itemId = bootstrap.getItemService().getItemId(event.getItemInHand());
        if (itemId == null) {
            return;
        }

        CustomBlockDefinition blockDefinition = bootstrap.getBlockService().getDefinitionByItemId(itemId);
        if (blockDefinition == null) {
            return;
        }

        bootstrap.getBlockService().setCustomBlock(event.getBlockPlaced(), blockDefinition.id());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        String blockId = bootstrap.getBlockService().getBlockId(block);
        if (blockId == null) {
            return;
        }

        event.setCancelled(true);

        boolean broken = bootstrap.getBlockService().breakCustomBlock(
            event.getPlayer(),
            block,
            event.getPlayer().getInventory().getItemInMainHand()
        );

        if (!broken) {
            return;
        }

        CustomBlockDefinition definition = bootstrap.getBlockService().getDefinition(blockId);
        if (definition == null || definition.mechanics().isEmpty()) {
            return;
        }

        TriggerContext context = TriggerContext.builder()
            .player(event.getPlayer())
            .block(block)
            .item(event.getPlayer().getInventory().getItemInMainHand())
            .sourceId(blockId)
            .build();

        bootstrap.getMechanicService().executeMechanics(definition.mechanics(), TriggerType.BLOCK_BREAK, context);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            if (bootstrap.getBrowserService().handlePendingSpawnerPlacement(event)) {
                return;
            }
            if (bootstrap.getEditorService().handleToolUse(event)) {
                return;
            }
            handleCustomBlockInteraction(event, item);
            handleFurniturePlacement(event, item);
        }

        if (!event.isCancelled() && (event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.RIGHT_CLICK_AIR)) {
            if (bootstrap.getEditorService().handleToolUse(event)) {
                return;
            }
            handleMobSpawnEgg(event, item);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            ItemStack weapon = player.getInventory().getItemInMainHand();
            String itemId = bootstrap.getItemService().getItemId(weapon);
            if (itemId != null) {
                CustomItemDefinition itemDefinition = bootstrap.getItemService().getDefinition(itemId);
                if (itemDefinition != null && !itemDefinition.mechanics().isEmpty()) {
                    TriggerContext context = TriggerContext.builder()
                        .player(player)
                        .target(event.getEntity())
                        .item(weapon)
                        .sourceId(itemId)
                        .build();
                    bootstrap.getMechanicService().executeMechanics(itemDefinition.mechanics(), TriggerType.ITEM_HIT, context);
                }
            }
        }

        Entity mobRoot = bootstrap.getMobService().resolveRootEntity(event.getEntity());
        String mobId = bootstrap.getMobService().getMobId(event.getEntity());
        if (mobId != null) {
            CustomMobDefinition definition = bootstrap.getMobService().getDefinition(mobId);
            if (definition != null && !definition.mechanics().isEmpty()) {
                TriggerContext context = TriggerContext.builder()
                    .player(event.getDamager() instanceof Player player ? player : null)
                    .target(mobRoot != null ? mobRoot : event.getEntity())
                    .sourceId(mobId)
                    .build();
                bootstrap.getMechanicService().executeMechanics(definition.mechanics(), TriggerType.MOB_HIT, context);
                bootstrap.getSkillService().executeSkills(definition.skills(), TriggerType.MOB_HIT, context);
            } else if (definition != null && !definition.skills().isEmpty()) {
                TriggerContext context = TriggerContext.builder()
                    .player(event.getDamager() instanceof Player player ? player : null)
                    .target(mobRoot != null ? mobRoot : event.getEntity())
                    .sourceId(mobId)
                    .build();
                bootstrap.getSkillService().executeSkills(definition.skills(), TriggerType.MOB_HIT, context);
            }
        }

        Entity furnitureRoot = bootstrap.getFurnitureService().resolveRootEntity(event.getEntity());
        String furnitureId = bootstrap.getFurnitureService().getFurnitureId(event.getEntity());
        if (furnitureId == null || !(event.getDamager() instanceof Player player)) {
            return;
        }

        event.setCancelled(true);
        removeFurnitureEntity(player, furnitureRoot != null ? furnitureRoot : event.getEntity(), furnitureId, true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        String mobId = bootstrap.getMobService().getMobId(event.getEntity());
        if (mobId == null) {
            return;
        }

        CustomMobDefinition definition = bootstrap.getMobService().getDefinition(mobId);
        bootstrap.getMobService().removeMobVisuals(event.getEntity());
        bootstrap.getMobService().dropConfiguredLoot(event.getEntity(), definition);
        if (definition == null || definition.mechanics().isEmpty()) {
            return;
        }

        TriggerContext context = TriggerContext.builder()
            .player(event.getEntity().getKiller())
            .target(event.getEntity())
            .sourceId(mobId)
            .build();
        bootstrap.getMechanicService().executeMechanics(definition.mechanics(), TriggerType.MOB_DEATH, context);
        bootstrap.getSkillService().executeSkills(definition.skills(), TriggerType.MOB_DEATH, context);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        ArmorStand stand = event.getRightClicked();
        Entity furnitureRoot = bootstrap.getFurnitureService().resolveRootEntity(stand);
        String furnitureId = bootstrap.getFurnitureService().getFurnitureId(stand);
        if (furnitureId == null) {
            return;
        }

        event.setCancelled(true);
        if (event.getPlayer().isSneaking()) {
            removeFurnitureEntity(event.getPlayer(), furnitureRoot != null ? furnitureRoot : stand, furnitureId, true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        Entity entity = event.getRightClicked();
        Entity furnitureRoot = bootstrap.getFurnitureService().resolveRootEntity(entity);
        String furnitureId = bootstrap.getFurnitureService().getFurnitureId(entity);
        FurnitureDefinition definition = furnitureId == null ? null : bootstrap.getFurnitureService().getDefinition(furnitureId);
        if (definition == null) {
            Entity mobRoot = bootstrap.getMobService().resolveRootEntity(entity);
            String mobId = bootstrap.getMobService().getMobId(entity);
            if (mobId == null) {
                return;
            }

            CustomMobDefinition mobDefinition = bootstrap.getMobService().getDefinition(mobId);
            if (mobDefinition == null) {
                return;
            }

            event.setCancelled(true);
            TriggerContext context = TriggerContext.builder()
                .player(event.getPlayer())
                .target(mobRoot != null ? mobRoot : entity)
                .sourceId(mobId)
                .build();
            bootstrap.getMechanicService().executeMechanics(mobDefinition.mechanics(), TriggerType.MOB_INTERACT, context);
            bootstrap.getSkillService().executeSkills(mobDefinition.skills(), TriggerType.MOB_INTERACT, context);
            return;
        }

        event.setCancelled(true);
        if (event.getPlayer().isSneaking()) {
            removeFurnitureEntity(event.getPlayer(), furnitureRoot != null ? furnitureRoot : entity, furnitureId, true);
            return;
        }

        Entity seatEntity = furnitureRoot != null ? furnitureRoot : entity;
        if (definition.seat() && seatEntity.getPassengers().isEmpty()) {
            seatEntity.addPassenger(event.getPlayer());
        }

        TriggerContext context = TriggerContext.builder()
            .player(event.getPlayer())
            .target(seatEntity)
            .sourceId(furnitureId)
            .build();
        bootstrap.getMechanicService().executeMechanics(definition.mechanics(), TriggerType.FURNITURE_INTERACT, context);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (bootstrap.getBrowserService().handleClick(event)) {
            return;
        }
        bootstrap.getMenuService().handleClick(event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        if (bootstrap.getBrowserService().handleSearchChat(event)) {
            return;
        }
        event.setMessage(bootstrap.getGlyphService().applyGlyphs(event.getMessage()));
    }

    private void handleCustomBlockInteraction(PlayerInteractEvent event, ItemStack item) {
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) {
            return;
        }

        String blockId = bootstrap.getBlockService().getBlockId(clickedBlock);
        if (blockId == null) {
            return;
        }

        CustomBlockDefinition definition = bootstrap.getBlockService().getDefinition(blockId);
        if (definition == null) {
            return;
        }

        if (definition.cancelVanillaInteraction()) {
            event.setCancelled(true);
        }

        TriggerContext context = TriggerContext.builder()
            .player(event.getPlayer())
            .block(clickedBlock)
            .item(item)
            .sourceId(blockId)
            .build();

        bootstrap.getMechanicService().executeMechanics(definition.mechanics(), TriggerType.BLOCK_INTERACT, context);
    }

    private void handleFurniturePlacement(PlayerInteractEvent event, ItemStack item) {
        String itemId = bootstrap.getItemService().getItemId(item);
        if (itemId == null) {
            return;
        }

        FurnitureDefinition definition = bootstrap.getFurnitureService().getDefinitionByItemId(itemId);
        if (definition == null) {
            return;
        }

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) {
            return;
        }

        Location placeLocation = clickedBlock.getRelative(event.getBlockFace()).getLocation().add(0.5, 0.0, 0.5);
        Entity entity = bootstrap.getFurnitureService().spawnFurniture(placeLocation, definition.id());
        if (entity == null) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.CREATIVE) {
            int amount = item.getAmount();
            if (amount <= 1) {
                player.getInventory().setItemInMainHand(null);
            } else {
                item.setAmount(amount - 1);
            }
        }
    }

    private void removeFurnitureEntity(Player player, Entity entity, String furnitureId, boolean dropItem) {
        FurnitureDefinition definition = bootstrap.getFurnitureService().getDefinition(furnitureId);
        if (definition == null) {
            bootstrap.getFurnitureService().removeFurniture(entity);
            return;
        }

        for (Entity passenger : new ArrayList<>(entity.getPassengers())) {
            entity.removePassenger(passenger);
        }
        entity.eject();

        if (dropItem) {
            ItemStack drop = bootstrap.getItemService().createItem(definition.itemId(), 1);
            if (drop != null) {
                entity.getWorld().dropItemNaturally(entity.getLocation(), drop);
            }
        }

        bootstrap.getFurnitureService().removeFurniture(entity);

        TriggerContext context = TriggerContext.builder()
            .player(player)
            .target(entity)
            .sourceId(furnitureId)
            .build();
        bootstrap.getMechanicService().executeMechanics(definition.mechanics(), TriggerType.FURNITURE_INTERACT, context);
    }

    private void handleMobSpawnEgg(PlayerInteractEvent event, ItemStack item) {
        String itemId = bootstrap.getItemService().getItemId(item);
        if (itemId == null) {
            return;
        }

        Block clickedBlock = event.getClickedBlock();
        BlockFace face = event.getBlockFace();
        MobService.SpawnResult nativeResult = bootstrap.getMobService().spawnFromEggItem(
            itemId,
            event.getPlayer(),
            clickedBlock,
            face
        );
        if (nativeResult.handled()) {
            event.setCancelled(true);
            if (nativeResult.message() != null && !nativeResult.message().isBlank()) {
                event.getPlayer().sendMessage(nativeResult.message());
            }

            if (!nativeResult.success()) {
                return;
            }

            if (event.getPlayer().getGameMode() != GameMode.CREATIVE
                && nativeResult.definition() != null
                && nativeResult.definition().consumeSpawnEgg()) {
                int amount = item.getAmount();
                if (amount <= 1) {
                    event.getPlayer().getInventory().setItemInMainHand(null);
                } else {
                    item.setAmount(amount - 1);
                }
            }
            return;
        }

        if (!bootstrap.getMythicMobsService().handlesSpawnEggItem(itemId)) {
            return;
        }

        MythicMobsService.EggSpawnResult mythicResult = bootstrap.getMythicMobsService().spawnFromEggItem(
            itemId,
            event.getPlayer(),
            clickedBlock,
            face
        );
        if (!mythicResult.handled()) {
            return;
        }

        event.setCancelled(true);
        if (mythicResult.message() != null && !mythicResult.message().isBlank()) {
            event.getPlayer().sendMessage(mythicResult.message());
        }

        if (!mythicResult.success()) {
            return;
        }

        if (event.getPlayer().getGameMode() != GameMode.CREATIVE
            && mythicResult.definition() != null
            && mythicResult.definition().consumeSpawnEgg()) {
            int amount = item.getAmount();
            if (amount <= 1) {
                event.getPlayer().getInventory().setItemInMainHand(null);
            } else {
                item.setAmount(amount - 1);
            }
        }
    }

    private String normalizeLoginHostname(String rawHost) {
        if (rawHost == null || rawHost.isBlank()) {
            return "";
        }

        String host = rawHost.trim();
        int nullSeparator = host.indexOf('\0');
        if (nullSeparator >= 0) {
            host = host.substring(0, nullSeparator);
        }

        if (host.startsWith("[") && host.contains("]")) {
            return host;
        }

        int colon = host.lastIndexOf(':');
        if (colon > 0) {
            String suffix = host.substring(colon + 1);
            if (suffix.matches("\\d+")) {
                host = host.substring(0, colon);
            }
        }

        return host.trim();
    }
}
