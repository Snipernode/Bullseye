package tsd.beye.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import tsd.beye.Bullseye;

public class RecipeService {
    private final Bullseye plugin;
    private final ItemService itemService;
    private final List<NamespacedKey> registeredKeys = new ArrayList<>();

    public RecipeService(Bullseye plugin, ItemService itemService) {
        this.plugin = plugin;
        this.itemService = itemService;
    }

    public void load(YamlConfiguration config) {
        unregisterAll();

        ConfigurationSection recipesSection = config.getConfigurationSection("recipes");
        if (recipesSection == null) {
            return;
        }

        int loaded = 0;
        for (String recipeId : recipesSection.getKeys(false)) {
            ConfigurationSection recipeSection = recipesSection.getConfigurationSection(recipeId);
            if (recipeSection == null) {
                continue;
            }

            String type = recipeSection.getString("type", "shaped").toLowerCase(Locale.ROOT);
            String resultId = recipeSection.getString("result", "");
            int amount = recipeSection.getInt("amount", 1);

            ItemStack result = itemService.createItem(resultId, amount);
            if (result == null) {
                Material material = parseMaterial(resultId);
                if (material == null) {
                    plugin.getLogger().warning("Skipping recipe '" + recipeId + "': unknown result '" + resultId + "'.");
                    continue;
                }
                result = new ItemStack(material, Math.max(1, amount));
            }

            NamespacedKey key = new NamespacedKey(plugin, "recipe_" + recipeId.toLowerCase(Locale.ROOT));
            boolean success = switch (type) {
                case "shaped" -> registerShapedRecipe(key, result, recipeSection);
                case "shapeless" -> registerShapelessRecipe(key, result, recipeSection);
                default -> {
                    plugin.getLogger().warning("Unknown recipe type '" + type + "' for recipe '" + recipeId + "'.");
                    yield false;
                }
            };

            if (success) {
                registeredKeys.add(key);
                loaded++;
            }
        }

        plugin.getLogger().info("Registered " + loaded + " custom recipes.");
    }

    public void unregisterAll() {
        for (NamespacedKey key : registeredKeys) {
            Bukkit.removeRecipe(key);
        }
        registeredKeys.clear();
    }

    private boolean registerShapedRecipe(NamespacedKey key, ItemStack result, ConfigurationSection section) {
        List<String> shape = section.getStringList("shape");
        if (shape.isEmpty()) {
            plugin.getLogger().warning("Skipping shaped recipe '" + key.getKey() + "': missing shape.");
            return false;
        }

        ShapedRecipe recipe = new ShapedRecipe(key, result);
        recipe.shape(shape.toArray(new String[0]));

        ConfigurationSection ingredientsSection = section.getConfigurationSection("ingredients");
        if (ingredientsSection == null) {
            plugin.getLogger().warning("Skipping shaped recipe '" + key.getKey() + "': missing ingredients.");
            return false;
        }

        for (String symbol : ingredientsSection.getKeys(false)) {
            String rawIngredient = ingredientsSection.getString(symbol, "");
            RecipeChoice choice = createRecipeChoice(rawIngredient);
            if (choice == null) {
                plugin.getLogger().warning("Skipping invalid ingredient '" + rawIngredient + "' in recipe '" + key.getKey() + "'.");
                return false;
            }

            recipe.setIngredient(symbol.charAt(0), choice);
        }

        return Bukkit.addRecipe(recipe);
    }

    private boolean registerShapelessRecipe(NamespacedKey key, ItemStack result, ConfigurationSection section) {
        List<String> ingredients = section.getStringList("ingredients");
        if (ingredients.isEmpty()) {
            plugin.getLogger().warning("Skipping shapeless recipe '" + key.getKey() + "': missing ingredients.");
            return false;
        }

        ShapelessRecipe recipe = new ShapelessRecipe(key, result);
        for (String ingredient : ingredients) {
            RecipeChoice choice = createRecipeChoice(ingredient);
            if (choice == null) {
                plugin.getLogger().warning("Skipping invalid ingredient '" + ingredient + "' in recipe '" + key.getKey() + "'.");
                return false;
            }
            recipe.addIngredient(choice);
        }

        return Bukkit.addRecipe(recipe);
    }

    private RecipeChoice createRecipeChoice(String rawIngredient) {
        if (rawIngredient == null || rawIngredient.isBlank()) {
            return null;
        }

        ItemStack custom = itemService.createItem(rawIngredient, 1);
        if (custom != null) {
            return new RecipeChoice.ExactChoice(custom);
        }

        Material material = parseMaterial(rawIngredient);
        if (material != null) {
            return new RecipeChoice.MaterialChoice(material);
        }

        return null;
    }

    private Material parseMaterial(String value) {
        try {
            return Material.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
