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
    private String searchFilter;
    private gg.drak.restored.gui.NetworkBrowserPrefs.FilterMode filterMode;
    private gg.drak.restored.gui.NetworkBrowserPrefs.SortMode sortMode;
    private gg.drak.restored.gui.NetworkBrowserPrefs.SortDirection sortDirection;
    private boolean combineStacks;

    public FeedingFilterPickerGui(Player player, Network network, UUID linkId, Consumer<ItemStack> onPicked) {
        super(player, CornerColor.YELLOW);
        this.network = network;
        this.linkId = linkId;
        this.onPicked = onPicked;
        gg.drak.restored.gui.NetworkBrowserPrefs.State prefs =
                gg.drak.restored.gui.NetworkBrowserPrefs.get(player.getUniqueId());
        this.searchFilter = prefs.searchFilter();
        this.filterMode = prefs.filterMode();
        this.sortMode = prefs.sortMode();
        this.sortDirection = prefs.sortDirection();
        this.combineStacks = prefs.combineStacks();
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

        List<StoredStack> stacks = displayEntries();

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
        gg.drak.restored.gui.NetworkItemSearchControls.place(this, contents, state());
        placePagination(contents, page, stacks.size());
        finishAndOpen(contents);
    }

    private gg.drak.restored.gui.NetworkBrowserPrefs.State state() {
        return new gg.drak.restored.gui.NetworkBrowserPrefs.State(
                searchFilter, filterMode, sortMode, sortDirection, combineStacks);
    }

    private void savePrefs() {
        gg.drak.restored.gui.NetworkBrowserPrefs.set(player.getUniqueId(), state());
    }

    private List<StoredStack> displayEntries() {
        List<StoredStack> sorted = gg.drak.restored.gui.NetworkItemSearchControls
                .filterAndSort(network.getCombinedStacks(), state());
        if (combineStacks) {
            return sorted;
        }
        List<StoredStack> split = new ArrayList<>();
        for (StoredStack stored : sorted) {
            long remaining = stored.getAmount();
            while (remaining > 0) {
                long shown = Math.min(64, remaining);
                split.add(new StoredStack(stored.getTemplate(), shown));
                remaining -= shown;
            }
        }
        return split;
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
            savePrefs();
            onPicked.accept(null);
            return;
        }
        if ("search".equals(key)) {
            if (event.isShiftClick()) {
                searchFilter = "";
                page = 0;
                savePrefs();
                render();
            } else {
                gg.drak.restored.gui.NetworkItemSearchPrompt.open(player, result -> {
                    if (result != null) {
                        searchFilter = result;
                        page = 0;
                        savePrefs();
                    }
                    open();
                });
            }
            return;
        }
        if ("filter_mode".equals(key)) {
            filterMode = filterMode.next();
            page = 0;
            savePrefs();
            render();
            return;
        }
        if ("sort_mode".equals(key)) {
            sortMode = sortMode.next();
            page = 0;
            savePrefs();
            render();
            return;
        }
        if ("sort_dir".equals(key)) {
            sortDirection = sortDirection.toggle();
            page = 0;
            savePrefs();
            render();
            return;
        }
        if ("combine".equals(key)) {
            combineStacks = !combineStacks;
            page = 0;
            savePrefs();
            render();
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
