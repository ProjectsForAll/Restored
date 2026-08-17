package gg.drak.restored.gui.pocket;

import host.plas.bou.gui.CornerColor;
import host.plas.bou.gui.GuiLayout;

import gg.drak.restored.data.PocketAugmentType;
import gg.drak.restored.gui.AbstractInventoryGui;
import gg.drak.restored.gui.GuiItems;
import gg.drak.restored.items.FeedingAugmentItem;
import gg.drak.restored.items.PocketAugmentItem;
import gg.drak.restored.items.PocketLinkItem;
import gg.drak.restored.items.RestoredItems;
import gg.drak.restored.util.LegacyColors;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PocketAugmentsGui extends AbstractInventoryGui implements PocketLinkGuiBound {
    private static final int COLUMNS_PER_PAGE = 7;
    private static final int INSTALL_ROW = 1;
    private static final int DESCRIPTOR_ROW = 2;
    private static final int OPEN_ROW = 3;

    private final UUID linkId;
    private int page;

    public PocketAugmentsGui(Player player, UUID linkId) {
        super(player, CornerColor.YELLOW);
        this.linkId = linkId;
        this.page = 0;
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

        ItemStack[] contents = beginShell(GuiLayout.SIZE_LARGE, "#FFED6A&lPocket Augments");
        PocketAugmentType[] all = PocketAugmentType.values();
        int totalPages = Math.max(1, (int) Math.ceil(all.length / (double) COLUMNS_PER_PAGE));
        if (page >= totalPages) {
            page = Math.max(0, totalPages - 1);
        }
        int start = page * COLUMNS_PER_PAGE;
        int end = Math.min(start + COLUMNS_PER_PAGE, all.length);

        for (int i = start; i < end; i++) {
            PocketAugmentType type = all[i];
            int col = (i - start) + 1;
            int installSlot = INSTALL_ROW * 9 + col;
            int descriptorSlot = DESCRIPTOR_ROW * 9 + col;
            int openSlot = OPEN_ROW * 9 + col;
            boolean installed = PocketLinkItem.hasAugment(link, type);

            if (installed) {
                // Display-only icon (not a real augment item) so stacks cannot merge into this slot.
                contents[installSlot] = GuiItems.button(
                        type.getIcon(),
                        "#00FC88&l" + type.getDisplayName() + " Augment",
                        List.of(
                                "#bdc8c9Installed (1/1).",
                                "#AAAAAAClick with an empty cursor to uninstall."
                        )
                );
            } else {
                contents[installSlot] = GuiItems.button(
                        Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                        "#AAAAAAEmpty Pocket Augment Slot",
                        List.of(
                                "#bdc8c9Place a #FFED6A" + type.getDisplayName() + " Augment #bdc8c9here.",
                                "#AAAAAAOnly one augment per slot.",
                                "#AAAAAAShift-click from your inventory to install."
                        )
                );
            }
            bindSlot(installSlot, "install:" + type.name());

            contents[descriptorSlot] = GuiItems.button(
                    type.getIcon(),
                    "#FFED6A&l" + type.getDisplayName(),
                    List.of(
                            "#bdc8c9Pocket augment slot.",
                            installed ? "#00FC88Installed" : "#FF5555Not installed"
                    )
            );
            bindSlot(descriptorSlot, "desc:" + type.name());

            if (installed) {
                contents[openSlot] = GuiItems.button(
                        Material.LIME_STAINED_GLASS_PANE,
                        "#00FC88&lOpen " + type.getDisplayName(),
                        List.of("#bdc8c9Click to configure this pocket augment.")
                );
            } else {
                contents[openSlot] = GuiItems.button(
                        Material.GRAY_STAINED_GLASS_PANE,
                        "#AAAAAALocked",
                        List.of("#bdc8c9Install the augment above to unlock.")
                );
            }
            bindSlot(openSlot, "open:" + type.name());
        }

        placeReturnButton(contents, "back");
        int backSlot = resolveBackSlot(contents.length);
        int prevSlot = GuiLayout.pagePrevSlot(backSlot);
        int nextSlot = GuiLayout.pageNextSlot(backSlot);
        if (page > 0) {
            contents[prevSlot] = GuiItems.pagePreviousButton(page);
            bindSlot(prevSlot, "__pageprev");
        }
        if (page + 1 < totalPages) {
            contents[nextSlot] = GuiItems.pageNextButton(page + 2);
            bindSlot(nextSlot, "__pagenext");
        }
        fillUnusedWithBlack(contents);
        finishAndOpen(contents);
    }

    private static ItemStack createAugmentItem(PocketAugmentType type) {
        if (type == PocketAugmentType.FEEDING) {
            return FeedingAugmentItem.create();
        }
        return PocketAugmentItem.create(type);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        ItemStack link = linkItem();
        if (link == null) {
            event.setCancelled(true);
            player.closeInventory();
            return;
        }

        if (event.getClickedInventory() != null && event.getClickedInventory().equals(player.getInventory())) {
            if (event.isShiftClick()) {
                // Always cancel — otherwise vanilla dumps the stack into empty GUI slots and it vanishes on re-render.
                event.setCancelled(true);
                ItemStack clicked = event.getCurrentItem();
                PocketAugmentType type = PocketAugmentType.fromItemTypeTag(
                        RestoredItems.getType(clicked).orElse(null));
                if (type != null && tryInstall(link, type)) {
                    consumeOneFromSlot(event);
                    render();
                }
            }
            return;
        }

        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(inventory)) {
            return;
        }
        event.setCancelled(true);
        String key = getKeyAtSlot(event.getRawSlot());
        if (key == null) {
            return;
        }
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
            new PocketLinkGui(player, link).open();
            return;
        }
        if (key.startsWith("install:")) {
            PocketAugmentType type = PocketAugmentType.valueOf(key.substring("install:".length()));
            handleInstallClick(link, type, event);
            return;
        }
        if (key.startsWith("open:")) {
            PocketAugmentType type = PocketAugmentType.valueOf(key.substring("open:".length()));
            if (!PocketLinkItem.hasAugment(link, type)) {
                player.sendMessage(LegacyColors.color("#FF5555Install this pocket augment first."));
                return;
            }
            if (type == PocketAugmentType.FEEDING) {
                new FeedingAugmentGui(player, linkId).open();
            } else if (type == PocketAugmentType.QUIVER) {
                new QuiverAugmentGui(player, linkId).open();
            } else if (type == PocketAugmentType.BACKPACK) {
                new BackpackAugmentGui(player, linkId).open();
            }
        }
    }

    private void handleInstallClick(ItemStack link, PocketAugmentType type, InventoryClickEvent event) {
        ItemStack cursor = event.getCursor();
        if (PocketLinkItem.hasAugment(link, type)) {
            if (cursor != null && !cursor.getType().isAir()) {
                player.sendMessage(LegacyColors.color("#FF5555Remove the installed augment first."));
                return;
            }
            if (!PocketLinkItem.uninstallAugment(link, type)) {
                return;
            }
            ItemStack give = createAugmentItem(type);
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(give);
            if (!leftover.isEmpty()) {
                PocketLinkItem.installAugment(link, type);
                player.sendMessage(LegacyColors.color("#FF5555No inventory space."));
                return;
            }
            player.sendMessage(LegacyColors.color("#00FC88Uninstalled " + type.getDisplayName() + " Augment."));
            render();
            return;
        }
        if (cursor == null || cursor.getType().isAir()) {
            return;
        }
        PocketAugmentType cursorType = PocketAugmentType.fromItemTypeTag(
                RestoredItems.getType(cursor).orElse(null));
        if (cursorType != type) {
            player.sendMessage(LegacyColors.color("#FF5555Only the matching pocket augment can go here."));
            return;
        }
        if (tryInstall(link, type)) {
            consumeOneFromCursor(event);
            render();
        }
    }

    private boolean tryInstall(ItemStack link, PocketAugmentType type) {
        if (PocketLinkItem.hasAugment(link, type)) {
            player.sendMessage(LegacyColors.color("#FF5555That augment is already installed."));
            return false;
        }
        if (!PocketLinkItem.installAugment(link, type)) {
            return false;
        }
        player.sendMessage(LegacyColors.color("#00FC88Installed " + type.getDisplayName() + " Augment."));
        return true;
    }

    @Override
    public void handleClose(InventoryCloseEvent event) {
        if (inventory == null) {
            return;
        }
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            PocketAugmentType type = PocketAugmentType.fromItemTypeTag(
                    RestoredItems.getType(stack).orElse(null));
            if (type == null) {
                continue;
            }
            inventory.setItem(slot, null);
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
            leftover.values().forEach(drop -> player.getWorld().dropItemNaturally(player.getLocation(), drop));
        }
    }

    private static void consumeOneFromCursor(InventoryClickEvent event) {
        ItemStack cursor = event.getCursor();
        if (cursor == null || cursor.getType().isAir()) {
            return;
        }
        if (cursor.getAmount() <= 1) {
            event.getView().setCursor(null);
            return;
        }
        ItemStack next = cursor.clone();
        next.setAmount(cursor.getAmount() - 1);
        event.getView().setCursor(next);
    }

    private static void consumeOneFromSlot(InventoryClickEvent event) {
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) {
            return;
        }
        if (clicked.getAmount() <= 1) {
            event.setCurrentItem(null);
            return;
        }
        ItemStack next = clicked.clone();
        next.setAmount(clicked.getAmount() - 1);
        event.setCurrentItem(next);
    }
}
