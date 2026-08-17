package gg.drak.restored.items;

import gg.drak.restored.Restored;
import gg.drak.restored.util.LegacyColors;
import host.plas.bou.items.ItemUtils;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Optional;
import java.util.UUID;

public final class ChestLinkingToolItem {

    private ChestLinkingToolItem() {
    }

    public static ItemStack create() {
        ItemStack item = RestoredItems.tagged(
                Material.IRON_SHOVEL,
                RestoredItems.TYPE_CHEST_LINKING_TOOL,
                "#FFED6A&lChest Linking Tool",
                "#bdc8c9Shift-right-click a network chest to bind.",
                "#FF5555Not bound.",
                "#bdc8c9Right-click a chest to link it as storage.",
                "#AAAAAALinked chests are preferred for inserts."
        );
        return item;
    }

    public static boolean isType(ItemStack stack) {
        return RestoredItems.isType(stack, RestoredItems.TYPE_CHEST_LINKING_TOOL);
    }

    public static Optional<UUID> getLinkedNetworkId(ItemStack stack) {
        return RestoredItems.getNetworkId(stack);
    }

    public static void linkNetwork(ItemStack stack, UUID networkId) {
        ItemUtils.setTag(stack, Restored.getInstance(), RestoredItems.TAG_NETWORK_ID, networkId.toString());
        refreshLore(stack);
    }

    public static void unlinkNetwork(ItemStack stack) {
        RestoredItems.clearNetworkId(stack);
        ItemUtils.setTag(stack, Restored.getInstance(), RestoredItems.TAG_NETWORK_ID, "");
        refreshLore(stack);
    }

    public static void refreshLore(ItemStack stack) {
        if (!isType(stack)) {
            return;
        }
        Optional<UUID> linked = getLinkedNetworkId(stack);
        String[] lore = linked.isPresent()
                ? new String[]{
                LegacyColors.color("#bdc8c9Shift-right-click a network chest to unbind."),
                LegacyColors.color("#00FC88Bound: #AAAAAA" + linked.get()),
                LegacyColors.color("#bdc8c9Right-click a chest to link/unlink storage."),
                LegacyColors.color("#AAAAAALinked chests are preferred for inserts.")
        }
                : new String[]{
                LegacyColors.color("#bdc8c9Shift-right-click a network chest to bind."),
                LegacyColors.color("#FF5555Not bound."),
                LegacyColors.color("#bdc8c9Right-click a chest to link it as storage."),
                LegacyColors.color("#AAAAAALinked chests are preferred for inserts.")
        };
        RestoredItems.storeCanonicalLore(stack, lore);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null && !meta.hasDisplayName()) {
            meta.setDisplayName(LegacyColors.color("#FFED6A&lChest Linking Tool"));
            stack.setItemMeta(meta);
        }
    }
}
