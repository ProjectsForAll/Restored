package gg.drak.restored.items;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public final class NetworkChestItem {

    private NetworkChestItem() {
    }

    public static ItemStack create() {
        return create(null);
    }

    public static ItemStack create(UUID networkId) {
        ItemStack item = RestoredItems.tagged(
                Material.CHEST,
                RestoredItems.TYPE_CHEST,
                "#FFED6A&lNetwork Chest",
                "#bdc8c9Place to create or move a network.",
                "#AAAAAABase capacity: 64 items."
        );
        if (networkId != null) {
            return RestoredItems.withNetworkId(item, networkId);
        }
        return item;
    }

    public static boolean isType(ItemStack stack) {
        return RestoredItems.isType(stack, RestoredItems.TYPE_CHEST);
    }

    public static UUID getNetworkId(ItemStack stack) {
        return RestoredItems.getNetworkId(stack).orElse(null);
    }
}
