package gg.drak.restored.database;

import gg.drak.restored.Restored;
import host.plas.bou.sql.DBOperator;

/**
 * Main database operator for the Restored plugin.
 * Handles database connection and table creation.
 */
public class MainOperator extends DBOperator {
    
    public MainOperator() {
        super(Restored.getDatabaseConfig().getConnectorSet(), Restored.getInstance());
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
