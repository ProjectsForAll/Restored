package host.plas.restored.data.blocks.datablock;

import host.plas.restored.Restored;
import host.plas.restored.data.Network;
import org.bukkit.block.Block;

import java.util.Optional;

public interface IDatalizable {
    Optional<Network> getNetwork();

    DataBlock getDataBlock();

    void setDataBlock(DataBlock dataBlock);

    default void saveDataBlock() {
        Restored.getBlockMap().saveBlock(getDataBlock());
    }

    default void loadDataBlock() {
        Restored.getBlockMap().getDataBlockAt(getBlock()).ifPresent(this::setDataBlock);
    }

    default void loadDataBlock(Network network) {
        Restored.getBlockMap().getDataBlockAt(getBlock(), network).ifPresent(this::setDataBlock);
    }

    Block getBlock();
}
