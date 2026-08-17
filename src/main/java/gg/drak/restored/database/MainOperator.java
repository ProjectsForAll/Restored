package gg.drak.restored.database;

import gg.drak.restored.Restored;
import gg.drak.restored.data.Network;
import gg.drak.restored.database.dao.NetworkAugmentDAO;
import gg.drak.restored.database.dao.NetworkDAO;
import gg.drak.restored.database.dao.NetworkItemDAO;
import gg.drak.restored.database.dao.NetworkLinkedChestDAO;
import gg.drak.restored.database.dao.NetworkOpenStatsDAO;
import gg.drak.restored.database.dao.NetworkPermissionDAO;
import gg.drak.restored.util.LinkedChestStorage;
import gg.drak.restored.util.NetworkBlockTags;
import host.plas.bou.sql.DBOperator;
import lombok.Getter;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
public class MainOperator extends DBOperator {
    private final NetworkDAO networkDAO;
    private final NetworkItemDAO networkItemDAO;
    private final NetworkPermissionDAO networkPermissionDAO;
    private final NetworkOpenStatsDAO networkOpenStatsDAO;
    private final NetworkAugmentDAO networkAugmentDAO;
    private final NetworkLinkedChestDAO networkLinkedChestDAO;
    private final DatabaseMiddleware middleware;

    public MainOperator() {
        super(Restored.getDatabaseConfig().getConnectorSet(), Restored.getInstance());
        this.middleware = new DatabaseMiddleware(this);
        this.networkDAO = new NetworkDAO(this);
        this.networkItemDAO = new NetworkItemDAO(this);
        this.networkPermissionDAO = new NetworkPermissionDAO(this);
        this.networkOpenStatsDAO = new NetworkOpenStatsDAO(this);
        this.networkAugmentDAO = new NetworkAugmentDAO(this);
        this.networkLinkedChestDAO = new NetworkLinkedChestDAO(this);
    }

    @Override
    public void ensureTables() {
        try {
            String statement = Statements.getStatement(Statements.StatementType.CREATE_TABLES, getConnectorSet());
            execute(statement, stmt -> {});
        } catch (Exception e) {
            Restored.getInstance().logSevere("Failed to ensure database tables", e);
        }
    }

    @Override
    public void ensureDatabase() {
        try {
            String statement = Statements.getStatement(Statements.StatementType.CREATE_DATABASE, getConnectorSet());
            execute(statement, stmt -> {});
        } catch (Exception e) {
            Restored.getInstance().logSevere("Failed to ensure database", e);
        }
    }

    public List<Network> loadAllNetworks() {
        List<Network> networks = new ArrayList<>();
        for (NetworkDAO.NetworkData data : networkDAO.getAll()) {
            Network network = new Network(UUID.fromString(data.getIdentifier()), UUID.fromString(data.getOwnerUuid()));
            network.setWorld(data.getWorld());
            network.setX(data.getX());
            network.setY(data.getY());
            network.setZ(data.getZ());
            network.setUpgradeCount(data.getUpgradeCount());

            for (NetworkItemDAO.ItemRow row : networkItemDAO.getByNetworkId(data.getIdentifier())) {
                network.loadItem(row.getItemKey(), row.getItemData(), row.getAmount());
            }

            for (NetworkPermissionDAO.PermissionRow row : networkPermissionDAO.getByNetworkId(data.getIdentifier())) {
                network.getRoles().put(UUID.fromString(row.getPlayerUuid()), row.getRole());
            }

            for (NetworkOpenStatsDAO.OpenStatRow row : networkOpenStatsDAO.getByNetworkId(data.getIdentifier())) {
                network.getOpenCounts().put(UUID.fromString(row.getPlayerUuid()), row.getOpens());
            }

            for (NetworkAugmentDAO.AugmentRow row : networkAugmentDAO.getByNetworkId(data.getIdentifier())) {
                network.loadAugment(row.getType());
                if (row.getType() == gg.drak.restored.data.AugmentType.ENCHANTING) {
                    network.loadEnchantingBookshelves(row.getEnchantingBookshelves());
                }
            }

            for (NetworkLinkedChestDAO.LinkedChestRow row : networkLinkedChestDAO.getByNetworkId(data.getIdentifier())) {
                network.loadLinkedChest(row.getWorld(), row.getX(), row.getY(), row.getZ());
                Location location = LinkedChestStorage.parseLocationKey(
                        gg.drak.restored.data.NetworkManager.locationKey(row.getWorld(), row.getX(), row.getY(), row.getZ())
                );
                if (location != null && location.getWorld() != null) {
                    NetworkBlockTags.setLinkedNetworkId(location.getBlock(), network.getIdentifier());
                }
            }

            network.getDirty().set(false);
            networks.add(network);
        }
        return networks;
    }

    /**
     * Queue an async save. Prefer this from gameplay code.
     */
    public void saveNetwork(Network network) {
        middleware.queueNetworkSave(network);
    }

    /**
     * Queue an async delete. Prefer this from gameplay code.
     */
    public void deleteNetwork(Network network) {
        middleware.queueNetworkDelete(network);
    }

    /**
     * Blocking persist used by the async flush worker.
     */
    public void persistSnapshot(NetworkSnapshot snapshot) {
        networkDAO.save(new NetworkDAO.NetworkData(
                snapshot.getIdentifier(),
                snapshot.getOwnerUuid(),
                snapshot.getWorld(),
                snapshot.getX(),
                snapshot.getY(),
                snapshot.getZ(),
                snapshot.getUpgradeCount(),
                snapshot.getTotalOpens()
        ));

        networkItemDAO.deleteAll(snapshot.getIdentifier());
        for (NetworkSnapshot.ItemEntry entry : snapshot.getItems()) {
            networkItemDAO.save(
                    snapshot.getIdentifier(),
                    entry.getItemKey(),
                    entry.getItemData(),
                    entry.getAmount()
            );
        }

        networkPermissionDAO.deleteAll(snapshot.getIdentifier());
        for (NetworkSnapshot.PermissionEntry entry : snapshot.getPermissions()) {
            networkPermissionDAO.save(
                    snapshot.getIdentifier(),
                    entry.getPlayerUuid(),
                    entry.getRole()
            );
        }

        networkOpenStatsDAO.deleteAll(snapshot.getIdentifier());
        for (NetworkSnapshot.OpenStatEntry entry : snapshot.getOpenStats()) {
            networkOpenStatsDAO.save(
                    snapshot.getIdentifier(),
                    entry.getPlayerUuid(),
                    entry.getOpens()
            );
        }

        networkAugmentDAO.deleteAll(snapshot.getIdentifier());
        for (var type : snapshot.getAugments()) {
            networkAugmentDAO.save(snapshot.getIdentifier(), type,
                    type == gg.drak.restored.data.AugmentType.ENCHANTING
                            ? snapshot.getEnchantingBookshelves() : 0);
        }

        networkLinkedChestDAO.deleteAll(snapshot.getIdentifier());
        for (NetworkSnapshot.LinkedChestEntry entry : snapshot.getLinkedChests()) {
            networkLinkedChestDAO.save(
                    snapshot.getIdentifier(),
                    entry.getWorld(),
                    entry.getX(),
                    entry.getY(),
                    entry.getZ()
            );
        }
    }

    /**
     * Blocking delete used by the async flush worker.
     */
    public void deleteNetworkSync(String identifier) {
        networkDAO.delete(identifier);
    }
}
