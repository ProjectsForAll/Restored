package gg.drak.restored.gui;

import host.plas.bou.gui.CornerColor;
import host.plas.bou.gui.GuiConfig;
import host.plas.bou.gui.GuiLayout;
import host.plas.bou.gui.editor.EditorDragDrop;

import gg.drak.restored.Restored;
import gg.drak.restored.data.Network;
import gg.drak.restored.data.StoredStack;
import gg.drak.restored.gui.augments.AugmentsListGui;
import gg.drak.restored.util.LegacyColors;
import gg.drak.restored.util.LinkedChestStorage;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
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

/**
 * Paginated network storage browser — distinct items split into 64-sized display slices.
 */
public class NetworkItemsGui extends PaginatedInventoryGui {
    private static final int DISPLAY_STACK_SIZE = 64;
    private static final int SIZE = GuiLayout.SIZE_LARGE;

    // Top-row filter chrome (slots 1–7; corners 0/8 stay yellow)
    private static final int SEARCH_SLOT = 2;
    private static final int FILTER_MODE_SLOT = 3;
    private static final int SORT_MODE_SLOT = 4;
    private static final int SORT_DIR_SLOT = 5;
    private static final int COMBINE_SLOT = 6;

    // Bottom-row navigation / actions (prev/next flank back via placePagination)
    private static final int DEPOSIT_SLOT = 46;
    private static final int AUGMENTS_SLOT = 47;
    private static final int BACK_SLOT = 49;
    /** Bottom row slot 8 (1-indexed); slot 9 is the corner chrome. */
    private static final int UPGRADES_SLOT = 52;

    private static final ConcurrentHashMap<UUID, PendingFilter> PENDING_CHAT = new ConcurrentHashMap<>();
    private static ChatFilterListener chatFilterListener;

    private final Network network;
    private String searchFilter;
    private NetworkBrowserPrefs.FilterMode filterMode;
    private NetworkBrowserPrefs.SortMode sortMode;
    private NetworkBrowserPrefs.SortDirection sortDirection;
    private boolean combineStacks;
    private final Map<Integer, DisplayEntry> contentEntries = new HashMap<>();

    private boolean warnedUnavailable;

    public NetworkItemsGui(Player player, Network network) {
        super(GuiConfig.builder(player).cornerColor(CornerColor.YELLOW).backSlot(BACK_SLOT).build());
        this.network = network;
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
        network.recordOpen(player.getUniqueId());
        render();
    }

    @Override
    protected void render() {
        ItemStack[] contents = beginShell(SIZE, "#FFED6A&lNetwork Storage");
        contentEntries.clear();

        List<DisplayEntry> entries = buildDisplayEntries();
        clampPage(entries.size(), SIZE);

        fillCurrentPage(contents, entries, SIZE, (arr, slot, entry) -> {
            arr[slot] = GuiUtils.asGuiStack(entry);
            contentEntries.put(slot, entry);
            bindSlot(slot, "item");
        });

        placeControls(contents);
        placePaginationChrome(contents, entries.size());
        warnUnavailableLinks();
        finishAndOpen(contents);
    }

    /**
     * Linked chests that have not been loaded since the server started have no known contents,
     * so they are missing from this view. Say so once per open rather than silently showing a
     * smaller total — a silently wrong count is what makes items look like they randomly vanish.
     */
    private void warnUnavailableLinks() {
        if (warnedUnavailable) {
            return;
        }
        warnedUnavailable = true;
        int unavailable = LinkedChestStorage.countUnavailableLinks(network);
        if (unavailable > 0) {
            player.sendMessage(LegacyColors.color(
                    "#FF5555" + unavailable + " linked chest(s) have not been loaded since the "
                            + "server started; their contents are not shown."));
        }
    }

    private void placeControls(ItemStack[] contents) {
        // Top row: search / filter / sort
        contents[SEARCH_SLOT] = GuiItems.button(
                Material.OAK_SIGN,
                "#FFED6A&lSearch Filter",
                List.of(
                        "#bdc8c9Click, then type a filter in chat.",
                        "#bdc8c9Shift-click to clear the filter.",
                        "#bdc8c9Type #FFED6Aclear #bdc8c9or #FFED6Acancel #bdc8c9to reset/abort.",
                        "#AAAAAACurrent: #FFED6A" + (searchFilter.isBlank() ? "None" : searchFilter)
                )
        );
        bindSlot(SEARCH_SLOT, "search");

        contents[FILTER_MODE_SLOT] = GuiItems.button(
                Material.NAME_TAG,
                "#FFED6A&lFilter Mode",
                List.of(
                        "#bdc8c9Click to cycle how search matches.",
                        "",
                        modeLine(NetworkBrowserPrefs.FilterMode.NAME_PLAIN, "Name (No Formatting & Lore)"),
                        modeLine(NetworkBrowserPrefs.FilterMode.NAME_FULL, "Name (Formatting & Lore)"),
                        modeLine(NetworkBrowserPrefs.FilterMode.MINECRAFT_ID, "Minecraft ID")
                )
        );
        bindSlot(FILTER_MODE_SLOT, "filter_mode");

        contents[SORT_MODE_SLOT] = GuiItems.button(
                Material.HOPPER,
                "#FFED6A&lSort By",
                List.of(
                        "#bdc8c9Click to cycle sort field.",
                        "",
                        modeLine(NetworkBrowserPrefs.SortMode.NAME, "Name"),
                        modeLine(NetworkBrowserPrefs.SortMode.ITEM_COUNT, "Item Count"),
                        modeLine(NetworkBrowserPrefs.SortMode.MINECRAFT_ID, "Minecraft ID")
                )
        );
        bindSlot(SORT_MODE_SLOT, "sort_mode");

        contents[SORT_DIR_SLOT] = GuiItems.button(
                Material.IRON_NUGGET,
                "#FFED6A&lSort Direction",
                List.of(
                        "#bdc8c9Click to toggle ascending/descending.",
                        "",
                        modeLine(NetworkBrowserPrefs.SortDirection.ASCENDING, "Ascending"),
                        modeLine(NetworkBrowserPrefs.SortDirection.DESCENDING, "Descending")
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

        // Bottom row: actions + back (prev/next placed by placePaginationChrome)
        contents[DEPOSIT_SLOT] = GuiItems.button(
                Material.CHEST,
                "#FFED6A&lDeposit Items",
                List.of(
                        "#bdc8c9Open a chest to dump items into the network.",
                        "#AAAAAAClosing that chest deposits everything."
                )
        );
        bindSlot(DEPOSIT_SLOT, "deposit");

        contents[AUGMENTS_SLOT] = GuiItems.button(
                Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE,
                "#FFED6A&lAugments",
                List.of(
                        "#bdc8c9Install and open network workstation augments.",
                        "#AAAAAACrafting, furnaces, grindstone, and more."
                )
        );
        bindSlot(AUGMENTS_SLOT, "augments");

        contents[UPGRADES_SLOT] = GuiItems.button(
                Material.GOLD_INGOT,
                "#FFED6A&lNetwork Upgrades",
                List.of(
                        "#bdc8c9Install or remove Network Upgrades.",
                        "#AAAAAAInstalled: #FFED6A" + network.getUpgradeCount(),
                        "#AAAAAAVirtual capacity: #FFED6A" + network.getCapacity()
                )
        );
        bindSlot(UPGRADES_SLOT, "upgrades");

        contents[BACK_SLOT] = GuiItems.returnButton();
        bindSlot(BACK_SLOT, "back");
    }

    private String modeLine(Enum<?> mode, String label) {
        boolean selected = mode == filterMode || mode == sortMode || mode == sortDirection;
        return (selected ? "#00FC88▶ " : "#AAAAAA  ") + label;
    }

    private String combineLine(boolean option, String label) {
        return (combineStacks == option ? "#00FC88▶ " : "#AAAAAA  ") + label;
    }

    /**
     * Distinct items are sorted. When combine is on, each item is one slot (amount 1).
     * When off, each is split into display slices of up to 64.
     */
    private List<DisplayEntry> buildDisplayEntries() {
        List<StoredStack> filtered = new ArrayList<>();
        for (StoredStack stack : network.getCombinedStacks()) {
            if (matchesFilter(stack)) {
                filtered.add(stack);
            }
        }

        Comparator<StoredStack> comparator = switch (sortMode) {
            case NAME -> Comparator.comparing(this::sortNameKey, String.CASE_INSENSITIVE_ORDER);
            case ITEM_COUNT -> Comparator.comparingLong(StoredStack::getAmount);
            case MINECRAFT_ID -> Comparator.comparing(this::minecraftId, String.CASE_INSENSITIVE_ORDER);
        };
        if (sortDirection == NetworkBrowserPrefs.SortDirection.DESCENDING) {
            comparator = comparator.reversed();
        }
        comparator = comparator.thenComparing(StoredStack::itemKey);
        filtered.sort(comparator);

        List<DisplayEntry> entries = new ArrayList<>();
        for (StoredStack stack : filtered) {
            String key = stack.itemKey();
            long total = stack.getAmount();
            if (combineStacks) {
                entries.add(new DisplayEntry(key, stack.getTemplate(), 1, total));
                continue;
            }
            long remaining = total;
            while (remaining > 0) {
                long shown = Math.min(DISPLAY_STACK_SIZE, remaining);
                entries.add(new DisplayEntry(key, stack.getTemplate(), shown, total));
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

    private String sortNameKey(StoredStack stack) {
        return strip(plainName(stack.getTemplate()));
    }

    private String plainName(ItemStack stack) {
        if (stack == null) {
            return "";
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return meta.getDisplayName();
        }
        return stack.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private String searchableFullName(ItemStack stack) {
        StringBuilder builder = new StringBuilder(plainName(stack));
        ItemMeta meta = stack.getItemMeta();
        if (meta != null && meta.hasLore() && meta.getLore() != null) {
            for (String line : meta.getLore()) {
                builder.append(' ').append(line);
            }
        }
        return strip(builder.toString());
    }

    private String minecraftId(StoredStack stack) {
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
        if (event.getClickedInventory() == null) {
            return;
        }

        if (event.getClickedInventory().equals(player.getInventory())) {
            if (event.isShiftClick() && network.canDeposit(player.getUniqueId())) {
                ItemStack clicked = event.getCurrentItem();
                if (clicked == null || clicked.getType().isAir()) {
                    return;
                }
                event.setCancelled(true);
                long amount = clicked.getAmount();
                long inserted = network.insert(clicked, amount);
                if (inserted > 0) {
                    clicked.setAmount((int) (amount - inserted));
                    if (clicked.getAmount() <= 0) {
                        event.setCurrentItem(null);
                    }
                    render();
                }
            }
            return;
        }

        if (!event.getClickedInventory().equals(inventory)) {
            return;
        }

        event.setCancelled(true);
        int slot = event.getRawSlot();
        String key = getKeyAtSlot(slot);

        if (handlePaginationClick(key)) {
            return;
        }
        if ("back".equals(key)) {
            player.closeInventory();
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
        if ("deposit".equals(key)) {
            if (!network.canDeposit(player.getUniqueId())) {
                player.sendMessage(LegacyColors.color("#FF5555You cannot deposit into this network."));
                return;
            }
            new NetworkDepositGui(player, network).open();
            return;
        }
        if ("augments".equals(key)) {
            if (!network.canAccess(player.getUniqueId())) {
                player.sendMessage(LegacyColors.color("#FF5555You cannot access this network."));
                return;
            }
            new AugmentsListGui(player, network).open();
            return;
        }
        if ("upgrades".equals(key)) {
            if (!network.canAccess(player.getUniqueId())) {
                player.sendMessage(LegacyColors.color("#FF5555You cannot access this network."));
                return;
            }
            new NetworkUpgradesGui(player, network).open();
            return;
        }

        ItemStack cursor = EditorDragDrop.cursorItem(event);
        if (cursor != null
                && network.canDeposit(player.getUniqueId())
                && EditorDragDrop.isTopInventorySlot(event, slot)
                && GuiUtils.isContentSlot(slot, inventory.getSize())) {
            long amount = cursor.getAmount();
            long inserted = network.insert(cursor, amount);
            if (inserted > 0) {
                ItemStack remaining = cursor.clone();
                remaining.setAmount((int) (amount - inserted));
                event.getView().setCursor(remaining.getAmount() > 0 ? remaining : null);
                render();
            }
            return;
        }

        DisplayEntry entry = contentEntries.get(slot);
        if (entry != null && network.canWithdraw(player.getUniqueId())) {
            withdraw(entry, event.getClick());
        }
    }

    @Override
    public void handleDrag(InventoryDragEvent event) {
        if (!network.canDeposit(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        boolean touchesPlayer = false;
        boolean touchesContent = false;
        int topSize = event.getView().getTopInventory().getSize();
        for (int slot : event.getRawSlots()) {
            if (slot >= 0 && slot < topSize) {
                if (GuiUtils.isContentSlot(slot, topSize)) {
                    touchesContent = true;
                } else {
                    event.setCancelled(true);
                    return;
                }
            } else {
                touchesPlayer = true;
            }
        }

        if (!touchesPlayer || !touchesContent) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);
        ItemStack oldCursor = event.getOldCursor();
        if (oldCursor == null || oldCursor.getType().isAir()) {
            return;
        }
        long amount = oldCursor.getAmount();
        long inserted = network.insert(oldCursor, amount);
        if (inserted > 0) {
            ItemStack remaining = oldCursor.clone();
            remaining.setAmount((int) (amount - inserted));
            event.getView().setCursor(remaining.getAmount() > 0 ? remaining : null);
            render();
        }
    }

    private void withdraw(DisplayEntry entry, ClickType clickType) {
        long available = network.getCombinedAmount(entry.itemKey());
        if (available <= 0) {
            if (LinkedChestStorage.hasUnreachableAmount(network, entry.itemKey())) {
                player.sendMessage(LegacyColors.color(
                        "#FF5555That item is in a linked chest that is not loaded right now."));
            } else {
                // Taken by someone else since this page was drawn.
                render();
            }
            return;
        }
        long withdrawAmount = switch (clickType) {
            case RIGHT, SHIFT_RIGHT -> 1;
            case SHIFT_LEFT -> Math.min(available, DISPLAY_STACK_SIZE);
            // Combined display uses amount 1 in-slot; left-click still takes up to a stack.
            default -> combineStacks
                    ? Math.min(available, DISPLAY_STACK_SIZE)
                    : Math.min(entry.stackAmount(), available);
        };
        long taken = network.extract(entry.itemKey(), withdrawAmount);
        if (taken <= 0) {
            return;
        }
        ItemStack give = entry.template().clone();
        while (taken > 0) {
            int stackSize = (int) Math.min(taken, Math.min(DISPLAY_STACK_SIZE, give.getMaxStackSize()));
            give.setAmount(stackSize);
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(give.clone());
            long notAdded = leftover.values().stream()
                    .filter(stack -> stack != null && !stack.getType().isAir())
                    .mapToLong(ItemStack::getAmount)
                    .sum();
            long added = stackSize - notAdded;
            taken -= added;
            if (notAdded > 0) {
                long returned = network.insert(give, notAdded);
                if (returned < notAdded) {
                    ItemStack drop = give.clone();
                    drop.setAmount((int) (notAdded - returned));
                    player.getWorld().dropItemNaturally(player.getLocation(), drop);
                }
                player.sendMessage(LegacyColors.color("#FF5555Your inventory is full."));
                break;
            }
        }
        render();
    }

    private void openSearchPrompt() {
        player.closeInventory();
        ensureChatFilterListener();
        PENDING_CHAT.put(player.getUniqueId(), new PendingFilter(this));
        player.sendMessage(LegacyColors.color("#FFED6AEnter a search filter in chat."));
        player.sendMessage(LegacyColors.color("#AAAAAAType 'clear' to clear, or 'cancel' to abort."));
    }

    private static void ensureChatFilterListener() {
        if (chatFilterListener != null) {
            return;
        }
        chatFilterListener = new ChatFilterListener();
        Restored.getInstance().registerListener(chatFilterListener);
    }

    public record DisplayEntry(String itemKey, ItemStack template, long stackAmount, long totalAmount) {
    }

    private record PendingFilter(NetworkItemsGui gui) {
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
                NetworkItemsGui gui = pending.gui();
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

    /**
     * Releases any linked-chunk tickets when the player leaves this view for good. Navigating to
     * a sub-menu or opening the chat search prompt also fires a close, so the tickets are kept if
     * another Restored GUI is opening in the same tick — dropping them there would let the chunks
     * unload and the linked-chest items vanish partway through the session.
     */
    @Override
    public void handleClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        java.util.UUID playerId = player.getUniqueId();
        Bukkit.getScheduler().runTask(Restored.getInstance(), () -> {
            if (!player.isOnline()) {
                LinkedChestStorage.releaseLease(playerId);
                return;
            }
            // The chat search prompt closes the inventory and leaves none open while the player
            // types; the lease has to survive that or the results come back with items missing.
            if (PENDING_CHAT.containsKey(playerId)) {
                return;
            }
            org.bukkit.inventory.InventoryHolder current =
                    player.getOpenInventory().getTopInventory().getHolder();
            if (!(current instanceof AbstractInventoryGui)) {
                LinkedChestStorage.releaseLease(playerId);
            }
        });
    }

}
