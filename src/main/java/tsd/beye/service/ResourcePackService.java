package tsd.beye.service;

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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.stream.Stream;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import tsd.beye.Bullseye;
import tsd.beye.model.CustomItemDefinition;
import tsd.beye.utils.FileUtil;
import tsd.beye.utils.HashUtil;
import tsd.beye.utils.TextUtil;

public class ResourcePackService {
    private final Bullseye plugin;
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

    public ResourcePackService(Bullseye plugin) {
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
        outputFile = section.getString("output-file", "generated/bullseye-pack.zip");

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
            hostingPath = "/resourcepack/bullseye-pack.zip";
            return;
        }

        hostingEnabled = hostingSection.getBoolean("enabled", false);
        hostingAutoRebuildIfMissing = hostingSection.getBoolean("auto-rebuild-if-missing", true);
        hostingProtocol = hostingSection.getString("protocol", "http");
        hostingBindAddress = hostingSection.getString("bind-address", "0.0.0.0");
        hostingPublicHost = hostingSection.getString("public-host", "");
        hostingPort = Math.max(1, hostingSection.getInt("port", 25590));
        hostingPath = normalizeContextPath(hostingSection.getString("path", "/resourcepack/bullseye-pack.zip"));
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

            plugin.getLogger().info("Bullseye resource pack hosting enabled at " + buildHostedUrl());

            if (hostingAutoRebuildIfMissing && !hasPackFile()) {
                plugin.getLogger().info("No generated pack found; rebuilding because hosting auto-rebuild is enabled.");
                rebuildPack();
            }
        } catch (IOException ex) {
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

    public boolean rebuildPack() {
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

            if (obfuscate) {
                obfuscateItemAssets(temp);
            }

            if (!Files.exists(temp.resolve("pack.mcmeta"))) {
                Files.writeString(
                    temp.resolve("pack.mcmeta"),
                    "{\"pack\":{\"pack_format\":34,\"description\":\"Bullseye generated resource pack\"}}",
                    StandardCharsets.UTF_8
                );
            }

            FileUtil.zipDirectory(temp, output);

            File outputFileObject = output.toFile();
            latestHashBytes = HashUtil.sha1Bytes(outputFileObject);
            latestHashHex = HashUtil.toHex(latestHashBytes);
            plugin.getLogger().info("Resource pack rebuilt: " + output + " (sha1=" + latestHashHex + ")");
            return true;
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to rebuild resource pack: " + ex.getMessage());
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

    public boolean sendPack(Player player, String joinHostnameOverride) {
        if (!enabled) {
            return false;
        }

        if (!overrideServerPack && serverPropertiesHasPack) {
            if (!overrideWarningLogged) {
                plugin.getLogger().warning("Bullseye pack send skipped: server.properties already defines a resource-pack.");
                plugin.getLogger().warning("Run /bullseye enable confirm to allow Bullseye to override the current server pack.");
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
                byte[] notFound = "Bullseye resource pack not built yet".getBytes(StandardCharsets.UTF_8);
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
        ordered.addAll(baseEntries);
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

        boolean mergedChildPack = false;
        try (Stream<Path> children = Files.list(source)) {
            List<Path> childDirs = children.filter(Files::isDirectory).toList();
            for (Path child : childDirs) {
                if (isPackRoot(child)) {
                    copyDirectoryContents(child, temp);
                    mergedChildPack = true;
                }
            }
        }

        if (!mergedChildPack) {
            copyDirectoryContents(source, temp);
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
        Path modelDir = root.resolve("assets/bullseye/models/item");
        Path textureDir = root.resolve("assets/bullseye/textures/item");

        if (!Files.exists(modelDir) && !Files.exists(textureDir)) {
            return;
        }

        Map<String, String> textureReplacements = renameAssets(textureDir, "bullseye:item/");
        Map<String, String> modelReplacements = renameAssets(modelDir, "bullseye:item/");

        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".json")).forEach(path -> {
                try {
                    String text = Files.readString(path, StandardCharsets.UTF_8);
                    for (Map.Entry<String, String> entry : textureReplacements.entrySet()) {
                        text = text.replace(entry.getKey(), entry.getValue());
                    }
                    for (Map.Entry<String, String> entry : modelReplacements.entrySet()) {
                        text = text.replace(entry.getKey(), entry.getValue());
                    }
                    Files.writeString(path, text, StandardCharsets.UTF_8);
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            });
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to apply resource pack obfuscation replacements: " + ex.getMessage());
        }
    }

    private Map<String, String> renameAssets(Path directory, String prefix) {
        Map<String, String> replacements = new HashMap<>();
        if (!Files.exists(directory)) {
            return replacements;
        }

        try (Stream<Path> stream = Files.list(directory)) {
            List<Path> files = stream.filter(Files::isRegularFile).toList();
            for (Path file : files) {
                String name = file.getFileName().toString();
                int dot = name.lastIndexOf('.');
                if (dot <= 0) {
                    continue;
                }

                String baseName = name.substring(0, dot);
                String extension = name.substring(dot);
                String obfuscated = shortSha1(baseName + System.nanoTime()) + extension;
                Path target = directory.resolve(obfuscated);
                Files.move(file, target);

                String oldKey = prefix + baseName;
                String newKey = prefix + obfuscated.substring(0, obfuscated.length() - extension.length());
                replacements.put(oldKey, newKey);
            }
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to obfuscate assets in " + directory + ": " + ex.getMessage());
        }

        return replacements;
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

        Path namespaceModelDir = root.resolve("assets/bullseye/models/item");
        Path namespaceTextureDir = root.resolve("assets/bullseye/textures/item");
        Path minecraftModelDir = root.resolve("assets/minecraft/models/item");

        try {
            Files.createDirectories(namespaceModelDir);
            Files.createDirectories(namespaceTextureDir);
            Files.createDirectories(minecraftModelDir);

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
                                "layer0": "bullseye:item/%s"
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
                String parent = entry.getKey().endsWith("_spawn_egg")
                    ? "minecraft:item/template_spawn_egg"
                    : "minecraft:item/generated";

                StringBuilder builder = new StringBuilder();
                builder.append("{\n");
                builder.append("  \"parent\": \"").append(parent).append("\",\n");
                builder.append("  \"textures\": {\n");
                builder.append("    \"layer0\": \"minecraft:item/").append(entry.getKey()).append("\"\n");
                builder.append("  },\n");
                builder.append("  \"overrides\": [\n");
                for (int i = 0; i < definitions.size(); i++) {
                    CustomItemDefinition definition = definitions.get(i);
                    builder.append("    {\n");
                    builder.append("      \"predicate\": {\n");
                    builder.append("        \"custom_model_data\": ").append(definition.customModelData()).append("\n");
                    builder.append("      },\n");
                    builder.append("      \"model\": \"bullseye:item/").append(definition.id()).append("\"\n");
                    builder.append("    }");
                    if (i + 1 < definitions.size()) {
                        builder.append(',');
                    }
                    builder.append('\n');
                }
                builder.append("  ]\n");
                builder.append("}\n");

                Files.writeString(
                    minecraftModelDir.resolve(entry.getKey() + ".json"),
                    builder.toString(),
                    StandardCharsets.UTF_8
                );
            }
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to generate Bullseye item model overrides: " + ex.getMessage());
        }
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
            return hex.substring(0, 14).toLowerCase(Locale.ROOT);
        } catch (NoSuchAlgorithmException ex) {
            return Long.toHexString(System.nanoTime());
        }
    }

    private String normalizeContextPath(String rawPath) {
        String value = rawPath == null ? "" : rawPath.trim();
        if (value.isBlank()) {
            return "/resourcepack/bullseye-pack.zip";
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
            Thread thread = new Thread(runnable, "bullseye-resourcepack-host");
            thread.setDaemon(true);
            return thread;
        }
    }
}
