package gg.drak.restored.items;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public final class NetworkCoreItem {

    private NetworkCoreItem() {
    }

    public static ItemStack create() {
        return RestoredItems.tagged(
                Material.NETHER_STAR,
                RestoredItems.TYPE_CORE,
                "#FFED6A&lNetwork Core",
                "#bdc8c9Craft into a Network Chest.",
                "#AAAAAACentral component for storage networks."
        );
    }

    public static boolean isType(ItemStack stack) {
        return RestoredItems.isType(stack, RestoredItems.TYPE_CORE);
    }
}
