package host.plas.restored.data;

import host.plas.restored.Restored;
import host.plas.restored.data.blocks.datablock.DataBlock;
import host.plas.restored.data.blocks.impl.Controller;
import host.plas.restored.data.blocks.NetworkBlock;
import host.plas.restored.data.blocks.ScreenBlock;
import host.plas.restored.data.blocks.impl.Drive;
import host.plas.restored.data.blocks.impl.Viewer;
import host.plas.restored.data.disks.StorageDisk;
import host.plas.restored.data.items.RestoredItem;
import host.plas.restored.data.screens.items.StoredItem;
import host.plas.restored.data.screens.items.ViewerPage;
import host.plas.restored.data.permission.PermissionNode;
import host.plas.restored.data.permission.PermissionSystem;
import host.plas.restored.data.screens.ScreenManager;
import host.plas.restored.data.storage.NetworkSerializable;
import host.plas.restored.utils.MessageUtils;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import tv.quaint.objects.Identifiable;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Getter @Setter
public class Network implements Identifiable {
    private String identifier; // in UUID format
    private String ownerUuid; // Owner of the network
    private Controller controller; // Location of the start of the network

    private NetworkSerializable storage;

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

    public Network(String identifier) {
        this.identifier = identifier;
        this.storage = new NetworkSerializable(this);
        this.permissionSystem = new PermissionSystem(this);

        onLoad();
    }

    public Network(String identifier, Block controller, Player owner) {
        this(identifier);
        this.ownerUuid = owner.getUniqueId().toString();

        this.controller = new Controller(this, controller.getLocation());
        getController().saveDataBlock();

        getStorage().saveController();

        getBlocks().forEach(NetworkBlock::saveDataBlock);

        onSave();
    }


    public Network(Block controller, Player owner) {
        this(UUID.randomUUID().toString(), controller, owner);
    }

    public void onLoad() {
        getStorage().onLoad();

        if (getController() != null) {
            getBlocks().forEach(block -> block.loadDataBlock(this));
        }
    }

    public void onSave() {
        Restored.getNetworkMap().saveNetwork(this);

        getStorage().onSave();

        saveAllBlocks();
    }

    public ConcurrentSkipListSet<StorageDisk> getDisks() {
        ConcurrentSkipListSet<StorageDisk> disks = new ConcurrentSkipListSet<>();

        getBlocks().forEach(b -> {
            if (b instanceof Drive) {
                Drive drive = (Drive) b;
                disks.addAll(drive.getDisks().values());
            }
        });

        return disks;
    }

    public ConcurrentSkipListSet<StoredItem> getContents() {
        ConcurrentSkipListSet<StoredItem> contents = new ConcurrentSkipListSet<>();

        getDisks().forEach(d -> {
            contents.addAll(d.getContents());
        });

        return contents;
    }

    public List<ItemStack> getAllContents() {
        List<ItemStack> contents = new ArrayList<>();

        getContents().forEach(c -> {
            BigInteger leftAmount = c.getAmount();
            while (leftAmount.compareTo(BigInteger.ZERO) > 0) {
                int amount = leftAmount.compareTo(BigInteger.valueOf(64)) > 0 ? 64 : leftAmount.intValue();
                ItemStack stack = c.getItem().clone();
                stack.setAmount(amount);

                contents.add(stack);

                leftAmount = leftAmount.subtract(BigInteger.valueOf(amount));
            }
        });

        return contents;
    }

    public List<StoredItem> getPageContents(int index) {
        return getPage(index).getContents();
    }

    public ViewerPage getPage(int index) {
        return getContentPages().get(index);
    }

    public List<ViewerPage> getContentPages() {
        List<ViewerPage> pages = new ArrayList<>();

        int index = 0;
        int itemIndex = 0;
        int itemsLeft = getContents().size();

        while (itemsLeft > 0) {
            int itemsOnPage = Math.min(itemsLeft, 45);

            List<StoredItem> pageContents = new ArrayList<>();

            for (int i = 0; i < itemsOnPage; i++) {
                pageContents.add(getSortedItemList().get(itemIndex));
                itemIndex++;
            }

            itemsLeft -= itemsOnPage;

            pages.add(new ViewerPage(index, pageContents));
            index++;
        }

        return pages;
    }

    public List<StoredItem> getSortedItemList() {
        ConcurrentSkipListSet<StoredItem> unsorted = getContents();

        List<StoredItem> sorted = new ArrayList<>(unsorted);

        sorted.sort(Comparator.comparing(i -> i.getItem().getType()));

        return sorted;
    }

    public void removeItem(StoredItem storedItem, BigInteger amount) {
        AtomicBoolean removed = new AtomicBoolean(false);

        getDisks().forEach(d -> {
            if (removed.get()) return;

            if (d.contains(storedItem)) {
                d.removeItem(storedItem, amount);
                removed.set(true);
            }
        });

        redrawInventories();
    }

    public void redrawInventories() {
        getBlocks().forEach(b -> {
            if (b instanceof ScreenBlock) {
                ((ScreenBlock) b).onRedraw();
            }
        });
    }

    public boolean canInsert(ItemStack stack) {
        AtomicBoolean canInsert = new AtomicBoolean(false);

        getDisks().forEach(d -> {
            if (canInsert.get()) return;

            canInsert.set(d.hasSpaceFor(stack));
        });

        return canInsert.get();
    }

    public boolean canInsertOne(ItemStack stack) {
        ItemStack c = stack.clone();
        c.setAmount(1);

        return canInsert(c);
    }

    public boolean insert(ItemStack stack) {
        AtomicBoolean inserted = new AtomicBoolean(false);

        getDisks().forEach(d -> {
            if (inserted.get()) return;

            if (d.hasSpaceFor(stack)) {
                d.addItem(stack);
                inserted.set(true);
            }
        });

        redrawInventories();

        return inserted.get();
    }

    public boolean isBlockInNetwork(Block block) {
        AtomicBoolean atomicBoolean = new AtomicBoolean(false);

        getBlocks().forEach(b -> {
            if (atomicBoolean.get()) return;

            if (b.getBlock().equals(block)) {
                atomicBoolean.set(true);
            }
        });

        return atomicBoolean.get();
    }

    public Location getControllerLocation() {
        return getController().getLocation();
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
        if (controller == null) return connectedBlocks;

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
        if (cachedBlocks == null) {
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
        cachedBlocks = new ConcurrentSkipListSet<>();

        getConnectedBlocks().forEach(b -> {
            if (b != null) {
                cachedBlocks.add(b);
            }
        });

        lastCacheUpdate = new Date();
    }

    public void iterateConnected(BlockFace[] faces, Block iteratingBlock, ConcurrentSkipListSet<NetworkBlock> connectedBlocks) {
        for (BlockFace face : faces) {
            Block relative = iteratingBlock.getRelative(face);
            Optional<DataBlock> dataBlock = NetworkManager.getDataBlockAt(relative, this);
            if (dataBlock.isPresent()) {
                DataBlock b = dataBlock.get();

                Optional<NetworkBlock> blockOptional = b.getNetworkBlock();
                if (blockOptional.isEmpty()) continue;
                NetworkBlock block = blockOptional.get();

                if (! connectedBlocks.contains(block)) {
                    connectedBlocks.add(block);
                    iterateConnected(faces, relative, connectedBlocks);
                }
            }
        }
    }

    public void saveAllBlocks() {
        getBlocks().forEach(NetworkManager::saveNetworkBlockAt);
    }

    public void removeBlock(NetworkBlock block) {
        NetworkManager.removeNetworkedBlock(block.getBlock());

        getBlocks().removeIf(b -> b.getUuid().equals(block.getUuid()));
    }

    public Optional<NetworkBlock> getNetworkBlock(Block block) {
        AtomicReference<Optional<NetworkBlock>> networkBlock = new AtomicReference<>(Optional.empty());

        getBlocks().forEach(b -> {
            if (b.getBlock().equals(block)) {
                networkBlock.set(Optional.of(b));
            }
        });

        return networkBlock.get();
    }

    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();

        Optional<NetworkBlock> networkBlock = getNetworkBlock(block);
        if (networkBlock.isPresent()) {
            NetworkBlock b = networkBlock.get();
            if (b instanceof ScreenBlock) {
                ScreenBlock screenBlock = (ScreenBlock) b;
                ScreenManager.getPlayersOf(screenBlock).forEach((p, s) -> {
                    s.close();
                });
            }

            if (b.equals(getController())) {
                event.setCancelled(true);

                b.onBreak(event);
                delete();
                return;
            }

            event.setCancelled(true);

            b.onBreak(event);
            removeBlock(b);
        }
    }

    public void delete() {
        getBlocks().forEach(block -> {
            if (block instanceof Controller) return;

            block.setNetwork(Optional.empty());
            block.getDataBlock().setNetwork(Optional.empty());
            block.saveDataBlock();

            getBlocks().remove(block);
        });
        getStorage().delete();

        NetworkManager.removeNetwork(this);
    }

    public void onBlockClick(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();

        Optional<NetworkBlock> networkBlock = getNetworkBlock(block);
        if (networkBlock.isPresent()) {
            NetworkBlock b = networkBlock.get();
            if (b instanceof ScreenBlock) {
                ScreenBlock screenBlock = (ScreenBlock) b;
                ScreenManager.getPlayersOf(screenBlock).forEach((p, s) -> {
                    s.close();
                });

                screenBlock.onRightClick(event.getPlayer());
            }
        } else {
            MessageUtils.logInfo("No network block found...");
        }
    }

    public boolean hasPermission(Player player, PermissionNode permission) {
        return getPermissionSystem().hasPermission(player, permission);
    }

    public void onBlockPlace(Block block, RestoredItem item) {
        NetworkBlock networkBlock = null;

        switch (item.getType()) {
            case CONTROLLER:
                if (getController() == null) {
                    networkBlock = new Controller(this, block.getLocation());

                    setController((Controller) networkBlock);
                }
                break;
            case DRIVE:
                if (getController() != null) {
                    networkBlock = new Drive(this, block.getLocation());
                }
                break;
            case VIEWER:
                if (getController() != null) {
                    networkBlock = new Viewer(this, block.getLocation());
                }
                break;
        }

        if (networkBlock != null) {
            networkBlock.onPlaced();
        }
    }
}
