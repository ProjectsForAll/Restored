package gg.drak.restored.items;

import gg.drak.restored.data.PocketAugmentType;
import org.bukkit.inventory.ItemStack;

public final class MagnetPocketAugmentItem {

    private MagnetPocketAugmentItem() {
    }

    public static ItemStack create() {
        return PocketAugmentItem.create(PocketAugmentType.MAGNET);
    }

    public static boolean isType(ItemStack stack) {
        return RestoredItems.isType(stack, PocketAugmentType.MAGNET.itemTypeTag());
    }
}
