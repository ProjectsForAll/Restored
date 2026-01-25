package gg.drak.restored.database.dao;

import gg.drak.restored.Restored;
import gg.drak.restored.database.MainOperator;
import gg.drak.restored.data.blocks.BlockLocation;
import gg.drak.restored.data.blocks.BlockType;
import lombok.Getter;
import lombok.Setter;

import java.sql.SQLException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Data Access Object for DataBlock operations.
 * Wraps NetworkBlockDAO to provide DataBlock-specific functionality.
 */
@Getter
public class DataBlockDAO {
    private final MainOperator operator;
    private final NetworkBlockDAO networkBlockDAO;
    
    public DataBlockDAO(MainOperator operator) {
        this.operator = operator;
        this.networkBlockDAO = operator.getNetworkBlockDAO();
    }
    
    /**
     * Insert or update a data block in the database.
     * @param identifier Block identifier (location string)
     * @param networkId Network UUID (can be null)
     * @param blockType Block type (can be null)
     * @throws SQLException if database operation fails
     */
    public void insert(String identifier, String networkId, BlockType blockType) throws SQLException {
        operator.ensureUsable();

        if (networkId == null || blockType == null) {
            // If network or type is null, delete the block instead
            delete(identifier);
            return;
        }
        
        networkBlockDAO.insert(identifier, networkId, blockType);
    }
    
    /**
     * Delete a data block from the database.
     * @param identifier Block identifier
     * @throws SQLException if database operation fails
     */
    public void delete(String identifier) throws SQLException {
        operator.ensureUsable();

        networkBlockDAO.delete(identifier);
    }
    
    /**
     * Delete a data block by BlockLocation.
     * @param blockLocation Block location
     * @throws SQLException if database operation fails
     */
    public void delete(BlockLocation blockLocation) throws SQLException {
        operator.ensureUsable();

        delete(blockLocation.asString());
    }
    
    /**
     * Get a data block by identifier.
     * @param identifier Block identifier (location string)
     * @return Optional containing DataBlockData if found
     * @throws SQLException if database operation fails
     */
    public Optional<DataBlockData> getById(String identifier) throws SQLException {
        operator.ensureUsable();

        Optional<NetworkBlockDAO.NetworkBlockData> blockData = networkBlockDAO.getById(identifier);
        
        if (blockData.isEmpty()) {
            return Optional.empty();
        }
        
        NetworkBlockDAO.NetworkBlockData data = blockData.get();
        return Optional.of(new DataBlockData(data.getIdentifier(), data.getNetworkId(), data.getBlockType()));
    }
    
    /**
     * Get a data block by BlockLocation.
     * @param blockLocation Block location
     * @return Optional containing DataBlockData if found
     * @throws SQLException if database operation fails
     */
    public Optional<DataBlockData> getByLocation(BlockLocation blockLocation) throws SQLException {
        operator.ensureUsable();

        return getById(blockLocation.asString());
    }
    
    /**
     * Check if a data block exists.
     * @param identifier Block identifier
     * @return true if block exists
     * @throws SQLException if database operation fails
     */
    public boolean exists(String identifier) throws SQLException {
        operator.ensureUsable();

        return getById(identifier).isPresent();
    }
    
    @Getter
    @Setter
    public static class DataBlockData {
        private final String identifier;
        private final BlockLocation blockLocation;
        private final String networkId;
        private final BlockType blockType;
        
        public DataBlockData(String identifier, String networkId, BlockType blockType) {
            this.identifier = identifier;
            this.blockLocation = BlockLocation.of(identifier);
            this.networkId = networkId;
            this.blockType = blockType;
        }
    }
}
