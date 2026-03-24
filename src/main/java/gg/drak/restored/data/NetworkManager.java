package gg.drak.restored.data;

import host.plas.bou.commands.Sender;
import host.plas.bou.gui.ScreenManager;
import host.plas.bou.gui.screens.ScreenInstance;
import gg.drak.restored.Restored;
import gg.drak.restored.data.blocks.NetworkBlock;
import gg.drak.restored.data.blocks.BlockLocation;
import gg.drak.restored.data.blocks.BlockType;
import gg.drak.restored.data.blocks.LocatedBlock;
import gg.drak.restored.data.blocks.NetworkMap;
import gg.drak.restored.data.blocks.SingleNetworkMap;
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
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListSet;

public class NetworkManager {
    public static Optional<Network> getNetwork(String identifier) {
        Optional<Network> cached = Restored.getDatabase().getMiddleware().getCachedNetwork(identifier);
        if (cached.isPresent()) return cached;
        
        return Restored.getDatabase().getNetworkDAO().getById(identifier).map(data -> {
            Network network = new Network(data.getIdentifier(), data.getOwnerUuid());
            Restored.getDatabase().getMiddleware().cacheNetwork(network);
            return network;
        });
    }

    public static Optional<Network> getNetwork(UUID uuid) {
        return getNetwork(uuid.toString());
    }

    public static void loadNetwork(Network network) {
        Restored.getDatabase().getMiddleware().cacheNetwork(network);
    }

    public static void unloadNetwork(Network network) {
        Restored.getDatabase().getMiddleware().removeNetworkFromCache(network.getIdentifier());

        NetworkMap.unloadSingleMap(network.getIdentifier());
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

    public static Optional<StorageDisk> getDisk(String identifier) {
        Optional<StorageDisk> cached = Restored.getDatabase().getMiddleware().getCachedDisk(identifier);
        if (cached.isPresent()) return cached;
        
        return Restored.getDatabase().getDiskDAO().getById(identifier).map(data -> {
            StorageDisk disk = new StorageDisk(null, data.getIdentifier(), data.getSlot());
            disk.setCapacity(data.getCapacity());
            disk.setContents(data.getItems());
            Restored.getDatabase().getMiddleware().cacheDisk(disk);
            return disk;
        });
    }

    public static Optional<StorageDisk> getDisk(UUID uuid) {
        return getDisk(uuid.toString());
    }

    public static void addDisk(StorageDisk disk) {
        Restored.getDatabase().getMiddleware().cacheDisk(disk);
    }

    public static void removeDisk(StorageDisk disk) {
        Restored.getDatabase().getMiddleware().removeDiskFromCache(disk.getIdentifier());
    }

    public static StorageDisk getOrGetDisk(Drive drive, String identifier) {
        Optional<StorageDisk> disk = getDisk(identifier);

        return disk.orElseGet(() -> {
            StorageDisk newDisk = new StorageDisk(drive, identifier);
            addDisk(newDisk);
            return newDisk;
        });
    }

    public static StorageDisk getOrGetDisk(Drive drive, String identifier, int slot) {
        Optional<StorageDisk> disk = getDisk(identifier);

        return disk.orElseGet(() -> {
            StorageDisk newDisk = new StorageDisk(drive, identifier, slot);
            addDisk(newDisk);
            return newDisk;
        });
    }

    public static StorageDisk getOrGetDisk(Drive drive, UUID uuid) {
        return getOrGetDisk(drive, uuid.toString());
    }

    public static void saveAll() {
        Restored.getDatabase().getMiddleware().getAllCachedNetworks().forEach(Network::onSave);
        Restored.getDatabase().getMiddleware().getAllCachedDisks().forEach(StorageDisk::save);
    }

    public static void clearAll() {
        // No longer needed as middleware handles it, but kept for compatibility
    }

    public static ConcurrentSkipListSet<Network> getNetworks() {
        return new ConcurrentSkipListSet<>(Restored.getDatabase().getMiddleware().getAllCachedNetworks());
    }

    public static ConcurrentSkipListSet<StorageDisk> getDisks() {
        return new ConcurrentSkipListSet<>(Restored.getDatabase().getMiddleware().getAllCachedDisks());
    }

    public static void onShutdown() {
        saveAll();
        
        // Flush the database middleware to ensure all pending operations are executed
        Restored.getDatabase().getMiddleware().flush();
        
        clearAll();
    }

    public static void onClickItem(StoredItem item, InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        ClickType type = event.getClick();
        ItemStack cursor = event.getCursor();
        boolean cursorEmpty = cursor == null || cursor.getType().isAir() || cursor.getAmount() <= 0;

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

        Runnable redraw = () -> screen.getScreenBlock()
                .filter(NetworkBlock.class::isInstance)
                .map(NetworkBlock.class::cast)
                .ifPresent(NetworkBlock::redraw);

        if (type == ClickType.SHIFT_LEFT || type == ClickType.SHIFT_RIGHT) {
            if (cursorEmpty && item.getAmount().compareTo(BigInteger.ZERO) > 0) {
                BigInteger totalMoved = BigInteger.ZERO;
                BigInteger remaining = item.getAmount();
                int maxStack = Math.max(1, item.getItem().getMaxStackSize());
                while (remaining.compareTo(BigInteger.ZERO) > 0) {
                    BigInteger take = remaining.min(BigInteger.valueOf(maxStack));
                    int giveAmt = take.min(BigInteger.valueOf(Integer.MAX_VALUE)).intValue();
                    ItemStack give = item.getItem().clone();
                    give.setAmount(giveAmt);
                    HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(give);
                    if (! overflow.isEmpty()) {
                        break;
                    }
                    totalMoved = totalMoved.add(take);
                    remaining = remaining.subtract(take);
                }
                if (totalMoved.compareTo(BigInteger.ZERO) > 0) {
                    network.removeItem(item, totalMoved);
                    event.setCancelled(true);
                    redraw.run();
                }
                return;
            }
        }

        if (type == ClickType.LEFT) {
            if (cursorEmpty && item.getAmount().compareTo(BigInteger.ZERO) > 0) {
                ItemStack one = item.getItem().clone();
                one.setAmount(1);
                HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(one);
                if (! overflow.isEmpty()) {
                    event.setCancelled(true);
                    return;
                }
                network.removeItem(item, BigInteger.ONE);
                event.setCancelled(true);
                redraw.run();
                return;
            }
            if (cursor != null && item.isComparable(cursor)) {
                if (item.getAmount().compareTo(BigInteger.ZERO) > 0) {
                    cursor.setAmount(cursor.getAmount() + 1);
                    network.removeItem(item, BigInteger.ONE);
                    event.setCancelled(true);
                    redraw.run();
                } else {
                    if (cursor.getAmount() > 0) {
                        cursor.setAmount(cursor.getAmount() - 1);
                        item.setAmount(item.getAmount().add(BigInteger.ONE));
                        event.setCancelled(true);
                        redraw.run();
                    } else {
                        event.setCancelled(true);
                    }
                }
            } else if (cursor != null && ! item.isComparable(cursor)) {
                event.setCancelled(true);
                int before = cursor.getAmount();
                int leftoverAmt = network.insertItems(cursor);
                int inserted = before - leftoverAmt;
                if (inserted > 0) {
                    if (leftoverAmt <= 0) {
                        event.setCursor(null);
                    } else {
                        ItemStack next = cursor.clone();
                        next.setAmount(leftoverAmt);
                        event.setCursor(next);
                    }
                    redraw.run();
                } else if (network.canInsertOne(cursor)) {
                    ItemStack one = cursor.clone();
                    one.setAmount(1);
                    network.insertItems(one);
                    cursor.setAmount(cursor.getAmount() - 1);
                    if (cursor.getAmount() <= 0) {
                        event.setCursor(null);
                    }
                    redraw.run();
                }
            }
        }
    }

    public static Optional<Network> getNetworkAt(Block block) {
        return getNetworkBlockAt(block).flatMap(NetworkBlock::getNetwork);
    }

    public static void saveNetworkBlockAt(NetworkBlock networkBlock) {
        networkBlock.onSave();
    }

    public static void removeNetworkedBlock(Block block) {
        getNetworkBlockAt(block).ifPresent(NetworkBlock::clean);
    }

    public static Optional<NetworkBlock> getNetworkBlockAt(Block block) {
        return NetworkMap.getLocatedBlock(gg.drak.restored.data.blocks.BlockLocation.of(block)).flatMap(locatedBlock -> {
            return NetworkManager.getOrGetNetwork(locatedBlock.getNetwork()).flatMap(network -> createNetworkBlock(network, locatedBlock));
        });
    }

    public static Optional<NetworkBlock> createNetworkBlock(Network network, LocatedBlock locatedBlock) {
        try {
            com.google.gson.JsonObject data = new com.google.gson.Gson().fromJson(locatedBlock.getData(), com.google.gson.JsonObject.class);
            NetworkBlock block = BlockType.getBlock(locatedBlock.getType(), UUID.fromString(locatedBlock.getIdentifier()), network, locatedBlock.getLocation().toLocation(), data);
            return Optional.ofNullable(block);
        } catch (Exception e) {
            Restored.getInstance().logSevere("Failed to create NetworkBlock instance for " + locatedBlock.getIdentifier(), e);
            return Optional.empty();
        }
    }

    public static void onBreakBlock(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        Optional<NetworkBlock> optionalNetworkBlock = getNetworkBlockAt(block);
        if (optionalNetworkBlock.isEmpty()) return;

        NetworkBlock networkBlock = optionalNetworkBlock.get();
        Optional<Network> optionalNetwork = networkBlock.getNetwork();
        if (optionalNetwork.isPresent()) {
            Network network = optionalNetwork.get();
            if (! network.hasPermission(player, PermissionNode.NETWORK_BREAK)) {
                Sender sender = new Sender(player);
                sender.sendMessage("&cYou do not have permission to break this block!");

                event.setCancelled(true);
                return;
            }
        }

        networkBlock.onBreak(event);
    }

    public static void onBlockClick(PlayerInteractEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        if (block == null) return;

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK) return;

        Optional<NetworkBlock> optionalNetworkBlock = getNetworkBlockAt(block);
        if (optionalNetworkBlock.isEmpty()) return;

        NetworkBlock networkBlock = optionalNetworkBlock.get();
        Optional<Network> optionalNetwork = networkBlock.getNetwork();
        
        if (optionalNetwork.isPresent()) {
            Network network = optionalNetwork.get();
            if (! network.hasPermission(player, PermissionNode.NETWORK_ACCESS)) {
                Sender sender = new Sender(player);
                sender.sendMessage("&cYou do not have permission to access this network!");

                event.setCancelled(true);
                return;
            }
        }

        networkBlock.onRightClick(player);
        event.setCancelled(true);
    }

    public static void onBlockPlace(BlockPlaceEvent event) {
        if (event.isCancelled()) return;

        Block block = event.getBlockAgainst();
        if (block == null) return;

        ItemStack item = event.getItemInHand();
        if (item == null) return;

        Optional<RestoredItem> optional = ItemManager.readItem(item);
        if (optional.isEmpty()) {
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
        ConcurrentSkipListSet<String> uuids = new ConcurrentSkipListSet<>();
        for (SingleNetworkMap map : NetworkMap.getNetworkMaps()) {
            if (map.getOwnerUUID().equalsIgnoreCase(player.getUniqueId().toString())) {
                uuids.add(map.getIdentifier());
            }
        }
        return uuids;
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
        ConcurrentSkipListSet<String> uuids = new ConcurrentSkipListSet<>();
        for (SingleNetworkMap map : NetworkMap.getNetworkMaps()) {
            uuids.add(map.getIdentifier());
        }
        return uuids;
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
