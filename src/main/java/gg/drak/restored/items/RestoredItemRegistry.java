package gg.drak.restored.items;

import gg.drak.restored.Restored;
import gg.drak.restored.data.AugmentType;
import gg.drak.restored.recipes.RecipeIngredientResolver;
import host.plas.bou.items.ItemFactory;
import host.plas.bou.items.retrievables.RetrievableKey;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Canonical Restored item keys for {@code /rgetitem} and BOU {@code /item-factory}.
 */
public final class RestoredItemRegistry {

    private static final Set<String> KEYS;

    static {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        keys.add("network_core");
        keys.add("core");
        keys.add("network_chest");
        keys.add("chest");
        keys.add("network_component");
        keys.add("component");
        keys.add("network_upgrade");
        keys.add("upgrade");
        keys.add("augment_component");
        for (AugmentType type : AugmentType.values()) {
            keys.add(type.itemTypeTag());
        }
        keys.add("pocket_link");
        keys.add("pocket_augment");
        for (gg.drak.restored.data.PocketAugmentType type : gg.drak.restored.data.PocketAugmentType.values()) {
            keys.add(type.itemTypeTag());
        }
        keys.add("feeding_augment");
        keys.add("pocket_augment_feeding");
        keys.add("quiver_augment");
        keys.add("backpack_augment");
        keys.add("chest_linking_tool");
        keys.add("linking_tool");
        KEYS = Collections.unmodifiableSet(keys);
    }

    private RestoredItemRegistry() {
    }

    public static Set<String> keys() {
        return KEYS;
    }

    public static void registerWithItemFactory() {
        Restored plugin = Restored.getInstance();
        for (String key : KEYS) {
            plugin.registerFactory(key, () -> create(key).orElse(null));
        }
    }

    public static void unregisterFromItemFactory() {
        Restored plugin = Restored.getInstance();
        for (String key : KEYS) {
            ItemFactory.unregisterFactory(RetrievableKey.of(plugin, key));
        }
    }

    public static Optional<ItemStack> create(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        String key = id.trim().toLowerCase(Locale.ROOT);
        if (!KEYS.contains(key) && !RecipeIngredientResolver.isExactCustom(key)) {
            return Optional.empty();
        }
        ItemStack stack = RecipeIngredientResolver.resolve(key);
        if (!RestoredItems.isRestoredItem(stack)) {
            return Optional.empty();
        }
        return Optional.of(stack);
    }
}
