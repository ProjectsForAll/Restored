package gg.drak.restored.gui;

import gg.drak.restored.data.Network;
import gg.drak.restored.data.StoredStack;
import gg.drak.restored.gui.NetworkBrowserPrefs.State;
import host.plas.bou.gui.CornerColor;
import host.plas.bou.gui.GuiLayout;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Selects a filter template from the network without extracting it. */
public class NetworkHopperFilterPickerGui extends AbstractInventoryGui {
    private final Network network;
    private final Consumer<ItemStack> onPicked;
    private final String title;
    private int page;
    private final Map<Integer, StoredStack> entries = new HashMap<>();
    private String searchFilter;
    private NetworkBrowserPrefs.FilterMode filterMode;
    private NetworkBrowserPrefs.SortMode sortMode;
    private NetworkBrowserPrefs.SortDirection sortDirection;
    private boolean combineStacks;

    public NetworkHopperFilterPickerGui(Player player, Network network, Consumer<ItemStack> onPicked) {
        this(player, network, onPicked, "#FFED6A&lSelect Hopper Filter Item");
    }

    public NetworkHopperFilterPickerGui(Player player, Network network, Consumer<ItemStack> onPicked, String title) {
        super(player, CornerColor.YELLOW);
        this.network = network;
        this.onPicked = onPicked;
        this.title = title;
        State prefs = NetworkBrowserPrefs.get(player.getUniqueId());
        this.searchFilter = prefs.searchFilter();
        this.filterMode = prefs.filterMode();
        this.sortMode = prefs.sortMode();
        this.sortDirection = prefs.sortDirection();
        this.combineStacks = prefs.combineStacks();
    }

    @Override
    public void open() {
        render();
    }

    private void render() {
        ItemStack[] contents = beginShell(GuiLayout.SIZE_LARGE, title);
        entries.clear();
        List<StoredStack> stacks = displayEntries();
        int perPage = perPage(GuiLayout.SIZE_LARGE);
        int totalPages = Math.max(1, (int) Math.ceil(stacks.size() / (double) perPage));
        page = Math.min(page, totalPages - 1);
        int[] slots = GuiLayout.listContentSlots(GuiLayout.SIZE_LARGE);
        int start = page * perPage;
        int end = Math.min(start + perPage, stacks.size());
        for (int i = start, slotIndex = 0; i < end && slotIndex < slots.length; i++, slotIndex++) {
            StoredStack stored = stacks.get(i);
            int slot = slots[slotIndex];
            contents[slot] = GuiUtils.asGuiStack(stored);
            entries.put(slot, stored);
            bindSlot(slot, "item");
        }
        placeReturnButton(contents, "back");
        NetworkItemSearchControls.place(this, contents, state());
        placePagination(contents, page, stacks.size());
        finishAndOpen(contents);
    }

    private State state() {
        return new State(searchFilter, filterMode, sortMode, sortDirection, combineStacks);
    }

    private void savePrefs() {
        NetworkBrowserPrefs.set(player.getUniqueId(), state());
    }

    private List<StoredStack> displayEntries() {
        List<StoredStack> sorted = NetworkItemSearchControls.filterAndSort(network.getCombinedStacks(), state());
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
                NetworkItemSearchPrompt.open(player, result -> {
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
        StoredStack stored = entries.get(event.getRawSlot());
        if (stored == null) {
            return;
        }
        ItemStack picked = stored.getTemplate().clone();
        picked.setAmount(1);
        onPicked.accept(picked);
    }
}
