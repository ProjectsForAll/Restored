package gg.drak.restored.gui;

import host.plas.bou.gui.CornerColor;
import host.plas.bou.gui.GuiLayout;

import gg.drak.restored.data.Network;
import gg.drak.restored.data.NetworkManager;
import gg.drak.restored.items.NetworkChestItem;
import gg.drak.restored.util.LegacyColors;
import gg.drak.restored.util.NetworkBlockTags;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class NetworkManageGui extends AbstractInventoryGui {
    private final Network network;
    private final Runnable backAction;

    public NetworkManageGui(Player player, Network network) {
        this(player, network, () -> new NetworksListGui(player).open());
    }

    public NetworkManageGui(Player player, Network network, Runnable backAction) {
        super(player, CornerColor.YELLOW);
        this.network = network;
        this.backAction = backAction;
    }

    @Override
    public void open() {
        ItemStack[] contents = beginShell(GuiLayout.SIZE_MEDIUM, "#FFED6A&lManage Network");
        contents[11] = GuiItems.button(
                Material.PLAYER_HEAD,
                "#FFED6A&lManage Players",
                List.of("#bdc8c9Configure player permissions.")
        );
        contents[13] = GuiItems.button(
                Material.BOOK,
                "#FFED6A&lNetwork Info",
                List.of("#bdc8c9View network statistics.")
        );
        contents[15] = GuiItems.button(
                Material.ENDER_CHEST,
                "#FFED6A&lOpen Network",
                List.of("#bdc8c9Open storage (within 5 blocks).")
        );
        contents[22] = GuiItems.button(
                Material.CHEST_MINECART,
                "#FFED6A&lMove Network",
                List.of("#bdc8c9Pick up this network chest.", "#FF5555Owner only.")
        );

        bindSlot(11, "players");
        bindSlot(13, "info");
        bindSlot(15, "open");
        bindSlot(22, "move");
        placeReturnButton(contents, "back");
        finishAndOpen(contents);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null) {
            return;
        }

        if (event.getClickedInventory().equals(player.getInventory()) && event.isShiftClick()) {
            // Always cancel — otherwise vanilla dumps non-upgrade stacks into empty GUI slots.
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked != null && GuiUtils.tryApplyUpgrade(player, clicked, network)) {
                if (clicked.getAmount() <= 0) {
                    event.setCurrentItem(null);
                }
                open();
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

        switch (key) {
            case "back" -> {
                player.closeInventory();
                backAction.run();
            }
            case "players" -> new PlayersGui(player, network, () -> new NetworkManageGui(player, network, backAction).open()).open();
            case "info" -> new NetworkInfoGui(player, network, () -> new NetworkManageGui(player, network, backAction).open()).open();
            case "open" -> openNetwork();
            case "move" -> confirmMove();
            default -> {
                if (event.isShiftClick()) {
                    ItemStack cursor = event.getCursor();
                    if (cursor != null) {
                        GuiUtils.tryApplyUpgrade(player, cursor, network);
                    }
                }
            }
        }
    }

    private void openNetwork() {
        if (!network.isOwner(player.getUniqueId())
                && gg.drak.restored.data.AdminAccess.actsAsOwner(player.getUniqueId(), network.getIdentifier())) {
            // Admins open from anywhere. Linked chests are loaded first so they can be used, and
            // this menu stays open meanwhile: closing it would end the admin's session access.
            gg.drak.restored.util.LinkedChestStorage.prepareLinkedChunks(network, player.getUniqueId(), () -> {
                if (player.isOnline()) {
                    new NetworkItemsGui(player, network).open();
                }
            });
            return;
        }
        if (!network.isPlaced()) {
            player.sendMessage(LegacyColors.color("#FF5555This network is not placed."));
            return;
        }
        Location loc = network.getLocation();
        if (loc == null || player.getLocation().getWorld() == null || !player.getLocation().getWorld().equals(loc.getWorld())
                || player.getLocation().distance(loc) > 5) {
            player.sendMessage(LegacyColors.color("#FF5555You must be within 5 blocks of the network chest."));
            return;
        }
        player.closeInventory();
        new NetworkItemsGui(player, network).open();
    }

    private void confirmMove() {
        if (!network.actsAsOwner(player.getUniqueId())) {
            player.sendMessage(LegacyColors.color("#FF5555Only the owner can move this network."));
            return;
        }
        if (!network.isPlaced()) {
            player.sendMessage(LegacyColors.color("#FF5555This network is not placed."));
            return;
        }
        new ConfirmGui(
                player,
                "#FFED6A&lMove Network?",
                p -> executeMove(p),
                () -> new NetworkManageGui(player, network, backAction).open()
        ).open();
    }

    private void executeMove(Player p) {
        if (!network.isPlaced()) {
            return;
        }
        Location oldLocation = network.getLocation();
        if (oldLocation == null || oldLocation.getWorld() == null) {
            p.sendMessage(LegacyColors.color("#FF5555The network's world is not currently loaded."));
            return;
        }
        Block block = oldLocation.getBlock();
        NetworkBlockTags.clearNetworkId(block);
        block.setType(Material.AIR);

        NetworkManager.updateLocation(network, oldLocation, null);
        network.save();

        ItemStack chestItem = NetworkChestItem.create(network.getIdentifier());
        java.util.Map<Integer, ItemStack> leftover = p.getInventory().addItem(chestItem);
        for (ItemStack drop : leftover.values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), drop);
        }
        p.sendMessage(LegacyColors.color("#00FC88Network chest picked up. Place it to relocate."));
        new NetworksListGui(p).open();
    }
}
