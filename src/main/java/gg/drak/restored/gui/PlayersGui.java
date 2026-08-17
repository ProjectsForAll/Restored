package gg.drak.restored.gui;

import host.plas.bou.gui.CornerColor;
import host.plas.bou.gui.GuiConfig;
import host.plas.bou.gui.GuiLayout;

import de.rapha149.signgui.SignGUI;
import de.rapha149.signgui.SignGUIResult;
import gg.drak.restored.Restored;
import gg.drak.restored.data.Network;
import gg.drak.restored.util.LegacyColors;
import gg.drak.restored.util.PlayerHeadService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class PlayersGui extends AbstractInventoryGui {
    private static final int PREV_SLOT = 45;
    private static final int SEARCH_SLOT = 48;
    private static final int BACK_SLOT = 49;
    private static final int NEXT_SLOT = 53;

    private static final ConcurrentHashMap<UUID, PendingSearch> PENDING_CHAT = new ConcurrentHashMap<>();
    private static ChatFallbackListener chatFallbackListener;
    private static boolean closeListenerRegistered;

    private final Network network;
    private final Runnable backAction;
    private int page;
    private String searchFilter;

    /** Sort key -> entry; keeps heads ordered as they arrive. */
    private final ConcurrentSkipListMap<String, PlayerEntry> discovered = new ConcurrentSkipListMap<>();
    private final AtomicInteger loadGeneration = new AtomicInteger();
    private final AtomicBoolean open = new AtomicBoolean(false);
    private final AtomicBoolean loading = new AtomicBoolean(false);
    private final AtomicBoolean refreshQueued = new AtomicBoolean(false);

    public PlayersGui(Player player, Network network, Runnable backAction) {
        this(player, network, backAction, "");
    }

    public PlayersGui(Player player, Network network, Runnable backAction, String searchFilter) {
        super(GuiConfig.builder(player).cornerColor(CornerColor.YELLOW).backSlot(BACK_SLOT).build());
        this.network = network;
        this.backAction = backAction;
        this.page = 0;
        this.searchFilter = searchFilter == null ? "" : searchFilter;
        ensureCloseListener();
    }

    @Override
    public void open() {
        open.set(true);
        discovered.clear();
        renderShell();
        startAsyncDiscovery();
    }

    private void renderShell() {
        ItemStack[] contents = beginShell(GuiLayout.SIZE_LARGE, "#FFED6A&lManage Players");
        placeChrome(contents, 0);
        bindSlot(SEARCH_SLOT, "search");
        bindSlot(BACK_SLOT, "back");
        // Loading hint in first content slot.
        int[] slots = GuiLayout.listContentSlots(GuiLayout.SIZE_LARGE);
        if (slots.length > 0) {
            contents[slots[0]] = GuiItems.button(
                    org.bukkit.Material.GRAY_STAINED_GLASS_PANE,
                    "#bdc8c9Loading players...",
                    List.of("#AAAAAAPlayer heads will appear as they are discovered.")
            );
        }
        finishAndOpen(contents);
    }

    private void startAsyncDiscovery() {
        int generation = loadGeneration.incrementAndGet();
        loading.set(true);
        PlayerHeadService.clearExpired();

        PlayerHeadService.discoverOfflinePlayersAsync(offlinePlayers -> {
            if (!isActive(generation)) {
                return;
            }
            // Hand off filtering + head resolves to async workers so the sync callback stays light.
            Bukkit.getScheduler().runTaskAsynchronously(Restored.getInstance(), () -> {
                if (!isActive(generation)) {
                    return;
                }
                List<OfflinePlayer> matched = filterPlayers(offlinePlayers);
                if (matched.isEmpty()) {
                    Bukkit.getScheduler().runTask(Restored.getInstance(), () -> {
                        if (!isActive(generation)) {
                            return;
                        }
                        loading.set(false);
                        refreshContent();
                    });
                    return;
                }

                AtomicInteger remaining = new AtomicInteger(matched.size());
                for (OfflinePlayer offline : matched) {
                    if (!isActive(generation)) {
                        return;
                    }
                    UUID uuid = offline.getUniqueId();
                    String fallbackName = offline.getName() != null ? offline.getName() : uuid.toString();
                    PlayerHeadService.resolveAsync(uuid, fallbackName, cached -> {
                        if (isActive(generation)) {
                            addDiscovered(cached);
                        }
                        if (remaining.decrementAndGet() <= 0 && isActive(generation)) {
                            loading.set(false);
                            queueRefresh();
                        }
                    });
                }
            });
        });
    }

    private List<OfflinePlayer> filterPlayers(List<OfflinePlayer> offlinePlayers) {
        String filter = searchFilter == null ? "" : searchFilter.toLowerCase(Locale.ROOT);
        List<OfflinePlayer> matched = new ArrayList<>();
        for (OfflinePlayer offline : offlinePlayers) {
            String name = offline.getName();
            if (name == null) {
                continue;
            }
            if (!filter.isBlank() && !name.toLowerCase(Locale.ROOT).contains(filter)) {
                continue;
            }
            matched.add(offline);
        }
        matched.sort(Comparator.comparing(p -> p.getName() != null ? p.getName().toLowerCase(Locale.ROOT) : p.getUniqueId().toString()));
        return matched;
    }

    private void addDiscovered(PlayerHeadService.CachedPlayer cached) {
        String name = cached.name() != null ? cached.name() : cached.uuid().toString();
        String sortKey = name.toLowerCase(Locale.ROOT) + "\0" + cached.uuid();
        ItemStack head = decorateHead(cached.headClone(), name, cached.uuid());
        discovered.put(sortKey, new PlayerEntry(cached.uuid(), name, head));
        queueRefresh();
    }

    /** Coalesce rapid discoveries into at most one inventory redraw per tick. */
    private void queueRefresh() {
        if (!refreshQueued.compareAndSet(false, true)) {
            return;
        }
        Bukkit.getScheduler().runTask(Restored.getInstance(), () -> {
            refreshQueued.set(false);
            refreshContent();
        });
    }

    private ItemStack decorateHead(ItemStack head, String name, UUID uuid) {
        ItemMeta meta = head.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(LegacyColors.color("#FFED6A" + name));
            meta.setLore(List.of(
                    LegacyColors.color("#bdc8c9Role: #AAAAAA" + network.getRole(uuid).displayName()),
                    "",
                    LegacyColors.color("#bdc8c9Click to edit permissions.")
            ));
            head.setItemMeta(meta);
        }
        return head;
    }

    private void refreshContent() {
        if (inventory == null || !open.get()) {
            return;
        }
        if (!player.isOnline() || !player.getOpenInventory().getTopInventory().equals(inventory)) {
            return;
        }

        List<PlayerEntry> entries = new ArrayList<>(discovered.values());
        int perPage = perPage(GuiLayout.SIZE_LARGE);
        int totalPages = Math.max(1, (int) Math.ceil(entries.size() / (double) Math.max(1, perPage)));
        if (page >= totalPages) {
            page = Math.max(0, totalPages - 1);
        }

        int[] slots = GuiLayout.listContentSlots(GuiLayout.SIZE_LARGE);
        for (int slot : slots) {
            inventory.setItem(slot, null);
            slotKeys.remove(slot);
        }

        if (entries.isEmpty()) {
            if (loading.get()) {
                inventory.setItem(slots[0], GuiItems.button(
                        org.bukkit.Material.GRAY_STAINED_GLASS_PANE,
                        "#bdc8c9Loading players...",
                        List.of("#AAAAAAPlayer heads will appear as they are discovered.")
                ));
            } else {
                inventory.setItem(slots[0], GuiItems.button(
                        org.bukkit.Material.BARRIER,
                        "#FF5555No players found",
                        List.of("#bdc8c9Try a different search.")
                ));
            }
        } else {
            int start = page * perPage;
            int end = Math.min(start + perPage, entries.size());
            for (int i = start, slotIndex = 0; i < end && slotIndex < slots.length; i++, slotIndex++) {
                PlayerEntry entry = entries.get(i);
                inventory.setItem(slots[slotIndex], entry.head());
                bindSlot(slots[slotIndex], "player:" + entry.uuid());
            }
        }

        // Refresh chrome / pagination without recreating the inventory.
        ItemStack[] chrome = new ItemStack[inventory.getSize()];
        placeChrome(chrome, entries.size());
        for (int slot : new int[]{PREV_SLOT, SEARCH_SLOT, BACK_SLOT, NEXT_SLOT}) {
            inventory.setItem(slot, chrome[slot]);
        }
        // Re-bind chrome keys
        slotKeys.entrySet().removeIf(e -> {
            int s = e.getKey();
            return s == PREV_SLOT || s == SEARCH_SLOT || s == BACK_SLOT || s == NEXT_SLOT
                    || "__pageprev".equals(e.getValue()) || "__pagenext".equals(e.getValue())
                    || "search".equals(e.getValue()) || "back".equals(e.getValue());
        });
        if (chrome[PREV_SLOT] != null) {
            bindSlot(PREV_SLOT, "__pageprev");
        }
        bindSlot(SEARCH_SLOT, "search");
        bindSlot(BACK_SLOT, "back");
        if (chrome[NEXT_SLOT] != null) {
            bindSlot(NEXT_SLOT, "__pagenext");
        }
    }

    private void placeChrome(ItemStack[] contents, int totalEntries) {
        contents[SEARCH_SLOT] = GuiItems.searchButton(searchFilter);
        contents[BACK_SLOT] = GuiItems.returnButton();

        int perPage = perPage(GuiLayout.SIZE_LARGE);
        int totalPages = Math.max(1, (int) Math.ceil(totalEntries / (double) Math.max(1, perPage)));
        if (page > 0) {
            contents[PREV_SLOT] = GuiItems.pagePreviousButton(page);
        }
        if (page + 1 < totalPages) {
            contents[NEXT_SLOT] = GuiItems.pageNextButton(page + 2);
        }
    }

    private boolean isActive(int generation) {
        return open.get() && loadGeneration.get() == generation;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(inventory)) {
            return;
        }
        event.setCancelled(true);
        String key = getKeyAtSlot(event.getRawSlot());
        if (key == null) {
            return;
        }

        if ("back".equals(key)) {
            closeGui();
            backAction.run();
            return;
        }
        if ("__pageprev".equals(key)) {
            page = Math.max(0, page - 1);
            refreshContent();
            return;
        }
        if ("__pagenext".equals(key)) {
            page++;
            refreshContent();
            return;
        }
        if ("search".equals(key)) {
            openSearch();
            return;
        }
        if (key.startsWith("player:")) {
            UUID targetId = UUID.fromString(key.substring(7));
            closeGui();
            new PlayerPermGui(player, network, targetId, () -> new PlayersGui(player, network, backAction, searchFilter).open()).open();
        }
    }

    private void closeGui() {
        open.set(false);
        loadGeneration.incrementAndGet();
        player.closeInventory();
    }

    private void openSearch() {
        open.set(false);
        loadGeneration.incrementAndGet();
        player.closeInventory();
        try {
            SignGUI.builder()
                    .setLines("", "^^^^^^^^^", "Enter name", "")
                    .callHandlerSynchronously(Restored.getInstance())
                    .setHandler((p, result) -> {
                        String query = extractSearchQuery(result);
                        Bukkit.getScheduler().runTask(Restored.getInstance(), () ->
                                new PlayersGui(p, network, backAction, query).open());
                        return List.of();
                    })
                    .build()
                    .open(player);
        } catch (Exception e) {
            Restored.getInstance().logWarning("SignGUI unavailable, falling back to chat: " + e.getMessage());
            ensureChatFallbackListener();
            PENDING_CHAT.put(player.getUniqueId(), new PendingSearch(network, backAction));
            player.sendMessage(LegacyColors.color("#FFED6AEnter a player name in chat, or type 'cancel'."));
        }
    }

    private static void ensureChatFallbackListener() {
        if (chatFallbackListener != null) {
            return;
        }
        chatFallbackListener = new ChatFallbackListener();
        Restored.getInstance().registerListener(chatFallbackListener);
    }

    private static void ensureCloseListener() {
        if (closeListenerRegistered) {
            return;
        }
        closeListenerRegistered = true;
        Restored.getInstance().registerListener(new Listener() {
            @EventHandler
            public void onClose(InventoryCloseEvent event) {
                if (!(event.getPlayer() instanceof Player)) {
                    return;
                }
                if (!(event.getInventory().getHolder() instanceof PlayersGui gui)) {
                    return;
                }
                gui.open.set(false);
                gui.loadGeneration.incrementAndGet();
            }
        });
    }

    private static String extractSearchQuery(SignGUIResult result) {
        for (String line : result.getLinesWithoutColor()) {
            if (line == null || line.isBlank() || line.contains("^") || line.equalsIgnoreCase("Enter name")) {
                continue;
            }
            return line.trim();
        }
        return "";
    }

    private record PlayerEntry(UUID uuid, String name, ItemStack head) {
    }

    private record PendingSearch(Network network, Runnable backAction) {
    }

    private static final class ChatFallbackListener implements Listener {
        @EventHandler(priority = EventPriority.LOWEST)
        public void onChat(AsyncPlayerChatEvent event) {
            PendingSearch pending = PENDING_CHAT.remove(event.getPlayer().getUniqueId());
            if (pending == null) {
                return;
            }
            event.setCancelled(true);
            String message = event.getMessage().trim();
            Player p = event.getPlayer();
            Bukkit.getScheduler().runTask(Restored.getInstance(), () -> {
                if (message.equalsIgnoreCase("cancel")) {
                    p.sendMessage(LegacyColors.color("#FF5555Cancelled."));
                    pending.backAction().run();
                    return;
                }
                new PlayersGui(p, pending.network(), pending.backAction(), message).open();
            });
        }
    }
}
