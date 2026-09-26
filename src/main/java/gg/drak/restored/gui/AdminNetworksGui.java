package gg.drak.restored.gui;

import host.plas.bou.gui.CornerColor;
import host.plas.bou.gui.GuiLayout;

import gg.drak.restored.data.AdminAccess;
import gg.drak.restored.data.Network;
import gg.drak.restored.data.NetworkAdmin;
import gg.drak.restored.data.NetworkManager;
import gg.drak.restored.util.LegacyColors;
import gg.drak.restored.util.LinkedChestStorage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Every network on the server (or those within a radius of the admin), for {@code /networkadmin
 * manage}. Left-click opens a network with owner-level access; right-click deletes it after
 * confirmation.
 */
public class AdminNetworksGui extends AbstractInventoryGui {
    private final Integer radius;
    private int page;

    /** @param radius block radius around the player, or null for every network on the server */
    public AdminNetworksGui(Player player, Integer radius) {
        super(player, CornerColor.RED);
        this.radius = radius;
    }

    @Override
    public void open() {
        render();
    }

    private void render() {
        String title = radius == null
                ? "#FF5555&lAll Networks"
                : "#FF5555&lNetworks within " + radius + " blocks";
        ItemStack[] contents = beginShell(GuiLayout.SIZE_LARGE, title);
        List<Entry> networks = collect();
        int perPage = perPage(GuiLayout.SIZE_LARGE);
        int maxPage = Math.max(0, (networks.size() - 1) / Math.max(1, perPage));
        page = Math.min(page, maxPage);
        int start = page * perPage;
        int end = Math.min(start + perPage, networks.size());
        int[] slots = GuiLayout.listContentSlots(GuiLayout.SIZE_LARGE);

        for (int i = start, slotIndex = 0; i < end && slotIndex < slots.length; i++, slotIndex++) {
            Entry entry = networks.get(i);
            contents[slots[slotIndex]] = icon(entry);
            bindSlot(slots[slotIndex], "net:" + entry.network().getIdentifierString());
        }

        placePagination(contents, page, networks.size());
        finishAndOpen(contents);
    }

    /** A network with its distance from the admin, or -1 when it is in another world or unplaced. */
    private record Entry(Network network, double distance) {
    }

    private List<Entry> collect() {
        Location here = player.getLocation();
        List<Entry> result = new ArrayList<>();
        for (Network network : NetworkManager.getNetworks()) {
            double distance = distanceTo(network, here);
            if (radius != null && (distance < 0 || distance > radius)) {
                continue;
            }
            result.add(new Entry(network, distance));
        }
        // Nearest first; networks elsewhere (other worlds, unplaced) after, in a stable order.
        result.sort(Comparator
                .comparing((Entry e) -> e.distance() < 0)
                .thenComparingDouble(Entry::distance)
                .thenComparing(e -> e.network().getIdentifierString()));
        return result;
    }

    private static double distanceTo(Network network, Location here) {
        if (!network.isPlaced() || here.getWorld() == null
                || !here.getWorld().getName().equals(network.getWorld())) {
            return -1;
        }
        double dx = network.getX() + 0.5 - here.getX();
        double dy = network.getY() + 0.5 - here.getY();
        double dz = network.getZ() + 0.5 - here.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private ItemStack icon(Entry entry) {
        Network network = entry.network();
        List<String> lore = new ArrayList<>();
        if (entry.distance() >= 0) {
            lore.add("#FFED6A" + Math.round(entry.distance()) + " #bdc8c9blocks away");
        } else if (network.isPlaced()) {
            lore.add("#bdc8c9In another world: #AAAAAA" + network.getWorld());
        } else {
            lore.add("#FF5555Not placed");
        }
        lore.add("#bdc8c9Owner: #AAAAAA" + network.getOwnerName());
        if (network.isPlaced()) {
            lore.add("#bdc8c9Location: #AAAAAA" + network.getWorld() + " "
                    + network.getX() + ", " + network.getY() + ", " + network.getZ());
        }
        lore.add("#bdc8c9Virtual items: #AAAAAA" + network.getTotalItems() + " / " + network.getCapacity());
        int linkedBlocks = LinkedChestStorage.countLinkedChestBlocks(network);
        if (linkedBlocks > 0) {
            lore.add("#bdc8c9Linked chests: #AAAAAA" + linkedBlocks);
        }
        lore.add("#bdc8c9UUID: #AAAAAA" + network.getIdentifierString());
        lore.add("");
        lore.add("#00FC88Left-click #bdc8c9to open as owner.");
        lore.add("#FF5555Right-click #bdc8c9to delete.");
        return GuiItems.button(Material.CHEST, "#FFED6A&lNetwork", lore);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null) {
            return;
        }
        // Nothing from the player's inventory belongs in this list.
        event.setCancelled(true);
        if (!event.getClickedInventory().equals(inventory)) {
            return;
        }
        String key = getKeyAtSlot(event.getRawSlot());
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
        if (key == null || !key.startsWith("net:")) {
            return;
        }
        Network network = NetworkManager.get(UUID.fromString(key.substring(4)));
        if (network == null) {
            render();
            return;
        }
        if (!player.hasPermission(AdminAccess.PERMISSION)) {
            player.closeInventory();
            return;
        }
        ClickType click = event.getClick();
        if (click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT) {
            confirmDelete(network);
        } else if (click == ClickType.LEFT || click == ClickType.SHIFT_LEFT) {
            openAsOwner(network);
        }
    }

    private void openAsOwner(Network network) {
        AdminAccess.grantSession(player.getUniqueId(), network.getIdentifier());
        new NetworkManageGui(player, network, this::open).open();
    }

    private void confirmDelete(Network network) {
        String owner = network.getOwnerName();
        new ConfirmGui(
                player,
                "#FF5555&lDelete " + owner + "'s network?",
                p -> {
                    if (NetworkManager.get(network.getIdentifier()) != network) {
                        p.sendMessage(LegacyColors.color("#FF5555That network no longer exists."));
                    } else {
                        NetworkAdmin.deleteAndRemoveBlock(network);
                        p.sendMessage(LegacyColors.color("#FF5555Deleted " + owner + "'s network. "
                                + "Virtual items were destroyed; linked chests were unlinked."));
                    }
                    open();
                },
                this::open
        ).open();
    }
}
