package host.plas.restored.data.blocks;

import host.plas.restored.data.Network;
import host.plas.restored.data.NetworkManager;
import host.plas.restored.data.blocks.datablock.DataBlock;
import host.plas.restored.data.blocks.datablock.IDatalizable;
import host.plas.restored.data.items.RestoredItem;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

@Getter @Setter
public abstract class NetworkBlock implements Comparable<NetworkBlock>, IDatalizable {
    private BlockType type;
    private UUID uuid;
    private Optional<Network> network;
    private Location location;
    private Supplier<RestoredItem> itemGetter;
    private DataBlock dataBlock;

    public String getIdentifier() {
        return uuid.toString();
    }

    public NetworkBlock(BlockType type, UUID uuid, Network network, Location location, Supplier<RestoredItem> itemGetter) {
        this.type = type;
        this.uuid = uuid;
        if (network == null) {
            this.network = Optional.empty();
        } else {
            this.network = Optional.of(network);
        }
        this.location = location;
        this.itemGetter = itemGetter;

        this.dataBlock = new DataBlock(getBlock(), this.network, type);

        NetworkManager.saveDataBlockAt(this.getDataBlock());
    }

    public NetworkBlock(BlockType type, UUID uuid, Network network, Location location, Supplier<RestoredItem> itemGetter, DataBlock dataBlock) {
        this.type = type;
        this.uuid = uuid;
        if (network == null) {
            this.network = Optional.empty();
        } else {
            this.network = Optional.of(network);
        }
        this.location = location;
        this.itemGetter = itemGetter;

        this.dataBlock = dataBlock;

        NetworkManager.saveDataBlockAt(this.getDataBlock());
    }

    public NetworkBlock(BlockType type, Network network, Location location, Supplier<RestoredItem> itemGetter) {
        this(type, UUID.randomUUID(), network, location, itemGetter);
    }

    public NetworkBlock(BlockType type, Network network, Location location, Supplier<RestoredItem> itemGetter, DataBlock dataBlock) {
        this(type, UUID.randomUUID(), network, location, itemGetter, dataBlock);
    }

    public abstract void onLoad();

    public abstract void onSave();

    public void onPlaced() {
        saveDataBlock();
    }

    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (! getBlock().equals(block)) return;

        dropBlock();

        event.setCancelled(true);

        clean();
    }

    public void clean() {
        network.ifPresent(value -> value.removeBlock(this));
        network = Optional.empty();
        getDataBlock().setNetwork(Optional.empty());

        saveDataBlock();
    }

    public void dropBlock() {
        ItemStack drop = itemGetter.get().getItem();

        getBlock().getWorld().dropItemNaturally(getBlock().getLocation(), drop);
    }

    public boolean isTickable() {
        return this instanceof Tickable;
    }

    public void onTick() {
        if (isTickable()) {
            ((Tickable) this).onTick();
        }
    }

    @Override
    public int compareTo(@NotNull NetworkBlock o) {
        if (location.getWorld().getName().equals(o.getLocation().getWorld().getName())) {
            if (location.getBlockX() == o.getLocation().getBlockX()) {
                if (location.getBlockY() == o.getLocation().getBlockY()) {
                    return Integer.compare(location.getBlockZ(), o.getLocation().getBlockZ());
                } else {
                    return Integer.compare(location.getBlockY(), o.getLocation().getBlockY());
                }
            } else {
                return Integer.compare(location.getBlockX(), o.getLocation().getBlockX());
            }
        } else {
            return location.getWorld().getName().compareTo(o.getLocation().getWorld().getName());
        }
    }

    public Block getBlock() {
        return location.getBlock();
    }
}
