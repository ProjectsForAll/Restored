package gg.drak.restored.gui;

import gg.drak.restored.data.blocks.impl.CraftingViewer;
import gg.drak.restored.data.blocks.impl.Viewer;
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

import java.util.function.Function;
import java.util.function.IntPredicate;

/**
 * Screen that keeps {@code noPlace} for most top slots but allows vanilla placement into
 * slots matched by {@code allowTopPlaceSlot} (e.g. crafting grid in {@link CraftingViewer}).
 * Network deposits (shift-click / cursor) are handled by {@link gg.drak.restored.events.MainListener}
 * at {@link org.bukkit.event.EventPriority#HIGHEST} so player inventory updates apply correctly.
 */
public class NetworkGuiScreenInstance extends ScreenInstance {

    private final IntPredicate allowTopPlaceSlot;
    private final Function<ItemStack, ItemStack> depositHandler;

    public NetworkGuiScreenInstance(@NotNull Player player, @NotNull GuiType type,
                                    @NotNull InventorySheet inventorySheet,
                                    @Nullable IntPredicate allowTopPlaceSlot) {
        this(player, type, inventorySheet, allowTopPlaceSlot, null);
    }

    public NetworkGuiScreenInstance(@NotNull Player player, @NotNull GuiType type,
                                    @NotNull InventorySheet inventorySheet,
                                    @Nullable IntPredicate allowTopPlaceSlot,
                                    @Nullable Function<ItemStack, ItemStack> depositHandler) {
        super(player, type, inventorySheet, true);
        this.allowTopPlaceSlot = allowTopPlaceSlot;
        this.depositHandler = depositHandler;
    }

    /**
     * Slots that accept vanilla placement (crafting grid, filter slots, etc.).
     */
    public boolean isVanillaPlaceSlot(int rawSlot) {
        return allowTopPlaceSlot != null && allowTopPlaceSlot.test(rawSlot);
    }

    public boolean hasDepositHandler() {
        return depositHandler != null;
    }

    /**
     * Top-inventory slots where cursor/shift-click should insert into the network.
     */
    public boolean isNetworkDepositSlot(int rawSlot) {
        if (rawSlot < 0 || rawSlot >= 45) {
            return false;
        }

        return getScreenBlock().map(block -> {
            if (block instanceof CraftingViewer) {
                return !CraftingViewer.isCraftingSlot(rawSlot);
            }
            if (block instanceof Viewer) {
                return true;
            }
            return allowTopPlaceSlot == null || !allowTopPlaceSlot.test(rawSlot);
        }).orElse(false);
    }

    @Override
    public boolean onClick(InventoryClickEvent event) {
        if (! (event.getWhoClicked() instanceof Player)) return false;
        Player p = (Player) event.getWhoClicked();

        ItemStack cursor = event.getCursor();

        Inventory clickedInventory = event.getClickedInventory();
        Inventory playerInventory = p.getInventory();
        Inventory topInventory = event.getView() != null
                ? event.getView().getTopInventory()
                : getInventory();
        if (clickedInventory == null || playerInventory == null || topInventory == null) return false;

        int topSize = topInventory.getSize();
        int rawSlot = event.getRawSlot();
        boolean rawInTopWindow = rawSlot >= 0 && rawSlot < topSize;

        InventoryAction action = event.getAction();
        boolean shiftFromPlayer = clickedInventory.equals(playerInventory)
                && action == InventoryAction.MOVE_TO_OTHER_INVENTORY;

        boolean isCursorPlace = false;
        if (clickedInventory.equals(topInventory)) {
            if (action == InventoryAction.PLACE_ALL
                    || action == InventoryAction.PLACE_ONE
                    || action == InventoryAction.PLACE_SOME
                    || action == InventoryAction.DROP_ALL_CURSOR
                    || action == InventoryAction.DROP_ONE_CURSOR) {
                isCursorPlace = true;
            } else if (cursor != null && cursor.getType() != Material.AIR
                    && action == InventoryAction.SWAP_WITH_CURSOR) {
                isCursorPlace = true;
            }
        }

        boolean vanillaPlaceSlot = allowTopPlaceSlot != null
                && rawInTopWindow
                && allowTopPlaceSlot.test(rawSlot);

        // Cancel vanilla shift into the GUI; MainListener deposits at HIGHEST and updates player inv
        if (hasDepositHandler() && shiftFromPlayer) {
            event.setCancelled(true);
            return false;
        }

        // Cancel vanilla cursor place on viewer slots; MainListener handles the deposit
        if (hasDepositHandler() && isCursorPlace && rawInTopWindow
                && isNetworkDepositSlot(rawSlot) && !vanillaPlaceSlot) {
            if (cursor != null && !cursor.getType().isAir()) {
                event.setCancelled(true);
                return false;
            }
        }

        // Crafting grid and other vanilla-place slots
        if ((isCursorPlace || shiftFromPlayer) && isNoPlace() && vanillaPlaceSlot) {
            event.setCancelled(false);
            return furtherClick(event);
        }

        // Block all other placement into protected GUI slots
        if ((isCursorPlace || shiftFromPlayer) && isNoPlace()) {
            event.setCancelled(true);
            return false;
        }

        return furtherClick(event);
    }
}
