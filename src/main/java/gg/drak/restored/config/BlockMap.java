package gg.drak.restored.config;

import gg.drak.thebase.storage.documents.SimpleJsonDocument;
import gg.drak.restored.Restored;
import gg.drak.restored.data.Network;
import gg.drak.restored.data.blocks.BlockLocation;
import gg.drak.restored.data.blocks.datablock.DataBlock;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.block.Block;

import java.util.Optional;

@Getter @Setter
public class BlockMap extends SimpleJsonDocument {
    public BlockMap() {
        super("block-map.json", Restored.getInstance(), false);
    }

    @Override
    public void onInit() {

    }

    @Override
    public void onSave() {

    }

    public static String getKey(BlockLocation blockLocation) {
        return blockLocation.asString();
    }

    public static String getKey(Block block) {
        return getKey(BlockLocation.of(block));
    }

    public Optional<DataBlock> getDataBlockAt(Block block) {
        reloadResource();

        if (! getResource().contains(getKey(block))) {
            Restored.getInstance().logInfo("Block not found in resource");
            return Optional.empty();
        }

        Restored.getInstance().logInfo("Block found in resource");

        DataBlock dataBlock = new DataBlock(block);

        Restored.getInstance().logInfo("DataBlock created");
        return Optional.of(dataBlock);
    }

    public Optional<DataBlock> getDataBlockAt(Block block, Network network) {
        reloadResource();

        if (! getResource().contains(getKey(block))) {
            Restored.getInstance().logInfo("Block not found in resource");
            return Optional.empty();
        }

        Restored.getInstance().logInfo("Block found in resource");

        if (network == null) {
            Restored.getInstance().logInfo("Network is null");
            return Optional.empty();
        }

        DataBlock dataBlock = new DataBlock(block, network);

        Restored.getInstance().logInfo("DataBlock created");
        return Optional.of(dataBlock);
    }

    public void saveBlock(DataBlock dataBlock) {
        dataBlock.writeMap();
        dataBlock.save();
    }

    public void removeBlock(Block block) {
        getResource().remove(getKey(block));
    }

    public void removeBlock(BlockLocation blockLocation) {
        getResource().remove(getKey(blockLocation));
    }

    public Optional<Network> getNetworkAtBlock(Block block) {
        Optional<DataBlock> dataBlock = getDataBlockAt(block);

        if (dataBlock.isEmpty()) {
            Restored.getInstance().logInfo("DataBlock is empty");
            return Optional.empty();
        }
        DataBlock data = dataBlock.get();

        if (data.getNetwork().isEmpty()) {
            Restored.getInstance().logInfo("Network is empty");
            return Optional.empty();
        }

        return data.getNetwork();
    }
}
