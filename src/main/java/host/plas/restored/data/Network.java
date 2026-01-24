package host.plas.restored.data;

import gg.drak.thebase.objects.Identifiable;
import gg.drak.thebase.storage.resources.flat.simple.SimpleConfiguration;
import host.plas.restored.Restored;
import host.plas.restored.data.blocks.NetworkMap;
import host.plas.restored.data.blocks.datablock.DataBlock;
import host.plas.restored.data.blocks.impl.Controller;
import host.plas.restored.data.blocks.NetworkBlock;
import host.plas.restored.data.permission.PermissionSystem;
import host.plas.restored.utils.IOUtils;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicBoolean;

@Getter @Setter
public class Network extends SimpleConfiguration {
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

    public String getFileName() {
        return getFileName(identifier);
    }

    public static String getFileName(String identifier) {
        return identifier + ".yml";
    }

    public Network(String identifier, String ownerUuid) {
        super(getFileName(identifier), IOUtils.getNetworkFolder(), false);

        this.identifier = identifier;
        this.ownerUuid = ownerUuid;
        this.permissionSystem = new PermissionSystem(this);

        this.cachedBlocks = new ConcurrentSkipListSet<>();
    }

    public Network(String identifier, Block controller, Player owner) {
        this(identifier, owner.getUniqueId().toString());

        Controller c = new Controller(this, controller.getLocation());
        this.controller = c;
        c.onPlaced();
    }

    public Network(Block controller, Player owner) {
        this(NetworkMap.generateUUID(), controller, owner);
    }

    @Override
    public void init() {

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

            AtomicBoolean isAlreadyConnected = new AtomicBoolean(false);
            connectedBlocks.forEach(b -> {
                if (b.getBlock().equals(relative)) {
                    isAlreadyConnected.set(true);
                }
            });
            if (isAlreadyConnected.get()) continue;

            Optional<DataBlock> dataBlock = NetworkManager.getDataBlockAt(relative, this);
            if (dataBlock.isPresent()) {
                Restored.getInstance().logInfo("DataBlock is present");

                DataBlock b = dataBlock.get();

                Optional<NetworkBlock> blockOptional = b.getNetworkBlock();
                if (blockOptional.isEmpty()) {
                    Restored.getInstance().logInfo("NetworkBlock is empty");
                    continue;
                }
                NetworkBlock block = blockOptional.get();
                Restored.getInstance().logInfo("NetworkBlock is present");

                connectedBlocks.add(block);
                iterateConnected(faces, relative, connectedBlocks);
            }
        }
    }
}
