package tsd.ttt.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import tsd.ttt.TTT;
import tsd.ttt.model.CustomItemDefinition;
import tsd.ttt.utils.FileUtil;
import tsd.ttt.utils.HashUtil;
import tsd.ttt.utils.TextUtil;

public class ResourcePackService {
    private static final String DEFAULT_PACK_FORMAT = "94.1";

    private final TTT plugin;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private ItemService itemService;

    private boolean enabled;
    private boolean autoSend;
    private boolean force;
    private boolean obfuscate;
    private boolean overrideServerPack;
    private boolean mirrorChampionsTextures;

    private String url;
    private String prompt;
    private String outputFile;
    private String packMinFormat = DEFAULT_PACK_FORMAT;
    private String packMaxFormat = DEFAULT_PACK_FORMAT;
    private List<String> sourceFolders = new ArrayList<>();

    private boolean hostingEnabled;
    private boolean hostingAutoRebuildIfMissing;
    private String hostingProtocol;
    private String hostingBindAddress;
    private String hostingPublicHost;
    private int hostingPort;
    private String hostingPath;

    private HttpServer httpServer;
    private ExecutorService hostingExecutor;

    private byte[] latestHashBytes;
    private String latestHashHex;
    private boolean serverPropertiesHasPack;
    private boolean overrideWarningLogged;

    public ResourcePackService(TTT plugin) {
        this.plugin = plugin;
    }

    public void setItemService(ItemService itemService) {
        this.itemService = itemService;
    }

    public void reloadSettings() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("resource-pack");
        if (section == null) {
            enabled = false;
            autoSend = false;
            overrideServerPack = false;
            hostingEnabled = false;
            return;
        }

        enabled = section.getBoolean("enabled", true);
        autoSend = section.getBoolean("auto-send", true);
        force = section.getBoolean("force", false);
        obfuscate = section.getBoolean("obfuscate", false);
        overrideServerPack = section.getBoolean("override-server-pack", false);
        mirrorChampionsTextures = section.getBoolean("compat.mirror-champions-textures", true);
        url = section.getString("url", "");
        prompt = section.getString("prompt", "");
        outputFile = section.getString("output-file", "generated/ttt-pack.zip");
        packMinFormat = parsePackFormat(section.getString("pack-min-format", DEFAULT_PACK_FORMAT));
        packMaxFormat = parsePackFormat(section.getString("pack-max-format", packMinFormat));

        sourceFolders = section.getStringList("source-folders");
        if (sourceFolders.isEmpty()) {
            sourceFolders = List.of("resourcepack/base", "resourcepack/addons");
        }

        serverPropertiesHasPack = detectServerPropertiesPack();
        overrideWarningLogged = false;

        ConfigurationSection hostingSection = section.getConfigurationSection("hosting");
        if (hostingSection == null) {
            hostingEnabled = false;
            hostingAutoRebuildIfMissing = true;
            hostingProtocol = "http";
            hostingBindAddress = "0.0.0.0";
            hostingPublicHost = "";
            hostingPort = 25590;
            hostingPath = "/resourcepack/ttt-pack.zip";
            return;
        }

        hostingEnabled = hostingSection.getBoolean("enabled", false);
        hostingAutoRebuildIfMissing = hostingSection.getBoolean("auto-rebuild-if-missing", true);
        hostingProtocol = hostingSection.getString("protocol", "http");
        hostingBindAddress = hostingSection.getString("bind-address", "0.0.0.0");
        hostingPublicHost = hostingSection.getString("public-host", "");
        hostingPort = clampPort(hostingSection.getInt("port", 25590));
        hostingPath = normalizeContextPath(hostingSection.getString("path", "/resourcepack/ttt-pack.zip"));
    }

    public void startOrRestartHosting() {
        stopHosting();

        if (!hostingEnabled) {
            return;
        }

        try {
            InetSocketAddress bind = createBindAddress();
            httpServer = HttpServer.create(bind, 0);
            httpServer.createContext(normalizeContextPath(hostingPath), this::handleHostedPackRequest);
            hostingExecutor = Executors.newFixedThreadPool(2, new ResourcePackThreadFactory());
            httpServer.setExecutor(hostingExecutor);
            httpServer.start();

            plugin.getLogger().info("TTT resource pack hosting enabled at " + buildHostedUrl());

            if (hostingAutoRebuildIfMissing && !hasPackFile()) {
                plugin.getLogger().info("No generated pack found; rebuilding because hosting auto-rebuild is enabled.");
                rebuildPack();
            }
        } catch (IOException | RuntimeException ex) {
            plugin.getLogger().severe("Failed to start built-in resource pack host: " + ex.getMessage());
            stopHosting();
        }
    }

    public void shutdown() {
        stopHosting();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isAutoSend() {
        return autoSend;
    }

    public boolean isOverrideServerPack() {
        return overrideServerPack;
    }

    public boolean isHostingEnabled() {
        return hostingEnabled;
    }

    public boolean isHostingRunning() {
        return httpServer != null;
    }

    public boolean hasExistingServerResourcePack() {
        return serverPropertiesHasPack;
    }

    public String getLatestHashHex() {
        return latestHashHex;
    }

    public boolean hasPackFile() {
        return Files.exists(getPackPath());
    }

    public synchronized boolean rebuildPack() {
        Path data = plugin.getDataFolder().toPath();
        Path temp = data.resolve("generated/.temp_pack");
        Path output = getPackPath();

        try {
            FileUtil.deleteDirectory(temp);
            Files.createDirectories(temp);

            for (String sourceFolder : orderedSourceFolders()) {
                Path source = resolvePath(data, sourceFolder);
                if (!Files.exists(source)) {
                    continue;
                }
                mergeSourceIntoTemp(source, temp);
            }

            if (mirrorChampionsTextures) {
                mirrorChampionTextureLayout(temp);
            }

            generateCustomItemPackAssets(temp);
            generateArmorPackAssets(temp);

            if (obfuscate) {
                obfuscateItemAssets(temp);
            }

            writePackMeta(temp);

            FileUtil.zipDirectory(temp, output);

            File outputFileObject = output.toFile();
            latestHashBytes = HashUtil.sha1Bytes(outputFileObject);
            latestHashHex = HashUtil.toHex(latestHashBytes);
            plugin.getLogger().info("Resource pack rebuilt: " + output + " (sha1=" + latestHashHex + ")");
            return true;
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to rebuild resource pack: " + ex.getMessage());
            latestHashBytes = null;
            latestHashHex = null;
            return false;
        } finally {
            try {
                FileUtil.deleteDirectory(temp);
            } catch (IOException ignored) {
            }
        }
    }

    public boolean sendPack(Player player) {
        return sendPack(player, null);
    }

    public synchronized boolean sendPack(Player player, String joinHostnameOverride) {
        if (!enabled) {
            return false;
        }

        if (!overrideServerPack && serverPropertiesHasPack) {
            if (!overrideWarningLogged) {
                plugin.getLogger().warning("TTT pack send skipped: server.properties already defines a resource-pack.");
                plugin.getLogger().warning("Run /ttt enable confirm to allow TTT to override the current server pack.");
                overrideWarningLogged = true;
            }
            return false;
        }

        if (hostingEnabled && hostingAutoRebuildIfMissing && !hasPackFile()) {
            rebuildPack();
        }

        refreshHashFromCurrentPackIfPresent();

        String resolvedUrl = resolvePackUrl(joinHostnameOverride);
        if (resolvedUrl == null || resolvedUrl.isBlank()) {
            plugin.getLogger().warning("Resource pack URL is not configured (external URL empty and hosting unavailable).");
            return false;
        }

        String resolvedPrompt = TextUtil.colorize(prompt);
        if (latestHashBytes != null) {
            player.setResourcePack(resolvedUrl, latestHashBytes, resolvedPrompt, force);
        } else {
            player.setResourcePack(resolvedUrl);
        }

        return true;
    }

    public void setEnabled(boolean value) {
        enabled = value;
        plugin.getConfig().set("resource-pack.enabled", value);
        plugin.saveConfig();
    }

    public void setAutoSend(boolean value) {
        autoSend = value;
        plugin.getConfig().set("resource-pack.auto-send", value);
        plugin.saveConfig();
    }

    public void setOverrideServerPack(boolean value) {
        overrideServerPack = value;
        plugin.getConfig().set("resource-pack.override-server-pack", value);
        plugin.saveConfig();
    }

    public String buildHostedUrl() {
        return buildHostedUrlForHost(null);
    }

    public String buildHostedUrlForHost(String hostOverride) {
        String host = hostingPublicHost == null ? "" : hostingPublicHost.trim();
        if (host.isBlank() && hostOverride != null && !hostOverride.isBlank()) {
            host = hostOverride.trim();
        }
        if (host.isBlank()) {
            host = plugin.getServer().getIp();
        }
        if (host == null || host.isBlank()) {
            host = "127.0.0.1";
        }
        if (host.contains(":") && !host.startsWith("[") && !host.endsWith("]")) {
            host = "[" + host + "]";
        }

        return hostingProtocol + "://" + host + ":" + hostingPort + normalizeContextPath(hostingPath);
    }

    private void handleHostedPackRequest(HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod();
            if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            Path packPath = getPackPath();
            if (!Files.exists(packPath)) {
                byte[] notFound = "TTT resource pack not built yet".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
                exchange.sendResponseHeaders(404, notFound.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(notFound);
                }
                return;
            }

            refreshHashFromCurrentPackIfPresent();

            exchange.getResponseHeaders().add("Content-Type", "application/zip");
            exchange.getResponseHeaders().add("Cache-Control", "no-cache");
            if (latestHashHex != null) {
                exchange.getResponseHeaders().add("ETag", '"' + latestHashHex + '"');
            }

            long length = Files.size(packPath);
            if ("HEAD".equalsIgnoreCase(method)) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }

            exchange.sendResponseHeaders(200, length);
            try (OutputStream out = exchange.getResponseBody()) {
                Files.copy(packPath, out);
            }
        } finally {
            exchange.close();
        }
    }

    private String resolvePackUrl(String joinHostnameOverride) {
        boolean hostedReady = hostingEnabled && httpServer != null;

        String baseUrl;
        if (hostedReady) {
            baseUrl = buildHostedUrlForHost(joinHostnameOverride);
        } else {
            baseUrl = url == null ? "" : url;
        }

        if (baseUrl.isBlank()) {
            return "";
        }

        if (hostedReady && latestHashHex != null) {
            return appendQuery(baseUrl, "hash=" + latestHashHex);
        }

        if (!hostedReady && latestHashHex != null) {
            return baseUrl.replace("{hash}", latestHashHex);
        }

        return baseUrl;
    }

    private String appendQuery(String baseUrl, String query) {
        if (baseUrl.contains("?")) {
            return baseUrl + "&" + query;
        }
        return baseUrl + "?" + query;
    }

    private Path getPackPath() {
        return plugin.getDataFolder().toPath().resolve(outputFile);
    }

    private InetSocketAddress createBindAddress() {
        String bindAddress = hostingBindAddress == null ? "" : hostingBindAddress.trim();
        if (bindAddress.isBlank() || "0.0.0.0".equals(bindAddress)) {
            return new InetSocketAddress(hostingPort);
        }
        return new InetSocketAddress(bindAddress, hostingPort);
    }

    private void stopHosting() {
        if (httpServer != null) {
            httpServer.stop(0);
            httpServer = null;
        }
        if (hostingExecutor != null) {
            hostingExecutor.shutdownNow();
            hostingExecutor = null;
        }
    }

    private void refreshHashFromCurrentPackIfPresent() {
        Path path = getPackPath();
        if (!Files.exists(path)) {
            return;
        }

        try {
            byte[] bytes = HashUtil.sha1Bytes(path.toFile());
            latestHashBytes = bytes;
            latestHashHex = HashUtil.toHex(bytes);
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to calculate pack hash: " + ex.getMessage());
        }
    }

    private Path resolvePath(Path dataFolder, String sourceFolder) {
        Path path = Path.of(sourceFolder);
        if (!path.isAbsolute()) {
            path = dataFolder.resolve(sourceFolder);
        }
        return path;
    }

    private List<String> orderedSourceFolders() {
        List<String> configured = sourceFolders == null ? List.of() : sourceFolders;
        List<String> ordered = new ArrayList<>(new LinkedHashSet<>(configured));

        if (ordered.stream().noneMatch(this::isBaseSourceFolder)) {
            ordered.add("resourcepack/base");
        }

        List<String> baseEntries = ordered.stream().filter(this::isBaseSourceFolder).toList();
        ordered.removeIf(this::isBaseSourceFolder);
        ordered.add(0, baseEntries.isEmpty() ? "" : baseEntries.getFirst());
        ordered.removeIf(String::isEmpty);
        return ordered;
    }

    private void mergeSourceIntoTemp(Path source, Path temp) throws IOException {
        if (!Files.isDirectory(source)) {
            if (Files.isRegularFile(source)) {
                Files.copy(source, temp.resolve(source.getFileName().toString()), StandardCopyOption.REPLACE_EXISTING);
            }
            return;
        }

        if (isPackRoot(source)) {
            copyDirectoryContents(source, temp);
            return;
        }

        List<Path> children;
        try (Stream<Path> childStream = Files.list(source)) {
            children = childStream.sorted().toList();
        }

        for (Path child : children) {
            if (!Files.isDirectory(child)) {
                continue;
            }

            if (isPackRoot(child)) {
                copyDirectoryContents(child, temp);
            } else {
                FileUtil.copyDirectory(child, temp.resolve(child.getFileName().toString()));
            }
        }
    }

    private boolean isPackRoot(Path path) {
        return Files.exists(path.resolve("pack.mcmeta")) || Files.isDirectory(path.resolve("assets"));
    }

    private void copyDirectoryContents(Path source, Path target) throws IOException {
        Files.createDirectories(target);
        try (Stream<Path> children = Files.list(source)) {
            for (Path child : children.toList()) {
                Path destination = target.resolve(child.getFileName().toString());
                if (Files.isDirectory(child)) {
                    FileUtil.copyDirectory(child, destination);
                } else {
                    Files.copy(child, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private void obfuscateItemAssets(Path root) {
        Path modelDir = root.resolve("assets/ttt/models/item");
        Path textureDir = root.resolve("assets/ttt/textures/item");

        if (!Files.exists(modelDir) && !Files.exists(textureDir)) {
            return;
        }

        Map<String, String> textureReplacements = renameAssets(textureDir, "ttt:item/", List.of(".png.mcmeta", ".png"));
        Map<String, String> modelReplacements = renameAssets(modelDir, "ttt:item/", List.of(".json"));

        List<Path> jsonFiles;
        try (Stream<Path> stream = Files.walk(root)) {
            jsonFiles = stream
                .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".json"))
                .sorted()
                .toList();
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to walk resource pack json files for obfuscation: " + ex.getMessage());
            return;
        }

        for (Path jsonFile : jsonFiles) {
            try {
                String text = Files.readString(jsonFile, StandardCharsets.UTF_8);
                text = replaceTextureReferences(text, textureReplacements);
                text = replaceModelReferences(text, modelReplacements);
                Files.writeString(jsonFile, text, StandardCharsets.UTF_8);
            } catch (IOException ex) {
                plugin.getLogger().warning("Skipped obfuscating unreadable resource pack json " + jsonFile.getFileName() + ": " + ex.getMessage());
            }
        }
    }

    private String replaceTextureReferences(String text, Map<String, String> replacements) {
        String output = text;
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            output = replaceJsonStringValue(output, "layer\\d*", entry.getKey(), entry.getValue());
        }
        return output;
    }

    private String replaceModelReferences(String text, Map<String, String> replacements) {
        String output = text;
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            output = replaceJsonStringValue(output, "model", entry.getKey(), entry.getValue());
        }
        return output;
    }

    private String replaceJsonStringValue(String text, String keyPattern, String oldValue, String newValue) {
        Pattern pattern = Pattern.compile("(\"" + keyPattern + "\"\\s*:\\s*)\"" + Pattern.quote(oldValue) + "\"");
        return pattern.matcher(text).replaceAll("$1\"" + Matcher.quoteReplacement(newValue) + "\"");
    }

    private Map<String, String> renameAssets(Path directory, String prefix, List<String> suffixes) {
        Map<String, String> replacements = new LinkedHashMap<>();
        if (!Files.exists(directory)) {
            return replacements;
        }

        try (Stream<Path> stream = Files.list(directory)) {
            List<Path> files = stream.filter(Files::isRegularFile).toList();

            Map<String, String> obfuscatedByAsset = new LinkedHashMap<>();
            List<String> skipped = new ArrayList<>();
            for (Path file : files) {
                String name = file.getFileName().toString();
                String suffix = matchSuffix(name, suffixes);
                if (suffix.isEmpty()) {
                    skipped.add(name);
                    continue;
                }

                String assetBase = name.substring(0, name.length() - suffix.length());
                obfuscatedByAsset.computeIfAbsent(assetBase, this::shortSha1);
            }

            for (String name : skipped) {
                plugin.getLogger().warning("Obfuscation skipped unexpected resource pack asset '" + name + "' in " + directory.getFileName() + ".");
            }

            for (Map.Entry<String, String> entry : obfuscatedByAsset.entrySet()) {
                String assetBase = entry.getKey();
                String obfuscated = entry.getValue();
                for (String suffix : suffixes) {
                    Path file = directory.resolve(assetBase + suffix);
                    if (!Files.exists(file)) {
                        continue;
                    }

                    Files.move(file, directory.resolve(obfuscated + suffix), StandardCopyOption.REPLACE_EXISTING);
                }
                replacements.put(prefix + assetBase, prefix + obfuscated);
            }
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to obfuscate assets in " + directory + ": " + ex.getMessage());
        }

        return replacements;
    }

    private String matchSuffix(String fileName, List<String> suffixes) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        for (String suffix : suffixes) {
            if (lower.endsWith(suffix) && fileName.length() > suffix.length()) {
                return fileName.substring(fileName.length() - suffix.length());
            }
        }
        return "";
    }

    private void writePackMeta(Path temp) throws IOException {
        Path metaPath = temp.resolve("pack.mcmeta");
        if (Files.exists(metaPath)) {
            plugin.getLogger().warning(
                "Replacing pack.mcmeta inherited from a source pack with the TTT pack descriptor; "
                    + "addon packs must ship assets only."
            );
        }

        Files.writeString(
            metaPath,
            """
            {
              "pack": {
                "min_format": %s,
                "max_format": %s,
                "description": "TTT generated resource pack"
              }
            }
            """.formatted(packMinFormat, packMaxFormat),
            StandardCharsets.UTF_8
        );
    }

    private void mirrorChampionTextureLayout(Path root) {
        Path assetsRoot = root.resolve("assets");
        if (!Files.isDirectory(assetsRoot)) {
            return;
        }

        try (Stream<Path> namespaces = Files.list(assetsRoot)) {
            for (Path namespaceRoot : namespaces.filter(Files::isDirectory).toList()) {
                Path championsRoot = namespaceRoot.resolve("textures/champions");
                if (!Files.isDirectory(championsRoot)) {
                    continue;
                }

                Path texturesRoot = namespaceRoot.resolve("textures");
                try (Stream<Path> files = Files.walk(championsRoot)) {
                    for (Path source : files.filter(Files::isRegularFile).toList()) {
                        if (!isTextureAsset(source)) {
                            continue;
                        }

                        Path relative = championsRoot.relativize(source);
                        Path target = texturesRoot.resolve(relative);
                        if (Files.exists(target)) {
                            continue;
                        }

                        Files.createDirectories(target.getParent());
                        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to apply Champions texture compatibility mirror: " + ex.getMessage());
        }
    }

    private boolean isTextureAsset(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".png")) {
            return true;
        }
        return name.endsWith(".png.mcmeta");
    }

    private boolean isBaseSourceFolder(String pathValue) {
        if (pathValue == null || pathValue.isBlank()) {
            return false;
        }

        String normalized = pathValue.replace('\\', '/').toLowerCase(Locale.ROOT).trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.endsWith("resourcepack/base");
    }

    private void generateCustomItemPackAssets(Path root) {
        if (itemService == null) {
            return;
        }

        Map<String, List<CustomItemDefinition>> byBaseModel = new HashMap<>();
        for (CustomItemDefinition definition : itemService.getDefinitions()) {
            if (definition.customModelData() <= 0) {
                continue;
            }
            byBaseModel.computeIfAbsent(materialModelName(definition.baseMaterial()), key -> new ArrayList<>()).add(definition);
        }

        Path namespaceModelDir = root.resolve("assets/ttt/models/item");
        Path namespaceTextureDir = root.resolve("assets/ttt/textures/item");
        Path minecraftModelDir = root.resolve("assets/minecraft/models/item");
        Path minecraftItemDir = root.resolve("assets/minecraft/items");

        try {
            Files.createDirectories(namespaceModelDir);
            Files.createDirectories(namespaceTextureDir);
            Files.createDirectories(minecraftModelDir);
            Files.createDirectories(minecraftItemDir);

            for (CustomItemDefinition definition : itemService.getDefinitions()) {
                if (definition.customModelData() <= 0) {
                    continue;
                }

                Path itemModel = namespaceModelDir.resolve(definition.id() + ".json");
                if (!Files.exists(itemModel)) {
                    Path expectedTexture = namespaceTextureDir.resolve(definition.id() + ".png");
                    if (Files.exists(expectedTexture)) {
                        Files.writeString(
                            itemModel,
                            """
                            {
                              "parent": "%s",
                              "textures": {
                                "layer0": "ttt:item/%s"
                              }
                            }
                            """.formatted(itemModelParent(definition.baseMaterial()), definition.id()).trim(),
                            StandardCharsets.UTF_8
                        );
                    }
                }
            }

            for (Map.Entry<String, List<CustomItemDefinition>> entry : byBaseModel.entrySet()) {
                List<CustomItemDefinition> definitions = new ArrayList<>(entry.getValue());
                definitions.sort((left, right) -> Integer.compare(left.customModelData(), right.customModelData()));
                writeLegacyItemOverrides(minecraftModelDir.resolve(entry.getKey() + ".json"), entry.getKey(), definitions);
                writeModernItemDefinition(minecraftItemDir.resolve(entry.getKey() + ".json"), entry.getKey(), definitions);
            }
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to generate TTT item model overrides: " + ex.getMessage());
        }
    }

    private void generateArmorPackAssets(Path root) {
        if (itemService == null) {
            return;
        }

        Map<String, List<String>> assetToItems = new LinkedHashMap<>();
        for (CustomItemDefinition definition : itemService.getDefinitions()) {
            if (definition.armor() != null && !definition.armor().asset().isBlank()) {
                assetToItems.computeIfAbsent(definition.armor().asset(), key -> new ArrayList<>()).add(definition.id());
            }
        }
        if (assetToItems.isEmpty()) {
            return;
        }

        Path equipmentDir = root.resolve("assets/ttt/equipment");
        Path layerDir = root.resolve("assets/ttt/textures/entity/armor");
        try {
            Files.createDirectories(equipmentDir);
            for (Map.Entry<String, List<String>> entry : assetToItems.entrySet()) {
                String asset = entry.getKey();
                JsonObject layer1 = new JsonObject();
                layer1.addProperty("texture", "ttt:entity/armor/" + asset + "_layer_1");
                JsonObject layer2 = new JsonObject();
                layer2.addProperty("texture", "ttt:entity/armor/" + asset + "_layer_2");
                JsonArray humanoid = new JsonArray();
                humanoid.add(layer1);
                JsonArray leggings = new JsonArray();
                leggings.add(layer2);
                JsonObject layers = new JsonObject();
                layers.add("humanoid", humanoid);
                layers.add("humanoid_leggings", leggings);
                JsonObject equipment = new JsonObject();
                equipment.add("layers", layers);
                writeJson(equipmentDir.resolve(asset + ".json"), equipment);

                for (String layer : List.of(asset + "_layer_1.png", asset + "_layer_2.png")) {
                    if (!Files.exists(layerDir.resolve(layer))) {
                        plugin.getLogger().warning("Armor asset '" + asset + "' is missing texture entity/armor/" + layer
                            + " (used by " + String.join(", ", entry.getValue()) + ").");
                    }
                }
            }
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to generate TTT armor equipment assets: " + ex.getMessage());
        }
    }

    private void writeLegacyItemOverrides(Path path, String materialName, List<CustomItemDefinition> definitions) throws IOException {
        JsonObject root = readJsonObject(path);
        if (!root.has("parent")) {
            root.addProperty("parent", materialName.endsWith("_spawn_egg")
                ? "minecraft:item/template_spawn_egg"
                : "minecraft:item/generated");
        }
        if (!root.has("textures")) {
            JsonObject textures = new JsonObject();
            textures.addProperty("layer0", "minecraft:item/" + materialName);
            root.add("textures", textures);
        }

        JsonArray overrides = root.has("overrides") && root.get("overrides").isJsonArray()
            ? root.getAsJsonArray("overrides")
            : new JsonArray();
        java.util.Set<String> knownOverrides = new java.util.HashSet<>();
        for (JsonElement element : overrides) {
            if (element.isJsonObject()) {
                knownOverrides.add(element.getAsJsonObject().toString());
            }
        }
        for (CustomItemDefinition definition : definitions) {
            JsonObject predicate = new JsonObject();
            predicate.addProperty("custom_model_data", definition.customModelData());
            JsonObject override = new JsonObject();
            override.add("predicate", predicate);
            override.addProperty("model", "ttt:item/" + definition.id());
            if (knownOverrides.add(override.toString())) {
                overrides.add(override);
            }
        }
        root.add("overrides", overrides);
        writeJson(path, root);
    }

    private void writeModernItemDefinition(Path path, String materialName, List<CustomItemDefinition> definitions) throws IOException {
        JsonObject root = readJsonObject(path);
        JsonElement existingModel = root.get("model");
        JsonObject dispatch;
        if (isCustomModelDataDispatch(existingModel)) {
            dispatch = existingModel.getAsJsonObject();
        } else {
            dispatch = new JsonObject();
            dispatch.addProperty("type", "minecraft:range_dispatch");
            dispatch.addProperty("property", "minecraft:custom_model_data");
            dispatch.add("fallback", existingModel == null ? vanillaItemModel(materialName) : existingModel.deepCopy());
            root.add("model", dispatch);
        }

        JsonArray entries = dispatch.has("entries") && dispatch.get("entries").isJsonArray()
            ? dispatch.getAsJsonArray("entries")
            : new JsonArray();
        java.util.Set<String> knownEntries = new java.util.HashSet<>();
        for (JsonElement element : entries) {
            if (element.isJsonObject()) {
                knownEntries.add(element.getAsJsonObject().toString());
            }
        }
        for (CustomItemDefinition definition : definitions) {
            JsonObject model = new JsonObject();
            model.addProperty("type", "minecraft:model");
            model.addProperty("model", "ttt:item/" + definition.id());
            JsonObject entry = new JsonObject();
            entry.addProperty("threshold", definition.customModelData());
            entry.add("model", model);
            if (knownEntries.add(entry.toString())) {
                entries.add(entry);
            }
        }
        java.util.List<JsonObject> sortedEntries = new java.util.ArrayList<>();
        for (JsonElement element : entries) {
            if (element.isJsonObject()) {
                sortedEntries.add(element.getAsJsonObject());
            }
        }
        sortedEntries.sort((left, right) -> {
            double leftThreshold = left.has("threshold") ? left.get("threshold").getAsDouble() : 0;
            double rightThreshold = right.has("threshold") ? right.get("threshold").getAsDouble() : 0;
            return Double.compare(leftThreshold, rightThreshold);
        });
        JsonArray orderedEntries = new JsonArray();
        for (JsonObject entry : sortedEntries) {
            orderedEntries.add(entry);
        }
        dispatch.add("entries", orderedEntries);
        root.add("model", dispatch);
        writeJson(path, root);
    }

    private boolean isCustomModelDataDispatch(JsonElement model) {
        if (model == null || !model.isJsonObject()) {
            return false;
        }
        JsonObject object = model.getAsJsonObject();
        return object.has("type")
            && object.has("property")
            && "minecraft:range_dispatch".equals(object.get("type").getAsString())
            && "minecraft:custom_model_data".equals(object.get("property").getAsString());
    }

    private JsonObject vanillaItemModel(String materialName) {
        JsonObject model = new JsonObject();
        model.addProperty("type", "minecraft:model");
        model.addProperty("model", "minecraft:item/" + materialName);
        return model;
    }

    private JsonObject readJsonObject(Path path) throws IOException {
        if (!Files.exists(path)) {
            return new JsonObject();
        }
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
            if (parsed.isJsonObject()) {
                return parsed.getAsJsonObject();
            }
        } catch (RuntimeException ex) {
            throw new IOException("Could not parse existing item definition " + path.getFileName(), ex);
        }
        throw new IOException("Existing item definition is not a JSON object: " + path.getFileName());
    }

    private void writeJson(Path path, JsonObject json) throws IOException {
        Files.writeString(path, gson.toJson(json) + "\n", StandardCharsets.UTF_8);
    }

    private String materialModelName(org.bukkit.Material material) {
        return material.name().toLowerCase(Locale.ROOT);
    }

    private String itemModelParent(org.bukkit.Material material) {
        String name = material.name().toLowerCase(Locale.ROOT);
        if (name.endsWith("_spawn_egg")) {
            return "minecraft:item/template_spawn_egg";
        }
        if (name.endsWith("_sword") || name.endsWith("_axe") || name.endsWith("_pickaxe") || name.endsWith("_shovel") || name.endsWith("_hoe")
            || material == org.bukkit.Material.STICK || material == org.bukkit.Material.BLAZE_ROD) {
            return "minecraft:item/handheld";
        }
        return "minecraft:item/generated";
    }

    private String shortSha1(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            String hex = HashUtil.toHex(hash);
            return hex.substring(0, 14);
        } catch (NoSuchAlgorithmException ex) {
            return Long.toHexString(System.nanoTime());
        }
    }

    private int clampPort(int port) {
        return Math.min(65535, Math.max(1, port));
    }

    private String parsePackFormat(String value) {
        String format = value == null ? "" : value.trim();
        return format.matches("\\d+(?:\\.\\d+)?") ? format : DEFAULT_PACK_FORMAT;
    }

    private String normalizeContextPath(String rawPath) {
        String value = rawPath == null ? "" : rawPath.trim();
        if (value.isBlank()) {
            return "/resourcepack/ttt-pack.zip";
        }
        if (!value.startsWith("/")) {
            value = "/" + value;
        }
        return value;
    }

    private boolean detectServerPropertiesPack() {
        Path serverPropertiesPath = Path.of("server.properties");
        if (!Files.exists(serverPropertiesPath)) {
            return false;
        }

        Properties properties = new Properties();
        try (FileInputStream input = new FileInputStream(serverPropertiesPath.toFile())) {
            properties.load(input);
            String resourcePack = properties.getProperty("resource-pack", "").trim();
            return !resourcePack.isBlank();
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not read server.properties for resource-pack detection: " + ex.getMessage());
            return false;
        }
    }

    private static final class ResourcePackThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "ttt-resourcepack-host");
            thread.setDaemon(true);
            return thread;
        }
    }
}
