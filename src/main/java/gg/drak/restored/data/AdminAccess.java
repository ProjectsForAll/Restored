package gg.drak.restored.data;

import gg.drak.restored.Restored;
import gg.drak.restored.database.MainOperator;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-admin overrides for network access.
 *
 * <ul>
 *     <li><b>Admin mode</b> (persisted): the player is treated as the owner of every network.</li>
 *     <li><b>Session grants</b> (memory only): the player is treated as the owner of one network
 *     opened from {@code /networkadmin manage}, until they leave the network menus or log off.</li>
 *     <li><b>Delete mode</b> (memory only): breaking a network chest deletes that network.</li>
 * </ul>
 *
 * Every override also requires {@link #PERMISSION} at the moment it is used, so revoking the
 * permission revokes the access even while a mode is still switched on.
 */
public final class AdminAccess {
    public static final String PERMISSION = "restored.command.networkadmin";

    private static final Set<UUID> ADMIN_MODE = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> DELETE_MODE = ConcurrentHashMap.newKeySet();
    private static final ConcurrentHashMap<UUID, Set<UUID>> SESSION_GRANTS = new ConcurrentHashMap<>();

    private AdminAccess() {
    }

    public static void init(MainOperator operator) {
        ADMIN_MODE.clear();
        if (operator != null) {
            ADMIN_MODE.addAll(operator.getPlayerPreferenceDAO().getAdminModePlayers());
        }
    }

    private static boolean permitted(UUID playerId) {
        Player player = playerId == null ? null : Bukkit.getPlayer(playerId);
        return player != null && player.hasPermission(PERMISSION);
    }

    /** True when {@code playerId} should be treated as the owner of {@code networkId}. */
    public static boolean actsAsOwner(UUID playerId, UUID networkId) {
        if (playerId == null) {
            return false;
        }
        boolean granted = ADMIN_MODE.contains(playerId);
        if (!granted && networkId != null) {
            Set<UUID> sessions = SESSION_GRANTS.get(playerId);
            granted = sessions != null && sessions.contains(networkId);
        }
        return granted && permitted(playerId);
    }

    public static boolean isAdminMode(UUID playerId) {
        return playerId != null && ADMIN_MODE.contains(playerId);
    }

    /** @return the new state */
    public static boolean toggleAdminMode(UUID playerId) {
        boolean enabled = !ADMIN_MODE.remove(playerId);
        if (enabled) {
            ADMIN_MODE.add(playerId);
        }
        if (Restored.getDatabase() != null) {
            Bukkit.getScheduler().runTaskAsynchronously(Restored.getInstance(),
                    () -> Restored.getDatabase().getPlayerPreferenceDAO().saveAdminMode(playerId, enabled));
        }
        return enabled;
    }

    public static void grantSession(UUID playerId, UUID networkId) {
        if (playerId != null && networkId != null) {
            SESSION_GRANTS.computeIfAbsent(playerId, id -> ConcurrentHashMap.newKeySet()).add(networkId);
        }
    }

    public static boolean hasSessionGrants(UUID playerId) {
        return playerId != null && SESSION_GRANTS.containsKey(playerId);
    }

    public static void clearSessionGrants(UUID playerId) {
        if (playerId != null) {
            SESSION_GRANTS.remove(playerId);
        }
    }

    public static boolean isDeleteMode(UUID playerId) {
        return playerId != null && DELETE_MODE.contains(playerId) && permitted(playerId);
    }

    /** @return the new state */
    public static boolean toggleDeleteMode(UUID playerId) {
        if (DELETE_MODE.remove(playerId)) {
            return false;
        }
        DELETE_MODE.add(playerId);
        return true;
    }

    /** Forgets per-login state. Admin mode is persisted and stays. */
    public static void onQuit(UUID playerId) {
        clearSessionGrants(playerId);
        DELETE_MODE.remove(playerId);
    }
}
