package gg.drak.restored.items;

import gg.drak.restored.data.AugmentType;
import org.bukkit.inventory.ItemStack;

public final class NetworkAugmentItem {

    private NetworkAugmentItem() {
    }

    public static ItemStack create(AugmentType type) {
        if (type == null) {
            throw new IllegalArgumentException("type");
        }
        return RestoredItems.tagged(
                type.getWorkstationMaterial(),
                type.itemTypeTag(),
                "#FFED6A&l" + type.getDisplayName() + " Augment",
                "#bdc8c9Install in a network's Augments GUI.",
                "#AAAAAAUnlocks the " + type.getDisplayName() + " workstation."
        );
    }

    public static boolean isType(ItemStack stack) {
        return getType(stack) != null;
    }

    public static AugmentType getType(ItemStack stack) {
        return RestoredItems.getType(stack)
                .map(AugmentType::fromItemTypeTag)
                .orElse(null);
    }

    public static boolean isType(ItemStack stack, AugmentType type) {
        return type != null && RestoredItems.isType(stack, type.itemTypeTag());
    }
}
