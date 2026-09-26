package gg.drak.restored.gui.compactor;

import gg.drak.restored.data.CompactConfiguration;
import gg.drak.restored.gui.GuiItems;
import gg.drak.restored.util.LegacyColors;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Display icon used by the paginated compacting-configuration menu. */
public final class CompactingConfigIcon {

    private CompactingConfigIcon() {
    }

    public static ItemStack create(CompactConfiguration configuration) {
        ItemStack icon = configuration.getItem() == null
                ? GuiItems.button(Material.PAPER, "#FFED6A&lCompacting Configuration")
                : configuration.getItem().clone();
        icon.setAmount(1);
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(LegacyColors.color("#FFED6A&lCompacting Configuration"));
            List<String> lore = meta.getLore() == null ? new ArrayList<>() : new ArrayList<>(meta.getLore());
            lore.add("");
            lore.add(LegacyColors.color("#AAAAAAID: #bdc8c9" + configuration.getIdentifier()));
            lore.add(LegacyColors.color("#AAAAAAState: "
                    + (configuration.isEnabled() ? "#00FC88Enabled" : "#FF5555Disabled")));
            lore.add(LegacyColors.color("#AAAAAAAction: #FFED6A" + configuration.getAction().name()));
            lore.add(LegacyColors.color("#AAAAAAItem: #FFED6A"
                    + (configuration.getItem() == null ? "Not set" : configuration.getItem().getType().name())));
            lore.add(LegacyColors.color("#AAAAAAQuantity: #FFED6A" + configuration.getOperand().display()
                    + " " + configuration.getQuantity()));
            lore.add(LegacyColors.color("#bdc8c9Click to edit."));
            meta.setLore(lore);
            icon.setItemMeta(meta);
        }
        return icon;
    }
}
