package host.plas.restored.data.blocks;

import host.plas.restored.Restored;
import host.plas.restored.config.NetworkMapConfig;
import host.plas.restored.data.Network;
import host.plas.restored.data.blocks.impl.Controller;
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

    public static void addNetworkMap(SingleNetworkMap singleNetworkMap) {
        networkMaps.add(singleNetworkMap);
    }

    public static void removeNetworkMap(String identifier) {
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

    public static void saveAll() {
        getConfig().saveNetworkMaps(networkMaps);
    }

    public static void save(String identifier) {
        getNetworkMap(identifier).ifPresent(NetworkMap::save);
    }

    public static void save(SingleNetworkMap singleNetworkMap) {
        getConfig().saveNetworkMap(singleNetworkMap);
    }

    public static void loadAll() {
        networkMaps = getConfig().getNetworkMaps();
    }

    public static void load(String identifier) {
        getConfig().getNetworkMap(identifier).ifPresent(NetworkMap::addNetworkMap);
    }

    public static void init() {
        loadAll();

        // do other things
    }

    public static void stop() {
        saveAll();

        // do other things
    }

    public static Network createNetwork(Block block, Player player) {
        String identifier = generateUUID();

        return new Network(identifier, block, player);
    }

    public static void addNetwork(Network network) {
        ConcurrentSkipListSet<LocatedBlock> locatedBlocks = new ConcurrentSkipListSet<>();

        network.getBlocks().forEach(networkBlock -> {
            LocatedBlock locatedBlock = new LocatedBlock(networkBlock.getIdentifier(), network.getIdentifier(), networkBlock.getType(), networkBlock.getBlockLocation());
            locatedBlocks.add(locatedBlock);
        });

        SingleNetworkMap singleNetworkMap = new SingleNetworkMap(network.getIdentifier(), network.getOwnerUuid(), locatedBlocks);

        addNetworkMap(singleNetworkMap);
    }

    public static void delete(String identifier) {
        getConfig().deleteNetworkMap(identifier);

        removeNetworkMap(identifier);
    }

    public static void delete(SingleNetworkMap singleNetworkMap) {
        delete(singleNetworkMap.getIdentifier());
    }
}
