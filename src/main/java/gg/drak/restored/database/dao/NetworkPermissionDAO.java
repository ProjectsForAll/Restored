package gg.drak.restored.database.dao;

import gg.drak.restored.Restored;
import gg.drak.restored.data.NetworkRole;
import gg.drak.restored.database.MainOperator;
import gg.drak.restored.database.Statements;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
public class NetworkPermissionDAO {
    private final MainOperator operator;

    public NetworkPermissionDAO(MainOperator operator) {
        this.operator = operator;
    }

    public void save(String networkId, String playerUuid, NetworkRole role) {
        try {
            operator.ensureUsable();
            String statement = Statements.getStatement(Statements.StatementType.INSERT_NETWORK_PERMISSION, operator.getConnectorSet());
            operator.execute(statement, stmt -> {
                try {
                    stmt.setString(1, networkId);
                    stmt.setString(2, playerUuid);
                    stmt.setString(3, role.name());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) {
            Restored.getInstance().logSevere("Failed to save permission for network " + networkId, e);
        }
    }

    public void deleteAll(String networkId) {
        try {
            operator.ensureUsable();
            String statement = Statements.getStatement(Statements.StatementType.DELETE_NETWORK_PERMISSIONS, operator.getConnectorSet());
            operator.execute(statement, stmt -> {
                try {
                    stmt.setString(1, networkId);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) {
            Restored.getInstance().logSevere("Failed to delete permissions for network " + networkId, e);
        }
    }

    public List<PermissionRow> getByNetworkId(String networkId) {
        try {
            operator.ensureUsable();
            String statement = Statements.getStatement(Statements.StatementType.GET_NETWORK_PERMISSIONS, operator.getConnectorSet());
            List<PermissionRow> rows = new ArrayList<>();
            operator.executeQuery(statement, stmt -> {
                try {
                    stmt.setString(1, networkId);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, rs -> {
                try {
                    while (rs.next()) {
                        rows.add(new PermissionRow(
                                rs.getString("PlayerUuid"),
                                NetworkRole.valueOf(rs.getString("Role"))
                        ));
                    }
                } catch (Exception e) {
                    Restored.getInstance().logSevere("Failed to read permissions for network " + networkId, e);
                }
            });
            return rows;
        } catch (Exception e) {
            Restored.getInstance().logSevere("Failed to get permissions for network " + networkId, e);
            return new ArrayList<>();
        }
    }

    @Getter
    public static class PermissionRow {
        private final String playerUuid;
        private final NetworkRole role;

        public PermissionRow(String playerUuid, NetworkRole role) {
            this.playerUuid = playerUuid;
            this.role = role;
        }
    }
}
