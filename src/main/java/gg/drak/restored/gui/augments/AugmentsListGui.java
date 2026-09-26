package gg.drak.restored.gui.augments;

import host.plas.bou.gui.CornerColor;
import host.plas.bou.gui.GuiLayout;

import gg.drak.restored.data.AugmentType;
import gg.drak.restored.data.Network;
import gg.drak.restored.gui.AbstractInventoryGui;
import gg.drak.restored.gui.GuiItems;
import gg.drak.restored.gui.NetworkItemsGui;
import gg.drak.restored.gui.compactor.CompactingConfigsGui;
import gg.drak.restored.items.NetworkAugmentItem;
import gg.drak.restored.util.LegacyColors;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AugmentsListGui extends AbstractInventoryGui {
    private static final int COLUMNS_PER_PAGE = 7;
    private static final int INSTALL_ROW = 1;
    private static final int DESCRIPTOR_ROW = 2;
    private static final int OPEN_ROW = 3;

    private final Network network;
    private int page;

    public AugmentsListGui(Player player, Network network) {
        super(player, CornerColor.YELLOW);
        this.network = network;
        this.page = 0;
    }

    @Override
    public void open() {
        render();
    }

    private void render() {
        ItemStack[] contents = beginShell(GuiLayout.SIZE_LARGE, "#FFED6A&lNetwork Augments");

        AugmentType[] all = AugmentType.values();
        int totalPages = Math.max(1, (int) Math.ceil(all.length / (double) COLUMNS_PER_PAGE));
        if (page >= totalPages) {
            page = Math.max(0, totalPages - 1);
        }
        int start = page * COLUMNS_PER_PAGE;
        int end = Math.min(start + COLUMNS_PER_PAGE, all.length);

        for (int i = start; i < end; i++) {
            AugmentType type = all[i];
            int col = (i - start) + 1;
            int installSlot = INSTALL_ROW * 9 + col;
            int descriptorSlot = DESCRIPTOR_ROW * 9 + col;
            int openSlot = OPEN_ROW * 9 + col;

            boolean installed = network.hasAugment(type);
            if (installed) {
                // Display-only icon (not a real augment item) so stacks cannot merge into this slot.
                contents[installSlot] = GuiItems.button(
                        type.getWorkstationMaterial(),
                        "#00FC88&l" + type.getDisplayName() + " Augment",
                        List.of(
                                "#bdc8c9Installed (1/1).",
                                "#AAAAAAClick with an empty cursor to uninstall."
                        )
                );
            } else {
                contents[installSlot] = GuiItems.button(
                        Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                        "#AAAAAAEmpty Augment Slot",
                        List.of(
                                "#bdc8c9Place a #FFED6A" + type.getDisplayName() + " Augment #bdc8c9here.",
                                "#AAAAAAOnly one augment per slot.",
                                "#AAAAAAShift-click from your inventory to install."
                        )
                );
            }
            bindSlot(installSlot, "install:" + type.name());

            contents[descriptorSlot] = GuiItems.button(
                    type.getWorkstationMaterial(),
                    "#FFED6A&l" + type.getDisplayName(),
                    List.of(
                            type == AugmentType.COMPACTOR
                                    ? "#bdc8c9Network augment slot."
                                    : "#bdc8c9Workstation augment slot.",
                            installed ? "#00FC88Installed" : "#FF5555Not installed"
                    )
            );
            bindSlot(descriptorSlot, "desc:" + type.name());

            if (installed) {
                contents[openSlot] = GuiItems.button(
                        Material.LIME_STAINED_GLASS_PANE,
                        "#00FC88&lOpen " + type.getDisplayName(),
                        List.of(type == AugmentType.COMPACTOR
                                ? "#bdc8c9Click to manage configurations."
                                : "#bdc8c9Click to open this workstation.")
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

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null) {
            return;
        }

        if (event.getClickedInventory().equals(player.getInventory())) {
            if (event.isShiftClick()) {
                // Always cancel — otherwise vanilla dumps the stack into empty GUI slots and it vanishes on re-render.
                event.setCancelled(true);
                ItemStack clicked = event.getCurrentItem();
                AugmentType type = NetworkAugmentItem.getType(clicked);
                if (type != null && tryInstall(type)) {
                    consumeOneFromSlot(event);
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
            new NetworkItemsGui(player, network).open();
            return;
        }

        if (key.startsWith("install:")) {
            AugmentType type = AugmentType.valueOf(key.substring("install:".length()));
            handleInstallClick(type, event);
            return;
        }

        if (key.startsWith("open:")) {
            AugmentType type = AugmentType.valueOf(key.substring("open:".length()));
            if (!network.hasAugment(type)) {
                player.sendMessage(LegacyColors.color("#FF5555Install this augment first."));
                return;
            }
            if (!network.canUseAugments(player.getUniqueId())) {
                player.sendMessage(LegacyColors.color("#FF5555You cannot use augments on this network."));
                return;
            }
            if (type == AugmentType.ENDER_CHEST) {
                player.openInventory(player.getEnderChest());
                return;
            }
            if (type == AugmentType.COMPACTOR) {
                new CompactingConfigsGui(player, network).open();
                return;
            }
            WorkstationGuis.open(player, network, type);
        }
    }

    private void handleInstallClick(AugmentType type, InventoryClickEvent event) {
        ItemStack cursor = event.getCursor();
        if (network.hasAugment(type)) {
            if (!network.canUseAugments(player.getUniqueId())) {
                player.sendMessage(LegacyColors.color("#FF5555You cannot modify augments on this network."));
                return;
            }
            if (cursor != null && !cursor.getType().isAir()) {
                player.sendMessage(LegacyColors.color("#FF5555Remove the installed augment first."));
                return;
            }
            if (!network.uninstallAugment(type)) {
                return;
            }
            network.save();
            ItemStack give = NetworkAugmentItem.create(type);
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(give);
            if (!leftover.isEmpty()) {
                ItemStack remain = leftover.values().iterator().next();
                long inserted = network.insert(remain, remain.getAmount());
                if (inserted < remain.getAmount()) {
                    network.installAugment(type);
                    network.save();
                    player.sendMessage(LegacyColors.color("#FF5555No space for the augment item."));
                    return;
                }
            }
            player.sendMessage(LegacyColors.color("#00FC88Uninstalled " + type.getDisplayName() + " Augment."));
            render();
            return;
        }

        if (cursor == null || cursor.getType().isAir()) {
            return;
        }
        if (!NetworkAugmentItem.isType(cursor, type)) {
            if (NetworkAugmentItem.isType(cursor)) {
                player.sendMessage(LegacyColors.color("#FF5555That augment belongs in a different slot."));
            } else {
                player.sendMessage(LegacyColors.color("#FF5555Only the matching augment can go here."));
            }
            return;
        }
        if (tryInstall(type)) {
            consumeOneFromCursor(event);
            render();
        }
    }

    private boolean tryInstall(AugmentType type) {
        if (!network.canUseAugments(player.getUniqueId())) {
            player.sendMessage(LegacyColors.color("#FF5555You cannot install augments on this network."));
            return false;
        }
        if (network.hasAugment(type)) {
            player.sendMessage(LegacyColors.color("#FF5555That augment is already installed."));
            return false;
        }
        if (!network.installAugment(type)) {
            return false;
        }
        network.save();
        player.sendMessage(LegacyColors.color("#00FC88Installed " + type.getDisplayName() + " Augment."));
        return true;
    }

    @Override
    public void handleClose(InventoryCloseEvent event) {
        if (inventory == null) {
            return;
        }
        // Return any real items that somehow landed in the GUI (should never happen).
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            if (!NetworkAugmentItem.isType(stack)) {
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
