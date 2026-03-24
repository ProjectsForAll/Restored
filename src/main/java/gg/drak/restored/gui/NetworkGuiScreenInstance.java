package gg.drak.restored.gui;

import host.plas.bou.gui.GuiType;
import host.plas.bou.gui.InventorySheet;
import host.plas.bou.gui.screens.ScreenInstance;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.IntPredicate;

/**
 * Screen that keeps {@code noPlace} for most top slots but allows vanilla placement into
 * slots matched by {@code allowTopPlaceSlot} (e.g. crafting grid in {@link gg.drak.restored.data.blocks.impl.CraftingViewer}).
 */
public class NetworkGuiScreenInstance extends ScreenInstance {

    private final IntPredicate allowTopPlaceSlot;

    public NetworkGuiScreenInstance(@NotNull Player player, @NotNull GuiType type,
                                    @NotNull InventorySheet inventorySheet,
                                    @Nullable IntPredicate allowTopPlaceSlot) {
        super(player, type, inventorySheet, true);
        this.allowTopPlaceSlot = allowTopPlaceSlot;
    }

    @Override
    public boolean onClick(InventoryClickEvent event) {
        if (! (event.getWhoClicked() instanceof Player)) return false;
        Player p = (Player) event.getWhoClicked();

        ItemStack cursor = event.getCursor();

        Inventory clickedInventory = event.getClickedInventory();
        Inventory playerInventory = p.getInventory();
        if (clickedInventory == null || playerInventory == null) return false;

        boolean isPlace = false;
        InventoryAction action = event.getAction();

        if (clickedInventory.equals(playerInventory)) {
            if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                isPlace = true;
            }
        } else {

            if (
                    action == InventoryAction.DROP_ALL_CURSOR || action == InventoryAction.DROP_ONE_CURSOR ||
                            action == InventoryAction.PLACE_ALL || action == InventoryAction.PLACE_ONE ||
                            action == InventoryAction.PLACE_SOME
            ) {
                isPlace = true;
            }

            if (! isPlace) {
                if (cursor.getType() != Material.AIR) {
                    if (action == InventoryAction.SWAP_WITH_CURSOR) {
                        isPlace = true;
                    }
                }
            }
        }

        boolean bypassNoPlace = allowTopPlaceSlot != null
                && clickedInventory.equals(getInventory())
                && allowTopPlaceSlot.test(event.getSlot());

        if (isPlace && isNoPlace() && ! bypassNoPlace) {
            event.setCancelled(true);
            return false;
        }

        return furtherClick(event);
    }
}
