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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class RecipeRegistrar {
    private static final Set<NamespacedKey> REGISTERED = new LinkedHashSet<>();
    private static final Map<NamespacedKey, ConfiguredRecipe> BY_KEY = new LinkedHashMap<>();
    private static boolean registryBridgeResolved;
    private static RecipeRegistryBridge registryBridge;
    private static boolean removalFallbackWarned;

    private RecipeRegistrar() {
    }

    public static void register() {
        long startedAt = System.nanoTime();
        Map<NamespacedKey, ConfiguredRecipe> desired = new LinkedHashMap<>();
        for (ConfiguredRecipe recipe : Restored.getRecipesConfig().getEnabledRecipes()) {
            desired.put(keyFor(recipe.getId()), recipe);
        }

        if (REGISTERED.isEmpty()) {
            adoptServerRecipes(desired);
        }

        Set<NamespacedKey> toRemove = new LinkedHashSet<>(REGISTERED);
        Set<NamespacedKey> toAdd = new LinkedHashSet<>();
        for (Map.Entry<NamespacedKey, ConfiguredRecipe> entry : desired.entrySet()) {
            NamespacedKey key = entry.getKey();
            ConfiguredRecipe previous = BY_KEY.get(key);
            if (!REGISTERED.contains(key)) {
                toAdd.add(key);
            } else if (!sameDefinition(previous, entry.getValue())) {
                toRemove.add(key);
                toAdd.add(key);
            } else {
                toRemove.remove(key);
            }
        }

        if (toRemove.isEmpty() && toAdd.isEmpty()) {
            Restored.getInstance().logInfo("Restored recipes unchanged; skipped recipe registry update ("
                    + REGISTERED.size() + " recipes, " + elapsedMillis(startedAt) + " ms)");
            return;
        }

        List<NamespacedKey> removals = new ArrayList<>(toRemove);
        boolean usedDirectRegistryMutation = false;
        for (int i = 0; i < removals.size(); i++) {
            NamespacedKey key = removals.get(i);
            RegistryResult result = removeRecipe(key);
            usedDirectRegistryMutation |= result.directMutation();
            if (result.changed()) {
                REGISTERED.remove(key);
                BY_KEY.remove(key);
                System.clearProperty(fingerprintProperty(key));
            }
        }

        List<NamespacedKey> additions = new ArrayList<>(toAdd);
        for (int i = 0; i < additions.size(); i++) {
            NamespacedKey key = additions.get(i);
            ConfiguredRecipe recipe = desired.get(key);
            try {
                RegistryResult result = addRecipe(createRecipe(recipe));
                usedDirectRegistryMutation |= result.directMutation();
                if (result.changed()) {
                    REGISTERED.add(key);
                    BY_KEY.put(key, recipe);
                    System.setProperty(fingerprintProperty(key), fingerprint(recipe));
                }
            } catch (Exception e) {
                Restored.getInstance().logWarning("Failed to register recipe " + recipe.getId() + ": " + e.getMessage());
            }
        }
        if (usedDirectRegistryMutation) {
            Bukkit.updateRecipes();
        }
        Restored.getInstance().logInfo("Registered " + REGISTERED.size() + " Restored recipes from recipes.yml ("
                + (removals.size() + additions.size()) + " changed, " + elapsedMillis(startedAt) + " ms)");
    }

    public static void unregisterAll() {
        List<NamespacedKey> keys = new ArrayList<>(REGISTERED);
        boolean usedDirectRegistryMutation = false;
        for (int i = 0; i < keys.size(); i++) {
            RegistryResult result = removeRecipe(keys.get(i));
            usedDirectRegistryMutation |= result.directMutation();
            System.clearProperty(fingerprintProperty(keys.get(i)));
        }
        if (usedDirectRegistryMutation) {
            Bukkit.updateRecipes();
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

    private static Recipe createRecipe(ConfiguredRecipe configured) {
        if (configured.getType() == ConfiguredRecipe.Type.SHAPED) {
            return createShapedRecipe(configured);
        }
        return createShapelessRecipe(configured);
    }

    private static ShapedRecipe createShapedRecipe(ConfiguredRecipe configured) {
        NamespacedKey key = keyFor(configured.getId());

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

        return recipe;
    }

    private static ShapelessRecipe createShapelessRecipe(ConfiguredRecipe configured) {
        NamespacedKey key = keyFor(configured.getId());

        ItemStack result = configured.createResult();
        ShapelessRecipe recipe = new ShapelessRecipe(key, result);
        for (String ingredientId : configured.getShapelessIngredients()) {
            recipe.addIngredient(RecipeIngredientResolver.toChoice(ingredientId));
        }

        return recipe;
    }

    private static RegistryResult addRecipe(Recipe recipe) {
        RecipeRegistryBridge bridge = getRegistryBridge();
        if (bridge != null) {
            try {
                bridge.add(recipe);
                return new RegistryResult(true, true);
            } catch (ReflectiveOperationException e) {
                Restored.getInstance().logWarning(
                        "Paper recipe batching failed; falling back to Bukkit registration: "
                                + e.getMessage());
            }
        }
        return new RegistryResult(Bukkit.addRecipe(recipe, false), false);
    }

    private static RegistryResult removeRecipe(NamespacedKey key) {
        RecipeRegistryBridge bridge = getRegistryBridge();
        if (bridge != null && bridge.canRemove()) {
            try {
                return new RegistryResult(bridge.remove(key), true);
            } catch (ReflectiveOperationException e) {
                warnRemovalFallback(e.getMessage());
            }
        } else if (bridge != null) {
            warnRemovalFallback("server recipe-key conversion is unavailable");
        }
        return new RegistryResult(Bukkit.removeRecipe(key, false), false);
    }

    private static void warnRemovalFallback(String reason) {
        if (!removalFallbackWarned) {
            removalFallbackWarned = true;
            Restored.getInstance().logWarning(
                    "Paper recipe removal batching is unavailable; falling back to Bukkit removal: " + reason);
        }
    }

    private static RecipeRegistryBridge getRegistryBridge() {
        if (!registryBridgeResolved) {
            registryBridgeResolved = true;
            try {
                registryBridge = RecipeRegistryBridge.create();
            } catch (ReflectiveOperationException e) {
                Restored.getInstance().logWarning(
                        "Paper recipe addition batching is unavailable; falling back to Bukkit recipe registration: "
                                + e.getMessage());
            }
        }
        return registryBridge;
    }

    /**
     * Picks up Restored recipes the server already holds from an earlier load of this plugin
     * (a /reload or plugin-manager reload keeps them registered, but this class starts empty).
     *
     * <p>Every Bukkit.addRecipe/removeRecipe rebuilds the recipe book and reloads every online
     * player's advancements, so re-adding an unchanged set stalls the server for seconds. A
     * recipe whose definition fingerprint matches the one recorded when it was added is adopted
     * as-is; any other Restored-namespace recipe is adopted without a definition, which makes the
     * normal diff below replace or remove it. The fingerprints live in JVM system properties
     * because those share the recipe registry's lifetime: they survive plugin reloads and vanish
     * on a server restart, exactly when the registry is rebuilt from nothing.
     */
    private static void adoptServerRecipes(Map<NamespacedKey, ConfiguredRecipe> desired) {
        String namespace = keyFor("probe").getNamespace();
        java.util.Iterator<Recipe> iterator = Bukkit.recipeIterator();
        while (iterator.hasNext()) {
            Recipe recipe;
            try {
                recipe = iterator.next();
            } catch (RuntimeException e) {
                continue;
            }
            if (!(recipe instanceof Keyed keyed) || !namespace.equals(keyed.getKey().getNamespace())) {
                continue;
            }
            NamespacedKey key = keyed.getKey();
            REGISTERED.add(key);
            ConfiguredRecipe wanted = desired.get(key);
            if (wanted != null && fingerprint(wanted).equals(System.getProperty(fingerprintProperty(key)))) {
                BY_KEY.put(key, wanted);
            }
        }
    }

    private static String fingerprintProperty(NamespacedKey key) {
        return "restored.recipe." + key;
    }

    private static String fingerprint(ConfiguredRecipe recipe) {
        return String.valueOf(Objects.hash(
                recipe.isEnabled(),
                recipe.getId(),
                recipe.getType(),
                recipe.getResultId(),
                recipe.getShape(),
                recipe.getShapedIngredients(),
                recipe.getShapelessIngredients()));
    }

    private static boolean sameDefinition(ConfiguredRecipe left, ConfiguredRecipe right) {
        return left != null
                && left.isEnabled() == right.isEnabled()
                && Objects.equals(left.getId(), right.getId())
                && left.getType() == right.getType()
                && Objects.equals(left.getResultId(), right.getResultId())
                && Objects.equals(left.getShape(), right.getShape())
                && Objects.equals(left.getShapedIngredients(), right.getShapedIngredients())
                && Objects.equals(left.getShapelessIngredients(), right.getShapelessIngredients());
    }

    private static long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

    private record RegistryResult(boolean changed, boolean directMutation) {
    }

    /**
     * Uses the same CraftBukkit conversion methods as CraftServer.addRecipe/removeRecipe,
     * but postpones the client recipe sync until all registry mutations are complete.
     */
    private static final class RecipeRegistryBridge {
        private final Method shapedFactory;
        private final Method shapedAdd;
        private final Method shapelessFactory;
        private final Method shapelessAdd;
        private final Method craftServerGetServer;
        private final Method minecraftServerGetRecipeManager;
        private final Method craftRecipeToMinecraft;
        private final Method craftNamespacedKeyToResourceKey;
        private final Object recipeRegistryKey;
        private final Method recipeManagerRemoveRecipe;

        private RecipeRegistryBridge(
                Method shapedFactory,
                Method shapedAdd,
                Method shapelessFactory,
                Method shapelessAdd,
                Method craftServerGetServer,
                Method minecraftServerGetRecipeManager,
                Method craftRecipeToMinecraft,
                Method craftNamespacedKeyToResourceKey,
                Object recipeRegistryKey,
                Method recipeManagerRemoveRecipe
        ) {
            this.shapedFactory = shapedFactory;
            this.shapedAdd = shapedAdd;
            this.shapelessFactory = shapelessFactory;
            this.shapelessAdd = shapelessAdd;
            this.craftServerGetServer = craftServerGetServer;
            this.minecraftServerGetRecipeManager = minecraftServerGetRecipeManager;
            this.craftRecipeToMinecraft = craftRecipeToMinecraft;
            this.craftNamespacedKeyToResourceKey = craftNamespacedKeyToResourceKey;
            this.recipeRegistryKey = recipeRegistryKey;
            this.recipeManagerRemoveRecipe = recipeManagerRemoveRecipe;
        }

        private static RecipeRegistryBridge create() throws ReflectiveOperationException {
            Class<?> craftShapedRecipe = Class.forName("org.bukkit.craftbukkit.inventory.CraftShapedRecipe");
            Class<?> craftShapelessRecipe = Class.forName("org.bukkit.craftbukkit.inventory.CraftShapelessRecipe");

            Method getServer = null;
            Method getRecipeManager = null;
            Method toMinecraft = null;
            Method toResourceKey = null;
            Object recipeRegistryKey = null;
            Method removeRecipe = null;

            try {
                Class<?> craftRecipe = Class.forName("org.bukkit.craftbukkit.inventory.CraftRecipe");
                toMinecraft = craftRecipe.getMethod("toMinecraft", NamespacedKey.class);
            } catch (ReflectiveOperationException ignored) {
                // Newer Paper versions moved recipe-key conversion to CraftNamespacedKey.
            }

            if (toMinecraft == null) {
                try {
                    Class<?> craftNamespacedKey = Class.forName("org.bukkit.craftbukkit.util.CraftNamespacedKey");
                    Class<?> resourceKey = Class.forName("net.minecraft.resources.ResourceKey");
                    Class<?> registries = Class.forName("net.minecraft.core.registries.Registries");
                    toResourceKey = craftNamespacedKey.getMethod("toResourceKey", resourceKey, NamespacedKey.class);
                    recipeRegistryKey = registries.getField("RECIPE").get(null);
                } catch (ReflectiveOperationException ignored) {
                    // Recipe additions only need the CraftShaped/CraftShapeless conversion path.
                    // Removal can use the public API when this server's key bridge differs.
                }
            }

            Class<?> minecraftRecipeKey = toMinecraft != null
                    ? toMinecraft.getReturnType()
                    : toResourceKey == null ? null : toResourceKey.getReturnType();
            if (minecraftRecipeKey != null) {
                try {
                    Object server = Bukkit.getServer();
                    getServer = server.getClass().getMethod("getServer");
                    Object minecraftServer = getServer.invoke(server);
                    getRecipeManager = minecraftServer.getClass().getMethod("getRecipeManager");
                    Object recipeManager = getRecipeManager.invoke(minecraftServer);
                    removeRecipe = recipeManager.getClass().getMethod("removeRecipe", minecraftRecipeKey);
                } catch (ReflectiveOperationException ignored) {
                    // Removal can use the public API when this server's recipe manager differs.
                }
            }

            return new RecipeRegistryBridge(
                    craftShapedRecipe.getMethod("fromBukkitRecipe", ShapedRecipe.class),
                    craftShapedRecipe.getMethod("addToCraftingManager"),
                    craftShapelessRecipe.getMethod("fromBukkitRecipe", ShapelessRecipe.class),
                    craftShapelessRecipe.getMethod("addToCraftingManager"),
                    getServer,
                    getRecipeManager,
                    toMinecraft,
                    toResourceKey,
                    recipeRegistryKey,
                    removeRecipe
            );
        }

        private boolean canRemove() {
            return craftServerGetServer != null
                    && minecraftServerGetRecipeManager != null
                    && (craftRecipeToMinecraft != null
                    || (craftNamespacedKeyToResourceKey != null && recipeRegistryKey != null))
                    && recipeManagerRemoveRecipe != null;
        }

        private void add(Recipe recipe) throws ReflectiveOperationException {
            Method factory = recipe instanceof ShapedRecipe ? shapedFactory : shapelessFactory;
            Method add = recipe instanceof ShapedRecipe ? shapedAdd : shapelessAdd;
            add.invoke(factory.invoke(null, recipe));
        }

        private boolean remove(NamespacedKey key) throws ReflectiveOperationException {
            Object server = Bukkit.getServer();
            Object minecraftServer = craftServerGetServer.invoke(server);
            Object recipeManager = minecraftServerGetRecipeManager.invoke(minecraftServer);
            Object minecraftKey = craftRecipeToMinecraft != null
                    ? craftRecipeToMinecraft.invoke(null, key)
                    : craftNamespacedKeyToResourceKey.invoke(null, recipeRegistryKey, key);
            return (Boolean) recipeManagerRemoveRecipe.invoke(recipeManager, minecraftKey);
        }
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
