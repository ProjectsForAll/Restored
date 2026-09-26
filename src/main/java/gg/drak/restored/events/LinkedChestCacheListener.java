package gg.drak.restored.events;

import gg.drak.restored.Restored;
import gg.drak.restored.util.LinkedChestCache;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.Inventory;

import java.util.List;

/**
 * Keeps {@link LinkedChestCache} in step with the world.
 *
 * <p>Inventory events fire before the change they describe is applied, so these handlers only
 * mark the chest dirty; the snapshot is rebuilt on the next read, after the change. Every
 * handler bails out on a single map lookup for chests that are not linked, since hopper move
 * events fire constantly across the whole server.
 */
public class LinkedChestCacheListener implements Listener {

    public LinkedChestCacheListener() {
        Restored.getInstance().registerListener(this);
        Restored.getInstance().logInfo("Registered LinkedChestCacheListener!");
    }

    private static void markDirty(Inventory inventory) {
        if (inventory != null) {
            // getLocation() reads the tile entity position; getHolder() would copy the block state.
            LinkedChestCache.markDirtyAt(inventory.getLocation());
        }
    }

    private static void markDirty(List<Block> blocks) {
        for (Block block : blocks) {
            LinkedChestCache.markDirtyAt(block.getLocation());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(InventoryMoveItemEvent event) {
        markDirty(event.getSource());
        markDirty(event.getDestination());
    }

    /** Opening a linked chest refreshes its snapshot, catching changes no event reported. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        markDirty(event.getView().getTopInventory());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        markDirty(event.getView().getTopInventory());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        markDirty(event.getView().getTopInventory());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        markDirty(event.getView().getTopInventory());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        LinkedChestCache.markDirtyAt(event.getBlock().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        markDirty(event.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        markDirty(event.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        LinkedChestCache.onChunkLoad(event.getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        LinkedChestCache.onChunkUnload(event.getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent event) {
        LinkedChestCache.onWorldLoad(event.getWorld());
    }
}
