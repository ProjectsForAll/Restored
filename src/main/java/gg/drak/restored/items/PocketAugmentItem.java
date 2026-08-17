package gg.drak.restored.items;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public final class PocketAugmentItem {

    private PocketAugmentItem() {
    }

    public static ItemStack create() {
        return RestoredItems.tagged(
                Material.BUNDLE,
                RestoredItems.TYPE_POCKET_AUGMENT,
                "#FFED6A&lPocket Augment",
                "#bdc8c9Craft with food items to make a Feeding Augment.",
                "#AAAAAABase component for Pocket Link augments."
        );
    }

    public static ItemStack create(gg.drak.restored.data.PocketAugmentType type) {
        if (type == null) {
            throw new IllegalArgumentException("type");
        }
        return RestoredItems.tagged(
                type.getIcon(),
                type.itemTypeTag(),
                "#FFED6A&l" + type.getDisplayName() + " Augment",
                "#bdc8c9Install in a Pocket Link's Pocket Augments GUI.",
                type == gg.drak.restored.data.PocketAugmentType.QUIVER
                        ? "#AAAAAAUses arrows from the linked network."
                        : "#AAAAAAAdds storage tied to this Pocket Link."
        );
    }

    public static boolean isType(ItemStack stack) {
        return RestoredItems.isType(stack, RestoredItems.TYPE_POCKET_AUGMENT);
    }
}
