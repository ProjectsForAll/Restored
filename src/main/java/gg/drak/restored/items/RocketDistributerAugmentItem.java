package gg.drak.restored.items;

import gg.drak.restored.data.PocketAugmentType;
import org.bukkit.inventory.ItemStack;

/** The craftable item installed into a Pocket Link. */
public final class RocketDistributerAugmentItem {

    private RocketDistributerAugmentItem() {
    }

    public static ItemStack create() {
        return PocketAugmentItem.create(PocketAugmentType.ROCKET_DISTRIBUTER);
    }

    public static boolean isType(ItemStack stack) {
        return RestoredItems.isType(stack, PocketAugmentType.ROCKET_DISTRIBUTER.itemTypeTag());
    }
}
