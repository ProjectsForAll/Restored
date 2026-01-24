package gg.drak.restored.config;

import gg.drak.thebase.lib.leonhard.storage.sections.FlatFileSection;
import gg.drak.thebase.objects.AtomicString;
import gg.drak.thebase.storage.documents.SimpleJsonDocument;
import gg.drak.restored.Restored;
import gg.drak.restored.data.Network;
import gg.drak.restored.data.blocks.BlockLocation;
import gg.drak.restored.data.blocks.BlockType;
import gg.drak.restored.data.blocks.LocatedBlock;
import gg.drak.restored.data.blocks.SingleNetworkMap;
import gg.drak.restored.utils.IOUtils;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;

@Getter @Setter
public class NetworkMapConfig extends SimpleJsonDocument {
    public NetworkMapConfig() {
        super("network-map.json", IOUtils.getStorageFolder(), false);
    }

    @Override
    public void onInit() {

    }

    @Override
    public void onSave() {

    }

    public ConcurrentSkipListSet<LocatedBlock> getNetworkLocatedBlocks(String identifier) {
        ConcurrentSkipListSet<LocatedBlock> r = new ConcurrentSkipListSet<>();
        FlatFileSection section = getResource().getSection("networks." + identifier);

        section.singleLayerKeySet().forEach(key -> {
            try {
                String location = section.getOrSetDefault(key + ".location", "");
                String type = section.getOrSetDefault(key + ".type", "");

                BlockType blockType = BlockType.valueOf(type);
                BlockLocation blockLocation = BlockLocation.of(location);

                LocatedBlock locatedBlock = new LocatedBlock(key, identifier, blockType, blockLocation);

                r.add(locatedBlock);
            } catch (Exception e) {
                Restored.getInstance().logWarning("Failed to load located block: " + key);
                Restored.getInstance().logWarning(e);
            }
        });

        return r;
    }

    public Optional<SingleNetworkMap> getNetworkMap(String identifier) {
        FlatFileSection section = getResource().getSection("networks");
        if (! containsNetwork(identifier)) {
            return Optional.empty();
        }

        ConcurrentSkipListSet<LocatedBlock> locatedBlocks = getNetworkLocatedBlocks(identifier);
        String ownerUUID = getNetworkOwner(identifier).orElse(null);
        SingleNetworkMap singleNetworkMap = new SingleNetworkMap(identifier, ownerUUID, locatedBlocks);

        return Optional.of(singleNetworkMap);
    }

    public ConcurrentSkipListSet<String> getNetworkIdentifiers() {
        FlatFileSection section = getResource().getSection("networks");
        return new ConcurrentSkipListSet<>(section.singleLayerKeySet());
    }

    public boolean containsNetwork(String identifier) {
        return getNetworkIdentifiers().contains(identifier);
    }

    public ConcurrentSkipListSet<SingleNetworkMap> getNetworkMaps() {
        ConcurrentSkipListSet<SingleNetworkMap> r = new ConcurrentSkipListSet<>();

        getNetworkIdentifiers().forEach(identifier -> {
            getNetworkMap(identifier).ifPresent(r::add);
        });

        return r;
    }

    public void saveNetworkMap(SingleNetworkMap singleNetworkMap) {
        FlatFileSection section = getResource().getSection("networks." + singleNetworkMap.getIdentifier());

        singleNetworkMap.getLocatedBlocks().forEach((blockLocation, locatedBlock) -> {
            section.set(locatedBlock.getIdentifier() + ".location", locatedBlock.getLocation().asString());
            section.set(locatedBlock.getIdentifier() + ".type", locatedBlock.getType().name());
        });

        addOwnedNetwork(singleNetworkMap.getOwnerUUID(), singleNetworkMap.getIdentifier());
    }

    public void saveNetworkMaps(Collection<SingleNetworkMap> networkMaps) {
        networkMaps.forEach(this::saveNetworkMap);
    }

    public void deleteNetworkMap(String identifier) {
        getResource().getSection("networks").remove(identifier);
    }

    public void deleteNetworkMap(SingleNetworkMap singleNetworkMap) {
        deleteNetworkMap(singleNetworkMap.getIdentifier());
    }

    public String getNetworkId(String blockId) {
        AtomicString r = new AtomicString("");
        getNetworkMaps().forEach(singleNetworkMap -> {
            singleNetworkMap.getLocatedBlocks().forEach((blockLocation, locatedBlock) -> {
                if (locatedBlock.getIdentifier().equals(blockId)) {
                    r.set(singleNetworkMap.getIdentifier());
                }
            });
        });

        return r.get();
    }

    public Optional<LocatedBlock> getLocatedBlock(String identifier) {
        if (! containsLocatedBlock(identifier)) {
            return Optional.empty();
        }

        FlatFileSection section = getResource().getSection("located-blocks." + identifier);

        String location = section.getOrSetDefault("location", "");
        String type = section.getOrSetDefault("type", "");

        BlockType blockType = BlockType.valueOf(type);
        BlockLocation blockLocation = BlockLocation.of(location);

        LocatedBlock block = new LocatedBlock(identifier, getNetworkId(identifier), blockType, blockLocation);

        return Optional.of(block);
    }

    public ConcurrentSkipListSet<String> getLocatedBlockIdentifiers() {
        FlatFileSection section = getResource().getSection("located-blocks");
        return new ConcurrentSkipListSet<>(section.singleLayerKeySet());
    }

    public boolean containsLocatedBlock(String identifier) {
        return getLocatedBlockIdentifiers().contains(identifier);
    }

    public ConcurrentSkipListMap<BlockLocation, LocatedBlock> getLocatedBlocks() {
        ConcurrentSkipListMap<BlockLocation, LocatedBlock> r = new ConcurrentSkipListMap<>();
        FlatFileSection section = getResource().getSection("located-blocks");

        section.singleLayerKeySet().forEach(key -> {
            try {
                Optional<LocatedBlock> locatedBlockOptional = getLocatedBlock(key);
                if (locatedBlockOptional.isEmpty()) return;

                LocatedBlock locatedBlock = locatedBlockOptional.get();
                BlockLocation blockLocation = locatedBlock.getLocation();

                r.put(blockLocation, locatedBlock);
            } catch (Exception e) {
                Restored.getInstance().logWarning("Failed to load located block: " + key);
                Restored.getInstance().logWarning(e);
            }
        });

        return r;
    }

    public void saveLocatedBlock(LocatedBlock locatedBlock) {
        FlatFileSection section = getResource().getSection("located-blocks");

        section.set(locatedBlock.getIdentifier() + ".location", locatedBlock.getLocation().asString());
        section.set(locatedBlock.getIdentifier() + ".type", locatedBlock.getType().name());
    }

    public void deleteLocatedBlock(String identifier) {
        getResource().getSection("located-blocks").remove(identifier);
    }

    public void saveLocatedBlocks(Collection<LocatedBlock> locatedBlocks) {
        locatedBlocks.forEach(this::saveLocatedBlock);
    }

    public ConcurrentSkipListSet<String> getOwnersOfNetworks() {
        return new ConcurrentSkipListSet<>(singleLayerKeySet("owners"));
    }

    public ConcurrentSkipListSet<String> getOwnedNetworks(String ownerUUID) {
        return new ConcurrentSkipListSet<>(getOrSetDefault("owners." + ownerUUID, new ArrayList<>()));
    }

    public ConcurrentSkipListMap<String, ConcurrentSkipListSet<String>> getOwnedNetworks() {
        ConcurrentSkipListMap<String, ConcurrentSkipListSet<String>> r = new ConcurrentSkipListMap<>();

        getOwnersOfNetworks().forEach(ownerUUID -> {
            r.put(ownerUUID, getOwnedNetworks(ownerUUID));
        });

        return r;
    }

    // Gets a owner UUID from a network identifier
    public Optional<String> getNetworkOwner(String identifier) {
        AtomicString r = new AtomicString(null);

        getOwnedNetworks().forEach((ownerUUID, networks) -> {
            if (networks.contains(identifier)) {
                r.set(ownerUUID);
            }
        });

        return Optional.ofNullable(r.get());
    }

    public void putOwnedNetworks(String ownerUUID, Collection<String> networks) {
        write("owners." + ownerUUID, networks);
    }

    public void addOwnedNetwork(String ownerUUID, String identifier) {
        ConcurrentSkipListSet<String> r = new ConcurrentSkipListSet<>(getOwnedNetworks(ownerUUID));
        r.add(identifier);

        putOwnedNetworks(ownerUUID, r);

        cleanOwnedNetworks();
    }

    public void addOwnedNetwork(Network network) {
        addOwnedNetwork(network.getOwnerUuid(), network.getIdentifier());
    }

    public void removeOwnedNetwork(String ownerUUID, String identifier) {
        ConcurrentSkipListSet<String> r = new ConcurrentSkipListSet<>(getOwnedNetworks(ownerUUID));
        r.remove(identifier);

        putOwnedNetworks(ownerUUID, r);

        cleanOwnedNetworks();
    }

    public void cleanOwnedNetworks() {
        getOwnedNetworks().forEach((ownerUUID, networks) -> {
            ConcurrentSkipListSet<String> r = networks;
            r.removeIf(network -> ! containsNetwork(network));

            putOwnedNetworks(ownerUUID, r);
        });
    }
}
