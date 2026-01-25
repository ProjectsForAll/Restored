package gg.drak.restored.data.items.impl;

import gg.drak.restored.data.Network;
import gg.drak.restored.data.blocks.impl.CraftingViewer;
import gg.drak.restored.data.items.IPlaceable;
import gg.drak.restored.data.items.ItemType;
import gg.drak.restored.data.items.RestoredItem;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

@Getter @Setter
public class CraftingViewerItem extends RestoredItem implements IPlaceable {
    public CraftingViewerItem() {
        super(ItemType.CRAFTING_VIEWER,
                Material.CRAFTING_TABLE,
                "&6&lCrafting Viewer",
                "&7Place this block to", "&7view and craft items in a network."
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
        CraftingViewer craftingViewer = new CraftingViewer(null, atBlock.getLocation());
        craftingViewer.onPlaced();

        placeAsBlock(atBlock, placedBy);
    }

    @Override
    public void placeAsBlock(Block atBlock, Player placedBy) {
        atBlock.setType(Material.CRAFTING_TABLE);
    }
}
