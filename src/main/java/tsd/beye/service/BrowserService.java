package tsd.beye.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import tsd.beye.Bullseye;
import tsd.beye.model.BullseyeModelDefinition;
import tsd.beye.model.CustomMobDefinition;
import tsd.beye.model.SpawnerDefinition;
import tsd.beye.utils.TextUtil;

public class BrowserService {
    private static final Layout ITEMS_LAYOUT = new Layout(
        "\uE901",
        54,
        List.of(10, 11, 12, 13, 14, 15, 19, 20, 21, 22, 23, 24, 28, 29, 30, 31, 32, 33),
        38,
        40,
        42,
        49
    );
    private static final Layout MOBS_LAYOUT = new Layout(
        "\uE902",
        54,
        List.of(10, 11, 12, 13, 14, 15, 19, 20, 21, 22, 23, 24, 28, 29, 30, 31, 32, 33, 37, 38, 39, 40, 41, 42),
        46,
        48,
        50,
        49
    );
    private static final Layout GENERIC_LAYOUT = new Layout(
        "&8Bullseye Browser",
        54,
        List.of(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43),
        45,
        53,
        49,
        47
    );

    private final Bullseye plugin;
    private final ItemService itemService;
    private final MobService mobService;
    private final SpawnerService spawnerService;
    private final ModelEngineService modelEngineService;
    private final Map<UUID, String> pendingSpawnerPlacement = new HashMap<>();
    private final Map<UUID, BrowserType> pendingSearchType = new HashMap<>();
    private final Map<UUID, BrowserState> stateByPlayer = new HashMap<>();

    public BrowserService(
        Bullseye plugin,
        ItemService itemService,
        MobService mobService,
        SpawnerService spawnerService,
        ModelEngineService modelEngineService
    ) {
        this.plugin = plugin;
        this.itemService = itemService;
        this.mobService = mobService;
        this.spawnerService = spawnerService;
        this.modelEngineService = modelEngineService;
    }

    public boolean open(Player player, String rawType, int page) {
        return open(player, rawType, page, null);
    }

    public boolean open(Player player, String rawType, int page, String query) {
        BrowserType type = BrowserType.from(rawType);
        if (type == null) {
            return false;
        }
        return open(player, type, page, query);
    }

    public boolean open(Player player, BrowserType type, int page) {
        return open(player, type, page, null);
    }

    public boolean open(Player player, BrowserType type, int page, String query) {
        if (player == null || type == null) {
            return false;
        }

        BrowserState currentState = stateByPlayer.get(player.getUniqueId());
        String resolvedQuery = query != null ? normalize(query) : currentState != null && currentState.type() == type ? currentState.query() : "";
        Layout layout = type.layout();
        List<Entry> entries = entries(type, resolvedQuery);
        int maxPage = Math.max(0, (entries.size() - 1) / layout.contentSlots().size());
        int resolvedPage = Math.max(0, Math.min(page, maxPage));

        Inventory inventory = Bukkit.createInventory(new BrowserHolder(type, resolvedPage, resolvedQuery), layout.size(), TextUtil.colorize(layout.title()));
        fillControls(inventory, type, resolvedPage, maxPage, resolvedQuery);

        int offset = resolvedPage * layout.contentSlots().size();
        for (int i = 0; i < layout.contentSlots().size(); i++) {
            int entryIndex = offset + i;
            if (entryIndex >= entries.size()) {
                break;
            }
            inventory.setItem(layout.contentSlots().get(i), entries.get(entryIndex).icon());
        }

        stateByPlayer.put(player.getUniqueId(), new BrowserState(type, resolvedPage, resolvedQuery));
        player.openInventory(inventory);
        return true;
    }

    public boolean handleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return false;
        }

        Inventory topInventory = event.getView().getTopInventory();
        if (!(topInventory.getHolder() instanceof BrowserHolder holder)) {
            return false;
        }

        event.setCancelled(true);
        if (event.getRawSlot() < 0 || event.getRawSlot() >= topInventory.getSize()) {
            return true;
        }

        Layout layout = holder.type().layout();
        if (event.getRawSlot() == layout.previousSlot()) {
            if (holder.page() > 0) {
                open(player, holder.type(), holder.page() - 1, holder.query());
            }
            return true;
        }

        if (event.getRawSlot() == layout.nextSlot()) {
            List<Entry> entries = entries(holder.type(), holder.query());
            int maxPage = Math.max(0, (entries.size() - 1) / layout.contentSlots().size());
            if (holder.page() < maxPage) {
                open(player, holder.type(), holder.page() + 1, holder.query());
            }
            return true;
        }

        if (event.getRawSlot() == layout.searchSlot()) {
            pendingSearchType.put(player.getUniqueId(), holder.type());
            player.closeInventory();
            player.sendMessage(TextUtil.colorize("&eType a search query in chat for the &f" + holder.type().displayName() + "&e browser."));
            player.sendMessage(TextUtil.colorize("&7Type &fcancel &7to abort."));
            return true;
        }

        if (event.getRawSlot() == layout.closeSlot()) {
            player.closeInventory();
            return true;
        }

        int slotIndex = layout.contentSlots().indexOf(event.getRawSlot());
        if (slotIndex < 0) {
            return true;
        }

        List<Entry> entries = entries(holder.type(), holder.query());
        int entryIndex = holder.page() * layout.contentSlots().size() + slotIndex;
        if (entryIndex < 0 || entryIndex >= entries.size()) {
            return true;
        }

        handleEntryClick(player, holder.type(), entries.get(entryIndex));
        return true;
    }

    public boolean handlePendingSpawnerPlacement(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null || event.getPlayer() == null) {
            return false;
        }

        String spawnerId = pendingSpawnerPlacement.remove(event.getPlayer().getUniqueId());
        if (spawnerId == null || spawnerId.isBlank()) {
            return false;
        }

        boolean placed = spawnerService.placeSpawner(event.getClickedBlock().getLocation(), spawnerId);
        event.setCancelled(placed);
        event.getPlayer().sendMessage(TextUtil.colorize(placed
            ? "&aPlaced Bullseye spawner &f" + spawnerId + "&a."
            : "&cCould not place Bullseye spawner &f" + spawnerId + "&c."
        ));
        return true;
    }

    public boolean handleSearchChat(AsyncPlayerChatEvent event) {
        BrowserType type = pendingSearchType.remove(event.getPlayer().getUniqueId());
        if (type == null) {
            return false;
        }

        event.setCancelled(true);
        String query = event.getMessage() == null ? "" : event.getMessage().trim();
        if (query.equalsIgnoreCase("cancel")) {
            Bukkit.getScheduler().runTask(plugin, () ->
                event.getPlayer().sendMessage(TextUtil.colorize("&7Browser search canceled."))
            );
            return true;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            open(event.getPlayer(), type, 0, query);
            event.getPlayer().sendMessage(TextUtil.colorize("&aShowing &f" + type.displayName() + "&a results for &f" + query + "&a."));
        });
        return true;
    }

    public void clearPlayerState(UUID playerId) {
        pendingSpawnerPlacement.remove(playerId);
        pendingSearchType.remove(playerId);
        stateByPlayer.remove(playerId);
    }

    private void handleEntryClick(Player player, BrowserType type, Entry entry) {
        switch (type) {
            case ITEMS -> {
                ItemStack item = itemService.createItem(entry.id(), 1);
                if (item != null) {
                    player.getInventory().addItem(item);
                    player.sendMessage(TextUtil.colorize("&aReceived &f" + entry.id() + "&a."));
                }
            }
            case MOBS -> {
                CustomMobDefinition mob = mobService.getDefinition(entry.id());
                if (mob == null) {
                    return;
                }
                if (!mob.spawnEggItemId().isBlank()) {
                    ItemStack item = itemService.createItem(mob.spawnEggItemId(), 1);
                    if (item != null) {
                        player.getInventory().addItem(item);
                        player.sendMessage(TextUtil.colorize("&aReceived spawn egg for &f" + mob.id() + "&a."));
                        return;
                    }
                }
                mobService.spawnMob(mob.id(), player.getLocation(), player);
            }
            case MODELS -> {
                BullseyeModelDefinition model = modelEngineService.getDefinition(entry.id());
                if (model == null) {
                    return;
                }
                if (!model.itemId().isBlank()) {
                    ItemStack item = itemService.createItem(model.itemId(), 1);
                    if (item != null) {
                        player.getInventory().addItem(item);
                        player.sendMessage(TextUtil.colorize("&aReceived model item &f" + model.itemId() + "&a."));
                        return;
                    }
                }
                player.sendMessage(TextUtil.colorize("&eModel blueprint: &f" + model.blueprint()));
            }
            case SPAWNERS -> {
                pendingSpawnerPlacement.put(player.getUniqueId(), entry.id());
                player.closeInventory();
                player.sendMessage(TextUtil.colorize("&eRight-click a block to place Bullseye spawner &f" + entry.id() + "&e."));
            }
        }
    }

    private void fillControls(Inventory inventory, BrowserType type, int page, int maxPage, String query) {
        Layout layout = type.layout();
        inventory.setItem(layout.previousSlot(), named(Material.ARROW, page > 0 ? "&6Previous Page" : "&8Previous Page"));
        inventory.setItem(layout.nextSlot(), named(Material.ARROW, page < maxPage ? "&6Next Page" : "&8Next Page"));
        inventory.setItem(layout.closeSlot(), named(Material.BARRIER, "&cClose"));

        String searchName = query == null || query.isBlank() ? "&6Search" : "&bSearch: &f" + query;
        inventory.setItem(layout.searchSlot(), named(Material.COMPASS, searchName));
    }

    private List<Entry> entries(BrowserType type, String query) {
        String normalizedQuery = normalize(query);
        return switch (type) {
            case ITEMS -> itemService.getItemIds().stream()
                .sorted()
                .map(id -> {
                    ItemStack icon = buildItemIcon(id);
                    return new Entry(id, buildSearchableText(id, icon), icon);
                })
                .filter(entry -> matchesQuery(entry, normalizedQuery))
                .toList();
            case MOBS -> mobService.getMobIds().stream()
                .sorted()
                .map(id -> {
                    ItemStack icon = buildMobIcon(id);
                    return new Entry(id, buildSearchableText(id, icon), icon);
                })
                .filter(entry -> matchesQuery(entry, normalizedQuery))
                .toList();
            case SPAWNERS -> spawnerService.getSpawnerIds().stream()
                .sorted()
                .map(id -> {
                    ItemStack icon = buildSpawnerIcon(id);
                    return new Entry(id, buildSearchableText(id, icon), icon);
                })
                .filter(entry -> matchesQuery(entry, normalizedQuery))
                .toList();
            case MODELS -> modelEngineService.getModelIds().stream()
                .sorted()
                .map(id -> {
                    ItemStack icon = buildModelIcon(id);
                    return new Entry(id, buildSearchableText(id, icon), icon);
                })
                .filter(entry -> matchesQuery(entry, normalizedQuery))
                .toList();
        };
    }

    private boolean matchesQuery(Entry entry, String query) {
        return query.isBlank() || entry.searchableText().contains(query);
    }

    private String buildSearchableText(String id, ItemStack icon) {
        StringBuilder builder = new StringBuilder(normalize(id));
        if (icon != null && icon.hasItemMeta()) {
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                if (meta.hasDisplayName()) {
                    builder.append(' ').append(normalize(stripColors(meta.getDisplayName())));
                }
                if (meta.hasLore() && meta.getLore() != null) {
                    for (String line : meta.getLore()) {
                        builder.append(' ').append(normalize(stripColors(line)));
                    }
                }
            }
        }
        return builder.toString();
    }

    private ItemStack buildItemIcon(String itemId) {
        ItemStack item = itemService.createItem(itemId, 1);
        return item != null ? item : named(Material.PAPER, "&f" + itemId);
    }

    private ItemStack buildMobIcon(String mobId) {
        CustomMobDefinition mob = mobService.getDefinition(mobId);
        if (mob == null) {
            return named(Material.ZOMBIE_HEAD, "&f" + mobId);
        }

        ItemStack icon = null;
        if (!mob.spawnEggItemId().isBlank()) {
            icon = itemService.createItem(mob.spawnEggItemId(), 1);
        }
        if (icon == null && !mob.modelId().isBlank()) {
            BullseyeModelDefinition model = modelEngineService.getDefinition(mob.modelId());
            if (model != null && !model.itemId().isBlank()) {
                icon = itemService.createItem(model.itemId(), 1);
            }
        }
        if (icon == null) {
            icon = new ItemStack(Material.DRAGON_HEAD);
        }

        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(TextUtil.colorize(mob.displayName()));
            meta.setLore(TextUtil.colorize(List.of(
                "&7ID: &f" + mob.id(),
                "&7Health: &f" + mob.health(),
                "&7Damage: &f" + mob.damage()
            )));
            icon.setItemMeta(meta);
        }
        return icon;
    }

    private ItemStack buildSpawnerIcon(String spawnerId) {
        SpawnerDefinition definition = spawnerService.getDefinition(spawnerId);
        ItemStack icon = new ItemStack(Material.SPAWNER);
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(TextUtil.colorize("&6" + spawnerId));
            if (definition != null) {
                meta.setLore(TextUtil.colorize(List.of(
                    "&7Mob: &f" + definition.mobId(),
                    "&7Interval: &f" + definition.intervalTicks(),
                    "&7Max Nearby: &f" + definition.maxNearby()
                )));
            }
            icon.setItemMeta(meta);
        }
        return icon;
    }

    private ItemStack buildModelIcon(String modelId) {
        BullseyeModelDefinition definition = modelEngineService.getDefinition(modelId);
        if (definition == null) {
            return named(Material.ITEM_FRAME, "&f" + modelId);
        }

        ItemStack icon = null;
        if (!definition.itemId().isBlank()) {
            icon = itemService.createItem(definition.itemId(), 1);
        }
        if (icon == null) {
            icon = new ItemStack(Material.ITEM_FRAME);
        }

        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(TextUtil.colorize("&b" + definition.id()));
            meta.setLore(TextUtil.colorize(List.of(
                "&7Blueprint: &f" + definition.blueprint(),
                "&7Animation: &f" + definition.defaultAnimation()
            )));
            icon.setItemMeta(meta);
        }
        return icon;
    }

    private ItemStack named(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(TextUtil.colorize(name));
            item.setItemMeta(meta);
        }
        return item;
    }

    private String stripColors(String value) {
        return value == null ? "" : value.replaceAll("(?i)§[0-9A-FK-ORX]", "");
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public enum BrowserType {
        ITEMS("Items", ITEMS_LAYOUT),
        MOBS("Mobs", MOBS_LAYOUT),
        SPAWNERS("Spawners", new Layout("&6Bullseye Spawners", 54, GENERIC_LAYOUT.contentSlots(), GENERIC_LAYOUT.previousSlot(), GENERIC_LAYOUT.nextSlot(), GENERIC_LAYOUT.searchSlot(), GENERIC_LAYOUT.closeSlot())),
        MODELS("Models", new Layout("&bBullseye Models", 54, GENERIC_LAYOUT.contentSlots(), GENERIC_LAYOUT.previousSlot(), GENERIC_LAYOUT.nextSlot(), GENERIC_LAYOUT.searchSlot(), GENERIC_LAYOUT.closeSlot()));

        private final String displayName;
        private final Layout layout;

        BrowserType(String displayName, Layout layout) {
            this.displayName = displayName;
            this.layout = layout;
        }

        public String displayName() {
            return displayName;
        }

        public Layout layout() {
            return layout;
        }

        public static BrowserType from(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            try {
                return BrowserType.valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }
    }

    private record Entry(String id, String searchableText, ItemStack icon) {
    }

    private record Layout(
        String title,
        int size,
        List<Integer> contentSlots,
        int previousSlot,
        int nextSlot,
        int searchSlot,
        int closeSlot
    ) {
    }

    private record BrowserState(BrowserType type, int page, String query) {
    }

    public record BrowserHolder(BrowserType type, int page, String query) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
