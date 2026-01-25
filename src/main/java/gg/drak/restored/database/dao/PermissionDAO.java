package gg.drak.restored.database.dao;

import gg.drak.restored.Restored;
import gg.drak.restored.database.MainOperator;
import gg.drak.restored.database.Statements;
import gg.drak.restored.data.permission.PermissionNode;
import host.plas.bou.sql.DatabaseType;
import lombok.Getter;
import lombok.Setter;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Data Access Object for Permission operations.
 */
public class PermissionDAO {
    
    private final MainOperator operator;
    
    public PermissionDAO(MainOperator operator) {
        this.operator = operator;
    }
    
    /**
     * Set a permission for a player on a network.
     * @param networkId Network UUID
     * @param playerUuid Player UUID
     * @param permissionNode Permission node
     * @param value Permission value
     * @throws SQLException if database operation fails
     */
    public void setPermission(String networkId, String playerUuid, PermissionNode permissionNode, boolean value) throws SQLException {
        operator.ensureUsable();
        
        String statement = Statements.getStatement(Statements.StatementType.INSERT_PERMISSION, operator.getConnectorSet());
        
        operator.execute(statement, stmt -> {
            try {
                stmt.setString(1, networkId);
                stmt.setString(2, playerUuid);
                stmt.setString(3, permissionNode.getNode());
                // Handle both MySQL BOOLEAN and SQLite INTEGER
                if (operator.getConnectorSet().getType() == DatabaseType.SQLITE) {
                    stmt.setInt(4, value ? 1 : 0);
                } else {
                    stmt.setBoolean(4, value);
                }
            } catch (Exception e) {
                Restored.getInstance().logSevere("Failed to set values for INSERT_PERMISSION statement", e);
                throw new RuntimeException(e);
            }
        });
    }
    
    /**
     * Remove all permissions for a network.
     * @param networkId Network UUID
     * @throws SQLException if database operation fails
     */
    public void removeAllPermissions(String networkId) throws SQLException {
        operator.ensureUsable();
        
        String statement = Statements.getStatement(Statements.StatementType.DELETE_PERMISSIONS_BY_NETWORK, operator.getConnectorSet());
        
        operator.execute(statement, stmt -> {
            try {
                stmt.setString(1, networkId);
            } catch (Exception e) {
                Restored.getInstance().logSevere("Failed to set values for DELETE_PERMISSIONS_BY_NETWORK statement", e);
                throw new RuntimeException(e);
            }
        });
    }
    
    /**
     * Get all permissions for a network.
     * @param networkId Network UUID
     * @return List of PermissionData
     * @throws SQLException if database operation fails
     */
    public List<PermissionData> getByNetworkId(String networkId) throws SQLException {
        operator.ensureUsable();
        
        String statement = Statements.getStatement(Statements.StatementType.GET_PERMISSIONS_BY_NETWORK, operator.getConnectorSet());
        
        List<PermissionData> permissions = new ArrayList<>();
        
        operator.executeQuery(statement, stmt -> {
            try {
                stmt.setString(1, networkId);
            } catch (Exception e) {
                Restored.getInstance().logSevere("Failed to set values for GET_PERMISSIONS_BY_NETWORK statement", e);
                throw new RuntimeException(e);
            }
        }, rs -> {
            try {
                while (rs.next()) {
                    String playerUuid = rs.getString("PlayerUuid");
                    String permissionNodeStr = rs.getString("PermissionNode");
                    // Handle both MySQL BOOLEAN and SQLite INTEGER
                    boolean value;
                    try {
                        value = rs.getBoolean("Value");
                    } catch (Exception e) {
                        // SQLite uses INTEGER, so try getInt
                        value = rs.getInt("Value") != 0;
                    }
                    
                    PermissionNode permissionNode = PermissionNode.ERROR;
                    for (PermissionNode node : PermissionNode.values()) {
                        if (node.getNode().equals(permissionNodeStr)) {
                            permissionNode = node;
                            break;
                        }
                    }
                    
                    permissions.add(new PermissionData(networkId, playerUuid, permissionNode, value));
                }
            } catch (Exception e) {
                Restored.getInstance().logSevere("Failed to read values from GET_PERMISSIONS_BY_NETWORK result set", e);
            }
        });
        
        return permissions;
    }
    
    @Getter
    @Setter
    public static class PermissionData {
        private final String networkId;
        private final String playerUuid;
        private final PermissionNode permissionNode;
        private final boolean value;
        
        public PermissionData(String networkId, String playerUuid, PermissionNode permissionNode, boolean value) {
            this.networkId = networkId;
            this.playerUuid = playerUuid;
            this.permissionNode = permissionNode;
            this.value = value;
        }
    }
}
