package host.plas.restored.config;

import host.plas.restored.Restored;
import host.plas.restored.data.blocks.BlockLocation;
import host.plas.restored.data.blocks.BlockType;
import host.plas.restored.data.blocks.LocatedBlock;
import host.plas.restored.data.blocks.SingleNetworkMap;
import lombok.Getter;
import lombok.Setter;
import tv.quaint.objects.AtomicString;
import tv.quaint.storage.documents.SimpleJsonDocument;
import tv.quaint.thebase.lib.leonhard.storage.sections.FlatFileSection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;

@Getter @Setter
public class NetworkMapConfig extends SimpleJsonDocument {
    public NetworkMapConfig() {
        super("network-map.json", Restored.getInstance(), false);
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
            section.set(locatedBlock.getIdentifier() + ".location", locatedBlock.getLocation().toString());
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

    public ConcurrentSkipListSet<String> getOwnedNetworks(String ownerUUID) {
        ConcurrentSkipListSet<String> r = new ConcurrentSkipListSet<>();
        FlatFileSection section = getResource().getSection("owners." + ownerUUID);

        section.singleLayerKeySet().forEach(key -> {
            r.addAll(section.getOrSetDefault(key, new ArrayList<>()));
        });

        return r;
    }

    public ConcurrentSkipListMap<String, ConcurrentSkipListSet<String>> getOwnedNetworks() {
        ConcurrentSkipListMap<String, ConcurrentSkipListSet<String>> r = new ConcurrentSkipListMap<>();
        FlatFileSection section = getResource().getSection("owners");

        section.singleLayerKeySet().forEach(key -> {
            r.put(key, getOwnedNetworks(key));
        });

        return r;
    }

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
        ConcurrentSkipListSet<String> r = getOwnedNetworks(ownerUUID);
        r.add(identifier);

        putOwnedNetworks(ownerUUID, r);

        cleanOwnedNetworks();
    }

    public void removeOwnedNetwork(String ownerUUID, String identifier) {
        ConcurrentSkipListSet<String> r = getOwnedNetworks(ownerUUID);
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
