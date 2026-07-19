package gg.drak.restored.data.items.impl;

import gg.drak.restored.data.Network;
import gg.drak.restored.data.blocks.impl.Exporter;
import gg.drak.restored.data.items.IPlaceable;
import gg.drak.restored.data.items.ItemType;
import gg.drak.restored.data.items.RestoredItem;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

@Getter @Setter
public class ExporterItem extends RestoredItem implements IPlaceable {
    public ExporterItem() {
        super(ItemType.EXPORTER,
                Material.DROPPER,
                "&6&lExporter",
                "&7Place adjacent to a container to",
                "&7push items from the network into it."
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
        Exporter block = new Exporter(null, atBlock.getLocation());
        block.onPlaced();
    }

    @Override
    public void placeAsBlock(Block atBlock, Player placedBy) {
        if (atBlock.getType() != Material.DROPPER) {
            atBlock.setType(Material.DROPPER);
        }
    }
}
