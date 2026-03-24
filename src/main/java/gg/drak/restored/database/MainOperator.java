package gg.drak.restored.database;

import gg.drak.restored.Restored;
import gg.drak.restored.data.Network;
import gg.drak.restored.data.blocks.LocatedBlock;
import gg.drak.restored.data.blocks.SingleNetworkMap;
import gg.drak.restored.data.blocks.impl.Drive;
import gg.drak.restored.data.disks.StorageDisk;
import gg.drak.restored.database.dao.*;
import host.plas.bou.sql.DBOperator;
import lombok.Getter;

import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * Main database operator for the Restored plugin.
 * Handles database connection and table creation.
 * Provides access to all DAOs.
 */
@Getter
public class MainOperator extends DBOperator {
    private final NetworkDAO networkDAO;
    private final NetworkBlockDAO networkBlockDAO;
    private final DiskDAO diskDAO;
    private final PermissionDAO permissionDAO;
    private final FilterDAO filterDAO;
    private final DatabaseMiddleware middleware;
    
    public MainOperator() {
        super(Restored.getDatabaseConfig().getConnectorSet(), Restored.getInstance());
        
        // Initialize Middleware
        this.middleware = new DatabaseMiddleware(this);

        // Initialize DAOs
        this.networkDAO = new NetworkDAO(this);
        this.networkBlockDAO = new NetworkBlockDAO(this);
        this.diskDAO = new DiskDAO(this);
        this.permissionDAO = new PermissionDAO(this);
        this.filterDAO = new FilterDAO(this);
    }

    @Override
    public void ensureTables() {
        try {
            String s1 = Statements.getStatement(Statements.StatementType.CREATE_TABLES, getConnectorSet());

            execute(s1, stmt -> {});

            // Migration: Ensure DriveId and Slot columns exist in Disks table
            try {
                if (getConnectorSet().getType() == host.plas.bou.sql.DatabaseType.MYSQL) {
                    execute("ALTER TABLE `" + getConnectorSet().getTablePrefix() + "Disks` ADD COLUMN IF NOT EXISTS DriveId VARCHAR(255) DEFAULT NULL AFTER Identifier;", stmt -> {});
                    execute("ALTER TABLE `" + getConnectorSet().getTablePrefix() + "Disks` ADD COLUMN IF NOT EXISTS Slot INTEGER AFTER DriveId;", stmt -> {});
                } else {
                    // SQLite doesn't support ADD COLUMN IF NOT EXISTS easily, but we can try and ignore errors
                    try { execute("ALTER TABLE `" + getConnectorSet().getTablePrefix() + "Disks` ADD COLUMN DriveId TEXT DEFAULT NULL;", stmt -> {}); } catch (Exception ignored) {}
                    try { execute("ALTER TABLE `" + getConnectorSet().getTablePrefix() + "Disks` ADD COLUMN Slot INTEGER;", stmt -> {}); } catch (Exception ignored) {}
                }
            } catch (Exception e) {
                // Ignore errors if columns already exist
            }
        } catch (Exception e) {
            Restored.getInstance().logSevere("Failed to ensure database tables", e);
        }
    }

    @Override
    public void ensureDatabase() {
        try {
            String s1 = Statements.getStatement(Statements.StatementType.CREATE_DATABASE, getConnectorSet());

            execute(s1, stmt -> {});
        } catch (Exception e) {
            Restored.getInstance().logSevere("Failed to ensure database", e);
        }
    }

    public java.util.concurrent.ConcurrentSkipListSet<SingleNetworkMap> getNetworkMaps() {
        java.util.concurrent.ConcurrentSkipListSet<SingleNetworkMap> maps = new java.util.concurrent.ConcurrentSkipListSet<>();

        for (NetworkDAO.NetworkData data : networkDAO.getAll()) {
            getNetworkMap(data.getIdentifier()).ifPresent(maps::add);
        }

        return maps;
    }

    public java.util.Optional<SingleNetworkMap> getNetworkMap(String identifier) {
        return networkDAO.getById(identifier).map(data -> {
            java.util.List<NetworkBlockDAO.NetworkBlockData> blocks = networkBlockDAO.getByNetworkId(identifier);
            java.util.concurrent.ConcurrentSkipListSet<LocatedBlock> locatedBlocks = new java.util.concurrent.ConcurrentSkipListSet<>();

            for (NetworkBlockDAO.NetworkBlockData blockData : blocks) {
                locatedBlocks.add(new LocatedBlock(blockData.getIdentifier(), blockData.getNetworkId(), blockData.getBlockType(), blockData.asBlockLocation(), blockData.getData()));
            }

            return new SingleNetworkMap(data.getIdentifier(), data.getOwnerUuid(), locatedBlocks);
        });
    }

    public void saveNetworkMap(SingleNetworkMap singleNetworkMap) {
        networkDAO.insert(singleNetworkMap.getIdentifier(), singleNetworkMap.getOwnerUUID());
        // Blocks are saved individually by NetworkBlock.onSave()
    }

    public ConcurrentSkipListSet<Network> getAllNetworks() {
        ConcurrentSkipListSet<Network> networks = new ConcurrentSkipListSet<>();
        for (NetworkDAO.NetworkData data : getNetworkDAO().getAll()) {
            // Check if already in middleware to avoid duplicate instances
            Network network = getMiddleware().getCachedNetwork(data.getIdentifier())
                    .orElseGet(() -> new Network(data.getIdentifier(), data.getOwnerUuid()));

            // 1. Load all block data into the network's map
            getNetworkBlockDAO().getByNetworkId(data.getIdentifier()).forEach(blockData -> {
                LocatedBlock locatedBlock = new LocatedBlock(
                        blockData.getIdentifier(),
                        blockData.getNetworkId(),
                        blockData.getBlockType(),
                        blockData.asBlockLocation(),
                        blockData.getData()
                );
                network.getNetworkMap().addLocatedBlock(locatedBlock);
            });

            // 2. Set the controller and instantiate all blocks
            network.getNetworkMap().getControllerImpl(Optional.of(network)).ifPresent(network::setController);
            network.updateCache(); // This creates the live NetworkBlock instances (Drives, Viewers, etc.)

            // 3. Load disks for any Drive blocks found
            network.getBlocks().forEach(block -> {
                if (block instanceof Drive) {
                    Drive drive = (Drive) block;
                    getDiskDAO().getByDriveId(drive.getIdentifier()).forEach(diskData -> {
                        // Use NetworkManager to ensure disk is cached in middleware
                        StorageDisk disk = gg.drak.restored.data.NetworkManager.getOrGetDisk(drive, diskData.getIdentifier(), diskData.getSlot());
                        disk.setCapacity(diskData.getCapacity());
                        disk.setContents(diskData.getItems());
                        drive.getDisks().put(diskData.getSlot(), disk);
                    });
                }
            });

            // 4. Load permissions
            getPermissionDAO().getByNetworkId(data.getIdentifier()).forEach(permissionData -> {
                network.getPermissionSystem().trust(permissionData.getPermissionNode(), permissionData.getPlayerUuid());
            });

            networks.add(network);
        }
        return networks;
    }
}
