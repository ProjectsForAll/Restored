package gg.drak.restored.data.items.impl;

import gg.drak.restored.data.items.ItemType;
import gg.drak.restored.data.items.RestoredItem;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Material;

@Getter @Setter
public class SpeedCardItem extends RestoredItem {
    public SpeedCardItem() {
        super(ItemType.SPEED_CARD,
                Material.SUGAR,
                "&a&lSpeed Card",
                "&7Insert into an Importer, Exporter,",
                "&7or Crafter to increase operation speed."
        );
    }

    @Override
    public void updateLore() {

    }
}
