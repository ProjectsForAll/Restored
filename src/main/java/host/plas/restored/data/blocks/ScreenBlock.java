package host.plas.restored.data.blocks;

import host.plas.restored.data.Network;
import host.plas.restored.data.blocks.datablock.DataBlock;
import host.plas.restored.data.items.RestoredItem;
import host.plas.restored.data.screens.InventorySheet;
import host.plas.restored.data.screens.ScreenInstance;
import host.plas.restored.data.screens.ScreenManager;
import host.plas.restored.events.own.*;
import host.plas.restored.utils.MessageUtils;
import lombok.Getter;
import lombok.Setter;
import mc.obliviate.inventory.Gui;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.function.Supplier;

@Getter @Setter
public abstract class ScreenBlock extends NetworkBlock {
    public ScreenBlock(BlockType type, Network network, Location location, Supplier<RestoredItem> itemGetter) {
        super(type, network, location, itemGetter);
    }

    public ScreenBlock(BlockType type, Network network, Location location, Supplier<RestoredItem> itemGetter, DataBlock block) {
        super(type, network, location, itemGetter, block);
    }

    public void onRightClick(Player player) {
        MessageUtils.logInfo("On right click at block...");

        Optional<Network> network = getNetwork();
        if (network.isPresent()) {
            NetworkOpenEvent event = new NetworkOpenEvent(network.get(), player, this);
            event.fire();

            if (!event.isCancelled()) {
                MessageUtils.logInfo("Opening Network...");
                onOpen(event);
            }
        } else {
            BlockOpenEvent event = new BlockOpenEvent(player, this);
            event.fire();

            if (! event.isCancelled()) {
                MessageUtils.logInfo("Opening Block...");
                onOpen(event);
            }
        }
    }

    public void onClose(Player player) {
        Optional<Network> network = getNetwork();
        if (network.isPresent()) {
            NetworkCloseEvent event = new NetworkCloseEvent(network.get(), player, this);
            event.fire();

            if (!event.isCancelled()) {
                onClose(event);
            }
        } else {
            BlockCloseEvent event = new BlockCloseEvent(player, this);
            event.fire();

            if (!event.isCancelled()) {
                onClose(event);
            }
        }
    }

    public void onRedraw() {
        Optional<Network> network = getNetwork();
        if (network.isPresent()) {
            NetworkRedrawEvent event = new NetworkRedrawEvent(network.get(), this);
            event.fire();

            if (!event.isCancelled()) {
                onRedraw(event);
            }
        } else {
            BlockRedrawEvent event = new BlockRedrawEvent(this);
            event.fire();

            if (!event.isCancelled()) {
                onRedraw(event);
            }
        }
    }

    public void onOpen(BlockOpenEvent event) {
        Player player = event.getPlayer();

        if (ScreenManager.hasScreen(player)) {
            ScreenManager.getScreen(player).ifPresent(ScreenInstance::open);
        } else {
            buildScreen(event).open();
        }
    }

    public void onClose(BlockCloseEvent event) {
        Player player = event.getPlayer();

        ScreenManager.removeScreen(player);
    }

    public abstract InventorySheet buildInventorySheet(Player player, ScreenBlock block);

    public abstract String buildTitle(Player player, ScreenBlock block);

    public ScreenInstance buildScreen(BlockOpenEvent event) {
        MessageUtils.logInfo("Building Screen...");

        Player player = event.getPlayer();
        ScreenBlock block = event.getBlock();

        MessageUtils.logInfo("Building sheet...");
        InventorySheet inventorySheet = buildInventorySheet(player, block);
        MessageUtils.logInfo("Building title...");
        String title = buildTitle(player, block);

        MessageUtils.logInfo("Creating screen instance...");

        return new ScreenInstance(player, getIdentifier(), title, inventorySheet, block);
    }

    public void onRedraw(BlockRedrawEvent event) {
        ScreenBlock block = event.getBlock();

        ScreenManager.getPlayersOf(block).forEach((p, s) -> {
            s.close();
            s.open();
        });
    }
}
