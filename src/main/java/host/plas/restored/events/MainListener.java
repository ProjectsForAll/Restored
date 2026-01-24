package host.plas.restored.events;

import host.plas.bou.gui.ScreenManager;
import host.plas.bou.gui.screens.ScreenInstance;
import host.plas.restored.Restored;
import host.plas.restored.data.NetworkManager;
import host.plas.restored.data.blocks.NetworkBlock;
import host.plas.restored.data.blocks.impl.Drive;
import host.plas.restored.data.blocks.impl.Viewer;
import host.plas.restored.data.blocks.inventory.InventoryBlock;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.concurrent.ConcurrentSkipListMap;

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

    @EventHandler
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
            Restored.getInstance().logInfo("Player does not have a screen!");
            return;
        }
        ScreenInstance screen = ScreenManager.getScreen(player).get(); // not null

        InventoryAction action = event.getAction();
        if (action == InventoryAction.PLACE_ONE || action == InventoryAction.PLACE_SOME || action == InventoryAction.PLACE_ALL) {
            Inventory inventory = event.getClickedInventory();
            if (inventory == null) return;
            if (screen.getInventory() != inventory) return;

            handlePutItem(event, PutType.PLACE_ONE);
        }
        ClickType type = event.getClick();
        if (action == InventoryAction.PICKUP_ONE || action == InventoryAction.PICKUP_SOME || action == InventoryAction.PICKUP_HALF ||
                action == InventoryAction.PICKUP_ALL || action == InventoryAction.MOVE_TO_OTHER_INVENTORY || action == InventoryAction.HOTBAR_SWAP ||
                action == InventoryAction.SWAP_WITH_CURSOR) {
            if (type == ClickType.SHIFT_LEFT || type == ClickType.SHIFT_RIGHT) {
                Inventory inventory = event.getClickedInventory();
                if (inventory == null) return;
                if (screen.getInventory() == inventory) {

                } else {
                    handlePutItem(event, PutType.SHIFT_CLICK_FROM_OWN);
                }
            }
        }
    }

    public static void handlePutItem(InventoryClickEvent event, PutType type) {
        Restored.getInstance().logInfo("Handling put item event...");

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
                        Restored.getInstance().logInfo("Block is a screen block!");

                        if (! (block instanceof NetworkBlock)) {
                            Restored.getInstance().logInfo("Block is not a network block!");
                            return;
                        }

                        NetworkBlock networkBlock = (NetworkBlock) block;
                        InventoryBlock invBlock = null;
                        if (networkBlock instanceof Drive) {
                            invBlock = (Drive) networkBlock;

                            Restored.getInstance().logInfo("Block is a drive!");
                        } else if (networkBlock instanceof Viewer) {
                            invBlock = (Viewer) networkBlock;

                            Restored.getInstance().logInfo("Block is a viewer!");
                        } else if (networkBlock instanceof InventoryBlock) {
                            invBlock = (InventoryBlock) networkBlock;
                        }

                        if (invBlock == null) {
                            Restored.getInstance().logInfo("Block is not an inventory block!");
                            return;
                        } else {
                            Restored.getInstance().logInfo("Block is an inventory block!");

                            switch (type) {
                                case PLACE_ONE:
                                    Restored.getInstance().logInfo("Placing one...");

                                    ItemStack placeLeft = invBlock.tryAddItem(event.getCursor());
                                    if (placeLeft == null) {
                                        return;
                                    }
                                    event.setCursor(placeLeft);
                                    break;
                                case SHIFT_CLICK_FROM_OWN:
                                    Restored.getInstance().logInfo("Shift clicking from own inventory...");

                                    ItemStack shiftLeft = invBlock.tryAddItem(event.getCurrentItem());
                                    if (shiftLeft == null) {
                                        return;
                                    }
                                    event.setCurrentItem(shiftLeft);
                                    break;
                            }
                        }
                    } catch (Throwable e) {
                        Restored.getInstance().logSevere("Error while handling put item event [1]: " + e.getMessage(), e);
                    }
                });
            } catch (Throwable e) {
                Restored.getInstance().logSevere("Error while handling put item event [2]: " + e.getMessage(), e);
            }
        });
    }

    public  enum PutType {
        SHIFT_CLICK_FROM_OWN,
        PLACE_ONE,
        ;
    }
}
