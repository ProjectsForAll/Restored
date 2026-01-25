package gg.drak.restored.database.dao;

import gg.drak.restored.Restored;
import gg.drak.restored.database.MainOperator;
import gg.drak.restored.database.Statements;
import gg.drak.restored.data.blocks.BlockType;
import lombok.Getter;
import lombok.Setter;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for NetworkBlock operations.
 */
public class NetworkBlockDAO {
    
    private final MainOperator operator;
    
    public NetworkBlockDAO(MainOperator operator) {
        this.operator = operator;
    }
    
    /**
     * Insert or update a network block in the database.
     * @param identifier Block identifier (location string)
     * @param networkId Network UUID
     * @param blockType Block type
     * @throws SQLException if database operation fails
     */
    public void insert(String identifier, String networkId, BlockType blockType) throws SQLException {
        operator.ensureUsable();
        
        String statement = Statements.getStatement(Statements.StatementType.INSERT_NETWORK_BLOCK, operator.getConnectorSet());
        
        operator.execute(statement, stmt -> {
            try {
                stmt.setString(1, identifier);
                stmt.setString(2, networkId);
                stmt.setString(3, blockType.name());
            } catch (Exception e) {
                Restored.getInstance().logSevere("Failed to set values for INSERT_NETWORK_BLOCK statement", e);
                throw new RuntimeException(e);
            }
        });
    }
    
    /**
     * Delete a network block from the database.
     * @param identifier Block identifier
     * @throws SQLException if database operation fails
     */
    public void delete(String identifier) throws SQLException {
        operator.ensureUsable();
        
        String statement = Statements.getStatement(Statements.StatementType.DELETE_NETWORK_BLOCK, operator.getConnectorSet());
        
        operator.execute(statement, stmt -> {
            try {
                stmt.setString(1, identifier);
            } catch (Exception e) {
                Restored.getInstance().logSevere("Failed to set values for DELETE_NETWORK_BLOCK statement", e);
                throw new RuntimeException(e);
            }
        });
    }
    
    /**
     * Delete all network blocks for a network.
     * @param networkId Network UUID
     * @throws SQLException if database operation fails
     */
    public void deleteByNetworkId(String networkId) throws SQLException {
        operator.ensureUsable();
        
        String statement = Statements.getStatement(Statements.StatementType.DELETE_NETWORK_BLOCKS_BY_NETWORK, operator.getConnectorSet());
        
        operator.execute(statement, stmt -> {
            try {
                stmt.setString(1, networkId);
            } catch (Exception e) {
                Restored.getInstance().logSevere("Failed to set values for DELETE_NETWORK_BLOCKS_BY_NETWORK statement", e);
                throw new RuntimeException(e);
            }
        });
    }
    
    /**
     * Get all network blocks for a network.
     * @param networkId Network UUID
     * @return List of NetworkBlockData
     * @throws SQLException if database operation fails
     */
    public List<NetworkBlockData> getByNetworkId(String networkId) throws SQLException {
        operator.ensureUsable();
        
        String statement = Statements.getStatement(Statements.StatementType.GET_NETWORK_BLOCKS_BY_NETWORK, operator.getConnectorSet());
        
        List<NetworkBlockData> blocks = new ArrayList<>();
        
        operator.executeQuery(statement, stmt -> {
            try {
                stmt.setString(1, networkId);
            } catch (Exception e) {
                Restored.getInstance().logSevere("Failed to set values for GET_NETWORK_BLOCKS_BY_NETWORK statement", e);
                throw new RuntimeException(e);
            }
        }, rs -> {
            try {
                while (rs.next()) {
                    String identifier = rs.getString("Identifier");
                    String blockTypeStr = rs.getString("BlockType");
                    
                    BlockType blockType = BlockType.NONE;
                    try {
                        blockType = BlockType.valueOf(blockTypeStr);
                    } catch (IllegalArgumentException e) {
                        Restored.getInstance().logWarning("Unknown block type: " + blockTypeStr);
                    }
                    
                    blocks.add(new NetworkBlockData(identifier, networkId, blockType));
                }
            } catch (Exception e) {
                Restored.getInstance().logSevere("Failed to read values from GET_NETWORK_BLOCKS_BY_NETWORK result set", e);
            }
        });
        
        return blocks;
    }
    
    @Getter
    @Setter
    public static class NetworkBlockData {
        private final String identifier;
        private final String networkId;
        private final BlockType blockType;
        
        public NetworkBlockData(String identifier, String networkId, BlockType blockType) {
            this.identifier = identifier;
            this.networkId = networkId;
            this.blockType = blockType;
        }
    }
}
