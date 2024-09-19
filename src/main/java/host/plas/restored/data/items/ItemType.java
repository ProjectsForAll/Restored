package host.plas.restored.data.items;

import host.plas.restored.data.blocks.BlockType;
import host.plas.restored.data.items.impl.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Optional;

@Getter
public enum ItemType {
    // Disks
    GENERIC_DISK,
    FOUR_K_DISK,

    // Blocks
    CONTROLLER(BlockType.CONTROLLER),
    DRIVE(BlockType.DRIVE),
    VIEWER(BlockType.VIEWER),

    // None
    NONE,
    ;

    private final Optional<BlockType> blockType;

    ItemType(BlockType blockType) {
        if (blockType == null) this.blockType = Optional.empty();
        else this.blockType = Optional.of(blockType);
    }

    ItemType() {
        this(null);
    }

    public boolean isPlaceable() {
        return blockType.isPresent();
    }
}
