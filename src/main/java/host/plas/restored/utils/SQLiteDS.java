package host.plas.restored.utils;

import host.plas.bou.sql.ConnectorSet;
import host.plas.bou.sql.DBOperator;
import host.plas.bou.sql.DatabaseType;
import host.plas.restored.Restored;
import lombok.Getter;
import lombok.Setter;
import tv.quaint.thebase.lib.hikari.HikariConfig;
import tv.quaint.thebase.lib.hikari.HikariDataSource;

import java.io.File;
import java.sql.Connection;

@Getter @Setter
public class SQLiteDS extends DBOperator {
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
    }

    @Override
    public void ensureTables() {

    }

    @Override
    public void ensureDatabase() {

    }
}
