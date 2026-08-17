package gg.drak.restored.gui.pocket;

import host.plas.bou.gui.CornerColor;
import host.plas.bou.gui.GuiConfig;
import host.plas.bou.gui.GuiLayout;

import gg.drak.restored.data.Network;
import gg.drak.restored.data.NetworkManager;
import gg.drak.restored.gui.AbstractInventoryGui;
import gg.drak.restored.gui.GuiItems;
import gg.drak.restored.items.PocketLinkItem;
import gg.drak.restored.util.LegacyColors;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class FeedingAugmentGui extends AbstractInventoryGui implements PocketLinkGuiBound {
    private static final int FILTER_ROW = 1;
    private static final int MODE_SLOT = 29;
    private static final int SORT_SLOT = 31;
    private static final int DIR_SLOT = 33;
    private static final int META_SLOT = 34;

    private final UUID linkId;

    public FeedingAugmentGui(Player player, UUID linkId) {
        super(GuiConfig.builder(player).cornerColor(CornerColor.YELLOW).backSlot(49).build());
        this.linkId = linkId;
    }

    private ItemStack linkItem() {
        return PocketLinkItem.findInInventory(player, linkId);
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
        ItemStack link = linkItem();
        if (link == null) {
            player.sendMessage(LegacyColors.color("#FF5555Pocket Link not found."));
            return;
        }
        PocketLinkItem.markGuiOpen(player, linkId);

        ItemStack[] contents = beginShell(GuiLayout.SIZE_LARGE, "#FFED6A&lFeeding Augment");
        List<ItemStack> filters = PocketLinkItem.getFeedFilters(link);

        for (int col = 1; col <= PocketLinkItem.FILTER_SLOTS; col++) {
            int slot = FILTER_ROW * 9 + col;
            int index = col - 1;
            ItemStack filter = filters.get(index);
            if (filter != null && !filter.getType().isAir()) {
                contents[slot] = withHint(filter, List.of(
                        "#bdc8c9Click to pick up.",
                        "#bdc8c9Or place items from your cursor / inventory."
                ));
            } else {
                contents[slot] = GuiItems.button(
                        Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                        "#AAAAAAFilter Slot " + (index + 1),
                        List.of(
                                "#bdc8c9Click to select from the linked network.",
                                "#bdc8c9Or place an item from your inventory / cursor."
                        )
                );
            }
            bindSlot(slot, "filter:" + index);
        }

        PocketLinkItem.FeedFilterMode mode = PocketLinkItem.getFeedFilterMode(link);
        contents[MODE_SLOT] = GuiItems.button(
                Material.PAPER,
                "#FFED6A&lFilter Mode",
                List.of(
                        "#bdc8c9Click to toggle whitelist/blacklist.",
                        "",
                        modeLine(mode == PocketLinkItem.FeedFilterMode.WHITELIST, "Whitelist"),
                        modeLine(mode == PocketLinkItem.FeedFilterMode.BLACKLIST, "Blacklist (default)")
                )
        );
        bindSlot(MODE_SLOT, "mode");

        PocketLinkItem.FeedSortMode sort = PocketLinkItem.getFeedSortMode(link);
        contents[SORT_SLOT] = GuiItems.button(
                Material.HOPPER,
                "#FFED6A&lSort By",
                List.of(
                        "#bdc8c9Click to cycle sort field.",
                        "",
                        modeLine(sort == PocketLinkItem.FeedSortMode.SLOT, "Slot ID (default)"),
                        modeLine(sort == PocketLinkItem.FeedSortMode.AMOUNT, "Item Amount"),
                        modeLine(sort == PocketLinkItem.FeedSortMode.NAME, "Name")
                )
        );
        bindSlot(SORT_SLOT, "sort");

        PocketLinkItem.FeedSortDir dir = PocketLinkItem.getFeedSortDir(link);
        contents[DIR_SLOT] = GuiItems.button(
                Material.IRON_NUGGET,
                "#FFED6A&lSort Direction",
                List.of(
                        "#bdc8c9Click to toggle direction.",
                        "",
                        modeLine(dir == PocketLinkItem.FeedSortDir.ASCENDING, "Ascending"),
                        modeLine(dir == PocketLinkItem.FeedSortDir.DESCENDING, "Descending (default)")
                )
        );
        bindSlot(DIR_SLOT, "dir");

        PocketLinkItem.FeedMetaMode meta = PocketLinkItem.getFeedMetaMode(link);
        contents[META_SLOT] = GuiItems.button(
                Material.COMMAND_BLOCK,
                "#FFED6A&lMeta Data",
                List.of(
                        "#bdc8c9Click to toggle meta matching.",
                        "",
                        modeLine(meta == PocketLinkItem.FeedMetaMode.RESPECT, "Respect Meta Data"),
                        modeLine(meta == PocketLinkItem.FeedMetaMode.ANY, "Allow Any Meta Data")
                )
        );
        bindSlot(META_SLOT, "meta");

        placeReturnButton(contents, "back");
        fillUnusedWithBlack(contents);
        finishAndOpen(contents);
    }

    private static String modeLine(boolean selected, String label) {
        return (selected ? "#00FC88▶ " : "#AAAAAA  ") + label;
    }

    private static ItemStack withHint(ItemStack stack, List<String> hints) {
        ItemStack copy = stack.clone();
        org.bukkit.inventory.meta.ItemMeta itemMeta = copy.getItemMeta();
        if (itemMeta != null) {
            List<String> lore = itemMeta.getLore() == null ? new ArrayList<>() : new ArrayList<>(itemMeta.getLore());
            lore.add("");
            for (String hint : hints) {
                lore.add(LegacyColors.color(hint));
            }
            itemMeta.setLore(lore);
            copy.setItemMeta(itemMeta);
        }
        return copy;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null) {
            return;
        }

        ItemStack link = linkItem();
        if (link == null) {
            player.closeInventory();
            return;
        }

        if (event.getClickedInventory().equals(player.getInventory())) {
            if (event.isShiftClick()) {
                ItemStack clicked = event.getCurrentItem();
                if (clicked == null || clicked.getType().isAir()) {
                    return;
                }
                int placed = placeIntoFilterSlots(link, clicked);
                if (placed > 0) {
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
        String key = getKeyAtSlot(event.getRawSlot());
        if (key == null) {
            return;
        }
        if ("back".equals(key)) {
            new PocketAugmentsGui(player, linkId).open();
            return;
        }
        if ("mode".equals(key)) {
            PocketLinkItem.setFeedFilterMode(link, PocketLinkItem.getFeedFilterMode(link).toggle());
            render();
            return;
        }
        if ("sort".equals(key)) {
            PocketLinkItem.setFeedSortMode(link, PocketLinkItem.getFeedSortMode(link).next());
            render();
            return;
        }
        if ("dir".equals(key)) {
            PocketLinkItem.setFeedSortDir(link, PocketLinkItem.getFeedSortDir(link).toggle());
            render();
            return;
        }
        if ("meta".equals(key)) {
            PocketLinkItem.setFeedMetaMode(link, PocketLinkItem.getFeedMetaMode(link).toggle());
            render();
            return;
        }
        if (key.startsWith("filter:")) {
            int index = Integer.parseInt(key.substring("filter:".length()));
            handleFilterSlotClick(link, index, event);
        }
    }

    private void handleFilterSlotClick(ItemStack link, int index, InventoryClickEvent event) {
        List<ItemStack> filters = PocketLinkItem.getFeedFilters(link);
        ItemStack existing = filters.get(index);
        ItemStack cursor = event.getCursor();
        boolean hasExisting = existing != null && !existing.getType().isAir();
        boolean hasCursor = cursor != null && !cursor.getType().isAir();

        if (hasCursor) {
            if (event.isRightClick()) {
                placeOneFromCursor(link, filters, index, existing, hasExisting, cursor, event);
            } else {
                placeAllFromCursor(link, filters, index, existing, hasExisting, cursor, event);
            }
            return;
        }

        if (hasExisting) {
            filters.set(index, null);
            PocketLinkItem.setFeedFilters(link, filters);
            event.getView().setCursor(existing.clone());
            render();
            return;
        }

        openFilterPicker(index);
    }

    private void placeAllFromCursor(
            ItemStack link,
            List<ItemStack> filters,
            int index,
            ItemStack existing,
            boolean hasExisting,
            ItemStack cursor,
            InventoryClickEvent event
    ) {
        if (!hasExisting) {
            filters.set(index, cursor.clone());
            PocketLinkItem.setFeedFilters(link, filters);
            event.getView().setCursor(null);
            render();
            return;
        }
        if (existing.isSimilar(cursor)) {
            int space = existing.getMaxStackSize() - existing.getAmount();
            if (space <= 0) {
                player.sendMessage(LegacyColors.color("#FF5555That filter slot is full."));
                return;
            }
            int move = Math.min(space, cursor.getAmount());
            existing.setAmount(existing.getAmount() + move);
            filters.set(index, existing);
            PocketLinkItem.setFeedFilters(link, filters);
            cursor.setAmount(cursor.getAmount() - move);
            if (cursor.getAmount() <= 0) {
                event.getView().setCursor(null);
            }
            render();
            return;
        }
        filters.set(index, cursor.clone());
        PocketLinkItem.setFeedFilters(link, filters);
        event.getView().setCursor(existing.clone());
        render();
    }

    private void placeOneFromCursor(
            ItemStack link,
            List<ItemStack> filters,
            int index,
            ItemStack existing,
            boolean hasExisting,
            ItemStack cursor,
            InventoryClickEvent event
    ) {
        if (!hasExisting) {
            ItemStack one = cursor.clone();
            one.setAmount(1);
            filters.set(index, one);
            PocketLinkItem.setFeedFilters(link, filters);
            cursor.setAmount(cursor.getAmount() - 1);
            if (cursor.getAmount() <= 0) {
                event.getView().setCursor(null);
            }
            render();
            return;
        }
        if (existing.isSimilar(cursor)) {
            if (existing.getAmount() >= existing.getMaxStackSize()) {
                player.sendMessage(LegacyColors.color("#FF5555That filter slot is full."));
                return;
            }
            existing.setAmount(existing.getAmount() + 1);
            filters.set(index, existing);
            PocketLinkItem.setFeedFilters(link, filters);
            cursor.setAmount(cursor.getAmount() - 1);
            if (cursor.getAmount() <= 0) {
                event.getView().setCursor(null);
            }
            render();
            return;
        }
        filters.set(index, cursor.clone());
        PocketLinkItem.setFeedFilters(link, filters);
        event.getView().setCursor(existing.clone());
        render();
    }

    private int placeIntoFilterSlots(ItemStack link, ItemStack fromPlayer) {
        if (fromPlayer == null || fromPlayer.getType().isAir()) {
            return 0;
        }
        List<ItemStack> filters = PocketLinkItem.getFeedFilters(link);
        int moved = 0;

        for (int i = 0; i < filters.size(); i++) {
            ItemStack existing = filters.get(i);
            if (existing == null || existing.getType().isAir() || !existing.isSimilar(fromPlayer)) {
                continue;
            }
            int space = existing.getMaxStackSize() - existing.getAmount();
            if (space <= 0) {
                continue;
            }
            int move = Math.min(space, fromPlayer.getAmount());
            existing.setAmount(existing.getAmount() + move);
            filters.set(i, existing);
            fromPlayer.setAmount(fromPlayer.getAmount() - move);
            moved += move;
            if (fromPlayer.getAmount() <= 0) {
                PocketLinkItem.setFeedFilters(link, filters);
                return moved;
            }
        }

        for (int i = 0; i < filters.size(); i++) {
            ItemStack existing = filters.get(i);
            if (existing != null && !existing.getType().isAir()) {
                continue;
            }
            filters.set(i, fromPlayer.clone());
            moved += fromPlayer.getAmount();
            fromPlayer.setAmount(0);
            PocketLinkItem.setFeedFilters(link, filters);
            return moved;
        }

        if (moved > 0) {
            PocketLinkItem.setFeedFilters(link, filters);
        }
        return moved;
    }

    private void openFilterPicker(int index) {
        ItemStack link = linkItem();
        if (link == null) {
            return;
        }
        Optional<UUID> networkId = PocketLinkItem.getLinkedNetworkId(link);
        if (networkId.isEmpty()) {
            player.sendMessage(LegacyColors.color("#FF5555Link a network first."));
            return;
        }
        Network network = NetworkManager.get(networkId.get());
        if (network == null) {
            player.sendMessage(LegacyColors.color("#FF5555Linked network no longer exists."));
            return;
        }
        final int filterIndex = index;
        new FeedingFilterPickerGui(player, network, linkId, picked -> {
            ItemStack current = linkItem();
            if (current == null) {
                return;
            }
            if (picked != null) {
                List<ItemStack> next = PocketLinkItem.getFeedFilters(current);
                ItemStack existing = next.get(filterIndex);
                if (existing != null && !existing.getType().isAir()) {
                    if (existing.isSimilar(picked)) {
                        int space = existing.getMaxStackSize() - existing.getAmount();
                        int move = Math.min(space, Math.max(1, picked.getAmount()));
                        existing.setAmount(existing.getAmount() + move);
                        next.set(filterIndex, existing);
                    } else {
                        next.set(filterIndex, picked);
                    }
                } else {
                    next.set(filterIndex, picked);
                }
                PocketLinkItem.setFeedFilters(current, next);
            }
            new FeedingAugmentGui(player, linkId).open();
        }).open();
    }
}
