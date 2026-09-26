package gg.drak.restored.items;

import gg.drak.restored.data.NetworkHopperRole;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public final class NetworkHopperItem {

    private NetworkHopperItem() {
    }

    public static ItemStack create(NetworkHopperRole role) {
        return create(role, null);
    }

    public static ItemStack create(NetworkHopperRole role, UUID networkId) {
        if (role == null) {
            throw new IllegalArgumentException("role");
        }
        String name = role == NetworkHopperRole.INPUT ? "Network Hopper (Input)" : "Network Hopper (Output)";
        String[] lore = role == NetworkHopperRole.INPUT
                ? new String[]{
                "#bdc8c9Place as a chest and link it to a network.",
                "#AAAAAAItems placed inside go directly into the network."
        }
                : new String[]{
                "#bdc8c9Place as a chest and link it to a network.",
                "#AAAAAAShift-right-click to configure withdrawals."
        };
        String type = role == NetworkHopperRole.INPUT
                ? RestoredItems.TYPE_NETWORK_HOPPER_INPUT
                : RestoredItems.TYPE_NETWORK_HOPPER_OUTPUT;
        ItemStack item = RestoredItems.tagged(Material.CHEST, type, "#FFED6A&l" + name, lore);
        return networkId == null ? item : RestoredItems.withNetworkId(item, networkId);
    }

    public static boolean isType(ItemStack stack, NetworkHopperRole role) {
        if (role == null) {
            return false;
        }
        return RestoredItems.isType(stack, role == NetworkHopperRole.INPUT
                ? RestoredItems.TYPE_NETWORK_HOPPER_INPUT
                : RestoredItems.TYPE_NETWORK_HOPPER_OUTPUT);
    }

    public static NetworkHopperRole getRole(ItemStack stack) {
        if (RestoredItems.isType(stack, RestoredItems.TYPE_NETWORK_HOPPER_INPUT)) {
            return NetworkHopperRole.INPUT;
        }
        if (RestoredItems.isType(stack, RestoredItems.TYPE_NETWORK_HOPPER_OUTPUT)) {
            return NetworkHopperRole.OUTPUT;
        }
        return null;
    }

    public static UUID getNetworkId(ItemStack stack) {
        return RestoredItems.getNetworkId(stack).orElse(null);
    }
}
