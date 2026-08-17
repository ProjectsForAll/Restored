package gg.drak.restored.gui;

import host.plas.bou.gui.CornerColor;
import host.plas.bou.gui.GuiLayout;

import gg.drak.restored.data.Network;
import gg.drak.restored.data.NetworkManager;
import gg.drak.restored.items.NetworkUpgradeItem;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

public class NetworksListGui extends AbstractInventoryGui {
    private int page;

    public NetworksListGui(Player player) {
        super(player, CornerColor.YELLOW);
        this.page = 0;
    }

    @Override
    public void open() {
        render();
    }

    private void render() {
        ItemStack[] contents = beginShell(GuiLayout.SIZE_LARGE, "#FFED6A&lYour Networks");
        List<Network> networks = NetworkManager.getNetworksForPlayer(player.getUniqueId());
        int perPage = perPage(GuiLayout.SIZE_LARGE);
        int start = page * perPage;
        int end = Math.min(start + perPage, networks.size());
        int[] slots = GuiLayout.listContentSlots(GuiLayout.SIZE_LARGE);

        for (int i = start, slotIndex = 0; i < end && slotIndex < slots.length; i++, slotIndex++) {
            Network network = networks.get(i);
            contents[slots[slotIndex]] = GuiUtils.networkIcon(network);
            bindSlot(slots[slotIndex], "net:" + network.getIdentifierString());
        }

        placePagination(contents, page, networks.size());
        finishAndOpen(contents);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null) {
            return;
        }

        if (!event.getClickedInventory().equals(inventory)) {
            return;
        }

        event.setCancelled(true);
        int slot = event.getRawSlot();
        String key = getKeyAtSlot(slot);

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

        if (key != null && key.startsWith("net:")) {
            UUID networkId = UUID.fromString(key.substring(4));
            Network network = NetworkManager.get(networkId);
            if (network == null) {
                return;
            }

            ItemStack cursor = event.getCursor();
            if (cursor != null && NetworkUpgradeItem.isType(cursor) && GuiUtils.tryApplyUpgrade(player, cursor, network)) {
                if (cursor.getAmount() <= 0) {
                    event.getView().setCursor(null);
                }
                render();
                return;
            }

            new NetworkManageGui(player, network).open();
        }
    }
}
