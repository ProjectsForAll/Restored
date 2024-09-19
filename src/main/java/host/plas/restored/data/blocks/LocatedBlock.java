package host.plas.restored.data.blocks;

import lombok.Getter;
import lombok.Setter;
import tv.quaint.objects.Identifiable;

import java.util.Optional;

@Getter @Setter
public class LocatedBlock implements Identifiable {
    private String identifier;
    private String network;

    private BlockType type;
    private BlockLocation location;

    public LocatedBlock(String identifier, String network, BlockType type, BlockLocation location) {
        this.identifier = identifier;
        this.network = network;
        this.type = type;
        this.location = location;
    }

    public boolean equals(LocatedBlock locatedBlock) {
        return this.identifier.equals(locatedBlock.getIdentifier());
    }

    public Optional<SingleNetworkMap> getSingleNetworkMap() {
        return NetworkMap.getNetworkMap(network);
    }
}
