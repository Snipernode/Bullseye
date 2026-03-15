package tsd.beye.service;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import tsd.beye.Bullseye;
import tsd.beye.model.CustomMobDefinition;
import tsd.beye.model.MythicMobDefinition;

public class MythicMobsService {
    private final Bullseye plugin;

    private final Map<String, MythicMobDefinition> definitions = new LinkedHashMap<>();
    private final Map<String, String> eggToDefinition = new LinkedHashMap<>();

    private boolean enabled;
    private boolean autoInjectOnStartup;
    private boolean autoInjectOnReload;
    private boolean runReloadAfterInject;
    private String reloadCommand = "mm reload";
    private String mobsFileName = "Bullseye-AutoMobs.yml";
    private String skillsFileName = "Bullseye-AutoSkills.yml";
    private boolean importExternalExamples;
    private List<String> externalExampleRoots = new ArrayList<>();
    private MobService mobService;

    public MythicMobsService(Bullseye plugin) {
        this.plugin = plugin;
    }

    public void setMobService(MobService mobService) {
        this.mobService = mobService;
    }

    public void reload() {
        definitions.clear();
        eggToDefinition.clear();

        YamlConfiguration config = YamlConfiguration.loadConfiguration(
            plugin.getDataFolder().toPath().resolve("mythicmobs.yml").toFile()
        );
        ConfigurationSection root = config.getConfigurationSection("mythicmobs");
        if (root == null) {
            enabled = false;
            return;
        }

        enabled = root.getBoolean("enabled", true);
        ConfigurationSection injector = root.getConfigurationSection("injector");
        if (injector != null) {
            autoInjectOnStartup = injector.getBoolean("auto-inject-on-startup", false);
            autoInjectOnReload = injector.getBoolean("auto-inject-on-reload", false);
            runReloadAfterInject = injector.getBoolean("run-reload-after-inject", true);
            reloadCommand = injector.getString("reload-command", "mm reload");
            mobsFileName = injector.getString("mobs-file", "Bullseye-AutoMobs.yml");
            skillsFileName = injector.getString("skills-file", "Bullseye-AutoSkills.yml");
            importExternalExamples = injector.getBoolean("import-external-examples", true);
            externalExampleRoots = new ArrayList<>(injector.getStringList("external-example-roots"));
        } else {
            autoInjectOnStartup = false;
            autoInjectOnReload = false;
            runReloadAfterInject = true;
            reloadCommand = "mm reload";
            mobsFileName = "Bullseye-AutoMobs.yml";
            skillsFileName = "Bullseye-AutoSkills.yml";
            importExternalExamples = true;
            externalExampleRoots = new ArrayList<>();
        }

        ConfigurationSection mobs = root.getConfigurationSection("mobs");
        if (mobs == null) {
            mergeNativeDefinitions();
            return;
        }

        for (String rawId : mobs.getKeys(false)) {
            ConfigurationSection section = mobs.getConfigurationSection(rawId);
            if (section == null) {
                continue;
            }

            String id = normalize(rawId);
            String mythicMobId = section.getString("mythic-mob", "").trim();
            if (mythicMobId.isBlank()) {
                continue;
            }

            String spawnEggItem = section.getString("spawn-egg-item", "").trim().toLowerCase(Locale.ROOT);
            Map<String, List<String>> generatedSkills = new LinkedHashMap<>();
            ConfigurationSection generatedSkillSection = section.getConfigurationSection("generated-skills");
            if (generatedSkillSection != null) {
                for (String skillId : generatedSkillSection.getKeys(false)) {
                    List<String> lines = generatedSkillSection.getStringList(skillId);
                    if (!lines.isEmpty()) {
                        generatedSkills.put(skillId, List.copyOf(lines));
                    }
                }
            }

            MythicMobDefinition definition = new MythicMobDefinition(
                id,
                mythicMobId,
                spawnEggItem,
                Math.max(1, section.getInt("level", 1)),
                section.getBoolean("consume-spawn-egg", true),
                section.getString("entity-type", "ZOMBIE").trim(),
                section.getString("display-name", rawId),
                Math.max(1.0D, section.getDouble("health", 20.0D)),
                Math.max(0.0D, section.getDouble("damage", 2.0D)),
                List.copyOf(section.getStringList("spawn-skills")),
                generatedSkills
            );

            definitions.put(id, definition);
            if (!spawnEggItem.isBlank()) {
                eggToDefinition.put(spawnEggItem, id);
            }
        }

        mergeNativeDefinitions();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("MythicMobs");
    }

    public boolean shouldAutoInjectOnStartup() {
        return enabled && autoInjectOnStartup;
    }

    public boolean shouldAutoInjectOnReload() {
        return enabled && autoInjectOnReload;
    }

    public Collection<String> getMobIds() {
        return Collections.unmodifiableSet(definitions.keySet());
    }

    public MythicMobDefinition getDefinition(String id) {
        if (id == null) {
            return null;
        }
        return definitions.get(normalize(id));
    }

    public boolean handlesSpawnEggItem(String itemId) {
        return itemId != null && eggToDefinition.containsKey(itemId.toLowerCase(Locale.ROOT));
    }

    public EggSpawnResult spawnFromEggItem(String itemId, Player player, Block clickedBlock, BlockFace face) {
        if (itemId == null || itemId.isBlank()) {
            return EggSpawnResult.unhandled();
        }

        String definitionId = eggToDefinition.get(itemId.toLowerCase(Locale.ROOT));
        if (definitionId == null) {
            return EggSpawnResult.unhandled();
        }

        MythicMobDefinition definition = definitions.get(definitionId);
        if (definition == null) {
            return EggSpawnResult.failure(null, "Mythic spawn definition is missing.");
        }

        Location spawnLocation = resolveSpawnLocation(player, clickedBlock, face);
        return spawnDefinition(definition, spawnLocation);
    }

    public EggSpawnResult spawnMob(String id, Location location) {
        MythicMobDefinition definition = getDefinition(id);
        if (definition == null) {
            return EggSpawnResult.failure(null, "Unknown Bullseye Mythic mob id.");
        }
        return spawnDefinition(definition, location);
    }

    public InjectionResult inject(boolean runReloadCommandNow) {
        if (!enabled) {
            return new InjectionResult(false, 0, 0, null, null, "MythicMobs integration is disabled.");
        }

        Plugin mythicMobs = Bukkit.getPluginManager().getPlugin("MythicMobs");
        if (mythicMobs == null || !mythicMobs.isEnabled()) {
            return new InjectionResult(false, 0, 0, null, null, "MythicMobs is not enabled.");
        }

        Path mythicRoot = mythicMobs.getDataFolder().toPath();
        Path mobsOutput = mythicRoot.resolve("Mobs").resolve(mobsFileName);
        Path skillsOutput = mythicRoot.resolve("Skills").resolve(skillsFileName);

        int externalCopies = importExternalExamples ? copyExternalExamples(mythicRoot) : 0;
        int generatedSkills = 0;

        try {
            Files.createDirectories(mobsOutput.getParent());
            Files.createDirectories(skillsOutput.getParent());
            Files.writeString(
                mobsOutput,
                buildGeneratedMobsYaml(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            );
            Files.writeString(
                skillsOutput,
                buildGeneratedSkillsYaml(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            );
            generatedSkills = definitions.values().stream().mapToInt(def -> def.generatedSkills().size()).sum();
        } catch (IOException ex) {
            return new InjectionResult(false, externalCopies, generatedSkills, mobsOutput, skillsOutput, "Failed to write Mythic files: " + ex.getMessage());
        }

        if (runReloadAfterInject && runReloadCommandNow && reloadCommand != null && !reloadCommand.isBlank()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), reloadCommand.trim());
        }

        return new InjectionResult(
            true,
            externalCopies,
            generatedSkills,
            mobsOutput,
            skillsOutput,
            "Injected Bullseye MythicMobs files."
        );
    }

    private EggSpawnResult spawnDefinition(MythicMobDefinition definition, Location location) {
        if (!enabled) {
            return EggSpawnResult.failure(definition, "Bullseye MythicMobs integration is disabled.");
        }
        if (!isAvailable()) {
            return EggSpawnResult.failure(definition, "MythicMobs is not enabled.");
        }

        try {
            Entity entity = spawnMythicMob(definition.mythicMobId(), location, definition.level());
            if (entity == null) {
                return EggSpawnResult.failure(definition, "MythicMobs returned no entity.");
            }
            return EggSpawnResult.success(definition, entity, "&aSpawned &f" + definition.displayName() + "&a.");
        } catch (ReflectiveOperationException | RuntimeException ex) {
            plugin.getLogger().warning("Failed to spawn MythicMob '" + definition.mythicMobId() + "': " + ex.getMessage());
            return EggSpawnResult.failure(definition, "Failed to spawn Mythic mob '" + definition.mythicMobId() + "'.");
        }
    }

    private Entity spawnMythicMob(String mythicMobId, Location location, int level)
        throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Class<?> mythicBukkitClass = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
        Method instMethod = mythicBukkitClass.getMethod("inst");
        Object mythicInstance = instMethod.invoke(null);
        Method helperMethod = mythicBukkitClass.getMethod("getAPIHelper");
        Object apiHelper = helperMethod.invoke(mythicInstance);
        Method spawnMethod = apiHelper.getClass().getMethod("spawnMythicMob", String.class, Location.class, int.class);
        Object result = spawnMethod.invoke(apiHelper, mythicMobId, location, level);
        return result instanceof Entity entity ? entity : null;
    }

    private Location resolveSpawnLocation(Player player, Block clickedBlock, BlockFace face) {
        if (clickedBlock != null && face != null) {
            return clickedBlock.getRelative(face).getLocation().add(0.5D, 0.0D, 0.5D);
        }

        if (player != null) {
            return player.getLocation().add(player.getLocation().getDirection().normalize().multiply(2.0D));
        }

        return plugin.getServer().getWorlds().get(0).getSpawnLocation();
    }

    private int copyExternalExamples(Path mythicRoot) {
        int copied = 0;
        for (Path root : resolveExternalExampleRoots()) {
            copied += copyExampleGroup(root.resolve("Mobs"), mythicRoot.resolve("Mobs"));
            copied += copyExampleGroup(root.resolve("Skills"), mythicRoot.resolve("Skills"));
        }
        return copied;
    }

    private int copyExampleGroup(Path source, Path target) {
        if (!Files.isDirectory(source)) {
            return 0;
        }

        try {
            Files.createDirectories(target);
            int count = 0;
            try (Stream<Path> files = Files.list(source)) {
                for (Path file : files.filter(Files::isRegularFile).toList()) {
                    String name = file.getFileName().toString();
                    if (!name.toLowerCase(Locale.ROOT).endsWith(".yml")) {
                        continue;
                    }
                    Files.copy(file, target.resolve(name), StandardCopyOption.REPLACE_EXISTING);
                    count++;
                }
            }
            return count;
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to copy MythicMobs examples from " + source + ": " + ex.getMessage());
            return 0;
        }
    }

    private List<Path> resolveExternalExampleRoots() {
        Set<Path> roots = new LinkedHashSet<>();
        for (String raw : externalExampleRoots) {
            Path resolved = resolvePath(raw);
            if (resolved != null && Files.isDirectory(resolved)) {
                roots.add(resolved);
            }
        }
        return new ArrayList<>(roots);
    }

    private Path resolvePath(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        try {
            Path path = Path.of(raw.trim());
            if (!path.isAbsolute()) {
                path = plugin.getDataFolder().toPath().resolve(path).normalize();
            }
            if (Files.exists(path)) {
                return path;
            }

            String value = raw.trim();
            if (value.matches("^[A-Za-z]:[\\\\/].*")) {
                char drive = Character.toLowerCase(value.charAt(0));
                String tail = value.substring(2).replace('\\', '/');
                Path wsl = Path.of("/mnt/" + drive + tail);
                if (Files.exists(wsl)) {
                    return wsl;
                }
            }
            return path;
        } catch (Exception ex) {
            return null;
        }
    }

    private String buildGeneratedMobsYaml() {
        StringBuilder yaml = new StringBuilder();
        yaml.append("# Auto-generated by Bullseye MythicMobs integration\n\n");
        for (MythicMobDefinition definition : definitions.values()) {
            yaml.append(definition.mythicMobId()).append(":\n");
            yaml.append("  Type: ").append(definition.entityType()).append("\n");
            yaml.append("  Display: '").append(escapeSingleQuoted(definition.displayName())).append("'\n");
            yaml.append("  Health: ").append(trimDouble(definition.health())).append("\n");
            yaml.append("  Damage: ").append(trimDouble(definition.damage())).append("\n");
            if (!definition.spawnSkills().isEmpty()) {
                yaml.append("  Skills:\n");
                for (String skill : definition.spawnSkills()) {
                    yaml.append("    - ").append(skill).append("\n");
                }
            }
            yaml.append("\n");
        }
        return yaml.toString();
    }

    private String buildGeneratedSkillsYaml() {
        StringBuilder yaml = new StringBuilder();
        yaml.append("# Auto-generated by Bullseye MythicMobs integration\n\n");
        Set<String> emitted = new LinkedHashSet<>();
        for (MythicMobDefinition definition : definitions.values()) {
            for (Map.Entry<String, List<String>> entry : definition.generatedSkills().entrySet()) {
                if (!emitted.add(entry.getKey())) {
                    continue;
                }
                yaml.append(entry.getKey()).append(":\n");
                yaml.append("  Skills:\n");
                for (String line : entry.getValue()) {
                    yaml.append("    - ").append(line).append("\n");
                }
                yaml.append("\n");
            }
        }
        return yaml.toString();
    }

    private String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private void mergeNativeDefinitions() {
        if (mobService == null) {
            return;
        }

        for (CustomMobDefinition mobDefinition : mobService.getDefinitions()) {
            definitions.putIfAbsent(mobDefinition.id(), toMythicDefinition(mobDefinition));
            if (!mobDefinition.spawnEggItemId().isBlank()) {
                eggToDefinition.putIfAbsent(mobDefinition.spawnEggItemId(), mobDefinition.id());
            }
        }
    }

    private MythicMobDefinition toMythicDefinition(CustomMobDefinition mobDefinition) {
        return new MythicMobDefinition(
            mobDefinition.id(),
            buildGeneratedMythicId(mobDefinition.id()),
            mobDefinition.spawnEggItemId(),
            1,
            mobDefinition.consumeSpawnEgg(),
            mobDefinition.entityType().name(),
            mobDefinition.displayName(),
            mobDefinition.health(),
            mobDefinition.damage(),
            List.of(),
            Map.of()
        );
    }

    private String buildGeneratedMythicId(String id) {
        StringBuilder builder = new StringBuilder("Bullseye");
        for (String part : normalize(id).split("_")) {
            if (part.isBlank()) {
                continue;
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.toString();
    }

    private String escapeSingleQuoted(String value) {
        return value == null ? "" : value.replace("'", "''");
    }

    private String trimDouble(double value) {
        if (value == (long) value) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }

    public record EggSpawnResult(boolean handled, boolean success, MythicMobDefinition definition, Entity entity, String message) {
        public static EggSpawnResult unhandled() {
            return new EggSpawnResult(false, false, null, null, "");
        }

        public static EggSpawnResult success(MythicMobDefinition definition, Entity entity, String message) {
            return new EggSpawnResult(true, true, definition, entity, message);
        }

        public static EggSpawnResult failure(MythicMobDefinition definition, String message) {
            return new EggSpawnResult(true, false, definition, null, message);
        }
    }

    public record InjectionResult(
        boolean success,
        int copiedExampleFiles,
        int generatedSkillCount,
        Path mobsFile,
        Path skillsFile,
        String message
    ) {
    }
}
