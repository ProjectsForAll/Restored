package gg.drak.restored.gui;

import host.plas.bou.gui.CornerColor;
import host.plas.bou.gui.GuiLayout;

import gg.drak.restored.data.Network;
import gg.drak.restored.util.LinkedChestStorage;
import gg.drak.restored.util.PlayerHeadService;
import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class NetworkInfoGui extends AbstractInventoryGui {
    private static final int DETAILS_SLOT = 13;

    private final Network network;
    private final Runnable backAction;
    private final AtomicBoolean open = new AtomicBoolean(false);

    public NetworkInfoGui(org.bukkit.entity.Player player, Network network, Runnable backAction) {
        super(player, CornerColor.YELLOW);
        this.network = network;
        this.backAction = backAction;
    }

    @Override
    public void open() {
        open.set(true);
        String cachedOwner = PlayerHeadService.getCached(network.getOwnerUuid())
                .map(PlayerHeadService.CachedPlayer::name)
                .orElse("...");
        renderDetails(cachedOwner);

        PlayerHeadService.resolveNameAsync(network.getOwnerUuid(), null, ownerName -> {
            if (!open.get() || inventory == null) {
                return;
            }
            if (!player.isOnline() || !player.getOpenInventory().getTopInventory().equals(inventory)) {
                return;
            }
            inventory.setItem(DETAILS_SLOT, buildDetailsItem(ownerName));
        });
    }

    private void renderDetails(String ownerName) {
        ItemStack[] contents = beginShell(GuiLayout.SIZE_MEDIUM, "#FFED6A&lNetwork Info");
        contents[DETAILS_SLOT] = buildDetailsItem(ownerName);
        placeReturnButton(contents, "back");
        bindSlot(getBackSlot(), "back");
        finishAndOpen(contents);
    }

    private ItemStack buildDetailsItem(String ownerName) {
        List<String> lore = new ArrayList<>();
        lore.add("#bdc8c9UUID: #AAAAAA" + network.getIdentifierString());
        if (network.isPlaced()) {
            lore.add("#bdc8c9World: #AAAAAA" + network.getWorld());
            lore.add("#bdc8c9Location: #AAAAAA" + network.getX() + ", " + network.getY() + ", " + network.getZ());
        } else {
            lore.add("#bdc8c9Location: #FF5555Not placed");
        }
        lore.add("#bdc8c9Owner: #AAAAAA" + ownerName);
        lore.add("#bdc8c9Virtual items: #AAAAAA" + network.getTotalItems() + " / " + network.getCapacity());
        lore.add("#bdc8c9Linked chests: #AAAAAA" + LinkedChestStorage.countLinkedChestBlocks(network)
                + " #bdc8c9(#AAAAAA" + LinkedChestStorage.countLinkedItems(network) + " #bdc8c9items)");
        lore.add("#bdc8c9Differed types: #AAAAAA" + network.getCombinedStacks().size());
        lore.add("#bdc8c9Total opens: #AAAAAA" + network.getTotalOpens());
        lore.add("#bdc8c9Upgrades: #AAAAAA" + network.getUpgradeCount());
        return GuiItems.button(Material.BOOK, "#FFED6A&lNetwork Details", lore);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(inventory)) {
            return;
        }
        event.setCancelled(true);
        if ("back".equals(getKeyAtSlot(event.getRawSlot()))) {
            open.set(false);
            player.closeInventory();
            backAction.run();
        }
    }
}
