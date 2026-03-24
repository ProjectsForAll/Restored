package gg.drak.restored.data.blocks;

import com.google.gson.JsonObject;
import host.plas.bou.gui.InventorySheet;
import host.plas.bou.gui.ScreenManager;
import host.plas.bou.gui.screens.ScreenInstance;
import host.plas.bou.gui.screens.blocks.ScreenBlock;
import host.plas.bou.gui.screens.events.BlockOpenEvent;
import host.plas.bou.gui.screens.events.BlockRedrawEvent;
import gg.drak.restored.Restored;
import gg.drak.restored.data.Network;
import gg.drak.restored.data.blocks.impl.Controller;
import gg.drak.restored.data.items.RestoredItem;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

@Getter @Setter
public abstract class NetworkBlock extends ScreenBlock {
    private String identifier;
    private BlockType type;
    private Optional<Network> network;
    private BlockLocation location;
    private Supplier<RestoredItem> itemGetter;
    private JsonObject data;

    public static JsonObject newData(String identifier, BlockType type, Optional<Network> network, Location location) {
        JsonObject data = new JsonObject();

        data.addProperty("identifier", identifier);
        data.addProperty("type", type.name());
        data.add("location", BlockLocation.of(location).toJson());
        data.addProperty("hasNetwork", network.isPresent());
        data.addProperty("networkId", network.map(Network::getUuid).map(UUID::toString).orElse("null"));

        return data;
    }

    public NetworkBlock(BlockType type, UUID uuid, @Nullable Network network, Location location, Supplier<RestoredItem> itemGetter, JsonObject data) {
        super(type, location);
        this.type = type;
        this.identifier = uuid.toString();
        if (network == null) {
            this.network = Optional.empty();
        } else {
            this.network = Optional.of(network);
        }
        this.location = BlockLocation.of(location);
        this.itemGetter = itemGetter;

        this.data = data;
    }

    public NetworkBlock(BlockType type, UUID uuid, @Nullable Network network, Location location, Supplier<RestoredItem> itemGetter) {
        this(type, uuid, network, location, itemGetter, newData(uuid.toString(), type, Optional.ofNullable(network), location));
    }

    public NetworkBlock(BlockType type, @Nullable Network network, Location location, Supplier<RestoredItem> itemGetter) {
        this(type, UUID.randomUUID(), network, location, itemGetter);
    }

    public NetworkBlock(BlockType type, @Nullable Network network, Location location, Supplier<RestoredItem> itemGetter, JsonObject data) {
        this(type, UUID.randomUUID(), network, location, itemGetter, data);
    }

    public abstract void onLoad();

    public abstract void onSaveSpecific();

    public void onSave() {
        onSaveSpecific();
        saveData();
    }

    public void onPlaced() {
        Restored.getInstance().logInfo("Placed block: " + this.getIdentifier());

        // Cache in middleware immediately
        Restored.getDatabase().getMiddleware().cacheBlock(this);

        LocatedBlock block = new LocatedBlock(this);
        // NetworkMap.addLocatedBlock(block); // No longer needed as middleware handles it
        // block.save();

        network.ifPresent(value -> {
            onSave();
            SingleNetworkMap map = value.getNetworkMap();
            map.addLocatedBlock(block);
            map.save();
        });
    }

    public Location getLocation() {
        return location.toLocation();
    }

    public void saveData() {
        // Cache in middleware immediately
        Restored.getDatabase().getMiddleware().cacheBlock(this);

        network.ifPresent(net -> {
            String dataString = data.toString();
            Restored.getDatabase().getNetworkBlockDAO().insert(identifier, net.getIdentifier(), type, dataString);
            
            // Update the data in the SingleNetworkMap
            net.getNetworkMap().getLocatedBlock(location).ifPresent(locatedBlock -> {
                locatedBlock.setData(dataString);
            });
        });
    }

    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (! getBlock().equals(block)) return;

        if (this instanceof Controller) {
            getNetwork().ifPresent(Network::delete);
        }

        dropBlock();

        event.setCancelled(true);

        clean();
    }

    public void clean() {
        network.ifPresent(value -> value.removeBlock(this));
        network = Optional.empty();

        Restored.getDatabase().getNetworkBlockDAO().delete(identifier);

        NetworkMap.removeLocatedBlock(this.getIdentifier());

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
        return location.toBlock();
    }

    public BlockLocation getBlockLocation() {
        return location;
    }

    @Override
    public ScreenInstance buildScreen(BlockOpenEvent event) {
        Player player = event.getPlayer();
        ScreenBlock block = event.getScreenBlock();
        InventorySheet inventorySheet = buildInventorySheet(player, block);
        ScreenInstance instance = createScreenInstance(player, inventorySheet);
        instance.setBlock(block);
        instance.setTitle(buildTitle(player, block));
        return instance;
    }

    /**
     * Subclasses (e.g. crafting viewer) may return a {@link ScreenInstance} that allows placement into specific slots.
     */
    protected ScreenInstance createScreenInstance(Player player, InventorySheet inventorySheet) {
        return new ScreenInstance(player, getType(), inventorySheet);
    }

    @Override
    public void onRedraw(BlockRedrawEvent event) {
        ScreenBlock block = event.getScreenBlock();
        if (! block.getIdentifier().equals(getIdentifier())) return;

        ScreenManager.getPlayersOf(block).forEach(screenInstance -> {
            Player player = screenInstance.getPlayer();
            InventorySheet fresh = buildInventorySheet(player, this);
            screenInstance.setInventorySheet(fresh);
            screenInstance.redraw();
            screenInstance.setBlock(this);
        });
    }
}
