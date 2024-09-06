package host.plas.restored.data.items.impl;

import host.plas.restored.data.Network;
import host.plas.restored.data.blocks.impl.Drive;
import host.plas.restored.data.items.IPlaceable;
import host.plas.restored.data.items.ItemType;
import host.plas.restored.data.items.RestoredItem;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.math.BigInteger;

@Getter @Setter
public class DriveItem extends RestoredItem implements IPlaceable {
    public DriveItem() {
        super(ItemType.DRIVE,
                Material.BLAST_FURNACE,
                "&c&lDrive",
                "&7Place this block to", "&7add storage to a network."
        );
    }

    @Override
    public void updateLore() {

    }

    @Override
    public void onNetworkPlace(Block atBlock, Player placedBy, Network network) {
        network.onBlockPlace(atBlock, this);

        placeAsBlock(atBlock, placedBy);
    }

    @Override
    public void onNoNetworkPlace(Block atBlock, Player placedBy) {
        new Drive(null, atBlock.getLocation());

        placeAsBlock(atBlock, placedBy);
    }

    @Override
    public void placeAsBlock(Block atBlock, Player placedBy) {
        atBlock.setType(Material.BLAST_FURNACE);
    }
}
