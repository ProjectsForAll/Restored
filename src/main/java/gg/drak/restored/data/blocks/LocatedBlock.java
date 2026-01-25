package gg.drak.restored.data.blocks;

import gg.drak.thebase.objects.AtomicString;
import gg.drak.thebase.objects.Identifiable;
import lombok.Getter;
import lombok.Setter;

import java.util.Optional;

@Getter @Setter
public class LocatedBlock implements Identifiable {
    private String identifier;
    private String network;

    private BlockType type;
    private BlockLocation location;
    private String data;

    public LocatedBlock(String identifier, String network, BlockType type, BlockLocation location, String data) {
        this.identifier = identifier;
        this.network = network;
        this.type = type;
        this.location = location;
        this.data = data;
    }

    public LocatedBlock(NetworkBlock block) {
        this(block.getIdentifier(), getNetworkIdentifier(block), block.getType(), block.getBlockLocation(), block.getData().toString());
    }

    public boolean equals(LocatedBlock locatedBlock) {
        return this.identifier.equals(locatedBlock.getIdentifier());
    }

    public Optional<SingleNetworkMap> getSingleNetworkMap() {
        if (network == null) {
            return Optional.empty();
        }
        if (network.isEmpty() || network.isBlank()) {
            return Optional.empty();
        }
        return NetworkMap.getNetworkMap(network);
    }

    public void save() {
        NetworkMap.saveLocatedBlock(this);
    }

    public void remove() {
        NetworkMap.removeLocatedBlock(this);
    }

    public void load() {
        NetworkMap.loadLocatedBlock(this);
    }

    public static String getNetworkIdentifier(NetworkBlock block) {
        AtomicString atomicString = new AtomicString("");
        block.getNetwork().ifPresent(network -> atomicString.set(network.getIdentifier()));
        return atomicString.get();
    }
}
