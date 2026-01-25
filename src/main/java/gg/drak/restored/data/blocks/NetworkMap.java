package gg.drak.restored.data.blocks;

import gg.drak.restored.Restored;
import gg.drak.restored.data.Network;
import gg.drak.restored.data.NetworkManager;
import gg.drak.restored.database.MainOperator;
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
            return singleNetworkMap.getLocatedBlocks().containsKey(blockLocation);
        }).findFirst();
    }

    public static Optional<SingleNetworkMap> getNetworkMap(Block block) {
        return getNetworkMap(getBlockLocation(block));
    }

    public static Optional<SingleNetworkMap> getNetworkMap(Location location) {
        return getNetworkMap(location.getBlock());
    }

    public static void addLocatedBlock(LocatedBlock locatedBlock) {
        // No longer needed as middleware handles it, but kept for compatibility
    }

    public static void removeLocatedBlock(String identifier) {
        getNetworkMaps().forEach(singleNetworkMap -> {
            singleNetworkMap.getLocatedBlocks().entrySet().removeIf(entry -> entry.getValue().getIdentifier().equals(identifier));
        });
        
        Restored.getDatabase().getMiddleware().removeBlockFromCache(identifier);
    }

    public static void removeLocatedBlock(LocatedBlock block) {
        removeLocatedBlock(block.getIdentifier());
    }

    public static boolean hasLocatedBlock(String identifier) {
        return getLocatedBlock(identifier).isPresent();
    }

    public static void confirmRemove(String identifier, SingleNetworkMap singleNetworkMap) {
        removeLocatedBlock(identifier);
        if (singleNetworkMap.hasLocatedBlock(identifier)) {
            singleNetworkMap.removeLocatedBlock(identifier);
        }
    }

    public static Optional<LocatedBlock> getLocatedBlock(String identifier) {
        Optional<NetworkBlock> cached = Restored.getDatabase().getMiddleware().getCachedBlock(identifier);
        if (cached.isPresent()) return cached.map(LocatedBlock::new);
        
        return Restored.getDatabase().getNetworkBlockDAO().getById(identifier).map(data -> {
            // Create a temporary LocatedBlock from the data
            return new LocatedBlock(data.getIdentifier(), data.getNetworkId(), data.getBlockType(), data.asBlockLocation(), data.getData());
        });
    }

    public static Optional<LocatedBlock> getLocatedBlock(BlockLocation blockLocation) {
        for (SingleNetworkMap map : networkMaps) {
            Optional<LocatedBlock> block = map.getLocatedBlock(blockLocation);
            if (block.isPresent()) return block;
        }
        return Optional.empty();
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

    public static MainOperator getDatabase() {
        return Restored.getDatabase();
    }

    public static void saveAllNetworks() {
        getNetworkMaps().forEach(singleNetworkMap -> {
            singleNetworkMap.getNetwork().ifPresent(Network::onSave);
        });
    }

    public static void saveNetwork(String identifier) {
        getNetworkMap(identifier).ifPresent(NetworkMap::saveNetwork);
    }

    public static void saveNetwork(Network network) {
        saveNetwork(network.getIdentifier());
    }

    public static void saveNetwork(SingleNetworkMap singleNetworkMap) {
        getDatabase().saveNetworkMap(singleNetworkMap);
    }

    public static void loadAllNetworks() {
        networkMaps = getDatabase().getNetworkMaps();
        
        // The global locatedBlocks mapping is now handled by the middleware's block cache
        // and the individual SingleNetworkMaps.
    }

    public static void loadNetwork(String identifier) {
        getDatabase().getNetworkMap(identifier).ifPresent(NetworkMap::loadSingleMap);
    }

    public static void saveAllLocatedBlocks() {
        // getConfig().saveLocatedBlocks(locatedBlocks.values());
    }

    public static void saveLocatedBlock(LocatedBlock locatedBlock) {
        // getConfig().saveLocatedBlock(locatedBlock);
    }

    public static void loadAllLocatedBlocks() {
        // locatedBlocks = getConfig().getLocatedBlocks();
    }

    public static void loadLocatedBlock(LocatedBlock locatedBlock) {
        // getConfig().getLocatedBlock(locatedBlock.getIdentifier()).ifPresent(NetworkMap::addLocatedBlock);
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
            LocatedBlock locatedBlock = new LocatedBlock(networkBlock.getIdentifier(), network.getIdentifier(), networkBlock.getType(), networkBlock.getBlockLocation(), networkBlock.getData().toString());
            singleNetworkMap.addLocatedBlock(locatedBlock);
        });

        loadSingleMap(singleNetworkMap);
    }

    public static void delete(String identifier) {
        // getConfig().deleteNetworkMap(identifier);

        unloadSingleMap(identifier);
    }

    public static void delete(SingleNetworkMap singleNetworkMap) {
        delete(singleNetworkMap.getIdentifier());
    }

    public static boolean hasNetworkHardLookup(String identifier) {
        // return getConfig().containsNetwork(identifier);
        return false;
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
