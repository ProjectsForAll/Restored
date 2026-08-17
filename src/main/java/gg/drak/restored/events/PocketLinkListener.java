package gg.drak.restored.events;

import gg.drak.restored.Restored;
import gg.drak.restored.data.Network;
import gg.drak.restored.data.NetworkManager;
import gg.drak.restored.gui.pocket.PocketLinkGui;
import gg.drak.restored.items.PocketLinkItem;
import gg.drak.restored.util.LegacyColors;
import gg.drak.restored.util.NetworkBlockTags;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;
import java.util.UUID;

public class PocketLinkListener implements Listener {

    public PocketLinkListener() {
        Restored.getInstance().registerListener(this);
        Restored.getInstance().logInfo("Registered PocketLinkListener!");
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onOpenLinkClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
                || PocketLinkItem.getOpenGuiLinkId(player).isEmpty()) {
            return;
        }
        if (PocketLinkItem.isOpenGuiLink(player, event.getCurrentItem())
                || PocketLinkItem.isOpenGuiLink(player, event.getCursor())) {
            event.setCancelled(true);
            return;
        }
        ClickType click = event.getClick();
        if (click == ClickType.NUMBER_KEY) {
            int hotbar = event.getHotbarButton();
            if (hotbar >= 0 && PocketLinkItem.isOpenGuiLink(player, player.getInventory().getItem(hotbar))) {
                event.setCancelled(true);
                return;
            }
        }
        if (click == ClickType.SWAP_OFFHAND
                && PocketLinkItem.isOpenGuiLink(player, player.getInventory().getItemInOffHand())) {
            event.setCancelled(true);
            return;
        }
        if (click == ClickType.DOUBLE_CLICK && inventoryContainsOpenLink(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onOpenLinkDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player
                && PocketLinkItem.getOpenGuiLinkId(player).isPresent()
                && PocketLinkItem.isOpenGuiLink(player, event.getOldCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onOpenLinkDrop(PlayerDropItemEvent event) {
        if (PocketLinkItem.isOpenGuiLink(event.getPlayer(), event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onOpenLinkSwap(PlayerSwapHandItemsEvent event) {
        if (PocketLinkItem.isOpenGuiLink(event.getPlayer(), event.getMainHandItem())
                || PocketLinkItem.isOpenGuiLink(event.getPlayer(), event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }

    private static boolean inventoryContainsOpenLink(Player player) {
        for (ItemStack stack : player.getInventory().getContents()) {
            if (PocketLinkItem.isOpenGuiLink(player, stack)) {
                return true;
            }
        }
        return PocketLinkItem.isOpenGuiLink(player, player.getInventory().getItemInOffHand());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (PocketLinkItem.isType(event.getItemInHand())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(LegacyColors.color("#FF5555Pocket Links cannot be placed. Click to open."));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!PocketLinkItem.isType(hand)) {
            return;
        }

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        // Shift-right-click network chest: link/unlink
        if (player.isSneaking() && action == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            Block block = event.getClickedBlock();
            if (block.getType() == Material.CHEST) {
                Network network = resolveNetwork(block);
                if (network != null) {
                    event.setCancelled(true);
                    handleLinkToggle(player, hand, network);
                    return;
                }
            }
        }

        // Opening while sneaking on a normal block still opens GUI unless it was a network chest handled above
        event.setCancelled(true);
        PocketLinkItem.ensureLinkId(hand);
        PocketLinkItem.refreshLore(hand);
        new PocketLinkGui(player, hand).open();
    }

    private void handleLinkToggle(Player player, ItemStack hand, Network network) {
        if (!network.canAccess(player.getUniqueId())) {
            player.sendMessage(LegacyColors.color("#FF5555You do not have access to this network."));
            return;
        }
        Optional<UUID> linked = PocketLinkItem.getLinkedNetworkId(hand);
        if (linked.isPresent() && linked.get().equals(network.getIdentifier())) {
            PocketLinkItem.unlinkNetwork(hand);
            player.sendMessage(LegacyColors.color("#AAAAAAPocket Link unlinked."));
            return;
        }
        PocketLinkItem.linkNetwork(hand, network.getIdentifier());
        player.sendMessage(LegacyColors.color("#00FC88Pocket Link linked to network."));
    }

    private Network resolveNetwork(Block block) {
        Network byLocation = NetworkManager.getByLocation(block.getLocation());
        if (byLocation != null) {
            return byLocation;
        }
        return NetworkBlockTags.getNetworkId(block)
                .map(NetworkManager::get)
                .orElse(null);
    }
}
