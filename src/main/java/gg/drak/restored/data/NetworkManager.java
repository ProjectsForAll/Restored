package gg.drak.restored.data;

import gg.drak.restored.Restored;
import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class NetworkManager {

    @Getter
    private static final ConcurrentHashMap<UUID, Network> byId = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Network> byLocation = new ConcurrentHashMap<>();

    private NetworkManager() {
    }

    public static String locationKey(String world, int x, int y, int z) {
        StringBuilder builder = new StringBuilder();
        builder.append(world).append(':').append(x).append(':').append(y).append(':').append(z);
        return builder.toString();
    }

    public static String locationKey(Location location) {
        return locationKey(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public static void loadAll(Collection<Network> networks) {
        byId.clear();
        byLocation.clear();
        for (Network network : networks) {
            register(network);
            Restored.getDatabase().getMiddleware().cacheNetwork(network);
        }
    }

    public static void register(Network network) {
        byId.put(network.getIdentifier(), network);
        if (network.isPlaced()) {
            byLocation.put(locationKey(network.getWorld(), network.getX(), network.getY(), network.getZ()), network);
        }
    }

    public static void unregister(Network network) {
        byId.remove(network.getIdentifier());
        if (network.isPlaced()) {
            byLocation.remove(locationKey(network.getWorld(), network.getX(), network.getY(), network.getZ()));
        }
        Restored.getDatabase().getMiddleware().removeNetworkFromCache(network.getIdentifierString());
    }

    public static void updateLocation(Network network, Location oldLocation, Location newLocation) {
        if (oldLocation != null && oldLocation.getWorld() != null) {
            byLocation.remove(locationKey(oldLocation));
        }
        network.setLocation(newLocation);
        if (network.isPlaced()) {
            byLocation.put(locationKey(network.getWorld(), network.getX(), network.getY(), network.getZ()), network);
        }
    }

    public static Network create(Player owner, Location location) {
        Network network = new Network(UUID.randomUUID(), owner.getUniqueId());
        network.setLocation(location);
        register(network);
        Restored.getDatabase().getMiddleware().cacheNetwork(network);
        network.markDirty();
        network.save();
        return network;
    }

    public static Network get(UUID id) {
        return byId.get(id);
    }

    public static Network getByLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        return byLocation.get(locationKey(location));
    }

    public static Network getByLocation(String world, int x, int y, int z) {
        return byLocation.get(locationKey(world, x, y, z));
    }

    public static Collection<Network> getNetworks() {
        return byId.values();
    }

    public static List<Network> getNetworksForPlayer(UUID playerUuid) {
        List<Network> result = new ArrayList<>();
        for (Network network : byId.values()) {
            if (network.isOwner(playerUuid) || network.getRole(playerUuid) == NetworkRole.ADMIN) {
                result.add(network);
            }
        }
        result.sort(NETWORK_ID_COMPARATOR);
        return result;
    }

    private static final Comparator<Network> NETWORK_ID_COMPARATOR = new NetworkIdComparator();

    public static void saveAllDirty() {
        for (Network network : byId.values()) {
            if (network.isDirty()) {
                network.save();
            }
        }
    }

    public static void saveAll() {
        for (Network network : byId.values()) {
            network.markDirty();
            network.save();
        }
    }

    private static final class NetworkIdComparator implements Comparator<Network> {
        @Override
        public int compare(Network a, Network b) {
            return a.getIdentifierString().compareTo(b.getIdentifierString());
        }
    }
}
