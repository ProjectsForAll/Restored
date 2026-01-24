package gg.drak.restored.data;

import host.plas.bou.commands.Sender;
import host.plas.bou.gui.ScreenManager;
import host.plas.bou.gui.screens.ScreenInstance;
import gg.drak.restored.Restored;
import gg.drak.restored.data.blocks.NetworkBlock;
import gg.drak.restored.data.blocks.NetworkMap;
import gg.drak.restored.data.blocks.SingleNetworkMap;
import gg.drak.restored.data.blocks.datablock.DataBlock;
import gg.drak.restored.data.blocks.impl.Drive;
import gg.drak.restored.data.disks.StorageDisk;
import gg.drak.restored.data.items.IPlaceable;
import gg.drak.restored.data.items.ItemManager;
import gg.drak.restored.data.items.RestoredItem;
import gg.drak.restored.data.permission.PermissionNode;
import gg.drak.restored.data.screens.items.StoredItem;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.math.BigInteger;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListSet;

public class NetworkManager {
    @Getter @Setter
    private static ConcurrentSkipListSet<Network> networks = new ConcurrentSkipListSet<>();

    private static Optional<Network> getNetwork(String identifier) {
        return networks.stream().filter(n -> n.getIdentifier().equals(identifier)).findFirst();
    }

    public static Optional<Network> getNetwork(UUID uuid) {
        return networks.stream().filter(n -> n.getUuid().equals(uuid)).findFirst();
    }

    public static void loadNetwork(Network network) {
        networks.add(network);
    }

    public static void unloadNetwork(Network network) {
        networks.removeIf(n -> n.getUuid().equals(network.getUuid()));

        NetworkMap.delete(network.getIdentifier());
    }

    public static Optional<Network> getOrGetNetwork(String identifier) {
        Optional<Network> network = getNetwork(identifier);
        if (network.isPresent()) return network;

        Optional<SingleNetworkMap> optional = NetworkMap.getNetworkMap(identifier);
        if (optional.isEmpty()) return Optional.empty();
        SingleNetworkMap map = optional.get();

        Network newNetwork = new Network(map.getIdentifier(), map.getOwnerUUID());
        map.getControllerImpl(Optional.of(newNetwork)).ifPresent(newNetwork::setController);
        loadNetwork(newNetwork);

        return Optional.of(newNetwork);
    }

    public static Optional<Network> getOrGetNetwork(UUID uuid) {
        return getOrGetNetwork(uuid.toString());
    }

    @Getter @Setter
    private static ConcurrentSkipListSet<StorageDisk> disks = new ConcurrentSkipListSet<>();

    public static Optional<StorageDisk> getDisk(String identifier) {
        return disks.stream().filter(d -> d.getIdentifier().equals(identifier)).findFirst();
    }

    public static Optional<StorageDisk> getDisk(UUID uuid) {
        return disks.stream().filter(d -> d.getUuid().equals(uuid)).findFirst();
    }

    public static void addDisk(StorageDisk disk) {
        disks.add(disk);
    }

    public static void removeDisk(StorageDisk disk) {
        disks.removeIf(d -> d.getUuid().equals(disk.getUuid()));
    }

    public static StorageDisk getOrGetDisk(Drive drive, String identifier) {
        Optional<StorageDisk> disk = getDisk(identifier);

        return disk.orElseGet(() -> new StorageDisk(drive, identifier));
    }

    public static StorageDisk getOrGetDisk(Drive drive, UUID uuid) {
        return getOrGetDisk(drive, uuid.toString());
    }

    public static void saveAll() {
        networks.forEach(Network::onSave);
        disks.forEach(StorageDisk::save);
    }

    public static void clearAll() {
        networks.clear();
        disks.clear();
    }

    public static void onShutdown() {
        saveAll();
        clearAll();
    }

    public static void onClickItem(StoredItem item, InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        ClickType type = event.getClick();
        ItemStack cursor = event.getCursor();

        Optional<ScreenInstance> screenOptional = ScreenManager.getScreen(player);
        if (screenOptional.isEmpty()) {
            return;
        }

        ScreenInstance screen = screenOptional.get();
        Optional<Optional<Network>> networkOptional = screen.getScreenBlock().filter(NetworkBlock.class::isInstance).map(NetworkBlock.class::cast).map(NetworkBlock::getNetwork);
        if (networkOptional.isEmpty()) {
            return;
        }
        if (networkOptional.get().isEmpty()) {
            return;
        }
        Network network = networkOptional.get().get();

        if (type == ClickType.LEFT) {
            if (cursor != null && item.isComparable(cursor)) {
                // item is greater than 0
                if (item.getAmount().compareTo(BigInteger.ZERO) > 0) {
                    cursor.setAmount(cursor.getAmount() + 1);
                    network.removeItem(item, BigInteger.ONE);

                    event.setCancelled(true);
                } else {
                    // item is 0
                    if (cursor.getAmount() > 0) {
                        cursor.setAmount(cursor.getAmount() - 1);
                        item.setAmount(item.getAmount().add(BigInteger.ONE));

                        event.setCancelled(true);
                    } else {
                        // cursor is 0
                        event.setCancelled(true);
                    }
                }
            } else if (cursor != null && ! item.isComparable(cursor)) {
                if (network.canInsert(cursor)) {
                    network.insert(cursor);
                } else {
                    if (network.canInsertOne(cursor)) {
                        ItemStack one = cursor.clone();
                        one.setAmount(1);

                        network.insert(one);

                        cursor.setAmount(cursor.getAmount() - 1);

                        event.setCancelled(true);
                    } else {
                        event.setCancelled(true);
                    }
                    event.setCancelled(true);
                }
            }
        }
    }

    public static Optional<Network> getNetworkAt(Block block) {
        return Restored.getBlockMap().getNetworkAtBlock(block);
    }

    public static void saveNetworkBlockAt(NetworkBlock networkBlock) {
        saveDataBlockAt(networkBlock.getDataBlock());
    }

    public static void removeNetworkedBlock(Block block) {
        removeDataBlockAt(block);
    }

    public static Optional<DataBlock> getDataBlockAt(Block block) {
        return Restored.getBlockMap().getDataBlockAt(block);
    }

    public static Optional<DataBlock> getDataBlockAt(Block block, Network network) {
        return Restored.getBlockMap().getDataBlockAt(block, network);
    }

    public static void saveDataBlockAt(DataBlock dataBlock) {
        Restored.getBlockMap().saveBlock(dataBlock);
    }

    public static void removeDataBlockAt(Block block) {
        Restored.getBlockMap().removeBlock(block);
    }

    public static void onBreakBlock(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        Optional<Network> optionalNetwork = NetworkManager.getNetworkAt(block);
        if (optionalNetwork.isEmpty()) return;

        Network network = optionalNetwork.get();
        if (! network.hasPermission(player, PermissionNode.NETWORK_BREAK)) {
            Sender sender = new Sender(player);
            sender.sendMessage("&cYou do not have permission to break this block!");

            event.setCancelled(true);
            return;
        }

        network.onBlockBreak(event);

        removeDataBlockAt(block);
    }

    public static void onBlockClick(PlayerInteractEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        if (block == null) return;

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK) return;

        Optional<Network> optionalNetwork = getNetworkAt(block);
        if (optionalNetwork.isEmpty()) {
            Restored.getInstance().logInfo("No network found at block...");
            Optional<NetworkBlock> optionalNetworkBlock = getNetworkBlockAt(block);
            if (optionalNetworkBlock.isEmpty()) {
                Restored.getInstance().logInfo("No network or network block found at block...");
                return;
            }

            NetworkBlock networkBlock = optionalNetworkBlock.get();

            networkBlock.onRightClick(player);
            event.setCancelled(true);

            return;
        } else {
            Restored.getInstance().logInfo("Network found at block...");
        }

        Network network = optionalNetwork.get();
        if (! network.hasPermission(player, PermissionNode.NETWORK_ACCESS)) {
            Sender sender = new Sender(player);
            sender.sendMessage("&cYou do not have permission to access this network!");

            event.setCancelled(true);
            return;
        }

        network.onBlockClick(event);

        event.setCancelled(true);
    }

    private static Optional<NetworkBlock> getNetworkBlockAt(Block block) {
        Optional<DataBlock> optionalDataBlock = Restored.getBlockMap().getDataBlockAt(block);
        if (optionalDataBlock.isEmpty()) return Optional.empty();

        DataBlock dataBlock = optionalDataBlock.get();

        return dataBlock.getNetworkBlock();
    }

    public static void onBlockPlace(BlockPlaceEvent event) {
        if (event.isCancelled()) return;

        Restored.getInstance().logInfo("Handling block placement for " + NetworkManager.class.getSimpleName() + "...");

        Block block = event.getBlockAgainst();
        if (block == null) return;

        ItemStack item = event.getItemInHand();
        if (item == null) return;

        Optional<RestoredItem> optional = ItemManager.readItem(item);
        if (optional.isEmpty()) {
            Restored.getInstance().logInfo("Item is null...");
            return;
        }
        RestoredItem restoredItem = optional.get();

        if (restoredItem instanceof IPlaceable) {
            IPlaceable placeable = (IPlaceable) restoredItem;
            placeable.onPlace(event);
        }
    }

    public static boolean hasAdjacentNetwork(Block block) {
        return getAdjacentNetwork(block).isPresent();
    }

    public static Optional<Network> getAdjacentNetwork(Block block) {
        BlockFace[] faces = new BlockFace[] {
                BlockFace.NORTH,
                BlockFace.EAST,
                BlockFace.SOUTH,
                BlockFace.WEST,
                BlockFace.UP,
                BlockFace.DOWN
        };

        for (BlockFace face : faces) {
            Block relative = block.getRelative(face);

            Optional<Network> optionalNetwork = NetworkManager.getNetworkAt(relative);
            if (optionalNetwork.isPresent()) {
                return optionalNetwork;
            }
        }

        return Optional.empty();
    }

    public static ConcurrentSkipListSet<String> getOwnedNetworkUuids(Player player) {
        return Restored.getNetworkMapConfig().getOwnedNetworks(player.getUniqueId().toString());
    }

    public static ConcurrentSkipListSet<Network> getOwnedNetworks(Player player) {
        ConcurrentSkipListSet<Network> networks = new ConcurrentSkipListSet<>();

        for (String uuid : getOwnedNetworkUuids(player)) {
            Optional<Network> network = NetworkManager.getNetwork(UUID.fromString(uuid));
            network.ifPresent(NetworkManager::loadNetwork);
        }

        return networks;
    }

    public static ConcurrentSkipListSet<String> getAllNetworkUuids() {
        return Restored.getNetworkMapConfig().getNetworkIdentifiers();
    }

    public static CompletableFuture<ConcurrentSkipListSet<Network>> getAllNetworks() {
        return CompletableFuture.supplyAsync(() -> {
            ConcurrentSkipListSet<Network> networks = new ConcurrentSkipListSet<>();

            for (String uuid : getAllNetworkUuids()) {
                CompletableFuture.runAsync(() -> NetworkManager.getOrGetNetwork(UUID.fromString(uuid)));
            }

            return networks;
        });
    }

    public static boolean isOwnerOf(String networkUuid, Player player) {
        return getOwnedNetworks(player).stream().anyMatch(n -> n.getUuid().toString().equalsIgnoreCase(networkUuid));
    }

    public static boolean isNetwork(String networkUuid) {
        return getAllNetworkUuids().stream().anyMatch(uuid -> uuid.equalsIgnoreCase(networkUuid));
    }

    public static boolean isValidPermission(String permission) {
        return getPermission(permission) != PermissionNode.ERROR;
    }

    public static PermissionNode getPermission(String permission) {
        try {
            return PermissionNode.valueOf(permission.toUpperCase());
        } catch (IllegalArgumentException e) {
            return PermissionNode.ERROR;
        }
    }

    public static ConcurrentSkipListSet<String> getValidPermissions() {
        ConcurrentSkipListSet<String> permissions = new ConcurrentSkipListSet<>();

        for (PermissionNode node : PermissionNode.values()) {
            if (node != PermissionNode.ERROR) permissions.add(node.getNode());
        }

        return permissions;
    }

    public static void setPermission(String networkIdentifier, Player other, String permission, boolean value) {
        Optional<Network> networkOptional = getOrGetNetwork(networkIdentifier);
        if (networkOptional.isEmpty()) return;

        if (permission.equalsIgnoreCase("all")) {
            if (value) {
                networkOptional.get().getPermissionSystem().trustWithAll(other);
            } else {
                networkOptional.get().getPermissionSystem().removeTrustWithAll(other);
            }

            return;
        }

        PermissionNode node = getPermission(permission);
        if (node == PermissionNode.ERROR) return;

        Network network = networkOptional.get();
        if (value) {
            network.getPermissionSystem().trust(node, other);
        } else {
            network.getPermissionSystem().removeTrust(node, other);
        }
    }
}
