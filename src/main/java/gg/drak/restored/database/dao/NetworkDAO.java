package gg.drak.restored.database.dao;

import gg.drak.restored.Restored;
import gg.drak.restored.database.MainOperator;
import gg.drak.restored.database.Statements;
import lombok.Getter;
import lombok.Setter;

import java.sql.SQLException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Data Access Object for Network operations.
 */
@Getter
public class NetworkDAO {
    private final MainOperator operator;
    
    public NetworkDAO(MainOperator operator) {
        this.operator = operator;
    }
    
    /**
     * Insert or update a network in the database.
     * @param identifier Network UUID
     * @param ownerUuid Owner player UUID
     */
    public void insert(String identifier, String ownerUuid) {
        try {
            operator.ensureUsable();
            
            String statement = Statements.getStatement(Statements.StatementType.INSERT_NETWORK, operator.getConnectorSet());

            operator.execute(statement, stmt -> {
                try {
                    stmt.setString(1, identifier);
                    stmt.setString(2, ownerUuid);
                } catch (Exception e) {
                    Restored.getInstance().logSevere("Failed to set values for INSERT_NETWORK statement", e);
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) {
            Restored.getInstance().logSevere("Failed to insert or update network with identifier: " + identifier, e);
        }
    }
    
    /**
     * Delete a network from the database.
     * @param identifier Network UUID
     */
    public void delete(String identifier) {
        try {
            operator.ensureUsable();
            
            // Remove from cache immediately
            operator.getMiddleware().removeNetworkFromCache(identifier);

            String statement = Statements.getStatement(Statements.StatementType.DELETE_NETWORK, operator.getConnectorSet());

            operator.execute(statement, stmt -> {
                try {
                    stmt.setString(1, identifier);
                } catch (Exception e) {
                    Restored.getInstance().logSevere("Failed to set values for DELETE_NETWORK statement", e);
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) {
            Restored.getInstance().logSevere("Failed to delete network with identifier: " + identifier, e);
        }
    }
    
    /**
     * Get a network by identifier.
     * @param identifier Network UUID
     * @return Optional containing NetworkData if found
     */
    public Optional<NetworkData> getById(String identifier) {
        try {
            operator.ensureUsable();

            String statement = Statements.getStatement(Statements.StatementType.GET_NETWORK, operator.getConnectorSet());

            AtomicReference<Optional<NetworkData>> ref = new AtomicReference<>(Optional.empty());

            operator.executeQuery(statement, stmt -> {
                try {
                    stmt.setString(1, identifier);
                } catch (Exception e) {
                    Restored.getInstance().logSevere("Failed to set values for GET_NETWORK statement", e);
                    throw new RuntimeException(e);
                }
            }, rs -> {
                try {
                    if (rs.next()) {
                        NetworkData data = new NetworkData(
                                rs.getString("Identifier"),
                                rs.getString("OwnerUuid")
                        );
                        
                        ref.set(Optional.of(data));
                    }
                } catch (Exception e) {
                    Restored.getInstance().logSevere("Failed to read values from GET_NETWORK result set", e);
                }
            });

            return ref.get();
        } catch (Exception e) {
            Restored.getInstance().logSevere("Failed to get network with identifier: " + identifier, e);
            return Optional.empty();
        }
    }

    /**
     * Get all networks from the database.
     * @return List of NetworkData
     */
    public java.util.List<NetworkData> getAll() {
        try {
            operator.ensureUsable();

            String statement = Statements.getStatement(Statements.StatementType.GET_ALL_NETWORKS, operator.getConnectorSet());

            java.util.List<NetworkData> networks = new java.util.ArrayList<>();

            operator.executeQuery(statement, stmt -> {}, rs -> {
                try {
                    while (rs.next()) {
                        NetworkData data = new NetworkData(
                                rs.getString("Identifier"),
                                rs.getString("OwnerUuid")
                        );
                        networks.add(data);
                    }
                } catch (Exception e) {
                    Restored.getInstance().logSevere("Failed to read values from GET_ALL_NETWORKS result set", e);
                }
            });

            return networks;
        } catch (Exception e) {
            Restored.getInstance().logSevere("Failed to get all networks", e);
            return new java.util.ArrayList<>();
        }
    }
    
    @Getter
    @Setter
    public static class NetworkData {
        private final String identifier;
        private final String ownerUuid;
        
        public NetworkData(String identifier, String ownerUuid) {
            this.identifier = identifier;
            this.ownerUuid = ownerUuid;
        }
    }
}
