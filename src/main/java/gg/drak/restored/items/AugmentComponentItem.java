package gg.drak.restored.items;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public final class AugmentComponentItem {

    private AugmentComponentItem() {
    }

    public static ItemStack create() {
        return RestoredItems.tagged(
                Material.QUARTZ,
                RestoredItems.TYPE_AUGMENT_COMPONENT,
                "#FFED6A&lAugment Component",
                "#bdc8c9Craft with a workstation to make an augment.",
                "#AAAAAAUsed for network workstation augments."
        );
    }

    public static boolean isType(ItemStack stack) {
        return RestoredItems.isType(stack, RestoredItems.TYPE_AUGMENT_COMPONENT);
    }
}
