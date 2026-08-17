package gg.drak.restored.items;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public final class NetworkComponentItem {

    private NetworkComponentItem() {
    }

    public static ItemStack create() {
        return RestoredItems.tagged(
                Material.IRON_INGOT,
                RestoredItems.TYPE_COMPONENT,
                "#FFED6A&lNetwork Component",
                "#bdc8c9Used to craft Network Upgrades.",
                "#AAAAAACombine with redstone and gold."
        );
    }

    public static boolean isType(ItemStack stack) {
        return RestoredItems.isType(stack, RestoredItems.TYPE_COMPONENT);
    }
}
