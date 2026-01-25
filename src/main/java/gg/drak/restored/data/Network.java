package gg.drak.restored.data;

import gg.drak.restored.Restored;
import gg.drak.restored.data.blocks.BlockLocation;
import gg.drak.restored.data.blocks.LocatedBlock;
import gg.drak.restored.data.blocks.NetworkMap;
import gg.drak.restored.data.blocks.SingleNetworkMap;
import gg.drak.restored.data.blocks.impl.Controller;
import gg.drak.restored.data.blocks.impl.CraftingViewer;
import gg.drak.restored.data.blocks.impl.Drive;
import gg.drak.restored.data.blocks.impl.Viewer;
import gg.drak.restored.data.blocks.NetworkBlock;
import gg.drak.restored.data.disks.StorageDisk;
import gg.drak.restored.data.items.impl.CraftingViewerItem;
import gg.drak.restored.data.items.impl.DriveItem;
import gg.drak.restored.data.items.impl.ViewerItem;
import gg.drak.restored.data.permission.PermissionNode;
import gg.drak.restored.data.permission.PermissionSystem;
import gg.drak.restored.data.screens.items.StoredItem;
import gg.drak.restored.data.screens.items.ViewerPage;
import gg.drak.restored.database.dao.PermissionDAO;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.math.BigInteger;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicBoolean;

@Getter @Setter
public class Network implements Comparable<Network> {
    private String identifier; // in UUID format
    private String ownerUuid; // Owner of the network
    private Controller controller; // Location of the start of the network

    private PermissionSystem permissionSystem;

    private ConcurrentSkipListSet<NetworkBlock> cachedBlocks;
    private Date lastCacheUpdate;

    public UUID getUuid() {
        return UUID.fromString(identifier);
    }

    public OfflinePlayer getOwner() {
        UUID uuid = UUID.fromString(ownerUuid);
        return Bukkit.getOfflinePlayer(uuid);
    }

    public Network(String identifier, String ownerUuid) {
        this.identifier = identifier;
        this.ownerUuid = ownerUuid;
        this.permissionSystem = new PermissionSystem(this);

        this.cachedBlocks = new ConcurrentSkipListSet<>();

        // Cache in middleware immediately
        Restored.getDatabase().getMiddleware().cacheNetwork(this);

        // Save to database immediately to avoid foreign key constraint issues when blocks are added
        save();
    }

    public void save() {
        // Save to database
        Restored.getDatabase().getNetworkDAO().insert(identifier, ownerUuid);
    }

    public Network(String identifier, Block controller, Player owner) {
        this(identifier, owner.getUniqueId().toString());

        Controller c = new Controller(this, controller.getLocation());
        this.controller = c;
        // Do not call updateCache here, it will be called when needed
        c.onPlaced();
    }

    public Network(Block controller, Player owner) {
        this(NetworkMap.generateUUID(), controller, owner);
    }

    public void init() {
        // Load permission system from database
        List<PermissionDAO.PermissionData> permissions =
                Restored.getDatabase().getPermissionDAO().getByNetworkId(identifier);

        for (PermissionDAO.PermissionData perm : permissions) {
            if (perm.getValue()) {
                permissionSystem.trust(
                        perm.getPermissionNode(),
                        perm.getPlayerUuid()
                );
            }
        }
    }

    public ConcurrentSkipListSet<NetworkBlock> getConnectedBlocks() {
        ConcurrentSkipListSet<NetworkBlock> connectedBlocks = new ConcurrentSkipListSet<>();

        // iterate out from the controller
        // and add all blocks to the list
        // that are connected to the controller
        // that are also not already in the list
        // and that are network blocks.
        // Include the controller in the list.
        Controller controller = getController();
        if (controller == null) {
            Restored.getInstance().logWarning("Controller is null");
            return connectedBlocks;
        }

        connectedBlocks.add(controller);
        Block controllerBlock = getController().getBlock();
        BlockFace[] faces = new BlockFace[] {
                BlockFace.NORTH,
                BlockFace.EAST,
                BlockFace.SOUTH,
                BlockFace.WEST,
                BlockFace.UP,
                BlockFace.DOWN
        };

        iterateConnected(faces, controllerBlock, connectedBlocks);

        return connectedBlocks;
    }

    public ConcurrentSkipListSet<NetworkBlock> getBlocks() {
        if (controller == null) {
            // Try to find the controller if it's missing
            getNetworkMap().getControllerImpl(Optional.of(this)).ifPresent(this::setController);
        }

        if (cachedBlocks == null || lastCacheUpdate == null) {
            updateCache();
        } else {
            // if is greater than 5 seconds ago
            if (lastCacheUpdate.before(new Date(System.currentTimeMillis() - (50 * 20 * 5)))) {
                updateCache();
            }
        }

        return cachedBlocks;
    }

    public void updateCache() {
        ConcurrentSkipListSet<NetworkBlock> newBlocks = getConnectedBlocks();
        
        // Ensure all blocks in the new set know they belong to this network
        newBlocks.forEach(block -> block.setNetwork(Optional.of(this)));

        // Replace the old cache with the new one to avoid stale instances
        this.cachedBlocks = newBlocks;

        lastCacheUpdate = new Date();
    }

    public void iterateConnected(BlockFace[] faces, Block iteratingBlock, ConcurrentSkipListSet<NetworkBlock> connectedBlocks) {
        for (BlockFace face : faces) {
            Block relative = iteratingBlock.getRelative(face);

            BlockLocation relLoc = gg.drak.restored.data.blocks.BlockLocation.of(relative);
            
            // Check if already in the list to avoid infinite recursion
            boolean alreadyProcessed = false;
            for (NetworkBlock b : connectedBlocks) {
                if (b.getBlockLocation().equals(relLoc)) {
                    alreadyProcessed = true;
                    break;
                }
            }
            if (alreadyProcessed) continue;

            Optional<LocatedBlock> locatedBlock = NetworkMap.getLocatedBlock(relLoc);
            if (locatedBlock.isPresent()) {
                // Check if we already have an instance in our global cache
                Optional<NetworkBlock> blockOptional = getNetworkBlock(relLoc);
                
                if (blockOptional.isEmpty()) {
                    blockOptional = NetworkManager.createNetworkBlock(this, locatedBlock.get());
                }

                if (blockOptional.isPresent()) {
                    NetworkBlock block = blockOptional.get();
                    block.setNetwork(Optional.of(this)); // Ensure the block knows its network

                    connectedBlocks.add(block);
                    iterateConnected(faces, relative, connectedBlocks);
                }
            }
        }
    }

    public SingleNetworkMap getNetworkMap() {
        Optional<SingleNetworkMap> map = NetworkMap.getNetworkMap(identifier);
        if (map.isPresent()) return map.get();
        
        // Create new map if it doesn't exist
        SingleNetworkMap newMap = new SingleNetworkMap(identifier, ownerUuid, new ConcurrentSkipListSet<>());
        NetworkMap.loadSingleMap(newMap);
        return newMap;
    }

    public void removeBlock(NetworkBlock block) {
        cachedBlocks.remove(block);
        getNetworkMap().removeLocatedBlock(block.getIdentifier());
        getNetworkMap().save();
    }

    public ConcurrentSkipListSet<StorageDisk> getDisks() {
        ConcurrentSkipListSet<StorageDisk> disks = new ConcurrentSkipListSet<>();
        
        getBlocks().forEach(block -> {
            if (block instanceof Drive) {
                Drive drive = (Drive) block;
                drive.getDisks().values().forEach(disks::add);
            }
        });
        
        return disks;
    }

    public boolean canInsert(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        
        ConcurrentSkipListSet<StorageDisk> disks = getDisks();
        if (disks.isEmpty()) {
            return false;
        }
        
        for (StorageDisk disk : disks) {
            if (disk.getRemainingCapacity().compareTo(BigInteger.ZERO) > 0) {
                return true;
            }
        }
        
        return false;
    }

    public boolean insert(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        
        ConcurrentSkipListSet<StorageDisk> disks = getDisks();
        if (disks.isEmpty()) {
            return false;
        }
        
        ItemStack remaining = stack.clone();
        int originalAmount = stack.getAmount();
        
        for (StorageDisk disk : disks) {
            if (remaining.getAmount() <= 0) {
                break;
            }
            
            if (! disk.isFull()) {
                BigInteger leftover = disk.addItem(remaining);
                disk.save();
                
                if (leftover.compareTo(BigInteger.ZERO) <= 0) {
                    remaining.setAmount(0);
                    break;
                }
                
                remaining.setAmount(leftover.intValue());
            }
        }
        
        return remaining.getAmount() < originalAmount;
    }

    public boolean canInsertOne(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        
        ItemStack one = stack.clone();
        one.setAmount(1);
        
        return canInsert(one);
    }

    public void removeItem(StoredItem item, BigInteger amount) {
        if (item == null || amount.compareTo(BigInteger.ZERO) <= 0) {
            return;
        }
        
        ConcurrentSkipListSet<StorageDisk> disks = getDisks();
        BigInteger remaining = amount;
        
        for (StorageDisk disk : disks) {
            if (remaining.compareTo(BigInteger.ZERO) <= 0) {
                break;
            }
            
            Optional<StoredItem> storedItem = disk.getStoredItem(item.getItem());
            if (storedItem.isPresent()) {
                BigInteger available = storedItem.get().getAmount();
                BigInteger toRemove = remaining.min(available);
                
                disk.removeItem(storedItem.get(), toRemove);
                disk.save();
                
                remaining = remaining.subtract(toRemove);
            }
        }
    }

    public Optional<ViewerPage> getPage(int pageIndex) {
        if (pageIndex < 1) {
            return Optional.empty();
        }
        
        List<StoredItem> allItems = new ArrayList<>();
        
        getDisks().forEach(disk -> {
            allItems.addAll(disk.getContents());
        });
        
        // Remove duplicates by combining items with the same type
        Map<ItemStack, StoredItem> itemMap = new HashMap<>();
        for (StoredItem item : allItems) {
            ItemStack key = StoredItem.flattenStack(item.getItem());
            itemMap.merge(key, item, (existing, newItem) -> {
                BigInteger combinedAmount = existing.getAmount().add(newItem.getAmount());
                return new StoredItem(existing.getIdentifier(), combinedAmount, existing.getItem());
            });
        }
        
        List<StoredItem> uniqueItems = new ArrayList<>(itemMap.values());
        
        int itemsPerPage = 45; // 5 rows * 9 columns
        int totalPages = (int) Math.ceil((double) uniqueItems.size() / itemsPerPage);
        
        if (pageIndex > totalPages) {
            return Optional.empty();
        }
        
        int startIndex = (pageIndex - 1) * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, uniqueItems.size());
        
        List<StoredItem> pageItems = uniqueItems.subList(startIndex, endIndex);
        
        return Optional.of(new ViewerPage(pageIndex, pageItems));
    }

    public void onSave() {
        // Save all blocks
        getBlocks().forEach(block -> {
            block.onSave();
        });
        
        // Save the network map
        getNetworkMap().save();
        
        // Save permission system to database
        // Clear existing permissions
        Restored.getDatabase().getPermissionDAO().removeAllPermissions(identifier);

        // Save current permissions
        permissionSystem.getTrusted().forEach((node, uuids) -> {
            for (String uuid : uuids) {
                Restored.getDatabase().getPermissionDAO().setPermission(identifier, uuid, node, true);
            }
        });
    }

    public boolean hasPermission(Player player, PermissionNode permission) {
        return permissionSystem.hasPermission(player, permission);
    }

    public void onBlockPlace(Block block, DriveItem item) {
        Drive drive = new Drive(this, block.getLocation());
        drive.onPlaced();
        updateCache();
    }

    public void onBlockPlace(Block block, ViewerItem item) {
        Viewer viewer = new Viewer(this, block.getLocation());
        viewer.onPlaced();
        updateCache();
    }

    public void onBlockPlace(Block block, CraftingViewerItem item) {
        CraftingViewer craftingViewer = new CraftingViewer(this, block.getLocation());
        craftingViewer.onPlaced();
        updateCache();
    }

    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Optional<NetworkBlock> networkBlockOptional = NetworkManager.getNetworkBlockAt(block);
        
        if (networkBlockOptional.isPresent()) {
            NetworkBlock networkBlock = networkBlockOptional.get();
            networkBlock.onBreak(event);
        }
    }

    public void onBlockClick(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null) return;
        
        Optional<NetworkBlock> networkBlockOptional = NetworkManager.getNetworkBlockAt(block);
        
        if (networkBlockOptional.isPresent()) {
            NetworkBlock networkBlock = networkBlockOptional.get();
            networkBlock.onRightClick(event.getPlayer());
        }
    }

    public void unload() {
        onSave();
        NetworkManager.unloadNetwork(this);
    }

    public void delete() {
        // Remove all blocks
        ConcurrentSkipListSet<NetworkBlock> blocks = new ConcurrentSkipListSet<>(getBlocks());
        blocks.forEach(block -> {
            block.clean();
        });
        
        // Delete the network map
        getNetworkMap().delete();
        
        // Delete from database
        Restored.getDatabase().getNetworkDAO().delete(identifier);
        Restored.getDatabase().getNetworkBlockDAO().deleteByNetworkId(identifier);
        Restored.getDatabase().getPermissionDAO().removeAllPermissions(identifier);
        
        // Unload from manager
        NetworkManager.getNetworks().removeIf(n -> n.getUuid().equals(getUuid()));
        NetworkMap.unloadSingleMap(getIdentifier());
    }

    public Optional<NetworkBlock> getNetworkBlock(BlockLocation location) {
        if (cachedBlocks == null) return Optional.empty();
        return cachedBlocks.stream().filter(block -> block.getBlockLocation().equals(location)).findFirst();
    }
    
    @Override
    public int compareTo(Network other) {
        if (other == null) {
            return 1;
        }
        return this.identifier.compareTo(other.identifier);
    }
}
