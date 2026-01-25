package gg.drak.restored.database;

import gg.drak.restored.Restored;
import gg.drak.restored.database.dao.DiskDAO;
import gg.drak.restored.database.dao.FilterDAO;
import gg.drak.restored.database.dao.NetworkBlockDAO;
import gg.drak.restored.database.dao.NetworkDAO;
import gg.drak.restored.database.dao.PermissionDAO;
import host.plas.bou.sql.DBOperator;
import lombok.Getter;

/**
 * Main database operator for the Restored plugin.
 * Handles database connection and table creation.
 * Provides access to all DAOs.
 */
public class MainOperator extends DBOperator {
    
    @Getter
    private final NetworkDAO networkDAO;
    
    @Getter
    private final NetworkBlockDAO networkBlockDAO;
    
    @Getter
    private final DiskDAO diskDAO;
    
    @Getter
    private final PermissionDAO permissionDAO;
    
    @Getter
    private final FilterDAO filterDAO;
    
    public MainOperator() {
        super(Restored.getDatabaseConfig().getConnectorSet(), Restored.getInstance());
        
        // Initialize DAOs
        this.networkDAO = new NetworkDAO(this);
        this.networkBlockDAO = new NetworkBlockDAO(this);
        this.diskDAO = new DiskDAO(this);
        this.permissionDAO = new PermissionDAO(this);
        this.filterDAO = new FilterDAO(this);
    }

    @Override
    public void ensureTables() {
        try {
            String statement = Statements.getStatement(Statements.StatementType.CREATE_TABLES, getConnectorSet());
            execute(statement, stmt -> {});
            Restored.getInstance().logInfo("Database tables ensured successfully!");
        } catch (Exception e) {
            Restored.getInstance().logSevere("Failed to ensure database tables", e);
        }
    }

    @Override
    public void ensureDatabase() {
        try {
            String statement = Statements.getStatement(Statements.StatementType.CREATE_DATABASE, getConnectorSet());
            if (statement != null && !statement.isEmpty()) {
                execute(statement, stmt -> {});
            }
            ensureTables();
        } catch (Exception e) {
            Restored.getInstance().logSevere("Failed to ensure database", e);
        }
    }
}
