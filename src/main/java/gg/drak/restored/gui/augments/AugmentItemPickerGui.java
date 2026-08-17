package gg.drak.restored.gui.augments;

import host.plas.bou.gui.CornerColor;
import host.plas.bou.gui.GuiConfig;
import host.plas.bou.gui.GuiLayout;

import gg.drak.restored.Restored;
import gg.drak.restored.data.AugmentType;
import gg.drak.restored.data.Network;
import gg.drak.restored.data.StoredStack;
import gg.drak.restored.gui.GuiItems;
import gg.drak.restored.gui.GuiUtils;
import gg.drak.restored.gui.NetworkBrowserPrefs;
import gg.drak.restored.gui.NetworkItemsGui;
import gg.drak.restored.gui.PaginatedInventoryGui;
import gg.drak.restored.util.LegacyColors;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Paginated network item picker with the same search/filter/sort chrome as network storage.
 */
public class AugmentItemPickerGui extends PaginatedInventoryGui {
    private static final int SIZE = GuiLayout.SIZE_LARGE;
    private static final int DISPLAY_STACK_SIZE = 64;
    private static final int SEARCH_SLOT = 2;
    private static final int FILTER_MODE_SLOT = 3;
    private static final int SORT_MODE_SLOT = 4;
    private static final int SORT_DIR_SLOT = 5;
    private static final int COMBINE_SLOT = 6;
    private static final int BACK_SLOT = 49;

    private static final ConcurrentHashMap<UUID, PendingFilter> PENDING_CHAT = new ConcurrentHashMap<>();
    private static ChatFilterListener chatFilterListener;

    private final Network network;
    private final AugmentType augmentType;
    private final int targetSlotId;
    private final int maxAmount;
    private final Consumer<ItemStack> onPicked;
    private final Map<Integer, NetworkItemsGui.DisplayEntry> contentEntries = new HashMap<>();

    private String searchFilter;
    private NetworkBrowserPrefs.FilterMode filterMode;
    private NetworkBrowserPrefs.SortMode sortMode;
    private NetworkBrowserPrefs.SortDirection sortDirection;
    private boolean combineStacks;

    public AugmentItemPickerGui(
            Player player,
            Network network,
            AugmentType augmentType,
            int targetSlotId,
            int maxAmount,
            Consumer<ItemStack> onPicked
    ) {
        super(GuiConfig.builder(player).cornerColor(CornerColor.YELLOW).backSlot(BACK_SLOT).build());
        this.network = network;
        this.augmentType = augmentType;
        this.targetSlotId = targetSlotId;
        this.maxAmount = Math.max(1, maxAmount);
        this.onPicked = onPicked;
        NetworkBrowserPrefs.State prefs = NetworkBrowserPrefs.get(player.getUniqueId());
        this.searchFilter = prefs.searchFilter();
        this.filterMode = prefs.filterMode();
        this.sortMode = prefs.sortMode();
        this.sortDirection = prefs.sortDirection();
        this.combineStacks = prefs.combineStacks();
    }

    private void savePrefs() {
        NetworkBrowserPrefs.set(player.getUniqueId(), new NetworkBrowserPrefs.State(
                searchFilter, filterMode, sortMode, sortDirection, combineStacks
        ));
    }

    @Override
    public void open() {
        render();
    }

    @Override
    protected void render() {
        ItemStack[] contents = beginShell(SIZE, "#FFED6A&lSelect Item — " + augmentType.getDisplayName());
        contentEntries.clear();

        List<NetworkItemsGui.DisplayEntry> entries = buildDisplayEntries();
        clampPage(entries.size(), SIZE);
        fillCurrentPage(contents, entries, SIZE, (arr, slot, entry) -> {
            arr[slot] = GuiUtils.asGuiStack(entry);
            contentEntries.put(slot, entry);
            bindSlot(slot, "item");
        });

        placeFilterChrome(contents);
        placeReturnButton(contents, "back");
        placePaginationChrome(contents, entries.size());
        finishAndOpen(contents);
    }

    private void placeFilterChrome(ItemStack[] contents) {
        contents[SEARCH_SLOT] = GuiItems.button(
                Material.OAK_SIGN,
                "#FFED6A&lSearch Filter",
                List.of(
                        "#bdc8c9Click, then type a filter in chat.",
                        "#bdc8c9Shift-click to clear the filter.",
                        "#AAAAAACurrent: #FFED6A" + (searchFilter.isBlank() ? "None" : searchFilter)
                )
        );
        bindSlot(SEARCH_SLOT, "search");

        contents[FILTER_MODE_SLOT] = GuiItems.button(
                Material.NAME_TAG,
                "#FFED6A&lFilter Mode",
                List.of(
                        modeLine(filterMode, NetworkBrowserPrefs.FilterMode.NAME_PLAIN, "Name (plain)"),
                        modeLine(filterMode, NetworkBrowserPrefs.FilterMode.NAME_FULL, "Name + lore"),
                        modeLine(filterMode, NetworkBrowserPrefs.FilterMode.MINECRAFT_ID, "Minecraft ID")
                )
        );
        bindSlot(FILTER_MODE_SLOT, "filter_mode");

        contents[SORT_MODE_SLOT] = GuiItems.button(
                Material.HOPPER,
                "#FFED6A&lSort By",
                List.of(
                        modeLine(sortMode, NetworkBrowserPrefs.SortMode.NAME, "Name"),
                        modeLine(sortMode, NetworkBrowserPrefs.SortMode.ITEM_COUNT, "Item Count"),
                        modeLine(sortMode, NetworkBrowserPrefs.SortMode.MINECRAFT_ID, "Minecraft ID")
                )
        );
        bindSlot(SORT_MODE_SLOT, "sort_mode");

        contents[SORT_DIR_SLOT] = GuiItems.button(
                Material.IRON_NUGGET,
                "#FFED6A&lSort Direction",
                List.of(
                        modeLine(sortDirection, NetworkBrowserPrefs.SortDirection.ASCENDING, "Ascending"),
                        modeLine(sortDirection, NetworkBrowserPrefs.SortDirection.DESCENDING, "Descending")
                )
        );
        bindSlot(SORT_DIR_SLOT, "sort_dir");

        contents[COMBINE_SLOT] = GuiItems.button(
                combineStacks ? Material.SLIME_BALL : Material.MAGMA_CREAM,
                "#FFED6A&lCombine",
                List.of(
                        "#bdc8c9Click to toggle how stacks are shown.",
                        "#bdc8c9Combined: amount 1 in the slot; hover for total.",
                        "",
                        combineLine(true, "Combine Stacks"),
                        combineLine(false, "Don't Combine Stacks")
                )
        );
        bindSlot(COMBINE_SLOT, "combine");
    }

    private String modeLine(Enum<?> current, Enum<?> option, String label) {
        return (current == option ? "#00FC88▶ " : "#AAAAAA  ") + label;
    }

    private String combineLine(boolean option, String label) {
        return (combineStacks == option ? "#00FC88▶ " : "#AAAAAA  ") + label;
    }

    private List<NetworkItemsGui.DisplayEntry> buildDisplayEntries() {
        List<StoredStack> filtered = new ArrayList<>();
        for (StoredStack stack : network.getCombinedStacks()) {
            if (matchesFilter(stack)) {
                filtered.add(stack);
            }
        }
        Comparator<StoredStack> comparator = switch (sortMode) {
            case NAME -> Comparator.comparing(s -> strip(plainName(s.getTemplate())), String.CASE_INSENSITIVE_ORDER);
            case ITEM_COUNT -> Comparator.comparingLong(StoredStack::getAmount);
            case MINECRAFT_ID -> Comparator.comparing(s -> minecraftId(s), String.CASE_INSENSITIVE_ORDER);
        };
        if (sortDirection == NetworkBrowserPrefs.SortDirection.DESCENDING) {
            comparator = comparator.reversed();
        }
        filtered.sort(comparator.thenComparing(StoredStack::itemKey));

        List<NetworkItemsGui.DisplayEntry> entries = new ArrayList<>();
        for (StoredStack stack : filtered) {
            String key = stack.itemKey();
            long total = stack.getAmount();
            if (combineStacks) {
                entries.add(new NetworkItemsGui.DisplayEntry(key, stack.getTemplate(), 1, total));
                continue;
            }
            long remaining = total;
            while (remaining > 0) {
                long shown = Math.min(DISPLAY_STACK_SIZE, remaining);
                entries.add(new NetworkItemsGui.DisplayEntry(key, stack.getTemplate(), shown, total));
                remaining -= shown;
            }
        }
        return entries;
    }

    private boolean matchesFilter(StoredStack stack) {
        if (searchFilter == null || searchFilter.isBlank()) {
            return true;
        }
        String query = searchFilter.toLowerCase(Locale.ROOT);
        ItemStack template = stack.getTemplate();
        return switch (filterMode) {
            case NAME_PLAIN -> strip(plainName(template)).toLowerCase(Locale.ROOT).contains(query);
            case NAME_FULL -> searchableFullName(template).toLowerCase(Locale.ROOT).contains(query);
            case MINECRAFT_ID -> minecraftId(stack).toLowerCase(Locale.ROOT).contains(query);
        };
    }

    private static String plainName(ItemStack stack) {
        if (stack == null) {
            return "";
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return meta.getDisplayName();
        }
        return stack.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private static String searchableFullName(ItemStack stack) {
        StringBuilder builder = new StringBuilder(plainName(stack));
        ItemMeta meta = stack.getItemMeta();
        if (meta != null && meta.hasLore() && meta.getLore() != null) {
            for (String line : meta.getLore()) {
                builder.append(' ').append(line);
            }
        }
        return strip(builder.toString());
    }

    private static String minecraftId(StoredStack stack) {
        if (stack.getTemplate() == null) {
            return "";
        }
        return stack.getTemplate().getType().getKey().toString();
    }

    private static String strip(String input) {
        if (input == null) {
            return "";
        }
        return ChatColor.stripColor(input.replace('&', '§'));
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(inventory)) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        String key = getKeyAtSlot(event.getRawSlot());
        if (handlePaginationClick(key)) {
            return;
        }
        if ("back".equals(key)) {
            savePrefs();
            onPicked.accept(null);
            return;
        }
        if ("search".equals(key)) {
            if (event.isShiftClick()) {
                if (!searchFilter.isBlank()) {
                    searchFilter = "";
                    page = 0;
                    savePrefs();
                    render();
                }
                return;
            }
            openSearchPrompt();
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
        NetworkItemsGui.DisplayEntry entry = contentEntries.get(event.getRawSlot());
        if (entry == null) {
            return;
        }
        if (!network.canWithdraw(player.getUniqueId())) {
            player.sendMessage(LegacyColors.color("#FF5555You cannot take items from this network."));
            return;
        }

        long available = network.getCombinedAmount(entry.itemKey());
        if (available <= 0) {
            render();
            return;
        }
        int maxStack = entry.template() == null ? DISPLAY_STACK_SIZE : entry.template().getMaxStackSize();
        long want = switch (event.getClick()) {
            case RIGHT, SHIFT_RIGHT -> 1;
            case SHIFT_LEFT -> Math.min(maxAmount, Math.min(DISPLAY_STACK_SIZE, available));
            default -> combineStacks
                    ? Math.min(maxAmount, Math.min(maxStack, available))
                    : Math.min(maxAmount, Math.min(entry.stackAmount(), available));
        };
        long taken = network.extract(entry.itemKey(), want);
        if (taken <= 0) {
            render();
            return;
        }
        network.save();
        savePrefs();
        ItemStack picked = entry.template().clone();
        picked.setAmount((int) taken);
        onPicked.accept(picked);
    }

    private void openSearchPrompt() {
        markKeepAndClose();
        ensureChatFilterListener();
        PENDING_CHAT.put(player.getUniqueId(), new PendingFilter(this));
        player.sendMessage(LegacyColors.color("#FFED6AEnter a search filter in chat."));
        player.sendMessage(LegacyColors.color("#AAAAAAType 'clear' to clear, or 'cancel' to abort."));
    }

    private void markKeepAndClose() {
        player.closeInventory();
    }

    private static void ensureChatFilterListener() {
        if (chatFilterListener != null) {
            return;
        }
        chatFilterListener = new ChatFilterListener();
        Restored.getInstance().registerListener(chatFilterListener);
    }

    private record PendingFilter(AugmentItemPickerGui gui) {
    }

    private static final class ChatFilterListener implements Listener {
        @EventHandler(priority = EventPriority.LOWEST)
        public void onChat(AsyncPlayerChatEvent event) {
            PendingFilter pending = PENDING_CHAT.remove(event.getPlayer().getUniqueId());
            if (pending == null) {
                return;
            }
            event.setCancelled(true);
            String message = event.getMessage().trim();
            Player p = event.getPlayer();
            Bukkit.getScheduler().runTask(Restored.getInstance(), () -> {
                AugmentItemPickerGui gui = pending.gui();
                if (!p.equals(gui.player)) {
                    return;
                }
                if (message.equalsIgnoreCase("cancel")) {
                    p.sendMessage(LegacyColors.color("#FF5555Cancelled."));
                    gui.open();
                    return;
                }
                if (message.equalsIgnoreCase("clear") || message.equalsIgnoreCase("none")) {
                    gui.searchFilter = "";
                } else {
                    gui.searchFilter = message;
                }
                gui.page = 0;
                gui.savePrefs();
                gui.open();
            });
        }
    }
}
