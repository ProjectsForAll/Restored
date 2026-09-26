package gg.drak.restored.items;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public final class MagnetCoreItem {

    private MagnetCoreItem() {
    }

    public static ItemStack create() {
        return RestoredItems.tagged(
                Material.IRON_INGOT,
                RestoredItems.TYPE_MAGNET_CORE,
                "#FFED6A&lMagnet Core",
                "#bdc8c9Core component for a Magnet Pocket Augment.",
                "#AAAAAAAttracts nearby item entities."
        );
    }

    public static boolean isType(ItemStack stack) {
        return RestoredItems.isType(stack, RestoredItems.TYPE_MAGNET_CORE);
    }
}
