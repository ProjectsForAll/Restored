package gg.drak.restored.database.dao;

import gg.drak.restored.Restored;
import gg.drak.restored.database.MainOperator;
import gg.drak.restored.database.Statements;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
public class NetworkLinkedHopperDAO {
    private final MainOperator operator;

    public NetworkLinkedHopperDAO(MainOperator operator) {
        this.operator = operator;
    }

    public void save(String networkId, String world, int x, int y, int z, String role) {
        try {
            operator.ensureUsable();
            String statement = Statements.getStatement(Statements.StatementType.INSERT_NETWORK_LINKED_HOPPER, operator.getConnectorSet());
            operator.execute(statement, stmt -> {
                try {
                    stmt.setString(1, networkId);
                    stmt.setString(2, world);
                    stmt.setInt(3, x);
                    stmt.setInt(4, y);
                    stmt.setInt(5, z);
                    stmt.setString(6, role);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) {
            Restored.getInstance().logSevere("Failed to save linked hopper for network " + networkId, e);
        }
    }

    public void deleteAll(String networkId) {
        try {
            operator.ensureUsable();
            String statement = Statements.getStatement(Statements.StatementType.DELETE_NETWORK_LINKED_HOPPERS, operator.getConnectorSet());
            operator.execute(statement, stmt -> {
                try {
                    stmt.setString(1, networkId);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) {
            Restored.getInstance().logSevere("Failed to delete linked hoppers for network " + networkId, e);
        }
    }

    public List<LinkedHopperRow> getByNetworkId(String networkId) {
        try {
            operator.ensureUsable();
            String statement = Statements.getStatement(Statements.StatementType.GET_NETWORK_LINKED_HOPPERS, operator.getConnectorSet());
            List<LinkedHopperRow> rows = new ArrayList<>();
            operator.executeQuery(statement, stmt -> {
                try {
                    stmt.setString(1, networkId);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, rs -> {
                try {
                    while (rs.next()) {
                        rows.add(new LinkedHopperRow(
                                rs.getString("World"),
                                rs.getInt("X"),
                                rs.getInt("Y"),
                                rs.getInt("Z"),
                                rs.getString("Role")
                        ));
                    }
                } catch (Exception e) {
                    Restored.getInstance().logSevere("Failed to read linked hoppers for network " + networkId, e);
                }
            });
            return rows;
        } catch (Exception e) {
            Restored.getInstance().logSevere("Failed to get linked hoppers for network " + networkId, e);
            return new ArrayList<>();
        }
    }

    @Getter
    public static class LinkedHopperRow {
        private final String world;
        private final int x;
        private final int y;
        private final int z;
        private final String role;

        public LinkedHopperRow(String world, int x, int y, int z, String role) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.role = role;
        }
    }
}
