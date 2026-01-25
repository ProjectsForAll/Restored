package gg.drak.restored.data.blocks.datablock;

import gg.drak.thebase.lib.leonhard.storage.sections.FlatFileSection;
import gg.drak.thebase.objects.Identifiable;
import gg.drak.restored.Restored;
import gg.drak.restored.config.BlockMap;
import gg.drak.restored.data.Network;
import gg.drak.restored.data.NetworkManager;
import gg.drak.restored.data.blocks.BlockLocation;
import gg.drak.restored.data.blocks.BlockType;
import gg.drak.restored.data.blocks.NetworkBlock;
import gg.drak.restored.data.blocks.impl.Controller;
import gg.drak.restored.data.blocks.impl.CraftingViewer;
import gg.drak.restored.data.blocks.impl.Drive;
import gg.drak.restored.data.blocks.impl.Viewer;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.block.Block;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListMap;

@Getter @Setter
public class DataBlock implements Identifiable {
    private String identifier;
    private BlockLocation blockLocation;
    private Optional<Network> network;
    private BlockType type;
    private ConcurrentSkipListMap<String, Object> data;

    public DataBlock(Block block, Optional<Network> network, BlockType type) {
        this.identifier = BlockMap.getKey(block);
        this.blockLocation = BlockLocation.of(block);
        this.network = network;
        this.type = type;

        this.data = new ConcurrentSkipListMap<>();

        writeMap();
        save();
    }

    public DataBlock(Block block, Optional<Network> network) {
        this.identifier = BlockMap.getKey(block);
        this.network = network;
        this.data = new ConcurrentSkipListMap<>();

        load();
        readMap();
    }

    public DataBlock(Block block) {
        this(block, Optional.empty());
    }

    public DataBlock(Block block, @NonNull Network network) {
        this(block, Optional.of(network));
    }

    public Optional<Location> readLocation() {
        String locationString = identifier; // location.
        if (locationString == null) {
            return Optional.empty();
        }

        BlockLocation blockLocation = BlockLocation.of(locationString);
        return Optional.of(blockLocation.toLocation());
    }

    public FlatFileSection getSection() {
        return Restored.getBlockMap().getResource().getSection(getIdentifier());
    }

    public void write(String identifier, Object value) {
        getSection().set(identifier, value);
    }

    public void save() {
        Restored.getInstance().logInfo("Saving DataBlock with " + data.size() + " keys: " + data.keySet());
        for (String key : data.keySet()) {
            Object value = data.get(key);
            write(key, value);
            Restored.getInstance().logInfo("Wrote key '" + key + "' = '" + value + "' to section");
        }
    }

    public void load() {
        Restored.getInstance().logInfo("Loading data block for: " + getIdentifier());

        FlatFileSection section = getSection();
        
        // Debug: Check what's in the section before loading
        Restored.getInstance().logInfo("Section keys before getMapParameterized: " + section.singleLayerKeySet());
        
        // Try reading directly from the section instead of using getMapParameterized
        // getMapParameterized("") might not work correctly for flat keys
        // Read each key directly from the section
        for (String key : section.singleLayerKeySet()) {
            Object value = section.get(key);
            if (value != null) {
                data.put(key, value);
                Restored.getInstance().logInfo("Loaded key '" + key + "' = '" + value + "' from section");
            }
        }
        
        // Also try getMapParameterized as fallback
        Map<String, Object> map = section.getMapParameterized("");
        if (!map.isEmpty()) {
            Restored.getInstance().logInfo("Also found " + map.size() + " keys via getMapParameterized: " + map.keySet());
            data.putAll(map);
        }
        
        Restored.getInstance().logInfo("Data block data loaded. Final data map keys: " + data.keySet());
    }

    public void readNetwork() {
        String networkIdentifier = getString("network");
        if (networkIdentifier != null) {
            Restored.getInstance().logInfo("Network identifier found: " + networkIdentifier);
            network = NetworkManager.getOrGetNetwork(networkIdentifier);
            if (network.isEmpty()) {
                Restored.getInstance().logWarning("Failed to load network: " + networkIdentifier);
            } else {
                Restored.getInstance().logInfo("Network loaded successfully: " + networkIdentifier);
            }
        } else {
            Restored.getInstance().logInfo("Network identifier not found in data map. Data map keys: " + data.keySet());
            network = Optional.empty();
        }
    }

    public void readMap() {
        Restored.getInstance().logInfo("Reading data block map...");

        if (this.network.isEmpty()) {
            readNetwork();
        }

        String typeString = getString("type");
        if (typeString != null) {
            type = BlockType.valueOf(typeString);
        }

        Restored.getInstance().logInfo("Data block map read");
    }

    public void writeMap() {
        if (network.isPresent()) {
            String networkId = network.get().getIdentifier();
            putMap("network", networkId);
            Restored.getInstance().logInfo("Writing network identifier to data map: " + networkId);
        } else {
            Restored.getInstance().logInfo("No network present, skipping network write");
        }

        if (type != null) {
            putMap("type", type.name());
            Restored.getInstance().logInfo("Writing type to data map: " + type.name());
        }
    }

    public void putMap(String key, Object value) {
        data.put(key, value);
    }

    public String getString(String key) {
        return (String) data.get(key);
    }

    public int getInt(String key) {
        return (int) data.get(key);
    }

    public boolean getBoolean(String key) {
        return (boolean) data.get(key);
    }

    public double getDouble(String key) {
        return (double) data.get(key);
    }

    public float getFloat(String key) {
        return (float) data.get(key);
    }

    public Optional<NetworkBlock> getNetworkBlock() {
        load();
        readMap();

        if (type == null) {
            return Optional.empty();
        }

        Network network = this.network.orElse(null);

        Optional<Location> locationOptional = readLocation();
        if (locationOptional.isEmpty()) {
            return Optional.empty();
        }

        Location location = locationOptional.get();

        switch (type) {
            case CONTROLLER:
                return Optional.of(new Controller(network, location, this));
            case DRIVE:
                return Optional.of(new Drive(network, location, this));
            case VIEWER:
                return Optional.of(new Viewer(network, location, this));
            case CRAFTING_VIEWER:
                return Optional.of(new CraftingViewer(network, location, this));
            default:
                return Optional.empty();
        }
    }

    public void delete() {
        Restored.getBlockMap().removeBlock(blockLocation);
    }
}
