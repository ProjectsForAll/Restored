package host.plas.restored.config;

import host.plas.restored.Restored;
import host.plas.restored.data.Network;
import host.plas.restored.data.blocks.datablock.DataBlock;
import host.plas.restored.utils.MessageUtils;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.block.Block;
import tv.quaint.storage.documents.SimpleJsonDocument;

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

    public static String getKey(Block block) {
        return block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }

    public Optional<DataBlock> getDataBlockAt(Block block) {
        reloadResource();

        if (! getResource().contains(getKey(block))) {
            MessageUtils.logInfo("Block not found in resource");
            return Optional.empty();
        }

        MessageUtils.logInfo("Block found in resource");

        DataBlock dataBlock = new DataBlock(block);

        MessageUtils.logInfo("DataBlock created");
        return Optional.of(dataBlock);
    }

    public Optional<DataBlock> getDataBlockAt(Block block, Network network) {
        reloadResource();

        if (! getResource().contains(getKey(block))) {
            MessageUtils.logInfo("Block not found in resource");
            return Optional.empty();
        }

        MessageUtils.logInfo("Block found in resource");

        if (network == null) {
            MessageUtils.logInfo("Network is null");
            return Optional.empty();
        }

        DataBlock dataBlock = new DataBlock(block, network);

        MessageUtils.logInfo("DataBlock created");
        return Optional.of(dataBlock);
    }

    public void saveBlock(DataBlock dataBlock) {
        dataBlock.writeMap();
        dataBlock.save();
    }

    public void removeBlock(Block block) {
        getResource().remove(getKey(block));
    }

    public Optional<Network> getNetworkAtBlock(Block block) {
        Optional<DataBlock> dataBlock = getDataBlockAt(block);

        if (dataBlock.isEmpty()) {
            MessageUtils.logInfo("DataBlock is empty");
            return Optional.empty();
        }
        DataBlock data = dataBlock.get();

        if (data.getNetwork().isEmpty()) {
            MessageUtils.logInfo("Network is empty");
            return Optional.empty();
        }

        return data.getNetwork();
    }
}
