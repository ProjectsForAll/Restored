package gg.drak.restored.data.items.impl;

import host.plas.bou.commands.Sender;
import gg.drak.restored.Restored;
import gg.drak.restored.data.Network;
import gg.drak.restored.data.NetworkManager;
import gg.drak.restored.data.blocks.NetworkMap;
import gg.drak.restored.data.items.IPlaceable;
import gg.drak.restored.data.items.ItemType;
import gg.drak.restored.data.items.RestoredItem;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.Optional;

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

        // Check if placing this controller would create a network with 2+ controllers
        Optional<Network> adjacentNetwork = NetworkManager.getAdjacentNetwork(placedBlock);
        if (adjacentNetwork.isPresent()) {
            Network network = adjacentNetwork.get();
            
            // Check if this network already has a controller
            if (network.getController() != null) {
                event.setCancelled(true);
                Sender sender = new Sender(player);
                sender.sendMessage("&cYou cannot place a controller block near a network that already has a controller!");
                return;
            }
        }

        // Check all orthogonally connected blocks for controllers
        BlockFace[] faces = new BlockFace[] {
                BlockFace.NORTH,
                BlockFace.EAST,
                BlockFace.SOUTH,
                BlockFace.WEST,
                BlockFace.UP,
                BlockFace.DOWN
        };

        for (BlockFace face : faces) {
            Block relative = placedBlock.getRelative(face);
            Optional<Network> relativeNetwork = NetworkManager.getNetworkAt(relative);
            
            if (relativeNetwork.isPresent()) {
                Network network = relativeNetwork.get();
                
                // Check if this connected network has a controller
                if (network.getController() != null) {
                    event.setCancelled(true);
                    Sender sender = new Sender(player);
                    sender.sendMessage("&cYou cannot place a controller block that would connect to a network with an existing controller!");
                    return;
                }
            }
        }

        // No adjacent networks with controllers, proceed with placement
        onNoNetworkPlace(placedBlock, player);
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
        // Set block type first so DataBlock is created with correct block
        placeAsBlock(atBlock, placedBy);
        
        // Create network (this will create the Controller and call onPlaced())
        Network network = NetworkMap.createNetwork(atBlock, placedBy);
        
        // Save network state
        network.onSave();
    }

    @Override
    public void placeAsBlock(Block atBlock, Player placedBy) {
        atBlock.setType(Material.IRON_BLOCK);
    }
}
