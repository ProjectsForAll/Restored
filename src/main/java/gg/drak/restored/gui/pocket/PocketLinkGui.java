package gg.drak.restored.gui.pocket;

import host.plas.bou.gui.CornerColor;
import host.plas.bou.gui.GuiLayout;

import gg.drak.restored.data.Network;
import gg.drak.restored.data.NetworkManager;
import gg.drak.restored.gui.AbstractInventoryGui;
import gg.drak.restored.gui.GuiItems;
import gg.drak.restored.gui.NetworkInfoGui;
import gg.drak.restored.gui.NetworkItemsGui;
import gg.drak.restored.items.PocketLinkItem;
import gg.drak.restored.util.LegacyColors;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class PocketLinkGui extends AbstractInventoryGui implements PocketLinkGuiBound {
    private static final int[] DEPOSIT_SLOTS = {20, 21, 22, 23, 24, 25, 26};
    private final UUID linkId;

    public PocketLinkGui(Player player, ItemStack pocketLink) {
        super(player, CornerColor.YELLOW);
        this.linkId = PocketLinkItem.ensureLinkId(pocketLink);
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
        ItemStack link = linkItem();
        if (link == null) {
            player.sendMessage(LegacyColors.color("#FF5555Pocket Link not found in your inventory."));
            return;
        }
        PocketLinkItem.markGuiOpen(player, linkId);
        ItemStack[] contents = beginShell(GuiLayout.SIZE_MEDIUM, "#FFED6A&lPocket Link");
        contents[11] = GuiItems.button(
                Material.BUNDLE,
                "#FFED6A&lPocket Augments",
                List.of("#bdc8c9Install and open pocket augments.")
        );
        bindSlot(11, "augments");

        Optional<UUID> networkId = PocketLinkItem.getLinkedNetworkId(link);
        contents[13] = GuiItems.button(
                Material.ENDER_CHEST,
                "#FFED6A&lOpen Network",
                List.of(
                        networkId.isPresent()
                                ? "#bdc8c9Open storage from anywhere."
                                : "#FF5555Link a network first.",
                        "#AAAAAANo distance check."
                )
        );
        bindSlot(13, "open");

        contents[15] = GuiItems.button(
                Material.BOOK,
                "#FFED6A&lNetwork Info",
                networkId.isPresent()
                        ? List.of(
                        "#bdc8c9View linked network details.",
                        "#AAAAAAShift-right-click a network chest to unlink."
                )
                        : List.of(
                        "#FF5555Not linked to a network.",
                        "#bdc8c9Shift-right-click a network chest to link."
                )
        );
        bindSlot(15, "info");

        for (int slot : DEPOSIT_SLOTS) {
            // These are intentionally blank: shift-clicking from the player inventory
            // deposits directly into the linked network.
            contents[slot] = null;
            bindSlot(slot, "deposit");
        }

        placeReturnButton(contents, "back");
        finishAndOpen(contents);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (event.getClickedInventory() != null && event.getClickedInventory().equals(player.getInventory())) {
            if (!event.isShiftClick()) {
                return;
            }
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType().isAir()) {
                return;
            }
            if (PocketLinkItem.isOpenGuiLink(player, clicked)) {
                event.setCancelled(true);
                return;
            }
            ItemStack link = linkItem();
            if (link == null) {
                player.closeInventory();
                return;
            }
            Optional<UUID> networkId = PocketLinkItem.getLinkedNetworkId(link);
            if (networkId.isEmpty()) {
                player.sendMessage(LegacyColors.color("#FF5555Link a network first."));
                return;
            }
            Network network = NetworkManager.get(networkId.get());
            if (network == null || !network.canDeposit(player.getUniqueId())) {
                player.sendMessage(LegacyColors.color("#FF5555You cannot deposit into this network."));
                return;
            }
            long amount = clicked.getAmount();
            long inserted = network.insert(clicked, amount);
            if (inserted > 0) {
                long remaining = amount - inserted;
                if (remaining <= 0) {
                    event.setCurrentItem(null);
                } else {
                    clicked.setAmount((int) remaining);
                    event.setCurrentItem(clicked);
                }
                network.save();
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
        if ("back".equals(key)) {
            player.closeInventory();
            return;
        }

        ItemStack link = linkItem();
        if (link == null) {
            player.closeInventory();
            player.sendMessage(LegacyColors.color("#FF5555Pocket Link not found."));
            return;
        }

        if ("augments".equals(key)) {
            new PocketAugmentsGui(player, linkId).open();
            return;
        }

        Optional<UUID> networkId = PocketLinkItem.getLinkedNetworkId(link);
        if ("open".equals(key)) {
            if (networkId.isEmpty()) {
                player.sendMessage(LegacyColors.color("#FF5555Link a network first."));
                return;
            }
            Network network = NetworkManager.get(networkId.get());
            if (network == null) {
                player.sendMessage(LegacyColors.color("#FF5555Linked network no longer exists."));
                return;
            }
            if (!network.canAccess(player.getUniqueId())) {
                player.sendMessage(LegacyColors.color("#FF5555You do not have access to this network."));
                return;
            }
            new NetworkItemsGui(player, network).open();
            return;
        }

        if ("info".equals(key)) {
            if (networkId.isEmpty()) {
                player.sendMessage(LegacyColors.color("#FF5555Shift-right-click a network chest to link."));
                return;
            }
            Network network = NetworkManager.get(networkId.get());
            if (network == null) {
                player.sendMessage(LegacyColors.color("#FF5555Linked network no longer exists."));
                return;
            }
            new NetworkInfoGui(player, network, () -> new PocketLinkGui(player, link).open()).open();
        }
    }
}
