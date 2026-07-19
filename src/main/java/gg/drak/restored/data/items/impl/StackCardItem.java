package gg.drak.restored.data.items.impl;

import gg.drak.restored.data.items.ItemType;
import gg.drak.restored.data.items.RestoredItem;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Material;

@Getter @Setter
public class StackCardItem extends RestoredItem {
    public StackCardItem() {
        super(ItemType.STACK_CARD,
                Material.GLOWSTONE_DUST,
                "&e&lStack Card",
                "&7Insert into an Importer, Exporter,",
                "&7or Crafter to process a full stack at a time."
        );
    }

    @Override
    public void updateLore() {

    }
}
