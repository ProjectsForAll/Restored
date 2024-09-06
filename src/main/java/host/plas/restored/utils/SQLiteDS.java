package host.plas.restored.utils;

import host.plas.restored.Restored;
import lombok.Getter;
import lombok.Setter;
import tv.quaint.thebase.lib.hikari.HikariConfig;
import tv.quaint.thebase.lib.hikari.HikariDataSource;

import java.io.File;
import java.sql.Connection;

@Getter @Setter
public class SQLiteDS {
    private HikariDataSource hikariDataSource;
    private Connection rawConnection;
    private String databaseName;

    public SQLiteDS(String databaseName) {
        databaseName = databaseName.toLowerCase();

        hikariDataSource = null;

        getStorageDirectory(); // Ensure the storage directory exists

        rawConnection = buildConnection();
    }

    public File getStorageDirectory() {
        File storageDirectory = new File(Restored.getInstance().getDataFolder(), "database");

        if (! storageDirectory.exists()) {
            storageDirectory.mkdirs();
        }

        return storageDirectory;
    }

    public Connection buildConnection() {
        try {
            if (hikariDataSource != null && ! hikariDataSource.isClosed()) {
                return hikariDataSource.getConnection();
            }

            Class.forName("org.sqlite.JDBC");

            HikariConfig hikariConfig = new HikariConfig();
            // Use SQLite
            hikariConfig.setJdbcUrl("jdbc:sqlite:" + getStorageDirectory().getAbsolutePath() + "/" + databaseName + ".db");
            hikariConfig.setDriverClassName("org.sqlite.JDBC");
            hikariConfig.setConnectionTestQuery("SELECT 1");
            hikariConfig.setConnectionTimeout(5000);
            hikariConfig.setMaximumPoolSize(10);
            hikariConfig.setPoolName("RestoredBlockStorage");
            hikariConfig.setLeakDetectionThreshold(30000);
            hikariConfig.setInitializationFailTimeout(1);
            hikariConfig.setReadOnly(false);
            hikariConfig.setRegisterMbeans(true);
            hikariConfig.setConnectionInitSql("PRAGMA foreign_keys = ON");
            // Use multiquery
            hikariConfig.addDataSourceProperty("multiQueries", true);

            hikariDataSource = new HikariDataSource(hikariConfig);

            return hikariDataSource.getConnection();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public void closeConnection() {
        try {
            if (hikariDataSource != null && ! hikariDataSource.isClosed()) {
                hikariDataSource.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Connection getConnection() {
        try {
            if (rawConnection != null && ! rawConnection.isClosed()) {
                return rawConnection;
            }

            rawConnection = buildConnection();

            return rawConnection;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
