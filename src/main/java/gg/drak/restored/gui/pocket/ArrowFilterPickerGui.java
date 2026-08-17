package gg.drak.restored.gui.pocket;

import gg.drak.restored.data.Network;
import gg.drak.restored.data.StoredStack;
import gg.drak.restored.gui.AbstractInventoryGui;
import gg.drak.restored.gui.GuiItems;
import gg.drak.restored.gui.GuiUtils;
import gg.drak.restored.items.PocketLinkItem;
import host.plas.bou.gui.CornerColor;
import host.plas.bou.gui.GuiLayout;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.UUID;

/** Picks only arrow stacks from a linked network without extracting them. */
public class ArrowFilterPickerGui extends AbstractInventoryGui implements PocketLinkGuiBound {
    private final Network network;
    private final Consumer<ItemStack> onPicked;
    private final UUID linkId;
    private final Map<Integer, StoredStack> entries = new HashMap<>();
    private int page;

    public ArrowFilterPickerGui(Player player, Network network, UUID linkId, Consumer<ItemStack> onPicked) {
        super(player, CornerColor.YELLOW);
        this.network = network;
        this.linkId = linkId;
        this.onPicked = onPicked;
    }

    @Override
    public UUID getPocketLinkId() {
        return linkId;
    }

    @Override
    public void open() {
        render();
    }

    private void render() {
        PocketLinkItem.markGuiOpen(player, linkId);
        ItemStack[] contents = beginShell(GuiLayout.SIZE_LARGE, "#FFED6A&lSelect Arrow");
        entries.clear();
        List<StoredStack> arrows = new ArrayList<>();
        for (StoredStack stored : network.getCombinedStacks()) {
            if (QuiverAugmentGui.isArrow(stored.getTemplate())) {
                arrows.add(stored);
            }
        }
        arrows.sort(Comparator.comparing(s -> StoredStack.itemKey(s.getTemplate())));
        int perPage = perPage(GuiLayout.SIZE_LARGE);
        int totalPages = Math.max(1, (int) Math.ceil(arrows.size() / (double) perPage));
        page = Math.min(page, totalPages - 1);
        int[] slots = GuiLayout.listContentSlots(GuiLayout.SIZE_LARGE);
        int start = page * perPage;
        for (int i = start, slot = 0; i < arrows.size() && slot < slots.length; i++, slot++) {
            int rawSlot = slots[slot];
            StoredStack stored = arrows.get(i);
            contents[rawSlot] = GuiUtils.asGuiStack(stored);
            entries.put(rawSlot, stored);
            bindSlot(rawSlot, "item");
        }
        placeReturnButton(contents, "back");
        int back = resolveBackSlot(contents.length);
        if (page > 0) {
            contents[GuiLayout.pagePrevSlot(back)] = GuiItems.pagePreviousButton(page);
            bindSlot(GuiLayout.pagePrevSlot(back), "prev");
        }
        if (page + 1 < totalPages) {
            contents[GuiLayout.pageNextSlot(back)] = GuiItems.pageNextButton(page + 2);
            bindSlot(GuiLayout.pageNextSlot(back), "next");
        }
        finishAndOpen(contents);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(inventory)) {
            return;
        }
        String key = getKeyAtSlot(event.getRawSlot());
        if ("back".equals(key)) {
            onPicked.accept(null);
        } else if ("prev".equals(key)) {
            page = Math.max(0, page - 1);
            render();
        } else if ("next".equals(key)) {
            page++;
            render();
        } else {
            StoredStack stored = entries.get(event.getRawSlot());
            if (stored != null) {
                ItemStack picked = stored.getTemplate().clone();
                picked.setAmount(1);
                onPicked.accept(picked);
            }
        }
    }
}
