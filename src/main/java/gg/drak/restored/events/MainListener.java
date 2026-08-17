package gg.drak.restored.events;

import gg.drak.restored.Restored;
import gg.drak.restored.data.Network;
import gg.drak.restored.data.NetworkManager;
import gg.drak.restored.gui.NetworkItemsGui;
import gg.drak.restored.gui.NetworkManageGui;
import gg.drak.restored.items.ChestLinkingToolItem;
import gg.drak.restored.items.NetworkChestItem;
import gg.drak.restored.items.NetworkUpgradeItem;
import gg.drak.restored.items.PocketLinkItem;
import gg.drak.restored.items.RestoredItems;
import gg.drak.restored.util.LegacyColors;
import gg.drak.restored.util.LinkedChestStorage;
import gg.drak.restored.util.NetworkBlockTags;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class MainListener implements Listener {

    public MainListener() {
        Restored.getInstance().registerListener(this);
        Restored.getInstance().logInfo("Registered MainListener!");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void preventNonPlaceableRestoredItems(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (!RestoredItems.isRestoredItem(item) || NetworkChestItem.isType(item)) {
            return;
        }
        event.setCancelled(true);
        event.getPlayer().sendMessage(LegacyColors.color("#FF5555This Restored item cannot be placed."));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (!NetworkChestItem.isType(item)) {
            return;
        }

        Player player = event.getPlayer();
        Block block = event.getBlockPlaced();
        UUID networkId = NetworkChestItem.getNetworkId(item);

        Network network;
        if (networkId != null) {
            network = NetworkManager.get(networkId);
            if (network == null) {
                player.sendMessage(LegacyColors.color("#FF5555That network no longer exists."));
                event.setCancelled(true);
                return;
            }
            if (!network.isOwner(player.getUniqueId())) {
                player.sendMessage(LegacyColors.color("#FF5555Only the owner can place this network chest."));
                event.setCancelled(true);
                return;
            }
            if (network.isPlaced()) {
                Network existing = NetworkManager.getByLocation(network.getLocation());
                if (existing != null && existing.getIdentifier().equals(network.getIdentifier())) {
                    player.sendMessage(LegacyColors.color("#FF5555This network is already placed."));
                    event.setCancelled(true);
                    return;
                }
            }
            if (!LinkedChestStorage.allLinksWithinRange(network, block.getLocation())) {
                event.setCancelled(true);
                player.sendMessage(LegacyColors.color("#FF5555Cannot place here: one or more linked chests are farther than "
                        + LinkedChestStorage.MAX_LINK_DISTANCE + " blocks. Unlink them first."));
                return;
            }
            NetworkManager.updateLocation(network, network.getLocation(), block.getLocation());
            network.save();
        } else {
            network = NetworkManager.create(player, block.getLocation());
        }

        // Face the player (same as vanilla chest placement).
        BlockFace facing = player.getFacing().getOppositeFace();
        if (facing != BlockFace.NORTH && facing != BlockFace.SOUTH
                && facing != BlockFace.EAST && facing != BlockFace.WEST) {
            facing = BlockFace.NORTH;
        }
        Chest chestData = (Chest) Material.CHEST.createBlockData();
        chestData.setFacing(facing);
        block.setBlockData(chestData);
        NetworkBlockTags.setNetworkId(block, network.getIdentifier());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Network network = resolveNetwork(block);
        if (network != null) {
            Player player = event.getPlayer();
            if (!network.isOwner(player.getUniqueId())) {
                event.setCancelled(true);
                player.sendMessage(LegacyColors.color("#FF5555Only the owner can break this network chest."));
                return;
            }

            if (!network.isEmpty()) {
                event.setCancelled(true);
                if (network.getUpgradeCount() > 0 || !network.getInstalledAugments().isEmpty()) {
                    player.sendMessage(LegacyColors.color(
                            "#FF5555Remove all Network Upgrades and augments before breaking the chest."));
                } else {
                    player.sendMessage(LegacyColors.color("#FF5555Empty the network before breaking the chest."));
                }
                return;
            }

            // Only players may cause a drop, and only when the network is empty.
            event.setDropItems(false);
            NetworkBlockTags.clearNetworkId(block);
            network.delete();

            ItemStack chestDrop = NetworkChestItem.create();
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(chestDrop);
            leftover.values().forEach(stack -> player.getWorld().dropItemNaturally(player.getLocation(), stack));
            player.sendMessage(LegacyColors.color("#AAAAAANetwork deleted."));
            return;
        }

        // Linked storage chest: allow vanilla break/drops, drop the link.
        clearLinkedChestIfPresent(block);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        protectNetworkBlocks(event.blockList());
        clearLinkedChestsInList(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        protectNetworkBlocks(event.blockList());
        clearLinkedChestsInList(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        if (resolveNetwork(event.getBlock()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (resolveNetwork(event.getBlock()) != null) {
            event.setCancelled(true);
        }
    }

    private void protectNetworkBlocks(List<Block> blocks) {
        Iterator<Block> iterator = blocks.iterator();
        while (iterator.hasNext()) {
            if (resolveNetwork(iterator.next()) != null) {
                iterator.remove();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null || event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block.getType() != Material.CHEST) {
            return;
        }

        Network network = resolveNetwork(block);
        if (network == null) {
            return;
        }

        Action action = event.getAction();
        Player player = event.getPlayer();

        if (action == Action.LEFT_CLICK_BLOCK) {
            handleLeftClickChest(event, player, network);
            return;
        }

        if (action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack mainHand = player.getInventory().getItemInMainHand();
        // Pocket Link / Chest Linking Tool shift-right-click handles bind instead of manage GUI.
        if (player.isSneaking() && (PocketLinkItem.isType(mainHand) || ChestLinkingToolItem.isType(mainHand))) {
            return;
        }

        if (!network.canAccess(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage(LegacyColors.color("#FF5555You do not have access to this network."));
            return;
        }

        event.setCancelled(true);
        if (player.isSneaking() && network.canManage(player.getUniqueId())) {
            new NetworkManageGui(player, network, () -> new NetworkItemsGui(player, network).open()).open();
        } else {
            new NetworkItemsGui(player, network).open();
        }
    }

    private void handleLeftClickChest(PlayerInteractEvent event, Player player, Network network) {
        if (player.isSneaking()) {
            event.setCancelled(true);
            removeUpgradeFromChest(player, network);
            return;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!NetworkUpgradeItem.isType(hand)) {
            return;
        }

        event.setCancelled(true);
        applyUpgradeToChest(player, network, hand);
    }

    private void applyUpgradeToChest(Player player, Network network, ItemStack hand) {
        if (!network.canManage(player.getUniqueId())) {
            player.sendMessage(LegacyColors.color("#FF5555You cannot upgrade this network."));
            return;
        }

        network.addUpgrade();
        network.save();
        hand.setAmount(hand.getAmount() - 1);
        player.sendMessage(LegacyColors.color("#00FC88Upgrade applied. Capacity is now " + network.getCapacity() + "."));
    }

    private void removeUpgradeFromChest(Player player, Network network) {
        if (!network.canManage(player.getUniqueId())) {
            player.sendMessage(LegacyColors.color("#FF5555You cannot remove upgrades from this network."));
            return;
        }
        if (network.getUpgradeCount() <= 0) {
            player.sendMessage(LegacyColors.color("#FF5555This network has no upgrades to remove."));
            return;
        }
        if (!network.removeUpgrade()) {
            player.sendMessage(LegacyColors.color("#FF5555Cannot remove an upgrade while items exceed the lower capacity."));
            return;
        }
        network.save();

        ItemStack upgrade = NetworkUpgradeItem.create();
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(upgrade);
        if (!leftover.isEmpty()) {
            leftover.values().forEach(stack -> player.getWorld().dropItemNaturally(player.getLocation(), stack));
        }
        player.sendMessage(LegacyColors.color("#00FC88Upgrade removed. Capacity is now " + network.getCapacity() + "."));
    }

    private void clearLinkedChestsInList(List<Block> blocks) {
        for (Block block : blocks) {
            if (block.getType() == Material.CHEST) {
                clearLinkedChestIfPresent(block);
            }
        }
    }

    private void clearLinkedChestIfPresent(Block block) {
        Block canonical = block.getType() == Material.CHEST
                ? LinkedChestStorage.canonicalChestBlock(block)
                : null;
        Block target = canonical != null ? canonical : block;

        Optional<UUID> linkedId = NetworkBlockTags.getLinkedNetworkId(target);
        if (linkedId.isEmpty() && target != block) {
            linkedId = NetworkBlockTags.getLinkedNetworkId(block);
        }

        String key = NetworkManager.locationKey(target.getLocation());
        Network network = linkedId.map(NetworkManager::get).orElse(null);
        if (network == null) {
            for (Network candidate : NetworkManager.getNetworks()) {
                if (candidate.hasLinkedChestKey(key)) {
                    network = candidate;
                    break;
                }
            }
        }
        if (network == null && block.getBlockData() instanceof org.bukkit.block.data.type.Chest chestData
                && chestData.getType() != org.bukkit.block.data.type.Chest.Type.SINGLE) {
            Block other = LinkedChestStorage.otherHalf(block, chestData);
            if (other != null) {
                String otherKey = NetworkManager.locationKey(other.getLocation());
                for (Network candidate : NetworkManager.getNetworks()) {
                    if (candidate.hasLinkedChestKey(otherKey)) {
                        network = candidate;
                        key = otherKey;
                        target = other;
                        break;
                    }
                }
            }
        }
        if (network == null) {
            NetworkBlockTags.clearLinkedNetworkId(block);
            if (canonical != null) {
                NetworkBlockTags.clearLinkedNetworkId(canonical);
            }
            return;
        }

        network.removeLinkedChestKey(key);
        NetworkBlockTags.clearLinkedNetworkId(target);
        NetworkBlockTags.clearLinkedNetworkId(block);
        network.save();
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
