package gg.drak.restored.config;

import gg.drak.restored.Restored;
import gg.drak.restored.data.AugmentType;
import gg.drak.restored.recipes.ConfiguredRecipe;
import gg.drak.restored.recipes.RecipeIngredientResolver;
import gg.drak.thebase.storage.resources.flat.simple.SimpleConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RecipesConfig extends SimpleConfiguration {
    /**
     * Must not be field-initialized: {@link SimpleConfiguration} calls {@link #init()} from
     * {@code super(...)}, and a field initializer would run afterward and wipe the cache.
     */
    private List<ConfiguredRecipe> cachedRecipes;

    public RecipesConfig() {
        super("recipes.yml", Restored.getInstance(), false);
        // Belt-and-suspenders if init did not populate for any reason.
        if (cachedRecipes == null || cachedRecipes.isEmpty()) {
            reloadCached();
        }
    }

    @Override
    public void init() {
        ensureDefaults();
        reloadCached();
    }

    public void reloadCached() {
        reloadResource();
        List<ConfiguredRecipe> loaded = new ArrayList<>();
        loaded.add(readRecipe("network_core", ConfiguredRecipe.Type.SHAPED, "network_core",
                List.of("GRG", "RNR", "GRG"),
                Map.of('G', "GLASS", 'R', "REDSTONE", 'N', "NETHER_STAR"),
                List.of()));
        loaded.add(readRecipe("network_chest", ConfiguredRecipe.Type.SHAPED, "network_chest",
                List.of("PPP", "PCP", "PPP"),
                Map.of('P', "PLANKS", 'C', "network_core"),
                List.of()));
        loaded.add(readRecipe("network_hopper_input", ConfiguredRecipe.Type.SHAPED, "network_hopper_input",
                List.of("XDX", "D D", "XHX"),
                Map.of('X', "PLANKS", 'D', "DIAMOND", 'H', "HOPPER"),
                List.of()));
        loaded.add(readRecipe("network_hopper_output", ConfiguredRecipe.Type.SHAPED, "network_hopper_output",
                List.of("XHX", "D D", "XDX"),
                Map.of('X', "PLANKS", 'D', "DIAMOND", 'H', "HOPPER"),
                List.of()));
        loaded.add(readRecipe("magnet_core", ConfiguredRecipe.Type.SHAPED, "magnet_core",
                List.of("IDI", "LQL", "CDC"),
                Map.of('I', "IRON_INGOT", 'D', "DIAMOND", 'L', "LAPIS_LAZULI", 'Q', "QUARTZ", 'C', "COPPER_INGOT"),
                List.of()));
        loaded.add(readRecipe("network_component", ConfiguredRecipe.Type.SHAPELESS, "network_component",
                List.of(),
                Map.of(),
                List.of("IRON_INGOT", "REDSTONE")));
        loaded.add(readRecipe("network_upgrade", ConfiguredRecipe.Type.SHAPELESS, "network_upgrade",
                List.of(),
                Map.of(),
                List.of("network_component", "REDSTONE", "GOLD_INGOT")));
        loaded.add(readRecipe("augment_component", ConfiguredRecipe.Type.SHAPELESS, "augment_component",
                List.of(),
                Map.of(),
                List.of("network_upgrade", "NETHER_QUARTZ")));
        for (AugmentType type : AugmentType.values()) {
            if (type == AugmentType.COMPACTOR) {
                loaded.add(readRecipe("compactor_augment", ConfiguredRecipe.Type.SHAPELESS, "compactor_augment",
                        List.of(), Map.of(), List.of("network_component", "DISPENSER", "PISTON")));
            } else {
                loaded.add(readRecipe(type.recipeId(), ConfiguredRecipe.Type.SHAPELESS, type.recipeId(),
                        List.of(), Map.of(), List.of("augment_component", type.getWorkstationMaterial().name())));
            }
        }
        loaded.add(readRecipe("pocket_link", ConfiguredRecipe.Type.SHAPELESS, "pocket_link",
                List.of(),
                Map.of(),
                List.of("augment_component", "BUNDLE", "CRAFTING_TABLE")));
        loaded.add(readRecipe("pocket_augment", ConfiguredRecipe.Type.SHAPELESS, "pocket_augment",
                List.of(),
                Map.of(),
                List.of("augment_component", "BUNDLE")));
        loaded.add(readRecipe("feeding_augment", ConfiguredRecipe.Type.SHAPELESS, "feeding_augment",
                List.of(),
                Map.of(),
                List.of("pocket_augment", "GOLDEN_CARROT", "GOLDEN_APPLE", "DIAMOND_SHOVEL")));
        loaded.add(readRecipe("quiver_augment", ConfiguredRecipe.Type.SHAPELESS, "quiver_augment",
                List.of(),
                Map.of(),
                List.of("pocket_augment", "BOW", "ARROW")));
        loaded.add(readRecipe("rocket_distributer_augment", ConfiguredRecipe.Type.SHAPELESS,
                "rocket_distributer_augment", List.of(), Map.of(),
                List.of("pocket_augment", "FIREWORK_ROCKET", "HOPPER")));
        loaded.add(readRecipe("backpack_augment", ConfiguredRecipe.Type.SHAPELESS, "backpack_augment",
                List.of(),
                Map.of(),
                List.of("pocket_augment", "CHEST")));
        loaded.add(readRecipe("magnet_pocket_augment", ConfiguredRecipe.Type.SHAPELESS, "pocket_augment_magnet",
                List.of(),
                Map.of(),
                List.of("pocket_augment", "magnet_core")));
        loaded.add(readRecipe("chest_linking_tool", ConfiguredRecipe.Type.SHAPED, "chest_linking_tool",
                List.of(" X ", " S ", " S "),
                Map.of('X', "network_component", 'S', "STICK"),
                List.of()));
        this.cachedRecipes = Collections.unmodifiableList(loaded);
        Restored.getInstance().logInfo("Loaded " + loaded.size() + " recipes from recipes.yml");
    }

    public List<ConfiguredRecipe> getRecipes() {
        return cachedRecipes == null ? List.of() : cachedRecipes;
    }

    public List<ConfiguredRecipe> getEnabledRecipes() {
        List<ConfiguredRecipe> enabled = new ArrayList<>();
        for (ConfiguredRecipe recipe : cachedRecipes) {
            if (recipe.isEnabled()) {
                enabled.add(recipe);
            }
        }
        return enabled;
    }

    public ConfiguredRecipe getRecipe(String id) {
        for (ConfiguredRecipe recipe : cachedRecipes) {
            if (recipe.getId().equalsIgnoreCase(id)) {
                return recipe;
            }
        }
        return null;
    }

    /**
     * Finds a Restored custom recipe that produces the given ingredient/result id
     * (supports aliases like {@code component} → {@code network_component}).
     */
    public ConfiguredRecipe findRecipeForIngredient(String ingredientId) {
        if (!RecipeIngredientResolver.isExactCustom(ingredientId)) {
            return null;
        }
        String canonical = RecipeIngredientResolver.canonicalId(ingredientId);
        if (canonical.isBlank()) {
            return null;
        }
        ConfiguredRecipe byId = getRecipe(canonical);
        if (byId != null) {
            return byId;
        }
        for (ConfiguredRecipe recipe : getRecipes()) {
            if (canonical.equalsIgnoreCase(recipe.getResultId())
                    || canonical.equalsIgnoreCase(RecipeIngredientResolver.canonicalId(recipe.getResultId()))) {
                return recipe;
            }
        }
        return null;
    }

    private void ensureDefaults() {
        writeShapedDefaults("network_core", "network_core",
                List.of("GRG", "RNR", "GRG"),
                Map.of('G', "GLASS", 'R', "REDSTONE", 'N', "NETHER_STAR"));
        writeShapedDefaults("network_chest", "network_chest",
                List.of("PPP", "PCP", "PPP"),
                Map.of('P', "PLANKS", 'C', "network_core"));
        writeShapedDefaults("network_hopper_input", "network_hopper_input",
                List.of("XDX", "D D", "XHX"),
                Map.of('X', "PLANKS", 'D', "DIAMOND", 'H', "HOPPER"));
        writeShapedDefaults("network_hopper_output", "network_hopper_output",
                List.of("XHX", "D D", "XDX"),
                Map.of('X', "PLANKS", 'D', "DIAMOND", 'H', "HOPPER"));
        writeShapedDefaults("magnet_core", "magnet_core",
                List.of("IDI", "LQL", "CDC"),
                Map.of('I', "IRON_INGOT", 'D', "DIAMOND", 'L', "LAPIS_LAZULI", 'Q', "QUARTZ", 'C', "COPPER_INGOT"));
        writeShapelessDefaults("network_component", "network_component",
                List.of("IRON_INGOT", "REDSTONE"));
        writeShapelessDefaults("network_upgrade", "network_upgrade",
                List.of("network_component", "REDSTONE", "GOLD_INGOT"));
        writeShapelessDefaults("augment_component", "augment_component",
                List.of("network_upgrade", "NETHER_QUARTZ"));
        for (AugmentType type : AugmentType.values()) {
            if (type == AugmentType.COMPACTOR) {
                writeShapelessDefaults("compactor_augment", "compactor_augment",
                        List.of("network_component", "DISPENSER", "PISTON"));
            } else {
                writeShapelessDefaults(type.recipeId(), type.recipeId(),
                        List.of("augment_component", type.getWorkstationMaterial().name()));
            }
        }
        writeShapelessDefaults("pocket_link", "pocket_link",
                List.of("augment_component", "BUNDLE", "CRAFTING_TABLE"));
        writeShapelessDefaults("pocket_augment", "pocket_augment",
                List.of("augment_component", "BUNDLE"));
        writeShapelessDefaults("feeding_augment", "feeding_augment",
                List.of("pocket_augment", "GOLDEN_CARROT", "GOLDEN_APPLE", "DIAMOND_SHOVEL"));
        writeShapelessDefaults("quiver_augment", "quiver_augment",
                List.of("pocket_augment", "BOW", "ARROW"));
        writeShapelessDefaults("rocket_distributer_augment", "rocket_distributer_augment",
                List.of("pocket_augment", "FIREWORK_ROCKET", "HOPPER"));
        writeShapelessDefaults("backpack_augment", "backpack_augment",
                List.of("pocket_augment", "CHEST"));
        writeShapelessDefaults("magnet_pocket_augment", "pocket_augment_magnet",
                List.of("pocket_augment", "magnet_core"));
        writeShapedDefaults("chest_linking_tool", "chest_linking_tool",
                List.of(" X ", " S ", " S "),
                Map.of('X', "network_component", 'S', "STICK"));
    }

    private void writeShapedDefaults(String id, String result, List<String> shape, Map<Character, String> ingredients) {
        String base = "recipes." + id + ".";
        getOrSetDefault(base + "enabled", true);
        getOrSetDefault(base + "type", "shaped");
        getOrSetDefault(base + "result", result);
        getOrSetDefault(base + "shape", shape);
        for (Map.Entry<Character, String> entry : ingredients.entrySet()) {
            getOrSetDefault(base + "ingredients." + entry.getKey(), entry.getValue());
        }
    }

    private void writeShapelessDefaults(String id, String result, List<String> ingredients) {
        String base = "recipes." + id + ".";
        getOrSetDefault(base + "enabled", true);
        getOrSetDefault(base + "type", "shapeless");
        getOrSetDefault(base + "result", result);
        getOrSetDefault(base + "ingredients", ingredients);
    }

    private ConfiguredRecipe readRecipe(
            String id,
            ConfiguredRecipe.Type defaultType,
            String defaultResult,
            List<String> defaultShape,
            Map<Character, String> defaultShapedIngredients,
            List<String> defaultShapelessIngredients
    ) {
        String base = "recipes." + id + ".";
        boolean enabled = parseBoolean(getOrSetDefault(base + "enabled", true), true);
        String typeRaw = String.valueOf(getOrSetDefault(base + "type", defaultType.name().toLowerCase()));
        ConfiguredRecipe.Type type = "shapeless".equalsIgnoreCase(typeRaw)
                ? ConfiguredRecipe.Type.SHAPELESS
                : ConfiguredRecipe.Type.SHAPED;
        String result = String.valueOf(getOrSetDefault(base + "result", defaultResult));

        if (type == ConfiguredRecipe.Type.SHAPED) {
            List<String> shape = readStringList(base + "shape", defaultShape);
            Map<Character, String> ingredients = new LinkedHashMap<>();
            for (String line : shape) {
                for (int i = 0; i < line.length(); i++) {
                    char key = line.charAt(i);
                    if (key == ' ' || ingredients.containsKey(key)) {
                        continue;
                    }
                    String fallback = defaultShapedIngredients.getOrDefault(key, "AIR");
                    String value = String.valueOf(getOrSetDefault(base + "ingredients." + key, fallback));
                    ingredients.put(key, value);
                }
            }
            // Keep any explicitly configured default keys even if shape changed oddly.
            for (Map.Entry<Character, String> entry : defaultShapedIngredients.entrySet()) {
                ingredients.putIfAbsent(entry.getKey(),
                        String.valueOf(getOrSetDefault(base + "ingredients." + entry.getKey(), entry.getValue())));
            }
            return new ConfiguredRecipe(id, enabled, type, result, shape, ingredients, List.of());
        }

        List<String> ingredients = readStringList(base + "ingredients", defaultShapelessIngredients);
        return new ConfiguredRecipe(id, enabled, type, result, List.of(), Map.of(), ingredients);
    }

    private List<String> readStringList(String path, List<String> defaults) {
        Object value = getOrSetDefault(path, defaults);
        if (value instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object entry : list) {
                if (entry != null) {
                    out.add(String.valueOf(entry));
                }
            }
            return out;
        }
        return new ArrayList<>(defaults);
    }

    private static boolean parseBoolean(Object value, boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return fallback;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }
}
