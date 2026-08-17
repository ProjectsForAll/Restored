package gg.drak.restored.items;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public final class NetworkUpgradeItem {

    private NetworkUpgradeItem() {
    }

    public static ItemStack create() {
        return RestoredItems.tagged(
                Material.GOLD_INGOT,
                RestoredItems.TYPE_UPGRADE,
                "#FFED6A&lNetwork Upgrade",
                "#bdc8c9Left-click a network chest to apply.",
                "#bdc8c9Shift-left-click a chest to remove one.",
                "#AAAAAAAdds 64 item capacity."
        );
    }

    public static boolean isType(ItemStack stack) {
        return RestoredItems.isType(stack, RestoredItems.TYPE_UPGRADE);
    }
}
