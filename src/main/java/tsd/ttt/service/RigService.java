package tsd.ttt.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import tsd.ttt.TTT;
import tsd.ttt.model.FurnitureDefinition;

/**
 * The body: true multi-display bone rigs built from BBModel groups.
 *
 * <p>Each named top-level group becomes its own display positioned at the group's pivot, showing
 * a group-filtered slice of the model's cubes. Heads track nearby players, limbs swing while
 * the host moves, everything else holds formation. All geometry math is relative-to-pivot:
 * {@code java_} formats keep Blockbench units (16 per block) while rigged formats scale by
 * 1/16, selected by a single unit-scale branch.</p>
 */
public class RigService {
    private static final long TICK_PERIOD = 3L;
    private static final double HEAD_RANGE = 12.0D;
    private static final double HEAD_YAW_LIMIT = 75.0D;
    private static final double HEAD_PITCH_LIMIT = 50.0D;
    private static final int MIN_GENERATED_CMD = 14000;

    private final TTT plugin;
    private final Map<String, RigConfig> configs = new LinkedHashMap<>();
    private final Map<String, RigTemplate> templates = new LinkedHashMap<>();
    private final Map<UUID, RigInstance> rigsByRep = new LinkedHashMap<>();
    private final Map<UUID, UUID> repByHost = new LinkedHashMap<>();
    private final Set<String> warnedMissing = new LinkedHashSet<>();
    private BukkitTask task;

    public RigService(TTT plugin) {
        this.plugin = plugin;
    }

    public void load(YamlConfiguration modelsConfig) {
        configs.clear();
        templates.clear();
        ConfigurationSection root = modelsConfig == null ? null : modelsConfig.getConfigurationSection("models");
        if (root == null) {
            return;
        }
        for (String modelId : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(modelId + ".rig");
            if (section == null || !section.getBoolean("enabled", false)) {
                continue;
            }
            String blueprint = root.getString(modelId + ".blueprint", "");
            if (blueprint.isBlank()) {
                continue;
            }
            configs.put(modelId.toLowerCase(Locale.ROOT), new RigConfig(
                Math.max(0.05D, section.getDouble("scale", 1.0D)),
                Math.max(0.0D, section.getDouble("swing-degrees", 25.0D)),
                lowerList(section.getStringList("head-groups")),
                lowerList(section.getStringList("limb-groups"))
            ));
            RigTemplate template = parseBlueprint(modelId.toLowerCase(Locale.ROOT), blueprint);
            if (template != null) {
                templates.put(modelId.toLowerCase(Locale.ROOT), template);
                provisionMissingParts(modelId.toLowerCase(Locale.ROOT), template);
            }
        }
        if (!configs.isEmpty()) {
            plugin.getLogger().info("Loaded " + configs.size() + " model rigs.");
        }
    }

    public boolean hasRig(String modelId) {
        return modelId != null && configs.containsKey(modelId.toLowerCase(Locale.ROOT)) && templates.containsKey(modelId.toLowerCase(Locale.ROOT));
    }

    public boolean isRepresentative(UUID displayId) {
        return displayId != null && rigsByRep.containsKey(displayId);
    }

    /** Finds the host entity for any bone display in any rig, or null. */
    public UUID resolveHostId(UUID displayId) {
        if (displayId == null) {
            return null;
        }
        for (RigInstance rig : rigsByRep.values()) {
            if (rig.repId().equals(displayId)) {
                return rig.hostId();
            }
            for (ItemDisplay bone : rig.bones().values()) {
                if (bone.getUniqueId().equals(displayId)) {
                    return rig.hostId();
                }
            }
        }
        return null;
    }

    /**
     * Builds a rig for a mob host. Returns the representative display, or null to fall back
     * to the single-display path.
     */
    public ItemDisplay trySpawnRigForMob(Entity host, String modelId, double offsetX, double offsetY, double offsetZ,
        float pitchDeg, float yawDeg, float rollDeg, float scaleX, float scaleY, float scaleZ) {
        RigTemplate template = templates.get(modelId.toLowerCase(Locale.ROOT));
        RigConfig config = configs.get(modelId.toLowerCase(Locale.ROOT));
        if (template == null || config == null || host.getWorld() == null) {
            return null;
        }
        Location root = host.getLocation().clone().add(offsetX, offsetY, offsetZ);
        Map<String, ItemDisplay> bones = new LinkedHashMap<>();
        ItemDisplay representative = null;
        for (BoneTemplate bone : template.bones().values()) {
            ItemStack part = plugin.getBootstrap().getItemService().createItem(bone.partItemId(), 1);
            if (part == null) {
                continue;
            }
            Location at = boneWorldPos(root, host.getLocation().getYaw(), bone, config);
            ItemDisplay display = at.getWorld().spawn(at, ItemDisplay.class);
            display.setItemStack(part);
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            display.setBillboard(Display.Billboard.FIXED);
            display.setInterpolationDuration(4);
            display.setViewRange(1.0F);
            display.setPersistent(false);
            display.setInvulnerable(true);
            display.setGravity(false);
            applyBonePose(display, bone, 0.0F, 0.0F, 0.0F, scaleX, scaleY, scaleZ);
            bones.put(bone.name(), display);
            if (representative == null) {
                representative = display;
            }
        }
        if (representative == null) {
            return null;
        }
        RigInstance rig = new RigInstance(host.getUniqueId(), representative.getUniqueId(), modelId.toLowerCase(Locale.ROOT),
            bones, root.clone(), host.getLocation().clone(), 0.0D, 0.0D, offsetX, offsetY, offsetZ,
            pitchDeg, yawDeg, rollDeg, scaleX, scaleY, scaleZ, currentTick());
        rigsByRep.put(representative.getUniqueId(), rig);
        repByHost.put(host.getUniqueId(), representative.getUniqueId());
        startTaskIfNeeded();
        return representative;
    }

    /**
     * Builds a rig for furniture. Returns the representative display, or null to fall back.
     */
    public ItemDisplay trySpawnRigForFurniture(Entity host, String modelId, FurnitureDefinition.DisplayDefinition pose) {
        return trySpawnRigForMob(host, modelId, pose.offsetX(), pose.offsetY(), pose.offsetZ(),
            pose.pitch(), pose.yaw(), pose.roll(),
            (float) pose.scaleX(), (float) pose.scaleY(), (float) pose.scaleZ());
    }

    public void removeByRepresentative(UUID displayId) {
        RigInstance rig = rigsByRep.remove(displayId);
        if (rig == null) {
            return;
        }
        repByHost.remove(rig.hostId());
        for (ItemDisplay display : rig.bones().values()) {
            if (display.isValid()) {
                display.remove();
            }
        }
    }

    public void removeAll() {
        for (UUID rep : new ArrayList<>(rigsByRep.keySet())) {
            removeByRepresentative(rep);
        }
    }

    public void shutdown() {
        removeAll();
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void startTaskIfNeeded() {
        if (task == null && !rigsByRep.isEmpty()) {
            task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, TICK_PERIOD, TICK_PERIOD);
        }
    }

    private void tick() {
        long now = currentTick();
        List<UUID> dead = new ArrayList<>();
        for (Map.Entry<UUID, RigInstance> entry : rigsByRep.entrySet()) {
            RigInstance rig = entry.getValue();
            Entity host = plugin.getServer().getEntity(rig.hostId());
            if (host == null || !host.isValid() || host.isDead()) {
                dead.add(entry.getKey());
                continue;
            }
            updateRig(rig, host, now);
        }
        for (UUID rep : dead) {
            removeByRepresentative(rep);
        }
        if (rigsByRep.isEmpty() && task != null) {
            task.cancel();
            task = null;
        }
    }

    private void updateRig(RigInstance rig, Entity host, long now) {
        RigTemplate template = templates.get(rig.modelId());
        RigConfig config = configs.get(rig.modelId());
        if (template == null || config == null) {
            return;
        }
        Location root = host.getLocation().clone().add(rig.offsetX(), rig.offsetY(), rig.offsetZ());
        float hostYaw = host.getLocation().getYaw();

        Location hostLast = rig.lastHostPos();
        double movedSq = hostLast.getWorld() != null && hostLast.getWorld().equals(root.getWorld())
            ? hostLast.distanceSquared(root)
            : 1.0D;
        boolean moving = movedSq > 0.0004D;
        double swingAmp = moving ? 1.0D : Math.max(0.0D, rig.swingAmp() - 0.15D);
        double phase = rig.swingPhase() + (moving ? (now - rig.lastTick()) * 0.35D : 0.0D);

        int limbIndex = 0;
        for (Map.Entry<String, ItemDisplay> entry : rig.bones().entrySet()) {
            BoneTemplate bone = template.bones().get(entry.getKey());
            ItemDisplay display = entry.getValue();
            if (bone == null || !display.isValid()) {
                continue;
            }
            display.teleport(boneWorldPos(root, hostYaw, bone, config));
            float extraYaw = 0.0F;
            float extraPitch = 0.0F;
            float extraRoll = 0.0F;
            if ("HEAD".equals(bone.role())) {
                float[] look = lookAtNearest(display, hostYaw);
                extraYaw = look[0];
                extraPitch = look[1];
            } else if ("LIMB".equals(bone.role())) {
                extraPitch = (float) (config.swingDegrees() * swingAmp * Math.sin(phase + limbIndex * Math.PI));
                limbIndex++;
            }
            display.setInterpolationDuration((int) (TICK_PERIOD + 1));
            applyBonePose(display, bone, extraPitch, extraYaw, extraRoll, rig.scaleX(), rig.scaleY(), rig.scaleZ());
        }
        rigsByRep.put(rig.repId(), new RigInstance(rig.hostId(), rig.repId(), rig.modelId(), rig.bones(),
            root, root.clone(), swingAmp, phase, rig.offsetX(), rig.offsetY(), rig.offsetZ(),
            rig.pitchDeg(), rig.yawDeg(), rig.rollDeg(), rig.scaleX(), rig.scaleY(), rig.scaleZ(), now));
    }

    private float[] lookAtNearest(ItemDisplay display, float hostYaw) {
        Player nearest = null;
        double nearestSq = HEAD_RANGE * HEAD_RANGE;
        for (Entity nearby : display.getNearbyEntities(HEAD_RANGE, HEAD_RANGE / 2.0D, HEAD_RANGE)) {
            if (nearby instanceof Player player && !player.isDead()) {
                double distSq = nearby.getLocation().distanceSquared(display.getLocation());
                if (distSq < nearestSq) {
                    nearestSq = distSq;
                    nearest = player;
                }
            }
        }
        if (nearest == null) {
            return new float[] {0.0F, 0.0F};
        }
        Location eye = nearest.getEyeLocation();
        Location from = display.getLocation();
        double dx = eye.getX() - from.getX();
        double dy = eye.getY() - from.getY();
        double dz = eye.getZ() - from.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double length = Math.sqrt(horizontal * horizontal + dy * dy);
        if (length < 0.001D) {
            return new float[] {0.0F, 0.0F};
        }
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(Math.asin(-dy / length));
        float relYaw = (float) wrapAngle(yaw - hostYaw);
        relYaw = Math.max((float) -HEAD_YAW_LIMIT, Math.min((float) HEAD_YAW_LIMIT, relYaw));
        pitch = Math.max((float) -HEAD_PITCH_LIMIT, Math.min((float) HEAD_PITCH_LIMIT, pitch));
        return new float[] {relYaw, pitch};
    }

    private void applyBonePose(ItemDisplay display, BoneTemplate bone, float extraPitch, float extraYaw, float extraRoll,
        float scaleX, float scaleY, float scaleZ) {
        display.setTransformation(new Transformation(
            new Vector3f(),
            new Quaternionf().rotateXYZ(
                (float) Math.toRadians(bone.baseRotation()[0] + extraPitch),
                (float) Math.toRadians(bone.baseRotation()[1] + extraYaw),
                (float) Math.toRadians(bone.baseRotation()[2] + extraRoll)
            ),
            new Vector3f(scaleX, scaleY, scaleZ),
            new Quaternionf()
        ));
    }

    private Location boneWorldPos(Location root, float hostYaw, BoneTemplate bone, RigConfig config) {
        double radians = Math.toRadians(hostYaw);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        double x = bone.pivotMc()[0] * config.scale();
        double y = bone.pivotMc()[1] * config.scale();
        double z = bone.pivotMc()[2] * config.scale();
        Location at = root.clone();
        at.add(x * cos - z * sin, y, z * cos + x * sin);
        return at;
    }

    private long currentTick() {
        return plugin.getServer().getWorlds().isEmpty()
            ? 0L
            : plugin.getServer().getWorlds().getFirst().getFullTime();
    }

    static double wrapAngle(double degrees) {
        double wrapped = degrees % 360.0D;
        if (wrapped > 180.0D) {
            wrapped -= 360.0D;
        } else if (wrapped < -180.0D) {
            wrapped += 360.0D;
        }
        return wrapped;
    }

    private List<String> lowerList(List<String> values) {
        List<String> lowered = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                lowered.add(value.trim().toLowerCase(Locale.ROOT));
            }
        }
        return lowered;
    }

    // ------------------------------------------------------------------ parsing

    private RigTemplate parseBlueprint(String modelId, String blueprintId) {
        Path file = plugin.getDataFolder().toPath().resolve("addons").resolve("generated").resolve("blueprints").resolve(blueprintId + ".bbmodel");
        if (!Files.isRegularFile(file)) {
            return null;
        }
        JsonObject root;
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) {
                return null;
            }
            root = parsed.getAsJsonObject();
        } catch (RuntimeException | IOException ex) {
            plugin.getLogger().warning("Could not parse blueprint '" + blueprintId + "' for rig '" + modelId + "'.");
            return null;
        }

        String format = "";
        if (root.has("meta") && root.get("meta").isJsonObject() && root.getAsJsonObject("meta").has("model_format")) {
            format = root.getAsJsonObject("meta").get("model_format").getAsString();
        }
        double unitScale = format.startsWith("java_") ? 1.0D : 1.0D / 16.0D;

        Map<String, JsonObject> elementsByUuid = new LinkedHashMap<>();
        if (root.has("elements") && root.get("elements").isJsonArray()) {
            for (JsonElement element : root.getAsJsonArray("elements")) {
                if (element.isJsonObject() && element.getAsJsonObject().has("uuid")) {
                    elementsByUuid.put(element.getAsJsonObject().get("uuid").getAsString(), element.getAsJsonObject());
                }
            }
        }
        Map<String, JsonObject> groupsByUuid = new LinkedHashMap<>();
        if (root.has("groups") && root.get("groups").isJsonArray()) {
            for (JsonElement element : root.getAsJsonArray("groups")) {
                if (element.isJsonObject() && element.getAsJsonObject().has("uuid")) {
                    groupsByUuid.put(element.getAsJsonObject().get("uuid").getAsString(), element.getAsJsonObject());
                }
            }
        }

        Map<String, List<String>> groupElements = new LinkedHashMap<>();
        Map<String, String> groupNames = new LinkedHashMap<>();
        if (root.has("outliner") && root.get("outliner").isJsonArray()) {
            for (JsonElement node : root.getAsJsonArray("outliner")) {
                walkOutliner(node, null, groupsByUuid, groupElements, groupNames);
            }
        }
        if (groupElements.isEmpty()) {
            return null;
        }

        RigConfig config = configs.get(modelId);
        Map<String, BoneTemplate> bones = new LinkedHashMap<>();
        double[] defaultPivot = unitScale == 1.0D ? new double[] {8.0D, 8.0D, 8.0D} : new double[] {0.0D, 0.0D, 0.0D};
        double[] rootPivot = null;
        for (Map.Entry<String, List<String>> entry : groupElements.entrySet()) {
            JsonObject group = groupsByUuid.get(entry.getKey());
            double[] pivot = readPivot(group, defaultPivot);
            String name = sanitize(groupNames.getOrDefault(entry.getKey(), "bone" + bones.size()));
            List<JsonObject> cubes = new ArrayList<>();
            for (String elementUuid : entry.getValue()) {
                JsonObject cube = elementsByUuid.get(elementUuid);
                if (cube != null) {
                    cubes.add(cube);
                }
            }
            if (cubes.isEmpty()) {
                continue;
            }
            if (rootPivot == null) {
                rootPivot = pivot;
            }
            String role = detectRole(name, config);
            double[] relativePivot = new double[] {
                (pivot[0] - rootPivot[0]) * unitScale,
                (pivot[1] - rootPivot[1]) * unitScale,
                (pivot[2] - rootPivot[2]) * unitScale
            };
            bones.put(name, new BoneTemplate(name, relativePivot, pivot, unitScale, cubes, role,
                modelId + "__" + name, baseRotation(group)));
        }
        if (bones.isEmpty()) {
            return null;
        }
        Map<String, String> textureIndexByUuid = new LinkedHashMap<>();
        List<String> textureRefs = mapRigTextures(modelId, root, file);
        if (root.has("textures") && root.get("textures").isJsonArray()) {
            JsonArray bbTextures = root.getAsJsonArray("textures");
            for (int index = 0; index < bbTextures.size(); index++) {
                if (bbTextures.get(index).isJsonObject()) {
                    JsonObject texture = bbTextures.get(index).getAsJsonObject();
                    if (texture.has("uuid") && texture.get("uuid").isJsonPrimitive()) {
                        textureIndexByUuid.put(texture.get("uuid").getAsString(), String.valueOf(index));
                    }
                }
            }
        }
        return new RigTemplate(modelId, unitScale, bones, textureIndexByUuid, textureRefs);
    }

    private void walkOutliner(JsonElement node, String currentGroup, Map<String, JsonObject> groupsByUuid,
        Map<String, List<String>> groupElements, Map<String, String> groupNames) {
        if (node.isJsonPrimitive() && node.getAsJsonPrimitive().isString()) {
            if (currentGroup != null) {
                groupElements.computeIfAbsent(currentGroup, key -> new ArrayList<>()).add(node.getAsString());
            }
            return;
        }
        if (!node.isJsonObject()) {
            return;
        }
        JsonObject object = node.getAsJsonObject();
        String uuid = object.has("uuid") ? object.get("uuid").getAsString() : "";
        String childGroup = currentGroup;
        if (!uuid.isBlank() && groupsByUuid.containsKey(uuid)) {
            childGroup = uuid;
            JsonObject group = groupsByUuid.get(uuid);
            String name = group.has("name") && group.get("name").isJsonPrimitive() ? group.get("name").getAsString() : uuid;
            groupNames.put(uuid, name);
            groupElements.computeIfAbsent(uuid, key -> new ArrayList<>());
        }
        if (object.has("children") && object.get("children").isJsonArray()) {
            for (JsonElement child : object.getAsJsonArray("children")) {
                walkOutliner(child, childGroup, groupsByUuid, groupElements, groupNames);
            }
        }
    }

    private double[] readPivot(JsonObject group, double[] fallback) {
        double[] pivot = new double[] {fallback[0], fallback[1], fallback[2]};
        if (group != null && group.has("origin") && group.get("origin").isJsonArray() && group.getAsJsonArray("origin").size() == 3) {
            for (int i = 0; i < 3; i++) {
                try {
                    pivot[i] = group.getAsJsonArray("origin").get(i).getAsDouble();
                } catch (RuntimeException ignored) {
                }
            }
        }
        return pivot;
    }

    private float[] baseRotation(JsonObject group) {
        float[] rotation = new float[] {0.0F, 0.0F, 0.0F};
        if (group != null && group.has("rotation") && group.get("rotation").isJsonArray() && group.getAsJsonArray("rotation").size() == 3) {
            for (int i = 0; i < 3; i++) {
                try {
                    rotation[i] = group.getAsJsonArray("rotation").get(i).getAsFloat();
                } catch (RuntimeException ignored) {
                }
            }
        }
        return rotation;
    }

    private String detectRole(String name, RigConfig config) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (config != null) {
            if (config.headGroups().contains(lower)) {
                return "HEAD";
            }
            if (config.limbGroups().contains(lower)) {
                return "LIMB";
            }
        }
        if (lower.contains("head") || lower.contains("skull") || lower.contains("face")) {
            return "HEAD";
        }
        if (lower.contains("arm") || lower.contains("hand") || lower.contains("leg") || lower.contains("foot")
            || lower.contains("limb") || lower.contains("wing") || lower.contains("tail") || lower.contains("fin")
            || lower.contains("ear") || lower.contains("horn")) {
            return "LIMB";
        }
        return "STATIC";
    }

    private String sanitize(String value) {
        String cleaned = value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
        cleaned = cleaned.replaceAll("^_+|_+$", "");
        return cleaned.isBlank() ? "bone" : cleaned;
    }

    // ------------------------------------------------------- part provisioning

    private void provisionMissingParts(String modelId, RigTemplate template) {
        Path addonRoot = plugin.getDataFolder().toPath().resolve("addons").resolve("generated");
        File itemsFile = addonRoot.resolve("items.yml").toFile();
        YamlConfiguration items = YamlConfiguration.loadConfiguration(itemsFile);
        YamlConfiguration baseItems = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "items.yml"));
        boolean changed = false;

        for (BoneTemplate bone : template.bones().values()) {
            if (items.getConfigurationSection("items." + bone.partItemId()) != null) {
                writePartModel(modelId, template, bone, false);
                continue;
            }
            int cmd = nextCmd(items, baseItems);
            items.set("items." + bone.partItemId() + ".base", Material.BONE.name());
            items.set("items." + bone.partItemId() + ".custom-model-data", cmd);
            items.set("items." + bone.partItemId() + ".name", "&f" + prettify(bone.name()));
            items.set("items." + bone.partItemId() + ".lore", List.of("&7Rig part of &f" + modelId));
            changed = true;
            writePartModel(modelId, template, bone, true);
        }

        if (changed) {
            try {
                if (itemsFile.getParentFile() != null) {
                    Files.createDirectories(itemsFile.getParentFile().toPath());
                }
                items.save(itemsFile);
                plugin.getLogger().info("Provisioned " + template.bones().size() + " rig parts for model '" + modelId + "'.");
            } catch (IOException ex) {
                plugin.getLogger().warning("Failed saving rig parts for '" + modelId + "': " + ex.getMessage());
            }
        }
    }

    private int nextCmd(YamlConfiguration items, YamlConfiguration baseItems) {
        int max = MIN_GENERATED_CMD - 1;
        for (YamlConfiguration config : List.of(items, baseItems)) {
            ConfigurationSection root = config.getConfigurationSection("items");
            if (root == null) {
                continue;
            }
            for (String id : root.getKeys(false)) {
                ConfigurationSection section = root.getConfigurationSection(id);
                if (section == null) {
                    continue;
                }
                if (Material.BONE.name().equals(section.getString("base"))) {
                    max = Math.max(max, section.getInt("custom-model-data", 0));
                }
            }
        }
        return Math.max(MIN_GENERATED_CMD, max + 1);
    }

    private void writePartModel(String modelId, RigTemplate template, BoneTemplate bone, boolean overwrite) {
        Path modelPath = plugin.getDataFolder().toPath()
            .resolve("resourcepack").resolve("addons").resolve("generated")
            .resolve("assets").resolve("ttt").resolve("models").resolve("item")
            .resolve(bone.partItemId() + ".json");
        if (Files.exists(modelPath) && !overwrite) {
            return;
        }
        try {
            JsonObject textures = new JsonObject();
            for (int i = 0; i < template.textureRefs().size(); i++) {
                textures.addProperty(String.valueOf(i), template.textureRefs().get(i));
            }
            if (textures.isEmpty()) {
                textures.addProperty("0", "ttt:item/" + modelId + "_rig_tex0");
            }
            textures.addProperty("particle", "#0");
            JsonArray elements = new JsonArray();
            for (JsonObject cube : bone.cubes()) {
                JsonObject converted = convertRigCube(cube, bone, template.textureIndexByUuid());
                if (converted != null) {
                    elements.add(converted);
                }
            }
            if (elements.isEmpty()) {
                return;
            }
            JsonObject model = new JsonObject();
            model.addProperty("parent", "minecraft:item/generated");
            model.add("textures", textures);
            model.add("elements", elements);
            Files.createDirectories(Objects.requireNonNull(modelPath.getParent()));
            Files.writeString(modelPath, model + "\n", StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException ex) {
            plugin.getLogger().warning("Failed writing rig part model '" + bone.partItemId() + "': " + ex.getMessage());
        }
    }

    private List<String> mapRigTextures(String modelId, JsonObject root, Path bbmodelFile) {
        List<String> refs = new ArrayList<>();
        Path textureDir = plugin.getDataFolder().toPath()
            .resolve("resourcepack").resolve("addons").resolve("generated")
            .resolve("assets").resolve("ttt").resolve("textures").resolve("item");
        List<Path> siblings = new ArrayList<>();
        Path parent = bbmodelFile.getParent();
        if (parent != null && Files.isDirectory(parent)) {
            try (java.util.stream.Stream<Path> stream = Files.list(parent)) {
                siblings = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                    .sorted()
                    .toList();
            } catch (IOException ignored) {
            }
        }

        int textureCount = 1;
        if (root.has("textures") && root.get("textures").isJsonArray()) {
            textureCount = Math.max(1, root.getAsJsonArray("textures").size());
        }
        try {
            Files.createDirectories(textureDir);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not prepare rig texture folder for '" + modelId + "'.");
            return refs;
        }
        for (int index = 0; index < textureCount; index++) {
            Path sibling = index < siblings.size() ? siblings.get(index) : (siblings.isEmpty() ? null : siblings.getFirst());
            Path target = textureDir.resolve(modelId + "_rig_tex" + index + ".png");
            if (sibling != null) {
                try {
                    Files.copy(sibling, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ex) {
                    plugin.getLogger().warning("Could not copy rig texture " + sibling + ": " + ex.getMessage());
                }
            } else if (!Files.exists(target)) {
                writeFlatPlaceholder(target);
            }
            refs.add("ttt:item/" + modelId + "_rig_tex" + index);
        }
        return refs;
    }

    private void writeFlatPlaceholder(Path target) {
        try {
            BufferedImage placeholder = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = placeholder.createGraphics();
            graphics.setColor(new Color(200, 200, 200, 255));
            graphics.fillRect(0, 0, 16, 16);
            graphics.dispose();
            ImageIO.write(placeholder, "png", target.toFile());
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not write rig texture placeholder " + target + ": " + ex.getMessage());
        }
    }

    private JsonObject convertRigCube(JsonObject cube, BoneTemplate bone, Map<String, String> textureIndexByUuid) {
        if (cube.has("type") && !(cube.get("type").isJsonPrimitive() && "cube".equals(cube.get("type").getAsString()))) {
            return null;
        }
        double[] from = readDoubles(cube.get("from"), 3);
        double[] to = readDoubles(cube.get("to"), 3);
        if (from == null || to == null) {
            return null;
        }
        double[] relFrom = new double[3];
        double[] relTo = new double[3];
        for (int i = 0; i < 3; i++) {
            relFrom[i] = (from[i] - bone.pivot()[i]) * bone.unitScale();
            relTo[i] = (to[i] - bone.pivot()[i]) * bone.unitScale();
        }
        if (!cube.has("faces") || !cube.get("faces").isJsonObject()) {
            return null;
        }
        JsonObject faces = cube.getAsJsonObject("faces");
        JsonObject convertedFaces = new JsonObject();
        for (String direction : List.of("north", "east", "south", "west", "up", "down")) {
            if (!faces.has(direction) || !faces.get(direction).isJsonObject()) {
                continue;
            }
            JsonObject face = faces.getAsJsonObject(direction);
            JsonObject convertedFace = new JsonObject();
            double[] uv = readDoubles(face.get("uv"), 4);
            if (uv != null) {
                JsonArray uvArray = new JsonArray();
                for (double value : uv) {
                    uvArray.add(value);
                }
                convertedFace.add("uv", uvArray);
            }
            convertedFace.addProperty("texture", "#" + resolveRigTextureKey(face.get("texture"), textureIndexByUuid));
            convertedFaces.add(direction, convertedFace);
        }
        if (convertedFaces.isEmpty()) {
            return null;
        }
        JsonObject converted = new JsonObject();
        converted.add("from", toJsonArray(relFrom));
        converted.add("to", toJsonArray(relTo));
        converted.add("faces", convertedFaces);
        return converted;
    }

    private String resolveRigTextureKey(JsonElement texture, Map<String, String> textureIndexByUuid) {
        if (texture != null && texture.isJsonPrimitive()) {
            if (texture.getAsJsonPrimitive().isNumber()) {
                return String.valueOf(texture.getAsInt());
            }
            String uuid = texture.getAsString();
            if (textureIndexByUuid.containsKey(uuid)) {
                return textureIndexByUuid.get(uuid);
            }
        }
        return "0";
    }

    private double[] readDoubles(JsonElement element, int expected) {
        if (element == null || !element.isJsonArray() || element.getAsJsonArray().size() != expected) {
            return null;
        }
        double[] values = new double[expected];
        for (int i = 0; i < expected; i++) {
            try {
                values[i] = element.getAsJsonArray().get(i).getAsDouble();
            } catch (RuntimeException ex) {
                return null;
            }
        }
        return values;
    }

    private JsonArray toJsonArray(double[] values) {
        JsonArray array = new JsonArray();
        for (double value : values) {
            array.add(value);
        }
        return array;
    }

    private String prettify(String id) {
        String[] parts = id.replace('-', '_').split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.toString();
    }

    public record RigConfig(double scale, double swingDegrees, List<String> headGroups, List<String> limbGroups) {
    }

    private record BoneTemplate(String name, double[] pivotMc, double[] pivot, double unitScale,
        List<JsonObject> cubes, String role, String partItemId, float[] baseRotation) {
    }

    private record RigTemplate(String modelId, double unitScale, Map<String, BoneTemplate> bones,
        Map<String, String> textureIndexByUuid, List<String> textureRefs) {
    }

    private record RigInstance(UUID hostId, UUID repId, String modelId, Map<String, ItemDisplay> bones,
        Location root, Location lastHostPos, double swingAmp, double swingPhase, double offsetX, double offsetY, double offsetZ,
        float pitchDeg, float yawDeg, float rollDeg, float scaleX, float scaleY, float scaleZ, long lastTick) {
    }
}
