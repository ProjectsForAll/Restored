package gg.drak.restored.gui;

import host.plas.bou.gui.CornerColor;
import host.plas.bou.gui.EditorInventoryGui;
import host.plas.bou.gui.GuiConfig;
import host.plas.bou.gui.GuiItems;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Restored adapter over BOU's {@link EditorInventoryGui}: adds open/click/drag/close
 * hooks consumed by {@link GuiListener}, plus fill-unused helpers.
 */
public abstract class AbstractInventoryGui extends EditorInventoryGui {

    protected AbstractInventoryGui(Player player, CornerColor cornerColor) {
        super(player, cornerColor);
    }

    protected AbstractInventoryGui(GuiConfig config) {
        super(config);
    }

    /**
     * Fills any remaining empty slots with black panes so unused areas are not placeable air.
     * Call after interactive slots have been assigned.
     */
    protected void fillUnusedWithBlack(ItemStack[] contents) {
        ItemStack black = GuiItems.filler(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType().isAir()) {
                contents[i] = black.clone();
            }
        }
    }

    /** Exposes the editor's slot binding to shared GUI chrome helpers. */
    public final void bindGuiSlot(int slot, String key) {
        bindSlot(slot, key);
    }

    public abstract void open();

    public abstract void handleClick(InventoryClickEvent event);

    public void handleDrag(InventoryDragEvent event) {
        event.setCancelled(true);
    }

    public void handleClose(InventoryCloseEvent event) {
        // Optional close hook for subclasses.
    }
}
