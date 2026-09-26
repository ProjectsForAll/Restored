package gg.drak.restored.gui.pocket;

import gg.drak.restored.gui.AbstractInventoryGui;
import gg.drak.restored.gui.GuiItems;
import gg.drak.restored.items.PocketLinkItem;
import gg.drak.restored.data.PlayerPreferences;
import gg.drak.restored.util.LegacyColors;
import host.plas.bou.gui.CornerColor;
import host.plas.bou.gui.GuiConfig;
import host.plas.bou.gui.GuiLayout;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

/** Toggle screen for a Pocket Link's Magnet Augment. */
public class MagnetPocketAugmentGui extends AbstractInventoryGui implements PocketLinkGuiBound {
    private final UUID linkId;

    public MagnetPocketAugmentGui(Player player, UUID linkId) {
        super(GuiConfig.builder(player).cornerColor(CornerColor.YELLOW).backSlot(49).build());
        this.linkId = linkId;
    }

    @Override
    public UUID getPocketLinkId() {
        return linkId;
    }

    private ItemStack linkItem() {
        return PocketLinkItem.findInInventory(player, linkId);
    }

    @Override
    public void open() {
        render();
    }

    private void render() {
        ItemStack link = linkItem();
        if (link == null) {
            player.sendMessage(LegacyColors.color("#FF5555Pocket Link not found."));
            return;
        }
        PocketLinkItem.markGuiOpen(player, linkId);
        ItemStack[] contents = beginShell(GuiLayout.SIZE_LARGE, "#FFED6A&lMagnet Pocket Augment");
        boolean enabled = PocketLinkItem.isMagnetEnabled(link);
        contents[20] = GuiItems.button(enabled ? Material.LIME_DYE : Material.GRAY_DYE,
                "#FFED6A&lMagnet: " + (enabled ? "ON" : "OFF"),
                List.of("#bdc8c9Toggle attraction of item entities within 10 blocks."));
        bindSlot(20, "enabled");

        boolean toNetwork = PlayerPreferences.isMagnetToNetwork(player.getUniqueId());
        contents[22] = GuiItems.button(Material.ENDER_CHEST,
                "#FFED6A&lPickup Destination: " + (toNetwork ? "NETWORK FIRST" : "INVENTORY FIRST"),
                List.of("#bdc8c9This preference is global and applies to every Magnet Augment."));
        bindSlot(22, "destination");

        boolean onlyFull = PocketLinkItem.isMagnetOnlyIfInventoryFull(link);
        contents[24] = GuiItems.button(onlyFull ? Material.CHEST : Material.BUNDLE,
                "#FFED6A&lNetwork Only When Inventory Full: " + (onlyFull ? "ON" : "OFF"),
                List.of("#bdc8c9Inventory is tried first when this is enabled.",
                        "#AAAAAAOverflow goes into the linked network only when no inventory space is available."));
        bindSlot(24, "only-full");
        placeReturnButton(contents, "back");
        fillUnusedWithBlack(contents);
        finishAndOpen(contents);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(inventory)) {
            return;
        }
        event.setCancelled(true);
        String key = getKeyAtSlot(event.getRawSlot());
        ItemStack link = linkItem();
        if (link == null || key == null) {
            return;
        }
        switch (key) {
            case "enabled" -> PocketLinkItem.setMagnetEnabled(link, !PocketLinkItem.isMagnetEnabled(link));
            case "destination" -> PlayerPreferences.setMagnetToNetwork(
                    player.getUniqueId(), !PlayerPreferences.isMagnetToNetwork(player.getUniqueId()));
            case "only-full" -> PocketLinkItem.setMagnetOnlyIfInventoryFull(link, !PocketLinkItem.isMagnetOnlyIfInventoryFull(link));
            case "back" -> new PocketAugmentsGui(player, linkId).open();
            default -> { return; }
        }
        if (!"back".equals(key)) {
            render();
        }
    }
}
