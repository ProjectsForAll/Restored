package host.plas.restored.data.blocks;

import gg.drak.thebase.objects.Identifiable;
import host.plas.restored.data.Network;
import host.plas.restored.data.NetworkManager;
import host.plas.restored.data.blocks.datablock.DataBlock;
import host.plas.restored.data.blocks.impl.Controller;
import lombok.Getter;
import lombok.Setter;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicReference;

@Getter @Setter
public class SingleNetworkMap implements Identifiable {
    private String identifier;
    private String ownerUUID;

    private ConcurrentSkipListMap<BlockLocation, LocatedBlock> locatedBlocks;

    public SingleNetworkMap(String identifier, String ownerUUID, ConcurrentSkipListMap<BlockLocation, LocatedBlock> locatedBlocks) {
        this.identifier = identifier;
        this.ownerUUID = ownerUUID;
        this.locatedBlocks = locatedBlocks;
    }

    public SingleNetworkMap(String identifier, String ownerUUID, Collection<LocatedBlock> locatedBlocks) {
        this(identifier, ownerUUID, wrapLocatedBlocks(locatedBlocks));
    }

    public void addLocatedBlock(LocatedBlock locatedBlock) {
        locatedBlocks.put(locatedBlock.getLocation(), locatedBlock);
    }

    public void removeLocatedBlock(String identifier) {
        locatedBlocks.forEach((location, locatedBlock) -> {
            if (locatedBlock.getIdentifier().equals(identifier)) {
                locatedBlocks.remove(location);
            }
        });

        confirmRemove(identifier);
    }

    public boolean hasLocatedBlock(String identifier) {
        return getLocatedBlock(identifier).isPresent();
    }

    public void confirmRemove(String identifier) {
        if (hasLocatedBlock(identifier)) {
            removeLocatedBlock(identifier);
        }
        if (NetworkMap.hasLocatedBlock(identifier)) {
            NetworkMap.removeLocatedBlock(identifier);
        }
    }

    public void removeLocatedBlock(LocatedBlock block) {
        removeLocatedBlock(block.getIdentifier());
    }

    public void removeLocatedBlock(BlockLocation blockLocation) {
        locatedBlocks.remove(blockLocation);
    }

    public Optional<LocatedBlock> getLocatedBlock(String identifier) {
        return locatedBlocks.values().stream().filter(locatedBlock -> locatedBlock.getIdentifier().equals(identifier)).findFirst();
    }

    public Optional<LocatedBlock> getLocatedBlock(BlockLocation blockLocation) {
        return Optional.ofNullable(locatedBlocks.get(blockLocation));
    }

    public Optional<LocatedBlock> getController() {
        return locatedBlocks.values().stream().filter(locatedBlock -> locatedBlock.getType() == BlockType.CONTROLLER).findFirst();
    }

    public void save() {
        NetworkMap.saveNetwork(this);
    }

    public void delete() {
        NetworkMap.delete(this);
    }

    public Optional<Network> getNetwork() {
        return NetworkManager.getOrGetNetwork(identifier);
    }

    public Optional<Controller> getControllerImpl(Optional<Network> optionalNetwork) {
        AtomicReference<Controller> found = new AtomicReference<>(null);

        if (optionalNetwork.isEmpty()) return Optional.empty();

        getController()
                .flatMap(locatedBlock -> NetworkManager.getDataBlockAt(locatedBlock.getLocation().toBlock(), optionalNetwork.get()))
                .flatMap(DataBlock::getNetworkBlock)
                .ifPresent(networkBlock -> {
                    if (networkBlock instanceof Controller) {
                        found.set((Controller) networkBlock);
                    }
                });

        return Optional.ofNullable(found.get());
    }

    public void unload() {
        locatedBlocks.forEach((location, block) -> {
            block.save();
        });
        locatedBlocks.clear();

        NetworkMap.unloadSingleMap(getIdentifier());
    }

    public static ConcurrentSkipListMap<BlockLocation, LocatedBlock> wrapLocatedBlocks(Collection<LocatedBlock> locatedBlocks) {
        ConcurrentSkipListMap<BlockLocation, LocatedBlock> r = new ConcurrentSkipListMap<>();
        locatedBlocks.forEach(locatedBlock -> r.put(locatedBlock.getLocation(), locatedBlock));
        return r;
    }
}
