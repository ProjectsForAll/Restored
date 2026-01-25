package gg.drak.restored.data.items;

import gg.drak.restored.data.blocks.BlockType;
import gg.drak.restored.data.items.impl.*;
import lombok.Getter;

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
    CRAFTING_VIEWER(BlockType.CRAFTING_VIEWER),

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
