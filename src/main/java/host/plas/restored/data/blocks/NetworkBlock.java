package host.plas.restored.data.blocks;

import host.plas.bou.gui.InventorySheet;
import host.plas.bou.gui.ScreenManager;
import host.plas.bou.gui.screens.ScreenInstance;
import host.plas.bou.gui.screens.blocks.ScreenBlock;
import host.plas.restored.Restored;
import host.plas.restored.data.Network;
import host.plas.restored.data.NetworkManager;
import host.plas.restored.data.blocks.datablock.DataBlock;
import host.plas.restored.data.blocks.datablock.IDatalizable;
import host.plas.restored.data.blocks.impl.Controller;
import host.plas.restored.data.items.RestoredItem;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

@Getter @Setter
public abstract class NetworkBlock extends ScreenBlock implements IDatalizable {
    private String identifier;
    private BlockType type;
    private Optional<Network> network;
    private Location location;
    private Supplier<RestoredItem> itemGetter;
    private DataBlock dataBlock;

    public NetworkBlock(BlockType type, UUID uuid, Network network, Location location, Supplier<RestoredItem> itemGetter) {
        super(type, location);
        this.type = type;
        this.identifier = uuid.toString();
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
        super(type, location);
        this.type = type;
        this.identifier = uuid.toString();
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
        Restored.getInstance().logInfo("Placed block: " + this.getIdentifier());

        saveDataBlock();
        LocatedBlock block = new LocatedBlock(this);
        NetworkMap.addLocatedBlock(block);
        block.save();

        network.ifPresent(value -> {
            SingleNetworkMap map = value.getMap();
            map.addLocatedBlock(block);
            map.save();
        });
    }

    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (! getBlock().equals(block)) return;

        if (this instanceof Controller) {
            Controller controller = (Controller) this;
            getNetwork().ifPresent(Network::delete);
        }

        dropBlock();

        event.setCancelled(true);

        clean();
    }

    public void clean() {
        network.ifPresent(value -> value.removeBlock(this));
        network = Optional.empty();

        getDataBlock().setNetwork(Optional.empty());
        getDataBlock().delete();

        NetworkMap.getLocatedBlock(this.getIdentifier()).ifPresent(LocatedBlock::remove);

        setAsAir();
    }

    public void setAsAir() {
        getBlock().setType(Material.AIR);
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

    public Block getBlock() {
        return location.getBlock();
    }

    public BlockLocation getBlockLocation() {
        return BlockLocation.of(getLocation());
    }

    public void redraw() {
        ScreenManager.getPlayersOf(this).forEach(screenInstance -> {
            screenInstance.close();

            InventorySheet sheet = buildInventorySheet(screenInstance.player, this);
            ScreenInstance instance = new ScreenInstance(screenInstance.player, screenInstance.getType(), sheet);
            instance.setBlock(this);
        });
    }
}
