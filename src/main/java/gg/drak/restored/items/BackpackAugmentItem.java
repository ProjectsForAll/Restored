package gg.drak.restored.items;

import gg.drak.restored.data.PocketAugmentType;
import org.bukkit.inventory.ItemStack;

public final class BackpackAugmentItem {

    private BackpackAugmentItem() {
    }

    public static ItemStack create() {
        return PocketAugmentItem.create(PocketAugmentType.BACKPACK);
    }

    public static boolean isType(ItemStack stack) {
        return RestoredItems.isType(stack, PocketAugmentType.BACKPACK.itemTypeTag());
    }
}
