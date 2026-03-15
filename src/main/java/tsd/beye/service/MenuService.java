package tsd.beye.service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import tsd.beye.Bullseye;
import tsd.beye.model.MenuButton;
import tsd.beye.model.MenuDefinition;
import tsd.beye.model.TriggerContext;
import tsd.beye.model.TriggerType;
import tsd.beye.utils.TextUtil;

public class MenuService {
    private final Bullseye plugin;
    private final ItemService itemService;
    private final Map<String, MenuDefinition> menus = new HashMap<>();
    private MechanicService mechanicService;

    public MenuService(Bullseye plugin, ItemService itemService) {
        this.plugin = plugin;
        this.itemService = itemService;
    }

    public void setMechanicService(MechanicService mechanicService) {
        this.mechanicService = mechanicService;
    }

    public void load(YamlConfiguration config) {
        menus.clear();

        ConfigurationSection section = config.getConfigurationSection("menus");
        if (section == null) {
            return;
        }

        for (String menuId : section.getKeys(false)) {
            ConfigurationSection menuSection = section.getConfigurationSection(menuId);
            if (menuSection == null) {
                continue;
            }

            int size = Math.max(9, menuSection.getInt("size", 27));
            if (size % 9 != 0) {
                size = ((size / 9) + 1) * 9;
            }
            size = Math.min(size, 54);
            String title = menuSection.getString("title", menuId);

            Map<Integer, MenuButton> buttons = new HashMap<>();
            ConfigurationSection buttonsSection = menuSection.getConfigurationSection("buttons");
            if (buttonsSection != null) {
                for (String slotKey : buttonsSection.getKeys(false)) {
                    int slot;
                    try {
                        slot = Integer.parseInt(slotKey);
                    } catch (NumberFormatException ex) {
                        plugin.getLogger().warning("Invalid menu slot '" + slotKey + "' in menu '" + menuId + "'.");
                        continue;
                    }

                    ConfigurationSection buttonSection = buttonsSection.getConfigurationSection(slotKey);
                    if (buttonSection == null) {
                        continue;
                    }

                    String customItemId = buttonSection.getString("item", "").toLowerCase(Locale.ROOT);
                    Material material = parseMaterial(buttonSection.getString("material", "BARRIER"), Material.BARRIER);
                    String name = buttonSection.getString("name", "");
                    List<String> lore = buttonSection.getStringList("lore");
                    List<String> mechanics = buttonSection.getStringList("mechanics");

                    buttons.put(slot, new MenuButton(slot, customItemId, material, name, lore, mechanics));
                }
            }

            menus.put(menuId.toLowerCase(Locale.ROOT), new MenuDefinition(menuId.toLowerCase(Locale.ROOT), size, title, buttons));
        }

        plugin.getLogger().info("Loaded " + menus.size() + " custom menus.");
    }

    public Set<String> getMenuIds() {
        return Collections.unmodifiableSet(menus.keySet());
    }

    public MenuDefinition getDefinition(String menuId) {
        if (menuId == null) {
            return null;
        }

        return menus.get(menuId.toLowerCase(Locale.ROOT));
    }

    public boolean openMenu(Player player, String menuId) {
        MenuDefinition definition = getDefinition(menuId);
        if (definition == null) {
            return false;
        }

        Inventory inventory = Bukkit.createInventory(new MenuHolder(definition.id()), definition.size(), TextUtil.colorize(definition.title()));
        for (MenuButton button : definition.buttons().values()) {
            ItemStack item = buildButtonItem(button);
            if (item != null) {
                inventory.setItem(button.slot(), item);
            }
        }

        player.openInventory(inventory);
        return true;
    }

    public void handleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Inventory topInventory = event.getView().getTopInventory();
        if (!(topInventory.getHolder() instanceof MenuHolder holder)) {
            return;
        }

        event.setCancelled(true);
        if (event.getRawSlot() < 0 || event.getRawSlot() >= topInventory.getSize()) {
            return;
        }

        MenuDefinition definition = getDefinition(holder.menuId());
        if (definition == null) {
            return;
        }

        MenuButton button = definition.buttons().get(event.getRawSlot());
        if (button == null || button.mechanics().isEmpty() || mechanicService == null) {
            return;
        }

        TriggerContext context = TriggerContext.builder()
            .player(player)
            .sourceId(definition.id())
            .build();

        mechanicService.executeMechanics(button.mechanics(), TriggerType.MENU_CLICK, context);
    }

    private ItemStack buildButtonItem(MenuButton button) {
        ItemStack item = null;
        if (button.customItemId() != null && !button.customItemId().isBlank()) {
            item = itemService.createItem(button.customItemId(), 1);
        }

        if (item == null) {
            item = new ItemStack(button.material());
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        if (button.name() != null && !button.name().isBlank()) {
            meta.setDisplayName(TextUtil.colorize(button.name()));
        }

        if (button.lore() != null && !button.lore().isEmpty()) {
            meta.setLore(TextUtil.colorize(button.lore()));
        }

        item.setItemMeta(meta);
        return item;
    }

    private Material parseMaterial(String value, Material fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }

        try {
            return Material.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Unknown menu material '" + value + "', using " + fallback.name());
            return fallback;
        }
    }

    public static final class MenuHolder implements InventoryHolder {
        private final String menuId;

        public MenuHolder(String menuId) {
            this.menuId = menuId;
        }

        public String menuId() {
            return menuId;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
