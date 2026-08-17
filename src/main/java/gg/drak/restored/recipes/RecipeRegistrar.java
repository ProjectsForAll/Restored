package gg.drak.restored.recipes;

import gg.drak.restored.Restored;
import host.plas.bou.utils.PluginUtils;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class RecipeRegistrar {
    private static final Set<NamespacedKey> REGISTERED = new HashSet<>();
    private static final Map<NamespacedKey, ConfiguredRecipe> BY_KEY = new HashMap<>();

    private RecipeRegistrar() {
    }

    public static void register() {
        unregisterAll();
        for (ConfiguredRecipe recipe : Restored.getRecipesConfig().getEnabledRecipes()) {
            try {
                if (recipe.getType() == ConfiguredRecipe.Type.SHAPED) {
                    registerShaped(recipe);
                } else {
                    registerShapeless(recipe);
                }
            } catch (Exception e) {
                Restored.getInstance().logWarning("Failed to register recipe " + recipe.getId() + ": " + e.getMessage());
            }
        }
        Restored.getInstance().logInfo("Registered " + REGISTERED.size() + " Restored recipes from recipes.yml");
    }

    public static void unregisterAll() {
        for (NamespacedKey key : REGISTERED) {
            Bukkit.removeRecipe(key);
        }
        REGISTERED.clear();
        BY_KEY.clear();
    }

    public static boolean isRestoredRecipe(Recipe recipe) {
        if (!(recipe instanceof Keyed keyed)) {
            return false;
        }
        return REGISTERED.contains(keyed.getKey());
    }

    public static ConfiguredRecipe getConfigured(Recipe recipe) {
        if (!(recipe instanceof Keyed keyed)) {
            return null;
        }
        return BY_KEY.get(keyed.getKey());
    }

    public static Set<NamespacedKey> getRegisteredKeys() {
        return Collections.unmodifiableSet(REGISTERED);
    }

    private static void registerShaped(ConfiguredRecipe configured) {
        NamespacedKey key = keyFor(configured.getId());
        Bukkit.removeRecipe(key);

        ItemStack result = configured.createResult();
        ShapedRecipe recipe = new ShapedRecipe(key, result);

        String[] shape = normalizeShape(configured.getShape());
        recipe.shape(shape);

        for (Map.Entry<Character, String> entry : configured.getShapedIngredients().entrySet()) {
            char ingredientKey = entry.getKey();
            if (!shapeUses(shape, ingredientKey)) {
                continue;
            }
            recipe.setIngredient(ingredientKey, RecipeIngredientResolver.toChoice(entry.getValue()));
        }

        Bukkit.addRecipe(recipe);
        REGISTERED.add(key);
        BY_KEY.put(key, configured);
    }

    private static void registerShapeless(ConfiguredRecipe configured) {
        NamespacedKey key = keyFor(configured.getId());
        Bukkit.removeRecipe(key);

        ItemStack result = configured.createResult();
        ShapelessRecipe recipe = new ShapelessRecipe(key, result);
        for (String ingredientId : configured.getShapelessIngredients()) {
            recipe.addIngredient(RecipeIngredientResolver.toChoice(ingredientId));
        }

        Bukkit.addRecipe(recipe);
        REGISTERED.add(key);
        BY_KEY.put(key, configured);
    }

    private static NamespacedKey keyFor(String id) {
        String sanitized = id.toLowerCase(Locale.ROOT).replace(' ', '_');
        return PluginUtils.getPluginKey(Restored.getInstance(), sanitized);
    }

    private static String[] normalizeShape(java.util.List<String> shape) {
        String[] lines = new String[Math.min(3, Math.max(1, shape.size()))];
        for (int i = 0; i < lines.length; i++) {
            String line = i < shape.size() ? shape.get(i) : "   ";
            if (line.length() > 3) {
                line = line.substring(0, 3);
            }
            while (line.length() < 3) {
                line = line + " ";
            }
            lines[i] = line;
        }
        return lines;
    }

    private static boolean shapeUses(String[] shape, char key) {
        for (String line : shape) {
            if (line.indexOf(key) >= 0) {
                return true;
            }
        }
        return false;
    }
}
