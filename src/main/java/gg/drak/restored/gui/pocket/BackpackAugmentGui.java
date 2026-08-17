package gg.drak.restored.gui.pocket;

import gg.drak.restored.gui.AbstractInventoryGui;
import gg.drak.restored.items.PocketLinkItem;
import gg.drak.restored.util.LegacyColors;
import host.plas.bou.gui.CornerColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/** A 27-slot inventory persisted on the Pocket Link item, not on the player. */
public class BackpackAugmentGui extends AbstractInventoryGui implements PocketLinkGuiBound {
    private final UUID linkId;

    public BackpackAugmentGui(Player player, UUID linkId) {
        super(player, CornerColor.YELLOW);
        this.linkId = linkId;
    }

    @Override
    public UUID getPocketLinkId() {
        return linkId;
    }

    @Override
    public void open() {
        ItemStack link = PocketLinkItem.findInInventory(player, linkId);
        if (link == null) {
            player.sendMessage(LegacyColors.color("#FF5555Pocket Link not found."));
            return;
        }
        PocketLinkItem.markGuiOpen(player, linkId);
        inventory = Bukkit.createInventory(this, 27, LegacyColors.color("#FFED6A&lBackpack Augment"));
        inventory.setContents(PocketLinkItem.getBackpackContents(link));
        player.openInventory(inventory);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        // Vanilla chest behavior is intentional. GuiListener exempts this inventory.
    }

    @Override
    public void handleDrag(InventoryDragEvent event) {
        // Vanilla chest behavior is intentional.
    }

    @Override
    public void handleClose(InventoryCloseEvent event) {
        ItemStack link = PocketLinkItem.findInInventory(player, linkId);
        if (link != null && inventory != null) {
            PocketLinkItem.setBackpackContents(link, inventory.getContents());
        }
    }
}
