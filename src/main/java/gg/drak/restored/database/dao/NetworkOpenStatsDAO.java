package gg.drak.restored.database.dao;

import gg.drak.restored.Restored;
import gg.drak.restored.database.MainOperator;
import gg.drak.restored.database.Statements;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
public class NetworkOpenStatsDAO {
    private final MainOperator operator;

    public NetworkOpenStatsDAO(MainOperator operator) {
        this.operator = operator;
    }

    public void save(String networkId, String playerUuid, long opens) {
        try {
            operator.ensureUsable();
            String statement = Statements.getStatement(Statements.StatementType.INSERT_NETWORK_OPEN_STAT, operator.getConnectorSet());
            operator.execute(statement, stmt -> {
                try {
                    stmt.setString(1, networkId);
                    stmt.setString(2, playerUuid);
                    stmt.setLong(3, opens);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) {
            Restored.getInstance().logSevere("Failed to save open stats for network " + networkId, e);
        }
    }

    public void deleteAll(String networkId) {
        try {
            operator.ensureUsable();
            String statement = Statements.getStatement(Statements.StatementType.DELETE_NETWORK_OPEN_STATS, operator.getConnectorSet());
            operator.execute(statement, stmt -> {
                try {
                    stmt.setString(1, networkId);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) {
            Restored.getInstance().logSevere("Failed to delete open stats for network " + networkId, e);
        }
    }

    public List<OpenStatRow> getByNetworkId(String networkId) {
        try {
            operator.ensureUsable();
            String statement = Statements.getStatement(Statements.StatementType.GET_NETWORK_OPEN_STATS, operator.getConnectorSet());
            List<OpenStatRow> rows = new ArrayList<>();
            operator.executeQuery(statement, stmt -> {
                try {
                    stmt.setString(1, networkId);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, rs -> {
                try {
                    while (rs.next()) {
                        rows.add(new OpenStatRow(
                                rs.getString("PlayerUuid"),
                                rs.getLong("Opens")
                        ));
                    }
                } catch (Exception e) {
                    Restored.getInstance().logSevere("Failed to read open stats for network " + networkId, e);
                }
            });
            return rows;
        } catch (Exception e) {
            Restored.getInstance().logSevere("Failed to get open stats for network " + networkId, e);
            return new ArrayList<>();
        }
    }

    @Getter
    public static class OpenStatRow {
        private final String playerUuid;
        private final long opens;

        public OpenStatRow(String playerUuid, long opens) {
            this.playerUuid = playerUuid;
            this.opens = opens;
        }
    }
}
