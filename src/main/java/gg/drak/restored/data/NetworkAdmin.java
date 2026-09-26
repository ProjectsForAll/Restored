package gg.drak.restored.data;

import gg.drak.restored.Restored;
import gg.drak.restored.gui.AbstractInventoryGui;
import gg.drak.restored.util.NetworkBlockTags;
import gg.drak.restored.util.PlatformScheduler;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;

import java.lang.reflect.Field;

/** Administrative network operations that bypass the normal ownership and emptiness rules. */
public final class NetworkAdmin {

    private NetworkAdmin() {
    }

    /**
     * Force-deletes a network. Virtual storage, workstation contents, upgrades and augments are
     * destroyed with it; linked chests, barrels and hoppers keep their contents and are only
     * unlinked. Players viewing the network are closed out first so nothing can be deposited into
     * a network that no longer exists.
     *
     * <p>The network chest block itself is left to the caller: a block-break deletion is already
     * removing it, while {@link #deleteAndRemoveBlock(Network)} clears it from the world.
     */
    public static void delete(Network network) {
        if (network == null) {
            return;
        }
        closeViewers(network);
        network.delete();
    }

    /** {@link #delete(Network)}, then removes the placed network chest block, if any. */
    public static void deleteAndRemoveBlock(Network network) {
        if (network == null) {
            return;
        }
        Location location = network.getLocation();
        delete(network);
        if (location == null) {
            return;
        }
        // The chunk may be far away and unloaded; load it without stalling this thread.
        PlatformScheduler.loadChunk(location).whenComplete((ignored, error) ->
                PlatformScheduler.runAtLocation(location, () -> {
                    Block block = location.getBlock();
                    if (block.getType() != Material.CHEST) {
                        return;
                    }
                    NetworkBlockTags.clearNetworkId(block);
                    if (block.getState() instanceof org.bukkit.block.Container container) {
                        // Never destroy stray items that ended up in the chest block itself.
                        for (org.bukkit.inventory.ItemStack stack : container.getInventory().getContents()) {
                            if (stack != null && !stack.getType().isAir()) {
                                block.getWorld().dropItemNaturally(location, stack);
                            }
                        }
                        container.getInventory().clear();
                    }
                    block.setType(Material.AIR);
                }));
    }

    /** Closes the open Restored menu of every player currently viewing {@code network}. */
    private static void closeViewers(Network network) {
        for (Player player : Restored.getInstance().getServer().getOnlinePlayers()) {
            InventoryHolder holder = player.getOpenInventory().getTopInventory().getHolder(false);
            if (holder instanceof AbstractInventoryGui gui && refersTo(gui, network)) {
                player.closeInventory();
            }
        }
    }

    /**
     * Network menus hold their network in a field; there is no common accessor across them, so
     * the fields are inspected directly. This only runs on an explicit admin deletion.
     */
    private static boolean refersTo(Object gui, Network network) {
        for (Class<?> type = gui.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (!Network.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    if (field.get(gui) == network) {
                        return true;
                    }
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // Unreadable field: fall through to the next one.
                }
            }
        }
        return false;
    }
}
