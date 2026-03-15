package tsd.beye.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;
import tsd.beye.Bullseye;
import tsd.beye.utils.FileUtil;

public class ConversionService {
    private static final Set<String> DEFAULT_SUPPORTED_PLUGIN_KEYS = Set.of(
        "nexo",
        "oraxen",
        "champions",
        "mythicmobs",
        "modelengine"
    );
    private static final List<String> DEFAULT_EXTERNAL_CONTENT_ROOTS = List.of(
        "../ChampionAllies",
        "../Champions",
        "G:/Peach Tree Studios Clients/Champions",
        "/mnt/g/Peach Tree Studios Clients/Champions"
    );
    private static final String DEFAULT_CHAMPIONS_NAMESPACE = "champion_allies";
    private static final String DEFAULT_CHAMPIONS_TARGET_SUBDIR = "textures/champions";
    private static final String DEFAULT_CHAMPIONS_SIGNATURE_FILE = "DATJR.lock.hex";
    private static final String DEFAULT_CHAMPIONS_SIGNATURE_HEX = "4441544A52";

    private final Bullseye plugin;
    private final Map<String, Plugin> detectedPlugins = new LinkedHashMap<>();
    private final Set<String> convertedPlugins = new LinkedHashSet<>();

    private boolean detectionEnabled;
    private boolean remindAdmins;
    private boolean overwriteExistingImports;
    private Set<String> supportedPluginKeys = new LinkedHashSet<>(DEFAULT_SUPPORTED_PLUGIN_KEYS);
    private List<String> externalContentRoots = new ArrayList<>();
    private String championsNamespace = DEFAULT_CHAMPIONS_NAMESPACE;
    private String championsTargetSubdir = DEFAULT_CHAMPIONS_TARGET_SUBDIR;
    private boolean championsWriteSignatureLock = true;
    private String championsSignatureFile = DEFAULT_CHAMPIONS_SIGNATURE_FILE;
    private String championsSignatureHex = DEFAULT_CHAMPIONS_SIGNATURE_HEX;

    public ConversionService(Bullseye plugin) {
        this.plugin = plugin;
    }

    public void reloadSettings() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("conversion");
        if (section == null) {
            detectionEnabled = true;
            remindAdmins = true;
            overwriteExistingImports = true;
            convertedPlugins.clear();
            supportedPluginKeys = new LinkedHashSet<>(DEFAULT_SUPPORTED_PLUGIN_KEYS);
            externalContentRoots = new ArrayList<>(DEFAULT_EXTERNAL_CONTENT_ROOTS);
            championsNamespace = DEFAULT_CHAMPIONS_NAMESPACE;
            championsTargetSubdir = DEFAULT_CHAMPIONS_TARGET_SUBDIR;
            championsWriteSignatureLock = true;
            championsSignatureFile = DEFAULT_CHAMPIONS_SIGNATURE_FILE;
            championsSignatureHex = DEFAULT_CHAMPIONS_SIGNATURE_HEX;
            return;
        }

        detectionEnabled = section.getBoolean("detect-plugins", true);
        remindAdmins = section.getBoolean("remind-admins", true);
        overwriteExistingImports = section.getBoolean("overwrite-existing-imports", true);
        supportedPluginKeys = new LinkedHashSet<>(section.getStringList("supported-plugin-keys").stream()
            .map(this::normalizePluginKey)
            .filter(value -> !value.isBlank())
            .toList());
        if (supportedPluginKeys.isEmpty()) {
            supportedPluginKeys = new LinkedHashSet<>(DEFAULT_SUPPORTED_PLUGIN_KEYS);
        }
        externalContentRoots = new ArrayList<>(section.getStringList("external-content-roots"));
        if (externalContentRoots.isEmpty()) {
            externalContentRoots = new ArrayList<>(DEFAULT_EXTERNAL_CONTENT_ROOTS);
        }

        loadChampionsImportSettings(section.getConfigurationSection("champions"));

        convertedPlugins.clear();
        for (String value : section.getStringList("converted-plugins")) {
            convertedPlugins.add(normalizePluginKey(value));
        }
    }

    public void scanInstalledPlugins() {
        detectedPlugins.clear();
        if (!detectionEnabled) {
            return;
        }

        for (Plugin installed : Bukkit.getPluginManager().getPlugins()) {
            String normalized = normalizePluginKey(installed.getName());
            if (supportedPluginKeys.contains(normalized)) {
                detectedPlugins.putIfAbsent(normalized, installed);
            }
        }
    }

    public boolean hasDetectedContentPlugins() {
        return !detectedPlugins.isEmpty();
    }

    public boolean isConversionRecommended() {
        if (!remindAdmins || detectedPlugins.isEmpty()) {
            return false;
        }

        for (String pluginKey : detectedPlugins.keySet()) {
            if (!convertedPlugins.contains(pluginKey)) {
                return true;
            }
        }
        return false;
    }

    public List<String> getDetectedPluginNames() {
        return detectedPlugins.values().stream().map(Plugin::getName).sorted().toList();
    }

    public void logDetectionPrompt() {
        if (!isConversionRecommended()) {
            return;
        }

        plugin.getLogger().warning("Detected content plugin(s): " + String.join(", ", getDetectedPluginNames()) + ".");
        plugin.getLogger().warning("Run /bullseye convert to import resource-pack assets into Bullseye.");
        plugin.getLogger().warning("If you want to continue and override the server pack, run /bullseye enable confirm.");
    }

    public ConversionReport convertDetectedPlugins() {
        if (detectedPlugins.isEmpty()) {
            scanInstalledPlugins();
        }

        List<ConversionEntry> entries = new ArrayList<>();
        for (Map.Entry<String, Plugin> entry : detectedPlugins.entrySet()) {
            String key = entry.getKey();
            Plugin external = entry.getValue();
            ConversionEntry result = convertPluginAssets(key, external);
            entries.add(result);
            if (result.success()) {
                convertedPlugins.add(key);
            }
        }

        ConversionEntry externalChampions = convertExternalChampionsAssets();
        if (externalChampions != null) {
            entries.add(externalChampions);
            if (externalChampions.success()) {
                convertedPlugins.add("champions");
            }
        }

        persistConvertedPlugins();
        return new ConversionReport(entries);
    }

    private ConversionEntry convertPluginAssets(String pluginKey, Plugin external) {
        return convertAssetsFromPath(
            pluginKey,
            external.getName(),
            external.getDataFolder().toPath(),
            "converted-" + pluginKey
        );
    }

    private ConversionEntry convertExternalChampionsAssets() {
        List<Path> roots = resolveExternalContentRoots();
        List<Path> eligible = roots.stream()
            .filter(this::hasPotentialImportData)
            .toList();

        if (eligible.isEmpty()) {
            return null;
        }

        String targetFolderName = "converted-champions-external";
        Path targetRoot = plugin.getDataFolder().toPath().resolve("resourcepack/addons/" + targetFolderName);
        try {
            if (overwriteExistingImports && Files.exists(targetRoot)) {
                FileUtil.deleteDirectory(targetRoot);
            }
            Files.createDirectories(targetRoot);
        } catch (IOException ex) {
            return new ConversionEntry("Champions (external)", false, 0, "Could not prepare target folder: " + ex.getMessage());
        }

        int copiedSources = 0;
        for (Path root : eligible) {
            copiedSources += importGenericPackData(root, targetRoot);
            copiedSources += importChampionsBbModelAssets(root, targetRoot);
        }

        if (copiedSources <= 0) {
            return new ConversionEntry("Champions (external)", false, 0, "No resource-pack data found to import");
        }

        return new ConversionEntry(
            "Champions (external)",
            true,
            copiedSources,
            "Imported from " + eligible.size() + " root(s) to " + targetRoot
        );
    }

    private ConversionEntry convertAssetsFromPath(String pluginKey, String displayName, Path sourceRoot, String targetFolderName) {
        if (sourceRoot == null || !Files.exists(sourceRoot)) {
            return new ConversionEntry(displayName, false, 0, "Data folder not found");
        }

        Path targetRoot = plugin.getDataFolder().toPath().resolve("resourcepack/addons/" + targetFolderName);
        try {
            if (overwriteExistingImports && Files.exists(targetRoot)) {
                FileUtil.deleteDirectory(targetRoot);
            }
            Files.createDirectories(targetRoot);
        } catch (IOException ex) {
            return new ConversionEntry(displayName, false, 0, "Could not prepare target folder: " + ex.getMessage());
        }

        int copiedSources = importGenericPackData(sourceRoot, targetRoot);
        if ("champions".equals(pluginKey)) {
            copiedSources += importChampionsBbModelAssets(sourceRoot, targetRoot);
        }
        copiedSources += importPluginDataDirectories(pluginKey, sourceRoot, targetRoot.resolve("imported-data"));

        if (copiedSources <= 0) {
            return new ConversionEntry(displayName, false, 0, "No resource-pack data found to import");
        }

        return new ConversionEntry(displayName, true, copiedSources, "Imported to " + targetRoot);
    }

    private int importGenericPackData(Path sourceRoot, Path targetRoot) {
        int copiedSources = 0;

        List<String> candidateDirectories = List.of(
            "pack",
            "resourcepack",
            "ResourcePack",
            "assets",
            "textures",
            "models",
            "font",
            "fonts"
        );

        for (String relative : candidateDirectories) {
            Path source = sourceRoot.resolve(relative);
            if (!Files.isDirectory(source)) {
                continue;
            }

            Path destination = targetRoot.resolve(relative.toLowerCase(Locale.ROOT));
            try {
                FileUtil.copyDirectory(source, destination);
                copiedSources++;
            } catch (IOException ex) {
                plugin.getLogger().warning("Failed to copy " + source + " -> " + destination + ": " + ex.getMessage());
            }
        }

        List<String> candidateFiles = List.of("pack.mcmeta", "pack.png", "sounds.json");
        for (String relative : candidateFiles) {
            Path source = sourceRoot.resolve(relative);
            if (!Files.isRegularFile(source)) {
                continue;
            }

            Path destination = targetRoot.resolve(relative);
            try {
                Files.createDirectories(destination.getParent());
                Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
                copiedSources++;
            } catch (IOException ex) {
                plugin.getLogger().warning("Failed to copy " + source + " -> " + destination + ": " + ex.getMessage());
            }
        }

        copiedSources += copyPackZips(sourceRoot, targetRoot.resolve("imported-zips"));
        return copiedSources;
    }

    private int importChampionsBbModelAssets(Path sourceRoot, Path targetRoot) {
        List<Path> bbRoots = resolveChampionsBbModelRoots(sourceRoot);
        if (bbRoots.isEmpty()) {
            return 0;
        }

        Path namespaceRoot = targetRoot.resolve("assets").resolve(championsNamespace);
        Path textureRoot = namespaceRoot;
        for (String part : championsTargetSubdir.split("/")) {
            if (part == null || part.isBlank()) {
                continue;
            }
            textureRoot = textureRoot.resolve(part);
        }
        Path bbModelDumpRoot = targetRoot.resolve("bbmodels");

        int copied = 0;
        Set<Path> writtenTargets = new LinkedHashSet<>();

        for (Path bbRoot : bbRoots) {
            try (Stream<Path> stream = Files.walk(bbRoot)) {
                List<Path> files = stream.filter(Files::isRegularFile).toList();
                for (Path file : files) {
                    String lower = file.getFileName().toString().toLowerCase(Locale.ROOT);
                    if (!lower.endsWith(".png") && !lower.endsWith(".bbmodel")) {
                        continue;
                    }

                    Path relative = bbRoot.relativize(file);
                    if (relative.getNameCount() <= 0) {
                        continue;
                    }

                    boolean texture = lower.endsWith(".png");
                    Path sanitizedRelative = sanitizeRelativeFilePath(
                        relative,
                        texture ? "texture.png" : "model.bbmodel"
                    );

                    Path target = texture
                        ? textureRoot.resolve(sanitizedRelative)
                        : bbModelDumpRoot.resolve(sanitizedRelative);

                    if (!writtenTargets.add(target.normalize())) {
                        continue;
                    }

                    try {
                        Files.createDirectories(target.getParent());
                        Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
                        copied++;
                    } catch (IOException ex) {
                        plugin.getLogger().warning(
                            "Failed to import Champions asset " + file + " -> " + target + ": " + ex.getMessage()
                        );
                    }
                }
            } catch (IOException ex) {
                plugin.getLogger().warning("Failed to scan BBModels directory " + bbRoot + ": " + ex.getMessage());
            }
        }

        if (copied > 0 && championsWriteSignatureLock) {
            writeChampionsSignatureLock(textureRoot, namespaceRoot);
            copied += 2;
        }

        return copied;
    }

    private int importPluginDataDirectories(String pluginKey, Path sourceRoot, Path targetRoot) {
        List<String> directories = switch (pluginKey) {
            case "mythicmobs" -> List.of("Mobs", "Skills", "Items", "Drops", "RandomSpawns");
            case "modelengine" -> List.of("blueprints", "models", "animations", "bbmodels");
            default -> List.of();
        };

        if (directories.isEmpty()) {
            return 0;
        }

        int copied = 0;
        for (String directory : directories) {
            Path source = sourceRoot.resolve(directory);
            if (!Files.isDirectory(source)) {
                continue;
            }

            Path destination = targetRoot.resolve(pluginKey).resolve(sanitizePathSegment(directory, "data"));
            try {
                FileUtil.copyDirectory(source, destination);
                copied++;
            } catch (IOException ex) {
                plugin.getLogger().warning("Failed to copy " + source + " -> " + destination + ": " + ex.getMessage());
            }
        }
        return copied;
    }

    private List<Path> resolveChampionsBbModelRoots(Path sourceRoot) {
        if (sourceRoot == null || !Files.isDirectory(sourceRoot)) {
            return List.of();
        }

        List<Path> candidates = new ArrayList<>();
        String sourceName = sourceRoot.getFileName() == null ? "" : sourceRoot.getFileName().toString().toLowerCase(Locale.ROOT);
        if ("bbmodels".equals(sourceName)) {
            candidates.add(sourceRoot);
        }
        candidates.add(sourceRoot.resolve("BBModels"));
        candidates.add(sourceRoot.resolve("bbmodels"));
        candidates.add(sourceRoot.resolve("src/main/resources/BBModels"));
        candidates.add(sourceRoot.resolve("target/classes/BBModels"));

        Set<Path> resolved = new LinkedHashSet<>();
        for (Path candidate : candidates) {
            if (!Files.isDirectory(candidate)) {
                continue;
            }
            resolved.add(candidate.normalize().toAbsolutePath());
        }
        return new ArrayList<>(resolved);
    }

    private void writeChampionsSignatureLock(Path textureRoot, Path namespaceRoot) {
        List<Path> targets = List.of(
            textureRoot.resolve(championsSignatureFile),
            namespaceRoot.resolve(championsSignatureFile)
        );

        for (Path target : targets) {
            try {
                Files.createDirectories(target.getParent());
                Files.writeString(
                    target,
                    championsSignatureHex + System.lineSeparator(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
                );
            } catch (IOException ex) {
                plugin.getLogger().warning("Failed to write Champions signature lock '" + target + "': " + ex.getMessage());
            }
        }
    }

    private int copyPackZips(Path sourceRoot, Path targetDir) {
        try (Stream<Path> stream = Files.list(sourceRoot)) {
            List<Path> zips = stream
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip"))
                .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).contains("pack"))
                .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                .toList();

            int copied = 0;
            for (Path zip : zips) {
                Path destination = targetDir.resolve(zip.getFileName().toString());
                Files.createDirectories(destination.getParent());
                Files.copy(zip, destination, StandardCopyOption.REPLACE_EXISTING);
                Path extracted = targetDir.resolve(stripZipExtension(zip.getFileName().toString()));
                extractZip(destination, extracted);
                copied++;
            }
            return copied;
        } catch (IOException ex) {
            return 0;
        }
    }

    private void extractZip(Path zipFile, Path targetDir) {
        try {
            Files.createDirectories(targetDir);
            try (ZipInputStream zipInput = new ZipInputStream(Files.newInputStream(zipFile))) {
                ZipEntry entry;
                while ((entry = zipInput.getNextEntry()) != null) {
                    if (entry.getName() == null || entry.getName().isBlank()) {
                        continue;
                    }

                    Path resolved = resolveZipEntry(targetDir, entry.getName());
                    if (entry.isDirectory()) {
                        Files.createDirectories(resolved);
                    } else {
                        Files.createDirectories(resolved.getParent());
                        Files.copy(zipInput, resolved, StandardCopyOption.REPLACE_EXISTING);
                    }
                    zipInput.closeEntry();
                }
            }
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to extract converted pack zip '" + zipFile + "': " + ex.getMessage());
        }
    }

    private Path resolveZipEntry(Path targetDir, String entryName) throws IOException {
        Path normalized = targetDir.resolve(entryName).normalize();
        if (!normalized.startsWith(targetDir.normalize())) {
            throw new IOException("Blocked zip-slip entry: " + entryName);
        }
        return normalized;
    }

    private String stripZipExtension(String name) {
        int dot = name.toLowerCase(Locale.ROOT).lastIndexOf(".zip");
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private void persistConvertedPlugins() {
        List<String> values = convertedPlugins.stream().sorted().collect(Collectors.toList());
        plugin.getConfig().set("conversion.converted-plugins", values);
        plugin.saveConfig();
    }

    private void loadChampionsImportSettings(ConfigurationSection section) {
        if (section == null) {
            championsNamespace = DEFAULT_CHAMPIONS_NAMESPACE;
            championsTargetSubdir = DEFAULT_CHAMPIONS_TARGET_SUBDIR;
            championsWriteSignatureLock = true;
            championsSignatureFile = DEFAULT_CHAMPIONS_SIGNATURE_FILE;
            championsSignatureHex = DEFAULT_CHAMPIONS_SIGNATURE_HEX;
            return;
        }

        championsNamespace = sanitizePathSegment(
            section.getString("namespace", DEFAULT_CHAMPIONS_NAMESPACE),
            DEFAULT_CHAMPIONS_NAMESPACE
        );
        championsTargetSubdir = sanitizeSubPath(
            section.getString("target-subdir", DEFAULT_CHAMPIONS_TARGET_SUBDIR),
            DEFAULT_CHAMPIONS_TARGET_SUBDIR
        );
        championsWriteSignatureLock = section.getBoolean("write-signature-lock", true);
        championsSignatureFile = sanitizeLockFileName(section.getString("signature-file-name", DEFAULT_CHAMPIONS_SIGNATURE_FILE));
        championsSignatureHex = normalizeHex(section.getString("signature-hex", DEFAULT_CHAMPIONS_SIGNATURE_HEX));
    }

    private List<Path> resolveExternalContentRoots() {
        if (externalContentRoots == null || externalContentRoots.isEmpty()) {
            return List.of();
        }

        Set<Path> roots = new LinkedHashSet<>();
        for (String raw : externalContentRoots) {
            Path resolved = resolveExternalPath(raw);
            if (resolved == null || !Files.isDirectory(resolved)) {
                continue;
            }
            roots.add(resolved.normalize().toAbsolutePath());
        }
        return new ArrayList<>(roots);
    }

    private Path resolveExternalPath(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String trimmed = raw.trim();
        Path candidate = parsePathSafely(trimmed);
        if (candidate == null) {
            return null;
        }

        if (!candidate.isAbsolute()) {
            candidate = plugin.getDataFolder().toPath().resolve(candidate).normalize();
        }

        if (Files.exists(candidate)) {
            return candidate;
        }

        if (trimmed.matches("^[A-Za-z]:[\\\\/].*")) {
            char drive = Character.toLowerCase(trimmed.charAt(0));
            String tail = trimmed.substring(2).replace('\\', '/');
            Path wsl = Path.of("/mnt/" + drive + tail);
            if (Files.exists(wsl)) {
                return wsl;
            }
        }

        if (trimmed.contains("\\")) {
            Path unixStyle = parsePathSafely(trimmed.replace('\\', '/'));
            if (unixStyle != null) {
                if (!unixStyle.isAbsolute()) {
                    unixStyle = plugin.getDataFolder().toPath().resolve(unixStyle).normalize();
                }
                if (Files.exists(unixStyle)) {
                    return unixStyle;
                }
            }
        }

        return candidate;
    }

    private boolean hasPotentialImportData(Path root) {
        if (root == null || !Files.isDirectory(root)) {
            return false;
        }

        for (String directory : List.of("resourcepack", "pack", "assets", "BBModels", "bbmodels")) {
            if (Files.isDirectory(root.resolve(directory))) {
                return true;
            }
        }

        for (String file : List.of("pack.mcmeta", "pack.png", "sounds.json")) {
            if (Files.isRegularFile(root.resolve(file))) {
                return true;
            }
        }

        try (Stream<Path> files = Files.list(root)) {
            return files
                .filter(Files::isRegularFile)
                .anyMatch(path -> {
                    String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                    return name.endsWith(".zip") && name.contains("pack");
                });
        } catch (IOException ex) {
            return false;
        }
    }

    private String normalizePluginKey(String pluginName) {
        if (pluginName == null) {
            return "";
        }

        String key = pluginName.trim().toLowerCase(Locale.ROOT);
        return switch (key) {
            case "oraxcen" -> "oraxen";
            case "championallies", "champion_allies", "champion-allies", "champions" -> "champions";
            case "modelenginer4", "modelengine_r4", "modelengine-r4" -> "modelengine";
            default -> key;
        };
    }

    private Path parsePathSafely(String value) {
        try {
            return Path.of(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private Path sanitizeRelativeFilePath(Path relative, String fallbackFileName) {
        List<String> parts = new ArrayList<>();
        int lastIndex = relative.getNameCount() - 1;
        for (int i = 0; i < relative.getNameCount(); i++) {
            String part = relative.getName(i).toString();
            if (i == lastIndex) {
                parts.add(sanitizeFileName(part, fallbackFileName));
            } else {
                parts.add(sanitizePathSegment(part, "asset"));
            }
        }

        if (parts.isEmpty()) {
            return Path.of(sanitizeFileName("", fallbackFileName));
        }
        return Path.of(String.join("/", parts));
    }

    private String sanitizeSubPath(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }

        String[] split = value.replace('\\', '/').split("/");
        List<String> sanitized = new ArrayList<>();
        for (String part : split) {
            if (part == null || part.isBlank()) {
                continue;
            }
            sanitized.add(sanitizePathSegment(part, "textures"));
        }

        if (sanitized.isEmpty()) {
            return fallback;
        }
        return String.join("/", sanitized);
    }

    private String sanitizeFileName(String fileName, String fallback) {
        String safeFallback = fallback == null || fallback.isBlank() ? "asset.dat" : fallback;
        String normalized = fileName == null ? "" : fileName.trim().replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String leaf = slash >= 0 ? normalized.substring(slash + 1) : normalized;

        int fallbackDot = safeFallback.lastIndexOf('.');
        String fallbackStem = fallbackDot > 0 ? safeFallback.substring(0, fallbackDot) : safeFallback;
        String fallbackExt = fallbackDot > 0 ? safeFallback.substring(fallbackDot + 1) : "dat";

        int dot = leaf.lastIndexOf('.');
        String stem = dot > 0 ? leaf.substring(0, dot) : leaf;
        String ext = dot > 0 ? leaf.substring(dot + 1) : fallbackExt;

        String sanitizedExt = ext.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (sanitizedExt.isBlank()) {
            sanitizedExt = fallbackExt.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        }
        if (sanitizedExt.isBlank()) {
            sanitizedExt = "dat";
        }

        return sanitizePathSegment(stem, sanitizePathSegment(fallbackStem, "asset")) + "." + sanitizedExt;
    }

    private String sanitizePathSegment(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }

        String normalized = value.toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        StringBuilder builder = new StringBuilder();
        for (char c : normalized.toCharArray()) {
            boolean allowed = (c >= 'a' && c <= 'z')
                || (c >= '0' && c <= '9')
                || c == '_';
            if (allowed) {
                builder.append(c);
            }
        }

        if (builder.isEmpty()) {
            return fallback;
        }
        return builder.toString();
    }

    private String sanitizeLockFileName(String value) {
        String raw = value == null ? DEFAULT_CHAMPIONS_SIGNATURE_FILE : value.trim();
        if (raw.isBlank()) {
            return DEFAULT_CHAMPIONS_SIGNATURE_FILE;
        }
        return raw.replace('\\', '_').replace('/', '_');
    }

    private String normalizeHex(String rawValue) {
        String raw = rawValue == null ? "" : rawValue.trim();
        if (raw.startsWith("0x") || raw.startsWith("0X")) {
            raw = raw.substring(2);
        }
        String cleaned = raw.replaceAll("[^a-fA-F0-9]", "");
        if (cleaned.isBlank() || cleaned.length() % 2 != 0) {
            return DEFAULT_CHAMPIONS_SIGNATURE_HEX;
        }
        return cleaned.toUpperCase(Locale.ROOT);
    }

    public record ConversionEntry(String pluginName, boolean success, int copiedSources, String message) {
    }

    public record ConversionReport(List<ConversionEntry> entries) {
        public int successfulConversions() {
            return (int) entries.stream().filter(ConversionEntry::success).count();
        }

        public int totalCopiedSources() {
            return entries.stream().mapToInt(ConversionEntry::copiedSources).sum();
        }
    }
}
