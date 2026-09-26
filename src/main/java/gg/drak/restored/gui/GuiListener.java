package gg.drak.restored.gui;

import gg.drak.restored.Restored;
import gg.drak.restored.gui.pocket.PocketLinkGuiBound;
import gg.drak.restored.items.PocketLinkItem;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class GuiListener implements Listener {

    public GuiListener() {
        Restored.getInstance().registerListener(this);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof AbstractInventoryGui gui)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player) || !player.equals(gui.getPlayer())) {
            event.setCancelled(true);
            return;
        }

        // Deposit chest is a free-form buffer — do not block vanilla placement.
        if (!(gui instanceof NetworkDepositGui) && !(gui instanceof gg.drak.restored.gui.pocket.BackpackAugmentGui)) {
            blockUnsafeGuiPlacement(event);
        }

        gui.handleClick(event);
    }

    /**
     * Prevents vanilla from dumping/swapping items into decoration slots (black panes, etc.).
     * Interactive GUIs still run their own {@code handleClick} logic on the cancelled event.
     */
    private static void blockUnsafeGuiPlacement(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        Inventory clicked = event.getClickedInventory();

        if (clicked == null) {
            // Outside / unknown click — block so top inventory cannot be mutated.
            deny(event);
            return;
        }

        if (clicked.equals(top)) {
            deny(event);
            return;
        }

        // Bottom-inventory actions that can move items into the top GUI.
        ClickType click = event.getClick();
        if (click == ClickType.SHIFT_LEFT
                || click == ClickType.SHIFT_RIGHT
                || click == ClickType.DOUBLE_CLICK
                || click == ClickType.UNKNOWN) {
            deny(event);
        }
    }

    private static void deny(InventoryClickEvent event) {
        event.setCancelled(true);
        event.setResult(Event.Result.DENY);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof AbstractInventoryGui gui)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player) || !player.equals(gui.getPlayer())) {
            event.setCancelled(true);
            return;
        }
        gui.handleDrag(event);
    }

    /**
     * Frees any linked-chunk tickets a disconnecting player still holds. Without this a player
     * who quits while browsing a network remotely would pin its chests' chunks indefinitely.
     */
    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        gg.drak.restored.util.LinkedChestStorage.releaseLease(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof AbstractInventoryGui gui)) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player) || !player.equals(gui.getPlayer())) {
            return;
        }
        gui.handleClose(event);
        if (gui instanceof PocketLinkGuiBound bound) {
            Bukkit.getScheduler().runTask(Restored.getInstance(), () -> {
                if (!player.isOnline()) {
                    PocketLinkItem.clearGuiOpenIf(player, bound.getPocketLinkId());
                    return;
                }
                InventoryHolder current = player.getOpenInventory().getTopInventory().getHolder();
                if (!(current instanceof PocketLinkGuiBound currentBound)
                        || !bound.getPocketLinkId().equals(currentBound.getPocketLinkId())) {
                    PocketLinkItem.clearGuiOpenIf(player, bound.getPocketLinkId());
                }
            });
        }
    }
}
