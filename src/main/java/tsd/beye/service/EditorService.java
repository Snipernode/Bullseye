package tsd.beye.service;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import tsd.beye.Bullseye;
import tsd.beye.core.Keychain;
import tsd.beye.model.BullseyeModelDefinition;
import tsd.beye.model.FurnitureDefinition;
import tsd.beye.model.SpawnerDefinition;
import tsd.beye.utils.TextUtil;

public class EditorService {
    private final Bullseye plugin;
    private final Keychain keychain;
    private final ItemService itemService;
    private final FurnitureService furnitureService;
    private final SpawnerService spawnerService;
    private final ModelEngineService modelEngineService;
    private final Map<UUID, FurnitureSession> furnitureSessions = new HashMap<>();
    private final Map<UUID, SpawnerSession> spawnerSessions = new HashMap<>();
    private final Map<UUID, ModelSession> modelSessions = new HashMap<>();
    private final Map<UUID, UUID> modelPreviewByPlayer = new HashMap<>();

    public EditorService(
        Bullseye plugin,
        Keychain keychain,
        ItemService itemService,
        FurnitureService furnitureService,
        SpawnerService spawnerService,
        ModelEngineService modelEngineService
    ) {
        this.plugin = plugin;
        this.keychain = keychain;
        this.itemService = itemService;
        this.furnitureService = furnitureService;
        this.spawnerService = spawnerService;
        this.modelEngineService = modelEngineService;
    }

    public boolean handleToolUse(PlayerInteractEvent event) {
        if (event.getPlayer() == null || event.getHand() == null) {
            return false;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) {
            return false;
        }

        ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        String type = meta.getPersistentDataContainer().get(keychain.editorToolType(), PersistentDataType.STRING);
        String id = meta.getPersistentDataContainer().get(keychain.editorToolId(), PersistentDataType.STRING);
        if (type == null || id == null || id.isBlank()) {
            return false;
        }

        Player player = event.getPlayer();
        switch (normalize(type)) {
            case "furniture" -> {
                if (event.getClickedBlock() == null) {
                    return false;
                }
                Location location = event.getClickedBlock().getRelative(event.getBlockFace()).getLocation().add(0.5D, 0.0D, 0.5D);
                Entity entity = furnitureService.spawnFurniture(location, id);
                event.setCancelled(true);
                player.sendMessage(TextUtil.colorize(entity != null
                    ? "&aPlaced furniture preview for &f" + id + "&a."
                    : "&cCould not place furniture &f" + id + "&c."));
                return true;
            }
            case "spawner" -> {
                if (event.getClickedBlock() == null) {
                    return false;
                }
                boolean placed = spawnerService.placeSpawner(event.getClickedBlock().getLocation(), id);
                event.setCancelled(true);
                player.sendMessage(TextUtil.colorize(placed
                    ? "&aPlaced spawner &f" + id + "&a."
                    : "&cCould not place spawner &f" + id + "&c."));
                return true;
            }
            case "model" -> {
                if (event.getClickedBlock() == null) {
                    return false;
                }
                previewModel(player, id, event.getClickedBlock().getRelative(event.getBlockFace()).getLocation().add(0.5D, 0.5D, 0.5D));
                event.setCancelled(true);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    public void clearPlayerState(UUID playerId) {
        furnitureSessions.remove(playerId);
        spawnerSessions.remove(playerId);
        modelSessions.remove(playerId);
        removePreview(playerId);
    }

    public String beginFurniture(UUID playerId, String id) {
        String normalizedId = normalize(id);
        FurnitureDefinition existing = furnitureService.getDefinition(normalizedId);
        FurnitureSession session = existing == null
            ? new FurnitureSession(normalizedId, normalizedId + "_item", normalizedId + "_item", "", false, 0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D, 0.0F, 0.0F, 0.0F)
            : new FurnitureSession(
                existing.id(),
                existing.itemId(),
                existing.displayItemId(),
                existing.modelId(),
                existing.seat(),
                existing.display().offsetX(),
                existing.display().offsetY(),
                existing.display().offsetZ(),
                existing.display().scaleX(),
                existing.display().scaleY(),
                existing.display().scaleZ(),
                existing.display().pitch(),
                existing.display().yaw(),
                existing.display().roll()
            );
        furnitureSessions.put(playerId, session);
        return "&aFurniture editor ready for &f" + normalizedId + "&a.";
    }

    public String beginSpawner(UUID playerId, String id) {
        String normalizedId = normalize(id);
        SpawnerDefinition existing = spawnerService.getDefinition(normalizedId);
        SpawnerSession session = existing == null
            ? new SpawnerSession(normalizedId, "", 200L, 1, 3, 20.0D, 16.0D, 2.0D, 1.0D)
            : new SpawnerSession(
                existing.id(),
                existing.mobId(),
                existing.intervalTicks(),
                existing.spawnCount(),
                existing.maxNearby(),
                existing.activationRange(),
                existing.checkRange(),
                existing.spawnRadius(),
                existing.yOffset()
            );
        spawnerSessions.put(playerId, session);
        return "&aSpawner editor ready for &f" + normalizedId + "&a.";
    }

    public String beginModel(UUID playerId, String id) {
        String normalizedId = normalize(id);
        BullseyeModelDefinition existing = modelEngineService.getDefinition(normalizedId);
        ModelSession session = existing == null
            ? new ModelSession(normalizedId, normalizedId + "_item", normalizedId, "idle", 0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D, 0.0F, 0.0F, 0.0F, "fixed", "fixed", 1.0F, 0.0F, 0.0F)
            : new ModelSession(
                existing.id(),
                existing.itemId(),
                existing.blueprint(),
                existing.defaultAnimation(),
                existing.offsetX(),
                existing.offsetY(),
                existing.offsetZ(),
                existing.scaleX(),
                existing.scaleY(),
                existing.scaleZ(),
                existing.pitch(),
                existing.yaw(),
                existing.roll(),
                existing.billboard().name().toLowerCase(Locale.ROOT),
                existing.transform().name().toLowerCase(Locale.ROOT),
                existing.viewRange(),
                existing.shadowRadius(),
                existing.shadowStrength()
            );
        modelSessions.put(playerId, session);
        return "&aModel editor ready for &f" + normalizedId + "&a.";
    }

    public String setField(UUID playerId, String type, String field, String value) {
        return switch (normalize(type)) {
            case "furniture" -> setFurnitureField(playerId, field, value);
            case "spawner" -> setSpawnerField(playerId, field, value);
            case "model" -> setModelField(playerId, field, value);
            default -> "&cUnknown editor type.";
        };
    }

    public List<String> show(UUID playerId, String type) {
        return switch (normalize(type)) {
            case "furniture" -> describeFurniture(furnitureSessions.get(playerId));
            case "spawner" -> describeSpawner(spawnerSessions.get(playerId));
            case "model" -> describeModel(modelSessions.get(playerId));
            default -> List.of("&cUnknown editor type.");
        };
    }

    public String currentSessionId(UUID playerId, String type) {
        return switch (normalize(type)) {
            case "furniture" -> furnitureSessions.containsKey(playerId) ? furnitureSessions.get(playerId).id : "";
            case "spawner" -> spawnerSessions.containsKey(playerId) ? spawnerSessions.get(playerId).id : "";
            case "model" -> modelSessions.containsKey(playerId) ? modelSessions.get(playerId).id : "";
            default -> "";
        };
    }

    public String save(UUID playerId, String type) {
        return switch (normalize(type)) {
            case "furniture" -> saveFurniture(playerId);
            case "spawner" -> saveSpawner(playerId);
            case "model" -> saveModel(playerId);
            default -> "&cUnknown editor type.";
        };
    }

    public ItemStack createTool(String type, String id) {
        String normalizedType = normalize(type);
        Material material = switch (normalizedType) {
            case "furniture" -> Material.STICK;
            case "spawner" -> Material.BLAZE_ROD;
            case "model" -> Material.ITEM_FRAME;
            default -> Material.PAPER;
        };

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        meta.setDisplayName(TextUtil.colorize("&6Bullseye " + normalizedType + " tool &7[" + id + "]"));
        meta.setLore(TextUtil.colorize(List.of(
            "&7Right-click a block to use this editor tool.",
            "&7Type: &f" + normalizedType,
            "&7ID: &f" + id
        )));
        meta.getPersistentDataContainer().set(keychain.editorToolType(), PersistentDataType.STRING, normalizedType);
        meta.getPersistentDataContainer().set(keychain.editorToolId(), PersistentDataType.STRING, normalize(id));
        item.setItemMeta(meta);
        return item;
    }

    private String setFurnitureField(UUID playerId, String field, String value) {
        FurnitureSession session = furnitureSessions.get(playerId);
        if (session == null) {
            return "&cStart a furniture session first.";
        }

        String normalizedField = normalize(field);
        return switch (normalizedField) {
            case "item", "item-id" -> {
                session.itemId = normalize(value);
                yield "&aFurniture item set to &f" + session.itemId + "&a.";
            }
            case "display", "display-item" -> {
                session.displayItemId = normalize(value);
                yield "&aFurniture display item set to &f" + session.displayItemId + "&a.";
            }
            case "model", "model-id" -> {
                session.modelId = normalize(value);
                yield "&aFurniture model set to &f" + session.modelId + "&a.";
            }
            case "seat" -> {
                session.seat = Boolean.parseBoolean(value);
                yield "&aFurniture seat set to &f" + session.seat + "&a.";
            }
            case "offset" -> setTriple(value, (x, y, z) -> {
                session.offsetX = x;
                session.offsetY = y;
                session.offsetZ = z;
            }, "furniture offset");
            case "scale" -> setTriple(value, (x, y, z) -> {
                session.scaleX = x;
                session.scaleY = y;
                session.scaleZ = z;
            }, "furniture scale");
            case "rotation" -> setRotation(value, (pitch, yaw, roll) -> {
                session.pitch = pitch;
                session.yaw = yaw;
                session.roll = roll;
            }, "furniture rotation");
            default -> "&cUnknown furniture field.";
        };
    }

    private String setSpawnerField(UUID playerId, String field, String value) {
        SpawnerSession session = spawnerSessions.get(playerId);
        if (session == null) {
            return "&cStart a spawner session first.";
        }

        String normalizedField = normalize(field);
        try {
            return switch (normalizedField) {
                case "mob" -> {
                    session.mobId = normalize(value);
                    yield "&aSpawner mob set to &f" + session.mobId + "&a.";
                }
                case "interval" -> {
                    session.interval = Long.parseLong(value);
                    yield "&aSpawner interval set to &f" + session.interval + "&a.";
                }
                case "spawn-count" -> {
                    session.spawnCount = Integer.parseInt(value);
                    yield "&aSpawner spawn-count set to &f" + session.spawnCount + "&a.";
                }
                case "max-nearby" -> {
                    session.maxNearby = Integer.parseInt(value);
                    yield "&aSpawner max-nearby set to &f" + session.maxNearby + "&a.";
                }
                case "activation-range" -> {
                    session.activationRange = Double.parseDouble(value);
                    yield "&aSpawner activation-range set to &f" + session.activationRange + "&a.";
                }
                case "check-range" -> {
                    session.checkRange = Double.parseDouble(value);
                    yield "&aSpawner check-range set to &f" + session.checkRange + "&a.";
                }
                case "spawn-radius" -> {
                    session.spawnRadius = Double.parseDouble(value);
                    yield "&aSpawner spawn-radius set to &f" + session.spawnRadius + "&a.";
                }
                case "y-offset" -> {
                    session.yOffset = Double.parseDouble(value);
                    yield "&aSpawner y-offset set to &f" + session.yOffset + "&a.";
                }
                default -> "&cUnknown spawner field.";
            };
        } catch (NumberFormatException ex) {
            return "&cInvalid numeric value.";
        }
    }

    private String setModelField(UUID playerId, String field, String value) {
        ModelSession session = modelSessions.get(playerId);
        if (session == null) {
            return "&cStart a model session first.";
        }

        String normalizedField = normalize(field);
        try {
            return switch (normalizedField) {
                case "item" -> {
                    session.itemId = normalize(value);
                    yield "&aModel item set to &f" + session.itemId + "&a.";
                }
                case "blueprint" -> {
                    session.blueprint = normalize(value);
                    yield "&aModel blueprint set to &f" + session.blueprint + "&a.";
                }
                case "animation", "default-animation" -> {
                    session.defaultAnimation = normalize(value);
                    yield "&aModel animation set to &f" + session.defaultAnimation + "&a.";
                }
                case "offset" -> setTriple(value, (x, y, z) -> {
                    session.offsetX = x;
                    session.offsetY = y;
                    session.offsetZ = z;
                }, "model offset");
                case "scale" -> setTriple(value, (x, y, z) -> {
                    session.scaleX = x;
                    session.scaleY = y;
                    session.scaleZ = z;
                }, "model scale");
                case "rotation" -> setRotation(value, (pitch, yaw, roll) -> {
                    session.pitch = pitch;
                    session.yaw = yaw;
                    session.roll = roll;
                }, "model rotation");
                case "billboard" -> {
                    session.billboard = normalize(value);
                    yield "&aModel billboard set to &f" + session.billboard + "&a.";
                }
                case "transform" -> {
                    session.transform = normalize(value);
                    yield "&aModel transform set to &f" + session.transform + "&a.";
                }
                case "view-range" -> {
                    session.viewRange = Float.parseFloat(value);
                    yield "&aModel view-range set to &f" + session.viewRange + "&a.";
                }
                case "shadow-radius" -> {
                    session.shadowRadius = Float.parseFloat(value);
                    yield "&aModel shadow-radius set to &f" + session.shadowRadius + "&a.";
                }
                case "shadow-strength" -> {
                    session.shadowStrength = Float.parseFloat(value);
                    yield "&aModel shadow-strength set to &f" + session.shadowStrength + "&a.";
                }
                default -> "&cUnknown model field.";
            };
        } catch (NumberFormatException ex) {
            return "&cInvalid numeric value.";
        }
    }

    private String saveFurniture(UUID playerId) {
        FurnitureSession session = furnitureSessions.get(playerId);
        if (session == null) {
            return "&cStart a furniture session first.";
        }

        File file = dataFile("furniture.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String path = "furniture." + session.id;
        yaml.set(path + ".item-id", session.itemId);
        yaml.set(path + ".display-item", session.displayItemId);
        yaml.set(path + ".model-id", session.modelId.isBlank() ? null : session.modelId);
        yaml.set(path + ".seat", session.seat);
        yaml.set(path + ".display.offset.x", session.offsetX);
        yaml.set(path + ".display.offset.y", session.offsetY);
        yaml.set(path + ".display.offset.z", session.offsetZ);
        yaml.set(path + ".display.scale.x", session.scaleX);
        yaml.set(path + ".display.scale.y", session.scaleY);
        yaml.set(path + ".display.scale.z", session.scaleZ);
        yaml.set(path + ".display.rotation.pitch", session.pitch);
        yaml.set(path + ".display.rotation.yaw", session.yaw);
        yaml.set(path + ".display.rotation.roll", session.roll);
        return saveYaml(file, yaml, true);
    }

    private String saveSpawner(UUID playerId) {
        SpawnerSession session = spawnerSessions.get(playerId);
        if (session == null) {
            return "&cStart a spawner session first.";
        }

        File file = dataFile("spawners.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String path = "spawners." + session.id;
        yaml.set(path + ".mob", session.mobId);
        yaml.set(path + ".interval", session.interval);
        yaml.set(path + ".spawn-count", session.spawnCount);
        yaml.set(path + ".max-nearby", session.maxNearby);
        yaml.set(path + ".activation-range", session.activationRange);
        yaml.set(path + ".check-range", session.checkRange);
        yaml.set(path + ".spawn-radius", session.spawnRadius);
        yaml.set(path + ".y-offset", session.yOffset);
        return saveYaml(file, yaml, false);
    }

    private String saveModel(UUID playerId) {
        ModelSession session = modelSessions.get(playerId);
        if (session == null) {
            return "&cStart a model session first.";
        }

        File file = dataFile("models.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String path = "models." + session.id;
        yaml.set(path + ".item", session.itemId);
        yaml.set(path + ".blueprint", session.blueprint);
        yaml.set(path + ".default-animation", session.defaultAnimation);
        yaml.set(path + ".offset.x", session.offsetX);
        yaml.set(path + ".offset.y", session.offsetY);
        yaml.set(path + ".offset.z", session.offsetZ);
        yaml.set(path + ".scale.x", session.scaleX);
        yaml.set(path + ".scale.y", session.scaleY);
        yaml.set(path + ".scale.z", session.scaleZ);
        yaml.set(path + ".rotation.pitch", session.pitch);
        yaml.set(path + ".rotation.yaw", session.yaw);
        yaml.set(path + ".rotation.roll", session.roll);
        yaml.set(path + ".billboard", session.billboard.toUpperCase(Locale.ROOT));
        yaml.set(path + ".transform", session.transform.toUpperCase(Locale.ROOT));
        yaml.set(path + ".view-range", session.viewRange);
        yaml.set(path + ".shadow-radius", session.shadowRadius);
        yaml.set(path + ".shadow-strength", session.shadowStrength);
        return saveYaml(file, yaml, true);
    }

    private String saveYaml(File file, YamlConfiguration yaml, boolean rebuildPack) {
        try {
            yaml.save(file);
            plugin.getBootstrap().getContentService().loadAll(false);
            if (rebuildPack) {
                plugin.getBootstrap().getPackGenerator().generatePack(false);
            }
            return "&aSaved editor changes to &f" + file.getName() + "&a.";
        } catch (IOException ex) {
            return "&cFailed to save editor file: &f" + ex.getMessage();
        }
    }

    private void previewModel(Player player, String modelId, Location location) {
        BullseyeModelDefinition definition = modelEngineService.getDefinition(modelId);
        if (definition == null) {
            player.sendMessage(TextUtil.colorize("&cUnknown model id: &f" + modelId));
            return;
        }

        ItemStack item = itemService.createItem(definition.itemId(), 1);
        if (item == null) {
            player.sendMessage(TextUtil.colorize("&cModel item is missing for &f" + modelId));
            return;
        }

        removePreview(player.getUniqueId());
        ItemDisplay display = location.getWorld().spawn(location.clone().add(definition.offsetX(), definition.offsetY(), definition.offsetZ()), ItemDisplay.class);
        display.setItemStack(item);
        display.setBillboard(definition.billboard());
        display.setItemDisplayTransform(definition.transform());
        display.setViewRange(definition.viewRange());
        display.setShadowRadius(definition.shadowRadius());
        display.setShadowStrength(definition.shadowStrength());
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(1);
        display.setTeleportDuration(1);
        display.setGravity(false);
        display.setPersistent(false);
        display.setTransformation(new Transformation(
            new Vector3f(),
            new Quaternionf().rotateXYZ(
                (float) Math.toRadians(definition.pitch()),
                (float) Math.toRadians(definition.yaw()),
                (float) Math.toRadians(definition.roll())
            ),
            new Vector3f((float) definition.scaleX(), (float) definition.scaleY(), (float) definition.scaleZ()),
            new Quaternionf()
        ));
        modelPreviewByPlayer.put(player.getUniqueId(), display.getUniqueId());
        player.sendMessage(TextUtil.colorize("&aSpawned model preview for &f" + modelId + "&a."));
    }

    private void removePreview(UUID playerId) {
        UUID previewId = modelPreviewByPlayer.remove(playerId);
        if (previewId == null) {
            return;
        }
        Entity entity = plugin.getServer().getEntity(previewId);
        if (entity != null && entity.isValid()) {
            entity.remove();
        }
    }

    private List<String> describeFurniture(FurnitureSession session) {
        if (session == null) {
            return List.of("&cStart a furniture session first.");
        }
        return List.of(
            "&6Furniture Editor: &f" + session.id,
            "&7item-id: &f" + session.itemId,
            "&7display-item: &f" + session.displayItemId,
            "&7model-id: &f" + session.modelId,
            "&7seat: &f" + session.seat,
            "&7offset: &f" + session.offsetX + ", " + session.offsetY + ", " + session.offsetZ,
            "&7scale: &f" + session.scaleX + ", " + session.scaleY + ", " + session.scaleZ,
            "&7rotation: &f" + session.pitch + ", " + session.yaw + ", " + session.roll
        );
    }

    private List<String> describeSpawner(SpawnerSession session) {
        if (session == null) {
            return List.of("&cStart a spawner session first.");
        }
        return List.of(
            "&6Spawner Editor: &f" + session.id,
            "&7mob: &f" + session.mobId,
            "&7interval: &f" + session.interval,
            "&7spawn-count: &f" + session.spawnCount,
            "&7max-nearby: &f" + session.maxNearby,
            "&7activation-range: &f" + session.activationRange,
            "&7check-range: &f" + session.checkRange,
            "&7spawn-radius: &f" + session.spawnRadius,
            "&7y-offset: &f" + session.yOffset
        );
    }

    private List<String> describeModel(ModelSession session) {
        if (session == null) {
            return List.of("&cStart a model session first.");
        }
        return List.of(
            "&6Model Editor: &f" + session.id,
            "&7item: &f" + session.itemId,
            "&7blueprint: &f" + session.blueprint,
            "&7animation: &f" + session.defaultAnimation,
            "&7offset: &f" + session.offsetX + ", " + session.offsetY + ", " + session.offsetZ,
            "&7scale: &f" + session.scaleX + ", " + session.scaleY + ", " + session.scaleZ,
            "&7rotation: &f" + session.pitch + ", " + session.yaw + ", " + session.roll,
            "&7billboard: &f" + session.billboard,
            "&7transform: &f" + session.transform
        );
    }

    private String setTriple(String raw, TripleSetter setter, String label) {
        String[] split = raw.trim().split("\\s+");
        if (split.length != 3) {
            return "&cUse three numeric values for " + label + ".";
        }
        try {
            setter.apply(Double.parseDouble(split[0]), Double.parseDouble(split[1]), Double.parseDouble(split[2]));
            return "&aUpdated " + label + ".";
        } catch (NumberFormatException ex) {
            return "&cInvalid numeric value.";
        }
    }

    private String setRotation(String raw, RotationSetter setter, String label) {
        String[] split = raw.trim().split("\\s+");
        if (split.length != 3) {
            return "&cUse three numeric values for " + label + ".";
        }
        try {
            setter.apply(Float.parseFloat(split[0]), Float.parseFloat(split[1]), Float.parseFloat(split[2]));
            return "&aUpdated " + label + ".";
        } catch (NumberFormatException ex) {
            return "&cInvalid numeric value.";
        }
    }

    private File dataFile(String name) {
        return plugin.getDataFolder().toPath().resolve(name).toFile();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    @FunctionalInterface
    private interface TripleSetter {
        void apply(double x, double y, double z);
    }

    @FunctionalInterface
    private interface RotationSetter {
        void apply(float pitch, float yaw, float roll);
    }

    private static final class FurnitureSession {
        private final String id;
        private String itemId;
        private String displayItemId;
        private String modelId;
        private boolean seat;
        private double offsetX;
        private double offsetY;
        private double offsetZ;
        private double scaleX;
        private double scaleY;
        private double scaleZ;
        private float pitch;
        private float yaw;
        private float roll;

        private FurnitureSession(
            String id,
            String itemId,
            String displayItemId,
            String modelId,
            boolean seat,
            double offsetX,
            double offsetY,
            double offsetZ,
            double scaleX,
            double scaleY,
            double scaleZ,
            float pitch,
            float yaw,
            float roll
        ) {
            this.id = id;
            this.itemId = itemId;
            this.displayItemId = displayItemId;
            this.modelId = modelId;
            this.seat = seat;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.offsetZ = offsetZ;
            this.scaleX = scaleX;
            this.scaleY = scaleY;
            this.scaleZ = scaleZ;
            this.pitch = pitch;
            this.yaw = yaw;
            this.roll = roll;
        }
    }

    private static final class SpawnerSession {
        private final String id;
        private String mobId;
        private long interval;
        private int spawnCount;
        private int maxNearby;
        private double activationRange;
        private double checkRange;
        private double spawnRadius;
        private double yOffset;

        private SpawnerSession(
            String id,
            String mobId,
            long interval,
            int spawnCount,
            int maxNearby,
            double activationRange,
            double checkRange,
            double spawnRadius,
            double yOffset
        ) {
            this.id = id;
            this.mobId = mobId;
            this.interval = interval;
            this.spawnCount = spawnCount;
            this.maxNearby = maxNearby;
            this.activationRange = activationRange;
            this.checkRange = checkRange;
            this.spawnRadius = spawnRadius;
            this.yOffset = yOffset;
        }
    }

    private static final class ModelSession {
        private final String id;
        private String itemId;
        private String blueprint;
        private String defaultAnimation;
        private double offsetX;
        private double offsetY;
        private double offsetZ;
        private double scaleX;
        private double scaleY;
        private double scaleZ;
        private float pitch;
        private float yaw;
        private float roll;
        private String billboard;
        private String transform;
        private float viewRange;
        private float shadowRadius;
        private float shadowStrength;

        private ModelSession(
            String id,
            String itemId,
            String blueprint,
            String defaultAnimation,
            double offsetX,
            double offsetY,
            double offsetZ,
            double scaleX,
            double scaleY,
            double scaleZ,
            float pitch,
            float yaw,
            float roll,
            String billboard,
            String transform,
            float viewRange,
            float shadowRadius,
            float shadowStrength
        ) {
            this.id = id;
            this.itemId = itemId;
            this.blueprint = blueprint;
            this.defaultAnimation = defaultAnimation;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.offsetZ = offsetZ;
            this.scaleX = scaleX;
            this.scaleY = scaleY;
            this.scaleZ = scaleZ;
            this.pitch = pitch;
            this.yaw = yaw;
            this.roll = roll;
            this.billboard = billboard;
            this.transform = transform;
            this.viewRange = viewRange;
            this.shadowRadius = shadowRadius;
            this.shadowStrength = shadowStrength;
        }
    }
}
