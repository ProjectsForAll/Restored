package gg.drak.restored.items;

import gg.drak.restored.data.PocketAugmentType;
import org.bukkit.inventory.ItemStack;

public final class FeedingAugmentItem {

    private FeedingAugmentItem() {
    }

    public static ItemStack create() {
        return RestoredItems.tagged(
                PocketAugmentType.FEEDING.getIcon(),
                PocketAugmentType.FEEDING.itemTypeTag(),
                "#FFED6A&lFeeding Augment",
                "#bdc8c9Install in a Pocket Link's Pocket Augments GUI.",
                "#AAAAAAAuto-feeds you from the linked network."
        );
    }

    public static boolean isType(ItemStack stack) {
        return RestoredItems.isType(stack, PocketAugmentType.FEEDING.itemTypeTag());
    }
}
