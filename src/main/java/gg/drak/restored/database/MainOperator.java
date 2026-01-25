package gg.drak.restored.database;

import gg.drak.restored.Restored;
import gg.drak.restored.database.dao.*;
import host.plas.bou.sql.DBOperator;
import lombok.Getter;

/**
 * Main database operator for the Restored plugin.
 * Handles database connection and table creation.
 * Provides access to all DAOs.
 */
@Getter
public class MainOperator extends DBOperator {
    private final DataBlockDAO dataBlockDAO;
    private final NetworkDAO networkDAO;
    private final NetworkBlockDAO networkBlockDAO;
    private final DiskDAO diskDAO;
    private final PermissionDAO permissionDAO;
    private final FilterDAO filterDAO;
    
    public MainOperator() {
        super(Restored.getDatabaseConfig().getConnectorSet(), Restored.getInstance());
        
        // Initialize DAOs
        this.dataBlockDAO = new DataBlockDAO(this);
        this.networkDAO = new NetworkDAO(this);
        this.networkBlockDAO = new NetworkBlockDAO(this);
        this.diskDAO = new DiskDAO(this);
        this.permissionDAO = new PermissionDAO(this);
        this.filterDAO = new FilterDAO(this);
    }

    @Override
    public void ensureTables() {
        try {
            String s1 = Statements.getStatement(Statements.StatementType.CREATE_TABLES, getConnectorSet());

            execute(s1, stmt -> {});
        } catch (Exception e) {
            Restored.getInstance().logSevere("Failed to ensure database tables", e);
        }
    }

    @Override
    public void ensureDatabase() {
        try {
            String s1 = Statements.getStatement(Statements.StatementType.CREATE_DATABASE, getConnectorSet());

            execute(s1, stmt -> {});
        } catch (Exception e) {
            Restored.getInstance().logSevere("Failed to ensure database", e);
        }
    }
}
