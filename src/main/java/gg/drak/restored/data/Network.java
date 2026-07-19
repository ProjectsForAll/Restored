package gg.drak.restored.data;

import gg.drak.restored.Restored;
import gg.drak.restored.data.blocks.BlockLocation;
import gg.drak.restored.data.blocks.LocatedBlock;
import gg.drak.restored.data.blocks.NetworkMap;
import gg.drak.restored.data.blocks.SingleNetworkMap;
import gg.drak.restored.data.blocks.impl.*;
import gg.drak.restored.data.blocks.NetworkBlock;
import gg.drak.restored.data.disks.StorageDisk;
import gg.drak.restored.data.items.impl.*;
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

    public List<StorageDisk> getDisks() {
        List<StorageDisk> disks = new ArrayList<>();

        // Collect drives sorted by priority (descending)
        List<Drive> drives = new ArrayList<>();
        getBlocks().forEach(block -> {
            if (block instanceof Drive) {
                drives.add((Drive) block);
            }
        });
        drives.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));

        for (Drive drive : drives) {
            disks.addAll(drive.getDisks().values());
        }

        return disks;
    }

    public List<ExternalStorage> getExternalStorages() {
        List<ExternalStorage> storages = new ArrayList<>();
        getBlocks().forEach(block -> {
            if (block instanceof ExternalStorage) {
                storages.add((ExternalStorage) block);
            }
        });
        storages.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
        return storages;
    }

    public boolean canInsert(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }

        List<StorageDisk> disks = getDisks();
        for (StorageDisk disk : disks) {
            if (disk.getRemainingCapacity().compareTo(BigInteger.ZERO) > 0) {
                return true;
            }
        }

        // Check external storages
        ItemStack probe = stack.clone();
        probe.setAmount(1);
        for (ExternalStorage es : getExternalStorages()) {
            if (es.canAcceptIntoContainer(probe)) {
                return true;
            }
        }

        return false;
    }

    /**
     * @return true if the full stack could be inserted into disks and/or external storage
     */
    public boolean canFullyInsert(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }

        BigInteger needed = BigInteger.valueOf(stack.getAmount());
        BigInteger diskSpace = BigInteger.ZERO;
        for (StorageDisk disk : getDisks()) {
            diskSpace = diskSpace.add(disk.getRemainingCapacity());
        }

        if (diskSpace.compareTo(needed) >= 0) {
            return true;
        }

        BigInteger externalNeeded = needed.subtract(diskSpace);
        if (externalNeeded.compareTo(BigInteger.ZERO) <= 0) {
            return true;
        }

        ItemStack probe = stack.clone();
        probe.setAmount(externalNeeded.min(BigInteger.valueOf(Integer.MAX_VALUE)).intValue());

        for (ExternalStorage es : getExternalStorages()) {
            int leftover = es.simulateInsertIntoContainer(probe);
            if (leftover <= 0) {
                return true;
            }
            probe.setAmount(leftover);
        }

        return probe.getAmount() <= 0;
    }

    /**
     * Insert items into the network's disks.
     * @return the number of items that could NOT be inserted (0 = all inserted).
     */
    public int insertItems(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return stack == null ? 0 : stack.getAmount();
        }

        ItemStack remaining = stack.clone();
        List<StorageDisk> disks = getDisks();

        // First pass: prefer disks that already contain this item type (keep items together)
        for (StorageDisk disk : disks) {
            if (remaining.getAmount() <= 0) break;
            if (disk.isFull()) continue;
            if (disk.getStoredItem(remaining).isEmpty()) continue; // skip disks that don't have this item yet

            BigInteger leftover = disk.addItem(remaining);
            disk.save();

            if (leftover.compareTo(BigInteger.ZERO) <= 0) {
                remaining.setAmount(0);
                break;
            }
            remaining.setAmount(leftover.intValue());
        }

        // Second pass: try any disk with space
        if (remaining.getAmount() > 0) {
            for (StorageDisk disk : disks) {
                if (remaining.getAmount() <= 0) break;
                if (disk.isFull()) continue;

                BigInteger leftover = disk.addItem(remaining);
                disk.save();

                if (leftover.compareTo(BigInteger.ZERO) <= 0) {
                    remaining.setAmount(0);
                    break;
                }
                remaining.setAmount(leftover.intValue());
            }
        }

        // Try external storages last
        if (remaining.getAmount() > 0) {
            for (ExternalStorage es : getExternalStorages()) {
                if (remaining.getAmount() <= 0) break;
                int leftover = es.insertIntoContainer(remaining);
                remaining.setAmount(leftover);
            }
        }

        return remaining.getAmount();
    }

    public boolean insert(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        int leftover = insertItems(stack);
        return leftover < stack.getAmount();
    }

    public boolean canInsertOne(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        
        ItemStack one = stack.clone();
        one.setAmount(1);
        
        return canInsert(one);
    }

    /**
     * @return the amount actually removed (may be less than requested)
     */
    public BigInteger removeItem(StoredItem item, BigInteger amount) {
        if (item == null || amount.compareTo(BigInteger.ZERO) <= 0) {
            return BigInteger.ZERO;
        }

        BigInteger remaining = amount;
        List<StorageDisk> disks = getDisks();

        // If the item knows which disk it lives in, try that disk first
        if (item.getDiskIdentifier() != null) {
            for (StorageDisk disk : disks) {
                if (!disk.getIdentifier().equals(item.getDiskIdentifier())) continue;

                Optional<StoredItem> storedItem = disk.getStoredItem(item.getItem());
                if (storedItem.isPresent()) {
                    BigInteger available = storedItem.get().getAmount();
                    BigInteger toRemove = remaining.min(available);

                    disk.removeItem(storedItem.get(), toRemove);
                    disk.save();

                    remaining = remaining.subtract(toRemove);
                }
                break;
            }
        }

        // Fall back to other disks if needed
        if (remaining.compareTo(BigInteger.ZERO) > 0) {
            for (StorageDisk disk : disks) {
                if (remaining.compareTo(BigInteger.ZERO) <= 0) break;
                // Skip the disk we already tried
                if (item.getDiskIdentifier() != null && disk.getIdentifier().equals(item.getDiskIdentifier())) continue;

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

        // Try external storages last
        if (remaining.compareTo(BigInteger.ZERO) > 0) {
            for (ExternalStorage es : getExternalStorages()) {
                if (remaining.compareTo(BigInteger.ZERO) <= 0) break;
                remaining = es.removeFromContainer(item.getItem(), remaining);
            }
        }

        return amount.subtract(remaining);
    }

    public Optional<ViewerPage> getPage(int pageIndex) {
        return getPage(pageIndex, 45);
    }

    public Optional<ViewerPage> getPage(int pageIndex, int itemsPerPage) {
        if (pageIndex < 1) {
            return Optional.empty();
        }

        List<StoredItem> allItems = new ArrayList<>();

        getDisks().forEach(disk -> {
            allItems.addAll(disk.getContents());
        });

        // Include external storage items
        for (ExternalStorage es : getExternalStorages()) {
            allItems.addAll(es.getExternalItems());
        }

        // Remove duplicates by combining items with the same type and metadata.
        // The merged StoredItem keeps the diskIdentifier of the first occurrence so
        // that removal can target the correct disk first.
        List<StoredItem> uniqueItems = new ArrayList<>();
        for (StoredItem item : allItems) {
            boolean merged = false;
            for (int i = 0; i < uniqueItems.size(); i++) {
                StoredItem existing = uniqueItems.get(i);
                if (existing.isComparable(item.getItem())) {
                    BigInteger combinedAmount = existing.getAmount().add(item.getAmount());
                    uniqueItems.set(i, new StoredItem(existing.getIdentifier(), existing.getDiskIdentifier(), combinedAmount, existing.getItem()));
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                uniqueItems.add(item);
            }
        }

        if (uniqueItems.isEmpty()) {
            return Optional.empty();
        }

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

    public void onBlockPlace(Block block, ExternalStorageItem item) {
        ExternalStorage externalStorage = new ExternalStorage(this, block.getLocation());
        externalStorage.onPlaced();
        updateCache();
    }

    public void onBlockPlace(Block block, ImporterItem item) {
        Importer importer = new Importer(this, block.getLocation());
        importer.onPlaced();
        updateCache();
    }

    public void onBlockPlace(Block block, ExporterItem item) {
        Exporter exporter = new Exporter(this, block.getLocation());
        exporter.onPlaced();
        updateCache();
    }

    public void onBlockPlace(Block block, CrafterItem item) {
        Crafter crafter = new Crafter(this, block.getLocation());
        crafter.onPlaced();
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
