package gg.drak.restored.utils;

import gg.drak.restored.database.MainOperator;
import gg.drak.restored.database.dao.DiskDAO;
import gg.drak.restored.database.dao.FilterDAO;
import gg.drak.restored.database.dao.NetworkBlockDAO;
import gg.drak.restored.database.dao.NetworkDAO;
import gg.drak.restored.database.dao.PermissionDAO;
import host.plas.bou.sql.ConnectorSet;
import host.plas.bou.sql.DBOperator;
import host.plas.bou.sql.DatabaseType;
import gg.drak.restored.Restored;
import lombok.Getter;
import lombok.Setter;

/**
 * Legacy SQLite database operator.
 * This class is deprecated - use DatabaseManager instead.
 * @deprecated Use DatabaseManager instead
 */
@Deprecated
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
        
        // Create a MainOperator for the DAOs to use
        MainOperator operator = new MainOperator();
        this.networkDAO = new NetworkDAO(operator);
        this.networkBlockDAO = new NetworkBlockDAO(operator);
        this.diskDAO = new DiskDAO(operator);
        this.permissionDAO = new PermissionDAO(operator);
        this.filterDAO = new FilterDAO(operator);
    }

    @Override
    public void ensureTables() {
        // Tables are now ensured by MainOperator.ensureDatabase()
        // This method is kept for compatibility but does nothing
        Restored.getInstance().logWarning("SQLiteDS.ensureTables() is deprecated. Use DatabaseManager instead.");
    }

    @Override
    public void ensureDatabase() {
        // Database is now ensured by MainOperator.ensureDatabase()
        // This method is kept for compatibility but does nothing
        Restored.getInstance().logWarning("SQLiteDS.ensureDatabase() is deprecated. Use DatabaseManager instead.");
    }
}
