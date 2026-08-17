package gg.drak.restored.gui.pocket;

import host.plas.bou.gui.CornerColor;
import host.plas.bou.gui.GuiLayout;

import gg.drak.restored.data.Network;
import gg.drak.restored.data.StoredStack;
import gg.drak.restored.gui.AbstractInventoryGui;
import gg.drak.restored.gui.GuiUtils;
import gg.drak.restored.items.PocketLinkItem;
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

/**
 * Picks a filter template from the network without extracting items.
 */
public class FeedingFilterPickerGui extends AbstractInventoryGui implements PocketLinkGuiBound {
    private final Network network;
    private final Consumer<ItemStack> onPicked;
    private final UUID linkId;
    private int page;
    private final Map<Integer, StoredStack> contentEntries = new HashMap<>();

    public FeedingFilterPickerGui(Player player, Network network, UUID linkId, Consumer<ItemStack> onPicked) {
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
        ItemStack[] contents = beginShell(GuiLayout.SIZE_LARGE, "#FFED6A&lSelect Filter Item");
        contentEntries.clear();

        List<StoredStack> stacks = new ArrayList<>(network.getCombinedStacks());
        stacks.sort(Comparator.comparing(s -> StoredStack.itemKey(s.getTemplate())));

        int perPage = perPage(GuiLayout.SIZE_LARGE);
        int totalPages = Math.max(1, (int) Math.ceil(stacks.size() / (double) perPage));
        if (page >= totalPages) {
            page = Math.max(0, totalPages - 1);
        }
        int start = page * perPage;
        int end = Math.min(start + perPage, stacks.size());
        int[] slots = GuiLayout.listContentSlots(GuiLayout.SIZE_LARGE);

        for (int i = start, slotIndex = 0; i < end && slotIndex < slots.length; i++, slotIndex++) {
            StoredStack stack = stacks.get(i);
            contents[slots[slotIndex]] = GuiUtils.asGuiStack(stack);
            contentEntries.put(slots[slotIndex], stack);
            bindSlot(slots[slotIndex], "item");
        }

        placeReturnButton(contents, "back");
        placePagination(contents, page, stacks.size());
        finishAndOpen(contents);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(inventory)) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        String key = getKeyAtSlot(event.getRawSlot());
        if ("__pageprev".equals(key)) {
            page = Math.max(0, page - 1);
            render();
            return;
        }
        if ("__pagenext".equals(key)) {
            page++;
            render();
            return;
        }
        if ("back".equals(key)) {
            onPicked.accept(null);
            return;
        }
        StoredStack stack = contentEntries.get(event.getRawSlot());
        if (stack == null) {
            return;
        }
        ItemStack template = stack.getTemplate().clone();
        template.setAmount(1);
        onPicked.accept(template);
    }
}
