package gg.drak.restored.database.dao;

import gg.drak.restored.Restored;
import gg.drak.restored.data.AugmentType;
import gg.drak.restored.database.MainOperator;
import gg.drak.restored.database.Statements;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
public class NetworkAugmentDAO {
    private final MainOperator operator;

    public NetworkAugmentDAO(MainOperator operator) {
        this.operator = operator;
    }

    public void save(String networkId, AugmentType type) {
        save(networkId, type, 0);
    }

    public void save(String networkId, AugmentType type, int enchantingBookshelves) {
        try {
            operator.ensureUsable();
            String statement = Statements.getStatement(Statements.StatementType.INSERT_NETWORK_AUGMENT, operator.getConnectorSet());
            operator.execute(statement, stmt -> {
                try {
                    stmt.setString(1, networkId);
                    String storedType = type.name();
                    if (type == AugmentType.ENCHANTING && enchantingBookshelves > 0) {
                        storedType += ":" + Math.min(15, enchantingBookshelves);
                    }
                    stmt.setString(2, storedType);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) {
            Restored.getInstance().logSevere("Failed to save augment for network " + networkId, e);
        }
    }

    public void deleteAll(String networkId) {
        try {
            operator.ensureUsable();
            String statement = Statements.getStatement(Statements.StatementType.DELETE_NETWORK_AUGMENTS, operator.getConnectorSet());
            operator.execute(statement, stmt -> {
                try {
                    stmt.setString(1, networkId);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) {
            Restored.getInstance().logSevere("Failed to delete augments for network " + networkId, e);
        }
    }

    public List<AugmentRow> getByNetworkId(String networkId) {
        try {
            operator.ensureUsable();
            String statement = Statements.getStatement(Statements.StatementType.GET_NETWORK_AUGMENTS, operator.getConnectorSet());
            List<AugmentRow> rows = new ArrayList<>();
            operator.executeQuery(statement, stmt -> {
                try {
                    stmt.setString(1, networkId);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, rs -> {
                try {
                    while (rs.next()) {
                        String stored = rs.getString("AugmentType");
                        String[] parts = stored.split(":", 2);
                        AugmentType type = AugmentType.valueOf(parts[0]);
                        int bookshelves = 0;
                        if (parts.length == 2) {
                            try {
                                bookshelves = Integer.parseInt(parts[1]);
                            } catch (NumberFormatException ignored) {
                            }
                        }
                        rows.add(new AugmentRow(type, bookshelves));
                    }
                } catch (Exception e) {
                    Restored.getInstance().logSevere("Failed to read augments for network " + networkId, e);
                }
            });
            return rows;
        } catch (Exception e) {
            Restored.getInstance().logSevere("Failed to get augments for network " + networkId, e);
            return new ArrayList<>();
        }
    }

    @Getter
    public static class AugmentRow {
        private final AugmentType type;
        private final int enchantingBookshelves;

        public AugmentRow(AugmentType type, int enchantingBookshelves) {
            this.type = type;
            this.enchantingBookshelves = enchantingBookshelves;
        }
    }
}
