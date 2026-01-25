package gg.drak.restored.database;

import gg.drak.restored.Restored;
import gg.drak.restored.database.dao.DiskDAO;
import gg.drak.restored.database.dao.FilterDAO;
import gg.drak.restored.database.dao.NetworkBlockDAO;
import gg.drak.restored.database.dao.NetworkDAO;
import gg.drak.restored.database.dao.PermissionDAO;
import lombok.Getter;

/**
 * Singleton database manager that provides access to all DAOs.
 * Initializes the database connection and ensures tables are created.
 */
public class DatabaseManager {
    
    private static DatabaseManager instance;
    
    @Getter
    private final MainOperator operator;
    
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
    
    private DatabaseManager() {
        this.operator = new MainOperator();
        this.operator.ensureDatabase();
        
        this.networkDAO = new NetworkDAO(operator);
        this.networkBlockDAO = new NetworkBlockDAO(operator);
        this.diskDAO = new DiskDAO(operator);
        this.permissionDAO = new PermissionDAO(operator);
        this.filterDAO = new FilterDAO(operator);
    }
    
    /**
     * Initialize the database manager singleton.
     * Should be called once during plugin startup.
     */
    public static void initialize() {
        if (instance == null) {
            instance = new DatabaseManager();
            Restored.getInstance().logInfo("DatabaseManager initialized successfully!");
        }
    }
    
    /**
     * Get the database manager instance.
     * @return The singleton instance
     * @throws IllegalStateException if not initialized
     */
    public static DatabaseManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("DatabaseManager not initialized! Call DatabaseManager.initialize() first.");
        }
        return instance;
    }
}
