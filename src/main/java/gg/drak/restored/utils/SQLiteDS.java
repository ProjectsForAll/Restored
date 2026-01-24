package gg.drak.restored.utils;

import host.plas.bou.sql.ConnectorSet;
import host.plas.bou.sql.DBOperator;
import host.plas.bou.sql.DatabaseType;
import gg.drak.restored.Restored;
import lombok.Getter;
import lombok.Setter;

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
