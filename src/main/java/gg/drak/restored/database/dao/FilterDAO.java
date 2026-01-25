package gg.drak.restored.database.dao;

import gg.drak.restored.Restored;
import gg.drak.restored.database.MainOperator;
import gg.drak.restored.database.Statements;
import lombok.Getter;

import java.sql.SQLException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Data Access Object for Filter operations.
 */
@Getter
public class FilterDAO {
    private final MainOperator operator;
    
    public FilterDAO(MainOperator operator) {
        this.operator = operator;
    }
    
    /**
     * Set a filter for a player.
     * @param playerUuid Player UUID
     * @param filter Filter string
     * @throws SQLException if database operation fails
     */
    public void setFilter(String playerUuid, String filter) throws SQLException {
        operator.ensureUsable();
        
        String statement = Statements.getStatement(Statements.StatementType.SET_FILTER, operator.getConnectorSet());
        
        operator.execute(statement, stmt -> {
            try {
                stmt.setString(1, playerUuid);
                stmt.setString(2, filter);
            } catch (Exception e) {
                Restored.getInstance().logSevere("Failed to set values for SET_FILTER statement", e);
                throw new RuntimeException(e);
            }
        });
    }
    
    /**
     * Get the last filter for a player.
     * @param playerUuid Player UUID
     * @return Optional containing filter string if found
     * @throws SQLException if database operation fails
     */
    public Optional<String> getLastFilter(String playerUuid) throws SQLException {
        operator.ensureUsable();
        
        String statement = Statements.getStatement(Statements.StatementType.GET_FILTER, operator.getConnectorSet());
        
        AtomicReference<Optional<String>> ref = new AtomicReference<>(Optional.empty());
        
        operator.executeQuery(statement, stmt -> {
            try {
                stmt.setString(1, playerUuid);
            } catch (Exception e) {
                Restored.getInstance().logSevere("Failed to set values for GET_FILTER statement", e);
                throw new RuntimeException(e);
            }
        }, rs -> {
            try {
                if (rs.next()) {
                    String filter = rs.getString("Filter");
                    if (filter != null && !filter.isEmpty()) {
                        ref.set(Optional.of(filter));
                    }
                }
            } catch (Exception e) {
                Restored.getInstance().logSevere("Failed to read values from GET_FILTER result set", e);
            }
        });
        
        return ref.get();
    }
    
    /**
     * Clear the filter for a player.
     * @param playerUuid Player UUID
     * @throws SQLException if database operation fails
     */
    public void clearFilter(String playerUuid) throws SQLException {
        operator.ensureUsable();
        
        String statement = Statements.getStatement(Statements.StatementType.CLEAR_FILTER, operator.getConnectorSet());
        
        operator.execute(statement, stmt -> {
            try {
                stmt.setString(1, playerUuid);
            } catch (Exception e) {
                Restored.getInstance().logSevere("Failed to set values for CLEAR_FILTER statement", e);
                throw new RuntimeException(e);
            }
        });
    }
}
