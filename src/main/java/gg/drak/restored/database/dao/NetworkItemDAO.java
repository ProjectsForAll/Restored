package gg.drak.restored.database.dao;

import gg.drak.restored.Restored;
import gg.drak.restored.database.MainOperator;
import gg.drak.restored.database.Statements;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
public class NetworkItemDAO {
    private final MainOperator operator;

    public NetworkItemDAO(MainOperator operator) {
        this.operator = operator;
    }

    public void save(String networkId, String itemKey, String itemData, long amount) {
        try {
            operator.ensureUsable();
            String statement = Statements.getStatement(Statements.StatementType.INSERT_NETWORK_ITEM, operator.getConnectorSet());
            operator.execute(statement, stmt -> {
                try {
                    stmt.setString(1, networkId);
                    stmt.setString(2, itemKey);
                    stmt.setString(3, itemData);
                    stmt.setLong(4, amount);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) {
            Restored.getInstance().logSevere("Failed to save network item for " + networkId, e);
        }
    }

    public void deleteAll(String networkId) {
        try {
            operator.ensureUsable();
            String statement = Statements.getStatement(Statements.StatementType.DELETE_NETWORK_ITEMS, operator.getConnectorSet());
            operator.execute(statement, stmt -> {
                try {
                    stmt.setString(1, networkId);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) {
            Restored.getInstance().logSevere("Failed to delete network items for " + networkId, e);
        }
    }

    public List<ItemRow> getByNetworkId(String networkId) {
        try {
            operator.ensureUsable();
            String statement = Statements.getStatement(Statements.StatementType.GET_NETWORK_ITEMS, operator.getConnectorSet());
            List<ItemRow> rows = new ArrayList<>();
            operator.executeQuery(statement, stmt -> {
                try {
                    stmt.setString(1, networkId);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, rs -> {
                try {
                    while (rs.next()) {
                        rows.add(new ItemRow(
                                rs.getString("ItemKey"),
                                rs.getString("ItemData"),
                                rs.getLong("Amount")
                        ));
                    }
                } catch (Exception e) {
                    Restored.getInstance().logSevere("Failed to read network items for " + networkId, e);
                }
            });
            return rows;
        } catch (Exception e) {
            Restored.getInstance().logSevere("Failed to get network items for " + networkId, e);
            return new ArrayList<>();
        }
    }

    @Getter
    public static class ItemRow {
        private final String itemKey;
        private final String itemData;
        private final long amount;

        public ItemRow(String itemKey, String itemData, long amount) {
            this.itemKey = itemKey;
            this.itemData = itemData;
            this.amount = amount;
        }
    }
}
