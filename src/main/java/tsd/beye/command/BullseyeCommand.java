package tsd.beye.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import tsd.beye.core.PluginBootstrap;
import tsd.beye.service.BrowserService;
import tsd.beye.service.ConversionService;
import tsd.beye.service.MobService;
import tsd.beye.service.MythicMobsService;
import tsd.beye.utils.TextUtil;

public class BullseyeCommand implements CommandExecutor, TabCompleter {
    private final PluginBootstrap bootstrap;

    public BullseyeCommand(PluginBootstrap bootstrap) {
        this.bootstrap = bootstrap;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player && bootstrap.getInventoryManager().openMainMenu(player)) {
                return true;
            }

            if (sender.hasPermission("bullseye.admin")) {
                sendHelp(sender);
            } else {
                sender.sendMessage(TextUtil.colorize("&cYou do not have permission."));
            }
            return true;
        }

        if (!sender.hasPermission("bullseye.admin")) {
            sender.sendMessage(TextUtil.colorize("&cYou do not have permission."));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> handleReload(sender);
            case "give" -> handleGive(sender, args);
            case "inspect" -> handleInspect(sender);
            case "pack" -> handlePack(sender, args);
            case "furniture" -> handleFurniture(sender, args);
            case "menu" -> handleMenu(sender, args);
            case "block" -> handleBlock(sender, args);
            case "spawner" -> handleSpawner(sender, args);
            case "randomspawn" -> handleRandomSpawn(sender, args);
            case "model" -> handleModel(sender, args);
            case "skill" -> handleSkill(sender, args);
            case "browse" -> handleBrowse(sender, args);
            case "editor" -> handleEditor(sender, args);
            case "generate" -> handleGenerate(sender, args);
            case "signature" -> handleSignature(sender, args);
            case "mob" -> handleMob(sender, args);
            case "mythic" -> handleMythic(sender, args);
            case "convert" -> handleConvert(sender);
            case "enable" -> handleEnable(sender, args);
            case "disable" -> handleDisable(sender, args);
            default -> sendHelp(sender);
        }

        return true;
    }

    private void handleReload(CommandSender sender) {
        bootstrap.reload();
        sender.sendMessage(TextUtil.colorize("&aBullseye reloaded."));
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(TextUtil.colorize("&cUsage: /bullseye give <player> <itemId> [amount]"));
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(TextUtil.colorize("&cPlayer not found."));
            return;
        }

        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Math.max(1, Integer.parseInt(args[3]));
            } catch (NumberFormatException ex) {
                sender.sendMessage(TextUtil.colorize("&cInvalid amount."));
                return;
            }
        }

        ItemStack item = bootstrap.getItemService().createItem(args[2], amount);
        if (item == null) {
            sender.sendMessage(TextUtil.colorize("&cUnknown custom item: &f" + args[2]));
            return;
        }

        target.getInventory().addItem(item);
        sender.sendMessage(TextUtil.colorize("&aGave &f" + args[2] + " &ato " + target.getName() + "."));
    }

    private void handleInspect(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(TextUtil.colorize("&cOnly players can run this command."));
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        String itemId = bootstrap.getItemService().getItemId(item);
        if (itemId == null) {
            sender.sendMessage(TextUtil.colorize("&eHeld item is not a Bullseye custom item."));
            return;
        }

        sender.sendMessage(TextUtil.colorize("&aCustom Item ID: &f" + itemId));
    }

    private void handlePack(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(TextUtil.colorize("&cUsage: /bullseye pack <rebuild|send|status> [player]"));
            return;
        }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "rebuild" -> {
                bootstrap.getPackGenerator().generatePack(false);
                if (bootstrap.getResourcePackService().hasPackFile()) {
                    sender.sendMessage(TextUtil.colorize("&aResource pack rebuilt. Hash: &f" + bootstrap.getResourcePackService().getLatestHashHex()));
                } else {
                    sender.sendMessage(TextUtil.colorize("&cFailed to rebuild resource pack. Check console."));
                }
            }
            case "send" -> {
                Player target;
                if (args.length >= 3) {
                    target = Bukkit.getPlayerExact(args[2]);
                } else if (sender instanceof Player player) {
                    target = player;
                } else {
                    sender.sendMessage(TextUtil.colorize("&cUsage: /bullseye pack send <player>"));
                    return;
                }

                if (target == null) {
                    sender.sendMessage(TextUtil.colorize("&cPlayer not found."));
                    return;
                }

                boolean success = bootstrap.getResourcePackService().sendPack(target);
                sender.sendMessage(TextUtil.colorize(success
                    ? "&aResource pack request sent to " + target.getName() + "."
                    : "&cFailed to send resource pack (enable override + configure URL/hosting)."));
            }
            case "status" -> {
                sender.sendMessage(TextUtil.colorize("&6Bullseye Pack Status"));
                sender.sendMessage(TextUtil.colorize("&7Enabled: &f" + bootstrap.getResourcePackService().isEnabled()));
                sender.sendMessage(TextUtil.colorize("&7Auto-Send: &f" + bootstrap.getResourcePackService().isAutoSend()));
                sender.sendMessage(TextUtil.colorize("&7Override: &f" + bootstrap.getResourcePackService().isOverrideServerPack()));
                sender.sendMessage(TextUtil.colorize("&7server.properties pack present: &f" + bootstrap.getResourcePackService().hasExistingServerResourcePack()));
                sender.sendMessage(TextUtil.colorize("&7Hosting: &f"
                    + bootstrap.getResourcePackService().isHostingEnabled()
                    + " (running: " + bootstrap.getResourcePackService().isHostingRunning() + ")"));
                if (bootstrap.getPackServer().packUrl() != null && !bootstrap.getPackServer().packUrl().isBlank()) {
                    sender.sendMessage(TextUtil.colorize("&7Pack URL: &f" + bootstrap.getPackServer().packUrl()));
                }
                sender.sendMessage(TextUtil.colorize("&7Pack built: &f" + bootstrap.getResourcePackService().hasPackFile()));
                sender.sendMessage(TextUtil.colorize("&7Last hash: &f" + bootstrap.getResourcePackService().getLatestHashHex()));
            }
            default -> sender.sendMessage(TextUtil.colorize("&cUsage: /bullseye pack <rebuild|send|status> [player]"));
        }
    }

    private void handleConvert(CommandSender sender) {
        bootstrap.getConversionService().scanInstalledPlugins();
        ConversionService.ConversionReport report = bootstrap.getConversionService().convertDetectedPlugins();

        if (report.entries().isEmpty()) {
            sender.sendMessage(TextUtil.colorize("&eNo supported plugins or external content roots detected."));
            sender.sendMessage(TextUtil.colorize("&7Expected plugins: &fNexo, Oraxen, ChampionAllies/Champions, MythicMobs, ModelEngine&7."));
            return;
        }

        for (ConversionService.ConversionEntry entry : report.entries()) {
            if (entry.success()) {
                sender.sendMessage(TextUtil.colorize("&aConverted &f" + entry.pluginName() + "&a: " + entry.message()));
            } else {
                sender.sendMessage(TextUtil.colorize("&cCould not convert &f" + entry.pluginName() + "&c: " + entry.message()));
            }
        }

        if (report.totalCopiedSources() > 0) {
            bootstrap.getPackGenerator().generatePack(false);
            sender.sendMessage(TextUtil.colorize(bootstrap.getResourcePackService().hasPackFile()
                ? "&aPack rebuilt after conversion."
                : "&eConversion complete, but auto rebuild failed. Run /bullseye pack rebuild."));
        }
    }

    private void handleEnable(CommandSender sender, String[] args) {
        bootstrap.getConversionService().scanInstalledPlugins();
        if (args.length < 2 || !"confirm".equalsIgnoreCase(args[1])) {
            sender.sendMessage(TextUtil.colorize("&6This will override server resource-pack delivery with Bullseye."));
            if (bootstrap.getConversionService().isConversionRecommended()) {
                sender.sendMessage(TextUtil.colorize("&eDetected: &f"
                    + String.join(", ", bootstrap.getConversionService().getDetectedPluginNames())
                    + "&e. Run &f/bullseye convert&e first if you want to import assets."));
            }
            sender.sendMessage(TextUtil.colorize("&eConfirm with: &f/bullseye enable confirm"));
            return;
        }

        bootstrap.getResourcePackService().setEnabled(true);
        bootstrap.getResourcePackService().setAutoSend(true);
        bootstrap.getResourcePackService().setOverrideServerPack(true);
        bootstrap.getPackServer().start();

        if (!bootstrap.getResourcePackService().hasPackFile()) {
            bootstrap.getPackGenerator().generatePack(false);
        }

        sender.sendMessage(TextUtil.colorize("&aBullseye resource-pack override enabled."));
        if (bootstrap.getPackServer().packUrl() != null && !bootstrap.getPackServer().packUrl().isBlank()) {
            sender.sendMessage(TextUtil.colorize("&7Hosted URL: &f" + bootstrap.getPackServer().packUrl()));
        }
    }

    private void handleDisable(CommandSender sender, String[] args) {
        if (args.length < 2 || !"confirm".equalsIgnoreCase(args[1])) {
            sender.sendMessage(TextUtil.colorize("&6This will stop Bullseye from overriding server resource-pack delivery."));
            sender.sendMessage(TextUtil.colorize("&eConfirm with: &f/bullseye disable confirm"));
            return;
        }

        bootstrap.getResourcePackService().setOverrideServerPack(false);
        bootstrap.getResourcePackService().setAutoSend(false);
        sender.sendMessage(TextUtil.colorize("&aBullseye resource-pack override disabled."));
    }

    private void handleFurniture(CommandSender sender, String[] args) {
        if (args.length < 3 || !"spawn".equalsIgnoreCase(args[1])) {
            sender.sendMessage(TextUtil.colorize("&cUsage: /bullseye furniture spawn <id> [player]"));
            return;
        }

        Player target;
        if (args.length >= 4) {
            target = Bukkit.getPlayerExact(args[3]);
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage(TextUtil.colorize("&cUsage: /bullseye furniture spawn <id> <player>"));
            return;
        }

        if (target == null) {
            sender.sendMessage(TextUtil.colorize("&cPlayer not found."));
            return;
        }

        if (bootstrap.getFurnitureService().spawnFurniture(target.getLocation(), args[2]) == null) {
            sender.sendMessage(TextUtil.colorize("&cUnknown furniture id: &f" + args[2]));
            return;
        }

        sender.sendMessage(TextUtil.colorize("&aSpawned furniture &f" + args[2] + "&a."));
    }

    private void handleMenu(CommandSender sender, String[] args) {
        if (args.length < 3 || !"open".equalsIgnoreCase(args[1])) {
            sender.sendMessage(TextUtil.colorize("&cUsage: /bullseye menu open <id> [player]"));
            return;
        }

        Player target;
        if (args.length >= 4) {
            target = Bukkit.getPlayerExact(args[3]);
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage(TextUtil.colorize("&cUsage: /bullseye menu open <id> <player>"));
            return;
        }

        if (target == null) {
            sender.sendMessage(TextUtil.colorize("&cPlayer not found."));
            return;
        }

        if (!bootstrap.getMenuService().openMenu(target, args[2])) {
            sender.sendMessage(TextUtil.colorize("&cUnknown menu id: &f" + args[2]));
            return;
        }

        sender.sendMessage(TextUtil.colorize("&aOpened menu &f" + args[2] + "&a."));
    }

    private void handleBlock(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(TextUtil.colorize("&cOnly players can run block commands."));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(TextUtil.colorize("&cUsage: /bullseye block <set|remove> [id]"));
            return;
        }

        Block target = player.getTargetBlockExact(6);
        if (target == null) {
            sender.sendMessage(TextUtil.colorize("&cNo target block in range."));
            return;
        }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "set" -> {
                if (args.length < 3) {
                    sender.sendMessage(TextUtil.colorize("&cUsage: /bullseye block set <id>"));
                    return;
                }

                if (bootstrap.getBlockService().getDefinition(args[2]) == null) {
                    sender.sendMessage(TextUtil.colorize("&cUnknown custom block: &f" + args[2]));
                    return;
                }

                bootstrap.getBlockService().setCustomBlock(target, args[2]);
                sender.sendMessage(TextUtil.colorize("&aSet target as custom block &f" + args[2] + "&a."));
            }
            case "remove" -> {
                boolean removed = bootstrap.getBlockService().removeCustomBlock(target).isPresent();
                sender.sendMessage(TextUtil.colorize(removed
                    ? "&aRemoved custom block marker from target block."
                    : "&eTarget block is not a custom block."));
            }
            default -> sender.sendMessage(TextUtil.colorize("&cUsage: /bullseye block <set|remove> [id]"));
        }
    }

    private void handleSignature(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(TextUtil.colorize("&cUsage: /bullseye signature <value>"));
            return;
        }

        String value = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        boolean valid = bootstrap.getSignatureService().matches(value);
        if (valid) {
            sender.sendMessage(TextUtil.colorize(
                "&aSignature is valid for DATJR. &7(hex=&f" + bootstrap.getSignatureService().getSignatureHex()
                    + "&7, dec=&f" + bootstrap.getSignatureService().getSignatureDecimal() + "&7)"
            ));
            return;
        }

        sender.sendMessage(TextUtil.colorize(
            "&cSignature invalid. Expected DATJR in hex (&f" + bootstrap.getSignatureService().getSignatureHex()
                + "&c) or decimal (&f" + bootstrap.getSignatureService().getSignatureDecimal() + "&c)."
        ));
    }

    private void handleSpawner(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(TextUtil.colorize("&cUsage: /bullseye spawner <status|place|remove> [id]"));
            return;
        }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "status" -> {
                sender.sendMessage(TextUtil.colorize("&6Bullseye Spawner Status"));
                sender.sendMessage(TextUtil.colorize("&7Definitions: &f" + bootstrap.getSpawnerService().getSpawnerIds().size()));
                sender.sendMessage(TextUtil.colorize("&7Placed spawners: &f" + bootstrap.getSpawnerService().getPlacedSpawnerCount()));
            }
            case "place" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(TextUtil.colorize("&cOnly players can place spawners."));
                    return;
                }
                if (args.length < 3) {
                    sender.sendMessage(TextUtil.colorize("&cUsage: /bullseye spawner place <id>"));
                    return;
                }

                Block target = player.getTargetBlockExact(6);
                if (target == null) {
                    sender.sendMessage(TextUtil.colorize("&cNo target block in range."));
                    return;
                }

                boolean placed = bootstrap.getSpawnerService().placeSpawner(target.getLocation(), args[2]);
                sender.sendMessage(TextUtil.colorize(placed
                    ? "&aPlaced Bullseye spawner &f" + args[2] + "&a."
                    : "&cUnknown Bullseye spawner id."));
            }
            case "remove" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(TextUtil.colorize("&cOnly players can remove spawners."));
                    return;
                }

                Block target = player.getTargetBlockExact(6);
                if (target == null) {
                    sender.sendMessage(TextUtil.colorize("&cNo target block in range."));
                    return;
                }

                boolean removed = bootstrap.getSpawnerService().removeSpawner(target.getLocation());
                sender.sendMessage(TextUtil.colorize(removed
                    ? "&aRemoved Bullseye spawner at target block."
                    : "&eNo Bullseye spawner is placed on that block."));
            }
            default -> sender.sendMessage(TextUtil.colorize("&cUsage: /bullseye spawner <status|place|remove> [id]"));
        }
    }

    private void handleModel(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(TextUtil.colorize("&cUsage: /bullseye model <status|sync|import>"));
            return;
        }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "status" -> {
                sender.sendMessage(TextUtil.colorize("&6Bullseye Model Engine Status"));
                sender.sendMessage(TextUtil.colorize("&7Namespace: &f" + bootstrap.getModelEngineService().getNamespace()));
                sender.sendMessage(TextUtil.colorize("&7Registered models: &f" + bootstrap.getModelEngineService().getModelIds().size()));
                sender.sendMessage(TextUtil.colorize("&7Discovered blueprints: &f" + bootstrap.getModelEngineService().getDiscoveredBlueprints().size()));
            }
            case "sync" -> {
                boolean synced = bootstrap.getModelEngineService().syncReferencePackAssets();
                sender.sendMessage(TextUtil.colorize(synced
                    ? "&aModelEngine reference pack assets synced into Bullseye addons."
                    : "&eNo reference pack roots were found to sync."));
            }
            case "import" -> {
                boolean overwrite = args.length >= 3 && "overwrite".equalsIgnoreCase(args[2]);
                var result = bootstrap.getModelEngineService().importDiscoveredBlueprints(overwrite);
                sender.sendMessage(TextUtil.colorize(result.success()
                    ? "&a" + result.message()
                    : "&e" + result.message()));
                sender.sendMessage(TextUtil.colorize("&7Blueprints: &f" + result.importedBlueprints()));
                sender.sendMessage(TextUtil.colorize("&7Items: &f" + result.generatedItems()));
                sender.sendMessage(TextUtil.colorize("&7Models: &f" + result.generatedModels()));
                sender.sendMessage(TextUtil.colorize("&7Textures: &f" + result.copiedTextures()));
            }
            default -> sender.sendMessage(TextUtil.colorize("&cUsage: /bullseye model <status|sync|import>"));
        }
    }

    private void handleSkill(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(TextUtil.colorize("&cUsage: /bullseye skill <status>"));
            return;
        }

        if ("status".equalsIgnoreCase(args[1])) {
            sender.sendMessage(TextUtil.colorize("&6Bullseye Skill Engine Status"));
            sender.sendMessage(TextUtil.colorize("&7Loaded skills: &f" + bootstrap.getSkillService().getSkillIds().size()));
            sender.sendMessage(TextUtil.colorize("&7Skill IDs: &f" + String.join(", ", bootstrap.getSkillService().getSkillIds())));
            return;
        }

        sender.sendMessage(TextUtil.colorize("&cUsage: /bullseye skill <status>"));
    }

    private void handleRandomSpawn(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(TextUtil.colorize("&cUsage: /bullseye randomspawn <status|pulse>"));
            return;
        }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "status" -> {
                sender.sendMessage(TextUtil.colorize("&6Bullseye Random Spawn Status"));
                sender.sendMessage(TextUtil.colorize("&7Definitions: &f" + bootstrap.getRandomSpawnService().getDefinitionCount()));
                sender.sendMessage(TextUtil.colorize("&7Definition IDs: &f" + String.join(", ", bootstrap.getRandomSpawnService().getDefinitionIds())));
            }
            case "pulse" -> {
                bootstrap.getRandomSpawnService().pulse();
                sender.sendMessage(TextUtil.colorize("&aTriggered a Bullseye random-spawn pulse."));
            }
            default -> sender.sendMessage(TextUtil.colorize("&cUsage: /bullseye randomspawn <status|pulse>"));
        }
    }

    private void handleBrowse(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(TextUtil.colorize("&cUsage: /bullseye browse <items|mobs|spawners|models> [page] [player]"));
            return;
        }

        BrowserService.BrowserType type = BrowserService.BrowserType.from(args[1]);
        if (type == null) {
            sender.sendMessage(TextUtil.colorize("&cUnknown browser type. Use items, mobs, spawners, or models."));
            return;
        }

        int page = 0;
        Player target = sender instanceof Player player ? player : null;

        if (args.length >= 3) {
            Integer parsedPage = parsePage(args[2]);
            if (parsedPage != null) {
                page = parsedPage;
                if (args.length >= 4) {
                    target = Bukkit.getPlayerExact(args[3]);
                }
            } else {
                target = Bukkit.getPlayerExact(args[2]);
            }
        }

        if (target == null) {
            sender.sendMessage(TextUtil.colorize("&cUsage: /bullseye browse <items|mobs|spawners|models> [page] <player>"));
            return;
        }

        if (!bootstrap.getBrowserService().open(target, type, page)) {
            sender.sendMessage(TextUtil.colorize("&cCould not open browser."));
            return;
        }

        sender.sendMessage(TextUtil.colorize("&aOpened the &f" + type.displayName() + "&a browser for &f" + target.getName() + "&a."));
    }

    private void handleEditor(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(TextUtil.colorize("&cOnly players can use Bullseye editors."));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(TextUtil.colorize("&cUsage: /bullseye editor <furniture|spawner|model> <begin|set|show|save|tool> ..."));
            return;
        }

        String type = args[1].toLowerCase(Locale.ROOT);
        String action = args[2].toLowerCase(Locale.ROOT);
        switch (action) {
            case "begin" -> {
                if (args.length < 4) {
                    sender.sendMessage(TextUtil.colorize("&cUsage: /bullseye editor " + type + " begin <id>"));
                    return;
                }
                String message = switch (type) {
                    case "furniture" -> bootstrap.getEditorService().beginFurniture(player.getUniqueId(), args[3]);
                    case "spawner" -> bootstrap.getEditorService().beginSpawner(player.getUniqueId(), args[3]);
                    case "model" -> bootstrap.getEditorService().beginModel(player.getUniqueId(), args[3]);
                    default -> "&cUnknown editor type.";
                };
                sender.sendMessage(TextUtil.colorize(message));
            }
            case "set" -> {
                if (args.length < 5) {
                    sender.sendMessage(TextUtil.colorize("&cUsage: /bullseye editor " + type + " set <field> <value...>"));
                    return;
                }
                String field = args[3];
                String value = String.join(" ", Arrays.copyOfRange(args, 4, args.length));
                sender.sendMessage(TextUtil.colorize(
                    bootstrap.getEditorService().setField(player.getUniqueId(), type, field, value)
                ));
            }
            case "show" -> bootstrap.getEditorService().show(player.getUniqueId(), type)
                .forEach(line -> sender.sendMessage(TextUtil.colorize(line)));
            case "save" -> sender.sendMessage(TextUtil.colorize(
                bootstrap.getEditorService().save(player.getUniqueId(), type)
            ));
            case "tool" -> {
                String id = bootstrap.getEditorService().currentSessionId(player.getUniqueId(), type);
                if (id.isBlank()) {
                    sender.sendMessage(TextUtil.colorize("&cStart an editor session first."));
                    return;
                }
                player.getInventory().addItem(bootstrap.getEditorService().createTool(type, id));
                sender.sendMessage(TextUtil.colorize("&aGiven a &f" + type + "&a tool for &f" + id + "&a."));
            }
            default -> sender.sendMessage(TextUtil.colorize("&cUsage: /bullseye editor <furniture|spawner|model> <begin|set|show|save|tool> ..."));
        }
    }

    private void handleGenerate(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendGenerateUsage(sender);
            return;
        }

        boolean overwrite = "overwrite".equalsIgnoreCase(args[args.length - 1]);
        int limit = overwrite ? args.length - 1 : args.length;
        String type = args[1].toLowerCase(Locale.ROOT);

        switch (type) {
            case "item" -> {
                if (limit < 5) {
                    sender.sendMessage(TextUtil.colorize("&cUsage: /bullseye generate item <id> <base> <cmd|auto> [name...] [overwrite]"));
                    return;
                }
                String name = joinArgs(args, 5, limit);
                sendGenerationResult(sender, bootstrap.getGeneratorService().generateItem(args[2], args[3], args[4], name, overwrite));
            }
            case "texture" -> {
                String style = limit >= 4 ? args[3] : "auto";
                sendGenerationResult(sender, bootstrap.getGeneratorService().generateTexture(args[2], style, overwrite));
            }
            case "model" -> {
                if (limit < 4) {
                    sender.sendMessage(TextUtil.colorize("&cUsage: /bullseye generate model <modelId> <itemId> [blueprint] [overwrite]"));
                    return;
                }
                String blueprint = limit >= 5 ? args[4] : "";
                sendGenerationResult(sender, bootstrap.getGeneratorService().generateModel(args[2], args[3], blueprint, overwrite));
            }
            case "all" -> {
                if (limit < 5) {
                    sender.sendMessage(TextUtil.colorize("&cUsage: /bullseye generate all <id> <base> <cmd|auto> [name...] [overwrite]"));
                    return;
                }
                String name = joinArgs(args, 5, limit);
                sendGenerationResult(sender, bootstrap.getGeneratorService().generateAll(args[2], args[3], args[4], name, overwrite));
            }
            case "block" -> {
                if (limit < 4) {
                    sender.sendMessage(TextUtil.colorize("&cUsage: /bullseye generate block <id> <cmd|auto> [name...] [overwrite]"));
                    return;
                }
                String name = joinArgs(args, 4, limit);
                sendGenerationResult(sender, bootstrap.getGeneratorService().generateBlock(args[2], args[3], name, overwrite));
            }
            case "furniture" -> {
                if (limit < 4) {
                    sender.sendMessage(TextUtil.colorize("&cUsage: /bullseye generate furniture <id> <cmd|auto> [name...] [overwrite]"));
                    return;
                }
                String name = joinArgs(args, 4, limit);
                sendGenerationResult(sender, bootstrap.getGeneratorService().generateFurniture(args[2], args[3], name, overwrite));
            }
            case "mob" -> {
                if (limit < 4) {
                    sender.sendMessage(TextUtil.colorize("&cUsage: /bullseye generate mob <id> <entityType> [name...] [overwrite]"));
                    return;
                }
                String name = joinArgs(args, 4, limit);
                sendGenerationResult(sender, bootstrap.getGeneratorService().generateMob(args[2], args[3], name, overwrite));
            }
            case "recipe" -> {
                String typeHint = limit >= 4 ? args[3] : "shaped";
                sendGenerationResult(sender, bootstrap.getGeneratorService().generateRecipe(args[2], typeHint, overwrite));
            }
            case "menu" -> {
                if (limit < 4) {
                    sender.sendMessage(TextUtil.colorize("&cUsage: /bullseye generate menu <id> <size> [title...] [overwrite]"));
                    return;
                }
                String title = joinArgs(args, 4, limit);
                sendGenerationResult(sender, bootstrap.getGeneratorService().generateMenu(args[2], args[3], title, overwrite));
            }
            case "button" -> {
                if (limit < 5) {
                    sender.sendMessage(TextUtil.colorize("&cUsage: /bullseye generate button <menuId> <slot> <item|material> [give|open_menu|open_browser|close] [target] [overwrite]"));
                    return;
                }
                String actionType = limit >= 6 ? args[5] : "give";
                String target = limit >= 7 ? joinArgs(args, 6, limit) : "";
                sendGenerationResult(sender, bootstrap.getGeneratorService().generateButton(args[2], args[3], args[4], actionType, target, overwrite));
            }
            case "import" -> {
                if (limit < 4) {
                    sender.sendMessage(TextUtil.colorize("&cUsage: /bullseye generate import <folder> <names|textures|bbmodels> [hint] [overwrite]"));
                    return;
                }
                String hint = limit >= 5 ? joinArgs(args, 4, limit) : "";
                sendGenerationResult(sender, bootstrap.getGeneratorService().importFolder(args[2], args[3], hint, overwrite));
            }
            case "browser" -> {
                if (limit < 3) {
                    sender.sendMessage(TextUtil.colorize("&cUsage: /bullseye generate browser <items|mobs> [prefix] [overwrite]"));
                    return;
                }
                String prefix = limit >= 4 ? joinArgs(args, 3, limit) : "";
                sendGenerationResult(sender, bootstrap.getGeneratorService().generateBrowser(args[2], prefix, overwrite));
            }
            default -> sendGenerateUsage(sender);
        }
    }

    private void sendGenerateUsage(CommandSender sender) {
        sender.sendMessage(TextUtil.colorize("&6Bullseye Generator"));
        sender.sendMessage(TextUtil.colorize("&e/bullseye generate item <id> <base> <cmd|auto> [name...] [overwrite]"));
        sender.sendMessage(TextUtil.colorize("&e/bullseye generate texture <id> [style] [overwrite]"));
        sender.sendMessage(TextUtil.colorize("&e/bullseye generate model <modelId> <itemId> [blueprint] [overwrite]"));
        sender.sendMessage(TextUtil.colorize("&e/bullseye generate all <id> <base> <cmd|auto> [name...] [overwrite]"));
        sender.sendMessage(TextUtil.colorize("&e/bullseye generate block <id> <cmd|auto> [name...] [overwrite]"));
        sender.sendMessage(TextUtil.colorize("&e/bullseye generate furniture <id> <cmd|auto> [name...] [overwrite]"));
        sender.sendMessage(TextUtil.colorize("&e/bullseye generate mob <id> <entityType> [name...] [overwrite]"));
        sender.sendMessage(TextUtil.colorize("&e/bullseye generate recipe <itemId> [shaped|shapeless] [overwrite]"));
        sender.sendMessage(TextUtil.colorize("&e/bullseye generate menu <id> <size> [title...] [overwrite]"));
        sender.sendMessage(TextUtil.colorize("&e/bullseye generate button <menuId> <slot> <item|material> [give|open_menu|open_browser|close] [target] [overwrite]"));
        sender.sendMessage(TextUtil.colorize("&e/bullseye generate import <folder> <names|textures|bbmodels> [hint] [overwrite]"));
        sender.sendMessage(TextUtil.colorize("&e/bullseye generate browser <items|mobs> [prefix] [overwrite]"));
    }

    private void sendGenerationResult(CommandSender sender, tsd.beye.service.GeneratorService.GenerationResult result) {
        sender.sendMessage(TextUtil.colorize((result.success() ? "&a" : "&c") + result.message()));
        for (String detail : result.details()) {
            sender.sendMessage(TextUtil.colorize("&7- &f" + detail));
        }
    }

    private void handleMythic(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(TextUtil.colorize("&cUsage: /bullseye mythic <status|inject|spawn>"));
            return;
        }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "status" -> {
                sender.sendMessage(TextUtil.colorize("&6Bullseye Mythic Bridge Status"));
                sender.sendMessage(TextUtil.colorize("&7Enabled: &f" + bootstrap.getMythicMobsService().isEnabled()));
                sender.sendMessage(TextUtil.colorize("&7MythicMobs available: &f" + bootstrap.getMythicMobsService().isAvailable()));
                sender.sendMessage(TextUtil.colorize("&7Native Bullseye mobs: &f" + bootstrap.getMobService().getMobIds().size()));
                sender.sendMessage(TextUtil.colorize("&7Exported Mythic definitions: &f" + bootstrap.getMythicMobsService().getMobIds().size()));
            }
            case "inject" -> {
                MythicMobsService.InjectionResult result = bootstrap.getMythicMobsService().inject(true);
                sender.sendMessage(TextUtil.colorize(result.success()
                    ? "&a" + result.message()
                    : "&c" + result.message()));
                if (result.mobsFile() != null) {
                    sender.sendMessage(TextUtil.colorize("&7Mobs file: &f" + result.mobsFile()));
                }
                if (result.skillsFile() != null) {
                    sender.sendMessage(TextUtil.colorize("&7Skills file: &f" + result.skillsFile()));
                }
            }
            case "spawn" -> {
                if (args.length < 3) {
                    sender.sendMessage(TextUtil.colorize("&cUsage: /bullseye mythic spawn <id> [player]"));
                    return;
                }

                Player target;
                if (args.length >= 4) {
                    target = Bukkit.getPlayerExact(args[3]);
                } else if (sender instanceof Player player) {
                    target = player;
                } else {
                    sender.sendMessage(TextUtil.colorize("&cUsage: /bullseye mythic spawn <id> <player>"));
                    return;
                }

                if (target == null) {
                    sender.sendMessage(TextUtil.colorize("&cPlayer not found."));
                    return;
                }

                MythicMobsService.EggSpawnResult result = bootstrap.getMythicMobsService().spawnMob(args[2], target.getLocation());
                sender.sendMessage(TextUtil.colorize(result.success()
                    ? "&aSpawned Bullseye Mythic mob &f" + args[2] + "&a."
                    : "&c" + result.message()));
            }
            default -> sender.sendMessage(TextUtil.colorize("&cUsage: /bullseye mythic <status|inject|spawn>"));
        }
    }

    private void handleMob(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(TextUtil.colorize("&cUsage: /bullseye mob <status|spawn>"));
            return;
        }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "status" -> {
                sender.sendMessage(TextUtil.colorize("&6Bullseye Mob Engine Status"));
                sender.sendMessage(TextUtil.colorize("&7Registered native mobs: &f" + bootstrap.getMobService().getMobIds().size()));
                sender.sendMessage(TextUtil.colorize("&7Mythic bridge enabled: &f" + bootstrap.getMythicMobsService().isEnabled()));
            }
            case "spawn" -> {
                if (args.length < 3) {
                    sender.sendMessage(TextUtil.colorize("&cUsage: /bullseye mob spawn <id> [player]"));
                    return;
                }

                Player target;
                if (args.length >= 4) {
                    target = Bukkit.getPlayerExact(args[3]);
                } else if (sender instanceof Player player) {
                    target = player;
                } else {
                    sender.sendMessage(TextUtil.colorize("&cUsage: /bullseye mob spawn <id> <player>"));
                    return;
                }

                if (target == null) {
                    sender.sendMessage(TextUtil.colorize("&cPlayer not found."));
                    return;
                }

                MobService.SpawnResult result = bootstrap.getMobService().spawnMob(args[2], target.getLocation(), target);
                sender.sendMessage(TextUtil.colorize(result.success()
                    ? "&aSpawned native Bullseye mob &f" + args[2] + "&a."
                    : "&c" + result.message()));
            }
            default -> sender.sendMessage(TextUtil.colorize("&cUsage: /bullseye mob <status|spawn>"));
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(TextUtil.colorize("&6Bullseye Admin Commands"));
        sender.sendMessage(TextUtil.colorize("&e/bullseye reload"));
        sender.sendMessage(TextUtil.colorize("&e/bullseye convert"));
        sender.sendMessage(TextUtil.colorize("&e/bullseye enable confirm"));
        sender.sendMessage(TextUtil.colorize("&e/bullseye disable confirm"));
        sender.sendMessage(TextUtil.colorize("&e/bullseye give <player> <itemId> [amount]"));
        sender.sendMessage(TextUtil.colorize("&e/bullseye inspect"));
        sender.sendMessage(TextUtil.colorize("&e/bullseye pack <rebuild|send|status> [player]"));
        sender.sendMessage(TextUtil.colorize("&e/bullseye furniture spawn <id> [player]"));
        sender.sendMessage(TextUtil.colorize("&e/bullseye menu open <id> [player]"));
        sender.sendMessage(TextUtil.colorize("&e/bullseye block <set|remove> [id]"));
        sender.sendMessage(TextUtil.colorize("&e/bullseye spawner <status|place|remove> [id]"));
        sender.sendMessage(TextUtil.colorize("&e/bullseye randomspawn <status|pulse>"));
        sender.sendMessage(TextUtil.colorize("&e/bullseye model <status|sync|import>"));
        sender.sendMessage(TextUtil.colorize("&e/bullseye skill <status>"));
        sender.sendMessage(TextUtil.colorize("&e/bullseye browse <items|mobs|spawners|models> [page] [player]"));
        sender.sendMessage(TextUtil.colorize("&e/bullseye editor <furniture|spawner|model> <begin|set|show|save|tool>"));
        sender.sendMessage(TextUtil.colorize("&e/bullseye generate <item|texture|model|all|block|furniture|mob|recipe|menu|button|import> ..."));
        sender.sendMessage(TextUtil.colorize("&e/bullseye mob <status|spawn>"));
        sender.sendMessage(TextUtil.colorize("&e/bullseye signature <value>"));
        sender.sendMessage(TextUtil.colorize("&e/bullseye mythic <status|inject|spawn>"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("bullseye.admin")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return complete(args[0], List.of("reload", "convert", "enable", "disable", "give", "inspect", "pack", "furniture", "menu", "block", "spawner", "randomspawn", "model", "skill", "browse", "editor", "generate", "mob", "signature", "mythic"));
        }

        if (args.length == 2 && ("enable".equalsIgnoreCase(args[0]) || "disable".equalsIgnoreCase(args[0]))) {
            return complete(args[1], List.of("confirm"));
        }

        if (args.length == 2 && "pack".equalsIgnoreCase(args[0])) {
            return complete(args[1], List.of("rebuild", "send", "status"));
        }

        if (args.length == 2 && "furniture".equalsIgnoreCase(args[0])) {
            return complete(args[1], List.of("spawn"));
        }

        if (args.length == 2 && "menu".equalsIgnoreCase(args[0])) {
            return complete(args[1], List.of("open"));
        }

        if (args.length == 2 && "block".equalsIgnoreCase(args[0])) {
            return complete(args[1], List.of("set", "remove"));
        }

        if (args.length == 2 && "spawner".equalsIgnoreCase(args[0])) {
            return complete(args[1], List.of("status", "place", "remove"));
        }

        if (args.length == 2 && "randomspawn".equalsIgnoreCase(args[0])) {
            return complete(args[1], List.of("status", "pulse"));
        }

        if (args.length == 2 && "model".equalsIgnoreCase(args[0])) {
            return complete(args[1], List.of("status", "sync", "import"));
        }

        if (args.length == 2 && "skill".equalsIgnoreCase(args[0])) {
            return complete(args[1], List.of("status"));
        }

        if (args.length == 2 && "editor".equalsIgnoreCase(args[0])) {
            return complete(args[1], List.of("furniture", "spawner", "model"));
        }

        if (args.length == 2 && "generate".equalsIgnoreCase(args[0])) {
            return complete(args[1], List.of("item", "texture", "model", "all", "block", "furniture", "mob", "recipe", "menu", "button", "import", "browser"));
        }

        if (args.length == 3 && "editor".equalsIgnoreCase(args[0])) {
            return complete(args[2], List.of("begin", "set", "show", "save", "tool"));
        }

        if (args.length == 4 && "generate".equalsIgnoreCase(args[0]) && ("item".equalsIgnoreCase(args[1]) || "all".equalsIgnoreCase(args[1]))) {
            return complete(args[3], Arrays.stream(Material.values()).map(Material::name).toList());
        }

        if (args.length == 4 && "generate".equalsIgnoreCase(args[0]) && "recipe".equalsIgnoreCase(args[1])) {
            return complete(args[3], List.of("shaped", "shapeless"));
        }

        if (args.length == 4 && "generate".equalsIgnoreCase(args[0]) && "menu".equalsIgnoreCase(args[1])) {
            return complete(args[3], List.of("27", "54"));
        }

        if (args.length == 4 && "generate".equalsIgnoreCase(args[0]) && "mob".equalsIgnoreCase(args[1])) {
            return complete(args[3], Arrays.stream(org.bukkit.entity.EntityType.values()).map(type -> type.name().toLowerCase(Locale.ROOT)).toList());
        }

        if (args.length == 4 && "generate".equalsIgnoreCase(args[0]) && "import".equalsIgnoreCase(args[1])) {
            return complete(args[3], List.of("names", "textures", "bbmodels"));
        }

        if (args.length == 4 && "generate".equalsIgnoreCase(args[0]) && "browser".equalsIgnoreCase(args[1])) {
            return complete(args[3], List.of("items", "mobs"));
        }

        if (args.length == 4 && "generate".equalsIgnoreCase(args[0]) && "texture".equalsIgnoreCase(args[1])) {
            return complete(args[3], List.of("auto", "gem", "blade", "egg", "orb", "scroll", "block", "overwrite"));
        }

        if (args.length == 4 && "generate".equalsIgnoreCase(args[0]) && "model".equalsIgnoreCase(args[1])) {
            return complete(args[3], new ArrayList<>(bootstrap.getItemService().getItemIds()));
        }

        if (args.length == 5 && "generate".equalsIgnoreCase(args[0]) && "button".equalsIgnoreCase(args[1])) {
            List<String> options = new ArrayList<>(bootstrap.getItemService().getItemIds());
            options.addAll(Arrays.stream(Material.values()).map(Material::name).toList());
            return complete(args[4], options);
        }

        if (args.length == 5 && "generate".equalsIgnoreCase(args[0]) && ("item".equalsIgnoreCase(args[1]) || "all".equalsIgnoreCase(args[1]))) {
            return complete(args[4], List.of("auto"));
        }

        if (args.length == 4 && "generate".equalsIgnoreCase(args[0]) && ("block".equalsIgnoreCase(args[1]) || "furniture".equalsIgnoreCase(args[1]))) {
            return complete(args[3], List.of("auto"));
        }

        if (args.length == 6 && "generate".equalsIgnoreCase(args[0]) && "button".equalsIgnoreCase(args[1])) {
            return complete(args[5], List.of("give", "open_menu", "open_browser", "close"));
        }

        if (args.length == 7 && "generate".equalsIgnoreCase(args[0]) && "button".equalsIgnoreCase(args[1])) {
            String actionType = args[5].toLowerCase(Locale.ROOT);
            if ("open_browser".equals(actionType)) {
                return complete(args[6], List.of("items", "mobs", "spawners", "models"));
            }
            if ("open_menu".equals(actionType)) {
                return complete(args[6], new ArrayList<>(bootstrap.getMenuService().getMenuIds()));
            }
            if ("give".equals(actionType)) {
                return complete(args[6], new ArrayList<>(bootstrap.getItemService().getItemIds()));
            }
        }

        if (args.length == 5 && "generate".equalsIgnoreCase(args[0]) && "model".equalsIgnoreCase(args[1])) {
            return complete(args[4], new ArrayList<>(bootstrap.getModelEngineService().getDiscoveredBlueprints()));
        }

        if (args.length == 2 && "browse".equalsIgnoreCase(args[0])) {
            return complete(args[1], Arrays.stream(BrowserService.BrowserType.values()).map(type -> type.name().toLowerCase(Locale.ROOT)).toList());
        }

        if (args.length == 2 && "mob".equalsIgnoreCase(args[0])) {
            return complete(args[1], List.of("status", "spawn"));
        }

        if (args.length == 2 && "mythic".equalsIgnoreCase(args[0])) {
            return complete(args[1], List.of("status", "inject", "spawn"));
        }

        if (args.length == 3 && "give".equalsIgnoreCase(args[0])) {
            return complete(args[2], new ArrayList<>(bootstrap.getItemService().getItemIds()));
        }

        if (args.length == 2 && "give".equalsIgnoreCase(args[0])) {
            return complete(args[1], Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()));
        }

        if (args.length == 3 && "furniture".equalsIgnoreCase(args[0]) && "spawn".equalsIgnoreCase(args[1])) {
            return complete(args[2], new ArrayList<>(bootstrap.getFurnitureService().getFurnitureIds()));
        }

        if (args.length == 3 && "menu".equalsIgnoreCase(args[0]) && "open".equalsIgnoreCase(args[1])) {
            return complete(args[2], new ArrayList<>(bootstrap.getMenuService().getMenuIds()));
        }

        if (args.length == 3 && "block".equalsIgnoreCase(args[0]) && "set".equalsIgnoreCase(args[1])) {
            return complete(args[2], new ArrayList<>(bootstrap.getBlockService().getBlockIds()));
        }

        if (args.length == 3 && "spawner".equalsIgnoreCase(args[0]) && "place".equalsIgnoreCase(args[1])) {
            return complete(args[2], new ArrayList<>(bootstrap.getSpawnerService().getSpawnerIds()));
        }

        if (args.length == 3 && "model".equalsIgnoreCase(args[0]) && "import".equalsIgnoreCase(args[1])) {
            return complete(args[2], List.of("overwrite"));
        }

        if ("generate".equalsIgnoreCase(args[0])) {
            return complete(args[args.length - 1], List.of("overwrite"));
        }

        if (args.length == 4 && "editor".equalsIgnoreCase(args[0]) && "begin".equalsIgnoreCase(args[2])) {
            return switch (args[1].toLowerCase(Locale.ROOT)) {
                case "furniture" -> complete(args[3], new ArrayList<>(bootstrap.getFurnitureService().getFurnitureIds()));
                case "spawner" -> complete(args[3], new ArrayList<>(bootstrap.getSpawnerService().getSpawnerIds()));
                case "model" -> complete(args[3], new ArrayList<>(bootstrap.getModelEngineService().getModelIds()));
                default -> Collections.emptyList();
            };
        }

        if (args.length == 4 && "editor".equalsIgnoreCase(args[0]) && "set".equalsIgnoreCase(args[2])) {
            return switch (args[1].toLowerCase(Locale.ROOT)) {
                case "furniture" -> complete(args[3], List.of("item", "display", "model", "seat", "offset", "scale", "rotation"));
                case "spawner" -> complete(args[3], List.of("mob", "interval", "spawn-count", "max-nearby", "activation-range", "check-range", "spawn-radius", "y-offset"));
                case "model" -> complete(args[3], List.of("item", "blueprint", "animation", "offset", "scale", "rotation", "billboard", "transform", "view-range", "shadow-radius", "shadow-strength"));
                default -> Collections.emptyList();
            };
        }

        if (args.length == 5 && "editor".equalsIgnoreCase(args[0]) && "set".equalsIgnoreCase(args[2])) {
            if ("furniture".equalsIgnoreCase(args[1])) {
                if ("item".equalsIgnoreCase(args[3]) || "display".equalsIgnoreCase(args[3])) {
                    return complete(args[4], new ArrayList<>(bootstrap.getItemService().getItemIds()));
                }
                if ("model".equalsIgnoreCase(args[3])) {
                    return complete(args[4], new ArrayList<>(bootstrap.getModelEngineService().getModelIds()));
                }
                if ("seat".equalsIgnoreCase(args[3])) {
                    return complete(args[4], List.of("true", "false"));
                }
            }
            if ("spawner".equalsIgnoreCase(args[1]) && "mob".equalsIgnoreCase(args[3])) {
                return complete(args[4], new ArrayList<>(bootstrap.getMobService().getMobIds()));
            }
            if ("model".equalsIgnoreCase(args[1])) {
                if ("item".equalsIgnoreCase(args[3])) {
                    return complete(args[4], new ArrayList<>(bootstrap.getItemService().getItemIds()));
                }
                if ("blueprint".equalsIgnoreCase(args[3])) {
                    return complete(args[4], new ArrayList<>(bootstrap.getModelEngineService().getDiscoveredBlueprints()));
                }
                if ("billboard".equalsIgnoreCase(args[3])) {
                    return complete(args[4], List.of("fixed", "center", "vertical", "horizontal"));
                }
                if ("transform".equalsIgnoreCase(args[3])) {
                    return complete(args[4], List.of("fixed", "ground", "gui", "head", "thirdperson_lefthand", "thirdperson_righthand", "firstperson_lefthand", "firstperson_righthand"));
                }
            }
        }

        if (args.length == 3 && "browse".equalsIgnoreCase(args[0])) {
            List<String> options = new ArrayList<>(List.of("1", "2", "3"));
            options.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
            return complete(args[2], options);
        }

        if (args.length == 4 && "browse".equalsIgnoreCase(args[0])) {
            return complete(args[3], Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()));
        }

        if (args.length == 3 && "mob".equalsIgnoreCase(args[0]) && "spawn".equalsIgnoreCase(args[1])) {
            return complete(args[2], new ArrayList<>(bootstrap.getMobService().getMobIds()));
        }

        if (args.length == 3 && "mythic".equalsIgnoreCase(args[0]) && "spawn".equalsIgnoreCase(args[1])) {
            return complete(args[2], new ArrayList<>(bootstrap.getMythicMobsService().getMobIds()));
        }

        if ((args.length == 3 && "pack".equalsIgnoreCase(args[0]) && "send".equalsIgnoreCase(args[1]))
            || (args.length == 4 && "furniture".equalsIgnoreCase(args[0]) && "spawn".equalsIgnoreCase(args[1]))
            || (args.length == 4 && "menu".equalsIgnoreCase(args[0]) && "open".equalsIgnoreCase(args[1]))
            || (args.length == 4 && "mob".equalsIgnoreCase(args[0]) && "spawn".equalsIgnoreCase(args[1]))
            || (args.length == 4 && "mythic".equalsIgnoreCase(args[0]) && "spawn".equalsIgnoreCase(args[1]))) {
            return complete(args[args.length - 1], Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()));
        }

        return Collections.emptyList();
    }

    private List<String> complete(String input, List<String> options) {
        String lower = input.toLowerCase(Locale.ROOT);
        return options.stream()
            .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lower))
            .sorted()
            .toList();
    }

    private Integer parsePage(String input) {
        try {
            return Math.max(0, Integer.parseInt(input) - 1);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String joinArgs(String[] args, int from, int toExclusive) {
        if (from >= toExclusive) {
            return "";
        }
        return String.join(" ", Arrays.copyOfRange(args, from, toExclusive));
    }
}
