package gg.drak.restored.gui.pocket;

import gg.drak.restored.data.Network;
import gg.drak.restored.data.NetworkManager;
import gg.drak.restored.data.PocketAugmentType;
import gg.drak.restored.gui.AbstractInventoryGui;
import gg.drak.restored.gui.GuiItems;
import gg.drak.restored.items.PocketLinkItem;
import gg.drak.restored.util.LegacyColors;
import host.plas.bou.gui.CornerColor;
import host.plas.bou.gui.GuiConfig;
import host.plas.bou.gui.GuiLayout;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Configuration screen for the Pocket Link's network-backed arrow supply. */
public class QuiverAugmentGui extends AbstractInventoryGui implements PocketLinkGuiBound {
    private static final int FILTER_ROW = 1;
    private static final int MODE_SLOT = 29;
    private static final int META_SLOT = 31;

    private final UUID linkId;

    public QuiverAugmentGui(Player player, UUID linkId) {
        super(GuiConfig.builder(player).cornerColor(CornerColor.YELLOW).backSlot(49).build());
        this.linkId = linkId;
    }

    @Override
    public UUID getPocketLinkId() {
        return linkId;
    }

    private ItemStack linkItem() {
        return PocketLinkItem.findInInventory(player, linkId);
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

        ItemStack[] contents = beginShell(GuiLayout.SIZE_LARGE, "#FFED6A&lQuiver Augment");
        List<ItemStack> filters = PocketLinkItem.getQuiverFilters(link);
        for (int col = 1; col <= PocketLinkItem.FILTER_SLOTS; col++) {
            int slot = FILTER_ROW * 9 + col;
            int index = col - 1;
            ItemStack filter = filters.get(index);
            if (filter != null && !filter.getType().isAir()) {
                contents[slot] = withHint(filter, List.of(
                        "#bdc8c9Click to pick up.",
                        "#bdc8c9Place arrows from your cursor / inventory."
                ));
            } else {
                contents[slot] = GuiItems.button(
                        Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                        "#AAAAAAArow Filter " + (index + 1),
                        List.of(
                                "#bdc8c9Click to select an arrow from the network.",
                                "#bdc8c9Or place an arrow from your cursor / inventory."
                        )
                );
            }
            bindSlot(slot, "filter:" + index);
        }

        PocketLinkItem.FeedFilterMode mode = PocketLinkItem.getQuiverFilterMode(link);
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

        PocketLinkItem.FeedMetaMode meta = PocketLinkItem.getQuiverMetaMode(link);
        contents[META_SLOT] = GuiItems.button(
                Material.COMMAND_BLOCK,
                "#FFED6A&lArrow Meta Data",
                List.of(
                        "#bdc8c9Click to toggle arrow matching.",
                        "",
                        modeLine(meta == PocketLinkItem.FeedMetaMode.RESPECT, "Respect Arrow Meta Data"),
                        modeLine(meta == PocketLinkItem.FeedMetaMode.ANY, "Allow Any Arrow Meta Data")
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
        ItemMeta meta = copy.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.getLore() == null ? new ArrayList<>() : new ArrayList<>(meta.getLore());
            lore.add("");
            hints.forEach(line -> lore.add(LegacyColors.color(line)));
            meta.setLore(lore);
            copy.setItemMeta(meta);
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
            event.setCancelled(true);
            player.closeInventory();
            return;
        }
        if (event.getClickedInventory().equals(player.getInventory())) {
            if (!event.isShiftClick()) {
                return;
            }
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (isArrow(clicked) && placeIntoFilterSlots(clicked) > 0) {
                if (clicked.getAmount() <= 0) {
                    event.setCurrentItem(null);
                }
                render();
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
        } else if ("mode".equals(key)) {
            PocketLinkItem.setQuiverFilterMode(link, PocketLinkItem.getQuiverFilterMode(link).toggle());
            render();
        } else if ("meta".equals(key)) {
            PocketLinkItem.setQuiverMetaMode(link, PocketLinkItem.getQuiverMetaMode(link).toggle());
            render();
        } else if (key.startsWith("filter:")) {
            handleFilterSlotClick(link, Integer.parseInt(key.substring("filter:".length())), event);
        }
    }

    private void handleFilterSlotClick(ItemStack link, int index, InventoryClickEvent event) {
        if (index < 0 || index >= PocketLinkItem.FILTER_SLOTS) {
            return;
        }
        List<ItemStack> filters = PocketLinkItem.getQuiverFilters(link);
        ItemStack existing = filters.get(index);
        ItemStack cursor = event.getCursor();
        boolean hasCursor = isArrow(cursor);
        if (cursor != null && !cursor.getType().isAir() && !hasCursor) {
            player.sendMessage(LegacyColors.color("#FF5555Quiver filters only accept arrows."));
            return;
        }
        if (hasCursor) {
            if (existing == null || existing.getType().isAir()) {
                filters.set(index, cursor.clone());
                event.getView().setCursor(null);
            } else if (existing.isSimilar(cursor)) {
                int move = Math.min(existing.getMaxStackSize() - existing.getAmount(), cursor.getAmount());
                if (move > 0) {
                    existing.setAmount(existing.getAmount() + move);
                    cursor.setAmount(cursor.getAmount() - move);
                    if (cursor.getAmount() <= 0) {
                        event.getView().setCursor(null);
                    }
                }
            } else {
                filters.set(index, cursor.clone());
                event.getView().setCursor(existing.clone());
            }
            PocketLinkItem.setQuiverFilters(link, filters);
            render();
            return;
        }
        if (existing != null && !existing.getType().isAir()) {
            filters.set(index, null);
            PocketLinkItem.setQuiverFilters(link, filters);
            event.getView().setCursor(existing.clone());
            render();
            return;
        }
        openFilterPicker(index);
    }

    private int placeIntoFilterSlots(ItemStack fromPlayer) {
        List<ItemStack> filters = PocketLinkItem.getQuiverFilters(linkItem());
        int moved = 0;
        for (int i = 0; i < filters.size(); i++) {
            ItemStack existing = filters.get(i);
            if (!isArrow(existing) || !existing.isSimilar(fromPlayer)) {
                continue;
            }
            int move = Math.min(existing.getMaxStackSize() - existing.getAmount(), fromPlayer.getAmount());
            if (move > 0) {
                existing.setAmount(existing.getAmount() + move);
                fromPlayer.setAmount(fromPlayer.getAmount() - move);
                moved += move;
            }
        }
        for (int i = 0; i < filters.size() && fromPlayer.getAmount() > 0; i++) {
            if (filters.get(i) == null || filters.get(i).getType().isAir()) {
                filters.set(i, fromPlayer.clone());
                moved += fromPlayer.getAmount();
                fromPlayer.setAmount(0);
            }
        }
        if (moved > 0) {
            PocketLinkItem.setQuiverFilters(linkItem(), filters);
        }
        return moved;
    }

    private void openFilterPicker(int index) {
        Optional<UUID> networkId = PocketLinkItem.getLinkedNetworkId(linkItem());
        if (networkId.isEmpty()) {
            player.sendMessage(LegacyColors.color("#FF5555Link a network first."));
            return;
        }
        Network network = NetworkManager.get(networkId.get());
        if (network == null) {
            player.sendMessage(LegacyColors.color("#FF5555Linked network no longer exists."));
            return;
        }
        new ArrowFilterPickerGui(player, network, linkId, picked -> {
            ItemStack current = linkItem();
            if (current == null || picked == null) {
                new QuiverAugmentGui(player, linkId).open();
                return;
            }
            List<ItemStack> filters = PocketLinkItem.getQuiverFilters(current);
            filters.set(index, picked);
            PocketLinkItem.setQuiverFilters(current, filters);
            new QuiverAugmentGui(player, linkId).open();
        }).open();
    }

    public static boolean isArrow(ItemStack stack) {
        return stack != null && switch (stack.getType()) {
            case ARROW, SPECTRAL_ARROW, TIPPED_ARROW -> true;
            default -> false;
        };
    }
}
