package gg.drak.restored.data.items.impl;

import gg.drak.restored.data.Network;
import gg.drak.restored.data.blocks.impl.Crafter;
import gg.drak.restored.data.items.IPlaceable;
import gg.drak.restored.data.items.ItemType;
import gg.drak.restored.data.items.RestoredItem;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

@Getter @Setter
public class CrafterItem extends RestoredItem implements IPlaceable {
    public CrafterItem() {
        super(ItemType.CRAFTER,
                Material.DISPENSER,
                "&d&lCrafter",
                "&7Place this block to auto-craft",
                "&7items using network resources."
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
        Crafter block = new Crafter(null, atBlock.getLocation());
        block.onPlaced();
    }

    @Override
    public void placeAsBlock(Block atBlock, Player placedBy) {
        if (atBlock.getType() != Material.DISPENSER) {
            atBlock.setType(Material.DISPENSER);
        }
    }
}
