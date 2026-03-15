package tsd.beye.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import tsd.beye.Bullseye;
import tsd.beye.core.Keychain;
import tsd.beye.model.BullseyeModelDefinition;
import tsd.beye.model.CustomMobDefinition;
import tsd.beye.model.TriggerContext;
import tsd.beye.model.TriggerType;
import tsd.beye.utils.TextUtil;

public class MobService {
    private final Bullseye plugin;
    private final ItemService itemService;
    private final Keychain keychain;
    private final Map<String, CustomMobDefinition> definitions = new LinkedHashMap<>();
    private final Map<String, String> spawnEggToMob = new LinkedHashMap<>();
    private final Map<UUID, UUID> modelDisplayByHost = new LinkedHashMap<>();
    private final Map<UUID, UUID> modelHostByDisplay = new LinkedHashMap<>();

    private MechanicService mechanicService;
    private SkillService skillService;
    private DropTableService dropTableService;
    private ModelEngineService modelEngineService;
    private BukkitTask syncTask;

    public MobService(Bullseye plugin, ItemService itemService, Keychain keychain) {
        this.plugin = plugin;
        this.itemService = itemService;
        this.keychain = keychain;
        ensureSyncTask();
    }

    public void setMechanicService(MechanicService mechanicService) {
        this.mechanicService = mechanicService;
    }

    public void setSkillService(SkillService skillService) {
        this.skillService = skillService;
    }

    public void setDropTableService(DropTableService dropTableService) {
        this.dropTableService = dropTableService;
    }

    public void setModelEngineService(ModelEngineService modelEngineService) {
        this.modelEngineService = modelEngineService;
    }

    public void load(YamlConfiguration config) {
        definitions.clear();
        spawnEggToMob.clear();
        ensureSyncTask();

        ConfigurationSection root = config.getConfigurationSection("mobs");
        if (root == null) {
            return;
        }

        for (String rawId : root.getKeys(false)) {
            ConfigurationSection mobSection = root.getConfigurationSection(rawId);
            if (mobSection == null) {
                continue;
            }

            String id = normalize(rawId);
            EntityType entityType = parseEntityType(mobSection.getString("entity-type"), EntityType.ZOMBIE);
            String displayName = mobSection.getString("display-name", rawId);
            String spawnEggItemId = normalize(mobSection.getString("spawn-egg-item", ""));
            boolean consumeSpawnEgg = mobSection.getBoolean("consume-spawn-egg", true);
            double health = Math.max(1.0D, mobSection.getDouble("health", 20.0D));
            double damage = Math.max(0.0D, mobSection.getDouble("damage", 2.0D));
            double movementSpeed = Math.max(0.001D, mobSection.getDouble("movement-speed", 0.25D));
            boolean useAi = mobSection.getBoolean("use-ai", true);
            boolean silent = mobSection.getBoolean("silent", false);
            boolean hideBaseEntity = mobSection.getBoolean("hide-base-entity", false);
            String dropTableId = normalize(mobSection.getString("drop-table", ""));
            String modelId = normalize(mobSection.getString("model-id", ""));
            List<String> skills = List.copyOf(mobSection.getStringList("skills"));
            List<String> mechanics = List.copyOf(mobSection.getStringList("mechanics"));

            ConfigurationSection modelSection = mobSection.getConfigurationSection("model");
            CustomMobDefinition.ModelDefinition model = CustomMobDefinition.ModelDefinition.disabled();
            if (modelSection != null) {
                model = new CustomMobDefinition.ModelDefinition(
                    normalize(modelSection.getString("item", "")),
                    modelSection.getDouble("offset.x", 0.0D),
                    modelSection.getDouble("offset.y", 0.0D),
                    modelSection.getDouble("offset.z", 0.0D),
                    modelSection.getDouble("scale.x", 1.0D),
                    modelSection.getDouble("scale.y", 1.0D),
                    modelSection.getDouble("scale.z", 1.0D),
                    (float) modelSection.getDouble("rotation.pitch", 0.0D),
                    (float) modelSection.getDouble("rotation.yaw", 0.0D),
                    (float) modelSection.getDouble("rotation.roll", 0.0D),
                    parseEnum(Display.Billboard.class, modelSection.getString("billboard"), Display.Billboard.FIXED),
                    parseEnum(ItemDisplay.ItemDisplayTransform.class, modelSection.getString("transform"), ItemDisplay.ItemDisplayTransform.FIXED),
                    (float) modelSection.getDouble("view-range", 1.0D),
                    (float) modelSection.getDouble("shadow-radius", 0.0D),
                    (float) modelSection.getDouble("shadow-strength", 0.0D)
                );
            }

            CustomMobDefinition definition = new CustomMobDefinition(
                id,
                entityType,
                displayName,
                spawnEggItemId,
                consumeSpawnEgg,
                health,
                damage,
                movementSpeed,
                useAi,
                silent,
                hideBaseEntity,
                dropTableId,
                modelId,
                skills,
                mechanics,
                model
            );
            definitions.put(id, definition);
            if (!spawnEggItemId.isBlank()) {
                spawnEggToMob.put(spawnEggItemId, id);
            }
        }

        plugin.getLogger().info("Loaded " + definitions.size() + " custom mobs.");
    }

    public Collection<String> getMobIds() {
        return Collections.unmodifiableSet(definitions.keySet());
    }

    public Collection<CustomMobDefinition> getDefinitions() {
        return Collections.unmodifiableCollection(definitions.values());
    }

    public CustomMobDefinition getDefinition(String mobId) {
        if (mobId == null) {
            return null;
        }
        return definitions.get(normalize(mobId));
    }

    public boolean handlesSpawnEggItem(String itemId) {
        return itemId != null && spawnEggToMob.containsKey(normalize(itemId));
    }

    public SpawnResult spawnFromEggItem(String itemId, Player player, Block clickedBlock, BlockFace face) {
        if (itemId == null || itemId.isBlank()) {
            return SpawnResult.unhandled();
        }

        String mobId = spawnEggToMob.get(normalize(itemId));
        if (mobId == null) {
            return SpawnResult.unhandled();
        }

        Location spawnLocation = resolveSpawnLocation(player, clickedBlock, face);
        return spawnMob(mobId, spawnLocation, player);
    }

    public SpawnResult spawnMob(String mobId, Location location) {
        return spawnMob(mobId, location, null);
    }

    public SpawnResult spawnMob(String mobId, Location location, Player sourcePlayer) {
        CustomMobDefinition definition = getDefinition(mobId);
        if (definition == null) {
            return SpawnResult.failure(null, "Unknown Bullseye mob id.");
        }
        if (location == null || location.getWorld() == null) {
            return SpawnResult.failure(definition, "Cannot spawn mob without a valid world location.");
        }

        Entity spawned = location.getWorld().spawnEntity(location, definition.entityType());
        if (!(spawned instanceof LivingEntity livingEntity)) {
            spawned.remove();
            return SpawnResult.failure(definition, "Configured entity type is not a living entity.");
        }

        configureLivingEntity(livingEntity, definition);
        livingEntity.getPersistentDataContainer().set(keychain.mobId(), PersistentDataType.STRING, definition.id());
        attachModel(livingEntity, definition);
        executeSpawnMechanics(livingEntity, definition, sourcePlayer);

        return SpawnResult.success(definition, livingEntity, "&aSpawned &f" + definition.displayName() + "&a.");
    }

    public String getMobId(Entity entity) {
        if (entity == null) {
            return null;
        }

        String mobId = entity.getPersistentDataContainer().get(keychain.mobId(), PersistentDataType.STRING);
        if (mobId != null) {
            return mobId;
        }

        Entity root = resolveRootEntity(entity);
        if (root == null || root == entity) {
            return null;
        }
        return root.getPersistentDataContainer().get(keychain.mobId(), PersistentDataType.STRING);
    }

    public boolean isCustomMob(Entity entity) {
        return getMobId(entity) != null;
    }

    public Entity getAttachedModelDisplay(Entity host) {
        if (host == null) {
            return null;
        }
        UUID displayId = modelDisplayByHost.get(host.getUniqueId());
        return displayId == null ? null : Bukkit.getEntity(displayId);
    }

    public Entity resolveRootEntity(Entity entity) {
        if (entity == null) {
            return null;
        }

        if (entity.getPersistentDataContainer().has(keychain.mobId(), PersistentDataType.STRING)) {
            return entity;
        }

        String hostId = entity.getPersistentDataContainer().get(keychain.mobModelHost(), PersistentDataType.STRING);
        if (hostId != null && !hostId.isBlank()) {
            try {
                return Bukkit.getEntity(UUID.fromString(hostId));
            } catch (IllegalArgumentException ignored) {
            }
        }

        UUID mappedHost = modelHostByDisplay.get(entity.getUniqueId());
        return mappedHost == null ? null : Bukkit.getEntity(mappedHost);
    }

    public void removeMobVisuals(Entity host) {
        if (host == null) {
            return;
        }

        UUID hostId = host.getUniqueId();
        UUID displayId = modelDisplayByHost.remove(hostId);
        if (displayId != null) {
            modelHostByDisplay.remove(displayId);
            Entity display = Bukkit.getEntity(displayId);
            if (display != null && display.isValid()) {
                display.remove();
            }
        }
    }

    public void stop() {
        if (syncTask != null) {
            syncTask.cancel();
            syncTask = null;
        }

        for (UUID displayId : new ArrayList<>(modelHostByDisplay.keySet())) {
            Entity display = Bukkit.getEntity(displayId);
            if (display != null && display.isValid()) {
                display.remove();
            }
        }
        modelDisplayByHost.clear();
        modelHostByDisplay.clear();
    }

    public void dropConfiguredLoot(LivingEntity entity, CustomMobDefinition definition) {
        if (dropTableService == null || entity == null || definition == null || definition.dropTableId().isBlank()) {
            return;
        }

        dropTableService.dropTable(definition.dropTableId(), entity.getLocation());
    }

    private void executeSpawnMechanics(LivingEntity livingEntity, CustomMobDefinition definition, Player sourcePlayer) {
        if (mechanicService == null || definition.mechanics().isEmpty()) {
            if (skillService == null || definition.skills().isEmpty()) {
                return;
            }
        }

        TriggerContext context = TriggerContext.builder()
            .player(sourcePlayer)
            .target(livingEntity)
            .sourceId(definition.id())
            .build();
        if (mechanicService != null) {
            mechanicService.executeMechanics(definition.mechanics(), TriggerType.MOB_SPAWN, context);
        }
        if (skillService != null) {
            skillService.executeSkills(definition.skills(), TriggerType.MOB_SPAWN, context);
        }
    }

    private void configureLivingEntity(LivingEntity entity, CustomMobDefinition definition) {
        entity.setCustomName(TextUtil.colorize(definition.displayName()));
        entity.setCustomNameVisible(!definition.hideBaseEntity());
        entity.setSilent(definition.silent());
        entity.setAI(definition.useAi());
        entity.setInvisible(definition.hideBaseEntity());
        entity.setRemoveWhenFarAway(false);

        if (entity instanceof Mob mob) {
            mob.setCanPickupItems(false);
        }

        setAttribute(entity, Attribute.MAX_HEALTH, definition.health());
        setAttribute(entity, Attribute.ATTACK_DAMAGE, definition.damage());
        setAttribute(entity, Attribute.MOVEMENT_SPEED, definition.movementSpeed());

        double health = definition.health();
        if (entity.getAttribute(Attribute.MAX_HEALTH) != null) {
            health = Math.min(health, entity.getAttribute(Attribute.MAX_HEALTH).getBaseValue());
        }
        entity.setHealth(Math.max(1.0D, health));
    }

    private void attachModel(LivingEntity host, CustomMobDefinition definition) {
        removeMobVisuals(host);
        CustomMobDefinition.ModelDefinition model = resolveModel(definition);
        if (model == null || !model.enabled()) {
            return;
        }

        ItemStack modelItem = itemService.createItem(model.itemId(), 1);
        if (modelItem == null) {
            plugin.getLogger().warning("Mob '" + definition.id() + "' references unknown model item '" + model.itemId() + "'.");
            return;
        }

        Location spawnLocation = modelLocation(host.getLocation(), model);
        ItemDisplay display = spawnLocation.getWorld().spawn(spawnLocation, ItemDisplay.class);
        display.setItemStack(modelItem);
        display.setItemDisplayTransform(model.transform());
        display.setBillboard(model.billboard());
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(1);
        display.setTeleportDuration(1);
        display.setViewRange(model.viewRange());
        display.setShadowRadius(model.shadowRadius());
        display.setShadowStrength(model.shadowStrength());
        display.setPersistent(false);
        display.setInvulnerable(true);
        display.setGravity(false);
        display.setTransformation(new Transformation(
            new Vector3f(),
            new Quaternionf().rotateXYZ(
                (float) Math.toRadians(model.pitch()),
                (float) Math.toRadians(model.yaw()),
                (float) Math.toRadians(model.roll())
            ),
            new Vector3f((float) model.scaleX(), (float) model.scaleY(), (float) model.scaleZ()),
            new Quaternionf()
        ));
        display.getPersistentDataContainer().set(keychain.mobModelHost(), PersistentDataType.STRING, host.getUniqueId().toString());
        host.getPersistentDataContainer().set(keychain.mobModelDisplay(), PersistentDataType.STRING, display.getUniqueId().toString());

        modelDisplayByHost.put(host.getUniqueId(), display.getUniqueId());
        modelHostByDisplay.put(display.getUniqueId(), host.getUniqueId());
    }

    private void ensureSyncTask() {
        if (syncTask != null) {
            return;
        }

        syncTask = Bukkit.getScheduler().runTaskTimer(plugin, this::syncDisplays, 1L, 1L);
    }

    private void syncDisplays() {
        if (modelDisplayByHost.isEmpty()) {
            return;
        }

        for (Map.Entry<UUID, UUID> entry : new ArrayList<>(modelDisplayByHost.entrySet())) {
            Entity host = Bukkit.getEntity(entry.getKey());
            Entity displayEntity = Bukkit.getEntity(entry.getValue());

            if (!(host instanceof LivingEntity livingHost) || !(displayEntity instanceof ItemDisplay display)) {
                cleanupLink(entry.getKey(), entry.getValue(), displayEntity);
                continue;
            }

            if (!livingHost.isValid() || livingHost.isDead()) {
                cleanupLink(entry.getKey(), entry.getValue(), display);
                continue;
            }

            CustomMobDefinition definition = getDefinition(getMobId(livingHost));
            CustomMobDefinition.ModelDefinition model = definition == null ? null : resolveModel(definition);
            if (definition == null || model == null || !model.enabled()) {
                cleanupLink(entry.getKey(), entry.getValue(), display);
                continue;
            }

            Location target = modelLocation(livingHost.getLocation(), model);
            target.setYaw(livingHost.getLocation().getYaw());
            target.setPitch(0.0F);
            if (!target.getWorld().equals(display.getWorld())) {
                cleanupLink(entry.getKey(), entry.getValue(), display);
                continue;
            }

            display.teleport(target);
        }
    }

    private void cleanupLink(UUID hostId, UUID displayId, Entity displayEntity) {
        modelDisplayByHost.remove(hostId);
        modelHostByDisplay.remove(displayId);
        Entity display = displayEntity != null ? displayEntity : Bukkit.getEntity(displayId);
        if (display != null && display.isValid()) {
            display.remove();
        }

        Entity host = Bukkit.getEntity(hostId);
        if (host != null && host.isValid()) {
            host.getPersistentDataContainer().remove(keychain.mobModelDisplay());
        }
    }

    private void setAttribute(LivingEntity entity, Attribute attribute, double value) {
        if (entity.getAttribute(attribute) == null) {
            return;
        }
        entity.getAttribute(attribute).setBaseValue(value);
    }

    private Location modelLocation(Location base, CustomMobDefinition.ModelDefinition model) {
        return base.clone().add(model.offsetX(), model.offsetY(), model.offsetZ());
    }

    private CustomMobDefinition.ModelDefinition resolveModel(CustomMobDefinition definition) {
        if (definition == null) {
            return null;
        }
        if (!definition.modelId().isBlank() && modelEngineService != null) {
            BullseyeModelDefinition modelDefinition = modelEngineService.getDefinition(definition.modelId());
            if (modelDefinition != null) {
                return modelDefinition.toRuntimeModel();
            }
        }
        return definition.model();
    }

    private Location resolveSpawnLocation(Player player, Block clickedBlock, BlockFace face) {
        if (clickedBlock != null && face != null) {
            return clickedBlock.getRelative(face).getLocation().add(0.5D, 0.0D, 0.5D);
        }

        if (player != null) {
            return player.getLocation().add(player.getLocation().getDirection().normalize().multiply(2.0D));
        }

        return plugin.getServer().getWorlds().isEmpty()
            ? null
            : plugin.getServer().getWorlds().get(0).getSpawnLocation();
    }

    private EntityType parseEntityType(String value, EntityType fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }

        try {
            return EntityType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Unknown mob entity-type '" + value + "', using " + fallback.name());
            return fallback;
        }
    }

    private <E extends Enum<E>> E parseEnum(Class<E> enumType, String value, E fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }

        try {
            return Enum.valueOf(enumType, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public record SpawnResult(boolean handled, boolean success, CustomMobDefinition definition, Entity entity, String message) {
        public static SpawnResult unhandled() {
            return new SpawnResult(false, false, null, null, "");
        }

        public static SpawnResult success(CustomMobDefinition definition, Entity entity, String message) {
            return new SpawnResult(true, true, definition, entity, message);
        }

        public static SpawnResult failure(CustomMobDefinition definition, String message) {
            return new SpawnResult(true, false, definition, null, message);
        }
    }
}
