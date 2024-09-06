package host.plas.restored.data.blocks;

import host.plas.restored.data.Network;
import host.plas.restored.data.blocks.impl.Controller;
import host.plas.restored.data.blocks.impl.Drive;
import host.plas.restored.data.blocks.impl.Viewer;
import lombok.Getter;
import org.bukkit.Location;

@Getter
public enum BlockType {
    CONTROLLER(9),
    DRIVE(9),
    VIEWER(54),
    ;

    private int slots;

    BlockType(int slots) {
        this.slots = slots;
    }

    BlockType() {
        this(-1);
    }

    public static NetworkBlock getBlock(BlockType type, Network network, Location location) {
        switch (type) {
            case CONTROLLER:
                return new Controller(network, location);
            case DRIVE:
                return new Drive(network, location);
            case VIEWER:
                return new Viewer(network, location);
            default:
                return null;
        }
    }
}
