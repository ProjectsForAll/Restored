package gg.drak.restored.data;

import gg.drak.restored.Restored;
import gg.drak.restored.database.MainOperator;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Persisted preferences that belong to a player rather than a particular item. */
public final class PlayerPreferences {
    private static final ConcurrentHashMap<UUID, Boolean> MAGNET_TO_NETWORK = new ConcurrentHashMap<>();

    private PlayerPreferences() {
    }

    public static void init(MainOperator operator) {
        MAGNET_TO_NETWORK.clear();
        if (operator == null) {
            return;
        }
        for (Map.Entry<UUID, Boolean> entry : operator.getPlayerPreferenceDAO().getMagnetToNetworkPreferences().entrySet()) {
            MAGNET_TO_NETWORK.put(entry.getKey(), entry.getValue());
        }
    }

    /** Inventory-first is the default for players with no saved preference. */
    public static boolean isMagnetToNetwork(UUID playerId) {
        return playerId != null && MAGNET_TO_NETWORK.getOrDefault(playerId, false);
    }

    public static void setMagnetToNetwork(UUID playerId, boolean value) {
        if (playerId == null) {
            return;
        }
        MAGNET_TO_NETWORK.put(playerId, value);
        if (Restored.getDatabase() != null) {
            Restored.getDatabase().getPlayerPreferenceDAO().saveMagnetToNetwork(playerId, value);
        }
    }
}
