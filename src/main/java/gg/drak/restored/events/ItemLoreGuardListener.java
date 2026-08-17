package gg.drak.restored.events;

import gg.drak.restored.Restored;
import gg.drak.restored.items.RestoredItems;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Reverts lore changes made by other plugins on Restored custom items.
 */
public class ItemLoreGuardListener implements Listener {

    public ItemLoreGuardListener() {
        Restored.getInstance().registerListener(this);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        RestoredItems.protectInventory(event.getPlayer().getInventory());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        RestoredItems.protectInventory(player.getInventory());
        RestoredItems.protectInventory(event.getInventory());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        RestoredItems.restoreLoreIfTampered(event.getCurrentItem());
        RestoredItems.restoreLoreIfTampered(event.getCursor());
        if (event.getWhoClicked() instanceof Player player) {
            RestoredItems.restoreLoreIfTampered(player.getInventory().getItemInMainHand());
            RestoredItems.restoreLoreIfTampered(player.getInventory().getItemInOffHand());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        for (ItemStack stack : event.getNewItems().values()) {
            RestoredItems.restoreLoreIfTampered(stack);
        }
        RestoredItems.restoreLoreIfTampered(event.getCursor());
        RestoredItems.restoreLoreIfTampered(event.getOldCursor());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemHeld(PlayerItemHeldEvent event) {
        ItemStack stack = event.getPlayer().getInventory().getItem(event.getNewSlot());
        RestoredItems.restoreLoreIfTampered(stack);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        RestoredItems.restoreLoreIfTampered(event.getItem().getItemStack());
    }
}
