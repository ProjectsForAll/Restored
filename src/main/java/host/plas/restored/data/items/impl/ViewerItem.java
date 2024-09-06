package host.plas.restored.data.items.impl;

import host.plas.restored.data.Network;
import host.plas.restored.data.blocks.impl.Drive;
import host.plas.restored.data.blocks.impl.Viewer;
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
public class ViewerItem extends RestoredItem implements IPlaceable {
    public ViewerItem() {
        super(ItemType.VIEWER,
                Material.SMOKER,
                "&6&lViewer",
                "&7Place this block to", "&7view things in a network."
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
        new Viewer(null, atBlock.getLocation());

        placeAsBlock(atBlock, placedBy);
    }

    @Override
    public void placeAsBlock(Block atBlock, Player placedBy) {
        atBlock.setType(Material.SMOKER);
    }
}
