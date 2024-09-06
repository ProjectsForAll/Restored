package host.plas.restored.data.items.impl;

import host.plas.bou.commands.Sender;
import host.plas.restored.Restored;
import host.plas.restored.data.Network;
import host.plas.restored.data.NetworkManager;
import host.plas.restored.data.items.IPlaceable;
import host.plas.restored.data.items.ItemType;
import host.plas.restored.data.items.RestoredItem;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;

@Getter @Setter
public class ControllerItem extends RestoredItem implements IPlaceable {
    public ControllerItem() {
        super(ItemType.CONTROLLER,
                Material.IRON_BLOCK,
                "&b&lController",
                "&7Place this block to", "&7create a new network."
        );
    }

    @Override
    public void updateLore() {

    }

    @Override
    public void onPlace(BlockPlaceEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        Block block = event.getBlockAgainst();
        if (block == null) return;

        Block placedBlock = event.getBlockPlaced();

        Restored.getInstance().logInfo("Handling block placement for " + getClass().getSimpleName() + "...");

        NetworkManager.getAdjacentNetwork(placedBlock).ifPresentOrElse(network -> {
            Restored.getInstance().logInfo("Found network for block placement for " + getClass().getSimpleName() + "...");

            event.setCancelled(true);
            Sender sender = new Sender(player);
            sender.sendMessage("&cYou cannot place a controller block near an existing network!");
        }, () -> {
            Restored.getInstance().logInfo("No network found for block placement for " + getClass().getSimpleName() + "...");

            onNoNetworkPlace(placedBlock, player);

//            event.setCancelled(true);
        });
    }

    @Override
    public void onNetworkPlace(Block atBlock, Player placedBy, Network network) {
//        network.onBlockPlace(atBlock, this);
//
//        placeAsBlock(atBlock, placedBy);

        // cancelled
    }

    @Override
    public void onNoNetworkPlace(Block atBlock, Player placedBy) {
        Network network = new Network(atBlock, placedBy);

        network.onSave();

        placeAsBlock(atBlock, placedBy);
    }

    @Override
    public void placeAsBlock(Block atBlock, Player placedBy) {
        atBlock.setType(Material.IRON_BLOCK);
    }
}
