package gg.drak.restored.database.dao;

import gg.drak.restored.Restored;
import gg.drak.restored.data.CompactConfiguration;
import gg.drak.restored.data.CompactingAction;
import gg.drak.restored.data.QuantityOperand;
import gg.drak.restored.database.MainOperator;
import gg.drak.restored.database.Statements;
import gg.drak.restored.serialization.PersistedItemCodec;
import lombok.Getter;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
public final class NetworkCompactConfigurationDAO {
    private final MainOperator operator;

    public NetworkCompactConfigurationDAO(MainOperator operator) {
        this.operator = operator;
    }

    public void save(String networkId, CompactConfiguration configuration) {
        try {
            operator.ensureUsable();
            String statement = Statements.getStatement(Statements.StatementType.INSERT_NETWORK_COMPACT_CONFIGURATION,
                    operator.getConnectorSet());
            operator.execute(statement, stmt -> {
                try {
                    stmt.setString(1, networkId);
                    stmt.setString(2, configuration.getIdentifier().toString());
                    stmt.setBoolean(3, configuration.isEnabled());
                    if (configuration.getItem() == null) {
                        stmt.setNull(4, java.sql.Types.LONGVARCHAR);
                    } else {
                        stmt.setString(4, PersistedItemCodec.serializePayload(configuration.getItem()));
                    }
                    stmt.setLong(5, configuration.getQuantity());
                    stmt.setString(6, configuration.getAction().name());
                    stmt.setString(7, configuration.getOperand().name());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) {
            Restored.getInstance().logSevere("Failed to save compactor configuration for network " + networkId, e);
        }
    }

    public void deleteAll(String networkId) {
        try {
            operator.ensureUsable();
            String statement = Statements.getStatement(Statements.StatementType.DELETE_NETWORK_COMPACT_CONFIGURATIONS,
                    operator.getConnectorSet());
            operator.execute(statement, stmt -> {
                try {
                    stmt.setString(1, networkId);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) {
            Restored.getInstance().logSevere("Failed to delete compactor configurations for network " + networkId, e);
        }
    }

    public List<CompactConfiguration> getByNetworkId(String networkId) {
        try {
            operator.ensureUsable();
            String statement = Statements.getStatement(Statements.StatementType.GET_NETWORK_COMPACT_CONFIGURATIONS,
                    operator.getConnectorSet());
            List<CompactConfiguration> result = new ArrayList<>();
            operator.executeQuery(statement, stmt -> {
                try {
                    stmt.setString(1, networkId);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, rs -> {
                try {
                    while (rs.next()) {
                        UUID id;
                        try {
                            id = UUID.fromString(rs.getString("ConfigId"));
                        } catch (IllegalArgumentException ignored) {
                            continue;
                        }
                        ItemStack item = null;
                        String itemData = rs.getString("ItemData");
                        if (itemData != null && !itemData.isBlank()) {
                            item = PersistedItemCodec.deserializePayload(itemData);
                        }
                        CompactingAction action = parseEnum(rs.getString("Action"), CompactingAction.class,
                                CompactingAction.COMPACT);
                        QuantityOperand operand = parseEnum(rs.getString("Operand"), QuantityOperand.class,
                                QuantityOperand.MORE_THAN_OR_EQUAL_TO);
                        result.add(new CompactConfiguration(id, rs.getBoolean("Enabled"), item,
                                Math.max(0, rs.getLong("Quantity")), action, operand));
                    }
                } catch (Exception e) {
                    Restored.getInstance().logSevere("Failed to read compactor configurations for network " + networkId, e);
                }
            });
            return result;
        } catch (Exception e) {
            Restored.getInstance().logSevere("Failed to get compactor configurations for network " + networkId, e);
            return new ArrayList<>();
        }
    }

    private static <E extends Enum<E>> E parseEnum(String raw, Class<E> type, E fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
