package tsd.beye.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Location;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import tsd.beye.Bullseye;
import tsd.beye.configs.SoundManager;
import tsd.beye.model.MechanicAction;
import tsd.beye.model.MechanicDefinition;
import tsd.beye.model.TriggerContext;
import tsd.beye.model.TriggerType;
import tsd.beye.utils.TextUtil;

public class MechanicService {
    private final Bullseye plugin;
    private final ItemService itemService;
    private final Map<String, MechanicDefinition> mechanics = new HashMap<>();
    private MenuService menuService;
    private SoundManager soundManager;
    private MobService mobService;
    private BrowserService browserService;

    public MechanicService(Bullseye plugin, ItemService itemService) {
        this.plugin = plugin;
        this.itemService = itemService;
    }

    public void setMenuService(MenuService menuService) {
        this.menuService = menuService;
    }

    public void setSoundManager(SoundManager soundManager) {
        this.soundManager = soundManager;
    }

    public void setMobService(MobService mobService) {
        this.mobService = mobService;
    }

    public void setBrowserService(BrowserService browserService) {
        this.browserService = browserService;
    }

    public void load(YamlConfiguration config) {
        mechanics.clear();

        ConfigurationSection section = config.getConfigurationSection("mechanics");
        if (section == null) {
            return;
        }

        for (String mechanicId : section.getKeys(false)) {
            ConfigurationSection mechanicSection = section.getConfigurationSection(mechanicId);
            if (mechanicSection == null) {
                continue;
            }

            Map<TriggerType, List<MechanicAction>> byTrigger = new HashMap<>();
            for (String triggerKey : mechanicSection.getKeys(false)) {
                TriggerType trigger = parseTrigger(triggerKey);
                if (trigger == null) {
                    plugin.getLogger().warning("Unknown trigger '" + triggerKey + "' in mechanic '" + mechanicId + "'.");
                    continue;
                }

                List<Map<?, ?>> actionMaps = mechanicSection.getMapList(triggerKey);
                List<MechanicAction> actions = actionMaps.stream()
                    .map(this::toAction)
                    .filter(Objects::nonNull)
                    .toList();

                byTrigger.put(trigger, actions);
            }

            mechanics.put(mechanicId.toLowerCase(Locale.ROOT), new MechanicDefinition(mechanicId.toLowerCase(Locale.ROOT), byTrigger));
        }

        plugin.getLogger().info("Loaded " + mechanics.size() + " mechanics.");
    }

    public void executeMechanics(Collection<String> mechanicIds, TriggerType trigger, TriggerContext context) {
        if (mechanicIds == null || mechanicIds.isEmpty()) {
            return;
        }

        for (String mechanicId : mechanicIds) {
            executeMechanic(mechanicId, trigger, context);
        }
    }

    public void executeMechanic(String mechanicId, TriggerType trigger, TriggerContext context) {
        if (mechanicId == null || mechanicId.isBlank()) {
            return;
        }

        MechanicDefinition definition = mechanics.get(mechanicId.toLowerCase(Locale.ROOT));
        if (definition == null) {
            return;
        }

        List<MechanicAction> actions = definition.actionsByTrigger().get(trigger);
        executeActions(actions, context);
    }

    public void executeActions(Collection<MechanicAction> actions, TriggerContext context) {
        if (actions == null || actions.isEmpty()) {
            return;
        }

        for (MechanicAction action : actions) {
            runAction(action, context);
        }
    }

    private MechanicAction toAction(Map<?, ?> map) {
        Object typeRaw = map.get("type");
        if (typeRaw == null) {
            return null;
        }

        String type = String.valueOf(typeRaw).toLowerCase(Locale.ROOT);
        Map<String, Object> data = new HashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            data.put(String.valueOf(entry.getKey()), entry.getValue());
        }

        return new MechanicAction(type, data);
    }

    private void runAction(MechanicAction action, TriggerContext context) {
        String type = action.type();
        Map<String, Object> data = action.data();

        Player player = context.getPlayer();
        Entity target = context.getTarget();
        Block block = context.getBlock();

        switch (type) {
            case "message" -> {
                if (player != null) {
                    String text = replacePlaceholders(getString(data, "text", ""), context);
                    player.sendMessage(TextUtil.colorize(text));
                }
            }
            case "console_command" -> {
                String command = replacePlaceholders(getString(data, "command", ""), context);
                if (!command.isBlank()) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                }
            }
            case "player_command" -> {
                if (player != null) {
                    String command = replacePlaceholders(getString(data, "command", ""), context);
                    if (!command.isBlank()) {
                        player.performCommand(command);
                    }
                }
            }
            case "sound" -> {
                Location location = resolveLocation(data, context);
                Sound sound = parseSound(getString(data, "sound", "ENTITY_EXPERIENCE_ORB_PICKUP"), Sound.ENTITY_EXPERIENCE_ORB_PICKUP);
                float volume = (float) getDouble(data, "volume", 1.0D);
                float pitch = (float) getDouble(data, "pitch", 1.0D);
                if (location != null && location.getWorld() != null) {
                    location.getWorld().playSound(location, sound, volume, pitch);
                }
            }
            case "particle" -> {
                Location location = resolveLocation(data, context);
                if (location != null && location.getWorld() != null) {
                    Particle particle = parseEnum(Particle.class, getString(data, "particle", "CRIT"), Particle.CRIT);
                    int count = getInt(data, "count", 8);
                    double spread = getDouble(data, "spread", 0.25D);
                    location.getWorld().spawnParticle(particle, location, count, spread, spread, spread, 0.02D);
                }
            }
            case "burn_target" -> {
                if (target instanceof LivingEntity livingEntity) {
                    int ticks = getInt(data, "ticks", 60);
                    livingEntity.setFireTicks(Math.max(ticks, livingEntity.getFireTicks()));
                }
            }
            case "damage_target" -> {
                if (target instanceof LivingEntity livingEntity) {
                    double amount = Math.max(0.0D, getDouble(data, "amount", 1.0D));
                    if (player != null) {
                        livingEntity.damage(amount, player);
                    } else {
                        livingEntity.damage(amount);
                    }
                }
            }
            case "potion" -> {
                String effectName = getString(data, "effect", "SPEED");
                PotionEffectType effectType = PotionEffectType.getByName(effectName.toUpperCase(Locale.ROOT));
                if (effectType == null) {
                    return;
                }

                int duration = getInt(data, "duration", 100);
                int amplifier = getInt(data, "amplifier", 0);
                String targetType = getString(data, "target", "player");
                LivingEntity recipient = "target".equalsIgnoreCase(targetType) && target instanceof LivingEntity livingEntity
                    ? livingEntity
                    : player;

                if (recipient != null) {
                    recipient.addPotionEffect(new PotionEffect(effectType, duration, amplifier, true, true));
                }
            }
            case "give_item" -> {
                if (player != null) {
                    String itemId = getString(data, "item", "");
                    int amount = getInt(data, "amount", 1);
                    ItemStack item = itemService.createItem(itemId, amount);
                    if (item != null) {
                        player.getInventory().addItem(item);
                    }
                }
            }
            case "open_menu" -> {
                if (player != null && menuService != null) {
                    String menuId = getString(data, "menu", "");
                    menuService.openMenu(player, menuId);
                }
            }
            case "open_browser" -> {
                if (player != null && browserService != null) {
                    String browser = getString(data, "browser", "");
                    int page = getInt(data, "page", 0);
                    browserService.open(player, browser, page);
                }
            }
            case "close_menu" -> {
                if (player != null) {
                    player.closeInventory();
                }
            }
            case "spawn_mob" -> {
                if (mobService == null) {
                    return;
                }

                String mobId = getString(data, "mob", "");
                Location location = resolveLocation(data, context);
                if (location == null) {
                    return;
                }

                mobService.spawnMob(mobId, location, player);
            }
            case "toggle_block" -> {
                if (block != null) {
                    Material on = parseMaterial(getString(data, "material-on", "REDSTONE_LAMP"), Material.REDSTONE_LAMP);
                    Material off = parseMaterial(getString(data, "material-off", "REDSTONE_BLOCK"), Material.REDSTONE_BLOCK);
                    block.setType(block.getType() == on ? off : on, false);
                }
            }
            default -> plugin.getLogger().warning("Unknown mechanic action type: " + type);
        }
    }

    private String replacePlaceholders(String value, TriggerContext context) {
        String output = value;
        if (context.getPlayer() != null) {
            output = output.replace("{player}", context.getPlayer().getName());
        }
        if (context.getSourceId() != null) {
            output = output.replace("{source_id}", context.getSourceId());
        }
        if (context.getTarget() != null) {
            output = output.replace("{target}", context.getTarget().getName());
        }
        if (context.getBlock() != null) {
            output = output
                .replace("{x}", Integer.toString(context.getBlock().getX()))
                .replace("{y}", Integer.toString(context.getBlock().getY()))
                .replace("{z}", Integer.toString(context.getBlock().getZ()));
        }
        return output;
    }

    private Location resolveLocation(Map<String, Object> data, TriggerContext context) {
        String targetType = getString(data, "target", "player").toLowerCase(Locale.ROOT);
        return switch (targetType) {
            case "self", "target" -> context.getTarget() != null ? context.getTarget().getLocation().add(0.0D, 1.0D, 0.0D) : null;
            case "block" -> context.getBlock() != null ? context.getBlock().getLocation().add(0.5D, 1.0D, 0.5D) : null;
            case "player" -> context.getPlayer() != null ? context.getPlayer().getLocation().add(0.0D, 1.0D, 0.0D) : null;
            default -> context.getPlayer() != null ? context.getPlayer().getLocation().add(0.0D, 1.0D, 0.0D) : null;
        };
    }

    private TriggerType parseTrigger(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        try {
            return TriggerType.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private Material parseMaterial(String value, Material fallback) {
        try {
            return Material.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    private <E extends Enum<E>> E parseEnum(Class<E> enumType, String value, E fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }

        try {
            return Enum.valueOf(enumType, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    private Sound parseSound(String value, Sound fallback) {
        if (soundManager != null) {
            return soundManager.resolveSound(value, fallback);
        }
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Sound.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    private String getString(Map<String, Object> data, String key, String fallback) {
        Object value = data.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private int getInt(Map<String, Object> data, String key, int fallback) {
        Object value = data.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }

        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
            }
        }

        return fallback;
    }

    private double getDouble(Map<String, Object> data, String key, double fallback) {
        Object value = data.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }

        if (value != null) {
            try {
                return Double.parseDouble(String.valueOf(value));
            } catch (NumberFormatException ignored) {
            }
        }

        return fallback;
    }
}
