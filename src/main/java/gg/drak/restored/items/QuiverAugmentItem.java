package gg.drak.restored.items;

import gg.drak.restored.data.PocketAugmentType;
import org.bukkit.inventory.ItemStack;

public final class QuiverAugmentItem {

    private QuiverAugmentItem() {
    }

    public static ItemStack create() {
        return PocketAugmentItem.create(PocketAugmentType.QUIVER);
    }

    public static boolean isType(ItemStack stack) {
        return RestoredItems.isType(stack, PocketAugmentType.QUIVER.itemTypeTag());
    }
}
