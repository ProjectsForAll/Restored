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
        // Don't reload resource here - it may overwrite unsaved changes
        // The resource should already be up-to-date in memory
        
        // Check if the section exists and has data by checking for the "type" key
        // This is more reliable than contains() since getSection() may create empty sections
        String key = getKey(block);
        var section = getResource().getSection(key);
        
        // Check if section has the "type" key, which indicates a valid DataBlock
        // Also check if the resource contains the key (for backwards compatibility)
        if (!section.contains("type") && !getResource().contains(key)) {
            Restored.getInstance().logInfo("Block not found in resource (no type key and not in resource)");
            return Optional.empty();
        }

        Restored.getInstance().logInfo("Block found in resource");

        DataBlock dataBlock = new DataBlock(block);

        Restored.getInstance().logInfo("DataBlock created");
        return Optional.of(dataBlock);
    }

    public Optional<DataBlock> getDataBlockAt(Block block, Network network) {
        // Don't reload resource here - it may overwrite unsaved changes
        // The resource should already be up-to-date in memory
        
        // Check if the section exists and has data by checking for the "type" key
        // This is more reliable than contains() since getSection() may create empty sections
        String key = getKey(block);
        var section = getResource().getSection(key);
        
        // Check if section has the "type" key, which indicates a valid DataBlock
        // Also check if the resource contains the key (for backwards compatibility)
        if (!section.contains("type") && !getResource().contains(key)) {
            Restored.getInstance().logInfo("Block not found in resource (no type key and not in resource)");
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
        Restored.getInstance().logInfo("Saving block: " + dataBlock.getIdentifier());
        dataBlock.writeMap();
        dataBlock.save();
        
        // Explicitly save the resource to ensure changes are persisted
        // This is critical for the data to be available when loading
        save();
        
        // Verify the data was written by checking the section
        String key = dataBlock.getIdentifier();
        var section = getResource().getSection(key);
        
        // Force a read to ensure the section is properly registered
        // This helps ensure the section is immediately available for contains() checks
        if (dataBlock.getType() != null) {
            String typeInSection = section.getOrSetDefault("type", null);
            if (typeInSection == null) {
                Restored.getInstance().logWarning("Warning: Type not found in section after save for: " + key);
            } else {
                Restored.getInstance().logInfo("Verified type in section: " + typeInSection);
            }
        }
        
        if (dataBlock.getNetwork().isPresent()) {
            String networkId = section.getOrSetDefault("network", null);
            if (networkId == null) {
                Restored.getInstance().logWarning("Warning: Network identifier not found in section after save for: " + key);
            } else {
                Restored.getInstance().logInfo("Verified network in section: " + networkId);
            }
        }
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
