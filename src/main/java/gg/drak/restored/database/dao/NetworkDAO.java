package gg.drak.restored.database.dao;

import gg.drak.restored.Restored;
import gg.drak.restored.database.MainOperator;
import gg.drak.restored.database.Statements;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Getter
public class NetworkDAO {
    private final MainOperator operator;

    public NetworkDAO(MainOperator operator) {
        this.operator = operator;
    }

    public void save(NetworkData data) {
        try {
            operator.ensureUsable();
            String statement = Statements.getStatement(Statements.StatementType.INSERT_NETWORK, operator.getConnectorSet());
            operator.execute(statement, stmt -> {
                try {
                    stmt.setString(1, data.identifier);
                    stmt.setString(2, data.ownerUuid);
                    stmt.setString(3, data.world);
                    if (data.world == null) {
                        stmt.setNull(4, java.sql.Types.INTEGER);
                        stmt.setNull(5, java.sql.Types.INTEGER);
                        stmt.setNull(6, java.sql.Types.INTEGER);
                    } else {
                        stmt.setInt(4, data.x);
                        stmt.setInt(5, data.y);
                        stmt.setInt(6, data.z);
                    }
                    stmt.setInt(7, data.upgradeCount);
                    stmt.setLong(8, data.totalOpens);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) {
            Restored.getInstance().logSevere("Failed to save network " + data.identifier, e);
        }
    }

    public void delete(String identifier) {
        try {
            operator.ensureUsable();
            String statement = Statements.getStatement(Statements.StatementType.DELETE_NETWORK, operator.getConnectorSet());
            operator.execute(statement, stmt -> {
                try {
                    stmt.setString(1, identifier);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) {
            Restored.getInstance().logSevere("Failed to delete network " + identifier, e);
        }
    }

    public Optional<NetworkData> getById(String identifier) {
        try {
            operator.ensureUsable();
            String statement = Statements.getStatement(Statements.StatementType.GET_NETWORK, operator.getConnectorSet());
            AtomicReference<Optional<NetworkData>> ref = new AtomicReference<>(Optional.empty());
            operator.executeQuery(statement, stmt -> {
                try {
                    stmt.setString(1, identifier);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, rs -> {
                try {
                    if (rs.next()) {
                        ref.set(Optional.of(readRow(rs)));
                    }
                } catch (Exception e) {
                    Restored.getInstance().logSevere("Failed to read network " + identifier, e);
                }
            });
            return ref.get();
        } catch (Exception e) {
            Restored.getInstance().logSevere("Failed to get network " + identifier, e);
            return Optional.empty();
        }
    }

    public List<NetworkData> getAll() {
        try {
            operator.ensureUsable();
            String statement = Statements.getStatement(Statements.StatementType.GET_ALL_NETWORKS, operator.getConnectorSet());
            List<NetworkData> networks = new ArrayList<>();
            operator.executeQuery(statement, stmt -> {}, rs -> {
                try {
                    while (rs.next()) {
                        networks.add(readRow(rs));
                    }
                } catch (Exception e) {
                    Restored.getInstance().logSevere("Failed to read all networks", e);
                }
            });
            return networks;
        } catch (Exception e) {
            Restored.getInstance().logSevere("Failed to get all networks", e);
            return new ArrayList<>();
        }
    }

    private NetworkData readRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        String world = rs.getString("World");
        int x = rs.getInt("X");
        int y = rs.getInt("Y");
        int z = rs.getInt("Z");
        if (rs.wasNull() || world == null || world.isEmpty()) {
            world = null;
        }
        return new NetworkData(
                rs.getString("Identifier"),
                rs.getString("OwnerUuid"),
                world,
                x, y, z,
                rs.getInt("UpgradeCount"),
                rs.getLong("TotalOpens")
        );
    }

    @Getter
    public static class NetworkData {
        private final String identifier;
        private final String ownerUuid;
        private final String world;
        private final int x;
        private final int y;
        private final int z;
        private final int upgradeCount;
        private final long totalOpens;

        public NetworkData(String identifier, String ownerUuid, String world, int x, int y, int z, int upgradeCount, long totalOpens) {
            this.identifier = identifier;
            this.ownerUuid = ownerUuid;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.upgradeCount = upgradeCount;
            this.totalOpens = totalOpens;
        }
    }
}
