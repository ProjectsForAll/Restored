package gg.drak.restored.events;

import gg.drak.restored.Restored;
import gg.drak.restored.data.Network;
import gg.drak.restored.data.NetworkManager;
import gg.drak.restored.data.NetworkHopperRole;
import gg.drak.restored.items.ChestLinkingToolItem;
import gg.drak.restored.util.LegacyColors;
import gg.drak.restored.util.LinkedChestStorage;
import gg.drak.restored.util.NetworkBlockTags;
import gg.drak.restored.util.NetworkHopperStorage;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;
import java.util.UUID;

public class ChestLinkingToolListener implements Listener {

    public ChestLinkingToolListener() {
        Restored.getInstance().registerListener(this);
        Restored.getInstance().logInfo("Registered ChestLinkingToolListener!");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!ChestLinkingToolItem.isType(hand)) {
            return;
        }

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        // Always cancel so the iron shovel never paths dirt/grass/farmland.
        event.setCancelled(true);

        if (action != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }

        Block block = event.getClickedBlock();
        if (!LinkedChestStorage.isSupportedStorage(block) && !NetworkHopperStorage.isHopper(block)) {
            return;
        }

        Network networkAtBlock = resolveNetwork(block);
        if (player.isSneaking() && networkAtBlock != null) {
            handleToolBindToggle(player, hand, networkAtBlock);
            return;
        }

        if (networkAtBlock != null) {
            player.sendMessage(LegacyColors.color("#FF5555Shift-right-click a network chest to bind this tool."));
            return;
        }

        handleStorageChestToggle(player, hand, block);
    }

    private void handleToolBindToggle(Player player, ItemStack hand, Network network) {
        if (!network.canManage(player.getUniqueId())) {
            player.sendMessage(LegacyColors.color("#FF5555You must be an admin or owner to bind this tool."));
            return;
        }
        Optional<UUID> bound = ChestLinkingToolItem.getLinkedNetworkId(hand);
        if (bound.isPresent() && bound.get().equals(network.getIdentifier())) {
            ChestLinkingToolItem.unlinkNetwork(hand);
            player.sendMessage(LegacyColors.color("#AAAAAAChest Linking Tool unbound."));
            return;
        }
        ChestLinkingToolItem.linkNetwork(hand, network.getIdentifier());
        player.sendMessage(LegacyColors.color("#00FC88Chest Linking Tool bound to network."));
    }

    private void handleStorageChestToggle(Player player, ItemStack hand, Block block) {
        Optional<UUID> boundId = ChestLinkingToolItem.getLinkedNetworkId(hand);
        if (boundId.isEmpty()) {
            player.sendMessage(LegacyColors.color("#FF5555Bind this tool to a network first (shift-right-click a network chest)."));
            return;
        }

        Network network = NetworkManager.get(boundId.get());
        if (network == null) {
            ChestLinkingToolItem.unlinkNetwork(hand);
            player.sendMessage(LegacyColors.color("#FF5555That network no longer exists. Tool unbound."));
            return;
        }
        if (!network.canManage(player.getUniqueId())) {
            player.sendMessage(LegacyColors.color("#FF5555You must be an admin or owner to link chests."));
            return;
        }
        if (!network.isPlaced()) {
            player.sendMessage(LegacyColors.color("#FF5555The network chest must be placed before linking storage."));
            return;
        }

        if (NetworkHopperStorage.isHopper(block)) {
            handleNetworkHopperToggle(player, block, network);
            return;
        }

        Block canonical = LinkedChestStorage.canonicalStorageBlock(block);
        if (canonical == null) {
            player.sendMessage(LegacyColors.color("#FF5555Only chests and barrels can be linked."));
            return;
        }

        String key = NetworkManager.locationKey(canonical.getLocation());
        if (network.hasLinkedChestKey(key)) {
            network.removeLinkedChestKey(key);
            NetworkBlockTags.clearLinkedNetworkId(canonical);
            network.save();
            player.sendMessage(LegacyColors.color("#AAAAAAChest unlinked from the network."));
            return;
        }

        // Already linked via the other half of a double chest?
        Optional<UUID> existingLink = NetworkBlockTags.getLinkedNetworkId(canonical);
        if (existingLink.isEmpty() && block != canonical) {
            existingLink = NetworkBlockTags.getLinkedNetworkId(block);
        }
        if (existingLink.isPresent()) {
            if (existingLink.get().equals(network.getIdentifier())) {
                network.removeLinkedChestKey(key);
                NetworkBlockTags.clearLinkedNetworkId(canonical);
                NetworkBlockTags.clearLinkedNetworkId(block);
                network.save();
                player.sendMessage(LegacyColors.color("#AAAAAAChest unlinked from the network."));
                return;
            }
            player.sendMessage(LegacyColors.color("#FF5555That chest is already linked to another network."));
            return;
        }

        if (!LinkedChestStorage.isWithinLinkRange(network, canonical.getLocation())) {
            player.sendMessage(LegacyColors.color("#FF5555Linked chests must be within "
                    + LinkedChestStorage.getLinkDistanceDescription() + " of the network chest."));
            return;
        }

        network.addLinkedChest(
                canonical.getWorld().getName(),
                canonical.getX(),
                canonical.getY(),
                canonical.getZ()
        );
        NetworkBlockTags.setLinkedNetworkId(canonical, network.getIdentifier());
        network.save();
        player.sendMessage(LegacyColors.color("#00FC88Storage linked as network storage."));
    }

    private void handleNetworkHopperToggle(Player player, Block block, Network network) {
        Optional<UUID> existing = NetworkBlockTags.getLinkedNetworkId(block);
        if (existing.isPresent() && !existing.get().equals(network.getIdentifier())) {
            player.sendMessage(LegacyColors.color("#FF5555That network hopper is already linked to another network."));
            return;
        }
        String key = NetworkManager.locationKey(block.getLocation());
        if (network.hasLinkedHopperKey(key)) {
            network.removeLinkedHopperKey(key);
            NetworkBlockTags.clearLinkedNetworkId(block);
            network.save();
            player.sendMessage(LegacyColors.color("#AAAAAANetwork hopper unlinked."));
            return;
        }
        if (!LinkedChestStorage.isWithinLinkRange(network, block.getLocation())) {
            player.sendMessage(LegacyColors.color("#FF5555Linked hoppers must be within "
                    + LinkedChestStorage.getLinkDistanceDescription() + " of the network chest."));
            return;
        }
        NetworkHopperStorage.link(block, network);
        network.save();
        NetworkHopperRole role = NetworkHopperStorage.role(block);
        player.sendMessage(LegacyColors.color("#00FC88Network hopper ("
                + (role == null ? "unknown" : role.id()) + ") linked."));
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
