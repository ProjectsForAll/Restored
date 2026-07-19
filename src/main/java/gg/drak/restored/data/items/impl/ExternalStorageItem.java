package gg.drak.restored.data.items.impl;

import gg.drak.restored.data.Network;
import gg.drak.restored.data.blocks.impl.ExternalStorage;
import gg.drak.restored.data.items.IPlaceable;
import gg.drak.restored.data.items.ItemType;
import gg.drak.restored.data.items.RestoredItem;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

@Getter @Setter
public class ExternalStorageItem extends RestoredItem implements IPlaceable {
    public ExternalStorageItem() {
        super(ItemType.EXTERNAL_STORAGE,
                Material.BARREL,
                "&9&lExternal Storage",
                "&7Place adjacent to a container to",
                "&7expose its contents as network storage."
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
        placeAsBlock(atBlock, placedBy);
        ExternalStorage block = new ExternalStorage(null, atBlock.getLocation());
        block.onPlaced();
    }

    @Override
    public void placeAsBlock(Block atBlock, Player placedBy) {
        if (atBlock.getType() != Material.BARREL) {
            atBlock.setType(Material.BARREL);
        }
    }
}
