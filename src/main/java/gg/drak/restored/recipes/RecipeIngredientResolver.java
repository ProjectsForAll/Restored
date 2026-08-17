package gg.drak.restored.recipes;

import gg.drak.restored.data.AugmentType;
import gg.drak.restored.integration.CustomItemBridge;
import gg.drak.restored.data.PocketAugmentType;
import gg.drak.restored.items.AugmentComponentItem;
import gg.drak.restored.items.ChestLinkingToolItem;
import gg.drak.restored.items.FeedingAugmentItem;
import gg.drak.restored.items.BackpackAugmentItem;
import gg.drak.restored.items.QuiverAugmentItem;
import gg.drak.restored.items.NetworkAugmentItem;
import gg.drak.restored.items.NetworkChestItem;
import gg.drak.restored.items.NetworkComponentItem;
import gg.drak.restored.items.NetworkCoreItem;
import gg.drak.restored.items.NetworkUpgradeItem;
import gg.drak.restored.items.PocketAugmentItem;
import gg.drak.restored.items.PocketLinkItem;
import gg.drak.restored.items.RestoredItems;
import gg.drak.restored.util.LegacyColors;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class RecipeIngredientResolver {

    private RecipeIngredientResolver() {
    }

    public static ItemStack resolve(String id) {
        if (id == null || id.isBlank()) {
            return new ItemStack(Material.BARRIER);
        }

        String key = id.trim();
        String lower = key.toLowerCase(Locale.ROOT);

        return switch (lower) {
            case "network_core", "core" -> NetworkCoreItem.create();
            case "network_chest", "chest" -> NetworkChestItem.create();
            case "network_component", "component" -> NetworkComponentItem.create();
            case "network_upgrade", "upgrade" -> NetworkUpgradeItem.create();
            case "augment_component" -> AugmentComponentItem.create();
            case "pocket_link" -> PocketLinkItem.create();
            case "pocket_augment" -> PocketAugmentItem.create();
            case "feeding_augment", "pocket_augment_feeding" -> FeedingAugmentItem.create();
            case "quiver_augment", "pocket_augment_quiver" -> QuiverAugmentItem.create();
            case "backpack_augment", "pocket_augment_backpack" -> BackpackAugmentItem.create();
            case "chest_linking_tool", "linking_tool" -> ChestLinkingToolItem.create();
            default -> {
                if (isPlanksTag(lower)) {
                    yield planksDisplayItem();
                }
                AugmentType augmentType = AugmentType.fromId(lower);
                if (augmentType != null) {
                    yield NetworkAugmentItem.create(augmentType);
                }
                PocketAugmentType pocketType = PocketAugmentType.fromId(lower);
                if (pocketType != null) {
                    yield switch (pocketType) {
                        case FEEDING -> FeedingAugmentItem.create();
                        case QUIVER -> QuiverAugmentItem.create();
                        case BACKPACK -> BackpackAugmentItem.create();
                    };
                }
                yield resolveExternalOrVanilla(key);
            }
        };
    }

    /**
     * True for recipe ids that should accept any {@link Tag#PLANKS} wood type.
     * {@code OAK_PLANKS} is included for backwards compatibility with older recipes.yml files.
     */
    public static boolean isPlanksTag(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        String lower = id.trim().toLowerCase(Locale.ROOT);
        return lower.equals("planks")
                || lower.equals("any_planks")
                || lower.equals("wood_planks")
                || lower.equals("#planks")
                || lower.equals("minecraft:planks")
                || lower.equals("oak_planks");
    }

    private static ItemStack planksDisplayItem() {
        ItemStack stack = new ItemStack(Material.OAK_PLANKS);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(LegacyColors.color("#FFED6AAny Wood Planks"));
            List<String> lore = new ArrayList<>();
            lore.add(LegacyColors.color("#bdc8c9Accepts all plank types"));
            lore.add(LegacyColors.color("#AAAAAA(oak, spruce, birch, jungle,"));
            lore.add(LegacyColors.color("#AAAAAAacacia, dark oak, mangrove,"));
            lore.add(LegacyColors.color("#AAAAAAcherry, bamboo, crimson, warped, …)"));
            meta.setLore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public static boolean isExactCustom(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        String lower = id.trim().toLowerCase(Locale.ROOT);
        if (lower.startsWith("network_") || lower.equals("core") || lower.equals("chest")
                || lower.equals("component") || lower.equals("upgrade")
                || lower.equals("augment_component") || lower.startsWith("augment_")
                || lower.equals("pocket_link") || lower.equals("pocket_augment")
                || lower.equals("feeding_augment") || lower.equals("quiver_augment")
                || lower.equals("backpack_augment") || lower.startsWith("pocket_augment_")
                || lower.equals("chest_linking_tool") || lower.equals("linking_tool")) {
            return true;
        }
        return lower.contains(":");
    }

    /**
     * Maps ingredient aliases to the canonical result/recipe id used in recipes.yml.
     */
    public static String canonicalId(String id) {
        if (id == null || id.isBlank()) {
            return "";
        }
        String lower = id.trim().toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "core" -> "network_core";
            case "chest" -> "network_chest";
            case "component" -> "network_component";
            case "upgrade" -> "network_upgrade";
            case "pocket_augment_feeding" -> "feeding_augment";
            case "linking_tool" -> "chest_linking_tool";
            default -> lower;
        };
    }

    /**
     * Uses Bukkit's built-in choice types only — Paper rejects custom {@link RecipeChoice}
     * implementations with "Unknown recipe stack instance".
     * <p>
     * Custom ids → {@link RecipeChoice.ExactChoice} (meta/tags must match).
     * Vanilla materials → {@link RecipeChoice.MaterialChoice}.
     * Restored-vs-vanilla substitution is additionally enforced by {@code CraftGuardListener}.
     */
    public static RecipeChoice toChoice(String id) {
        if (isPlanksTag(id)) {
            return new RecipeChoice.MaterialChoice(Tag.PLANKS);
        }
        ItemStack stack = resolve(id);
        if (isExactCustom(id) || RestoredItems.isRestoredItem(stack)) {
            ItemStack one = stack.clone();
            one.setAmount(1);
            return new RecipeChoice.ExactChoice(one);
        }
        Material material = stack.getType();
        if (material.isAir()) {
            return new RecipeChoice.MaterialChoice(Material.BARRIER);
        }
        return new RecipeChoice.MaterialChoice(material);
    }

    public static String restoredTypeOfIngredientId(String id) {
        if (!isExactCustom(id)) {
            return null;
        }
        return RestoredItems.getType(resolve(id)).orElse(null);
    }

    private static ItemStack resolveExternalOrVanilla(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        if (lower.startsWith("itemsadder:") || lower.startsWith("ia:")) {
            String customId = key.substring(key.indexOf(':') + 1);
            return CustomItemBridge.resolveIcon(customId, Material.BARRIER);
        }
        if (lower.startsWith("nexo:")) {
            return CustomItemBridge.resolveIcon(key.substring(5), Material.BARRIER);
        }
        if (lower.startsWith("mythic:") || lower.startsWith("mythicmobs:")) {
            String customId = key.substring(key.indexOf(':') + 1);
            return CustomItemBridge.resolveIcon(customId, Material.BARRIER);
        }

        Material material = Material.matchMaterial(key);
        if (material == null) {
            material = Material.matchMaterial(key.toUpperCase(Locale.ROOT));
        }
        if (material == null || material.isAir()) {
            return new ItemStack(Material.BARRIER);
        }
        return new ItemStack(material);
    }
}
