package gg.drak.restored.utils;

import host.plas.bou.sql.ConnectorSet;
import host.plas.bou.sql.DBOperator;
import host.plas.bou.sql.DatabaseType;
import gg.drak.restored.Restored;
import lombok.Getter;
import lombok.Setter;

import java.sql.SQLException;

@Getter @Setter
public class SQLiteDS extends DBOperator {
    private NetworkDAO networkDAO;
    private NetworkBlockDAO networkBlockDAO;
    private DiskDAO diskDAO;
    private PermissionDAO permissionDAO;
    private FilterDAO filterDAO;
    
    public SQLiteDS() {
        super(new ConnectorSet(
                DatabaseType.SQLITE,
                "",
                0,
                "",
                "",
                "",
                "",
                "restored.db"
                ), Restored.getInstance());
        
        this.networkDAO = new NetworkDAO(this);
        this.networkBlockDAO = new NetworkBlockDAO(this);
        this.diskDAO = new DiskDAO(this);
        this.permissionDAO = new PermissionDAO(this);
        this.filterDAO = new FilterDAO(this);
    }

    @Override
    public void ensureTables() {
        try {
            networkDAO.createTable();
            networkBlockDAO.createTable();
            diskDAO.createTable();
            permissionDAO.createTable();
            filterDAO.createTable();
            
            Restored.getInstance().logInfo("Database tables ensured successfully!");
        } catch (SQLException e) {
            Restored.getInstance().logSevere("Failed to ensure database tables: " + e.getMessage(), e);
        }
    }

    @Override
    public void ensureDatabase() {
        // SQLite creates the database file automatically, so we just ensure tables
        ensureTables();
    }
}
