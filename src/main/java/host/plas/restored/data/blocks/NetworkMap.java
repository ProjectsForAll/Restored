package host.plas.restored.data.blocks;

import host.plas.restored.Restored;
import host.plas.restored.config.NetworkMapConfig;
import host.plas.restored.data.Network;
import host.plas.restored.data.NetworkManager;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicBoolean;

public class NetworkMap {
    @Getter @Setter
    private static ConcurrentSkipListSet<SingleNetworkMap> networkMaps = new ConcurrentSkipListSet<>();
    @Getter @Setter
    private static ConcurrentSkipListMap<BlockLocation, LocatedBlock> locatedBlocks = new ConcurrentSkipListMap<>();

    public static void loadSingleMap(SingleNetworkMap singleNetworkMap) {
        networkMaps.add(singleNetworkMap);
    }

    public static void unloadSingleMap(String identifier) {
        networkMaps.removeIf(singleNetworkMap -> singleNetworkMap.getIdentifier().equals(identifier));
    }

    public static Optional<SingleNetworkMap> getNetworkMap(String identifier) {
        return networkMaps.stream().filter(singleNetworkMap -> singleNetworkMap.getIdentifier().equals(identifier)).findFirst();
    }

    public static Optional<SingleNetworkMap> getNetworkMap(BlockLocation blockLocation) {
        return networkMaps.stream().filter(singleNetworkMap -> {
            AtomicBoolean found = new AtomicBoolean(false);
            singleNetworkMap.getLocatedBlocks().forEach((location, locatedBlock) -> {
                if (location.equals(blockLocation)) {
                    found.set(true);
                }
            });

            return found.get();
        }).findFirst();
    }

    public static Optional<SingleNetworkMap> getNetworkMap(Block block) {
        return getNetworkMap(getBlockLocation(block));
    }

    public static Optional<SingleNetworkMap> getNetworkMap(Location location) {
        return getNetworkMap(location.getBlock());
    }

    public static void addLocatedBlock(LocatedBlock locatedBlock) {
        locatedBlocks.put(locatedBlock.getLocation(), locatedBlock);
    }

    public static void removeLocatedBlock(String identifier) {
        Optional<LocatedBlock> block = getLocatedBlock(identifier);
        locatedBlocks.forEach((location, locatedBlock) -> {
            if (locatedBlock.getIdentifier().equals(identifier)) {
                locatedBlocks.remove(location);
            }
        });

        block.map(LocatedBlock::getSingleNetworkMap).flatMap(singleNetworkMap -> {
            singleNetworkMap.ifPresent(networkMap -> networkMap.removeLocatedBlock(identifier));
            return singleNetworkMap;
        }).ifPresent(networkMap -> confirmRemove(identifier, networkMap));
    }

    public static void removeLocatedBlock(LocatedBlock block) {
        removeLocatedBlock(block.getIdentifier());
    }

    public static boolean hasLocatedBlock(String identifier) {
        return getLocatedBlock(identifier).isPresent();
    }

    public static void confirmRemove(String identifier, SingleNetworkMap singleNetworkMap) {
        if (hasLocatedBlock(identifier)) {
            removeLocatedBlock(identifier);
        }
        if (singleNetworkMap.hasLocatedBlock(identifier)) {
            singleNetworkMap.removeLocatedBlock(identifier);
        }
    }

    public static Optional<LocatedBlock> getLocatedBlock(String identifier) {
        return locatedBlocks.values().stream().filter(locatedBlock -> locatedBlock.getIdentifier().equals(identifier)).findFirst();
    }

    public static Optional<LocatedBlock> getLocatedBlock(BlockLocation blockLocation) {
        return Optional.ofNullable(locatedBlocks.get(blockLocation));
    }

    public static Optional<LocatedBlock> getLocatedBlock(Block block) {
        return getLocatedBlock(getBlockLocation(block));
    }

    public static BlockLocation getBlockLocation(Block block) {
        return new BlockLocation(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    public static BlockLocation getBlockLocation(Location location) {
        return getBlockLocation(location.getBlock());
    }

    public static boolean containsUUID(String identifier) {
        return networkMaps.stream().anyMatch(singleNetworkMap -> singleNetworkMap.getIdentifier().equals(identifier));
    }

    public static String generateUUID() {
        String uuid = UUID.randomUUID().toString();
        while (containsUUID(uuid)) {
            uuid = UUID.randomUUID().toString();
        }

        return uuid;
    }

    public static NetworkMapConfig getConfig() {
        return Restored.getNetworkMapConfig();
    }

    public static void saveAllNetworks() {
        getConfig().saveNetworkMaps(networkMaps);
    }

    public static void saveNetwork(String identifier) {
        getNetworkMap(identifier).ifPresent(NetworkMap::saveNetwork);
    }

    public static void saveNetwork(Network network) {
        saveNetwork(network.getIdentifier());
    }

    public static void saveNetwork(SingleNetworkMap singleNetworkMap) {
        getConfig().saveNetworkMap(singleNetworkMap);
    }

    public static void loadAllNetworks() {
        networkMaps = getConfig().getNetworkMaps();
    }

    public static void loadNetwork(String identifier) {
        getConfig().getNetworkMap(identifier).ifPresent(NetworkMap::loadSingleMap);
    }

    public static void saveAllLocatedBlocks() {
        getConfig().saveLocatedBlocks(locatedBlocks.values());
    }

    public static void saveLocatedBlock(LocatedBlock locatedBlock) {
        getConfig().saveLocatedBlock(locatedBlock);
    }

    public static void loadAllLocatedBlocks() {
        locatedBlocks = getConfig().getLocatedBlocks();
    }

    public static void loadLocatedBlock(LocatedBlock locatedBlock) {
        getConfig().getLocatedBlock(locatedBlock.getIdentifier()).ifPresent(NetworkMap::addLocatedBlock);
    }

    public static void init() {
        loadAllNetworks();

        loadAllLocatedBlocks();

        // do other things
    }

    public static void stop() {
        saveAllNetworks();

        saveAllLocatedBlocks();

        // do other things
    }

    public static Network createNetwork(Block block, Player player) {
        String identifier = generateUUID();

        Network network = new Network(identifier, block, player);

        NetworkManager.loadNetwork(network);

        createNetworkMap(network);

        return network;
    }

    public static void createNetworkMap(Network network) {
        SingleNetworkMap singleNetworkMap = new SingleNetworkMap(network.getIdentifier(), network.getOwnerUuid(), new ConcurrentSkipListSet<>());

        network.getBlocks().forEach(networkBlock -> {
            LocatedBlock locatedBlock = new LocatedBlock(networkBlock.getIdentifier(), network.getIdentifier(), networkBlock.getType(), networkBlock.getBlockLocation());
            singleNetworkMap.addLocatedBlock(locatedBlock);
        });

        loadSingleMap(singleNetworkMap);
    }

    public static void delete(String identifier) {
        getConfig().deleteNetworkMap(identifier);

        unloadSingleMap(identifier);
    }

    public static void delete(SingleNetworkMap singleNetworkMap) {
        delete(singleNetworkMap.getIdentifier());
    }

    public static boolean hasNetworkHardLookup(String identifier) {
        return getConfig().containsNetwork(identifier);
    }

    public static boolean hasNetwork(String identifier) {
        return getNetworkMap(identifier).isPresent();
    }

    public static void onNetworkLoad(Network network) {
        if (hasNetwork(network.getIdentifier())) {
            return;
        } else if (hasNetworkHardLookup(network.getIdentifier())) {
            loadNetwork(network.getIdentifier());
        } else {
            createNetworkMap(network);
        }
    }

    public static Optional<Network> getNetwork(String networkId) {
        return getNetworkMap(networkId).flatMap(SingleNetworkMap::getNetwork);
    }
}
