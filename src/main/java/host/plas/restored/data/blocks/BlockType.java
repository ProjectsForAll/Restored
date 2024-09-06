package host.plas.restored.data.blocks;

import host.plas.bou.gui.GuiType;
import host.plas.restored.data.Network;
import host.plas.restored.data.blocks.impl.Controller;
import host.plas.restored.data.blocks.impl.Drive;
import host.plas.restored.data.blocks.impl.Viewer;
import lombok.Getter;
import org.bukkit.Location;

@Getter
public enum BlockType implements GuiType {
    CONTROLLER(9, "Controller"),
    DRIVE(9, "Drive"),
    VIEWER(54, "Viewer"),

    NONE,
    ;

    private final int slots;
    private final String title;

    BlockType(int slots, String title) {
        this.slots = slots;
        this.title = title;
    }

    BlockType() {
        this(-1, null);
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
