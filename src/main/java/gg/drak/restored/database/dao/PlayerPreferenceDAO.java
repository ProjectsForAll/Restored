package gg.drak.restored.database.dao;

import gg.drak.restored.Restored;
import gg.drak.restored.database.MainOperator;
import gg.drak.restored.database.Statements;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Getter
public class PlayerPreferenceDAO {
    private final MainOperator operator;

    public PlayerPreferenceDAO(MainOperator operator) {
        this.operator = operator;
    }

    public Map<UUID, Boolean> getMagnetToNetworkPreferences() {
        Map<UUID, Boolean> result = new HashMap<>();
        try {
            operator.ensureUsable();
            String statement = Statements.getStatement(Statements.StatementType.GET_PLAYER_PREFERENCES, operator.getConnectorSet());
            operator.executeQuery(statement, stmt -> {
            }, rs -> {
                try {
                    while (rs.next()) {
                        try {
                            result.put(UUID.fromString(rs.getString("PlayerUuid")), rs.getBoolean("MagnetToNetwork"));
                        } catch (IllegalArgumentException ignored) {
                            // Skip malformed persisted player ids.
                        }
                    }
                } catch (Exception e) {
                    Restored.getInstance().logSevere("Failed to read player preferences", e);
                }
            });
        } catch (Exception e) {
            Restored.getInstance().logSevere("Failed to get player preferences", e);
        }
        return result;
    }

    public void saveMagnetToNetwork(UUID playerId, boolean value) {
        if (playerId == null) {
            return;
        }
        try {
            operator.ensureUsable();
            String statement = Statements.getStatement(Statements.StatementType.INSERT_PLAYER_PREFERENCE, operator.getConnectorSet());
            operator.execute(statement, stmt -> {
                try {
                    stmt.setString(1, playerId.toString());
                    stmt.setBoolean(2, value);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) {
            Restored.getInstance().logSevere("Failed to save player preference for " + playerId, e);
        }
    }

    /** Players who have admin mode switched on. */
    public java.util.Set<UUID> getAdminModePlayers() {
        java.util.Set<UUID> result = new java.util.HashSet<>();
        try {
            operator.ensureUsable();
            String statement = Statements.getStatement(Statements.StatementType.GET_ADMIN_MODES, operator.getConnectorSet());
            operator.executeQuery(statement, stmt -> {
            }, rs -> {
                try {
                    while (rs.next()) {
                        try {
                            result.add(UUID.fromString(rs.getString("PlayerUuid")));
                        } catch (IllegalArgumentException ignored) {
                            // Skip malformed persisted player ids.
                        }
                    }
                } catch (Exception e) {
                    Restored.getInstance().logSevere("Failed to read admin modes", e);
                }
            });
        } catch (Exception e) {
            Restored.getInstance().logSevere("Failed to get admin modes", e);
        }
        return result;
    }

    public void saveAdminMode(UUID playerId, boolean enabled) {
        if (playerId == null) {
            return;
        }
        try {
            operator.ensureUsable();
            String statement = Statements.getStatement(
                    enabled ? Statements.StatementType.INSERT_ADMIN_MODE : Statements.StatementType.DELETE_ADMIN_MODE,
                    operator.getConnectorSet());
            operator.execute(statement, stmt -> {
                try {
                    stmt.setString(1, playerId.toString());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) {
            Restored.getInstance().logSevere("Failed to save admin mode for " + playerId, e);
        }
    }
}
