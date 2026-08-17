package gg.drak.restored.gui;

import host.plas.bou.gui.CornerColor;
import host.plas.bou.gui.GuiConfig;
import host.plas.bou.gui.GuiLayout;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Shared pagination helpers for list-style inventory GUIs (OpenDonut-style).
 */
public abstract class PaginatedInventoryGui extends AbstractInventoryGui {
    protected int page;

    protected PaginatedInventoryGui(Player player, CornerColor cornerColor) {
        super(player, cornerColor);
        this.page = 0;
    }

    protected PaginatedInventoryGui(GuiConfig config) {
        super(config);
        this.page = 0;
    }

    protected abstract void render();

    /**
     * Clamps {@link #page} into range and returns the total page count.
     */
    protected int clampPage(int totalEntries, int size) {
        int per = Math.max(1, perPage(size));
        int totalPages = Math.max(1, (int) Math.ceil(totalEntries / (double) per));
        if (page >= totalPages) {
            page = Math.max(0, totalPages - 1);
        }
        if (page < 0) {
            page = 0;
        }
        return totalPages;
    }

    protected <T> void fillCurrentPage(ItemStack[] contents, List<T> entries, int size, PageSlotBinder<T> binder) {
        int per = Math.max(1, perPage(size));
        int start = page * per;
        int end = Math.min(start + per, entries.size());
        int[] slots = GuiLayout.listContentSlots(size);
        for (int i = start, slotIndex = 0; i < end && slotIndex < slots.length; i++, slotIndex++) {
            binder.bind(contents, slots[slotIndex], entries.get(i));
        }
    }

    /**
     * Standard OpenDonut chrome: previous at {@code back - 1}, next at {@code back + 1}.
     */
    protected void placePaginationChrome(ItemStack[] contents, int totalEntries) {
        placePagination(contents, page, totalEntries);
    }

    /**
     * Prev/next on the bottom-row corners (slots {@code size-9} / {@code size-1}) when the
     * center chrome is already full of controls.
     */
    protected void placePaginationCorners(ItemStack[] contents, int totalEntries) {
        int size = contents.length;
        int prevSlot = size - 9;
        int nextSlot = size - 1;
        int per = Math.max(1, perPage(size));
        int totalPages = Math.max(1, (int) Math.ceil(totalEntries / (double) per));
        if (page > 0) {
            contents[prevSlot] = GuiItems.pagePreviousButton(page);
            bindSlot(prevSlot, "__pageprev");
        }
        if (page + 1 < totalPages) {
            contents[nextSlot] = GuiItems.pageNextButton(page + 2);
            bindSlot(nextSlot, "__pagenext");
        }
    }

    /**
     * @return true if the click was consumed as a page change
     */
    protected boolean handlePaginationClick(String key) {
        if ("__pageprev".equals(key)) {
            page = Math.max(0, page - 1);
            render();
            return true;
        }
        if ("__pagenext".equals(key)) {
            page++;
            render();
            return true;
        }
        return false;
    }

    @FunctionalInterface
    protected interface PageSlotBinder<T> {
        void bind(ItemStack[] contents, int slot, T entry);
    }
}
