package gg.drak.restored.events;

import host.plas.bou.gui.ScreenManager;
import host.plas.bou.gui.screens.ScreenInstance;
import gg.drak.restored.Restored;
import gg.drak.restored.data.NetworkManager;
import gg.drak.restored.data.blocks.NetworkBlock;
import gg.drak.restored.data.blocks.impl.CraftingViewer;
import gg.drak.restored.data.blocks.impl.Drive;
import gg.drak.restored.data.blocks.impl.Viewer;
import gg.drak.restored.data.blocks.inventory.InventoryBlock;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainListener implements Listener {
    public MainListener() {
        Restored.getInstance().registerListener(this);

        Restored.getInstance().logInfo("Registered MainListener!");
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        NetworkManager.onBreakBlock(event);
    }

    @EventHandler
    public void onBlockClick(PlayerInteractEvent event) {
        NetworkManager.onBlockClick(event);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        NetworkManager.onBlockPlace(event);
    }

    /**
     * Runs at {@link EventPriority#HIGHEST} so Obliviate/ScreenInstance can mark the click cancelled first;
     * we still apply network insert logic and cursor updates manually.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInsertItem(InventoryClickEvent event) {
        List<HumanEntity> viewers = event.getViewers();
        if (viewers.isEmpty()) return;

        HumanEntity viewer = viewers.get(0);
        for (int i = 0; i < viewers.size(); i++) {
            viewer = viewers.get(i);
            if (viewer instanceof Player) {
                break;
            }
        }

        if (! (viewer instanceof Player)) return;
        Player player = (Player) viewer;

        if (! ScreenManager.hasScreen(player)) {
            return;
        }
        ScreenInstance screen = ScreenManager.getScreen(player).get(); // not null

        InventoryAction action = event.getAction();

        // Handle placing items into the network inventory
        if (action == InventoryAction.PLACE_ONE || action == InventoryAction.PLACE_SOME || action == InventoryAction.PLACE_ALL || action == InventoryAction.SWAP_WITH_CURSOR) {
            Inventory inventory = event.getClickedInventory();
            if (inventory == null) return;

            // If clicking in the top inventory (the network screen)
            if (screen.getInventory().equals(inventory)) {
                int slot = event.getRawSlot();
                if (shouldSkipManualDeposit(screen, slot)) {
                    return;
                }
                ItemStack cur = event.getCursor();
                if (cur == null || cur.getType().isAir()) {
                    return;
                }
                if (handlePutItem(event, PutType.CURSOR_PLACE, action)) {
                    event.setCancelled(true);
                }
                return;
            }
        }

        // Handle shift-clicking from player inventory into network inventory
        ClickType type = event.getClick();
        if (type == ClickType.SHIFT_LEFT || type == ClickType.SHIFT_RIGHT) {
            Inventory inventory = event.getClickedInventory();
            if (inventory == null) return;

            // If clicking in the bottom inventory (player inventory) and top is a network screen
            if (! screen.getInventory().equals(inventory)) {
                if (screen.getScreenBlock().orElse(null) instanceof CraftingViewer) {
                    return;
                }
                if (handlePutItem(event, PutType.SHIFT_CLICK_FROM_OWN, action)) {
                    event.setCancelled(true);
                }
                return;
            } else {
                // If shift-clicking from the network screen, we should allow it (it will be handled by the library/Icons)
                return;
            }
        }
    }

    /**
     * Obliviate/noPlace GUIs cancel drags by default; allow drags that only touch the crafting grid.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDragIntoCrafting(InventoryDragEvent event) {
        if (! (event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        if (! ScreenManager.hasScreen(player)) return;

        ScreenInstance screen = ScreenManager.getScreen(player).get();
        if (! (screen.getScreenBlock().orElse(null) instanceof CraftingViewer)) return;

        int topSize = event.getView().getTopInventory().getSize();
        boolean touchesTop = false;
        for (int raw : event.getRawSlots()) {
            if (raw < topSize) {
                touchesTop = true;
                if (! CraftingViewer.isCraftingSlot(raw)) {
                    return;
                }
            }
        }
        if (touchesTop) {
            event.setCancelled(false);
        }
    }

    /**
     * Crafting grid must use vanilla placement; viewer pagination row is UI-only.
     */
    private static boolean shouldSkipManualDeposit(ScreenInstance screen, int rawSlot) {
        return screen.getScreenBlock().map(block -> {
            if (block instanceof CraftingViewer) {
                return CraftingViewer.isCraftingSlot(rawSlot);
            }
            if (block instanceof Viewer) {
                return rawSlot >= 45;
            }
            return false;
        }).orElse(false);
    }

    public static boolean handlePutItem(InventoryClickEvent event, PutType type, InventoryAction action) {
        AtomicBoolean handled = new AtomicBoolean(false);
        ConcurrentSkipListMap<Integer, Player> viewers = new ConcurrentSkipListMap<>();
        event.getViewers().forEach(viewer -> {
            if (! (viewer instanceof Player)) return;
            Player player = (Player) viewer;
            viewers.put(viewers.size(), player);
        });

        viewers.forEach((i, player) -> {
            try {
                ScreenManager.getScreen(player).flatMap(ScreenInstance::getScreenBlock).ifPresent(block -> {
                    try {
                        if (! (block instanceof NetworkBlock)) {
                            return;
                        }

                        NetworkBlock networkBlock = (NetworkBlock) block;
                        InventoryBlock invBlock = null;
                        if (networkBlock instanceof Drive) {
                            invBlock = (Drive) networkBlock;
                        } else if (networkBlock instanceof Viewer) {
                            invBlock = (Viewer) networkBlock;
                        } else if (networkBlock instanceof CraftingViewer) {
                            invBlock = (CraftingViewer) networkBlock;
                        } else if (networkBlock instanceof InventoryBlock) {
                            invBlock = (InventoryBlock) networkBlock;
                        }

                        if (invBlock == null) {
                            return;
                        }
                        switch (type) {
                            case CURSOR_PLACE:
                                ItemStack cursor = event.getCursor();
                                if (cursor == null || cursor.getType().isAir()) return;

                                ItemStack toInsert = cursor.clone();
                                if (action == InventoryAction.PLACE_ONE) {
                                    toInsert.setAmount(1);
                                }

                                ItemStack afterInsert = invBlock.tryAddItem(toInsert);
                                int leftoverOnAttempt = afterInsert != null ? afterInsert.getAmount() : 0;
                                int inserted = toInsert.getAmount() - leftoverOnAttempt;
                                if (inserted <= 0) {
                                    return;
                                }
                                handled.set(true);
                                int newCursorAmount = cursor.getAmount() - inserted;
                                if (newCursorAmount <= 0) {
                                    event.setCursor(null);
                                } else {
                                    ItemStack nextCursor = cursor.clone();
                                    nextCursor.setAmount(newCursorAmount);
                                    event.setCursor(nextCursor);
                                }
                                break;
                            case SHIFT_CLICK_FROM_OWN:
                                ItemStack itemToShift = event.getCurrentItem();
                                if (itemToShift == null || itemToShift.getType().isAir()) return;

                                ItemStack shiftLeft = itemToShift.clone();

                                while (shiftLeft != null && shiftLeft.getAmount() > 0) {
                                    int beforeAmount = shiftLeft.getAmount();
                                    shiftLeft = invBlock.tryAddItem(shiftLeft);
                                    if (shiftLeft != null && shiftLeft.getAmount() == beforeAmount) {
                                        break;
                                    }
                                    handled.set(true);
                                }

                                if (shiftLeft == null || shiftLeft.getAmount() == 0) {
                                    event.setCurrentItem(null);
                                } else {
                                    event.setCurrentItem(shiftLeft);
                                }
                                break;
                        }
                    } catch (Throwable e) {
                        Restored.getInstance().logSevere("Error while handling put item event [1]: " + e.getMessage(), e);
                    }
                });
            } catch (Throwable e) {
                Restored.getInstance().logSevere("Error while handling put item event [2]: " + e.getMessage(), e);
            }
        });
        return handled.get();
    }

    public enum PutType {
        SHIFT_CLICK_FROM_OWN,
        CURSOR_PLACE,
        ;
    }
}
