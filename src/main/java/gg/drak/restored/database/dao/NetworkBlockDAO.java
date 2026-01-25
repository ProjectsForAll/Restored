package gg.drak.restored.database.dao;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import gg.drak.restored.Restored;
import gg.drak.restored.data.blocks.BlockLocation;
import gg.drak.restored.database.MainOperator;
import gg.drak.restored.database.Statements;
import gg.drak.restored.data.blocks.BlockType;
import host.plas.bou.sql.DatabaseType;
import host.plas.bou.sql.DbArg;
import lombok.Getter;
import lombok.Setter;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Data Access Object for NetworkBlock operations.
 */
@Getter
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
     */
    public void insert(String identifier, String networkId, BlockType blockType, String data) {
        try {
            operator.ensureUsable();
            
            String statement = Statements.getStatement(Statements.StatementType.INSERT_NETWORK_BLOCK, operator.getConnectorSet());

            operator.getMiddleware().queueOperation(statement, stmt -> {
                try {
                    DbArg arg = new DbArg();

                    stmt.setString(arg.next(), identifier);
                    stmt.setString(arg.next(), networkId);
                    stmt.setString(arg.next(), blockType.name());
                    stmt.setString(arg.next(), data);

                    if (operator.getType() == DatabaseType.MYSQL) {
                        // In MySQL, ON DUPLICATE KEY UPDATE uses VALUES(column) which doesn't need extra parameters
                    }
                } catch (Exception e) {
                    Restored.getInstance().logSevere("Failed to set values for INSERT_NETWORK_BLOCK statement", e);
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) {
            Restored.getInstance().logSevere("Failed to queue insert or update network block with identifier: " + identifier, e);
        }
    }
    
    /**
     * Delete a network block from the database.
     * @param identifier Block identifier
     */
    public void delete(String identifier) {
        try {
            operator.ensureUsable();
            
            // Remove from cache immediately
            operator.getMiddleware().removeBlockFromCache(identifier);

            String statement = Statements.getStatement(Statements.StatementType.DELETE_NETWORK_BLOCK, operator.getConnectorSet());

            operator.getMiddleware().queueOperation(statement, stmt -> {
                try {
                    stmt.setString(1, identifier);
                } catch (Exception e) {
                    Restored.getInstance().logSevere("Failed to set values for DELETE_NETWORK_BLOCK statement", e);
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) {
            Restored.getInstance().logSevere("Failed to queue delete network block with identifier: " + identifier, e);
        }
    }
    
    /**
     * Delete all network blocks for a network.
     * @param networkId Network UUID
     */
    public void deleteByNetworkId(String networkId) {
        try {
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
        } catch (Exception e) {
            Restored.getInstance().logSevere("Failed to delete network blocks for network ID: " + networkId, e);
        }
    }
    
    /**
     * Get a network block by identifier.
     * @param identifier Block identifier
     * @return Optional containing NetworkBlockData if found
     */
    public Optional<NetworkBlockData> getById(String identifier) {
        try {
            operator.ensureUsable();

            String statement = Statements.getStatement(Statements.StatementType.GET_NETWORK_BLOCK, operator.getConnectorSet());

            AtomicReference<Optional<NetworkBlockData>> ref = new AtomicReference<>(Optional.empty());

            operator.executeQuery(statement, stmt -> {
                try {
                    stmt.setString(1, identifier);
                } catch (Exception e) {
                    Restored.getInstance().logSevere("Failed to set values for GET_NETWORK_BLOCK statement", e);
                    throw new RuntimeException(e);
                }
            }, rs -> {
                try {
                    if (rs.next()) {
                        String blockTypeStr = rs.getString("BlockType");
                        String networkId = rs.getString("NetworkId");
                        String data = rs.getString("Data");

                        BlockType blockType = BlockType.NONE;
                        try {
                            blockType = BlockType.valueOf(blockTypeStr);
                        } catch (IllegalArgumentException e) {
                            Restored.getInstance().logWarning("Unknown block type: " + blockTypeStr);
                        }

                        NetworkBlockData blockData = new NetworkBlockData(identifier, networkId, blockType, data);
                        
                        ref.set(Optional.of(blockData));
                    }
                } catch (Exception e) {
                    Restored.getInstance().logSevere("Failed to read values from GET_NETWORK_BLOCK result set", e);
                }
            });

            return ref.get();
        } catch (Exception e) {
            Restored.getInstance().logSevere("Failed to get network block with identifier: " + identifier, e);
            return Optional.empty();
        }
    }
    
    /**
     * Get all network blocks for a network.
     * @param networkId Network UUID
     * @return List of NetworkBlockData
     */
    public List<NetworkBlockData> getByNetworkId(String networkId) {
        try {
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
                        String data = rs.getString("Data");

                        BlockType blockType = BlockType.NONE;
                        try {
                            blockType = BlockType.valueOf(blockTypeStr);
                        } catch (IllegalArgumentException e) {
                            Restored.getInstance().logWarning("Unknown block type: " + blockTypeStr);
                        }

                        blocks.add(new NetworkBlockData(identifier, networkId, blockType, data));
                    }
                } catch (Exception e) {
                    Restored.getInstance().logSevere("Failed to read values from GET_NETWORK_BLOCKS_BY_NETWORK result set", e);
                }
            });

            return blocks;
        } catch (Exception e) {
            Restored.getInstance().logSevere("Failed to get network blocks for network ID: " + networkId, e);
            return new ArrayList<>();
        }
    }
    
    @Getter
    @Setter
    public static class NetworkBlockData {
        private final String identifier;
        private final String networkId;
        private final BlockType blockType;
        private  final String data;
        
        public NetworkBlockData(String identifier, String networkId, BlockType blockType, String data) {
            this.identifier = identifier;
            this.networkId = networkId;
            this.blockType = blockType;
            this.data = data;
        }

        public BlockLocation asBlockLocation() {
            JsonObject jsonObject = new Gson().fromJson(data, JsonObject.class);
            JsonObject locationJson = jsonObject.getAsJsonObject("location");
            return BlockLocation.fromJson(locationJson);
        }
    }
}
