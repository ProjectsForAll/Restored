package gg.drak.restored.data.blocks.inventory;

import org.bukkit.inventory.ItemStack;

public interface InventoryBlock {
    ItemStack tryAddItem(ItemStack stack);

    void redraw();
}
