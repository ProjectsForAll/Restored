package host.plas.restored.data.blocks.datablock;

import host.plas.restored.Restored;
import host.plas.restored.config.BlockMap;
import host.plas.restored.data.Network;
import host.plas.restored.data.NetworkManager;
import host.plas.restored.data.blocks.BlockType;
import host.plas.restored.data.blocks.NetworkBlock;
import host.plas.restored.data.blocks.impl.Controller;
import host.plas.restored.data.blocks.impl.Drive;
import host.plas.restored.data.blocks.impl.Viewer;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import tv.quaint.objects.Identifiable;
import tv.quaint.thebase.lib.leonhard.storage.sections.FlatFileSection;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListMap;

@Getter @Setter
public class DataBlock implements Identifiable {
    private String identifier;
    private Optional<Network> network;
    private BlockType type;
    private ConcurrentSkipListMap<String, Object> data;

    public DataBlock(Block block, Optional<Network> network, BlockType type) {
        this.identifier = BlockMap.getKey(block);
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
        String[] split = identifier.split(":");
        World world = Bukkit.getWorld(split[0]);
        if (world == null) {
            return Optional.empty();
        }

        int x = Integer.parseInt(split[1]);
        int y = Integer.parseInt(split[2]);
        int z = Integer.parseInt(split[3]);

        return Optional.of(new Location(world, x, y, z));
    }

    public FlatFileSection getSection() {
        return Restored.getBlockMap().getResource().getSection(getIdentifier());
    }

    public void write(String identifier, Object value) {
        getSection().set(identifier, value);
    }

    public void save() {
        for (String key : data.keySet()) {
            write(key, data.get(key));
        }
    }

    public void load() {
        Restored.getInstance().logInfo("Loading data block...");

        FlatFileSection section = getSection();

        Map<String, Object> map = section.getMapParameterized("");
        data.putAll(map);

        Restored.getInstance().logInfo("Data block data loaded");
    }

    public void readNetwork() {
        String networkIdentifier = getString("network");
        if (networkIdentifier != null) {
            Restored.getInstance().logInfo("Network identifier found");
            network = NetworkManager.getOrGetNetwork(networkIdentifier);
        } else {
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
        network.ifPresent(value -> putMap("network", value.getIdentifier()));

        if (type != null) {
            putMap("type", type.name());
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
            default:
                return Optional.empty();
        }
    }
}
