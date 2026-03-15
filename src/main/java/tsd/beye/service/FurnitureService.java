package tsd.beye.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import tsd.beye.Bullseye;
import tsd.beye.core.Keychain;
import tsd.beye.model.BullseyeModelDefinition;
import tsd.beye.model.FurnitureDefinition;

public class FurnitureService {
    private final Bullseye plugin;
    private final ItemService itemService;
    private final Keychain keychain;
    private final Map<String, FurnitureDefinition> furnitureDefinitions = new LinkedHashMap<>();
    private final Map<String, String> furnitureByItemId = new LinkedHashMap<>();
    private final Map<UUID, UUID> displayByHost = new LinkedHashMap<>();
    private final Map<UUID, UUID> hostByDisplay = new LinkedHashMap<>();

    private ModelEngineService modelEngineService;

    public FurnitureService(Bullseye plugin, ItemService itemService, Keychain keychain) {
        this.plugin = plugin;
        this.itemService = itemService;
        this.keychain = keychain;
    }

    public void setModelEngineService(ModelEngineService modelEngineService) {
        this.modelEngineService = modelEngineService;
    }

    public void loadDefinitions(YamlConfiguration config) {
        furnitureDefinitions.clear();
        furnitureByItemId.clear();

        ConfigurationSection section = config.getConfigurationSection("furniture");
        if (section == null) {
            return;
        }

        for (String furnitureId : section.getKeys(false)) {
            ConfigurationSection furnitureSection = section.getConfigurationSection(furnitureId);
            if (furnitureSection == null) {
                continue;
            }

            String itemId = normalize(furnitureSection.getString("item-id", furnitureId));
            String displayItemId = normalize(furnitureSection.getString("display-item", itemId));
            String modelId = normalize(furnitureSection.getString("model-id", ""));
            boolean seat = furnitureSection.getBoolean("seat", false);
            List<String> mechanics = furnitureSection.getStringList("mechanics");

            ConfigurationSection displaySection = furnitureSection.getConfigurationSection("display");
            FurnitureDefinition.DisplayDefinition display = displaySection == null
                ? FurnitureDefinition.DisplayDefinition.defaults()
                : new FurnitureDefinition.DisplayDefinition(
                    displaySection.getDouble("offset.x", 0.0D),
                    displaySection.getDouble("offset.y", 0.0D),
                    displaySection.getDouble("offset.z", 0.0D),
                    displaySection.getDouble("scale.x", 1.0D),
                    displaySection.getDouble("scale.y", 1.0D),
                    displaySection.getDouble("scale.z", 1.0D),
                    (float) displaySection.getDouble("rotation.pitch", 0.0D),
                    (float) displaySection.getDouble("rotation.yaw", 0.0D),
                    (float) displaySection.getDouble("rotation.roll", 0.0D),
                    parseEnum(Display.Billboard.class, displaySection.getString("billboard"), Display.Billboard.FIXED),
                    parseEnum(ItemDisplay.ItemDisplayTransform.class, displaySection.getString("transform"), ItemDisplay.ItemDisplayTransform.FIXED),
                    (float) displaySection.getDouble("view-range", 1.0D),
                    (float) displaySection.getDouble("shadow-radius", 0.0D),
                    (float) displaySection.getDouble("shadow-strength", 0.0D)
                );

            FurnitureDefinition definition = new FurnitureDefinition(
                normalize(furnitureId),
                itemId,
                displayItemId,
                modelId,
                seat,
                mechanics,
                display
            );

            furnitureDefinitions.put(definition.id(), definition);
            furnitureByItemId.put(definition.itemId(), definition.id());
        }

        plugin.getLogger().info("Loaded " + furnitureDefinitions.size() + " furniture definitions.");
    }

    public void stop() {
        for (UUID displayId : new ArrayList<>(hostByDisplay.keySet())) {
            Entity display = Bukkit.getEntity(displayId);
            if (display != null && display.isValid()) {
                display.remove();
            }
        }
        displayByHost.clear();
        hostByDisplay.clear();
    }

    public Set<String> getFurnitureIds() {
        return Collections.unmodifiableSet(furnitureDefinitions.keySet());
    }

    public FurnitureDefinition getDefinition(String furnitureId) {
        if (furnitureId == null) {
            return null;
        }
        return furnitureDefinitions.get(normalize(furnitureId));
    }

    public FurnitureDefinition getDefinitionByItemId(String itemId) {
        if (itemId == null) {
            return null;
        }

        String furnitureId = furnitureByItemId.get(normalize(itemId));
        if (furnitureId == null) {
            return null;
        }

        return getDefinition(furnitureId);
    }

    public Entity spawnFurniture(Location location, String furnitureId) {
        FurnitureDefinition definition = getDefinition(furnitureId);
        if (definition == null || location == null || location.getWorld() == null) {
            return null;
        }

        ArmorStand host = (ArmorStand) location.getWorld().spawnEntity(location, EntityType.ARMOR_STAND);
        host.setVisible(false);
        host.setGravity(false);
        host.setInvulnerable(false);
        host.setMarker(false);
        host.setBasePlate(false);
        host.setSmall(false);
        host.setArms(false);
        host.setPersistent(false);
        host.getPersistentDataContainer().set(keychain.furnitureId(), PersistentDataType.STRING, definition.id());

        ItemDisplay display = spawnDisplay(host, definition);
        if (display != null) {
            host.getPersistentDataContainer().set(keychain.furnitureModelDisplay(), PersistentDataType.STRING, display.getUniqueId().toString());
            displayByHost.put(host.getUniqueId(), display.getUniqueId());
            hostByDisplay.put(display.getUniqueId(), host.getUniqueId());
        }

        return host;
    }

    public boolean isFurnitureEntity(Entity entity) {
        return getFurnitureId(entity) != null;
    }

    public String getFurnitureId(Entity entity) {
        Entity root = resolveRootEntity(entity);
        if (root == null) {
            return null;
        }
        return root.getPersistentDataContainer().get(keychain.furnitureId(), PersistentDataType.STRING);
    }

    public Entity resolveRootEntity(Entity entity) {
        if (entity == null) {
            return null;
        }

        String direct = entity.getPersistentDataContainer().get(keychain.furnitureId(), PersistentDataType.STRING);
        if (direct != null) {
            return entity;
        }

        String hostId = entity.getPersistentDataContainer().get(keychain.furnitureModelHost(), PersistentDataType.STRING);
        if (hostId != null && !hostId.isBlank()) {
            try {
                return Bukkit.getEntity(UUID.fromString(hostId));
            } catch (IllegalArgumentException ignored) {
            }
        }

        UUID mappedHost = hostByDisplay.get(entity.getUniqueId());
        if (mappedHost != null) {
            return Bukkit.getEntity(mappedHost);
        }

        return null;
    }

    public Entity getDisplayEntity(Entity entity) {
        Entity root = resolveRootEntity(entity);
        if (root == null) {
            return null;
        }

        UUID displayId = displayByHost.get(root.getUniqueId());
        if (displayId == null) {
            String stored = root.getPersistentDataContainer().get(keychain.furnitureModelDisplay(), PersistentDataType.STRING);
            if (stored != null && !stored.isBlank()) {
                try {
                    displayId = UUID.fromString(stored);
                } catch (IllegalArgumentException ignored) {
                    displayId = null;
                }
            }
        }

        return displayId == null ? null : Bukkit.getEntity(displayId);
    }

    public void removeFurniture(Entity entity) {
        Entity root = resolveRootEntity(entity);
        if (root == null) {
            if (entity != null && entity.isValid()) {
                entity.remove();
            }
            return;
        }

        for (Entity passenger : new ArrayList<>(root.getPassengers())) {
            root.removePassenger(passenger);
        }
        root.eject();

        Entity display = getDisplayEntity(root);
        UUID rootId = root.getUniqueId();
        if (display != null && display.isValid()) {
            hostByDisplay.remove(display.getUniqueId());
            display.remove();
        }
        displayByHost.remove(rootId);
        root.getPersistentDataContainer().remove(keychain.furnitureModelDisplay());
        root.remove();
    }

    private ItemDisplay spawnDisplay(Entity host, FurnitureDefinition definition) {
        if (host.getWorld() == null) {
            return null;
        }

        ResolvedDisplay displayDefinition = resolveDisplay(definition);
        ItemStack item = itemService.createItem(displayDefinition.itemId(), 1);
        if (item == null) {
            item = new ItemStack(Material.STICK);
        }

        Location spawnLocation = host.getLocation().clone().add(
            displayDefinition.display().offsetX(),
            displayDefinition.display().offsetY(),
            displayDefinition.display().offsetZ()
        );

        ItemDisplay display = spawnLocation.getWorld().spawn(spawnLocation, ItemDisplay.class);
        display.setItemStack(item);
        display.setItemDisplayTransform(displayDefinition.display().transform());
        display.setBillboard(displayDefinition.display().billboard());
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(1);
        display.setTeleportDuration(1);
        display.setViewRange(displayDefinition.display().viewRange());
        display.setShadowRadius(displayDefinition.display().shadowRadius());
        display.setShadowStrength(displayDefinition.display().shadowStrength());
        display.setPersistent(false);
        display.setInvulnerable(true);
        display.setGravity(false);
        display.setTransformation(new Transformation(
            new Vector3f(),
            new Quaternionf().rotateXYZ(
                (float) Math.toRadians(displayDefinition.display().pitch()),
                (float) Math.toRadians(displayDefinition.display().yaw()),
                (float) Math.toRadians(displayDefinition.display().roll())
            ),
            new Vector3f(
                (float) displayDefinition.display().scaleX(),
                (float) displayDefinition.display().scaleY(),
                (float) displayDefinition.display().scaleZ()
            ),
            new Quaternionf()
        ));
        display.getPersistentDataContainer().set(keychain.furnitureModelHost(), PersistentDataType.STRING, host.getUniqueId().toString());
        return display;
    }

    private ResolvedDisplay resolveDisplay(FurnitureDefinition definition) {
        if (modelEngineService != null && !definition.modelId().isBlank()) {
            BullseyeModelDefinition model = modelEngineService.getDefinition(definition.modelId());
            if (model != null) {
                FurnitureDefinition.DisplayDefinition display = new FurnitureDefinition.DisplayDefinition(
                    model.offsetX(),
                    model.offsetY(),
                    model.offsetZ(),
                    model.scaleX(),
                    model.scaleY(),
                    model.scaleZ(),
                    model.pitch(),
                    model.yaw(),
                    model.roll(),
                    model.billboard(),
                    model.transform(),
                    model.viewRange(),
                    model.shadowRadius(),
                    model.shadowStrength()
                );
                String itemId = !model.itemId().isBlank() ? model.itemId() : definition.displayItemId();
                return new ResolvedDisplay(itemId, display);
            }
        }

        return new ResolvedDisplay(definition.displayItemId(), definition.display());
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

    private record ResolvedDisplay(String itemId, FurnitureDefinition.DisplayDefinition display) {
    }
}
